package so_operativos;

import java.util.concurrent.Semaphore;

/**
 * Representa un proceso dentro del simulador. Contiene su PCB simulado,
 * lógica de ejecución (CPU/IO bound) y gestión de estados y métricas.
 */
public class Proceso implements Runnable {
    private static int nextId = 1; // Contador estático para generar IDs únicos

    /** Resetea el contador de IDs. Útil al cargar estado. */
    public static void resetNextId(int startValue) { nextId = Math.max(1, startValue); }
    /** Obtiene el próximo ID a usar. Útil al guardar estado. */
    public static int getNextId() { return nextId; }

    /** Define si un proceso tiende a usar más CPU o a realizar operaciones de E/S. */
    public enum TipoBound { CPU_BOUND, I_O_BOUND }

    // --- Atributos del PCB simulado ---
    private final int id; // Identificador único
    private String nombre; // Nombre descriptivo
    private EstadoProceso estado; // Estado actual (LISTO, EJECUCION, etc.)

    // --- Atributos de Ejecución ---
    private int instruccionesRestantes; // Instrucciones pendientes
    private int programCounter; // Contador de programa (simulado)
    private String registroInstruccion; // Instrucción actual (simulada)
    private int registroA; // Registro general (simulado)
    private final int prioridad; // Prioridad numérica (menor = más prioritario)
    private final int instruccionesTotales; // Número total original de instrucciones

    // --- Atributos específicos para I/O Bound ---
    private final TipoBound tipo; // CPU_BOUND o I_O_BOUND
    private int ciclosParaInterrupcion; // Ciclos de CPU antes de pedir E/S
    private int ciclosParaSatisfacerIO; // Duración de la operación de E/S
    private int contadorCiclos; // Ciclos CPU ejecutados en la ráfaga actual
    private int contadorIOCiclos; // Ciclos de E/S completados en la operación actual

    // --- Control de concurrencia ---
    private transient Semaphore cpuSemaphore; // Semáforo para acceso a CPU (no se guarda)

    // --- Atributos para Métricas ---
    private final long tiempoLlegada; // Timestamp (real) de creación
    private long tiempoFinalizacion; // Timestamp (real) de finalización
    private long tiempoTotalBloqueado; // Tiempo acumulado en BLOQUEADO o SUSPENDIDO_BLOQUEADO
    private long tiempoTotalEsperandoListo; // Tiempo acumulado en LISTO o SUSPENDIDO_LISTO
    private long tiempoInicioRafagaActual; // Timestamp (real) de entrada al estado actual

    /** Constructor para procesos I/O Bound. */
    public Proceso(String nombre, int totalInstrucciones, int prioridad, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO) { this(nextId++, nombre, EstadoProceso.NUEVO, totalInstrucciones, prioridad, TipoBound.I_O_BOUND, ciclosParaInterrupcion, ciclosParaSatisfacerIO, 0, "NOP", 0, 0, 0, System.currentTimeMillis(), 0, 0, 0); this.estado = EstadoProceso.LISTO; }
    /** Constructor para procesos CPU Bound. */
    public Proceso(String nombre, int totalInstrucciones, int prioridad) { this(nextId++, nombre, EstadoProceso.NUEVO, totalInstrucciones, prioridad, TipoBound.CPU_BOUND, 0, 0, 0, "NOP", 0, 0, 0, System.currentTimeMillis(), 0, 0, 0); this.estado = EstadoProceso.LISTO; }

    /** Constructor principal (privado) usado internamente y para cargar estado. */
    private Proceso(int id, String nombre, EstadoProceso estado, int instruccionesRestantes, int prioridad, TipoBound tipo, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO, int programCounter, String registroInstruccion, int registroA, int contadorCiclos, int contadorIOCiclos, long tiempoLlegada, long tiempoFinalizacion, long tiempoTotalBloqueado, long tiempoTotalEsperandoListo) { /* ... asignación de atributos ... */ this.id = id; this.nombre = nombre; this.estado = estado; this.instruccionesRestantes = instruccionesRestantes; this.prioridad = prioridad; this.instruccionesTotales = instruccionesRestantes + programCounter; this.tipo = tipo; this.ciclosParaInterrupcion = ciclosParaInterrupcion; this.ciclosParaSatisfacerIO = ciclosParaSatisfacerIO; this.programCounter = programCounter; this.registroInstruccion = registroInstruccion; this.registroA = registroA; this.contadorCiclos = contadorCiclos; this.contadorIOCiclos = contadorIOCiclos; this.tiempoLlegada = tiempoLlegada; this.tiempoFinalizacion = tiempoFinalizacion; this.tiempoTotalBloqueado = tiempoTotalBloqueado; this.tiempoTotalEsperandoListo = tiempoTotalEsperandoListo; this.tiempoInicioRafagaActual = System.currentTimeMillis(); }

