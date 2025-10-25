package so_operativos;

import java.util.concurrent.Semaphore;

public class Proceso implements Runnable {
    private static int nextId = 1;

    public static void resetNextId(int startValue) { nextId = startValue; }
    public static int getNextId() { return nextId; }

    public enum TipoBound { CPU_BOUND, I_O_BOUND }

    private final int id;
    private String nombre;
    private EstadoProceso estado;

    private int instruccionesRestantes;
    private int programCounter;
    private String registroInstruccion;
    private int registroA;
    private final int prioridad;
    private final int instruccionesTotales; 

    private final TipoBound tipo;
    private int ciclosParaInterrupcion;
    private int ciclosParaSatisfacerIO;
    private int contadorCiclos;
    private int contadorIOCiclos;

    private transient Semaphore cpuSemaphore;

    
    private final long tiempoLlegada; 
    private long tiempoFinalizacion; 
    private long tiempoTotalBloqueado; 
    private long tiempoTotalEsperandoListo; 
    private long tiempoInicioRafagaActual; 

    
    public Proceso(String nombre, int totalInstrucciones, int prioridad, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO) {
        this(nextId++, nombre, EstadoProceso.NUEVO, totalInstrucciones, prioridad, TipoBound.I_O_BOUND, ciclosParaInterrupcion, ciclosParaSatisfacerIO, 0, "NOP", 0, 0, 0, System.currentTimeMillis(), 0, 0, 0); 
        this.estado = EstadoProceso.LISTO;
    }

    
    public Proceso(String nombre, int totalInstrucciones, int prioridad) {
         this(nextId++, nombre, EstadoProceso.NUEVO, totalInstrucciones, prioridad, TipoBound.CPU_BOUND, 0, 0, 0, "NOP", 0, 0, 0, System.currentTimeMillis(), 0, 0, 0); 
         this.estado = EstadoProceso.LISTO;
    }

    
     public Proceso(int id, String nombre, EstadoProceso estado, int instruccionesRestantes, int prioridad, TipoBound tipo, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO, int programCounter, String registroInstruccion, int registroA, int contadorCiclos, int contadorIOCiclos, long tiempoLlegada, long tiempoFinalizacion, long tiempoTotalBloqueado, long tiempoTotalEsperandoListo) {
         this.id = id;
         this.nombre = nombre;
         this.estado = estado;
         this.instruccionesRestantes = instruccionesRestantes;
         this.prioridad = prioridad;
         
         this.instruccionesTotales = instruccionesRestantes + programCounter;
         this.tipo = tipo;
         this.ciclosParaInterrupcion = ciclosParaInterrupcion;
         this.ciclosParaSatisfacerIO = ciclosParaSatisfacerIO;
         this.programCounter = programCounter;
         this.registroInstruccion = registroInstruccion;
         this.registroA = registroA;
         this.contadorCiclos = contadorCiclos;
         this.contadorIOCiclos = contadorIOCiclos;
         
         this.tiempoLlegada = tiempoLlegada;
         this.tiempoFinalizacion = tiempoFinalizacion;
         this.tiempoTotalBloqueado = tiempoTotalBloqueado;
         this.tiempoTotalEsperandoListo = tiempoTotalEsperandoListo;
         this.tiempoInicioRafagaActual = System.currentTimeMillis(); 
     }

    
    public Proceso(int id, String nombre, EstadoProceso estado, int instruccionesRestantes, int prioridad, TipoBound tipo, int ciclosParaInterrupcion, int ciclosParaSatisfacerIO, int programCounter, String registroInstruccion, int registroA, int contadorCiclos, int contadorIOCiclos) {
        this(id, nombre, estado, instruccionesRestantes, prioridad, tipo, ciclosParaInterrupcion, ciclosParaSatisfacerIO, programCounter, registroInstruccion, registroA, contadorCiclos, contadorIOCiclos, System.currentTimeMillis(), 0, 0, 0); 
         if (estado == EstadoProceso.TERMINADO) { 
             this.tiempoFinalizacion = System.currentTimeMillis(); 
         }
    }

    
    public String toStringData() {
        return String.join(",",
                 String.valueOf(id), nombre, estado.name(),
                 String.valueOf(instruccionesRestantes), String.valueOf(prioridad), tipo.name(),
                 String.valueOf(ciclosParaInterrupcion), String.valueOf(ciclosParaSatisfacerIO),
                 String.valueOf(programCounter), registroInstruccion, String.valueOf(registroA),
                 String.valueOf(contadorCiclos), String.valueOf(contadorIOCiclos),
                 
                 String.valueOf(tiempoLlegada), String.valueOf(tiempoFinalizacion),
                 String.valueOf(tiempoTotalBloqueado), String.valueOf(tiempoTotalEsperandoListo)
        );
    }

    
     public static Proceso fromStringData(String data) throws IllegalArgumentException {
         String[] parts = data.split(",", 17); 
         if (parts.length < 13) { 
             throw new IllegalArgumentException("Formato de datos inválido para Proceso (partes=" + parts.length +"): " + data);
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

             
             long tLlegada = (parts.length > 13) ? Long.parseLong(parts[13]) : System.currentTimeMillis();
             long tFin = (parts.length > 14) ? Long.parseLong(parts[14]) : 0;
             long tBloq = (parts.length > 15) ? Long.parseLong(parts[15]) : 0;
             long tListo = (parts.length > 16) ? Long.parseLong(parts[16]) : 0;

             return new Proceso(id, nombre, estado, instrRest, prio, tipo, ciclosInt, ciclosSat, pc, ir, regA, contCiclos, contIO, tLlegada, tFin, tBloq, tListo);
         } catch (Exception e) {
             throw new IllegalArgumentException("Error parseando datos de Proceso: " + data + " | Error: " + e.getMessage(), e);
         }
     }

