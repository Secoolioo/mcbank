# BankRanking

Paper-Plugin für Minecraft **26.2** (Java 25): Bank-NPCs, bei denen Spieler Items abgeben und dafür
Punkte bekommen, plus eine Rangliste am rechten Bildschirmrand.

## Was es kann

- **Bank-NPCs**: beliebig viele Figuren mit echtem Spieler-Skin (Mannequin), jede mit eigener Nummer.
  Rechtsklick öffnet ein Fenster mit Rahmen, 28 Ablageplätzen, mitlaufender Wertanzeige und
  Haken-Knopf zum Abgeben.
- **Items abgeben**: alles hineinlegen, jederzeit wieder herausnehmen. Erst der Haken-Knopf unten
  in der Mitte verrechnet den Inhalt. Fenster schließen ohne Klick = alles zurück.
- **Shulker-Boxen und Bündel** werden geleert: der Inhalt zählt, die leere Box kommt zurück.
- **Rangliste** rechts im Bild: Top 3, eigener Platz, eigene Tode, aktueller Minecraft-Tag.
- **Punkte** nach einer frei konfigurierbaren Formel, siehe unten.

## Befehle

| Befehl | Recht | Wirkung |
| --- | --- | --- |
| `/spawnrank [Spieler]` | `bankranking.admin` | Setzt einen Bank-NPC an deine Position. Ohne Angabe bekommt er deinen Skin, sonst den des genannten Spielers. |
| `/kontostand` | `bankranking.kontostand` | Eigener Punktestand und Platz. |
| `/reichste` | `bankranking.reichste` | Die zehn reichsten Spieler. |
| `/bankranking` | `bankranking.admin` | Hilfe. |
| `/bankranking list` | `bankranking.admin` | Alle NPCs mit Nummer, Position, Skin und Status. |
| `/bankranking removenpc <nr>` | `bankranking.admin` | Entfernt einen NPC. |
| `/bankranking skin <nr> <Spieler>` | `bankranking.admin` | Ändert den Skin eines NPCs. |
| `/bankranking reload` | `bankranking.admin` | Lädt `config.yml` und `players.yml` neu. |
| `/bankranking wert` | `bankranking.admin` | Zeigt die komplette Rechnung für das Item in der Hand. |
| `/bankranking sidebar` | `bankranking.admin` | Prüft die Rangliste und baut sie neu auf. |

Das Recht `bankranking.use` (standardmäßig für alle) erlaubt das Benutzen der NPCs.

## Punkte-Formel

```
Punkte = Grundwert × Anzahl × Kategorie-Faktor × Seltenheits-Faktor
         + 2 je Verzauberungsstufe
```

- **Grundwert**: kommt aus einer eingebauten Tabelle mit über 200 Materialien. Erde kostet 0,05,
  ein Diamant 20, ein Smaragd 25, ein Netherite-Barren 200. Blöcke zählen wie neun Rohstoffe,
  Werkzeuge und Rüstung nach ihrer Stufe. Einzelne Werte lassen sich in der Konfiguration
  überschreiben, alles Unbekannte bekommt den Standardwert 0,5.
- **Kategorie-Faktor**: Waffen ×2, Werkzeuge ×1,5, Rüstung ×1,5, Ressourcen ×1, Nahrung ×0,5,
  Sonstiges ×1.
- **Seltenheits-Faktor**: die eingebaute Minecraft-Seltenheit als Multiplikator, common ×1 bis
  epic ×3.

Beispiele mit den Standardwerten: 64 Bruchstein ergeben 3,2 Punkte, ein Diamantschwert mit
Schärfe V und Haltbarkeit III 156 Punkte, eine Elytra 900 Punkte.

Alle Zahlen stehen in `plugins/BankRanking/config.yml` und lassen sich mit `/bankranking reload`
ohne Neustart ändern. `/bankranking wert` zeigt die komplette Rechnung für das Item in der Hand.

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

Ergebnis: `build/libs/BankRanking-1.1.0.jar`.

## Herunterladen

Das fertige Plugin liegt als Anhang am [neuesten Release](https://github.com/Secoolioo/mcbank/releases/latest)
und im Ordner `dist/`.

Direkt auf dem Server, im Ordner `plugins/`:

```bash
wget https://github.com/Secoolioo/mcbank/releases/latest/download/BankRanking-1.1.0.jar
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
