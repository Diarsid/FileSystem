package diarsid.files.objectstore;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import diarsid.support.model.Identity;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public interface ObjectStore<K extends Serializable, T extends Identity<K>> extends AutoCloseable {

    interface Listener {

        UUID uuid();

        default void onUnsubscribed() {}

        interface OnCreated<K extends Serializable, T extends Identity<K>> extends Listener {

            static <K extends Serializable, T extends Identity<K>> OnCreated<K, T> getDefault(Consumer<T> listener) {
                return new DefaultObjectStoreListenerOnCreated<>(listener);
            }

            void onCreated(T t);
        }

        interface OnRemoved extends Listener {

            static OnRemoved getDefault(Consumer<String> listener) {
                return new DefaultObjectStoreListenerOnRemoved(listener);
            }

            void onRemoved(String serializedKey);
        }

        interface OnChanged<K extends Serializable, T extends Identity<K>> extends Listener {

            static <K extends Serializable, T extends Identity<K>> OnChanged<K, T> getDefault(Consumer<T> listener) {
                return new DefaultObjectStoreListenerOnChanged<>(listener);
            }

            void onChanged(T t);
        }
    }

    enum Instruction {
        DONT_LOCK_OBJECT,
        DONT_LOCK_STORE
    }

    boolean exists(K key);

    T getBy(K key);

    List<T> getAllBy(List<K> keys);

    List<T> getAll();

    Optional<T> findBy(K key);

    void save(T t);

    default void saveAll(T... ts) {
        this.saveAll(asList(ts));
    }

    void saveAll(List<T> list);

    boolean remove(K key);

    boolean removeAll(List<K> key);

    default boolean remove(T t) {
        return this.remove(t.id());
    }

    default boolean remove(List<T> list) {
        List<K> keys = list
                .stream()
                .map(Identity::id)
                .collect(toList());

        return this.removeAll(keys);
    }

    void clear();

    void subscribe(Listener.OnCreated<K, T> listener);

    void subscribe(Listener.OnRemoved listener);

    void subscribe(Listener.OnChanged<K, T> listener);

    boolean unsubscribe(UUID listenerUuid);

}
