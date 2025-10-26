    /*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package so_operativos;

/**
 *
 * @author adria
 */

/**
 * Interfaz que define el contrato para todos los algoritmos de planificación.
 * Permite al Simulador interactuar con diferentes políticas de forma polimórfica.
 */
public interface Planificador {
    /**
     * Selecciona y remueve el siguiente proceso a ejecutar de la cola de listos,
     * según la lógica específica del algoritmo implementado.
     * @param colaListos La cola de procesos en estado LISTO.
     * @return El proceso seleccionado para ejecutar, o null si la cola está vacía.
     */
    Proceso seleccionarSiguiente(Cola colaListos);

    /**
     * Devuelve el nombre descriptivo del algoritmo de planificación.
     * @return El nombre del planificador (ej. "1. FCFS (No Expropiativo)").
     */
    String getNombre();
}