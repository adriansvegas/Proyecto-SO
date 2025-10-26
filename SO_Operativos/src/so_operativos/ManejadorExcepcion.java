package so_operativos;

/**
 * Representa un hilo que simula la duración de una operación de E/S para un proceso.
 * Se ejecuta en paralelo al simulador principal. Al finalizar, mueve el proceso
 * de vuelta a la cola de listos.
 */
public class ManejadorExcepcion implements Runnable {
    private final Proceso procesoEnEspera; // El proceso que está realizando la E/S
    private final Cola colaListos; // Cola a la que volverá el proceso al terminar E/S
    private final long duracionCicloMs; // Duración de un ciclo (para simular espera)
    private final int ciclosIoIniciales; // Ciclos de E/S que ya había completado (si se reanuda)

    /**
     * Constructor del manejador.
     * @param proceso El proceso que entra en E/S.
     * @param colaListos La cola de procesos listos del simulador.
     * @param duracionCicloMs Duración de un ciclo de simulación en ms.
     */
    public ManejadorExcepcion(Proceso proceso, Cola colaListos, long duracionCicloMs) {
        this.procesoEnEspera = proceso;
        this.colaListos = colaListos;
        this.duracionCicloMs = duracionCicloMs;
        this.ciclosIoIniciales = proceso.getContadorIOCiclos(); // Guarda progreso inicial
    }

    /**
     * Lógica de ejecución del hilo de E/S.
     * Simula el paso del tiempo y actualiza el estado del proceso al finalizar.
     */
    @Override
    public void run() {
        int ciclosRestantesIO = procesoEnEspera.getCiclosParaSatisfacerIO() - ciclosIoIniciales;

        System.out.printf("   [EXCEPCIÓN - %s] ⏳ Iniciando/Reanudando E/S (%d ciclos restantes de %d).%n", procesoEnEspera.getNombre(), ciclosRestantesIO, procesoEnEspera.getCiclosParaSatisfacerIO());

        // Simula cada ciclo de E/S
        for (int i = 0; i < ciclosRestantesIO; i++) {
             // Solo incrementa contador si sigue en estado BLOQUEADO (podría ser suspendido)
             if (procesoEnEspera.getEstado() == EstadoProceso.BLOQUEADO) {
                procesoEnEspera.incrementarContadorCiclosIO();
             } else {
                 // Si el estado cambió (ej. SUSPENDIDO_BLOQUEADO), se interrumpe la E/S actual
                 System.out.printf("   [EXCEPCIÓN - %s] ⚠️ E/S interrumpida (Estado: %s). Saliendo.%n", procesoEnEspera.getNombre(), procesoEnEspera.getEstado());
                 return; // Termina el hilo
             }
            // Espera simulando la duración de un ciclo
            try { Thread.sleep(duracionCicloMs); }
            catch (InterruptedException e) {
                 // Si el hilo es interrumpido (ej. por suspensión o cierre), termina
                 System.out.printf("   [EXCEPCIÓN - %s] ⚠️ Hilo E/S interrumpido (Estado: %s). Saliendo.%n", procesoEnEspera.getNombre(), procesoEnEspera.getEstado());
                 Thread.currentThread().interrupt(); return; // Termina el hilo
            }
        }

        // Verifica si la E/S se completó y el proceso sigue en estado BLOQUEADO
        if (procesoEnEspera.getEstado() == EstadoProceso.BLOQUEADO && procesoEnEspera.getContadorIOCiclos() >= procesoEnEspera.getCiclosParaSatisfacerIO()) {
            procesoEnEspera.setEstado(EstadoProceso.LISTO); // Cambia estado a LISTO
            procesoEnEspera.resetContadorCiclos(); // Resetea contadores para la próxima ráfaga de CPU

            // Añade de forma segura (synchronized) el proceso de vuelta a la cola de listos
            synchronized (colaListos) { colaListos.add(procesoEnEspera); }

            System.out.printf("   [EXCEPCIÓN - %s] ✅ E/S completada. Retorna a LISTO.%n", procesoEnEspera.getNombre());
        } else {
            // Caso donde el estado cambió antes de completar la E/S (ej. suspendido)
            System.out.printf("   [EXCEPCIÓN - %s] E/S no completada o estado cambió (Estado final: %s, Ciclos IO: %d/%d). Hilo terminando.%n", procesoEnEspera.getNombre(), procesoEnEspera.getEstado(), procesoEnEspera.getContadorIOCiclos(), procesoEnEspera.getCiclosParaSatisfacerIO());
        }
        // El hilo termina su ejecución aquí
    }
}