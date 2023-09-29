package diarsid.filesystem.api;

import diarsid.files.FileInvoker;

import static java.lang.String.format;

public class InvokeException extends FileSystemException {

    private final String target;
    private final FileInvoker.TargetType targetType;
    private final Exception reason;

    public InvokeException(String target, FileInvoker.TargetType targetType, Exception reason) {
        super(format("Failed to invoke %s %s: %s", target, targetType.name(), reason.getMessage()), reason);
        this.target = target;
        this.targetType = targetType;
        this.reason = reason;
    }

    public String target() {
        return target;
    }

    public FileInvoker.TargetType targetType() {
        return targetType;
    }

    public Exception reason() {
        return reason;
    }
}
