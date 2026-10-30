"""Tests del catàleg de l'avatar (scripts/avatar/cataleg.py i scripts/genera_avatar.py)."""

import unittest

from test import utils  # noqa: F401  (afegeix scripts/ al camí)

import genera_avatar as g
from avatar import cataleg as c


class TestAvatar(unittest.TestCase):
    def test_mirall(self):
        self.assertEqual(c.mirall("M10 20 L30 40 Z"), "M190 20 L170 40 Z")
        self.assertEqual(c.mirall(c.mirall("M10 20 C1 2 3 4 5 6 Q7 8 9 10 Z")), "M10 20 C1 2 3 4 5 6 Q7 8 9 10 Z")

    def test_cataleg_coherent(self):
        g.comprova()

    def test_kotlin_al_dia(self):
        self.assertEqual(g.KOTLIN.read_text(encoding="utf-8"), g.kotlin(),
                         "Torna a executar python3 scripts/genera_avatar.py")


if __name__ == "__main__":
    unittest.main()
