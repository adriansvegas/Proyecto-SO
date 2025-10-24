package so_operativos;

/**
 * Implementación de una Cola (Queue) usando una Lista Simplemente Enlazada,
 * cumpliendo la restricción de no usar java.util.
 */
public class CustomQueue {
    private CustomNode head;
    private CustomNode tail;
    private int size;

    public CustomQueue() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /** Añade un proceso al final de la cola (enqueue). */
    public synchronized void add(Proceso proceso) {
        CustomNode newNode = new CustomNode(proceso);
        if (tail == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.setNext(newNode);
            tail = newNode;
        }
        size++;
    }

    /** Saca y remueve el proceso del frente de la cola (dequeue). */
    public synchronized Proceso poll() {
        if (head == null) {
            return null;
        }
        Proceso proceso = head.getData();
        head = head.getNext();
        if (head == null) { // La cola se vació
            tail = null;
        }
        size--;
        return proceso;
    }

    /** Retorna el proceso del frente sin removerlo (peek). */
    public Proceso peek() {
        return (head != null) ? head.getData() : null;
    }

    public boolean isEmpty() {
        return head == null;
    }

    public int size() {
        return size;
    }

    /** Convierte la cola en un arreglo de procesos para facilitar la planificación. */
    public Proceso[] toArray() {
        Proceso[] array = new Proceso[size];
        CustomNode current = head;
        int index = 0;
        while (current != null) {
            array[index++] = current.getData();
            current = current.getNext();
        }
        return array;
    }

    /** Limpia la cola y la reconstruye a partir de un arreglo ordenado. */
    public synchronized void rebuildFrom(Proceso[] array, int length) {
        head = null;
        tail = null;
        size = 0;

        for (int i = 0; i < length; i++) {
            if (array[i] != null) {
                add(array[i]);
            }
        }
    }

    /** Encuentra y remueve un proceso específico (usado por SJF/Prioridad). */
    public synchronized Proceso remove(Proceso target) {
        if (head == null) return null;

        if (head.getData() == target) {
            return poll();
        }

        CustomNode current = head;
        while (current.getNext() != null) {
            if (current.getNext().getData() == target) {
                Proceso removed = current.getNext().getData();
                current.setNext(current.getNext().getNext());
                if (current.getNext() == null) {
                    tail = current;
                }
                size--;
                return removed;
            }
            current = current.getNext();
        }
        return null;
    }
}