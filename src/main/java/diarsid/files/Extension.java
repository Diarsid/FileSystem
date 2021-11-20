package diarsid.files;

import java.util.Objects;

public class Extension {

    private final String nameLowerCase;
    private final String nameUpperCase;
    private final String dotNameLowerCase;
    private final String dotNameUpperCase;

    public Extension(String name) {
        this.nameLowerCase = name.toLowerCase();
        this.nameUpperCase = name.toUpperCase();
        this.dotNameLowerCase = "." + this.nameLowerCase;
        this.dotNameUpperCase = "." + this.nameUpperCase;
    }

    public String name() {
        return this.nameLowerCase;
    }

    public boolean matches(String fileName) {
        return fileName.endsWith(nameLowerCase) ||
                fileName.endsWith(nameUpperCase) ||
                fileName.endsWith(dotNameLowerCase) ||
                fileName.endsWith(dotNameUpperCase);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Extension)) return false;
        Extension extension = (Extension) o;
        return nameLowerCase.equals(extension.nameLowerCase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nameLowerCase);
    }
}
