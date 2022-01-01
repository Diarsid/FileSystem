package diarsid.files.objectstore.exceptions;

import java.io.InvalidClassException;
import java.nio.file.Path;

import static java.lang.String.format;

public class ObjectClassNotMatchesException extends ObjectStoreException {

    private final Path path;

    public ObjectClassNotMatchesException(Class type, Path path, InvalidClassException e) {
        super(format("File '%s' is not an object of %s class!",
                path.toString(),
                type.getCanonicalName()),
                e);
        this.path = path;
    }
}
