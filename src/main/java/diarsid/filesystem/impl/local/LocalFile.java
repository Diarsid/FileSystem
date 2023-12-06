package diarsid.filesystem.impl.local;

import java.io.IOException;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

import org.slf4j.LoggerFactory;

import diarsid.files.Extension;
import diarsid.files.PathReentrantLock;
import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.File;
import diarsid.filesystem.api.FileSystem;
import diarsid.support.objects.references.Result;

import static java.nio.file.StandardOpenOption.READ;
import static java.util.Objects.nonNull;

import static diarsid.filesystem.api.NoResultReason.PATH_NOT_EXISTS;

class LocalFile implements File, ChangeableFSEntry {

    private final FileSystem fileSystem;

    private final Path path;
    private final String name;

    LocalFile(Path path, FileSystem fileSystem) {
        if ( path.isAbsolute() ) {
            this.path = path;
        }
        else {
            this.path = path.toAbsolutePath();
        }
        this.name = this.path.getFileName().toString();
        this.fileSystem = fileSystem;
        fileSystem.isFile(this.path);
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
    public void showInDefaultFileManager() {
        this.fileSystem.showInDefaultFileManager(this);
    }

    @Override
    public Result<Directory> parent() {
        Path parent = this.path.getParent();
        if ( nonNull(parent) ) {
            return this.fileSystem.toDirectory(parent);
        }
        else {
            return Result.empty(PATH_NOT_EXISTS);
        }
    }

    @Override
    public Result<Directory> firstExistingParent() {
        return this.fileSystem.firstExistingParentOf(this.path);
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
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isFile() {
        return true;
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
        return true;
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
    public long size() {
        return this.fileSystem.sizeOf(this);
    }

    @Override
    public int compareTo(FSEntry otherFSEntry) {
        if ( otherFSEntry.isDirectory() ) {
            return 1;
        }
        else {
            return this.name.compareToIgnoreCase(otherFSEntry.name());
        }
    }

    @Override
    public Optional<Extension> extension() {
        return this.fileSystem.extensions().getFor(this);
    }

    @Override
    public Directory directory() {
        return this.parent().orThrow();
    }

    @Override
    public void open() {
        this.fileSystem.open(this);
    }

    @Override
    public <T> Result<T> readAs(Class<T> type) {
        return this.directory().readFromFile(this.name, type);
    }

    @Override
    public Result<Object> read() {
        return this.directory().readFromFile(this.name);
    }

    @Override
    public void write(Serializable object) {
        this.directory().writeAsFile(this.name, object);
    }

    @Override
    public void lockAndDo(Runnable toDoInLock) {
        Lock access = PathReentrantLock.of(path, true);
        access.lock();
        try (var fileChannel = FileChannel.open(path, READ);
             var lock = fileChannel.lock()) {

            try {
                toDoInLock.run();
            }
            catch (Exception e) {
                LoggerFactory.getLogger(LocalFile.class).error("Exception during toDoInLock while holding lock of file: " + this.path, e);
            }
        }
        catch (Exception e) {
            LoggerFactory.getLogger(LocalFile.class).error("Cannot lock or read file: " + this.path, e);
        }
        finally {
            access.unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalFile)) return false;
        LocalFile localFile = (LocalFile) o;
        return path.equals(localFile.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public LocalDateTime createdAt() {
        return this.fileSystem.creationTimeOf(this).orThrow();
    }

    @Override
    public LocalDateTime actualAt() {
        return this.fileSystem.modificationTimeOf(this).orThrow();
    }

    @Override
    public Result<LocalDateTime> creationTime() {
        return this.fileSystem.creationTimeOf(this);
    }

    @Override
    public Result<LocalDateTime> modificationTime() {
        return this.fileSystem.modificationTimeOf(this);
    }
}
