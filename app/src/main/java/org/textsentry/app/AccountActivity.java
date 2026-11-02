package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.net.Uri;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Shared account and sign-up entry point for Play and sideload editions.
 * Opens Google's existing-account or first-time sign-up flow in the system browser.
 * Never collects Google passwords or SMS messages.
 */
public final class AccountActivity extends Activity {
    private static final int AUTH_REQUEST_CODE = 405;
    private AuthorizationService service;
    private AuthSessionStore sessions;
    private TextView status;
    private TextView subscriptionStatus;
    private TextView revenueCatAccountId;
    private Button subscribeButton;
    private boolean continueSetup;
    private boolean onboardingKeyFetchStarted;
    private boolean keyRequestInFlight;
    private int authGeneration;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        service = new AuthorizationService(this);
        sessions = new AuthSessionStore(this);
        continueSetup = getIntent().getBooleanExtra("continue_setup", false)
                || SetupFlow.subscriptionHandoffPending(this);
        render();
        if (getIntent().getBooleanExtra("auto_login", false)
                && state == null && !sessions.hasSession()) {
            startLogin();
        } else if (sessions.hasSession() && !getIntent().getBooleanExtra("welcome", false)
                && !continueSetup) {
            fetchManagedKey();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (sessions != null && sessions.hasSession()) refreshSubscription();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // A billing return is a navigation signal, NEVER evidence of payment.
        if (isBillingReturn(intent) && sessions != null && sessions.hasSession()) {
            refreshSubscription();
        }
    }

    static boolean isBillingReturn(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        Uri uri = intent.getData();
        return uri != null && "textsentry".equals(uri.getScheme())
                && "billing".equals(uri.getHost()) && "/return".equals(uri.getPath());
    }

    /** Fetch the real RevenueCat entitlement from AWS, independent of the beta key bypass. */
    private void refreshSubscription() {
        if (!sessions.hasSession()) return;
        final int generation = authGeneration;
        final TextView label = subscriptionStatus;
        final TextView accountId = revenueCatAccountId;
        if (label == null) return;
        label.setText("Checking subscription…");
        if (continueSetup && subscribeButton != null) {
            subscribeButton.setText("Checking subscription…");
            subscribeButton.setEnabled(false);
        }
        try {
            AuthState auth = sessions.load();
            if (auth == null || !auth.isAuthorized()) {
                label.setText("Sign in again to check subscription.");
                offerSignInRetry();
                return;
            }
            auth.performActionWithFreshTokens(service, (accessToken, idToken, failure) -> {
                if (generation != authGeneration || isFinishing() || isDestroyed()) return;
                if (failure != null || accessToken == null) {
                    label.setText("Subscription status unavailable");
                    offerSubscriptionRetry();
                    return;
                }
                new Thread(() -> {
                    AccountStatusClient.Status found = null;
                    try {
                        found = AccountStatusClient.fetch(accessToken);
                    } catch (Exception ignored) {
                        // No bearer token, account identifier, SMS or HTTP body in errors.
                    }
                    final AccountStatusClient.Status verified = found;
                    runOnUiThread(() -> {
                        if (generation != authGeneration || isFinishing() || isDestroyed()
                                || !sessions.hasSession() || subscriptionStatus != label) return;
                        try {
                            AuthState latest = sessions.load();
                            if (latest == null || !latest.isAuthorized()) return;
                        } catch (Exception ignored) { return; }
                        boolean sandbox = BuildConfig.WEB_CHECKOUT_ENABLED
                                && BuildConfig.WEB_CHECKOUT_URL.contains("/sandbox/");
                        label.setText(verified == null
                                ? "Subscription status unavailable"
                                : verified.label(sandbox));
                        if (verified == null || "unavailable".equals(verified.subscription)) {
                            // A failed verification is not a reason to purchase again.
                            offerSubscriptionRetry();
                            return;
                        }
                        if (verified != null && accountId != null
                                && revenueCatAccountId == accountId) {
                            // Cognito sub is the RevenueCat App User ID. Select/copy it
                            // to compare with the RevenueCat customer dashboard.
                            accountId.setText("RevenueCat customer ID: " + verified.userId);
                        }
                        boolean paid = verified != null
                                && verified.subscriptionValidForBuild(sandbox);
                        boolean keyEligible = verified != null && verified.managedAccess
                                && verified.subscriptionValidForBuild(false);
                        if (subscribeButton != null && BuildConfig.WEB_CHECKOUT_ENABLED) {
                            if (keyEligible) {
                                subscribeButton.setEnabled(true);
                                subscribeButton.setText(continueSetup
                                        ? "Finish setup" : "Refresh managed key");
                                subscribeButton.setOnClickListener(v -> fetchManagedKey());
                            } else if (paid || (verified.subscribed() && verified.isSandbox == null)
                                    || "missing_entitlement".equals(verified.reason)) {
                                // Ambiguous existing purchases require verification, not another charge.
                                subscribeButton.setEnabled(true);
                                subscribeButton.setText("Check subscription again");
                                subscribeButton.setOnClickListener(v -> refreshSubscription());
                            } else {
                                subscribeButton.setEnabled(true);
                                subscribeButton.setText("Subscribe to TextSentry");
                                subscribeButton.setOnClickListener(v -> openCheckout());
                            }
                        }
                        if (SubscriptionOnboarding.shouldRetrieveKey(continueSetup,
                                onboardingKeyFetchStarted, verified)) {
                            onboardingKeyFetchStarted = true;
                            fetchManagedKey();
                        }
                    });
                }, "textsentry-subscription-status").start();
            });
        } catch (Exception ignored) {
            label.setText("Subscription status unavailable");
            offerSubscriptionRetry();
        }
    }

