package diarsid.files.objects.store;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.UUID.randomUUID;

public class Main {

    private static final Logger log = LoggerFactory.getLogger("Main");

    public static void main(String[] args) throws Exception {
        ObjectStore<UUID, Model> models = new FileObjectStore<>(Paths.get("D:/DEV/test/store"), Model.class);

        ReadWriteLock locks = new ReentrantReadWriteLock(true);
        var readLock = locks.readLock();
        var writeLock = locks.writeLock();

        ObjectStore.Listener.OnCreated<UUID, Model> onCreated = ObjectStore.Listener.OnCreated.getDefault(model -> {
            log.info("before created: " + model.uuid);
            writeLock.lock();
            try {
                log.info("created: " + model.uuid);
            }
            finally {
                writeLock.unlock();
            }
        });

        ObjectStore.Listener.OnRemoved onRemoved = ObjectStore.Listener.OnRemoved.getDefault(serializedKey -> {
            log.info("removed: " + serializedKey);
        });

        ObjectStore.Listener.OnChanged<UUID, Model> onChanged = ObjectStore.Listener.OnChanged.getDefault(model -> {
            log.info("changed: " + model.uuid);
        });

        models.subscribe(onCreated);
        models.subscribe(onRemoved);
        models.subscribe(onChanged);

//        models.clear();

        UUID uuid = UUID.fromString("9b49c9aa-85cf-456b-8cc3-77b947014bdc");

        Model model = new Model(uuid, "some string data 4", 0.1);
        model.list.add(new Nested("aaaa", true));
        model.map.put(3, new Nested("bbb", true));

        writeLock.lock();
        try {
            models.save(model);
            models.save(model);
            models.save(model);
        }
        finally {
            writeLock.unlock();
        }

        Model model2 = new Model(randomUUID(), "xxxxx", 0.1);
        model2.list.add(new Nested("aaaa", true));
        model2.map.put(3, new Nested("bbb", true));

        writeLock.lock();
        try {
            models.save(model2);
        }
        finally {
            writeLock.unlock();
        }

        System.out.println("finished");
//        Model model1 = models.getBy(model.uuid);

//        models.remove(model);

//        Optional<Model> model2 = models.findBy(model.uuid);
//
//        if ( ! model2.isPresent() ) {
//            throw new IllegalStateException();
//        }
        List<Model> all = models.getAll();


        Thread.sleep(500);
        models.close();
        System.out.println("closed");
    }
}
