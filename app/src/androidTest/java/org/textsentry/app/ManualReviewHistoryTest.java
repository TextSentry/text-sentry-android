package org.textsentry.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Verify the platform share contract and encrypted manual history in both flavors. */
@RunWith(AndroidJUnit4.class)
public class ManualReviewHistoryTest {
    @Test public void messagingAppsCanShareTextIntoBothAndroidFlavors() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setPackage(context.getPackageName())
                .putExtra(Intent.EXTRA_TEXT,
                        "Election Day is Tuesday. Vote for a candidate.");
        assertFalse("TextSentry must appear as a text sharing target",
                context.getPackageManager().queryIntentActivities(
                        share, PackageManager.MATCH_DEFAULT_ONLY).isEmpty());
    }

    @Test public void userSubmittedMultimediaTextIsEncryptedAsManualHistory() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(context);
        history.clear();
        final String body = "Election Day tomorrow. Vote for Example Candidate for Senate.";
        SystemOneClient provider = new SystemOneClient((url, key, request) ->
                new JSONObject().put("model", "jev-latest")
                        .put("answers", new JSONObject().put("reply",
                                new JSONObject().put("type", "noul")
                                        .put("noul", 0.97))).toString());
        try {
            ManualReviewService.Result result = ManualReviewService.evaluate(
                    body, MessageCategories.compose(MessageCategories.POLITICAL, ""), 0.90,
                    (rule, text, threshold) -> provider.evaluate(
                            "synthetic-test-key", SystemOneClient.MODEL,
                            rule, text, "STOP", threshold),
                    (text, probability, reply, status, source) -> history.add(
                            "Manual review", text, probability, reply, status, source));
            assertTrue(result.suggestStop);
            assertEquals(1, history.list().size());
            HistoryStore.Entry stored = history.list().get(0);
            assertEquals(body, stored.body);
            assertEquals("manual", stored.source);
            assertEquals("manual_suggestion", stored.status);
            assertEquals("STOP", stored.reply);
            assertEquals("Not applicable — manual analysis", stored.deletionStatus());
            String encrypted = context.getSharedPreferences(
                    "textsentry_message_history", Context.MODE_PRIVATE)
                    .getString("encrypted_records", "");
            assertFalse(encrypted.contains(body));
        } finally {
            history.clear();
        }
    }
}
