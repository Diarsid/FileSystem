package diarsid.files.objects.store;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.files.LocalDirectoryWatcher;
import diarsid.files.PathReentrantLock;
import diarsid.files.objects.exceptions.ObjectInFileClassException;
import diarsid.files.objects.exceptions.ObjectInFileNotFoundException;
import diarsid.files.objects.exceptions.ObjectInFileNotReadableException;
import diarsid.files.objects.store.exceptions.NoStoreDirectoryException;
import diarsid.files.objects.store.exceptions.ObjectStoreException;
import diarsid.support.concurrency.threads.IncrementNamedThreadFactory;
import diarsid.support.model.Identity;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.stream.Collectors.toUnmodifiableList;

import static diarsid.support.concurrency.threads.ThreadsUtil.shutdownAndWait;

public class FileObjectStore<K extends Serializable, T extends Identity<K>> implements ObjectStore<K, T> {

    private static final int MAX_PARALLELISM = 1; // see the ConcurrentHashMap doc for explanation

    private final Path directory;
    private final Path storeFileLock;
    private final Map<Path, Lock> access;
    private final Class<T> tClass;
    private final String tClassSignature;
    private final LocalDirectoryWatcher watcher;
    private final ExecutorService async;
    private final ConcurrentHashMap<UUID, Listener> allListeners;
    private final ConcurrentHashMap<UUID, Listener.OnCreated<K, T>> createdListeners;
    private final ConcurrentHashMap<UUID, Listener.OnRemoved> removedListeners;
    private final ConcurrentHashMap<UUID, Listener.OnChanged<K, T>> changedListeners;
    private final Logger log;

    public FileObjectStore(Path directory, Class<T> tClass) {
        if ( ! Files.exists(directory) ) {
            throw new NoStoreDirectoryException();
        }

        if ( ! Files.isDirectory(directory) ) {
            throw new NoStoreDirectoryException();
        }

        this.access = new ConcurrentHashMap<>();

        this.directory = directory;
        this.tClass = tClass;
        this.tClassSignature = tClass.getCanonicalName();
        this.storeFileLock = this.directory.resolve(".store." + this.tClassSignature);

        if ( ! Files.exists(this.storeFileLock) ) {
            try {
                Files.createFile(directory.resolve(this.storeFileLock));
            }
            catch (IOException e) {
                throw new ObjectStoreException(e);
            }
        }

        this.log = LoggerFactory.getLogger(format("%s<%s>", ObjectStore.class.getSimpleName(), this.tClassSignature));

        this.watcher = new LocalDirectoryWatcher(
                this.directory,
                this::transmitChangeToListenersOrSkip,
                LocalDirectoryWatcher.CallbackSynchronization.PER_WATCHER);

        this.watcher.startWork();

        this.allListeners = new ConcurrentHashMap<>();
        this.createdListeners = new ConcurrentHashMap<>();
        this.removedListeners = new ConcurrentHashMap<>();
        this.changedListeners = new ConcurrentHashMap<>();

        ThreadFactory threadFactory = new IncrementNamedThreadFactory(
                FileObjectStore.class.getSimpleName() + "<" + this.tClass.getSimpleName() + ">[" + this.directory.toString() + "].%s");
        this.async = Executors.newFixedThreadPool(1, threadFactory);
    }

    private Lock lockOf(Path path) {
        return this.access.computeIfAbsent(path, (newPath) -> new PathReentrantLock(newPath, true));
    }

    @Override
    public boolean exists(K key) {
        return Files.exists(Paths.get(this.filePathOf(key).toString()));
    }

    private Path filePathOf(T t) {
        return directory.resolve(tClassSignature + "." + t.id().toString());
    }

    private Path filePathOf(K key) {
        return directory.resolve(tClassSignature + "." + key.toString());
    }

    private String keyPartOf(Path path) {
        String fileName = path.getFileName().toString();

        if ( fileName.startsWith(this.tClassSignature) ) {
            return fileName.substring(this.tClassSignature.length() + 1);
        }

        return null;
    }

    private boolean notBelongToStore(Path path) {
        return ! path.getFileName().toString().startsWith(this.tClassSignature);
    }

