package org.textsentry.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find plausible names in the SMS. Jev—not these heuristics—chooses the suggested phrase. */
final class ExceptionPhraseCandidates {
    static final int MAX_CANDIDATES = 8;
    private static final Pattern BRANDED_LINK = Pattern.compile(
            "https?://(?:www\\.)?([a-z][a-z0-9-]{3,40})\\."
                    + "(?:laylo\\.co|myshopify\\.com|substack\\.com|beehiiv\\.com)"
                    + "(?:[/?#:]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile(
            "\\b[A-Z][a-zA-Z0-9'&-]{2,}"
                    + "(?:\\s+[A-Z][a-zA-Z0-9'&-]{2,}){1,3}\\b");
    private static final Pattern ATTRIBUTION = Pattern.compile(
            "(?i)\\b(?:it's|from|presented by|brought to you by)\\s+"
                    + "([a-z][a-z0-9'&-]*(?:\\s+[a-z][a-z0-9'&-]*){1,3})");

    private ExceptionPhraseCandidates() {}

    static List<String> extract(String message) {
        if (message == null || message.isBlank() || message.length() > 1600) {
            return List.of();
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();

        // Artist/organization subdomains can reveal names even in lowercase SMS text.
        Matcher links = BRANDED_LINK.matcher(message);
        while (links.find()) add(unique, links.group(1).replace('-', ' '));

        String plain = URL.matcher(message).replaceAll(" ");
        Matcher named = TITLE.matcher(plain);
        while (named.find()) add(unique, named.group());

        Matcher attributed = ATTRIBUTION.matcher(plain);
        while (attributed.find()) {
            String name = attributed.group(1)
                    .replaceAll("(?i)\\s+(?:tickets?|tix|promo|offers?|sale)$", "");
            add(unique, name);
        }

        List<String> candidates = new ArrayList<>();
        for (String name : unique.values()) {
            candidates.add(name);
            if (candidates.size() == MAX_CANDIDATES) break;
        }
        return candidates;
    }

    private static void add(LinkedHashMap<String, String> values, String phrase) {
        String candidate = phrase.trim();
        if (!ExceptionRules.validPhrase(candidate)) return;
        String folded = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        values.putIfAbsent(folded, candidate);
    }
}
