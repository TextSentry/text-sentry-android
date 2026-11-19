package org.textsentry.app;

import org.json.JSONObject;
import java.io.IOException;

/** Subscription state from the Cognito-protected account API, not a checkout redirect. */
final class AccountStatusClient {
    private AccountStatusClient() {}

    static final class Status {
        final String userId;
        final String subscription;
        final boolean managedAccess;
        final String reason;
        final String expiresAt;
        final Boolean isSandbox;
        final boolean complimentaryAccess;
        Status(String userId, String subscription, boolean managedAccess,
               String reason, String expiresAt, Boolean isSandbox, boolean complimentaryAccess) {
            this.userId = userId;
            this.subscription = subscription;
            this.managedAccess = managedAccess;
            this.reason = reason;
            this.expiresAt = expiresAt;
            this.isSandbox = isSandbox;
            this.complimentaryAccess = complimentaryAccess;
        }
        boolean subscribed() { return "active".equals(subscription); }
        boolean subscriptionValidForBuild(boolean sandboxBuild) {
            return subscribed() && (Boolean.FALSE.equals(isSandbox)
                    || (sandboxBuild && Boolean.TRUE.equals(isSandbox)));
        }
        boolean managedKeyEligible() {
            return managedAccess && (complimentaryAccess || subscriptionValidForBuild(false));
        }
        String label(boolean sandboxBuild) {
            if (managedAccess && complimentaryAccess) return "Complimentary access active";
            if (subscribed()) {
                if (Boolean.TRUE.equals(isSandbox))
                    return sandboxBuild ? "TextSentry subscription active (sandbox)"
                            : "Sandbox subscription only — live subscription required";
                if (Boolean.FALSE.equals(isSandbox))
                    return "TextSentry subscription active";
                return "Subscription environment unavailable";
            }
            if ("unavailable".equals(subscription)) return "Subscription status unavailable";
            if ("expired".equals(reason)) return Boolean.TRUE.equals(isSandbox) || sandboxBuild
                    ? "Sandbox subscription expired" : "Subscription expired";
            if ("missing_entitlement".equals(reason))
                return "Purchase is missing the textsentry entitlement";
            if ("no_purchase".equals(reason))
                return "No purchase found for this account";
            return "Not subscribed";
        }
    }

    static Status parse(String body) {
        try {
            JSONObject data = new JSONObject(body);
            String userId = data.getString("user_id");
            String subscription = data.optString("subscription_status", "unavailable");
            if (userId.isBlank() || userId.length() > 256
                    || !("active".equals(subscription) || "inactive".equals(subscription)
                            || "unavailable".equals(subscription))) {
                throw new IllegalArgumentException("Invalid account status");
            }
            return new Status(userId, subscription, data.optBoolean("managed_access", false),
                    data.optString("subscription_reason", ""),
                    data.optString("subscription_expires_at", ""),
                    data.isNull("subscription_sandbox") ? null
                            : data.opt("subscription_sandbox") instanceof Boolean
                            ? data.getBoolean("subscription_sandbox") : null,
                    Boolean.TRUE.equals(data.opt("complimentary_access")));
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Invalid account status");
        }
    }

    static Status fetch(String accessToken) throws IOException {
        try {
            return parse(WebCheckout.fetchAccount(accessToken));
        } catch (IllegalArgumentException ignored) {
            throw new IOException("Unable to verify your subscription.");
        }
    }
}
