package diarsid.files.objects.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import diarsid.support.model.Unique;

public class Model implements Unique {

    public final UUID uuid;
    public final String string;
    public final double x;
    public final List<Nested> list;
    public final Map<Integer, Nested> map;

    public Model(UUID uuid, String string, double x) {
        this.uuid = uuid;
        this.string = string;
        this.x = x;
        this.list = new ArrayList<>();
        this.map = new HashMap<>();
    }

    @Override
    public UUID uuid() {
        return this.uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Model)) return false;
        Model model = (Model) o;
        return Double.compare(model.x, x) == 0 &&
                uuid.equals(model.uuid) &&
                string.equals(model.string) &&
                list.equals(model.list) &&
                map.equals(model.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, string, x, list, map);
    }

    @Override
    public String toString() {
        return uuid.toString();
    }
}
