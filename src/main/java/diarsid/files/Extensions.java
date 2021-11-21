package diarsid.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import diarsid.filesystem.api.File;

import static java.util.Objects.isNull;

public class Extensions {

    private final HashMap<String, Extension> extensionsByNames;

    public Extensions() {
        this.extensionsByNames = new HashMap<>();
    }

    public Extension getBy(String name) {
        String ext = name.strip().toLowerCase();
        return this.extensionsByNames.computeIfAbsent(ext, (ext1) -> new Extension(ext));
    }

    public Optional<Extension> getFor(String fileName) {
        String extensionString = extensionStringOrNullFrom(fileName);
        if ( isNull(extensionString) || extensionString.isBlank() ) {
            return Optional.empty();
        }
        else {
            Extension extension = this.extensionsByNames.computeIfAbsent(extensionString, Extension::new);
            return Optional.of(extension);
        }
    }

    public Optional<Extension> getFor(Path path) {
        if ( Files.isDirectory(path) ) {
            return Optional.empty();
        }

        String extensionString = extensionStringOrNullFrom(path.getFileName().toString());
        if ( isNull(extensionString) || extensionString.isBlank() ) {
            return Optional.empty();
        }
        else {
            Extension extension = this.extensionsByNames.computeIfAbsent(extensionString, Extension::new);
            return Optional.of(extension);
        }
    }

    public Optional<Extension> getFor(File file) {
        String extensionString = this.extensionStringOrNullFrom(file);
        if ( isNull(extensionString) || extensionString.isBlank() ) {
            return Optional.empty();
        }
        else {
            Extension extension = this.extensionsByNames.computeIfAbsent(extensionString, Extension::new);
            return Optional.of(extension);
        }
    }

    private static String extensionStringOrNullFrom(File file) {
        return extensionStringOrNullFrom(file.name());
    }

    private static String extensionStringOrNullFrom(String fileName) {
        int lastCommaIndex = fileName.lastIndexOf('.');

        if ( lastCommaIndex < 0 ) {
            return null;
        }
        else {
            return fileName.substring(lastCommaIndex + 1);
        }
    }
}
