"""Catàleg de l'avatar: totes les peces, en vectorial. És la font única.

L'avatar és un bust dibuixat en un quadrat de 200 × 200. Cada peça té una o més capes; cada capa té una profunditat
(`z`), un camí SVG (només ordres absolutes M, L, C, Q i Z) i els colors d'ompliment i de vora. Els colors poden ser un
hexadecimal o un rol que posa el jugador: `pell`, `pell_ombra`, `cabell`, `cabell_ombra` o `tinta`.

`scripts/genera_avatar.py` en genera el catàleg de Kotlin de l'app i la llista de peces per al servidor.

Profunditats: 5 cabell de darrere · 10 coll i cos · 20-24 roba · 30 bufanda · 38 orelles · 40 cap · 50 cara ·
55 bigoti · 60 ulleres · 70 cabell de davant · 80-82 barrets · 90 auriculars.
"""

K = 0.5523  # aproximació d'una el·lipse amb quatre Bézier cúbiques


def f(x):
    return f"{x:g}"


def elipse(cx, cy, rx, ry):
    kx, ky = rx * K, ry * K
    return (
        f"M{f(cx)} {f(cy - ry)} "
        f"C{f(cx + kx)} {f(cy - ry)} {f(cx + rx)} {f(cy - ky)} {f(cx + rx)} {f(cy)} "
        f"C{f(cx + rx)} {f(cy + ky)} {f(cx + kx)} {f(cy + ry)} {f(cx)} {f(cy + ry)} "
        f"C{f(cx - kx)} {f(cy + ry)} {f(cx - rx)} {f(cy + ky)} {f(cx - rx)} {f(cy)} "
        f"C{f(cx - rx)} {f(cy - ky)} {f(cx - kx)} {f(cy - ry)} {f(cx)} {f(cy - ry)} Z"
    )


def mirall(d):
    """El camí reflectit respecte de l'eix vertical del centre (x → 200 − x)."""
    sortida, x_toca = [], True
    for t in d.replace(",", " ").split():
        cap = t[0]
        if cap.isalpha():
            sortida.append(cap)
            x_toca = True
            t = t[1:]
            if not t:
                continue
        valor = float(t)
        sortida.append(f(200 - valor) if x_toca else f(valor))
        x_toca = not x_toca
    text = " ".join(sortida)
    for lletra in "MLCQZ":
        text = text.replace(f"{lletra} ", lletra)
    return text


def capa(z, d, fill=None, stroke="tinta", w=3.0):
    return {"z": z, "d": d, "fill": fill, "stroke": stroke, "w": w}


def peca(id, ranura, capes, npc=False):
    return {"id": id, "ranura": ranura, "capes": capes, "npc": npc}


# --- Colors que tria el jugador -------------------------------------------------------------------------------------

TONS_PELL = [
    ("pell_1", "#F8DCC8"),
    ("pell_2", "#EEC1A0"),
    ("pell_3", "#D29B74"),
    ("pell_4", "#A86F4C"),
    ("pell_5", "#6E4630"),
]

COLORS_CABELL = [
    ("cabell_negre", "#2B2B2B"),
    ("cabell_castany", "#6B4226"),
    ("cabell_ros", "#E3B95B"),
    ("cabell_pelroig", "#B5522B"),
    ("cabell_gris", "#B4B4B4"),
    ("cabell_magenta", "#FF3D7F"),
]

# --- Cos (sempre present) -------------------------------------------------------------------------------------------

COLL = "M87 110 L113 110 L114 170 L86 170 Z"
OMBRA_COLL = "M87 124 C95 132 105 132 113 124 L113 136 C105 140 95 140 87 136 Z"
CAP = elipse(100, 88, 36, 40)
ORELLA = elipse(64, 94, 7, 10)
ORELLA_DINS = "M65 88 Q60 94 65 100"

TRONC = "M24 200 C24 170 44 152 78 148 L122 148 C156 152 176 170 176 200 Z"
TRONC_V = "M24 200 C24 170 44 152 78 148 L88 148 L100 166 L112 148 L122 148 C156 152 176 170 176 200 Z"
TRONC_RODO = "M24 200 C24 170 44 152 78 148 C86 156 114 156 122 148 C156 152 176 170 176 200 Z"
MEITAT_E = "M24 200 C24 170 44 152 78 148 L90 150 L97 200 Z"
SOLAPA_E = "M78 148 L90 150 L95 176 L80 162 Z"

COS = [
    capa(10, COLL, "pell"),
    capa(11, OMBRA_COLL, "pell_ombra", None),
    capa(38, ORELLA, "pell"),
    capa(39, ORELLA_DINS, None, "tinta", 2),
    capa(38, mirall(ORELLA), "pell"),
    capa(39, mirall(ORELLA_DINS), None, "tinta", 2),
    capa(40, CAP, "pell"),
]

