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



/**
 * Componente JTextPane personalizado para mostrar logs y eventos de la simulación
 * con formato y colores específicos del tema visual.
 */
public class GuiOutput extends JTextPane {
    private StyledDocument doc; // Modelo del documento para texto con estilo
    // Colores del tema visual
    private final Color COLOR_FONDO_SECUNDARIO = new Color(68, 71, 90);
    private final Color COLOR_TEXTO_NORMAL = new Color(248, 248, 242);
    private final Color COLOR_DETALLES_VERDE = new Color(80, 250, 123);
    private final Color COLOR_DETALLES_NARANJA = new Color(255, 184, 108);

    /** Constructor que configura el estilo inicial del panel. */
    public GuiOutput() {
        configurarConsola();
    }

    /** Establece las propiedades visuales iniciales del JTextPane. */
    private void configurarConsola() {
        doc = getStyledDocument();
        setEditable(false); // No editable por el usuario
        setBackground(COLOR_FONDO_SECUNDARIO);
        setForeground(COLOR_TEXTO_NORMAL);
        setFont(new Font("Consolas", Font.PLAIN, 13));
        setCaretColor(COLOR_DETALLES_VERDE); // Color del cursor
        setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8)); // Padding
    }

    /**
     * Agrega una línea de texto con un color específico al final del panel.
     * Asegura que la actualización se realice en el Event Dispatch Thread (EDT) de Swing.
     * @param texto El texto a agregar.
     * @param color El color del texto.
     */
    public void agregarLinea(String texto, Color color) {
        SwingUtilities.invokeLater(() -> { // Ejecución segura en el hilo de UI
            try {
                SimpleAttributeSet estilo = new SimpleAttributeSet();
                StyleConstants.setForeground(estilo, color);
                StyleConstants.setFontFamily(estilo, "Consolas");
                StyleConstants.setFontSize(estilo, 13);
                doc.insertString(doc.getLength(), texto + "\n", estilo); // Agrega al final
                setCaretPosition(doc.getLength()); // Auto-scroll
            } catch (BadLocationException e) {
                 System.err.println("Error GuiOutput - agregarLinea: " + e.getMessage());
                 e.printStackTrace(); // Log del error en consola estándar
            }
        });
    }

    /**
     * Agrega una línea de texto con el color por defecto.
     * @param texto El texto a agregar.
     */
    public void agregarLinea(String texto) {
        agregarLinea(texto, COLOR_TEXTO_NORMAL);
    }

    /** Limpia todo el contenido del panel. */
    public void limpiar() {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.remove(0, doc.getLength());
            } catch (BadLocationException e) {
                 System.err.println("Error GuiOutput - limpiar: " + e.getMessage());
                 e.printStackTrace();
            }
        });
    }

    /** Agrega una línea separadora visual. */
    public void agregarSeparador() {
        agregarLinea("------------------------------------------------------------", COLOR_DETALLES_VERDE);
    }
}