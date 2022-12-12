package diarsid.files.objects.store.exceptions;

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

    public ObjectStoreException(String message, Exception e) {
        super(message, e);
    }
}
