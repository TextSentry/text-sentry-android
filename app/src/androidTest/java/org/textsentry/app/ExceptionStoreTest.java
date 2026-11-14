package org.textsentry.app;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExceptionStoreTest {
    @Test public void encryptedRulesPersistAndSurviveHistoryClear() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExceptionStore store = new ExceptionStore(app);
        store.deleteAll();
        try {
            assertTrue(store.addSender("+15551234567"));
            assertTrue(store.addPhrase("Two Friends"));
            assertFalse(store.addPhrase("two friends"));
            assertTrue(new ExceptionStore(app).matches("(555) 123-4567", "Unrelated text"));
            assertTrue(new ExceptionStore(app).matches("+15559876543",
                    "Tickets! https://twofriends.laylo.co/t/abc"));
            assertFalse(store.matches("+15559876543", "Unrelated other brand"));
            assertEquals(2, store.list().size());
            String saved = app.getSharedPreferences("textsentry_exceptions", Context.MODE_PRIVATE)
                    .getString("rules", "");
            assertFalse(saved.isEmpty());
            assertFalse(saved.contains("Two Friends"));
            assertFalse(saved.contains("+15551234567"));
            new HistoryStore(app).clear();
            assertEquals(2, store.list().size());
            assertTrue(store.remove("sender", "+15551234567"));
            assertFalse(store.matches("+15551234567", "No brand mention"));
        } finally {
            store.deleteAll();
        }
    }

    @Test public void corruptCiphertextCannotBeTreatedAsNoExceptions() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExceptionStore store = new ExceptionStore(app);
        store.deleteAll();
        try {
            store.addPhrase("Big Bootie World");
            app.getSharedPreferences("textsentry_exceptions", Context.MODE_PRIVATE)
                    .edit().putString("rules", "broken").commit();
            try {
                store.matches("+15551234567", "Big Bootie World tickets");
                fail("Corrupt policy must throw so automatic STOP fails closed");
            } catch (Exception expected) {
                // The worker catches policy errors and never sends.
            }
        } finally {
            store.deleteAll();
        }
    }

    @Test public void fullDataEraseRemovesExceptions() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExceptionStore store = new ExceptionStore(app);
        store.deleteAll();
        store.addPhrase("Two Friends");
        DataControl.deleteLocalData(app);
        assertTrue(new ExceptionStore(app).list().isEmpty());
    }
}
