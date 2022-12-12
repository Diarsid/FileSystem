package diarsid.files.objects.store;

import java.io.Serializable;
import java.util.Objects;

public class Nested implements Serializable {

    public final String string;
    public final boolean active;

    public Nested(String string, boolean active) {
        this.string = string;
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Nested)) return false;
        Nested nested = (Nested) o;
        return active == nested.active &&
                string.equals(nested.string);
    }

    @Override
    public int hashCode() {
        return Objects.hash(string, active);
    }
}
