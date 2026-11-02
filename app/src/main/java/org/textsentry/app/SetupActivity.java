package org.textsentry.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** First run: sign in or BYOK, verify managed subscription, then open Settings.
 * SMS and background permissions are configured later, never silently requested. */
public final class SetupActivity extends Activity {
    private boolean openingSettings;
    private boolean showingOwnKey;
    private boolean subscriptionOpened;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            showingOwnKey = state.getBoolean("show_own_key", false);
            subscriptionOpened = state.getBoolean("subscription_opened", false);
        }
        display();
    }

    @Override protected void onResume() {
        super.onResume();
        display();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("show_own_key", showingOwnKey);
        out.putBoolean("subscription_opened", subscriptionOpened);
        super.onSaveInstanceState(out);
    }

    private void display() {
        if (isFinishing() || openingSettings) return;
        boolean complete = SetupGate.canEnterApp(this);
        boolean needsBilling = SetupFlow.needsSubscriptionPrompt(this);
        // An existing installation goes to Home unchanged unless it still owes
        // the one-time verified checkout for a Google-managed sideload account.
        if (SetupFlow.shouldReturnHome(complete, BuildConfig.WEB_CHECKOUT_ENABLED,
                needsBilling)) {
            openApp(MainActivity.class);
            return;
        }
        if (!SetupGate.hasAccess(this)) {
            renderLogin();
        } else if (!SetupFlow.readyForSettings(
                new AuthSessionStore(this).hasSession(), new KeyVault(this).hasKey(),
                "sideload".equals(BuildConfig.FLAVOR), SetupFlow.subscriptionPromptSeen(this))) {
            // Open the one-action checkout screen immediately after Google login.
            // If the user backs out, show a simple way to resume, not a loop.
            if (BuildConfig.WEB_CHECKOUT_ENABLED && !subscriptionOpened) {
                subscriptionOpened = true;
                startActivity(SubscriptionOnboarding.continueSetupIntent(this));
            } else {
                renderSubscription();
            }
        } else {
            // First run starts paused, regardless of any stale saved automation state.
            // Existing installations keep their explicit monitoring preference.
            if (!SetupFlow.isComplete(this)) new Settings(this).pause();
            SetupFlow.complete(this);
            // Keep an incoming Play share intent when one started the setup flow.
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            openApp(shared != null && !shared.isEmpty()
                    ? MainActivity.class : SettingsActivity.class);
        }
    }

    private void openApp(Class<?> destination) {
        if (openingSettings || isFinishing()) return;
        openingSettings = true;
        Intent next = new Intent(this, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.isEmpty() && destination == MainActivity.class) {
            next.setAction(Intent.ACTION_SEND).setType("text/plain");
            next.putExtra(Intent.EXTRA_TEXT, shared);
        }
        if (destination == SettingsActivity.class) {
            // Settings is the first screen, but Home remains underneath it so
            // the back arrow returns to protection controls instead of exiting.
            startActivities(new Intent[]{new Intent(this, MainActivity.class), next});
        } else {
            startActivity(next);
        }
        finish();
    }

    private LinearLayout stage(String label, String title, String subtitle) {
        LinearLayout root = UiKit.screen(this, "", false);
        TextView step = UiKit.text(this, label, 12, UiKit.MUTED, true);
        step.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 8));
        root.addView(step);
        TextView heading = UiKit.text(this, title, 29, UiKit.INK, true);
        heading.setLetterSpacing(-0.035f);
        root.addView(heading);
        TextView description = UiKit.text(this, subtitle, 15, UiKit.MUTED, false);
        description.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 18));
        root.addView(description);
        return root;
    }

    private void renderLogin() {
        LinearLayout root = stage("GET STARTED", "Stop sorting through spam.",
                "Sign in to subscribe, or use your own TypeSafe key.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        if (!showingOwnKey) {
            UiKit.googleSignInButton(this, card, () -> {
                Intent intent = new Intent(this, AccountActivity.class);
                intent.putExtra("welcome", true);
                intent.putExtra("auto_login", true);
                startActivity(intent);
            });
            smallLink(card, "Use your own TypeSafe API key", () ->
                    OwnKeyAgreement.show(this, () -> {
                        showingOwnKey = true;
                        renderLogin();
                    }));
        } else {
            EditText key = UiKit.input(this, card, "TypeSafe API key", "", false, true);
            key.setHint("Paste your key");
            TextView error = UiKit.description(this, card, "");
            UiKit.button(this, card, "Continue", true, () -> {
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
            smallLink(card, "Use Google instead", () -> {
                showingOwnKey = false;
                renderLogin();
            });
        }
        TextView note = UiKit.description(this, root,
                "Private by design. Your texts never reach TextSentry servers.");
        note.setGravity(Gravity.CENTER);
    }

    private void renderSubscription() {
        LinearLayout root = stage("SUBSCRIPTION", "Activate TextSentry.",
                "Subscribe to unlock managed AI access.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        if (BuildConfig.WEB_CHECKOUT_ENABLED) {
            UiKit.button(this, card, "Subscribe to TextSentry", true, () ->
                    startActivity(SubscriptionOnboarding.continueSetupIntent(this)));
            UiKit.description(this, card,
                    "Already subscribed? Continue to automatically verify your account.");
        } else {
            UiKit.description(this, card,
                    "Subscriptions are unavailable in this build. Install the latest signed "
                            + "sideload release or use your own API key.");
        }
        smallLink(card, "Use your own key instead", () -> {
            // A Google session always takes precedence over BYOK in AI requests.
            // Remove it before the user enters a personal key.
            new Settings(this).pause();
            ManagedKeyMemory.clear();
            new AuthSessionStore(this).clear();
            SetupFlow.resetSubscriptionPrompt(this);
            showingOwnKey = false;
            display();
        });
    }

    private void smallLink(LinearLayout parent, String label, Runnable action) {
        TextView link = UiKit.description(this, parent, label);
        link.setGravity(Gravity.CENTER);
        link.setTextSize(13);
        link.setTextColor(UiKit.INK);
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> action.run());
    }
}
