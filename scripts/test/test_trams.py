import unittest

from andana.circulacions import codifica_polilinia
from andana.trams import trams

# Dades de prova (no del joc): una línia de tren recta de nord a sud amb tres parades i una línia de bus.
TRACAT = codifica_polilinia([(41.4100, 2.2000), (41.4000, 2.2000)])
CIRCULACIONS = {
    "linies": [
        {"id": "prova:R1", "codi": "R1", "mode": "rodalies"},
        {"id": "prova:B1", "codi": "B1", "mode": "bus"},
    ],
    "tracats": [TRACAT],
    "patrons": [
        # Nord (0 m), Mig (556 m) i Sud (1112 m).
        {"linia": 0, "tracat": 0, "distancies": [0, 556, 1112], "arribades": [0, 60, 120], "parades": [0, 1, 2]},
        # La tornada: els mateixos trams.
        {"linia": 0, "tracat": 0, "distancies": [1112, 556, 0], "arribades": [0, 60, 120], "parades": [2, 1, 0]},
        # Un bus amb una parada virtual de la frontera al mig: no fa tram entre les dues de dins.
        {"linia": 1, "tracat": 0, "distancies": [0, 556, 1112], "arribades": [0, 60, 120], "parades": [0, -1, 2]},
    ],
}
NODES = {
    "prova:R1": [("t:nord", 41.4100, 2.2001), ("t:mig", 41.4050, 2.2000), ("t:sud", 41.4000, 2.2000)],
    "prova:B1": [("b:nord", 41.4100, 2.2000), ("b:sud", 41.4000, 2.2000)],
}


class TestTrams(unittest.TestCase):
    def test_parades_seguides_fan_trams(self):
        llista, aparellades, sense_node = trams(CIRCULACIONS, NODES)
        self.assertIn(("prova:R1", "t:mig", "t:nord"), llista)
        self.assertIn(("prova:R1", "t:mig", "t:sud"), llista)
        # L'anada i la tornada no dupliquen trams, i no hi ha cap tram que se salti una parada.
        self.assertEqual(2, sum(1 for t in llista if t[0] == "prova:R1"))
        self.assertEqual(0, sense_node)
        self.assertEqual(8, aparellades)

    def test_la_frontera_talla_el_recorregut(self):
        llista, _, _ = trams(CIRCULACIONS, NODES)
        self.assertFalse(any(t[0] == "prova:B1" for t in llista))

    def test_una_parada_lluny_dels_nodes_no_fa_tram(self):
        nodes = {"prova:R1": [("t:nord", 41.4100, 2.2000), ("t:sud", 41.4000, 2.2000)]}
        llista, _, sense_node = trams(CIRCULACIONS, nodes)
        # El Mig no té node: el nord i el sud no queden units.
        self.assertEqual([], llista)
        self.assertEqual(2, sense_node)


if __name__ == "__main__":
    unittest.main()
