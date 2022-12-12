package diarsid.files.objects.exceptions;

import java.nio.file.Path;

import static java.lang.String.format;

public class ObjectInFileClassException extends ObjectInFileException {

    private final Path path;

    public ObjectInFileClassException(Class<?> type, Path path, Exception e) {
        super(format("File '%s' is not an object of %s class!",
                path.toString(),
                type.getCanonicalName()),
                e);
        this.path = path;
    }

    public ObjectInFileClassException(Path path, Exception e) {
        super(format("Issue with class of object in file '%s'!",
                path.toString()),
                e);
        this.path = path;
    }
}
