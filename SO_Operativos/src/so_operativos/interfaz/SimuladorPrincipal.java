package so_operativos.interfaz;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import so_operativos.ConfiguracionSimulacion;
import so_operativos.CustomQueue;
import so_operativos.EstadoProceso;
import so_operativos.Logger;
import so_operativos.Main;
import so_operativos.Planificador;
import so_operativos.Simulador;
import so_operativos.Proceso;
import so_operativos.planificadores.*;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * Ventana principal de la interfaz gráfica del simulador.
 * Gestiona la visualización y la interacción del usuario con el simulador.
 */
public class SimuladorPrincipal extends JFrame {

    // --- Colores para el tema visual ---
    private final Color COLOR_FONDO_PRINCIPAL = new Color(40, 42, 54);
    private final Color COLOR_FONDO_SECUNDARIO = new Color(68, 71, 90);
    private final Color COLOR_DETALLES_NARANJA = new Color(255, 184, 108);
    private final Color COLOR_DETALLES_VERDE = new Color(80, 250, 123);
    private final Color COLOR_TEXTO_NORMAL = new Color(248, 248, 242);
    private final Color COLOR_TEXTO_COMENTARIO = new Color(98, 114, 164);
    private final Color COLOR_BOTON_NORMAL = new Color(98, 114, 164);
    private final Color COLOR_BOTON_HOVER = COLOR_DETALLES_NARANJA;
    private final Color COLOR_BOTON_DETENER = new Color(255, 85, 85);
    private final Color COLOR_BOTON_DETENER_HOVER = new Color(255, 121, 198);

    // --- Componentes GUI ---
    private GuiOutput consola; // Panel para mostrar logs y eventos
    private JButton btnEjecutarCiclo, btnEjecutarContinuo, btnDetenerContinuo, btnSalir, btnAgregarProceso, btnGuardarEstado, btnCargarEstado, btnMostrarMetricas;
    private JTextField velocidadTextField; // Campo para ingresar la velocidad de simulación
    private JButton btnAplicarVelocidad; // Botón para aplicar la velocidad
    private JComboBox<String> selectorAlgoritmo; // Selector de algoritmo de planificación
    private Simulador simulador; // Referencia al núcleo lógico del simulador
    private EventDisplay streamRedirector; // Redirige System.out/err a la consola GUI

    // Modelos para las listas de procesos en la GUI
    private DefaultListModel<String> modeloListaListos;
    private DefaultListModel<String> modeloListaBloqueados;
    private DefaultListModel<String> modeloListaListosSusp;
    private DefaultListModel<String> modeloListaBloqueadosSusp;
    private DefaultListModel<String> modeloListaTerminados;

    // Elementos de visualización de estado
    private JTextArea areaInfoProceso; // Muestra detalles del PCB seleccionado
    private JLabel labelProcesoCPU; // Muestra el proceso en ejecución
    private JLabel labelModoOperacion; // Kernel o Usuario
    private JLabel labelTiempoSimulacion; // Tiempo simulado transcurrido
    private JLabel labelInfoMemoria; // Uso de memoria simulada
    private JLabel labelQuantumRestante; // Para Round Robin

    // Control para la ejecución continua
    private Thread hiloSimulacionContinua;
    private final AtomicBoolean continuarSimulacion = new AtomicBoolean(false); // Flag para detener/reanudar

    /**
     * Constructor de la ventana principal.
     * @param simulador Instancia del simulador que controlará la GUI.
     */
    public SimuladorPrincipal(Simulador simulador) {
        this.simulador = simulador;
        configurarVentana();
        crearComponentes();
        streamRedirector = new EventDisplay(consola);
        agregarEventos();
        mostrarBienvenida();
        actualizarDashboardInicial();
    }

    /** Configura propiedades básicas de la ventana JFrame. */
    private void configurarVentana() {
        setTitle("Simulador Planificación SO (Adrián Vegas) - Rediseñado v2");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Se maneja el cierre manualmente
        setSize(1400, 900);
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_FONDO_PRINCIPAL);
        setLayout(new BorderLayout(10, 10));