    public void setCpuSemaphore(Semaphore cpuSemaphore) { this.cpuSemaphore = cpuSemaphore; }

    
    public long getTiempoLlegada() { return tiempoLlegada; }
    public long getTiempoFinalizacion() { return tiempoFinalizacion; }
    public long getTiempoTotalBloqueado() { return tiempoTotalBloqueado; }
    public long getTiempoTotalEsperandoListo() { return tiempoTotalEsperandoListo; }
    public long getTiempoRetorno() { return (tiempoFinalizacion > 0) ? tiempoFinalizacion - tiempoLlegada : -1; } 
    public long getTiempoRespuesta() { return getTiempoRetorno() - tiempoTotalBloqueado; } 


    
    public void setEstado(EstadoProceso nuevoEstado) {
        EstadoProceso estadoAnterior = this.estado;
        long ahora = System.currentTimeMillis(); 

        
        if ((estadoAnterior == EstadoProceso.LISTO || estadoAnterior == EstadoProceso.SUSPENDIDO_LISTO) && estadoAnterior != nuevoEstado) {
            tiempoTotalEsperandoListo += (ahora - tiempoInicioRafagaActual);
        }
        
        else if ((estadoAnterior == EstadoProceso.BLOQUEADO || estadoAnterior == EstadoProceso.SUSPENDIDO_BLOQUEADO) && estadoAnterior != nuevoEstado) {
            tiempoTotalBloqueado += (ahora - tiempoInicioRafagaActual);
        }
        
        

        
        this.estado = nuevoEstado;
        this.tiempoInicioRafagaActual = ahora; 

        
        if (nuevoEstado == EstadoProceso.TERMINADO) {
            this.tiempoFinalizacion = ahora;
        }

        
        Logger.log("Proceso " + id + " (" + nombre + ") cambió estado: " + estadoAnterior + " -> " + nuevoEstado);
    }


    
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public EstadoProceso getEstado() { return estado; }
    
    public int getInstruccionesRestantes() { return instruccionesRestantes; }
    public int getProgramCounter() { return programCounter; }
    public int getPrioridad() { return prioridad; }
    public TipoBound getTipo() { return tipo; }
    public int getCiclosParaSatisfacerIO() { return ciclosParaSatisfacerIO; }
    public int getContadorIOCiclos() { return contadorIOCiclos; }


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
        if (cpuSemaphore == null) {
             Logger.log("ERROR FATAL: Semaphore nulo para Proceso " + id + ". Ejecución abortada.");
             setEstado(EstadoProceso.TERMINADO); 
             this.instruccionesRestantes = 0;
             return;
         }
        try {
            cpuSemaphore.acquire();
            
            

            while (instruccionesRestantes > 0 && this.estado == EstadoProceso.EJECUCION) {
                instruccionesRestantes--;
                programCounter++;
                registroA++;
                contadorCiclos++;
                this.registroInstruccion = (tipo == TipoBound.CPU_BOUND) ? "CALCULA" : "PROCESA";

                if (tipo == TipoBound.I_O_BOUND && contadorCiclos >= ciclosParaInterrupcion && ciclosParaInterrupcion > 0) {
                    this.registroInstruccion = "IO_EXCEPTION";
                    setEstado(EstadoProceso.BLOQUEADO); 
                    
                    break;
                }
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            
            Thread.currentThread().interrupt(); 
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
            id, nombre, estado, prioridad, programCounter, registroInstruccion,
            registroA, instruccionesRestantes, ioInfo);
    }
}