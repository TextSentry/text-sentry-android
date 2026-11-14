package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.List;

/** User-managed, on-device exceptions that prevent STOP but do not change Jev classification. */
public final class ExceptionsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SetupGate.enforce(this)) return;
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        render();
    }

    private void render() {
        LinearLayout root = UiKit.screen(this, "Exceptions", true);
        LinearLayout intro = UiKit.card(this, root, UiKit.HIGHLIGHT);
        UiKit.heading(this, intro, "Protected messages");
        UiKit.description(this, intro,
                "Matching messages are still classified, but TextSentry will never auto-send STOP. "
                + "Rules are encrypted on this device. Clearing History keeps them.");
        UiKit.button(this, intro, "Add a phrase", true, () -> editPhrase(""));
        List<ExceptionStore.Rule> rules;
        try {
            rules = new ExceptionStore(this).list();
        } catch (Exception error) {
            UiKit.description(this, intro, "Exceptions unavailable. Automatic STOP will be blocked.");
            return;
        }
        if (rules.isEmpty()) {
            UiKit.description(this, intro, "No exceptions yet. Add one from a history message.");
        }
        for (ExceptionStore.Rule rule : rules) {
            LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, card, "sender".equals(rule.type) ? "Protected sender" : "Protected phrase");
            UiKit.description(this, card, rule.value);
            UiKit.button(this, card, "Remove exception", false, () ->
                    new AlertDialog.Builder(this)
                            .setTitle("Remove exception?")
                            .setMessage("Future matching messages may again receive an automatic STOP.")
                            .setPositiveButton("Remove", (d, which) -> {
                                try {
                                    new ExceptionStore(this).remove(rule.type, rule.value);
                                    render();
                                } catch (Exception error) {
                                    Toast.makeText(this, "Couldn't remove exception", Toast.LENGTH_LONG).show();
                                }
                            }).setNegativeButton("Cancel", null).show());
        }
    }

    private void editPhrase(String suggested) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Brand, event, or keyword");
        input.setText(suggested);
        input.setSelection(input.length());
        new AlertDialog.Builder(this)
                .setTitle("Protect a phrase")
                .setMessage("Messages containing this phrase (ignoring spaces and punctuation) "
                        + "will still be classified, but won't receive an automatic STOP.")
                .setView(input)
                .setPositiveButton("Save", (d, which) -> {
                    try {
                        if (new ExceptionStore(this).addPhrase(input.getText().toString())) {
                            render();
                        } else {
                            Toast.makeText(this, "Already protected", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception error) {
                        Toast.makeText(this,
                                "Use a specific brand or event name (at least 4 characters).",
                                Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }
}
