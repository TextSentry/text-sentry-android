package org.textsentry.app;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class MmsInboxPolicyTest {
    @Test public void neverScansHistoryFromBeforeOptInOrTheSentBox() {
        long enabledAt = 1_800_000_000_000L;
        assertFalse(MmsInboxPolicy.isNewInboxMessage(1_799_999_999L, enabledAt, true));
        assertFalse(MmsInboxPolicy.isNewInboxMessage(1_800_000_001L, enabledAt, false));
        assertTrue(MmsInboxPolicy.isNewInboxMessage(1_800_000_001L, enabledAt, true));
    }

    @Test public void combinesActualMmsTextSubjectAndLocalImageOcr() {
        assertEquals("Election Day Tuesday\nVote Example Candidate for Senate\nPaid for by committee",
                MmsInboxPolicy.combineText("Election Day Tuesday",
                        Arrays.asList("Vote Example Candidate for Senate"),
                        Arrays.asList("Paid for by committee")));
    }

    @Test public void skipsBlankMmsRatherThanSendingImagesToTextOnlyModel() {
        assertEquals("", MmsInboxPolicy.combineText(null, Arrays.asList(" ", ""),
                Arrays.asList("", "  ")));
    }

    @Test public void truncatesTextsToProviderMaximumWithoutRepeatingParts() {
        String large = "X".repeat(2000);
        String text = MmsInboxPolicy.combineText("", Arrays.asList(large,large),
                Arrays.asList(large));
        assertEquals(1600,text.length());
        assertEquals(large.substring(0,1600),text);
    }

    @Test public void groupMmsAndUnvalidatedSenderCannotAutoReply() {
        assertFalse(MmsInboxPolicy.mayReply("+15555550123", 2));
        assertFalse(MmsInboxPolicy.mayReply("PromoName", 1));
        assertTrue(MmsInboxPolicy.mayReply("+15555550123", 1));
    }

    @Test public void onlyReceivedTextIsEligibleForModelClassification() {
        assertFalse(MmsInboxPolicy.canClassify(" ", "+15555550123"));
        assertFalse(MmsInboxPolicy.canClassify("Election campaign", "INVALID"));
        assertTrue(MmsInboxPolicy.canClassify("Vote Example Candidate for Senate", "+15555550123"));
        assertFalse(MmsInboxPolicy.canClassify("Your verification code is 123123", "+15555550123"));
    }
}
