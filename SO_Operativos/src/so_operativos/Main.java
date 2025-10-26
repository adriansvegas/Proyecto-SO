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



public class Main {
    public static final int DEFAULT_PRIORITY = 5;
    public static final int DEFAULT_QUANTUM = 20;

    public static void main(String[] args) {
        Logger.init();

        ConfiguracionSimulacion config = ConfiguracionSimulacion.cargarConfiguracion();
        Planificador planificadorInicial = new PlanificadorFCFS();
        Simulador sim = new Simulador(config, planificadorInicial);

        SwingUtilities.invokeLater(() -> {
            SimuladorPrincipal gui = new SimuladorPrincipal(sim);
            gui.setVisible(true);
        });
    }
}
