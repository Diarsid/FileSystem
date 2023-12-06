package diarsid.filesystem.impl.local;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import diarsid.files.PathReentrantLock;
import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.File;
import diarsid.filesystem.api.FileSystem;
import diarsid.support.objects.references.Result;

import static java.nio.file.Files.list;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import static diarsid.filesystem.api.FileSystem.getNameFrom;
import static diarsid.filesystem.api.NoResultReason.FILE_CONTENT_CLASS_NOT_READABLE;
import static diarsid.filesystem.api.NoResultReason.FILE_CREATION_COLLISION;
import static diarsid.filesystem.api.NoResultReason.PATH_NOT_EXISTS;

class LocalDirectory implements Directory, ChangeableFSEntry {

    private final FileSystem fileSystem;

    private final Path path;
    private final String name;
    private final String fullName;

    LocalDirectory(Path path, FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        if ( path.isAbsolute() ) {
            this.path = path;
        }
        else {
            this.path = path.toAbsolutePath();
        }
        this.name = getNameFrom(path);
        this.fullName = this.path.toString();
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public Path path() {
        return this.path;
    }

    @Override
    public void lockAndDo(Runnable toDoInLock) {
        boolean locked = false;
        boolean notBroken = true;

        Path lockFile = path.resolve(".lock");
        Lock access = PathReentrantLock.of(lockFile, true);
        access.lock();
        try {
            while ( ! locked && notBroken ) {
                try (var fileChannel = FileChannel.open(lockFile, READ, WRITE);
                     var lock = fileChannel.lock()) {
                    locked = true;

                    try {
                        toDoInLock.run();
                    }
                    catch (Exception e) {
                        notBroken = false;
                        LoggerFactory.getLogger(LocalFile.class).error("Exception during toDoInLock while holding lock of file: " + this.path, e);
                    }
                }
                catch (NoSuchFileException e) {
                    try (var fileChannel = FileChannel.open(lockFile, READ, WRITE, CREATE_NEW);
                         var lock = fileChannel.lock()) {
                        locked = true;

                        try {
                            toDoInLock.run();
                        }
                        catch (Exception eOfRun) {
                            notBroken = false;
                            LoggerFactory.getLogger(LocalFile.class).error("Exception during toDoInLock while holding lock of file: " + this.path, e);
                        }
                    }
                    catch (FileAlreadyExistsException e2) {
                        // just ignore and go to next iteration
                    }
                    catch (IOException e2) {
                        notBroken = false;
                        LoggerFactory.getLogger(LocalFile.class).error("Cannot lock or read file: " + this.path, e);
                    }
                }
                catch (IOException e) {
                    notBroken = false;
                    LoggerFactory.getLogger(LocalFile.class).error("Cannot lock or read file: " + this.path, e);
                }
            }
        }
        finally {
            try {
                Files.deleteIfExists(lockFile);
            }
            catch (IOException e) {
                LoggerFactory.getLogger(LocalFile.class).error("Cannot remove a lock file of: " + this.path, e);
            }
            access.unlock();
        }
    }

    @Override
    public void showInDefaultFileManager() {
        this.fileSystem.showInDefaultFileManager(this);
    }

    @Override
    public Result<Directory> parent() {
        return this.fileSystem.parentOf(this);
    }

    @Override
    public Result<FSEntry> toFSEntry(Path path) {
        return this.fileSystem.toFSEntry(this.path.resolve(path));
    }

    @Override
    public Result<File> file(String name) {
        return this.fileSystem.toFile(this.path.resolve(name));
    }

    @Override
    public boolean hasFile(String name) {
        Path namePath = this.path.resolve(name);
        return Files.exists(namePath) && Files.isRegularFile(namePath);
    }

    @Override
    public boolean hasDirectory(String name) {
        Path namePath = this.path.resolve(name);
        return Files.exists(namePath) && Files.isDirectory(namePath);
    }

    @Override
    public Result<File> fileCreateIfNotExists(String name) {
        return this.fileSystem.toFileCreateIfNotExists(this.path.resolve(name));
    }

    @Override
    public Result<Directory> directory(String name) {
        return this.fileSystem.toDirectory(this.path.resolve(name));
    }

    @Override
    public Result<Directory> directoryCreateIfNotExists(String name) {
        return this.fileSystem.toDirectoryCreateIfNotExists(this.path.resolve(name));
    }

    @Override
    public long countChildren() {
        try ( var stream = this.fileSystem.list(this) ) {
            return stream.count();
        }
    }

    @Override
    public Result<Directory> firstExistingParent() {
        return this.fileSystem.firstExistingParentOf(this.path);
    }

    @Override
    public boolean isIndirectParentOf(FSEntry fsEntry) {
        return fsEntry.path().startsWith(this.path) && ( ! this.equals(fsEntry) );
    }

    @Override
    public boolean isIndirectParentOf(Path path) {
        return path.startsWith(this.path) && ( ! this.path.equals(path) );
    }

    @Override
    public boolean isDirectParentOf(FSEntry fsEntry) {
        Path entryParent = fsEntry.path().getParent();

        if ( isNull(entryParent) ) {
            return false;
        }

        return entryParent.equals(this.path);
    }

    @Override
    public boolean isDirectParentOf(Path path) {
        Path entryParent = path.getParent();

        if ( isNull(entryParent) ) {
            return false;
        }

        return entryParent.equals(this.path);
    }

    @Override
    public boolean isRoot() {
        return this.fileSystem.isRoot(this);
    }

    @Override
    public List<Directory> parents() {
        return this.fileSystem.parentsOf(this);
    }

    @Override
    public int depth() {
        return this.path.getNameCount();
    }

    @Override
    public void checkChildrenPresence(Consumer<Boolean> consumer) {
        try (Stream<Path> pathsStream = list(this.path)) {
            consumer.accept(pathsStream.count() > 0);
        }
        catch (AccessDeniedException denied) {
            consumer.accept(false);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void checkDirectoriesPresence(Consumer<Boolean> consumer) {
        try (Stream<Path> pathsStream = list(this.path)) {
            consumer.accept(pathsStream.anyMatch(this.fileSystem::isDirectory));
        }
        catch (AccessDeniedException denied) {
            consumer.accept(false);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void checkFilesPresence(Consumer<Boolean> consumer) {
        try (Stream<Path> pathsStream = list(this.path)) {
            consumer.accept(pathsStream.anyMatch(this.fileSystem::isFile));
        }
        catch (AccessDeniedException denied) {
            consumer.accept(false);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void feedChildren(Consumer<List<FSEntry>> consumer) {
        try ( Stream<FSEntry> entriesStream = this.fileSystem.list(this) ) {
            List<FSEntry> entries = entriesStream
                    .sorted()
                    .collect(toList());

            consumer.accept(entries);
        }
    }

    @Override
    public void feedChildren(Consumer<List<FSEntry>> consumer, Comparator<FSEntry> comparator) {
        try ( Stream<FSEntry> entriesStream = this.fileSystem.list(this) ) {
            List<FSEntry> entries = entriesStream
                    .sorted(comparator)
                    .collect(toList());

            consumer.accept(entries);
        }
    }

    @Override
    public void feedDirectories(Consumer<List<Directory>> consumer) {
        try ( Stream<FSEntry> entriesStream = this.fileSystem.list(this) ) {
            List<Directory> directories = entriesStream
                    .filter(FSEntry::isDirectory)
                    .map(FSEntry::asDirectory)
                    .sorted()
                    .collect(toList());

            consumer.accept(directories);
        }
    }

    @Override
    public void feedDirectories(Consumer<List<Directory>> consumer, Comparator<Directory> comparator) {
        try ( Stream<FSEntry> entriesStream = this.fileSystem.list(this) ) {
            List<Directory> directories = entriesStream
                    .filter(FSEntry::isDirectory)
                    .map(FSEntry::asDirectory)
                    .sorted(comparator)
                    .collect(toList());

            consumer.accept(directories);
        }
    }

    @Override
    public void feedFiles(Consumer<List<File>> consumer) {
        try ( Stream<FSEntry> entriesStream = this.fileSystem.list(this) ) {
            List<File> files = entriesStream
                    .filter(FSEntry::isFile)
                    .map(FSEntry::asFile)
                    .sorted()
                    .collect(toList());

            consumer.accept(files);
        }
    }

    @Override
    public void feedFiles(Consumer<List<File>> consumer, Comparator<File> comparator) {
        try ( Stream<FSEntry> entriesStream = this.fileSystem.list(this) ) {
            List<File> files = entriesStream
                    .filter(FSEntry::isFile)
                    .map(FSEntry::asFile)
                    .sorted(comparator)
                    .collect(toList());

            consumer.accept(files);
        }
    }

    @Override
    public void host(FSEntry newEntry, Consumer<Boolean> callback) {
        boolean result = this.fileSystem.move(newEntry, this);
        callback.accept(result);
    }

    @Override
    public void hostAll(
            List<FSEntry> newEntries,
            Consumer<Boolean> callback,
            ProgressTrackerBack<FSEntry> progressTracker) {
        boolean result = this.fileSystem.moveAll(newEntries, this, progressTracker);
        callback.accept(result);
    }

    @Override
    public boolean host(FSEntry newEntry) {
        return this.fileSystem.move(newEntry, this);
    }

    @Override
    public boolean hostAll(List<FSEntry> newEntries, ProgressTrackerBack<FSEntry> progressTracker) {
        return this.fileSystem.moveAll(newEntries, this, progressTracker);
    }

    @Override
    public Result.Void remove(String name) {
        Path pathToRemove = this.path.resolve(name);
        boolean success = this.fileSystem.remove(pathToRemove);

        if ( success ) {
            return Result.Void.success();
        }
        else {
            return Result.Void.fail("Cannot remove " + pathToRemove);
        }
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
        try {
            return Files.isHidden(this.path);
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean moveTo(Directory newPlace) {
        return this.fileSystem.move(this, newPlace);
    }

    @Override
    public boolean remove() {
        return this.fileSystem.remove(this);
    }

    @Override
    public boolean canBeIgnored() {
        return nonNull(this.path.getParent());
    }

    @Override
    public boolean exists() {
        return this.fileSystem.exists(this);
    }

    @Override
    public boolean isAbsent() {
        return this.fileSystem.isAbsent(this);
    }

    @Override
    public FileSystem fileSystem() {
        return this.fileSystem;
    }

    @Override
    public boolean canBe(Edit edit) {
        switch ( edit ) {
            case MOVED:
            case DELETED:
            case RENAMED:
                return ! this.fileSystem.isRoot(this);
            case FILLED: return true;
            default: return false;
        }
    }

    @Override
    public Result<File> writeAsFile(String fileName, Serializable object) {
        Path file = this.path.resolve(fileName);
        Lock access = PathReentrantLock.of(file, true);
        access.lock();
        try (var fileChannel = FileChannel.open(file, READ, WRITE);
             var lock = fileChannel.lock()) {

            fileChannel.truncate(0);
            var os = Channels.newOutputStream(fileChannel);
            var oos = new ObjectOutputStream(os);
            oos.writeObject(object);
            oos.flush();

            return this.file(fileName);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(file, READ, WRITE, CREATE_NEW);
                 var lock = fileChannel.lock()) {

                var os = Channels.newOutputStream(fileChannel);
                var oos = new ObjectOutputStream(os);

                oos.writeObject(object);
                oos.flush();

                return this.file(fileName);
            }
            catch (FileAlreadyExistsException eOnWrite) {
                return Result.empty(FILE_CREATION_COLLISION);
            }
            catch (IOException eOnWrite) {
                return Result.empty(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            return Result.empty(eOnWrite);
        }
        finally {
            access.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Result<T> readFromFile(String fileName, Class<T> type) {
        Path file = this.path.resolve(fileName);
        Lock access = PathReentrantLock.of(file, true);
        access.lock();
        try (var fileChannel = FileChannel.open(file, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            return Result.completed((T) ois.readObject());
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            return Result.empty(FILE_CONTENT_CLASS_NOT_READABLE);
        }
        catch (NoSuchFileException e) {
            return Result.empty(PATH_NOT_EXISTS);
        }
        catch (IOException e) {
            return Result.empty(e);
        }
        finally {
            access.unlock();
        }
    }

    @Override
    public Result<Object> readFromFile(String fileName) {
        Path file = this.path.resolve(fileName);
        Lock access = PathReentrantLock.of(file, true);
        access.lock();
        try (var fileChannel = FileChannel.open(file, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            return Result.completed(ois.readObject());
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            return Result.empty(FILE_CONTENT_CLASS_NOT_READABLE);
        }
        catch (NoSuchFileException e) {
            return Result.empty(PATH_NOT_EXISTS);
        }
        catch (IOException e) {
            return Result.empty(e);
        }
        finally {
            access.unlock();
        }
    }

    @Override
    public void watch() {
        this.fileSystem.watch(this);
    }

    @Override
    public int compareTo(FSEntry otherFSEntry) {
        if ( otherFSEntry.isFile() ) {
            return -1;
        }
        else {
            return this.name.compareToIgnoreCase(otherFSEntry.name());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalDirectory)) return false;
        LocalDirectory that = (LocalDirectory) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return "LocalDirectory{" +
                "fullName='" + fullName + '\'' +
                '}';
    }
}
