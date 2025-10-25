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
    public void setDuracionCicloMs(long duracionCicloMs) { this.duracionCicloMs = duracionCicloMs; /* guardarConfiguracion(); */ }

    // Simulación de carga (sustituye el uso de librerías/JSON para este ejemplo)
    public static ConfiguracionSimulacion cargarConfiguracion() {
        return new ConfiguracionSimulacion(); 
    }
    
    // Método simulado de guardado (implementación real usaría Gson)
    private void guardarConfiguracion() {
        // Aquí iría la lógica para escribir en un archivo JSON usando Gson
    }
}