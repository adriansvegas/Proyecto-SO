/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos.interfaz;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import javax.swing.SwingUtilities;
import java.awt.Color;

/**
 *
 * @author adria
 */
/**
 * Redirige los flujos de salida estándar (System.out) y error (System.err)
 * hacia un componente GuiOutput, permitiendo ver estos mensajes en la interfaz gráfica.
 */
public class EventDisplay {
    private GuiOutput consola; // Componente GUI destino
    private PrintStream originalOut; // Guarda el System.out original
    private PrintStream originalErr; // Guarda el System.err original
    // Colores para diferenciar la salida estándar de la de error
    private final Color COLOR_TEXTO_NORMAL = new Color(248, 248, 242);
    private final Color COLOR_ERROR = new Color(255, 85, 85);

    /**
     * Constructor que asocia el redirector con un componente GuiOutput.
     * @param consola El panel donde se mostrará la salida redirigida.
     */
    public EventDisplay(GuiOutput consola) {
        this.consola = consola;
    }

    /** Activa la redirección de System.out y System.err. */
    public void redirect() {
         redirectSystemStreams();
     }

    /** Reemplaza los flujos de salida del sistema por flujos personalizados. */
    private void redirectSystemStreams() {
        if (originalOut == null) originalOut = System.out; // Guarda el original si no se ha hecho
        if (originalErr == null) originalErr = System.err;

        // Crea nuevos PrintStreams que escriben en nuestro OutputStream personalizado
        PrintStream printStreamOut = new PrintStream(new CustomOutputStream(consola, COLOR_TEXTO_NORMAL), true); // true para autoFlush
        System.setOut(printStreamOut); // Reemplaza System.out

        PrintStream printStreamErr = new PrintStream(new CustomOutputStream(consola, COLOR_ERROR), true);
        System.setErr(printStreamErr); // Reemplaza System.err
        System.out.println("--- Redirección de consola activada ---"); // Mensaje de confirmación
    }

    /** Restaura System.out y System.err a sus flujos originales. */
    public void restoreSystemStreams() {
        if (originalOut != null) {
            System.out.flush(); // Asegura que se envíe cualquier buffer restante
            System.setOut(originalOut); // Restaura el original
            originalOut = null; // Evita restaurar múltiples veces
             System.out.println("--- Redirección System.out restaurada ---"); // Mensaje en consola original
        }
        if (originalErr != null) {
             System.err.flush();
            System.setErr(originalErr); // Restaura el original
            originalErr = null;
             System.err.println("--- Redirección System.err restaurada ---"); // Mensaje en consola original
        }
    }

    /**
     * Clase interna privada que actúa como un OutputStream.
     * Envía los bytes escritos (como texto) al componente GuiOutput asociado,
     * asegurando que la actualización ocurra en el hilo de UI de Swing.
     */
    private class CustomOutputStream extends OutputStream {
        private GuiOutput consola;
        private Color color;
        private StringBuilder buffer; // Acumula caracteres hasta encontrar un salto de línea

        public CustomOutputStream(GuiOutput consola, Color color) {
            this.consola = consola;
            this.color = color;
            this.buffer = new StringBuilder();
        }

        /** Procesa un byte individual. Si es salto de línea, envía el buffer. */
        @Override
        public void write(int b) throws IOException {
            char c = (char) b;
            if (c == '\n') { // Salto de línea encontrado
                final String line = buffer.toString();
                 SwingUtilities.invokeLater(() -> consola.agregarLinea(line, color)); // Envía al GUI en el hilo correcto
                buffer.setLength(0); // Limpia buffer
            } else {
                buffer.append(c); // Acumula en buffer
            }
        }

        /** Optimización para procesar arrays de bytes (maneja múltiples líneas). */
         @Override
         public void write(byte[] b, int off, int len) throws IOException {
             if (b == null) throw new NullPointerException();
             if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) throw new IndexOutOfBoundsException();
             if (len == 0) return;
             String text = new String(b, off, len); // Convierte bytes a String

             int start = 0;
             for (int i = 0; i < text.length(); i++) {
                 if (text.charAt(i) == '\n') { // Línea completa encontrada
                     buffer.append(text.substring(start, i));
                     final String lineToSend = buffer.toString();
                     SwingUtilities.invokeLater(() -> consola.agregarLinea(lineToSend, color));
                     buffer.setLength(0);
                     start = i + 1;
                 }
             }
             if (start < text.length()) buffer.append(text.substring(start)); // Añade el resto al buffer
         }

        /** Envía cualquier contenido restante en el buffer al hacer flush. */
        @Override
         public void flush() throws IOException {
             if (buffer.length() > 0) {
                 final String lineToSend = buffer.toString();
                 SwingUtilities.invokeLater(() -> consola.agregarLinea(lineToSend, color)); // Envía sin añadir \n
                 buffer.setLength(0);
             }
         }
    }
}