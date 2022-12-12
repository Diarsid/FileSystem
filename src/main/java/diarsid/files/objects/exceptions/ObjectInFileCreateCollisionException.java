package diarsid.files.objects.exceptions;

import java.nio.file.Path;

public class ObjectInFileCreateCollisionException extends ObjectInFileException {

    private final Path path;

    public ObjectInFileCreateCollisionException(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }
}
