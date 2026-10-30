import unittest

from andana.flota import flota_per_linia

# Dades de prova (no del joc): una línia amb tres expedicions d'una hora i una altra d'un sol viatge.
CIRCULACIONS = {
    "linies": [{"id": "prova:R1", "codi": "R1", "mode": "rodalies"}, {"id": "prova:R1b", "codi": "R1", "mode": "rodalies"},
               {"id": "prova:B1", "codi": "B1", "mode": "bus"}],
    "patrons": [
        {"linia": 0, "tracat": 0, "distancies": [0, 30_000], "arribades": [0, 3600]},
        {"linia": 1, "tracat": 0, "distancies": [0, 20_000], "arribades": [0, 3600]},
        {"linia": 2, "tracat": 0, "distancies": [0, 5_000], "arribades": [0, 1200]},
    ],
    # 08:00 i 08:30 (dues alhora), 09:00 (surt quan arriba la de les 08:00: el mateix vehicle) i el bus a les 10:00.
    "expedicions": {"feiner": [0, 8 * 3600, 1, 8 * 3600 + 1800, 0, 9 * 3600, 2, 10 * 3600]},
}


class TestFlota(unittest.TestCase):
    def test_vehicles_en_hora_punta(self):
        f = flota_per_linia(CIRCULACIONS)
        # Les dues rutes de la R1 són la mateixa línia; com a molt n'hi ha dues alhora.
        self.assertEqual(2, f["rodalies:R1"]["vehicles_punta"])
        self.assertEqual(3, f["rodalies:R1"]["expedicions"])
        self.assertAlmostEqual(80.0, f["rodalies:R1"]["km"])
        self.assertEqual(1, f["bus:B1"]["vehicles_punta"])

    def test_sense_expedicions_no_hi_ha_flota(self):
        self.assertEqual({}, flota_per_linia(CIRCULACIONS, "festiu"))


if __name__ == "__main__":
    unittest.main()
