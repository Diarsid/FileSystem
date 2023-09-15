package diarsid.files.objects;

import java.io.Serializable;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import static diarsid.support.concurrency.test.CurrentThread.async;
import static diarsid.support.concurrency.test.CurrentThread.awaitForAll;

public class InFileWatchedDemo implements Serializable {

    @Test
    public void readAndWriteTest() {
        InFile.Initializer<Demo> demoInitializer = new InFile.Initializer<>() {

            @Override
            public Demo onFileCreatedGetInitial() {
                return new Demo("xxx", 0);
            }

            @Override
            public Class<Demo> type() {
                return Demo.class;
            }
        };

        InFileWatched<Demo> inFileWatchedDemo = new InFileWatched<>(
                Paths.get("D:/DEV/test/.in-file-watched-demo-a"),
                demoInitializer,
                (demo) -> System.out.println("changed " + demo));

        inFileWatchedDemo.write(new Demo("yyy", 2));

        assertThat(inFileWatchedDemo.read().string).isEqualTo("yyy");
    }

    public static class JVM1Writer {
        public static void main(String[] args) {
            InFile.Initializer<Demo> demoInitializer = new InFile.Initializer<>() {

                @Override
                public Demo onFileCreatedGetInitial() {
                    return new Demo("xxx", 0);
                }

                @Override
                public Class<Demo> type() {
                    return Demo.class;
                }
            };

            InFileWatched<Demo> inFileWatchedDemo = new InFileWatched<>(
                    Paths.get("D:/DEV/test/.in-file-watched-demo"),
                    demoInitializer,
                    (demo) -> System.out.println("changed " + demo));

            async()
                    .loopEndless()
                    .eachTimeSleep(3000)
                    .eachTimeDo(() -> {
                        System.out.println("changing...");
                        Demo demo = inFileWatchedDemo.read();
                        Demo newDemo = new Demo("xxx_" + demo.i, demo.i + 1);
                        inFileWatchedDemo.write(newDemo);
                        System.out.println("changing done");
                    });

            awaitForAll();
        }
    }

    public static class JVM2Reader {
        public static void main(String[] args) {
            InFile.Initializer<Demo> demoInitializer = new InFile.Initializer<Demo>() {

                @Override
                public Demo onFileCreatedGetInitial() {
                    return new Demo("xxx", 0);
                }

                @Override
                public Class<Demo> type() {
                    return Demo.class;
                }
            };

            InFileWatched<Demo> inFileWatchedDemo = new InFileWatched<>(
                    Paths.get("D:/DEV/test/.in-file-watched-demo"),
                    demoInitializer,
                    (demo) -> System.out.println("changed " + demo));
        }
    }
}
