# tools/

## translate.py — locale generator

Generates the per-locale `*.json` files under
`common/src/main/resources/assets/betterbrightness/lang/` from the canonical
English source `en_us.json`, plus a deterministic upside-down `en_ud.json`.

The committed locale files are the build artifact — the build never runs this
script. `en_us.json` is the source of truth and is never modified. The joke
locales `en_pt` / `lol_us` are maintained by hand and are not generated here.

### Setup

```sh
pip install deep-translator      # only needed for an actual translation run
```

(On externally-managed distros use a venv: `python -m venv .venv && .venv/bin/pip install deep-translator`,
then run the script with `.venv/bin/python`.)

### Usage

```sh
python tools/translate.py                 # translate every mappable locale + en_ud
python tools/translate.py --skip-existing # don't re-translate locales already present
python tools/translate.py --selftest      # offline tests only — no network, no deps
```

`--selftest` verifies placeholder mask→translate→unmask round-trips
(`"Brightness: %s%%"`) and that the `en_ud` flip is reversible on ASCII letters.
It needs no network and no `deep-translator`, so it is safe to run in CI.

The run is idempotent (per-locale `try/except`, one failure never aborts the
rest) and prints an `OK / SKIPPED / FAILED` summary at the end. Format tokens
(`%s`, `%%`, `%1$s`) are masked before translation and restored afterward; if a
token is lost in translation that key falls back to English.
