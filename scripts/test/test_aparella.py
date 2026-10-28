import unittest

from test import utils  # noqa: F401

from andana.aparella import aparella, similitud


class TestAparella(unittest.TestCase):
    def test_abreviatures_de_tmb(self):
        self.assertEqual(1.0, similitud("P. DE GRÀCIA", "Passeig de Gràcia"))
        self.assertEqual(1.0, similitud("R.J.OLIVERAS", "Rambla Just Oliveras"))
        self.assertEqual(1.0, similitud("PARAL.LEL", "Paral·lel"))
        self.assertEqual(1.0, similitud("ST. ANDREU", "Sant Andreu"))
        self.assertGreater(similitud("CATALUNYA", "Catalunya"), similitud("CATALUNYA", "Plaça de Catalunya"))

    def test_aparella_una_linia(self):
        l4 = {"a": "Ciutadella | Vila Olímpica", "b": "Barceloneta", "c": "Guinardó | Hospital de Sant Pau"}
        self.assertEqual({"CIUTADELLA": "a", "BARCELONETA": "b", "GUINARDÓ": "c"},
                         aparella(["CIUTADELLA", "BARCELONETA", "GUINARDÓ"], l4))
        l5 = {"d": "Sant Pau | Dos de Maig", "e": "Camp de l'Arpa"}
        self.assertEqual({"H. DE ST. PAU": "d", "CAMP DE L´ARPA": "e"}, aparella(["H. DE ST. PAU", "CAMP DE L´ARPA"], l5))

    def test_l_ultim_sense_parella_s_aparella_amb_l_ultim_lliure(self):
        self.assertEqual({"A": "x", "QUALSEVOL": "y"}, aparella(["A", "QUALSEVOL"], {"x": "A", "y": "Una altra"}))

    def test_no_aparella_noms_diferents(self):
        self.assertEqual({}, aparella(["VERDAGUER", "DIAGONAL"], {"x": "Sagrera", "y": "Fondo"}))


if __name__ == "__main__":
    unittest.main()
