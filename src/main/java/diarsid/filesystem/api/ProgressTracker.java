package diarsid.filesystem.api;

import java.util.List;
import java.util.function.Consumer;

import diarsid.filesystem.impl.local.ProgressTrackerBack;

public class ProgressTracker<T> extends ProgressTrackerBack<T> {

    public interface ProgressConsumer<T> {
        void accept(long progressIndex, T item);
    }

    public ProgressTracker(
            Consumer<List<T>> onStart,
            Consumer<T> onItemStart,
            ProgressConsumer<T> onItemDone,
            Runnable onStop) {
        super(onStart, onItemStart, onItemDone, onStop);
    }
}
