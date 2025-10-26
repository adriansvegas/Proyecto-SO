/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package so_operativos;

import so_operativos.planificadores.PlanificadorFCFS;
import javax.swing.SwingUtilities; // Necesario para lanzar la GUI
import so_operativos.interfaz.SimuladorPrincipal; // Importar la GUI
/**
 *
 * @author Edgar
 */



/**
 * Clase principal de la aplicación. Punto de entrada que inicializa
 * el simulador y lanza la interfaz gráfica (SimuladorPrincipal).
 */
public class Main {
    // Constantes por defecto usadas en la creación de procesos o planificadores
    public static final int DEFAULT_PRIORITY = 5; // Prioridad por defecto (1-10)
    public static final int DEFAULT_QUANTUM = 20; // Quantum por defecto para Round Robin

    /**
     * Método principal (entry point).
     * @param args Argumentos de línea de comandos (no utilizados).
     */
    public static void main(String[] args) {
        // Inicializa el logger globalmente
        Logger.init();

        // Carga la configuración (duración de ciclo)
        ConfiguracionSimulacion config = ConfiguracionSimulacion.cargarConfiguracion();
        // Establece FCFS como planificador inicial (será sobreescrito si se carga estado)
        Planificador planificadorInicial = new PlanificadorFCFS();
        // Crea la instancia principal del simulador (el constructor carga el estado)
        Simulador sim = new Simulador(config, planificadorInicial);

        // Lanza la interfaz gráfica en el Event Dispatch Thread (EDT) de Swing
        SwingUtilities.invokeLater(() -> {
            SimuladorPrincipal gui = new SimuladorPrincipal(sim); // Crea la ventana principal
            gui.setVisible(true); // La hace visible
        });

        
    }
}