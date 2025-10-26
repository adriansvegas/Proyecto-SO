/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos.planificadores;

import so_operativos.CustomQueue;
import so_operativos.Planificador;
import so_operativos.Proceso;

/**
 *
 * @author Edgar
 */
// Clase FCFS
public class PlanificadorFCFS implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
        return colaListos.poll();
    }
    @Override
    public String getNombre() { return "1. FCFS (No Expropiativo)"; }
}
