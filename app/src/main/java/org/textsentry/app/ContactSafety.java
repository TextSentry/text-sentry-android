package org.textsentry.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

/** Local contacts exclusion: failures or missing permission must never authorize analysis. */
final class ContactSafety {
    interface Lookup {
        /** True if saved, false if absent, null if contacts could not be checked. */
        Boolean isContact(String sender);
    }

    private ContactSafety() {}

    static boolean canAnalyze(Context context, String sender) {
        if (context == null) return false;
        boolean granted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        return canAnalyze(granted, sender, number -> {
            Uri lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            try (Cursor cursor = context.getContentResolver().query(lookup,
                    new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
                return cursor == null ? null : cursor.moveToFirst();
            }
        });
    }

    static boolean canAnalyze(boolean granted, String sender, Lookup lookup) {
        if (!granted || lookup == null || !MessagePolicy.eligible(sender, "Personal message"))
            return false;
        try {
            // A successful lookup with no match is the ONLY allowed case.
            return Boolean.FALSE.equals(lookup.isContact(sender));
        } catch (RuntimeException unavailable) {
            return false; // Provider errors, revoked permissions, malformed lookups.
        }
    }
}
