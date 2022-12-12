package diarsid.filesystem.impl.local;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.File;
import diarsid.filesystem.api.FileSystem;
import diarsid.support.exceptions.UnsupportedLogicException;
import diarsid.support.objects.references.Result;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import static diarsid.filesystem.api.NoResultReason.PATH_NOT_EXISTS;
import static diarsid.filesystem.api.NoResultReason.PATH_NOT_POSSIBLE;

public class LocalMachineDirectory implements Directory {

    private final String machineName;
    private final Path machinePath;
    private final LocalFileSystem fileSystem;
    private final List<Path> roots;

    LocalMachineDirectory(LocalFileSystem fileSystem, Iterable<Path> rootPaths) {
        this.machineName = getMachineName();
        this.machinePath = Paths.get(this.machineName);
        this.fileSystem = fileSystem;
        this.roots = new ArrayList<>();
        rootPaths.forEach(this.roots::add);
    }

    private static String getMachineName() {
        String machineName = "Local Machine";

        try {
            InetAddress addr;
            addr = InetAddress.getLocalHost();
            machineName = addr.getHostName();
        }
        catch (UnknownHostException ex) {
            ex.printStackTrace();
        }

        return machineName;
    }

    @Override
    public String name() {
        return this.machineName;
    }

    @Override
    public Path path() {
        return this.machinePath;
    }

    @Override
    public void lockAndDo(Runnable toDoInLock) {
        throw new UnsupportedLogicException();
    }

    @Override
    public void showInDefaultFileManager() {
        this.fileSystem.showInDefaultFileManager(this);
    }

    @Override
    public Result<Directory> parent() {
        return Result.empty(PATH_NOT_POSSIBLE);
    }

    @Override
    public Result<FSEntry> toFSEntry(Path path) {
        return this.fileSystem.toFSEntry(path);
    }

    @Override
    public Result<File> file(String name) {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public boolean hasFile(String name) {
        return false;
    }

    @Override
    public boolean hasDirectory(String name) {
        for ( Path root : this.roots ) {
            if ( root.toString().startsWith(name) ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Result<File> fileCreateIfNotExists(String name) {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public Result<Directory> directory(String name) {
        for ( Path root : this.roots ) {
            if ( root.toString().equalsIgnoreCase(name) ) {
                return this.fileSystem.toDirectory(root);
            }
        }
        return Result.empty(PATH_NOT_EXISTS);
    }

    @Override
    public Result<Directory> directoryCreateIfNotExists(String name) {
        Result<Directory> directory = this.directory(name);

        if ( directory.isPresent() ) {
            return directory;
        }

        return Result.empty(PATH_NOT_POSSIBLE);
    }

    @Override
    public long countChildren() {
        return this.roots.size();
    }

    @Override
    public Result<Directory> firstExistingParent() {
        return Result.empty(PATH_NOT_POSSIBLE);
    }

    @Override
    public boolean isIndirectParentOf(FSEntry fsEntry) {
        return true;
    }

    @Override
    public boolean isIndirectParentOf(Path path) {
        return true;
    }

    @Override
    public boolean isDirectParentOf(FSEntry fsEntry) {
        if ( fsEntry.isFile() ) {
            return false;
        }

        return fsEntry.asDirectory().isRoot();
    }

    @Override
    public boolean isDirectParentOf(Path path) {
        return this.roots.contains(path);
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    @Override
    public List<Directory> parents() {
        return emptyList();
    }

    @Override
    public int depth() {
        return 0;
    }

    @Override
    public void checkChildrenPresence(Consumer<Boolean> consumer) {
        consumer.accept(true);
    }

    @Override
    public void checkDirectoriesPresence(Consumer<Boolean> consumer) {
        consumer.accept(true);
    }

    @Override
    public void checkFilesPresence(Consumer<Boolean> consumer) {
        consumer.accept(false);
    }

    @Override
    public void feedChildren(Consumer<List<FSEntry>> consumer) {
        List<FSEntry> entries = this.roots
                .stream()
                .map(this.fileSystem::toLocalFSEntry)
                .sorted()
                .collect(toList());

        consumer.accept(entries);
    }

    @Override
    public void feedChildren(Consumer<List<FSEntry>> consumer, Comparator<FSEntry> comparator) {
        List<FSEntry> entries = this.roots
                .stream()
                .map(this.fileSystem::toLocalFSEntry)
                .sorted(comparator)
                .collect(toList());

        consumer.accept(entries);
    }

    @Override
    public void feedDirectories(Consumer<List<Directory>> consumer) {
        List<Directory> directories = this.roots
                .stream()
                .filter(this.fileSystem::isDirectory)
                .map(this.fileSystem::toLocalDirectory)
                .sorted()
                .collect(toList());

        consumer.accept(directories);
    }

    @Override
    public void feedDirectories(Consumer<List<Directory>> consumer, Comparator<Directory> comparator) {
        List<Directory> directories = this.roots
                .stream()
                .filter(this.fileSystem::isDirectory)
                .map(this.fileSystem::toLocalDirectory)
                .sorted(comparator)
                .collect(toList());

        consumer.accept(directories);
    }

    @Override
    public void feedFiles(Consumer<List<File>> consumer) {
        List<File> files = this.roots
                .stream()
                .filter(this.fileSystem::isFile)
                .map(this.fileSystem::toLocalFile)
                .collect(toList());

        consumer.accept(files);
    }

    @Override
    public void feedFiles(Consumer<List<File>> consumer, Comparator<File> comparator) {
        List<File> files = this.roots
                .stream()
                .filter(this.fileSystem::isFile)
                .map(this.fileSystem::toLocalFile)
                .sorted(comparator)
                .collect(toList());

        consumer.accept(files);
    }

    @Override
    public void host(FSEntry newEntry, Consumer<Boolean> callback) {
        throw new UnsupportedOperationException("This directory is root");
    }

    @Override
    public void hostAll(List<FSEntry> newEntries, Consumer<Boolean> callback, ProgressTrackerBack<FSEntry> progressTracker) {
        throw new UnsupportedOperationException("This directory is root");
    }

    @Override
    public boolean host(FSEntry newEntry) {
        throw new UnsupportedOperationException("This directory is root");
    }

    @Override
    public boolean hostAll(List<FSEntry> newEntries, ProgressTrackerBack<FSEntry> progressTracker) {
        throw new UnsupportedOperationException("This directory is root");
    }

    @Override
    public Result.Void remove(String name) {
        throw new UnsupportedOperationException("This directory is root");
    }

    @Override
    public boolean canBe(Edit edit) {
        return false;
    }

    @Override
    public Result<File> writeAsFile(String fileName, Serializable object) {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public <T> Result<T> readFromFile(String fileName, Class<T> type) {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public Result<Object> readFromFile(String fileName) {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean moveTo(Directory newPlace) {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public boolean remove() {
        throw new UnsupportedOperationException("This directory is machine");
    }

    @Override
    public boolean canBeIgnored() {
        return false;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isAbsent() {
        return false;
    }

    @Override
    public FileSystem fileSystem() {
        return this.fileSystem;
    }

    @Override
    public int compareTo(FSEntry otherFSEntry) {
        return 1;
    }

    @Override
    public void watch() {
        // do nothing
    }

    List<Path> roots() {
        return this.roots;
    }
}
