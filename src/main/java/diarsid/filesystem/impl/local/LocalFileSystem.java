package diarsid.filesystem.impl.local;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

import diarsid.files.Extensions;
import diarsid.files.LocalDirectoryWatcher;
import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.File;
import diarsid.filesystem.api.FileSystem;
import diarsid.filesystem.api.FileSystemType;
import diarsid.filesystem.api.ignoring.Ignores;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.objects.references.Result;

import static java.awt.Desktop.getDesktop;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.util.Collections.reverse;
import static java.util.Comparator.reverseOrder;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import static diarsid.filesystem.api.FileSystemType.LOCAL;
import static diarsid.filesystem.api.NoResultReason.PATH_IS_NOT_DIRECTORY;
import static diarsid.filesystem.api.NoResultReason.PATH_IS_NOT_FILE;
import static diarsid.filesystem.api.NoResultReason.PATH_NOT_EXISTS;
import static diarsid.support.concurrency.ThreadUtils.currentThreadTrack;

public class LocalFileSystem implements FileSystem {

    static class PathMove {

        private Path oldPath;
        private Path newPath;

        PathMove() {
        }

        void setFromTo(Path from, Path to) {
            this.oldPath = from;
            this.newPath = to;
        }

        List<PathChange> findChangesTo(List<Path> oldPaths) {
            List<PathChange> changes = oldPaths
                    .stream()
                    .map(this::findChangeTo)
                    .filter(Objects::nonNull)
                    .collect(toList());

            return changes;
        }

        private PathChange findChangeTo(Path removedPath) {
            Path removedPathRelative = this.oldPath.relativize(removedPath);
            try {
                Path createdPath = this.newPath.resolve(removedPathRelative).toRealPath();
                return new PathChange(removedPath, createdPath, removedPathRelative);
            } catch (IOException e) {
                handle(e);
                return null;
            }
        }

        void clear() {
            this.oldPath = null;
            this.newPath = null;
        }
    }

    static class PathChange {

        private final Path oldPath;
        private final Path newPath;
        private final Path relativePath;
        private final boolean isDirectory;

        PathChange(Path oldPath, Path newPath, Path relativePath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
            this.relativePath = relativePath;
            this.isDirectory = Files.exists(newPath) && Files.isDirectory(newPath);
        }

        public Path oldPath() {
            return oldPath;
        }

        public Path newPath() {
            return newPath;
        }

        public Path relativePath() {
            return relativePath;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public String toString() {
            return "PathChange{" +
                    "old=" + oldPath +
                    ", new=" + newPath +
                    ", relative=" + relativePath +
                    ", type=" + (isDirectory ? "directory" : "file") +
                    '}';
        }
    }

    private final Ignores ignores;
    private final HashMap<Path, LocalDirectoryWatcher> watchersByPath;
    private final LocalMachineDirectory localMachineDirectory;
    private final Extensions extensions;
    private final Desktop desktop;
    private final Predicate<FSEntry> notIgnored;
    private final ChangesImpl changes;

    public LocalFileSystem(
            Ignores ignores,
            NamedThreadSource namedThreadSource,
            java.nio.file.FileSystem fileSystem) {
        this.ignores = ignores;
        this.watchersByPath = new HashMap<>();
        this.localMachineDirectory = new LocalMachineDirectory(this, fileSystem.getRootDirectories());
        this.extensions = new Extensions();
        this.desktop = getDesktop();
        this.notIgnored = this.ignores::isNotIgnored;
        this.changes = new ChangesImpl(namedThreadSource);

        this.changes.listenForEntriesRemoved(this::removeWatchers);
        this.changes.listenForEntriesAdded(this::createWatchersForEntries);
    }

    private void removeWatchers(List<Path> paths) {
        synchronized ( this.watchersByPath ) {
            for ( Path path : paths ) {
                LocalDirectoryWatcher localDirectoryWatcher = this.watchersByPath.remove(path);

                if ( nonNull(localDirectoryWatcher) ) {
                    localDirectoryWatcher.destroy();
                }
            }
        }
    }

    @Override
    public Directory machineDirectory() {
        return this.localMachineDirectory;
    }

    @Override
    public Result<FSEntry> toFSEntry(Path path) {
        if ( Files.exists(path) ) {
            try {
                Path realPath = path.toRealPath();
                return Result.completed(this.toLocalFSEntry(realPath));
            }
            catch (IOException e) {
                return Result.empty(e);
            }
        }
        else {
            return Result.empty(PATH_NOT_EXISTS);
        }
    }

