"""Genera el catàleg de l'avatar per a l'app a partir de scripts/avatar/cataleg.py.

    python3 scripts/genera_avatar.py                      # escriu el Kotlin de l'app
    python3 scripts/genera_avatar.py --sql                # escriu les peces per a la migració
    python3 scripts/genera_avatar.py --previsualitza f.html  # un full amb combinacions, per revisar els dibuixos
"""

import argparse
import sys
from pathlib import Path

ARREL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ARREL / "scripts"))

from avatar import cataleg as c  # noqa: E402

KOTLIN = ARREL / "app/src/main/kotlin/cat/andana947/avatar/CatalegGenerat.kt"


def enfosqueix(hexa, factor=0.82):
    r, g, b = (int(hexa[i:i + 2], 16) for i in (1, 3, 5))
    return "#{:02X}{:02X}{:02X}".format(*(round(v * factor) for v in (r, g, b)))


def comprova():
    ids = [p["id"] for p in c.PECES]
    repetits = {i for i in ids if ids.count(i) > 1}
    if repetits:
        raise SystemExit(f"Peces repetides: {sorted(repetits)}")
    ranures = {r for r, _ in c.RANURES} | {"cara", "bigoti"}
    for p in c.PECES:
        if p["ranura"] not in ranures:
            raise SystemExit(f"{p['id']}: ranura desconeguda {p['ranura']}")
        for capa in p["capes"]:
            for t in capa["d"].split():
                if t[0].isalpha() and t[0] not in "MLCQZ":
                    raise SystemExit(f"{p['id']}: ordre de camí no admesa {t[0]}")
    for combinacio in (c.DIRECTOR, c.INICIAL):
        for clau, valor in combinacio.items():
            if clau in ("cos", "pell", "color_cabell"):
                continue
            if valor not in ids:
                raise SystemExit(f"Peça desconeguda a una combinació: {valor}")


# --- Kotlin ---------------------------------------------------------------------------------------------------------


def kt_text(v):
    return "null" if v is None else '"' + v + '"'


def kt_capa(capa):
    return f'Capa({capa["z"]}, "{capa["d"]}", {kt_text(capa["fill"])}, {kt_text(capa["stroke"])}, {capa["w"]}f)'


def kt_nom(id_):
    return f"R.string.avatar_{id_}"


def kotlin():
    l = [
        "// Generat per scripts/genera_avatar.py a partir de scripts/avatar/cataleg.py. No l'editis a mà.",
        "package cat.andana947.avatar",
        "",
        "import cat.andana947.R",
        "",
        "internal object CatalegGenerat {",
        "    val cos: List<Capa> = listOf(",
        *[f"        {kt_capa(x)}," for x in c.COS],
        "    )",
        "",
        "    val peces: List<Peca> = listOf(",
    ]
    for p in c.PECES:
        nom = "null" if p["npc"] else kt_nom(p["id"])
        l.append(f'        Peca("{p["id"]}", "{p["ranura"]}", {nom}, {str(p["npc"]).lower()}, listOf(')
        l.extend(f"            {kt_capa(x)}," for x in p["capes"])
        l.append("        )),")
    l += [
        "    )",
        "",
        "    val tonsPell: List<ColorAvatar> = listOf(",
        *[f'        ColorAvatar("{i}", 0xFF{h[1:]}, {kt_nom(i)}),' for i, h in c.TONS_PELL],
        "    )",
        "",
        "    val colorsCabell: List<ColorAvatar> = listOf(",
        *[f'        ColorAvatar("{i}", 0xFF{h[1:]}, {kt_nom(i)}),' for i, h in c.COLORS_CABELL],
        "    )",
        "",
        "    val ranures: List<Ranura> = listOf(",
        *[f'        Ranura("{r}", {str(o).lower()}, R.string.avatar_ranura_{r}),' for r, o in c.RANURES],
        "    )",
        "",
        f"    val director = {kt_avatar(c.DIRECTOR)}",
        "",
        f"    val inicial = {kt_avatar(c.INICIAL)}",
        "}",
        "",
    ]
    return "\n".join(l)


def kt_avatar(a):
    camps = {
        "cos": "cos", "pell": "pell", "cabell": "cabell", "color_cabell": "colorCabell", "cap": "cap",
        "tronc": "tronc", "coll": "coll", "ulls": "ulls", "orelles": "orelles", "bigoti": "bigoti",
    }
    parts = [f'{camps[k]} = "{v}"' for k, v in a.items()]
    return "Avatar(" + ", ".join(parts) + ")"


# --- SQL ------------------------------------------------------------------------------------------------------------


def sql():
    files = [f"    ('{p['id']}', '{p['ranura']}', {str(p['npc']).lower()})" for p in c.PECES]
    files += [f"    ('{i}', 'pell', false)" for i, _ in c.TONS_PELL]
    files += [f"    ('{i}', 'color_cabell', false)" for i, _ in c.COLORS_CABELL]
    return "insert into public.peces_avatar (id, ranura, nomes_npc) values\n" + ",\n".join(files) + ";\n"


