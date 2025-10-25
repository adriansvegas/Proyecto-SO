package so_operativos;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
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

    private Proceso procesoActual;
    private transient Thread procesoThread; // transient: no se guarda directamente

    private long tiempoSimulacion;
    private int ciclosQuantum;
    private final transient Semaphore cpuSemaphore; // transient: no se guarda

    private static final int UMBRAL_PROCESOS_MEMORIA = 5;

    // Nombre del archivo de estado
    private static final String ESTADO_FILE = "sim_estado.txt";


    // Getters requeridos por Main.java
    public Planificador getPlanificador() { return this.planificador; }
    public ConfiguracionSimulacion getConfig() { return this.config; }

    public Simulador(ConfiguracionSimulacion config, Planificador planificadorInicial) {
        this.config = config;
        // No establecer planificador aquí si vamos a cargarlo
        // this.planificador = planificador;

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

        // Intenta cargar el estado al iniciar, si falla, usa el planificador inicial
         if (!cargarEstado()) {
             System.out.println("No se encontró archivo de estado o falló la carga. Iniciando simulación desde cero.");
             this.planificador = planificadorInicial; // Usa el planificador por defecto
             // Podrías añadir procesos de ejemplo aquí si quieres que existan al iniciar sin archivo
             /*
             agregarProceso(new Proceso("P1-ALTA_PRIO", 500, 1));
             agregarProceso(new Proceso("P2-IO_LOW", 300, 8, 50, 150));
             agregarProceso(new Proceso("P3-SJF_SHORT", 200, 5));
             agregarProceso(new Proceso("P4-IO_MED", 800, 5, 20, 100));
             */
         } else {
             System.out.println("Estado de la simulación cargado exitosamente desde " + ESTADO_FILE);
             // Asignar semáforo a todos los procesos cargados
              asignarSemaforoAProcesos();
              // Reiniciar hilos necesarios (especialmente los de E/S bloqueados)
              reiniciarHilosPostCarga();
         }
    }

    public void setPlanificador(Planificador planificador) {
        this.planificador = planificador;
        System.out.println("✅ Política de planificación cambiada a: " + planificador.getNombre());
        reordenarColaListos(null);
    }

    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore);
        if (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             colaListos.add(proceso);
             proceso.setEstado(EstadoProceso.LISTO);
             System.out.println("➡️ Proceso " + proceso.getNombre() + " agregado a LISTO.");
             reordenarColaListos(proceso);
        } else {
            colaListosSuspendidos.add(proceso);
            proceso.setEstado(EstadoProceso.SUSPENDIDO_LISTO);
            System.out.println("⏳ Proceso " + proceso.getNombre() + " agregado a SUSPENDIDO_LISTO (Memoria llena).");
        }
         revisarYSuspenderSiNecesario(); // Revisar si este nuevo proceso fuerza a suspender otro
    }

    private void revisarYSuspenderSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() > UMBRAL_PROCESOS_MEMORIA) {
             // Decide a quién suspender: ¿Listo o Bloqueado?
             // Estrategia simple: si hay listos, suspende al de menor prioridad. Si no, suspende al bloqueado de menor prioridad.
             Proceso aSuspender = null;
             EstadoProceso nuevoEstado = null;
             CustomQueue origen = null;

             if (!colaListos.isEmpty()) {
                 aSuspender = buscarProcesoMenosPrioritario(colaListos);
                 origen = colaListos;
                 nuevoEstado = EstadoProceso.SUSPENDIDO_LISTO;
             } else if (!mapaProcesosBloqueados.isEmpty()) {
                 // Buscar en mapaProcesosBloqueados (necesita iterar o método auxiliar)
                 aSuspender = buscarProcesoMenosPrioritarioEnMapa(mapaProcesosBloqueados);
                 origen = null; // Indicar que viene del mapa
                 nuevoEstado = EstadoProceso.SUSPENDIDO_BLOQUEADO;
             }

             if (aSuspender != null) {
                 if (origen == colaListos) {
                    colaListos.remove(aSuspender);
                    aSuspender.setEstado(nuevoEstado);
                    colaListosSuspendidos.add(aSuspender);
                    System.out.println("⚠️ MEMORIA: Proceso " + aSuspender.getNombre() + " suspendido (LISTO -> SUSP_LISTO).");
                 } else { // Viene de bloqueado
                    mapaProcesosBloqueados.remove(aSuspender.getId());
                    // Interrumpir su hilo de E/S si está activo
                    Thread hiloExcepcion = procesosEnExcepcion.remove(aSuspender.getId());
                    if (hiloExcepcion != null && hiloExcepcion.isAlive()) {
                        hiloExcepcion.interrupt();
                    }
                    aSuspender.setEstado(nuevoEstado);
                    colaBloqueadosSuspendidos.add(aSuspender);
                    System.out.println("⚠️ MEMORIA: Proceso " + aSuspender.getNombre() + " suspendido (BLOQUEADO -> SUSP_BLOQ).");
                 }
             } else {
                 break; // No se pudo encontrar proceso para suspender
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
                     // Ponerlo en el mapa y reiniciar su hilo de E/S
                     mapaProcesosBloqueados.put(aReanudar.getId(), aReanudar);
                     reanudarManejadorExcepcion(aReanudar); // Inicia el hilo de E/S
                  }
             }

             if (aReanudar != null) {
                 aReanudar.setEstado(estadoDestino);
                 System.out.println("⭐ MEMORIA: Proceso " + aReanudar.getNombre() + " reanudado a " + estadoDestino + ".");
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
            proceso.setEstado(EstadoProceso.BLOQUEADO); // Asegurar estado
        }
        System.out.println("   [Simulador] Reanudando E/S para " + proceso.getNombre() + " desde ciclo " + proceso.getContadorIOCiclos());
        ManejadorExcepcion handler = new ManejadorExcepcion(
            proceso,
            colaListos, // Al terminar E/S, vuelve a LISTO
            config.getDuracionCicloMs()
            // El manejador ahora lee el contador inicial del proceso
        );
        Thread handlerThread = new Thread(handler, "Excepción-" + proceso.getId() + "-Reanudado");
        handlerThread.start();
        procesosEnExcepcion.put(proceso.getId(), handlerThread);
    }

    private void reordenarColaListos(Proceso nuevoProceso) {
        // ... (código sin cambios) ...
        if (planificador instanceof PlanificadorFCFS || planificador instanceof PlanificadorRoundRobin) return;

        Proceso[] lista = colaListos.toArray();
        int length = colaListos.size();

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
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty()
               || !colaListosSuspendidos.isEmpty() || !colaBloqueadosSuspendidos.isEmpty();
    }

    public void ejecutarCicloSimulacion() {
        // ... (código sin cambios hasta el final) ...
        tiempoSimulacion += config.getDuracionCicloMs();
        System.out.println("\n--- Ciclo de Simulación @ " + tiempoSimulacion + "ms ---");

        manejarExcepciones();
        comprobarExpropiacion();

        revisarYSuspenderSiNecesario();
        revisarYReanudarSiNecesario();

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
        procesosEnExcepcion.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                 // El proceso ya habrá sido movido a LISTO por el ManejadorExcepcion
                 // Solo necesitamos quitarlo del mapa de bloqueados aquí
                mapaProcesosBloqueados.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return;

        if (procesoActual.haTerminado()) {
            procesoActual.setEstado(EstadoProceso.TERMINADO);

            if (terminadosCount >= procesosTerminadosArray.length) {
                Proceso[] newArray = new Proceso[procesosTerminadosArray.length * 2];
                 for (int i = 0; i < terminadosCount; i++) {
                     newArray[i] = procesosTerminadosArray[i];
                 }
                procesosTerminadosArray = newArray;
            }
            procesosTerminadosArray[terminadosCount++] = procesoActual;

            if (procesoThread != null) procesoThread.interrupt();
            Proceso terminado = procesoActual;
            procesoActual = null;
            procesoThread = null;
            System.out.println("🛑 Proceso " + terminado.getNombre() + " terminó.");

            revisarYReanudarSiNecesario();

        } else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
            if (procesoThread != null) procesoThread.interrupt();

             Proceso bloqueado = procesoActual;
             procesoActual = null;
             procesoThread = null;

             System.out.println("🚨 EXCEPCIÓN: " + bloqueado.getNombre() + " genera E/S.");

             if (colaListos.size() + mapaProcesosBloqueados.size() + 1 <= UMBRAL_PROCESOS_MEMORIA) {
                 bloqueado.setEstado(EstadoProceso.BLOQUEADO);
                 mapaProcesosBloqueados.put(bloqueado.getId(), bloqueado);
                 ManejadorExcepcion handler = new ManejadorExcepcion(
                     bloqueado,
                     colaListos,
                     config.getDuracionCicloMs()
                 );
                 Thread handlerThread = new Thread(handler, "Excepción-" + bloqueado.getId());
                 handlerThread.start();
                 procesosEnExcepcion.put(bloqueado.getId(), handlerThread);
                 System.out.println("   -> Pasa a estado BLOQUEADO.");
             } else {
                 bloqueado.setEstado(EstadoProceso.SUSPENDIDO_BLOQUEADO);
                 colaBloqueadosSuspendidos.add(bloqueado);
                 System.out.println("   -> Pasa a estado SUSPENDIDO_BLOQUEADO (Memoria llena).");
             }
             revisarYReanudarSiNecesario();
             revisarYSuspenderSiNecesario();
        }
    }

    private void comprobarExpropiacion() {
        // ... (código sin cambios) ...
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
            System.out.println("🚨 EXPROPIACIÓN: Proceso " + candidato.getNombre() + " expropia a " + procesoActual.getNombre() + ".");
            procesoActual.setEstado(EstadoProceso.LISTO);
            if (procesoThread != null) procesoThread.interrupt();

            Proceso expropiado = procesoActual;
            procesoActual = null;
            procesoThread = null;

            colaListos.add(expropiado);
            reordenarColaListos(expropiado);
        }
    }

    private void manejarQuantum() {
        // ... (código sin cambios) ...
        if (procesoActual != null && planificador instanceof PlanificadorRoundRobin rr) {
            ciclosQuantum--;
            if (ciclosQuantum <= 0) {
                 System.out.println("⏱️ Quantum terminado para " + procesoActual.getNombre() + ". Expropiado a LISTO.");
                procesoActual.setEstado(EstadoProceso.LISTO);

                if (procesoThread != null) procesoThread.interrupt();

                Proceso expropiado = procesoActual;
                procesoActual = null;
                procesoThread = null;

                colaListos.add(expropiado);

                 revisarYSuspenderSiNecesario();
                 revisarYReanudarSiNecesario();
            }
        }
    }

    private void planificarSiguiente() {
        // ... (código sin cambios) ...
        if (procesoActual == null && !colaListos.isEmpty()) {
            Proceso siguienteProceso;
            siguienteProceso = planificador.seleccionarSiguiente(colaListos);

            if (siguienteProceso != null) {
                procesoActual = siguienteProceso;
                procesoActual.setEstado(EstadoProceso.EJECUCION);

                if (planificador instanceof PlanificadorRoundRobin rr) {
                    ciclosQuantum = rr.getQuantum();
                } else {
                    ciclosQuantum = 0;
                }

                procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId());
                procesoThread.start();

                System.out.println("⬅️ CONTEXT SWITCH: Proceso " + procesoActual.getNombre() + " pasa a EJECUCIÓN.");
            }
        }
    }

    public void mostrarEstado() {
        // ... (código sin cambios) ...
        System.out.println("\n--- VISUALIZACIÓN DE ESTADO DEL KERNEL ---");
        System.out.printf("   [SO] Tiempo: %dms | Política: %s | Ciclo: %dms%n",
                             tiempoSimulacion, planificador.getNombre(), config.getDuracionCicloMs());
        System.out.printf("   [Memoria] Procesos Activos (Listo+Bloq): %d/%d%n",
                             colaListos.size() + mapaProcesosBloqueados.size(), UMBRAL_PROCESOS_MEMORIA);

        System.out.println("\n## CPU (Proceso en Ejecución)");
        if (procesoActual != null) {
            procesoActual.mostrarPCB();
             if (planificador instanceof PlanificadorRoundRobin) {
                 System.out.println("      Quantum restante: " + ciclosQuantum);
             }
        } else {
            System.out.println("   [IDLE] CPU inactivo.");
        }

        System.out.println("\n## Cola de Listos (" + colaListos.size() + " procesos)");
        Proceso[] listosArray = colaListos.toArray();
        for(Proceso p : listosArray) {
            p.mostrarPCB();
        }

        System.out.println("\n## Cola de Bloqueados (" + mapaProcesosBloqueados.size() + " procesos / " + procesosEnExcepcion.size() + " hilos E/S)");
        if (mapaProcesosBloqueados.isEmpty()) {
            System.out.println("   (Sin procesos bloqueados por E/S)");
        } else {
             for (Proceso p : mapaProcesosBloqueados.values()) {
                  p.mostrarPCB();
             }
        }

        System.out.println("\n## Cola de Listos Suspendidos (" + colaListosSuspendidos.size() + " procesos)");
        Proceso[] listosSuspendidosArray = colaListosSuspendidos.toArray();
        for(Proceso p : listosSuspendidosArray) {
            p.mostrarPCB();
        }

        System.out.println("\n## Cola de Bloqueados Suspendidos (" + colaBloqueadosSuspendidos.size() + " procesos)");
        Proceso[] bloqueadosSuspendidosArray = colaBloqueadosSuspendidos.toArray();
        for(Proceso p : bloqueadosSuspendidosArray) {
            p.mostrarPCB();
        }

        System.out.println("\n## Procesos Terminados (" + terminadosCount + " procesos)");
        for(int i = 0; i < terminadosCount; i++) {
             Proceso p = procesosTerminadosArray[i];
             System.out.printf("   [ID %d - %s | Pri:%d]%n", p.getId(), p.getNombre(), p.getPrioridad());
        }

        System.out.println("----------------------------------------");
    }

    // --- NUEVOS MÉTODOS PARA GUARDAR Y CARGAR ESTADO ---

    public boolean guardarEstado() {
        System.out.println("💾 Guardando estado de la simulación en " + ESTADO_FILE + "...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ESTADO_FILE, false))) {
            // Guardar estado global
            writer.write("TIEMPO," + tiempoSimulacion); writer.newLine();
            writer.write("PLANIFICADOR," + planificador.getClass().getName()); writer.newLine(); 
            writer.write("PROCESO_ACTUAL," + (procesoActual != null ? procesoActual.getId() : "IDLE")); writer.newLine();
             writer.write("QUANTUM_RESTANTE," + ciclosQuantum); writer.newLine(); 
             writer.write("NEXT_ID," + Proceso.getNextId()); writer.newLine(); 

            // Guardar colas y arrays
            guardarCola(writer, "LISTO", colaListos);
            guardarCola(writer, "LISTO_SUSP", colaListosSuspendidos);
            guardarCola(writer, "BLOQ_SUSP", colaBloqueadosSuspendidos);
            guardarMapaBloqueados(writer, "BLOQUEADO", mapaProcesosBloqueados);
            guardarTerminados(writer, "TERMINADO", procesosTerminadosArray, terminadosCount);

            System.out.println("💾 Estado guardado exitosamente.");
            return true;
        } catch (IOException e) {
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

    private void guardarTerminados(BufferedWriter writer, String prefix, Proceso[] array, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            writer.write(prefix + "," + array[i].toStringData());
            writer.newLine();
        }
    }


    public boolean cargarEstado() {
        System.out.println("🔄 Intentando cargar estado desde " + ESTADO_FILE + "...");
         // Usamos listas temporales porque no podemos modificar las colas mientras iteramos
         java.util.ArrayList<Proceso> tempListos = new java.util.ArrayList<>();
         java.util.ArrayList<Proceso> tempListosSusp = new java.util.ArrayList<>();
         java.util.ArrayList<Proceso> tempBloqueados = new java.util.ArrayList<>();
         java.util.ArrayList<Proceso> tempBloqSusp = new java.util.ArrayList<>();
         java.util.ArrayList<Proceso> tempTerminados = new java.util.ArrayList<>();
         int idProcesoActual = -1; // -1 indica IDLE
         String nombrePlanificador = null;
         int quantumRestanteCargado = 0;
         int nextIdCargado = 1; // Valor por defecto si no se encuentra

        try (BufferedReader reader = new BufferedReader(new FileReader(ESTADO_FILE))) {
            String line;
            int maxId = 0; // Para resetear Proceso.nextId

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // Ignorar líneas vacías

                String[] parts = line.split(",", 2); // Separar prefijo del resto
                if (parts.length < 2) continue; // Ignorar líneas mal formadas

                String prefix = parts[0];
                String data = parts[1];

                try {
                    switch (prefix) {
                        case "TIEMPO":
                            tiempoSimulacion = Long.parseLong(data);
                            break;
                        case "PLANIFICADOR":
                            nombrePlanificador = data;
                            break;
                        case "PROCESO_ACTUAL":
                            if (!data.equals("IDLE")) {
                                idProcesoActual = Integer.parseInt(data);
                            } else {
                                idProcesoActual = -1;
                            }
                            break;
                         case "QUANTUM_RESTANTE":
                             quantumRestanteCargado = Integer.parseInt(data);
                             break;
                         case "NEXT_ID":
                             nextIdCargado = Integer.parseInt(data);
                             break;
                        case "LISTO":
                        case "BLOQUEADO":
                        case "LISTO_SUSP":
                        case "BLOQ_SUSP":
                        case "TERMINADO":
                            Proceso p = Proceso.fromStringData(data);
                            if (p.getId() > maxId) maxId = p.getId(); // Actualizar maxId encontrado
                            switch (prefix) {
                                case "LISTO": tempListos.add(p); break;
                                case "BLOQUEADO": tempBloqueados.add(p); break;
                                case "LISTO_SUSP": tempListosSusp.add(p); break;
                                case "BLOQ_SUSP": tempBloqSusp.add(p); break;
                                case "TERMINADO": tempTerminados.add(p); break;
                            }
                            break;
                        default:
                            System.err.println("   Prefijo desconocido en archivo de estado: " + prefix);
                            break;
                    }
                } catch (IllegalArgumentException |ArrayIndexOutOfBoundsException | NullPointerException e) {
                     System.err.println("   Error parseando línea (" + prefix + "): " + line + " | Error: " + e.getMessage());
                     // Continuar con la siguiente línea si es posible
                }
            }

            // --- Reconstruir estado del simulador ---
            // Resetear estado actual
            limpiarEstadoSimulador();

             // Establecer el siguiente ID de proceso correctamente
             Proceso.resetNextId(Math.max(nextIdCargado, maxId + 1));


            // Cargar Planificador (Requiere instanciarlo por nombre)
            if (nombrePlanificador != null) {
                try {
                   
                    if (nombrePlanificador.contains("PlanificadorRoundRobin")) {
                        
                         int quantum = Main.DEFAULT_QUANTUM; 
                        
                        this.planificador = new PlanificadorRoundRobin(quantum);
                        this.ciclosQuantum = quantumRestanteCargado; 
                    } else {
                         
                        Class<?> clazz = Class.forName(nombrePlanificador);
                        this.planificador = (Planificador) clazz.getDeclaredConstructor().newInstance();
                    }
                     System.out.println("   Planificador cargado: " + this.planificador.getNombre());
                } catch (Exception e) {
                    System.err.println("❌ Error al instanciar el Planificador " + nombrePlanificador + ": " + e.getMessage() + ". Usando FCFS por defecto.");
                    this.planificador = new PlanificadorFCFS(); // Fallback
                }
            } else {
                System.err.println("❌ Nombre del planificador no encontrado en el archivo. Usando FCFS por defecto.");
                this.planificador = new PlanificadorFCFS(); // Fallback si no se encontró
            }


            // Cargar colas
            colaListos.rebuildFrom(tempListos.toArray(new Proceso[0]), tempListos.size());
            colaListosSuspendidos.rebuildFrom(tempListosSusp.toArray(new Proceso[0]), tempListosSusp.size());
            colaBloqueadosSuspendidos.rebuildFrom(tempBloqSusp.toArray(new Proceso[0]), tempBloqSusp.size());

             // Cargar mapa de bloqueados (no tienen hilo aún)
             for (Proceso p : tempBloqueados) {
                 mapaProcesosBloqueados.put(p.getId(), p);
             }

            // Cargar terminados
            if (tempTerminados.size() > procesosTerminadosArray.length) {
                 procesosTerminadosArray = new Proceso[tempTerminados.size()]; // Redimensionar si es necesario
            }
            terminadosCount = 0;
            for (Proceso p : tempTerminados) {
                procesosTerminadosArray[terminadosCount++] = p;
            }

             // Cargar proceso actual (buscarlo por ID en las listas cargadas)
             procesoActual = buscarProcesoPorId(idProcesoActual, tempListos, tempBloqueados, tempListosSusp, tempBloqSusp, tempTerminados);
             if (procesoActual != null) {
                  // Si el proceso actual estaba LISTO o EJECUCION (al guardar), ponerlo en EJECUCION ahora
                 // Si estaba BLOQUEADO, se manejará al reiniciar hilos
                 if(procesoActual.getEstado() == EstadoProceso.LISTO || procesoActual.getEstado() == EstadoProceso.EJECUCION){
                     procesoActual.setEstado(EstadoProceso.EJECUCION);
                 }
                 System.out.println("   Proceso actual cargado: ID " + procesoActual.getId());
             } else if (idProcesoActual != -1) {
                  System.err.println("   Error: Proceso actual con ID " + idProcesoActual + " no encontrado al cargar.");
             }


            return true; // Carga exitosa (o parcialmente exitosa)

        } catch (IOException e) {
            System.err.println("❌ Error al leer el archivo de estado " + ESTADO_FILE + ": " + e.getMessage());
            return false; 
        } catch (Exception e) {
             System.err.println("❌ Error inesperado durante la carga del estado: " + e.getMessage());
             e.printStackTrace(); 
             return false; 
        }
    }

   
     private Proceso buscarProcesoPorId(int id, java.util.List<Proceso>... listas) {
         if (id == -1) return null;
         for (java.util.List<Proceso> lista : listas) {
             for (Proceso p : lista) {
                 if (p.getId() == id) {
                     return p;
                 }
             }
         }
         return null; // No encontrado
     }


  
    private void limpiarEstadoSimulador() {
         // Detener hilos actuales si existen
         if (procesoThread != null && procesoThread.isAlive()) {
             procesoThread.interrupt();
         }
         for (Thread t : procesosEnExcepcion.values()) {
             if (t != null && t.isAlive()) {
                 t.interrupt();
             }
         }

        colaListos.rebuildFrom(new Proceso[0], 0);
        colaListosSuspendidos.rebuildFrom(new Proceso[0], 0);
        colaBloqueadosSuspendidos.rebuildFrom(new Proceso[0], 0);
        procesosEnExcepcion.clear();
        mapaProcesosBloqueados.clear();
        procesosTerminadosArray = new Proceso[INITIAL_CAPACITY];
        terminadosCount = 0;
        procesoActual = null;
        procesoThread = null;
        tiempoSimulacion = 0;
        ciclosQuantum = 0;
        
    }

     
     public void asignarSemaforoAProcesos() {
         asignarSemaforoEnCola(colaListos);
         asignarSemaforoEnCola(colaListosSuspendidos);
         asignarSemaforoEnCola(colaBloqueadosSuspendidos);
          for(Proceso p : mapaProcesosBloqueados.values()){
              p.setCpuSemaphore(this.cpuSemaphore);
          }
         if (procesoActual != null) {
             procesoActual.setCpuSemaphore(this.cpuSemaphore);
         }
         // Terminados no necesitan semáforo
     }
     private void asignarSemaforoEnCola(CustomQueue queue) {
         Proceso[] array = queue.toArray();
         for (Proceso p : array) {
             p.setCpuSemaphore(this.cpuSemaphore);
         }
     }

   
     public void reiniciarHilosPostCarga() {
          // Reiniciar hilos de procesos bloqueados (E/S)
         System.out.println("   Reiniciando hilos de E/S...");
         
          ConcurrentHashMap<Integer, Proceso> copiaMapaBloqueados = new ConcurrentHashMap<>(mapaProcesosBloqueados);
          for (Proceso p : copiaMapaBloqueados.values()) {
              if (p.getEstado() == EstadoProceso.BLOQUEADO) {
                  reanudarManejadorExcepcion(p);
                  System.out.println("      -> Hilo E/S para Proceso " + p.getId() + " reiniciado.");
              } else {
                
                   System.err.println("      -> Advertencia: Proceso " + p.getId() + " en mapa de bloqueados pero con estado " + p.getEstado());
              }
          }

         
         if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) {
              System.out.println("   Reiniciando hilo de CPU para Proceso " + procesoActual.getId() + "...");
              procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId() + "-Reanudado");
              procesoThread.start();
               System.out.println("      -> Hilo CPU para Proceso " + procesoActual.getId() + " reiniciado.");
          } else if (procesoActual != null) {
              
               System.out.println("   Proceso actual cargado (" + procesoActual.getId() + ") no estaba en EJECUCION (Estado: "+ procesoActual.getEstado()+"). CPU queda IDLE.");
               
               if(procesoActual.getEstado() != EstadoProceso.TERMINADO) {
                   
               }
               procesoActual = null; // Dejar CPU idle
          }
     }

    

} 