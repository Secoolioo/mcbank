package de.secoolio.bankranking;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Bewahrt Abschnitte einer YAML-Datei wortgetreu auf, die das Plugin nicht lesen kann.
 *
 * <p>Ein Tippfehler in einem von Hand bearbeiteten Eintrag soll nicht dazu fuehren, dass der
 * Eintrag beim naechsten Speichern verschwindet. Stattdessen wird sein Rohinhalt gemerkt und
 * unveraendert zurueckgeschrieben, damit der Betreiber ihn in Ruhe reparieren kann.
 */
final class YamlSections {

    private YamlSections() {
    }

    /** Liest einen Abschnitt samt Unterabschnitten in verschachtelte Maps. */
    static Map<String, Object> copyOf(ConfigurationSection section) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (section == null) {
            return copy;
        }
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                copy.put(key, copyOf(nested));
            } else {
                copy.put(key, value);
            }
        }
        return copy;
    }

    /** Schreibt eine solche Kopie unveraendert an ihren Pfad zurueck. */
    static void restore(ConfigurationSection root, String path, Map<String, Object> values) {
        if (values.isEmpty()) {
            // Ein leerer Abschnitt laesst sich nicht als Wert schreiben; ein leerer Knoten reicht.
            root.createSection(path);
            return;
        }
        ConfigurationSection target = root.createSection(path);
        write(target, values);
    }

    private static void write(ConfigurationSection target, Map<String, Object> values) {
        values.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                ConfigurationSection child = target.createSection(key);
                nested.forEach((childKey, childValue) -> writeOne(child, String.valueOf(childKey), childValue));
            } else {
                target.set(key, value);
            }
        });
    }

    private static void writeOne(ConfigurationSection target, String key, Object value) {
        if (value instanceof Map<?, ?> nested) {
            ConfigurationSection child = target.createSection(key);
            nested.forEach((childKey, childValue) -> writeOne(child, String.valueOf(childKey), childValue));
        } else {
            target.set(key, value);
        }
    }
}
