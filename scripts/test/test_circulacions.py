import datetime as dt
import tempfile
import unittest

from test.utils import GTFS_PROVA, crea_gtfs

import genera_circulacions
from andana import circulacions
from andana.circulacions import Circulacions
from andana.territori import distancia

# Un tren de prova que va de nord a sud per una via recta, amb una parada al mig on s'atura 30 segons.
GTFS_TREN = {
    **GTFS_PROVA,
    "stops.txt": (
        "stop_id,stop_name,stop_lat,stop_lon\n"
        "N,Nord,41.4100,2.2000\nM,Mig,41.4050,2.2001\nS,Sud,41.4000,2.2000\n"
    ),
    "routes.txt": "route_id,agency_id,route_short_name,route_long_name,route_type,route_color\nT,A,R1,Tren,2,7DBCEC\nX,A,R9,Altre,2,000000\n",
    "trips.txt": (
        "route_id,service_id,trip_id,shape_id\n"
        "T,FEINER,t1,F1\nT,FEINER,t2,F1\nT,CAPSETMANA,t3,\nX,FEINER,x1,F1\n"
    ),
    "shapes.txt": (
        "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n"
        "F1,41.4100,2.2000,1\nF1,41.4075,2.20005,2\nF1,41.4050,2.2000,3\nF1,41.4025,2.20001,4\nF1,41.4000,2.2000,5\n"
    ),
    "frequencies.txt": "trip_id,start_time,end_time,headway_secs\n",
    "stop_times.txt": (
        "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
        "t1,08:00:00,08:00:00,N,1\nt1,08:02:00,08:02:30,M,2\nt1,08:04:00,08:04:00,S,3\n"
        "t2,25:10:00,25:10:00,N,1\nt2,25:12:00,25:12:30,M,2\nt2,25:14:00,25:14:00,S,3\n"
        "t3,10:00:00,10:00:00,N,1\nt3,10:05:00,10:05:00,S,2\n"
        "x1,09:00:00,09:00:00,N,1\nx1,09:05:00,09:05:00,S,2\n"
    ),
}


class TestGeometria(unittest.TestCase):
    def test_la_polilinia_codificada_torna_els_mateixos_punts(self):
        punts = [(41.38506, 2.17340), (41.40000, 2.20000), (38.5, -120.2)]
        # L'exemple de la documentació de Google.
        self.assertEqual("_p~iF~ps|U_ulLnnqC_mqNvxq`@", circulacions.codifica_polilinia([(38.5, -120.2), (40.7, -120.95), (43.252, -126.453)]))
        for (a, b), (c, d) in zip(punts, circulacions.descodifica_polilinia(circulacions.codifica_polilinia(punts))):
            self.assertAlmostEqual(a, c, places=5)
            self.assertAlmostEqual(b, d, places=5)

    def test_simplifica_treu_els_punts_alineats(self):
        punts = [(41.40, 2.20), (41.401, 2.20), (41.402, 2.20), (41.402, 2.21)]
        self.assertEqual([(41.40, 2.20), (41.402, 2.20), (41.402, 2.21)], circulacions.simplifica(punts))

    def test_les_parades_es_projecten_en_ordre(self):
        # Anada i tornada pel mateix carrer: la segona parada A és la de la tornada, no la de l'anada.
        punts = [(41.40, 2.20), (41.41, 2.20), (41.40, 2.20)]
        distancies, pitjor = circulacions.projecta_parades(punts, [(41.40, 2.20), (41.41, 2.20), (41.40, 2.20)])
        tram = distancia(41.40, 2.20, 41.41, 2.20)
        self.assertAlmostEqual(0, distancies[0], delta=1)
        self.assertAlmostEqual(tram, distancies[1], delta=1)
        self.assertAlmostEqual(2 * tram, distancies[2], delta=1)
        self.assertLess(pitjor, 1)


