package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YamlSectionsTest {

    @Test
    @DisplayName("Ein unlesbarer Abschnitt überlebt das Speichern wortgetreu")
    void roundTripKeepsEverything() throws InvalidConfigurationException {
        YamlConfiguration source = new YamlConfiguration();
        source.loadFromString("""
                spieler:
                  kaputt:
                    name: Steve
                    punkte: "12,5"
                    liste:
                      - eins
                      - zwei
                    tief:
                      innen: 42
                """);
        Map<String, Object> copy = YamlSections.copyOf(source.getConfigurationSection("spieler.kaputt"));

        YamlConfiguration target = new YamlConfiguration();
        YamlSections.restore(target, "spieler.kaputt", copy);
        YamlConfiguration reread = new YamlConfiguration();
        reread.loadFromString(target.saveToString());

        assertEquals("Steve", reread.getString("spieler.kaputt.name"));
        assertEquals("12,5", reread.getString("spieler.kaputt.punkte"));
        assertEquals(List.of("eins", "zwei"), reread.getStringList("spieler.kaputt.liste"));
        assertEquals(42, reread.getInt("spieler.kaputt.tief.innen"));
    }
}
