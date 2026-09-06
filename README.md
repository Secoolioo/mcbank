# BankRanking

Paper-Plugin für Minecraft **26.2** (Java 25): Bank-NPCs, bei denen Spieler Items abgeben und dafür
Punkte bekommen, eine Rangliste am rechten Bildschirmrand — und Kopfgelder samt
bildschirmfüllendem Steckbrief.

## Was es kann

- **Bank-NPCs**: beliebig viele Figuren mit echtem Spieler-Skin (Mannequin), jede mit eigener Nummer.
  Ein Rechtsklick öffnet das Hauptmenü.
- **Hauptmenü** mit drei Wegen: Abgeben, Rangliste und Mein Konto. Der Rahmen jedes Fensters trägt
  die Farbe deines Rangs.
- **Abgeben**: Items in 28 Plätze legen, jederzeit wieder herausnehmen. Erst der Haken-Knopf unten
  in der Mitte verrechnet den Inhalt. Fenster schließen ohne Klick = alles zurück. Eine Anzeige
  rechnet laufend mit, was die Einzahlung bringen würde.
- **Shulker-Boxen und Bündel** werden geleert: der Inhalt zählt, die leere Hülle kommt zurück.
- **Rangliste als Fenster**: Treppchen für die ersten drei, Plätze vier bis zehn darunter, ganz
  unten dein eigener Stand mit dem Abstand nach oben und unten.
- **Kontoseite**: Rang und Fortschritt, Anzahl und Umfang deiner Einzahlungen, die größte
  Einzahlung, dein Lieblingsmaterial und die letzten fünf Abgaben.
- **Ränge** von Bronze bis Netherite. Ein Aufstieg wird allen Spielern im Chat gemeldet.
- **Effekte**: Partikel und ein Text in der Bildmitte beim Abgeben; ein Balken am oberen Bildrand
  zeigt den Weg zum nächsten Rang, solange ein Bank-Fenster offen ist.
- **Rangliste am Bildschirmrand**: die ersten drei in Gold, Silber und Bronze, jeder Name in der
  Farbe seines Rangs, dazu dein Platz, der Abstand zum Vordermann, dein Fortschritt, deine Tode
  und der Minecraft-Tag.

### Kopfgeld

- **`/kopfgeld`** öffnet die Auswahl aller Spieler. Wer angeklickt wird, bekommt ein Einsatzfenster;
  wer den Gejagten tötet, bekommt den Einsatz.
- **Einsatz** nur in Smaragden, Diamanten und Netherite, jeweils auch als Block. Nur unverzauberte
  und unbenannte Ware — sonst wäre "Material und Anzahl" keine verlustfreie Darstellung.
- **Mehrere Einsätze auf dieselbe Person sammeln sich in einem Topf**, und ein Kopfgeld verfällt nie.
- **Missbrauchsschutz**: kein Kopfgeld auf sich selbst; wer selbst eingezahlt hat, kassiert nicht;
  nach einer Auszahlung ist das Opfer eine Weile gesperrt und derselbe Killer noch länger.
- **Der Name des Gejagten wird in der TAB-Liste rot**, und er selbst bekommt einen Balken am oberen
  Bildrand.
- **Beim Aussetzen sehen alle einmalig ein WANTED-Plakat** — bildschirmfüllend, mit dem echten Skin-
  Gesicht des Gejagten. Die Belohnung steht dort als Sinnbild und Stückzahl: ein Netherit-Würfel mit
  einer 3 und ein Diamant mit einer 64 brauchen keine Erklärung. Dazu ein Chat-Plakat, das stehen bleibt.
- **Die Belohnung ist nie eine abstrakte Zahl.** Überall steht, was der Killer tatsächlich bekommt —
  „2 Netheritblöcke und 12 Diamanten". Eine Punktzahl wäre doppelt irreführend gewesen: sie sähe aus
  wie ein Bankguthaben, entspräche aber nicht dem, was die Bank für dieselben Gegenstände gutschreibt.
- **Volles Inventar?** Dann erscheint vor dem Killer eine schwebende Kiste, die nur er öffnen kann und
  die verschwindet, sobald sie leer ist. `/kopfgeld beute` holt sie von überall.

### Das Resourcepack

Das Plugin bringt ein eigenes Resourcepack mit und liefert es über einen winzigen eingebauten
Webserver aus — kein Internet nötig, der Hash kann nie veralten. Darin stecken die Grafik des
Plakats, eine Pixelschrift, mit der der Server das Spielergesicht Punkt für Punkt malt, und elf
eigene Western-Klänge.

**Niemand wird ausgesperrt.** Wer das Pack ablehnt oder dessen Download scheitert, bekommt
automatisch die Sparfassung: Titel und Untertitel in gewöhnlicher Schrift, Vanilla-Klänge. Das
Gesicht sieht er trotzdem — im Chat-Plakat steckt es als Vanilla-Objekt und braucht kein Pack.

**Und wenn der Port zu ist?** Dann reicht das Plugin das Pack einmalig über die öffentliche Adresse
des Releases nach. Das ist der Fall, den sonst niemand bemerkt: der Selbsttest des Plugins geht nur
an sich selbst und sagt nichts darüber, ob ein Mitspieler durch die Firewall kommt.
`/bankranking pack` zeigt, wer es geladen hat.

Eigene Klänge lassen sich ohne Codeänderung einsetzen: eine `.ogg`-Datei nach
`plugins/BankRanking/pack-eigene/sounds/` legen, benannt wie das Ereignis (`plakat.ogg`,
`schuss.ogg`, …). Beim nächsten Start wird sie ins Pack übernommen.

## Punkte-Formel

```
Punkte = Grundwert × Anzahl × Kategorie-Faktor × Seltenheits-Faktor
         + 2 je Verzauberungsstufe
```

