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


public class Simulador {
    private final ConfiguracionSimulacion config;
    private Planificador planificador;

    private final CustomQueue colaListos;
    private final CustomQueue colaListosSuspendidos;
    private final CustomQueue colaBloqueadosSuspendidos;

    private final ConcurrentHashMap<Integer, Thread> procesosEnExcepcion;
    private final ConcurrentHashMap<Integer, Proceso> mapaProcesosBloqueados; // Cambiado

    private Proceso[] procesosTerminadosArray; // Mantenido para compatibilidad potencial
    private int terminadosCount; // Mantenido para compatibilidad potencial
    private static final int INITIAL_CAPACITY = 10; // Mantenido

    public Proceso procesoActual; // Hecho público para acceso desde GUI
    private transient Thread procesoThread; // Mantenido

    private long tiempoSimulacion;
    private int ciclosQuantum; // Ciclos restantes del quantum actual
    private final transient Semaphore cpuSemaphore;

    public static final int UMBRAL_PROCESOS_MEMORIA = 5; // Hecho público
    public static final String ESTADO_FILE = "sim_estado.txt"; // Hecho público

    // Métricas
    private long tiempoInicioSimulacionReal;
    private long tiempoTotalCpuOcupado;
    private long tiempoInicioUsoCpuActual;
    private List<Proceso> listaProcesosCompletados; // Usar List para métricas


