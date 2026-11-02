package org.textsentry.app;

/**
 * User-submitted manual review (including copied MMS/RCS text).
 * This class cannot send SMS or access the device's messages provider.
 */
public final class ManualReviewService {
    public interface Provider {
        SystemOneClient.Evaluation evaluate(String rule, String body,
                                            double threshold) throws Exception;
    }

    public interface Recorder {
        void record(String body, double confidence, String reply,
                    String status, String source) throws Exception;
    }

    public static final class Result {
        public final boolean suggestStop;
        public final double confidence;

        private Result(boolean suggestStop, double confidence) {
            this.suggestStop = suggestStop;
            this.confidence = confidence;
        }
    }

    private ManualReviewService() {}

    public static Result evaluate(String message, String rule, double threshold,
                                  Provider provider, Recorder recorder) throws Exception {
        if (!MessagePolicy.eligible("+15555550123", message)
                || rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("Message or categories unavailable");
        }
        if (provider == null || recorder == null) {
            throw new IllegalArgumentException("Provider and local history required");
        }
        SystemOneClient.Evaluation evaluation = provider.evaluate(rule, message, threshold);
        // The configured AI reply is fixed STOP; this never invokes a carrier send.
        boolean suggest = evaluation.decision.shouldReply;
        recorder.record(message, evaluation.confidence, suggest ? "STOP" : "",
                suggest ? "manual_suggestion" : "no_reply", "manual");
        return new Result(suggest, evaluation.confidence);
    }
}
