package diarsid.files.objects.exceptions;

import java.nio.file.Path;

import static java.lang.String.format;

public class ObjectInFileNotReadableException extends ObjectInFileException {

    private final Path path;
    private final Class<?> type;

    public ObjectInFileNotReadableException(Class<?> type, Path path, Exception e) {
        super(format("Cannot read object of %s class from file '%s'!",
                type.getCanonicalName(),
                path.toString()),
                e);
        this.path = path;
        this.type = type;
    }

    public Path filePath() {
        return path;
    }

    public Class<?> desiredType() {
        return type;
    }
}
