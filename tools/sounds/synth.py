"""Ein kleiner Synthesebaukasten fuer die Western-Klaenge.

Auf dem Rechner ist kein scipy vorhanden, deshalb kommt alles mit numpy aus. Das ist keine
Einschraenkung: Aufprallgeraeusche entstehen ohnehin am ueberzeugendsten aus gedaempften
Resonanzen, und die lassen sich als Faltung mit einer abklingenden Sinusschwingung
schreiben - ein Zweizeiler, der ohne Filterbibliothek auskommt.

Der Unterschied zwischen einer Glocke und einer Muenze steckt allein in den Verhaeltnissen
der Resonanzen: ganzzahlige Vielfache klingen glockig, krumme metallisch-scheppernd.
"""

import numpy as np

RATE = 44100


def stille(dauer):
    return np.zeros(int(dauer * RATE))


def zeit(dauer):
    return np.arange(int(dauer * RATE)) / RATE


def rauschen(rng, dauer):
    return rng.standard_normal(int(dauer * RATE))


def mode(frequenz, abklingzeit, dauer=None):
    """Eine gedaempfte Schwingung - der Grundbaustein jedes Anschlags."""
    laenge = dauer if dauer is not None else abklingzeit * 6.0
    t = zeit(laenge)
    return np.exp(-t / abklingzeit) * np.sin(2 * np.pi * frequenz * t)


def anschlag(rng, moden, anregung=0.002, dauer=None):
    """Ein Rauschimpuls, der mehrere Resonanzen anregt.

    moden ist eine Liste aus (Frequenz, Abklingzeit, Lautstaerke).
    """
    impuls = rauschen(rng, anregung)
    laenge = dauer or max(a for _, a, _ in moden) * 6.0
    summe = stille(laenge)
    for frequenz, abkling, pegel in moden:
        kern = mode(frequenz, abkling, laenge)
        teil = np.convolve(impuls, kern)[:len(summe)]
        summe += pegel * teil
    return summe


def tiefpass(signal, grenze):
    """Einpoliger Tiefpass als Faltung - die Impulsantwort ist a*(1-a)^n."""
    a = 1.0 - np.exp(-2 * np.pi * grenze / RATE)
    laenge = int(np.ceil(np.log(1e-3) / np.log(max(1e-9, 1.0 - a))))
    kern = a * (1.0 - a) ** np.arange(max(1, laenge))
    return np.convolve(signal, kern)[:len(signal)]


def hochpass(signal, grenze):
    return signal - tiefpass(signal, grenze)


def gleitender_tiefpass(signal, von, bis):
    """Tiefpass, dessen Grenzfrequenz ueber die Laufzeit wandert."""
    n = len(signal)
    grenzen = np.linspace(von, bis, n)
    a = 1.0 - np.exp(-2 * np.pi * grenzen / RATE)
    ausgang = np.zeros(n)
    letzter = 0.0
    for i in range(n):
        letzter += a[i] * (signal[i] - letzter)
        ausgang[i] = letzter
    return ausgang


def hallfahne(rng, dauer, abkling, grenze=4200.0):
    """Eine kuenstliche Raumantwort: abklingendes Rauschen mit Hoehendaempfung."""
    t = zeit(dauer)
    ir = rng.standard_normal(len(t)) * np.exp(-t / abkling)
    return tiefpass(ir, grenze)


def hall(signal, ir, anteil, erstreflexionen=()):
    """Mischt Raumklang bei. Die Erstreflexionen geben dem Raum seine Groesse."""
    nass = np.convolve(signal, ir)[:len(signal) + len(ir)]
    trocken = np.pad(signal, (0, len(nass) - len(signal)))
    for versatz, pegel in erstreflexionen:
        start = int(versatz * RATE)
        laenge = min(len(signal), len(trocken) - start)
        if start < len(trocken) and laenge > 0:
            trocken[start:start + laenge] += pegel * signal[:laenge]
    nass /= max(1e-9, np.max(np.abs(nass)))
    return trocken * (1.0 - anteil) + nass * anteil


def huelle(signal, attack=0.005, release=0.02):
    """Weiche Kanten, damit es beim Start und Ende nicht knackt."""
    n = len(signal)
    a = min(int(attack * RATE), n // 2)
    r = min(int(release * RATE), n // 2)
    maske = np.ones(n)
    if a > 0:
        maske[:a] = np.linspace(0.0, 1.0, a) ** 2
    if r > 0:
        maske[-r:] = np.linspace(1.0, 0.0, r) ** 2
    return signal * maske


def normiere(signal, spitze_db):
    hoch = np.max(np.abs(signal))
    if hoch < 1e-9:
        return signal
    return signal / hoch * (10.0 ** (spitze_db / 20.0))


def blechton(dauer, grundton, obertoene=24, anschwellen=0.45, halten=0.6):
    """Ein Blasinstrument: die Obertoene setzen nacheinander ein, nicht gleichzeitig.

    Genau darin liegt der Blech-Charakter - je lauter geblasen wird, desto heller wird der
    Klang. Setzen alle Obertoene sofort ein, klingt es wie eine Orgel.
    """
    t = zeit(dauer)
    huellkurve = np.where(
        t < halten + anschwellen,
        1.0 - np.exp(-t / anschwellen),
        (1.0 - np.exp(-(halten + anschwellen) / anschwellen))
        * np.exp(-(t - halten - anschwellen) / 0.5))

    # Das Horn greift zu Beginn leicht unter den Ton und zieht ihn hoch.
    frequenz = grundton * (1.0 - 0.03 * np.exp(-t / 0.12))
    vibrato = 1.0 + 0.004 * np.sin(2 * np.pi * 4.5 * t) * np.clip((t - 0.6) / 0.4, 0, 1)
    phase = 2 * np.pi * np.cumsum(frequenz * vibrato) / RATE

    klang = np.zeros(len(t))
    for k in range(1, obertoene + 1):
        schwelle = (k - 1) * 0.06
        oeffnung = np.clip((huellkurve - schwelle) / 0.25, 0.0, 1.0)
        klang += np.sin(k * phase) * oeffnung / k
    return klang * huellkurve


def zunge(dauer, grundton, obertoene=15, verstimmung=1.0046):
    """Eine Mundharmonika-Zunge: nur ungerade Obertoene, dazu eine zweite, leicht
    verstimmte Zunge. Deren Schwebung ist das, was den Klang ausmacht."""
    t = zeit(dauer)
    klang = np.zeros(len(t))
    for stimme, pegel in ((1.0, 1.0), (verstimmung, 0.7)):
        for k in range(1, obertoene + 1, 2):
            klang += pegel * np.sin(2 * np.pi * grundton * stimme * k * t) / (k ** 1.2)
    return klang


def lege(ziel, signal, ab):
    """Mischt ein Signal an einer Zeitstelle in eine laengere Spur."""
    start = int(ab * RATE)
    ende = min(len(ziel), start + len(signal))
    if start < len(ziel):
        ziel[start:ende] += signal[:ende - start]
    return ziel
