package diarsid.files.objects;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileLongSequenceTest {

    @Test
    public void longOverrun() throws Exception {
        Path file = Paths.get("D:/DEV/test/long-sequence");
        Files.deleteIfExists(file);

        FileLongSequence sequence = new FileLongSequence(file, Long.MAX_VALUE-1, 1, () -> 1L);
        assertThat(sequence.getAndIncrement()).isEqualTo(Long.MAX_VALUE-1);
        assertThat(sequence.getAndIncrement()).isEqualTo(Long.MAX_VALUE);
        assertThat(sequence.getAndIncrement()).isEqualTo(Long.MIN_VALUE);
    }
}
