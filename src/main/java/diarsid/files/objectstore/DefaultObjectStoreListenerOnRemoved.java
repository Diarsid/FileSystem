package diarsid.files.objectstore;

import java.util.UUID;
import java.util.function.Consumer;

import static java.util.UUID.randomUUID;

public class DefaultObjectStoreListenerOnRemoved implements ObjectStore.Listener.OnRemoved {

    private final UUID uuid;
    private final Consumer<String> delegate;

    public DefaultObjectStoreListenerOnRemoved(Consumer<String> delegate) {
        this(randomUUID(), delegate);
    }

    public DefaultObjectStoreListenerOnRemoved(UUID uuid, Consumer<String> delegate) {
        this.uuid = uuid;
        this.delegate = delegate;
    }

    @Override
    public UUID uuid() {
        return this.uuid;
    }

    @Override
    public void onRemoved(String key) {
        this.delegate.accept(key);
    }
}
