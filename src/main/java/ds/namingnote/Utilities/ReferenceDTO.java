package ds.namingnote.Utilities;

public class ReferenceDTO {

    String ipOfReference;
    String fileName;

    public ReferenceDTO(String ipOfReference, String fileName) {
        this.ipOfReference = ipOfReference;
        this.fileName = fileName;
    }

    public ReferenceDTO() {
    }

    public String getIpOfReference() {
        return ipOfReference;
    }

    public void setIpOfReference(String ipOfReference) {
        this.ipOfReference = ipOfReference;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
