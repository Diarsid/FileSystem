package diarsid.filesystem.api.ignoring;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import diarsid.filesystem.api.FSEntry;
import diarsid.support.objects.groups.async.AsyncConsumers;

public interface Ignores {

    Ignores INSTANCE = new IgnoresHolder();

    enum Sort {
        BY_TIME,
        BY_PATH
    }

    boolean isIgnored(FSEntry fsEntry);

    Optional<Ignore> findFor(FSEntry fsEntry);

    Collection<Ignore> all();

    List<Ignore> allBy(Sort sort);

    Ignore ignore(FSEntry fsEntry);

    boolean undo(Ignore ignore);

    AsyncConsumers<Ignore> onIgnore();

    AsyncConsumers<Ignore> onIgnoreUndo();

    default boolean isNotIgnored(FSEntry fsEntry) {
        return ! this.isIgnored(fsEntry);
    }
}