    private void offerSubscriptionRetry() {
        if (!continueSetup || subscribeButton == null) return;
        subscribeButton.setText("Check subscription again");
        subscribeButton.setEnabled(true);
        subscribeButton.setOnClickListener(v -> refreshSubscription());
    }

    private void offerSignInRetry() {
        if (!continueSetup || subscribeButton == null) return;
        subscribeButton.setText("Sign in again");
        subscribeButton.setEnabled(true);
        subscribeButton.setOnClickListener(v -> startLogin());
    }

    private void render() {
        boolean welcome = getIntent().getBooleanExtra("welcome", false);
        LinearLayout root = UiKit.screen(this,
                welcome ? "Continue with Google" : "Account", !welcome);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, card, continueSetup ? "Activate subscription" : welcome ? "Sign in or sign up" : "Google account");
        UiKit.description(this, card,
                continueSetup ? "Subscribe securely. We verify payment automatically." : welcome ? "One account. Less spam." : "Manage your Google sign-in.");
        status = UiKit.description(this, card, "");
        refreshStatus();
        subscriptionStatus = null;
        revenueCatAccountId = null;
        subscribeButton = null;
        if (sessions.hasSession() && "sideload".equals(BuildConfig.FLAVOR)) {
            // Always explain the subscription path, even in a checkout-disabled
            // public prerelease. Don't silently omit it and confuse users.
            UiKit.heading(this, card, "Subscription");
            subscriptionStatus = UiKit.description(this, card, "Checking subscription…");
            if (BuildConfig.WEB_CHECKOUT_ENABLED) {
                subscribeButton = UiKit.button(this, card, "Subscribe to TextSentry",
                        true, this::openCheckout);
                if (continueSetup) {
                    subscribeButton.setText("Checking subscription…");
                    subscribeButton.setEnabled(false);
                }
                UiKit.description(this, card,
                        "Secure browser checkout. Your subscription is verified by TextSentry.");
            } else {
                subscribeButton = UiKit.button(this, card,
                        "Subscription checkout unavailable in this build", false,
                        () -> { });
                subscribeButton.setEnabled(false);
                UiKit.description(this, card,
                        "Install the subscription-enabled sideload APK to subscribe. "
                                + "Your account and local history are not removed.");
            }
            if (!continueSetup) {
                UiKit.button(this, card, "Refresh subscription", false,
                        this::refreshSubscription);
                revenueCatAccountId = UiKit.description(this, card, "");
                revenueCatAccountId.setTextIsSelectable(true);
            }
        }
        if (sessions.hasSession()) {
            if (!continueSetup && !welcome) {
                UiKit.button(this, card, "Switch Google account", false, this::startLogin);
            }
        } else {
            UiKit.googleSignInButton(this, card, this::startLogin);
        }
        // BYOK entry lives on the required Setup screen; no settings bypass here.
        if (sessions.hasSession() && !continueSetup && !welcome) {
            UiKit.button(this, card, "Sign out on this device", false, () ->
                    new AlertDialog.Builder(this)
                            .setTitle("Sign out of TextSentry?")
                            .setMessage("This removes your saved account session on this device. " +
                                    "Your local message history and BYOK settings are unchanged.")
                            .setPositiveButton("Sign out", (dialog, which) -> {
                                try {
                                    authGeneration++;
                                    new Settings(this).pause();
                                    ManagedKeyMemory.clear();
                                    sessions.clear();
                                    SetupFlow.resetSubscriptionPrompt(this);
                                    render();
                                } catch (Exception e) {
                                    status.setText("Could not remove the account session.");
                                }
                            })
                            .setNegativeButton("Cancel", null).show());
        }

