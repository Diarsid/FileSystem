package diarsid.files.objects;

import java.io.Serializable;
import java.util.Objects;

public class Demo implements Serializable {

    String string;
    int i;

    public Demo(String string, int i) {
        this.string = string;
        this.i = i;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Demo)) return false;
        Demo demo = (Demo) o;
        return i == demo.i &&
                string.equals(demo.string);
    }

    @Override
    public int hashCode() {
        return Objects.hash(string, i);
    }

    @Override
    public String toString() {
        return "Demo{" +
                "string='" + string + '\'' +
                ", i=" + i +
                '}';
    }
}
