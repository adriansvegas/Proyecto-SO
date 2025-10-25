package so_operativos;

public class ManejadorExcepcion implements Runnable {
    private final Proceso procesoEnEspera;
    private final CustomQueue colaListos;
    private final long duracionCicloMs;
    private final int ciclosIoIniciales;

    public ManejadorExcepcion(Proceso proceso, CustomQueue colaListos, long duracionCicloMs) {
        this.procesoEnEspera = proceso;
        this.colaListos = colaListos;
        this.duracionCicloMs = duracionCicloMs;
        this.ciclosIoIniciales = proceso.getContadorIOCiclos();
    }


    @Override
    public void run() {
        int ciclosRestantesIO = procesoEnEspera.getCiclosParaSatisfacerIO() - ciclosIoIniciales;

        System.out.printf("   [EXCEPCIÓN - %s] ⏳ Iniciando/Reanudando manejo de E/S (%d ciclos restantes de %d).%n",
                          procesoEnEspera.getNombre(),
                          ciclosRestantesIO,
                          procesoEnEspera.getCiclosParaSatisfacerIO());

        for (int i = 0; i < ciclosRestantesIO; i++) {
             if (procesoEnEspera.getEstado() == EstadoProceso.BLOQUEADO) {
                procesoEnEspera.incrementarContadorCiclosIO();
             } else {
                 System.out.printf("   [EXCEPCIÓN - %s] ⚠️ E/S interrumpida (Estado actual: %s). Saliendo.%n",
                                   procesoEnEspera.getNombre(), procesoEnEspera.getEstado());
                 return;
             }
            try {
                Thread.sleep(duracionCicloMs);
            } catch (InterruptedException e) {
                 System.out.printf("   [EXCEPCIÓN - %s] ⚠️ Hilo de E/S interrumpido (Estado: %s). Saliendo.%n",
                                   procesoEnEspera.getNombre(), procesoEnEspera.getEstado());
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (procesoEnEspera.getEstado() == EstadoProceso.BLOQUEADO && procesoEnEspera.getContadorIOCiclos() >= procesoEnEspera.getCiclosParaSatisfacerIO()) {
            procesoEnEspera.setEstado(EstadoProceso.LISTO);
            procesoEnEspera.resetContadorCiclos();

            synchronized (colaListos) {
                 colaListos.add(procesoEnEspera);
            }

            System.out.printf("   [EXCEPCIÓN - %s] ✅ E/S completada. Retorna a LISTO.%n", procesoEnEspera.getNombre());

        } else {
              System.out.printf("   [EXCEPCIÓN - %s] E/S no completada o estado cambió (Estado final: %s, Ciclos IO: %d/%d). Hilo terminando.%n",
                                procesoEnEspera.getNombre(), procesoEnEspera.getEstado(), procesoEnEspera.getContadorIOCiclos(), procesoEnEspera.getCiclosParaSatisfacerIO());
        }
    }
}
