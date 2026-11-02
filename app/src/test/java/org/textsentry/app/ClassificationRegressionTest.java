package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;

/** Offline checks cover exact request payloads and category boundaries.
 * The separately gated live contract tests assert actual Jev probabilities.
 */
public class ClassificationRegressionTest {
    @Test public void reportedPoliticalPersuasionIsSubmittedUnchangedWithItsCategory() throws Exception {
        assertOneRequestForReportedSms(ClassificationFixtures.POLITICAL_PERSUASION,
                MessageCategories.POLITICAL);
    }

    @Test public void reportedTurnoutReminderIsSubmittedUnchangedWithItsCategory() throws Exception {
        assertOneRequestForReportedSms(ClassificationFixtures.GET_OUT_THE_VOTE,
                MessageCategories.VOTE);
    }

    private static void assertOneRequestForReportedSms(String sms, int category) throws Exception {
        final int[] requests = {0};
        SystemOneClient client = new SystemOneClient((url, key, body) -> {
            requests[0]++;
            JSONObject request = new JSONObject(body);
            assertEquals(sms, request.getString("state"));
            assertEquals(1, request.getJSONObject("questions").length());
            JSONObject question = request.getJSONObject("questions").getJSONObject("reply");
            assertEquals("noul", question.getString("type"));
            String instructions = question.getString("instructions");
            assertFalse("SMS must not be embedded in classifier instructions",
                    instructions.contains(sms));
            assertTrue(instructions.contains("owner rule"));
            return new JSONObject().put("model", "jev-latest")
                    .put("answers", new JSONObject().put("reply",
                            new JSONObject().put("type", "noul").put("noul", 0.97)))
                    .toString();
        });
        SystemOneClient.Evaluation evaluation = client.evaluate(
                "synthetic-key", SystemOneClient.MODEL,
                MessageCategories.compose(category, ""), sms, "STOP", 0.90);
        assertEquals(1, requests[0]);
        assertEquals(0.97, evaluation.confidence, 0.00001);
        assertTrue(evaluation.decision.shouldReply);
    }

    @Test public void broadLegitimateAutomatedFixtureSuiteIsAvailableForLiveScoring() {
        assertTrue("Keep a broad automated non-spam regression set",
                ClassificationFixtures.SERVICE_NOTIFICATIONS.length >= 30);
        for (ClassificationFixtures.Case example : ClassificationFixtures.SERVICE_NOTIFICATIONS) {
            assertFalse(example.name, example.name.isBlank());
            assertTrue(example.name, MessagePolicy.eligible("+15555550100", example.sms));
        }
    }

    @Test public void politicalAndTurnoutRulesDoNotDependOnMarketingSelection() {
        String politicalOnly = MessageCategories.compose(MessageCategories.POLITICAL, "");
        String voteOnly = MessageCategories.compose(MessageCategories.VOTE, "");
        assertTrue(politicalOnly.contains("persuasion"));
        assertTrue(politicalOnly.contains("issue advocacy"));
        assertFalse(politicalOnly.contains("nonpartisan get-out-the-vote"));
        assertTrue(voteOnly.contains("get-out-the-vote"));
        assertTrue(voteOnly.contains("logistics"));
        assertFalse(voteOnly.contains("candidate or political party campaigns"));
    }

    @Test public void serviceMessagesRemainExclusionsWhenEveryCategoryIsEnabled() {
        String instructions = MessageCategories.compose(MessageCategories.ALL, "");
        for (String phrase : new String[] {
                "pharmacy", "daycare", "schools", "appointments",
                "delivery", "banking", "utilities", "official election"
        }) {
            assertTrue("Missing safe-service exclusion: " + phrase,
                    instructions.contains(phrase));
        }
    }
}
