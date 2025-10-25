/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 *
 * @author adria
 */



public class Simulador {
    private final ConfiguracionSimulacion config;
    private Planificador planificador;

    private final CustomQueue colaListos;
    // Colas nuevas para el estado suspendido
    private final CustomQueue colaListosSuspendidos;
    private final CustomQueue colaBloqueadosSuspendidos;

    // ConcurrentHashMap es la única estructura de java.util permitida para gestión de hilos/concurrencia
    private final ConcurrentHashMap<Integer, Thread> procesosEnExcepcion;
    // Almacén para buscar procesos bloqueados por ID (necesario para suspensión desde bloqueado)
    private final ConcurrentHashMap<Integer, Proceso> mapaProcesosBloqueados;

    // REEMPLAZO: Arreglo simple para procesos terminados
    private Proceso[] procesosTerminadosArray;
    private int terminadosCount;
    private static final int INITIAL_CAPACITY = 10; // Capacidad inicial para el arreglo

    private Proceso procesoActual;
    private Thread procesoThread;

    private long tiempoSimulacion;
    private int ciclosQuantum;
    private final Semaphore cpuSemaphore;

    // Umbral simple para simular límite de memoria (Listo + Bloqueado)
    private static final int UMBRAL_PROCESOS_MEMORIA = 5;

    // Getters requeridos por Main.java
    public Planificador getPlanificador() { return this.planificador; }
    public ConfiguracionSimulacion getConfig() { return this.config; }

    public Simulador(ConfiguracionSimulacion config, Planificador planificador) {
        this.config = config;
        this.planificador = planificador;

        this.colaListos = new CustomQueue();
        // Inicializar nuevas colas
        this.colaListosSuspendidos = new CustomQueue();
        this.colaBloqueadosSuspendidos = new CustomQueue();

        this.procesosEnExcepcion = new ConcurrentHashMap<>();
        this.mapaProcesosBloqueados = new ConcurrentHashMap<>(); // Inicializar mapa

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
        reordenarColaListos(null);
    }

    // Modificado para incluir lógica de suspensión
    public void agregarProceso(Proceso proceso) {
        proceso.setCpuSemaphore(this.cpuSemaphore);
        // Decide si entra a Listo o Suspendido_Listo basado en el umbral
        if (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             colaListos.add(proceso);
             proceso.setEstado(EstadoProceso.LISTO);
             System.out.println("➡️ Proceso " + proceso.getNombre() + " agregado a LISTO.");
             reordenarColaListos(proceso);
        } else {
            colaListosSuspendidos.add(proceso);
            proceso.setEstado(EstadoProceso.SUSPENDIDO_LISTO);
            System.out.println("⏳ Proceso " + proceso.getNombre() + " agregado a SUSPENDIDO_LISTO (Memoria llena).");
            // Opcional: Reordenar colaListosSuspendidos si se necesita alguna prioridad allí
        }

        // Intenta suspender si se sobrepasa el umbral DESPUÉS de añadir (menos común aquí)
        // revisarYSuspenderSiNecesario(); // Podrías llamar a una función que haga esto
    }

