# Google Play release preparation

## Current release strategy

**Two product flavors** are intentionally offered:

- `play`: text shared/pasted manually, user-consented TypeSafe AI evaluation, editable suggestion and user-initiated SMS composition via `ACTION_SENDTO`. **No SMS or call-log permissions, receivers, monitoring or automatic SMS sends.**
- `sideload`: one explicit **Start Monitoring** action enables SMS and text-only MMS monitoring (no separate MMS switch or popup), requesting any missing `RECEIVE_SMS` and hard-restricted `READ_SMS` permissions in one operation. Optional `SEND_SMS` remains separately consented; `RECEIVE_MMS` and `RECEIVE_WAP_PUSH` remain sideload-only. The MMS path reads only new completed MMS stored by the existing default messaging app; it classifies only text parts and subject (no image analysis), uses encrypted history and blocks automatic group replies. A compatible installer/Android grant is required. **Never upload this artifact to Play Console.** Inbox deletion remains unavailable without building a complete default SMS/MMS app.

Google Play's SMS policy requires active default SMS handler status or an individually approved exception for the restricted permissions. A hypothetical exception for *device automation* requires automation across multiple areas of Android; this app currently automates SMS only and should not claim it qualifies. A spam-blocking exception is also subject to Google review, not guaranteed. Do not change the manifest to mimic default-handler eligibility without implementing and testing a complete usable SMS/MMS client.

Play onboarding stops at "Start reviewing" after Google/BYOK setup, without requesting Android SMS or background permissions. The sideload build has a separate four-stage guide for battery usage, optional outgoing SMS, incoming SMS and preview-only monitoring; Play must never adopt those permissions or monitoring pages.

Both editions require Google-only Cognito sign-in or locally encrypted TypeSafe BYOK setup before accessing the app. First launch offers Google first with a small BYOK link. Google uses browser redirect with PKCE. It adds no SMS or Call Log permissions. Cognito stores account identity (including email) separately from locally encrypted account tokens; Google and AWS process sign-in information, and no SMS message is sent to the account API. The Play Data Safety disclosure and public privacy policy must cover Google account information, RAM-only OpenRouter keys, and direct transmission of user-consented analyzed text to **OpenRouter and downstream TypeSafe** in signed-in mode, or **TypeSafe directly** for BYOK. No SMS body is sent to AWS. The Play edition still requires explicit consent for every manual analysis; no automatic SMS permissions or sending.

## Build and verify

Requirements: JDK 17, Android SDK 36, Gradle wrapper 9.8.2.

```bash
./gradlew :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug :app:lintPlayDebug :app:assemblePlayDebug :app:assembleSideloadDebug
./gradlew :app:bundlePlayRelease
./gradlew :app:connectedSideloadDebugAndroidTest :app:connectedPlayDebugAndroidTest
```

CI audits the built Play APK manifest using Android build tools and rejects `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` and related restricted SMS/Call Log permissions. Android instrumentation independently verifies installed permissions and local data deletion.

The **unsigned** Play bundle for CI compilation is `app/build/outputs/bundle/playRelease/app-play-release.aab`. Do not upload it to Play. Once the four `ANDROID_*` signing secrets are configured, a trusted main-branch workflow signs the Play bundle and uploads a `TextSentry-play-signed-aab` artifact for manual submission to Play Console. It also publishes a separately signed sideload APK to a GitHub prerelease. The `TYPESAFE_AI_API_KEY` CI secret is only for tests, not app credentials.

## Play Console owner checklist

1. Register or verify your Google Play developer identity and register the package `org.textsentry.app`, which becomes the permanent Play package name after publication. Confirm this package name before the first release.
2. Enable Play App Signing. Create/store a durable upload key; add the four Android signing secrets; use only the **play release AAB**.
3. Add a stable, public, non-editable privacy policy URL. `PRIVACY.md` is in the public repository, but consider enabling GitHub Pages with a polished policy URL. Ensure Play listing identity/contact information matches the privacy contact mechanism.
4. Complete the Data safety questionnaire honestly: user-submitted messages can leave the device for a provider they select; provider handling/retention vary. The Play build does **not** automatically access SMS or contacts and contains no advertising.
5. Complete app content declarations (age rating, target audience, ads, data safety). Provide screenshots/icon/feature graphic, a short and full listing description that **does not claim automatic SMS monitoring or response** in the Play edition.
6. Test on real devices through internal testing before submitting for production review. Test share-sheet import, encrypted key setup, consent, zero-SMS-permission audit, app-to-SMS compose, data erasure, and Android 16 edge-to-edge behavior.
7. Confirm developer account testing eligibility (some new personal developer accounts must complete a closed-test period) in Play Console.

**Not yet Play-approved:** The repo can produce a Play-oriented artifact and policy documentation, but only Google reviews and approves publication. The full automatic SMS experience is **not** available in the Play flavor until an approved restricted-permission pathway is built and reviewed.


### Local STOP exceptions (both flavors)

Users may explicitly protect a sender number or a suggested/editable keyword from History and manage encrypted local exceptions in Settings. Choosing a Jev suggestion is a separate, explicitly consented Jev Choice request that sends only the selected history message directly to the existing provider, never AWS; names are extracted locally and Jev chooses a candidate. Play retains manual-only behavior and new user consent is required for each suggestion. Exceptions only suppress automatic STOP in sideload; Play remains manual-only and gains no SMS/Call Log permissions, receivers, or background monitoring. Clear History retains rules; Delete app data erases them.


### Encrypted history retention and email compatibility

Both flavors now store message history in a device-only SQLite database with per-record AES-256-GCM content encryption. Confirmed Android sent replies are retained indefinitely until users clear data; all other history expires after 24 hours and is pruned on read/write plus best-effort WorkManager maintenance. Existing encrypted 100-record history migrates without network calls. History is paged to avoid loading an unbounded collection into the UI. The Play flavor still cannot send/confirm SMS, so all Play manual analyses expire after 24 hours; no restricted permissions are introduced. User-initiated support drafts now supply message text via both email intent extras and an encoded mailto body.
