package so_operativos;

import java.util.concurrent.Semaphore;

public class Proceso implements Runnable {
    private static int nextId = 1;

    public enum TipoBound { CPU_BOUND, I_O_BOUND }

    private final int id;
    private String nombre;
    private EstadoProceso estado;

    // Componentes del PCB (PC, IR, Registros, Prioridad)
    private int instruccionesRestantes;
    private int programCounter;
    private String registroInstruccion;
    private int registroA; 
    private final int prioridad;

    // Parámetros de E/S
    private final TipoBound tipo;
    private int ciclosParaInterrupcion;
    private int ciclosParaSatisfacerIO;
    private int contadorCiclos;
    private int contadorIOCiclos;

    private Semaphore cpuSemaphore;

    // Constructor I/O_BOUND
    public Proceso(String nombre, int totalInstrucciones, int prioridad, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO) {
        this.id = nextId++;
        this.nombre = nombre;
        this.instruccionesRestantes = totalInstrucciones;
        this.prioridad = prioridad;
        this.tipo = TipoBound.I_O_BOUND;
        this.estado = EstadoProceso.LISTO;
        this.programCounter = 0;
        this.registroInstruccion = "NOP";
        this.registroA = 0;
        this.ciclosParaInterrupcion = ciclosParaInterrupcion;
        this.ciclosParaSatisfacerIO = ciclosParaSatisfacerIO;
        this.contadorCiclos = 0;
        this.contadorIOCiclos = 0;
    }

    // Constructor CPU_BOUND (Corregido)
    public Proceso(String nombre, int totalInstrucciones, int prioridad) {
        this.id = nextId++;
        this.nombre = nombre;
        this.instruccionesRestantes = totalInstrucciones;
        this.prioridad = prioridad;
        this.tipo = TipoBound.CPU_BOUND;
        this.estado = EstadoProceso.LISTO;
        this.programCounter = 0;
        this.registroInstruccion = "NOP";
        this.registroA = 0;
        this.ciclosParaInterrupcion = 0;
        this.ciclosParaSatisfacerIO = 0;
        this.contadorCiclos = 0;
        this.contadorIOCiclos = 0;
    }

    public void setCpuSemaphore(Semaphore cpuSemaphore) { this.cpuSemaphore = cpuSemaphore; }

    // Getters
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public EstadoProceso getEstado() { return estado; }
    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    public int getInstruccionesRestantes() { return instruccionesRestantes; }
    public int getProgramCounter() { return programCounter; }
    public int getPrioridad() { return prioridad; }
    public TipoBound getTipo() { return tipo; }
    public int getCiclosParaSatisfacerIO() { return ciclosParaSatisfacerIO; }

    public void resetContadorCiclos() {
        this.contadorCiclos = 0;
        this.contadorIOCiclos = 0;
    }

    public void incrementarContadorCiclosIO() {
        this.contadorIOCiclos++;
    }

    public boolean haTerminado() {
        return instruccionesRestantes <= 0;
    }

    @Override
    public void run() {
        // Lógica de ejecución se añadirá en una etapa posterior
    }

    public void mostrarPCB() {
        // Lógica de visualización se añadirá en una etapa posterior
    }
}

