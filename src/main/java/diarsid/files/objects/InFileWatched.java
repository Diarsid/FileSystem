package diarsid.files.objects;

import java.io.Closeable;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import diarsid.files.LocalFileWatcher;
import diarsid.filesystem.api.Directory;

import static java.util.Objects.isNull;

import static diarsid.files.LocalDirectoryWatcher.CallbackSynchronization.PER_WATCHER;

public class InFileWatched<T> extends InFile<T> implements Closeable {

    private final LocalFileWatcher fileWatcher;
    private final Consumer<T> changeListener;
    private final AtomicReference<T> lastT;

    public InFileWatched(Directory directory, String name, Class<T> type, Consumer<T> changeListener) {
        super(directory, name, type);
        this.changeListener = changeListener;
        this.lastT = new AtomicReference<>(this.read());

        this.fileWatcher = new LocalFileWatcher(
                directory.file(name).orThrow().path(),
                this::acceptAndPropagateChange,
                PER_WATCHER);

        this.fileWatcher.startWork();
    }

    public InFileWatched(Directory directory, String name, Initializer<T> initializer, Consumer<T> changeListener) {
        super(directory, name, initializer);
        this.changeListener = changeListener;
        this.lastT = new AtomicReference<>(this.read());

        this.fileWatcher = new LocalFileWatcher(
                directory.file(name).orThrow().path(),
                this::acceptAndPropagateChange,
                PER_WATCHER);

        this.fileWatcher.startWork();
    }

    public InFileWatched(Path path, Initializer<T> initializer, Consumer<T> changeListener) {
        super(path, initializer);
        this.changeListener = changeListener;
        this.lastT = new AtomicReference<>(this.read());

        this.fileWatcher = new LocalFileWatcher(
                path,
                this::acceptAndPropagateChange,
                PER_WATCHER);

        this.fileWatcher.startWork();
    }

    private void acceptAndPropagateChange(WatchEvent.Kind<?> kind, Path changedPath) {
        T newT = this.read();
        T oldT = this.lastT.getAndSet(newT);

        boolean newTisNull = isNull(newT);
        boolean oldTisNull = isNull(oldT);

        if ( newTisNull && oldTisNull ) {
            return;
        }

        if ( newTisNull ^ oldTisNull ) {
            this.changeListener.accept(newT);
            return;
        }

        if ( newT.equals(oldT) ) {
            return;
        }

        this.changeListener.accept(newT);
    }

    @Override
    public void close() {
        this.fileWatcher.destroy();
    }
}
