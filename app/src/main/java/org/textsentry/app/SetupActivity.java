package org.textsentry.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * First run is deliberately short: sign in, verify paid access (sideload),
 * then open Settings. Permissions and monitoring are configured afterward.
 */
public final class SetupActivity extends Activity {
    private boolean navigating;
    private boolean showingOwnKey;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState != null) showingOwnKey = savedState.getBoolean("show_own_key", false);
        display();
    }

    @Override protected void onResume() {
        super.onResume();
        display();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("show_own_key", showingOwnKey);
        super.onSaveInstanceState(out);
    }

    private void display() {
        if (isFinishing() || navigating) return;
        boolean hasAccess = SetupGate.hasAccess(this);
        boolean needsSubscription = SetupFlow.needsSubscriptionPrompt(this);
        if (!hasAccess) {
            renderLogin();
        } else if (!SetupFlow.canCompleteSetup(hasAccess,
                BuildConfig.WEB_CHECKOUT_ENABLED, needsSubscription)) {
            renderSubscription();
        } else if (!SetupFlow.isComplete(this)) {
            // A session or key must never silently start message monitoring.
            new Settings(this).pause();
            SetupFlow.complete(this);
            openSettings();
        } else {
            goHome();
        }
    }

    private Intent homeIntent() {
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.isEmpty()) {
            home.setAction(Intent.ACTION_SEND).setType("text/plain");
            home.putExtra(Intent.EXTRA_TEXT, shared);
        }
        return home;
    }

    private void goHome() {
        if (navigating || isFinishing()) return;
        navigating = true;
        startActivity(homeIntent());
        finish();
    }

    private void openSettings() {
        if (navigating || isFinishing()) return;
        navigating = true;
        // Back from Settings leads to Home; first run opens Settings directly.
        startActivities(new Intent[]{homeIntent(), new Intent(this, SettingsActivity.class)});
        finish();
    }

    private LinearLayout screen(String step, String title, String subtitle) {
        LinearLayout root = UiKit.screen(this, "", false);
        TextView label = UiKit.text(this, step, 12, UiKit.MUTED, true);
        label.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 8));
        root.addView(label);
        TextView heading = UiKit.text(this, title, 29, UiKit.INK, true);
        heading.setLetterSpacing(-0.035f);
        root.addView(heading);
        TextView description = UiKit.text(this, subtitle, 15, UiKit.MUTED, false);
        description.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 18));
        root.addView(description);
        return root;
    }

    private void renderLogin() {
        LinearLayout root = screen("WELCOME", "Stop sorting through spam.",
                "Sign in to get started.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.googleSignInButton(this, card, () -> {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra("welcome", true);
            intent.putExtra("auto_login", true);
            startActivity(intent);
        });

        TextView skip = UiKit.description(this, card, "Use your own TypeSafe API key instead");
        skip.setGravity(Gravity.CENTER);
        skip.setTextSize(13);
        skip.setTextColor(UiKit.INK);
        skip.setPaintFlags(skip.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        skip.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16));
        skip.setClickable(true);
        skip.setFocusable(true);
        skip.setOnClickListener(v -> {
            if (showingOwnKey) {
                showingOwnKey = false;
                renderLogin();
            } else {
                OwnKeyAgreement.show(this, () -> {
                    showingOwnKey = true;
                    renderLogin();
                });
            }
        });

        if (showingOwnKey) {
            EditText key = UiKit.input(this, card, "TypeSafe API key", "", false, true);
            key.setHint("Paste your key");
            TextView error = UiKit.description(this, card, "");
            UiKit.button(this, card, "Continue with my key", true, () -> {
                String value = key.getText().toString().trim();
                if (value.isEmpty()) {
                    error.setText("Enter your key to continue.");
                    return;
                }
                try {
                    new KeyVault(this).save(value);
                    display();
                } catch (Exception e) {
                    error.setText("Could not save your key.");
                }
            });
        }
        TextView note = UiKit.description(this, root,
                "Private by design. Your texts never reach TextSentry servers.");
        note.setGravity(Gravity.CENTER);
    }

    private void renderSubscription() {
        LinearLayout root = screen("SUBSCRIPTION", "Activate TextSentry.",
                "Subscribe or restore your existing subscription to continue.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.button(this, card, "Subscribe or restore", true,
                () -> startActivity(SubscriptionOnboarding.continueSetupIntent(this)));
        UiKit.description(this, root,
                "Secure checkout opens in your browser. We'll verify your subscription "
                        + "and take you straight to Settings.");
    }
}
