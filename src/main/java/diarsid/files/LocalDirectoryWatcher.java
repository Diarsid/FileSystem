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

import diarsid.support.concurrency.stateful.workers.AbstractStatefulDestroyableWorker;
import diarsid.support.concurrency.threads.IncrementNamedThreadFactory;
import diarsid.support.objects.CommonEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import static diarsid.support.concurrency.threads.ThreadsUtil.shutdownAndWait;

public class LocalDirectoryWatcher extends AbstractStatefulDestroyableWorker {

    private static Logger log = LoggerFactory.getLogger(LocalDirectoryWatcher.class);

    public enum CallbackSynchronization implements CommonEnum<CallbackSynchronization> {
        PER_JVM,
        PER_WATCHER,
        NONE
    }

    private static final Object CALLBACK_MONITOR = new Object();

    private final Path path;
    private final BiConsumer<WatchEvent.Kind<?>, Path> callback;
    private final ExecutorService async;
    private final CallbackSynchronization sync;
    private WatchService watchService;

    public LocalDirectoryWatcher(
            Path path,
            BiConsumer<WatchEvent.Kind<?>, Path> callback,
            CallbackSynchronization sync) {
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
                    Path filePath = (Path) watchEvent.context();
                    Path dir = (Path) watchKey.watchable();
                    Path path = dir.resolve(filePath).toAbsolutePath();

                    switch ( this.sync ) {
                        case PER_JVM:
                            synchronized ( CALLBACK_MONITOR ) {
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
        }
        catch (IOException e) {

        }

        return true;
    }
}
