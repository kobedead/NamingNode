package ds.namingnote.Utilities;

public class NextAndPreviousIDDTO {
    private int nextID, previousID;

    public NextAndPreviousIDDTO() {
    }

    public int getNextID() {
        return nextID;
    }

    public int getPreviousID() {
        return previousID;
    }

    public void setNextID(int nextID) {
        this.nextID = nextID;
    }

    public void setPreviousID(int previousID) {
        this.previousID = previousID;
    }

    public NextAndPreviousIDDTO(int nextID, int previousID) {
        this.nextID = nextID;
        this.previousID = previousID;
    }
}
