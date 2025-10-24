/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;

/**
 *
 * @author Edgar
 */

public class ConfiguracionSimulacion {
    private long duracionCicloMs = 100; // Valor por defecto

    public long getDuracionCicloMs() { return duracionCicloMs; }
    public void setDuracionCicloMs(long duracionCicloMs) { this.duracionCicloMs = duracionCicloMs; }

    public static ConfiguracionSimulacion cargarConfiguracion() {
        return new ConfiguracionSimulacion(); 
    }
}
