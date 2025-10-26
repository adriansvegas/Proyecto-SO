package so_operativos.interfaz; 

import javax.swing.AbstractListModel;
import so_operativos.EDD.Arraylist; 

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template

/**
 *
 * @author adria
 */


/**
 * Implementación simplificada de un ListModel para JList, usando Arraylist.
 */
public class CustomListModel<E> extends AbstractListModel<E> {

    private Arraylist<E> delegate;

    public CustomListModel() {
        delegate = new Arraylist<>();
    }

    @Override
    public int getSize() {
        return delegate.size();
    }

    @Override
    public E getElementAt(int index) {
        return delegate.get(index);
    }

    // --- Métodos para modificar el modelo (similar a DefaultListModel) ---

    public void addElement(E element) {
        int index = delegate.size();
        delegate.add(element);
        fireIntervalAdded(this, index, index);
    }

    public void removeElementAt(int index) {
        if (index >= 0 && index < delegate.size()) {
            delegate.remove(index);
            fireIntervalRemoved(this, index, index);
        } else {
             throw new ArrayIndexOutOfBoundsException("removeElementAt: index out of range: "+index);
        }
    }

    public boolean removeElement(Object obj) {
         int index = delegate.indexOf(obj);
         if (index != -1) {
             removeElementAt(index);
             return true;
         }
         return false;
    }

    public void clear() {
        int index1 = delegate.size() - 1;
        delegate.clear();
        if (index1 >= 0) {
            fireIntervalRemoved(this, 0, index1);
        }
    }

    public boolean contains(Object elem) {
        return delegate.contains(elem);
    }
    
    // Add other methods from DefaultListModel if needed, adapting them to Arraylist
    // For example: insertElementAt, setElementAt, etc.
     public E get(int index) {
        return delegate.get(index);
    }
}
