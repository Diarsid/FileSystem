package diarsid.filesystem;

import org.junit.jupiter.api.Test;

import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FileSystem;

public class DirectoryTest {

    @Test
    public void directoryLock() {
        Directory directory = FileSystem.DEFAULT_INSTANCE.toDirectory("D:/DEV/test").get();

        directory.lockAndDo(() -> {
            System.out.println("in lock");
        });
    }

    @Test
    public void directoryLock2() {
        Directory directory = FileSystem.DEFAULT_INSTANCE.toDirectory("D:/DEV/test").get();

        directory.lockAndDo(() -> {
            System.out.println("in lock");
        });
    }
}
