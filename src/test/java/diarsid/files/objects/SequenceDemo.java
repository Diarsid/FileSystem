package diarsid.files.objects;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static diarsid.support.concurrency.test.CurrentThread.async;
import static diarsid.support.concurrency.test.CurrentThread.awaitForAll;

public class SequenceDemo {

    public static class JVM1 {
        public static void main(String[] args) {
            FileLongSequence sequence = new FileLongSequence("D:/DEV/test/.sequence");

            async()
                    .loopEndless()
                    .eachTimeSleep(10)
                    .eachTimeDo(() -> {
                        String thread = Thread.currentThread().getName();
                        long start = System.currentTimeMillis();
                        long value = sequence.getAndIncrement();
                        long end = System.currentTimeMillis();
                        System.out.println("Thread:" + thread + " value:" + value + " time:" + (end-start));
                    });

            async()
                    .loopEndless()
                    .eachTimeSleep(10)
                    .eachTimeDo(() -> {
                        String thread = Thread.currentThread().getName();
                        long start = System.currentTimeMillis();
                        long value = sequence.getAndIncrement();
                        long end = System.currentTimeMillis();
                        System.out.println("Thread:" + thread + " value:" + value + " time:" + (end-start));
                    });

            awaitForAll();
        }
    }

    public static class JVM2 {
        public static void main(String[] args) {
            FileLongSequence sequence = new FileLongSequence("D:/DEV/test/.sequence");

            async()
                    .loopEndless()
                    .eachTimeSleep(10)
                    .eachTimeDo(() -> {
                        String thread = Thread.currentThread().getName();
                        long start = System.currentTimeMillis();
                        long value = sequence.getAndIncrement();
                        long end = System.currentTimeMillis();
                        System.out.println("Thread:" + thread + " value:" + value + " time:" + (end-start));
                    });

            async()
                    .loopEndless()
                    .eachTimeSleep(10)
                    .eachTimeDo(() -> {
                        String thread = Thread.currentThread().getName();
                        long start = System.currentTimeMillis();
                        long value = sequence.getAndIncrement();
                        long end = System.currentTimeMillis();
                        System.out.println("Thread:" + thread + " value:" + value + " time:" + (end-start));
                    });

            awaitForAll();
        }
    }

    public static class RemoveSequenceFile {
        public static void main(String[] args) throws Exception {
            Path path = Paths.get("D:/DEV/test/.sequence-1");
            Files.deleteIfExists(path);
            FileLongSequence sequence = new FileLongSequence(path);

            async()
                    .loopEndless()
                    .eachTimeSleep(1000)
                    .eachTimeDo(() -> {
                        long value = sequence.getAndIncrement();
                        System.out.println("value:" + value);
                    });

            async()
                    .sleep(5500)
                    .afterSleepDo(() -> {
                        Files.deleteIfExists(path);
                        System.out.println("removed");
                    });

            awaitForAll();
        }
    }


}
