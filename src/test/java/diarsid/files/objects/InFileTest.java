package diarsid.files.objects;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InFileTest {

    private static final InFile.Initializer<String> STRING_INITIALIZER = new InFile.Initializer<>() {

        @Override
        public Class<String> type() {
            return String.class;
        }

        @Override
        public String onFileCreatedGetInitial() {
            return "initial";
        }
    };

    @Test
    public void cases() throws Exception {
        Path file = Paths.get("D:/DEV/test/string-in-file");
        Files.deleteIfExists(file);

        InFile<String> inFile = new InFile<>(file, STRING_INITIALIZER);

        assertThat(inFile.read()).isEqualTo("initial");

        inFile.write(null);
        assertThat(inFile.read()).isNull();

        inFile.write("next");
        assertThat(inFile.read()).isEqualTo("next");

        assertThat(inFile.extractOrNull()).isEqualTo("next");
        assertThat(inFile.read()).isNull();
        assertThat(inFile.extractOrNull()).isNull();

        assertThat(inFile.ifPresentResetTo("new")).isNull();
        assertThat(inFile.read()).isNull();

        assertThat(inFile.ifNotPresentResetTo("new")).isNull();
        assertThat(inFile.read()).isEqualTo("new");
    }

    @Test
    public void otherLinks() throws Exception {
        Path file = Paths.get("D:/DEV/test/string-in-file");
        Files.deleteIfExists(file);

        AtomicBoolean onCreatedInvoked = new AtomicBoolean(false);
        AtomicBoolean onExistsInvoked = new AtomicBoolean(false);

        InFile.Initializer<String> stringInitializer = new InFile.Initializer<>() {

            @Override
            public Class<String> type() {
                return String.class;
            }

            @Override
            public String onFileCreatedGetInitial() {
                onCreatedInvoked.set(true);
                return "initial";
            }

            @Override
            public void onFileAlreadyExists(String existingT) {
                onExistsInvoked.set(true);
            }
        };

        InFile<String> inFile1 = new InFile<>(file, stringInitializer);
        assertThat(inFile1.read()).isEqualTo("initial");
        assertThat(onCreatedInvoked).isTrue();
        assertThat(onExistsInvoked).isFalse();

        InFile<String> inFile2 = new InFile<>(file, stringInitializer);
        assertThat(onCreatedInvoked).isTrue();
        assertThat(onExistsInvoked).isTrue();
        assertThat(inFile1.read()).isEqualTo("initial");
        assertThat(inFile2.read()).isEqualTo("initial");

        inFile1.resetTo("next-2");
        assertThat(inFile1.read()).isEqualTo("next-2");
        assertThat(inFile2.read()).isEqualTo("next-2");
    }

    public static class RefData implements Serializable {

        public String s;

        public RefData(String s) {
            this.s = s;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RefData)) return false;
            RefData refData = (RefData) o;
            return Objects.equals(s, refData.s);
        }

        @Override
        public int hashCode() {
            return Objects.hash(s);
        }
    }

    @Test
    public void mutateRefData() throws Exception {
        Path file = Paths.get("D:/DEV/test/ref-data-in-file");
        Files.deleteIfExists(file);

        InFile.Initializer<RefData> refDataInitializer = new InFile.Initializer<>() {

            @Override
            public Class<RefData> type() {
                return RefData.class;
            }

            @Override
            public RefData onFileCreatedGetInitial() {
                return new RefData("initial");
            }
        };

        InFile<RefData> inFile = new InFile<>(file, refDataInitializer);
        assertThat(inFile.read().s).isEqualTo("initial");

        inFile.modifyIfPresent(data -> {
            data.s = "next";
        });

        assertThat(inFile.read().s).isEqualTo("next");

        inFile.nullify();

        assertThat(inFile.read()).isNull();

        AtomicBoolean invoked = new AtomicBoolean(false);
        inFile.modifyIfPresent(data -> {
            data.s = "next-not-null";
            invoked.set(true);
        });

        assertThat(invoked).isFalse();
        assertThat(inFile.read()).isNull();

        inFile.modifyNullable(data -> {
            return new RefData("not-null");
        });

        assertThat(inFile.read().s).isEqualTo("not-null");

        inFile.modifyNullable(data -> {
            return new RefData("not-null-next");
        });

        assertThat(inFile.read().s).isEqualTo("not-null-next");

        inFile.modifyIfPresent(data -> {
            data.s = "modified";
            invoked.set(true);
        });

        assertThat(invoked).isTrue();
        assertThat(inFile.read().s).isEqualTo("modified");
    }

    @Test
    public void readAndWriteRefData() throws Exception {
        Path file = Paths.get("D:/DEV/test/ref-data-in-file");
        Files.deleteIfExists(file);

        InFile.Initializer<RefData> refDataInitializer = new InFile.Initializer<>() {

            @Override
            public Class<RefData> type() {
                return RefData.class;
            }

            @Override
            public RefData onFileCreatedGetInitial() {
                return new RefData("initial");
            }
        };

        InFile<RefData> inFile = new InFile<>(file, refDataInitializer);
        assertThat(inFile.read().s).isEqualTo("initial");

        inFile.write(new RefData("write-after-read"));

        assertThat(inFile.read().s).isEqualTo("write-after-read");
    }

    @Test
    public void initialNull() throws Exception {
        Path file = Paths.get("D:/DEV/test/string-in-file");
        Files.deleteIfExists(file);

        InFile<String> inFile = new InFile<>(file, () -> String.class);
        assertThat(inFile.read()).isNull();

        inFile.write("next");
        assertThat(inFile.read()).isEqualTo("next");
    }
}
