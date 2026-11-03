package org.textsentry.app;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsSafetyTest {
    @Test public void settingsAreLockedToJevLatestAndThresholdPersists() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteSharedPreferences("textsentry_settings");
        Settings settings = new Settings(context);
        assertEquals("jev-latest", settings.model());
        assertTrue(settings.systemOne());
        assertEquals(90, settings.confidence());
        settings.savePolicy("Only clear promotional opt-outs", 82, "STOP");
        Settings again = new Settings(context);
        assertEquals(82, again.confidence());
        assertEquals("jev-latest", again.model());
        assertEquals("Only clear promotional opt-outs", again.prompt());
        settings.addHistory("Preview only");
        assertTrue(settings.history().contains("Preview only"));
        settings.clearHistory();
        assertFalse(settings.history().contains("Preview only"));
        for (int bad : new int[]{49, 101}) {
            try {
                settings.savePolicy("Rules", bad, "STOP");
                fail("Threshold must be validated");
            } catch (IllegalArgumentException expected) {}
        }
    }

    @Test public void legacyChatProviderKeyIsClearedBeforeTypeSafeUse() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("textsentry_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("system_one", false)
                .putBoolean("monitor", true).commit();
        KeyVault key = new KeyVault(context);
        key.save("legacy-chat-token");
        Settings updated = new Settings(context);
        assertFalse(key.hasKey());
        assertFalse(updated.monitoring());
        assertEquals("jev-latest", updated.model());
    }

    @Test public void categorySelectionsPersistAndChangeTheQueuedWorkGeneration() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteSharedPreferences("textsentry_settings");
        Settings settings = new Settings(context);
        assertEquals(MessageCategories.ALL, settings.categoryMask());
        // User overrides are still persisted, including explicit unchecking.

        String initial = settings.generation();
        settings.saveCategoryPolicy("Ignore messages about my car", 93,
                MessageCategories.VOTE | MessageCategories.POLITICAL);
        Settings next = new Settings(context);
        assertEquals(MessageCategories.VOTE | MessageCategories.POLITICAL, next.categoryMask());
        assertNotEquals(initial, next.generation());
        assertTrue(next.effectivePrompt().contains("nonpartisan get-out-the-vote"));
        assertTrue(next.effectivePrompt().contains("candidate or political party campaigns"));
        assertFalse(next.effectivePrompt().contains("marketing advertisements"));
        next.saveCategoryPolicy("", 95, 0);
        assertEquals("", next.effectivePrompt());
    }

    @Test public void startingMonitoringEnablesNewSmsAndMmsTogetherAndPauseResetsBoth() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        app.deleteSharedPreferences("textsentry_settings");
        Settings settings = new Settings(app);
        assertFalse(settings.monitoring());
        assertFalse(settings.mmsEnabled());
        assertEquals(0, settings.mmsSinceMs());
        String initialGeneration = settings.generation();
        long before = System.currentTimeMillis();

        settings.startMonitoring();
        assertTrue(settings.monitoring());
        assertTrue(settings.mmsEnabled());
        assertFalse(settings.autoSend());
        assertTrue(settings.mmsSinceMs() >= before);
        assertNotEquals(initialGeneration, settings.generation());
        long since = settings.mmsSinceMs();

        // Enabling auto-replies must remain independent and must not reset MMS history.
        settings.setAutomation(true, true);
        assertTrue(settings.autoSend());
        assertTrue(settings.mmsEnabled());
        assertEquals(since, settings.mmsSinceMs());

        settings.pause();
        assertFalse(settings.monitoring());
        assertFalse(settings.autoSend());
        assertFalse(settings.mmsEnabled());
        assertEquals(0, settings.mmsSinceMs());

        settings.startMonitoring();
        assertTrue(settings.mmsSinceMs() >= since);
        assertTrue(settings.mmsEnabled());
        assertFalse(settings.autoSend());
    }

    @Test public void savingAndPausingInvalidateAlreadyQueuedMessages() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteSharedPreferences("textsentry_settings");
        Settings settings = new Settings(context);
        String old = settings.generation();
        settings.save(Settings.DEFAULT_ENDPOINT, Settings.DEFAULT_MODEL,
                Settings.DEFAULT_PROMPT, true, false);
        String running = settings.generation();
        assertNotEquals(old, running);
        settings.pause();
        assertFalse(settings.monitoring());
        assertFalse(settings.autoSend());
        assertNotEquals(running, settings.generation());
    }
    @Test public void developerModeIsHiddenByDefaultAndPersistsIndependentlyOfRules() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteSharedPreferences("textsentry_settings");
        Settings settings = new Settings(context);
        assertFalse(settings.developerMode());
        String generation = settings.generation();
        settings.setDeveloperMode(true);
        assertTrue(new Settings(context).developerMode());
        assertEquals(generation, settings.generation());
        settings.saveCategoryPolicy("Ignore messages from my dentist.", 90,
                MessageCategories.MARKETING);
        assertTrue(settings.effectivePrompt().contains("Ignore messages from my dentist."));
        assertTrue(settings.developerMode());
        settings.setDeveloperMode(false);
        assertFalse(new Settings(context).developerMode());
    }

}
