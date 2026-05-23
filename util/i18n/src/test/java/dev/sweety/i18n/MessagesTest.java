package dev.sweety.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {

    @BeforeEach
    void resetLocale() {
        Messages.setDefaultLocale(Locale.ENGLISH);
    }

    @AfterEach
    void restoreLocale() {
        Messages.setDefaultLocale(Locale.getDefault());
    }

    @Test
    void englishBaseline_happy() {
        Messages m = Messages.forBundle("messages");
        assertEquals("Hello, World!", m.get("test.greeting", "World"));
        assertEquals("Goodbye, World!", m.get("test.farewell", "World"));
    }

    @Test
    void localeSwitchToItalian_picksSeparateBundle() {
        Messages.setDefaultLocale(Locale.ITALIAN);
        Messages m = Messages.forBundle("messages");
        assertEquals("Ciao, Mario!", m.get("test.greeting", "Mario"));
        assertEquals("Arrivederci, Mario!", m.get("test.farewell", "Mario"));
    }

    @Test
    void italianFallsBackToEnglishForMissingKey() {
        Messages.setDefaultLocale(Locale.ITALIAN);
        Messages m = Messages.forBundle("messages");
        // en_only key is NOT in messages_it.yml; should fall back to en
        assertEquals("English only", m.get("test.en_only"));
    }

    @Test
    void missingKeyReturnsLiteralKey() {
        Messages m = Messages.forBundle("messages");
        String missing = "test.nonexistent.key";
        assertEquals(missing, m.get(missing));
    }

    @Test
    void hasReturnsTrueForPresentKey() {
        Messages m = Messages.forBundle("messages");
        assertTrue(m.has("test.greeting"));
        assertFalse(m.has("test.does_not_exist"));
    }

    @Test
    void messageFormatArgSubstitution() {
        Messages m = Messages.forBundle("messages");
        assertEquals("Hello, Alice!", m.get("test.greeting", "Alice"));
    }

    @Test
    void apostropheEscapingWorksWithMessageFormat() {
        Messages m = Messages.forBundle("messages");
        // Template is "It''s {0}." — MessageFormat double-quotes yield single quote
        assertEquals("It's test.", m.get("test.apostrophe", "test"));
    }

    @Test
    void diskOverrideBeatsClasspath(@TempDir Path tmp) throws Exception {
        // Write an override bundle to disk
        String override = "test:\n  greeting: \"Override {0}!\"\n";
        Files.writeString(tmp.resolve("messages_en.yml"), override);

        Messages.setDefaultLocale(Locale.ENGLISH);
        Messages m = Messages.forBundle("messages", new dev.sweety.config.yml.YamlConfiguration(), tmp);
        assertEquals("Override World!", m.get("test.greeting", "World"));
    }

    @Test
    void setDefaultLocaleInvalidatesCache() {
        Messages.setDefaultLocale(Locale.ENGLISH);
        Messages en = Messages.forBundle("messages");
        assertEquals("Hello, X!", en.get("test.greeting", "X"));

        Messages.setDefaultLocale(Locale.ITALIAN);
        Messages it = Messages.forBundle("messages");
        assertEquals("Ciao, X!", it.get("test.greeting", "X"));

        assertNotSame(en, it);
    }

    @Test
    void reloadReturnsNewInstance() {
        Messages m = Messages.forBundle("messages");
        Messages reloaded = m.reload();
        assertNotSame(m, reloaded);
        assertEquals(m.get("test.greeting", "A"), reloaded.get("test.greeting", "A"));
    }
}
