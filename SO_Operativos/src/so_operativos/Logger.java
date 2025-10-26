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


/**
 * Clase de utilidad estática para registrar eventos importantes de la simulación
 * en un archivo de log (`simulador.log`).
 */
public class Logger {
    private static final String LOG_FILE = "simulador.log"; // Nombre del archivo de log
    private static PrintWriter writer; // Stream para escribir en el archivo
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"); // Formato de timestamp

    /**
     * Inicializa el logger. Abre (o crea) el archivo de log en modo append.
     * Debe llamarse una vez al inicio de la aplicación.
     */
    public static void init() {
        try {
            // FileWriter(..., true) para añadir al final del archivo si ya existe
            // BufferedWriter para eficiencia, PrintWriter con autoFlush
            writer = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)), true);
            log("--- Inicio de Sesión del Simulador ---");
        } catch (IOException e) {
            System.err.println("❌ Error al inicializar el Logger: " + e.getMessage());
            writer = null; // Deshabilita el logging si falla la inicialización
        }
    }

    /**
     * Escribe un mensaje en el archivo de log, precedido por un timestamp.
     * @param message El mensaje a registrar.
     */
    public static void log(String message) {
        if (writer != null) {
            String timestamp = dateFormat.format(new Date());
            writer.println(timestamp + " - " + message);
            // System.out.println("[LOG] " + message); // Descomentar para ver logs también en consola
        }
    }

    /**
     * Cierra el archivo de log de forma segura.
     * Debe llamarse al finalizar la aplicación.
     */
    public static void close() {
        if (writer != null) {
            log("--- Fin de Sesión del Simulador ---");
            writer.close();
            writer = null; // Marca como cerrado
        }
    }
}