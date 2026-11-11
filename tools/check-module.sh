#!/usr/bin/env bash
# Uso:  bash tools/check-module.sh <id_do_modulo>
#
# Compila SÓ o módulo informado (src/main/java/com/renanpontes/isekaimahou/feature/<id>) com javac,
# contra a fundação já compilada (build/classes/java/main) e as bibliotecas (build/classpath.txt).
# Não usa o Gradle, então vários módulos podem ser checados ao mesmo tempo sem conflito.
# Também valida todos os JSON do módulo (traduções e recursos listados em tools/json-roots do módulo).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MOD="${1:?informe o id do modulo, ex: fire_water}"
SRC="$ROOT/src/main/java/com/renanpontes/isekaimahou/feature/$MOD"
OUT="$ROOT/build/modcheck/$MOD"
JAVAC="$(ls -d "$HOME"/.gradle/jdks/*21*/bin/javac.exe 2>/dev/null | head -1)"
[ -x "$JAVAC" ] || { echo "javac 21 nao encontrado em ~/.gradle/jdks"; exit 2; }
[ -f "$ROOT/build/classpath.txt" ] || { echo "Falta build/classpath.txt (o coordenador roda: ./gradlew printClasspath)"; exit 2; }

rm -rf "$OUT"; mkdir -p "$OUT"
CP="$(cat "$ROOT/build/classpath.txt");$(cygpath -w "$ROOT/build/classes/java/main")"
: > "$OUT/sources.txt"
while IFS= read -r f; do
  printf '"%s"\n' "$(cygpath -w "$f" | sed 's/\\/\\\\/g')" >> "$OUT/sources.txt"
done < <(find "$SRC" -name '*.java')

echo "== javac ($MOD): $(wc -l < "$OUT/sources.txt") arquivos"
"$JAVAC" -encoding UTF-8 -proc:none -Xmaxerrs 200 -nowarn -d "$(cygpath -w "$OUT")" -cp "$CP" @"$(cygpath -w "$OUT/sources.txt")"
echo "== javac OK"

echo "== JSON"
py -I "$ROOT/tools/check-json.py" "$ROOT/src/main/lang/$MOD" "$ROOT/src/main/resources"
echo "== Avisos (traducoes/texturas)"
py -I "$ROOT/tools/check-assets.py" "$ROOT" "$MOD" || true
echo "== TUDO OK ($MOD)"
