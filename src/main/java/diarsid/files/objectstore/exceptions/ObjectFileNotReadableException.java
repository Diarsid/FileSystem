package diarsid.files.objectstore.exceptions;

import java.nio.file.Path;

import static java.lang.String.format;

public class ObjectFileNotReadableException extends ObjectStoreException {

    private final Path path;

    public ObjectFileNotReadableException(Class type, Path path) {
        super(format("File '%s' is not a readable object of %s class!", path.toString(), type.getCanonicalName()));
        this.path = path;
    }
}
