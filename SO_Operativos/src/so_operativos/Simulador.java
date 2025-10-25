package so_operativos;

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


public class Simulador {
    private final ConfiguracionSimulacion config;
    private Planificador planificador;

    private final CustomQueue colaListos;
    private final CustomQueue colaListosSuspendidos;
    private final CustomQueue colaBloqueadosSuspendidos;

    private final ConcurrentHashMap<Integer, Thread> procesosEnExcepcion;
    private final ConcurrentHashMap<Integer, Proceso> mapaProcesosBloqueados;

    private Proceso[] procesosTerminadosArray;
    private int terminadosCount;
    private static final int INITIAL_CAPACITY = 10;

    public Proceso procesoActual;
    private transient Thread procesoThread;

    private long tiempoSimulacion;
    private int ciclosQuantum;
    private final transient Semaphore cpuSemaphore;

    private static final int UMBRAL_PROCESOS_MEMORIA = 5;
    private static final String ESTADO_FILE = "sim_estado.txt";

    private long tiempoInicioSimulacionReal;
    private long tiempoTotalCpuOcupado;
    private long tiempoInicioUsoCpuActual;
    private List<Proceso> listaProcesosCompletados;

    public Planificador getPlanificador() { return this.planificador; }
    public ConfiguracionSimulacion getConfig() { return this.config; }

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

