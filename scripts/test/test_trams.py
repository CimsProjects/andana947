"""Tests dels trams (GDD 4.0), amb línies de prova (no són del joc)."""

import json
import unittest

from test.utils import ARREL

from andana.trams import GRAN, MITJA, PETIT, agrupa, arrodoneix, classifica, estacions_del_recorregut, mida, preu

CONFIG = json.loads((ARREL / "dades" / "configuracio_joc.json").read_text(encoding="utf-8"))["trams"]


def linia(id_, codi, operador="TMB", mode="metro", nom="", color=""):
    return {"id": id_, "operador": operador, "mode": mode, "codi": codi, "nom": nom, "color": color}


def parades(id_linia, n, expedicions):
    return [{"linia_id": id_linia, "node_id": f"{id_linia}:{i}", "expedicions_feiner": str(expedicions)}
            for i in range(n)]


class TestAgrupa(unittest.TestCase):
    def test_els_sentits_d_una_linia_son_un_sol_tram(self):
        linies = [linia("rodalies:A_R1", "R1", "Renfe", "rodalies", "Molins - Maçanet", "ff0000"),
                  linia("rodalies:B_R1", "R1", "Renfe", "rodalies", "Maçanet - Molins")]
        ln = [{"linia_id": "rodalies:A_R1", "node_id": "renfe:1", "expedicions_feiner": "10"},
              {"linia_id": "rodalies:B_R1", "node_id": "renfe:1", "expedicions_feiner": "12"},
              {"linia_id": "rodalies:B_R1", "node_id": "renfe:2", "expedicions_feiner": "12"}]
        [tram] = agrupa(linies, ln, {})
        self.assertEqual("rodalies:R1", tram.id)
        self.assertEqual(["rodalies:A_R1", "rodalies:B_R1"], tram.linies)
        self.assertEqual(2, tram.parades)
        self.assertEqual(34, tram.arribades_feiner)
        self.assertEqual("FF0000", tram.color)

    def test_els_codis_generics_de_renfe_es_parteixen_per_recorregut(self):
        linies = [linia("renfe_md:7140079400VRE", "REG.EXP.", "Renfe", "regional"),
                  linia("renfe_md:7940071400VRE", "REG.EXP.", "Renfe", "regional"),
                  linia("renfe_md:7150078400VRE", "REG.EXP.", "Renfe", "regional")]
        noms = {"renfe:71400": "Reus", "renfe:79400": "Barcelona Estació de França"}
        trams = agrupa(linies, [], noms)
        self.assertEqual(2, len(trams))
        reus = next(t for t in trams if t.id == "renfe_md:REG.EXP.:71400-79400")
        self.assertEqual(2, len(reus.linies))
        self.assertEqual("Regional exprés Reus – Barcelona Estació de França", reus.nom)

    def test_estacions_del_recorregut(self):
        self.assertEqual(("71400", "79400"), estacions_del_recorregut("renfe_md:7140079400VRE"))
        self.assertIsNone(estacions_del_recorregut("tmb:1.1.1"))


class TestMidaIPreu(unittest.TestCase):
    def test_mida_per_terços(self):
        self.assertEqual(PETIT, mida(10, 90, 649))
        self.assertEqual(MITJA, mida(90, 90, 649))
        self.assertEqual(GRAN, mida(649, 90, 649))

    def test_preu_d_una_andana_de_quatre_dies(self):
        # 36 arribades en 12 parades: 3 per andana × 3 monedes × 4 dies = 36.
        self.assertEqual(36, preu(36, 12, PETIT, CONFIG))
        # 273 arribades en 21 parades: 13 × 7,5 × 4 = 390.
        self.assertEqual(390, preu(273, 21, MITJA, CONFIG))

    def test_preu_minim_i_grans_sense_preu(self):
        self.assertEqual(CONFIG["preuMinim"], preu(1, 3, PETIT, CONFIG))
        self.assertIsNone(preu(5000, 30, GRAN, CONFIG))

    def test_arrodoneix_els_mitjos_cap_amunt(self):
        self.assertEqual(20, arrodoneix(15, 10))
        self.assertEqual(10, arrodoneix(14.9, 10))
        self.assertEqual(13, arrodoneix(12.5, 1))

    def test_classifica_descarta_els_trams_curts(self):
        linies = [linia(f"tmb:{i}", f"L{i}") for i in range(7)]
        ln = parades("tmb:0", 2, 50)  # massa curt
        for i in range(1, 7):
            ln += parades(f"tmb:{i}", 3, i * 10)
        jugables, talls = classifica(agrupa(linies, ln, {}), CONFIG)
        self.assertNotIn("tmb:L0", [t.id for t in jugables])
        self.assertEqual((90, 150), talls)
        self.assertEqual([PETIT, PETIT, MITJA, MITJA, GRAN, GRAN], [t.mida for t in jugables])


if __name__ == "__main__":
    unittest.main()
