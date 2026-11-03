package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class ManagedKeyClientTest {
    @Test public void parsesIssuedKeyAndMonthExpiryWithoutExposingItInStatus() throws Exception {
        String json = "{\"key\":\"synthetic-key-not-real\",\"period\":\"2099-11\","
                + "\"expires_at\":\"2099-12-01T00:00:00Z\",\"monthly_limit_usd\":1}";
        ManagedKeyClient.KeyResponse key = ManagedKeyClient.parse(json);
        assertEquals("synthetic-key-not-real", key.key);
        assertEquals("2099-11", key.period);
        assertTrue(key.expiresAtMillis > System.currentTimeMillis());
        assertFalse(key.status().contains("synthetic-key-not-real"));
    }

    @Test public void supportsCurrentAndLegacyCapsAndReportsActualLimit() {
        for (String[] supported : new String[][] {
                {"0.9", "$0.90/month"}, {"1", "$1.00/month"}, {"1.0", "$1.00/month"}
        }) {
            String json = "{\"key\":\"synthetic-managed-key\",\"period\":\"2099-11\","
                    + "\"expires_at\":\"2099-12-01T00:00:00Z\","
                    + "\"monthly_limit_usd\":" + supported[0] + "}";
            ManagedKeyClient.KeyResponse response = ManagedKeyClient.parse(json);
            assertTrue(response.status().contains(supported[1]));
            assertFalse(response.status().contains(response.key));
        }
    }

    @Test public void rejectsMalformedOrUnsupportedCaps() {
        for (String invalid : new String[] {
                "0", "-1", "0.89", "0.91", "1.01", "5", "null", "\"0.9\""
        }) {
            String json = "{\"key\":\"synthetic-managed-key\",\"period\":\"2099-11\","
                    + "\"expires_at\":\"2099-12-01T00:00:00Z\","
                    + "\"monthly_limit_usd\":" + invalid + "}";
            try {
                ManagedKeyClient.parse(json);
                fail("Unsupported or malformed monthly limit accepted: " + invalid);
            } catch (IllegalArgumentException expected) {
                // Must not leak the key in errors.
            }
        }
    }

    @Test public void revokedKeyErrorIsNotConfusedWithSubscriptionOrMonthlySpend() {
        assertEquals("This month's managed key was revoked and cannot be reissued until next month.",
                ManagedKeyClient.failureMessage(429,
                        "{\"error\":\"key_revoked_until_next_month\"}"));
        assertEquals("Too many requests. Try again shortly.",
                ManagedKeyClient.failureMessage(429, "{}"));
        assertEquals("An active subscription is required for managed access.",
                ManagedKeyClient.failureMessage(402,
                        "{\"error\":\"active_subscription_required\"}"));
        assertEquals("Provisioning in progress. Try again shortly.",
                ManagedKeyClient.failureMessage(409, ""));
        assertEquals("Managed key unavailable (HTTP 503).",
                ManagedKeyClient.failureMessage(503, ""));
    }

    @Test public void providerErrorGivesSanitizedActionableDiagnostic() {
        assertEquals("OpenRouter key creation failed (HTTP 403). Check management-key access.",
                ManagedKeyClient.failureMessage(503,
                        "{\"error\":\"provider_unavailable\",\"stage\":\"key_create\",\"upstream_status\":403}"));
        assertEquals("OpenRouter model guardrail setup failed (HTTP 400).",
                ManagedKeyClient.failureMessage(503,
                        "{\"error\":\"provider_unavailable\",\"stage\":\"guardrail_lookup\",\"upstream_status\":400}"));
        assertEquals("RevenueCat verification is unavailable. Try again shortly.",
                ManagedKeyClient.failureMessage(503,
                        "{\"error\":\"provider_unavailable\",\"stage\":\"subscription\"}"));
        assertEquals("Key database or configuration unavailable. Check the deployed AWS stack.",
                ManagedKeyClient.failureMessage(503,
                        "{\"error\":\"temporarily_unavailable\",\"stage\":\"database_or_configuration\"}"));
        assertEquals("Managed key unavailable (HTTP 503).",
                ManagedKeyClient.failureMessage(503,
                        "{\"error\":\"provider_unavailable\",\"stage\":\"not-a-recognized-stage\",\"upstream_status\":401}"));
        assertEquals("Managed key unavailable (HTTP 503).",
                ManagedKeyClient.failureMessage(503, "{\"stage\":\"key_create\"}"));
        assertEquals("Managed key unavailable (HTTP 503).",
                ManagedKeyClient.failureMessage(503, "not-json"));
    }

    @Test public void refusesExpiredMissingOrOverBudgetKeys() throws Exception {
        for (String json : new String[] {
            "{\"key\":\"x\",\"period\":\"2020-01\",\"expires_at\":\"2020-02-01T00:00:00Z\",\"monthly_limit_usd\":1}",
            "{\"period\":\"2099-11\",\"expires_at\":\"2099-12-01T00:00:00Z\",\"monthly_limit_usd\":1}",
            "{\"key\":\"x\",\"period\":\"2099-11\",\"expires_at\":\"2099-12-01T00:00:00Z\",\"monthly_limit_usd\":5}"
        }) {
            try {
                ManagedKeyClient.parse(json);
                fail("Invalid/overbudget or expired key must be rejected");
            } catch (IllegalArgumentException expected) {
                // Never echo response bodies or key strings in failures.
            }
        }
    }
}