        if (sessions.hasSession() && !continueSetup && !welcome) {
            UiKit.button(this, card, "Refresh key", false, this::fetchManagedKey);
        }
        UiKit.description(this, root, "Your key stays in memory. No SMS goes to AWS.");
    }

    private void refreshStatus() {
        if (!sessions.hasSession()) {
            status.setText("○ Not signed in");
            return;
        }
        try {
            AuthState state = sessions.load();
            status.setText(state != null && state.isAuthorized()
                    ? "✓ Signed in with Google on this device"
                    : "○ Session needs renewal. Sign in again.");
        } catch (Exception e) {
            status.setText("○ Could not restore saved session. Sign in again.");
        }
    }

    private void startLogin() {
        authGeneration++;
        if ("sideload".equals(BuildConfig.FLAVOR)) SetupFlow.resetSubscriptionPrompt(this);
        new Settings(this).pause(); // Don't keep queued SMS work across account switches.
        ManagedKeyMemory.clear();
        status.setText("Connecting securely to TextSentry…");
        new Thread(() -> {
            try {
                CognitoConfig config = retrieveConfiguration();
                runOnUiThread(() -> openBrowser(config));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(
                        "Sign-in configuration is unavailable. Check the connection and try again."));
            }
        }, "textsentry-auth-discovery").start();
    }

    private static CognitoConfig retrieveConfiguration() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(CognitoConfig.DISCOVERY_URL).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Authentication configuration unavailable");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] chunk = new byte[1024];
                int count;
                while ((count = stream.read(chunk)) >= 0) {
                    if (buffer.size() + count > 8192) {
                        throw new IllegalArgumentException("Unexpected configuration size");
                    }
                    buffer.write(chunk, 0, count);
                }
            }
            return CognitoConfig.parse(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private void openBrowser(CognitoConfig config) {
        if (isFinishing() || isDestroyed()) return;
        try {
            AuthorizationRequest request = CognitoLogin.createRequest(config);
            startActivityForResult(service.getAuthorizationRequestIntent(request),
                    AUTH_REQUEST_CODE);
            status.setText("Finish signing in through Google…");
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            status.setText("No supported browser is available to sign in.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != AUTH_REQUEST_CODE) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText("Google sign-in was canceled.");
            return;
        }
        AuthorizationResponse authorization = AuthorizationResponse.fromIntent(data);
        AuthorizationException error = AuthorizationException.fromIntent(data);
        if (authorization == null || error != null) {
            status.setText("Google sign-in failed. Please try again.");
            return;
        }
        status.setText("Completing secure sign-in…");
        AuthState state = new AuthState();
        state.update(authorization, null);
        service.performTokenRequest(authorization.createTokenExchangeRequest(),
                (tokens, failure) -> {
                    if (tokens == null || failure != null) {
                        runOnUiThread(() -> status.setText(
                                "Cognito could not complete sign-in. Try again."));
                        return;
                    }
                    state.update(tokens, null);
                    try {
                        sessions.save(state);
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                authGeneration++;
                                ManagedKeyMemory.clear();
                                if (getIntent().getBooleanExtra("welcome", false)) {
                                    // Return to the short subscription flow; key stays RAM-only.
                                    finish();
                                } else {
                                    render();
                                    refreshSubscription();
                                    // Onboarding must verify entitlement before asking AWS for a key.
                                    if (!continueSetup) fetchManagedKey();
                                }
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> status.setText(
                                "The account could not be saved securely. Sign-in was not completed."));
                    }
                });
    }

    /** Open hosted checkout only after verifying the account's Cognito sub with AWS.
     * Never put Cognito tokens or SMS messages in checkout URLs.
     */
    private void openCheckout() {
        if (!BuildConfig.WEB_CHECKOUT_ENABLED || !sessions.hasSession()) return;
        final int generation = authGeneration;
        status.setText("Preparing secure checkout…");
        try {
            AuthState state = sessions.load();
            if (state == null || !state.isAuthorized()
                    || state.getRefreshToken() == null) {
                status.setText("Sign in again before subscribing.");
                return;
            }
            final String refreshToken = state.getRefreshToken();
            state.performActionWithFreshTokens(service, (accessToken, idToken, failure) -> {
                if (generation != authGeneration || isFinishing() || isDestroyed()) return;
                if (failure != null || accessToken == null) {
                    status.setText("Sign in again before subscribing.");
                    return;
                }
                try {
                    sessions.save(state);
                } catch (Exception e) {
                    status.setText("Could not save your session securely.");
                    return;
                }
                new Thread(() -> {
                    String url = null;
                    try {
                        url = WebCheckout.purchaseUrl(WebCheckout.fetchUserId(accessToken),
                                BuildConfig.WEB_CHECKOUT_URL);
                    } catch (Exception ignored) {
                        // Do not include tokens, account identifiers or HTTP responses in UI errors.
                    }
                    final String target = url;
                    runOnUiThread(() -> {
                        if (generation != authGeneration || isFinishing() || isDestroyed()) return;
                        try {
                            AuthState latest = sessions.load();
                            if (latest == null || !refreshToken.equals(latest.getRefreshToken())) {
                                status.setText("Account changed. Sign in again.");
                                return;
                            }
                        } catch (Exception e) {
                            status.setText("Could not verify your session.");
                            return;
                        }
                        if (target == null) {
                            status.setText("Checkout unavailable. Please try again.");
                            return;
                        }
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                    .addCategory(Intent.CATEGORY_BROWSABLE));
                            status.setText("Checkout opened in your browser. Return here after paying.");
                        } catch (ActivityNotFoundException e) {
                            status.setText("Install a browser to complete checkout.");
                        }
                    });
                }, "textsentry-checkout").start();
            });
        } catch (Exception e) {
            status.setText("Could not prepare checkout. Sign in again.");
        }
    }

    /** Refresh Cognito access tokens when necessary, then fetch into RAM only. */
    private void fetchManagedKey() {
        if (keyRequestInFlight) return;
        if (!sessions.hasSession()) {
            ManagedKeyMemory.clear();
            status.setText("Sign in with Google to get your managed key.");
            return;
        }
        final int generation = authGeneration;
        keyRequestInFlight = true;
        status.setText("Checking your managed key…");
        try {
            AuthState state = sessions.load();
            if (state == null || !state.isAuthorized()) {
                keyRequestInFlight = false;
                onboardingKeyFetchStarted = false;
                ManagedKeyMemory.clear();
                status.setText("Sign in again to refresh your account.");
                return;
            }
            state.performActionWithFreshTokens(service, (accessToken, idToken, failure) -> {
                if (generation != authGeneration || isFinishing() || isDestroyed()) return;
                if (failure != null || accessToken == null) {
                    keyRequestInFlight = false;
                    onboardingKeyFetchStarted = false;
                    ManagedKeyMemory.clear();
                    status.setText("Account session expired. Sign in again.");
                    return;
                }
                try {
                    // Persist only OAuth session tokens, never the OpenRouter key.
                    sessions.save(state);
                } catch (Exception e) {
                    keyRequestInFlight = false;
                    onboardingKeyFetchStarted = false;
                    ManagedKeyMemory.clear();
                    status.setText("Unable to secure refreshed account tokens.");
                    return;
                }
                new Thread(() -> {
                    ManagedKeyClient.KeyResponse key = null;
                    String error = null;
                    try {
                        key = ManagedKeyClient.fetch(accessToken);
                    } catch (Exception e) {
                        // ManagedKeyClient exceptions do not contain credentials.
                        error = e.getMessage();
                    }
                    final ManagedKeyClient.KeyResponse fetched = key;
                    final String problem = error;
                    runOnUiThread(() -> {
                        if (generation != authGeneration || isFinishing() || isDestroyed()
                                || !sessions.hasSession()) return;
                        keyRequestInFlight = false;
                        if (fetched == null) {
                            onboardingKeyFetchStarted = false;
                            ManagedKeyMemory.clear();
                            status.setText(problem == null
                                    ? "Could not retrieve managed key."
                                    : problem);
                        } else {
                            ManagedKeyMemory.install(fetched.key, fetched.expiresAtMillis);
                            status.setText(fetched.status());
                            if (continueSetup) {
                                // Only verified entitlement and a RAM key can finish setup.
                                if (SubscriptionOnboarding.completeAfterVerifiedKey(this)) {
                                    finish();
                                } else {
                                    onboardingKeyFetchStarted = false;
                                    status.setText("Could not install the managed key. Retry.");
                                }
                            } else if (getIntent().getBooleanExtra("welcome", false)) {
                                finish();
                            }
                        }
                    });
                }, "textsentry-managed-key").start();
            });
        } catch (Exception e) {
            keyRequestInFlight = false;
            onboardingKeyFetchStarted = false;
            ManagedKeyMemory.clear();
            status.setText("Unable to read account session. Sign in again.");
        }
    }

    @Override protected void onDestroy() {
        if (service != null) service.dispose();
        super.onDestroy();
    }
}
