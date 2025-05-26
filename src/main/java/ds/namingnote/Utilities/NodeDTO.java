package ds.namingnote.Utilities;

public class NodeDTO {
    private int ID;

    private String IP;


    public NodeDTO(int ID, String IP) {
        this.ID = ID;
        this.IP = IP;
    }


    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public String getIP() {
        return IP;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }
}
