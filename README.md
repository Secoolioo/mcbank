# BankRanking

Paper-Plugin für Minecraft **26.2** (Java 25): Bank-NPCs, bei denen Spieler Items abgeben und dafür
Punkte bekommen, plus eine Rangliste am rechten Bildschirmrand.

## Was es kann

- **Bank-NPCs**: beliebig viele Figuren mit echtem Spieler-Skin (Mannequin), jede mit eigener Nummer.
  Rechtsklick öffnet ein Fenster mit 27 Plätzen.
- **Items abgeben**: alles hineinlegen, jederzeit wieder herausnehmen. Erst der Smaragd-Knopf unten
  rechts verrechnet den Inhalt. Fenster schließen ohne Klick = alles zurück.
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

Das Recht `bankranking.use` (standardmäßig für alle) erlaubt das Benutzen der NPCs.

## Punkte-Formel

```
Punkte = Basiswert × Anzahl × Kategorie-Faktor + Bonus × Summe der Verzauberungsstufen
```

- **Basiswert**: die eingebaute Minecraft-Seltenheit (common 1, uncommon 3, rare 7, epic 15).
  Wichtig: Minecraft stuft fast alles als *common* ein, auch Diamanten und Netherite. Wer das ändern
  will, trägt feste Werte unter `punkte.material-basiswerte` ein.
- **Kategorie-Faktor**: Waffen ×2, Werkzeuge ×1,5, Rüstung ×1,5, Ressourcen ×1, Nahrung ×0,5,
  Sonstiges ×1.
- **Bonus**: 2 Punkte je Verzauberungsstufe, einmal pro Stapel.

Beispiel: 64 gebratene Rindersteaks (32,0) plus ein Diamantschwert mit Schärfe V (2,0 + 10,0) ergeben
44,0 Punkte.

Alle Zahlen stehen in `plugins/BankRanking/config.yml` und lassen sich mit `/bankranking reload`
ohne Neustart ändern.

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

Ergebnis: `build/libs/BankRanking-1.0.0.jar`.

## Herunterladen

Das fertige Plugin liegt fertig gebaut unter [`dist/BankRanking-1.0.0.jar`](dist/BankRanking-1.0.0.jar)
und zusätzlich als Release-Anhang.

Direkt auf dem Server, im Ordner `plugins/`:

```bash
wget https://github.com/Secoolioo/mcbank/releases/latest/download/BankRanking-1.0.0.jar
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
