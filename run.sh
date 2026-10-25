#!/bin/sh
# The compiler: ./run.sh -I spec/include prog.c -o prog.s && gcc prog.s -o prog   (x86-64 Linux, System V ABI)
# ./run.sh --fetch-jars downloads Clojure 1.12.6 and its two spec jars into ./lib (or set CLJ_LIB).
HERE="$(dirname "$(readlink -f "$0")")"; LIB="${CLJ_LIB:-$HERE/lib}"
if [ "$1" = "--fetch-jars" ]; then
  mkdir -p "$LIB" && cd "$LIB" || exit 1
  for j in org/clojure/clojure/1.12.6/clojure-1.12.6.jar org/clojure/spec.alpha/0.5.238/spec.alpha-0.5.238.jar org/clojure/core.specs.alpha/0.4.74/core.specs.alpha-0.4.74.jar; do
    curl -fsSLO "https://repo1.maven.org/maven2/$j" || exit 1; done; exit 0
fi
exec "$HERE/tools/clj-run" -m ccc.main "$@"
