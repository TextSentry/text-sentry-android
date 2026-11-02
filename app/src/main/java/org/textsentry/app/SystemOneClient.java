package org.textsentry.app;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * TypeSafe System One/Jev is a classifier, not a chat model. It decides whether
 * a pre-written reply is appropriate; it never invents the text to send.
 */
public final class SystemOneClient {
    public static final String ENDPOINT = "https://api.typesafe.ai/v1/systemone";
    public static final String MODEL = "jev-latest";
    public static final String OPENROUTER_ENDPOINT = "https://openrouter.ai/api/alpha/decisions";
    public static final String OPENROUTER_MODEL = "typesafe/jev-1.14";
    public static final double APPROVAL_THRESHOLD = 0.95;

    /** Injectable transport keeps every PR test offline and deterministic. */
    public interface Transport {
        String post(String endpoint, String apiKey, String body) throws Exception;
    }

    /** Receives exact JSON request and raw JSON response; credentials are never included. */
    public interface TraceSink {
        void capture(String request, String response) throws Exception;
    }

    private final Transport transport;
    public SystemOneClient() { this(new HttpsTransport()); }
    public SystemOneClient(Transport transport) {
        if (transport == null) throw new IllegalArgumentException("Transport required");
        this.transport = transport;
    }

    public double score(String apiKey, String model, String userRule, String message)
            throws Exception {
        return score(apiKey, model, userRule, message, null);
    }

    private double score(String apiKey, String model, String userRule, String message,
                         TraceSink trace) throws Exception {
        return scoreConfigured(apiKey, model, ENDPOINT, MODEL, userRule, message, trace);
    }

    private double scoreConfigured(String apiKey, String model, String endpoint,
                                   String expectedModel, String userRule, String message,
                                   TraceSink trace) throws Exception {
        if (isEmpty(apiKey) || !expectedModel.equals(model) || isEmpty(userRule) || isEmpty(message)) {
            throw new IllegalArgumentException("Missing System One settings");
        }
        if (message.length() > 1600 || userRule.length() > 3000) {
            throw new IllegalArgumentException("Request too long");
        }
        JSONObject criteria = new JSONObject()
                .put("true", "The SMS clearly has the persuasive or promotional purpose "
                        + "defined by an owner's selected category, not merely a STOP footer or URL")
                .put("false", "Routine pharmacy, daycare, school, appointment, delivery, banking, "
                        + "billing, utility or neutral official election notice; personal, "
                        + "security, emergency, scam, ambiguous or unrelated message");
        JSONObject question = new JSONObject()
                .put("type", "noul")
                .put("instructions", "Classify the SMS by its communication purpose, not by "
                        + "whether the recipient opted in or whether STOP should be sent. "
                        + "An explicit get-out-the-vote appeal can include official voting "
                        + "links or logistics. A STOP footer alone proves nothing. "
                        + "If the meaning is unclear, answer NO. Apply the owner's rule: "
                        + userRule)
                .put("criteria", criteria);
        JSONObject request = new JSONObject()
                .put("model", expectedModel)
                .put("state", message)
                .put("questions", new JSONObject().put("reply", question));
        final String body = request.toString();
        String raw = "";
        try {
            raw = transport.post(endpoint, apiKey.trim(), body);
        } finally {
            // An optional local diagnostic must never change a provider decision or
            // expose the key. Transport failure has an empty response by design.
            if (trace != null) {
                try { trace.capture(body, raw); } catch (Exception ignored) {}
            }
        }
        try {
            JSONObject json = new JSONObject(raw);
            if (isEmpty(json.optString("model", ""))) throw new IllegalArgumentException("No model");
            JSONObject answers = json.optJSONObject("answers");
            if (answers == null) throw new IllegalArgumentException("No answers");
            JSONObject reply = answers.optJSONObject("reply");
            if (reply == null || !"noul".equals(reply.optString("type"))) {
                throw new IllegalArgumentException("Wrong answer type");
            }
            Object probability = reply.opt("noul");
            if (!(probability instanceof Number)) throw new IllegalArgumentException("No probability");
            double value = ((Number) probability).doubleValue();
            if (!Double.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Invalid probability");
            }
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unexpected TypeSafe response", e);
        }
    }

