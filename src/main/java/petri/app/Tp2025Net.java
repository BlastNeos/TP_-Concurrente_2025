package petri.app;

import petri.core.Marking;
import petri.core.PetriNet;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class Tp2025Net {

    public static final int PLACES = 12;
    public static final int TRANSITIONS = 12;

    private Tp2025Net() {}

    /**
     * Construye la PetriNet del TP 2025.
     * pre[p][t]  = tokens consumidos de la plaza p por la transición t
     * post[p][t] = tokens producidos hacia la plaza p por la transición t
     *
     * Orden: P0..P11 y T0..T11
     */
    public static PetriNet build(long[] delayMs) {
        if (delayMs == null || delayMs.length != TRANSITIONS) {
            throw new IllegalArgumentException("delayMs debe tener longitud " + TRANSITIONS);
        }

        int[][] pre = {
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P0
                {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P1
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P2
                {0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0}, // P3
                {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0}, // P4
                {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0}, // P5
                {0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0}, // P6
                {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0}, // P7
                {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0}, // P8
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0}, // P9
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0}, // P10
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}  // P11
        };

        int[][] post = {
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, // P0
                {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P1
                {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P2
                {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P3
                {0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // P4
                {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0}, // P5
                {0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0}, // P6
                {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0}, // P7
                {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0}, // P8
                {0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0}, // P9
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0}, // P10
                {0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0}  // P11
        };

        return new PetriNet(pre, post, delayMs);
    }

    /**
     * Marcado inicial (orden P0..P11).
     */
    public static Marking initialMarking() {
        int[] init = {3, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0};
        return new Marking(init);
    }

    /**
     * Genera delays aleatorios (ms) para las transiciones temporales:
     * {T1, T3, T4, T6, T8, T9, T10}.
     *
     * Las demás quedan en 0.
     */
    public static long[] randomDelaysForTimed(long minMs, long maxMs) {
        if (minMs < 0 || maxMs < minMs) {
            throw new IllegalArgumentException("Rango inválido: [" + minMs + ", " + maxMs + "]");
        }

        long[] d = new long[TRANSITIONS]; // por defecto 0

        // temporales según tu lista
        Set<Integer> timed = Set.of(1, 3, 4, 6, 8, 9, 10);

        for (int t : timed) {
            d[t] = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        }
        return d;
    }

    public static long[] fixedDelaysScenario0() {
        long[] d = new long[TRANSITIONS];
        d[1]  = 250;   // T1
        d[3]  = 50;   // T3
        d[4]  = 50;   // T4
        d[6]  = 50;   // T6
        d[8]  = 50;   // T8
        d[9]  = 50;   // T9
        d[10] = 50;   // T10
        return d;
    }
}
