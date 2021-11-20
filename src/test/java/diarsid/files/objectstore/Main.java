package diarsid.files.objectstore;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.UUID.randomUUID;

public class Main {

    private static final Logger log = LoggerFactory.getLogger("Main");

    public static void main(String[] args) throws Exception {
        ObjectStore<UUID, Model> models = new FileObjectStore<>(Paths.get("D:/DEV/test/store"), Model.class);

        ObjectStore.CreatedListener<UUID, Model> createdListener = model -> {
            log.info("created: " + model.uuid);
        };

        ObjectStore.RemovedListener removedListener = serializedKey -> {
            log.info("removed: " + serializedKey);
        };

        ObjectStore.ChangedListener<UUID, Model> changedListener = model -> {
            log.info("changed: " + model.uuid);
        };

        models.subscribe(createdListener);
        models.subscribe(removedListener);
        models.subscribe(changedListener);

//        models.clear();

        Model model = new Model(randomUUID(), "some string data", 0.1);
        model.list.add(new Nested("aaaa", true));
        model.map.put(3, new Nested("bbb", true));

        models.save(model);
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

    }
}
