# BankRanking

Paper-Plugin für Minecraft **26.2** (Java 25): Bank-NPCs, bei denen Spieler Items abgeben und dafür
Punkte bekommen, plus eine Rangliste am rechten Bildschirmrand.

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
plugins/BankRanking/config.yml     alle Zahlen, NPC-Name, Sidebar-Titel
plugins/BankRanking/players.yml    Spieler-UUID -> Name und Punkte
plugins/BankRanking/npcs.yml       Positionen und Skins der NPCs
```

## Bauen

Es genügt eine Java-Laufzeit; Gradle lädt sich das nötige JDK 25 selbst nach `~/.gradle/jdks`.

```bash
./gradlew build
```

Ergebnis: `build/libs/BankRanking-2.0.0.jar`.

## Herunterladen

Das fertige Plugin liegt als Anhang am [neuesten Release](https://github.com/Secoolioo/mcbank/releases/latest)
und im Ordner `dist/`.

Direkt auf dem Server, im Ordner `plugins/`:

```bash
wget https://github.com/Secoolioo/mcbank/releases/latest/download/BankRanking-2.0.0.jar
```

Oder das ganze Projekt holen:

```bash
git clone https://github.com/Secoolioo/mcbank.git
```

## Installieren

1. JAR nach `plugins/` auf den Paper-26.2-Server kopieren.
2. Server neu starten (nicht `/reload`).
3. Im Spiel `/spawnrank` an der gewünschten Stelle ausführen.

Voraussetzung: Paper **26.2** und Java **25**. Auf älteren Versionen startet das Plugin nicht.
