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
public class EventDisplay {
    private GuiOutput consola;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private final Color COLOR_TEXTO_NORMAL = new Color(248, 248, 242);
    private final Color COLOR_ERROR = new Color(255, 85, 85);

    public EventDisplay(GuiOutput consola) {
        this.consola = consola;
    }

    public void redirect() {
          redirectSystemStreams();
      }


    private void redirectSystemStreams() {
        if (originalOut == null) originalOut = System.out;
        if (originalErr == null) originalErr = System.err;

        PrintStream printStreamOut = new PrintStream(new CustomOutputStream(consola, COLOR_TEXTO_NORMAL), true);
        System.setOut(printStreamOut);

        PrintStream printStreamErr = new PrintStream(new CustomOutputStream(consola, COLOR_ERROR), true);
        System.setErr(printStreamErr);
        System.out.println("--- Redirección de consola activada ---");
    }

    public void restoreSystemStreams() {
        if (originalOut != null) {
            System.out.flush();
            System.setOut(originalOut);
            originalOut = null;
            System.out.println("--- Redirección System.out restaurada ---");
        }
        if (originalErr != null) {
            System.err.flush();
            System.setErr(originalErr);
            originalErr = null;
            System.err.println("--- Redirección System.err restaurada ---");
        }
    }

    private class CustomOutputStream extends OutputStream {
        private GuiOutput consola;
        private Color color;
        private StringBuilder buffer;

        public CustomOutputStream(GuiOutput consola, Color color) {
            this.consola = consola;
            this.color = color;
            this.buffer = new StringBuilder();
        }

        @Override
        public void write(int b) throws IOException {
            char c = (char) b;
            if (c == '\n') {
                final String line = buffer.toString();
                SwingUtilities.invokeLater(() -> consola.agregarLinea(line, color));
                buffer.setLength(0);
            } else {
                buffer.append(c);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) throw new IndexOutOfBoundsException();
            if (len == 0) return;
            String text = new String(b, off, len);

            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    buffer.append(text.substring(start, i));
                    final String lineToSend = buffer.toString();
                    SwingUtilities.invokeLater(() -> consola.agregarLinea(lineToSend, color));
                    buffer.setLength(0);
                    start = i + 1;
                }
            }
            if (start < text.length()) buffer.append(text.substring(start));
        }

        @Override
        public void flush() throws IOException {
            if (buffer.length() > 0) {
                final String lineToSend = buffer.toString();
                SwingUtilities.invokeLater(() -> consola.agregarLinea(lineToSend, color));
                buffer.setLength(0);
            }
        }
    }
}
