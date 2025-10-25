/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author Edgar
 */


public class Logger {
    private static final String LOG_FILE = "simulador.log";
    private static PrintWriter writer;
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // Inicializa el logger (abre el archivo)
    public static void init() {
        try {
            
            writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)), true); // Auto-flush
            log("--- Inicio de Sesión del Simulador ---");
        } catch (IOException e) {
            System.err.println("❌ Error al inicializar el Logger: " + e.getMessage());
            writer = null; // Desactiva el logger si hay error
        }
    }

    // Escribe un mensaje de log con timestamp
    public static void log(String message) {
        if (writer != null) {
            String timestamp = dateFormat.format(new Date());
            writer.println(timestamp + " - " + message);
            // System.out.println("[LOG] " + message); // Descomentar para ver logs en consola también
        }
    }

    // Cierra el archivo de log (importante llamar al final)
    public static void close() {
        if (writer != null) {
            log("--- Fin de Sesión del Simulador ---");
            writer.close();
            writer = null;
        }
    }
}