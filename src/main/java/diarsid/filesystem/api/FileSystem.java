package diarsid.filesystem.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import diarsid.files.Extensions;
import diarsid.files.PathBearer;
import diarsid.filesystem.api.ignoring.Ignores;
import diarsid.filesystem.impl.local.LocalFileSystem;
import diarsid.filesystem.impl.local.ProgressTrackerBack;
import diarsid.support.callbacks.ValueCallback;
import diarsid.support.callbacks.groups.ActiveCallback;
import diarsid.support.concurrency.threads.NamedThreadSource;
import diarsid.support.objects.references.Result;

import static java.nio.file.FileSystems.getDefault;
import static java.util.Objects.isNull;

public interface FileSystem {

    FileSystem DEFAULT_INSTANCE = new LocalFileSystem(
            Ignores.INSTANCE,
            new NamedThreadSource(LocalFileSystem.class.getSimpleName()), getDefault());

    static FileSystem newInstance(Ignores ignores, NamedThreadSource namedThreadSource, java.nio.file.FileSystem fileSystem) {
        return new LocalFileSystem(ignores, namedThreadSource, fileSystem);
    }

    static String getNameFrom(Path path) {
        String name;
        Path fileName = path.getFileName();
        if (isNull(fileName)) {
            name = path.toString();
        }
        else {
            name = fileName.toString();
        }
        return name;
    }

    interface Changes {

        ActiveCallback<ValueCallback<List<FSEntry>>> listenForEntriesAdded(ValueCallback<List<FSEntry>> callback);

        ActiveCallback<ValueCallback<List<Path>>> listenForEntriesRemoved(ValueCallback<List<Path>> callback);
    }

    Directory machineDirectory();

    Result<FSEntry> toFSEntry(Path path);

    default Result<Directory> toDirectory(String path) {
        return this.toDirectory(Paths.get(path));
    }

    Result<Directory> toDirectory(Path path);

    Result<Directory> toDirectoryCreateIfNotExists(Path path);

    default Result<Directory> toDirectoryCreateIfNotExists(String path) {
        return this.toDirectoryCreateIfNotExists(Paths.get(path));
    }

    Result<File> toFile(Path path);

    Result<File> toFileCreateIfNotExists(Path path);

    default Result<File> toFileCreateIfNotExists(String path) {
        return this.toFileCreateIfNotExists(Paths.get(path));
    }

    default Result<File> toFile(String path) {
        return this.toFile(Paths.get(path));
    }

    boolean isDirectory(Path path);

    boolean isFile(Path path);

    boolean exists(FSEntry fsEntry);

    default boolean isAbsent(FSEntry fsEntry) {
        return ! this.exists(fsEntry);
    }

    boolean copy(FSEntry whatToCopy, Directory parentDirectoryWhereToMove);

    boolean move(FSEntry whatToMove, Directory parentDirectoryWhereToMove);

    boolean rename(FSEntry whatToRename, String newName);

    boolean remove(FSEntry entry);

    boolean remove(Path path);

    boolean copyAll(
            List<FSEntry> whatToCopy,
            Directory parentDirectoryWhereToMove,
            ProgressTrackerBack<FSEntry> progressTracker);

    boolean moveAll(
            List<FSEntry> whatToMove,
            Directory parentDirectoryWhereToMove,
            ProgressTrackerBack<FSEntry> progressTracker);

    boolean removeAll(
            List<FSEntry> entries,
            ProgressTrackerBack<FSEntry> progressTracker);

    boolean open(File file);

    void showInDefaultFileManager(FSEntry fsEntry);

    Stream<FSEntry> list(Directory directory); /* do not forget to close the stream! */

    Result<Directory> parentOf(FSEntry fsEntry);

    Result<Directory> firstExistingParentOf(Path path);

    List<Directory> parentsOf(FSEntry fsEntry);

    List<Directory> parentsOf(Path path);

    long sizeOf(FSEntry fsEntry);

    Extensions extensions();

    boolean isRoot(Directory directory);

    boolean isMachine(Directory directory);

    default boolean isNotMachine(Directory directory) {
        return ! this.isMachine(directory);
    }

    FileSystemType type();

    Changes changes();

    void watch(Directory directory);

    Result<LocalDateTime> creationTimeOf(Path path);

    default Result<LocalDateTime> creationTimeOf(PathBearer pathBearer) {
        return this.creationTimeOf(pathBearer.path());
    }

    Result<LocalDateTime> modificationTimeOf(Path path);

    default Result<LocalDateTime> modificationTimeOf(PathBearer pathBearer) {
        return this.modificationTimeOf(pathBearer.path());
    }
}
