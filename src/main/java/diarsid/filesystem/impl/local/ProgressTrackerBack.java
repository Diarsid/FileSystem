package diarsid.filesystem.impl.local;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.currentTimeMillis;

public class ProgressTrackerBack<T> {

    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);

    private static final long NOT_MEASURED = -1;

    public static ProgressTrackerBack<FSEntry> DEFAULT = new ProgressTrackerBack<>(
            (all) -> {
                log.info("begin for " + all.size() + " items");
            },
            (fsEntry) -> {
                log.info("...item start : " + fsEntry.path());
            },
            (index, fsEntry) -> {
                log.info("...item done  : " + index + " " + fsEntry.path());
            },
            () ->  {
                log.info("completed");
            });

    private final Consumer<List<T>> onStart;
    private final Consumer<T> onItemStart;
    private final ProgressTracker.ProgressConsumer<T> onItemDone;
    private final Runnable onStop;
    private final AtomicLong done;

    private long startTime;
    private long stopTime;
    private long all;

    public ProgressTrackerBack(
            Consumer<List<T>> onStart,
            Consumer<T> onItemStart,
            ProgressTracker.ProgressConsumer<T> onItemDone,
            Runnable onStop) {
        this.done = new AtomicLong(0);
        this.onStart = onStart;
        this.onItemStart = onItemStart;
        this.onItemDone = onItemDone;
        this.onStop = onStop;
        this.clear();
    }

    void begin(List<T> allT) {
        this.clear();
        this.all = allT.size();
        this.startTime = currentTimeMillis();
        this.onStart.accept(allT);
    }

    void processing(T t) {
        this.onItemStart.accept(t);
    }

    void processingDone(T t) {
        this.onItemDone.accept(this.done.getAndIncrement(), t);
    }

    void completed() {
        this.stopTime = currentTimeMillis();
        this.onStop.run();
    }

    public long all() {
        return this.all;
    }

    public void clear() {
        this.startTime = NOT_MEASURED;
        this.stopTime = NOT_MEASURED;
        this.all = 0;
        this.done.set(0);
    }
}
