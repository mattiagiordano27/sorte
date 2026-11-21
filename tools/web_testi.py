"""Genera docs/app/testi.js (versione web) dai testi dell'app Android (Manual.kt, UiText.kt, Credits.kt): una sola fonte."""
import json, os, re
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # radice del progetto
SRC = f"{R}/app/src/main/java/mattiagiordano/sorte"

def items(body):
    """Divide il corpo di una listOf(...) negli elementi di primo livello."""
    out, depth, cur, i, s = [], 0, "", 0, body
    while i < len(s):
        c = s[i]
        if c == '"':
            j = i + 1
            while s[j] != '"': j += 2 if s[j] == '\\' else 1
            cur += s[i:j + 1]; i = j + 1; continue
        if c in "(": depth += 1
        if c in ")": depth -= 1
        if c == "," and depth == 0: out.append(cur.strip()); cur = ""
        else: cur += c
        i += 1
    if cur.strip(): out.append(cur.strip())
    return out

def strings(expr):
    """Gli argomenti stringa (concatenazioni con + unite)."""
    args = items(expr[expr.index("(") + 1: expr.rindex(")")])
    return ["".join(json.loads('"' + m + '"') for m in re.findall(r'"((?:[^"\\]|\\.)*)"', a)) for a in args]

def block_list(src, name):
    m = re.search(rf"private val {name} = listOf\(", src)
    i = m.end(); depth = 1; j = i
    while depth:
        c = src[j]
        if c == '"':
            j += 1
            while src[j] != '"': j += 2 if src[j] == '\\' else 1
        elif c == "(": depth += 1
        elif c == ")": depth -= 1
        j += 1
    out = []
    for it in items(src[i:j - 1]):
        kind = it.split("(")[0].replace("Block.", "").strip()
        out.append(["Symbols"] if kind == "Symbols" else [kind] + strings(it))
    return out

man = open(f"{SRC}/Manual.kt").read()
def symmap(name):
    body = re.search(rf"val {name} = mapOf\((.*?)\n    \)", man, re.S).group(1)
    return dict(re.findall(r'"([^"]+)" to "([^"]+)"', body))

ui = open(f"{SRC}/UiText.kt").read()
def uitext(name):
    body = ui[ui.index(f"private val {name} = UiText("):]
    body = body[:body.index("\n        )")]
    out = {}
    for k, v in re.findall(r'(\w+) = ((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', body):
        out[k] = "".join(json.loads('"' + s + '"') for s in re.findall(r'"((?:[^"\\]|\\.)*)"', v))
    return out

cr = open(f"{SRC}/Credits.kt").read()
author = re.search(r'AUTHOR = "([^"]+)"', cr).group(1); year = re.search(r'YEAR = "([^"]+)"', cr).group(1)
version = re.search(r'VERSION = "([^"]+)"', cr).group(1)
def credits(it):
    part = cr.split(") else listOf(")[0 if it else 1]
    lines = [json.loads('"' + s + '"') for s in re.findall(r'"((?:[^"\\]|\\.)*)"', part.split("listOf(")[-1])]
    return [l.replace("$YEAR", year).replace("$AUTHOR", author) for l in lines]

data = {
    "version": version,
    "manual": {"it": block_list(man, "IT"), "en": block_list(man, "EN")},
    "symIt": symmap("SYMBOL_IT"), "symEn": symmap("SYMBOL_EN"),
    "ui": {"it": uitext("IT"), "en": uitext("EN")},
    "credits": {"it": credits(True), "en": credits(False)},
}
open(f"{R}/docs/app/testi.js", "w").write("// generato da testi_da_app.py: non modificare a mano\nconst TESTI = " + json.dumps(data, ensure_ascii=False, indent=1) + ";\n")
print(len(data["manual"]["it"]), len(data["manual"]["en"]), "blocchi;", len(data["credits"]["it"]), "crediti")
