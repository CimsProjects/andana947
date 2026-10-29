import unittest

from test import utils  # noqa: F401  (afegeix scripts/ al camí)

import genera_contorn


def quadrat(x, y, costat):
    return {"type": "Polygon", "coordinates": [[[x, y], [x + costat, y], [x + costat, y + costat], [x, y + costat], [x, y]]]}


class TestGeneraContorn(unittest.TestCase):
    def test_els_municipis_veins_fan_un_sol_forat_i_la_capsa_els_inclou(self):
        municipis = {"features": [{"geometry": quadrat(1.0, 41.0, 0.5)}, {"geometry": quadrat(1.5, 41.0, 0.5)}]}
        resultat = genera_contorn.mascara(genera_contorn.contorn(municipis))
        entitat = resultat["features"][0]
        self.assertEqual([1.0, 41.0, 2.0, 41.5], entitat["properties"]["capsa"])
        anells = entitat["geometry"]["coordinates"]
        self.assertEqual(genera_contorn.MON, anells[0])
        self.assertEqual(2, len(anells))

    def test_els_trossos_minusculs_es_descarten_i_les_illes_es_queden(self):
        municipis = {"features": [
            {"geometry": quadrat(1.0, 41.0, 0.5)},
            {"geometry": quadrat(3.0, 42.0, 0.01)},
            {"geometry": quadrat(2.5, 40.5, 0.0001)},
        ]}
        self.assertEqual(2, len(genera_contorn.contorn(municipis)))


if __name__ == "__main__":
    unittest.main()
