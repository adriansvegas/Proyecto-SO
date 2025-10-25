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
    private int programCounter; // PC incrementará una unidad por ciclo
    private String registroInstruccion;
    private int registroA; 
    private final int prioridad;

    // Parámetros de E/S
    private final TipoBound tipo;
    private int ciclosParaInterrupcion;
    private int ciclosParaSatisfacerIO;
    private int contadorCiclos; // Ciclos de CPU transcurridos en la ráfaga actual
    private int contadorIOCiclos; // Ciclos de E/S transcurridos

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
    
   // Constructor CPU_BOUND
    public Proceso(String nombre, int totalInstrucciones, int prioridad) {
        this.id = nextId++;
        this.nombre = nombre;
        this.instruccionesRestantes = totalInstrucciones;
        this.prioridad = prioridad;
        this.tipo = TipoBound.CPU_BOUND; // Se asigna el tipo correcto una sola vez
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
        try {
            cpuSemaphore.acquire(); 
            this.estado = EstadoProceso.EJECUCION;

            while (instruccionesRestantes > 0 && this.estado == EstadoProceso.EJECUCION) {
                // SIMPLIFICACIÓN: Una instrucción por ciclo
                instruccionesRestantes--;
                programCounter++; // PC incrementa una unidad
                registroA++; 
                contadorCiclos++;

                this.registroInstruccion = (tipo == TipoBound.CPU_BOUND) ? 
                                           "CALCULA" : 
                                           "PROCESA";

                // Verificar si se genera la excepción de E/S
                if (tipo == TipoBound.I_O_BOUND && contadorCiclos >= ciclosParaInterrupcion) {
                    this.registroInstruccion = "IO_EXCEPTION";
                    this.estado = EstadoProceso.BLOQUEADO; // Marca el estado para el SO
                    break; 
                }
                
                // Pausa mínima para que el Simulador pueda chequear y expropiar
                Thread.sleep(1); 
            }

        } catch (InterruptedException e) {
            // Hilo interrumpido por el Kernel (Expropiación o fin de Quantum)
        } finally {
            cpuSemaphore.release();
        }
    }
    
    public void mostrarPCB() {
        String ioInfo = "";
        if (tipo == TipoBound.I_O_BOUND) {
            ioInfo = String.format(" | E/S: %d/%d", contadorIOCiclos, ciclosParaSatisfacerIO);
        }
        
        System.out.printf("   [PCB id: %d - %s] Est: %s, Pri: %d, PC: %d, IR: %s, R_A: %d | Rest: %d%s%n", 
            id, 
            nombre, 
            estado, 
            prioridad,
            programCounter, 
            registroInstruccion,
            registroA,
            instruccionesRestantes, 
            ioInfo
        );
    }
}