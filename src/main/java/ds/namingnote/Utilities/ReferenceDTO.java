package ds.namingnote.Utilities;

public class ReferenceDTO {

    String ipOfRefrence;
    String fileName;

    public ReferenceDTO(String ipOfRefrence, String fileName) {
        this.ipOfRefrence = ipOfRefrence;
        this.fileName = fileName;
    }

    public String getIpOfRefrence() {
        return ipOfRefrence;
    }

    public void setIpOfRefrence(String ipOfRefrence) {
        this.ipOfRefrence = ipOfRefrence;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
