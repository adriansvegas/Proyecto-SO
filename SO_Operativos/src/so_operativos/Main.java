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
    // Definir como públicos y estáticos para acceder desde Simulador si es necesario (ej. para RR)
    public static final int DEFAULT_PRIORITY = 5;
    public static final int DEFAULT_QUANTUM = 20;

    public static void main(String[] args) {
        ConfiguracionSimulacion config = ConfiguracionSimulacion.cargarConfiguracion();

        Planificador planificadorInicial = new PlanificadorFCFS(); // Planificador por defecto si no se carga estado
        // Pasa el planificador inicial al constructor por si falla la carga
        Simulador simulador = new Simulador(config, planificadorInicial);
        Scanner scanner = new Scanner(System.in);
        int opcion;

        // Ya no añadimos procesos de ejemplo aquí, se cargan desde el archivo si existe

        do {
            mostrarMenu(simulador);
            try {
                if (scanner.hasNextInt()) {
                    opcion = scanner.nextInt();
                    scanner.nextLine(); // Consumir newline
                } else {
                    System.out.println("Entrada inválida. Por favor, ingrese un número.");
                    scanner.nextLine(); // Consumir entrada inválida
                    opcion = -1; // Forzar reintento
                    continue;
                }

                switch (opcion) {
                    case 1: ejecutarCiclo(simulador); break;
                    case 2: ejecutarContinuamente(simulador); break;
                    case 3: crearNuevoProceso(simulador, scanner); break;
                    case 4: modificarDuracionCiclo(simulador, scanner); break;
                    case 5: cambiarPlanificador(simulador, scanner); break;
                    case 6: // Nueva opción Guardar Estado
                        simulador.guardarEstado();
                        break;
                    case 7: // Nueva opción Cargar Estado
                        if (simulador.cargarEstado()) {
                            // Si la carga fue exitosa, reasignar semáforos y reiniciar hilos
                             System.out.println("Estado cargado. Reiniciando semáforos y hilos...");
                             simulador.asignarSemaforoAProcesos(); // Método añadido en Simulador
                             simulador.reiniciarHilosPostCarga();  // Método añadido en Simulador
                             simulador.mostrarEstado(); // Mostrar estado cargado
                        } else {
                            System.out.println("Fallo al cargar el estado. La simulación continúa como estaba.");
                        }
                        break;
                    case 0: System.out.println("Saliendo del simulador. ¡Adiós!"); break;
                    default: System.out.println("Opción no válida. Intente de nuevo.");
                }
            } catch (InputMismatchException e) {
                System.out.println("Error: Por favor, ingrese un número válido.");
                scanner.nextLine(); // Limpiar buffer del scanner
                opcion = -1; // Forzar reintento
            } catch (Exception e) {
                 // Captura general para otros posibles errores durante la ejecución de opciones
                 System.err.println("!!! Ocurrió un error inesperado: " + e.getMessage());
                 e.printStackTrace(); // Imprime detalles del error para depuración
                 opcion = -1; // Opcional: forzar reintento o salir
            }

        } while (opcion != 0);

        scanner.close();
    }

    private static void mostrarMenu(Simulador simulador) {
        System.out.println("\n==================================");
        System.out.println("      SIMULADOR CONCURRENTE       ");
        System.out.println("==================================");
        // Asegurarse que planificador no sea null antes de llamar a getNombre
        String nombrePlanificador = (simulador.getPlanificador() != null) ? simulador.getPlanificador().getNombre() : "No asignado";
        System.out.println("Política Actual: " + nombrePlanificador);
        System.out.printf("Ciclo Simulado: %dms%n", simulador.getConfig().getDuracionCicloMs());
        System.out.println("----------------------------------");
        System.out.println("1. Ejecutar UN ciclo de simulación");
        System.out.println("2. Ejecutar continuamente");
        System.out.println("3. Crear y agregar nuevo proceso");
        System.out.println("4. Modificar duración del ciclo");
        System.out.println("5. Cambiar política de planificación (6 Tipos)");
        System.out.println("6. Guardar Estado Actual"); // Nueva opción
        System.out.println("7. Cargar Estado Anterior"); // Nueva opción
        System.out.println("0. Salir");
        System.out.print("Seleccione una opción: ");
    }

    private static void ejecutarCiclo(Simulador simulador) {
        simulador.ejecutarCicloSimulacion();
    }

    private static void ejecutarContinuamente(Simulador simulador) {
        System.out.println("Ejecutando simulación continuamente. Presione CTRL+C para detener...");
        try {
            while (simulador.quedanProcesos()) {
                simulador.ejecutarCicloSimulacion();
            }
            System.out.println("\n--- Todos los procesos han terminado ---");
            simulador.mostrarEstado();
        } catch (Exception e) { // Cambiado de RuntimeException a Exception más general
             if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                  System.out.println("\n--- Ejecución continua interrumpida (CTRL+C?) ---");
             } else {
                 System.out.println("\n--- Ocurrió un error durante la ejecución continua ---");
                 e.printStackTrace(); // Muestra el error
             }
            simulador.mostrarEstado(); // Muestra el estado final o al momento del error
        }
    }

    private static void crearNuevoProceso(Simulador simulador, Scanner scanner) {
        // ... (código sin cambios) ...
        System.out.print("Nombre del proceso: ");
        String nombre = scanner.nextLine();

        int instrucciones = -1;
        while (instrucciones <= 0) {
            System.out.print("Cantidad de instrucciones: ");
            if (scanner.hasNextInt()) {
                instrucciones = scanner.nextInt();
                if (instrucciones <= 0) {
                     System.out.println("Valor inválido. Ingrese un número positivo.");
                }
            } else {
                System.out.println("Entrada no numérica. Ingrese un número positivo.");
            }
            scanner.nextLine(); // Consumir newline o entrada inválida
        }

        int prioridad = -1;
        while (prioridad < 1 || prioridad > 10) {
            System.out.printf("Prioridad (1=Alta, 10=Baja) [Defecto: %d]: ", DEFAULT_PRIORITY);
            String line = scanner.nextLine().trim();
             if (line.isEmpty()) {
                  prioridad = DEFAULT_PRIORITY;
             } else {
                 try {
                     prioridad = Integer.parseInt(line);
                     if (prioridad < 1 || prioridad > 10) {
                          System.out.println("Prioridad fuera de rango (1-10).");
                     }
                 } catch (NumberFormatException e) {
                     System.out.println("Entrada inválida. Use números o deje vacío para el defecto.");
                     prioridad = -1; // Forzar reintento
                 }
             }
        }

        System.out.print("¿Es I/O Bound? (s/n) [Defecto: n]: ");
        String esIoBoundStr = scanner.nextLine().trim().toLowerCase();
        boolean esIoBound = esIoBoundStr.equals("s");

        if (esIoBound) {
            int ciclosInt = -1;
            while (ciclosInt <= 0) {
                System.out.print("Ciclos CPU para generar interrupción de E/S (>0): ");
                if (scanner.hasNextInt()) {
                    ciclosInt = scanner.nextInt();
                     if (ciclosInt <= 0) {
                          System.out.println("Valor inválido. Ingrese un número positivo.");
                     }
                } else {
                    System.out.println("Entrada no numérica. Ingrese un número positivo.");
                }
                scanner.nextLine(); // Consumir newline o entrada inválida
            }

            int ciclosSat = -1;
            while (ciclosSat <= 0) {
                System.out.print("Ciclos de E/S para satisfacerla (>0): ");
                if (scanner.hasNextInt()) {
                    ciclosSat = scanner.nextInt();
                     if (ciclosSat <= 0) {
                          System.out.println("Valor inválido. Ingrese un número positivo.");
                     }
                } else {
                    System.out.println("Entrada no numérica. Ingrese un número positivo.");
                }
                scanner.nextLine(); // Consumir newline o entrada inválida
            }

            simulador.agregarProceso(new Proceso(nombre, instrucciones, prioridad, ciclosInt, ciclosSat));
        } else {
            simulador.agregarProceso(new Proceso(nombre, instrucciones, prioridad));
        }
         System.out.println("Proceso " + nombre + " añadido.");
    }

    private static void modificarDuracionCiclo(Simulador simulador, Scanner scanner) {
        long nuevaDuracion = -1;
        while (nuevaDuracion <= 0) {
            System.out.print("Nueva duración del ciclo en milisegundos (ms > 0): ");
            if (scanner.hasNextLong()) {
                nuevaDuracion = scanner.nextLong();
                 if (nuevaDuracion <= 0) {
                      System.out.println("Valor inválido. Ingrese un número positivo.");
                 }
            } else {
                System.out.println("Entrada no numérica. Ingrese un número positivo.");
            }
            scanner.nextLine(); // Consumir newline o entrada inválida
        }

        simulador.getConfig().setDuracionCicloMs(nuevaDuracion);
        System.out.println("✅ Duración del ciclo actualizada a " + nuevaDuracion + "ms.");
    }

    private static void cambiarPlanificador(Simulador simulador, Scanner scanner) {
        int quantum = DEFAULT_QUANTUM; // Usa el default definido en Main

        System.out.println("\n--- CAMBIAR PLANIFICADOR (6 POLÍTICAS) ---");
        System.out.println("1. FCFS (No Expropiativo)");
        System.out.println("2. SJF (No Expropiativo)");
        System.out.println("3. SRT (SJF Expropiativo)");
        System.out.println("4. Round Robin (Expropiativo por Quantum)");
        System.out.println("5. Prioridad (No Expropiativa)");
        System.out.println("6. Prioridad (Expropiativa)");
        System.out.print("Seleccione el planificador: ");

        int elec = -1;
        if (scanner.hasNextInt()) {
            elec = scanner.nextInt();
            scanner.nextLine(); // Consumir newline
        } else {
            scanner.nextLine(); // Consumir entrada inválida
            System.out.println("Opción inválida.");
            return;
        }

        Planificador nuevoPlanificador = null;
        try {
            switch (elec) {
                case 1: nuevoPlanificador = new PlanificadorFCFS(); break;
                case 2: nuevoPlanificador = new PlanificadorSJF(); break;
                case 3: nuevoPlanificador = new PlanificadorSRT(); break;
                case 4:
                    System.out.printf("Ingrese el quantum (ciclos > 0) para Round Robin [Defecto: %d]: ", quantum);
                     String quantumLine = scanner.nextLine().trim();
                     if (quantumLine.isEmpty()) {
                         // Usa default
                     } else {
                         try {
                              int q = Integer.parseInt(quantumLine);
                              if (q > 0) {
                                  quantum = q;
                              } else {
                                  System.out.println("Quantum inválido, usando defecto " + quantum);
                              }
                         } catch (NumberFormatException e) {
                              System.out.println("Entrada inválida para quantum, usando defecto " + quantum);
                         }
                     }
                    nuevoPlanificador = new PlanificadorRoundRobin(quantum);
                    break;
                case 5: nuevoPlanificador = new PlanificadorPrioridadNoExpropiativa(); break;
                case 6: nuevoPlanificador = new PlanificadorPrioridadExpropiativa(); break;
                default:
                    System.out.println("Opción no reconocida.");
                    return;
            }
        } catch (Exception e) {
             System.err.println("Error creando planificador: " + e.getMessage());
             return;
        }

        if (nuevoPlanificador != null) {
            simulador.setPlanificador(nuevoPlanificador);
        }
    }
}