# --- Previsualització -----------------------------------------------------------------------------------------------


def capes_de(avatar, cara=None):
    peces = {p["id"]: p for p in c.PECES}
    capes = list(c.COS)
    cara = cara or ("cara_noia" if avatar.get("cos") == "noia" else "cara_noi")
    for clau in ("cabell", "cap", "tronc", "coll", "ulls", "orelles", "bigoti"):
        if avatar.get(clau):
            capes += peces[avatar[clau]]["capes"]
    capes += peces[cara]["capes"]
    return sorted(capes, key=lambda x: x["z"])


def svg(avatar, mida=160, cara=None):
    pell = dict(c.TONS_PELL)[avatar["pell"]]
    cabell = dict(c.COLORS_CABELL)[avatar["color_cabell"]]
    rols = {
        "pell": pell, "pell_ombra": enfosqueix(pell), "cabell": cabell, "cabell_ombra": enfosqueix(cabell),
        "tinta": "#111111",
    }
    cos = []
    for x in capes_de(avatar, cara):
        fill = rols.get(x["fill"], x["fill"]) or "none"
        stroke = rols.get(x["stroke"], x["stroke"]) or "none"
        cos.append(
            f'<path d="{x["d"]}" fill="{fill}" stroke="{stroke}" stroke-width="{x["w"]}" '
            f'stroke-linejoin="round" stroke-linecap="round"/>'
        )
    return f'<svg viewBox="0 0 200 200" width="{mida}" height="{mida}">{"".join(cos)}</svg>'


def previsualitza(cami):
    caselles = []

    def casella(titol, avatar, cara=None):
        caselles.append(f"<figure>{svg(avatar, cara=cara)}<figcaption>{titol}</figcaption></figure>")

    casella("director", c.DIRECTOR, "cara_director")
    casella("director, ullet", c.DIRECTOR, "cara_director_ullet")
    casella("inicial noi", c.INICIAL)
    casella("inicial noia", {**c.INICIAL, "cos": "noia", "cabell": "cabell_llarg"})
    for p in c.PECES:
        if p["npc"]:
            continue
        a = {**c.INICIAL, p["ranura"]: p["id"]}
        if p["ranura"] == "cabell" and p["id"] in ("cabell_llarg", "cabell_cua", "cabell_monyo"):
            a["cos"] = "noia"
        casella(p["id"], a)
    for i, _ in c.TONS_PELL:
        casella(i, {**c.INICIAL, "pell": i, "cabell": "cabell_arrissat", "color_cabell": "cabell_negre"})
    for i, _ in c.COLORS_CABELL:
        casella(i, {**c.INICIAL, "cos": "noia", "cabell": "cabell_cua", "color_cabell": i})
    casella("combinació 1", {"cos": "noia", "pell": "pell_4", "cabell": "cabell_llarg", "color_cabell": "cabell_negre",
                             "cap": "cap_llana", "tronc": "tronc_abric", "coll": "coll_bufanda"})
    casella("combinació 2", {"cos": "noi", "pell": "pell_1", "cabell": "cabell_curt", "color_cabell": "cabell_pelroig",
                             "cap": "cap_beisbol", "tronc": "tronc_dessuadora", "orelles": "orelles_auriculars"})
    casella("combinació 3", {"cos": "noi", "pell": "pell_3", "cabell": "cabell_rapat", "color_cabell": "cabell_negre",
                             "cap": "cap_casc", "tronc": "tronc_armilla", "ulls": "ulls_sol"})
    casella("combinació 4", {"cos": "noia", "pell": "pell_5", "cabell": "cabell_monyo", "color_cabell": "cabell_magenta",
                             "cap": "cap_barretina", "tronc": "tronc_texana", "ulls": "ulls_rodones"})
    html = (
        "<!doctype html><meta charset=utf-8><style>body{font:12px sans-serif;margin:16px;background:#fff}"
        "main{display:grid;grid-template-columns:repeat(6,170px);gap:8px}figure{margin:0;text-align:center;"
        "background:#F5F5F5;border:1.5px solid #111}</style><main>" + "".join(caselles) + "</main>"
    )
    Path(cami).write_text(html, encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sql", action="store_true")
    ap.add_argument("--previsualitza")
    args = ap.parse_args()
    comprova()
    if args.sql:
        sys.stdout.write(sql())
    elif args.previsualitza:
        previsualitza(args.previsualitza)
    else:
        KOTLIN.parent.mkdir(parents=True, exist_ok=True)
        KOTLIN.write_text(kotlin(), encoding="utf-8")
        print(f"Escrit {KOTLIN.relative_to(ARREL)}")


if __name__ == "__main__":
    main()
