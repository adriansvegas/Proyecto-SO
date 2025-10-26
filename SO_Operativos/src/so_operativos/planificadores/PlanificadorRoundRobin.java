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
// Clase Round Robin
public class PlanificadorRoundRobin implements Planificador {
    private final int quantum;
    public PlanificadorRoundRobin(int quantum) { this.quantum = quantum; }
    @Override
    public Proceso seleccionarSiguiente(Cola colaListos) {
        return colaListos.poll();
    }
    public int getQuantum() { return quantum; }
    @Override
    public String getNombre() { return "4. Round Robin (Expropiativo Q: " + quantum + ")"; }
}
