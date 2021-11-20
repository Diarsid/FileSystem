package diarsid.files.objectstore.exceptions;

public class ObjectStoreException extends RuntimeException {

    public ObjectStoreException() {
        super();
    }

    public ObjectStoreException(Exception e) {
        super(e);
    }

    public ObjectStoreException(String message) {
        super(message);
    }
}