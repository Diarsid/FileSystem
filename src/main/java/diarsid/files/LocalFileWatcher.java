package diarsid.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.function.BiConsumer;

import diarsid.filesystem.api.Directory;
import diarsid.filesystem.api.File;
import diarsid.support.objects.references.Result;

import static java.lang.String.format;

public class LocalFileWatcher extends LocalDirectoryWatcher {

    private final Path path;

    public LocalFileWatcher(
        Directory directory,
        String fileName,
        BiConsumer<WatchEvent.Kind<?>, Path> callback,
        CallbackSynchronization sync) {
        super(directory, callback, sync, (filePath) -> {
            return Files.isRegularFile(filePath) && filePath.getFileName().endsWith(fileName);
        });
        Result<File> file = directory.file(fileName);

        if ( file.isEmpty() ) {
            throw new IllegalArgumentException(format("Cannot create %s for file '%s' in directory '%s' - %s",
                    LocalFileWatcher.class.getSimpleName(),
                    fileName,
                    directory.path(),
                    file.reason().subject()));
        }

        this.path = file.get().path();
    }

    public LocalFileWatcher(
            Path path,
            BiConsumer<WatchEvent.Kind<?>, Path> callback,
            CallbackSynchronization sync) {
        super(path.getParent(), callback, sync, (filePath) -> {
            return Files.isRegularFile(filePath) && filePath.equals(path);
        });

        this.path = path;

        if ( Files.isDirectory(path) ) {
            throw new IllegalArgumentException(format("Cannot create %s for path '%s' - it is directory!",
                    LocalFileWatcher.class.getSimpleName(),
                    path));
        }
    }

    @Override
    public Path path() {
        return this.path;
    }
}
