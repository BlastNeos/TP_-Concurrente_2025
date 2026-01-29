package petri.monitor;

import petri.runtime.NetState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Monitor: único punto de sincronización para operar sobre la red.
 * Controla:
 * - exclusión mutua (lock)
 * - colas de condición (una por transición)
 * - temporización (vía state.timeLeft)
 * - política de wake-up (Policy)
 *
 * Importante: los Workers NO tocan NetState directamente, solo llaman fireTransition().
 */
public class Monitor implements MonitorInterface {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition[] cond;
    private final int[] waiting;

    private final NetState state;
    private final Policy policy;

    private final AtomicBoolean stop = new AtomicBoolean(false);

    private final StringBuilder sequence = new StringBuilder(1024);

    // métricas
    private final int[] firedCount;       // disparos reales por transición
    private final int[] policyPickCount;  // elecciones de policy al despertar

    // límites “feed & drain”
    private final int feedTransition = 0;   // T0
    private final int drainTransition = 11; // T11
    private final int limit;               // ej: 220

    // estado de fases
    private boolean stopFeeding = false;   // cuando true, T0 queda prohibida

    // criterio por tiempo (seguridad)
    private final long startMs;
    private final long maxRunMs;           // ej: 30_000 o 40_000

    public Monitor(NetState state, Policy policy, int transitions) {
        this.state = state;
        this.policy = policy;

        this.cond = new Condition[transitions];
        this.waiting = new int[transitions];

        this.firedCount = new int[transitions];
        this.policyPickCount = new int[transitions];

        this.limit = 200;                 // <<< tu tope
        this.startMs = System.currentTimeMillis();
        this.maxRunMs = 45_000;           // seguridad: 40s (ajustable)

        for (int i = 0; i < transitions; i++) {
            cond[i] = lock.newCondition();
            waiting[i] = 0;
        }
    }

    /** Pide detener la ejecución de todos los workers. */
    public void requestStop() {
        lock.lock();
        try {
            stop.set(true);
            for (Condition c : cond) c.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** (No está en la interfaz del TP; es método extra útil para Main/Logs) */
    public boolean isStopRequested() {
        return stop.get();
    }

    public String getSequence() {
        lock.lock();
        try {
            return sequence.toString();
        } finally {
            lock.unlock();
        }
    }

    public int[] getFiredCountSnapshot() {
        lock.lock();
        try { return firedCount.clone(); }
        finally { lock.unlock(); }
    }

    public int[] getPolicyPickCountSnapshot() {
        lock.lock();
        try { return policyPickCount.clone(); }
        finally { lock.unlock(); }
    }

    @Override
    public boolean fireTransition(int t) {
        lock.lock();
        try {
            // seguridad por tiempo total (evita que quede corriendo eterno si algo raro pasa)
            long now = System.currentTimeMillis();
            if (!stop.get() && (now - startMs >= maxRunMs)) {
                requestStop();
                return false;
            }

            while (!stop.get()) {

                // Fase de drenaje: T0 está prohibida
                if (t == feedTransition && stopFeeding) {
                    // Backoff pequeño  : evita busy loop del worker de entrada
                    waiting[t]++;
                    try {
                        cond[t].await(5, TimeUnit.MILLISECONDS);
                    } finally {
                        waiting[t]--;
                    }
                    return true; // no disparó nada, pero el worker sigue con su lista (T1, etc.)
                }

                long left = state.timeLeft(t);

                if (left == 0) {
                    // Disparo real
                    state.fire(t);
                    firedCount[t]++;
                    appendToSequence(t);

                    // Si alcanzamos el límite de alimentación, cortamos T0 (sin frenar el programa)
                    if (t == feedTransition && firedCount[feedTransition] >= limit) {
                        stopFeeding = true;
                        // Despertar a los que estén esperando en T0 para que no queden colgados
                        cond[feedTransition].signalAll();
                    }

                    // Si alcanzamos el límite de drenaje (salida completada), recién ahí frenamos
                    if (t == drainTransition && firedCount[drainTransition] >= limit) {
                        requestStop();
                        return false;
                    }

                    // Wake-up según política
                    List<Integer> readyToWake = computeReadyToWake();
                    if (!readyToWake.isEmpty()) {
                        int toWake = policy.choose(readyToWake);
                        policyPickCount[toWake]++;  // métrica: decisión de política
                        cond[toWake].signal();
                    }

                    return true;
                }

                if (left == -1) {
                    waiting[t]++;
                    try {
                        cond[t].await();
                    } finally {
                        waiting[t]--;
                    }
                } else {
                    waiting[t]++;
                    try {
                        cond[t].await(left, TimeUnit.MILLISECONDS);
                    } finally {
                        waiting[t]--;
                    }
                }
            }

            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void appendToSequence(int t) {
        if (t < 10) sequence.append("T0").append(t).append(' ');
        else sequence.append("T").append(t).append(' ');
    }

    private List<Integer> computeReadyToWake() {
        int n = waiting.length;
        List<Integer> ready = new ArrayList<>();

        for (int t = 0; t < n; t++) {
            if (waiting[t] > 0) {

                // Si estamos drenando, no tiene sentido despertar hilos de T0
                if (t == feedTransition && stopFeeding) continue;

                long left = state.timeLeft(t);
                if (left != -1) {
                    ready.add(t);
                }
            }
        }
        return ready;
    }
}