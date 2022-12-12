package diarsid.files.objects.store;

import java.io.Serializable;
import java.util.UUID;
import java.util.function.Consumer;

import diarsid.support.model.Identity;

import static java.util.UUID.randomUUID;

public class DefaultObjectStoreListenerOnChanged<K extends Serializable, T extends Identity<K>> implements ObjectStore.Listener.OnChanged<K, T> {

    private final UUID uuid;
    private final Consumer<T> delegate;

    public DefaultObjectStoreListenerOnChanged(Consumer<T> delegate) {
        this(randomUUID(), delegate);
    }

    public DefaultObjectStoreListenerOnChanged(UUID uuid, Consumer<T> delegate) {
        this.uuid = uuid;
        this.delegate = delegate;
    }

    @Override
    public UUID uuid() {
        return this.uuid;
    }

    @Override
    public void onChanged(T t) {
        this.delegate.accept(t);
    }
}
