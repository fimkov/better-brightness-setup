#!/usr/bin/env python3
"""Auto-generate Minecraft locale files for Better Brightness Setup.

Reads the canonical English locale (en_us.json) and produces a translated
<locale>.json for every Minecraft locale we can map to a Google Translate
language code, plus a deterministic upside-down en_ud.json.

Usage:
    python tools/translate.py                # translate all mappable locales
    python tools/translate.py --skip-existing  # don't re-translate existing files
    python tools/translate.py --selftest       # offline tests, no network

Requires: pip install deep-translator   (only for the actual translation run;
the --selftest mode and en_ud generation need no network and no extra deps.)

The committed locale files are the build artifact; the build never runs this
script. en_us.json is the source of truth and is never modified.
"""

import argparse
import json
import os
import re
import sys
import time

# --------------------------------------------------------------------------
# Paths (robust to the current working directory)
# --------------------------------------------------------------------------
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
LANG_DIR = os.path.join(
    REPO_ROOT,
    "common", "src", "main", "resources",
    "assets", "betterbrightness", "lang",
)
EN_US = os.path.join(LANG_DIR, "en_us.json")

# Locales written/curated by hand -- never machine-generated here (would clobber the corrections).
# en_us is the source; en_pt/lol_us are the joke locales; ru_ru was hand-fixed (Google mistranslated
# "Done" -> "Сделанный" and "Clear" -> "Прозрачный").
EXCLUDE = {"en_us", "en_pt", "lol_us", "ru_ru"}

# --------------------------------------------------------------------------
# Minecraft locale code -> Google Translate language code.
# Locales with no sensible Google code are simply absent (and logged).
# --------------------------------------------------------------------------
MC_LOCALE_TO_GOOGLE = {
    "af_za": "af",
    "ar_sa": "ar",
    "az_az": "az",
    "be_by": "be",
    "bg_bg": "bg",
    "bs_ba": "bs",
    "ca_es": "ca",
    "cs_cz": "cs",
    "cy_gb": "cy",
    "da_dk": "da",
    "de_at": "de",
    "de_ch": "de",
    "de_de": "de",
    "el_gr": "el",
    "en_au": "en",
    "en_ca": "en",
    "en_gb": "en",
    "en_nz": "en",
    "eo_uy": "eo",
    "es_ar": "es",
    "es_cl": "es",
    "es_ec": "es",
    "es_es": "es",
    "es_mx": "es",
    "es_uy": "es",
    "es_ve": "es",
    "et_ee": "et",
    "eu_es": "eu",
    "fa_ir": "fa",
    "fi_fi": "fi",
    "fil_ph": "tl",
    "fo_fo": "fo",
    "fr_ca": "fr",
    "fr_fr": "fr",
    "ga_ie": "ga",
    "gd_gb": "gd",
    "gl_es": "gl",
    "he_il": "iw",
    "hi_in": "hi",
    "hr_hr": "hr",
    "hu_hu": "hu",
    "hy_am": "hy",
    "id_id": "id",
    "is_is": "is",
    "it_it": "it",
    "ja_jp": "ja",
    "ka_ge": "ka",
    "kk_kz": "kk",
    "kn_in": "kn",
    "ko_kr": "ko",
    "la_la": "la",
    "lb_lu": "lb",
    "lt_lt": "lt",
    "lv_lv": "lv",
    "mk_mk": "mk",
    "mn_mn": "mn",
    "ms_my": "ms",
    "mt_mt": "mt",
    "nb_no": "no",
    "nl_be": "nl",
    "nl_nl": "nl",
    "nn_no": "no",
    "pl_pl": "pl",
    "pt_br": "pt",
    "pt_pt": "pt",
    "ro_ro": "ro",
    "ru_ru": "ru",
    "sk_sk": "sk",
    "sl_si": "sl",
    "sq_al": "sq",
    "sr_sp": "sr",
    "sv_se": "sv",
    "sw_ke": "sw",
    "ta_in": "ta",
    "te_in": "te",
    "th_th": "th",
    "tr_tr": "tr",
    "uk_ua": "uk",
    "vi_vn": "vi",
    "yi_de": "yi",
    "zh_cn": "zh-CN",
    "zh_tw": "zh-TW",
}