class TestCirculacions(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        cami = crea_gtfs(f"{self.dir.name}/tren.zip", GTFS_TREN)
        self.c = Circulacions(linies={"prova:T": {"id": "prova:T", "codi": "R1", "mode": "rodalies",
                                                 "operador": "Renfe", "color": "7dbcec"}})
        self.c.afegeix_feed(cami, "gtfs_prova", {"T": "prova:T"}, des_de=dt.date(2026, 11, 2))
        self.json = self.c.a_json("2026-11-02")

    def tearDown(self):
        self.dir.cleanup()

    def test_tria_un_dia_de_cada_tipus(self):
        self.assertEqual({"feiner": dt.date(2026, 11, 11), "dissabte": dt.date(2026, 11, 7),
                          "festiu": dt.date(2026, 11, 8)}, self.c.dies["gtfs_prova"])

    def test_nomes_les_linies_demanades(self):
        self.assertEqual(["prova:T"], [l["id"] for l in self.json["linies"]])
        self.assertEqual("7DBCEC", self.json["linies"][0]["color"])

    def test_les_expedicions_que_comparteixen_horari_fan_servir_el_mateix_patro(self):
        # t1 i t2 (que surt a la 01:10 de l'endemà, 25:10) tenen el mateix patró; t3 és de cap de setmana.
        self.assertEqual([0, 8 * 3600, 0, 25 * 3600 + 600], self.json["expedicions"]["feiner"])
        self.assertEqual(2, len(self.json["expedicions"]["dissabte"]))
        self.assertEqual(len(self.json["expedicions"]["dissabte"]), len(self.json["expedicions"]["festiu"]))

    def test_el_patro_te_les_distancies_i_els_temps_de_cada_parada(self):
        patro = self.json["patrons"][0]
        self.assertEqual([0, 120, 240], patro["arribades"])
        self.assertEqual([0, 150, 240], patro["sortides"])
        total = distancia(41.41, 2.20, 41.40, 2.20)
        self.assertAlmostEqual(0, patro["distancies"][0], delta=2)
        self.assertAlmostEqual(total / 2, patro["distancies"][1], delta=5)
        self.assertAlmostEqual(total, patro["distancies"][2], delta=5)

    def test_sense_tracat_la_recta_entre_parades(self):
        # t3 no té shape_id: el traçat són les dues parades.
        patro = self.json["patrons"][self.json["expedicions"]["dissabte"][0]]
        self.assertNotIn("sortides", patro)
        self.assertEqual(2, len(circulacions.descodifica_polilinia(self.json["tracats"][patro["tracat"]])))


    def test_el_patro_diu_quines_parades_son(self):
        patro = self.json["patrons"][0]
        self.assertEqual(["Nord", "Mig", "Sud"], [self.json["parades"][i] for i in patro["parades"]])
        self.assertNotIn("origen", patro)
        self.assertNotIn("desti", patro)


class TestParadesALaFrontera(unittest.TestCase):
    """Un tren que surt de Catalunya: la darrera parada és virtual i el destí real es desa a part."""

    def test_desti_real_fora_de_catalunya(self):
        with tempfile.TemporaryDirectory() as d:
            cami = crea_gtfs(f"{d}/tren.zip", GTFS_TREN)
            c = Circulacions(linies={"prova:T": {"id": "prova:T", "codi": "R1", "mode": "rodalies",
                                                "operador": "Renfe", "color": "7dbcec"}})
            # Només el nord és «dins»: el Sud (41,400) queda fora.
            c.afegeix_feed(cami, "gtfs_prova", {"T": "prova:T"}, des_de=dt.date(2026, 11, 2),
                           dins=lambda lat, lon: lat >= 41.404)
            j = c.a_json("2026-11-02")
        patro = j["patrons"][j["expedicions"]["feiner"][0]]
        self.assertEqual(["Nord", "Mig"], [j["parades"][i] for i in patro["parades"][:2]])
        self.assertEqual(-1, patro["parades"][-1])
        self.assertEqual("Sud", j["parades"][patro["desti"][0]])
        self.assertEqual(240, patro["desti"][1])
        self.assertNotIn("origen", patro)


class TestRetall(unittest.TestCase):
    # Una via recta de nord a sud, amb la frontera a 41,405 (només el nord és «dins»).
    punts = [(41.41, 2.2), (41.40, 2.2)]
    dins = staticmethod(lambda lat, lon: lat >= 41.405)

    def test_un_recorregut_que_surt_s_acaba_a_la_frontera(self):
        total = distancia(41.41, 2.2, 41.40, 2.2)
        distancies = [0.0, total * 0.25, total]
        retall = circulacions.retalla(self.punts, distancies, self.dins)
        self.assertEqual((0, 1), (retall.primera, retall.ultima))
        self.assertAlmostEqual(total / 2, retall.distancies[-1], delta=10)
        self.assertAlmostEqual(41.405, retall.punts[-1][0], places=3)
        # Surt de la parada 1 a les 100 s i arriba a la 2 als 400 s: la frontera (a 1/3 del tros) als 200 s.
        temps = circulacions.temps_retallats(retall, distancies, [(0, 0), (90, 100), (400, 400)])
        self.assertEqual([(0, 0), (90, 100)], temps[:2])
        self.assertAlmostEqual(200, temps[2][0], delta=3)

    def test_un_recorregut_que_entra_comenca_a_la_frontera(self):
        total = distancia(41.41, 2.2, 41.40, 2.2)
        punts = list(reversed(self.punts))
        retall = circulacions.retalla(punts, [0.0, total], self.dins)
        self.assertIsNotNone(retall.entrada)
        self.assertAlmostEqual(0, retall.distancies[0])
        self.assertAlmostEqual(total / 2, retall.distancies[-1], delta=10)
        self.assertAlmostEqual(41.405, retall.punts[0][0], places=3)

    def test_tot_dins_o_tot_fora(self):
        self.assertIs(self.punts, circulacions.retalla(self.punts, [0.0, 10.0], lambda lat, lon: True).punts)
        self.assertIsNone(circulacions.retalla(self.punts, [0.0, 10.0], lambda lat, lon: False))


class TestLiniesAmbVehicles(unittest.TestCase):
    def test_trens_i_busos_pero_no_la_resta(self):
        with tempfile.TemporaryDirectory() as d:
            cami = f"{d}/linies.csv"
            with open(cami, "w", encoding="utf-8") as f:
                f.write("id,operador,mode,codi,nom,color\n"
                        "tmb:1.1.1,TMB,metro,L1,Hospital de Bellvitge - Fondo,CE1126\n"
                        "amb:129,AMB,bus,87,Horta-Gràcia,ffaa00\n"
                        "interurbans:42,Generalitat de Catalunya,bus,e3,Barcelona - Igualada,\n"
                        "prova:X,Prova,funicular,F,Funicular,\n")
            per_feed, files = genera_circulacions.linies_amb_vehicles(cami)
        self.assertEqual({"gtfs_tmb": {"1.1.1": "tmb:1.1.1"}, "gtfs_amb": {"129": "amb:129"},
                          "gtfs_interurbans": {"42": "interurbans:42"}}, per_feed)
        self.assertEqual("bus", files["amb:129"]["mode"])


if __name__ == "__main__":
    unittest.main()
