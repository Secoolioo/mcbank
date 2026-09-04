package de.secoolio.bankranking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Alle Spielertexte an einer Stelle, im MiniMessage-Format. */
public final class Messages {

    public static final String PREFIX = "<dark_green>[Bank]</dark_green> ";

    public static final String NUR_SPIELER = "<red>Diesen Befehl kann nur ein Spieler ausführen.";
    public static final String KEINE_RECHTE = "<red>Du darfst die Bank nicht benutzen.";

    public static final String GUI_TITEL =
            "<dark_green>\u2726</dark_green> <bold>Bank</bold> <dark_gray>|</dark_gray> <gray>Items abgeben";
    public static final String BUTTON_NAME = "<green><bold>\u2714 Abgeben</bold></green>";
    public static final String[] BUTTON_LORE = {
            "<gray>Wandelt alle Items im Fenster",
            "<gray>in Punkte um.",
            "",
            "<red>\u26a0 Die Items sind danach weg.",
            "<dark_gray>Fenster schließen = alles zurück"
    };
    /** <punkte> = aktueller Wert der eingelegten Items, <anzahl> = Anzahl der Items. */
    public static final String WERT_ANZEIGE_NAME = "<gold><bold>Aktueller Wert</bold></gold>";
    public static final String[] WERT_ANZEIGE_LORE = {
            "<gray>Eingelegt: <white><anzahl></white> Items",
            "<gray>Wert: <gold><punkte></gold> Punkte"
    };
    public static final String WERT_ANZEIGE_LEER_NAME = "<gray><bold>Noch nichts eingelegt</bold></gray>";
    public static final String[] WERT_ANZEIGE_LEER_LORE = {
            "<gray>Lege Items in die freien Plätze.",
            "<gray>Hier siehst du dann ihren Wert."
    };
    /** <punkte> = Kontostand, <platz> = Platz in der Rangliste. */
    public static final String KONTO_ANZEIGE_NAME = "<aqua><bold>Dein Konto</bold></aqua>";
    public static final String[] KONTO_ANZEIGE_LORE = {
            "<gray>Punkte: <gold><punkte></gold>",
            "<gray>Platz: <white><platz></white>"
    };
    public static final String KONTO_ANZEIGE_OHNE_PLATZ = "<gray>noch keiner</gray>";

    public static final String BANK_LEER = "<yellow>Die Bank ist leer - lege zuerst Items hinein.";
    public static final String BANK_WERTLOS = "<yellow>Diese Items sind 0 Punkte wert - nimm sie wieder heraus.";
    public static final String BANK_FEHLER =
            "<red>Die Punkte konnten nicht gespeichert werden. Deine Items bleiben in der Bank!";
    public static final String BANK_BESTAETIGT =
            "<green>Du hast <white><anzahl></white> Items abgegeben und <gold><punkte></gold> Punkte erhalten."
                    + " Kontostand: <gold><gesamt></gold>";
    public static final String BANK_BEHAELTER_ZURUECK = "<gray>Leere Behälter hast du zurückbekommen.";
    public static final String BANK_ZURUECK = "<gray>Nichts abgegeben - du hast deine Items zurückbekommen.";
    public static final String BANK_ZURUECK_BODEN =
            "<yellow>Dein Inventar war voll - einige Items liegen vor dir auf dem Boden.";

    public static final String KONTOSTAND = "<green>Dein Kontostand: <gold><punkte></gold> Punkte<platz>.";
    public static final String KONTOSTAND_PLATZ = " <gray>(Platz <white><platz></white>)</gray>";

    public static final String REICHSTE_KOPF = "<gold><bold>Die reichsten Spieler</bold></gold>";
    public static final String REICHSTE_ZEILE =
            "<yellow><platz>.</yellow> <white><name></white> <dark_gray>-</dark_gray> <gold><punkte></gold> Punkte";
    public static final String REICHSTE_LEER = "<gray>Noch niemand hat Punkte gesammelt.";

    public static final String[] ADMIN_HILFE = {
            "<gold>/spawnrank [Spieler]</gold> <gray>- Bank-NPC an deiner Position setzen (Skin: du oder der Spieler)",
            "<gold>/bankranking list</gold> <gray>- alle Bank-NPCs auflisten",
            "<gold>/bankranking removenpc <nr></gold> <gray>- Bank-NPC entfernen",
            "<gold>/bankranking skin <nr> <Spieler></gold> <gray>- Skin eines NPCs ändern",
            "<gold>/bankranking reload</gold> <gray>- config.yml und players.yml neu laden",
            "<gold>/bankranking wert</gold> <gray>- Punktwert des Items in deiner Hand anzeigen",
            "<gold>/bankranking sidebar</gold> <gray>- Rangliste prüfen und neu aufbauen"
    };

