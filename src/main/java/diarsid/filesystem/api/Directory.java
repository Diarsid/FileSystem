package diarsid.filesystem.api;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import diarsid.filesystem.impl.local.LocalMachineDirectory;
import diarsid.filesystem.impl.local.ProgressTrackerBack;
import diarsid.support.objects.references.Result;

public interface Directory extends FSEntry {

    enum Edit {
        MOVED,
        DELETED,
        RENAMED,
        FILLED
    }

    Result<Directory> parent();

    Result<FSEntry> toFSEntry(Path path);

    default Result<FSEntry> toFSEntry(String path) {
        return this.toFSEntry(Paths.get(path));
    }

    Result<File> file(String name);

    Result<File> fileCreateIfNotExists(String name);

    Result<Directory> directory(String name);

    Result<Directory> directoryCreateIfNotExists(String name);

    boolean hasFile(String name);

    boolean hasDirectory(String name);

    long countChildren();

    default boolean isParentOf(FSEntry fsEntry) {
        return this.isIndirectParentOf(fsEntry) || this.isDirectParentOf(fsEntry);
    }

    boolean isIndirectParentOf(FSEntry fsEntry);

    boolean isIndirectParentOf(Path path);

    boolean isDirectParentOf(FSEntry fsEntry);

    boolean isDirectParentOf(Path path);

    boolean isRoot();

    default boolean isNotRoot() {
        return ! this.isRoot();
    }

    void checkChildrenPresence(Consumer<Boolean> consumer);

    void checkDirectoriesPresence(Consumer<Boolean> consumer);

    void checkFilesPresence(Consumer<Boolean> consumer);

    void feedChildren(Consumer<List<FSEntry>> itemsConsumer);

    void feedChildren(Consumer<List<FSEntry>> itemsConsumer, Comparator<FSEntry> comparator);

    void feedDirectories(Consumer<List<Directory>> directoriesConsumer);

    void feedDirectories(Consumer<List<Directory>> directoriesConsumer, Comparator<Directory> comparator);

    void feedFiles(Consumer<List<File>> filesConsumer);

    void feedFiles(Consumer<List<File>> filesConsumer, Comparator<File> comparator);

    void host(FSEntry newEntry, Consumer<Boolean> callback);

    void hostAll(List<FSEntry> newEntries, Consumer<Boolean> callback, ProgressTrackerBack<FSEntry> progressTracker);

    boolean host(FSEntry newEntry);

    boolean hostAll(List<FSEntry> newEntries, ProgressTrackerBack<FSEntry> progressTracker);

    Result.Void remove(String name);

    default boolean canHost(FSEntry newEntry) {
        if ( this instanceof LocalMachineDirectory) {
            return false;
        }

        if ( newEntry.isFile() ) {
            return true;
        }

        boolean can = true;
        Directory newDirectory = newEntry.asDirectory();

        if ( newDirectory.equals(this) ) {
            can = false;
        }

        if ( can && newDirectory.isIndirectParentOf(this) ) {
            can = false;
        }

        return can;
    }

    default boolean canNotHost(FSEntry newEntry) {
        return ! this.canHost(newEntry);
    }

    boolean canBe(Edit edit);

    default boolean canNotBe(Edit edit) {
        return ! this.canBe(edit);
    }

    Result<File> writeAsFile(String fileName, Serializable object);

    <T> Result<T> readFromFile(String fileName, Class<T> type);

    Result<Object> readFromFile(String fileName);

    void watch();

}