# --------------------------------------------------------------------------
# Placeholder protection
# --------------------------------------------------------------------------
# Match Minecraft / Java format tokens, longest-first so e.g. %1$s wins over %s:
#   %1$s  positional argument
#   %s    string argument (also %d, %f, ...)
#   %%    literal percent
TOKEN_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]|%%")

# Sentinel template. Pure ASCII, no spaces, no punctuation that translation
# engines like to "fix". Tested to survive Google Translate unchanged.
def _sentinel(i):
    return "ZZ%dZZ" % i


def mask(text):
    """Replace each format token with an opaque sentinel.

    Returns (masked_text, tokens) where tokens[i] is the original token that
    sentinel ZZ{i}ZZ stands for.
    """
    tokens = []

    def repl(m):
        idx = len(tokens)
        tokens.append(m.group(0))
        return _sentinel(idx)

    return TOKEN_RE.sub(repl, text), tokens


def unmask(text, tokens):
    """Restore original tokens. Tolerant of case/spacing changes a translator
    may introduce around the sentinel (e.g. 'zz0zz', 'ZZ 0 ZZ')."""
    for i, tok in enumerate(tokens):
        pat = re.compile(r"[Zz]\s*[Zz]\s*%d\s*[Zz]\s*[Zz]" % i)
        text = pat.sub(lambda _m, t=tok: t, text)
    return text


def tokens_of(text):
    """Multiset (sorted list) of format tokens in a string, for verification."""
    return sorted(TOKEN_RE.findall(text))


# --------------------------------------------------------------------------
# Upside-down (en_ud) generation -- deterministic, no network.
# --------------------------------------------------------------------------
_UD_MAP = {
    "a": "ɐ", "b": "q", "c": "ɔ", "d": "p", "e": "ǝ",
    "f": "ɟ", "g": "ƃ", "h": "ɥ", "i": "ᴉ", "j": "ɾ",
    "k": "ʞ", "l": "l", "m": "ɯ", "n": "u", "o": "o",
    "p": "d", "q": "b", "r": "ɹ", "s": "s", "t": "ʇ",
    "u": "n", "v": "ʌ", "w": "ʍ", "x": "x", "y": "ʎ", "z": "z",
    "A": "∀", "B": "੧", "C": "Ɔ", "D": "ᗡ", "E": "Ǝ",
    "F": "Ⅎ", "G": "פ", "H": "H", "I": "I", "J": "ſ",
    "K": "⋊", "L": "˥", "M": "W", "N": "N", "O": "O",
    "P": "Ԁ", "Q": "Ό", "R": "ᴚ", "S": "S", "T": "┴",
    "U": "∩", "V": "Λ", "W": "M", "X": "X", "Y": "⅄", "Z": "Z",
    "0": "0", "1": "Ɩ", "2": "ᘔ", "3": "Ɛ", "4": "ㄣ",
    "5": "ϛ", "6": "9", "7": "ㄥ", "8": "8", "9": "6",
    ".": "˙", ",": "'", "'": ",", "\"": ",,", "`": "ˌ",
    "?": "¿", "!": "¡", "(": ")", ")": "(", "[": "]", "]": "[",
    "{": "}", "}": "{", "<": ">", ">": "<", "&": "⅋", "_": "‾",
    ";": "؛",
}


def flip(text):
    """Deterministic upside-down transform of a single text segment.

    Maps characters to their unicode upside-down equivalents and reverses the
    string (MC's own en_ud convention). Unknown characters pass through.
    """
    return "".join(_UD_MAP.get(ch, ch) for ch in reversed(text))


