package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.Test;

/**
 * Live contract test: synthetic SMS only, no carrier SMS sent.
 * Secret is injected by a trusted GitHub Actions job, never PR CI.
 */
public class SystemOneLiveTest {
    @Test public void modelClassifiesClearOptOutBetterThanPersonalConversation() throws Exception {
        String key = System.getenv("TYPESAFE_AI_API_KEY");
        Assume.assumeTrue("Requires TYPESAFE_AI_API_KEY", key != null && !key.trim().isEmpty());
        SystemOneClient client = new SystemOneClient();
        String rule = "Only opt-in promotional messages that explicitly say reply STOP to unsubscribe. Never personal messages.";
        double advertising = client.score(key, "jev-latest", rule,
                "20% off this week at Example Store! Reply STOP to unsubscribe.");
        double personal = client.score(key, "jev-latest", rule,
                "Hey! Are we still on for dinner next Thursday?");
        assertTrue("Jev should rank unambiguous opt-out higher than personal chat",
                advertising > personal);
    }

    /** Actual Jev probabilities, not mocked transport responses.
     * Runs only in the trusted main/workflow_dispatch job with a TypeSafe secret.
     */
    @Test public void reportedPoliticalCampaignAndTurnoutSmsScoreAtLeastNinetyPercent()
            throws Exception {
        String key = System.getenv("TYPESAFE_AI_API_KEY");
        Assume.assumeTrue("Requires TYPESAFE_AI_API_KEY", key != null && !key.trim().isEmpty());
        SystemOneClient client = new SystemOneClient();
        assertHighScore(client, key, "issue advocacy / political only",
                ClassificationFixtures.POLITICAL_PERSUASION, MessageCategories.POLITICAL);
        assertHighScore(client, key, "issue advocacy / all categories",
                ClassificationFixtures.POLITICAL_PERSUASION, MessageCategories.ALL);
        assertHighScore(client, key, "turnout reminder / vote only",
                ClassificationFixtures.GET_OUT_THE_VOTE, MessageCategories.VOTE);
        assertHighScore(client, key, "turnout reminder / all categories",
                ClassificationFixtures.GET_OUT_THE_VOTE, MessageCategories.ALL);
    }

    private static void assertHighScore(SystemOneClient client, String key,
                                        String label, String sms, int categories)
            throws Exception {
        double score = client.score(key, SystemOneClient.MODEL,
                MessageCategories.compose(categories, ""), sms);
        assertTrue(label + " should score at least 90%, got " + score,
                score >= 0.90);
    }

    @Test public void legitimateAutomatedServiceNotificationsScoreLow() throws Exception {
        String key = System.getenv("TYPESAFE_AI_API_KEY");
        Assume.assumeTrue("Requires TYPESAFE_AI_API_KEY", key != null && !key.trim().isEmpty());
        SystemOneClient client = new SystemOneClient();
        String rule = MessageCategories.compose(MessageCategories.ALL, "");
        StringBuilder violations = new StringBuilder();
        // Run every sample before reporting a failure, so the CI report identifies
        // all problem categories instead of only the first false positive.
        for (ClassificationFixtures.Case example : ClassificationFixtures.SERVICE_NOTIFICATIONS) {
            double score = client.score(key, SystemOneClient.MODEL, rule, example.sms);
            if (score > 0.40) {
                violations.append(example.name).append(" = ")
                        .append(Math.round(score * 100)).append("%; ");
            }
        }
        assertEquals("Routine automated messages should remain at or below 40%: "
                + violations, 0, violations.length());
    }

}
