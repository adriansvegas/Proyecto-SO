package so_operativos;

import java.util.concurrent.Semaphore;

public class Proceso implements Runnable {
    private static int nextId = 1;

    public static void resetNextId(int startValue) {
        nextId = startValue;
    }
     public static int getNextId() {
         return nextId;
     }

    public enum TipoBound { CPU_BOUND, I_O_BOUND }

    private final int id;
    private String nombre;
    private EstadoProceso estado;

    private int instruccionesRestantes;
    private int programCounter;
    private String registroInstruccion;
    private int registroA;
    private final int prioridad;

    private final TipoBound tipo;
    private int ciclosParaInterrupcion;
    private int ciclosParaSatisfacerIO;
    private int contadorCiclos; // Ciclos de CPU transcurridos en la ráfaga actual
    private int contadorIOCiclos; // Ciclos de E/S transcurridos

    private transient Semaphore cpuSemaphore; // transient: no se guarda en el archivo

    public Proceso(String nombre, int totalInstrucciones, int prioridad, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO) {
        this(nextId++, nombre, EstadoProceso.NUEVO, totalInstrucciones, prioridad, TipoBound.I_O_BOUND, ciclosParaInterrupcion, ciclosParaSatisfacerIO, 0, "NOP", 0, 0, 0);
        this.estado = EstadoProceso.LISTO; // El estado inicial real suele ser LISTO o SUSPENDIDO_LISTO
    }

    public Proceso(String nombre, int totalInstrucciones, int prioridad) {
         this(nextId++, nombre, EstadoProceso.NUEVO, totalInstrucciones, prioridad, TipoBound.CPU_BOUND, 0, 0, 0, "NOP", 0, 0, 0);
         this.estado = EstadoProceso.LISTO; // El estado inicial real suele ser LISTO o SUSPENDIDO_LISTO
    }

     public Proceso(int id, String nombre, EstadoProceso estado, int instruccionesRestantes, int prioridad, TipoBound tipo, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO, int programCounter, String registroInstruccion, int registroA, int contadorCiclos, int contadorIOCiclos) {
         this.id = id;
         this.nombre = nombre;
         this.estado = estado;
         this.instruccionesRestantes = instruccionesRestantes;
         this.prioridad = prioridad;
         this.tipo = tipo;
         this.ciclosParaInterrupcion = ciclosParaInterrupcion;
         this.ciclosParaSatisfacerIO = ciclosParaSatisfacerIO;
         this.programCounter = programCounter;
         this.registroInstruccion = registroInstruccion;
         this.registroA = registroA;
         this.contadorCiclos = contadorCiclos;
         this.contadorIOCiclos = contadorIOCiclos;
         // cpuSemaphore se asignará al añadirlo al simulador
     }


    public String toStringData() {
        return String.join(",",
                String.valueOf(id),
                nombre, // Asumiendo que el nombre no tiene comas
                estado.name(),
                String.valueOf(instruccionesRestantes),
                String.valueOf(prioridad),
                tipo.name(),
                String.valueOf(ciclosParaInterrupcion),
                String.valueOf(ciclosParaSatisfacerIO),
                String.valueOf(programCounter),
                registroInstruccion,
                String.valueOf(registroA),
                String.valueOf(contadorCiclos),
                String.valueOf(contadorIOCiclos)
        );
    }

     public static Proceso fromStringData(String data) throws IllegalArgumentException {
         String[] parts = data.split(",", 13); // Limitar a 13 partes
         if (parts.length < 13) {
             throw new IllegalArgumentException("Formato de datos inválido para Proceso: " + data);
         }
         try {
             int id = Integer.parseInt(parts[0]);
             String nombre = parts[1];
             EstadoProceso estado = EstadoProceso.valueOf(parts[2]);
             int instrRest = Integer.parseInt(parts[3]);
             int prio = Integer.parseInt(parts[4]);
             TipoBound tipo = TipoBound.valueOf(parts[5]);
             int ciclosInt = Integer.parseInt(parts[6]);
             int ciclosSat = Integer.parseInt(parts[7]);
             int pc = Integer.parseInt(parts[8]);
             String ir = parts[9];
             int regA = Integer.parseInt(parts[10]);
             int contCiclos = Integer.parseInt(parts[11]);
             int contIO = Integer.parseInt(parts[12]);

             return new Proceso(id, nombre, estado, instrRest, prio, tipo, ciclosInt, ciclosSat, pc, ir, regA, contCiclos, contIO);
         } catch (Exception e) {
             throw new IllegalArgumentException("Error parseando datos de Proceso: " + data + " | Error: " + e.getMessage(), e);
         }
     }

    public void setCpuSemaphore(Semaphore cpuSemaphore) { this.cpuSemaphore = cpuSemaphore; }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public EstadoProceso getEstado() { return estado; }
    public void setEstado(EstadoProceso estado) { this.estado = estado; }
    public int getInstruccionesRestantes() { return instruccionesRestantes; }
    public int getProgramCounter() { return programCounter; }
    public int getPrioridad() { return prioridad; }
    public TipoBound getTipo() { return tipo; }
    public int getCiclosParaSatisfacerIO() { return ciclosParaSatisfacerIO; }

    public int getContadorIOCiclos() { return contadorIOCiclos; }


    public void resetContadorCiclos() {
        this.contadorCiclos = 0;
        this.contadorIOCiclos = 0; // Se resetea al volver a Listo
    }

    public void incrementarContadorCiclosIO() {
        this.contadorIOCiclos++;
    }

    public boolean haTerminado() {
        return instruccionesRestantes <= 0;
    }

    @Override
    public void run() {
         if (cpuSemaphore == null) {
              System.err.println("ERROR FATAL: Semaphore nulo para Proceso " + id + ". Ejecución abortada.");
              this.estado = EstadoProceso.TERMINADO; // Marcar como terminado para evitar reintentos
              this.instruccionesRestantes = 0;
              return;
         }
        try {
            cpuSemaphore.acquire();
            //this.estado = EstadoProceso.EJECUCION; // Estado se setea en Simulador.planificarSiguiente

            while (instruccionesRestantes > 0 && this.estado == EstadoProceso.EJECUCION) {
                instruccionesRestantes--;
                programCounter++; // PC incrementa una unidad
                registroA++;
                contadorCiclos++;

                this.registroInstruccion = (tipo == TipoBound.CPU_BOUND) ?
                                           "CALCULA" :
                                           "PROCESA";

                if (tipo == TipoBound.I_O_BOUND && contadorCiclos >= ciclosParaInterrupcion && ciclosParaInterrupcion > 0) {
                    this.registroInstruccion = "IO_EXCEPTION";
                    this.estado = EstadoProceso.BLOQUEADO; // Marca el estado para el SO
                    break;
                }

                Thread.sleep(1);
            }

        } catch (InterruptedException e) {
        } finally {
            cpuSemaphore.release();
        }
    }

    public void mostrarPCB() {
        String ioInfo = "";
        if (tipo == TipoBound.I_O_BOUND) {
            ioInfo = String.format(" | E/S: %d/%d (Int en %d)", contadorIOCiclos, ciclosParaSatisfacerIO, ciclosParaInterrupcion);
        }

        System.out.printf("   [PCB %d - %s] Est: %s, Pri: %d, PC: %d, IR: %s, R_A: %d | Rest: %d%s%n",
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