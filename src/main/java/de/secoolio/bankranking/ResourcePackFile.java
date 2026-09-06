package de.secoolio.bankranking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Das auszuliefernde Resourcepack samt seinem SHA-1.
 *
 * <p>Das Grundpack liegt fertig gepackt im Jar. Liegen im Ordner {@code pack-eigene} des
 * Plugins eigene Dateien des Betreibers, werden sie darueber gelegt und das Pack neu gepackt.
 * Genau dafuer ist der Ordner da: eigene Klaenge einsetzen, ohne den Code anzufassen - und
 * ohne dass fremdes Tonmaterial ins oeffentliche Repository oder ins Release-Jar wandert.
 *
 * <p><strong>Der Hash muss zwischen Neustarts gleich bleiben.</strong> Sonst haelt jeder Client
 * das Pack fuer neu und laedt es bei jedem Serverstart erneut herunter. Ein ZIP ist von Haus aus
 * nicht reproduzierbar - Zeitstempel und Eintragsreihenfolge wandern hinein und damit in den
 * Hash. Deshalb wird beim Neupacken sortiert und mit festen Zeitstempeln gearbeitet.
 */
final class ResourcePackFile {

    /** Wo das Grundpack im Jar liegt. */
    static final String RESOURCE = "/pack/kopfgeld.zip";

    /** Unterordner im Plugin-Verzeichnis, aus dem eigene Dateien uebernommen werden. */
    static final String OWN_DIRECTORY = "pack-eigene";

    /** Wohin eine Datei aus {@code pack-eigene/sounds} im Pack gehoert. */
    private static final String SOUND_TARGET = "assets/bankranking/sounds/kopfgeld/";

    /**
     * Fester Zeitstempel fuer jeden Eintrag.
     *
     * <p>Der Wert selbst ist beliebig; entscheidend ist, dass er sich nicht aendert. Java legt
     * ihn zusaetzlich als DOS-Zeit in der Zeitzone des Servers ab - zieht der Server in eine
     * andere Zeitzone um, laedt jeder Client das Pack einmalig neu. Das ist hinnehmbar.
     */
    private static final long FIXED_TIME = 315532800000L;      // 1980-01-01

    private final byte[] bytes;
    private final String sha1;
    private final List<String> replaced;

    private ResourcePackFile(byte[] bytes, List<String> replaced) {
        this.bytes = bytes;
        this.sha1 = sha1Of(bytes);
        this.replaced = List.copyOf(replaced);
    }

    /**
     * Baut das auszuliefernde Pack.
     *
     * @param pluginFolder der Ordner des Plugins; darin wird {@code pack-eigene} gesucht
     * @param log          nimmt Hinweise auf uebernommene und abgelehnte Dateien entgegen
     */
    static ResourcePackFile build(Path pluginFolder, java.util.logging.Logger log) throws IOException {
        byte[] grund = readResource();
        Map<String, byte[]> eigene = readOwnFiles(pluginFolder, log);
        if (eigene.isEmpty()) {
            // Ohne eigene Dateien wird nichts angefasst: das Grundpack ist bereits zur
            // Bauzeit reproduzierbar gepackt worden, sein Hash steht damit fest.
            return new ResourcePackFile(grund, List.of());
        }
        return repack(grund, eigene);
    }

