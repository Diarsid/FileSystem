package diarsid.filesystem.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Optional;

import diarsid.files.Extension;
import diarsid.files.SizeInBytes;
import diarsid.support.model.CreatedAt;
import diarsid.support.model.UpdatedAt;
import diarsid.support.objects.references.Result;

public interface File extends FSEntry, CreatedAt, UpdatedAt {

    long size();

    Optional<Extension> extension();

    Directory directory();

    void open();

    <T> Result<T> readAs(Class<T> type);

    Result<Object> read();

    void write(Serializable object);

    default Long getSize() {
        return this.size();
    }

    default SizeInBytes sizeGradation() {
        return SizeInBytes.of(this.size());
    }

    default String sizeFormat() {
        return this.sizeGradation().format(this.size());
    }

    Result<LocalDateTime> creationTime();

    Result<LocalDateTime> modificationTime();
}