    public AiDecision decide(String apiKey, String model, String userRule,
                             String message, String cannedReply) throws Exception {
        return decide(apiKey, model, userRule, message, cannedReply, APPROVAL_THRESHOLD);
    }

    public AiDecision decide(String apiKey, String model, String userRule,
                             String message, String cannedReply, double threshold) throws Exception {
        return evaluate(apiKey, model, userRule, message, cannedReply, threshold).decision;
    }

    /** The very same provider response supplies the verdict and its exact yes probability. */
    public static final class Evaluation {
        public final AiDecision decision;
        public final double confidence;
        private Evaluation(AiDecision decision, double confidence) {
            this.decision = decision;
            this.confidence = confidence;
        }
    }

    public Evaluation evaluate(String apiKey, String model, String userRule,
                               String message, String cannedReply, double threshold) throws Exception {
        return evaluate(apiKey, model, userRule, message, cannedReply, threshold, null);
    }

    public Evaluation evaluate(String apiKey, String model, String userRule,
                               String message, String cannedReply, double threshold,
                               TraceSink trace) throws Exception {
        if (!Double.isFinite(threshold) || threshold < 0.50 || threshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be 50–100%");
        }
        if (!validReply(cannedReply)) {
            throw new IllegalArgumentException("Invalid configured reply");
        }
        return evaluateConfigured(apiKey, model, ENDPOINT, MODEL, userRule,
                message, cannedReply, threshold, trace);
    }

    /** Same Jev typed decision through OpenRouter, with a RAM-only managed credential. */
    public Evaluation evaluateManaged(String managedKey, String userRule, String message,
                                      String cannedReply, double threshold, TraceSink trace)
            throws Exception {
        return evaluateConfigured(managedKey, OPENROUTER_MODEL, OPENROUTER_ENDPOINT,
                OPENROUTER_MODEL, userRule, message, cannedReply, threshold, trace);
    }

    private Evaluation evaluateConfigured(String apiKey, String model, String endpoint,
                                          String expectedModel, String userRule,
                                          String message, String cannedReply, double threshold,
                                          TraceSink trace) throws Exception {
        if (!Double.isFinite(threshold) || threshold < 0.50 || threshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be 50–100%");
        }
        if (!validReply(cannedReply)) {
            throw new IllegalArgumentException("Invalid configured reply");
        }
        double confidence = scoreConfigured(apiKey, model, endpoint, expectedModel,
                userRule, message, trace);
        boolean approved = confidence >= threshold;
        // User-selected reply only; never send message text from an LLM response.
        AiDecision decision = AiDecision.parse(new JSONObject().put("should_reply", approved)
                .put("reply", approved ? cannedReply.trim() : "").toString());
        return new Evaluation(decision, confidence);
    }

    public static boolean validReply(String reply) {
        if (reply == null || reply.isEmpty() || reply.trim().isEmpty()
                || reply.length() > 120) return false;
        return !reply.contains("\n") && !reply.contains("\r");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class HttpsTransport implements Transport {
        @Override public String post(String endpoint, String apiKey, String body) throws Exception {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try {
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (connection.getResponseCode() != 200) {
                    // Do not expose upstream error body: it may include echoed SMS.
                    throw new IOException("System One HTTP " + connection.getResponseCode());
                }
                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream result = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int count;
                    while ((count = input.read(buf)) != -1) {
                        if (result.size() + count > 65536) throw new IOException("Response too large");
                        result.write(buf, 0, count);
                    }
                    return new String(result.toByteArray(), StandardCharsets.UTF_8);
                }
            } finally {
                connection.disconnect();
            }
        }
    }
}
