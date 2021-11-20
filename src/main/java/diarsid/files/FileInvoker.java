package diarsid.files;

import java.awt.Desktop;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import diarsid.filesystem.api.InvokeException;
import diarsid.support.objects.CommonEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

import static diarsid.files.FileInvoker.Invocation.FILE_OPENED;
import static diarsid.files.FileInvoker.Invocation.NOT_ACCESSIBLE;
import static diarsid.files.FileInvoker.Invocation.NOT_EXISTS;
import static diarsid.files.FileInvoker.Invocation.WEB_URL_BROWSED;

public class FileInvoker {

    private static final Logger log = LoggerFactory.getLogger(FileInvoker.class);

    public enum TargetType implements CommonEnum<TargetType> {
        FILE,
        WEB_URL;
    }

    public enum Invocation implements CommonEnum<Invocation> {

        FILE_OPENED(true),
        WEB_URL_BROWSED(true),
        NOT_ACCESSIBLE(false),
        NOT_EXISTS(false);

        public boolean success;
        public boolean fail;

        Invocation(boolean success) {
            this.success = success;
            this.fail = !success;
        }

    }

    private final Desktop desktop;

    public FileInvoker() {
        this.desktop = Desktop.getDesktop();
    }

    public FileInvoker(Desktop desktop) {
        this.desktop = desktop;
    }

    public Invocation invoke(File file) {
        Path path = file.toPath();
        return this.invoke(path);
    }

    public Invocation invoke(Path path) {
        boolean exists;
        try {
            exists = Files.exists(path);
        }
        catch (Exception e) {
            return NOT_ACCESSIBLE;
        }

        if ( exists ) {
            if ( Files.isExecutable(path) ) {
                this.open(path.toFile());
                return FILE_OPENED;
            }
            else {
                return NOT_ACCESSIBLE;
            }
        }
        else {
            return NOT_EXISTS;
        }
    }

    private static URI toUriOrNull(String path) {
        if ( path.contains("/") ) {
            try {
                return new URL(path).toURI();
            }
            catch (MalformedURLException | URISyntaxException e) {
                return null;
            }
        }
        else {
            return null;
        }
    }

    public Invocation invoke(String path /* filesystem path or url */) {
        URI uri = toUriOrNull(path);
        if ( isNull(uri) ) {
            return this.invoke(Paths.get(path));
        }
        else {
            log.info("browsing " + path);
            try {
                this.desktop.browse(new URI(path));
                return WEB_URL_BROWSED;
            }
            catch (Exception e) {
                throw new InvokeException(path, TargetType.WEB_URL, e);
            }
        }
    }

    private void open(File file) {
        try {
            log.info("opening " + file);
            this.desktop.open(file);
        }
        catch (Exception e) {
            throw new InvokeException(file.toString(), TargetType.FILE, e);
        }
    }
}
