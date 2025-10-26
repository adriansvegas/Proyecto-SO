package so_operativos;

/**
 * Nodo simple para la implementación de la lista enlazada (base de CustomQueue).
 * Contiene un dato (Proceso) y una referencia al siguiente nodo.
 */
public class CustomNode {
    private Proceso data; // El proceso almacenado en este nodo
    private CustomNode next; // Referencia al siguiente nodo en la lista/cola

    /** Constructor que inicializa el nodo con un proceso. */
    public CustomNode(Proceso data) {
        this.data = data;
        this.next = null; // Inicialmente no apunta a ningún otro nodo
    }

    // --- Getters y Setters ---
    public Proceso getData() { return data; }
    public CustomNode getNext() { return next; }
    public void setNext(CustomNode next) { this.next = next; }
}