#!/usr/bin/env python3
"""Zeichnet das fertige Plakat so, wie der Client es zeigen wird.

Das ist mehr als eine Vorschau: hier wird dieselbe Abfolge aus Glyphen und Abstaenden
gebaut, die spaeter die Java-Seite schickt, und mit denselben Regeln gezeichnet, die der
Client anwendet. Stimmt die Vorschau, stimmt die Rechnung - und ein Fehler faellt hier auf
statt erst auf dem Server.

Aufruf:  python3 tools/pack/preview.py [ziel.png]
"""

import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import geometry as g

PACK = "src/main/pack"
LUPE = 4          # Der Titel wird vierfach vergroessert gezeichnet.


class Schrift:
    """Laedt die Font aus dem Pack und beantwortet: wie breit und wie hoch ist ein Zeichen."""

    def __init__(self, pack):
        with open(os.path.join(pack, "assets/bankranking/font/kopfgeld.json"),
                  encoding="utf-8") as f:
            providers = json.load(f)["providers"]
        self.abstand = {}
        self.glyphen = {}
        for p in providers:
            if p["type"] == "space":
                for zeichen, weite in p["advances"].items():
                    self.abstand[zeichen] = weite
                continue
            pfad = os.path.join(pack, "assets/bankranking/textures",
                                p["file"].split(":", 1)[1])
            blatt = Image.open(pfad).convert("RGBA")
            zeilen = p["chars"]
            spalten = max(len(z) for z in zeilen)
            zell_b = blatt.width // spalten
            zell_h = blatt.height // len(zeilen)
            for zi, zeile in enumerate(zeilen):
                for si, zeichen in enumerate(zeile):
                    if zeichen == " ":
                        continue
                    zelle = blatt.crop((si * zell_b, zi * zell_h,
                                        (si + 1) * zell_b, (zi + 1) * zell_h))
                    self.glyphen[zeichen] = (zelle, p["height"], p["ascent"])

    def breite(self, zeichen):
        """Der Vorschub eines Zeichens - genau das, was der Client aufaddiert."""
        if zeichen in self.abstand:
            return self.abstand[zeichen]
        zelle, hoehe, _ = self.glyphen[zeichen]
        sichtbar = _sichtbare_breite(zelle)
        return round(sichtbar * hoehe / zelle.height) + 1


def _sichtbare_breite(zelle):
    """Die rechteste Spalte mit Deckkraft bestimmt die Breite - so macht es der Client."""
    alpha = np.array(zelle)[:, :, 3]
    spalten = np.where(alpha.any(axis=0))[0]
    return int(spalten[-1]) + 1 if len(spalten) else 0


def zeichne(schrift, teile, breite_px, hoehe_px, links_textzeile, oben_textzeile):
    """Malt eine Folge aus (Zeichen, Farbe) und liefert Bild und Gesamtvorschub."""
    leinwand = Image.new("RGBA", (breite_px * LUPE, hoehe_px * LUPE), (24, 26, 32, 255))
    cursor = 0
    for zeichen, farbe in teile:
        if zeichen in schrift.abstand:
            cursor += schrift.abstand[zeichen]
            continue
        zelle, hoehe, ascent = schrift.glyphen[zeichen]
        ziel_b = round(_sichtbare_breite(zelle) * hoehe / zelle.height)
        skaliert = zelle.resize((max(1, ziel_b * LUPE), hoehe * LUPE), Image.NEAREST)
        if farbe is not None:
            eingefaerbt = np.array(skaliert).astype(np.float32)
            eingefaerbt[:, :, 0] *= farbe[0] / 255.0
            eingefaerbt[:, :, 1] *= farbe[1] / 255.0
            eingefaerbt[:, :, 2] *= farbe[2] / 255.0
            skaliert = Image.fromarray(eingefaerbt.astype(np.uint8), "RGBA")
        oben = (g.BASIS - ascent) - oben_textzeile
        leinwand.alpha_composite(
            skaliert, (int((cursor - links_textzeile) * LUPE), int(oben * LUPE)))
        cursor += ziel_b + 1
    return leinwand, cursor


# --------------------------------------------------------- Aufbau der Zeile

def abstand(schrift, weite):
    """Zerlegt einen Abstand in Zweierpotenz-Zeichen - hoechstens elf Stueck."""
    teile = []
    rest = abs(weite)
    basis = g.CP_ABSTAND_PLUS if weite > 0 else g.CP_ABSTAND_MINUS
    for i in reversed(range(g.ABSTAND_POTENZEN)):
        if rest >= 2 ** i:
            rest -= 2 ** i
            teile.append((chr(basis + i), None))
    return teile


