package org.textsentry.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

/** Play edition only analyzes text when users request it. */
public final class MainActivity extends Activity {
    private String lastSettingsGeneration;
    private EditText message;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SetupGate.enforce(this)) return;
        refresh(sharedMessage(getIntent()));
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        // Keep the user's in-progress draft when returning from their SMS app.
        Settings settings = new Settings(this);
        if (message != null && !settings.generation().equals(lastSettingsGeneration)) {
            refresh(message.getText().toString());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_SEND.equals(intent.getAction()) && SetupGate.enforce(this)) {
            refresh(sharedMessage(intent));
        }
    }

    private void refresh(String draft) {
        Settings settings = new Settings(this);
        lastSettingsGeneration = settings.generation();
        message = PlayUi.show(this, settings, draft);
    }

    private String sharedMessage(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) return shared.subSequence(0, Math.min(1600, shared.length())).toString();
        }
        return "";
    }
}
