module diarsid.filesystem {

    requires java.desktop;
    requires org.slf4j;
    requires diarsid.support;

    exports diarsid.files;
    exports diarsid.files.objectstore;
    exports diarsid.files.objectstore.exceptions;
    exports diarsid.filesystem.api;
    exports diarsid.filesystem.api.ignoring;
}
