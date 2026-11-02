package org.textsentry.app;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.List;

/**
 * Read only received carrier MMS already stored by the current SMS/MMS app.
 * No write access to Android's inbox and no network/carrier MMS downloads.
 */
final class MmsInboxReader {
    static final class Item {
        final long id;
        final long timestampSeconds;
        final String sender;
        final String body;
        final boolean allowReply;
        Item(long id, long timestampSeconds, String sender, String body, boolean allowReply) {
            this.id = id;
            this.timestampSeconds = timestampSeconds;
            this.sender = sender;
            this.body = body;
            this.allowReply = allowReply;
        }
    }

    private MmsInboxReader() {}

    static List<Item> recent(ContentResolver resolver, long sinceMillis) {
        List<Item> results = new ArrayList<>();
        if (sinceMillis <= 0) return results;
        // The provider stores MMS dates as seconds, unlike SMS millisecond dates.
        String since = Long.toString((sinceMillis + 999L) / 1000L);
        try (Cursor cursor = resolver.query(Telephony.Mms.Inbox.CONTENT_URI,
                new String[]{"_id", "date", "m_type", "sub"},
                "date >= ?", new String[]{since}, "date DESC")) {
            if (cursor == null) return results;
            int count = 0;
            while (cursor.moveToNext() && count++ < 100) {
                long id = cursor.getLong(0);
                long date = cursor.getLong(1);
                // 132 = M-Retrieve.conf, i.e. a fully downloaded MMS (not just M-Notification.ind).
                if (cursor.getInt(2) != 132
                        || !MmsInboxPolicy.isNewInboxMessage(date, sinceMillis, true)) continue;
                String sender = fromAddress(resolver, id);
                if (sender == null) continue;
                String body = textAndImages(resolver, id, cursor.getString(3));
                int recipients = recipientCount(resolver, id);
                results.add(new Item(id, date, sender, body,
                        MmsInboxPolicy.mayReply(sender, recipients)));
            }
        }
        return results;
    }

    private static String fromAddress(ContentResolver resolver, long id) {
        Uri uri = Uri.parse("content://mms/" + id + "/addr");
        String from = null;
        try (Cursor cursor = resolver.query(uri,
                new String[]{"address", "type"}, "type = 137", null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value == null) continue;
                value = value.replaceAll("(?i)/TYPE=(?:PLMN|IPv4|IPv6)$", "").trim();
                // Fail closed for multiple/ambiguous senders or alphanumeric headers.
                if (from != null || !MessagePolicy.eligible(value, "MMS received")) return null;
                from = value;
            }
        }
        return from;
    }

    private static int recipientCount(ContentResolver resolver, long id) {
        // Only one-to-one MMS can authorize the existing optional SMS STOP.
        try (Cursor cursor = resolver.query(Uri.parse("content://mms/" + id + "/addr"),
                new String[]{"address"}, "type = 151", null, null)) {
            if (cursor == null) return 0;
            int count = 0;
            while (cursor.moveToNext()) {
                if (cursor.getString(0) != null) count++;
                if (count > 1) return count;
            }
            return count;
        }
    }

    private static String textAndImages(ContentResolver resolver, long id, String subject) {
        List<String> texts = new ArrayList<>();
        try (Cursor cursor = resolver.query(Uri.parse("content://mms/" + id + "/part"),
                new String[]{"_id", "ct", "text"}, null, null, "seq ASC")) {
            if (cursor == null) return "";
            while (cursor.moveToNext()) {
                String mime = cursor.getString(1);
                if (!"text/plain".equalsIgnoreCase(mime)) continue;
                String text = cursor.getString(2);
                if (text != null) texts.add(text);
            }
        }
        // Deliberately no OCR SDK: images remain on-device and are not analyzed.
        return MmsInboxPolicy.combineText(subject, texts);
    }
}