        // Listener para manejar el cierre de la ventana correctamente
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                detenerSimulacionContinua();
                if (streamRedirector != null) { streamRedirector.restoreSystemStreams(); }
                simulador.cerrarSimulador(); // Llama al cierre lógico del simulador (logs, métricas)
                System.out.println("Cerrando GUI y Simulador.");
                dispose(); // Cierra la ventana Swing
                System.exit(0); // Termina la aplicación
            }
        });
    }

    /** Crea y organiza todos los componentes visuales de la interfaz. */
    private void crearComponentes() {
        JPanel panelSuperior = new JPanel(new BorderLayout(10, 5));
        panelSuperior.setBackground(COLOR_FONDO_PRINCIPAL);
        panelSuperior.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        panelSuperior.add(crearPanelTitulo(), BorderLayout.CENTER);
        panelSuperior.add(crearPanelCPU(), BorderLayout.EAST);

        JPanel panelCentral = new JPanel(new GridBagLayout());
        panelCentral.setBackground(COLOR_FONDO_PRINCIPAL);
        panelCentral.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH; gbc.insets = new Insets(5, 5, 5, 5);

        JPanel panelListos = crearPanelColaVertical("Listos", modeloListaListos = new DefaultListModel<>());
        JPanel panelListosSusp = crearPanelColaVertical("Listos Suspendidos", modeloListaListosSusp = new DefaultListModel<>());
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.18; gbc.weighty = 0.5; panelCentral.add(panelListos, gbc);
        gbc.gridy = 1; panelCentral.add(panelListosSusp, gbc);

        consola = new GuiOutput();
        JScrollPane scrollConsola = new JScrollPane(consola);
        scrollConsola.setBorder(crearBordeEstilizado("Log de Simulación"));
        scrollConsola.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollConsola.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridheight = 2; gbc.weightx = 0.44; gbc.weighty = 1.0; panelCentral.add(scrollConsola, gbc);
        gbc.gridheight = 1;

        JPanel panelBloqueados = crearPanelColaVertical("Bloqueados", modeloListaBloqueados = new DefaultListModel<>());
        JPanel panelBloqueadosSusp = crearPanelColaVertical("Bloqueados Suspendidos", modeloListaBloqueadosSusp = new DefaultListModel<>());
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.18; gbc.weighty = 0.5; panelCentral.add(panelBloqueados, gbc);
        gbc.gridy = 1; panelCentral.add(panelBloqueadosSusp, gbc);

        JPanel panelInferior = new JPanel(new GridBagLayout());
        panelInferior.setBackground(COLOR_FONDO_PRINCIPAL);
        panelInferior.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        GridBagConstraints gbcInf = new GridBagConstraints();
        gbcInf.fill = GridBagConstraints.BOTH; gbcInf.insets = new Insets(5, 5, 5, 5);

        JPanel panelInfo = crearPanelInfoPCB();
        gbcInf.gridx = 0; gbcInf.gridy = 0; gbcInf.gridheight = 2; gbcInf.weightx = 0.3; gbcInf.weighty = 1.0; panelInferior.add(panelInfo, gbcInf);
        gbcInf.gridheight = 1;

        JPanel panelControles = crearPanelControles();
        gbcInf.gridx = 1; gbcInf.gridy = 0; gbcInf.gridheight = 2; gbcInf.weightx = 0.4; panelInferior.add(panelControles, gbcInf);
        gbcInf.gridheight = 1;

        JPanel panelTerminados = crearPanelColaVertical("Terminados", modeloListaTerminados = new DefaultListModel<>());
        gbcInf.gridx = 2; gbcInf.gridy = 0; gbcInf.gridheight = 2; gbcInf.weightx = 0.3; panelInferior.add(panelTerminados, gbcInf);

        add(panelSuperior, BorderLayout.NORTH); add(panelCentral, BorderLayout.CENTER); add(panelInferior, BorderLayout.SOUTH);
    }

    /** Crea el panel superior con el título de la aplicación. */
    private JPanel crearPanelTitulo() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER)); panel.setBackground(COLOR_FONDO_PRINCIPAL); panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0)); JLabel titulo = new JLabel("Simulador Planificación SO (Adrián Vegas)"); titulo.setFont(new Font("Segoe UI", Font.BOLD, 24)); titulo.setForeground(COLOR_DETALLES_NARANJA); panel.add(titulo); return panel;
    }

    /** Crea el panel que muestra la información del estado actual de la CPU. */
    private JPanel crearPanelCPU() {
        JPanel panel = new JPanel(new GridBagLayout()); GridBagConstraints gbcCPU = new GridBagConstraints(); panel.setOpaque(false); panel.setBorder(crearBordeEstilizado("CPU")); gbcCPU.fill = GridBagConstraints.HORIZONTAL; gbcCPU.insets = new Insets(2, 5, 2, 5); gbcCPU.anchor = GridBagConstraints.WEST; labelTiempoSimulacion = new JLabel("Tiempo: 0ms"); estilizarLabel(labelTiempoSimulacion, COLOR_DETALLES_VERDE, 14, Font.BOLD); gbcCPU.gridx = 0; gbcCPU.gridy = 0; gbcCPU.gridwidth = 1; panel.add(labelTiempoSimulacion, gbcCPU); labelInfoMemoria = new JLabel("Mem: 0/0"); estilizarLabel(labelInfoMemoria, COLOR_TEXTO_COMENTARIO, 12, Font.PLAIN); gbcCPU.gridx = 1; gbcCPU.gridy = 0; gbcCPU.anchor = GridBagConstraints.EAST; panel.add(labelInfoMemoria, gbcCPU); gbcCPU.anchor = GridBagConstraints.WEST; labelProcesoCPU = new JLabel("IDLE"); estilizarLabel(labelProcesoCPU, COLOR_TEXTO_NORMAL, 16, Font.BOLD); labelProcesoCPU.setHorizontalAlignment(SwingConstants.CENTER); gbcCPU.gridx = 0; gbcCPU.gridy = 1; gbcCPU.gridwidth = 2; gbcCPU.fill = GridBagConstraints.HORIZONTAL; panel.add(labelProcesoCPU, gbcCPU); gbcCPU.gridwidth = 1; gbcCPU.fill = GridBagConstraints.HORIZONTAL; labelModoOperacion = new JLabel("Modo: Kernel (SO)"); estilizarLabel(labelModoOperacion, COLOR_TEXTO_COMENTARIO, 11, Font.ITALIC); gbcCPU.gridx = 0; gbcCPU.gridy = 2; panel.add(labelModoOperacion, gbcCPU); labelQuantumRestante = new JLabel("Q: -"); estilizarLabel(labelQuantumRestante, COLOR_TEXTO_COMENTARIO, 11, Font.ITALIC); labelQuantumRestante.setVisible(false); gbcCPU.gridx = 1; gbcCPU.gridy = 2; gbcCPU.anchor = GridBagConstraints.EAST; panel.add(labelQuantumRestante, gbcCPU); panel.setPreferredSize(new Dimension(280, 100)); return panel;
    }

    /** Crea el panel que muestra la información detallada (PCB) del proceso seleccionado. */
    private JPanel crearPanelInfoPCB() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false); panel.setBorder(crearBordeEstilizado("Info Proceso Seleccionado (PCB)")); areaInfoProceso = new JTextArea("Selecciona un proceso de las listas..."); areaInfoProceso.setEditable(false); areaInfoProceso.setBackground(COLOR_FONDO_SECUNDARIO); areaInfoProceso.setForeground(COLOR_TEXTO_NORMAL); areaInfoProceso.setFont(new Font("Consolas", Font.PLAIN, 12)); areaInfoProceso.setLineWrap(true); areaInfoProceso.setWrapStyleWord(true); areaInfoProceso.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); JScrollPane scrollPane = new JScrollPane(areaInfoProceso); scrollPane.setBorder(null); panel.add(scrollPane, BorderLayout.CENTER); return panel;
    }

    /**
     * Crea un panel con una lista para mostrar procesos en una cola específica.
     * @param titulo Título del panel (ej. "Listos", "Bloqueados").
     * @param modelo El DefaultListModel asociado a la JList.
     * @return El JPanel creado.
     */
    private JPanel crearPanelColaVertical(String titulo, DefaultListModel<String> modelo) {
        JList<String> lista = new JList<>(modelo); lista.setBackground(COLOR_FONDO_SECUNDARIO); lista.setForeground(COLOR_TEXTO_NORMAL); lista.setFont(new Font("Consolas", Font.PLAIN, 12)); lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); lista.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        // Listener para actualizar el panel de Info PCB al seleccionar un proceso
        lista.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) { String selectedValue = lista.getSelectedValue(); if (selectedValue != null) { try { int procesoId = Integer.parseInt(selectedValue.split(" - ")[0]); Proceso p = simulador.buscarProcesoPorIdEnTodasLasListas(procesoId); mostrarInfoPCBDetallada(p); } catch (Exception ex) { areaInfoProceso.setText("Error al obtener info:\n" + selectedValue); } } } });
        JScrollPane scrollPane = new JScrollPane(lista); scrollPane.setBorder(null);
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false); panel.setBorder(crearBordeEstilizado(titulo)); panel.add(scrollPane, BorderLayout.CENTER); return panel;
    }

    /** Crea el panel inferior que contiene los botones de control, selector y campo de velocidad. */
    private JPanel crearPanelControles() {
        JPanel panel = new JPanel(new GridBagLayout()); panel.setOpaque(false); GridBagConstraints gbcCtrl = new GridBagConstraints(); gbcCtrl.fill = GridBagConstraints.HORIZONTAL; gbcCtrl.insets = new Insets(4, 6, 4, 6); gbcCtrl.weightx = 1.0;
        btnEjecutarCiclo = crearBotonEstilizado("Ejecutar Ciclo"); btnEjecutarContinuo = crearBotonEstilizado("Ejecutar Continuo"); btnDetenerContinuo = crearBotonEstilizado("Detener Continuo"); personalizarBotonDetener(btnDetenerContinuo, false); gbcCtrl.gridx = 0; gbcCtrl.gridy = 0; gbcCtrl.gridwidth = 1; panel.add(btnEjecutarCiclo, gbcCtrl); gbcCtrl.gridx = 1; gbcCtrl.gridy = 0; panel.add(btnEjecutarContinuo, gbcCtrl); gbcCtrl.gridx = 2; gbcCtrl.gridy = 0; panel.add(btnDetenerContinuo, gbcCtrl);
        JLabel labelAlgoritmo = new JLabel("Planificador:", SwingConstants.LEFT); estilizarLabel(labelAlgoritmo, COLOR_TEXTO_NORMAL, 12, Font.PLAIN); selectorAlgoritmo = new JComboBox<>(new String[]{ "1. FCFS (No Expropiativo)", "2. SJF (No Expropiativo)", "3. SRT (SJF Expropiativo)", "4. Round Robin (Expropiativo)", "5. Prioridad (No Expropiativa)", "6. Prioridad (Expropiativa)" }); selectorAlgoritmo.setFont(new Font("Segoe UI", Font.PLAIN, 12)); selectorAlgoritmo.setBackground(COLOR_FONDO_SECUNDARIO); selectorAlgoritmo.setForeground(COLOR_TEXTO_NORMAL); gbcCtrl.gridx = 0; gbcCtrl.gridy = 1; gbcCtrl.gridwidth = 1; gbcCtrl.weightx = 0; gbcCtrl.fill = GridBagConstraints.NONE; gbcCtrl.anchor = GridBagConstraints.EAST; panel.add(labelAlgoritmo, gbcCtrl); gbcCtrl.gridx = 1; gbcCtrl.gridy = 1; gbcCtrl.gridwidth = 2; gbcCtrl.weightx = 1; gbcCtrl.fill = GridBagConstraints.HORIZONTAL; gbcCtrl.anchor = GridBagConstraints.WEST; panel.add(selectorAlgoritmo, gbcCtrl);
        JLabel labelVelocidad = new JLabel("Velocidad (ms):", SwingConstants.LEFT); estilizarLabel(labelVelocidad, COLOR_TEXTO_NORMAL, 12, Font.PLAIN); velocidadTextField = new JTextField(String.valueOf(simulador.getConfig().getDuracionCicloMs()), 5); velocidadTextField.setFont(new Font("Consolas", Font.PLAIN, 12)); velocidadTextField.setBackground(COLOR_FONDO_SECUNDARIO); velocidadTextField.setForeground(COLOR_TEXTO_NORMAL); velocidadTextField.setCaretColor(COLOR_DETALLES_VERDE); velocidadTextField.setBorder(BorderFactory.createCompoundBorder( BorderFactory.createLineBorder(COLOR_TEXTO_COMENTARIO), BorderFactory.createEmptyBorder(3, 5, 3, 5) )); btnAplicarVelocidad = crearBotonEstilizado("Aplicar"); btnAplicarVelocidad.setPreferredSize(new Dimension(80, btnAplicarVelocidad.getPreferredSize().height)); btnAplicarVelocidad.setBorder(new CompoundBorder(new LineBorder(COLOR_FONDO_PRINCIPAL), new EmptyBorder(4, 10, 4, 10))); gbcCtrl.gridx = 0; gbcCtrl.gridy = 2; gbcCtrl.gridwidth = 1; gbcCtrl.weightx = 0; gbcCtrl.fill = GridBagConstraints.NONE; gbcCtrl.anchor = GridBagConstraints.EAST; panel.add(labelVelocidad, gbcCtrl); gbcCtrl.gridx = 1; gbcCtrl.gridy = 2; gbcCtrl.gridwidth = 1; gbcCtrl.weightx = 1; gbcCtrl.fill = GridBagConstraints.HORIZONTAL; gbcCtrl.anchor = GridBagConstraints.WEST; panel.add(velocidadTextField, gbcCtrl); gbcCtrl.gridx = 2; gbcCtrl.gridy = 2; gbcCtrl.gridwidth = 1; gbcCtrl.weightx = 0; gbcCtrl.fill = GridBagConstraints.NONE; gbcCtrl.anchor = GridBagConstraints.WEST; panel.add(btnAplicarVelocidad, gbcCtrl);
        btnAgregarProceso = crearBotonEstilizado("Añadir Proceso"); btnGuardarEstado = crearBotonEstilizado("Guardar Estado"); btnCargarEstado = crearBotonEstilizado("Cargar Estado"); gbcCtrl.gridx = 0; gbcCtrl.gridy = 3; gbcCtrl.gridwidth = 1; gbcCtrl.fill = GridBagConstraints.HORIZONTAL; gbcCtrl.anchor = GridBagConstraints.CENTER; panel.add(btnAgregarProceso, gbcCtrl); gbcCtrl.gridx = 1; gbcCtrl.gridy = 3; panel.add(btnGuardarEstado, gbcCtrl); gbcCtrl.gridx = 2; gbcCtrl.gridy = 3; panel.add(btnCargarEstado, gbcCtrl);
        btnMostrarMetricas = crearBotonEstilizado("Ver Métricas"); btnSalir = crearBotonEstilizado("Salir"); gbcCtrl.gridx = 0; gbcCtrl.gridy = 4; panel.add(btnMostrarMetricas, gbcCtrl); gbcCtrl.gridx = 1; gbcCtrl.gridy = 4; panel.add(new JLabel(""), gbcCtrl); gbcCtrl.gridx = 2; gbcCtrl.gridy = 4; panel.add(btnSalir, gbcCtrl);
        return panel;
    }

    // --- Métodos Auxiliares de Estilo ---

    /** Crea un borde estándar con título para los paneles. */
    private Border crearBordeEstilizado(String titulo) { Border linea = BorderFactory.createLineBorder(COLOR_FONDO_SECUNDARIO, 1); Border tituloBorde = BorderFactory.createTitledBorder( linea, titulo, javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new Font("Segoe UI", Font.BOLD, 12), COLOR_DETALLES_NARANJA); Border padding = BorderFactory.createEmptyBorder(5, 5, 5, 5); return new CompoundBorder(tituloBorde, padding); }
    /** Aplica un estilo estándar a un JLabel. */
    private void estilizarLabel(JLabel label, Color foreground, int fontSize, int fontStyle) { label.setForeground(foreground); label.setFont(new Font("Segoe UI", fontStyle, fontSize)); }
    /** Crea un JButton con el estilo visual definido para la aplicación. */
    private JButton crearBotonEstilizado(String texto) { JButton boton = new JButton(texto); boton.setFont(new Font("Segoe UI", Font.BOLD, 12)); boton.setBackground(COLOR_BOTON_NORMAL); boton.setForeground(COLOR_TEXTO_NORMAL); boton.setFocusPainted(false); Border line = new LineBorder(COLOR_FONDO_PRINCIPAL); Border padding = new EmptyBorder(5, 15, 5, 15); boton.setBorder(new CompoundBorder(line, padding)); boton.setCursor(new Cursor(Cursor.HAND_CURSOR)); boton.addMouseListener(new MouseAdapter() { @Override public void mouseEntered(MouseEvent e) { if (boton == btnDetenerContinuo && !btnDetenerContinuo.isEnabled()) { boton.setBackground(COLOR_BOTON_DETENER_HOVER); } else if (boton != btnDetenerContinuo || btnDetenerContinuo.isEnabled()) { boton.setBackground(COLOR_BOTON_HOVER); boton.setForeground(COLOR_FONDO_PRINCIPAL); } } @Override public void mouseExited(MouseEvent e) { if (boton == btnDetenerContinuo) { personalizarBotonDetener(btnDetenerContinuo, !btnDetenerContinuo.isEnabled()); } else { boton.setBackground(COLOR_BOTON_NORMAL); boton.setForeground(COLOR_TEXTO_NORMAL); } } }); return boton; }
    /** Personaliza la apariencia del botón "Detener Continuo" según esté activo o inactivo. */
    private void personalizarBotonDetener(JButton boton, boolean activo) { if (activo) { boton.setBackground(COLOR_BOTON_DETENER); boton.setEnabled(true); boton.setForeground(COLOR_TEXTO_NORMAL); } else { boton.setBackground(COLOR_FONDO_SECUNDARIO); boton.setEnabled(false); boton.setForeground(COLOR_TEXTO_COMENTARIO); } }

    /** Registra los listeners para los eventos de los botones y otros controles. */
    private void agregarEventos() {
        btnEjecutarCiclo.addActionListener(e -> { detenerSimulacionContinua(); ejecutarUnCiclo(); });
        btnEjecutarContinuo.addActionListener(e -> iniciarSimulacionContinua());
        btnDetenerContinuo.addActionListener(e -> detenerSimulacionContinua());
        selectorAlgoritmo.addActionListener(e -> cambiarPlanificadorSeleccionado());
        btnAgregarProceso.addActionListener(e -> mostrarFormularioProceso());
        btnGuardarEstado.addActionListener(e -> { detenerSimulacionContinua(); if (simulador.guardarEstado()) consola.agregarLinea("Estado guardado.", COLOR_DETALLES_VERDE); else consola.agregarLinea("Error al guardar.", COLOR_BOTON_DETENER); });
        btnCargarEstado.addActionListener(e -> cargarEstadoSimulador());
        btnMostrarMetricas.addActionListener(e -> { detenerSimulacionContinua(); mostrarMetricasEnConsolaYGrafica(); });
        btnSalir.addActionListener(e -> { this.dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING)); });

        // Listener para aplicar la velocidad ingresada en el JTextField
        ActionListener aplicarVelocidadAction = e -> {
            try {
                String txt = velocidadTextField.getText().trim();
                long nDur = Long.parseLong(txt);
                if (nDur > 0) {
                    simulador.getConfig().setDuracionCicloMs(nDur);
                    consola.agregarLinea("Velocidad: " + nDur + " ms/ciclo.", COLOR_DETALLES_VERDE);
                    Logger.log("Ciclo: " + nDur + "ms (GUI).");
                } else {
                    JOptionPane.showMessageDialog(this, "> 0 ms.", "Inválido", JOptionPane.WARNING_MESSAGE);
                    velocidadTextField.setText(String.valueOf(simulador.getConfig().getDuracionCicloMs()));
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Número inválido (ms).", "Error", JOptionPane.ERROR_MESSAGE);
                velocidadTextField.setText(String.valueOf(simulador.getConfig().getDuracionCicloMs()));
            }
        };
        btnAplicarVelocidad.addActionListener(aplicarVelocidadAction);
        velocidadTextField.addActionListener(aplicarVelocidadAction); // También al presionar Enter
    }

    /** Ejecuta un único ciclo de simulación y actualiza la interfaz. */
    private void ejecutarUnCiclo() { if (!simulador.quedanProcesos() && simulador.procesoActual == null) { consola.agregarLinea("Simulación finalizada.", COLOR_DETALLES_NARANJA); return; } simulador.ejecutarCicloSimulacion(); actualizarDashboard(); }

    /** Inicia la simulación continua en un hilo separado. */
    private void iniciarSimulacionContinua() {
        if (hiloSimulacionContinua != null && hiloSimulacionContinua.isAlive()) return;
        if (!simulador.quedanProcesos() && simulador.procesoActual == null) { consola.agregarLinea("No hay procesos.", COLOR_DETALLES_NARANJA); return; }
        continuarSimulacion.set(true); btnEjecutarContinuo.setEnabled(false); btnEjecutarCiclo.setEnabled(false); personalizarBotonDetener(btnDetenerContinuo, true); btnCargarEstado.setEnabled(false); btnGuardarEstado.setEnabled(false);
        consola.agregarLinea("Iniciando simulación continua...", COLOR_DETALLES_VERDE); streamRedirector.redirect(); // Activa redirección de System.out/err
        hiloSimulacionContinua = new Thread(() -> {
            try {
                while (continuarSimulacion.get() && (simulador.quedanProcesos() || simulador.procesoActual != null)) {
                    simulador.ejecutarCicloSimulacion(); // El ciclo incluye su propio sleep
                    SwingUtilities.invokeLater(this::actualizarDashboard); // Actualiza GUI en el EDT
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); System.out.println("Hilo sim interrumpido."); } else { System.err.println("Error sim continua: " + e.getMessage()); e.printStackTrace(); }
            } finally {
                // Asegura que los botones se restablezcan al finalizar/detener
                SwingUtilities.invokeLater(() -> { detenerSimulacionContinua(); consola.agregarLinea("Simulación continua finalizada/detenida.", COLOR_DETALLES_NARANJA); if (!simulador.quedanProcesos() && simulador.procesoActual == null) { consola.agregarLinea("Todos terminaron.", COLOR_DETALLES_VERDE); } });
            }
        });
        hiloSimulacionContinua.setName("SimuladorContinuoGUI"); hiloSimulacionContinua.start();
    }

    /** Detiene la simulación continua. */
    private void detenerSimulacionContinua() {
        continuarSimulacion.set(false);
        if (hiloSimulacionContinua != null && hiloSimulacionContinua.isAlive()) {
            hiloSimulacionContinua.interrupt();
            try { hiloSimulacionContinua.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        hiloSimulacionContinua = null;
        // Restablece el estado de los botones
        SwingUtilities.invokeLater(() -> { btnEjecutarContinuo.setEnabled(true); btnEjecutarCiclo.setEnabled(true); personalizarBotonDetener(btnDetenerContinuo, false); btnCargarEstado.setEnabled(true); btnGuardarEstado.setEnabled(true); System.out.println("Simulación detenida."); });
    }

    /** Cambia el algoritmo de planificación según la selección del JComboBox. */
    private void cambiarPlanificadorSeleccionado() {
        detenerSimulacionContinua(); String sel = (String) selectorAlgoritmo.getSelectedItem(); if (sel == null) return; Planificador np = null; int q = Main.DEFAULT_QUANTUM;
        try {
            String id = sel.split("\\.")[0].trim();
            switch (id) {
                case "1": np = new PlanificadorFCFS(); break;
                case "2": np = new PlanificadorSJF(); break;
                case "3": np = new PlanificadorSRT(); break;
                case "4": String qs = JOptionPane.showInputDialog(this, "Quantum RR:", String.valueOf(q)); if (qs != null && !qs.trim().isEmpty()) { try { int iq = Integer.parseInt(qs.trim()); if (iq > 0) q = iq; else JOptionPane.showMessageDialog(this, "Inválido.", "Warn", JOptionPane.WARNING_MESSAGE); } catch (NumberFormatException e) { JOptionPane.showMessageDialog(this, "Inválido.", "Error", JOptionPane.ERROR_MESSAGE); } } np = new PlanificadorRoundRobin(q); break;
                case "5": np = new PlanificadorPrioridadNoExpropiativa(); break;
                case "6": np = new PlanificadorPrioridadExpropiativa(); break;
                default: consola.agregarLinea("No reconocido.", COLOR_BOTON_DETENER); return;
            }
        } catch (Exception e) { consola.agregarLinea("Error: " + e.getMessage(), COLOR_BOTON_DETENER); e.printStackTrace(); return; }
        if (np != null) { simulador.setPlanificador(np); consola.agregarLinea("Planificador: " + np.getNombre(), COLOR_DETALLES_VERDE); actualizarDashboard(); }
    }

    /** Carga el estado previamente guardado del simulador. */
    private void cargarEstadoSimulador() {
        detenerSimulacionContinua(); streamRedirector.restoreSystemStreams(); // Restaura consola antes de cargar
        if (simulador.cargarEstado()) {
            consola.limpiar(); consola.agregarLinea("Estado cargado. Reiniciando...", COLOR_DETALLES_VERDE);
            simulador.asignarSemaforoAProcesos(); simulador.reiniciarHilosPostCarga();
            actualizarDashboardInicial(); // Actualiza GUI con el estado cargado
            // Actualiza el selector de algoritmo en la GUI para reflejar el cargado
            Planificador p = simulador.getPlanificador();
            if (p != null) { for (int i = 0; i < selectorAlgoritmo.getItemCount(); i++) { String item = selectorAlgoritmo.getItemAt(i); String nItem = item.replaceAll("^\\d+\\.\\s*|\\s*\\(.*?\\)$", "").trim(); String nCargado = p.getNombre().replaceAll("^\\d+\\.\\s*|\\s*\\(.*?Q:.*?\\)$", "").trim(); if (nItem.equalsIgnoreCase(nCargado)) { selectorAlgoritmo.setSelectedIndex(i); break; } } }
            consola.agregarLinea("Listo.", COLOR_DETALLES_VERDE);
        } else { consola.agregarLinea("Fallo al cargar.", COLOR_BOTON_DETENER); }
    }

    /**
     * Muestra un diálogo rediseñado para que el usuario ingrese los datos de un nuevo proceso.
     */
    private void mostrarFormularioProceso() {
        detenerSimulacionContinua();

        JTextField nombreField = new JTextField(15);
        JSpinner instruccionesSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 10)); // Valor inicial, min, max, step
        JSpinner prioridadSpinner = new JSpinner(new SpinnerNumberModel(Main.DEFAULT_PRIORITY, 1, 10, 1));
        JCheckBox esIoBoundCheck = new JCheckBox("Proceso I/O Bound");
        JSpinner ciclosIntSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 1000, 1));
        JSpinner ciclosSatSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));

        // Panel para los campos de E/S, inicialmente oculto y deshabilitado
        JPanel panelIO = new JPanel(new GridBagLayout());
        panelIO.setOpaque(false);
        GridBagConstraints gbcIO = new GridBagConstraints();
        gbcIO.insets = new Insets(2, 5, 2, 5); gbcIO.anchor = GridBagConstraints.WEST;
        gbcIO.gridx = 0; gbcIO.gridy = 0; panelIO.add(new JLabel("Ciclos CPU p/ Interrupción:"), gbcIO);
        gbcIO.gridx = 1; gbcIO.gridy = 0; gbcIO.fill = GridBagConstraints.HORIZONTAL; panelIO.add(ciclosIntSpinner, gbcIO);
        gbcIO.gridx = 0; gbcIO.gridy = 1; gbcIO.fill = GridBagConstraints.NONE; panelIO.add(new JLabel("Ciclos Duración E/S:"), gbcIO);
        gbcIO.gridx = 1; gbcIO.gridy = 1; gbcIO.fill = GridBagConstraints.HORIZONTAL; panelIO.add(ciclosSatSpinner, gbcIO);
        panelIO.setVisible(false); setPanelEnabled(panelIO, false);

        // Muestra/oculta y habilita/deshabilita el panel de E/S al marcar/desmarcar el checkbox
        esIoBoundCheck.addActionListener(e -> { boolean selected = esIoBoundCheck.isSelected(); panelIO.setVisible(selected); setPanelEnabled(panelIO, selected); });

        // Panel principal del formulario con BoxLayout vertical
        JPanel panelForm = new JPanel(); panelForm.setLayout(new BoxLayout(panelForm, BoxLayout.Y_AXIS)); panelForm.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panelForm.add(crearCampoFormulario("Nombre:", nombreField));
        panelForm.add(Box.createVerticalStrut(5)); // Espaciador vertical
        panelForm.add(crearCampoFormulario("Instrucciones:", instruccionesSpinner));
        panelForm.add(Box.createVerticalStrut(5));
        panelForm.add(crearCampoFormulario("Prioridad (1-10):", prioridadSpinner));
        panelForm.add(Box.createVerticalStrut(10));
        panelForm.add(esIoBoundCheck);
        panelForm.add(Box.createVerticalStrut(5));
        panelForm.add(panelIO);

        // Muestra el diálogo modal
        int resultado = JOptionPane.showConfirmDialog(this, panelForm, "Añadir Nuevo Proceso", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        // Si el usuario presiona OK, intenta crear el proceso
        if (resultado == JOptionPane.OK_OPTION) {
            try {
                String nombre = nombreField.getText().trim();
                if (nombre.isEmpty()) throw new IllegalArgumentException("Nombre vacío.");
                int instrucciones = (Integer) instruccionesSpinner.getValue();
                int prioridad = (Integer) prioridadSpinner.getValue();
                boolean esIoBound = esIoBoundCheck.isSelected();
                Proceso nuevoProceso;
                if (esIoBound) {
                    int ciclosInt = (Integer) ciclosIntSpinner.getValue();
                    int ciclosSat = (Integer) ciclosSatSpinner.getValue();
                    nuevoProceso = new Proceso(nombre, instrucciones, prioridad, ciclosInt, ciclosSat);
                } else {
                    nuevoProceso = new Proceso(nombre, instrucciones, prioridad);
                }
                simulador.agregarProceso(nuevoProceso);
                consola.agregarLinea("Proceso '" + nombre + "' añadido.", COLOR_DETALLES_VERDE);
                actualizarDashboard();
            } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error de Entrada", JOptionPane.ERROR_MESSAGE); } catch (Exception e) { JOptionPane.showMessageDialog(this, "Error inesperado al crear proceso: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); e.printStackTrace(); }
        }
    }

    /** Método auxiliar para crear una fila (JLabel + JComponent) en el formulario. */
    private JPanel crearCampoFormulario(String labelText, JComponent component) { JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); panel.setOpaque(false); JLabel label = new JLabel(labelText); label.setPreferredSize(new Dimension(150, label.getPreferredSize().height)); panel.add(label); panel.add(component); return panel; }
    /** Habilita o deshabilita recursivamente todos los componentes dentro de un JPanel. */
    private void setPanelEnabled(JPanel panel, boolean enabled) { panel.setEnabled(enabled); for (Component comp : panel.getComponents()) { comp.setEnabled(enabled); if (comp instanceof JPanel) { setPanelEnabled((JPanel) comp, enabled); } } }

    /** Calcula y muestra las métricas de rendimiento en la consola y genera una gráfica. */
    private void mostrarMetricasEnConsolaYGrafica() {
        consola.agregarSeparador(); consola.agregarLinea("Calculando Métricas...", COLOR_TEXTO_COMENTARIO);
        // Captura la salida estándar y de error del método calcularYMostrarMetricas
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos); PrintStream oldOut = System.out; PrintStream oldErr = System.err; System.setOut(ps); System.setErr(ps);
        try { simulador.calcularYMostrarMetricas(); } catch (Exception e) { ps.println("\nError métricas: " + e.getMessage()); e.printStackTrace(ps); } finally { System.out.flush(); System.err.flush(); System.setOut(oldOut); System.setErr(oldErr); } // Restaura streams
        consola.agregarLinea(baos.toString(), COLOR_DETALLES_NARANJA); consola.agregarSeparador(); // Muestra salida capturada

        // Intenta obtener métricas calculadas y generar la gráfica
        try { double tRetornoProm = simulador.getTiempoRetornoPromedioCalculado(); double tEsperaProm = simulador.getTiempoEsperaPromedioCalculado(); String nombreAlgoritmo = (simulador.getPlanificador() != null) ? simulador.getPlanificador().getNombre() : "N/A"; if (tRetornoProm >= 0 && tEsperaProm >= 0 && simulador.getListaProcesosCompletados().size() > 0) { mostrarGraficaResultados(nombreAlgoritmo, tRetornoProm, tEsperaProm); } else { consola.agregarLinea("No hay datos para gráfica.", COLOR_TEXTO_COMENTARIO); } } catch (Exception e) { consola.agregarLinea("Error datos gráfica: " + e.getMessage(), COLOR_BOTON_DETENER); e.printStackTrace(System.err); }
    }

    /** Muestra una ventana emergente con la gráfica de resultados usando JFreeChart. */
    private void mostrarGraficaResultados(String nombreAlgoritmo, double tRetornoProm, double tEsperaProm) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset(); String nombreAlgoritmoCorto = nombreAlgoritmo.replaceAll("^\\d+\\.\\s*|\\s*\\(.*?\\)$", "").trim(); dataset.addValue(tRetornoProm, "T. Retorno Prom.", nombreAlgoritmoCorto); dataset.addValue(tEsperaProm, "T. Espera Prom.", nombreAlgoritmoCorto);
        JFreeChart barChart = ChartFactory.createBarChart("Métricas: " + nombreAlgoritmoCorto, "Métrica", "Tiempo (ms)", dataset, PlotOrientation.VERTICAL, true, true, false);
        // Aplica estilo visual a la gráfica
        barChart.setBackgroundPaint(COLOR_FONDO_PRINCIPAL); barChart.getTitle().setPaint(COLOR_DETALLES_NARANJA); if (barChart.getLegend() != null) { barChart.getLegend().setBackgroundPaint(COLOR_FONDO_SECUNDARIO); barChart.getLegend().setItemPaint(COLOR_TEXTO_NORMAL); }
        CategoryPlot plot = barChart.getCategoryPlot(); plot.setBackgroundPaint(COLOR_FONDO_SECUNDARIO); plot.setRangeGridlinePaint(COLOR_TEXTO_COMENTARIO); plot.setDomainGridlinesVisible(false); plot.getDomainAxis().setLabelPaint(COLOR_TEXTO_NORMAL); plot.getDomainAxis().setTickLabelPaint(COLOR_TEXTO_NORMAL); plot.getRangeAxis().setLabelPaint(COLOR_TEXTO_NORMAL); plot.getRangeAxis().setTickLabelPaint(COLOR_TEXTO_NORMAL);
        BarRenderer renderer = (BarRenderer) plot.getRenderer(); renderer.setSeriesPaint(0, COLOR_DETALLES_VERDE); renderer.setSeriesPaint(1, COLOR_DETALLES_NARANJA); renderer.setDrawBarOutline(false); renderer.setMaximumBarWidth(0.10);
        // Muestra la gráfica en una nueva ventana
        ChartPanel chartPanel = new ChartPanel(barChart); chartPanel.setPreferredSize(new Dimension(600, 450)); JFrame popupFrame = new JFrame("Reporte: " + nombreAlgoritmoCorto); popupFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); popupFrame.setContentPane(chartPanel); popupFrame.pack(); popupFrame.setLocationRelativeTo(this); popupFrame.setVisible(true);
    }

    /** Actualiza todos los componentes de la GUI para reflejar el estado actual del simulador. */
    private void actualizarDashboard() { if (simulador == null) return; SwingUtilities.invokeLater(() -> { labelTiempoSimulacion.setText("Tiempo: " + simulador.getTiempoSimulacion() + "ms"); labelInfoMemoria.setText(String.format("Mem: %d/%d", simulador.getColaListos().size() + simulador.getMapaProcesosBloqueados().size(), Simulador.UMBRAL_PROCESOS_MEMORIA)); Proceso enCpu = simulador.procesoActual; if (enCpu != null) { labelProcesoCPU.setText(enCpu.getId() + " - " + enCpu.getNombre()); labelModoOperacion.setText("Modo: Usuario"); labelModoOperacion.setForeground(COLOR_DETALLES_VERDE); } else { labelProcesoCPU.setText("IDLE"); labelModoOperacion.setText("Modo: Kernel (SO)"); labelModoOperacion.setForeground(COLOR_DETALLES_NARANJA); } if (simulador.getPlanificador() instanceof PlanificadorRoundRobin rr) { labelQuantumRestante.setText("Q: " + simulador.getCiclosQuantum()); labelQuantumRestante.setVisible(true); } else { labelQuantumRestante.setVisible(false); } actualizarListaGUI(modeloListaListos, simulador.getColaListos().toArray()); actualizarListaGUI(modeloListaBloqueados, simulador.getMapaProcesosBloqueados().values().toArray(new Proceso[0])); actualizarListaGUI(modeloListaListosSusp, simulador.getColaListosSuspendidos().toArray()); actualizarListaGUI(modeloListaBloqueadosSusp, simulador.getColaBloqueadosSuspendidos().toArray()); actualizarListaGUI(modeloListaTerminados, simulador.getListaProcesosCompletados().toArray(new Proceso[0])); }); }

    /**
     * Actualiza eficientemente una JList (modelo) comparando con un array de procesos.
     * Evita limpiar y re-agregar todo, mejorando el rendimiento visual.
     */
    private void actualizarListaGUI(DefaultListModel<String> modelo, Proceso[] procesos) { List<String> nuevos = new ArrayList<>(); for (Proceso p : procesos) { if(p!=null) nuevos.add(p.getId() + " - " + p.getNombre()); } for (int i = modelo.getSize() - 1; i >= 0; i--) { if (!nuevos.contains(modelo.getElementAt(i))) modelo.removeElementAt(i); } for (String s : nuevos) { if (!modelo.contains(s)) modelo.addElement(s); } }

    /** Muestra la información detallada del PCB de un proceso en el JTextArea. */
    private void mostrarInfoPCBDetallada(Proceso p) { if (p == null) { areaInfoProceso.setText("Selecciona un proceso..."); return; } String io = ""; if (p.getTipo() == Proceso.TipoBound.I_O_BOUND) { io = String.format("\nCiclos Int E/S: %d\nCiclos Sat E/S: %d\nCont CPU: %d\nCont E/S: %d", p.getCiclosParaInterrupcion(), p.getCiclosParaSatisfacerIO(), p.getContadorCiclos(), p.getContadorIOCiclos()); } String info = String.format( "ID: %d | Nombre: %s\n"+ "Estado: %s %s\n" + "Prioridad: %d | Tipo: %s\n" + "--------------------\n" + "PC: %d | IR: %s | R_A: %d\n" + "Instr. Rest: %d / %d\n" + "--------------------\n" + "T. Llegada: %d ms\n" + "T. Final: %d ms\n" + "T. Bloq Total: %d ms\n" + "T. Espera Total: %d ms\n" + "T. Retorno: %d ms\n" + "T. Respuesta: %d ms" + "%s", p.getId(), p.getNombre(), p.getEstado(), (p.isSuspendidoGUI() ? "[S]" : ""), p.getPrioridad(), p.getTipo(), p.getProgramCounter(), p.getRegistroInstruccion(), p.getRegistroA(), p.getInstruccionesRestantes(), p.getInstruccionesTotales(), p.getTiempoLlegada(), p.getTiempoFinalizacion(), p.getTiempoTotalBloqueado(), p.getTiempoTotalEsperandoListo(), p.getTiempoRetorno(), p.getTiempoRespuesta(), io ); areaInfoProceso.setText(info); areaInfoProceso.setCaretPosition(0); }

    /** Actualiza la GUI al iniciar la aplicación o después de cargar un estado. */
    private void actualizarDashboardInicial() { velocidadTextField.setText(String.valueOf(simulador.getConfig().getDuracionCicloMs())); actualizarDashboard(); }

    /** Muestra un mensaje de bienvenida en la consola de la GUI. */
    private void mostrarBienvenida() { consola.agregarSeparador(); consola.agregarLinea("SIMULADOR PLANIFICACIÓN SO - Adrián Vegas (Rediseñado)", COLOR_DETALLES_NARANJA); consola.agregarSeparador(); consola.agregarLinea("Estado inicial cargado. Selecciona una acción.", COLOR_TEXTO_COMENTARIO); consola.agregarLinea(""); }

    /** Punto de entrada principal de la aplicación. Crea el simulador y lanza la GUI. */
    public static void main(String[] args) { ConfiguracionSimulacion config = ConfiguracionSimulacion.cargarConfiguracion(); Planificador planificadorInicial = new PlanificadorFCFS(); Simulador sim = new Simulador(config, planificadorInicial); SwingUtilities.invokeLater(() -> { SimuladorPrincipal gui = new SimuladorPrincipal(sim); gui.setVisible(true); }); }
}