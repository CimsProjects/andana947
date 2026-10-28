import json
import tempfile
import unittest

from test.utils import ARREL, crea_gtfs

from andana import gtfs
from andana.territori import Territori, distancia, projecta
from andana.xarxa import ConfigFeed, Xarxa, mode_tmb

TERRITORI = None


def territori():
    global TERRITORI
    if TERRITORI is None:
        TERRITORI = Territori(ARREL / "dades/territori/municipis_catalunya.geojson",
                              ARREL / "dades/territori/comarques.json")
    return TERRITORI


class TestTerritori(unittest.TestCase):
    def test_municipis_i_superficies(self):
        t = territori()
        self.assertEqual(947, len(t.municipis))
        # Barcelona fa uns 101 km² i Catalunya uns 32.100.
        self.assertAlmostEqual(101, t.municipis["08019"].superficie_km2, delta=2)
        self.assertAlmostEqual(32_100, sum(m.superficie_km2 for m in t.municipis.values()), delta=150)
        self.assertEqual("13", t.municipis["08019"].comarca)

    def test_municipi_d_un_punt(self):
        self.assertEqual("08019", territori().municipi_de(41.4359, 2.2005))  # Baró de Viver
        self.assertIsNone(territori().municipi_de(40.4168, -3.7038))  # Madrid

    def test_la_projeccio_conserva_les_distancies_curtes(self):
        a, b = projecta(2.2, 41.4), projecta(2.2, 41.4005)
        self.assertAlmostEqual(distancia(41.4, 2.2, 41.4005, 2.2), ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5,
                               delta=0.05)

    def test_poblacio_en_un_radi(self):
        # Una secció quadrada d'1 km² amb 1.000 habitants: un cercle de 100 m a dins en té 1000 × π × 0,01.
        dl = 0.5 / 111.195
        dlon = 0.5 / (111.195 * 0.7495)
        seccio = {"type": "Feature", "properties": {"CCA": "09", "CUSEC": "0801901001", "CUMUN": "08019"},
                  "geometry": {"type": "Polygon", "coordinates": [[[2.2 - dlon, 41.4 - dl], [2.2 + dlon, 41.4 - dl],
                                                                   [2.2 + dlon, 41.4 + dl], [2.2 - dlon, 41.4 + dl],
                                                                   [2.2 - dlon, 41.4 - dl]]]}}
        with tempfile.TemporaryDirectory() as d:
            with open(f"{d}/s.geojson", "w") as f:
                json.dump({"type": "FeatureCollection", "features": [seccio]}, f)
            with open(f"{d}/c.csv", "w") as f:
                f.write("ccaa,cpro,cmun,dist,secc,t1_1\n09,08,019,01,001,1000\n")
            t = Territori(ARREL / "dades/territori/municipis_catalunya.geojson", ARREL / "dades/territori/comarques.json")
            t.carrega_seccions(f"{d}/s.geojson", f"{d}/c.csv")
            self.assertAlmostEqual(31.4, t.poblacio_radi(41.4, 2.2, 100), delta=1.0)
            self.assertAlmostEqual(1000, t.municipis["08019"].poblacio)


class TestXarxa(unittest.TestCase):
    def test_estacions_i_fusio_de_parades(self):
        with tempfile.TemporaryDirectory() as d:
            feed = gtfs.llegeix(crea_gtfs(f"{d}/prova.zip"), "gtfs_prova")
        xarxa = Xarxa(territori(), radi_fusio_bus=30)
        xarxa.afegeix_feed(ConfigFeed("gtfs_prova", "prova", "Prova", mode_tmb), feed)
        xarxa.fusiona_estacions()
        xarxa.fusiona_parades_bus()
        # L'estació pare agrupa les dues andanes.
        self.assertIn("prova:E1", xarxa.nodes)
        self.assertEqual(2, xarxa.nodes["prova:E1"].expedicions_feiner)
        # B1 i B2 són a 14 m i tenen el mateix nom: un sol node amb les expedicions de totes dues.
        busos = [n for n in xarxa.nodes.values() if n.mode == "bus"]
        self.assertEqual(2, len(busos))
        fusionat = max(busos, key=lambda n: n.expedicions_feiner)
        self.assertEqual(8, fusionat.expedicions_feiner)
        self.assertEqual({("gtfs_prova", "B1"), ("gtfs_prova", "B2")}, fusionat.parades_gtfs)
        self.assertEqual(["L1"], xarxa.codis_linia(xarxa.nodes["prova:E1"]))


if __name__ == "__main__":
    unittest.main()
