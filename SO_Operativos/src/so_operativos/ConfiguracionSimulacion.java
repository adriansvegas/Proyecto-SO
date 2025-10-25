/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;
import java.io.FileWriter; // Necesario para escribir
import java.io.IOException; // Para manejar errores de archivo
import java.io.FileReader; // Necesario para leer
import java.io.BufferedReader; // Para leer líneas

/**
 *
 * @author Edgar
 */

package so_operativos;

// Imports añadidos para manejo de archivos
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

public class ConfiguracionSimulacion {
    private long duracionCicloMs = 100; // Valor por defecto
    private static final String CONFIG_FILE = "sim_config.txt"; // Nombre del archivo

    public long getDuracionCicloMs() { return duracionCicloMs; }

    public void setDuracionCicloMs(long duracionCicloMs) {
        this.duracionCicloMs = duracionCicloMs;
        guardarConfiguracion(); // Llama a guardar al modificar
    }

    // Modificado para cargar desde archivo
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
            System.err.println("No se pudo cargar la configuración desde " + CONFIG_FILE + ", usando valores por defecto. Error: " + e.getMessage());
            // Si falla al leer o parsear, simplemente usa los valores por defecto.
            // Crea el archivo con el valor por defecto si no existe o falla la carga inicial.
             config.guardarConfiguracion();
        }
        return config;
    }

    // Modificado para guardar en archivo (ya no es privado para poder llamarlo desde cargarConfiguracion si falla)
    void guardarConfiguracion() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE, false)) { // false para sobrescribir
            writer.write("duracionCicloMs=" + this.duracionCicloMs + "\n");
             System.out.println("Configuración guardada en " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error al guardar la configuración en " + CONFIG_FILE + ": " + e.getMessage());
        }
    }
}