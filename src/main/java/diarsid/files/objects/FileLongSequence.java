package diarsid.files.objects;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import diarsid.files.PathBearer;
import diarsid.files.PathReentrantLock;
import diarsid.files.objects.exceptions.ObjectInFileException;
import diarsid.support.objects.references.Possible;
import diarsid.support.objects.references.References;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class FileLongSequence implements LongSupplier, PathBearer {

    private static final Logger log = LoggerFactory.getLogger(FileLongSequence.class);

    private static final long VALUE_NOT_SET = -1;
    private static final long DEFAULT_INITIAL_VALUE = 0;
    private static final long DEFAULT_INCREMENT_STEP = 1;

    private final Path path;
    private final Lock incrementing;
    private final long initialValue;
    private final long incrementStep;
    private final Possible<Supplier<Long>> restore;
    private volatile long valueCopy;

    public FileLongSequence(Path path) {
        this(path, DEFAULT_INITIAL_VALUE, DEFAULT_INCREMENT_STEP, null);
    }

    public FileLongSequence(String path) {
        this(Paths.get(path));
    }

    public FileLongSequence(Path path, long initialValue, long incrementStep, Supplier<Long> restore) {
        this.path = path;
        this.initialValue = initialValue;
        this.valueCopy = initialValue;
        this.incrementStep = incrementStep;
        this.incrementing = new PathReentrantLock(this.path, true);
        this.restore = References.simplePossibleWith(restore);
        this.createIfNotExists();
    }

    @Override
    public Path path() {
        return this.path;
    }

    public void createIfNotExists() {
        this.incrementing.lock();
        try {
            this.createFileIfNotExistsAndWriteInitial();
        }
        finally {
            this.incrementing.unlock();
        }
    }

    @Override
    public long getAsLong() {
        return this.get();
    }

    public long get() {
        long value = VALUE_NOT_SET;

        this.incrementing.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            boolean requiresRestore = false;
            try {
                InputStream is = Channels.newInputStream(fileChannel);
                ObjectInputStream ois = new ObjectInputStream(is);
                value = ois.readLong();
            }
            catch (EOFException e) {
                log.error("sequence value of {} is lost, trying to restore...", this.path);
                requiresRestore = true;
            }
            catch (StreamCorruptedException e) {
                log.error("sequence {} is corrupted, trying to restore...", this.path);
                requiresRestore = true;
            }

            if ( requiresRestore ) {
                long restoringValue = tryRestoreCurrentValueOrGetRuntimeCopy();

                fileChannel.truncate(0);

                OutputStream os = Channels.newOutputStream(fileChannel);
                ObjectOutputStream oos = new ObjectOutputStream(os);
                oos.writeLong(restoringValue);
                oos.flush();

                value = restoringValue;
            }

            this.valueCopy = value;

            return value;
        }
        catch (NoSuchFileException e) {
            return this.createFileIfNotExistsAndGet();
        }
        catch (Exception e) {
            log.error("", e);
            return value;
        }
        finally {
            this.incrementing.unlock();
        }
    }

    public long getAndIncrement() {
        long value = VALUE_NOT_SET;

        this.incrementing.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            try {
                InputStream is = Channels.newInputStream(fileChannel);
                ObjectInputStream ois = new ObjectInputStream(is);
                value = ois.readLong();
            }
            catch (EOFException e) {
                log.error("sequence value of {} is lost, trying to restore...", this.path);
                value = this.tryRestoreCurrentValueOrGetRuntimeCopy();
            }
            catch (StreamCorruptedException e) {
                log.error("sequence {} is corrupted, trying to restore...", this.path);
                value = this.tryRestoreCurrentValueOrGetRuntimeCopy();
            }

            long valueI = value + this.incrementStep;

            fileChannel.truncate(0);

            OutputStream os = Channels.newOutputStream(fileChannel);
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeLong(valueI);
            oos.flush();

            this.valueCopy = valueI;

            return value;
        }
        catch (NoSuchFileException e) {
            return this.createFileIfNotExistsAndGetAndIncrement();
        }
        catch (Exception e) {
            log.error("", e);
            return value;
        }
        finally {
            this.incrementing.unlock();
        }
    }

    private long tryRestoreCurrentValueOrGetRuntimeCopy() {
        long value;

        if ( this.restore.isPresent() ) {
            try {
                value = this.restore.orThrow().get();
                return value;
            }
            catch (Exception e) {
                log.warn(format("Cannot restore value of sequence %s using given restoration due to %s, fallback to copied value", this.path, e.getMessage()), e);
            }
        }

        value = this.valueCopy;

        return value;
    }

    private long createFileIfNotExistsAndGetAndIncrement() {
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
             var os = Channels.newOutputStream(fileChannel);
             var objectOutputStream = new ObjectOutputStream(os);
             var lock = fileChannel.lock()) {

            long oldCurrentValue = this.tryRestoreCurrentValueOrGetRuntimeCopy();
            long newCurrentValue = oldCurrentValue + this.incrementStep;
            objectOutputStream.writeLong(newCurrentValue);
            this.valueCopy = newCurrentValue;
            log.info("Sequence file '{}' does not exist, created with incremented value: {}", this.path, newCurrentValue);
            return oldCurrentValue;
        }
        catch (FileAlreadyExistsException e) {
            return this.getAndIncrement();
        }
        catch (IOException e) {
            throw new ObjectInFileException(e);
        }
    }

    private long createFileIfNotExistsAndGet() {
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
             var os = Channels.newOutputStream(fileChannel);
             var objectOutputStream = new ObjectOutputStream(os);
             var lock = fileChannel.lock()) {

            long oldCurrentValue = this.tryRestoreCurrentValueOrGetRuntimeCopy();
            objectOutputStream.writeLong(oldCurrentValue);
            this.valueCopy = oldCurrentValue;
            log.info("Sequence file '{}' does not exist, created with current value: {}", this.path, oldCurrentValue);
            return oldCurrentValue;
        }
        catch (FileAlreadyExistsException e) {
            return this.get();
        }
        catch (IOException e) {
            throw new ObjectInFileException(e);
        }
    }

    private void createFileIfNotExistsAndWriteInitial() {
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
             var os = Channels.newOutputStream(fileChannel);
             var objectOutputStream = new ObjectOutputStream(os);
             var lock = fileChannel.lock()) {

            objectOutputStream.writeLong(this.initialValue);
            this.valueCopy = this.initialValue;
            log.info("Sequence file '{}' does not exist, created with initial value: {}", this.path, this.initialValue);
        }
        catch (FileAlreadyExistsException e) {
            log.info("Sequence file '{}' exists", path);
        }
        catch (IOException e) {
            throw new ObjectInFileException(e);
        }
    }

}
