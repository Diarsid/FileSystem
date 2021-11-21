package diarsid.filesystem.impl.local;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.FileSystem;
import diarsid.support.callbacks.ValueCallback;
import diarsid.support.callbacks.groups.ActiveCallback;
import diarsid.support.callbacks.groups.AsyncValueCallbacks;
import diarsid.support.concurrency.threads.NamedThreadSource;

import static java.util.Collections.singletonList;

import static diarsid.support.concurrency.ThreadUtils.currentThreadTrack;

public class ChangesImpl implements FileSystem.Changes {

    private final AsyncValueCallbacks<List<FSEntry>> pathsAddedCallbacks;
    private final AsyncValueCallbacks<List<Path>> pathsRemovedCallbacks;

    public ChangesImpl(NamedThreadSource namedThreadSource) {
        Executor executor = namedThreadSource.newNamedCachedThreadPool("filesystem.activities");
        this.pathsAddedCallbacks = new AsyncValueCallbacks<>(executor);
        this.pathsRemovedCallbacks = new AsyncValueCallbacks<>(executor);
    }

    void added(FSEntry entry) {
        currentThreadTrack("diarsid", (element) -> System.out.println("    " + element));
        this.pathsAddedCallbacks.callAndAwait(singletonList(entry));
    }

    void added(List<FSEntry> paths) {
        currentThreadTrack("diarsid", (element) -> System.out.println("    " + element));
        this.pathsAddedCallbacks.callAndAwait(paths);
    }

    void removed(Path path) {
        this.pathsRemovedCallbacks.callAndAwait(singletonList(path));
    }

    void removed(List<Path> paths) {
        this.pathsRemovedCallbacks.callAndAwait(paths);
    }

    @Override
    public ActiveCallback<ValueCallback<List<FSEntry>>> listenForEntriesAdded(ValueCallback<List<FSEntry>> callback) {
        return this.pathsAddedCallbacks.add(callback);
    }

    @Override
    public ActiveCallback<ValueCallback<List<Path>>> listenForEntriesRemoved(ValueCallback<List<Path>> callback) {
        return this.pathsRemovedCallbacks.add(callback);
    }
}
