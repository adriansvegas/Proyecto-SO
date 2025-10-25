import so_operativos.Proceso;
import java.util.concurrent.ConcurrentHashMap;



/**
 *
 * @author adria
 */

public class ManejadorExcepcion implements Runnable {
    private final Proceso procesoEnEspera;
    private final CustomQueue colaListos;
    private final long duracionCicloMs;
    
    public ManejadorExcepcion(Proceso proceso, CustomQueue colaListos, long duracionCicloMs) {
        this.procesoEnEspera = proceso;
        this.colaListos = colaListos;
        this.duracionCicloMs = duracionCicloMs;
    }

    @Override
    public void run() {
        System.out.printf("   [EXCEPCIÓN - %s] ⏳ Iniciando manejo de E/S (%d ciclos).%n", 
                          procesoEnEspera.getNombre(), 
                          procesoEnEspera.getCiclosParaSatisfacerIO());
        
        // Simular el tiempo de E/S
        for (int i = 0; i < procesoEnEspera.getCiclosParaSatisfacerIO(); i++) {
            procesoEnEspera.incrementarContadorCiclosIO(); 
            try {
                Thread.sleep(duracionCicloMs); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // E/S completada. Regresar el proceso a la cola de listos
        procesoEnEspera.setEstado(EstadoProceso.LISTO);
        procesoEnEspera.resetContadorCiclos();
        
        // El proceso regresa al "procesador donde fue generado" (la única cola de listos)
        colaListos.add(procesoEnEspera); 
        
        System.out.printf("   [EXCEPCIÓN - %s] ✅ E/S completada. Retorna a LISTO.%n", procesoEnEspera.getNombre());
    }
}