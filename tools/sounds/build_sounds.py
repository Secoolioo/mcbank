#!/usr/bin/env python3
"""Erzeugt die eigenen Western-Klaenge des Resourcepacks.

Aufruf:  python3 tools/sounds/build_sounds.py [zielordner]

Alle Klaenge entstehen aus Code, es wird kein fremdes Material verwendet. Ausgegeben wird
Ogg/Vorbis mit 44100 Hz - Minecraft dekodiert kein Opus, eine Opus-Datei mit der Endung
.ogg bliebe stumm.

Mono oder Stereo ist keine Geschmacksfrage: Minecraft spielt Monodateien ortsgebunden ab
und laesst sie mit der Entfernung leiser werden, Stereodateien dagegen richtungslos und
konstant laut. Deshalb sind die beiden Klaenge, die alle gleichzeitig und unabhaengig von
ihrer Position hoeren sollen, in Stereo, und alles andere in Mono.
"""

import json
import os
import re
import subprocess
import zlib
import sys
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from synth import (RATE, anschlag, blechton, gleitender_tiefpass, hall, hallfahne, hochpass,
                   huelle, lege, mode, normiere, rauschen, stille, tiefpass, zeit, zunge)

SEED = 20260906


# ------------------------------------------------------------------ Klaenge

def fanfare(rng, raum):
    """Blechhorn-Fanfare. Stereo, 2,6 s.

    Drei Stimmen auf B1, F2 und B2; die dritte spaeter und leicht verstimmt, damit es nach
    zwei Blaesern klingt und nicht nach einem verdoppelten.
    """
    dauer = 2.6
    links = stille(dauer)
    rechts = stille(dauer)

    for grundton, versatz, seite, pegel in (
            (58.27, 0.00, -0.35, 1.00),      # B1
            (87.31, 0.04, 0.35, 0.80),       # F2
            (116.54, 0.09, 0.00, 0.55),      # B2
    ):
        ton = blechton(dauer - versatz, grundton * (1.003 if seite > 0 else 1.0))
        lege(links, ton * pegel * (0.5 - seite / 2 + 0.5), versatz)
        lege(rechts, ton * pegel * (0.5 + seite / 2 + 0.5), versatz)

    # Die Luft im Instrument: breite Resonanz, gleiche Huellkurve wie die Toene.
    t = zeit(dauer)
    atem = tiefpass(hochpass(rauschen(rng, dauer), 1800.0), 5000.0)
    atem *= (1.0 - np.exp(-t / 0.45)) * np.exp(-np.maximum(0.0, t - 1.5) / 0.6) * 0.030
    links += atem
    rechts += np.roll(atem, int(0.011 * RATE))      # Haas-Versatz nur auf dem Rauschen

    links = hall(links, raum, 0.22, [(0.063, 0.50), (0.121, 0.33), (0.197, 0.22)])
    rechts = hall(rechts, raum, 0.22, [(0.071, 0.48), (0.129, 0.31), (0.205, 0.20)])
    n = min(len(links), len(rechts))
    return np.stack([huelle(links[:n], 0.012, 0.04), huelle(rechts[:n], 0.012, 0.04)], axis=1)


def nagel(rng, raum):
    """Ein Nagel wird ins Holz geschlagen. Trocken, kein Nachhall."""
    holz = anschlag(rng, [(190, 0.050, 1.0), (460, 0.035, 0.7), (1150, 0.020, 0.4)],
                    anregung=0.004, dauer=0.30)
    metall = anschlag(rng, [(2900, 0.045, 1.0), (4400, 0.020, 0.6)],
                      anregung=0.002, dauer=0.30) * 0.25
    klang = holz + metall
    return huelle(hall(klang, raum[:int(0.12 * RATE)], 0.10), 0.0005, 0.03)


