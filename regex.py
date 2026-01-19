import re
from pathlib import Path
from collections import Counter

# === Configuración ===
file_path = Path("logs") / "sequence.txt"

data = file_path.read_text(encoding="utf-8").strip()

# === Extraer transiciones (T00..T11) ===
tokens = re.findall(r"T\d\d", data)
c = Counter(tokens)

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
     "IT1: Rama superior (T0-T1-T2-T3-T4-T11)"),
    (re.compile(r'T00.*?T01.*?T05.*?T06.*?T11', re.DOTALL),
     "IT2: Rama media (T0-T1-T5-T6-T11)"),
    (re.compile(r'T00.*?T01.*?T07.*?T08.*?T09.*?T10.*?T11', re.DOTALL),
     "IT3: Rama inferior (T0-T1-T7-T8-T9-T10-T11)")
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
print(f"\nDel analisis con regex se encontraron {total} IT completas secuencialmente.")

for count, (_, name) in zip(counts, it_patterns):
    print(f"{name}: {count}")

"""
if data.strip():
    print("\n❌ ERROR: quedaron transiciones sin consumir")
    print("Sobrante:", data)
else:
    print("\n✅ OK: la secuencia cumple los invariantes de transición")
"""

# === Parámetros esperados ===
LIMIT = 200

# === Conteos base ===
t00 = c["T00"]
t11 = c["T11"]

# === Verificación de “balance” por rama (cada token que entra a una rama debe atravesar todas sus transiciones) ===
ok_branch_top = (c["T02"] == c["T03"] == c["T04"])
ok_branch_mid = (c["T05"] == c["T06"])
ok_branch_bot = (c["T07"] == c["T08"] == c["T09"] == c["T10"])

# === Cantidad de ciclos por rama (si la rama está balanceada, cualquiera de sus transiciones sirve como contador) ===
n1 = c["T02"]  # rama superior
n2 = c["T05"]  # rama media
n3 = c["T07"]  # rama inferior

# === Propiedades globales esperadas ===
ok_limit = (t00 == LIMIT and t11 == LIMIT)
ok_split = (n1 + n2 + n3 == t11)  # todo lo que sale por T11 viene de alguna rama
ok_entry_exit = (t00 == t11)      # en estado drenado, entradas = salidas

# === Reporte ===
"""
print("\n=== Conteo de disparos por transición ===")
for k in sorted(c.keys()):
    print(f"{k}: {c[k]}")

print("\n=== Chequeos ===")
print(f"- T00 == {LIMIT}: {t00} -> {'OK' if t00 == LIMIT else 'ERROR'}")
print(f"- T11 == {LIMIT}: {t11} -> {'OK' if t11 == LIMIT else 'ERROR'}")
print(f"- Entradas == Salidas (T00 == T11): {t00} vs {t11} -> {'OK' if ok_entry_exit else 'ERROR'}")
"""

print(f"\nAnalizando estructuralmente las transiciones se encontraron {c['T02'] + c['T05'] + c['T07']} IT completas.")
print(f"IT1: Rama superior (T0-T1-T2-T3-T4-T11): {c['T02']}")
print(f"IT2: Rama media (T0-T1-T5-T6-T11): {c['T05']}")
print(f"IT3: Rama inferior (T0-T1-T7-T8-T9-T10-T11): {c['T07']}")
"""
print("\n=== Conteo de invariantes (ciclos completados) ===")
print(f"- Ciclos por rama: n1={n1}, n2={n2}, n3={n3}")
print(f"- n1+n2+n3 == T11 -> {n1+n2+n3} == {t11} -> {'OK' if ok_split else 'ERROR'}")
"""
# === Resultado final ===
all_ok = ok_limit and ok_branch_top and ok_branch_mid and ok_branch_bot and ok_split and ok_entry_exit
print("\n✅ OK: se completaron 200 ciclos (invariantes) y se cumplen balances por rama"
      if all_ok
      else "\n❌ ERROR: no se cumplen las condiciones esperadas (revisar conteos / timeout / logs)")
