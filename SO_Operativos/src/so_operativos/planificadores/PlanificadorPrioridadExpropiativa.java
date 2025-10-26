/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos.planificadores;

import so_operativos.Cola;
import so_operativos.Planificador;
import so_operativos.Proceso;

/**
 *
 * @author Edgar
 */
// Clase Prioridad Expropiativa
 public class PlanificadorPrioridadExpropiativa implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(Cola colaListos) {
        // La selección es igual a Prioridad No Expropiativa; la expropiación ocurre en el Simulador
        if (colaListos.isEmpty()) return null;
        Proceso[] lista = colaListos.toArray();
        Proceso masPrioritario = lista[0];
        for (int i = 1; i < lista.length; i++) {
            if (lista[i].getPrioridad() < masPrioritario.getPrioridad()) {
                masPrioritario = lista[i];
            }
        }
        return colaListos.remove(masPrioritario);
    }
    @Override
    public String getNombre() { return "6. Prioridad (Expropiativa)"; }
}
