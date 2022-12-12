package diarsid.files.aware;

import java.nio.file.Path;

public interface PathAware {

    default void onCreated(Path path) {
        // nothing
    }

    default void onAccessed(Path path) {
        // nothing
    }
}