def make_en_ud(value):
    """Upside-down a value while leaving MC format tokens intact.

    Tokens are masked out, the surrounding text is flipped, then the original
    tokens are restored in place (token positions are flipped left<->right to
    match the reversed text, so they end up where a reader expects them)."""
    masked, tokens = mask(value)
    if not tokens:
        return flip(value)
    # Split on sentinels, flip each text chunk, reverse chunk order, and weave
    # the tokens back in (reversed) so token order matches the flipped text.
    parts = re.split(r"(ZZ\d+ZZ)", masked)
    out = []
    for part in reversed(parts):
        m = re.fullmatch(r"ZZ(\d+)ZZ", part)
        if m:
            out.append(tokens[int(m.group(1))])
        else:
            out.append(flip(part))
    return "".join(out)


# --------------------------------------------------------------------------
# JSON I/O -- preserve en_us key order exactly.
# --------------------------------------------------------------------------
def load_en_us():
    with open(EN_US, "r", encoding="utf-8") as f:
        # object_pairs_hook keeps insertion order (it already is in py3.7+,
        # but be explicit).
        return json.load(f)


def write_locale(locale, mapping):
    path = os.path.join(LANG_DIR, locale + ".json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(mapping, f, ensure_ascii=False, indent=4)
        f.write("\n")
    return path


# --------------------------------------------------------------------------
# Translation of one locale
# --------------------------------------------------------------------------
def translate_locale(en, mc_locale, google_code, retries=2, log=print):
    """Translate every value of `en` into `google_code`. Returns dict with
    the same keys/order. On a per-key token loss, falls back to English for
    that key (and logs)."""
    from deep_translator import GoogleTranslator

    keys = list(en.keys())
    masked_inputs = []
    token_lists = []
    for k in keys:
        m, toks = mask(en[k])
        masked_inputs.append(m)
        token_lists.append(toks)

    translator = GoogleTranslator(source="en", target=google_code)

    translated = None
    last_err = None
    for attempt in range(retries + 1):
        try:
            # Prefer one batched call per locale to be gentle on the free API.
            translated = translator.translate_batch(masked_inputs)
            break
        except Exception as e:  # noqa: BLE001 - transient API errors
            last_err = e
            if attempt < retries:
                time.sleep(2.0 * (attempt + 1))
            else:
                # Last resort: try one-by-one (some texts may individually
                # succeed even if the batch tripped a rate limit).
                try:
                    translated = [translator.translate(s) for s in masked_inputs]
                    break
                except Exception as e2:  # noqa: BLE001
                    last_err = e2
    if translated is None:
        raise RuntimeError("translation failed: %r" % (last_err,))

    out = {}
    for i, k in enumerate(keys):
        raw = translated[i]
        if raw is None or raw == "":
            # Translator returned nothing usable -> keep English.
            log("    [%s] empty translation for %r, using English" % (mc_locale, k))
            out[k] = en[k]
            continue
        restored = unmask(raw, token_lists[i])
        if tokens_of(restored) != tokens_of(en[k]):
            log("    [%s] token loss on %r (got %r) -> English fallback"
                % (mc_locale, k, restored))
            out[k] = en[k]
        else:
            out[k] = restored
    return out


# --------------------------------------------------------------------------
# Self-test (offline)
# --------------------------------------------------------------------------
def selftest():
    failures = []

    # 1) mask -> identity "translate" -> unmask round-trips.
    sample = "Brightness: %s%%"
    masked, tokens = mask(sample)
    assert "%" not in masked, "mask left a %% behind: %r" % masked
    assert tokens == ["%s", "%%"], "unexpected tokens: %r" % tokens
    restored = unmask(masked, tokens)
    if restored != sample:
        failures.append("round-trip identity: %r != %r" % (restored, sample))

    # 1b) tolerant of case/spacing changes a translator may introduce around
    # the sentinel itself (only the sentinel is mangled, not the prose).
    mangled = masked.replace("ZZ0ZZ", "zz 0 zz").replace("ZZ1ZZ", "zz 1 zz")
    restored2 = unmask(mangled, tokens)
    if restored2 != sample:
        failures.append("round-trip mangled: %r != %r" % (restored2, sample))

    # 1c) positional token survives.
    s2 = "Pick %1$s now"
    m2, t2 = mask(s2)
    assert t2 == ["%1$s"], "positional token not captured: %r" % t2
    if unmask(m2, t2) != s2:
        failures.append("positional round-trip: %r" % unmask(m2, t2))

    # 2) en_ud flip is reversible on ASCII letters: the char map is injective,
    # so building its inverse and undoing the reversal recovers the original.
    flipped_vals = list(_UD_MAP.values())
    assert len(flipped_vals) == len(set(flipped_vals)), \
        "_UD_MAP is not injective; flip would not be reversible"
    inv = {v: k for k, v in _UD_MAP.items()}

    def unflip(text):
        return "".join(inv.get(ch, ch) for ch in reversed(text))

    for word in ["Done", "Brightness", "Hidden", "abcXYZ"]:
        once = flip(word)
        back = unflip(once)
        if back != word:
            failures.append("flip not reversible: %r -> %r -> %r"
                            % (word, once, back))

    # 2b) en_ud keeps format tokens intact.
    ud = make_en_ud("Brightness: %s%%")
    if tokens_of(ud) != tokens_of("Brightness: %s%%"):
        failures.append("en_ud lost tokens: %r" % ud)

    if failures:
        print("SELFTEST FAILED:")
        for f in failures:
            print("  -", f)
        return 1
    print("SELFTEST PASSED")
    return 0


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------
def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-existing", action="store_true",
                        help="skip locales whose .json file already exists")
    parser.add_argument("--selftest", action="store_true",
                        help="run offline self-tests and exit (no network)")
    parser.add_argument("--sleep", type=float, default=1.0,
                        help="seconds to sleep between locales (default 1.0)")
    args = parser.parse_args(argv)

    if args.selftest:
        return selftest()

    en = load_en_us()
    print("Source: %s (%d keys)" % (EN_US, len(en)))

    ok, skipped, failed = [], [], []

    # --- Always (re)generate en_ud deterministically. ---
    ud_path = os.path.join(LANG_DIR, "en_ud.json")
    if args.skip_existing and os.path.exists(ud_path):
        skipped.append("en_ud")
        print("skip  en_ud (exists)")
    else:
        try:
            ud = {k: make_en_ud(v) for k, v in en.items()}
            write_locale("en_ud", ud)
            ok.append("en_ud")
            print("OK    en_ud (upside-down)")
        except Exception as e:  # noqa: BLE001
            failed.append(("en_ud", repr(e)))
            print("FAIL  en_ud: %r" % e)

    # --- Translated locales. ---
    targets = sorted(MC_LOCALE_TO_GOOGLE.items())
    for mc_locale, google_code in targets:
        if mc_locale in EXCLUDE:
            continue
        path = os.path.join(LANG_DIR, mc_locale + ".json")
        if args.skip_existing and os.path.exists(path):
            skipped.append(mc_locale)
            print("skip  %s (exists)" % mc_locale)
            continue
        try:
            mapping = translate_locale(en, mc_locale, google_code)
            write_locale(mc_locale, mapping)
            ok.append(mc_locale)
            print("OK    %s (%s)" % (mc_locale, google_code))
        except Exception as e:  # noqa: BLE001
            failed.append((mc_locale, repr(e)))
            print("FAIL  %s (%s): %r" % (mc_locale, google_code, e))
        time.sleep(args.sleep)

    # --- Summary. ---
    print("\n==== SUMMARY ====")
    print("OK      : %d  %s" % (len(ok), " ".join(sorted(ok))))
    print("SKIPPED : %d  %s" % (len(skipped), " ".join(sorted(skipped))))
    print("FAILED  : %d" % len(failed))
    for loc, err in failed:
        print("    %s: %s" % (loc, err))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
