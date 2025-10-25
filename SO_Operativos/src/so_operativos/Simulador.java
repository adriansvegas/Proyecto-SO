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
    private final ConfiguracionSimulacion config;
    private Planificador planificador;
    
    private final CustomQueue colaListos;
    
    // ConcurrentHashMap es la única estructura de java.util permitida para gestión de hilos/concurrencia
    private final ConcurrentHashMap<Integer, Thread> procesosEnExcepcion; 

    // REEMPLAZO: Arreglo simple para procesos terminados
    private Proceso[] procesosTerminadosArray;
    private int terminadosCount;
    private static final int INITIAL_CAPACITY = 10; // Capacidad inicial para el arreglo

    private Proceso procesoActual;
    private Thread procesoThread;

    private long tiempoSimulacion;
    private int ciclosQuantum;
    private final Semaphore cpuSemaphore;
    
    // Getters requeridos por Main.java
    
    public Planificador getPlanificador() {
        return this.planificador;
    }
    
    public ConfiguracionSimulacion getConfig() {
        return this.config;
    }

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

    public boolean quedanProcesos() {
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty();
    }

    public void ejecutarCicloSimulacion() {
        tiempoSimulacion += config.getDuracionCicloMs();
        System.out.println("\n--- Ciclo de Simulación @ " + tiempoSimulacion + "ms ---");

        manejarExcepciones();
        comprobarExpropiacion();

        if (procesoActual == null) {
            planificarSiguiente();
        } else {
            manejarQuantum(); 
        }

        chequearEstadoEjecucion();
        mostrarEstado();

        try {
            Thread.sleep(config.getDuracionCicloMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void manejarExcepciones() {
        // Remover hilos de excepción que han terminado
        procesosEnExcepcion.entrySet().removeIf(entry -> !entry.getValue().isAlive());
    }

    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return;
        
        if (procesoActual.haTerminado()) {
            procesoActual.setEstado(EstadoProceso.TERMINADO);
            
            // Redimensionar el arreglo si es necesario
            if (terminadosCount >= procesosTerminadosArray.length) {
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
        } else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
            // Genera una EXCEPCIÓN DE E/S
            if (procesoThread != null) procesoThread.interrupt(); 
            
            ManejadorExcepcion handler = new ManejadorExcepcion(
                procesoActual, 
                colaListos, 
                config.getDuracionCicloMs()
            );
            Thread handlerThread = new Thread(handler, "Excepción-" + procesoActual.getId());
            handlerThread.start();
            
            procesosEnExcepcion.put(procesoActual.getId(), handlerThread);
            System.out.println("🚨 EXCEPCIÓN: " + procesoActual.getNombre() + " genera E/S. En cola de excepción.");
            
            procesoActual = null;
            procesoThread = null;
            reordenarColaListos(null);
        }
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

    private void planificarSiguiente() {
        if (!colaListos.isEmpty()) {
            Proceso siguienteProceso;
            synchronized (colaListos) {
                siguienteProceso = planificador.seleccionarSiguiente(colaListos); 
            }
            
            if (siguienteProceso != null) {
                procesoActual = siguienteProceso;
                
                if (planificador instanceof PlanificadorRoundRobin rr) {
                    ciclosQuantum = rr.getQuantum();
                }

                procesoThread = new Thread(procesoActual);
                procesoThread.start();
                
                System.out.println("⬅️ CONTEXT SWITCH: Proceso " + procesoActual.getNombre() + " pasa a EJECUCIÓN.");
            }
        }
    }

    public void mostrarEstado() {
        System.out.println("\n--- VISUALIZACIÓN DE ESTADO DEL KERNEL ---");
        System.out.printf("   [SO] Tiempo: %dms | Política: %s%n", 
                             tiempoSimulacion, planificador.getNombre());
        
        System.out.println("\n## CPU (Proceso en Ejecución)");
        if (procesoActual != null) {
            procesoActual.mostrarPCB();
        } else {
            System.out.println("   [IDLE] CPU inactivo.");
        }

        System.out.println("\n## Cola de Listos (" + colaListos.size() + " procesos)");
        Proceso[] listosArray = colaListos.toArray();
        for(Proceso p : listosArray) {
            p.mostrarPCB();
        }

        System.out.println("\n## Manejador de Excepciones (E/S) (" + procesosEnExcepcion.size() + " hilos)");
        if (procesosEnExcepcion.isEmpty()) {
            System.out.println("   (Sin excepciones activas)");
        } else {
            // Mostrar los procesos en E/S (son aquellos que aún tienen su hilo activo)
            for (Integer id : procesosEnExcepcion.keySet()) {
                // Se necesitaría una lista auxiliar para buscar el proceso, pero evitamos estructuras no permitidas.
                // Simplemente listamos los IDs y asumimos que el ManejadorExcepcion ya mostró su estado.
                 System.out.printf("   [Hilo Excepción ID %d] Atendiendo E/S...%n", id);
            }
        }
        
        System.out.println("\n## Procesos Terminados (" + terminadosCount + " procesos)");
        for(int i = 0; i < terminadosCount; i++) {
             Proceso p = procesosTerminadosArray[i];
             System.out.printf("   [ID %d - %s | Pri:%d]%n", p.getId(), p.getNombre(), p.getPrioridad());
        }
        
        System.out.println("----------------------------------------");
    }
}