#!/bin/bash
# Checks batch-mode error handling.
V="$(dirname "$(readlink -f "$0")")/.."
CLJ="$(dirname "$(readlink -f "$0")")/clj-run"
E=$V/.work/err
rm -rf "$E"; mkdir -p "$E"
printf 'int main() { return 1 +; }\n' > "$E/bad.c"
printf 'int main() { return 3; }\n' > "$E/good.c"
printf 'int main() { return x; }\n' > "$E/bad2.c"
printf '%s\n' "$E/bad.c $E/bad.s" "$E/good.c $E/good.s" "$E/bad2.c $E/bad2.s" > "$E/list"
touch "$E/bad.s"
cd "$V"
"$CLJ" -m ccc.main -I spec/include --batch "$E/list"; echo "batch exit=$?"
ls "$E"
"$CLJ" -m ccc.main -I spec/include "$E/bad.c" -o "$E/x.s"; echo "single exit=$?"
ls "$E"