# --- Cares (van amb el cos; no es trien) ----------------------------------------------------------------------------

ULL = elipse(86, 94, 4, 5)
CELLA = "M77 83 Q86 78 95 83"
NAS = "M100 98 Q104 104 99 107"
BOCA = "M89 113 Q100 122 111 113"
GALTA = elipse(78, 107, 6, 3.5)
PESTANYA = "M82 90 L77 86"


def cara_base(extra=()):
    return [
        capa(50, ULL, "tinta", None),
        capa(50, mirall(ULL), "tinta", None),
        capa(50, CELLA, None, "tinta", 3),
        capa(50, mirall(CELLA), None, "tinta", 3),
        capa(50, NAS, None, "tinta", 2.5),
        capa(50, BOCA, None, "tinta", 3),
        capa(49, GALTA, "#FF9CB8", None),
        capa(49, mirall(GALTA), "#FF9CB8", None),
        *extra,
    ]


ULL_FELIC = "M80 96 Q86 89 92 96"
BIGOTI = "M78 109 C84 101 95 101 100 106 C105 101 116 101 122 109 C117 116 108 116 100 111 C92 116 83 116 78 109 Z"
SOMRIURE_DIRECTOR = "M92 118 Q100 124 108 118"
ULLET = "M108 95 Q114 99 120 95"

CARES = [
    peca("cara_noi", "cara", cara_base(), npc=True),
    peca("cara_noia", "cara", cara_base([
        capa(50, PESTANYA, None, "tinta", 2.5),
        capa(50, mirall(PESTANYA), None, "tinta", 2.5),
    ]), npc=True),
    peca("cara_director", "cara", [
        capa(50, ULL_FELIC, None, "tinta", 3),
        capa(50, mirall(ULL_FELIC), None, "tinta", 3),
        capa(50, NAS, None, "tinta", 2.5),
        capa(50, SOMRIURE_DIRECTOR, None, "tinta", 3),
        capa(49, GALTA, "#FF9CB8", None),
        capa(49, mirall(GALTA), "#FF9CB8", None),
    ], npc=True),
    peca("cara_director_ullet", "cara", [
        capa(50, ULL_FELIC, None, "tinta", 3),
        capa(50, ULLET, None, "tinta", 3),
        capa(50, NAS, None, "tinta", 2.5),
        capa(50, SOMRIURE_DIRECTOR, None, "tinta", 3),
        capa(49, GALTA, "#FF9CB8", None),
        capa(49, mirall(GALTA), "#FF9CB8", None),
    ], npc=True),
]

# --- Cabells --------------------------------------------------------------------------------------------------------

CABELL_CURT = "M62 92 C58 58 78 42 102 42 C126 42 144 58 138 92 C136 80 130 70 120 65 C108 72 88 73 76 65 C68 72 64 80 62 92 Z"
CABELL_RAPAT = "M65 80 C67 56 84 47 100 47 C116 47 133 56 135 80 C120 69 80 69 65 80 Z"
CABELL_ARRISSAT = (
    "M60 98 Q48 86 57 73 Q53 57 69 51 Q75 36 92 39 Q101 29 113 37 Q129 35 133 51 Q149 57 145 73 "
    "Q153 86 140 98 Q138 80 126 71 Q113 78 100 71 Q87 78 74 71 Q62 80 60 98 Z"
)
CABELL_LLARG_DARRERE = "M60 90 C56 52 80 38 100 38 C120 38 144 52 140 90 L146 158 C128 166 72 166 54 158 Z"
CABELL_LLARG_DAVANT = "M62 94 C58 56 80 42 100 42 C122 42 142 56 138 94 C134 74 124 63 112 59 C104 70 86 77 67 77 C64 83 63 88 62 94 Z"
CABELL_ENRERE = "M62 90 C60 56 80 42 100 42 C120 42 140 56 138 90 C132 70 116 60 100 60 C84 60 68 70 62 90 Z"
CUA = "M128 58 C152 54 162 82 154 112 C150 126 141 134 136 130 C143 110 141 84 128 72 Z"
GOMA = "M127 58 L135 56 L138 66 L130 70 Z"
MONYO = elipse(100, 37, 16, 14)

