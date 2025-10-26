package so_operativos;

import so_operativos.planificadores.PlanificadorPrioridadExpropiativa;
import so_operativos.planificadores.PlanificadorRoundRobin;
import so_operativos.planificadores.PlanificadorFCFS;
import so_operativos.planificadores.PlanificadorSRT;
import so_operativos.planificadores.PlanificadorSJF;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import so_operativos.EstadoProceso;


/**
 * Núcleo lógico del simulador. Gestiona el ciclo de vida de los procesos,
 * las colas de estados, la interacción con el planificador y la persistencia del estado.
 */
public class Simulador {
    private final ConfiguracionSimulacion config; // Configuración (ej. duración ciclo)
    private Planificador planificador; // Algoritmo de planificación actual

    // Colas de estados de procesos
    private final CustomQueue colaListos;
    private final CustomQueue colaListosSuspendidos;
    private final CustomQueue colaBloqueadosSuspendidos;

    // Gestión de procesos bloqueados y sus hilos de E/S
    private final ConcurrentHashMap<Integer, Thread> procesosEnExcepcion; // Hilos activos de E/S
    private final ConcurrentHashMap<Integer, Proceso> mapaProcesosBloqueados; // Procesos en estado BLOQUEADO

    // Almacenamiento de procesos terminados (array obsoleto, usar lista)
    private Proceso[] procesosTerminadosArray;
    private int terminadosCount;
    private static final int INITIAL_CAPACITY = 10;

    public Proceso procesoActual; // Proceso actualmente en la CPU (null si idle)
    private transient Thread procesoThread; // Hilo del proceso en CPU (transient: no se guarda)

    private long tiempoSimulacion; // Tiempo simulado transcurrido (en ms)
    private int ciclosQuantum; // Quantum restante para RR
    private final transient Semaphore cpuSemaphore; // Semáforo para controlar acceso a la CPU (1 permiso)

    // Constantes de configuración
    public static final int UMBRAL_PROCESOS_MEMORIA = 5; // Máximo procesos en Listo + Bloqueado
    public static final String ESTADO_FILE = "sim_estado.txt"; // Archivo para guardar/cargar estado

    // Variables para cálculo de métricas
    private long tiempoInicioSimulacionReal; // Tiempo real de inicio
    private long tiempoTotalCpuOcupado; // Tiempo real acumulado de CPU usada
    private long tiempoInicioUsoCpuActual; // Timestamp real de cuándo empezó a usarse la CPU por el proceso actual
    private List<Proceso> listaProcesosCompletados; // Lista para almacenar procesos terminados

