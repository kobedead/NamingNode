package ds.namingnote.Utilities;

public class NextAndPreviousNodeDTO {
    private int nextID, previousID;

    public NextAndPreviousNodeDTO() {
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

    public NextAndPreviousNodeDTO(int nextID, int previousID) {
        this.nextID = nextID;
        this.previousID = previousID;
    }
}
