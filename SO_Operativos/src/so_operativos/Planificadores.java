/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;

/**
 *
 * @author adria
 */
// Clase FCFS
class PlanificadorFCFS implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
        return colaListos.poll();
    }
    @Override
    public String getNombre() { return "1. FCFS (No Expropiativo)"; }
}

// Clase SJF
class PlanificadorSJF implements Planificador {
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

// Clase SRT (SJF Expropiativo)
class PlanificadorSRT implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
        // La selección es igual a SJF; la expropiación ocurre en el Simulador
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
    public String getNombre() { return "3. SRT (SJF Expropiativo)"; }
}

// Clase Round Robin
class PlanificadorRoundRobin implements Planificador {
    private final int quantum;
    public PlanificadorRoundRobin(int quantum) { this.quantum = quantum; }
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
        return colaListos.poll();
    }
    public int getQuantum() { return quantum; }
    @Override
    public String getNombre() { return "4. Round Robin (Expropiativo Q: " + quantum + ")"; }
}

// Clase Prioridad No Expropiativa
class PlanificadorPrioridadNoExpropiativa implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
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

// Clase Prioridad Expropiativa
class PlanificadorPrioridadExpropiativa implements Planificador {
    @Override
    public Proceso seleccionarSiguiente(CustomQueue colaListos) {
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
