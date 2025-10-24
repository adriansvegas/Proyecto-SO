package so_operativos;

/**
 * Nodo simple para la implementación de la lista enlazada (base de CustomQueue).
 */
public class CustomNode {
    private Proceso data;
    private CustomNode next;

    public CustomNode(Proceso data) {
        this.data = data;
        this.next = null;
    }

    public Proceso getData() { return data; }
    public CustomNode getNext() { return next; }
    public void setNext(CustomNode next) { this.next = next; }
}