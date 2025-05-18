package ds.namingnote.model;

import java.util.Objects;

public class LocalFile {
    String fileName;
    boolean isLocked;

    public LocalFile(String fileName, boolean isLocked) {
        this.fileName = fileName;
        this.isLocked = isLocked;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LocalFile localFile = (LocalFile) o;
        return Objects.equals(fileName, localFile.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fileName);
    }
}