def gejagt(rng, raum):
    """Klapperschlange und tiefer Drone - hoert nur der Gejagte."""
    dauer = 1.4
    t = zeit(dauer)
    spur = stille(dauer)

    # Die Rasselrate steigt an: die Schlange wird nervoeser.
    rate = np.linspace(40.0, 70.0, len(t))
    naechster = 0.0
    while naechster < dauer:
        klick = anschlag(rng, [(5200, 0.0015, 1.0)], anregung=0.0015, dauer=0.02)
        lege(spur, klick * (0.7 + 0.3 * rng.random()), naechster)
        naechster += 1.0 / rate[min(len(rate) - 1, int(naechster * RATE))]

    drone = (np.sin(2 * np.pi * 49 * t) + 0.5 * np.sin(2 * np.pi * 98 * t))
    drone *= 0.20 * (1.0 + 0.3 * np.sin(2 * np.pi * 0.8 * t))
    return huelle(hall(spur * 0.8 + drone, raum, 0.08), 0.01, 0.08)


def schuss(rng, raum):
    """Ein trockener Revolverschuss aus naechster Naehe."""
    dauer = 0.55
    knall = rauschen(rng, 0.0012)
    knall = np.diff(np.pad(knall, (1, 0)))          # differenzieren: heller Anriss
    spur = np.pad(knall, (0, int(dauer * RATE) - len(knall)))

    spur += anschlag(rng, [(120, 0.060, 1.0), (310, 0.040, 0.7), (780, 0.030, 0.4)],
                     anregung=0.003, dauer=dauer)
    mechanik = anschlag(rng, [(3100, 0.012, 1.0)], anregung=0.001, dauer=dauer) * 0.12
    lege(spur, mechanik, 0.006)

    spur *= np.exp(-zeit(dauer) / 0.10)
    return huelle(hall(spur, raum[:int(0.35 * RATE)], 0.18), 0.0002, 0.05)


def schuss_fern(rng, raum):
    """Derselbe Schuss, nur aus der Ferne: kein Direktschall, nur Rueckwuerfe."""
    trocken = schuss(rng, raum)[:int(0.3 * RATE)]
    dauer = 1.6
    spur = stille(dauer)
    for versatz, pegel, grenze in ((0.00, 0.45, 900.0), (0.38, 0.40, 900.0),
                                   (0.72, 0.24, 600.0), (1.15, 0.13, 400.0)):
        lege(spur, tiefpass(trocken, grenze) * pegel, versatz)
    return huelle(hall(spur, raum, 0.45), 0.015, 0.25)


def mundharmonika(rng, raum):
    """Ein absteigendes Dreiklang-Motiv - der klassische Western-Abgang."""
    dauer = 2.8
    links = stille(dauer)
    rechts = stille(dauer)

    for grundton, ab, laenge, beugen in ((587.33, 0.00, 0.55, False),
                                         (466.16, 0.55, 0.60, False),
                                         (349.23, 1.15, 1.45, True)):
        ton = zunge(laenge, grundton)
        ton += 0.4 * zunge(laenge, grundton * 2 / 3)     # die Quinte darunter
        t = zeit(laenge)
        if beugen:
            # Blues-Bend: der letzte Ton sinkt zum Schluss um 60 Cent.
            biegung = 1.0 - 0.035 * np.clip((t - laenge + 0.5) / 0.5, 0, 1)
            ton *= 1.0
            ton = zunge(laenge, grundton) * biegung + 0.4 * zunge(laenge, grundton * 2 / 3)
        form = np.clip(t / 0.04, 0, 1) * np.clip((laenge - t) / 0.12, 0, 1)
        form *= 1.0 + 0.10 * np.sin(2 * np.pi * 5.0 * t)
        ton *= form * 0.25

        # Vor jedem Ton hoert man die Luft - ohne das klingt es nach Synthesizer.
        atem = tiefpass(hochpass(rauschen(rng, laenge + 0.03), 700.0), 4000.0) * 0.05
        atem *= np.clip(np.linspace(0, 1, len(atem)) * 6, 0, 1)

        lege(links, ton, ab)
        lege(rechts, ton * 0.92, ab + 0.004)
        lege(links, atem, max(0.0, ab - 0.03))
        lege(rechts, atem * 0.9, max(0.0, ab - 0.03))

    veranda = raum[:int(0.9 * RATE)]
    links = hall(links, veranda, 0.25)
    rechts = hall(rechts, veranda, 0.25)
    n = min(len(links), len(rechts))
    return np.stack([huelle(links[:n], 0.01, 0.1), huelle(rechts[:n], 0.01, 0.1)], axis=1)


