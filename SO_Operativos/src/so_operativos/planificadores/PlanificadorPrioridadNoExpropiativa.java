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
// Clase Prioridad No Expropiativa
public class PlanificadorPrioridadNoExpropiativa implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(Cola colaListos) {
        if (colaListos.isEmpty()) return null;
        Proceso[] lista = colaListos.toArray();
        Proceso masPrioritario = lista[0]; // Menor valor de prioridad = más alto
        for (int i = 1; i < lista.length; i++) {
            if (lista[i].getPrioridad() < masPrioritario.getPrioridad()) {
                masPrioritario = lista[i];
            }
        }
        return colaListos.remove(masPrioritario);
    }
    @Override
    public String getNombre() { return "5. Prioridad (No Expropiativa)"; }
}
