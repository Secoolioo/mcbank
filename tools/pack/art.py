"""Zeichnet die Grafiken des Resourcepacks: Plakat, Pixelpunkt und zwei eigene Schriften.

Alles entsteht aus Code, nichts wird von aussen geladen - so bleibt der Bau reproduzierbar
und es haengt keine fremde Datei am Projekt. Der Zufall benutzt einen festen Startwert,
damit zwei Laeufe dasselbe Bild ergeben und der Pack-Hash sich nicht grundlos aendert.
"""

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import geometry as g

SEED = 20260906

SCHRIFT_FETT = "/usr/share/fonts/google-noto/NotoSerif-CondensedBlack.ttf"
SCHRIFT_HALB = "/usr/share/fonts/google-noto/NotoSerif-CondensedExtraBold.ttf"

# Farben des gealterten Papiers.
PAPIER_HELL = (222, 202, 160)
PAPIER_DUNKEL = (188, 162, 116)
TINTE = (46, 32, 22)
TINTE_MATT = (92, 70, 48)


def _rauschen(rng, breite, hoehe, koernung):
    """Weiches Rauschen zwischen 0 und 1, fuer Papierfaserung und Flecken."""
    klein = rng.random((max(1, hoehe // koernung), max(1, breite // koernung)))
    bild = Image.fromarray((klein * 255).astype(np.uint8)).resize((breite, hoehe), Image.BICUBIC)
    return np.asarray(bild, dtype=np.float32) / 255.0


def papier(rng, breite, hoehe):
    """Das Pergament: Grundton, Faserung, Flecken und dunklere Raender."""
    fein = _rauschen(rng, breite, hoehe, 2)
    grob = _rauschen(rng, breite, hoehe, 14)
    flecken = _rauschen(rng, breite, hoehe, 40)

    # Grundton zwischen hell und dunkel mischen, gesteuert von den groberen Schichten.
    misch = np.clip(0.45 * grob + 0.40 * flecken + 0.15 * fein, 0.0, 1.0)
    misch = np.clip((misch - 0.30) * 1.9, 0.0, 1.0)

    hell = np.array(PAPIER_HELL, dtype=np.float32)
    dunkel = np.array(PAPIER_DUNKEL, dtype=np.float32)
    bild = hell[None, None, :] * (1.0 - misch[:, :, None]) + dunkel[None, None, :] * misch[:, :, None]

    # Zum Rand hin nachdunkeln: altes Papier vergilbt aussen zuerst.
    yy, xx = np.mgrid[0:hoehe, 0:breite].astype(np.float32)
    rand = np.maximum(
        np.abs(xx / (breite - 1) - 0.5) * 2.0,
        np.abs(yy / (hoehe - 1) - 0.5) * 2.0,
    )
    schatten = np.clip((rand - 0.55) / 0.45, 0.0, 1.0) ** 1.6
    bild *= (1.0 - 0.30 * schatten)[:, :, None]

    # Ein paar dunkle Sprenkel als Stockflecken.
    sprenkel = rng.random((hoehe, breite)) > 0.9985
    bild[sprenkel] *= 0.55
    return np.clip(bild, 0, 255).astype(np.uint8)


def _passende_schrift(zeichner, text, schriftdatei, breite, hoehe):
    """Sucht die groesste Schriftgroesse, mit der der Text noch in das Feld passt.

    Die Punktgroesse einer Schrift sagt nichts darueber, wie hoch die Buchstaben wirklich
    werden - je nach Schnitt und Zeichenvorrat weicht das deutlich ab. Deshalb wird
    gemessen statt gerechnet, sonst ragt die Schlagzeile aus ihrem Band heraus.
    """
    beste = ImageFont.truetype(schriftdatei, 8)
    for groesse in range(8, 200):
        schrift = ImageFont.truetype(schriftdatei, groesse)
        links, hoch, rechts, runter = zeichner.textbbox((0, 0), text, font=schrift)
        if rechts - links > breite or runter - hoch > hoehe:
            break
        beste = schrift
    return beste


def _text_ins_band(zeichner, mitte_x, band, text, schriftdatei, farbe, breite, fuellgrad=1.0):
    """Setzt Text mittig in ein Band, so gross wie er dort hineinpasst."""
    oben, unten = band
    feld_hoehe = (unten - oben) * fuellgrad
    schrift = _passende_schrift(zeichner, text, schriftdatei, breite, feld_hoehe)
    links, hoch, rechts, runter = zeichner.textbbox((0, 0), text, font=schrift)
    x = mitte_x - (rechts - links) / 2 - links
    y = oben + ((unten - oben) - (runter - hoch)) / 2 - hoch
    zeichner.text((x, y), text, font=schrift, fill=farbe)
    return rechts - links


def _band(oben, unten):
    """Rechnet eine Textzeilen-Spanne in Bildpunkte der Plakatquelle um."""
    hoehe = g.QUELL_HOEHE
    return (
        (oben - g.PLAKAT_OBEN) / g.HOEHE * hoehe,
        (unten - g.PLAKAT_OBEN) / g.HOEHE * hoehe,
    )


def plakat():
    """Das vollstaendige Plakat als RGBA-Bild, 400 x 220 Bildpunkte."""
    rng = np.random.default_rng(SEED)
    breite = g.QUELL_BREITE * g.STREIFEN
    hoehe = g.QUELL_HOEHE

    bild = Image.fromarray(papier(rng, breite, hoehe), "RGB").convert("RGBA")
    zeichner = ImageDraw.Draw(bild)
    mitte = breite / 2

    # --- Rahmenlinien ---
    zeichner.rectangle([4, 4, breite - 5, hoehe - 5], outline=TINTE, width=3)
    zeichner.rectangle([10, 10, breite - 11, hoehe - 11], outline=TINTE_MATT, width=1)

    # --- Schlagzeile ---
    band = _band(*g.SCHLAGZEILE)
    _text_ins_band(zeichner, mitte, band, "GESUCHT", SCHRIFT_FETT, TINTE, breite * 0.62)

    # --- Unterzeile mit Zierstrichen links und rechts ---
    band = _band(*g.UNTERZEILE)
    zeilenmitte = (band[0] + band[1]) / 2
    weite = _text_ins_band(zeichner, mitte, band, "TOT ODER LEBENDIG",
                           SCHRIFT_HALB, TINTE, breite * 0.46, fuellgrad=0.90)
    for seite in (-1, 1):
        innen = mitte + seite * (weite / 2 + 10)
        aussen = mitte + seite * (breite * 0.44)
        zeichner.line([(innen, zeilenmitte - 1), (aussen, zeilenmitte - 1)],
                      fill=TINTE_MATT, width=1)
        zeichner.line([(innen + 4, zeilenmitte + 2), (aussen - 4, zeilenmitte + 2)],
                      fill=TINTE_MATT, width=1)

    # --- Portraitrahmen: die Flaeche, in die der Server das Gesicht malt ---
    oben, unten = _band(*g.GESICHT)
    halb = (unten - oben) / 2
    kasten = [mitte - halb - 3, oben - 3, mitte + halb + 3, unten + 3]
    zeichner.rectangle(kasten, fill=(28, 21, 15, 255))
    zeichner.rectangle(kasten, outline=TINTE, width=3)
    zeichner.rectangle([kasten[0] + 3, kasten[1] + 3, kasten[2] - 3, kasten[3] - 3],
                       outline=(126, 100, 66, 255), width=1)
    # Vier Ziernaegel in den Ecken des Rahmens.
    for ex in (kasten[0] + 2, kasten[2] - 2):
        for ey in (kasten[1] + 2, kasten[3] - 2):
            zeichner.ellipse([ex - 2, ey - 2, ex + 2, ey + 2], fill=(150, 128, 96, 255))

    # --- Trennlinie ueber der Belohnung ---
    oben, _ = _band(*g.BELOHNUNG)
    zeichner.line([(mitte - breite * 0.36, oben - 4), (mitte + breite * 0.36, oben - 4)],
                  fill=TINTE_MATT, width=2)

    # --- Fuss: eine Zierleiste. Text stand hier zu dicht an den Zahlen darueber. ---
    band = _band(*g.FUSS)
    linie = (band[0] + band[1]) / 2
    zeichner.line([(mitte - breite * 0.30, linie), (mitte + breite * 0.30, linie)],
                  fill=TINTE_MATT, width=2)
    for seite in (-1, 1):
        x = mitte + seite * (breite * 0.32)
        zeichner.polygon([(x, linie - 3), (x + seite * 5, linie), (x, linie + 3)],
                         fill=TINTE_MATT)

    # --- Nagelloecher oben ---
    for seite in (-1, 1):
        x = mitte + seite * (breite * 0.45)
        zeichner.ellipse([x - 5, 11, x + 5, 21], fill=(66, 48, 34, 255))
        zeichner.ellipse([x - 3, 12, x + 3, 18], fill=(158, 136, 104, 255))

    # --- Gerissene Raender: die Ecken werden durchsichtig geknabbert ---
    maske = Image.new("L", (breite, hoehe), 255)
    riss = ImageDraw.Draw(maske)
    for x in range(breite):
        riss.line([(x, 0), (x, rng.integers(0, 3))], fill=0)
        riss.line([(x, hoehe - 1 - rng.integers(0, 3)), (x, hoehe - 1)], fill=0)
    for y in range(hoehe):
        riss.line([(0, y), (rng.integers(0, 3), y)], fill=0)
        riss.line([(breite - 1 - rng.integers(0, 3), y), (breite - 1, y)], fill=0)
    maske = maske.filter(ImageFilter.MinFilter(3))
    bild.putalpha(maske)
    return bild


def streifen(voll):
    """Zerlegt das Plakat in die vier Spalten-Glyphen."""
    teile = []
    for i in range(g.STREIFEN):
        teil = voll.crop((i * g.QUELL_BREITE, 0, (i + 1) * g.QUELL_BREITE, g.QUELL_HOEHE))
        teile.append(_kante_sichern(teil))
    return teile


def _kante_sichern(bild):
    """Setzt unten rechts einen fast durchsichtigen Punkt.

    Minecraft bestimmt die Breite eines Glyphen aus der rechtesten Spalte, die ueberhaupt
    Deckkraft hat. Ohne diesen Punkt schrumpfte ein Glyph mit durchsichtigem Rand, und alle
    danach berechneten Abstaende waeren verschoben.
    """
    daten = np.array(bild)
    if daten[-1, -1, 3] == 0:
        daten[-1, -1] = [0, 0, 0, 1]
    return Image.fromarray(daten, "RGBA")


def pixelpunkt():
    """Der weisse Punkt, aus dem der Server das Gesicht malt."""
    daten = np.zeros((g.PIXEL_QUELL_HOEHE, g.PIXEL_QUELL_BREITE, 4), dtype=np.uint8)
    daten[0:g.PIXEL_QUELL_BREITE, :, :] = 255      # oben ein volles weisses Quadrat
    return Image.fromarray(daten, "RGBA")


def _schriftblatt(zeichen, zelle, spalten, schriftdatei, fuellgrad, ziel_hoehe):
    """Legt einen Zeichensatz als Raster ab und misst dabei jede Einzelbreite.

    Zwei Entscheidungen stecken darin, beide waren am ersten Entwurf sichtbar falsch:

    Erstens sitzen alle Zeichen auf einer gemeinsamen Grundlinie statt in ihrer Zelle
    mittig. Sonst schwebt ein Punkt in halber Hoehe, und aus 12.500 wird 12-500.

    Zweitens bekommt keine Zelle einen kuenstlichen Breiten-Anker. Minecraft bestimmt die
    Breite eines Zeichens aus seiner rechtesten deckenden Spalte; laesst man das zu, steht
    ein I schmaler als ein M und der Name liest sich wie Schrift statt wie eine Tabelle.
    Der Preis ist, dass die Java-Seite die Einzelbreiten kennen muss - deshalb liefert
    diese Funktion sie mit, und sie landen in metrics.properties.
    """
    zeilen = (len(zeichen) + spalten - 1) // spalten
    bild = Image.new("RGBA", (zelle[0] * spalten, zelle[1] * zeilen), (255, 255, 255, 0))
    zeichner = ImageDraw.Draw(bild)
    schrift = ImageFont.truetype(schriftdatei, int(zelle[1] * fuellgrad))

    hoch, tief = schrift.getmetrics()
    # Grundlinie so legen, dass Oberlaengen oben und Unterlaengen unten Platz haben.
    grundlinie = (zelle[1] + hoch - tief) / 2

    for i, z in enumerate(zeichen):
        spalte, zeile = i % spalten, i // spalten
        x0, y0 = spalte * zelle[0], zeile * zelle[1]
        zeichner.text((x0 + 1, y0 + grundlinie), z, font=schrift,
                      fill=(255, 255, 255, 255), anchor="ls")

    daten = np.array(bild)
    breiten = {}
    for i, z in enumerate(zeichen):
        spalte, zeile = i % spalten, i // spalten
        aussch = daten[zeile * zelle[1]:(zeile + 1) * zelle[1],
                       spalte * zelle[0]:(spalte + 1) * zelle[0], 3]
        gefuellt = np.where(aussch.any(axis=0))[0]
        quell_breite = int(gefuellt[-1]) + 1 if len(gefuellt) else 0
        # Genau die Rechnung des Clients: Quellbreite auf die Zielhoehe skalieren,
        # aufrunden, plus ein Pixel Abstand zum naechsten Zeichen.
        breiten[z] = int(np.ceil(quell_breite * ziel_hoehe / zelle[1])) + 1
    return Image.fromarray(daten, "RGBA"), breiten


def schrift_klein():
    return _schriftblatt(g.KLEIN_ZEICHEN, g.KLEIN_ZELLE, g.KLEIN_SPALTEN,
                         SCHRIFT_FETT, 0.80, g.KLEIN_HEIGHT)


def schrift_gross():
    return _schriftblatt(g.GROSS_ZEICHEN, g.GROSS_ZELLE, g.GROSS_SPALTEN,
                         SCHRIFT_FETT, 0.84, g.GROSS_HEIGHT)


def sinnbilder():
    """Ein Sinnbild je Einsatz-Material, damit die Belohnung ohne Worte lesbar ist.

    Gezeichnet, nicht kopiert: die Texturen von Minecraft gehoeren Mojang, und ein eigener
    Entwurf passt ohnehin besser zur Anmutung des Plakats. Bloecke bekommen einen Wuerfel in
    Schraegsicht, Rohstoffe ihre Edelsteinform - das reicht, um sie auseinanderzuhalten.
    """
    farben = {
        "diamond": ((0x8F, 0xF5, 0xEA), (0x36, 0xC0, 0xB0), (0x1B, 0x7A, 0x70)),
        "emerald": ((0x7C, 0xF7, 0xA8), (0x24, 0xC4, 0x5E), (0x0F, 0x76, 0x38)),
        "netherite": ((0x7A, 0x66, 0x6D), (0x4A, 0x3B, 0x41), (0x2A, 0x20, 0x25)),
    }
    zelle = g.ITEM_ZELLE
    bild = Image.new("RGBA", (zelle[0] * g.ITEM_SPALTEN, zelle[1]), (255, 255, 255, 0))
    zeichner = ImageDraw.Draw(bild)

    for i, name in enumerate(g.ITEM_NAMEN):
        stoff = "netherite" if name.startswith("netherite") else name.split("_")[0]
        hell, mittel, dunkel = farben[stoff]
        x0 = i * zelle[0]
        mx, my = x0 + zelle[0] / 2, zelle[1] / 2

        if name.endswith("_block") or name == "netherite_block":
            # Wuerfel in Schraegsicht: Deckflaeche hell, Vorderseite mittel, Seite dunkel.
            b, h = 8.0, 4.5
            zeichner.polygon([(mx, my - h - 5), (mx + b, my - 5), (mx, my + h - 5), (mx - b, my - 5)],
                             fill=hell, outline=dunkel)
            zeichner.polygon([(mx - b, my - 5), (mx, my + h - 5), (mx, my + h + 5), (mx - b, my + 5)],
                             fill=mittel, outline=dunkel)
            zeichner.polygon([(mx + b, my - 5), (mx, my + h - 5), (mx, my + h + 5), (mx + b, my + 5)],
                             fill=dunkel, outline=dunkel)
        elif name == "netherite_ingot":
            # Barren: ein liegendes Trapez mit heller Oberkante.
            zeichner.polygon([(mx - 8, my + 4), (mx - 6, my - 3), (mx + 6, my - 3), (mx + 8, my + 4)],
                             fill=mittel, outline=dunkel)
            zeichner.polygon([(mx - 6, my - 3), (mx - 4, my - 6), (mx + 4, my - 6), (mx + 6, my - 3)],
                             fill=hell, outline=dunkel)
        else:
            # Edelstein: Rhombus mit heller Facette oben links.
            zeichner.polygon([(mx, my - 8), (mx + 7, my), (mx, my + 8), (mx - 7, my)],
                             fill=mittel, outline=dunkel)
            zeichner.polygon([(mx, my - 8), (mx + 3, my - 2), (mx, my + 1), (mx - 4, my - 2)],
                             fill=hell)

    return _mit_breitenanker(bild, zelle, len(g.ITEM_NAMEN), g.ITEM_SPALTEN, g.ITEM_HEIGHT)


def _mit_breitenanker(bild, zelle, anzahl, spalten, ziel_hoehe):
    """Setzt je Zelle den Breiten-Anker und misst die Einzelbreiten."""
    daten = np.array(bild)
    breiten = {}
    for i in range(anzahl):
        spalte, zeile = i % spalten, i // spalten
        aussch = daten[zeile * zelle[1]:(zeile + 1) * zelle[1],
                       spalte * zelle[0]:(spalte + 1) * zelle[0], 3]
        gefuellt = np.where(aussch.any(axis=0))[0]
        quell_breite = int(gefuellt[-1]) + 1 if len(gefuellt) else 0
        breiten[i] = int(np.ceil(quell_breite * ziel_hoehe / zelle[1])) + 1
    return Image.fromarray(daten, "RGBA"), breiten


def packbild():
    """Das Vorschaubild des Packs, 64 x 64."""
    return plakat().resize((64, 64), Image.LANCZOS).convert("RGBA")