def muenzen(rng, raum):
    """Muenzen klimpern. Krumme Modenverhaeltnisse - sonst klaenge es nach Glocke."""
    dauer = 0.9
    spur = stille(dauer)
    anzahl = 11
    for i in range(anzahl):
        ab = 0.55 * (i / anzahl) ** 1.4 + rng.random() * 0.02
        f = 1900 + rng.random() * 1500
        klang = anschlag(rng, [(f, 0.09, 1.0), (f * 2.76, 0.05, 0.6), (f * 5.40, 0.03, 0.3)],
                         anregung=0.002, dauer=0.35)
        lege(spur, klang * (0.4 + 0.6 * rng.random()), ab)

    # Die letzte Muenze trudelt auf dem Tisch aus: immer schnellere Amplitudenwellen.
    t = zeit(0.3)
    trudeln = anschlag(rng, [(2400, 0.25, 1.0), (2400 * 2.76, 0.10, 0.4)],
                       anregung=0.002, dauer=0.3)
    trudeln *= 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.linspace(4, 28, len(t))) / RATE)
    lege(spur, trudeln * 0.5, 0.6)
    return huelle(hall(spur, raum[:int(0.3 * RATE)], 0.10), 0.001, 0.05)


def uhr(rng, raum):
    """Ein trockener Ratschen-Tick. Laeuft wiederholt, deshalb bewusst leise."""
    klang = anschlag(rng, [(1350, 0.012, 1.0), (3900, 0.006, 0.5)],
                     anregung=0.0008, dauer=0.12)
    return huelle(klang, 0.0002, 0.02)


def hahn(rng, raum):
    """Der Revolverhahn wird gespannt und rastet ein."""
    dauer = 0.35
    spur = stille(dauer)
    for i in range(8):                          # das Ratschen dazwischen
        lege(spur, anschlag(rng, [(1800, 0.004, 1.0)], anregung=0.0006, dauer=0.03)
             * 0.03 * (i + 1) / 8, 0.008 + i * 0.010)
    lege(spur, anschlag(rng, [(2400, 0.010, 1.0), (900, 0.018, 0.5)],
                        anregung=0.0008, dauer=0.15) * 0.6, 0.0)
    lege(spur, anschlag(rng, [(4800, 0.005, 1.0), (2400, 0.012, 0.7)],
                        anregung=0.0008, dauer=0.15), 0.095)
    return huelle(hall(spur, raum[:int(0.2 * RATE)], 0.05), 0.0005, 0.03)


def sporen(rng, raum):
    """Sporen klirren: ein Stahlraedchen, angestossen von einem Lederschritt."""
    dauer = 0.70
    spur = stille(dauer)
    lege(spur, anschlag(rng, [(90, 0.05, 1.0)], anregung=0.004, dauer=0.2) * 0.35, 0.0)
    for i in range(6):
        f = 2600 + rng.random() * 1600
        abkling = 0.25 if i == 5 else 0.10
        klang = anschlag(rng, [(f, abkling, 1.0), (f * 2.4, 0.06, 0.6),
                               (f * 4.1, 0.04, 0.35), (f * 6.9, 0.03, 0.2)],
                         anregung=0.0015, dauer=0.4)
        lege(spur, klang * (0.35 + 0.5 * rng.random()), 0.02 + i * 0.065)
    return huelle(hall(spur, raum[:int(0.3 * RATE)], 0.12), 0.001, 0.05)


