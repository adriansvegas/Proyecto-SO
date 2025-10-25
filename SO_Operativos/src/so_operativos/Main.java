/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package so_operativos;

import java.util.InputMismatchException;
import java.util.Scanner;
/**
 *
 * @author Edgar
 */



public class Main {
    public static final int DEFAULT_PRIORITY = 5;
    public static final int DEFAULT_QUANTUM = 20;

    public static void main(String[] args) {
        // Logger se inicializa dentro del constructor de Simulador ahora
        ConfiguracionSimulacion config = ConfiguracionSimulacion.cargarConfiguracion();
        Planificador planificadorInicial = new PlanificadorFCFS();
        Simulador simulador = new Simulador(config, planificadorInicial);
        Scanner scanner = new Scanner(System.in);
        int opcion = -1; // Inicializar diferente de 0

        do {
            mostrarMenu(simulador);
            try {
                if (scanner.hasNextInt()) {
                    opcion = scanner.nextInt();
                    scanner.nextLine();
                } else {
                    System.out.println("Entrada inválida. Por favor, ingrese un número.");
                    scanner.nextLine();
                    opcion = -1;
                    continue;
                }

                switch (opcion) {
                    case 1: ejecutarCiclo(simulador); break;
                    case 2: ejecutarContinuamente(simulador); break;
                    case 3: crearNuevoProceso(simulador, scanner); break;
                    case 4: modificarDuracionCiclo(simulador, scanner); break;
                    case 5: cambiarPlanificador(simulador, scanner); break;
                    case 6: simulador.guardarEstado(); break;
                    case 7:
                        if (simulador.cargarEstado()) {
                             System.out.println("Estado cargado. Reiniciando semáforos y hilos...");
                             simulador.asignarSemaforoAProcesos();
                             simulador.reiniciarHilosPostCarga();
                             simulador.mostrarEstado();
                        } else {
                            System.out.println("Fallo al cargar el estado.");
                        }
                        break;
                    case 8: // Nueva opción Métricas
                        simulador.calcularYMostrarMetricas();
                        break;
                    case 0:
                        System.out.println("Saliendo del simulador...");
                        simulador.cerrarSimulador(); // Llama a cerrar logger y mostrar métricas finales
                        break;
                    default: System.out.println("Opción no válida. Intente de nuevo.");
                }
            } catch (InputMismatchException e) {
                System.out.println("Error: Por favor, ingrese un número válido.");
                scanner.nextLine();
                opcion = -1;
            } catch (Exception e) {
                 System.err.println("!!! Ocurrió un error inesperado en el menú: " + e.getMessage());
                 e.printStackTrace();
                 opcion = -1; // Evita bucle infinito si el error persiste
            }

        } while (opcion != 0);

        scanner.close();
        System.out.println("Simulador terminado.");
        // Asegurarse de cerrar el logger si no se salió por la opción 0
        Logger.close();
    }

    private static void mostrarMenu(Simulador simulador) {
        System.out.println("\n==================================");
        System.out.println("      SIMULADOR CONCURRENTE       ");
        System.out.println("==================================");
        String nombrePlanificador = (simulador.getPlanificador() != null) ? simulador.getPlanificador().getNombre() : "No asignado";
        System.out.println("Política Actual: " + nombrePlanificador);
        System.out.printf("Ciclo Simulado: %dms%n", simulador.getConfig().getDuracionCicloMs());
        System.out.println("----------------------------------");
        System.out.println("1. Ejecutar UN ciclo de simulacion");
        System.out.println("2. Ejecutar continuamente");
        System.out.println("3. Crear y agregar nuevo proceso");
        System.out.println("4. Modificar duracion del ciclo");
        System.out.println("5. Cambiar politica de planificacion (6 Tipos)");
        System.out.println("6. Guardar Estado Actual");
        System.out.println("7. Cargar Estado Anterior");
        System.out.println("8. Mostrar Metricas de Rendimiento"); // Nueva opción
        System.out.println("0. Salir");
        System.out.print("Seleccione una opcion: ");
    }

    private static void ejecutarCiclo(Simulador simulador) {
        if (!simulador.quedanProcesos() && simulador.procesoActual == null) {
            System.out.println("No hay procesos activos o en colas para ejecutar.");
            simulador.calcularYMostrarMetricas(); // Mostrar métricas si ya no hay nada
            return;
        }
        simulador.ejecutarCicloSimulacion();
    }

    private static void ejecutarContinuamente(Simulador simulador) {
        if (!simulador.quedanProcesos() && simulador.procesoActual == null) {
            System.out.println("No hay procesos activos o en colas para ejecutar.");
            simulador.calcularYMostrarMetricas(); // Mostrar métricas si ya no hay nada
            return;
        }
        System.out.println("Ejecutando simulacion continuamente. Presione CTRL+C para detener...");
        try {
            while (simulador.quedanProcesos()) {
                simulador.ejecutarCicloSimulacion();
            }
            System.out.println("\n--- Todos los procesos han terminado ---");
            simulador.mostrarEstado();
            simulador.calcularYMostrarMetricas(); // Mostrar métricas al finalizar
        } catch (Exception e) {
             if (e instanceof InterruptedException || (e.getCause() instanceof InterruptedException)) {
                  System.out.println("\n--- Ejecucion continua interrumpida ---");
                  Logger.log("Ejecucion continua interrumpida por usuario.");
             } else {
                 System.out.println("\n--- Ocurrio un error durante la ejecucion continua ---");
                 Logger.log("ERROR Inesperado en ejecucion continua: " + e.getMessage());
                 e.printStackTrace();
             }
            simulador.mostrarEstado();
            simulador.calcularYMostrarMetricas(); // Mostrar métricas (parciales) al interrumpir/error
        }
    }

