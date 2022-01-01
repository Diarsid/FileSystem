package diarsid.files.objectstore;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.stream.Collectors;

import diarsid.files.LocalDirectoryWatcher;
import diarsid.files.objectstore.exceptions.NoStoreDirectoryException;
import diarsid.files.objectstore.exceptions.NoSuchObjectException;
import diarsid.files.objectstore.exceptions.ObjectClassNotMatchesException;
import diarsid.files.objectstore.exceptions.ObjectFileNotReadableException;
import diarsid.files.objectstore.exceptions.ObjectStoreException;
import diarsid.support.concurrency.threads.IncrementNamedThreadFactory;
import diarsid.support.model.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toUnmodifiableList;

import static diarsid.support.concurrency.threads.ThreadsUtil.shutdownAndWait;

public class FileObjectStore<K extends Serializable, T extends Identity<K>> implements ObjectStore<K, T> {

    private static final int MAX_PARALLELISM = 1; // see the ConcurrentHashMap doc for explanation

    private final Path directory;
    private final Path storeLock;
    private final Class<T> tClass;
    private final String tClassSignature;
    private final LocalDirectoryWatcher watcher;
    private final ExecutorService async;
    private final ConcurrentHashMap<UUID, Listener> allListeners;
    private final ConcurrentHashMap<UUID, CreatedListener<K, T>> createdListeners;
    private final ConcurrentHashMap<UUID, RemovedListener> removedListeners;
    private final ConcurrentHashMap<UUID, ChangedListener<K, T>> changedListeners;
    private final Logger log;

    public FileObjectStore(Path directory, Class<T> tClass) {
        if ( ! Files.exists(directory) ) {
            throw new NoStoreDirectoryException();
        }

        if ( ! Files.isDirectory(directory) ) {
            throw new NoStoreDirectoryException();
        }

        this.directory = directory;
        this.tClass = tClass;
        this.tClassSignature = tClass.getCanonicalName();
        this.storeLock = this.directory.resolve(".store." + this.tClassSignature);

        if ( ! Files.exists(this.storeLock) ) {
            try {
                Files.createFile(directory.resolve(this.storeLock));
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
        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
             var storeLock = storeFileChannel.lock()) {
            return this.read(path);
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
    }

    // Дима хуй
    // (c) Юра 01.01.2022

    @SuppressWarnings("unchecked")
    private T read(Path path) {
        try (var fileChannel = FileChannel.open(path, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var objectInputStream = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            T t = (T) objectInputStream.readObject();
            return t;
        }
        catch (NoSuchFileException e) {
            throw new NoSuchObjectException(e);
        }
        catch (StreamCorruptedException e) {
            throw new ObjectFileNotReadableException(this.tClass, path, e);
        }
        catch (InvalidClassException e) {
            throw new ObjectClassNotMatchesException(this.tClass, path, e);
        }
        catch (IOException | ClassNotFoundException e) {
            throw new ObjectStoreException(e);
        }
    }

    @Override
    public synchronized List<T> getAllBy(List<K> keys) {
        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
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
    }

    private T readOrNull(Path path) {
        try {
            return this.read(path);
        } catch (ObjectFileNotReadableException | ObjectClassNotMatchesException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public synchronized List<T> getAll() {
        try (var fileChannel = FileChannel.open(this.storeLock, READ, WRITE);
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
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Optional<T> findBy(K key) {
        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();
             var fileChannel = FileChannel.open(this.filePathOf(key), READ, WRITE);
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
    }

    @Override
    public synchronized void save(T t) {
        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();
             var fileChannel = FileChannel.open(this.filePathOf(t), READ, WRITE, CREATE);
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
    }

    @Override
    public synchronized void saveAll(List<T> list) {
        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
             var storeLock = storeFileChannel.lock()) {

            for ( T t : list ) {
                try (var fileChannel = FileChannel.open(this.filePathOf(t), READ, WRITE, CREATE);
                     var os = Channels.newOutputStream(fileChannel);
                     var objectOutputStream = new ObjectOutputStream(os);
                     var lock = fileChannel.lock()) {
                    objectOutputStream.writeObject(t);
                }
            }
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
    }

    @Override
    public synchronized boolean remove(K key) {
        Path path = this.filePathOf(key);
        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();
             var fileChannel = FileChannel.open(path, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var objectInputStream = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            Files.deleteIfExists(path);
            return true;
        }
        catch (NoSuchFileException e) {
            return false;
        }
        catch (IOException e) {
            throw new ObjectStoreException(e);
        }
    }

    @Override
    public synchronized boolean removeAll(List<K> keys) {
        List<Path> paths = keys
                .stream()
                .map(this::filePathOf)
                .collect(Collectors.toList());

        try (var storeFileChannel = FileChannel.open(this.storeLock, READ, WRITE);
             var storeLock = storeFileChannel.lock();) {

            for ( Path path : paths ) {
                try (var fileChannel = FileChannel.open(path, READ, WRITE);
                     var is = Channels.newInputStream(fileChannel);
                     var objectInputStream = new ObjectInputStream(is);
                     var lock = fileChannel.lock()) {
                    Files.deleteIfExists(path);
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
    }

    @Override
    public synchronized void clear() {
        try (var fileChannel = FileChannel.open(this.storeLock, READ, WRITE);
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
    }

    @Override
    public UUID subscribe(CreatedListener<K, T> listener) {
        UUID key = randomUUID();
        this.createdListeners.put(key, listener);
        return key;
    }

    @Override
    public UUID subscribe(RemovedListener listener) {
        UUID key = randomUUID();
        this.removedListeners.put(key, listener);
        return key;
    }

    @Override
    public UUID subscribe(ChangedListener<K, T> listener) {
        UUID key = randomUUID();
        this.changedListeners.put(key, listener);
        return key;
    }

    @Override
    public boolean unsubscribe(UUID uuid) {
        var listener = this.allListeners.remove(uuid);

        boolean removed = listener != null;

        if ( removed ) {
            boolean removed2 = this.getListenersOf(listener).remove(uuid) != null;

            if ( ! removed2 ) {
                throw new ObjectStoreException();
            }

            try {
                listener.onUnsubscribed();
            }
            catch (Exception e) {
                throw new ObjectStoreException(e);
            }
        }

        return removed;
    }

    private ConcurrentHashMap<UUID, ? extends Listener> getListenersOf(Listener listener) {
        ConcurrentHashMap<UUID, ? extends Listener> listeners = null;
        if ( listener instanceof ChangedListener ) {
            listeners =  this.changedListeners;
        }
        else if ( listener instanceof CreatedListener ) {
            listeners =  this.createdListeners;
        }
        else if ( listener instanceof RemovedListener ) {
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
                changedPath.equals(this.storeLock) ||
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
                this.async.submit(() -> {
                    this.removedListeners.forEachValue(MAX_PARALLELISM, listener -> listener.onRemoved(keyString));
                });
            }
        }
    }
}
