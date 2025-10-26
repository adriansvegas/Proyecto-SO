package so_operativos;

/**
 * Define los posibles estados en los que puede encontrarse un proceso
 * durante su ciclo de vida en el simulador.
 */
public enum EstadoProceso {
    NUEVO,               // Recién creado, aún no admitido por el planificador a largo plazo.
    LISTO,               // En memoria principal, esperando asignación de CPU.
    EJECUCION,           // Instrucciones siendo ejecutadas por la CPU.
    BLOQUEADO,           // Esperando que se complete una operación de E/S.
    TERMINADO,           // Ejecución completada.
    SUSPENDIDO_LISTO,    // Movido de Listo a disco (memoria secundaria) por falta de memoria principal.
    SUSPENDIDO_BLOQUEADO // Movido de Bloqueado a disco por falta de memoria principal.
}