    private static void crearNuevoProceso(Simulador simulador, Scanner scanner) {
        // ... (código sin cambios) ...
        System.out.print("Nombre del proceso: "); String nombre = scanner.nextLine();
        int instrucciones = -1;
        while (instrucciones <= 0) { /*...*/
            System.out.print("Cantidad de instrucciones: ");
            if (scanner.hasNextInt()) {
                instrucciones = scanner.nextInt();
                if (instrucciones <= 0) System.out.println("Valor inválido. Ingrese un número positivo.");
            } else System.out.println("Entrada no numérica. Ingrese un número positivo.");
            scanner.nextLine();
        }
        int prioridad = -1;
        while (prioridad < 1 || prioridad > 10) { /*...*/
            System.out.printf("Prioridad (1=Alta, 10=Baja) [Defecto: %d]: ", DEFAULT_PRIORITY);
            String line = scanner.nextLine().trim();
             if (line.isEmpty()) prioridad = DEFAULT_PRIORITY;
             else { try { prioridad = Integer.parseInt(line); if (prioridad < 1 || prioridad > 10) System.out.println("Prioridad fuera de rango (1-10)."); } catch (NumberFormatException e) { System.out.println("Entrada inválida."); prioridad = -1; } }
        }
        System.out.print("¿Es I/O Bound? (s/n) [Defecto: n]: "); String esIoBoundStr = scanner.nextLine().trim().toLowerCase(); boolean esIoBound = esIoBoundStr.equals("s");
        if (esIoBound) {
            int ciclosInt = -1;
            while (ciclosInt <= 0) { /*...*/
                System.out.print("Ciclos CPU para generar interrupción de E/S (>0): ");
                if (scanner.hasNextInt()) { ciclosInt = scanner.nextInt(); if (ciclosInt <= 0) System.out.println("Valor inválido."); } else System.out.println("Entrada no numérica.");
                scanner.nextLine();
            }
            int ciclosSat = -1;
            while (ciclosSat <= 0) { /*...*/
                System.out.print("Ciclos de E/S para satisfacerla (>0): ");
                if (scanner.hasNextInt()) { ciclosSat = scanner.nextInt(); if (ciclosSat <= 0) System.out.println("Valor inválido."); } else System.out.println("Entrada no numérica.");
                scanner.nextLine();
            }
            simulador.agregarProceso(new Proceso(nombre, instrucciones, prioridad, ciclosInt, ciclosSat));
        } else {
            simulador.agregarProceso(new Proceso(nombre, instrucciones, prioridad));
        }
        System.out.println("Proceso " + nombre + " añadido."); // Confirmación
    }

    private static void modificarDuracionCiclo(Simulador simulador, Scanner scanner) {
        // ... (código sin cambios) ...
        long nuevaDuracion = -1;
        while (nuevaDuracion <= 0) {
            System.out.print("Nueva duración del ciclo en milisegundos (ms > 0): ");
            if (scanner.hasNextLong()) { nuevaDuracion = scanner.nextLong(); if (nuevaDuracion <= 0) System.out.println("Valor inválido."); } else System.out.println("Entrada no numérica.");
            scanner.nextLine();
        }
        simulador.getConfig().setDuracionCicloMs(nuevaDuracion);
        System.out.println("✅ Duración del ciclo actualizada a " + nuevaDuracion + "ms.");
        Logger.log("Duración del ciclo cambiada a " + nuevaDuracion + "ms.");
    }

    private static void cambiarPlanificador(Simulador simulador, Scanner scanner) {
        // ... (código sin cambios) ...
        int quantum = DEFAULT_QUANTUM;
        System.out.println("\n--- CAMBIAR PLANIFICADOR (6 POLÍTICAS) ---"); /* ... Opciones ... */
        System.out.print("Seleccione el planificador: ");
        int elec = -1;
        if (scanner.hasNextInt()) { elec = scanner.nextInt(); scanner.nextLine(); } else { scanner.nextLine(); System.out.println("Opción inválida."); return; }
        Planificador nuevoPlanificador = null;
        try {
            switch (elec) {
                case 1: nuevoPlanificador = new PlanificadorFCFS(); break;
                case 2: nuevoPlanificador = new PlanificadorSJF(); break;
                case 3: nuevoPlanificador = new PlanificadorSRT(); break;
                case 4:
                    System.out.printf("Ingrese el quantum (ciclos > 0) para Round Robin [Defecto: %d]: ", quantum);
                     String quantumLine = scanner.nextLine().trim();
                     if (!quantumLine.isEmpty()) { try { int q = Integer.parseInt(quantumLine); if (q > 0) quantum = q; else System.out.println("Quantum inválido, usando defecto " + quantum); } catch (NumberFormatException e) { System.out.println("Entrada inválida, usando defecto " + quantum); } }
                    nuevoPlanificador = new PlanificadorRoundRobin(quantum); break;
                case 5: nuevoPlanificador = new PlanificadorPrioridadNoExpropiativa(); break;
                case 6: nuevoPlanificador = new PlanificadorPrioridadExpropiativa(); break;
                default: System.out.println("Opción no reconocida."); return;
            }
        } catch (Exception e) { System.err.println("Error creando planificador: " + e.getMessage()); return; }
        if (nuevoPlanificador != null) simulador.setPlanificador(nuevoPlanificador);
    }
}