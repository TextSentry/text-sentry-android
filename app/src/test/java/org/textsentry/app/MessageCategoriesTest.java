package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class MessageCategoriesTest {
    @Test public void defaultsSelectAllThreeCategories() {
        assertEquals(MessageCategories.ALL, MessageCategories.DEFAULT_MASK);
        String instructions = MessageCategories.compose(MessageCategories.DEFAULT_MASK, "");
        assertTrue(instructions.contains("marketing advertisements"));
        assertTrue(instructions.contains("private one-to-one"));
        assertTrue(instructions.contains("nonpartisan get-out-the-vote"));
        assertTrue(instructions.contains("candidate or political party campaigns"));
    }

    @Test public void allSelectedCategoriesShareOneBooleanDecision() {
        String instructions = MessageCategories.compose(
                MessageCategories.VOTE | MessageCategories.POLITICAL | MessageCategories.MARKETING,
                "Ignore messages from my dentist.");
        assertTrue(instructions.contains("nonpartisan get-out-the-vote"));
        assertTrue(instructions.contains("candidate or political party campaigns"));
        assertTrue(instructions.contains("marketing advertisements"));
        assertTrue(instructions.contains("ANY ONE"));
        assertTrue(instructions.contains("Ignore messages from my dentist."));
        assertTrue(instructions.contains("do not require an opt-out keyword"));
        assertTrue(instructions.contains("even if it has no unsubscribe instructions"));
        assertFalse(instructions.contains("require an explicit STOP"));
        assertTrue(instructions.contains("never override"));
    }

    @Test public void promotionalOrPoliticalTextsDoNotNeedAnOptOutPhrase() {
        for (int category : new int[] {
                MessageCategories.VOTE,
                MessageCategories.POLITICAL,
                MessageCategories.MARKETING
        }) {
            String instructions = MessageCategories.compose(category, "");
            assertTrue(instructions.contains("do not require an opt-out keyword"));
            assertFalse(instructions.contains("require an explicit STOP"));
        }
    }

    @Test public void politicalOutreachRulesAreScopedToThePoliticalCategory() {
        for (int mask = 0; mask <= MessageCategories.ALL; mask++) {
            String instructions = MessageCategories.compose(mask, "");
            boolean political = (mask & MessageCategories.POLITICAL) != 0;
            assertEquals(political, instructions.contains("political surveys and opinion polling"));
            assertEquals(political, instructions.contains("nonpartisan political outreach"));
            if (political) {
                assertTrue(instructions.contains("casual conversation"));
                assertTrue(instructions.contains("personal messages"));
            }
        }
    }

    @Test public void noSelectionsMeansNoProviderQuery() {
        assertEquals("", MessageCategories.compose(0, "Ignore everything"));
        assertEquals("None selected", MessageCategories.summary(0));
    }

    @Test public void selectedCategoryRulesAreNotAddedWhenUnchecked() {
        String onlyVote = MessageCategories.compose(MessageCategories.VOTE, "");
        assertTrue(onlyVote.contains("nonpartisan get-out-the-vote"));
        assertFalse(onlyVote.contains("candidate or political party campaigns"));
        assertFalse(onlyVote.contains("marketing advertisements"));
        assertFalse(onlyVote.contains("Ignore messages from my dentist."));
    }

    @Test public void rejectsUnknownMaskAndOversizedCombinedRules() {
        for (int invalid : new int[]{-1, 8, 15}) {
            try {
                MessageCategories.compose(invalid, "");
                fail("Unexpected mask must be rejected");
            } catch (IllegalArgumentException expected) {}
        }
        try {
            MessageCategories.compose(MessageCategories.VOTE, "x".repeat(3000));
            fail("Combined instructions cannot exceed TypeSafe limits");
        } catch (IllegalArgumentException expected) {}
    }
}
