package org.textsentry.app;

/** No purchase redirect, beta bypass or sandbox receipt alone can activate managed setup. */
final class SubscriptionOnboarding {
    private SubscriptionOnboarding() { }

    static android.content.Intent continueSetupIntent(android.content.Context context) {
        SetupFlow.beginSubscriptionHandoff(context);
        return new android.content.Intent(context, AccountActivity.class)
                .putExtra("continue_setup", true);
    }

    static boolean completeAfterVerifiedKey(android.content.Context context) {
        if (!ManagedKeyMemory.hasActiveKey()) return false;
        SetupFlow.completeSubscriptionPrompt(context);
        return true;
    }

    static boolean shouldRetrieveKey(boolean setupContinuation, boolean alreadyFetching,
                                     AccountStatusClient.Status account) {
        return setupContinuation && !alreadyFetching && account != null
                && account.managedKeyEligible();
    }
}
