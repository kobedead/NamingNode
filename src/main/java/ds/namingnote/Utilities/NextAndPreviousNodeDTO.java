package ds.namingnote.Utilities;

public class NextAndPreviousNodeDTO {
    private Node next, previous;

    public NextAndPreviousNodeDTO() {

    }

    public NextAndPreviousNodeDTO(Node next, Node previous) {
        this.next = next;
        this.previous = previous;
    }

    public Node getNext() {
        return next;
    }

    public Node getPrevious() {
        return previous;
    }

    public void setNext(Node next) {
        this.next = next;
    }

    public void setPrevious(Node previous) {
        this.previous = previous;
    }
}
