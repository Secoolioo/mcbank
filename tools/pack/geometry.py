"""Die Masse des WANTED-Plakats an einer einzigen Stelle.

Alles rechnet in "Textpixeln" - der Einheit, in der Minecraft Schrift misst. Der Titel wird
vierfach vergroessert gezeichnet, ein Textpixel ist dort also vier Bildschirmpixel. Bei
Bildschirmgroesse 1920x1080 und Oberflaechen-Groesse 4 bleiben 120 x 67,5 Textpixel uebrig -
daraus folgt das Plakatmass 120 x 66.

Die Herkunft der Zahlen steht in jedem Feld, damit spaeter niemand raten muss.
"""

# ---------------------------------------------------------------- Grundmasse

BREITE = 120          # Textpixel; fuellt die Titelzeile bei Oberflaechen-Groesse 4 genau aus
HOEHE = 66            # Textpixel; 98 % der verfuegbaren Hoehe

STREIFEN = 4          # So viele Spalten-Glyphen ergeben das Plakat
STREIFEN_BREITE = BREITE // STREIFEN          # 30 Textpixel je Streifen

# Quellaufloesung je Streifen. 100/220 ist exakt 30/66, die Skalierung bleibt also ganzzahlig
# und es entstehen keine Rundungsfehler in der Breite.
QUELL_BREITE = 100
QUELL_HOEHE = 220

# Ein Glyph darf hoechstens 256x256 Pixel gross sein - beide Masse liegen darunter.
assert QUELL_BREITE <= 256 and QUELL_HOEHE <= 256

# Senkrechte Lage: ein Glyph beginnt bei zeilen_y + 7 - ascent, im Titel ist zeilen_y = -10.
# Der Ursprung liegt in der Bildschirmmitte, y waechst nach unten.
BASIS = -3            # -10 + 7
ASCENT_PLAKAT = 30    # Oberkante bei -33, Unterkante bei +33 - senkrecht mittig


def ascent_fuer(oberkante):
    """Der ascent, mit dem ein Glyph seine Oberkante genau bei dieser Textzeile hat."""
    return BASIS - oberkante


# ------------------------------------------------------- Aufteilung der Flaeche
# Alle Angaben in Textpixeln, Oberkante bis Unterkante, relativ zur Bildschirmmitte.

PLAKAT_OBEN = -33
PLAKAT_UNTEN = 33

SCHLAGZEILE = (-33, -27)      # "GESUCHT", in die Kunst gebacken
UNTERZEILE = (-27, -24)       # "TOT ODER LEBENDIG", in die Kunst gebacken
# Zwischen -24 und -22 bleibt Luft fuer den Rahmen um das Portrait.
GESICHT = (-22, 2)            # 24 x 24, vom Server aus Skin-Pixeln gemalt
NAME = (3, 11)                # Spielername, eigene Schrift, Hoehe 8
BELOHNUNG = (13, 29)          # Betrag, eigene Schrift, Hoehe 16
FUSS = (29, 33)               # Zierleiste, in die Kunst gebacken


# ------------------------------------------------------------------- Gesicht

GESICHT_PIXEL = 3             # Ein Skin-Pixel wird zu 3 x 3 Textpixeln
GESICHT_KANTE = 8 * GESICHT_PIXEL             # 24 Textpixel
# pixel.png ist 8 breit und 64 hoch: oben ein weisses 8x8-Feld, darunter durchsichtig.
# Die Polsterung erlaubt ein grosses height (und damit grosse ascents), waehrend der
# sichtbare Punkt klein bleibt.
PIXEL_QUELL_BREITE = 8
PIXEL_QUELL_HOEHE = 64
GESICHT_HEIGHT = PIXEL_QUELL_HOEHE * GESICHT_PIXEL // PIXEL_QUELL_BREITE   # 24
# Minecraft setzt zwischen zwei Glyphen einen Pixel Abstand, der Vorschub ist also
# Breite + 1. Ein Abstandszeichen mit -1 zieht ihn wieder ab.
GESICHT_ADVANCE = GESICHT_PIXEL + 1

def gesicht_ascents():
    """Je Skin-Zeile ein ascent, damit die acht Zeilen luekenlos untereinander sitzen."""
    return [ascent_fuer(GESICHT[0] + zeile * GESICHT_PIXEL) for zeile in range(8)]


# --------------------------------------------------------------- Eigene Schrift
# Zwei Groessen: klein fuer den Namen, gross fuer den Betrag.

# Kleine Schrift: Zelle 12x16 in der Quelle, gezeichnet mit Hoehe 8 (also halbiert).
KLEIN_ZELLE = (12, 16)
KLEIN_HEIGHT = 8
KLEIN_BREITE = KLEIN_ZELLE[0] * KLEIN_HEIGHT // KLEIN_ZELLE[1]      # 6
KLEIN_ADVANCE = KLEIN_BREITE + 1                                     # 7
KLEIN_ASCENT = ascent_fuer(NAME[0])                                  # -6

# Grosse Schrift: Zelle 24x32, gezeichnet mit Hoehe 16 (ebenfalls halbiert).
GROSS_ZELLE = (24, 32)
GROSS_HEIGHT = 16
GROSS_BREITE = GROSS_ZELLE[0] * GROSS_HEIGHT // GROSS_ZELLE[1]       # 12
GROSS_ADVANCE = GROSS_BREITE + 1                                     # 13
GROSS_ASCENT = ascent_fuer(BELOHNUNG[0])                             # -16

# Zeichenvorrat. Spielernamen bestehen nur aus A-Z, a-z, 0-9 und _; fuer das Plakat werden
# sie in Grossbuchstaben gesetzt, damit dieser Satz reicht.
KLEIN_ZEICHEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-. "
KLEIN_SPALTEN = 8

# Die grossen Ziffern liegen im Privatnutzungsbereich statt auf echten Ziffern: sonst
# haetten zwei Provider denselben Codepoint und wuerden sich gegenseitig verdraengen.
GROSS_ZEICHEN = "0123456789.,x"
GROSS_BASIS_CODEPOINT = 0xE300
GROSS_SPALTEN = 4


# ----------------------------------------------------------------- Codepoints

CP_ABSTAND_PLUS = 0xE010      # +1, +2, +4 ... als Zweierpotenzen
CP_ABSTAND_MINUS = 0xE020     # -1, -2, -4 ...
ABSTAND_POTENZEN = 11         # bis +-1024

CP_STREIFEN = 0xE100          # vier Plakat-Spalten
CP_GESICHT = 0xE200           # acht Gesichtszeilen


def pruefe():
    """Die Regeln, deren Verletzung den Client die ganze Schrift verwerfen laesst."""
    fehler = []
    for name, ascent, height in [
            ("plakat", ASCENT_PLAKAT, HOEHE),
            ("klein", KLEIN_ASCENT, KLEIN_HEIGHT),
            ("gross", GROSS_ASCENT, GROSS_HEIGHT),
    ] + [("gesicht%d" % i, a, GESICHT_HEIGHT) for i, a in enumerate(gesicht_ascents())]:
        if ascent > height:
            fehler.append("%s: ascent %d > height %d" % (name, ascent, height))
    if STREIFEN * (STREIFEN_BREITE + 1) - STREIFEN != BREITE:
        fehler.append("Streifenbreiten ergeben nicht %d" % BREITE)
    if len(KLEIN_ZEICHEN) > KLEIN_SPALTEN * 8:
        fehler.append("zu viele kleine Zeichen")
    return fehler