def kiste_weg(rng, raum):
    """Die Belohnungskiste loest sich auf: Deckelschlag und absteigendes Wischen."""
    dauer = 0.80
    spur = stille(dauer)
    lege(spur, anschlag(rng, [(150, 0.10, 1.0), (360, 0.07, 0.7)],
                        anregung=0.004, dauer=0.4), 0.0)
    wisch = gleitender_tiefpass(rauschen(rng, 0.45), 2600.0, 340.0)
    wisch *= np.exp(-zeit(0.45) / 0.18) * 0.5
    lege(spur, wisch, 0.06)
    lege(spur, anschlag(rng, [(120, 0.12, 1.0)], anregung=0.003, dauer=0.3) * 0.25, 0.30)
    return huelle(hall(spur, raum[:int(0.4 * RATE)], 0.20), 0.001, 0.06)


# Name, Funktion, Kanaele, Ziel-Lautheit in LUFS, Hoechstdauer in Sekunden.
#
# Gemessen statt geschaetzt: das Vanilla-Endportal (block/end_portal/endportal.ogg) liegt
# bei -15,8 LUFS. Daran haengt die Ankuendigung, alles andere ordnet sich darunter ein.
# Kurze Klaenge unter 0,4 s lassen sich nach EBU R128 nicht integriert messen; sie werden
# deshalb weiterhin ueber den Spitzenpegel eingestellt (Ziel als None markiert).
ENDPORTAL_LUFS = -15.8

KLAENGE = [
    ("fanfare", fanfare, 2, ENDPORTAL_LUFS, 3.6),
    ("nagel", nagel, 1, None, 0.40),
    ("gejagt", gejagt, 1, -19.0, 2.2),
    ("schuss", schuss, 1, -16.5, 0.90),
    ("schuss_fern", schuss_fern, 1, -24.0, 2.60),
    ("mundharmonika", mundharmonika, 2, -18.0, 3.40),
    ("muenzen", muenzen, 1, -22.0, 1.20),
    ("hahn", hahn, 1, None, 0.45),
    ("sporen", sporen, 1, -24.0, 0.95),
    ("kiste_weg", kiste_weg, 1, -22.0, 1.10),
]

# Spitzenpegel fuer die drei zu kurzen Klaenge.
KURZ_SPITZE = {"nagel": -3.0, "hahn": -7.0}

# Klaenge, die im Pack liegen, aber nicht hier entstehen: vom Betreiber geliefertes Material.
# Sie werden nicht angefasst, muessen aber in sounds.json stehen.
MITGELIEFERT = [
    # Name, Kanaele, ob vorgeladen wird
    ("plakat", 2, True),
]

# Untertitel je Klang. Sie sind die einzige Barrierefreiheits-Massnahme dieser Funktion:
# ein gehoerloser Spieler erfaehrt ueber "Klapperschlange rasselt", dass auf ihn ein
# Kopfgeld ausgesetzt ist. Deshalb hat jeder Klang einen, auch die unwichtigen.
UNTERTITEL = {
    "plakat":        ("Steckbrief wird angeschlagen", "Wanted poster goes up"),
    "fanfare":       ("Horn schallt ueber das Land", "Horn sounds across the land"),
    "nagel":         ("Nagel wird eingeschlagen", "Nail driven in"),
    "gejagt":        ("Klapperschlange rasselt", "Rattlesnake rattles"),
    "schuss":        ("Schuss faellt", "Gunshot"),
    "schuss_fern":   ("Schuss hallt in der Ferne", "Distant gunshot echoes"),
    "mundharmonika": ("Mundharmonika spielt", "Harmonica plays"),
    "muenzen":       ("Muenzen klimpern", "Coins jingle"),
    "hahn":          ("Revolverhahn klickt", "Hammer cocks"),
    "sporen":        ("Sporen klirren", "Spurs jingle"),
    "kiste_weg":     ("Kiste verschwindet", "Crate disappears"),
}

