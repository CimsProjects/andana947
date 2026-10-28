import unittest

from test import utils  # noqa: F401  (afegeix scripts/ al camí)

from andana.text import normalitza, slug


class TestText(unittest.TestCase):
    def test_els_noms_de_fonts_diferents_coincideixen(self):
        self.assertEqual(normalitza("RBLA. JUST OLIVERAS"), normalitza("Rambla Just Oliveras"))
        self.assertEqual(normalitza("PARAL·LEL"), normalitza("Paral·lel"))
        self.assertEqual(normalitza("BARÓ DE VIVER"), normalitza("Baró de Viver"))
        self.assertEqual(normalitza("Pl. de Catalunya"), normalitza("Plaça Catalunya"))

    def test_slug(self):
        self.assertEqual("baro-de-viver", slug("Baró de Viver"))


if __name__ == "__main__":
    unittest.main()
