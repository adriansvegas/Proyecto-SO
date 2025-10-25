/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package so_operativos;

/**
 *
 * @author Edgar
 */

import java.util.InputMismatchException;
import java.util.Scanner;

public class Main {
    private static final int DEFAULT_PRIORITY = 5;
    private static final int DEFAULT_QUANTUM = 20;

    public static void main(String[] args) {
        ConfiguracionSimulacion config = ConfiguracionSimulacion.cargarConfiguracion();

        Planificador planificadorInicial = new PlanificadorFCFS();
        Simulador simulador = new Simulador(config, planificadorInicial);
        Scanner scanner = new Scanner(System.in);
        int opcion;

        // Procesos de ejemplo
        simulador.agregarProceso(new Proceso("P1-ALTA_PRIO", 500, 1)); 
        simulador.agregarProceso(new Proceso("P2-IO_LOW", 300, 8, 50, 150)); 
        simulador.agregarProceso(new Proceso("P3-SJF_SHORT", 200, 5));
        simulador.agregarProceso(new Proceso("P4-IO_MED", 800, 5, 20, 100));

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
                    case 5: System.out.println("Opción (Cambiar Planificador) se implementará pronto."); break; // Placeholder
                    case 0: System.out.println("Saliendo del simulador. ¡Adiós!"); break;
                    default: System.out.println("Opción no válida. Intente de nuevo.");
                }
            } catch (InputMismatchException e) {
                System.out.println("Error: Por favor, ingrese un número válido.");
                scanner.nextLine(); 
                opcion = -1;
            }

        } while (opcion != 0);

        scanner.close();
    }

    private static void mostrarMenu(Simulador simulador) {
        System.out.println("\n==================================");
        System.out.println("      SIMULADOR CONCURRENTE       ");
        System.out.println("==================================");
        System.out.println("Política Actual: " + simulador.getPlanificador().getNombre());
        System.out.printf("Ciclo Simulado: %dms%n", simulador.getConfig().getDuracionCicloMs());
        System.out.println("----------------------------------");
        System.out.println("1. Ejecutar UN ciclo de simulación");
        System.out.println("2. Ejecutar continuamente");
        System.out.println("3. Crear y agregar nuevo proceso");
        System.out.println("4. Modificar duración del ciclo");
        System.out.println("5. Cambiar política de planificación (6 Tipos)");
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
        } catch (Exception e) {
            System.out.println("\n--- Ejecución continua detenida ---");
            simulador.mostrarEstado();
        }
    }

    private static void crearNuevoProceso(Simulador simulador, Scanner scanner) {
        System.out.print("Nombre del proceso: ");
        String nombre = scanner.nextLine();

        int instrucciones = -1;
        while (instrucciones <= 0) {
            System.out.print("Cantidad de instrucciones: ");
            if (scanner.hasNextInt()) {
                instrucciones = scanner.nextInt();
            } else {
                System.out.println("Valor inválido. Ingrese un número positivo.");
            }
            scanner.nextLine();
        }

        int prioridad = -1;
        while (prioridad < 1 || prioridad > 10) {
            System.out.printf("Prioridad (1=Alta, 10=Baja) [Defecto: %d]: ", DEFAULT_PRIORITY);
            if (scanner.hasNextInt()) {
                prioridad = scanner.nextInt();
            } else {
                prioridad = DEFAULT_PRIORITY; 
            }
            scanner.nextLine();
        }

        System.out.print("¿Es I/O Bound? (s/n): ");
        String esIoBoundStr = scanner.nextLine().trim().toLowerCase();
        boolean esIoBound = esIoBoundStr.equals("s");

        if (esIoBound) {
            int ciclosInt = -1;
            while (ciclosInt <= 0) {
                System.out.print("Ciclos para generar interrupción de E/S: ");
                if (scanner.hasNextInt()) {
                    ciclosInt = scanner.nextInt();
                } else {
                    System.out.println("Valor inválido. Ingrese un número positivo.");
                }
                scanner.nextLine();
            }

            int ciclosSat = -1;
            while (ciclosSat <= 0) {
                System.out.print("Ciclos para satisfacer E/S: ");
                if (scanner.hasNextInt()) {
                    ciclosSat = scanner.nextInt();
                } else {
                    System.out.println("Valor inválido. Ingrese un número positivo.");
                }
                scanner.nextLine();
            }

            simulador.agregarProceso(new Proceso(nombre, instrucciones, prioridad, ciclosInt, ciclosSat));
        } else {
            simulador.agregarProceso(new Proceso(nombre, instrucciones, prioridad));
        }
    }
    
    private static void cambiarPlanificador(Simulador simulador, Scanner scanner) {
    int quantum = DEFAULT_QUANTUM; 

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
        scanner.nextLine();
    } else {
        scanner.nextLine(); 
        System.out.println("Opción inválida.");
        return;
    }

    Planificador nuevoPlanificador;
    switch (elec) {
        case 1: nuevoPlanificador = new PlanificadorFCFS(); break;
        case 2: nuevoPlanificador = new PlanificadorSJF(); break;
        case 3: nuevoPlanificador = new PlanificadorSRT(); break;
        case 4:
            System.out.printf("Ingrese el quantum (ciclos) para Round Robin (Defecto: %d): ", quantum);
            if (scanner.hasNextInt()) {
                quantum = scanner.nextInt();
                scanner.nextLine();
            } else {
                scanner.nextLine();
            }
            nuevoPlanificador = new PlanificadorRoundRobin(quantum);
            break;
        case 5: nuevoPlanificador = new PlanificadorPrioridadNoExpropiativa(); break;
        case 6: nuevoPlanificador = new PlanificadorPrioridadExpropiativa(); break;
        default:
            System.out.println("Opción no reconocida.");
            return;
    }

    simulador.setPlanificador(nuevoPlanificador);
}

    private static void modificarDuracionCiclo(Simulador simulador, Scanner scanner) {
        long nuevaDuracion = -1;
        while (nuevaDuracion <= 0) {
            System.out.print("Nueva duración del ciclo en milisegundos (ms): ");
            if (scanner.hasNextLong()) {
                nuevaDuracion = scanner.nextLong();
            } else {
                System.out.println("Valor inválido. Ingrese un número positivo.");
            }
            scanner.nextLine();
        }

        simulador.getConfig().setDuracionCicloMs(nuevaDuracion);
        System.out.println("✅ Duración del ciclo actualizada a " + nuevaDuracion + "ms.");
    }

    // cambiarPlanificador se implementará en la siguiente etapa
}