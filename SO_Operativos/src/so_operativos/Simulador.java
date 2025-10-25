/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;

/**
 *
 * @author adria
 */

import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;

public class Simulador {
    private ConfiguracionSimulacion config;
    private Planificador planificador;

    private final CustomQueue colaListos;

    // ConcurrentHashMap es la única estructura de java.util permitida
    private final ConcurrentHashMap<Integer, Thread> procesosEnExcepcion; 

    // Arreglo simple para procesos terminados
    private Proceso[] procesosTerminadosArray;
    private int terminadosCount;
    private static final int INITIAL_CAPACITY = 10;

    private Proceso procesoActual;
    private Thread procesoThread;

    private long tiempoSimulacion;
    private int ciclosQuantum; // Se usará en el futuro
    private final Semaphore cpuSemaphore; 

    // Getters (necesarios para el Main.java en la siguiente etapa)
    public Planificador getPlanificador() { return this.planificador; }
    public ConfiguracionSimulacion getConfig() { return this.config; }

    public Simulador(ConfiguracionSimulacion config, Planificador planificador) {
        this.config = config;
        this.planificador = planificador;
        this.colaListos = new CustomQueue(); 
        this.procesosEnExcepcion = new ConcurrentHashMap<>();
        this.procesosTerminadosArray = new Proceso[INITIAL_CAPACITY];
        this.terminadosCount = 0;
        this.procesoActual = null;
        this.procesoThread = null;
        this.tiempoSimulacion = 0;
        this.ciclosQuantum = 0;
        this.cpuSemaphore = new Semaphore(1);
    }

