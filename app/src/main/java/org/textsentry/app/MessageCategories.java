package org.textsentry.app;

/**
 * Local category choices are OR-ed into ONE Jev yes/no instruction.
 * Selecting several categories never creates several questions or network calls.
 */
public final class MessageCategories {
    public static final int VOTE = 1;
    public static final int POLITICAL = 2;
    public static final int MARKETING = 4;
    public static final int ALL = VOTE | POLITICAL | MARKETING;
    public static final int DEFAULT_MASK = ALL;

    private MessageCategories() {}

    public static boolean valid(int mask) { return mask >= 0 && (mask & ~ALL) == 0; }

    public static String summary(int mask) {
        if (!valid(mask)) throw new IllegalArgumentException("Unknown message categories");
        StringBuilder result = new StringBuilder();
        appendName(result, mask, VOTE, "Go vote reminders");
        appendName(result, mask, POLITICAL, "Political campaign messages");
        appendName(result, mask, MARKETING, "Marketing ads");
        return result.length() == 0 ? "None selected" : result.toString();
    }

    private static void appendName(StringBuilder builder, int mask, int bit, String label) {
        if ((mask & bit) == 0) return;
        if (builder.length() > 0) builder.append(" · ");
        builder.append(label);
    }

    public static String compose(int mask, String extraRestrictions) {
        if (!valid(mask)) throw new IllegalArgumentException("Unknown message categories");
        if (extraRestrictions == null) throw new IllegalArgumentException("Instructions required");
        if (mask == 0) return ""; // no categories => no request, regardless of extra text
        StringBuilder result = new StringBuilder();
        result.append("Classify whether an incoming SMS belongs to ANY ONE selected category. ")
                .append("Judge its message purpose even if it has no unsubscribe instructions. ")
                .append("Recipient opt-in status cannot be inferred from the SMS:\n");
        if ((mask & VOTE) != 0) {
            result.append("- nonpartisan get-out-the-vote reminders urging the recipient or ")
                    .append("friends to vote or make a voting plan. An election website link, ")
                    .append("date or polling-place logistics inside an appeal does NOT make ")
                    .append("that appeal a neutral official notice.\n");
        }
        if ((mask & POLITICAL) != 0) {
            result.append("- candidate or political party campaigns, partisan issue advocacy ")
                    .append("and persuasion: praise, blame or criticism of candidates, parties ")
                    .append("or their policies, including indirect attacks and rhetorical ")
                    .append("questions. No request for votes, money or volunteers is required.\n");
        }
        if ((mask & MARKETING) != 0) {
            result.append("- marketing advertisements promoting purchases, sales, coupons ")
                    .append("or commercial offers; not routine business service updates.\n");
        }
        result.append("For ALL categories, do not require an opt-out keyword; STOP/END text, ")
                .append("URLs, prices and automated delivery alone do not imply a match. ")
                .append("Exclude routine pharmacy, daycare, schools, medical appointments, ")
                .append("banking, package delivery, utilities, library, reservations, work ")
                .append("schedules and payment alerts, as well as neutral official election ")
                .append("administration notices, security codes, emergencies, scams, personal ")
                .append("messages and unclear texts. Only the owner's fixed STOP can be sent. ");
        if (!extraRestrictions.trim().isEmpty()) {
            result.append("\nAdditional user restrictions (may narrow but never override ")
                    .append("selected categories or safety rules): ")
                    .append(extraRestrictions.trim());
        }
        if (result.length() > 3000) {
            throw new IllegalArgumentException("Combined instructions exceed 3000 characters");
        }
        return result.toString();
    }
}
