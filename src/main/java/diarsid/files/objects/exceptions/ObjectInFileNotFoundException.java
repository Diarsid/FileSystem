package diarsid.files.objects.exceptions;

import java.nio.file.Path;

public class ObjectInFileNotFoundException extends ObjectInFileException {

    private final Path path;

    public ObjectInFileNotFoundException(Path path) {
        this.path = path;
    }

    public Path expectedPath() {
        return path;
    }
}
