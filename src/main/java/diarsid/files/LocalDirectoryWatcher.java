package diarsid.files;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.filesystem.api.Directory;
import diarsid.support.concurrency.stateful.workers.AbstractStatefulDestroyableWorker;
import diarsid.support.concurrency.threads.IncrementNamedThreadFactory;
import diarsid.support.objects.CommonEnum;

import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import static diarsid.files.LocalDirectoryWatcher.CallbackSynchronization.NONE;
import static diarsid.support.concurrency.threads.ThreadsUtil.shutdownAndWait;

public class LocalDirectoryWatcher extends AbstractStatefulDestroyableWorker implements PathBearer {

    private static Logger log = LoggerFactory.getLogger(LocalDirectoryWatcher.class);

    public enum CallbackSynchronization implements CommonEnum<CallbackSynchronization> {
        PER_JVM,
        PER_WATCHER,
        NONE
    }

    private static final Object STATIC_CALLBACK_MONITOR = new Object();

    private final Path path;
    private final BiConsumer<WatchEvent.Kind<?>, Path> callback;
    private final ExecutorService async;
    private final CallbackSynchronization sync;
    private final Predicate<Path> filter;
    private WatchService watchService;

    public LocalDirectoryWatcher(
            Directory directory,
            BiConsumer<WatchEvent.Kind<?>, Path> callback) {
        this(directory.path(), callback, NONE);
    }

    public LocalDirectoryWatcher(
            Directory directory,
            BiConsumer<WatchEvent.Kind<?>, Path> callback,
            CallbackSynchronization sync) {
        this(directory.path(), callback, sync);
    }

    public LocalDirectoryWatcher(
            Directory directory,
            BiConsumer<WatchEvent.Kind<?>, Path> callback,
            CallbackSynchronization sync,
            Predicate<Path> filter) {
        this(directory.path(), callback, sync, filter);
    }

    public LocalDirectoryWatcher(
            Path path,
            BiConsumer<WatchEvent.Kind<?>, Path> callback,
            CallbackSynchronization sync) {
        this(path, callback, sync, (testedPath) -> true);
    }

    LocalDirectoryWatcher(
            Path path,
            BiConsumer<WatchEvent.Kind<?>, Path> callback,
            CallbackSynchronization sync,
            Predicate<Path> filter) {
        super(format("path[%s]", path));
        if ( ! Files.isDirectory(path) ) {
            throw new IllegalArgumentException();
        }
        this.path = path;
        this.callback = callback;
        ThreadFactory threadFactory = new IncrementNamedThreadFactory(
                LocalDirectoryWatcher.class.getSimpleName() + "[" + this.path.toString() + "].%s");
        this.async = Executors.newFixedThreadPool(1, threadFactory);;
        this.sync = sync;
        this.filter = filter;
    }

    @Override
    public Path path() {
        return this.path;
    }

    @Override
    protected boolean doSynchronizedStartWork() {
        boolean started;

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.path.register(this.watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            this.async.submit(this::asyncWatching);
            started = true;
        }
        catch (IOException e) {
            e.printStackTrace();
            started = false;
        }
        catch (Exception e) {
            e.printStackTrace();
            started = false;
        }

        return started;
    }

    private void asyncWatching() {
        WatchKey watchKey;
        boolean watchIsActive = true;

        Path filePath;
        Path dir;
        Path path;

        while ( super.isWorkingOrTransitingToWorking() && watchIsActive ) {
            try {
                watchKey = this.watchService.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                continue;
            }

            try {
                List<WatchEvent<?>> watchEventList = watchKey.pollEvents();
                for ( WatchEvent<?> watchEvent : watchEventList ) {
                    filePath = (Path) watchEvent.context();
                    dir = (Path) watchKey.watchable();
                    path = dir.resolve(filePath).toAbsolutePath();

                    if ( this.filter.test(path) ) {
                        switch ( this.sync ) {
                            case PER_JVM:
                                synchronized (STATIC_CALLBACK_MONITOR) {
                                    this.callback.accept(watchEvent.kind(), path);
                                }
                                break;
                            case PER_WATCHER:
                                synchronized ( this ) {
                                    this.callback.accept(watchEvent.kind(), path);
                                }
                                break;
                            case NONE:
                                this.callback.accept(watchEvent.kind(), path);
                                break;
                            default:
                                log.warn(format("%s '%s' in %s[%s] is not supported!",
                                        CallbackSynchronization.class.getSimpleName(),
                                        this.sync,
                                        LocalDirectoryWatcher.class.getSimpleName(),
                                        this.path));
                        }
                    }
                }

                watchIsActive = watchKey.reset();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected boolean doSynchronizedDestroy() {
        try {
            this.watchService.close();
            shutdownAndWait(this.async);
            return true;
        }
        catch (IOException e) {
            log.error("Cannot close OS file watcher", e);
            return false;
        }
    }
}
