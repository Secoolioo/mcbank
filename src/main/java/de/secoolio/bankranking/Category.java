package de.secoolio.bankranking;

import java.util.Locale;
import java.util.Optional;

/**
 * Die sechs Item-Kategorien mit ihrem Config-Schluessel, dem deutschen Anzeigenamen
 * und dem Standard-Multiplikator.
 */
public enum Category {

    WAFFEN("waffen", "Waffen", 2.0),
    WERKZEUGE("werkzeuge", "Werkzeuge", 1.5),
    RUESTUNG("ruestung", "Rüstung", 1.5),
    RESSOURCEN("ressourcen", "Ressourcen", 1.0),
    NAHRUNG("nahrung", "Nahrung", 0.5),
    SONSTIGES("sonstiges", "Sonstiges", 1.0);

    private final String configKey;
    private final String displayName;
    private final double defaultMultiplier;

    Category(String configKey, String displayName, double defaultMultiplier) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.defaultMultiplier = defaultMultiplier;
    }

    public String configKey() {
        return this.configKey;
    }

    public String displayName() {
        return this.displayName;
    }

    public double defaultMultiplier() {
        return this.defaultMultiplier;
    }

    /**
     * Liest eine Kategorie aus einem Config-Wert. Erlaubt sind der Config-Schluessel,
     * der Enum-Name und die deutsche Schreibweise mit Umlaut ("Rüstung").
     */
    public static Optional<Category> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae");
        for (Category category : values()) {
            if (category.configKey.equals(key) || category.name().toLowerCase(Locale.ROOT).equals(key)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
