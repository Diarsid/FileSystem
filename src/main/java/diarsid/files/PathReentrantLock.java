package diarsid.files;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PathReentrantLock implements Lock, PathBearer {

    private static final Map<Path, Lock> LOCKS_BY_PATH = new ConcurrentHashMap<>();

    private final Path path;
    private final Lock delegate;

    public PathReentrantLock(Path path, boolean fairness) {
        this.path = path;
        this.delegate = of(path, fairness);
    }

    public static Lock of(Path path, boolean fairness) {
        return LOCKS_BY_PATH.computeIfAbsent(path, (newPath) -> new ReentrantLock(fairness));
    }

    @Override
    public Path path() {
        return this.path;
    }

    @Override
    public void lock() {
        this.delegate.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        this.delegate.lockInterruptibly();
    }

    @Override
    public boolean tryLock() {
        return this.delegate.tryLock();
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return this.delegate.tryLock(time, unit);
    }

    @Override
    public void unlock() {
        this.delegate.unlock();
    }

    @Override
    public Condition newCondition() {
        return this.delegate.newCondition();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathReentrantLock)) return false;
        PathReentrantLock that = (PathReentrantLock) o;
        return path.equals(that.path) &&
                delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, delegate);
    }
}
