#!/usr/bin/env bash
# Uso:  bash tools/check-tree.sh <tag> [core]
#
# Compila com javac (sem Gradle) contra as bibliotecas e build/classes/java/main.
#  - sem "core": compila a ÁRVORE INTEIRA (núcleo + todos os módulos) — mostra se uma mudança no núcleo quebrou algum módulo.
#  - com "core": compila só o núcleo (core, glyph, network, client, module, mixin, IsekaiMahou.java).
# <tag> separa a pasta de saída (build/treecheck/<tag>) para vários agentes rodarem ao mesmo tempo.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="${1:?informe uma tag, ex: a1}"
MODE="${2:-all}"
J="$ROOT/src/main/java/com/renanpontes/isekaimahou"
OUT="$ROOT/build/treecheck/$TAG"
JAVAC="$(ls -d "$HOME"/.gradle/jdks/*21*/bin/javac.exe 2>/dev/null | head -1)"
[ -x "$JAVAC" ] || { echo "javac 21 nao encontrado"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT/classes"
CP="$(cat "$ROOT/build/classpath.txt");$(cygpath -w "$ROOT/build/classes/java/main")"
: > "$OUT/sources.txt"
if [ "$MODE" = "core" ]; then
  { find "$J/core" "$J/glyph" "$J/network" "$J/client" "$J/module" "$J/mixin" -name '*.java'; echo "$J/IsekaiMahou.java"; } > "$OUT/list.txt"
else
  find "$J" -name '*.java' > "$OUT/list.txt"
fi
cygpath -w -f "$OUT/list.txt" | sed 's/\\/\\\\/g; s/^/"/; s/$/"/' > "$OUT/sources.txt"
echo "== javac ($MODE): $(wc -l < "$OUT/sources.txt") arquivos"
"$JAVAC" -encoding UTF-8 -proc:none -Xmaxerrs 400 -nowarn -d "$(cygpath -w "$OUT/classes")" -cp "$CP" @"$(cygpath -w "$OUT/sources.txt")"
echo "== COMPILOU OK ($MODE)"