    public Simulador(ConfiguracionSimulacion config, Planificador planificadorInicial) {
        Logger.init(); // Logger se inicializa aquí
        Logger.log("Inicializando Simulador...");

        this.config = config;

        // Inicialización de colas y mapas
        this.colaListos = new CustomQueue();
        this.colaListosSuspendidos = new CustomQueue();
        this.colaBloqueadosSuspendidos = new CustomQueue();
        this.procesosEnExcepcion = new ConcurrentHashMap<>();
        this.mapaProcesosBloqueados = new ConcurrentHashMap<>(); // Mapa para bloqueados
        this.procesosTerminadosArray = new Proceso[INITIAL_CAPACITY]; // Mantenido
        this.terminadosCount = 0; // Mantenido

        // Estado inicial
        this.procesoActual = null;
        this.procesoThread = null;
        this.tiempoSimulacion = 0;
        this.ciclosQuantum = 0;
        this.cpuSemaphore = new Semaphore(1); // Semáforo para la CPU

        // Métricas
        this.tiempoInicioSimulacionReal = System.currentTimeMillis();
        this.tiempoTotalCpuOcupado = 0;
        this.tiempoInicioUsoCpuActual = 0;
        this.listaProcesosCompletados = new ArrayList<>(); // Lista para terminados

        // Carga de estado
        if (!cargarEstado()) {
            Logger.log("No se cargó estado previo. Iniciando simulación nueva.");
            this.planificador = planificadorInicial; // Usar el planificador inicial si no hay estado
        } else {
            Logger.log("Estado cargado desde " + ESTADO_FILE);
            // Asegurarse de que los objetos Proceso tengan el semáforo y los hilos reiniciados
            asignarSemaforoAProcesos();
            reiniciarHilosPostCarga();
            // Registrar inicio de uso de CPU si un proceso estaba ejecutándose
             if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) {
                tiempoInicioUsoCpuActual = System.currentTimeMillis(); // Inicia conteo CPU
             }
        }
         Logger.log("Simulador listo. Planificador: " + (this.planificador != null ? this.planificador.getNombre() : "Ninguno"));
    }

    // --- Getters para la GUI ---
    public Planificador getPlanificador() { return this.planificador; }
    public ConfiguracionSimulacion getConfig() { return this.config; }
    public CustomQueue getColaListos() { return colaListos; }
    public java.util.Map<Integer, Proceso> getMapaProcesosBloqueados() { return mapaProcesosBloqueados; } // Devuelve el Mapa
    public CustomQueue getColaListosSuspendidos() { return colaListosSuspendidos; }
    public CustomQueue getColaBloqueadosSuspendidos() { return colaBloqueadosSuspendidos; }
    public List<Proceso> getListaProcesosCompletados() { return this.listaProcesosCompletados; }
    public long getTiempoSimulacion() { return tiempoSimulacion; }
    public int getCiclosQuantum() { return ciclosQuantum; }

    public void setPlanificador(Planificador planificador) {
        Planificador anterior = this.planificador;
        this.planificador = planificador;
        Logger.log("Cambio de Planificador: "
                + (anterior != null ? anterior.getNombre() : "Ninguno") + " -> "
                + (planificador != null ? planificador.getNombre() : "Ninguno"));
        // Reordenar la cola de listos según el nuevo planificador (si aplica)
        reordenarColaListos(null); // Pasar null para reordenar todo
    }

    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore); // Asignar semáforo
        Logger.log("Solicitud para agregar Proceso " + proceso.getId() + " (" + proceso.getNombre() + ")");
        // Lógica de admisión (Planificador a Largo Plazo + Mediano Plazo inicial)
        if (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             colaListos.add(proceso);
             proceso.setEstado(EstadoProceso.LISTO); // Estado inicial en memoria
             Logger.log("   -> Proceso " + proceso.getId() + " admitido a LISTO.");
             reordenarColaListos(proceso); // Reordenar si es necesario
        } else {
            colaListosSuspendidos.add(proceso); // Va a suspendido si no hay memoria
            proceso.setEstado(EstadoProceso.SUSPENDIDO_LISTO);
            Logger.log("   -> Proceso " + proceso.getId() + " enviado a SUSPENDIDO_LISTO (Memoria llena).");
        }
        revisarYSuspenderSiNecesario(); // Asegurarse de no exceder umbral
    }

    // --- Gestión de Suspensión/Reanudación (Planificador a Mediano Plazo) ---

    private void revisarYSuspenderSiNecesario() {
         // Suspender si hay demasiados procesos en memoria (Listos + Bloqueados)
         while (colaListos.size() + mapaProcesosBloqueados.size() > UMBRAL_PROCESOS_MEMORIA) {
             Proceso aSuspender = null;
             EstadoProceso nuevoEstado = null;
             CustomQueue origenCola = null; // Para saber de dónde viene (Listo o Bloqueado)
             boolean origenMapa = false;

             // Prioridad de suspensión: Menos prioritario en Listo, luego menos prioritario en Bloqueado
             if (!colaListos.isEmpty()) {
                 aSuspender = buscarProcesoMenosPrioritario(colaListos);
                 origenCola = colaListos;
                 nuevoEstado = EstadoProceso.SUSPENDIDO_LISTO;
                 origenMapa = false;
             } else if (!mapaProcesosBloqueados.isEmpty()) {
                 aSuspender = buscarProcesoMenosPrioritarioEnMapa(mapaProcesosBloqueados);
                 origenCola = null; // Viene del mapa
                 nuevoEstado = EstadoProceso.SUSPENDIDO_BLOQUEADO;
                 origenMapa = true;
             }

             if (aSuspender != null) {
                 Logger.log("SUSPENSIÓN (Mediano Plazo): Proceso " + aSuspender.getId() + " (" + aSuspender.getNombre() + ") por memoria llena.");
                 if (!origenMapa) { // Vino de colaListos
                    colaListos.remove(aSuspender);
                    aSuspender.setEstado(nuevoEstado);
                    colaListosSuspendidos.add(aSuspender); // A cola suspendido-listo
                 } else { // Vino de mapaProcesosBloqueados
                    mapaProcesosBloqueados.remove(aSuspender.getId()); // Sacar del mapa activo
                    // Interrumpir hilo de E/S si estaba activo
                    Thread hiloExcepcion = procesosEnExcepcion.remove(aSuspender.getId());
                    if (hiloExcepcion != null && hiloExcepcion.isAlive()) {
                        hiloExcepcion.interrupt();
                        Logger.log("   -> Hilo E/S para " + aSuspender.getId() + " interrumpido por suspensión.");
                    }
                    aSuspender.setEstado(nuevoEstado);
                    colaBloqueadosSuspendidos.add(aSuspender); // A cola suspendido-bloqueado
                 }
             } else {
                 // Esto no debería pasar si la condición del while se cumple y hay procesos.
                 Logger.log("Advertencia: Se necesita suspender pero no se encontró candidato (Listo/Bloq vacíos?).");
                 break; // Salir del bucle para evitar loop infinito
             }
         }
    }

    private void revisarYReanudarSiNecesario() {
         // Reanudar si hay espacio en memoria y hay procesos suspendidos
         while (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             Proceso aReanudar = null;
             EstadoProceso estadoDestino = null;

             // Prioridad de reanudación: Más prioritario Suspendido-Listo, luego más prioritario Suspendido-Bloqueado
             if (!colaListosSuspendidos.isEmpty()) {
                 aReanudar = buscarProcesoMasPrioritario(colaListosSuspendidos);
                 if (aReanudar != null) {
                    colaListosSuspendidos.remove(aReanudar); // Sacar de suspendidos
                    estadoDestino = EstadoProceso.LISTO;
                    colaListos.add(aReanudar); // Añadir a listos
                 }
             }
             else if (!colaBloqueadosSuspendidos.isEmpty()) { // Si no hay listos-susp, buscar bloqueados-susp
                  aReanudar = buscarProcesoMasPrioritario(colaBloqueadosSuspendidos);
                  if (aReanudar != null) {
                      colaBloqueadosSuspendidos.remove(aReanudar); // Sacar de suspendidos
                      estadoDestino = EstadoProceso.BLOQUEADO;
                      mapaProcesosBloqueados.put(aReanudar.getId(), aReanudar); // Añadir a bloqueados activos
                      // Reiniciar su manejo de E/S (porque estaba suspendido)
                      reanudarManejadorExcepcion(aReanudar);
                  }
             }

             if (aReanudar != null) {
                 Logger.log("REANUDACIÓN (Mediano Plazo): Proceso " + aReanudar.getId() + " (" + aReanudar.getNombre() + ") reanudado a " + estadoDestino + ".");
                 aReanudar.setEstado(estadoDestino);
                 if (estadoDestino == EstadoProceso.LISTO) {
                    reordenarColaListos(aReanudar); // Reordenar si es necesario
                 }
             } else {
                 break; // No hay más procesos para reanudar o no hay candidatos
             }
         }
    }

    // --- Métodos auxiliares para buscar procesos por prioridad ---

    private Proceso buscarProcesoMenosPrioritario(CustomQueue queue) {
        if (queue.isEmpty()) return null;
        Proceso[] array = queue.toArray();
        Proceso peor = array[0];
        // Iterar para encontrar el de MAYOR valor de prioridad (menor prioridad)
        for (int i = 1; i < array.length; i++) {
             if (array[i] != null && array[i].getPrioridad() >= peor.getPrioridad()) { // >= para desempatar con el último
                 peor = array[i];
             }
        }
        return peor;
    }

     private Proceso buscarProcesoMenosPrioritarioEnMapa(ConcurrentHashMap<Integer, Proceso> map) {
        if (map.isEmpty()) return null;
        Proceso peor = null;
        // Iterar para encontrar el de MAYOR valor de prioridad
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
         // Iterar para encontrar el de MENOR valor de prioridad (mayor prioridad)
         for (int i = 1; i < array.length; i++) {
              if (array[i] != null && array[i].getPrioridad() < mejor.getPrioridad()) {
                  mejor = array[i];
              }
         }
         return mejor;
     }

    // --- Reiniciar manejo de E/S para procesos reanudados ---
    private void reanudarManejadorExcepcion(Proceso proceso) {
        // Asegurarse de que el estado sea BLOQUEADO antes de iniciar el handler
        if (proceso.getEstado() != EstadoProceso.BLOQUEADO) {
            proceso.setEstado(EstadoProceso.BLOQUEADO);
        }
        Logger.log("Reiniciando ManejadorExcepcion para Proceso " + proceso.getId() + " (E/S restante).");
        ManejadorExcepcion handler = new ManejadorExcepcion(proceso, colaListos, config.getDuracionCicloMs());
        Thread handlerThread = new Thread(handler, "Excepción-" + proceso.getId() + "-Reanudado");
        handlerThread.start();
        procesosEnExcepcion.put(proceso.getId(), handlerThread); // Registrar el nuevo hilo
    }


    // --- Reordenar cola de listos (para SJF, SRT, Prioridad) ---
    private void reordenarColaListos(Proceso nuevoProceso) {
        // No reordenar para FCFS o RR
        if (planificador instanceof PlanificadorFCFS || planificador instanceof PlanificadorRoundRobin) {
            return;
        }

        // Obtener todos los procesos listos
        Proceso[] lista = colaListos.toArray();
        int length = colaListos.size();
        if (length <= 1) return; // No necesita ordenar si hay 0 o 1

        // Ordenar usando Bubble Sort (simple, para pocos elementos está bien)
        boolean swapped;
        for (int i = 0; i < length - 1; i++) {
            swapped = false;
            for (int j = 0; j < length - 1 - i; j++) {
                boolean swapCondition = false;
                Proceso p1 = lista[j];
                Proceso p2 = lista[j+1];

                // Condición de swap depende del planificador
                if (planificador instanceof PlanificadorSJF || planificador instanceof PlanificadorSRT) {
                    // Ordenar por instrucciones restantes (ascendente)
                    if (p1.getInstruccionesRestantes() > p2.getInstruccionesRestantes()) {
                        swapCondition = true;
                    }
                } else { // Asume Prioridad (No Expropiativa o Expropiativa)
                    // Ordenar por prioridad (ascendente, menor valor es más prioritario)
                    if (p1.getPrioridad() > p2.getPrioridad()) {
                        swapCondition = true;
                    }
                }

                if (swapCondition) {
                    // Swap
                    Proceso temp = lista[j];
                    lista[j] = lista[j+1];
                    lista[j+1] = temp;
                    swapped = true;
                }
            }
             // Si no hubo swaps en una pasada, la lista está ordenada
             if (!swapped) break;
        }

        // Reconstruir la cola con la lista ordenada
        colaListos.rebuildFrom(lista, length);
        // Logger.log("Cola de listos reordenada según " + planificador.getNombre()); // Opcional: log detallado
    }

    // --- Comprobar si quedan procesos activos ---
    public boolean quedanProcesos() {
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty()
                || !colaListosSuspendidos.isEmpty() || !colaBloqueadosSuspendidos.isEmpty();
                // No incluir mapaProcesosBloqueados aquí, ya que procesosEnExcepcion cubre eso
    }

    // --- Ciclo principal de simulación ---
    public void ejecutarCicloSimulacion() {
        long inicioCicloReal = System.currentTimeMillis(); // Tiempo real al inicio del ciclo
        tiempoSimulacion += config.getDuracionCicloMs(); // Avanzar tiempo simulado
        Logger.log("Inicio Ciclo " + (tiempoSimulacion / config.getDuracionCicloMs()) + " @ Tiempo Simulado " + tiempoSimulacion + "ms");

        // Actualizar tiempo total de CPU ocupado si había un proceso ejecutándose
        if (procesoActual != null && tiempoInicioUsoCpuActual > 0) {
            tiempoTotalCpuOcupado += (inicioCicloReal - tiempoInicioUsoCpuActual);
            tiempoInicioUsoCpuActual = 0; // Resetear hasta que se asigne de nuevo
        }

        // 1. Manejar E/S (hilos que terminan y mueven procesos a LISTO)
        manejarExcepciones(); // Limpia hilos terminados y mueve procesos

        // 2. Comprobar expropiación (si aplica)
        comprobarExpropiacion();

        // 3. Planificador a Mediano Plazo (Suspender/Reanudar por memoria)
        revisarYSuspenderSiNecesario(); // Primero suspender si es necesario
        revisarYReanudarSiNecesario(); // Luego reanudar si hay espacio

        // 4. Planificador a Corto Plazo (Seleccionar proceso si CPU está idle)
        if (procesoActual == null) {
             Logger.log("CPU Idle. Intentando planificar...");
            planificarSiguiente();
        } else {
             // Si ya hay un proceso, registrar inicio de uso de CPU para este ciclo
             tiempoInicioUsoCpuActual = inicioCicloReal;
             // Manejar quantum si es RR
            manejarQuantum(); // Decrementa quantum y expropia si llega a 0
        }

        // 5. Chequear si el proceso actual terminó o entró en E/S DESPUÉS de su ejecución
        chequearEstadoEjecucion(); // Mueve a TERMINADO o BLOQUEADO/SUSPENDIDO_BLOQUEADO

        // 6. Mostrar estado actual (opcional, para debug en consola)
        mostrarEstado(); // Muestra colas, CPU, etc.

        // --- Sincronización con tiempo real ---
        long finCicloLogicoReal = System.currentTimeMillis();
        long duracionLogica = finCicloLogicoReal - inicioCicloReal; // Tiempo real gastado en lógica
        long tiempoDormir = config.getDuracionCicloMs() - duracionLogica; // Tiempo a esperar

        if (tiempoDormir > 0) {
            try {
                Thread.sleep(tiempoDormir); // Esperar para simular duración del ciclo
            } catch (InterruptedException e) {
                Logger.log("WARN: Sleep del ciclo principal interrumpido.");
                Thread.currentThread().interrupt(); // Restablecer flag de interrupción
            }
        } else {
            // Logger.log("WARN: Ciclo " + (tiempoSimulacion / config.getDuracionCicloMs()) + " tardó más que la duración configurada (" + duracionLogica + "ms).");
        }
         // Registrar fin de uso de CPU si un proceso ejecutó hasta el final del ciclo real
         if (procesoActual != null && tiempoInicioUsoCpuActual > 0) {
            long tiempoRealFinCiclo = System.currentTimeMillis();
             tiempoTotalCpuOcupado += (tiempoRealFinCiclo - inicioCicloReal); // Sumar tiempo real de uso
             tiempoInicioUsoCpuActual = tiempoRealFinCiclo; // Preparar para el próximo ciclo
         } else {
             tiempoInicioUsoCpuActual = 0; // CPU estuvo idle o se interrumpió
         }

    } // Fin ejecutarCicloSimulacion

    // --- Manejo de Hilos de E/S ---
    private void manejarExcepciones() {
        // Usar removeIf para limpiar el mapa de hilos terminados
        procesosEnExcepcion.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) { // Si el hilo de E/S terminó
                Proceso p = mapaProcesosBloqueados.remove(entry.getKey()); // Intentar sacar de bloqueados
                if (p != null) {
                    // El ManejadorExcepcion ya debería haber movido el proceso a LISTO
                    Logger.log("Hilo E/S para Proceso " + p.getId() + " terminado. Proceso debería estar en LISTO.");
                    // Ya no es necesario agregarlo a colaListos aquí, el handler lo hace.
                    // Solo revisamos si se puede reanudar algo MÁS por el espacio liberado.
                    revisarYReanudarSiNecesario(); // Revisar si se puede traer algo de suspendido
                } else {
                     // Esto puede pasar si fue suspendido mientras estaba bloqueado
                     Logger.log("WARN: Hilo E/S para ID " + entry.getKey() + " terminado, pero proceso no encontrado en mapa Bloqueados (¿Suspendido?).");
                }
                return true; // Eliminar la entrada del mapa de hilos
            }
            return false; // Mantener la entrada si el hilo sigue vivo
        });
    }

    // --- Chequeo de Estado Post-Ejecución ---
    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return; // No hay nada que chequear

        // Chequear si terminó
        if (procesoActual.haTerminado()) {
            Proceso terminado = procesoActual; // Guardar referencia

            // Cambiar estado y registrar métricas
            terminado.setEstado(EstadoProceso.TERMINADO); // setEstado actualiza tiempos
            Logger.log("PROCESO TERMINADO: ID=" + terminado.getId() + ", Nombre=" + terminado.getNombre() + ", Tiempo Retorno=" + terminado.getTiempoRetorno() + "ms");

            // Añadir a la lista de completados para métricas finales
            listaProcesosCompletados.add(terminado);

            // Código de array mantenido por si acaso, pero lista es mejor
            if (terminadosCount >= procesosTerminadosArray.length) {
                Proceso[] newArray = new Proceso[procesosTerminadosArray.length * 2];
                 for (int i = 0; i < terminadosCount; i++) newArray[i] = procesosTerminadosArray[i];
                procesosTerminadosArray = newArray;
            }
            procesosTerminadosArray[terminadosCount++] = terminado; // Mantenido

            // Liberar CPU
            if (procesoThread != null) procesoThread.interrupt(); // Interrumpir hilo si sigue vivo
            procesoActual = null;
            procesoThread = null;

            // Revisar si se puede reanudar algo ahora que terminó uno
            revisarYReanudarSiNecesario();

        }
        // Chequear si entró en E/S (estado cambiado por el Proceso en run())
        else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
             Proceso bloqueado = procesoActual; // Guardar referencia
             // Liberar CPU
             if (procesoThread != null) procesoThread.interrupt();
             procesoActual = null;
             procesoThread = null;

             Logger.log("E/S Requerida: Proceso " + bloqueado.getId() + " (" + bloqueado.getNombre() + ")");

             // Decidir si va a BLOQUEADO activo o SUSPENDIDO_BLOQUEADO
             if (colaListos.size() + mapaProcesosBloqueados.size() + 1 <= UMBRAL_PROCESOS_MEMORIA) { // +1 porque aún no está en el mapa
                 bloqueado.setEstado(EstadoProceso.BLOQUEADO); // Confirmar estado
                 mapaProcesosBloqueados.put(bloqueado.getId(), bloqueado); // Añadir a mapa de bloqueados activos
                 // Crear y lanzar hilo manejador de E/S
                 ManejadorExcepcion handler = new ManejadorExcepcion(bloqueado, colaListos, config.getDuracionCicloMs());
                 Thread handlerThread = new Thread(handler, "Excepción-" + bloqueado.getId());
                 handlerThread.start();
                 procesosEnExcepcion.put(bloqueado.getId(), handlerThread); // Registrar hilo
                 Logger.log("   -> Proceso " + bloqueado.getId() + " pasa a BLOQUEADO.");
             } else {
                 // No hay espacio en memoria activa, va a suspendido
                 bloqueado.setEstado(EstadoProceso.SUSPENDIDO_BLOQUEADO);
                 colaBloqueadosSuspendidos.add(bloqueado);
                 Logger.log("   -> Proceso " + bloqueado.getId() + " pasa a SUSPENDIDO_BLOQUEADO (Memoria llena).");
             }
             // Revisar si se puede/debe reanudar/suspender ALGO MÁS tras este cambio
             revisarYReanudarSiNecesario(); // Puede haber espacio si vino de Listo y fue a Suspendido
             revisarYSuspenderSiNecesario(); // Puede que necesite suspender si fue a Bloqueado y llenó memoria
        }
    } // Fin chequearEstadoEjecucion

    // --- Comprobar Expropiación ---
    private void comprobarExpropiacion() {
        // Solo aplica si hay un proceso en CPU y la cola de listos no está vacía
        if (procesoActual == null || colaListos.isEmpty()) return;

        boolean expropiar = false;
        Proceso candidato = colaListos.peek(); // Ver el siguiente en la cola de listos
        if (candidato == null) return; // Cola vacía después de todo

        // Lógica de expropiación según el planificador
        if (planificador instanceof PlanificadorSRT) {
            // Expropiar si el candidato tiene menos tiempo restante que el actual
            if (candidato.getInstruccionesRestantes() < procesoActual.getInstruccionesRestantes()) {
                expropiar = true;
            }
        } else if (planificador instanceof PlanificadorPrioridadExpropiativa) {
            // Expropiar si el candidato tiene mayor prioridad (menor número) que el actual
            if (candidato.getPrioridad() < procesoActual.getPrioridad()) {
                expropiar = true;
            }
        }
        // RR maneja su expropiación en manejarQuantum
        // FCFS, SJF, Prioridad No Expropiativa no expropian

        if (expropiar) {
            Logger.log("EXPROPIACIÓN: Proceso " + candidato.getId() + " (" + candidato.getNombre() + ") expropia a " + procesoActual.getId() + " (" + procesoActual.getNombre() + ").");
            Proceso expropiado = procesoActual; // Guardar referencia

            // Interrumpir hilo del proceso actual y liberar CPU
            if (procesoThread != null) procesoThread.interrupt();
            procesoActual = null;
            procesoThread = null;

            // Devolver proceso expropiado a la cola de Listos
            expropiado.setEstado(EstadoProceso.LISTO); // Cambiar estado
            colaListos.add(expropiado); // Añadir al final (o reordenar)
            reordenarColaListos(expropiado); // Reordenar la cola después de añadirlo
            // No se llama a planificarSiguiente aquí, se hará en el flujo normal del ciclo
        }
    } // Fin comprobarExpropiacion

    // --- Manejar Quantum para Round Robin ---
    private void manejarQuantum() {
        if (procesoActual != null && planificador instanceof PlanificadorRoundRobin rr) {
            ciclosQuantum--; // Decrementar quantum restante
            // Logger.log("   -> Quantum para " + procesoActual.getId() + " ahora es " + ciclosQuantum); // Log detallado
            if (ciclosQuantum <= 0) {
                 // Quantum expirado, expropiar
                 Logger.log("QUANTUM FIN: Proceso " + procesoActual.getId() + " (" + procesoActual.getNombre() + ") vuelve a LISTO.");
                 Proceso expropiado = procesoActual; // Guardar referencia

                 // Interrumpir hilo y liberar CPU
                 if (procesoThread != null) procesoThread.interrupt();
                 procesoActual = null;
                 procesoThread = null;

                 // Devolver a cola de Listos
                 expropiado.setEstado(EstadoProceso.LISTO);
                 colaListos.add(expropiado); // RR añade al final

                 // Revisar suspensión/reanudación después del cambio
                 revisarYSuspenderSiNecesario();
                 revisarYReanudarSiNecesario();
                 // No se llama a planificarSiguiente aquí, se hará en el flujo normal
            }
        }
    } // Fin manejarQuantum

    // --- Planificar Siguiente Proceso (si CPU idle) ---
    private void planificarSiguiente() {
        // Solo planificar si CPU está idle Y hay procesos listos
        if (procesoActual == null && !colaListos.isEmpty()) {
            Proceso siguienteProceso = planificador.seleccionarSiguiente(colaListos); // El planificador saca de la cola

            if (siguienteProceso != null) {
                procesoActual = siguienteProceso; // Asignar a CPU
                Logger.log("CONTEXT SWITCH: Proceso " + procesoActual.getId() + " (" + procesoActual.getNombre() + ") seleccionado para ejecución.");
                procesoActual.setEstado(EstadoProceso.EJECUCION); // Cambiar estado

                // Asignar quantum si es RR
                if (planificador instanceof PlanificadorRoundRobin rr) {
                     ciclosQuantum = rr.getQuantum(); // Obtener quantum del planificador
                     Logger.log("   -> Quantum asignado: " + ciclosQuantum);
                } else {
                     ciclosQuantum = 0; // No aplica quantum
                }

                // Registrar inicio de uso de CPU para métricas
                tiempoInicioUsoCpuActual = System.currentTimeMillis();

                // Crear y lanzar el hilo para el proceso
                procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId());
                procesoThread.start();
            } else {
                 // Esto podría pasar si la cola se vació entre isEmpty() y seleccionarSiguiente() (concurrencia)
                 Logger.log("Planificador no seleccionó proceso (¿cola vacía después de peek?). CPU Idle.");
            }
        } else if (procesoActual == null && colaListos.isEmpty()) {
             // Logger.log("CPU Idle y Cola Listos vacía."); // Log opcional
        }
    } // Fin planificarSiguiente

    // --- Mostrar Estado en Consola (para Debug) ---
    public void mostrarEstado() {
        // Esta función es principalmente para debug en consola, la GUI usa getters.
        System.out.println("\n--- VISUALIZACIÓN DE ESTADO DEL KERNEL ---");
        String planificadorNombre = (planificador != null) ? planificador.getNombre() : "N/A";
        System.out.printf("   [SO] Tiempo: %dms | Política: %s | Ciclo: %dms%n",
                             tiempoSimulacion, planificadorNombre, config.getDuracionCicloMs());
        System.out.printf("   [Memoria] Procesos Activos (Listo+Bloq): %d/%d%n",
                             colaListos.size() + mapaProcesosBloqueados.size(), UMBRAL_PROCESOS_MEMORIA);

        System.out.println("\n## CPU (Proceso en Ejecución)");
        if (procesoActual != null) {
            procesoActual.mostrarPCB(); // Método del Proceso para mostrar su info
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
             // Iterar sobre los valores del mapa para mostrar los PCB
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

        // Mostrar terminados de forma concisa
        System.out.println("\n## Procesos Terminados (" + listaProcesosCompletados.size() + " procesos)");
        for(Proceso p : listaProcesosCompletados) {
             // Mostrar info relevante de terminados
             System.out.printf("   [ID %d - %s | Pri:%d | T.Retorno:%dms | T.Espera:%dms | T.Bloq:%dms]%n",
                   p.getId(), p.getNombre(), p.getPrioridad(),
                   p.getTiempoRetorno(), p.getTiempoTotalEsperandoListo(), p.getTiempoTotalBloqueado());
        }
         if(listaProcesosCompletados.isEmpty()) System.out.println("   (Ninguno)");

        System.out.println("----------------------------------------");
    } // Fin mostrarEstado


    // --- Guardar Estado ---
    public boolean guardarEstado() {
        Logger.log("GUARDANDO ESTADO...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ESTADO_FILE, false))) { // false = sobrescribir
            // Guardar estado general
            writer.write("TIEMPO," + tiempoSimulacion); writer.newLine();
            String planificadorName = (planificador != null) ? planificador.getClass().getName() : "null"; // Guardar nombre de clase
            writer.write("PLANIFICADOR," + planificadorName); writer.newLine();
            writer.write("PROCESO_ACTUAL," + (procesoActual != null ? procesoActual.getId() : "IDLE")); writer.newLine();
            writer.write("QUANTUM_RESTANTE," + ciclosQuantum); writer.newLine();
            writer.write("NEXT_ID," + Proceso.getNextId()); writer.newLine(); // Guardar el próximo ID a usar

            // Guardar procesos en cada cola/mapa
            guardarCola(writer, "LISTO", colaListos);
            guardarCola(writer, "LISTO_SUSP", colaListosSuspendidos);
            guardarCola(writer, "BLOQ_SUSP", colaBloqueadosSuspendidos);
            guardarMapaBloqueados(writer, "BLOQUEADO", mapaProcesosBloqueados); // Guardar mapa
            guardarListaTerminados(writer, "TERMINADO", listaProcesosCompletados); // Guardar lista terminados


            Logger.log("Estado guardado exitosamente.");
            return true;
        } catch (IOException e) {
            Logger.log("ERROR al guardar estado: " + e.getMessage());
            System.err.println("❌ Error al guardar el estado: " + e.getMessage());
            return false;
        }
    }

    // Métodos auxiliares para guardar
    private void guardarCola(BufferedWriter writer, String prefix, CustomQueue queue) throws IOException {
        Proceso[] array = queue.toArray();
        for (Proceso p : array) {
            if (p != null) { // Chequeo extra
                writer.write(prefix + "," + p.toStringData()); // Usa método del Proceso
                writer.newLine();
            }
        }
    }
     private void guardarMapaBloqueados(BufferedWriter writer, String prefix, ConcurrentHashMap<Integer, Proceso> map) throws IOException {
        for (Proceso p : map.values()) {
             if (p != null) {
                 writer.write(prefix + "," + p.toStringData());
                 writer.newLine();
             }
        }
     }
    private void guardarListaTerminados(BufferedWriter writer, String prefix, List<Proceso> lista) throws IOException {
        for (Proceso p : lista) {
            if (p != null) {
                writer.write(prefix + "," + p.toStringData());
                writer.newLine();
            }
        }
    }

    // --- Cargar Estado ---
    public boolean cargarEstado() {
        Logger.log("CARGANDO ESTADO desde " + ESTADO_FILE + "...");
         // Usar listas temporales para cargar los procesos
         List<Proceso> tempListos = new ArrayList<>();
         List<Proceso> tempListosSusp = new ArrayList<>();
         List<Proceso> tempBloqueados = new ArrayList<>();
         List<Proceso> tempBloqSusp = new ArrayList<>();
         List<Proceso> tempTerminados = new ArrayList<>();
         int idProcesoActual = -1; // ID del proceso que estaba en CPU
         String nombrePlanificador = null;
         int quantumRestanteCargado = 0;
         int nextIdCargado = 1; // Próximo ID a usar

        try (BufferedReader reader = new BufferedReader(new FileReader(ESTADO_FILE))) {
            String line;
            int maxId = 0; // Para resetear Proceso.nextId correctamente

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", 2); // Dividir solo en el primer delimitador
                if (parts.length < 2) continue; // Ignorar líneas mal formateadas
                String prefix = parts[0].trim();
                String data = parts[1].trim();

                try {
                     switch (prefix) {
                         // Cargar estado general
                         case "TIEMPO": tiempoSimulacion = Long.parseLong(data); break;
                         case "PLANIFICADOR": nombrePlanificador = data.equals("null") ? null : data; break;
                         case "PROCESO_ACTUAL": idProcesoActual = data.equals("IDLE") ? -1 : Integer.parseInt(data); break;
                         case "QUANTUM_RESTANTE": quantumRestanteCargado = Integer.parseInt(data); break;
                         case "NEXT_ID": nextIdCargado = Integer.parseInt(data); break;
                         // Cargar procesos según su estado guardado
                         case "LISTO": case "BLOQUEADO": case "LISTO_SUSP":
                         case "BLOQ_SUSP": case "TERMINADO":
                             Proceso p = Proceso.fromStringData(data); // Usar método estático del Proceso
                             if (p.getId() > maxId) maxId = p.getId(); // Rastrear el ID más alto
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
                } catch (Exception e) { // Capturar errores al parsear línea
                     Logger.log("ERROR parseando línea (" + prefix + "): " + line + " | Error: " + e.getMessage());
                     // Considerar si continuar o abortar la carga
                }
            }

            // Limpiar estado actual antes de cargar el nuevo
            limpiarEstadoSimulador();

            // Resetear ID estático de Proceso
            Proceso.resetNextId(Math.max(nextIdCargado, maxId + 1)); // Asegura IDs únicos

             // Instanciar el planificador cargado
             if (nombrePlanificador != null) {
                 try {
                     // Caso especial para RR que necesita quantum
                     if (nombrePlanificador.contains("PlanificadorRoundRobin")) {
                         // Asumir quantum por defecto si no se guardó explícitamente (se podría añadir al save/load)
                         this.planificador = new PlanificadorRoundRobin(Main.DEFAULT_QUANTUM);
                         this.ciclosQuantum = quantumRestanteCargado; // Restaurar quantum restante
                     } else {
                         // Usar reflexión para crear otros planificadores
                         Class<?> clazz = Class.forName(nombrePlanificador);
                         this.planificador = (Planificador) clazz.getDeclaredConstructor().newInstance();
                     }
                     Logger.log("   Planificador cargado: " + this.planificador.getNombre());
                 } catch (Exception e) {
                     Logger.log("ERROR al instanciar Planificador " + nombrePlanificador + ": " + e.getMessage() + ". Usando FCFS.");
                     this.planificador = new PlanificadorFCFS(); // Planificador por defecto si falla
                 }
             } else {
                  Logger.log("WARN: Planificador no encontrado en estado. Usando FCFS.");
                 this.planificador = new PlanificadorFCFS();
             }


             // Reconstruir colas y mapas
             // Usar rebuildFrom para eficiencia
            colaListos.rebuildFrom(tempListos.toArray(new Proceso[0]), tempListos.size());
            colaListosSuspendidos.rebuildFrom(tempListosSusp.toArray(new Proceso[0]), tempListosSusp.size());
            colaBloqueadosSuspendidos.rebuildFrom(tempBloqSusp.toArray(new Proceso[0]), tempBloqSusp.size());
            // Llenar mapa de bloqueados
            for (Proceso p : tempBloqueados) mapaProcesosBloqueados.put(p.getId(), p);

             // Llenar lista de terminados
             // Usar la lista directamente
            listaProcesosCompletados.addAll(tempTerminados);
             // Código de array mantenido
             terminadosCount = 0;
             if (listaProcesosCompletados.size() > procesosTerminadosArray.length) {
                 procesosTerminadosArray = new Proceso[listaProcesosCompletados.size()];
             }
             for(Proceso p : listaProcesosCompletados) {
                 if (terminadosCount < procesosTerminadosArray.length) {
                     procesosTerminadosArray[terminadosCount++] = p;
                 }
             }


             // Restaurar proceso actual en CPU si había uno
              procesoActual = buscarProcesoPorId(idProcesoActual, tempListos, tempBloqueados, tempListosSusp, tempBloqSusp, tempTerminados); // Buscar en las listas temporales
              if (procesoActual != null) {
                   // Asegurarse de que el estado sea EJECUCION y sacarlo de su cola original
                   if(procesoActual.getEstado() == EstadoProceso.LISTO || procesoActual.getEstado() == EstadoProceso.EJECUCION){
                       procesoActual.setEstado(EstadoProceso.EJECUCION); // Poner en ejecución
                       // Sacarlo de la cola de listos si estaba ahí
                       colaListos.remove(procesoActual);
                   } else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
                       // Si estaba bloqueado, debería estar en el mapa, no necesita acción extra aquí
                       // Su hilo se reiniciará en reiniciarHilosPostCarga
                   } else {
                       // Si estaba suspendido o terminado, no debería estar en CPU
                       Logger.log("WARN: Proceso actual cargado (" + procesoActual.getId() + ") estaba en estado " + procesoActual.getEstado() + ". CPU queda IDLE.");
                       procesoActual = null; // Dejar CPU idle
                   }
                  Logger.log("   Proceso actual cargado: ID " + (procesoActual != null ? procesoActual.getId() : "IDLE"));
              } else if (idProcesoActual != -1) {
                   // ID guardado no corresponde a ningún proceso cargado
                   Logger.log("ERROR: Proceso actual ID " + idProcesoActual + " no encontrado en las listas cargadas.");
              }

            Logger.log("Carga de estado completada.");
            return true;

        } catch (IOException e) {
            Logger.log("ERROR al leer archivo de estado " + ESTADO_FILE + ": " + e.getMessage());
            // System.err.println("❌ Error al leer archivo de estado: " + e.getMessage());
            return false;
        } catch (Exception e) { // Captura genérica para otros errores de carga
             Logger.log("ERROR inesperado durante carga: " + e.getMessage());
             e.printStackTrace();
             return false;
        }
    }

    // Método auxiliar para buscar proceso por ID en las listas temporales durante la carga
    private Proceso buscarProcesoPorId(int id, List<Proceso>... listas) {
        if (id == -1) return null;
        for (List<Proceso> lista : listas) {
             for (Proceso p : lista) {
                 if (p.getId() == id) {
                     return p;
                 }
             }
        }
        // También buscar en el mapa de bloqueados que se está reconstruyendo
         if (mapaProcesosBloqueados.containsKey(id)) { // Chequea el mapa que se está llenando
             return mapaProcesosBloqueados.get(id);
         }
        return null; // No encontrado
     }

    // Limpiar estado antes de cargar
    private void limpiarEstadoSimulador() {
        // Detener hilos activos
        if (procesoThread != null && procesoThread.isAlive()) procesoThread.interrupt();
        for (Thread t : procesosEnExcepcion.values()) if (t != null && t.isAlive()) t.interrupt();

        // Limpiar colas y mapas
        colaListos.rebuildFrom(new Proceso[0], 0); // Limpia la cola
        colaListosSuspendidos.rebuildFrom(new Proceso[0], 0);
        colaBloqueadosSuspendidos.rebuildFrom(new Proceso[0], 0);
        procesosEnExcepcion.clear();
        mapaProcesosBloqueados.clear();
        // Limpiar listas/arrays de terminados
        procesosTerminadosArray = new Proceso[INITIAL_CAPACITY];
        terminadosCount = 0;
        listaProcesosCompletados.clear();
        // Resetear estado general
        procesoActual = null;
        procesoThread = null;
        tiempoSimulacion = 0;
        ciclosQuantum = 0;
        // Resetear métricas
        tiempoTotalCpuOcupado = 0;
        tiempoInicioUsoCpuActual = 0;
        tiempoInicioSimulacionReal = System.currentTimeMillis(); // Reiniciar tiempo real
     }

     // Asignar semáforo a todos los procesos después de cargar
    public void asignarSemaforoAProcesos() {
         asignarSemaforoEnCola(colaListos);
         asignarSemaforoEnCola(colaListosSuspendidos);
         asignarSemaforoEnCola(colaBloqueadosSuspendidos);
         for(Proceso p : mapaProcesosBloqueados.values()) p.setCpuSemaphore(this.cpuSemaphore);
         // Asignar al proceso actual si existe
        if (procesoActual != null) procesoActual.setCpuSemaphore(this.cpuSemaphore);
        // No asignar a terminados
     }

     // Auxiliar para asignar semáforo en una cola
     private void asignarSemaforoEnCola(CustomQueue queue) {
         Proceso[] array = queue.toArray();
         for (Proceso p : array) {
             if (p != null) p.setCpuSemaphore(this.cpuSemaphore);
         }
     }

     // Reiniciar hilos necesarios después de cargar estado
    public void reiniciarHilosPostCarga() {
         Logger.log("Reiniciando hilos post-carga...");
         // Reiniciar hilos de E/S para procesos bloqueados
         // Crear copia para evitar ConcurrentModificationException si reanudarManejadorExcepcion modifica el mapa
         ConcurrentHashMap<Integer, Proceso> copiaMapaBloqueados = new ConcurrentHashMap<>(mapaProcesosBloqueados);
         for (Proceso p : copiaMapaBloqueados.values()) {
             if (p.getEstado() == EstadoProceso.BLOQUEADO) {
                 reanudarManejadorExcepcion(p); // Reinicia el hilo de E/S
             }
         }
         // Reiniciar hilo del proceso en CPU si corresponde
        if (procesoActual != null && procesoActual.getEstado() == EstadoProceso.EJECUCION) {
             Logger.log("   -> Reiniciando hilo CPU para Proceso " + procesoActual.getId());
             tiempoInicioUsoCpuActual = System.currentTimeMillis(); // Iniciar conteo CPU
             procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId() + "-Reanudado");
             procesoThread.start();
         }
     }


    // --- Cálculo y Muestra de Métricas ---
    public void calcularYMostrarMetricas() {
        if (listaProcesosCompletados.isEmpty()) {
            System.out.println("\n--- MÉTRICAS DE RENDIMIENTO ---");
            System.out.println("   No hay procesos completados para calcular métricas.");
            return;
        }

        // Calcular tiempo real total si no se había hecho
        long tiempoTotalSimulacionReal = System.currentTimeMillis() - tiempoInicioSimulacionReal;
        if (tiempoTotalSimulacionReal <= 0) tiempoTotalSimulacionReal = 1; // Evitar división por cero

        // 1. Throughput (Procesos completados por segundo real)
        double tiempoTotalSegundos = tiempoTotalSimulacionReal / 1000.0;
        double throughput = (tiempoTotalSegundos > 0) ? listaProcesosCompletados.size() / tiempoTotalSegundos : 0;

        // 2. Utilización de CPU (Porcentaje de tiempo real que CPU estuvo ocupado)
        // Asegurarse de que el tiempo de uso actual se contabilice si la simulación se detuvo
        if (procesoActual != null && tiempoInicioUsoCpuActual > 0) {
             tiempoTotalCpuOcupado += (System.currentTimeMillis() - tiempoInicioUsoCpuActual);
             tiempoInicioUsoCpuActual = 0; // Detener conteo
        }
        double utilizacionCpu = (double) tiempoTotalCpuOcupado * 100.0 / tiempoTotalSimulacionReal;

        // 3. Tiempos Promedio (calculados sobre los procesos COMPLETADOS)
        long sumaTiemposRespuesta = 0;
        long sumaTiemposRetorno = 0;
        long sumaTiemposEspera = 0;
        int countParaPromedio = 0;

        for (Proceso p : listaProcesosCompletados) {
            long tRetorno = p.getTiempoRetorno();
            if (tRetorno >= 0) { // Asegurarse de que el proceso realmente terminó
                 // Calcular/Obtener tiempos del proceso
                 long tRespuesta = p.getTiempoRespuesta(); // Tiempo hasta la primera respuesta
                 long tEspera = p.getTiempoTotalEsperandoListo(); // Tiempo en Listo + SuspendidoListo

                 sumaTiemposRetorno += tRetorno;
                 sumaTiemposRespuesta += tRespuesta;
                 sumaTiemposEspera += tEspera;
                 countParaPromedio++;
            }
        }

        double tiempoRespuestaPromedio = (countParaPromedio > 0) ? (double) sumaTiemposRespuesta / countParaPromedio : 0;
        double tiempoRetornoPromedio = (countParaPromedio > 0) ? (double) sumaTiemposRetorno / countParaPromedio : 0;
        double tiempoEsperaPromedio = (countParaPromedio > 0) ? (double) sumaTiemposEspera / countParaPromedio : 0;


        // Mostrar métricas
        System.out.println("\n--- MÉTRICAS DE RENDIMIENTO ---");
        System.out.printf("   Tiempo Total Simulación (Real): %.3f s%n", tiempoTotalSegundos);
        System.out.printf("   Procesos Completados: %d%n", listaProcesosCompletados.size());
        System.out.printf("   Throughput: %.3f procesos/s%n", throughput);
        System.out.printf("   Tiempo Total CPU Ocupado: %d ms%n", tiempoTotalCpuOcupado);
        System.out.printf("   Utilización de CPU: %.2f%%%n", utilizacionCpu);
        System.out.printf("   Tiempo de Retorno Promedio: %.2f ms%n", tiempoRetornoPromedio);
        System.out.printf("   Tiempo de Respuesta Promedio (aprox): %.2f ms%n", tiempoRespuestaPromedio); // Asumiendo que getTiempoRespuesta es correcto
        System.out.printf("   Tiempo de Espera Promedio (en Listo/SuspListo): %.2f ms%n", tiempoEsperaPromedio);
        System.out.println("---------------------------------");

        // Loggear métricas
         Logger.log(String.format("METRICAS FINALES: Procesos=%d, Throughput=%.3f p/s, CPU Util=%.2f%%, T.RetornoAvg=%.2fms, T.RespAvg=%.2fms, T.EsperaAvg=%.2fms",
                 listaProcesosCompletados.size(), throughput, utilizacionCpu, tiempoRetornoPromedio, tiempoRespuestaPromedio, tiempoEsperaPromedio));
    }


     // --- Cerrar Simulador (limpieza) ---
     public void cerrarSimulador() {
         Logger.log("Cerrando Simulador...");
         // Detener hilos si siguen activos
          if (procesoThread != null && procesoThread.isAlive()) {
              procesoThread.interrupt();
          }
          for (Thread t : procesosEnExcepcion.values()) {
              if (t != null && t.isAlive()) {
                  t.interrupt();
              }
          }
         // Calcular y mostrar métricas finales
         calcularYMostrarMetricas();
         // Cerrar el logger
         Logger.close();
     }

     // --- Método de búsqueda para la GUI ---
    public Proceso buscarProcesoPorIdEnTodasLasListas(int id) {
        // Buscar en CPU
        if (procesoActual != null && procesoActual.getId() == id) return procesoActual;
        // Buscar en Listos
        Proceso p = buscarEnCola(colaListos, id);
        if (p != null) return p;
        // Buscar en Bloqueados
        p = mapaProcesosBloqueados.get(id); // Buscar en mapa por ID
        if (p != null) return p;
        // Buscar en Listos Suspendidos
        p = buscarEnCola(colaListosSuspendidos, id);
        if (p != null) return p;
        // Buscar en Bloqueados Suspendidos
        p = buscarEnCola(colaBloqueadosSuspendidos, id);
        if (p != null) return p;
        // Buscar en Terminados
        for(Proceso terminado : listaProcesosCompletados) {
            if(terminado.getId() == id) return terminado;
        }
        // No encontrado
        return null;
    }

    // Método auxiliar para buscar en CustomQueue por ID
    private Proceso buscarEnCola(CustomQueue cola, int id) {
        if (cola == null) return null;
        Proceso[] array = cola.toArray(); // Usar el método existente
        for (Proceso p : array) {
            if (p != null && p.getId() == id) {
                return p;
            }
        }
        return null;
    }
    
   

    
    public double getTiempoRetornoPromedioCalculado() {
        if (listaProcesosCompletados == null || listaProcesosCompletados.isEmpty()) {
            return 0.0;
        }
        long sumaTiemposRetorno = 0;
        int countParaPromedio = 0;
        for (Proceso p : listaProcesosCompletados) {
            long tRetorno = p.getTiempoRetorno();
            // Asegurarse de que el proceso realmente terminó y el tiempo es válido
            if (tRetorno >= 0) {
                sumaTiemposRetorno += tRetorno;
                countParaPromedio++;
            }
        }
        return (countParaPromedio > 0) ? (double) sumaTiemposRetorno / countParaPromedio : 0.0;
    }

    /**
     * Calcula y devuelve el Tiempo de Espera Promedio (en Listo/SuspListo)
     * de los procesos completados.
     * @return El tiempo promedio en milisegundos, o 0.0 si no hay procesos completados.
     */
    public double getTiempoEsperaPromedioCalculado() {
        if (listaProcesosCompletados == null || listaProcesosCompletados.isEmpty()) {
            return 0.0;
        }
        long sumaTiemposEspera = 0;
        int countParaPromedio = 0;
        for (Proceso p : listaProcesosCompletados) {
            // Usar tiempo de retorno para verificar que esté completo
             long tRetorno = p.getTiempoRetorno();
             if (tRetorno >= 0) {
                 sumaTiemposEspera += p.getTiempoTotalEsperandoListo();
                 countParaPromedio++;
            }
        }
        return (countParaPromedio > 0) ? (double) sumaTiemposEspera / countParaPromedio : 0.0;
    }

} 
