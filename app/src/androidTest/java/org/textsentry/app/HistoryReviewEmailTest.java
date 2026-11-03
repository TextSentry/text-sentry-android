package org.textsentry.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** History review must be an explicit email draft, never a silent submission. */
@RunWith(AndroidJUnit4.class)
public class HistoryReviewEmailTest {
    @Test public void mmsNoMatchCreatesAddressedDraftWithExactMessageAndJevDecision()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        try {
            history.add("+15550001234", "Are you bringing the board game tonight?",
                    0.07, "", "no_reply", "mms");
            Intent intent = HistoryReviewEmail.createIntent(history.list().get(0));
            assertEquals(Intent.ACTION_SENDTO, intent.getAction());
            assertEquals("mailto:support@textsentry.org", intent.getData().toString());
            assertEquals("TextSentry message review",
                    intent.getStringExtra(Intent.EXTRA_SUBJECT));
            String body = intent.getStringExtra(Intent.EXTRA_TEXT);
            assertNotNull(body);
            assertTrue(body.contains("Are you bringing the board game tonight?"));
            assertTrue(body.contains("Jev decision: No match"));
            assertTrue(body.contains("Jev match probability: 7%"));
            assertTrue(body.contains("Message type: MMS"));
            assertTrue(body.contains("Sender: +15550001234"));
        } finally {
            history.clear();
        }
    }

    @Test public void positiveDecisionAndReplyStatusAreRepresentedAccurately()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        try {
            history.add("+15550005678", "Limited weekend sale!", 0.98, "STOP",
                    "preview", "sms");
            String body = HistoryReviewEmail.createIntent(history.list().get(0))
                    .getStringExtra(Intent.EXTRA_TEXT);
            assertTrue(body.contains("Jev decision: Match"));
            assertTrue(body.contains("Jev match probability: 98%"));
            assertTrue(body.contains("Suggested reply: STOP"));
            assertTrue(body.contains("Reply status: Preview · not sent"));
            assertTrue(body.contains("Message type: SMS"));
        } finally {
            history.clear();
        }
    }

    @Test public void everyHistoryCardHasItsOwnAccessibleOverflowButton() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new Settings(app);
        KeyVault vault = new KeyVault(app);
        HistoryStore history = new HistoryStore(app);
        vault.save("synthetic-history-review-test-key");
        SetupFlow.complete(app);
        history.clear();
        Activity activity = null;
        try {
            history.add("+15550000001", "One test message", 0.01, "",
                    "no_reply", "sms");
            history.add("+15550000002", "Second test message", 0.99, "STOP",
                    "preview", "mms");
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(app, HistoryActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(2, countWithDescription(activity.findViewById(android.R.id.content),
                    "Message options"));
        } finally {
            if (activity != null) {
                Activity launched = activity;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(launched::finish);
            }
            history.clear();
            vault.clear();
            SetupFlow.reset(app);
        }
    }

    private static int countWithDescription(View view, String description) {
        int total = description.contentEquals(view.getContentDescription() == null
                ? "" : view.getContentDescription()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                total += countWithDescription(group.getChildAt(i), description);
            }
        }
        return total;
    }
}
