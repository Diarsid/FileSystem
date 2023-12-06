package diarsid.files.objects;

import java.util.ArrayList;
import java.util.List;

import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.FSEntry;
import diarsid.filesystem.api.FileSystem;

import static java.lang.System.currentTimeMillis;

import static diarsid.filesystem.api.FileSystem.DEFAULT_INSTANCE;

public class Test {

    public static void main(String[] args) throws Exception {
//        long start0 = currentTimeMillis();
//        List<String> files = Files
//                .list(Paths.get("D:\\CONTENT\\Images\\Photos\\Turkey"))
//                .filter(path -> Files.exists(path))
//                .map(path -> path.getFileName().toString())
//                .collect(toList());
//        long end0 = currentTimeMillis();
//        System.out.println("files: " + files.size());
//        System.out.println("time:  " + (end0 - start0));

        FileSystem fileSystem = DEFAULT_INSTANCE;

        List<FSEntry> entries = new ArrayList<>();


        Directory directory = fileSystem
                .toDirectory("D:\\CONTENT\\Images\\Photos\\Turkey")
                .orThrow();

        long start1 = currentTimeMillis();
        directory.feedChildren(childrens -> {
                    entries.addAll(childrens);
                });
        long end1 = currentTimeMillis();

        entries.forEach(fsEntry -> System.out.println(fsEntry.path().toString()));


        System.out.println("entries: " + entries.size());
        System.out.println("time:  " + (end1 - start1));
    }
}
