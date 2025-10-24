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
    }

    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore);
        colaListos.add(proceso);
        proceso.setEstado(EstadoProceso.LISTO);
    }

    public boolean quedanProcesos() {
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty();
    }

    public void ejecutarCicloSimulacion() {
        tiempoSimulacion += config.getDuracionCicloMs();
        System.out.println("\n--- Ciclo de Simulación @ " + tiempoSimulacion + "ms ---");

        // Lógica de Excepciones y Expropiación (se añadirán en etapas futuras)

        if (procesoActual == null) {
            planificarSiguiente();
        } else {
            // Lógica de Quantum (se añadirá en una etapa futura)
        }

        chequearEstadoEjecucion();
        mostrarEstado();

        try {
            Thread.sleep(config.getDuracionCicloMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
COMMIT 4

Archivos a añadir: src/so_operativos/Simulador.java

Mensaje de Commit: feat: Implementar núcleo del Simulador (Kernel) con bucle principal

Etapa 5: Interfaz de Usuario (El Menú Principal)
Objetivo: Crear el punto de entrada Main y una clase ConfiguracionSimulacion para que el simulador sea interactivo.

1. Archivo: ConfiguracionSimulacion.java (Nuevo)
Acción: Crear la clase de configuración.

Ruta: src/so_operativos/ConfiguracionSimulacion.java

Contenido:

Java

package so_operativos;

public class ConfiguracionSimulacion {
    private long duracionCicloMs = 100; // Valor por defecto

    public long getDuracionCicloMs() { return duracionCicloMs; }
    public void setDuracionCicloMs(long duracionCicloMs) { this.duracionCicloMs = duracionCicloMs; }

    public static ConfiguracionSimulacion cargarConfiguracion() {
        return new ConfiguracionSimulacion(); 
    }
}
