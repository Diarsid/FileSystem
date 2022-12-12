package diarsid.files.aware;

import java.nio.file.Path;

public interface PathLockAware {

    default void onLockAcquired(Path path)  {
        // nothing
    }

    default void onLockReleased(Path path) {
        // nothing
    }
}
