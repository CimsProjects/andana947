import unittest

from test.utils import llibre

from andana import afluencia

CALENDARI = {"diesFeiners": 251, "diesNoFeiners": 114, "factorNoFeiner": 0.6}


class TestConversions(unittest.TestCase):
    def test_feiner_des_de_anual(self):
        # GDD 8: Casa de l'Aigua, 46.884 entrades el 2024 ≈ 147 per feiner.
        self.assertAlmostEqual(146.79, afluencia.feiner_des_de_anual(46_884, CALENDARI), delta=0.01)

    def test_el_factor_calibrat_recupera_el_de_partida(self):
        anual = 1000 * (251 + 0.6 * 114)
        self.assertAlmostEqual(0.6, afluencia.factor_no_feiner_calibrat(1000, anual, 251, 114))


class TestModelRodalies(unittest.TestCase):
    """Les comprovacions de la fase 2 del GDD (secció 2.2)."""

    entrades = {"a": 1000.0, "b": 5000.0, "c": 3000.0, "d": 200.0}
    pes = {"a": {"R1": 10}, "b": {"R1": 10, "R2": 30}, "c": {"R2": 5}, "d": {"R1": 4}}
    factors = {None: 1.0, "R1": 1.2, "R2": 0.8}

    def setUp(self):
        self.resultat = afluencia.model_rodalies(self.entrades, self.pes, self.factors)

    def test_a_les_estacions_d_una_sola_linia_creixen_f(self):
        self.assertAlmostEqual(1200.0, self.resultat["a"])
        self.assertAlmostEqual(2400.0, self.resultat["c"])
        total_abans, total_despres = sum(self.entrades.values()), sum(self.resultat.values())
        self.assertTrue(0.8 <= total_despres / total_abans <= 1.2)

    def test_b_l_ordre_dins_de_cada_linia_es_mante(self):
        for linia in ("R1", "R2"):
            estacions = [e for e, p in self.pes.items() if p.keys() == {linia}]
            per_entrades = sorted(estacions, key=self.entrades.get)
            per_resultat = sorted(estacions, key=self.resultat.get)
            self.assertEqual(per_entrades, per_resultat)

    def test_una_estacio_de_dues_linies_barreja_els_factors(self):
        self.assertAlmostEqual(5000 * (0.25 * 1.2 + 0.75 * 0.8), self.resultat["b"])

    def test_sense_factor_de_linia_fa_servir_el_de_xarxa(self):
        r = afluencia.model_rodalies({"x": 100.0}, {"x": {"R9": 1}}, {None: 1.05})
        self.assertAlmostEqual(105.0, r["x"])


class TestModelParades(unittest.TestCase):
    def test_reparteix_el_total_de_cada_linia(self):
        pes = {"p1": 10.0, "p2": 30.0, "p3": 60.0}
        r = afluencia.model_parades({"L": 1000.0, "M": 100.0}, pes, {"L": ["p1", "p2"], "M": ["p2", "p3"]})
        self.assertAlmostEqual(250.0, r["p1"])
        self.assertAlmostEqual(750.0 + 33.333333, r["p2"], places=4)
        self.assertAlmostEqual(1100.0, sum(r.values()))

    def test_pes_de_parada(self):
        # GDD 2.3: expedicions × (1 + població_400m / 1.000).
        self.assertEqual(100 * 3.5, afluencia.pes_parada(100, 2500, 1000))

    def test_pes_regional(self):
        self.assertEqual(10 * 100, afluencia.pes_regional(10, 10_000, 0.5))

    def test_l_ajust_de_l_exponent_recupera_beta(self):
        punts = [(e * p ** 0.7, e, p) for e, p in ((10, 1000), (40, 50_000), (5, 300), (80, 1_600_000))]
        self.assertAlmostEqual(0.7, afluencia.ajust_exponent(punts))


class TestLectors(unittest.TestCase):
    def test_tmb_metro(self):
        capcalera = [None, "LÍNIA  1", None] + ["GENER", "FEBRER"] + [None] * 10
        files_feiners = [[None, "EVOLUCIÓ"], capcalera, [None, None, "BARÓ DE VIVER", 1600, 1800] + [None] * 10,
                         [None, "TOTAL", None, 1600, 1800]]
        files_mensuals = [capcalera + ["ACUMULAT"],
                          [None, None, "BARÓ DE VIVER", 40000, 42000] + [None] * 10 + [500000]]
        r = afluencia.llegeix_tmb_metro(llibre({"Mensuals": files_mensuals, "Feiners": files_feiners}))
        self.assertEqual({"feiner": 1700.0, "anual": 500000.0}, r[("L1", "BARÓ DE VIVER")])

    def test_nom_de_linia_tmb(self):
        self.assertEqual("L9N", afluencia._nom_linia_tmb("LÍNIA 9 NORD"))
        self.assertEqual("L10S", afluencia._nom_linia_tmb("LÍNIA  10  SUD"))
        self.assertEqual("L11", afluencia._nom_linia_tmb("LÍNIA 11"))

    def test_tmb_bus(self):
        files = [[None, "LÍNIES", "TOTAL MENSUAL", "V/D FEINERS", "TOTAL MENSUAL", "V/D FEINERS"],
                 [None, 6, 275577, 11372.9, 283698, 12459.3],
                 [None, "H12", 100, 0, 0, 0]]
        r = afluencia.llegeix_tmb_bus(llibre({"2025": files}))
        self.assertAlmostEqual((11372.9 + 12459.3) / 2, r["6"]["feiner"])
        self.assertEqual(275577 + 283698, r["6"]["anual"])
        self.assertNotIn("H12", r)

    def test_renfe_2018(self):
        files = [["CODIGO_ESTACION", "NOMBRE_ESTACION", "NUCLEO_CERCANIAS", "TRAMO_HORARIO", "VIAJEROS_SUBIDOS",
                  "VIAJEROS_BAJADOS"],
                 [71600, "SANT VICENÇ", "BARCELONA", "05:00 - 05:30", 43, 0],
                 [71600, "SANT VICENÇ", "BARCELONA", "05:30 - 06:00", 46, 10]]
        r = afluencia.llegeix_renfe_2018(llibre({"Sheet1": files}))
        self.assertEqual({"nom": "SANT VICENÇ", "pujats": 89.0, "baixats": 10.0}, r["71600"])


if __name__ == "__main__":
    unittest.main()
