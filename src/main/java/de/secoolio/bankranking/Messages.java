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
    // ----- Hauptmenue -----

    public static final String MENU_TITEL =
            "<dark_green>\u2726</dark_green> <bold>Bank</bold>";
    public static final String MENU_KOPF_NAME = "<aqua><bold><name></bold></aqua>";
    public static final String[] MENU_KOPF_LORE = {
            "<gray>Punkte: <gold><punkte></gold>",
            "<gray>Platz: <white><platz></white>",
            "<gray>Rang: <rang>",
            "",
            "<dark_gray>Items zählen bei dir <faktor>%"
    };
    public static final String MENU_ABGEBEN_NAME = "<green><bold>Abgeben</bold></green>";
    public static final String MENU_ABGEBEN_LORE = "<gray>Items einlegen und in Punkte umwandeln";
    public static final String MENU_RANGLISTE_NAME = "<gold><bold>Rangliste</bold></gold>";
    public static final String MENU_RANGLISTE_LORE = "<gray>Die zehn reichsten Spieler";
    public static final String MENU_KONTO_NAME = "<aqua><bold>Mein Konto</bold></aqua>";
    public static final String MENU_KONTO_LORE = "<gray>Rang, Fortschritt und deine Zahlen";
    /** <rang> im Namen; in der Lore <naechster>, <ab>, <balken>, <prozent>, <rest>. */
    public static final String MENU_RANG_NAME = "<gray>Dein Rang: <rang>";
    public static final String[] MENU_RANG_LORE = {
            "<gray>Nächster Rang: <naechster> <dark_gray>ab <ab> Punkten",
            "<balken> <white><prozent>%</white>",
            "<gray>Noch <white><rest></white> Punkte"
    };
    public static final String MENU_RANG_MAX_LORE = "<dark_purple>Höchster Rang erreicht";

    // ----- Rangliste -----

    public static final String TOP_TITEL =
            "<gold>\u2726</gold> <bold>Rangliste</bold> <dark_gray>|</dark_gray> <gray>Top 10";
    public static final String TOP_KOPF_LORE = "<gray><anzahl> Spieler mit Punkten";
    public static final String[] TOP_PLATZ_NAME = {
            "<gold><bold>1. <name></bold></gold>",
            "<white><bold>2. <name></bold></white>",
            "<color:#cd7f32><bold>3. <name></bold></color:#cd7f32>"
    };
    public static final String TOP_PLATZ_WEITER = "<yellow><platz>.</yellow> <white><name></white>";
    public static final String[] TOP_EINTRAG_LORE = {
            "<gray>Punkte: <gold><punkte></gold>",
            "<gray>Rang: <rang>"
    };
    public static final String TOP_DAS_BIST_DU = "<aqua>Das bist du!";
    public static final String TOP_FREI = "<gray>noch frei";
    public static final String[] TOP_PODEST = {
            "<gold>Platz 1", "<white>Platz 2", "<color:#cd7f32>Platz 3"
    };
    public static final String TOP_ICH_NAME = "<aqua><bold>Du: Platz <platz></bold></aqua>";
    public static final String TOP_ICH_PUNKTE = "<gray>Punkte: <gold><punkte></gold>";
    public static final String TOP_ICH_VOR = "<gray>Vor dir: <white><name></white> <dark_gray>+<abstand>";
    public static final String TOP_ICH_HINTER = "<gray>Hinter dir: <white><name></white> <dark_gray>-<abstand>";
    public static final String TOP_ICH_FUEHRT = "<green>Du führst! <dark_gray>+<abstand> Vorsprung";
    public static final String TOP_ICH_LEER = "<gray>Noch keine Punkte - gib Items ab!";

    // ----- Konto -----

    public static final String KONTO_TITEL =
            "<aqua>\u2726</aqua> <bold>Mein Konto</bold> <dark_gray>|</dark_gray> <gray><name>";
    public static final String KONTO_LEER = "<gray>Noch keine Einzahlung";
    public static final String KONTO_EINZAHLUNGEN_NAME = "<yellow><bold>Einzahlungen</bold></yellow>";
    public static final String[] KONTO_EINZAHLUNGEN_LORE = {
            "<gray>Anzahl: <white><anzahl></white>",
            "<gray>Items gesamt: <white><items></white>",
            "<gray>Im Schnitt: <white><schnitt></white> Punkte"
    };
    public static final String KONTO_GROESSTE_NAME = "<gold><bold>Größte Einzahlung</bold></gold>";
    public static final String[] KONTO_GROESSTE_LORE = {
            "<gold>+<punkte> Punkte",
            "<gray><items> Items, vor allem <white><material></white>",
            "<dark_gray><datum>"
    };
    public static final String KONTO_LIEBLING_NAME = "<green><bold>Lieblingsmaterial</bold></green>";
    public static final String KONTO_LIEBLING_LORE =
            "<white><material></white> <dark_gray>|</dark_gray> <gray><anzahl> Stück abgegeben";
    public static final String KONTO_LETZTE_NAME = "<aqua><bold>Letzte Einzahlungen</bold></aqua>";
    public static final String KONTO_LETZTE_ZEILE =
            "<dark_gray><datum></dark_gray> <gold>+<punkte></gold> <gray>(<items> Items)";
    public static final String KONTO_FAKTOR_NAME = "<light_purple><bold>Wertfaktor</bold></light_purple>";
    public static final String[] KONTO_FAKTOR_LORE = {
            "<gray>Items zählen bei dir <white><faktor>%</white>",
            "<dark_gray>Je reicher, desto weniger je Item.",
            "",
            "<dark_gray>/bankranking wert zeigt die ganze Rechnung."
    };

    public static final String BUTTON_ZURUECK_NAME = "<yellow>« Zurück zum Menü";
    public static final String BUTTON_ZURUECK_LORE =
            "<dark_gray>Eingelegte Items kommen zurück in dein Inventar.";
    public static final String BUTTON_SCHLIESSEN_NAME = "<red><bold>Schließen</bold></red>";
    public static final String BUTTON_SCHLIESSEN_LORE = "<dark_gray>Fenster zumachen";

    /** <roh> = Wert ohne Bremsen, <punkte> = tatsaechlicher Wert, <prozent> = Verhaeltnis. */
    public static final String WERT_ANZEIGE_NAME = "<gold><bold>Aktueller Wert</bold></gold>";
    public static final String[] WERT_ANZEIGE_LORE = {
            "<gray>Eingelegt: <white><anzahl></white> Items",
            "<gray>Grundwert: <dark_gray><roh></dark_gray>",
            "<gray>Du bekommst: <gold><punkte></gold> Punkte <dark_gray>(<prozent>%)</dark_gray>"
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
            "<gray>Platz: <white><platz></white>",
            "<gray>Rang: <rang>",
            "",
            "<dark_gray>Items zählen bei dir <faktor>%",
            "<dark_gray>Je reicher, desto weniger je Item."
    };
    public static final String KONTO_ANZEIGE_OHNE_PLATZ = "<gray>noch keiner</gray>";

    public static final String BANK_LEER = "<yellow>Die Bank ist leer - lege zuerst Items hinein.";
    public static final String BANK_WERTLOS = "<yellow>Diese Items sind 0 Punkte wert - nimm sie wieder heraus.";
    public static final String BANK_FEHLER =
            "<red>Die Punkte konnten nicht gespeichert werden. Deine Items bleiben in der Bank!";
    public static final String BANK_BESTAETIGT =
            "<green>Du hast <white><anzahl></white> Items abgegeben und <gold><punkte></gold> Punkte erhalten."
                    + " Kontostand: <gold><gesamt></gold>";

    /** <roh> = Wert ohne Bremsen, <prozent> = wie viel davon uebrig blieb. */
    public static final String BANK_GEDAEMPFT =
            "<dark_gray>Grundwert wäre <roh> gewesen - du bekommst <prozent>%,"
                    + " weil dein Konto wächst und der Markt gesättigt ist.";
    public static final String RANG_AUFSTIEG =
            "<gold><bold>Aufstieg!</bold></gold> <gray>Du bist jetzt <rang><gray>.";
    public static final String BANK_BEHAELTER_ZURUECK = "<gray>Leere Behälter hast du zurückbekommen.";
    public static final String BANK_ZURUECK = "<gray>Nichts abgegeben - du hast deine Items zurückbekommen.";
    public static final String BANK_TOD_BODEN =
            "<yellow>Du bist gestorben - deine Bank-Items liegen an deinem Todesort auf dem Boden.";
    public static final String BANK_ZURUECK_BODEN =
            "<yellow>Dein Inventar war voll - einige Items liegen vor dir auf dem Boden.";

    public static final String KONTOSTAND = "<green>Dein Kontostand: <gold><punkte></gold> Punkte<platz>.";
    public static final String KONTOSTAND_PLATZ = " <gray>(Platz <white><platz></white>)</gray>";

    /** <rang>, <faktor> = Wertfaktor in Prozent, <naechster> = Punkte bis zum naechsten Rang. */
    public static final String KONTOSTAND_RANG =
            "<gray>Rang: <rang> <dark_gray>|</dark_gray> <gray>Items zählen bei dir <white><faktor>%</white>"
                    + " <dark_gray>|</dark_gray> <gray>nächster Rang ab <white><naechster></white>";
    public static final String KONTOSTAND_HOECHSTER = "höchster erreicht";

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

    public static final String SIDEBAR_FEHLER =
            "<red>Die Rangliste konnte nicht aufgebaut werden - der Grund steht in der Server-Konsole.";
    public static final String RELOAD_TEILWEISE =
            "<yellow>config.yml neu geladen, players.yml NICHT - Einzelheiten stehen im Server-Log.";
    // ----- Effekte -----

    /** <punkte> = Punkte dieser Einzahlung. */
    public static final String TITEL_EINZAHLUNG = "<gold><bold>+<punkte> Punkte</bold></gold>";
    /** <gesamt> = neuer Kontostand, <rang> = aktueller Rang. */
    public static final String UNTERTITEL_EINZAHLUNG =
            "<gray>Kontostand <white><gesamt></white> <dark_gray>|</dark_gray> <rang>";
    public static final String TITEL_AUFSTIEG = "<gold><bold>Aufstieg!</bold></gold>";
    public static final String UNTERTITEL_AUFSTIEG = "<gray>Du bist jetzt <rang>";
    public static final String AKTIONSLEISTE_PUNKTE = "<gold>+<punkte> Punkte";
    /** <name> = Spielername, <rang> = neuer Rang. */
    public static final String RANG_BROADCAST =
            "<dark_green>[Bank]</dark_green> <gold>\u2726</gold> <white><name></white> <gray>ist jetzt <rang><gray>!";

    // ----- Fortschrittsbalken -----

    /** <rang>, <naechster>, <punkte>, <ziel>, <prozent>. */
    public static final String BOSSBAR_TEXT =
            "<rang><gray> » </gray><naechster>  <white><punkte></white><gray>/</gray>"
                    + "<white><ziel></white> <dark_gray>(<prozent>%)";
    public static final String BOSSBAR_HOECHSTER =
            "<rang> <dark_gray>|</dark_gray> <gray>Höchster Rang erreicht";

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
    /** <faktor> = Marktsaettigung in Prozent. */
    public static final String WERT_SAETTIGUNG =
            "<gray>Markt-Sättigung: <white><faktor>%</white> <dark_gray>(sinkt, je mehr du davon abgibst)";
    /** <faktor> = Wohlstands-Bremse in Prozent, <rang> = aktueller Rang. */
    public static final String WERT_WOHLSTAND =
            "<gray>Dein Rang <rang><gray>: <white><faktor>%</white> <dark_gray>(sinkt mit deinem Kontostand)";
    public static final String WERT_ENDWERT =
            "<gray>Tatsächlich: <gold><bold><punkte></bold></gold> Punkte";
    public static final String WERT_SUMME =
            "<gray>= <white><basis></white> * <white><anzahl></white> * <white><faktor></white>"
                    + " * <white><seltenheit></white> + <white><bonus></white>"
                    + " = <gold><bold><punkte></bold></gold> Punkte";

    /** <platzfarbe> und <namensfarbe> werden vor dem Auswerten eingesetzt. */
    public static final String SIDEBAR_ZEILE_TOP =
            "<platzfarbe><platz>. <namensfarbe><name> <white><punkte>";
    public static final String SIDEBAR_ZEILE_ICH = "<aqua>Du:</aqua> Platz <white><platz></white> <gold><punkte></gold>";
    public static final String SIDEBAR_ZEILE_ICH_LEER = "<aqua>Du:</aqua> <gray>noch keine Punkte";
    /** <platzfarbe>, <namensfarbe>, <platz>, <name>, <punkte>. */
    public static final String[] SIDEBAR_PLATZ_FARBEN = {"<gold>", "<white>", "<color:#cd7f32>"};
    /** <rang>, <naechster>. */
    public static final String SIDEBAR_ZEILE_RANG = "<gray>Rang:</gray> <rang> <dark_gray>»</dark_gray> <naechster>";
    public static final String SIDEBAR_KEIN_NAECHSTER = "<dark_gray>-";
    /** <balken>, <prozent>. */
    public static final String SIDEBAR_ZEILE_BALKEN = "<balken> <white><prozent>%</white>";
    public static final String SIDEBAR_ZEILE_MAX = "<dark_purple>Höchster Rang erreicht";
    /** <name>, <abstand>. */
    public static final String SIDEBAR_ZEILE_VOR_DIR =
            "<gray>Vor dir:</gray> <white><name></white> <dark_gray>+<abstand>";
    /** <abstand> = Vorsprung auf Platz zwei. */
    public static final String SIDEBAR_ZEILE_FUEHRT = "<gold>Du führst!</gold> <dark_gray>+<abstand>";
    public static final String SIDEBAR_ZEILE_TODE = "<red>Tode:</red> <white><tode></white>";
    public static final String SIDEBAR_ZEILE_TAG = "<gray>Tag <white><tag></white>";

    private Messages() {
    }

    /** MiniMessage-Text in eine Komponente wandeln (ohne den kursiven Item-Standard). */
    public static Component mm(String text, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(text, resolvers).decoration(TextDecoration.ITALIC, false);
    }
}