    @Override
    public synchronized T getBy(K key) {
        Path path = this.filePathOf(key);
        Lock pathAccess = this.lockOf(this.storeFileLock);
        pathAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock()) {
            return this.read(path);
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
        }
    }

    // Дима хуй
    // (c) Юра 01.01.2022

    @SuppressWarnings("unchecked")
    private T read(Path path) {
        Lock pathAccess = this.lockOf(path);
        pathAccess.lock();
        try (var fileChannel = FileChannel.open(path, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var objectInputStream = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            T t = (T) objectInputStream.readObject();
            return t;
        }
        catch (NoSuchFileException e) {
            throw new ObjectInFileNotFoundException(path);
        }
        catch (StreamCorruptedException e) {
            throw new ObjectInFileNotReadableException(this.tClass, path, e);
        }
        catch (InvalidClassException e) {
            throw new ObjectInFileClassException(this.tClass, path, e);
        }
        catch (IOException | ClassNotFoundException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
        }
    }

    @Override
    public synchronized List<T> getAllBy(List<K> keys) {
        Lock pathAccess = this.lockOf(this.storeFileLock);
        pathAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock()) {
            return keys
                    .stream()
                    .map(this::filePathOf)
                    .map(this::readOrNull)
                    .filter(Objects::nonNull)
                    .collect(toUnmodifiableList());
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
        }
    }

    private T readOrNull(Path path) {
        try {
            return this.read(path);
        } catch (ObjectInFileNotReadableException | ObjectInFileClassException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public synchronized List<T> getAll() {
        Lock pathAccess = this.lockOf(this.storeFileLock);
        pathAccess.lock();
        try (var fileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = fileChannel.lock();
             var files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith(this.tClassSignature))
                    .map(this::readOrNull)
                    .filter(Objects::nonNull)
                    .collect(toUnmodifiableList());
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Optional<T> findBy(K key) {
        Path filePath = this.filePathOf(key);

        Lock pathStoreAccess = this.lockOf(this.storeFileLock);
        Lock pathAccess = this.lockOf(filePath);

        pathStoreAccess.lock();
        pathAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();
             var fileChannel = FileChannel.open(filePath, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var objectInputStream = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            T t = (T) objectInputStream.readObject();
            return Optional.of(t);
        }
        catch (NoSuchFileException e) {
            return Optional.empty();
        }
        catch (IOException | ClassNotFoundException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
            pathStoreAccess.unlock();
        }
    }

    @Override
    public synchronized void save(T t) {
        Path filePath = this.filePathOf(t);

        Lock pathStoreAccess = this.lockOf(this.storeFileLock);
        Lock pathAccess = this.lockOf(filePath);

        pathStoreAccess.lock();
        pathAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();
             var fileChannel = FileChannel.open(filePath, READ, WRITE, CREATE);
             var os = Channels.newOutputStream(fileChannel)) {
            var lock = fileChannel.lock();
            var objectOutputStream = new ObjectOutputStream(os);
            objectOutputStream.writeObject(t);
            lock.close();
            objectOutputStream.close();
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
            pathStoreAccess.unlock();
        }
    }

    @Override
    public synchronized void saveAll(List<T> list) {
        Lock pathStoreAccess = this.lockOf(this.storeFileLock);
        pathStoreAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock()) {

            Lock pathAccess;
            Path filePath;
            for ( T t : list ) {
                filePath = this.filePathOf(t);
                pathAccess = this.lockOf(filePath);
                pathAccess.lock();
                try (var fileChannel = FileChannel.open(filePath, READ, WRITE, CREATE);
                     var os = Channels.newOutputStream(fileChannel);
                     var objectOutputStream = new ObjectOutputStream(os);
                     var lock = fileChannel.lock()) {
                    objectOutputStream.writeObject(t);
                }
                finally {
                    pathAccess.unlock();
                }
            }
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathStoreAccess.unlock();
        }
    }

    @Override
    public synchronized boolean remove(K key) {
        Path filePath = this.filePathOf(key);

        Lock pathStoreAccess = this.lockOf(this.storeFileLock);
        Lock pathAccess = this.lockOf(filePath);

        pathStoreAccess.lock();
        pathAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();
             var fileChannel = FileChannel.open(filePath, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var objectInputStream = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            Files.deleteIfExists(filePath);
            return true;
        }
        catch (NoSuchFileException e) {
            return false;
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathAccess.unlock();
            pathStoreAccess.unlock();
        }
    }

    @Override
    public synchronized boolean removeAll(List<K> keys) {
        List<Path> paths = keys
                .stream()
                .map(this::filePathOf)
                .collect(Collectors.toList());

        Lock pathStoreAccess = this.lockOf(this.storeFileLock);
        pathStoreAccess.lock();
        try (var storeFileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();) {

            Lock pathAccess;
            for ( Path path : paths ) {
                pathAccess = this.lockOf(path);
                pathAccess.lock();
                try (var fileChannel = FileChannel.open(path, READ, WRITE);
                     var lock = fileChannel.lock()) {
                    Files.deleteIfExists(path);
                }
                finally {
                    pathAccess.unlock();
                }
            }

            return true;
        }
        catch (NoSuchFileException e) {
            return false;
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathStoreAccess.unlock();
        }
    }

    @Override
    public synchronized void clear() {
        Lock pathStoreAccess = this.lockOf(this.storeFileLock);
        pathStoreAccess.lock();
        try (var fileChannel = FileChannel.open(this.storeFileLock, READ, WRITE);
             var storeLock = fileChannel.lock();
             var files = Files.list(directory)) {
            files
                    .filter(path -> path.getFileName().toString().startsWith(this.tClassSignature))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        }
                        catch (IOException e) {
                            throw new ObjectStoreException(e);
                        }
                    });
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
        finally {
            pathStoreAccess.unlock();
        }
    }

    @Override
    public void subscribe(Listener.OnCreated<K, T> listener) {
        this.createdListeners.put(listener.uuid(), listener);
    }

    @Override
    public void subscribe(Listener.OnRemoved listener) {
        this.removedListeners.put(listener.uuid(), listener);
    }

    @Override
    public void subscribe(Listener.OnChanged<K, T> listener) {
        this.changedListeners.put(listener.uuid(), listener);
    }

    @Override
    public boolean unsubscribe(UUID uuid) {
        var abstractListener = this.allListeners.remove(uuid);

        boolean removed = abstractListener != null;

        if ( removed ) {
            boolean listener = this.getListenersOf(abstractListener).remove(uuid) != null;

            if ( ! listener ) {
                throw new ObjectStoreException();
            }

            try {
                abstractListener.onUnsubscribed();
            }
            catch (Exception e) {
                throw new ObjectStoreException(e);
            }
        }

        return removed;
    }

    private ConcurrentHashMap<UUID, ? extends Listener> getListenersOf(Listener listener) {
        ConcurrentHashMap<UUID, ? extends Listener> listeners = null;
        if ( listener instanceof ObjectStore.Listener.OnChanged) {
            listeners =  this.changedListeners;
        }
        else if ( listener instanceof ObjectStore.Listener.OnCreated) {
            listeners =  this.createdListeners;
        }
        else if ( listener instanceof ObjectStore.Listener.OnRemoved) {
            listeners =  this.removedListeners;
        }

        if ( listeners == null ) {
            throw new ObjectStoreException();
        }

        return listeners;
    }

    @Override
    public void close() throws Exception {
        this.watcher.destroy();
        shutdownAndWait(this.async);
    }

    private void transmitChangeToListenersOrSkip(WatchEvent.Kind changeKind, Path changedPath) {
        boolean skip = changedPath.equals(this.directory) ||
                changedPath.equals(this.storeFileLock) ||
                this.notBelongToStore(changedPath);

        if ( skip ) {
            log.info("skip " + changedPath);
            return;
        }

        this.transmitChangeToListenersSynced(changeKind, changedPath);
    }

    private synchronized void transmitChangeToListenersSynced(WatchEvent.Kind changeKind, Path changedPath) {
        if ( changeKind.equals(ENTRY_MODIFY) ) {
            if ( ! this.changedListeners.isEmpty() ) {
                this.async.submit(() -> {
                    T t;
                    synchronized ( this ) {
                        t = this.read(changedPath);
                    }
                    this.changedListeners.forEachValue(MAX_PARALLELISM, listener -> listener.onChanged(t));
                });
            }
        }
        else if ( changeKind.equals(ENTRY_CREATE) ) {
            if ( ! this.createdListeners.isEmpty() ) {
                this.async.submit(() -> {
                    T t;
                    synchronized ( this ) {
                        t = this.read(changedPath);
                    }
                    this.createdListeners.forEachValue(MAX_PARALLELISM, listener -> listener.onCreated(t));
                });
            }
        }
        else if ( changeKind.equals(ENTRY_DELETE) ) {
            if ( ! this.removedListeners.isEmpty() ) {
                String keyString = this.keyPartOf(changedPath);
                if (keyString != null) {
                    this.async.submit(() -> {
                        this.removedListeners.forEachValue(MAX_PARALLELISM, listener -> listener.onRemoved(keyString));
                    });
                }
            }
        }
    }
}
