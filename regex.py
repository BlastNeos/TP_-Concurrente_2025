import re
from pathlib import Path

# === Configuración ===
file_path = Path("logs") / "sequence.txt"

with open(file_path, "r", encoding="utf-8") as f:
    data = f.read().strip()

# === Patrón general de un invariante de transición ===
# Captura un ciclo completo y permite "ruido" entre transiciones
cycle_pattern = re.compile(
    r'(T00).*?(T01).*?'
    r'('
    r'(T02).*?(T03).*?(T04)'
    r'|(T05).*?(T06)'
    r'|(T07).*?(T08).*?(T09).*?(T10)'
    r')'
    r'.*?(T11)',
    re.DOTALL
)

# === Patrones específicos de cada IT ===
it_patterns = [
    (re.compile(r'T00.*?T01.*?T02.*?T03.*?T04.*?T11', re.DOTALL),
     "IT1: Rama superior (T2-T3-T4)"),
    (re.compile(r'T00.*?T01.*?T05.*?T06.*?T11', re.DOTALL),
     "IT2: Rama media (T5-T6)"),
    (re.compile(r'T00.*?T01.*?T07.*?T08.*?T09.*?T10.*?T11', re.DOTALL),
     "IT3: Rama inferior (T7-T8-T9-T10)")
]

counts = [0] * len(it_patterns)
total = 0

# === Consumo secuencial ===
while True:
    match = cycle_pattern.search(data)
    if not match:
        break

    cycle = match.group(0)

    for i, (pat, _) in enumerate(it_patterns):
        if pat.fullmatch(cycle):
            counts[i] += 1
            break

    # eliminar el ciclo encontrado
    data = data[:match.start()] + data[match.end():]
    total += 1

# === Resultados ===
print(f"\nTotal de invariantes encontrados: {total}\n")

for count, (_, name) in zip(counts, it_patterns):
    print(f"{name}: {count}")

if data.strip():
    print("\n❌ ERROR: quedaron transiciones sin consumir")
    print("Sobrante:", data)
else:
    print("\n✅ OK: la secuencia cumple los invariantes de transición")
