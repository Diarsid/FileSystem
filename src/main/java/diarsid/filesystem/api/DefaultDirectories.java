package diarsid.filesystem.api;

import java.util.List;

import diarsid.files.objects.exceptions.ObjectInFileException;
import diarsid.support.strings.split.SplitByChar;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

public class DefaultDirectories {

    public static final Directory USER_HOME = FileSystem
            .DEFAULT_INSTANCE
            .userHomeDirectory()
            .orThrow(reason -> new FileSystemException("User home directory not accessible - " + reason));

    public static final Directory JAVA_IN_USER_HOME = USER_HOME
            .directoryCreateIfNotExists(".java")
            .orThrow(reason -> new FileSystemException("Cannot create or access directory {user_home}/.java - " + reason));

    public static final Directory IN_FILE_OBJECTS = JAVA_IN_USER_HOME
            .directoryCreateIfNotExists(".in-file-objects")
            .orThrow(reason -> new FileSystemException("Cannot create or access directory {user_home}/.java/.in-file-objects - " + reason));


    public static String canonicalClassNamePackagesToPath(Class<?> type) {
        SplitByChar splitByDot = new SplitByChar('.');
        List<String> canonicalName = splitByDot.process(type.getCanonicalName());
        String canonicalNamePath = canonicalName
                .stream()
                .map(name -> "." + name)
                .collect(joining("/"));

        return canonicalNamePath;
    }

    public static Directory directoryOfCanonicalClassNameInJavaUserHome(Class<?> type) {
        return IN_FILE_OBJECTS
                .directoryCreateIfNotExists(canonicalClassNamePackagesToPath(type))
                .orThrow(reason -> new ObjectInFileException(format(
                        "Cannot create class %s path in default in-file-objects directory %s - %s",
                        type.getCanonicalName(),
                        JAVA_IN_USER_HOME.path().toString(),
                        reason)));
    }
}