        if (!cargarEstado()) {
            Logger.log("No se cargó estado previo. Iniciando simulación nueva.");
            this.planificador = planificadorInicial;
        } else {
            Logger.log("Estado cargado desde " + ESTADO_FILE);
            asignarSemaforoAProcesos();
            reiniciarHilosPostCarga();
             if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) {
                tiempoInicioUsoCpuActual = System.currentTimeMillis();
             }
        }
         Logger.log("Simulador listo. Planificador: " + (this.planificador != null ? this.planificador.getNombre() : "Ninguno"));
    }

    public void setPlanificador(Planificador planificador) {
        Planificador anterior = this.planificador;
        this.planificador = planificador;
        Logger.log("Cambio de Planificador: "
                + (anterior != null ? anterior.getNombre() : "Ninguno") + " -> "
                + (planificador != null ? planificador.getNombre() : "Ninguno"));
        reordenarColaListos(null);
    }

    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore);
        Logger.log("Solicitud para agregar Proceso " + proceso.getId() + " (" + proceso.getNombre() + ")");
        if (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             colaListos.add(proceso);
             proceso.setEstado(EstadoProceso.LISTO);
             reordenarColaListos(proceso);
        } else {
            colaListosSuspendidos.add(proceso);
            proceso.setEstado(EstadoProceso.SUSPENDIDO_LISTO);
        }
        revisarYSuspenderSiNecesario();
    }

    private void revisarYSuspenderSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() > UMBRAL_PROCESOS_MEMORIA) {
             Proceso aSuspender = null;
             EstadoProceso nuevoEstado = null;
             CustomQueue origen = null;

             if (!colaListos.isEmpty()) {
                 aSuspender = buscarProcesoMenosPrioritario(colaListos);
                 origen = colaListos;
                 nuevoEstado = EstadoProceso.SUSPENDIDO_LISTO;
             } else if (!mapaProcesosBloqueados.isEmpty()) {
                 aSuspender = buscarProcesoMenosPrioritarioEnMapa(mapaProcesosBloqueados);
                 origen = null;
                 nuevoEstado = EstadoProceso.SUSPENDIDO_BLOQUEADO;
             }

             if (aSuspender != null) {
                 Logger.log("SUSPENSIÓN: Proceso " + aSuspender.getId() + " (" + aSuspender.getNombre() + ") por memoria llena.");
                 if (origen == colaListos) {
                    colaListos.remove(aSuspender);
                    aSuspender.setEstado(nuevoEstado);
                    colaListosSuspendidos.add(aSuspender);
                 } else {
                    mapaProcesosBloqueados.remove(aSuspender.getId());
                    Thread hiloExcepcion = procesosEnExcepcion.remove(aSuspender.getId());
                    if (hiloExcepcion != null && hiloExcepcion.isAlive()) {
                        hiloExcepcion.interrupt();
                        Logger.log("   -> Hilo E/S para " + aSuspender.getId() + " interrumpido por suspensión.");
                    }
                    aSuspender.setEstado(nuevoEstado);
                    colaBloqueadosSuspendidos.add(aSuspender);
                 }
             } else {
                 Logger.log("Advertencia: Se necesita suspender pero no se encontró candidato (Listo/Bloq vacíos?).");
                 break;
             }
         }
    }

    private void revisarYReanudarSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             Proceso aReanudar = null;
             EstadoProceso estadoDestino = null;

             if (!colaListosSuspendidos.isEmpty()) {
                 aReanudar = buscarProcesoMasPrioritario(colaListosSuspendidos);
                 if (aReanudar != null) {
                    colaListosSuspendidos.remove(aReanudar);
                    estadoDestino = EstadoProceso.LISTO;
                    colaListos.add(aReanudar);
                 }
             }
             else if (!colaBloqueadosSuspendidos.isEmpty()) {
                  aReanudar = buscarProcesoMasPrioritario(colaBloqueadosSuspendidos);
                  if (aReanudar != null) {
                      colaBloqueadosSuspendidos.remove(aReanudar);
                      estadoDestino = EstadoProceso.BLOQUEADO;
                      mapaProcesosBloqueados.put(aReanudar.getId(), aReanudar);
                      reanudarManejadorExcepcion(aReanudar);
                  }
             }

             if (aReanudar != null) {
                 Logger.log("REANUDACIÓN: Proceso " + aReanudar.getId() + " (" + aReanudar.getNombre() + ") reanudado a " + estadoDestino + ".");
                 aReanudar.setEstado(estadoDestino);
                 if (estadoDestino == EstadoProceso.LISTO) {
                    reordenarColaListos(aReanudar);
                 }
             } else {
                 break;
             }
         }
    }

    private Proceso buscarProcesoMenosPrioritario(CustomQueue queue) {
        if (queue.isEmpty()) return null;
        Proceso[] array = queue.toArray();
        Proceso peor = array[0];
        for (int i = 1; i < array.length; i++) {
             if (array[i].getPrioridad() >= peor.getPrioridad()) {
                 peor = array[i];
             }
        }
        return peor;
    }

     private Proceso buscarProcesoMenosPrioritarioEnMapa(ConcurrentHashMap<Integer, Proceso> map) {
        if (map.isEmpty()) return null;
        Proceso peor = null;
        for (Proceso p : map.values()) {
             if (peor == null || p.getPrioridad() >= peor.getPrioridad()) {
                 peor = p;
             }
        }
        return peor;
     }

    private Proceso buscarProcesoMasPrioritario(CustomQueue queue) {
         if (queue.isEmpty()) return null;
         Proceso[] array = queue.toArray();
         Proceso mejor = array[0];
         for (int i = 1; i < array.length; i++) {
              if (array[i].getPrioridad() < mejor.getPrioridad()) {
                  mejor = array[i];
              }
         }
         return mejor;
     }

    private void reanudarManejadorExcepcion(Proceso proceso) {
        if (proceso.getEstado() != EstadoProceso.BLOQUEADO) {
            proceso.setEstado(EstadoProceso.BLOQUEADO);
        }
        Logger.log("Reiniciando ManejadorExcepcion para Proceso " + proceso.getId() + " (E/S restante).");
        ManejadorExcepcion handler = new ManejadorExcepcion(proceso, colaListos, config.getDuracionCicloMs());
        Thread handlerThread = new Thread(handler, "Excepción-" + proceso.getId() + "-Reanudado");
        handlerThread.start();
        procesosEnExcepcion.put(proceso.getId(), handlerThread);
    }

    private void reordenarColaListos(Proceso nuevoProceso) {
        if (planificador instanceof PlanificadorFCFS || planificador instanceof PlanificadorRoundRobin) return;
        Proceso[] lista = colaListos.toArray();
        int length = colaListos.size();
        if (length <= 1) return;

        boolean swapped;
        for (int i = 0; i < length - 1; i++) {
            swapped = false;
            for (int j = 0; j < length - 1 - i; j++) {
                boolean swapCondition = false;
                Proceso p1 = lista[j];
                Proceso p2 = lista[j+1];

                if (planificador instanceof PlanificadorSJF || planificador instanceof PlanificadorSRT) {
                    if (p1.getInstruccionesRestantes() > p2.getInstruccionesRestantes()) swapCondition = true;
                } else {
                    if (p1.getPrioridad() > p2.getPrioridad()) swapCondition = true;
                }

                if (swapCondition) {
                    Proceso temp = lista[j];
                    lista[j] = lista[j+1];
                    lista[j+1] = temp;
                    swapped = true;
                }
            }
             if (!swapped) break;
        }
        colaListos.rebuildFrom(lista, length);
    }

    public boolean quedanProcesos() {
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty()
                || !colaListosSuspendidos.isEmpty() || !colaBloqueadosSuspendidos.isEmpty();
    }

    public void ejecutarCicloSimulacion() {
        long inicioCicloReal = System.currentTimeMillis();
        tiempoSimulacion += config.getDuracionCicloMs();
        Logger.log("Inicio Ciclo " + (tiempoSimulacion / config.getDuracionCicloMs()) + " @ Tiempo Simulado " + tiempoSimulacion + "ms");

        if (procesoActual != null && tiempoInicioUsoCpuActual > 0) {
            tiempoTotalCpuOcupado += (inicioCicloReal - tiempoInicioUsoCpuActual);
            tiempoInicioUsoCpuActual = 0;
        }

        manejarExcepciones();
        comprobarExpropiacion();
        revisarYSuspenderSiNecesario();
        revisarYReanudarSiNecesario();

        if (procesoActual == null) {
             Logger.log("CPU Idle. Intentando planificar...");
            planificarSiguiente();
        } else {
             tiempoInicioUsoCpuActual = inicioCicloReal;
            manejarQuantum();
        }

        chequearEstadoEjecucion();
        mostrarEstado();

        long finCicloLogicoReal = System.currentTimeMillis();
        long duracionLogica = finCicloLogicoReal - inicioCicloReal;
        long tiempoDormir = config.getDuracionCicloMs() - duracionLogica;

        if (tiempoDormir > 0) {
            try {
                Thread.sleep(tiempoDormir);
            } catch (InterruptedException e) {
                Logger.log("WARN: Sleep del ciclo principal interrumpido.");
                Thread.currentThread().interrupt();
            }
        } else {
        }
         if (procesoActual != null && tiempoInicioUsoCpuActual > 0) {
            long tiempoRealFinCiclo = System.currentTimeMillis();
             tiempoTotalCpuOcupado += (tiempoRealFinCiclo - inicioCicloReal);
             tiempoInicioUsoCpuActual = tiempoRealFinCiclo;
         } else {
             tiempoInicioUsoCpuActual = 0;
         }

    }

    private void manejarExcepciones() {
        procesosEnExcepcion.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                Proceso p = mapaProcesosBloqueados.remove(entry.getKey());
                if (p != null) {
                    Logger.log("Hilo E/S para Proceso " + p.getId() + " terminado. Proceso movido a LISTO.");
                    revisarYReanudarSiNecesario();
                } else {
                     Logger.log("WARN: Hilo E/S para ID " + entry.getKey() + " terminado, pero proceso no encontrado en mapa Bloqueados.");
                }
                return true;
            }
            return false;
        });
    }

    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return;

        if (procesoActual.haTerminado()) {
            Proceso terminado = procesoActual;

            terminado.setEstado(EstadoProceso.TERMINADO);
            Logger.log("PROCESO TERMINADO: ID=" + terminado.getId() + ", Nombre=" + terminado.getNombre() + ", Tiempo Retorno=" + terminado.getTiempoRetorno() + "ms");

            listaProcesosCompletados.add(terminado);

            if (terminadosCount >= procesosTerminadosArray.length) {
                Proceso[] newArray = new Proceso[procesosTerminadosArray.length * 2];
                 for (int i = 0; i < terminadosCount; i++) newArray[i] = procesosTerminadosArray[i];
                procesosTerminadosArray = newArray;
            }
            procesosTerminadosArray[terminadosCount++] = terminado;

            if (procesoThread != null) procesoThread.interrupt();
            procesoActual = null;
            procesoThread = null;

            revisarYReanudarSiNecesario();

        } else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
             Proceso bloqueado = procesoActual;
             if (procesoThread != null) procesoThread.interrupt();
             procesoActual = null;
             procesoThread = null;

             Logger.log("E/S Requerida: Proceso " + bloqueado.getId() + " (" + bloqueado.getNombre() + ")");

             if (colaListos.size() + mapaProcesosBloqueados.size() + 1 <= UMBRAL_PROCESOS_MEMORIA) {
                 bloqueado.setEstado(EstadoProceso.BLOQUEADO);
                 mapaProcesosBloqueados.put(bloqueado.getId(), bloqueado);
                 ManejadorExcepcion handler = new ManejadorExcepcion(bloqueado, colaListos, config.getDuracionCicloMs());
                 Thread handlerThread = new Thread(handler, "Excepción-" + bloqueado.getId());
                 handlerThread.start();
                 procesosEnExcepcion.put(bloqueado.getId(), handlerThread);
                 Logger.log("   -> Proceso " + bloqueado.getId() + " pasa a BLOQUEADO.");
             } else {
                 bloqueado.setEstado(EstadoProceso.SUSPENDIDO_BLOQUEADO);
                 colaBloqueadosSuspendidos.add(bloqueado);
                 Logger.log("   -> Proceso " + bloqueado.getId() + " pasa a SUSPENDIDO_BLOQUEADO (Memoria llena).");
             }
             revisarYReanudarSiNecesario();
             revisarYSuspenderSiNecesario();
        }
    }

    private void comprobarExpropiacion() {
        if (procesoActual == null || colaListos.isEmpty()) return;
        boolean expropiar = false;
        Proceso candidato = colaListos.peek();
        if (candidato == null) return;

        if (planificador instanceof PlanificadorSRT) {
            if (candidato.getInstruccionesRestantes() < procesoActual.getInstruccionesRestantes()) expropiar = true;
        } else if (planificador instanceof PlanificadorPrioridadExpropiativa) {
            if (candidato.getPrioridad() < procesoActual.getPrioridad()) expropiar = true;
        }

        if (expropiar) {
            Logger.log("EXPROPIACIÓN: Proceso " + candidato.getId() + " (" + candidato.getNombre() + ") expropia a " + procesoActual.getId() + " (" + procesoActual.getNombre() + ").");
            Proceso expropiado = procesoActual;
            if (procesoThread != null) procesoThread.interrupt();
            procesoActual = null;
            procesoThread = null;

            expropiado.setEstado(EstadoProceso.LISTO);
            colaListos.add(expropiado);
            reordenarColaListos(expropiado);
        }
    }

    private void manejarQuantum() {
        if (procesoActual != null && planificador instanceof PlanificadorRoundRobin rr) {
            ciclosQuantum--;
            if (ciclosQuantum <= 0) {
                 Logger.log("QUANTUM FIN: Proceso " + procesoActual.getId() + " (" + procesoActual.getNombre() + ") vuelve a LISTO.");
                 Proceso expropiado = procesoActual;
                 if (procesoThread != null) procesoThread.interrupt();
                 procesoActual = null;
                 procesoThread = null;

                 expropiado.setEstado(EstadoProceso.LISTO);
                 colaListos.add(expropiado);

                 revisarYSuspenderSiNecesario();
                 revisarYReanudarSiNecesario();
            }
        }
    }

    private void planificarSiguiente() {
        if (procesoActual == null && !colaListos.isEmpty()) {
            Proceso siguienteProceso = planificador.seleccionarSiguiente(colaListos);

            if (siguienteProceso != null) {
                procesoActual = siguienteProceso;
                Logger.log("CONTEXT SWITCH: Proceso " + procesoActual.getId() + " (" + procesoActual.getNombre() + ") seleccionado para ejecución.");
                procesoActual.setEstado(EstadoProceso.EJECUCION);

                if (planificador instanceof PlanificadorRoundRobin rr) {
                     ciclosQuantum = rr.getQuantum();
                     Logger.log("   -> Quantum asignado: " + ciclosQuantum);
                } else {
                     ciclosQuantum = 0;
                }

                tiempoInicioUsoCpuActual = System.currentTimeMillis();

                procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId());
                procesoThread.start();
            } else {
                 Logger.log("Planificador no seleccionó proceso (¿cola vacía después de peek?). CPU Idle.");
            }
        }
    }

    public void mostrarEstado() {
        System.out.println("\n--- VISUALIZACIÓN DE ESTADO DEL KERNEL ---");
        String planificadorNombre = (planificador != null) ? planificador.getNombre() : "N/A";
        System.out.printf("   [SO] Tiempo: %dms | Política: %s | Ciclo: %dms%n",
                             tiempoSimulacion, planificadorNombre, config.getDuracionCicloMs());
        System.out.printf("   [Memoria] Procesos Activos (Listo+Bloq): %d/%d%n",
                             colaListos.size() + mapaProcesosBloqueados.size(), UMBRAL_PROCESOS_MEMORIA);

        System.out.println("\n## CPU (Proceso en Ejecución)");
        if (procesoActual != null) {
            procesoActual.mostrarPCB();
             if (planificador instanceof PlanificadorRoundRobin) {
                 System.out.println("     Quantum restante: " + ciclosQuantum);
             }
        } else {
            System.out.println("   [IDLE] CPU inactivo.");
        }

        System.out.println("\n## Cola de Listos (" + colaListos.size() + " procesos)");
        Proceso[] listosArray = colaListos.toArray();
        for(Proceso p : listosArray) p.mostrarPCB();
        if(listosArray.length == 0) System.out.println("   (Vacía)");


        System.out.println("\n## Cola de Bloqueados (" + mapaProcesosBloqueados.size() + " procesos / " + procesosEnExcepcion.size() + " hilos E/S)");
        if (mapaProcesosBloqueados.isEmpty()) {
            System.out.println("   (Vacía)");
        } else {
             for (Proceso p : mapaProcesosBloqueados.values()) p.mostrarPCB();
        }

        System.out.println("\n## Cola de Listos Suspendidos (" + colaListosSuspendidos.size() + " procesos)");
        Proceso[] listosSuspendidosArray = colaListosSuspendidos.toArray();
        for(Proceso p : listosSuspendidosArray) p.mostrarPCB();
         if(listosSuspendidosArray.length == 0) System.out.println("   (Vacía)");

        System.out.println("\n## Cola de Bloqueados Suspendidos (" + colaBloqueadosSuspendidos.size() + " procesos)");
        Proceso[] bloqueadosSuspendidosArray = colaBloqueadosSuspendidos.toArray();
        for(Proceso p : bloqueadosSuspendidosArray) p.mostrarPCB();
        if(bloqueadosSuspendidosArray.length == 0) System.out.println("   (Vacía)");

        System.out.println("\n## Procesos Terminados (" + listaProcesosCompletados.size() + " procesos)");
        for(Proceso p : listaProcesosCompletados) {
             System.out.printf("   [ID %d - %s | Pri:%d | T.Retorno:%dms | T.Espera:%dms | T.Bloq:%dms]%n",
                   p.getId(), p.getNombre(), p.getPrioridad(),
                   p.getTiempoRetorno(), p.getTiempoTotalEsperandoListo(), p.getTiempoTotalBloqueado());
        }
         if(listaProcesosCompletados.isEmpty()) System.out.println("   (Ninguno)");

        System.out.println("----------------------------------------");
    }

    public boolean guardarEstado() {
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


            Logger.log("Estado guardado exitosamente.");
            return true;
        } catch (IOException e) {
            Logger.log("ERROR al guardar estado: " + e.getMessage());
            System.err.println("❌ Error al guardar el estado: " + e.getMessage());
            return false;
        }
    }
    private void guardarCola(BufferedWriter writer, String prefix, CustomQueue queue) throws IOException {
        Proceso[] array = queue.toArray();
        for (Proceso p : array) {
            writer.write(prefix + "," + p.toStringData());
            writer.newLine();
        }
    }
     private void guardarMapaBloqueados(BufferedWriter writer, String prefix, ConcurrentHashMap<Integer, Proceso> map) throws IOException {
        for (Proceso p : map.values()) {
             writer.write(prefix + "," + p.toStringData());
             writer.newLine();
        }
     }
    private void guardarListaTerminados(BufferedWriter writer, String prefix, List<Proceso> lista) throws IOException {
        for (Proceso p : lista) {
            writer.write(prefix + "," + p.toStringData());
            writer.newLine();
        }
    }
    public boolean cargarEstado() {
        Logger.log("CARGANDO ESTADO desde " + ESTADO_FILE + "...");
         List<Proceso> tempListos = new ArrayList<>();
         List<Proceso> tempListosSusp = new ArrayList<>();
         List<Proceso> tempBloqueados = new ArrayList<>();
         List<Proceso> tempBloqSusp = new ArrayList<>();
         List<Proceso> tempTerminados = new ArrayList<>();
         int idProcesoActual = -1;
         String nombrePlanificador = null;
         int quantumRestanteCargado = 0;
         int nextIdCargado = 1;

        try (BufferedReader reader = new BufferedReader(new FileReader(ESTADO_FILE))) {
            String line;
            int maxId = 0;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", 2);
                if (parts.length < 2) continue;
                String prefix = parts[0];
                String data = parts[1];

                try {
                     switch (prefix) {
                         case "TIEMPO": tiempoSimulacion = Long.parseLong(data); break;
                         case "PLANIFICADOR": nombrePlanificador = data.equals("null") ? null : data; break;
                         case "PROCESO_ACTUAL": idProcesoActual = data.equals("IDLE") ? -1 : Integer.parseInt(data); break;
                         case "QUANTUM_RESTANTE": quantumRestanteCargado = Integer.parseInt(data); break;
                         case "NEXT_ID": nextIdCargado = Integer.parseInt(data); break;
                         case "LISTO": case "BLOQUEADO": case "LISTO_SUSP":
                         case "BLOQ_SUSP": case "TERMINADO":
                             Proceso p = Proceso.fromStringData(data);
                             if (p.getId() > maxId) maxId = p.getId();
                             switch (prefix) {
                                 case "LISTO": tempListos.add(p); break;
                                 case "BLOQUEADO": tempBloqueados.add(p); break;
                                 case "LISTO_SUSP": tempListosSusp.add(p); break;
                                 case "BLOQ_SUSP": tempBloqSusp.add(p); break;
                                 case "TERMINADO": tempTerminados.add(p); break;
                             }
                             break;
                         default: Logger.log("WARN: Prefijo desconocido en estado: " + prefix); break;
                     }
                } catch (Exception e) {
                     Logger.log("ERROR parseando línea (" + prefix + "): " + line + " | Error: " + e.getMessage());
                }
            }

            limpiarEstadoSimulador();
            Proceso.resetNextId(Math.max(nextIdCargado, maxId + 1));

             if (nombrePlanificador != null) {
                 try {
                     if (nombrePlanificador.contains("PlanificadorRoundRobin")) {
                         this.planificador = new PlanificadorRoundRobin(Main.DEFAULT_QUANTUM);
                         this.ciclosQuantum = quantumRestanteCargado;
                     } else {
                         Class<?> clazz = Class.forName(nombrePlanificador);
                         this.planificador = (Planificador) clazz.getDeclaredConstructor().newInstance();
                     }
                     Logger.log("   Planificador cargado: " + this.planificador.getNombre());
                 } catch (Exception e) {
                     Logger.log("ERROR al instanciar Planificador " + nombrePlanificador + ": " + e.getMessage() + ". Usando FCFS.");
                     this.planificador = new PlanificadorFCFS();
                 }
             } else {
                  Logger.log("WARN: Planificador no encontrado en estado. Usando FCFS.");
                 this.planificador = new PlanificadorFCFS();
             }


             
            colaListos.rebuildFrom(tempListos.toArray(new Proceso[0]), tempListos.size());
            colaListosSuspendidos.rebuildFrom(tempListosSusp.toArray(new Proceso[0]), tempListosSusp.size());
            colaBloqueadosSuspendidos.rebuildFrom(tempBloqSusp.toArray(new Proceso[0]), tempBloqSusp.size());
            for (Proceso p : tempBloqueados) mapaProcesosBloqueados.put(p.getId(), p);

             
            listaProcesosCompletados.addAll(tempTerminados);
             
             terminadosCount = 0;
             if (listaProcesosCompletados.size() > procesosTerminadosArray.length) {
                 procesosTerminadosArray = new Proceso[listaProcesosCompletados.size()];
             }
             for(Proceso p : listaProcesosCompletados) {
                 procesosTerminadosArray[terminadosCount++] = p;
             }


             
              procesoActual = buscarProcesoPorId(idProcesoActual, tempListos, tempBloqueados, tempListosSusp, tempBloqSusp, tempTerminados);
              if (procesoActual != null) {
                   if(procesoActual.getEstado() == EstadoProceso.LISTO || procesoActual.getEstado() == EstadoProceso.EJECUCION){
                       procesoActual.setEstado(EstadoProceso.EJECUCION);
                       
                       colaListos.remove(procesoActual);
                   } else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
                       
                   } else {
                       Logger.log("WARN: Proceso actual cargado (" + procesoActual.getId() + ") estaba en estado " + procesoActual.getEstado() + ". CPU queda IDLE.");
                       procesoActual = null;
                   }
                  Logger.log("   Proceso actual cargado: ID " + (procesoActual != null ? procesoActual.getId() : "IDLE"));
              } else if (idProcesoActual != -1) {
                   Logger.log("ERROR: Proceso actual ID " + idProcesoActual + " no encontrado.");
              }

            Logger.log("Carga de estado completada.");
            return true;

        } catch (IOException e) {
            Logger.log("ERROR al leer archivo de estado " + ESTADO_FILE + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
             Logger.log("ERROR inesperado durante carga: " + e.getMessage());
             e.printStackTrace();
             return false;
        }
    }
    private Proceso buscarProcesoPorId(int id, List<Proceso>... listas) {
        if (id == -1) return null;
        for (List<Proceso> lista : listas) {
             for (Proceso p : lista) {
                 if (p.getId() == id) {
                     return p;
                 }
             }
        }
        
         if (mapaProcesosBloqueados.containsKey(id)) {
             return mapaProcesosBloqueados.get(id);
         }
        return null;
     }
    private void limpiarEstadoSimulador() {
        if (procesoThread != null && procesoThread.isAlive()) procesoThread.interrupt();
        for (Thread t : procesosEnExcepcion.values()) if (t != null && t.isAlive()) t.interrupt();

        colaListos.rebuildFrom(new Proceso[0], 0);
        colaListosSuspendidos.rebuildFrom(new Proceso[0], 0);
        colaBloqueadosSuspendidos.rebuildFrom(new Proceso[0], 0);
        procesosEnExcepcion.clear();
        mapaProcesosBloqueados.clear();
        procesosTerminadosArray = new Proceso[INITIAL_CAPACITY];
        terminadosCount = 0;
        listaProcesosCompletados.clear();
        procesoActual = null;
        procesoThread = null;
        tiempoSimulacion = 0;
        ciclosQuantum = 0;
        tiempoTotalCpuOcupado = 0;
        tiempoInicioUsoCpuActual = 0;
        tiempoInicioSimulacionReal = System.currentTimeMillis();
     }
    public void asignarSemaforoAProcesos() {
         asignarSemaforoEnCola(colaListos);
         asignarSemaforoEnCola(colaListosSuspendidos);
         asignarSemaforoEnCola(colaBloqueadosSuspendidos);
         for(Proceso p : mapaProcesosBloqueados.values()) p.setCpuSemaphore(this.cpuSemaphore);
        if (procesoActual != null) procesoActual.setCpuSemaphore(this.cpuSemaphore);
        
     }
     private void asignarSemaforoEnCola(CustomQueue queue) {
         Proceso[] array = queue.toArray();
         for (Proceso p : array) p.setCpuSemaphore(this.cpuSemaphore);
     }
    public void reiniciarHilosPostCarga() {
         Logger.log("Reiniciando hilos post-carga...");
         ConcurrentHashMap<Integer, Proceso> copiaMapaBloqueados = new ConcurrentHashMap<>(mapaProcesosBloqueados);
         for (Proceso p : copiaMapaBloqueados.values()) {
             if (p.getEstado() == EstadoProceso.BLOQUEADO) {
                 reanudarManejadorExcepcion(p);
             }
         }
        if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) {
             Logger.log("   -> Reiniciando hilo CPU para Proceso " + procesoActual.getId());
             tiempoInicioUsoCpuActual = System.currentTimeMillis();
             procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId() + "-Reanudado");
             procesoThread.start();
         }
     }

    public void calcularYMostrarMetricas() {
        if (listaProcesosCompletados.isEmpty()) {
            System.out.println("\n--- MÉTRICAS DE RENDIMIENTO ---");
            System.out.println("   No hay procesos completados para calcular métricas.");
            return;
        }

        long tiempoTotalSimulacionReal = System.currentTimeMillis() - tiempoInicioSimulacionReal;
        if (tiempoTotalSimulacionReal == 0) tiempoTotalSimulacionReal = 1;

        
        double tiempoTotalSegundos = tiempoTotalSimulacionReal / 1000.0;
        double throughput = (tiempoTotalSegundos > 0) ? listaProcesosCompletados.size() / tiempoTotalSegundos : 0;

        
        if (procesoActual != null && tiempoInicioUsoCpuActual > 0) {
             tiempoTotalCpuOcupado += (System.currentTimeMillis() - tiempoInicioUsoCpuActual);
        }
        double utilizacionCpu = (double) tiempoTotalCpuOcupado * 100.0 / tiempoTotalSimulacionReal;

        
        long sumaTiemposRespuesta = 0;
        long sumaTiemposRetorno = 0;
        long sumaTiemposEspera = 0;
        int countParaPromedio = 0;

        for (Proceso p : listaProcesosCompletados) {
            long tRetorno = p.getTiempoRetorno();
            if (tRetorno >= 0) {
                 
                 long tRespuesta = p.getTiempoRespuesta();
                 long tEspera = p.getTiempoTotalEsperandoListo();

                 sumaTiemposRetorno += tRetorno;
                 sumaTiemposRespuesta += tRespuesta;
                 sumaTiemposEspera += tEspera;
                 countParaPromedio++;
            }
        }

        double tiempoRespuestaPromedio = (countParaPromedio > 0) ? (double) sumaTiemposRespuesta / countParaPromedio : 0;
        double tiempoRetornoPromedio = (countParaPromedio > 0) ? (double) sumaTiemposRetorno / countParaPromedio : 0;
        double tiempoEsperaPromedio = (countParaPromedio > 0) ? (double) sumaTiemposEspera / countParaPromedio : 0;


        System.out.println("\n--- MÉTRICAS DE RENDIMIENTO ---");
        System.out.printf("   Tiempo Total Simulación (Real): %.3f s%n", tiempoTotalSegundos);
        System.out.printf("   Procesos Completados: %d%n", listaProcesosCompletados.size());
        System.out.printf("   Throughput: %.3f procesos/s%n", throughput);
        System.out.printf("   Tiempo Total CPU Ocupado: %d ms%n", tiempoTotalCpuOcupado);
        System.out.printf("   Utilización de CPU: %.2f%%%n", utilizacionCpu);
        System.out.printf("   Tiempo de Retorno Promedio: %.2f ms%n", tiempoRetornoPromedio);
        System.out.printf("   Tiempo de Respuesta Promedio (aprox): %.2f ms%n", tiempoRespuestaPromedio);
        System.out.printf("   Tiempo de Espera Promedio (en Listo/SuspListo): %.2f ms%n", tiempoEsperaPromedio);
        System.out.println("---------------------------------");

        
         Logger.log(String.format("METRICAS FINALES: Procesos=%d, Throughput=%.3f p/s, CPU Util=%.2f%%, T.RetornoAvg=%.2fms, T.RespAvg=M.2fms, T.EsperaAvg=%.2fms",
                 listaProcesosCompletados.size(), throughput, utilizacionCpu, tiempoRetornoPromedio, tiempoRespuestaPromedio, tiempoEsperaPromedio));
    }


     public void cerrarSimulador() {
         Logger.log("Cerrando Simulador...");
          if (procesoThread != null && procesoThread.isAlive()) {
              procesoThread.interrupt();
          }
          for (Thread t : procesosEnExcepcion.values()) {
              if (t != null && t.isAlive()) {
                  t.interrupt();
              }
          }
         
         calcularYMostrarMetricas();
         Logger.close();
     }


}