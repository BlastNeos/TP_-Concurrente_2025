package petri.app;

import petri.core.Marking;
import petri.core.PetriNet;
import petri.monitor.*;
import petri.runtime.NetState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        long startMs = System.currentTimeMillis();
        Log logger = new Log();

        // ===== 1) Configuración de corrida (TP pide 20–40s) =====
        long runMs = 40_000; // 40s (dentro del rango pedido)

        // ===== 2) Delays aleatorios para transiciones temporales =====
        //long[] delays = Tp2025Net.randomDelaysForTimed(1, 5);
        long[] delays = Tp2025Net.fixedDelaysScenario0();

        // ===== 3) Construir red + estado =====
        PetriNet net = Tp2025Net.build(delays);
        Marking initial = Tp2025Net.initialMarking();
        NetState state = new NetState(net, initial);

        // ===== 4) Monitor + política =====
        Policy policy = new RandomPolicy(); // luego metemos PriorityPolicy
        //Monitor monitor = new Monitor(state, policy, net.transitions());
        Monitor monitor = new Monitor(state, policy, Tp2025Net.TRANSITIONS);
        MonitorInterface mon = monitor; // por si Worker usa la interfaz

        // ===== 5) Segmentación según el diagrama (5 hilos) =====
        int[] A = {0, 1};             // Entrada
        int[] B = {2, 3, 4};          // Rama superior
        int[] C = {5, 6};             // Rama media
        int[] D = {7, 8, 9, 10};      // Rama inferior
        int[] E = {11};               // Salida

        Worker wA = new Worker(A, mon);
        Worker wB = new Worker(B, mon);
        Worker wC = new Worker(C, mon);
        Worker wD = new Worker(D, mon);
        Worker wE = new Worker(E, mon);

        // ===== 6) Lanzar virtual threads =====
        List<Thread> threads = new ArrayList<>();
        threads.add(Thread.ofVirtual().name("A-Entrada").start(wA));
        threads.add(Thread.ofVirtual().name("B-Procesamiento-Medio").start(wB));  //Rama superior
        threads.add(Thread.ofVirtual().name("C-Procesamiento-Rapido").start(wC));  //Rama media
        threads.add(Thread.ofVirtual().name("D-Procesamiento-Lento").start(wD));  //Rama inferior
        threads.add(Thread.ofVirtual().name("E-Salida").start(wE));

        // ===== 7) Correr y detener limpio =====
        //Thread.sleep(runMs);
        //monitor.requestStop();
        while (!monitor.isStopRequested()) {
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed >= runMs) {
                monitor.requestStop();      // si algo salió mal, cortás igual
                break;
            }
            Thread.sleep(10);               // polling liviano
        }

        for (Thread t : threads) 
            t.join();

        // ===== 8) Resumen =====
        int[] fired = monitor.getFiredCountSnapshot();
        int[] picks = monitor.getPolicyPickCountSnapshot();

        System.out.println("\n=== MÉTRICAS DE DISPARO ===");
        for (int i = 0; i < fired.length; i++) {
            System.out.printf("T%d disparos: %d%n", i, fired[i]);
        }

        System.out.println("\n=== CICLOS / INVARIANTES ===");
        System.out.println("Ciclos inyectados (T0):  " + fired[0]);
        System.out.println("Ciclos completados (T11): " + fired[11]);

        System.out.println("\n=== CONFLICTO (DISPAROS REALES) ===");
        System.out.println("T2: " + fired[2] + " | T5: " + fired[5] + " | T7: " + fired[7]);

        System.out.println("\n=== CONFLICTO (DECISIONES DE POLÍTICA) ===");
        System.out.println("Pick T2: " + picks[2] + " | Pick T5: " + picks[5] + " | Pick T7: " + picks[7]);

        long totalConflictFires = fired[2] + fired[5] + fired[7];
        if (totalConflictFires > 0) {
            System.out.printf("Distribución real: T2=%.1f%%, T5=%.1f%%, T7=%.1f%%%n",
                    100.0 * fired[2] / totalConflictFires,
                    100.0 * fired[5] / totalConflictFires,
                    100.0 * fired[7] / totalConflictFires);
        }
        System.out.println("\nStopFeeding activado (T0>=limit): " + (fired[0] >= 200));
        System.out.println("Drain completado (T11>=limit): " + (fired[11] >= 200));

        long endMs = System.currentTimeMillis();
        System.out.println("Duración: " + (endMs-startMs) + " ms");
        
        logger.writeSequence(monitor.getSequence());
        logger.writeSummary(runMs, startMs, endMs, delays, policy, monitor, state);

    }
}
