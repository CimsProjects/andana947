import datetime as dt
import tempfile
import unittest

from test.utils import crea_gtfs

from andana import gtfs


class TestGtfs(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.feed = gtfs.llegeix(crea_gtfs(f"{self.dir.name}/prova.zip"), "prova")

    def tearDown(self):
        self.dir.cleanup()

    def test_tria_un_dimecres_amb_servei_normal(self):
        self.assertEqual(dt.date(2026, 11, 11), self.feed.dia_referencia)

    def test_compta_les_expedicions_del_dia(self):
        self.assertEqual(2, self.feed.expedicions_ruta["M1"])
        # h1, h2 i hf (6 per freqüència).
        self.assertEqual(8, self.feed.expedicions_ruta["H1"])
        self.assertEqual(7, self.feed.expedicions_parada_ruta[("B1", "H1")])
        self.assertEqual(1, self.feed.expedicions_parada_ruta[("E1a", "M1")])

    def test_llegeix_parades_i_rutes(self):
        self.assertEqual("E1", self.feed.parades["E1a"].pare)
        self.assertEqual(1, self.feed.rutes["M1"].tipus)
        self.assertEqual({"H1"}, self.feed.rutes_de_parada()["B1"])

    def test_filtre_de_rutes(self):
        feed = gtfs.llegeix(f"{self.dir.name}/prova.zip", "prova", filtre_ruta=lambda r: r.tipus == 3)
        self.assertEqual({"H1"}, set(feed.rutes))
        self.assertNotIn("M1", feed.expedicions_ruta)


if __name__ == "__main__":
    unittest.main()
