package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueft das mitgelieferte Pack von innen.
 *
 * <p>Ein fehlender Klang faellt sonst erst im Spiel auf, und dort als "der Ton fehlt" ohne
 * jeden Hinweis auf die Ursache: ein einziger falscher Eintrag laesst den Client die gesamte
 * {@code sounds.json} verwerfen, womit schlagartig alle eigenen Klaenge stumm sind, obwohl das
 * Pack geladen ist. {@link SoundCue} und die Pack-Datei koennen ausserdem auseinanderlaufen,
 * ohne dass es irgendwo auffaellt - genau das faengt dieser Test.
 */
class PackContentsTest {

    private static final Map<String, byte[]> INHALT = new HashMap<>();

    @BeforeAll
    static void packOeffnen() throws IOException {
        try (InputStream in = ResourcePackFile.class.getResourceAsStream(
                ResourcePackFile.RESOURCE)) {
            assertNotNull(in, "Das Resourcepack fehlt im Klassenpfad ("
                    + ResourcePackFile.RESOURCE + ")");
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry eintrag;
                while ((eintrag = zip.getNextEntry()) != null) {
                    if (eintrag.isDirectory()) {
                        continue;
                    }
                    ByteArrayOutputStream puffer = new ByteArrayOutputStream();
                    zip.transferTo(puffer);
                    INHALT.put(eintrag.getName(), puffer.toByteArray());
                }
            }
        }
    }

    @Test
    @DisplayName("Das Grundgeruest ist vollstaendig")
    void grundgeruest() {
        assertTrue(INHALT.containsKey("pack.mcmeta"), "pack.mcmeta fehlt");
        assertTrue(INHALT.containsKey("assets/bankranking/sounds.json"), "sounds.json fehlt");
    }

    @Test
    @DisplayName("Zu jedem SoundCue gibt es einen Eintrag in der sounds.json")
    void jederKlangIstAngemeldet() {
        JsonObject klaenge = sounds();
        // Ueber SoundNames statt ueber SoundCue: die Aufzaehlung traegt Vanilla-Klaenge als
        // Ersatz mit sich und liesse sich ohne laufenden Server gar nicht laden.
        for (String ereignis : SoundNames.ALLE) {
            assertTrue(klaenge.has(ereignis),
                    "In der sounds.json fehlt der Eintrag " + ereignis
                            + " - dieser Klang bliebe im Spiel stumm.");
        }
    }

    @Test
    @DisplayName("Jede in der sounds.json genannte Datei liegt auch im Pack")
    void jedeGenannteDateiExistiert() {
        JsonObject klaenge = sounds();
        for (String ereignis : klaenge.keySet()) {
            for (var eintrag : klaenge.getAsJsonObject(ereignis).getAsJsonArray("sounds")) {
                String name = eintrag.getAsJsonObject().get("name").getAsString();
                assertTrue(name.startsWith("bankranking:"),
                        "Ohne Namensraum sucht der Client in assets/minecraft: " + name);
                String pfad = "assets/bankranking/sounds/"
                        + name.substring("bankranking:".length()) + ".ogg";
                assertTrue(INHALT.containsKey(pfad),
                        "Die sounds.json nennt " + pfad + ", die Datei fehlt aber im Pack.");
            }
        }
    }

    @Test
    @DisplayName("Jede Klangdatei ist wirklich eine Ogg-Datei")
    void alleKlaengeSindOgg() {
        boolean gefunden = false;
        for (Map.Entry<String, byte[]> datei : INHALT.entrySet()) {
            if (!datei.getKey().endsWith(".ogg")) {
                continue;
            }
            gefunden = true;
            byte[] daten = datei.getValue();
            assertTrue(daten.length > 4 && daten[0] == 'O' && daten[1] == 'g'
                            && daten[2] == 'g' && daten[3] == 'S',
                    datei.getKey() + " beginnt nicht mit OggS - der Client wuerde sie "
                            + "ueberspringen.");
        }
        assertTrue(gefunden, "Im Pack liegt keine einzige Klangdatei");
    }

    @Test
    @DisplayName("pack.mcmeta nennt min_format und max_format, aber kein pack_format")
    void packFormat() {
        JsonObject meta = JsonParser.parseString(
                new String(INHALT.get("pack.mcmeta"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("pack");
        assertTrue(meta.has("min_format"), "min_format fehlt");
        assertTrue(meta.has("max_format"), "max_format fehlt");
        // Seit 1.21.9 darf pack_format nicht mehr dabeistehen; sonst meldet der Client
        // "missing mandatory fields min_format and max_format" und verwirft das Pack.
        assertFalse(meta.has("pack_format"), "pack_format darf nicht mehr dabeistehen");
    }

    @Test
    @DisplayName("Die Plakat-Schrift und ihre Grafiken sind da")
    void plakatVorhanden() {
        assertTrue(INHALT.containsKey("assets/bankranking/font/kopfgeld.json"),
                "Die Schriftbeschreibung des Plakats fehlt");
        boolean grafik = INHALT.keySet().stream()
                .anyMatch(name -> name.startsWith("assets/bankranking/textures/font/")
                        && name.endsWith(".png"));
        assertTrue(grafik, "Im Pack liegt keine einzige Schriftgrafik");
    }

    private static JsonObject sounds() {
        return JsonParser.parseString(new String(
                        INHALT.get("assets/bankranking/sounds.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
