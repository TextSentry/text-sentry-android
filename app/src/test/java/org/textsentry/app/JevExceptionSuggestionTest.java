package org.textsentry.app;

import static org.junit.Assert.*;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

/** Explicit-on-demand Jev Choice, never a local heuristic presented as AI. */
public class JevExceptionSuggestionTest {
    private static final String MESSAGE = "yoo Detroit, it's pocket planets! "
            + "Tickets for Starlight Festival go live tomorrow! "
            + "https://pocketplanets.laylo.co/t/example";

    private static String response(String choice, double probability, double confidence)
            throws Exception {
        JSONObject probabilities = new JSONObject()
                .put("candidate_0", "candidate_0".equals(choice) ? probability : 0.12)
                .put("candidate_1", "candidate_1".equals(choice) ? probability : 0.12)
                .put("none", "none".equals(choice) ? probability : 0.02);
        return new JSONObject().put("model", SystemOneClient.OPENROUTER_MODEL)
                .put("answers", new JSONObject()
                        .put("phrase", new JSONObject().put("type", "choice")
                                .put("choice", choice)
                                .put("probabilities", probabilities)
                                .put("confidence", confidence))).toString();
    }

    @Test public void localCandidatesIncludeBrandAndEventWithoutGenericMarketing() {
        List<String> phrases = ExceptionPhraseCandidates.extract(MESSAGE);
        assertTrue(phrases.contains("pocketplanets"));
        assertTrue(phrases.contains("Starlight Festival"));
        assertFalse(phrases.contains("tickets"));
        assertFalse(phrases.contains("LIVE"));
        assertTrue(phrases.size() <= ExceptionPhraseCandidates.MAX_CANDIDATES);
    }

    @Test public void oneJevChoiceSelectsBestCandidateEvenWhenNotFirst() throws Exception {
        List<String> candidates = List.of("Starlight Festival", "pocketplanets");
        int[] requests = {0};
        SystemOneClient client = new SystemOneClient((url, key, json) -> {
            requests[0]++;
            assertEquals(SystemOneClient.OPENROUTER_ENDPOINT, url);
            assertEquals("managed-test-key", key);
            JSONObject payload = new JSONObject(json);
            assertEquals(SystemOneClient.OPENROUTER_MODEL, payload.getString("model"));
            assertEquals(MESSAGE, payload.getString("state"));
            assertEquals(1, payload.getJSONObject("questions").length());
            JSONObject question = payload.getJSONObject("questions").getJSONObject("phrase");
            assertEquals("choice", question.getString("type"));
            JSONObject options = question.getJSONObject("criteria");
            assertEquals(3, options.length());
            assertTrue(options.getString("candidate_0").contains("Starlight Festival"));
            assertTrue(options.getString("candidate_1").contains("pocketplanets"));
            assertTrue(options.has("none"));
            return response("candidate_1", 0.94, 0.90);
        });
        assertEquals("pocketplanets", client.suggestExceptionManaged(
                "managed-test-key", MESSAGE, candidates));
        assertEquals(1, requests[0]);
    }

    @Test public void directByokUsesTypeSafeNotOpenRouter() throws Exception {
        SystemOneClient client = new SystemOneClient((url, key, body) -> {
            assertEquals(SystemOneClient.ENDPOINT, url);
            assertEquals(SystemOneClient.MODEL,
                    new JSONObject(body).getString("model"));
            return response("candidate_0", 0.95, 0.88);
        });
        assertEquals("pocketplanets", client.suggestException(
                "byok-test-key", SystemOneClient.MODEL, MESSAGE,
                List.of("pocketplanets")));
    }

    @Test public void ambiguousNoneOrInvalidAnswerNeverFabricatesPhrase() throws Exception {
        for (String raw : new String[] {
                response("none", 0.95, 0.90),
                response("candidate_0", 0.40, 0.40),
                response("candidate_0", 0.95, 0.12),
                response("candidate_99", 0.95, 0.90),
                "{\"model\":\"jev\",\"answers\":{\"phrase\":{\"type\":\"noul\",\"noul\":0.99}}}",
                "{\"model\":\"jev\",\"answers\":{\"phrase\":{\"type\":\"choice\",\"choice\":\"candidate_0\",\"confidence\":1,\"probabilities\":{\"candidate_0\":\"0.99\"}}}}"
        }) {
            SystemOneClient client = new SystemOneClient((url, key, json) -> raw);
            try {
                String suggestion = client.suggestExceptionManaged("key", MESSAGE,
                        List.of("pocketplanets"));
                assertEquals("none or low confidence must stay manual", "", suggestion);
            } catch (IllegalArgumentException expected) {
                // Malformed or unknown output fails closed. No rule is ever saved automatically.
            }
        }
    }

    @Test public void noCandidatesSkipsNetworkAndNeverCallsClassifierForUnrelatedTask()
            throws Exception {
        SystemOneClient client = new SystemOneClient((url, key, body) -> {
            fail("No candidates should mean no Jev request");
            return "";
        });
        assertEquals("", client.suggestExceptionManaged(
                "key", "SALE NOW! CLICK HERE!", List.of()));
    }

    @Test public void apiErrorsPropagateWithoutSavingRule() {
        SystemOneClient client = new SystemOneClient((url, key, body) -> {
            throw new java.io.IOException("Unavailable");
        });
        try {
            client.suggestExceptionManaged("key", MESSAGE, List.of("pocketplanets"));
            fail("Network error must not create a local suggestion");
        } catch (Exception expected) {
            assertTrue(expected instanceof java.io.IOException);
        }
    }

    @Test public void rejectUnrelatedOrOverlyBroadCandidatesBeforeNetwork() throws Exception {
        SystemOneClient client = new SystemOneClient((url, key, body) -> {
            fail("Invalid candidates must not reach Jev");
            return "";
        });
        try {
            client.suggestExceptionManaged("key", MESSAGE, List.of("tickets"));
            fail("Generic marketing word should fail");
        } catch (IllegalArgumentException expected) {}
        try {
            client.suggestExceptionManaged("key", MESSAGE, List.of("unrelated business"));
            fail("A phrase not in the message should fail");
        } catch (IllegalArgumentException expected) {}
    }
}
