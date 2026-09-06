#!/usr/bin/env python3
"""Erzeugt die losen Dateien des Resourcepacks unter src/main/pack.

Aufruf:  python3 tools/pack/build_pack.py [zielordner]

Das Ergebnis wird eingecheckt. Gradle packt daraus die ZIP, deshalb braucht ein normaler
Build kein Python. Dieses Skript laeuft nur, wenn sich die Grafik aendern soll.
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import art
import geometry as g

PACK_FORMAT = 88          # Minecraft 26.2
NAMENSRAUM = "bankranking"
FONT = "kopfgeld"


def cp(zeichen):
    return chr(zeichen)


def abstands_provider():
    """Zweierpotenzen als Vorschub, positiv und negativ.

    Damit laesst sich jeder ganzzahlige Abstand aus hoechstens elf Zeichen zusammensetzen.
    Negative Vorschuebe wirken nur, wenn der Server die Nachricht schickt - genau das ist
    hier der Fall.
    """
    vorschuebe = {}
    for i in range(g.ABSTAND_POTENZEN):
        vorschuebe[cp(g.CP_ABSTAND_PLUS + i)] = 2 ** i
        vorschuebe[cp(g.CP_ABSTAND_MINUS + i)] = -(2 ** i)
    return {"type": "space", "advances": vorschuebe}


def font_json():
    provider = [abstands_provider()]

    for i in range(g.STREIFEN):
        provider.append({
            "type": "bitmap",
            "file": "%s:font/plakat_%d.png" % (NAMENSRAUM, i),
            "ascent": g.ASCENT_PLAKAT,
            "height": g.HOEHE,
            "chars": [cp(g.CP_STREIFEN + i)],
        })

    # Acht Mal dieselbe Datei, nur mit anderem ascent: so sitzt jede Skin-Zeile eine
    # Punktreihe tiefer, ohne dass ascent jemals height uebersteigt.
    for zeile, ascent in enumerate(g.gesicht_ascents()):
        provider.append({
            "type": "bitmap",
            "file": "%s:font/pixel.png" % NAMENSRAUM,
            "ascent": ascent,
            "height": g.GESICHT_HEIGHT,
            "chars": [cp(g.CP_GESICHT + zeile)],
        })

    provider.append({
        "type": "bitmap",
        "file": "%s:font/schrift_klein.png" % NAMENSRAUM,
        "ascent": g.KLEIN_ASCENT,
        "height": g.KLEIN_HEIGHT,
        "chars": _raster(g.KLEIN_ZEICHEN, g.KLEIN_SPALTEN),
    })

    gross = "".join(cp(g.GROSS_BASIS_CODEPOINT + i) for i in range(len(g.GROSS_ZEICHEN)))
    provider.append({
        "type": "bitmap",
        "file": "%s:font/schrift_gross.png" % NAMENSRAUM,
        "ascent": g.GROSS_ASCENT,
        "height": g.GROSS_HEIGHT,
        "chars": _raster(gross, g.GROSS_SPALTEN),
    })
    return {"providers": provider}


def _raster(zeichen, spalten):
    """Teilt den Zeichenvorrat in Zeilen auf und fuellt die letzte mit Leerstellen.

    Minecraft teilt die Textur nach Anzahl der Zeilen und Laenge der laengsten Zeile auf,
    alle Zeilen muessen also gleich lang sein.
    """
    zeilen = []
    for start in range(0, len(zeichen), spalten):
        stueck = zeichen[start:start + spalten]
        zeilen.append(stueck + " " * (spalten - len(stueck)))
    return zeilen


def metriken(breiten_klein, breiten_gross):
    """Die Masse, die die Java-Seite braucht - eine einzige Quelle der Wahrheit."""
    zeilen = [
        "# Von tools/pack/build_pack.py erzeugt. Nicht von Hand aendern.",
        "font=%s:%s" % (NAMENSRAUM, FONT),
        "plakat.breite=%d" % g.BREITE,
        "plakat.hoehe=%d" % g.HOEHE,
        "plakat.streifen=%s" % "".join(cp(g.CP_STREIFEN + i) for i in range(g.STREIFEN)),
        "plakat.streifen-advance=%d" % (g.STREIFEN_BREITE + 1),
        "gesicht.zeilen=%s" % "".join(cp(g.CP_GESICHT + i) for i in range(8)),
        "gesicht.pixel=%d" % g.GESICHT_PIXEL,
        "gesicht.advance=%d" % g.GESICHT_ADVANCE,
        "gesicht.oben=%d" % g.GESICHT[0],
        "klein.zeichen=%s" % g.KLEIN_ZEICHEN,
        # Je Zeichen ein Vorschub, in derselben Reihenfolge wie klein.zeichen. Die Werte
        # sind am fertigen Bild gemessen, nicht geschaetzt - nur so trifft die Java-Seite
        # dieselbe Breite wie der Client.
        "klein.breiten=%s" % " ".join(str(breiten_klein[z]) for z in g.KLEIN_ZEICHEN),
        "klein.oben=%d" % g.NAME[0],
        "gross.zeichen=%s" % g.GROSS_ZEICHEN,
        "gross.basis=%d" % g.GROSS_BASIS_CODEPOINT,
        "gross.breiten=%s" % " ".join(str(breiten_gross[z]) for z in g.GROSS_ZEICHEN),
        "gross.oben=%d" % g.BELOHNUNG[0],
        "abstand.plus=%d" % g.CP_ABSTAND_PLUS,
        "abstand.minus=%d" % g.CP_ABSTAND_MINUS,
        "abstand.potenzen=%d" % g.ABSTAND_POTENZEN,
    ]
    return "\n".join(zeilen) + "\n"


def schreibe(ziel):
    fehler = g.pruefe()
    if fehler:
        raise SystemExit("Geometrie verletzt die Regeln des Clients:\n  " + "\n  ".join(fehler))

    assets = os.path.join(ziel, "assets", NAMENSRAUM)
    texturen = os.path.join(assets, "textures", "font")
    os.makedirs(os.path.join(assets, "font"), exist_ok=True)
    os.makedirs(texturen, exist_ok=True)

    voll = art.plakat()
    for i, teil in enumerate(art.streifen(voll)):
        teil.save(os.path.join(texturen, "plakat_%d.png" % i), optimize=True)
    art.pixelpunkt().save(os.path.join(texturen, "pixel.png"), optimize=True)
    blatt_klein, breiten_klein = art.schrift_klein()
    blatt_gross, breiten_gross = art.schrift_gross()
    blatt_klein.save(os.path.join(texturen, "schrift_klein.png"), optimize=True)
    blatt_gross.save(os.path.join(texturen, "schrift_gross.png"), optimize=True)
    art.packbild().save(os.path.join(ziel, "pack.png"), optimize=True)

    with open(os.path.join(ziel, "metrics.properties"), "w", encoding="utf-8") as f:
        f.write(metriken(breiten_klein, breiten_gross))

    with open(os.path.join(assets, "font", FONT + ".json"), "w", encoding="utf-8") as f:
        json.dump(font_json(), f, ensure_ascii=False, indent=2)
        f.write("\n")

    with open(os.path.join(ziel, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {
            "description": "BankRanking - Kopfgeld",
            "min_format": PACK_FORMAT,
            "max_format": PACK_FORMAT,
        }}, f, ensure_ascii=False, indent=2)
        f.write("\n")

    vorschau = os.path.join(os.path.dirname(ziel), "plakat-vorschau.png")
    voll.save(vorschau)
    print("Pack geschrieben nach %s" % ziel)
    print("Vorschau des ganzen Plakats: %s" % vorschau)


def pruefe_pack(ziel):
    """Prueft das fertige Pack gegen die Regeln, an denen der Client scheitert.

    Die wichtigste ist ascent <= height: wird sie verletzt, lehnt der Client nicht etwa
    den einen Glyphen ab, sondern die komplette Schrift - der Spieler sieht dann ueberall
    nur noch Kaestchen. Das faellt im Spiel sofort auf, aber erst dann. Hier faellt es
    beim Bauen auf.
    """
    from PIL import Image

    fehler = []
    with open(os.path.join(ziel, "pack.mcmeta"), encoding="utf-8") as f:
        meta = json.load(f)["pack"]
    if "pack_format" in meta or "supported_formats" in meta:
        fehler.append("pack.mcmeta enthaelt pack_format - seit 1.21.9 muss es fehlen")
    if meta.get("min_format") != PACK_FORMAT or meta.get("max_format") != PACK_FORMAT:
        fehler.append("pack.mcmeta nennt nicht Format %d" % PACK_FORMAT)

    fontdatei = os.path.join(ziel, "assets", NAMENSRAUM, "font", FONT + ".json")
    with open(fontdatei, encoding="utf-8") as f:
        provider = json.load(f)["providers"]

    belegt = {}
    for nummer, p in enumerate(provider):
        if p["type"] == "space":
            for z in p["advances"]:
                if z in belegt:
                    fehler.append("Codepoint U+%04X doppelt vergeben" % ord(z))
                belegt[z] = nummer
            continue
        if p["ascent"] > p["height"]:
            fehler.append("Provider %d: ascent %d groesser als height %d - das laesst den "
                          "Client die ganze Schrift verwerfen"
                          % (nummer, p["ascent"], p["height"]))
        pfad = os.path.join(ziel, "assets", NAMENSRAUM, "textures",
                            p["file"].split(":", 1)[1])
        if not os.path.exists(pfad):
            fehler.append("Provider %d: Textur fehlt (%s)" % (nummer, pfad))
            continue
        bild = Image.open(pfad)
        spalten = max(len(z) for z in p["chars"])
        zell_b = bild.width // spalten
        zell_h = bild.height // len(p["chars"])
        if zell_b > 256 or zell_h > 256:
            fehler.append("Provider %d: Glyphenzelle %dx%d ueberschreitet 256x256"
                          % (nummer, zell_b, zell_h))
        if bild.width % spalten or bild.height % len(p["chars"]):
            fehler.append("Provider %d: %dx%d laesst sich nicht in %dx%d Zellen teilen"
                          % (nummer, bild.width, bild.height, spalten, len(p["chars"])))
        for zeile in p["chars"]:
            for z in zeile:
                if z == " ":
                    continue
                if z in belegt:
                    fehler.append("Codepoint U+%04X doppelt vergeben (Provider %d und %d)"
                                  % (ord(z), belegt[z], nummer))
                belegt[z] = nummer

    if fehler:
        raise SystemExit("Pack ist fehlerhaft:\n  " + "\n  ".join(fehler))
    print("Pack geprueft: %d Provider, %d Codepoints, alle Regeln eingehalten"
          % (len(provider), len(belegt)))


if __name__ == "__main__":
    ziel = sys.argv[1] if len(sys.argv) > 1 else "src/main/pack"
    schreibe(ziel)
    pruefe_pack(ziel)
