package ds.namingnote.Utilities;

public class NextAndPreviousNodeDTO {
    private Node nextNode;
    private Node previousNode;

    public NextAndPreviousNodeDTO(Node nextID, Node previousID) {
        this.nextNode = nextID;
        this.previousNode = previousID;
    }

    public Node getNextNode() {
        return nextNode;
    }

    public void setNextNode(Node nextID) {
        this.nextNode = nextID;
    }

    public Node getPreviousNode() {
        return previousNode;
    }

    public void setPreviousNode(Node previousID) {
        this.previousNode = previousID;
    }
}
