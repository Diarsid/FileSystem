package diarsid.files.objects.store;

import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.File;
import diarsid.filesystem.api.FileSystem;

import static diarsid.filesystem.api.FileSystem.DEFAULT_INSTANCE;

public class Main2 {

    public static void main(String[] args) {
        FileSystem fileSystem = DEFAULT_INSTANCE;

        Directory directory = fileSystem.machineDirectory();
        boolean x = directory.hasDirectory("D");

        System.out.println(x);

    }
}
