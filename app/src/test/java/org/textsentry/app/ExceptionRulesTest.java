package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class ExceptionRulesTest {
    private static final String CONCERT =
            "Hey, it's the Pocket Planets! Starlight Festival tickets are live. "
            + "https://pocketplanets.laylo.co/t/example";

    @Test public void senderRulesMatchFormattingButNotDifferentNumbers() {
        assertEquals("15551234567", ExceptionRules.canonicalSender("+1 (555) 123-4567"));
        assertEquals("15551234567", ExceptionRules.canonicalSender("555-123-4567"));
        assertEquals("", ExceptionRules.canonicalSender("Manual review"));
        assertTrue(ExceptionRules.matchesSender("+15551234567", "(555) 123-4567"));
        assertFalse(ExceptionRules.matchesSender("+15551234567", "+15557654321"));
    }

    @Test public void phraseRuleMatchesBrandsAcrossWhitespaceAndLinks() {
        assertTrue(ExceptionRules.matchesPhrase("Pocket Planets", CONCERT));
        assertTrue(ExceptionRules.matchesPhrase("pocketplanets", CONCERT));
        assertTrue(ExceptionRules.matchesPhrase("Pocket Planets",
                "https://pocketplanets.laylo.co/t/example"));
        assertTrue(ExceptionRules.matchesPhrase("Starlight Festival", CONCERT));
        assertFalse(ExceptionRules.matchesPhrase("Pocket Planets", "Tickets for another band"));
        assertFalse(ExceptionRules.matchesPhrase("sale", "Click on the link"));
    }

    @Test public void suggestedKeywordUsesBrandLinkOrNamedEvent() {
        assertEquals("pocketplanets", ExceptionRules.suggestPhrase(CONCERT));
        assertEquals("Starlight Festival", ExceptionRules.suggestPhrase(
                "Starlight Festival tickets are live!"));
        assertEquals("", ExceptionRules.suggestPhrase("sale today, click the link!"));
    }

    @Test public void invalidAndOverlyBroadRulesAreRejected() {
        for (String invalid : new String[] {"", "VIP", "sale", "!!!", "42", "x".repeat(100)}) {
            assertFalse(ExceptionRules.validPhrase(invalid));
        }
        assertTrue(ExceptionRules.validPhrase("Pocket Planets"));
        assertTrue(ExceptionRules.validPhrase("Starlight Festival"));
    }
}
