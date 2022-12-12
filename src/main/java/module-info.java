module diarsid.filesystem {

    requires java.desktop;
    requires org.slf4j;
    requires diarsid.support;

    exports diarsid.files;
    exports diarsid.files.objects;
    exports diarsid.files.objects.exceptions;
    exports diarsid.files.objects.store;
    exports diarsid.files.objects.store.exceptions;
    exports diarsid.filesystem.api;
    exports diarsid.filesystem.api.ignoring;
}
