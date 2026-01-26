package petri.runtime;

import petri.core.Marking;
import petri.core.PetriNet;

import java.util.Arrays;

/**
 * Estado "en ejecución" de una red de Petri:
 * - referencia a la estructura (PetriNet)
 * - marcado actual (Marking)
 * - control de temporización por transición (enabledSince)
 *
 * Nota: esta clase NO sincroniza (eso lo hace el monitor).
 */
public class NetState {
    private final PetriNet net; // estructura fija de la red (pre/post/delays)

    // estado dinámico: el marcado actual (tokens por plaza)
    private Marking marking;

    // enabledSince[t] = timestamp (ms) cuando Tt quedó habilitada por tokens
    // si no está habilitada por tokens -> -1
    private final long[] enabledSince;

    public NetState(PetriNet net, Marking initial) {
        this.net = net;
        this.marking = initial;

        // un "reloj" por transición
        this.enabledSince = new long[net.transitions()];
        Arrays.fill(enabledSince, -1L); // -1 => "no está corriendo el reloj"
    }

    public Marking getMarking() {
        return marking; // devuelve el estado actual (inmutable)
    }

    public int[] getMarkingSnapshot() {
        return marking.snapshot(); // asume que Marking tiene snapshot() que devuelve int[]
    }

    /**
     * Devuelve:
     *  -1  si NO está habilitada por tokens
     *   0  si puede disparar ya (tokens OK y tiempo cumplido)
     *  >0  milisegundos que faltan para poder disparar por tiempo
     *
     * Idea: separa "habilitada por tokens" de "habilitada por tiempo".
     */
    public long timeLeft(int t) {
        // 1) Si no está habilitada por tokens, no hay temporizador activo
        if (!net.isEnabledByTokens(marking, t)) {
            enabledSince[t] = -1L; // resetea el reloj porque dejó de estar habilitada
            return -1;
        }

        // 2) Si no es temporizada, puede disparar ya
        long delay = net.delayMs(t);
        if (delay == 0) return 0;

        // 3) Si es temporizada, calculamos cuánto falta desde que se habilitó por tokens
        long now = System.currentTimeMillis();

        // si recién ahora quedó habilitada (o se reseteó antes), arrancamos el reloj
        if (enabledSince[t] == -1L) {
            enabledSince[t] = now;
        }

        long elapsed = now - enabledSince[t]; // tiempo transcurrido desde que se habilitó
        long left = delay - elapsed;          // tiempo restante para cumplir el delay

        // nunca devolvemos negativo: si ya pasó, devolvemos 0
        return Math.max(left, 0);
    }

    /**
     * Dispara la transición t asumiendo que ya puede (timeLeft(t) == 0).
     * Actualiza el marcado y resetea el reloj de esa transición.
     */
    public void fire(int t) {
        // check de seguridad: si falta tiempo o tokens, no se puede disparar
        if (timeLeft(t) != 0) {
            throw new IllegalStateException("No se puede disparar T" + t + " todavía (tokens o tiempo)");
        }

        // actualiza el marcado aplicando la ecuación de estado
        marking = net.fire(marking, t);

        // resetea el reloj de esa transición (el próximo "habilitada" arranca de nuevo)
        enabledSince[t] = -1L;
    }
}