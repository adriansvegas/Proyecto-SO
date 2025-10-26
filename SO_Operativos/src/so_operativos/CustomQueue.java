package so_operativos;

/**
 * Implementación de una Cola (Queue) FIFO usando una Lista Simplemente Enlazada.
 * Diseñada específicamente para este proyecto para evitar usar java.util.Queue.
 */
public class CustomQueue {
    private CustomNode head; // Referencia al primer nodo (frente de la cola)
    private CustomNode tail; // Referencia al último nodo (final de la cola)
    private int size; // Número de elementos en la cola

    /** Constructor que inicializa una cola vacía. */
    public CustomQueue() { this.head = null; this.tail = null; this.size = 0; }

    /**
     * Añade un proceso al final de la cola (enqueue).
     * Es synchronized para seguridad en entornos multihilo.
     * @param proceso El proceso a añadir.
     */
    public synchronized void add(Proceso proceso) {
        CustomNode newNode = new CustomNode(proceso);
        if (tail == null) { // Si la cola está vacía
            head = newNode; tail = newNode;
        } else { // Si la cola no está vacía
            tail.setNext(newNode); tail = newNode;
        }
        size++;
    }

    /**
     * Saca y remueve el proceso del frente de la cola (dequeue).
     * Es synchronized para seguridad en entornos multihilo.
     * @return El proceso del frente, o null si la cola está vacía.
     */
    public synchronized Proceso poll() {
        if (head == null) { return null; } // Cola vacía
        Proceso proceso = head.getData();
        head = head.getNext(); // Avanza el head
        if (head == null) { tail = null; } // Si se vació la cola, tail también es null
        size--;
        return proceso;
    }

    /**
     * Retorna el proceso del frente sin removerlo (peek).
     * @return El proceso del frente, o null si la cola está vacía.
     */
    public Proceso peek() { return (head != null) ? head.getData() : null; }

    /** Verifica si la cola está vacía. */
    public boolean isEmpty() { return head == null; }

    /** Retorna el número de elementos en la cola. */
    public int size() { return size; }

    /**
     * Convierte la cola en un arreglo de procesos. Útil para iterar o reordenar.
     * @return Un array con los procesos en el orden de la cola.
     */
    public Proceso[] toArray() {
        Proceso[] array = new Proceso[size]; CustomNode current = head; int index = 0;
        while (current != null) { array[index++] = current.getData(); current = current.getNext(); }
        return array;
    }

    /**
     * Limpia la cola actual y la reconstruye a partir de un arreglo (posiblemente ordenado).
     * Es synchronized para seguridad.
     * @param array El array de procesos fuente.
     * @param length El número de elementos válidos en el array.
     */
    public synchronized void rebuildFrom(Proceso[] array, int length) {
        head = null; tail = null; size = 0; // Limpia la cola
        for (int i = 0; i < length; i++) { if (array[i] != null) { add(array[i]); } } // Añade elementos del array
    }

    /**
     * Encuentra y remueve un proceso específico de la cola (usado por SJF/Prioridad).
     * Es synchronized para seguridad.
     * @param target El proceso a remover.
     * @return El proceso removido, o null si no se encontró.
     */
    public synchronized Proceso remove(Proceso target) {
        if (head == null) return null; // Cola vacía
        if (head.getData() == target) { return poll(); } // Si es el primero, usa poll()

        CustomNode current = head;
        while (current.getNext() != null) { // Busca en los siguientes nodos
            if (current.getNext().getData() == target) {
                Proceso removed = current.getNext().getData();
                current.setNext(current.getNext().getNext()); // Enlaza el anterior con el siguiente del objetivo
                if (current.getNext() == null) { tail = current; } // Si se eliminó el último, actualiza tail
                size--; return removed;
            }
            current = current.getNext();
        }
        return null; // No encontrado
    }
}