    FSEntry toLocalFSEntry(Path path) {
        if (Files.isDirectory(path)) {
            return this.toLocalDirectory(path);
        }
        else {
            return this.toLocalFile(path);
        }
    }

    @Override
    public Result<Directory> userHomeDirectory() {
        String userHome = getProperty("user.home");

        if ( isNull(userHome) || userHome.isEmpty() || userHome.isBlank() ) {
            return Result.empty("User home is not defined!");
        }

        return this.toDirectory(userHome);
    }

    @Override
    public Result<Directory> toDirectory(Path path) {
        if ( Files.exists(path) ) {
            if ( Files.isDirectory(path) ) {
                try {
                    Path realPath = path.toRealPath();
                    return Result.completed(this.toLocalDirectory(realPath));
                }
                catch (IOException e) {
                    return Result.empty(e);
                }
            }
            else {
                return Result.empty(PATH_IS_NOT_DIRECTORY);
            }
        }
        else {
            return Result.empty(PATH_NOT_EXISTS);
        }
    }

    @Override
    public Result<Directory> toDirectoryCreateIfNotExists(Path path) {
        if ( Files.exists(path) ) {
            if ( Files.isDirectory(path) ) {
                try {
                    Path realPath = path.toRealPath();
                    return Result.completed(this.toLocalDirectory(realPath));
                }
                catch (IOException e) {
                    return Result.empty(e);
                }
            }
            else {
                return Result.empty(PATH_IS_NOT_DIRECTORY);
            }
        }
        else {
            try {
                Path created = Files.createDirectories(path.toAbsolutePath());
                return Result.completed(this.toLocalDirectory(created));
            }
            catch (IOException e) {
                return Result.empty(e);
            }
        }
    }

    LocalDirectory toLocalDirectory(Path path) {
        LocalDirectory localDirectory = new LocalDirectory(path, this);
        return localDirectory;
    }

    @Override
    public Result<File> toFile(Path path) {
        if ( Files.exists(path) ) {
            if ( ! Files.isDirectory(path) ) {
                try {
                    Path realPath = path.toRealPath();
                    return Result.completed(this.toLocalFile(realPath));
                }
                catch (IOException e) {
                    return Result.empty(e);
                }
            }
            else {
                return Result.empty(PATH_IS_NOT_FILE);
            }
        }
        else {
            return Result.empty(PATH_NOT_EXISTS);
        }
    }

    @Override
    public Result<File> toFileCreateIfNotExists(Path path) {
        if ( Files.exists(path) ) {
            if ( ! Files.isDirectory(path) ) {
                try {
                    Path realPath = path.toRealPath();
                    return Result.completed(this.toLocalFile(realPath));
                }
                catch (IOException e) {
                    return Result.empty(e);
                }
            }
            else {
                return Result.empty(PATH_IS_NOT_FILE);
            }
        }
        else {
            try {
                Path created = Files.createFile(path.toAbsolutePath());
                return Result.completed(this.toLocalFile(created));
            }
            catch (IOException e) {
                return Result.empty(e);
            }
        }
    }