    public void setPlanificador(Planificador planificador) { 
        this.planificador = planificador; 
        System.out.println("✅ Política de planificación cambiada a: " + planificador.getNombre());
        reordenarColaListos(null);
    }

    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore);
        colaListos.add(proceso);
        proceso.setEstado(EstadoProceso.LISTO);
        reordenarColaListos(proceso);
    }

    public boolean quedanProcesos() {
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty();
    }

   public void ejecutarCicloSimulacion() {
    tiempoSimulacion += config.getDuracionCicloMs();
    System.out.println("\n--- Ciclo de Simulación @ " + tiempoSimulacion + "ms ---");

    // manejarExcepciones(); // Se añade en la próxima etapa
    comprobarExpropiacion(); // <--- AÑADIDO

    if (procesoActual == null) {
        planificarSiguiente();
    } else {
        manejarQuantum(); // <--- AÑADIDO
    }

    chequearEstadoEjecucion();
    mostrarEstado();
    // ...
}
   
   

    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return;

        if (procesoActual.haTerminado()) {
            procesoActual.setEstado(EstadoProceso.TERMINADO);

            if (terminadosCount >= procesosTerminadosArray.length) {
                // Redimensionar (simple)
                Proceso[] newArray = new Proceso[procesosTerminadosArray.length * 2];
                for (int i = 0; i < terminadosCount; i++) {
                    newArray[i] = procesosTerminadosArray[i];
                }
                procesosTerminadosArray = newArray;
            }
            procesosTerminadosArray[terminadosCount++] = procesoActual;

            if (procesoThread != null) procesoThread.interrupt(); 
            procesoActual = null;
            procesoThread = null;
            System.out.println("🛑 Proceso terminó.");
        } 
        // Lógica de E/S (se añadirá en una etapa futura)
    }

    private void planificarSiguiente() {
        if (!colaListos.isEmpty()) {
            Proceso siguienteProceso;
            synchronized (colaListos) {
                siguienteProceso = planificador.seleccionarSiguiente(colaListos); 
            }

            if (siguienteProceso != null) {
        procesoActual = siguienteProceso;

        if (planificador instanceof PlanificadorRoundRobin rr) { // <--- AÑADIDO
            ciclosQuantum = rr.getQuantum(); // <--- AÑADIDO
        }

        procesoThread = new Thread(procesoActual);

                System.out.println("⬅️ CONTEXT SWITCH: Proceso " + procesoActual.getNombre() + " pasa a EJECUCIÓN.");
            }
        }
    }
    
    private void reordenarColaListos(Proceso nuevoProceso) {
    if (planificador instanceof PlanificadorFCFS || planificador instanceof PlanificadorRoundRobin) return;

    Proceso[] lista = colaListos.toArray();
    int length = colaListos.size();

    // Algoritmo de ordenamiento Burbuja (sin librerías)
    for (int i = 0; i < length - 1; i++) {
        for (int j = 0; j < length - 1 - i; j++) {
            boolean swap = false;
            Proceso p1 = lista[j];
            Proceso p2 = lista[j+1];

            if (planificador instanceof PlanificadorSJF || planificador instanceof PlanificadorSRT) {
                if (p1.getInstruccionesRestantes() > p2.getInstruccionesRestantes()) {
                    swap = true;
                }
            } else { // Prioridad
                if (p1.getPrioridad() > p2.getPrioridad()) {
                    swap = true;
                }
            }

            if (swap) {
                Proceso temp = lista[j];
                lista[j] = lista[j+1];
                lista[j+1] = temp;
            }
        }
    }

    colaListos.rebuildFrom(lista, length); 
}
    
    private void comprobarExpropiacion() {
    if (procesoActual == null || colaListos.isEmpty()) return;

    boolean expropiar = false;
    Proceso candidato = colaListos.peek();

    if (planificador instanceof PlanificadorSRT) {
        if (candidato.getInstruccionesRestantes() < procesoActual.getInstruccionesRestantes()) expropiar = true;
    } else if (planificador instanceof PlanificadorPrioridadExpropiativa) {
        if (candidato.getPrioridad() < procesoActual.getPrioridad()) expropiar = true;
    }

    if (expropiar) {
        procesoActual.setEstado(EstadoProceso.LISTO);
        colaListos.add(procesoActual);

        if (procesoThread != null) procesoThread.interrupt(); 

        procesoActual = null;
        procesoThread = null;
        System.out.println("🚨 EXPROPIACIÓN: Proceso expropiado y movido a LISTO.");
        reordenarColaListos(null); // Asegurar el orden después de añadir el expropiado
    }
}
    
    private void manejarQuantum() {
    if (planificador instanceof PlanificadorRoundRobin rr) {
        ciclosQuantum--;
        if (ciclosQuantum <= 0) {
            procesoActual.setEstado(EstadoProceso.LISTO);
            colaListos.add(procesoActual);

            if (procesoThread != null) procesoThread.interrupt();

            procesoActual = null;
            procesoThread = null;
            System.out.println("⏱️ Quantum terminado. Proceso expropiado a LISTO.");
        }
    }
}
    
    

    public void mostrarEstado() {
        System.out.println("\n--- VISUALIZACIÓN DE ESTADO DEL KERNEL ---");
        System.out.printf("   [SO] Tiempo: %dms | Política: %s%n", 
                            tiempoSimulacion, planificador.getNombre());

        System.out.println("\n## CPU (Proceso en Ejecución)");
        if (procesoActual != null) {
            System.out.println("   [ID " + procesoActual.getId() + "] " + procesoActual.getNombre());
        } else {
            System.out.println("   [IDLE] CPU inactivo.");
        }

        System.out.println("\n## Cola de Listos (" + colaListos.size() + " procesos)");
        Proceso[] listosArray = colaListos.toArray();
        for(Proceso p : listosArray) {
            System.out.println("   [ID " + p.getId() + "] " + p.getNombre());
        }

        System.out.println("\n## Procesos Terminados (" + terminadosCount + " procesos)");
        for(int i = 0; i < terminadosCount; i++) {
            Proceso p = procesosTerminadosArray[i];
            System.out.printf("   [ID %d - %s | Pri:%d]%n", p.getId(), p.getNombre(), p.getPrioridad());
        }
        System.out.println("----------------------------------------");
    }
}