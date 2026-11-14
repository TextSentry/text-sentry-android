package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class ExceptionRulesTest {
    private static final String CONCERT =
            "yoo Detroit, it's two friends! tix for Big Bootie World are LIVE AT 10AM "
            + "with code 'WORLD27'\nVIP tix are available NOW! "
            + "https://twofriends.laylo.co/t/ZUla9C/McTGRj5";

    @Test public void senderRulesMatchFormattingButNotDifferentNumbers() {
        assertEquals("15551234567", ExceptionRules.canonicalSender("+1 (555) 123-4567"));
        assertEquals("15551234567", ExceptionRules.canonicalSender("555-123-4567"));
        assertEquals("", ExceptionRules.canonicalSender("Manual review"));
        assertTrue(ExceptionRules.matchesSender("+15551234567", "(555) 123-4567"));
        assertFalse(ExceptionRules.matchesSender("+15551234567", "+15557654321"));
    }

    @Test public void phraseRuleMatchesBrandsAcrossWhitespaceAndLinks() {
        assertTrue(ExceptionRules.matchesPhrase("Two Friends", CONCERT));
        assertTrue(ExceptionRules.matchesPhrase("twofriends", CONCERT));
        assertTrue(ExceptionRules.matchesPhrase("Big Bootie World", CONCERT));
        assertFalse(ExceptionRules.matchesPhrase("Two Friends", "Tickets for another band"));
        assertFalse(ExceptionRules.matchesPhrase("sale", "Click on the link"));
    }

    @Test public void suggestedKeywordUsesBrandLinkOrNamedEvent() {
        assertEquals("twofriends", ExceptionRules.suggestPhrase(CONCERT));
        assertEquals("Big Bootie World", ExceptionRules.suggestPhrase(
                "Big Bootie World tickets are live!"));
        assertEquals("", ExceptionRules.suggestPhrase("sale today, click the link!"));
    }

    @Test public void invalidAndOverlyBroadRulesAreRejected() {
        for (String invalid : new String[] {"", "VIP", "sale", "!!!", "42", "x".repeat(100)}) {
            assertFalse(ExceptionRules.validPhrase(invalid));
        }
        assertTrue(ExceptionRules.validPhrase("Two Friends"));
        assertTrue(ExceptionRules.validPhrase("Big Bootie World"));
    }
}
