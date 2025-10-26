/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;


import java.io.FileWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 *
 * @author Edgar
 */

/**
 * Gestiona la configuración de la simulación, específicamente la duración del ciclo.
 * Permite cargar y guardar esta configuración en un archivo.
 */
public class ConfiguracionSimulacion {
    private long duracionCicloMs = 100; // Duración por defecto de un ciclo en milisegundos
    private static final String CONFIG_FILE = "sim_config.txt"; // Nombre del archivo de configuración

    /** Obtiene la duración actual del ciclo. */
    public long getDuracionCicloMs() { return duracionCicloMs; }

    /** Establece una nueva duración para el ciclo y guarda la configuración. */
    public void setDuracionCicloMs(long duracionCicloMs) {
        this.duracionCicloMs = duracionCicloMs;
        guardarConfiguracion(); // Guarda automáticamente al modificar
    }

    /**
     * Carga la configuración desde el archivo CONFIG_FILE.
     * Si el archivo no existe o hay un error, usa y guarda los valores por defecto.
     * @return Una instancia de ConfiguracionSimulacion con los valores cargados o por defecto.
     */
    public static ConfiguracionSimulacion cargarConfiguracion() {
        ConfiguracionSimulacion config = new ConfiguracionSimulacion();
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line = reader.readLine();
            if (line != null) {
               // Asume formato simple: "duracionCicloMs=valor"
               String[] parts = line.split("=");
               if (parts.length == 2 && parts[0].trim().equals("duracionCicloMs")) {
                   config.duracionCicloMs = Long.parseLong(parts[1].trim());
                   System.out.println("Configuración cargada: duracionCicloMs=" + config.duracionCicloMs);
               }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("No se pudo cargar config desde " + CONFIG_FILE + ", usando defecto. Error: " + e.getMessage());
            config.guardarConfiguracion(); // Guarda el valor por defecto si falla la carga
        }
        return config;
    }

    /** Guarda la configuración actual (duracionCicloMs) en el archivo CONFIG_FILE. */
    void guardarConfiguracion() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE, false)) { // false para sobrescribir
            writer.write("duracionCicloMs=" + this.duracionCicloMs + "\n");
             System.out.println("Configuración guardada en " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error al guardar config en " + CONFIG_FILE + ": " + e.getMessage());
        }
    }
}