    /** (Deprecado) Constructor anterior, mantenido por compatibilidad. */
    @Deprecated public Proceso(int id, String nombre, EstadoProceso estado, int instruccionesRestantes, int prioridad, TipoBound tipo, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO, int programCounter, String registroInstruccion, int registroA, int contadorCiclos, int contadorIOCiclos) { this(id, nombre, estado, instruccionesRestantes, prioridad, tipo, ciclosParaInterrupcion, ciclosParaSatisfacerIO, programCounter, registroInstruccion, registroA, contadorCiclos, contadorIOCiclos, System.currentTimeMillis(), 0, 0, 0); if (estado == EstadoProceso.TERMINADO) { this.tiempoFinalizacion = this.tiempoLlegada; } }

    /** Convierte el estado del proceso a un String CSV para guardarlo. */
    public String toStringData() { return String.join(",", String.valueOf(id), nombre, estado.name(), String.valueOf(instruccionesRestantes), String.valueOf(prioridad), tipo.name(), String.valueOf(ciclosParaInterrupcion), String.valueOf(ciclosParaSatisfacerIO), String.valueOf(programCounter), registroInstruccion, String.valueOf(registroA), String.valueOf(contadorCiclos), String.valueOf(contadorIOCiclos), String.valueOf(tiempoLlegada), String.valueOf(tiempoFinalizacion), String.valueOf(tiempoTotalBloqueado), String.valueOf(tiempoTotalEsperandoListo) ); }

    /** Método estático para reconstruir un objeto Proceso desde su representación String CSV. */
    public static Proceso fromStringData(String data) throws IllegalArgumentException { /* ... código de parseo ... */ String[] parts = data.split(",", 17); if (parts.length < 13) { throw new IllegalArgumentException("Formato inválido: " + data); } try { int id = Integer.parseInt(parts[0]); String n = parts[1]; EstadoProceso est = EstadoProceso.valueOf(parts[2]); int iR = Integer.parseInt(parts[3]); int pri = Integer.parseInt(parts[4]); TipoBound t = TipoBound.valueOf(parts[5]); int cI = Integer.parseInt(parts[6]); int cS = Integer.parseInt(parts[7]); int pc = Integer.parseInt(parts[8]); String ir = parts[9]; int rA = Integer.parseInt(parts[10]); int cC = Integer.parseInt(parts[11]); int cIO = Integer.parseInt(parts[12]); long tL = (parts.length > 13 && !parts[13].isEmpty())?Long.parseLong(parts[13]):System.currentTimeMillis(); long tF = (parts.length > 14 && !parts[14].isEmpty())?Long.parseLong(parts[14]):0; long tB = (parts.length > 15 && !parts[15].isEmpty())?Long.parseLong(parts[15]):0; long tLi = (parts.length > 16 && !parts[16].isEmpty())?Long.parseLong(parts[16]):0; return new Proceso(id, n, est, iR, pri, t, cI, cS, pc, ir, rA, cC, cIO, tL, tF, tB, tLi); } catch (Exception e) { throw new IllegalArgumentException("Error parseando: " + data + " | " + e.getMessage(), e); } }

    /** Asigna el semáforo de CPU (llamado por Simulador). */
    public void setCpuSemaphore(Semaphore cpuSemaphore) { this.cpuSemaphore = cpuSemaphore; }

    // --- Getters para Métricas ---
    public long getTiempoLlegada() { return tiempoLlegada; }
    public long getTiempoFinalizacion() { return tiempoFinalizacion; }
    public long getTiempoTotalBloqueado() { return tiempoTotalBloqueado; }
    public long getTiempoTotalEsperandoListo() { return tiempoTotalEsperandoListo; }
    public long getTiempoRetorno() { return (tiempoFinalizacion > 0) ? tiempoFinalizacion - tiempoLlegada : -1; }
    public long getTiempoRespuesta() { long tRetorno = getTiempoRetorno(); return (tRetorno >= 0) ? tRetorno - tiempoTotalBloqueado : -1; } // Aproximación