    private static byte[] readResource() throws IOException {
        try (InputStream in = ResourcePackFile.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("Das Resourcepack fehlt im Jar (" + RESOURCE + ")");
            }
            return in.readAllBytes();
        }
    }

    /** Liest {@code pack-eigene/sounds}; unbekannte Dateien werden gemeldet, nicht verschluckt. */
    private static Map<String, byte[]> readOwnFiles(Path pluginFolder,
                                                    java.util.logging.Logger log) throws IOException {
        Path klaenge = pluginFolder.resolve(OWN_DIRECTORY).resolve("sounds");
        Map<String, byte[]> gefunden = new TreeMap<>();
        if (!Files.isDirectory(klaenge)) {
            return gefunden;
        }
        try (var eintraege = Files.list(klaenge)) {
            for (Path datei : eintraege.sorted().toList()) {
                String name = datei.getFileName().toString();
                if (!Files.isRegularFile(datei)) {
                    continue;
                }
                if (!name.endsWith(".ogg")) {
                    // Ein Tippfehler im Dateinamen ist der wahrscheinlichste Fehler beim
                    // Austausch - er muss sichtbar sein, statt still zu wirken.
                    log.warning("pack-eigene/sounds/" + name
                            + " ist keine .ogg-Datei und wird nicht uebernommen");
                    continue;
                }
                byte[] inhalt = Files.readAllBytes(datei);
                if (!istOgg(inhalt)) {
                    // Eine Datei, die nur .ogg heisst, macht den Klang bei allen Spielern mit
                    // Pack lautlos - und zwar ohne jede Fehlermeldung. Deshalb wird hier
                    // hineingesehen, statt dem Namen zu glauben.
                    log.warning("pack-eigene/sounds/" + name + " ist keine Ogg-Datei (die ersten "
                            + "vier Bytes muessten OggS lauten) und wird nicht uebernommen. "
                            + "Umwandeln zum Beispiel mit: ffmpeg -i deine-datei -c:a libvorbis "
                            + "-ar 44100 " + name);
                    continue;
                }
                gefunden.put(SOUND_TARGET + name, inhalt);
            }
        }
        return gefunden;
    }

    /** Ogg-Dateien beginnen immer mit der Kennung OggS. */
    private static boolean istOgg(byte[] inhalt) {
        return inhalt.length > 4 && inhalt[0] == 'O' && inhalt[1] == 'g'
                && inhalt[2] == 'g' && inhalt[3] == 'S';
    }

    /** Packt das Grundpack mit den eigenen Dateien neu - sortiert und mit festen Zeitstempeln. */
    private static ResourcePackFile repack(byte[] grund, Map<String, byte[]> eigene)
            throws IOException {
        Map<String, byte[]> alle = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(grund))) {
            ZipEntry eintrag;
            while ((eintrag = zip.getNextEntry()) != null) {
                if (!eintrag.isDirectory()) {
                    alle.put(eintrag.getName(), zip.readAllBytes());
                }
            }
        }

        List<String> ersetzt = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : eigene.entrySet()) {
            if (alle.containsKey(e.getKey())) {
                ersetzt.add(e.getKey());
            }
            alle.put(e.getKey(), e.getValue());
        }

        ByteArrayOutputStream aus = new ByteArrayOutputStream(grund.length + 4096);
        try (ZipOutputStream zip = new ZipOutputStream(aus)) {
            // TreeMap liefert die Namen sortiert - zusammen mit dem festen Zeitstempel ist
            // damit das Ergebnis bei gleicher Eingabe byteweise gleich.
            for (Map.Entry<String, byte[]> e : alle.entrySet()) {
                // Nur die Aenderungszeit setzen. Erstellungs- und Zugriffszeit sind bei
                // einem frischen Eintrag ohnehin nicht gesetzt und wandern nicht in die Datei.
                ZipEntry eintrag = new ZipEntry(e.getKey());
                eintrag.setTime(FIXED_TIME);
                zip.putNextEntry(eintrag);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return new ResourcePackFile(aus.toByteArray(), ersetzt);
    }

    /** Legt das Pack zusaetzlich als Datei ab, damit der Betreiber hineinsehen kann. */
    void writeTo(Path ziel) throws IOException {
        Files.createDirectories(ziel.getParent());
        Files.write(ziel, this.bytes);
    }

    byte[] bytes() {
        return this.bytes;
    }

    /** Der SHA-1 in vierzig Hexziffern - genau die Form, die das Protokoll verlangt. */
    String sha1() {
        return this.sha1;
    }

    int size() {
        return this.bytes.length;
    }

    /** Welche Dateien des Grundpacks durch eigene ersetzt wurden. */
    List<String> replaced() {
        return this.replaced;
    }

    private static String sha1Of(byte[] daten) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(daten));
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 gehoert zum Pflichtumfang jeder Java-Laufzeit.
            throw new IllegalStateException("SHA-1 fehlt in dieser Java-Laufzeit", e);
        }
    }
}