    public static final String NPC_GESETZT =
            "<green>Bank-NPC <white>#<nr></white> gesetzt bei <white><pos></white> (Skin: <white><skin></white>).";
    public static final String NPC_ENTFERNT = "<green>Bank-NPC <white>#<nr></white> entfernt.";
    public static final String NPC_UNBEKANNT = "<yellow>Es gibt keinen Bank-NPC mit der Nummer <white><nr></white>.";
    public static final String NPC_WELT_FEHLT =
            "<yellow>Bank-NPC <white>#<nr></white> steht in einer Welt, die gerade nicht geladen ist -"
                    + " er bleibt vorerst bestehen.";
    public static final String NPC_SKIN_GEAENDERT =
            "<green>Bank-NPC <white>#<nr></white> trägt jetzt den Skin von <white><skin></white>.";
    public static final String NPC_SKIN_UNGUELTIG =
            "<red>'<skin>' ist kein gültiger Spielername (1-16 Zeichen, keine Leerzeichen).";
    public static final String NPC_SPAWN_FEHLER =
            "<red>Der NPC konnte nicht gesetzt werden - ein anderes Plugin hat das Spawnen verhindert.";
    public static final String NPC_GESPERRT =
            "<red>npcs.yml ist beschädigt - es können gerade keine NPCs gesetzt werden."
                    + " Bitte die Datei im Plugin-Ordner prüfen (Details im Server-Log).";
    public static final String NPC_LISTE_KOPF = "<gold><bold>Bank-NPCs</bold></gold>";
    public static final String NPC_LISTE_LEER = "<gray>Es gibt noch keine Bank-NPCs. Setze einen mit /spawnrank.";
    public static final String NPC_LISTE_ZEILE =
            "<yellow>#<nr></yellow> <white><pos></white> <dark_gray>|</dark_gray> Skin: <white><skin></white>"
                    + " <dark_gray>|</dark_gray> <status>";
    public static final String NPC_STATUS_DA = "<green>geladen</green>";
    public static final String NPC_STATUS_FEHLT = "<yellow>Chunk nicht geladen</yellow>";

    public static final String SIDEBAR_KOPF = "<gold><bold>Rangliste-Status</bold></gold>";
    public static final String SIDEBAR_STATUS_CONFIG = "<gray>In der config.yml eingeschaltet: <wert>";
    public static final String SIDEBAR_STATUS_BOARD = "<gray>Für dich angelegt: <wert>";
    public static final String SIDEBAR_STATUS_SICHTBAR = "<gray>Wird dir gerade angezeigt: <wert>";
    public static final String SIDEBAR_JA = "<green>ja</green>";
    public static final String SIDEBAR_NEIN = "<red>nein</red>";
    public static final String SIDEBAR_NEU = "<green>Rangliste neu aufgebaut - sie sollte jetzt rechts stehen.";
    public static final String SIDEBAR_AUS =
            "<yellow>Die Rangliste ist in der config.yml abgeschaltet (sidebar.aktiv: false).";

    public static final String RELOAD_OK =
            "<green>Konfiguration neu geladen. Eventuelle Warnungen stehen im Server-Log.";

    public static final String WERT_LEER = "<yellow>Nimm ein Item in die Haupthand.";
    public static final String WERT_KOPF = "<gold>Wert von <white><material></white> <gray>x<anzahl></gray>:";
    public static final String WERT_GRUNDWERT = "<gray>Grundwert: <white><basis></white> je Stück";
    public static final String WERT_SELTENHEIT =
            "<gray>Seltenheit: <white><seltenheit></white> (Faktor <white><faktor></white>)";
    public static final String WERT_KATEGORIE =
            "<gray>Kategorie: <white><kategorie></white> (Faktor <white><faktor></white>)";
    public static final String WERT_VERZAUBERUNG =
            "<gray>Verzauberungsstufen: <white><stufen></white> (Bonus <white><bonus></white>)";
    public static final String WERT_BEHAELTER =
            "<gray>Behälter mit <white><stapel></white> Stapeln Inhalt - der Behälter selbst kommt zurück.";
    public static final String WERT_GESAMT =
            "<gray>Gesamtwert des Inhalts: <gold><bold><punkte></bold></gold> Punkte";
    public static final String WERT_SUMME =
            "<gray>= <white><basis></white> * <white><anzahl></white> * <white><faktor></white>"
                    + " * <white><seltenheit></white> + <white><bonus></white>"
                    + " = <gold><bold><punkte></bold></gold> Punkte";

    public static final String SIDEBAR_ZEILE_TOP =
            "<yellow><platz>.</yellow> <white><name></white> <gold><punkte></gold>";
    public static final String SIDEBAR_ZEILE_ICH = "<aqua>Du:</aqua> Platz <white><platz></white> <gold><punkte></gold>";
    public static final String SIDEBAR_ZEILE_ICH_LEER = "<aqua>Du:</aqua> <gray>noch keine Punkte";
    public static final String SIDEBAR_ZEILE_TODE = "<red>Tode:</red> <white><tode></white>";
    public static final String SIDEBAR_ZEILE_TAG = "<gray>Tag <white><tag></white>";

    private Messages() {
    }

    /** MiniMessage-Text in eine Komponente wandeln (ohne den kursiven Item-Standard). */
    public static Component mm(String text, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(text, resolvers).decoration(TextDecoration.ITALIC, false);
    }
}