def plakat_zeile(schrift, gesicht, name, belohnung):
    """Die vollstaendige Titelzeile: Hintergrund, Gesicht, Name, Betrag.

    Der Cursor beginnt in der Bildschirmmitte, weil die Gesamtbreite am Ende auf null
    gebracht wird - der Client zentriert dann eine Zeile der Breite null genau dort.
    """
    teile = []
    cursor = 0

    def springe_auf(ziel):
        nonlocal cursor
        teile.extend(abstand(schrift, ziel - cursor))
        cursor = ziel

    # Hintergrund: vier Streifen, dazwischen je ein Pixel zurueck.
    springe_auf(-g.BREITE // 2)
    for i in range(g.STREIFEN):
        teile.append((chr(g.CP_STREIFEN + i), (255, 255, 255)))
        teile.append((chr(g.CP_ABSTAND_MINUS), None))
        cursor += g.STREIFEN_BREITE
    # Gesicht: Zeile fuer Zeile, jede endet mit einem Ruecksprung an den Anfang.
    springe_auf(-g.GESICHT_KANTE // 2)
    anfang = cursor
    for zeile in range(8):
        for spalte in range(8):
            teile.append((chr(g.CP_GESICHT + zeile), gesicht[zeile][spalte]))
            teile.append((chr(g.CP_ABSTAND_MINUS), None))
        cursor += 8 * g.GESICHT_PIXEL
        teile.extend(abstand(schrift, anfang - cursor))
        cursor = anfang

    # Name, mittig. Die Breite wird aus den Einzelvorschueben aufaddiert, weil die
    # Zeichen unterschiedlich breit sind.
    text = [z for z in name.upper()[:16] if z in schrift.glyphen]
    springe_auf(-sum(schrift.breite(z) for z in text) // 2)
    for z in text:
        teile.append((z, (58, 40, 26)))
        cursor += schrift.breite(z)

    # Belohnung: je Posten ein Sinnbild und die Stueckzahl daneben.
    posten = []
    for material, anzahl in belohnung[:2]:
        symbol = chr(g.CP_ITEM + g.ITEM_NAMEN.index(material))
        ziffern = [chr(g.GROSS_BASIS_CODEPOINT + g.GROSS_ZEICHEN.index(z))
                   for z in str(anzahl) if z in g.GROSS_ZEICHEN]
        posten.append((symbol, ziffern))

    gesamt = 0
    for i, (symbol, ziffern) in enumerate(posten):
        gesamt += schrift.breite(symbol) + 2 + sum(schrift.breite(z) for z in ziffern)
        if i:
            gesamt += 8
    springe_auf(-gesamt // 2)
    for i, (symbol, ziffern) in enumerate(posten):
        if i:
            teile.extend(abstand(schrift, 8))
            cursor += 8
        teile.append((symbol, (255, 255, 255)))
        cursor += schrift.breite(symbol)
        teile.extend(abstand(schrift, 2))
        cursor += 2
        for z in ziffern:
            teile.append((z, (122, 24, 18)))
            cursor += schrift.breite(z)

    # Ausgleich: die Zeile muss die Breite null haben, sonst sitzt sie nicht mittig.
    springe_auf(0)
    return teile


def beispiel_gesicht():
    """Ein erfundenes Gesicht, damit sich die Lage beurteilen laesst."""
    haut, haar, auge, mund = (198, 152, 112), (72, 48, 30), (60, 90, 160), (150, 84, 70)
    raster = [[haut] * 8 for _ in range(8)]
    for x in range(8):
        raster[0][x] = haar
        raster[1][x] = haar
    for x in (0, 7):
        for y in range(2, 8):
            raster[y][x] = haar
    raster[3][2] = raster[3][5] = auge
    raster[5][3] = raster[5][4] = mund
    return raster


if __name__ == "__main__":
    schrift = Schrift(PACK)
    teile = plakat_zeile(schrift, beispiel_gesicht(), "Secoolioo",
                         [("netherite_block", 3), ("diamond", 64)])
    bild, gesamt = zeichne(schrift, teile, g.BREITE + 8, g.HOEHE + 8,
                           -g.BREITE // 2 - 4, g.PLAKAT_OBEN - 4)

    ziel = sys.argv[1] if len(sys.argv) > 1 else "src/main/plakat-simulation.png"
    bild.convert("RGB").save(ziel)
    print("Zeichen in der Zeile : %d" % len(teile))
    print("Gesamtvorschub       : %d  (muss 0 sein, sonst sitzt das Plakat nicht mittig)"
          % gesamt)
    print("Simulation           : %s  (%d x %d)" % (ziel, bild.width, bild.height))