    // Función auxiliar para suspender (Ejemplo simple: el último de la cola de listos)
    private void revisarYSuspenderSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() > UMBRAL_PROCESOS_MEMORIA && !colaListos.isEmpty()) {
            Proceso aSuspender = buscarProcesoMenosPrioritario(colaListos); // O el último, o el de menor prioridad, etc.
             if (aSuspender != null) {
                colaListos.remove(aSuspender); // Asegúrate que remove funcione bien
                aSuspender.setEstado(EstadoProceso.SUSPENDIDO_LISTO);
                colaListosSuspendidos.add(aSuspender);
                System.out.println("⚠️ MEMORIA: Proceso " + aSuspender.getNombre() + " suspendido (LISTO -> SUSP_LISTO).");
             } else {
                 break; // No se pudo encontrar/remover proceso para suspender
             }
         }
         // Lógica similar para suspender desde Bloqueado si es necesario (más complejo)
         // Se podría buscar el proceso bloqueado con más tiempo restante de E/S, por ejemplo
    }

     // Función auxiliar para reanudar (Ejemplo simple: el primero de suspendidos listos)
    private void revisarYReanudarSiNecesario() {
         while (colaListos.size() + mapaProcesosBloqueados.size() < UMBRAL_PROCESOS_MEMORIA) {
             Proceso aReanudar = null;
             EstadoProceso estadoDestino = null;

             // Prioriza reanudar de Listo_Suspendido
             if (!colaListosSuspendidos.isEmpty()) {
                 aReanudar = buscarProcesoMasPrioritario(colaListosSuspendidos); // O el primero, etc.
                 if (aReanudar != null) {
                    colaListosSuspendidos.remove(aReanudar);
                    estadoDestino = EstadoProceso.LISTO;
                    colaListos.add(aReanudar); // Añadir a la cola de listos
                 }
             }
             // Si no hay listos suspendidos, intenta con bloqueados suspendidos
             else if (!colaBloqueadosSuspendidos.isEmpty()) {
                  aReanudar = buscarProcesoMasPrioritario(colaBloqueadosSuspendidos); // O el primero, etc.
                  if (aReanudar != null) {
                     colaBloqueadosSuspendidos.remove(aReanudar);
                     estadoDestino = EstadoProceso.BLOQUEADO;
                     // Ponerlo de nuevo en el mapa de bloqueados y reiniciar su hilo de excepción
                     mapaProcesosBloqueados.put(aReanudar.getId(), aReanudar);
                     reanudarManejadorExcepcion(aReanudar); // Necesitas implementar esto
                  }
             }

             if (aReanudar != null) {
                 aReanudar.setEstado(estadoDestino);
                 System.out.println("⭐ MEMORIA: Proceso " + aReanudar.getNombre() + " reanudado a " + estadoDestino + ".");
                 if (estadoDestino == EstadoProceso.LISTO) {
                    reordenarColaListos(aReanudar); // Reordenar si aplica
                 }
             } else {
                 break; // No hay más procesos para reanudar o no se pudieron remover
             }
         }
    }

    // --- Métodos de búsqueda simples (puedes hacerlos más sofisticados) ---
    private Proceso buscarProcesoMenosPrioritario(CustomQueue queue) {
        if (queue.isEmpty()) return null;
        Proceso[] array = queue.toArray();
        Proceso peor = array[0];
        // Busca el de MAYOR prioridad numérica (menor importancia) o el último si son iguales
        for (int i = 1; i < array.length; i++) {
             if (array[i].getPrioridad() >= peor.getPrioridad()) {
                 peor = array[i];
             }
        }
        return peor;
    }

    private Proceso buscarProcesoMasPrioritario(CustomQueue queue) {
         if (queue.isEmpty()) return null;
         Proceso[] array = queue.toArray();
         Proceso mejor = array[0];
         // Busca el de MENOR prioridad numérica (mayor importancia) o el primero si son iguales
         for (int i = 1; i < array.length; i++) {
              if (array[i].getPrioridad() < mejor.getPrioridad()) {
                  mejor = array[i];
              }
         }
         return mejor;
     }

    // Necesitarías implementar la lógica para reiniciar el hilo ManejadorExcepcion
    private void reanudarManejadorExcepcion(Proceso proceso) {
        System.out.println("   [Simulador] Reanudando E/S para " + proceso.getNombre());
        proceso.setEstado(EstadoProceso.BLOQUEADO); // Asegura estado correcto
        ManejadorExcepcion handler = new ManejadorExcepcion(
            proceso,
            colaListos, // Vuelve a la cola de listos al terminar
            config.getDuracionCicloMs()
            // Podrías necesitar pasarle cuántos ciclos de IO le faltaban
        );
        Thread handlerThread = new Thread(handler, "Excepción-" + proceso.getId() + "-Reanudado");
        handlerThread.start();
        procesosEnExcepcion.put(proceso.getId(), handlerThread); // Re-añade a hilos activos
        mapaProcesosBloqueados.put(proceso.getId(), proceso); // Asegura que esté en el mapa correcto
    }
    // --- Fin métodos de búsqueda ---


    private void reordenarColaListos(Proceso nuevoProceso) {
        if (planificador instanceof PlanificadorFCFS || planificador instanceof PlanificadorRoundRobin) return;

        Proceso[] lista = colaListos.toArray();
        int length = colaListos.size();

        // Algoritmo de ordenamiento Burbuja (sin librerías)
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

    // Modificado para incluir chequeo de nuevas colas
    public boolean quedanProcesos() {
        return !colaListos.isEmpty() || procesoActual != null || !procesosEnExcepcion.isEmpty()
               || !colaListosSuspendidos.isEmpty() || !colaBloqueadosSuspendidos.isEmpty(); // <-- Chequeo añadido
    }

    public void ejecutarCicloSimulacion() {
        tiempoSimulacion += config.getDuracionCicloMs();
        System.out.println("\n--- Ciclo de Simulaci?n @ " + tiempoSimulacion + "ms ---");

        manejarExcepciones(); // Quita hilos de E/S terminados
        comprobarExpropiacion(); // Verifica si un proceso listo debe quitar al actual

        // --- Lógica de Suspensión/Reanudación ---
        // Se ejecutan ANTES de planificar o chequear estado,
        // para asegurar que las colas estén actualizadas.
        revisarYSuspenderSiNecesario(); // Suspende si hay demasiados procesos en memoria
        revisarYReanudarSiNecesario();  // Reanuda si hay espacio en memoria y procesos suspendidos
        // ---------------------------------------

        if (procesoActual == null) {
            planificarSiguiente(); // Asigna CPU si está libre y hay procesos listos
        } else {
            manejarQuantum(); // Verifica si se acabó el quantum para RR
        }

        chequearEstadoEjecucion(); // Verifica si el proceso actual terminó o se bloqueó
        mostrarEstado(); // Muestra el estado actual de todas las colas

        try {
            Thread.sleep(config.getDuracionCicloMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Modificado para quitar proceso del mapaProcesosBloqueados
    private void manejarExcepciones() {
        // Remover hilos de excepción que han terminado y quitar del mapa de bloqueados
        procesosEnExcepcion.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                mapaProcesosBloqueados.remove(entry.getKey()); // Quitar del mapa al terminar E/S
                return true; // Eliminar la entrada del ConcurrentHashMap procesosEnExcepcion
            }
            return false; // Mantener la entrada si el hilo sigue vivo
        });
    }


    // Modificado para incluir lógica de reanudación al terminar
    private void chequearEstadoEjecucion() {
        if (procesoActual == null) return;

        if (procesoActual.haTerminado()) {
            procesoActual.setEstado(EstadoProceso.TERMINADO);

            // Redimensionar el arreglo si es necesario
            if (terminadosCount >= procesosTerminadosArray.length) {
                Proceso[] newArray = new Proceso[procesosTerminadosArray.length * 2];
                //System.arraycopy(procesosTerminadosArray, 0, newArray, 0, terminadosCount); // Alternativa más eficiente si se permitiera
                 for (int i = 0; i < terminadosCount; i++) {
                     newArray[i] = procesosTerminadosArray[i];
                 }
                procesosTerminadosArray = newArray;
            }
            procesosTerminadosArray[terminadosCount++] = procesoActual;

            if (procesoThread != null) procesoThread.interrupt();
            Proceso terminado = procesoActual; // Guardar referencia antes de anular
            procesoActual = null;
            procesoThread = null;
            System.out.println("🛑 Proceso " + terminado.getNombre() + " terminó.");

            // Al terminar un proceso, revisa si se puede reanudar alguno suspendido
            revisarYReanudarSiNecesario();

        } else if (procesoActual.getEstado() == EstadoProceso.BLOQUEADO) {
            // Genera una EXCEPCIÓN DE E/S
            if (procesoThread != null) procesoThread.interrupt();

             Proceso bloqueado = procesoActual; // Guardar referencia
             procesoActual = null;
             procesoThread = null;

             System.out.println("🚨 EXCEPCIÓN: " + bloqueado.getNombre() + " genera E/S.");

             // Decide si va a Bloqueado o Suspendido_Bloqueado
             if (colaListos.size() + mapaProcesosBloqueados.size() + 1 <= UMBRAL_PROCESOS_MEMORIA) { // +1 por el que se va a bloquear
                 bloqueado.setEstado(EstadoProceso.BLOQUEADO);
                 mapaProcesosBloqueados.put(bloqueado.getId(), bloqueado); // Añadir al mapa de bloqueados
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

            // Ya no es necesario reordenar aquí porque el proceso o va a bloqueado o a suspendido
            // reordenarColaListos(null);
             // Al bloquearse uno, puede que haya espacio para reanudar otro (menos común, pero posible)
             revisarYReanudarSiNecesario();
             // También, si se llenó la memoria justo ahora, hay que suspender
             revisarYSuspenderSiNecesario();
        }
    }

    private void comprobarExpropiacion() {
        if (procesoActual == null || colaListos.isEmpty()) return;

        boolean expropiar = false;
        Proceso candidato = colaListos.peek(); // Solo mira el primero (que debería ser el mejor si la cola está ordenada)

        if (candidato == null) return; // Cola vacía o error en peek

        if (planificador instanceof PlanificadorSRT) {
            if (candidato.getInstruccionesRestantes() < procesoActual.getInstruccionesRestantes()) expropiar = true;
        } else if (planificador instanceof PlanificadorPrioridadExpropiativa) {
            if (candidato.getPrioridad() < procesoActual.getPrioridad()) expropiar = true;
        }

        if (expropiar) {
            System.out.println("🚨 EXPROPIACIÓN: Proceso " + candidato.getNombre() + " expropia a " + procesoActual.getNombre() + ".");
            procesoActual.setEstado(EstadoProceso.LISTO);
            //colaListos.add(procesoActual); // No añadir directamente, planificarSiguiente lo recogerá si es necesario
            if (procesoThread != null) procesoThread.interrupt();

            Proceso expropiado = procesoActual; // Guardar referencia
            procesoActual = null; // Marcar CPU como libre
            procesoThread = null;

            // Añadir el expropiado de nuevo a la cola y reordenar ANTES de planificar el siguiente
            colaListos.add(expropiado);
            reordenarColaListos(expropiado);

            // No llamamos a planificarSiguiente aquí directamente,
            // el flujo principal lo hará al ver que procesoActual es null.
        }
    }


    private void manejarQuantum() {
        if (procesoActual != null && planificador instanceof PlanificadorRoundRobin rr) {
            ciclosQuantum--;
            if (ciclosQuantum <= 0) {
                 System.out.println("⏱️ Quantum terminado para " + procesoActual.getNombre() + ". Expropiado a LISTO.");
                procesoActual.setEstado(EstadoProceso.LISTO);

                if (procesoThread != null) procesoThread.interrupt();

                Proceso expropiado = procesoActual; // Guardar referencia
                procesoActual = null;
                procesoThread = null;

                // Añadir de nuevo a la cola DESPUÉS de liberar la CPU
                colaListos.add(expropiado);
                // No se necesita reordenar para RR

                 // Al salir por quantum, revisa si hay que suspender/reanudar
                 revisarYSuspenderSiNecesario();
                 revisarYReanudarSiNecesario();
            }
        }
    }

    private void planificarSiguiente() {
        // Solo planifica si la CPU está libre Y hay procesos en la cola de listos
        if (procesoActual == null && !colaListos.isEmpty()) {
            Proceso siguienteProceso;
            // No es necesario synchronized si las operaciones de CustomQueue ya lo son
            siguienteProceso = planificador.seleccionarSiguiente(colaListos);

            if (siguienteProceso != null) {
                procesoActual = siguienteProceso;
                procesoActual.setEstado(EstadoProceso.EJECUCION); // Cambiar estado ANTES de iniciar hilo

                if (planificador instanceof PlanificadorRoundRobin rr) {
                    ciclosQuantum = rr.getQuantum();
                } else {
                    ciclosQuantum = 0; // Resetea por si acaso
                }

                procesoThread = new Thread(procesoActual, "Proceso-" + procesoActual.getId());
                procesoThread.start();

                System.out.println("⬅️ CONTEXT SWITCH: Proceso " + procesoActual.getNombre() + " pasa a EJECUCIÓN.");
            }
        }
    }

    // Modificado para mostrar todas las colas
    public void mostrarEstado() {
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
            // Iterar sobre los procesos en el mapa de bloqueados para mostrar su PCB
            // Usamos mapaProcesosBloqueados.values() si se permite esa pequeña flexibilidad de iteración
            // Alternativa manual si no se permite: buscar cada proceso por ID en un array temporal (menos eficiente)
             for (Proceso p : mapaProcesosBloqueados.values()) { // Si esto no se permite, hay que buscarlo
                  p.mostrarPCB();
                  // Podríamos mostrar cuánto le falta de E/S si Proceso tuviera un getContadorIOCiclos()
             }
            // Mostrar hilos activos que podrían no estar en el mapa si hubo inconsistencia (debug)
            /*
            for (Integer id : procesosEnExcepcion.keySet()) {
                 if (!mapaProcesosBloqueados.containsKey(id)) {
                      System.out.printf("   [Hilo Excepción ID %d activo pero no en mapa Bloqueados]%n", id);
                 }
            }
            */
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
             // Mostrar más info si se desea, por ahora solo nombre y ID/Prioridad
             System.out.printf("   [ID %d - %s | Pri:%d]%n", p.getId(), p.getNombre(), p.getPrioridad());
        }

        System.out.println("----------------------------------------");
    }
}