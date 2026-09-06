package de.secoolio.bankranking;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Hilfen fuer die Tests: Logger mit Mitschrift, Config aus Text, Standard-Bausteine. */
final class TestSupport {

    /** Essbare Materialien fuer die Tests - im Betrieb liefert das die Server-Registry. */
    static final Set<Material> EDIBLE = Set.of(
            Material.BREAD, Material.GOLDEN_APPLE, Material.COOKED_BEEF, Material.APPLE, Material.CARROT,
            Material.DRIED_KELP);

    private TestSupport() {
    }

    static final class RecordingLogger extends Logger {
        private final List<String> warnings = new ArrayList<>();

        RecordingLogger() {
            super("BankRankingTest", null);
            setUseParentHandlers(false);
            setLevel(Level.ALL);
            addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                        RecordingLogger.this.warnings.add(record.getMessage());
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }

        List<String> warnings() {
            return this.warnings;
        }

        long warningsContaining(String needle) {
            return this.warnings.stream().filter(w -> w.contains(needle)).count();
        }
    }

    static YamlConfiguration config(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (InvalidConfigurationException ex) {
            throw new IllegalArgumentException(ex);
        }
        return configuration;
    }

    /** Die mit dem Plugin ausgelieferte config.yml aus dem Klassenpfad. */
    static YamlConfiguration bundledConfig() {
        try (InputStream in = TestSupport.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (in == null) {
                throw new IllegalStateException("config.yml nicht im Klassenpfad gefunden");
            }
            return config(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static CategoryClassifier classifier() {
        return new CategoryClassifier(java.util.Map.of(), EDIBLE::contains);
    }

    static Settings defaults() {
        return Settings.load(bundledConfig(), new RecordingLogger());
    }

    static Scorer scorer() {
        Settings settings = defaults();
        return new Scorer(settings, new CategoryClassifier(settings.categoryOverrides(), EDIBLE::contains));
    }
}
