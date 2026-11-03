package org.textsentry.app;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Fetches an already-issued OpenRouter key, or provisions this month's first key.
 * Only sends Cognito bearer tokens; never sends SMS or stores keys on disk.
 */
final class ManagedKeyClient {
    private static final String URL = "https://api.textsentry.org/v1/key";
    private ManagedKeyClient() {}

    static final class KeyResponse {
        final String key;
        final String period;
        final long expiresAtMillis;
        final double monthlyLimitUsd;

        KeyResponse(String key, String period, long expiresAtMillis, double monthlyLimitUsd) {
            this.key = key;
            this.period = period;
            this.expiresAtMillis = expiresAtMillis;
            this.monthlyLimitUsd = monthlyLimitUsd;
        }

        String status() {
            String budget = monthlyLimitUsd == 0.9 ? "$0.90" : "$1.00";
            return "✓ Managed key ready · " + budget + "/month · " + period;
        }
    }

    static KeyResponse parse(String json) {
        try {
            JSONObject data = new JSONObject(json);
            String key = data.getString("key");
            String period = data.getString("period");
            String expiry = data.getString("expires_at");
            // Only accept numeric limits that the backend currently issues.
            Object rawCap = data.get("monthly_limit_usd");
            if (!(rawCap instanceof Number)) {
                throw new IllegalArgumentException("Invalid managed key response");
            }
            double cap = ((Number) rawCap).doubleValue();
            long expiresAtMillis = Instant.parse(expiry).toEpochMilli();
            if (key.isBlank() || key.length() > 512
                    || !period.matches("[0-9]{4}-(0[1-9]|1[0-2])")
                    || (cap != 0.9 && cap != 1.0) || expiresAtMillis <= System.currentTimeMillis()) {
                throw new IllegalArgumentException("Invalid managed key response");
            }
            return new KeyResponse(key, period, expiresAtMillis, cap);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid managed key response");
        }
    }

    static String failureMessage(int status, String body) {
        if (status == 401) return "Session expired. Sign in again.";
        if (status == 402) return "An active subscription is required for managed access.";
        if (status == 409) return "Provisioning in progress. Try again shortly.";
        if (status == 429) {
            try {
                if ("key_revoked_until_next_month".equals(
                        new JSONObject(body).optString("error"))) {
                    return "This month's managed key was revoked and cannot be reissued until next month.";
                }
            } catch (Exception ignored) { }
            return "Too many requests. Try again shortly.";
        }
        if (status == 503) {
            try {
                JSONObject error = new JSONObject(body);
                String kind = error.optString("error", "");
                String stage = error.optString("stage", "");
                int upstream = error.optInt("upstream_status", 0);
                String http = upstream >= 400 && upstream <= 599
                        ? " (HTTP " + upstream + ")" : "";
                if ("provider_unavailable".equals(kind)) {
                    switch (stage) {
                        case "subscription":
                            return "RevenueCat verification is unavailable. Try again shortly.";
                        case "guardrail_lookup":
                            return "OpenRouter model guardrail setup failed" + http + ".";
                        case "guardrail_assign":
                            return "OpenRouter model guardrail assignment failed" + http + ".";
                        case "key_create":
                            return "OpenRouter key creation failed" + http + "."
                                    + (upstream == 401 || upstream == 403
                                            ? " Check management-key access." : "");
                        case "key_resume":
                            return "OpenRouter could not resume your managed key" + http + ".";
                        case "key_suspend":
                            return "OpenRouter could not suspend your managed key" + http + ".";
                        default:
                            break;
                    }
                }
                if ("temporarily_unavailable".equals(kind)
                        && "database_or_configuration".equals(stage)) {
                    return "Key database or configuration unavailable. "
                            + "Check the deployed AWS stack.";
                }
            } catch (Exception ignored) { }
        }
        return "Managed key unavailable (HTTP " + status + ").";
    }

    static KeyResponse fetch(String accessToken) throws IOException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Missing account session");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(URL).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            int status = connection.getResponseCode();
            if (status != 200 && status != 201) {
                // Interpret only the backend's known error code. Never echo
                // response bodies, access tokens, or provider keys to the UI.
                String body = "";
                try (InputStream errors = connection.getErrorStream()) {
                    if (errors != null) {
                        byte[] small = new byte[512];
                        int n = errors.read(small);
                        if (n > 0) body = new String(small, 0, n, StandardCharsets.UTF_8);
                    }
                } catch (IOException ignored) { }
                throw new IOException(failureMessage(status, body));
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[1024];
                int n;
                while ((n = input.read(chunk)) != -1) {
                    if (buffer.size() + n > 8192) {
                        throw new IOException("Invalid key response size");
                    }
                    buffer.write(chunk, 0, n);
                }
            }
            try {
                return parse(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid key response");
            }
        } finally {
            connection.disconnect();
        }
    }
}
