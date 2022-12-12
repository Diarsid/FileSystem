package diarsid.filesystem.api;

import diarsid.support.objects.CommonEnum;

public enum NoResultReason implements CommonEnum<NoResultReason> {
    PATH_NOT_EXISTS,
    PATH_NOT_POSSIBLE,
    PATH_IS_NOT_FILE,
    PATH_IS_NOT_DIRECTORY,
    FILE_CONTENT_CLASS_NOT_READABLE,
    FILE_CREATION_COLLISION;
}
