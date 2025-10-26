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
// Clase SJF
public class PlanificadorSJF implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
        if (colaListos.isEmpty()) return null;
        Proceso[] lista = colaListos.toArray();
        Proceso masCorto = lista[0];
        for (int i = 1; i < lista.length; i++) {
            if (lista[i].getInstruccionesRestantes() < masCorto.getInstruccionesRestantes()) {
                masCorto = lista[i];
            }
        }
        return colaListos.remove(masCorto);
    }
    @Override
    public String getNombre() { return "2. SJF (No Expropiativo)"; }
}