- **Grundwert**: kommt aus einer eingebauten Tabelle mit über 200 Materialien. Erde kostet 0,05,
  ein Diamant 20, ein Smaragd 25, ein Netherite-Barren 200. Blöcke zählen wie neun Rohstoffe,
  Werkzeuge und Rüstung nach ihrer Stufe. Verarbeitete Formen zählen wie ihr Rohstoff, ein
  Steinziegel also wie ein Stein und eine Glasscheibe wie der Sand darin. Einzelne Werte lassen
  sich in der Konfiguration überschreiben (das gilt dann auch für alle abgeleiteten Formen),
  alles Unbekannte bekommt den Standardwert 0,05.
- **Kategorie-Faktor**: Waffen ×2, Werkzeuge ×1,5, Rüstung ×1,5, Ressourcen ×1, Nahrung ×0,5,
  Sonstiges ×1.
- **Seltenheits-Faktor**: die eingebaute Minecraft-Seltenheit als Multiplikator, common ×1 bis
  epic ×3.

Beispiele mit den Standardwerten: 64 Bruchstein ergeben 3,2 Punkte, ein Diamantschwert mit
Schärfe V und Haltbarkeit III 156 Punkte, eine Elytra 900 Punkte.

## Zwei Bremsen gegen Farmen

Damit niemand uneinholbar davonzieht und Farmen nicht alles entscheiden, wird der Rohwert zweimal
gedämpft:

**Wohlstands-Bremse.** Je mehr Punkte jemand hat, desto weniger zählt jedes weitere Item. Mit den
Standardwerten zählt ein Item bei 5.000 Punkten noch 66 Prozent, bei 50.000 noch 24 Prozent. Die
Kurve ist weich, es gibt also keine Sprünge. Sichtbar wird sie als Rang: Bronze, Silber, Gold,
Platin, Diamant, Netherite.

**Marktsättigung.** Wer denselben Rohstoff massenhaft abliefert, drückt dessen Preis, so wie ein
Markt, den man mit Ware überschwemmt. Nach etwa 833 Eisenbarren zählt Eisen nur noch die Hälfte.
Der Zähler halbiert sich täglich, eine Pause stellt den Preis also wieder her. Seltene Einzelfunde
bleiben davon unberührt, weil der Zähler pro Rohstoff geführt wird. Barren, Blöcke und Nuggets
desselben Metalls zählen auf denselben Zähler, Umkraften hilft also nicht.

Beide Bremsen buchen die Fläche unter ihrer Kurve statt des Preises am Rand. Dadurch ist es
gleichgültig, ob jemand alles auf einmal oder in vielen kleinen Portionen abgibt und in welcher
Reihenfolge die Stapel im Fenster liegen.

Beide Bremsen lassen sich in der Konfiguration einstellen oder abschalten.

Alle Zahlen stehen in `plugins/BankRanking/config.yml` und lassen sich mit `/bankranking reload`
ohne Neustart ändern. `/bankranking wert` zeigt die komplette Rechnung für das Item in der Hand,
einschließlich beider Bremsen.

## Dateien auf dem Server

```
plugins/BankRanking/config.yml        alle Zahlen, NPC-Name, Sidebar-Titel, Kopfgeld, Resourcepack
plugins/BankRanking/players.yml       Spieler-UUID -> Name und Punkte
plugins/BankRanking/npcs.yml          Positionen und Skins der NPCs
plugins/BankRanking/kopfgelder.yml    laufende Kopfgelder und noch nicht abgeholte Beute
plugins/BankRanking/pack/             das ausgelieferte Resourcepack
plugins/BankRanking/pack-eigene/      eigene Klänge, die das mitgelieferte Pack überschreiben
```

`kopfgelder.yml` enthält echte Gegenstände von Spielern. Wird sie unlesbar, rührt das Plugin sie
**nicht** an und sperrt jeden Schreibzugriff, bis jemand sie repariert hat — anders als
`players.yml`, die im selben Fall zur Seite gelegt wird.

## Bauen

Es genügt eine Java-Laufzeit; Gradle lädt sich das nötige JDK 25 selbst nach `~/.gradle/jdks`.

```bash
./gradlew build
```

Ergebnis: `build/libs/BankRanking-3.1.0.jar`.

Die losen Pack-Dateien liegen eingecheckt unter `src/main/pack`; Gradle packt sie reproduzierbar
(feste Zeitstempel, feste Reihenfolge), damit der SHA-1 zwischen Builds gleich bleibt. Neu erzeugen
lassen sie sich mit Python 3 samt Pillow und numpy:

```bash
python3 tools/pack/build_pack.py src/main/pack
python3 tools/sounds/build_sounds.py
```

## Herunterladen

Das fertige Plugin liegt als Anhang am [neuesten Release](https://github.com/Secoolioo/mcbank/releases/latest)
und im Ordner `dist/`.

Direkt auf dem Server, im Ordner `plugins/`:

```bash
wget https://github.com/Secoolioo/mcbank/releases/latest/download/BankRanking-3.1.0.jar
```

Oder das ganze Projekt holen:

```bash
git clone https://github.com/Secoolioo/mcbank.git
```

## Installieren

1. JAR nach `plugins/` auf den Paper-26.2-Server kopieren.
2. Server neu starten (nicht `/reload`).
3. Im Spiel `/spawnrank` an der gewünschten Stelle ausführen.
4. Für die Kopfgelder: den Port aus `resourcepack.port` (Standard 8123) in der Firewall freigeben,
   damit die Spieler das Pack laden können. Ohne ihn läuft alles weiter, nur eben in der Sparfassung.

Voraussetzung: Paper **26.2** und Java **25**. Auf älteren Versionen startet das Plugin nicht.
