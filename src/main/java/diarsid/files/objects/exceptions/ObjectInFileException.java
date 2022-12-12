package diarsid.files.objects.exceptions;

import java.nio.file.Path;

public class ObjectInFileException extends RuntimeException {

    public ObjectInFileException() {
    }

    public ObjectInFileException(String message) {
        super(message);
    }

    public ObjectInFileException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectInFileException(Throwable cause) {
        super(cause);
    }
}
