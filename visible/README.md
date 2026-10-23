# Visible tests

`W1/` to `W5/` hold about a quarter of each wave's tests. The hidden runner scores the other three quarters, which
come from the same sources and categories.

| File | What it holds |
|---|---|
| `<id>.c` | the program |
| `<id>.expected` | its exact stdout (bytes) |
| `<id>.exit` | its exit status |
| `manifest.json` | `{ "<id>": { "category": …, "exit": …, "timeoutMs": … } }` |

To run one test by hand, from your compiler directory (with `INC` = the provided headers' directory):

```sh
clj-run -m ccc.main -I "$INC" W1/w1-0001.c -o /tmp/t.s && gcc /tmp/t.s -o /tmp/t && /tmp/t > /tmp/t.out; echo $?
cmp /tmp/t.out W1/w1-0001.expected && cat W1/w1-0001.exit
```

A test passes when stdout matches byte for byte **and** the exit status matches. W6's programs are in `spec/bench/`.
