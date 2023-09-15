package diarsid.files.objects.exceptions;

import java.io.Serializable;

import static java.lang.String.format;

public class ObjectInFileNotSerializableException extends ObjectInFileException {

    public ObjectInFileNotSerializableException(Class<?> type) {
        super(format("Class %s must implement %s!",
                type.getCanonicalName(), Serializable.class.getCanonicalName()));
    }
}