CABELLS = [
    peca("cabell_curt", "cabell", [capa(70, CABELL_CURT, "cabell")]),
    peca("cabell_rapat", "cabell", [capa(70, CABELL_RAPAT, "cabell", "tinta", 2)]),
    peca("cabell_arrissat", "cabell", [capa(70, CABELL_ARRISSAT, "cabell")]),
    peca("cabell_llarg", "cabell", [
        capa(5, CABELL_LLARG_DARRERE, "cabell_ombra"),
        capa(70, CABELL_LLARG_DAVANT, "cabell"),
    ]),
    peca("cabell_cua", "cabell", [
        capa(5, CUA, "cabell_ombra"),
        capa(6, GOMA, "#FF3D7F", "tinta", 2),
        capa(70, CABELL_ENRERE, "cabell"),
    ]),
    peca("cabell_monyo", "cabell", [
        capa(5, MONYO, "cabell_ombra"),
        capa(70, CABELL_ENRERE, "cabell"),
    ]),
    # El director: calb per dalt, amb dos flocs als costats.
    peca("cabell_calb", "cabell", [
        capa(70, "M63 100 C58 84 62 70 72 63 C70 76 70 88 69 101 Z", "cabell", "tinta", 2),
        capa(70, mirall("M63 100 C58 84 62 70 72 63 C70 76 70 88 69 101 Z"), "cabell", "tinta", 2),
    ], npc=True),
]

# --- Barrets --------------------------------------------------------------------------------------------------------

CORONA_GORRA = "M60 74 C60 46 80 34 100 34 C120 34 140 46 140 74 Z"
CINTA_GORRA = "M60 64 L140 64 L140 78 L60 78 Z"
VISERA = "M57 78 L143 78 C138 91 62 91 57 78 Z"

BARRETS = [
    peca("cap_revisor", "cap", [
        capa(80, CORONA_GORRA, "#23304A"),
        capa(81, CINTA_GORRA, "#111111"),
        capa(82, VISERA, "#111111"),
        capa(82, elipse(100, 52, 7, 6), "#FF3D7F", "tinta", 2),
    ]),
    peca("cap_beisbol", "cap", [
        capa(80, "M62 78 C60 46 80 35 100 35 C120 35 140 46 138 78 Z", "#FF3D7F"),
        capa(81, "M100 36 L100 76", None, "tinta", 2),
        capa(82, "M58 78 C80 70 120 70 142 78 C137 91 63 91 58 78 Z", "#111111"),
        capa(82, elipse(100, 35, 4, 3), "#111111", None),
    ]),
    peca("cap_llana", "cap", [
        capa(79, elipse(100, 30, 11, 10), "#F2F2F2"),
        capa(80, "M62 78 C60 46 80 34 100 34 C120 34 140 46 138 78 Z", "#E0A526"),
        capa(81, "M59 68 L141 68 L142 84 L58 84 Z", "#C48A12"),
        capa(81, "M72 70 L72 82 M86 70 L86 82 M100 70 L100 82 M114 70 L114 82 M128 70 L128 82", None, "tinta", 2),
    ]),
    peca("cap_casc", "cap", [
        capa(80, "M57 84 C55 44 80 30 100 30 C120 30 145 44 143 84 C130 75 70 75 57 84 Z", "#2EB8A8"),
        capa(81, "M88 34 L86 54 M100 32 L100 52 M112 34 L114 54", None, "tinta", 3),
        capa(81, "M62 84 L80 124 M138 84 L120 124", None, "tinta", 2),
    ]),
    peca("cap_barretina", "cap", [
        capa(80, "M64 72 C66 40 92 28 114 31 C138 34 152 50 156 70 C158 82 148 85 143 76 C139 62 131 59 127 68 Z", "#C62D1F"),
        capa(81, "M61 66 C80 60 120 60 139 66 L139 80 C120 74 80 74 61 80 Z", "#8E1F15"),
    ]),
    # El director: la gorra vermella de cap d'estació.
    peca("cap_cap_estacio", "cap", [
        capa(80, CORONA_GORRA, "#C62D1F"),
        capa(81, CINTA_GORRA, "#111111"),
        capa(81, "M62 70 L138 70", None, "#E0A526", 3),
        capa(82, VISERA, "#111111"),
        capa(82, elipse(100, 52, 7, 6), "#E0A526", "tinta", 2),
    ], npc=True),
]

# --- Roba -----------------------------------------------------------------------------------------------------------


def jaqueta(color, solapa, interior="#FFFFFF", coll=TRONC_RODO):
    return [
        capa(20, coll, interior),
        capa(21, MEITAT_E, color),
        capa(21, mirall(MEITAT_E), color),
        capa(22, SOLAPA_E, solapa, "tinta", 2.5),
        capa(22, mirall(SOLAPA_E), solapa, "tinta", 2.5),
    ]