    /**
     * Cambia el estado del proceso y actualiza los tiempos acumulados en los estados.
     * @param nuevoEstado El nuevo estado al que transiciona el proceso.
     */
    public void setEstado(EstadoProceso nuevoEstado) {
        EstadoProceso estadoAnterior = this.estado; long ahora = System.currentTimeMillis();
        if (estadoAnterior != nuevoEstado) { // Solo si el estado realmente cambia
            // Acumula tiempo en el estado anterior
            if (estadoAnterior == EstadoProceso.LISTO || estadoAnterior == EstadoProceso.SUSPENDIDO_LISTO) { tiempoTotalEsperandoListo += (ahora - tiempoInicioRafagaActual); }
            else if (estadoAnterior == EstadoProceso.BLOQUEADO || estadoAnterior == EstadoProceso.SUSPENDIDO_BLOQUEADO) { tiempoTotalBloqueado += (ahora - tiempoInicioRafagaActual); }
            // Actualiza estado y reinicia temporizador de ráfaga
            this.estado = nuevoEstado; this.tiempoInicioRafagaActual = ahora;
            if (nuevoEstado == EstadoProceso.TERMINADO) { this.tiempoFinalizacion = ahora; } // Marca tiempo de finalización
            Logger.log("Proceso " + id + " (" + nombre + ") cambió estado: " + estadoAnterior + " -> " + nuevoEstado);
        }
    }

    // --- Getters para Atributos ---
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public EstadoProceso getEstado() { return estado; }
    public int getInstruccionesRestantes() { return instruccionesRestantes; }
    public int getProgramCounter() { return programCounter; }
    public int getPrioridad() { return prioridad; }
    public TipoBound getTipo() { return tipo; }
    public int getCiclosParaSatisfacerIO() { return ciclosParaSatisfacerIO; }
    public int getContadorIOCiclos() { return contadorIOCiclos; }
    public int getInstruccionesTotales() { return instruccionesTotales; }
    public int getCiclosParaInterrupcion() { return ciclosParaInterrupcion; }
    public int getContadorCiclos() { return contadorCiclos; }
    public String getRegistroInstruccion() { return registroInstruccion; }
    public int getRegistroA() { return registroA; }
    /** Usado por la GUI para identificar estados suspendidos. */
    public boolean isSuspendidoGUI() { return estado == EstadoProceso.SUSPENDIDO_LISTO || estado == EstadoProceso.SUSPENDIDO_BLOQUEADO; }

    /** Resetea contadores de ciclos CPU y E/S. */
    public void resetContadorCiclos() { this.contadorCiclos = 0; this.contadorIOCiclos = 0; }
    /** Incrementa el contador de ciclos de E/S completados. */
    public void incrementarContadorCiclosIO() { this.contadorIOCiclos++; }
    /** Verifica si el proceso ha completado todas sus instrucciones. */
    public boolean haTerminado() { return instruccionesRestantes <= 0; }

    /**
     * Lógica principal de ejecución del proceso (método run de Runnable).
     * Simula la ejecución de instrucciones, manejo de E/S y espera de CPU.
     */
    @Override
    public void run() {
        if (cpuSemaphore == null) { Logger.log("ERROR FATAL: Semaphore nulo para Proceso " + id); setEstado(EstadoProceso.TERMINADO); this.instruccionesRestantes = 0; return; }
        try {
            cpuSemaphore.acquire(); // Intenta adquirir el semáforo de la CPU
            // Bucle principal de ejecución del proceso
            while (instruccionesRestantes > 0 && this.estado == EstadoProceso.EJECUCION) {
                // Simula ejecución de una instrucción
                instruccionesRestantes--; programCounter++; registroA++; contadorCiclos++;
                this.registroInstruccion = (tipo == TipoBound.CPU_BOUND) ? "CALCULA" : "PROCESA"; // Simula tipo instrucción
                // Verifica si necesita E/S
                if (tipo == TipoBound.I_O_BOUND && contadorCiclos >= ciclosParaInterrupcion && ciclosParaInterrupcion > 0) {
                    this.registroInstruccion = "IO_EXCEPTION"; setEstado(EstadoProceso.BLOQUEADO); break; // Pasa a bloqueado y sale
                }
                Thread.sleep(1); // Pequeña pausa para permitir interrupción
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); // Hilo interrumpido (expropiación, E/S, fin)
        } finally { cpuSemaphore.release(); } // Libera el semáforo de CPU
    }

    /** Muestra información básica del PCB en la consola estándar. */
    public void mostrarPCB() { String ioInfo = ""; if (tipo == TipoBound.I_O_BOUND) { if (estado == EstadoProceso.BLOQUEADO || estado == EstadoProceso.SUSPENDIDO_BLOQUEADO) ioInfo = String.format(" | E/S: %d/%d", contadorIOCiclos, ciclosParaSatisfacerIO); else ioInfo = String.format(" | Próx E/S en %d ciclos CPU", ciclosParaInterrupcion - contadorCiclos); } System.out.printf("    [PCB %d - %s] Est: %s, Pri: %d, PC: %d, IR: %s, R_A: %d | Rest: %d%s%n", id, nombre, estado, prioridad, programCounter, registroInstruccion, registroA, instruccionesRestantes, ioInfo); }
}