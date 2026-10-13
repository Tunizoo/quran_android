# -*- coding: utf-8 -*-
"""
Writes the translated strings.xml files of the new feature modules (wird, divinenames, tajweed)
from the dictionaries in translations_*.py. Run: python gen_strings.py
"""
import glob, importlib, os, re
from xml.sax.saxutils import escape

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
MODULES = {
    "wird": os.path.join(ROOT, "feature", "wird", "src", "main", "res"),
    "divinenames": os.path.join(ROOT, "feature", "divinenames", "src", "main", "res"),
    "tajweed": os.path.join(ROOT, "feature", "tajweed", "src", "main", "res"),
    "memorization": os.path.join(ROOT, "feature", "memorization", "src", "main", "res"),
}


def android_escape(value):
    s = escape(value, {'"': "\\\"", "'": "\\'"})
    return s


def write(module, lang, strings, plurals):
    folder = os.path.join(MODULES[module], "values-" + lang)
    os.makedirs(folder, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name, value in strings.items():
        lines.append(f'  <string name="{name}">{android_escape(value)}</string>')
    for name, items in plurals.items():
        lines.append(f'  <plurals name="{name}">')
        for quantity, value in items.items():
            lines.append(f'    <item quantity="{quantity}">{android_escape(value)}</item>')
        lines.append("  </plurals>")
    lines.append("</resources>")
    with open(os.path.join(folder, "strings.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", module, lang, len(strings), "strings", len(plurals), "plurals")


def english_keys(module):
    """Names of the strings and plurals in the module's default (English) resources."""
    text = open(os.path.join(MODULES[module], "values", "strings.xml"), encoding="utf-8").read()
    return set(re.findall(r'<(?:string|plurals) name="([^"]+)"', text))


def main():
    for path in sorted(glob.glob(os.path.join(HERE, "translations_*.py"))):
        name = os.path.splitext(os.path.basename(path))[0]
        module = importlib.import_module(name)
        lang = module.LANG
        modules = dict(module.MODULES)
        # feature/memorization strings live in memo_strings.py, keyed by language
        import memo_strings
        if lang in memo_strings.MEMO:
            modules["memorization"] = (memo_strings.MEMO[lang], memo_strings.MEMO_PLURALS.get(lang, {}))
        # gamification strings of feature/wird live in gamification_strings.py
        import gamification_strings
        if lang in gamification_strings.GAMIFICATION and "wird" in modules:
            wird_strings, wird_plurals = modules["wird"]
            modules["wird"] = (
                {**wird_strings, **gamification_strings.GAMIFICATION[lang]},
                {**wird_plurals, **gamification_strings.GAMIFICATION_PLURALS.get(lang, {})},
            )
        for mod_name, (strings, plurals) in modules.items():
            expected = english_keys(mod_name)
            provided = set(strings) | set(plurals)
            missing = sorted(expected - provided)
            extra = sorted(provided - expected)
            if missing or extra:
                print(f"  {lang}/{mod_name}: missing {missing} extra {extra}")
            write(mod_name, lang, strings, plurals)


if __name__ == "__main__":
    main()
