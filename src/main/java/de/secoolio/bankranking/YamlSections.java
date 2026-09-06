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

    /**
     * Schreibt einen bewahrten Wert unveraendert zurueck.
     *
     * <p>Der Schluessel wird woertlich gesetzt, auch wenn er einen Punkt enthaelt: sonst wuerde aus
     * einem Namen wie {@code my.player} unversehens eine Verschachtelung.
     *
     * @param parent der Abschnitt, in den geschrieben wird
     * @param key    der Schluessel darin
     * @param value  eine Map (Abschnitt) oder ein einfacher Wert
     */
    static void restore(ConfigurationSection parent, String key, Object value) {
        if (value instanceof Map<?, ?> nested) {
            if (nested.isEmpty()) {
                // Ein leerer Abschnitt laesst sich nicht als Wert schreiben; ein leerer Knoten reicht.
                parent.createSection(key);
                return;
            }
            ConfigurationSection target = parent.createSection(key);
            nested.forEach((childKey, childValue) -> restore(target, String.valueOf(childKey), childValue));
            return;
        }
        parent.set(key, value);
    }

    /** Der Rohinhalt eines Schluessels: ein Abschnitt wird zur Map, alles andere bleibt, wie es ist. */
    static Object rawValue(ConfigurationSection section, String key) {
        Object value = section.get(key);
        return value instanceof ConfigurationSection nested ? copyOf(nested) : value;
    }
}
