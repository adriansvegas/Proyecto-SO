/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package so_operativos;

/**
 *
 * @author adria
 */
public interface Planificador {
    Proceso seleccionarSiguiente(CustomQueue colaListos);
    String getNombre();
}