TRONCS = [
    peca("tronc_samarreta", "tronc", [capa(20, TRONC_V, "#FFFFFF")]),
    peca("tronc_texana", "tronc", [
        *jaqueta("#4A78B5", "#3A6299"),
        capa(23, elipse(93, 182, 2.5, 2.5), "#E0A526", None),
        capa(23, elipse(107, 182, 2.5, 2.5), "#E0A526", None),
        capa(23, "M44 172 L58 168 M156 172 L142 168", None, "tinta", 2),
    ]),
    peca("tronc_armilla", "tronc", [
        capa(20, TRONC_V, "#9AA0A6"),
        capa(21, "M30 200 C34 172 50 158 80 150 L93 200 Z", "#E6F23A"),
        capa(21, mirall("M30 200 C34 172 50 158 80 150 L93 200 Z"), "#E6F23A"),
        capa(22, "M37 176 L88 176 L90 186 L33 186 Z", "#D9D9D9", "tinta", 2),
        capa(22, mirall("M37 176 L88 176 L90 186 L33 186 Z"), "#D9D9D9", "tinta", 2),
    ]),
    peca("tronc_abric", "tronc", jaqueta("#3E4349", "#2A2E33", coll=TRONC_V)),
    peca("tronc_dessuadora", "tronc", [
        capa(5, "M66 156 C60 136 72 124 86 130 L114 130 C128 124 140 136 134 156 Z", "#D92E6A"),
        capa(20, TRONC_RODO, "#FF3D7F"),
        capa(21, "M92 154 L90 176 M108 154 L110 176", None, "tinta", 2.5),
        capa(21, "M74 186 L126 186 L130 200 L70 200 Z", "#D92E6A", "tinta", 2.5),
    ]),
    peca("tronc_uniforme", "tronc", [
        *jaqueta("#23304A", "#17203A", coll=TRONC_V),
        capa(23, "M96 151 L104 151 L106 158 L102 184 L100 188 L98 184 L94 158 Z", "#FF3D7F", "tinta", 2),
        capa(23, "M56 166 L72 166 L72 174 L56 174 Z", "#E0A526", "tinta", 2),
    ]),
]

# --- Complements ----------------------------------------------------------------------------------------------------

COMPLEMENTS = [
    peca("coll_bufanda", "coll", [
        capa(30, "M78 140 C90 151 110 151 122 140 L126 157 C111 168 89 168 74 157 Z", "#FF3D7F"),
        capa(31, "M104 156 L120 155 L118 192 L102 192 Z", "#FF3D7F"),
        capa(32, "M104 168 L119 168 M103 180 L118 180", None, "#FFFFFF", 4),
    ]),
    peca("ulls_rodones", "ulls", [
        capa(60, elipse(86, 94, 11, 10), None, "tinta", 3),
        capa(60, elipse(114, 94, 11, 10), None, "tinta", 3),
        capa(60, "M97 93 Q100 89 103 93", None, "tinta", 3),
        capa(60, "M75 91 L65 89 M125 91 L135 89", None, "tinta", 3),
    ]),
    peca("ulls_sol", "ulls", [
        capa(60, "M72 87 L98 87 L96 101 C94 107 76 107 74 101 Z", "#111111"),
        capa(60, mirall("M72 87 L98 87 L96 101 C94 107 76 107 74 101 Z"), "#111111"),
        capa(60, "M98 90 L102 90", None, "tinta", 3),
        capa(61, "M77 91 L83 91", None, "#FFFFFF", 2),
        capa(61, "M105 91 L111 91", None, "#FFFFFF", 2),
    ]),
    peca("orelles_auriculars", "orelles", [
        capa(90, "M60 94 C54 36 146 36 140 94", None, "tinta", 7),
        capa(90, elipse(61, 96, 9, 13), "#FF3D7F"),
        capa(90, elipse(139, 96, 9, 13), "#FF3D7F"),
    ]),
    peca("bigoti", "bigoti", [capa(55, BIGOTI, "cabell", "tinta", 2)], npc=True),
]

PECES = [*CARES, *CABELLS, *BARRETS, *TRONCS, *COMPLEMENTS]

# Ranures que tria el jugador, en l'ordre de l'editor. Les obligatòries no es poden deixar buides.
RANURES = [
    ("cabell", True),
    ("cap", False),
    ("tronc", True),
    ("coll", False),
    ("ulls", False),
    ("orelles", False),
]

# El director (docs/lore.md): una combinació fixa.
DIRECTOR = {
    "cos": "noi",
    "pell": "pell_2",
    "cabell": "cabell_calb",
    "color_cabell": "cabell_gris",
    "cap": "cap_cap_estacio",
    "tronc": "tronc_uniforme",
    "ulls": "ulls_rodones",
    "bigoti": "bigoti",
}

# El primer avatar que es veu a l'editor.
INICIAL = {
    "cos": "noi",
    "pell": "pell_2",
    "cabell": "cabell_curt",
    "color_cabell": "cabell_castany",
    "tronc": "tronc_samarreta",
}