# Reichweite in Bloecken, nur fuer die wirklich ortsgebundenen Klaenge. Alles, was weiter
# tragen soll, waehlt seine Hoerer in Java aus - sonst haetten Spieler mit Pack eine
# andere Reichweite als Spieler ohne, und das faellt erst im Betrieb auf.
REICHWEITE = {"nagel": 16, "gejagt": 8, "schuss": 16, "schuss_fern": 24,
              "muenzen": 16, "kiste_weg": 16}

# Beim ersten Abspielen darf es nicht stocken - diese drei sitzen auf dem Moment.
VORLADEN = {"plakat", "fanfare", "nagel", "schuss"}


def schreibe_wav(pfad, signal, kanaele):
    daten = np.clip(signal, -1.0, 1.0)
    if kanaele == 1 and daten.ndim == 2:
        daten = daten.mean(axis=1)
    if kanaele == 2 and daten.ndim == 1:
        daten = np.stack([daten, daten], axis=1)
    ganz = (daten * 32767.0).astype("<i2")
    with wave.open(pfad, "wb") as f:
        f.setnchannels(kanaele)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(ganz.tobytes())


def nach_ogg(wav, ogg, kanaele):
    """Ogg/Vorbis, ausdruecklich libvorbis - der eingebaute Encoder klingt schlechter,
    und Opus wuerde Minecraft gar nicht dekodieren."""
    qualitaet = "6" if kanaele == 2 else "4"
    subprocess.run(
        ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", wav,
         "-vn", "-map_metadata", "-1", "-map_chapters", "-1",
         "-ac", str(kanaele), "-ar", str(RATE),
         "-c:a", "libvorbis", "-q:a", qualitaet, ogg],
        check=True)


def pruefe(ogg, kanaele):
    aus = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "a:0",
         "-show_entries", "stream=codec_name,sample_rate,channels",
         "-show_entries", "format=duration",
         "-of", "default=nw=1", ogg],
        check=True, capture_output=True, text=True).stdout
    werte = dict(z.split("=", 1) for z in aus.strip().splitlines())
    if werte["codec_name"] != "vorbis":
        raise SystemExit("%s ist kein Vorbis, sondern %s" % (ogg, werte["codec_name"]))
    if int(werte["sample_rate"]) != RATE or int(werte["channels"]) != kanaele:
        raise SystemExit("%s hat %s Hz / %s Kanaele" % (ogg, werte["sample_rate"],
                                                        werte["channels"]))
    return float(werte["duration"])


def lautheit(ogg):
    aus = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-i", ogg, "-af", "ebur128=peak=true",
         "-f", "null", "-"],
        capture_output=True, text=True).stderr.strip().splitlines()
    lufs = spitze = None
    for zeile in aus[-14:]:
        z = zeile.strip()
        if z.startswith("I:") and "LUFS" in z:
            lufs = float(z.split()[1])
        if z.startswith("Peak:"):
            spitze = float(z.split()[1])
    return lufs, spitze


