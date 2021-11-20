package diarsid.files.objectstore;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import diarsid.support.model.Identity;

import static java.util.stream.Collectors.toList;

public interface ObjectStore<K extends Serializable, T extends Identity<K>> extends AutoCloseable {

    interface Listener {

        default void onUnsubscribed() {}
    }

    interface CreatedListener<K extends Serializable, T extends Identity<K>> extends Listener {

        void onCreated(T t);
    }

    interface RemovedListener extends Listener {

        void onRemoved(String serializedKey);
    }

    interface ChangedListener<K extends Serializable, T extends Identity<K>> extends Listener {

        void onChanged(T t);
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

    UUID subscribe(CreatedListener<K, T> listener);

    UUID subscribe(RemovedListener listener);

    UUID subscribe(ChangedListener<K, T> listener);

    boolean unsubscribe(UUID listenerUuid);

}
