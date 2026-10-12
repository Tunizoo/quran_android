# -*- coding: utf-8 -*-
"""
Adds the `translations` table to divine_names.db from translations/<lang>.py (meaning, note and
evidence reference of every name/attribute in another language; the Quranic evidence itself stays
in Arabic). Run after gen_divine_names.py, or alone to refresh the translations only:
    python gen_translations.py            # all languages
    python gen_translations.py --check en # validate one language, write nothing
"""
import glob, importlib.util, json, os, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
TR = os.path.join(HERE, "translations")
OUT = os.path.join(HERE, "divine_names.db")
SOURCE = json.load(open(os.path.join(TR, "source_ar.json"), encoding="utf-8"))
IDS = [e["id"] for e in SOURCE["entries"]]
NOTES = {e["id"]: e["note"] for e in SOURCE["entries"]}


def load(path):
    spec = importlib.util.spec_from_file_location("tr_" + os.path.basename(path)[:-3], path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def check(mod):
    problems = []
    for key in ("LANG", "SOURCES_NAMES", "SOURCES_ATTRIBUTES", "ENTRIES"):
        if not hasattr(mod, key):
            problems.append(f"missing {key}")
    if problems:
        return problems
    entries = mod.ENTRIES
    for id in IDS:
        e = entries.get(id)
        if e is None:
            problems.append(f"{id}: missing")
            continue
        for field in ("meaning", "evidence_ref", "note"):
            if field not in e:
                problems.append(f"{id}: missing field {field}")
        if not e.get("meaning", "").strip():
            problems.append(f"{id}: empty meaning")
        if not e.get("evidence_ref", "").strip():
            problems.append(f"{id}: empty evidence_ref")
        if bool(NOTES[id].strip()) != bool(e.get("note", "").strip()):
            problems.append(f"{id}: note should be {'non-empty' if NOTES[id].strip() else 'empty'}")
    for extra in sorted(set(entries) - set(IDS)):
        problems.append(f"{extra}: unknown id")
    return problems


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "--check":
        mod = load(os.path.join(TR, sys.argv[2] + ".py"))
        problems = check(mod)
        print("\n".join(problems) if problems else f"{sys.argv[2]}: OK ({len(mod.ENTRIES)} entries)")
        sys.exit(1 if problems else 0)

    db = sqlite3.connect(OUT)
    db.executescript("""
    DROP TABLE IF EXISTS translations;
    CREATE TABLE translations(
      name_id TEXT NOT NULL, lang TEXT NOT NULL, meaning TEXT NOT NULL, evidence_ref TEXT NOT NULL,
      note TEXT NOT NULL, sources TEXT NOT NULL,
      PRIMARY KEY(name_id, lang)
    );
    """)
    kinds = {e["id"]: e["kind"] for e in SOURCE["entries"]}
    total = 0
    for path in sorted(glob.glob(os.path.join(TR, "*.py"))):
        mod = load(path)
        problems = check(mod)
        if problems:
            print(f"{mod.LANG}: {len(problems)} problems, skipped:\n  " + "\n  ".join(problems[:20]))
            continue
        for id, e in mod.ENTRIES.items():
            sources = mod.SOURCES_ATTRIBUTES if kinds[id] == "attribute" else mod.SOURCES_NAMES
            db.execute("insert into translations values(?,?,?,?,?,?)",
                       (id, mod.LANG, e["meaning"].strip(), e["evidence_ref"].strip(), e["note"].strip(), sources))
            total += 1
        print(mod.LANG, len(mod.ENTRIES), "entries")
    db.execute("insert or replace into meta values('version','3')")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print("translations:", total, "db size:", os.path.getsize(OUT))


if __name__ == "__main__":
    main()