def kuerze(signal, hoechstdauer):
    """Schneidet die Hallfahne ab und blendet weich aus.

    Der Hall verlaengert jedes Signal um die Laenge der Raumantwort. Fuer die Ankuendigung
    zaehlt aber die geplante Dauer, weil Bild und Klang zusammenpassen muessen.
    """
    grenze = int(hoechstdauer * RATE)
    if len(signal) <= grenze:
        return signal
    gekuerzt = signal[:grenze]
    ausblenden = min(int(0.15 * RATE), grenze // 3)
    rampe = np.linspace(1.0, 0.0, ausblenden) ** 1.5
    if gekuerzt.ndim == 2:
        gekuerzt = gekuerzt.copy()
        gekuerzt[-ausblenden:] *= rampe[:, None]
    else:
        gekuerzt = gekuerzt.copy()
        gekuerzt[-ausblenden:] *= rampe
    return gekuerzt


def baue(ziel):
    os.makedirs(ziel, exist_ok=True)
    roh = os.path.join(ziel, "_wav")
    os.makedirs(roh, exist_ok=True)

    # Ein gemeinsamer Hallraum fuer alle Klaenge - das ist es, was sie wie einen Satz
    # klingen laesst statt wie elf Einzelstuecke.
    raum = hallfahne(np.random.default_rng(SEED + 1), 2.0, 0.8)

    print("%-16s %7s %5s %10s %10s %8s" % ("Klang", "Dauer", "Kan.", "LUFS", "Spitze", "Ziel"))
    for name, funktion, kanaele, ziel_lufs, hoechstdauer in KLAENGE:
        # Der Startwert haengt am Namen, nicht an Pythons hash() - das ist je Prozess
        # zufaellig und wuerde bei jedem Lauf andere Dateien und damit einen anderen
        # Pack-Hash erzeugen.
        signal = funktion(np.random.default_rng(SEED + zlib.crc32(name.encode())), raum)
        signal = kuerze(signal, hoechstdauer)
        signal = normiere(signal, -1.0)

        wav = os.path.join(roh, name + ".wav")
        ogg = os.path.join(ziel, name + ".ogg")
        schreibe_wav(wav, signal, kanaele)
        nach_ogg(wav, ogg, kanaele)

        if ziel_lufs is not None:
            # Zweiter Durchgang: die tatsaechliche Lautheit messen und den Unterschied
            # als Verstaerkung anwenden. Der Spitzenpegel bleibt dabei unter -1 dBFS.
            ist, _ = lautheit(ogg)
            faktor = 10.0 ** ((ziel_lufs - ist) / 20.0)
            angepasst = signal * faktor
            spitze = np.max(np.abs(angepasst))
            if spitze > 10.0 ** (-1.0 / 20.0):
                angepasst *= 10.0 ** (-1.0 / 20.0) / spitze
            schreibe_wav(wav, angepasst, kanaele)
            nach_ogg(wav, ogg, kanaele)
        else:
            schreibe_wav(wav, normiere(signal, KURZ_SPITZE[name]), kanaele)
            nach_ogg(wav, ogg, kanaele)

        dauer = pruefe(ogg, kanaele)
        lufs, tp = lautheit(ogg)
        print("%-16s %6.2fs %5d %9.1f  %9.1f %8s"
              % (name, dauer, kanaele, lufs, tp,
                 "%.1f" % ziel_lufs if ziel_lufs is not None else "Spitze"))
    print("\nFertig in %s" % ziel)


def schreibe_sounds_json(namensraum_ordner):
    """sounds.json und die beiden Sprachdateien - erzeugt, nicht von Hand gepflegt.

    Wichtig: der Namensraum im Feld name wird NICHT vom Ordner geerbt. Ohne das
    vorangestellte bankranking: sucht der Client in assets/minecraft und findet nichts.
    """
    eintraege = {}
    alle = [(name, kanaele) for name, _, kanaele, _, _ in KLAENGE]
    alle += [(name, kanaele) for name, kanaele, _ in MITGELIEFERT]
    for name, kanaele in sorted(alle):
        klang = {
            "name": "bankranking:kopfgeld/" + name,
            "volume": 1.0,
            "pitch": 1.0,
            "stream": False,
        }
        if name in REICHWEITE:
            klang["attenuation_distance"] = REICHWEITE[name]
        if name in VORLADEN:
            klang["preload"] = True
        eintraege["kopfgeld." + name] = {
            "subtitle": "subtitles.bankranking.kopfgeld." + name,
            "sounds": [klang],
        }

    with open(os.path.join(namensraum_ordner, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(eintraege, f, ensure_ascii=False, indent=2)
        f.write("\n")

    lang = os.path.join(namensraum_ordner, "lang")
    os.makedirs(lang, exist_ok=True)
    for datei, spalte in (("de_de.json", 0), ("en_us.json", 1)):
        namen = [n for n, _, _, _, _ in KLAENGE] + [n for n, _, _ in MITGELIEFERT]
        texte = {"subtitles.bankranking.kopfgeld." + n: UNTERTITEL[n][spalte]
                 for n in sorted(namen)}
        with open(os.path.join(lang, datei), "w", encoding="utf-8") as f:
            json.dump(texte, f, ensure_ascii=False, indent=2)
            f.write("\n")


def pruefe_sounds_json(namensraum_ordner):
    """Ein einziger ungueltiger Eintrag laesst den Client die ganze Datei verwerfen -
    dann waeren alle Klaenge stumm, obwohl das Pack geladen ist. Deshalb hier."""
    with open(os.path.join(namensraum_ordner, "sounds.json"), encoding="utf-8") as f:
        eintraege = json.load(f)
    erwartet = {"kopfgeld." + n for n, _, _, _, _ in KLAENGE}
    erwartet |= {"kopfgeld." + n for n, _, _ in MITGELIEFERT}
    fehler = []
    if set(eintraege) != erwartet:
        fehler.append("Ereignisse weichen ab: %s" % (set(eintraege) ^ erwartet))
    for schluessel, eintrag in eintraege.items():
        for klang in eintrag["sounds"]:
            name = klang["name"]
            if not name.startswith("bankranking:"):
                fehler.append("%s: name ohne Namensraum" % schluessel)
            if not re.fullmatch(r"[a-z0-9/._:-]+", name):
                fehler.append("%s: unerlaubte Zeichen in name" % schluessel)
            datei = os.path.join(namensraum_ordner, "sounds",
                                 name.split(":", 1)[1] + ".ogg")
            if not os.path.exists(datei):
                fehler.append("%s: Datei fehlt (%s)" % (schluessel, datei))
            if klang.get("volume", 1.0) <= 0 or klang.get("pitch", 1.0) <= 0:
                fehler.append("%s: volume oder pitch nicht positiv" % schluessel)
        for datei in ("de_de.json", "en_us.json"):
            with open(os.path.join(namensraum_ordner, "lang", datei), encoding="utf-8") as f:
                if eintrag["subtitle"] not in json.load(f):
                    fehler.append("%s: Untertitel fehlt in %s" % (schluessel, datei))
    benutzt = set()
    for eintrag in eintraege.values():
        for klang in eintrag["sounds"]:
            benutzt.add(klang["name"].split(":", 1)[1] + ".ogg")
    ordner = os.path.join(namensraum_ordner, "sounds", "kopfgeld")
    if os.path.isdir(ordner):
        for datei in sorted(os.listdir(ordner)):
            if datei.endswith(".ogg") and ("kopfgeld/" + datei) not in benutzt:
                fehler.append("%s wird von keinem Eintrag benutzt - Leiche im Pack" % datei)

    if fehler:
        raise SystemExit("sounds.json ist fehlerhaft:\n  " + "\n  ".join(fehler))
    print("sounds.json geprueft: %d Ereignisse, alle Dateien und Untertitel vorhanden"
          % len(eintraege))


if __name__ == "__main__":
    baue(sys.argv[1] if len(sys.argv) > 1
         else "src/main/pack/assets/bankranking/sounds/kopfgeld")
    namensraum = os.path.abspath(os.path.join(
        sys.argv[1] if len(sys.argv) > 1
        else "src/main/pack/assets/bankranking/sounds/kopfgeld", "..", ".."))
    schreibe_sounds_json(namensraum)
    pruefe_sounds_json(namensraum)
