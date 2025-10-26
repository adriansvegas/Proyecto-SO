/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos.interfaz;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/**
 *
 * @author adria
 */



public class GuiOutput extends JTextPane {
    private StyledDocument doc;
    private final Color COLOR_FONDO_SECUNDARIO = new Color(68, 71, 90);
    private final Color COLOR_TEXTO_NORMAL = new Color(248, 248, 242);
    private final Color COLOR_DETALLES_VERDE = new Color(80, 250, 123);
    private final Color COLOR_DETALLES_NARANJA = new Color(255, 184, 108);

    public GuiOutput() {
        configurarConsola();
    }

    private void configurarConsola() {
        doc = getStyledDocument();

        setEditable(false);
        setBackground(COLOR_FONDO_SECUNDARIO);
        setForeground(COLOR_TEXTO_NORMAL);
        setFont(new Font("Consolas", Font.PLAIN, 13));
        setCaretColor(COLOR_DETALLES_VERDE);
        setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    public void agregarLinea(String texto, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet estilo = new SimpleAttributeSet();
                StyleConstants.setForeground(estilo, color);
                StyleConstants.setFontFamily(estilo, "Consolas");
                StyleConstants.setFontSize(estilo, 13);

                doc.insertString(doc.getLength(), texto + "\n", estilo);
                setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                System.err.println("Error ConsolaGamer - agregarLinea: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void agregarLinea(String texto) {
        agregarLinea(texto, COLOR_TEXTO_NORMAL);
    }

    public void limpiar() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException e) {
                System.err.println("Error ConsolaGamer - limpiar: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void agregarSeparador() {
        agregarLinea("------------------------------------------------------------", COLOR_DETALLES_VERDE);
    }
}