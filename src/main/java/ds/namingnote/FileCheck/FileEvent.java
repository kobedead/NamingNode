package ds.namingnote.FileCheck;


import java.io.File;

public class FileEvent {

    public enum FileEventType {
        ADDED, MODIFIED, DELETED
    }


    private final File file;
    private final FileEventType type;

    public FileEvent(File file, FileEventType type) {
        this.file = file;
        this.type = type;
    }

    public File getFile() { return file; }
    public FileEventType getType() { return type; }

    @Override
    public String toString() {
        return type + ": " + file.getAbsolutePath();
    }
}