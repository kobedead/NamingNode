package ds.namingnote.Utilities;

public class Node {

    private int ID;

    private String IP;


    public Node(int ID, String IP) {
        this.ID = ID;
        this.IP = IP;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return ID == node.ID; // Compare only the ID
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


    @Override
    public String toString() {
        return "Node{" +
                "ID ='" + ID + '\'' +
                ", IP =" + IP +
                '}';
    }




}