    LocalFile toLocalFile(Path path) {
        LocalFile localFile = new LocalFile(path, this);
        return localFile;
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public boolean isFile(Path path) {
        return Files.isRegularFile(path);
    }

    @Override
    public boolean exists(FSEntry fsEntry) {
        return Files.exists(fsEntry.path());
    }

    @Override
    public boolean copy(FSEntry whatToCopy, Directory whereToCopy) {
        boolean success;

        LocalDirectory directoryHost = (LocalDirectory) whereToCopy;
        if ( whatToCopy.isFile() ) {
            LocalFile file = (LocalFile) whatToCopy;
            try {
                Files.copy(file.path(), directoryHost.path().resolve(file.name()));
                success = true;
            }
            catch (IOException e) {
                handle(e);
                success = false;
            }
        }
        else {
            LocalDirectory directoryToCopy = (LocalDirectory) whatToCopy;

            if ( directoryToCopy.equals(directoryHost) ) {
                return false;
            }

            Path directoryToCopyParentPath = directoryToCopy.path().getParent();

            try {
                List<Path> paths = Files
                        .walk(directoryToCopy.path())
                        .collect(toList());

                Path subPath;
                for (Path path : paths) {
                    subPath = directoryToCopyParentPath.relativize(path);
                    System.out.println(" copy ");
                    System.out.println(" src : " + path.toString());
                    System.out.println(" sub : " + subPath.toString());
                    System.out.println(" trg : " + directoryHost.path().resolve(subPath).toString());
                    Files.copy(path, directoryHost.path().resolve(subPath), COPY_ATTRIBUTES);

                }
                success = true;
            }
            catch (IOException e) {
                handle(e);
                success = false;
            }
        }

        return success;
    }

    @Override
    public boolean move(FSEntry whatToMove, Directory whereToMove) {
        boolean success;

        LocalDirectory directoryHost = (LocalDirectory) whereToMove;

        if ( whatToMove.isFile() ) {
            LocalFile fileToMove = (LocalFile) whatToMove;
            Path oldPath = fileToMove.path();
            Path newPath = directoryHost.path().resolve(fileToMove.name());

            boolean moved;
            try {
                Files.move(oldPath, newPath, REPLACE_EXISTING);
                moved = true;
            }
            catch (AccessDeniedException e) {
                handle(e);
                moved = false;
            }
            catch (IOException e) {
                handle(e);
                moved = false;
            }

            if ( moved ) {
                this.changes.removed(oldPath);
                this.changes.added(this.toLocalFSEntry(newPath));
            }

            success = moved;
        }
        else {
            LocalDirectory directoryToMove = (LocalDirectory) whatToMove;

            if ( directoryToMove.equals(directoryHost) ) {
                return false;
            }

            if ( directoryToMove.isIndirectParentOf(directoryHost) ) {
                return false;
            }

            try {
                Path oldPath = directoryToMove.path();
                Path newPath = directoryHost.path().resolve(directoryToMove.name());
                System.out.println("[FS] [move] " + oldPath + " -> " + newPath);

                List<Path> pathsToRemove = Files
                        .walk(oldPath)
                        .sorted(reverseOrder())
                        .collect(toList());

                this.removeWatchers(pathsToRemove);

                boolean moved;
                try {
                    Files.move(oldPath, newPath);
                    moved = true;
                }
                catch (AccessDeniedException e) {
                    handle(e);
                    moved = false;
                }
                catch (IOException e) {
                    handle(e);
                    moved = false;
                }

                if ( moved ) {
                    List<FSEntry> newFSEntries = Files
                            .walk(newPath)
                            .map(this::toLocalFSEntry)
                            .sorted(reverseOrder())
                            .collect(toList());

                    this.changes.removed(pathsToRemove);
                    this.changes.added(newFSEntries);

                    success = true;
                }
                else {
                    this.createWatchers(pathsToRemove);
                    success = false;
                }

            }
            catch (IOException e) {
                handle(e);
                success = false;
            }
        }

        return success;
    }

    @Override
    public boolean rename(FSEntry whatToRename, String newName) {
        boolean success;

        if ( whatToRename.isFile() ) {
            LocalFile fileToRename = (LocalFile) whatToRename;
            Path oldPath = fileToRename.path();
            Path parent = oldPath.getParent();

            Path newPath;
            if ( isNull(parent) ) {
                newPath = Paths.get(newName);
            }
            else {
                newPath = parent.resolve(newName);
            }

            boolean renamed;
            try {
                System.out.println("[FS] [rename] " + oldPath + " -> " + newPath);
                Files.move(oldPath, newPath, REPLACE_EXISTING);
                renamed = true;
            }
            catch (AccessDeniedException e) {
                handle(e);
                renamed = false;
            }
            catch (IOException e) {
                handle(e);
                renamed = false;
            }

            if ( renamed ) {
                this.changes.removed(oldPath);
                this.changes.added(this.toLocalFSEntry(newPath));
            }

            success = renamed;
        }
        else {
            LocalDirectory directoryToRename = (LocalDirectory) whatToRename;

            try {
                Path oldPath = directoryToRename.path();Path parent = oldPath.getParent();

                Path newPath;
                if ( isNull(parent) ) {
                    newPath = Paths.get(newName);
                }
                else {
                    newPath = parent.resolve(newName);
                }

                List<Path> pathsToRemove = Files
                        .walk(oldPath)
                        .sorted(reverseOrder())
                        .collect(toList());

                this.removeWatchers(pathsToRemove);

                boolean renamed;
                try {
                    System.out.println("[FS] [rename] " + oldPath + " -> " + newPath);
                    Files.move(oldPath, newPath);
                    renamed = true;
                }
                catch (AccessDeniedException e) {
                    handle(e);
                    renamed = false;
                }
                catch (IOException e) {
                    e.printStackTrace();
                    renamed = false;
                }

                if ( renamed ) {
                    List<FSEntry> newFSEntries = Files
                            .walk(newPath)
                            .map(this::toLocalFSEntry)
                            .sorted(reverseOrder())
                            .collect(toList());

                    this.changes.removed(pathsToRemove);
                    this.changes.added(newFSEntries);

                    success = true;
                }
                else {
                    this.createWatchers(pathsToRemove);
                    success = false;
                }

            }
            catch (IOException e) {
                handle(e);
                success = false;
            }
        }

        return success;
    }

//    private ChangeableFSEntry makeChangeToCachedPaths(PathChange pathChange) {
//        if ( pathChange.isDirectory() ) {
//            ChangeableFSEntry entry = this.entriesByPath.remove(pathChange.oldPath);
//
//            if ( nonNull(entry) ) {
//                LocalDirectory directory = entry.asDirectory();
//                directory.movedTo(pathChange.newPath);
//                this.entriesByPath.put(pathChange.newPath, directory);
//                createAndAddNewPathWatcher(directory);
//                return directory;
//            }
//            else {
//                return null;
//            }
//        }
//        else {
//            ChangeableFSEntry entry = this.entriesByPath.remove(pathChange.oldPath);
//
//            if ( nonNull(entry) ) {
////                removePathWatcher(pathChange.oldPath);
//                LocalFile file = entry.asFile();
//                file.movedTo(pathChange.newPath);
//                this.entriesByPath.put(pathChange.newPath, file);
////                createAndAddNewPathWatcher(pathChange.newPath);
//                return file;
//            }
//            else {
//                return null;
//            }
//        }
//    }

//    private void remove(Path path) {
//        System.out.println("[REMOVING PATH] " + path);
//        ChangeableFSEntry entry = this.entriesByPath.remove(path);
//        this.entriesByPath.remove(path);
////        if ( nonNull(path) ) {
////            try {
////                Files.walk(path).forEach(visitedPath -> {
////                    System.out.println("[REMOVING PATH] subpath " + path);
////                    this.entriesByPath.remove(visitedPath);
////                    removePathWatcher(visitedPath);
////                });
////            }
////            catch (IOException e) {
////                e.printStackTrace();
////            }
////        }
//
//    }

    @Override
    public boolean remove(FSEntry entry) {
        boolean success;

        System.out.println("removing... " + entry.path());
        currentThreadTrack("diarsid", (element) -> System.out.println("    " + element));
        if ( entry.isFile() ) {
            LocalFile file = (LocalFile) entry;
            Path oldPath = file.path();
            try {
                Files.delete(oldPath);
                this.changes.removed(oldPath);
                success = true;
            }
            catch (IOException e) {
                handle(e);
                success = false;
            }
        }
        else {
            LocalDirectory directoryToRemove = (LocalDirectory) entry;
            Path pathToRemove = directoryToRemove.path();

            try {
                List<Path> pathsToRemove = Files
                        .walk(pathToRemove)
                        .sorted(reverseOrder())
                        .collect(toList());

                this.removeWatchers(pathsToRemove);

                List<Path> removedPaths = new ArrayList<>();
                for ( Path path : pathsToRemove ) {
                    try {
                        Files.delete(path);
                        removedPaths.add(path);
                    }
                    catch (IOException e) {
                        handle(e);
                        break;
                    }
                }

                boolean removed = removedPaths.size() == pathsToRemove.size();

                if ( removedPaths.size() > 0 ) {
                    this.changes.removed(removedPaths);
                }

                if ( removed ) {
                    success = true;
                }
                else {
                    pathsToRemove.removeAll(removedPaths);
                    this.createWatchers(pathsToRemove);
                    success = false;
                }
            }
            catch (IOException e) {
                handle(e);
                success = false;
            }
        }

        return success;
    }

    @Override
    public boolean remove(Path path) {
        boolean success;

        System.out.println("removing... " + path);
        currentThreadTrack("diarsid", (element) -> System.out.println("    " + element));

        if ( Files.exists(path) ) {
            if ( Files.isRegularFile(path) ) {
                try {
                    Files.delete(path);
                    this.changes.removed(path);
                    success = true;
                }
                catch (IOException e) {
                    handle(e);
                    success = false;
                }
            }
            else if ( Files.isDirectory(path) ) {
                try {
                    List<Path> pathsToRemove = Files
                            .walk(path)
                            .sorted(reverseOrder())
                            .collect(toList());

                    this.removeWatchers(pathsToRemove);

                    List<Path> removedPaths = new ArrayList<>();
                    for ( Path pathToRemove : pathsToRemove ) {
                        try {
                            Files.delete(pathToRemove);
                            removedPaths.add(pathToRemove);
                        }
                        catch (IOException e) {
                            handle(e);
                            break;
                        }
                    }

                    boolean removed = removedPaths.size() == pathsToRemove.size();

                    if ( removedPaths.size() > 0 ) {
                        this.changes.removed(removedPaths);
                    }

                    if ( removed ) {
                        success = true;
                    }
                    else {
                        pathsToRemove.removeAll(removedPaths);
                        this.createWatchers(pathsToRemove);
                        success = false;
                    }
                }
                catch (IOException e) {
                    handle(e);
                    success = false;
                }
            }
            else {
                success = false;
            }
        }
        else {
            success = true;
        }

        return success;
    }

    @Override
    public boolean moveAll(
            List<FSEntry> whatToMove,
            Directory parentDirectoryWhereToMove,
            ProgressTrackerBack<FSEntry> progressTracker) {
        whatToMove.sort(FSEntry.compareByDepth);

        progressTracker.begin(whatToMove);

        boolean moved;
        for (FSEntry entry : whatToMove) {
            progressTracker.processing(entry);
            moved = this.move(entry, parentDirectoryWhereToMove);
            if ( moved ) {
                progressTracker.processingDone(entry);
            }
            else {

            }
        }

        progressTracker.completed();
        return true;
    }

    @Override
    public boolean copyAll(
            List<FSEntry> whatToCopy,
            Directory parentDirectoryWhereToCopy,
            ProgressTrackerBack<FSEntry> progressTracker) {
        whatToCopy.sort(FSEntry.compareByDepth);

        progressTracker.begin(whatToCopy);

        for (FSEntry entry : whatToCopy) {
            progressTracker.processing(entry);
            this.copy(entry, parentDirectoryWhereToCopy);
            progressTracker.processingDone(entry);
        }

        progressTracker.completed();
        return true;
    }

    @Override
    public boolean removeAll(List<FSEntry> whatToRemove, ProgressTrackerBack<FSEntry> progressTracker) {
        whatToRemove.sort(FSEntry.compareByDepth);

        progressTracker.begin(whatToRemove);

        for (FSEntry entry : whatToRemove) {
            progressTracker.processing(entry);
            this.remove(entry);
            progressTracker.processingDone(entry);
        }

        progressTracker.completed();
        return true;
    }

    @Override
    public boolean open(File file) {
        LocalFile localFile = (LocalFile) file;
        try {
            this.desktop.open(localFile.path().toFile());
            return true;
        }
        catch (AccessDeniedException denied) {
            handle(denied);
            return false;
        }
        catch (IOException e) {
            handle(e);
            return false;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void showInDefaultFileManager(FSEntry fsEntry) {
        if ( fsEntry.isFile() ) {
            Result<Directory> parent = fsEntry.parent();
            try {
                if ( parent.isPresent() ) {
                    this.desktop.open(parent.get().path().toFile());
                }
                else {
                    this.desktop.open(this.localMachineDirectory.roots().get(0).toFile());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                if ( fsEntry instanceof LocalMachineDirectory ) {
                    this.desktop.open(this.localMachineDirectory.roots().get(0).toFile());
                }
                else {
                    this.desktop.open(fsEntry.path().toFile());
                }
            }
            catch (AccessDeniedException denied) {
                handle(denied);
            }
            catch (IOException e) {
                handle(e);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Stream<FSEntry> list(Directory directory) {
        LocalDirectory localDirectory = (LocalDirectory) directory;
        try {
             return Files
                     .list(localDirectory.path())
                     .map(this::toLocalFSEntry)
                     .filter(this.notIgnored);
        }
        catch (AccessDeniedException denied) {
            handle(denied);
            return Stream.empty();
        }
        catch (IOException e) {
            handle(e);
            return Stream.empty();
        }
    }

    @Override
    public Result<Directory> parentOf(FSEntry fsEntry) {
        Path parentPath = fsEntry.path().getParent();
        if ( nonNull(parentPath) ) {
            return this.toDirectory(parentPath);
        }
        else {
            return Result.empty(PATH_NOT_EXISTS);
        }
    }

    @Override
    public Result<Directory> firstExistingParentOf(Path path) {
        Path parent = path.getParent();

        while ( nonNull(parent) ) {
            if ( Files.exists(parent) ) {
                break;
            }
            else {
                parent = parent.getParent();
            }
        }

        if ( isNull(parent) ) {
            return Result.empty(PATH_NOT_EXISTS);
        }
        else {
            return this.toDirectory(parent);
        }
    }

    @Override
    public List<Directory> parentsOf(FSEntry fsEntry) {
        Path parentPath = fsEntry.path().getParent();
        return this.splitToDirectories(parentPath);
    }

    @Override
    public List<Directory> parentsOf(Path path) {
        Path parentPath = path.getParent();
        return this.splitToDirectories(parentPath);
    }

    private List<Directory> splitToDirectories(Path parentPath) {
        Directory parentDirectory;

        List<Directory> parents = new ArrayList<>();

        while (nonNull(parentPath)) {
            parentDirectory = this.toLocalDirectory(parentPath);
            parents.add(parentDirectory);
            parentPath = parentPath.getParent();
        }

        reverse(parents);

        return parents;
    }

    @Override
    public long sizeOf(FSEntry fsEntry) {
        long size;

        try {
            size = Files.size(fsEntry.path());
        }
        catch (IOException e) {
            handle(e);
            size = -1;
        }

        return size;
    }

    @Override
    public Extensions extensions() {
        return this.extensions;
    }

    @Override
    public boolean isRoot(Directory directory) {
        LocalDirectory localDirectory = (LocalDirectory) directory;
        return this.localMachineDirectory.roots().contains(localDirectory.path());
    }

    @Override
    public boolean isMachine(Directory directory) {
        return directory instanceof LocalMachineDirectory;
    }

    @Override
    public FileSystemType type() {
        return LOCAL;
    }

    @Override
    public Changes changes() {
        return this.changes;
    }

    @Override
    public void watch(Directory directory) {
        synchronized ( this.watchersByPath ) {
            this.createAndPutNewWatcherIfAbsent(directory.path());
        }
    }

    @Override
    public Result<LocalDateTime> creationTimeOf(Path path) {
        try {
            Object attribute = Files.getAttribute(path, "creationTime");
            Instant instant = ((FileTime) attribute).toInstant();
            return Result.completed(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        }
        catch (IOException e) {
            return Result.empty(e);
        }
    }

    @Override
    public Result<LocalDateTime> modificationTimeOf(Path path) {
        try {
            Object attribute = Files.getAttribute(path, "lastModifiedTime");
            Instant instant = ((FileTime) attribute).toInstant();
            return Result.completed(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        }
        catch (IOException e) {
            return Result.empty(e);
        }
    }

    private void createWatchers(List<Path> paths) {
        synchronized ( this.watchersByPath ) {
            paths.stream()
                    .filter(Files::isDirectory)
                    .forEach(this::createAndPutNewWatcherIfAbsent);
        }
    }

    private void createWatchersForEntries(List<FSEntry> entries) {
        synchronized ( this.watchersByPath ) {
            entries.stream()
                    .filter(FSEntry::isDirectory)
                    .map(FSEntry::path)
                    .forEach(this::createAndPutNewWatcherIfAbsent);
        }
    }

    private void createAndPutNewWatcherIfAbsent(Path path) {
        if ( ! Files.exists(path) ) {
            return;
        }

        if ( ! Files.isDirectory(path) ) {
            return;
        }

        this.watchersByPath.computeIfAbsent(path, this::createNewWatcher);
    }

    private LocalDirectoryWatcher createNewWatcher(Path path) {
        LocalDirectoryWatcher watcher = new LocalDirectoryWatcher(
                path,
                (eventKind, pathOnChange) -> {
                    if ( eventKind.equals(ENTRY_DELETE) ) {
                        System.out.println("[WATCH]" + pathOnChange.toString() + " " + eventKind);
                        this.changes.removed(pathOnChange);
                    }
                    else if ( eventKind.equals(ENTRY_CREATE) ) {
                        System.out.println("[WATCH]" + pathOnChange.toString() + " " + eventKind);
                        this.toFSEntry(pathOnChange).ifPresent(this.changes::added);
                    }
                },
                LocalDirectoryWatcher.CallbackSynchronization.PER_JVM);

        watcher.startWork();

        return watcher;
    }

    private static void handle(IOException e) {
        printStackTraceFor(e);
    }

    private static void handle(AccessDeniedException e) {
        printStackTraceFor(e);
    }

    private static void printStackTraceFor(IOException e) {
        System.out.println(e);
        for (StackTraceElement element : e.getStackTrace()) {
            if (element.getClassName().startsWith("diarsid")) {
                System.out.println("    " + element);
            }
        }
    }
}
