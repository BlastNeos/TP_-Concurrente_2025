package petri.app;

import petri.monitor.Monitor;
import petri.monitor.Policy;
import petri.runtime.NetState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public final class Log {

    private final Path dir;

    public Log() {
        this.dir = Paths.get("logs");
    }

    private Path file(String name) {
        return dir.resolve(name);
    }

    private void ensureDir() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio logs/", e);
        }
    }

    /**
     * Escribe la secuencia en un archivo (para regex).
     * Formato recomendado: tokens separados por espacio, una sola línea.
     */
    public void writeSequence(String sequence) {
        ensureDir();
        Path out = file("sequence.txt");
        // Normalizar espacios: una secuencia limpia simplifica regex
        String normalized = sequence.trim().replaceAll("\\s+", " ") + System.lineSeparator();

        try {
            Files.writeString(out, normalized, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo escribir " + out, e);
        }
    }

    /**
     * Escribe un resumen completo de la corrida.
     * Esto reemplaza los println del Main (o los complementa).
     */
    public void writeSummary(long runMs,
                             long startMs,
                             long endMs,
                             long[] delays,
                             Policy policy,
                             Monitor monitor,
                             NetState state) {

        ensureDir();

        int[] fired = monitor.getFiredCountSnapshot();
    int[] picks = monitor.getPolicyPickCountSnapshot();
    // NetState expone el Marking (inmutable). Pedimos una copia del arreglo mediante snapshot().
    int[] marking = state.getMarking().snapshot();

        long totalConflictFires = fired[2] + fired[5] + fired[7];

        StringBuilder sb = new StringBuilder(2048);
        sb.append("=== FIN SIMULACIÓN ===\n");
        //sb.append("RunId: ").append(runId).append("\n");
        sb.append("Duración configurada (runMs): ").append(runMs).append(" ms\n");
        sb.append("Tiempo real: ").append(endMs - startMs).append(" ms\n");
        sb.append("Policy: ").append(policy.getClass().getSimpleName()).append("\n");
        sb.append("Delays temporales (ms) por transición: ").append(Arrays.toString(delays)).append("\n");
        sb.append("Marking final: ").append(Arrays.toString(marking)).append("\n\n");

        sb.append("=== MÉTRICAS DE DISPARO ===\n");
        for (int i = 0; i < fired.length; i++) {
            sb.append("T").append(i).append(" disparos: ").append(fired[i]).append("\n");
        }
        sb.append("\n");

        sb.append("=== CICLOS / INVARIANTES ===\n");
        sb.append("Ciclos inyectados (T0): ").append(fired[0]).append("\n");
        sb.append("Ciclos completados (T11): ").append(fired[11]).append("\n\n");

        sb.append("=== CONFLICTO (DISPAROS REALES) ===\n");
        sb.append("T2: ").append(fired[2]).append(" | T5: ").append(fired[5]).append(" | T7: ").append(fired[7]).append("\n\n");

        sb.append("=== CONFLICTO (DECISIONES DE POLÍTICA) ===\n");
        sb.append("Pick T2: ").append(picks[2]).append(" | Pick T5: ").append(picks[5]).append(" | Pick T7: ").append(picks[7]).append("\n");

        if (totalConflictFires > 0) {
            sb.append(String.format("Distribución real: T2=%.1f%%, T5=%.1f%%, T7=%.1f%%%n",
                    100.0 * fired[2] / totalConflictFires,
                    100.0 * fired[5] / totalConflictFires,
                    100.0 * fired[7] / totalConflictFires));
        }

        sb.append("\nStopFeeding activado (T0>=limit): ").append(fired[0] >= 200).append("\n");
        sb.append("Drain completado (T11>=limit): ").append(fired[11] >= 200).append("\n");

        Path out = file("summary.txt");
        try {
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo escribir " + out, e);
        }
    }
}
