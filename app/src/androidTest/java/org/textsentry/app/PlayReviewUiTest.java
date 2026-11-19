package org.textsentry.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlayReviewUiTest {
    @Test public void sharedTextStillRequiresExplicitConsent() throws Exception {
        assumeTrue("play".equals(BuildConfig.FLAVOR));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        new Settings(app).pause();
        new AuthSessionStore(app).clear();
        KeyVault vault = new KeyVault(app);
        vault.save("synthetic-review-ui-key");
        SetupFlow.complete(app);
        Activity activity = null;
        try {
            String sample = "Synthetic hardware store promotion";
            activity = instrumentation.startActivitySync(new Intent(app, MainActivity.class)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, sample)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertNotNull(findText(root, sample));
            assertNotNull(root.findViewById(R.id.open_history));
            assertNotNull(root.findViewById(R.id.open_settings));
            assertNotNull(findText(root, "Compose a reply"));
            assertNull(findText(root, "Recipient number"));
            TextView analyze = findText(root, "Analyze message");
            assertNotNull(analyze);
            instrumentation.runOnMainSync(analyze::performClick);
            assertNotNull(findText(root, "Please consent to this analysis first."));
            assertFalse(new Settings(app).autoSend());
        } finally {
            if (activity != null) instrumentation.runOnMainSync(activity::finish);
            vault.clear();
            SetupFlow.reset(app);
        }
    }

    private static TextView findText(View view, String value) {
        if (view instanceof TextView text && value.contentEquals(text.getText())) return text;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView match = findText(group.getChildAt(index), value);
                if (match != null) return match;
            }
        }
        return null;
    }
}
