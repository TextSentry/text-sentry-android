package org.textsentry.app;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

/** A copied/shared MMS text is user-initiated, never an automatic carrier reply. */
public class ManualReviewServiceTest {
    private static final String CAMPAIGN =
            "Election Day is Tuesday. Vote Example Candidate for State Senate. "
            + "Your polling place: https://example.org/vote. Reply STOP to opt out.";

    private static SystemOneClient fakeModel(double probability) {
        return new SystemOneClient((url, key, body) ->
                new JSONObject().put("model", SystemOneClient.MODEL)
                        .put("answers", new JSONObject().put("reply",
                                new JSONObject().put("type", "noul")
                                        .put("noul", probability))).toString());
    }

    @Test public void positiveManualReviewSavesLocallyOnlyWithNoAutoSend() throws Exception {
        final String[] recorded = new String[5];
        final int[] count = {0};
        SystemOneClient model = fakeModel(0.97);
        ManualReviewService.Result answer = ManualReviewService.evaluate(
                CAMPAIGN, MessageCategories.compose(MessageCategories.ALL, ""), 0.90,
                (rule, body, threshold) -> model.evaluate(
                        "synthetic-key", SystemOneClient.MODEL, rule, body, "STOP", threshold),
                (body, confidence, reply, status, source) -> {
                    count[0]++;
                    recorded[0] = body;
                    recorded[1] = String.valueOf(confidence);
                    recorded[2] = reply;
                    recorded[3] = status;
                    recorded[4] = source;
                });
        assertEquals(1, count[0]);
        assertEquals(CAMPAIGN, recorded[0]);
        assertEquals("0.97", recorded[1]);
        assertEquals("STOP", recorded[2]);
        assertEquals("manual_suggestion", recorded[3]);
        assertEquals("manual", recorded[4]);
        assertTrue(answer.suggestStop);
        assertEquals(0.97, answer.confidence, 0.0001);
    }

    @Test public void lowScoreManualReviewSavesAsNoReply() throws Exception {
        final String[] result = new String[3];
        SystemOneClient model = fakeModel(0.12);
        ManualReviewService.Result answer = ManualReviewService.evaluate(
                "Daycare: We close early today for maintenance.",
                MessageCategories.compose(MessageCategories.ALL, ""), 0.90,
                (rule, body, threshold) -> model.evaluate(
                        "synthetic-key", SystemOneClient.MODEL, rule, body, "STOP", threshold),
                (body, score, reply, status, source) -> {
                    result[0] = reply;
                    result[1] = status;
                    result[2] = source;
                });
        assertFalse(answer.suggestStop);
        assertEquals("", result[0]);
        assertEquals("no_reply", result[1]);
        assertEquals("manual", result[2]);
    }

    @Test public void rejectEmptyOversizedOrUnselectedBeforeCallingProvider() throws Exception {
        final int[] calls = {0};
        ManualReviewService.Provider neverCall = (rule, body, threshold) -> {
            calls[0]++;
            throw new AssertionError("Must not call provider");
        };
        ManualReviewService.Recorder neverRecord = (body, score, reply, status, source) -> {
            throw new AssertionError("Must not record invalid inputs");
        };
        for (String invalid : new String[] {"", "   ", "x".repeat(1601),
                "Your verification code is 123456"}) {
            try {
                ManualReviewService.evaluate(invalid, "Some rule", 0.90, neverCall, neverRecord);
                fail("Invalid message should be rejected");
            } catch (IllegalArgumentException expected) { }
        }
        try {
            ManualReviewService.evaluate(CAMPAIGN, "", 0.90, neverCall, neverRecord);
            fail("No categories should skip analysis");
        } catch (IllegalArgumentException expected) { }
        assertEquals(0, calls[0]);
    }

    @Test public void providerFailureCannotStoreAClaimedDecision() throws Exception {
        final int[] writes = {0};
        try {
            ManualReviewService.evaluate(CAMPAIGN, "campaign", 0.90,
                    (rule, body, threshold) -> { throw new java.io.IOException("offline"); },
                    (body, confidence, reply, status, source) -> { writes[0]++; });
            fail("Provider failures must fail closed");
        } catch (java.io.IOException expected) { }
        assertEquals(0, writes[0]);
    }
}
