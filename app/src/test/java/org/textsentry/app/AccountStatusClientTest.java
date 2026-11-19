package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class AccountStatusClientTest {
    @Test public void verifiedEntitlementIsDistinctFromBetaManagedAccess() {
        AccountStatusClient.Status beta = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-sub\",\"managed_access\":true,\"subscription_required\":false,\"subscription_status\":\"inactive\"}");
        assertEquals("Not subscribed", beta.label(false));
        assertFalse(beta.subscribed());

        AccountStatusClient.Status subscribed = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-sub\",\"managed_access\":true,\"subscription_required\":false,\"subscription_status\":\"active\",\"subscription_sandbox\":true}");
        assertTrue(subscribed.subscribed());
        assertEquals("TextSentry subscription active (sandbox)", subscribed.label(true));
    }

    @Test public void providerOutageAndLegacyBackendNeverClaimPaid() {
        AccountStatusClient.Status unreachable = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-sub\",\"managed_access\":true,\"subscription_status\":\"unavailable\"}");
        assertFalse(unreachable.subscribed());
        assertEquals("Subscription status unavailable", unreachable.label(false));

        AccountStatusClient.Status legacy = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-sub\",\"managed_access\":true}");
        assertFalse(legacy.subscribed());
        assertEquals("Subscription status unavailable", legacy.label(false));
    }

    @Test public void sandboxExpiryIsClearlyDifferentFromMissingPurchase() {
        AccountStatusClient.Status expired = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-123\",\"subscription_status\":\"inactive\","
                + "\"subscription_reason\":\"expired\",\"subscription_sandbox\":true,"
                + "\"subscription_expires_at\":\"2026-11-01T20:00:00Z\"}");
        assertEquals("Sandbox subscription expired", expired.label(true));
        assertEquals("2026-11-01T20:00:00Z", expired.expiresAt);

        AccountStatusClient.Status noPurchase = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-123\",\"subscription_status\":\"inactive\","
                + "\"subscription_reason\":\"no_purchase\"}");
        assertEquals("No purchase found for this account", noPurchase.label(true));
    }

    @Test public void purchasedProductMissingEntitlementHasDistinctReason() {
        AccountStatusClient.Status missing = AccountStatusClient.parse(
                "{\"user_id\":\"cognito-123\",\"subscription_status\":\"inactive\","
                + "\"subscription_reason\":\"missing_entitlement\"}");
        assertEquals("Purchase is missing the textsentry entitlement", missing.label(true));
    }

    @Test public void productionNeverTreatsSandboxAsPaid() {
        AccountStatusClient.Status sandbox = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"active\","
                + "\"subscription_sandbox\":true}");
        assertTrue(sandbox.subscribed());
        assertFalse(sandbox.subscriptionValidForBuild(false));
        assertTrue(sandbox.subscriptionValidForBuild(true));
        assertEquals("Sandbox subscription only — live subscription required",
                sandbox.label(false));
        assertEquals("TextSentry subscription active (sandbox)", sandbox.label(true));
    }

    @Test public void livePurchaseWorksForEitherSideloadBuild() {
        AccountStatusClient.Status live = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"active\","
                + "\"subscription_sandbox\":false}");
        assertTrue(live.subscriptionValidForBuild(false));
        assertTrue(live.subscriptionValidForBuild(true));
        assertEquals("TextSentry subscription active", live.label(false));
    }

    @Test public void unknownPurchaseEnvironmentIsNotVerifiedForProduction() {
        AccountStatusClient.Status legacy = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"active\"}");
        assertFalse(legacy.subscriptionValidForBuild(false));
        assertEquals("Subscription environment unavailable", legacy.label(false));
    }

    @Test public void onboardingFetchesKeyOnlyForServerAuthorizedLiveSubscription() {
        AccountStatusClient.Status live = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"active\","
                + "\"subscription_sandbox\":false,\"managed_access\":true}");
        assertTrue(SubscriptionOnboarding.shouldRetrieveKey(true, false, live));
        assertFalse(SubscriptionOnboarding.shouldRetrieveKey(true, true, live));
        assertFalse(SubscriptionOnboarding.shouldRetrieveKey(false, false, live));

        AccountStatusClient.Status sandbox = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"active\","
                + "\"subscription_sandbox\":true,\"managed_access\":true}");
        assertFalse(SubscriptionOnboarding.shouldRetrieveKey(true, false, sandbox));

        AccountStatusClient.Status betaOnly = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"inactive\","
                + "\"managed_access\":true}");
        assertFalse(SubscriptionOnboarding.shouldRetrieveKey(true, false, betaOnly));
        assertFalse(SubscriptionOnboarding.shouldRetrieveKey(true, false, null));
    }

    @Test public void complimentaryAccessIsNotAReceiptButAllowsManagedOnboarding() {
        AccountStatusClient.Status credit = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"unavailable\","
                + "\"managed_access\":true,\"complimentary_access\":true}");
        assertFalse(credit.subscribed());
        assertFalse(credit.subscriptionValidForBuild(false));
        assertTrue(credit.managedKeyEligible());
        assertTrue(SubscriptionOnboarding.shouldRetrieveKey(true, false, credit));
        assertEquals("Complimentary access active", credit.label(false));
        AccountStatusClient.Status denied = AccountStatusClient.parse(
                "{\"user_id\":\"id\",\"subscription_status\":\"inactive\","
                + "\"managed_access\":false,\"complimentary_access\":true}");
        assertFalse(denied.managedKeyEligible());
    }

    @Test public void unsupportedResponseIsRejected() {
        for (String data : new String[] {"{}", "not json",
                "{\"subscription_status\":\"active\"}",
                "{\"user_id\":\"\",\"subscription_status\":\"active\"}",
                "{\"user_id\":\"abc\",\"subscription_status\":\"unexpected\"}"}) {
            try {
                AccountStatusClient.parse(data);
                fail("Must reject malformed account status");
            } catch (IllegalArgumentException expected) {}
        }
    }
}
