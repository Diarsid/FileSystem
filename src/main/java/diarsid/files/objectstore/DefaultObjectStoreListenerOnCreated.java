package diarsid.files.objectstore;

import java.io.Serializable;
import java.util.UUID;
import java.util.function.Consumer;

import diarsid.support.model.Identity;

import static java.util.UUID.randomUUID;

public class DefaultObjectStoreListenerOnCreated<K extends Serializable, T extends Identity<K>> implements ObjectStore.Listener.OnCreated<K, T> {

    private final UUID uuid;
    private final Consumer<T> delegate;

    public DefaultObjectStoreListenerOnCreated(Consumer<T> delegate) {
        this(randomUUID(), delegate);
    }

    public DefaultObjectStoreListenerOnCreated(UUID uuid, Consumer<T> delegate) {
        this.uuid = uuid;
        this.delegate = delegate;
    }

    @Override
    public UUID uuid() {
        return this.uuid;
    }

    @Override
    public void onCreated(T t) {
        this.delegate.accept(t);
    }
}
