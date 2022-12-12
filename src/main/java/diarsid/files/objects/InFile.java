package diarsid.files.objects;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import diarsid.files.PathBearer;
import diarsid.files.PathReentrantLock;
import diarsid.files.objects.exceptions.ObjectInFileClassException;
import diarsid.files.objects.exceptions.ObjectInFileCreateCollisionException;
import diarsid.files.objects.exceptions.ObjectInFileException;
import diarsid.filesystem.api.Directory;
import diarsid.support.model.CreatedAt;
import diarsid.support.model.Identity;
import diarsid.support.model.Named;
import diarsid.support.model.UpdatedAt;
import diarsid.support.objects.references.Reference;
import diarsid.support.objects.references.References;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import static diarsid.support.objects.references.Reference.Type.VALUE;
import static diarsid.support.objects.references.Reference.ValuePresence.NULLABLE;

public class InFile<T>
        implements
            Identity<String>,
            Named,
            CreatedAt,
            UpdatedAt,
            Reference.Writable.Nullable<T>,
            PathBearer {

    public interface Initializer<T> extends Supplier<Class<T>> {

        Class<T> type();

        default T onFileCreatedGetInitial() {
            return null;
        }

        default void onFileAlreadyExists(T existingT) {
            // nothing
        }

        @Override
        default Class<T> get() {
            return this.type();
        }
    }

    private final Path path;
    private final Lock access;
    private final String name;
    private final Class<T> type;
    private volatile T lastT;

    public InFile(Directory directory, String name, Class<T> type) {
        this(directory.path().resolve(name), () -> type);
    }

    public InFile(Directory directory, String name, Initializer<T> initializer) {
        this(directory.path().resolve(name), initializer);
    }

    @SuppressWarnings("unchecked")
    public InFile(Path path, Initializer<T> initializer) {
        this.path = path;
        this.access = new PathReentrantLock(this.path, true);
        this.name = path.getFileName().toString();
        this.type = initializer.type();

        if ( Files.isDirectory(this.path) ) {
            throw new IllegalArgumentException(format("Path '%s' is directory!", this.path));
        }

        this.access.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var ois = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            T currentT = (T) ois.readObject();
            this.lastT = currentT;
            initializer.onFileAlreadyExists(currentT);
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.path, e);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                T newT = initializer.onFileCreatedGetInitial();
                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnRead) {
            throw new ObjectInFileException(eOnRead);
        }
        finally {
            this.access.unlock();
        }
    }

    @Override
    public Path path() {
        return this.path;
    }

    @SuppressWarnings("unchecked")
    public T read() {
        this.access.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var is = Channels.newInputStream(fileChannel);
             var ois = new ObjectInputStream(is);
             var lock = fileChannel.lock()) {

            T currentT = (T) ois.readObject();
            this.lastT = currentT;
            return currentT;
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.type, this.path, e);
        }
        catch (NoSuchFileException e) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                oos.writeObject(this.lastT);
                oos.flush();
                return this.lastT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException e) {
            throw new ObjectInFileException(e);
        }
        finally {
            this.access.unlock();
        }
    }

    public void write(T newT) {
        this.access.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            fileChannel.truncate(0);
            var os = Channels.newOutputStream(fileChannel);
            var oos = new ObjectOutputStream(os);
            oos.writeObject(newT);
            oos.flush();
            this.lastT = newT;
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            throw new ObjectInFileException(eOnWrite);
        }
        finally {
            this.access.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T readAndWrite(T newT) {
        this.access.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            T currentT = (T) ois.readObject();
            this.lastT = currentT;

            fileChannel.truncate(0);

            var os = Channels.newOutputStream(fileChannel);
            var oos = new ObjectOutputStream(os);

            oos.writeObject(newT);
            oos.flush();
            this.lastT = newT;

            return currentT;
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.type, this.path, e);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
                return lastT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            throw new ObjectInFileException(eOnWrite);
        }
        finally {
            this.access.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T readAndModify(Function<T, T> oldToNew, boolean doIfNull) {
        this.access.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            T currentT = (T) ois.readObject();
            this.lastT = currentT;

            T newT = null;
            boolean doWrite = false;
            if ( isNull(currentT) ) {
                if ( doIfNull ) {
                    newT = oldToNew.apply(currentT);
                    doWrite = true;
                }
            }
            else {
                newT = oldToNew.apply(currentT);
                doWrite = true;
            }

            if ( doWrite ) {
                fileChannel.truncate(0);

                var os = Channels.newOutputStream(fileChannel);
                var oos = new ObjectOutputStream(os);

                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
            }

            return currentT;
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.type, this.path, e);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var lock = fileChannel.lock()) {

                T currentT = this.lastT;

                T newT = null;
                boolean doWrite = false;
                if ( isNull(currentT) ) {
                    if ( doIfNull ) {
                        newT = oldToNew.apply(currentT);
                        doWrite = true;
                    }
                }
                else {
                    newT = oldToNew.apply(currentT);
                    doWrite = true;
                }

                if ( doWrite ) {
                    fileChannel.truncate(0);

                    var os = Channels.newOutputStream(fileChannel);
                    var oos = new ObjectOutputStream(os);

                    oos.writeObject(newT);
                    oos.flush();
                    this.lastT = newT;
                }

                return this.lastT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            throw new ObjectInFileException(eOnWrite);
        }
        finally {
            this.access.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T readAndMutateIfPresent(Consumer<T> mutate) {
        this.access.lock();
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            T currentT = (T) ois.readObject();
            this.lastT = currentT;

            if ( nonNull(currentT) ) {
                mutate.accept(currentT);

                fileChannel.truncate(0);

                var os = Channels.newOutputStream(fileChannel);
                var oos = new ObjectOutputStream(os);

                oos.writeObject(currentT);
                oos.flush();
            }

            return currentT;
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.type, this.path, e);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                oos.writeObject(null);
                oos.flush();
                this.lastT = null;
                return this.lastT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            throw new ObjectInFileException(eOnWrite);
        }
        finally {
            this.access.unlock();
        }
    }

    @Override
    public String id() {
        return this.path.toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T ifNotPresentResetTo(T newT) {
        this.access.lock();
        boolean write;
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            T currentT = (T) ois.readObject();
            write = isNull(currentT);

            if ( write ) {
                fileChannel.truncate(0);

                var os = Channels.newOutputStream(fileChannel);
                var oos = new ObjectOutputStream(os);

                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
            }

            return currentT;
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.type, this.path, e);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
                return lastT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            throw new ObjectInFileException(eOnWrite);
        }
        finally {
            this.access.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T ifPresentResetTo(T newT) {
        this.access.lock();
        boolean write;
        try (var fileChannel = FileChannel.open(this.path, READ, WRITE);
             var lock = fileChannel.lock()) {

            var is = Channels.newInputStream(fileChannel);
            var ois = new ObjectInputStream(is);

            T currentT = (T) ois.readObject();
            write = nonNull(currentT);

            if ( write ) {
                fileChannel.truncate(0);

                var os = Channels.newOutputStream(fileChannel);
                var oos = new ObjectOutputStream(os);

                oos.writeObject(newT);
                oos.flush();
                this.lastT = newT;
            }

            return currentT;
        }
        catch (InvalidClassException | ClassCastException | ClassNotFoundException e) {
            throw new ObjectInFileClassException(this.type, this.path, e);
        }
        catch (NoSuchFileException eOnRead) {
            try (var fileChannel = FileChannel.open(this.path, READ, WRITE, CREATE_NEW);
                 var os = Channels.newOutputStream(fileChannel);
                 var oos = new ObjectOutputStream(os);
                 var lock = fileChannel.lock()) {

                oos.writeObject(null);
                oos.flush();
                this.lastT = null;
                return lastT;
            }
            catch (FileAlreadyExistsException eOnWrite) {
                throw new ObjectInFileCreateCollisionException(this.path);
            }
            catch (IOException eOnWrite) {
                throw new ObjectInFileException(eOnWrite);
            }
        }
        catch (IOException eOnWrite) {
            throw new ObjectInFileException(eOnWrite);
        }
        finally {
            this.access.unlock();
        }
    }

    @Override
    public T nullify() {
        return this.readAndWrite(null);
    }

    @Override
    public T extractOrThrow() {
        T t = this.readAndWrite(null);
        requireNonNull(t);
        return t;
    }

    @Override
    public T extractOrNull() {
        return this.readAndWrite(null);
    }

    @Override
    public T extractOr(T otherT) {
        T currentT = this.readAndWrite(null);
        if ( isNull(currentT) ) {
            return otherT;
        }
        else {
            return currentT;
        }
    }

    @Override
    public T resetTo(Optional<T> optional) {
        if ( optional.isPresent() ) {
            return this.readAndWrite(optional.get());
        }
        else {
            return this.read();
        }
    }

    @Override
    public T resetTo(Readable.Nullable<T> nullable) {
        if ( nullable.isPresent() ) {
            return this.readAndWrite(nullable.orNull());
        }
        else {
            return this.read();
        }
    }

    @Override
    public T resetTo(Readable.NonNull<T> nonNull) {
        return this.readAndWrite(nonNull.get());
    }

    @Override
    public T modifyNullable(Function<T, T> oldNullableToNew) {
        return this.readAndModify(oldNullableToNew, true);
    }

    @Override
    public T modifyIfPresent(Function<T, T> oldToNew) {
        return this.readAndModify(oldToNew, false);
    }

    @Override
    public T modifyIfPresent(Consumer<T> mutateOld) {
        return this.readAndMutateIfPresent(mutateOld);
    }

    @Override
    public boolean isNotPresent() {
        return this.read() == null;
    }

    @Override
    public boolean isPresent() {
        return this.read() != null;
    }

    @Override
    public boolean isEmpty() {
        return this.read() == null;
    }

    @Override
    public boolean isNotEmpty() {
        return this.read() != null;
    }

    @Override
    public void ifNotPresent(Runnable runnable) {
        if ( this.read() != null ) {
            runnable.run();
        }
    }

    @Override
    public void ifPresent(Consumer<T> consumer) {
        T t = this.read();
        if ( nonNull(t) ) {
            consumer.accept(t);
        }
    }

    @Override
    public <R> R mapValueOrThrow(Function<T, R> function) {
        T t = this.read();
        requireNonNull(t);
        return function.apply(t);
    }

    @Override
    public <R> R mapValueOr(Function<T, R> function, R r) {
        T t = this.read();
        if ( nonNull(t) ) {
            return function.apply(t);
        }
        else {
            return r;
        }
    }

    @Override
    public <R> R mapValueOrNull(Function<T, R> function) {
        T t = this.read();
        if ( nonNull(t) ) {
            return function.apply(t);
        }
        else {
            return null;
        }
    }

    @Override
    public Optional<T> optional() {
        return Optional.ofNullable(this.read());
    }

    @Override
    public T or(T otherT) {
        T currentT = this.read();
        if ( isNull(currentT) ) {
            return otherT;
        }
        else {
            return currentT;
        }
    }

    @Override
    public T orOther(Readable<T> readable) {
        T currentT = this.read();
        if ( isNull(currentT) ) {
            if ( readable.valuePresence().is(NULLABLE) ) {
                return readable.asNullable().orNull();
            }
            else {
                return readable.asNonNull().get();
            }
        }
        else {
            return currentT;
        }
    }

    @Override
    public Reference.Readable.Nullable<T> orDefault(T t) {
        T currentT = this.read();
        if ( isNull(currentT) ) {
            return References.simplePossibleWith(t);
        }
        else {
            return References.simplePossibleWith(currentT);
        }
    }

    @Override
    public <R> Reference.Readable.Nullable<R> map(Function<T, R> function) {
        T currentT = this.read();
        if ( isNull(currentT) ) {
            return References.simplePossibleButEmpty();
        }
        else {
            return References.simplePossibleWith(function.apply(currentT));
        }
    }

    @Override
    public T orThrow() {
        T currentT = this.read();
        requireNonNull(currentT);
        return currentT;
    }

    @Override
    public T orNull() {
        return this.read();
    }

    @Override
    public T orThrow(Supplier<? extends RuntimeException> supplier) {
        T currentT = this.read();

        if ( isNull(currentT) ) {
            throw supplier.get();
        }

        return currentT;
    }

    @Override
    public T resetTo(T t) {
        return this.readAndWrite(t);
    }

    @Override
    public boolean notEqualsToOther(Readable<T> readable) {
        return false;
    }

    @Override
    public boolean equalsTo(T otherT) {
        return Objects.equals(this.read(), otherT);
    }

    @Override
    public boolean notEqualsTo(T otherT) {
        return ! Objects.equals(this.read(), otherT);
    }

    @Override
    public boolean match(Predicate<T> predicate) {
        T t = this.read();
        if ( isNull(t) ) {
            return false;
        }
        else {
            return predicate.test(t);
        }
    }

    @Override
    public boolean notMatch(Predicate<T> predicate) {
        T t = this.read();
        if ( isNull(t) ) {
            return true;
        }
        else {
            return ! predicate.test(t);
        }
    }

    @Override
    public ValuePresence valuePresence() {
        return NULLABLE;
    }

    @Override
    public Type type() {
        return VALUE;
    }

    @Override
    public LocalDateTime createdAt() {
        return null;
    }

    @Override
    public LocalDateTime actualAt() {
        return null;
    }

    @Override
    public String name() {
        return this.path.toString();
    }
}
