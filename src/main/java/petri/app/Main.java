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

        // ===== 1) Watchdog de seguridad (NO es la duración objetivo) =====
        long watchdogMs = 40_000; // el TP pide que normalmente termine 20–40s; esto evita loops infinitos

        // ===== 2) Delays fijos (Escenario 0) =====
        long[] delays = Tp2025Net.fixedDelaysScenario0();

        // ===== 3) Construir red + estado =====
        PetriNet net = Tp2025Net.build(delays);
        Marking initial = Tp2025Net.initialMarking();
        NetState state = new NetState(net, initial);

        // ===== 4) Monitor + política =====
        Policy policy = new RandomPolicy(); // o new PriorityPolicy(Set.of(5))
        Monitor monitor = new Monitor(state, policy, Tp2025Net.TRANSITIONS);
        MonitorInterface mon = monitor;

        // ===== 5) Segmentos / responsabilidades (dimensionamiento: 9 hilos) =====
        int[] PRE  = {0, 1};                // segmento previo al conflicto
        int[] R1   = {2, 3, 4};             // rama 1
        int[] R2   = {5, 6};                // rama 2
        int[] R3   = {7, 8, 9, 10};         // rama 3
        int[] POST = {11};                  // segmento posterior al join

        // ===== 6) Lanzar 9 virtual threads =====
        List<Thread> threads = new ArrayList<>();

        // 3 hilos PRE (paralelismo por capacidad del buffer)
        threads.add(Thread.ofVirtual().name("PRE-1").start(new Worker(PRE, mon)));
        threads.add(Thread.ofVirtual().name("PRE-2").start(new Worker(PRE, mon)));
        threads.add(Thread.ofVirtual().name("PRE-3").start(new Worker(PRE, mon)));

        // 1 hilo por rama
        threads.add(Thread.ofVirtual().name("R1").start(new Worker(R1, mon)));
        threads.add(Thread.ofVirtual().name("R2").start(new Worker(R2, mon)));
        threads.add(Thread.ofVirtual().name("R3").start(new Worker(R3, mon)));

        // 3 hilos POST (paralelismo por capacidad del buffer)
        threads.add(Thread.ofVirtual().name("POST-1").start(new Worker(POST, mon)));
        threads.add(Thread.ofVirtual().name("POST-2").start(new Worker(POST, mon)));
        threads.add(Thread.ofVirtual().name("POST-3").start(new Worker(POST, mon)));

        // ===== 7) Esperar fin natural (T11>=limit) o watchdog =====
        while (!monitor.isStopRequested()) {
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed >= watchdogMs) {
                monitor.requestStop();
                break;
            }
            Thread.sleep(10);
        }

        for (Thread t : threads) t.join();

        // ===== 8) Resumen / logs =====
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
        long elapsedMs = endMs - startMs;
        System.out.println("Duración real: " + elapsedMs + " ms");
        System.out.println("Watchdog configurado: " + watchdogMs + " ms");

        logger.writeSequence(monitor.getSequence());

        // OJO: acá conviene pasar elapsed real. Si no querés cambiar Log, al menos no lo llames "runMs".
        logger.writeSummary(watchdogMs, startMs, endMs, delays, policy, monitor, state);
    }
}