    /**
     * Constructor del Simulador. Inicializa colas, semáforos y carga el estado si existe.
     * @param config La configuración inicial de la simulación.
     * @param planificadorInicial El planificador a usar si no se carga estado.
     */
    public Simulador(ConfiguracionSimulacion config, Planificador planificadorInicial) {
        Logger.init();
        Logger.log("Inicializando Simulador...");
        this.config = config;
        this.colaListos = new CustomQueue();
        this.colaListosSuspendidos = new CustomQueue();
        this.colaBloqueadosSuspendidos = new CustomQueue();
        this.procesosEnExcepcion = new ConcurrentHashMap<>();
        this.mapaProcesosBloqueados = new ConcurrentHashMap<>();
        this.procesosTerminadosArray = new Proceso[INITIAL_CAPACITY];
        this.terminadosCount = 0;
        this.procesoActual = null;
        this.procesoThread = null;
        this.tiempoSimulacion = 0;
        this.ciclosQuantum = 0;
        this.cpuSemaphore = new Semaphore(1);
        this.tiempoInicioSimulacionReal = System.currentTimeMillis();
        this.tiempoTotalCpuOcupado = 0;
        this.tiempoInicioUsoCpuActual = 0;
        this.listaProcesosCompletados = new ArrayList<>();

        // Intenta cargar estado, si falla, usa el planificador inicial
        if (!cargarEstado()) {
            Logger.log("No se cargó estado previo. Iniciando simulación nueva.");
            this.planificador = planificadorInicial;
        } else {
            Logger.log("Estado cargado desde " + ESTADO_FILE);
            asignarSemaforoAProcesos(); // Reasigna semáforo a objetos Proceso deserializados
            reiniciarHilosPostCarga(); // Reinicia hilos de E/S y CPU si estaban activos
             if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) {
                tiempoInicioUsoCpuActual = System.currentTimeMillis(); // Inicia cómputo de uso CPU
             }
        }
         Logger.log("Simulador listo. Planificador: " + (this.planificador != null ? this.planificador.getNombre() : "Ninguno"));
    }

    // --- Getters para acceso desde la GUI ---
    public Planificador getPlanificador() { return this.planificador; }
    public ConfiguracionSimulacion getConfig() { return this.config; }
    public CustomQueue getColaListos() { return colaListos; }
    public java.util.Map<Integer, Proceso> getMapaProcesosBloqueados() { return mapaProcesosBloqueados; }
    public CustomQueue getColaListosSuspendidos() { return colaListosSuspendidos; }
    public CustomQueue getColaBloqueadosSuspendidos() { return colaBloqueadosSuspendidos; }
    public List<Proceso> getListaProcesosCompletados() { return this.listaProcesosCompletados; }
    public long getTiempoSimulacion() { return tiempoSimulacion; }
    public int getCiclosQuantum() { return ciclosQuantum; }

    /** Cambia el algoritmo de planificación en tiempo de ejecución. */
    public void setPlanificador(Planificador planificador) {
        Planificador anterior = this.planificador;
        this.planificador = planificador;
        Logger.log("Cambio de Planificador: " + (anterior != null ? anterior.getNombre() : "Ninguno") + " -> " + (planificador != null ? planificador.getNombre() : "Ninguno"));
        reordenarColaListos(null); // Reordena la cola de listos si aplica (SJF, Prioridad, etc.)
    }

    /**
     * Agrega un nuevo proceso al sistema. Decide si va a Listo o Suspendido-Listo
     * basado en el umbral de memoria.
     */
    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore); // Asigna el semáforo de CPU al proceso
        Logger.log("Solicitud para agregar Proceso " + proceso.getId() + " (" + proceso.getNombre() + ")");
        // Planificador a Largo Plazo + Mediano Plazo inicial (admisión)
        if (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             colaListos.add(proceso);
             proceso.setEstado(EstadoProceso.LISTO);
             Logger.log("   -> Proceso " + proceso.getId() + " admitido a LISTO.");
             reordenarColaListos(proceso);
        } else {
            colaListosSuspendidos.add(proceso);
            proceso.setEstado(EstadoProceso.SUSPENDIDO_LISTO);
            Logger.log("   -> Proceso " + proceso.getId() + " enviado a SUSPENDIDO_LISTO (Memoria llena).");
        }
        revisarYSuspenderSiNecesario(); // Asegura no exceder el umbral después de agregar
    }

    // --- Gestión de Suspensión/Reanudación (Planificador a Mediano Plazo) ---

    /** Planificador a Mediano Plazo: Suspende procesos si se excede el umbral de memoria. */
    private void revisarYSuspenderSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() > UMBRAL_PROCESOS_MEMORIA) {
             Proceso aSuspender = null; EstadoProceso nuevoEstado = null; CustomQueue origenCola = null; boolean origenMapa = false;
             // Prioridad: Menos prioritario en Listo, luego menos prioritario en Bloqueado
             if (!colaListos.isEmpty()) { aSuspender = buscarProcesoMenosPrioritario(colaListos); origenCola = colaListos; nuevoEstado = EstadoProceso.SUSPENDIDO_LISTO; origenMapa = false; }
             else if (!mapaProcesosBloqueados.isEmpty()) { aSuspender = buscarProcesoMenosPrioritarioEnMapa(mapaProcesosBloqueados); origenCola = null; nuevoEstado = EstadoProceso.SUSPENDIDO_BLOQUEADO; origenMapa = true; }

             if (aSuspender != null) {
                 Logger.log("SUSPENSIÓN (Mediano Plazo): Proceso " + aSuspender.getId() + " (" + aSuspender.getNombre() + ") por memoria llena.");
                 if (!origenMapa) { colaListos.remove(aSuspender); aSuspender.setEstado(nuevoEstado); colaListosSuspendidos.add(aSuspender); }
                 else { mapaProcesosBloqueados.remove(aSuspender.getId()); Thread hiloExcepcion = procesosEnExcepcion.remove(aSuspender.getId()); if (hiloExcepcion != null && hiloExcepcion.isAlive()) { hiloExcepcion.interrupt(); Logger.log("   -> Hilo E/S para " + aSuspender.getId() + " interrumpido por suspensión."); } aSuspender.setEstado(nuevoEstado); colaBloqueadosSuspendidos.add(aSuspender); }
             } else { Logger.log("Advertencia: Se necesita suspender pero no se encontró candidato."); break; }
         }
    }

    /** Planificador a Mediano Plazo: Reanuda procesos suspendidos si hay espacio en memoria. */
    private void revisarYReanudarSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             Proceso aReanudar = null; EstadoProceso estadoDestino = null;
             // Prioridad: Más prioritario Suspendido-Listo, luego más prioritario Suspendido-Bloqueado
             if (!colaListosSuspendidos.isEmpty()) { aReanudar = buscarProcesoMasPrioritario(colaListosSuspendidos); if (aReanudar != null) { colaListosSuspendidos.remove(aReanudar); estadoDestino = EstadoProceso.LISTO; colaListos.add(aReanudar); } }
             else if (!colaBloqueadosSuspendidos.isEmpty()) { aReanudar = buscarProcesoMasPrioritario(colaBloqueadosSuspendidos); if (aReanudar != null) { colaBloqueadosSuspendidos.remove(aReanudar); estadoDestino = EstadoProceso.BLOQUEADO; mapaProcesosBloqueados.put(aReanudar.getId(), aReanudar); reanudarManejadorExcepcion(aReanudar); } }

             if (aReanudar != null) { Logger.log("REANUDACIÓN (Mediano Plazo): Proceso " + aReanudar.getId() + " (" + aReanudar.getNombre() + ") reanudado a " + estadoDestino + "."); aReanudar.setEstado(estadoDestino); if (estadoDestino == EstadoProceso.LISTO) { reordenarColaListos(aReanudar); } }
             else { break; }
         }
    }

    // --- Métodos auxiliares para buscar procesos por prioridad ---
    private Proceso buscarProcesoMenosPrioritario(CustomQueue queue) { if (queue.isEmpty()) return null; Proceso[] array = queue.toArray(); Proceso peor = array[0]; for (int i = 1; i < array.length; i++) { if (array[i] != null && array[i].getPrioridad() >= peor.getPrioridad()) { peor = array[i]; } } return peor; }
    private Proceso buscarProcesoMenosPrioritarioEnMapa(ConcurrentHashMap<Integer, Proceso> map) { if (map.isEmpty()) return null; Proceso peor = null; for (Proceso p : map.values()) { if (peor == null || p.getPrioridad() >= peor.getPrioridad()) { peor = p; } } return peor; }
    private Proceso buscarProcesoMasPrioritario(CustomQueue queue) { if (queue.isEmpty()) return null; Proceso[] array = queue.toArray(); Proceso mejor = array[0]; for (int i = 1; i < array.length; i++) { if (array[i] != null && array[i].getPrioridad() < mejor.getPrioridad()) { mejor = array[i]; } } return mejor; }

    /** Reinicia el hilo manejador de E/S para un proceso que fue reanudado desde suspendido-bloqueado. */
    private void reanudarManejadorExcepcion(Proceso proceso) {
        if (proceso.getEstado() != EstadoProceso.BLOQUEADO) { proceso.setEstado(EstadoProceso.BLOQUEADO); }
        Logger.log("Reiniciando ManejadorExcepcion para Proceso " + proceso.getId() + " (E/S restante).");
        ManejadorExcepcion handler = new ManejadorExcepcion(proceso, colaListos, config.getDuracionCicloMs());
        Thread handlerThread = new Thread(handler, "Excepción-" + proceso.getId() + "-Reanudado");
        handlerThread.start(); procesosEnExcepcion.put(proceso.getId(), handlerThread);
    }

    /** Reordena la cola de listos según el criterio del planificador actual (si no es FCFS o RR). */
    private void reordenarColaListos(Proceso nuevoProceso) {
        if (planificador instanceof PlanificadorFCFS || planificador instanceof PlanificadorRoundRobin) { return; }
        Proceso[] lista = colaListos.toArray(); int length = colaListos.size(); if (length <= 1) return;
        boolean swapped;
        for (int i = 0; i < length - 1; i++) {
            swapped = false;
            for (int j = 0; j < length - 1 - i; j++) {
                boolean swapCondition = false; Proceso p1 = lista[j]; Proceso p2 = lista[j+1];
                if (p1 == null || p2 == null) continue; // Salta si hay nulls (precaución)
                if (planificador instanceof PlanificadorSJF || planificador instanceof PlanificadorSRT) { if (p1.getInstruccionesRestantes() > p2.getInstruccionesRestantes()) swapCondition = true; }
                else { if (p1.getPrioridad() > p2.getPrioridad()) swapCondition = true; }
                if (swapCondition) { Proceso temp = lista[j]; lista[j] = lista[j+1]; lista[j+1] = temp; swapped = true; }
            }
             if (!swapped) break;
        }
        colaListos.rebuildFrom(lista, length);
    }

    /** Verifica si aún quedan procesos activos o pendientes en el sistema. */
    public boolean quedanProcesos() { return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty() || !colaListosSuspendidos.isEmpty() || !colaBloqueadosSuspendidos.isEmpty(); }

    /**
     * Ejecuta un ciclo completo de la simulación.
     * Incluye manejo de E/S, planificación a mediano y corto plazo, y ejecución del proceso.
     */
    public void ejecutarCicloSimulacion() {
        long inicioCicloReal = System.currentTimeMillis();
        tiempoSimulacion += config.getDuracionCicloMs();
        Logger.log("Inicio Ciclo " + (tiempoSimulacion / config.getDuracionCicloMs()) + " @ Tiempo Simulado " + tiempoSimulacion + "ms");

        if (procesoActual != null && tiempoInicioUsoCpuActual > 0) { tiempoTotalCpuOcupado += (inicioCicloReal - tiempoInicioUsoCpuActual); tiempoInicioUsoCpuActual = 0; }

        manejarExcepciones(); // 1. Manejar fin de E/S
        comprobarExpropiacion(); // 2. Comprobar expropiación por prioridad/SRT
        revisarYSuspenderSiNecesario(); // 3a. Mediano Plazo: Suspender si hay exceso
        revisarYReanudarSiNecesario(); // 3b. Mediano Plazo: Reanudar si hay espacio

        // 4. Corto Plazo: Seleccionar o continuar proceso
        if (procesoActual == null) { Logger.log("CPU Idle. Intentando planificar..."); planificarSiguiente(); }
        else { tiempoInicioUsoCpuActual = inicioCicloReal; manejarQuantum(); } // Maneja quantum para RR

        chequearEstadoEjecucion(); // 5. Verificar si proceso actual terminó o bloqueó
        mostrarEstado(); // 6. (Opcional) Muestra estado en consola

        // Sincronización con tiempo real
        long finCicloLogicoReal = System.currentTimeMillis(); long duracionLogica = finCicloLogicoReal - inicioCicloReal; long tiempoDormir = config.getDuracionCicloMs() - duracionLogica;
        if (tiempoDormir > 0) { try { Thread.sleep(tiempoDormir); } catch (InterruptedException e) { Logger.log("WARN: Sleep del ciclo principal interrumpido."); Thread.currentThread().interrupt(); } }
        if (procesoActual != null && tiempoInicioUsoCpuActual > 0) { long t = System.currentTimeMillis(); tiempoTotalCpuOcupado += (t - inicioCicloReal); tiempoInicioUsoCpuActual = t; } else { tiempoInicioUsoCpuActual = 0; }
    }

    /** Verifica hilos de E/S terminados y mueve los procesos correspondientes a Listo. */
    private void manejarExcepciones() {
        procesosEnExcepcion.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                Proceso p = mapaProcesosBloqueados.remove(entry.getKey());
                if (p != null) { Logger.log("Hilo E/S para Proceso " + p.getId() + " terminado. Proceso debería estar en LISTO."); revisarYReanudarSiNecesario(); }
                else { Logger.log("WARN: Hilo E/S para ID " + entry.getKey() + " terminado, pero proceso no encontrado en mapa Bloqueados (¿Suspendido?)."); }
                return true; // Elimina entrada del mapa de hilos
            }
            return false; // Mantiene si el hilo sigue vivo
        });
    }

    /** Verifica si el proceso actual ha terminado o ha entrado en estado de bloqueo por E/S. */
    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return;

        if (procesoActual.haTerminado()) { // Proceso completó todas sus instrucciones
            Proceso terminado = procesoActual;
            terminado.setEstado(EstadoProceso.TERMINADO);
            Logger.log("PROCESO TERMINADO: ID=" + terminado.getId() + ", Nombre=" + terminado.getNombre() + ", Tiempo Retorno=" + terminado.getTiempoRetorno() + "ms");
            listaProcesosCompletados.add(terminado); // Añade a la lista para métricas
            if (procesoThread != null) procesoThread.interrupt(); // Libera CPU
            procesoActual = null; procesoThread = null;
            revisarYReanudarSiNecesario(); // Revisa si se puede reanudar algo
        }
        else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) { // Proceso solicitó E/S
             Proceso bloqueado = procesoActual;
             if (procesoThread != null) procesoThread.interrupt(); // Libera CPU
             procesoActual = null; procesoThread = null;
             Logger.log("E/S Requerida: Proceso " + bloqueado.getId() + " (" + bloqueado.getNombre() + ")");

             // Decide si va a Bloqueado activo o Suspendido-Bloqueado
             if (colaListos.size() + mapaProcesosBloqueados.size() + 1 <= UMBRAL_PROCESOS_MEMORIA) {
                 bloqueado.setEstado(EstadoProceso.BLOQUEADO); mapaProcesosBloqueados.put(bloqueado.getId(), bloqueado);
                 ManejadorExcepcion handler = new ManejadorExcepcion(bloqueado, colaListos, config.getDuracionCicloMs()); // Crea manejador E/S
                 Thread handlerThread = new Thread(handler, "Excepción-" + bloqueado.getId()); handlerThread.start(); procesosEnExcepcion.put(bloqueado.getId(), handlerThread); // Registra hilo
                 Logger.log("   -> Proceso " + bloqueado.getId() + " pasa a BLOQUEADO.");
             } else {
                 bloqueado.setEstado(EstadoProceso.SUSPENDIDO_BLOQUEADO); colaBloqueadosSuspendidos.add(bloqueado); // Va a suspendido
                 Logger.log("   -> Proceso " + bloqueado.getId() + " pasa a SUSPENDIDO_BLOQUEADO (Memoria llena).");
             }
             revisarYReanudarSiNecesario(); revisarYSuspenderSiNecesario(); // Revisa mediano plazo
        }
    }

    /** Verifica si el planificador actual requiere expropiación (SRT, Prioridad Expropiativa). */
    private void comprobarExpropiacion() {
        if (procesoActual == null || colaListos.isEmpty()) return;
        boolean expropiar = false; Proceso candidato = colaListos.peek(); if (candidato == null) return;

        if (planificador instanceof PlanificadorSRT) { if (candidato.getInstruccionesRestantes() < procesoActual.getInstruccionesRestantes()) expropiar = true; }
        else if (planificador instanceof PlanificadorPrioridadExpropiativa) { if (candidato.getPrioridad() < procesoActual.getPrioridad()) expropiar = true; }

        if (expropiar) {
            Logger.log("EXPROPIACIÓN: Proceso " + candidato.getId() + " (" + candidato.getNombre() + ") expropia a " + procesoActual.getId() + " (" + procesoActual.getNombre() + ").");
            Proceso expropiado = procesoActual;
            if (procesoThread != null) procesoThread.interrupt(); // Libera CPU
            procesoActual = null; procesoThread = null;
            expropiado.setEstado(EstadoProceso.LISTO); colaListos.add(expropiado); // Devuelve a Listo
            reordenarColaListos(expropiado);
        }
    }

    /** Maneja la lógica del quantum para el algoritmo Round Robin. */
    private void manejarQuantum() {
        if (procesoActual != null && planificador instanceof PlanificadorRoundRobin rr) {
            ciclosQuantum--;
            if (ciclosQuantum <= 0) { // Quantum expirado
                 Logger.log("QUANTUM FIN: Proceso " + procesoActual.getId() + " (" + procesoActual.getNombre() + ") vuelve a LISTO.");
                 Proceso expropiado = procesoActual;
                 if (procesoThread != null) procesoThread.interrupt(); // Libera CPU
                 procesoActual = null; procesoThread = null;
                 expropiado.setEstado(EstadoProceso.LISTO); colaListos.add(expropiado); // Devuelve al final de Listo
                 revisarYSuspenderSiNecesario(); revisarYReanudarSiNecesario(); // Revisa mediano plazo
            }
        }
    }

    /** Planificador a Corto Plazo: Selecciona el siguiente proceso a ejecutar si la CPU está libre. */
    private void planificarSiguiente() {
        if (procesoActual == null && !colaListos.isEmpty()) {
            Proceso siguienteProceso = planificador.seleccionarSiguiente(colaListos); // Pide al planificador
            if (siguienteProceso != null) {
                procesoActual = siguienteProceso; // Asigna a CPU
                Logger.log("CONTEXT SWITCH: Proceso " + procesoActual.getId() + " (" + procesoActual.getNombre() + ") seleccionado para ejecución.");
                procesoActual.setEstado(EstadoProceso.EJECUCION);
                if (planificador instanceof PlanificadorRoundRobin rr) { ciclosQuantum = rr.getQuantum(); Logger.log("   -> Quantum asignado: " + ciclosQuantum); } else { ciclosQuantum = 0; }
                tiempoInicioUsoCpuActual = System.currentTimeMillis(); // Inicia cómputo uso CPU
                procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId()); procesoThread.start(); // Lanza hilo del proceso
            } else { Logger.log("Planificador no seleccionó proceso. CPU Idle."); }
        }
    }

    /** Muestra el estado actual de todas las colas y la CPU en la consola estándar (para debugging). */
    public void mostrarEstado() { System.out.println("\n--- VISUALIZACIÓN DE ESTADO DEL KERNEL ---"); String n = (planificador != null) ? planificador.getNombre() : "N/A"; System.out.printf("   [SO] Tiempo: %dms | Política: %s | Ciclo: %dms%n", tiempoSimulacion, n, config.getDuracionCicloMs()); System.out.printf("   [Memoria] Procesos Activos (Listo+Bloq): %d/%d%n", colaListos.size() + mapaProcesosBloqueados.size(), UMBRAL_PROCESOS_MEMORIA); System.out.println("\n## CPU (Proceso en Ejecución)"); if (procesoActual != null) { procesoActual.mostrarPCB(); if (planificador instanceof PlanificadorRoundRobin) System.out.println("     Quantum restante: " + ciclosQuantum); } else System.out.println("   [IDLE] CPU inactivo."); System.out.println("\n## Cola de Listos (" + colaListos.size() + " procesos)"); Proceso[] l = colaListos.toArray(); for(Proceso p:l) if(p!=null) p.mostrarPCB(); if(l.length == 0) System.out.println("   (Vacía)"); System.out.println("\n## Cola de Bloqueados (" + mapaProcesosBloqueados.size() + " procesos / " + procesosEnExcepcion.size() + " hilos E/S)"); if (mapaProcesosBloqueados.isEmpty()) System.out.println("   (Vacía)"); else for (Proceso p : mapaProcesosBloqueados.values()) if(p!=null) p.mostrarPCB(); System.out.println("\n## Cola de Listos Suspendidos (" + colaListosSuspendidos.size() + " procesos)"); Proceso[] ls = colaListosSuspendidos.toArray(); for(Proceso p:ls) if(p!=null) p.mostrarPCB(); if(ls.length == 0) System.out.println("   (Vacía)"); System.out.println("\n## Cola de Bloqueados Suspendidos (" + colaBloqueadosSuspendidos.size() + " procesos)"); Proceso[] bs = colaBloqueadosSuspendidos.toArray(); for(Proceso p:bs) if(p!=null) p.mostrarPCB(); if(bs.length == 0) System.out.println("   (Vacía)"); System.out.println("\n## Procesos Terminados (" + listaProcesosCompletados.size() + " procesos)"); for(Proceso p:listaProcesosCompletados) if(p!=null) System.out.printf("   [ID %d - %s | Pri:%d | T.Retorno:%dms | T.Espera:%dms | T.Bloq:%dms]%n", p.getId(), p.getNombre(), p.getPrioridad(), p.getTiempoRetorno(), p.getTiempoTotalEsperandoListo(), p.getTiempoTotalBloqueado()); if(listaProcesosCompletados.isEmpty()) System.out.println("   (Ninguno)"); System.out.println("----------------------------------------"); }

    /** Guarda el estado actual de la simulación en un archivo de texto. */
    public boolean guardarEstado() { /* ... código sin cambios ... */
        Logger.log("GUARDANDO ESTADO...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ESTADO_FILE, false))) {
            writer.write("TIEMPO," + tiempoSimulacion); writer.newLine();
            String planificadorName = (planificador != null) ? planificador.getClass().getName() : "null";
            writer.write("PLANIFICADOR," + planificadorName); writer.newLine();
            writer.write("PROCESO_ACTUAL," + (procesoActual != null ? procesoActual.getId() : "IDLE")); writer.newLine();
            writer.write("QUANTUM_RESTANTE," + ciclosQuantum); writer.newLine();
            writer.write("NEXT_ID," + Proceso.getNextId()); writer.newLine();
            guardarCola(writer, "LISTO", colaListos);
            guardarCola(writer, "LISTO_SUSP", colaListosSuspendidos);
            guardarCola(writer, "BLOQ_SUSP", colaBloqueadosSuspendidos);
            guardarMapaBloqueados(writer, "BLOQUEADO", mapaProcesosBloqueados);
            guardarListaTerminados(writer, "TERMINADO", listaProcesosCompletados);
            Logger.log("Estado guardado exitosamente."); return true;
        } catch (IOException e) { Logger.log("ERROR al guardar estado: " + e.getMessage()); System.err.println("❌ Error al guardar el estado: " + e.getMessage()); return false; }
    }
    private void guardarCola(BufferedWriter writer, String prefix, CustomQueue queue) throws IOException { Proceso[] array = queue.toArray(); for (Proceso p : array) { if(p!=null) {writer.write(prefix + "," + p.toStringData()); writer.newLine();} } }
    private void guardarMapaBloqueados(BufferedWriter writer, String prefix, ConcurrentHashMap<Integer, Proceso> map) throws IOException { for (Proceso p : map.values()) { if(p!=null) { writer.write(prefix + "," + p.toStringData()); writer.newLine(); } } }
    private void guardarListaTerminados(BufferedWriter writer, String prefix, List<Proceso> lista) throws IOException { for (Proceso p : lista) { if(p!=null) { writer.write(prefix + "," + p.toStringData()); writer.newLine(); } } }

    /** Carga el estado de la simulación desde un archivo de texto. */
    public boolean cargarEstado() { /* ... código sin cambios ... */
        Logger.log("CARGANDO ESTADO desde " + ESTADO_FILE + "...");
        List<Proceso> tempListos = new ArrayList<>(); List<Proceso> tempListosSusp = new ArrayList<>(); List<Proceso> tempBloqueados = new ArrayList<>(); List<Proceso> tempBloqSusp = new ArrayList<>(); List<Proceso> tempTerminados = new ArrayList<>(); int idProcesoActual = -1; String nombrePlanificador = null; int quantumRestanteCargado = 0; int nextIdCargado = 1;
        try (BufferedReader reader = new BufferedReader(new FileReader(ESTADO_FILE))) {
            String line; int maxId = 0;
            while ((line = reader.readLine()) != null) { if (line.trim().isEmpty()) continue; String[] parts = line.split(",", 2); if (parts.length < 2) continue; String prefix = parts[0].trim(); String data = parts[1].trim(); try { switch (prefix) { case "TIEMPO": tiempoSimulacion = Long.parseLong(data); break; case "PLANIFICADOR": nombrePlanificador = data.equals("null") ? null : data; break; case "PROCESO_ACTUAL": idProcesoActual = data.equals("IDLE") ? -1 : Integer.parseInt(data); break; case "QUANTUM_RESTANTE": quantumRestanteCargado = Integer.parseInt(data); break; case "NEXT_ID": nextIdCargado = Integer.parseInt(data); break; case "LISTO": case "BLOQUEADO": case "LISTO_SUSP": case "BLOQ_SUSP": case "TERMINADO": Proceso p = Proceso.fromStringData(data); if (p.getId() > maxId) maxId = p.getId(); switch (prefix) { case "LISTO": tempListos.add(p); break; case "BLOQUEADO": tempBloqueados.add(p); break; case "LISTO_SUSP": tempListosSusp.add(p); break; case "BLOQ_SUSP": tempBloqSusp.add(p); break; case "TERMINADO": tempTerminados.add(p); break; } break; default: Logger.log("WARN: Prefijo desconocido: " + prefix); break; } } catch (Exception e) { Logger.log("ERROR parseando línea (" + prefix + "): " + line + " | Error: " + e.getMessage()); } }
            limpiarEstadoSimulador(); Proceso.resetNextId(Math.max(nextIdCargado, maxId + 1));
            if (nombrePlanificador != null) { try { if (nombrePlanificador.contains("PlanificadorRoundRobin")) { this.planificador = new PlanificadorRoundRobin(Main.DEFAULT_QUANTUM); this.ciclosQuantum = quantumRestanteCargado; } else { Class<?> clazz = Class.forName(nombrePlanificador); this.planificador = (Planificador) clazz.getDeclaredConstructor().newInstance(); } Logger.log("   Planificador cargado: " + this.planificador.getNombre()); } catch (Exception e) { Logger.log("ERROR al instanciar Planificador " + nombrePlanificador + ". Usando FCFS."); this.planificador = new PlanificadorFCFS(); } } else { Logger.log("WARN: Planificador no encontrado. Usando FCFS."); this.planificador = new PlanificadorFCFS(); }
            colaListos.rebuildFrom(tempListos.toArray(new Proceso[0]), tempListos.size()); colaListosSuspendidos.rebuildFrom(tempListosSusp.toArray(new Proceso[0]), tempListosSusp.size()); colaBloqueadosSuspendidos.rebuildFrom(tempBloqSusp.toArray(new Proceso[0]), tempBloqSusp.size()); for (Proceso p : tempBloqueados) mapaProcesosBloqueados.put(p.getId(), p);
            listaProcesosCompletados.addAll(tempTerminados); terminadosCount = 0; if (listaProcesosCompletados.size() > procesosTerminadosArray.length) procesosTerminadosArray = new Proceso[listaProcesosCompletados.size()]; for(Proceso p : listaProcesosCompletados) if (terminadosCount < procesosTerminadosArray.length) procesosTerminadosArray[terminadosCount++] = p;
            procesoActual = buscarProcesoPorId(idProcesoActual, tempListos, tempBloqueados, tempListosSusp, tempBloqSusp, tempTerminados); if (procesoActual != null) { if(procesoActual.getEstado() == EstadoProceso.LISTO || procesoActual.getEstado() == EstadoProceso.EJECUCION){ procesoActual.setEstado(EstadoProceso.EJECUCION); colaListos.remove(procesoActual); } else if (procesoActual.getEstado() != EstadoProceso.BLOQUEADO) { Logger.log("WARN: Proceso actual cargado ("+procesoActual.getId()+") estado "+procesoActual.getEstado()+". CPU IDLE."); procesoActual = null; } Logger.log("   Proceso actual cargado: ID " + (procesoActual != null ? procesoActual.getId() : "IDLE")); } else if (idProcesoActual != -1) Logger.log("ERROR: Proceso actual ID " + idProcesoActual + " no encontrado.");
            Logger.log("Carga de estado completada."); return true;
        } catch (IOException e) { Logger.log("ERROR al leer archivo de estado " + ESTADO_FILE + ": " + e.getMessage()); return false; } catch (Exception e) { Logger.log("ERROR inesperado durante carga: " + e.getMessage()); e.printStackTrace(); return false; }
    }
    private Proceso buscarProcesoPorId(int id, List<Proceso>... listas) { if (id == -1) return null; for (List<Proceso> lista : listas) for (Proceso p : lista) if (p.getId() == id) return p; if (mapaProcesosBloqueados.containsKey(id)) return mapaProcesosBloqueados.get(id); return null; }
    private void limpiarEstadoSimulador() { /* ... código sin cambios ... */ if (procesoThread != null && procesoThread.isAlive()) procesoThread.interrupt(); for (Thread t : procesosEnExcepcion.values()) if (t != null && t.isAlive()) t.interrupt(); colaListos.rebuildFrom(new Proceso[0], 0); colaListosSuspendidos.rebuildFrom(new Proceso[0], 0); colaBloqueadosSuspendidos.rebuildFrom(new Proceso[0], 0); procesosEnExcepcion.clear(); mapaProcesosBloqueados.clear(); procesosTerminadosArray = new Proceso[INITIAL_CAPACITY]; terminadosCount = 0; listaProcesosCompletados.clear(); procesoActual = null; procesoThread = null; tiempoSimulacion = 0; ciclosQuantum = 0; tiempoTotalCpuOcupado = 0; tiempoInicioUsoCpuActual = 0; tiempoInicioSimulacionReal = System.currentTimeMillis(); }

    /** Reasigna la referencia del semáforo a todos los procesos después de cargar desde archivo. */
    public void asignarSemaforoAProcesos() { asignarSemaforoEnCola(colaListos); asignarSemaforoEnCola(colaListosSuspendidos); asignarSemaforoEnCola(colaBloqueadosSuspendidos); for(Proceso p : mapaProcesosBloqueados.values()) p.setCpuSemaphore(this.cpuSemaphore); if (procesoActual != null) procesoActual.setCpuSemaphore(this.cpuSemaphore); }
    private void asignarSemaforoEnCola(CustomQueue queue) { Proceso[] array = queue.toArray(); for (Proceso p : array) if(p!=null) p.setCpuSemaphore(this.cpuSemaphore); }

    /** Reinicia los hilos de E/S y el hilo del proceso en CPU si estaban activos al guardar. */
    public void reiniciarHilosPostCarga() { /* ... código sin cambios ... */ Logger.log("Reiniciando hilos post-carga..."); ConcurrentHashMap<Integer, Proceso> copiaMapaBloqueados = new ConcurrentHashMap<>(mapaProcesosBloqueados); for (Proceso p : copiaMapaBloqueados.values()) if (p.getEstado() == EstadoProceso.BLOQUEADO) reanudarManejadorExcepcion(p); if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) { Logger.log("   -> Reiniciando hilo CPU para Proceso " + procesoActual.getId()); tiempoInicioUsoCpuActual = System.currentTimeMillis(); procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId() + "-Reanudado"); procesoThread.start(); } }

    /** Calcula y muestra las métricas de rendimiento en la consola estándar. */
    public void calcularYMostrarMetricas() { /* ... código sin cambios ... */ if (listaProcesosCompletados.isEmpty()) { System.out.println("\n--- MÉTRICAS DE RENDIMIENTO ---"); System.out.println("   No hay procesos completados."); return; } long tiempoTotalSimulacionReal = System.currentTimeMillis() - tiempoInicioSimulacionReal; if (tiempoTotalSimulacionReal <= 0) tiempoTotalSimulacionReal = 1; double tiempoTotalSegundos = tiempoTotalSimulacionReal / 1000.0; double throughput = (tiempoTotalSegundos > 0) ? listaProcesosCompletados.size() / tiempoTotalSegundos : 0; if (procesoActual != null && tiempoInicioUsoCpuActual > 0) { tiempoTotalCpuOcupado += (System.currentTimeMillis() - tiempoInicioUsoCpuActual); tiempoInicioUsoCpuActual = 0; } double utilizacionCpu = (double) tiempoTotalCpuOcupado * 100.0 / tiempoTotalSimulacionReal; long sumaTiemposRespuesta = 0; long sumaTiemposRetorno = 0; long sumaTiemposEspera = 0; int countParaPromedio = 0; for (Proceso p : listaProcesosCompletados) { long tRetorno = p.getTiempoRetorno(); if (tRetorno >= 0) { long tRespuesta = p.getTiempoRespuesta(); long tEspera = p.getTiempoTotalEsperandoListo(); sumaTiemposRetorno += tRetorno; sumaTiemposRespuesta += tRespuesta; sumaTiemposEspera += tEspera; countParaPromedio++; } } double tRespProm = (countParaPromedio > 0) ? (double) sumaTiemposRespuesta / countParaPromedio : 0; double tRetProm = (countParaPromedio > 0) ? (double) sumaTiemposRetorno / countParaPromedio : 0; double tEspProm = (countParaPromedio > 0) ? (double) sumaTiemposEspera / countParaPromedio : 0; System.out.println("\n--- MÉTRICAS DE RENDIMIENTO ---"); System.out.printf("   Tiempo Total Simulación (Real): %.3f s%n", tiempoTotalSegundos); System.out.printf("   Procesos Completados: %d%n", listaProcesosCompletados.size()); System.out.printf("   Throughput: %.3f procesos/s%n", throughput); System.out.printf("   Tiempo Total CPU Ocupado: %d ms%n", tiempoTotalCpuOcupado); System.out.printf("   Utilización de CPU: %.2f%%%n", utilizacionCpu); System.out.printf("   Tiempo de Retorno Promedio: %.2f ms%n", tRetProm); System.out.printf("   Tiempo de Respuesta Promedio (aprox): %.2f ms%n", tRespProm); System.out.printf("   Tiempo de Espera Promedio (en Listo/SuspListo): %.2f ms%n", tEspProm); System.out.println("---------------------------------"); Logger.log(String.format("METRICAS FINALES: Procesos=%d, Throughput=%.3f p/s, CPU Util=%.2f%%, T.RetornoAvg=%.2fms, T.RespAvg=%.2fms, T.EsperaAvg=%.2fms", listaProcesosCompletados.size(), throughput, utilizacionCpu, tRetProm, tRespProm, tEspProm)); }

    /** Cierra el simulador de forma segura, deteniendo hilos y cerrando el logger. */
    public void cerrarSimulador() { /* ... código sin cambios ... */ Logger.log("Cerrando Simulador..."); if (procesoThread != null && procesoThread.isAlive()) procesoThread.interrupt(); for (Thread t : procesosEnExcepcion.values()) if (t != null && t.isAlive()) t.interrupt(); calcularYMostrarMetricas(); Logger.close(); }

    /** Busca un proceso por su ID en todas las colas y en la CPU. Usado por la GUI. */
    public Proceso buscarProcesoPorIdEnTodasLasListas(int id) { /* ... código sin cambios ... */ if (procesoActual != null && procesoActual.getId() == id) return procesoActual; Proceso p = buscarEnCola(colaListos, id); if (p != null) return p; p = mapaProcesosBloqueados.get(id); if (p != null) return p; p = buscarEnCola(colaListosSuspendidos, id); if (p != null) return p; p = buscarEnCola(colaBloqueadosSuspendidos, id); if (p != null) return p; for(Proceso terminado : listaProcesosCompletados) if(terminado.getId() == id) return terminado; return null; }
    private Proceso buscarEnCola(CustomQueue cola, int id) { if (cola == null) return null; Proceso[] array = cola.toArray(); for (Proceso p : array) if (p != null && p.getId() == id) return p; return null; }

    /** Calcula y devuelve el Tiempo de Retorno Promedio de los procesos completados. */
    public double getTiempoRetornoPromedioCalculado() { if (listaProcesosCompletados == null || listaProcesosCompletados.isEmpty()) return 0.0; long s=0; int c=0; for(Proceso p:listaProcesosCompletados){long t=p.getTiempoRetorno(); if(t>=0){s+=t; c++;}} return (c>0)?(double)s/c:0.0; }
    /** Calcula y devuelve el Tiempo de Espera Promedio de los procesos completados. */
    public double getTiempoEsperaPromedioCalculado() { if (listaProcesosCompletados == null || listaProcesosCompletados.isEmpty()) return 0.0; long s=0; int c=0; for(Proceso p:listaProcesosCompletados){long t=p.getTiempoRetorno(); if(t>=0){s+=p.getTiempoTotalEsperandoListo(); c++;}} return (c>0)?(double)s/c:0.0; }
}