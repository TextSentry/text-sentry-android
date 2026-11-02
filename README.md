# TextSentry

**Less noise. More signal.**

TextSentry is an Android message assistant powered by **TypeSafe Jev System One**. New users sign in with Google to use a monthly $1-capped OpenRouter key automatically; or they can skip registration and bring their own TypeSafe API key (BYOK). Both modes use the same single Jev yes/no decision, selected message categories and 50–100% threshold (default **95%**). The only automated reply is the fixed **STOP**.

## License

TextSentry Android source code is **source-available for noncommercial use** under the [PolyForm Noncommercial License 1.0.0](LICENSE). You may use, study, modify, and redistribute the code for permitted noncommercial purposes, including personal and hobby use. **Commercial use requires a separate written license from the applicable copyright holder.** This is not an OSI-approved open-source license. Third-party components retain their own licenses; this grant does not grant TextSentry trademark rights or imply endorsement.

## TextSentry account (Google sign-in)

The Play and sideload editions now offer Google sign-in / sign-up through AWS Cognito. First-time Google users create an account and returning users sign in through the same browser-based authorization-code + S256 PKCE flow. The app loads public Cognito configuration from `https://api.textsentry.org/v1/auth/config`, then keeps Cognito access/ID/refresh tokens encrypted locally with a separate Android Keystore AES-GCM key. Sign-out erases the local session, as does Delete all app data. After sign-in (or after reopening the Account screen with a saved session), the app refreshes Cognito tokens if necessary and retrieves the active-month OpenRouter key through `GET https://api.textsentry.org/v1/key`. The key remains **in process RAM only**; the Account screen reports readiness without displaying or storing the key. A Refresh managed key action is available; sign-out and data deletion clear memory.

**Personal key acknowledgment:** Selecting a personal TypeSafe key first opens an explicit provider-responsibility dialog. The user must agree before the key-entry UI appears; cancel returns to sign-in. Replacing a key in Settings requires the same acknowledgment before saving. TextSentry's managed ZDR safeguard does not apply to independently provided keys; provider terms, retention, and charges belong to that user. This responsibility notice is intentionally in the app, not the public website.

**Required setup:** The launcher opens a minimal native setup screen. **Continue with Google** is the primary action; a small **Skip sign-up · Bring your own key** link expands a secure API key field. No Home, Settings, History, or developer screens are accessible until a Google session or BYOK key has been saved. This applies after sign-out and local-data removal too, even on older installs where the previous optional welcome was dismissed. Existing configured users skip setup. Play shared text is preserved in memory while completing setup. **Unified analysis:** If signed in, Play manual review, sideload sample tests and sideload monitoring all use Jev over **OpenRouter Decisions API** with the monthly managed key. If no account exists and a BYOK key has been saved, they use Jev over the direct **TypeSafe System One** endpoint. Both flows use the exact same rules, threshold, local safety checks and fixed STOP reply. An expired RAM-only key is automatically retrieved using the encrypted Cognito session from AWS; the managed key itself is never saved to device storage. If signed in but key fetch fails, no automatic fallback sends any credential to a different provider. Sign-in does not enable monitoring or auto-replies. No SMS contents reach TextSentry AWS.

Two distributions are maintained:

- **Play flavor:** A manual assistant for pasted/shared text. It requires explicit per-analysis consent, never monitors incoming SMS, never sends SMS automatically and holds no restricted SMS permissions. The Play flavor is **not yet approved or published**.
- **Sideload flavor:** An experimental tool to monitor eligible incoming SMS and optionally send STOP when local safeguards and the user-selected threshold pass. Monitoring and automatic replies remain **off by default**, with independent permission/consent checks and a one-tap pause.

## Guided first-run setup

Both editions begin with a branded Google sign-in or optional on-device TypeSafe BYOK. Until the wizard is completed, Home, Settings, History and developer tools are inaccessible (including on upgrades from earlier releases). The compact navy header uses the shared TextSentry shield and wordmark, with website-matched type, cyan accent, and the same "Stop sorting through spam" messaging.

**Experimental sideload:** (1) sign in or BYOK, (2) request incoming SMS access, with outgoing SMS optional, (3) follow the large on-screen battery guide: **Open TextSentry app settings → App battery usage (or Battery) → Unrestricted**, then return to TextSentry, and (4) **Start monitoring**. The final action starts **preview-only**, never sends automatic replies. Unrestricted battery is recommended but not required; it does not grant SMS permissions. When the installer hard-restricts SMS access, Android can refuse the grant; the wizard offers app-info and troubleshooting, and users may finish without monitoring. No SMS text goes to TextSentry AWS.

**Google Play:** (1) sign in or BYOK, (2) start manually reviewing shared/pasted texts. No SMS runtime permissions, background monitoring or auto-reply controls exist in Play flavor.

Existing users need to complete the guide once after this release; credentials, message history, categories, confidence and existing BYOK settings remain intact. Resetting app data also resets onboarding.

## App experience

Home keeps monitoring controls and sample analysis up front (sideload), or manual analysis and optional SMS compose (Play). Account, history, and settings are simple secondary actions; detailed rules and privacy controls remain available in Settings.

### Message categories

The Settings page provides three independent checkboxes:

- **Go vote reminders** — unsolicited nonpartisan get-out-the-vote reminders.
- **Political campaign messages** — unsolicited candidate, political party, fundraising and campaign appeals.
- **Marketing ads** — unsolicited commercial promotions, coupons and advertisements.

All three categories are checked by default for new installations and for users who have not saved a selection. Explicitly saved choices, including unchecked categories, remain unchanged. Selected categories are combined using an **OR** into ONE Jev yes/no classification request per eligible, nonduplicate SMS, with the optional extra restrictions appended to that same instruction. There are no separate requests for each checked category. With none selected, analysis is skipped entirely.

Jev now judges the message's purpose and category even if it does **not** contain a STOP or unsubscribe instruction. A message matching any enabled category can qualify at the configured confidence threshold. Official voter registration/polling place notices, personal texts, OTPs, and ambiguous texts remain excluded. This is opt-out filtering, not political messaging or voter outreach; no voter information is deleted and TextSentry never sends election content to others. A false positive may still occur, so use preview mode before allowing automatic replies. A separate **Settings** screen provides encrypted API-key entry/removal, category checkboxes, confidence slider and privacy/data controls. Advanced extra prompt editing is available only in hidden developer tools. Changing the policy invalidates queued SMS work, and upgrading from a legacy chat provider clears the old credential before any TypeSafe call.

### Hidden developer mode

In **Settings**, tap the **AI connection** heading **seven times**, then confirm the privacy warning. A new **Developer mode** card links to **Developer tools** in both app editions. The developer page lets you write, save or clear optional **Additional prompt** text; it is appended to the selected category instructions in the **same Jev request**. Developer text cannot enable unchecked categories, change the fixed STOP reply or override local safety checks. Existing saved extra instructions remain active if developer mode is switched off. Changes invalidate queued processing using older rules.

While enabled, developer mode captures the **five most recent TypeSafe request JSON bodies and raw response JSON bodies** for sideload SMS, sideload sample tests, and consented Play manual analysis. The trace is encrypted locally with the history Keystore key; it excludes the API key and Authorization header. Request JSON includes SMS text and filtering rules. The developer page offers refresh, a clear-traces action and a disable action. Disabling stops recording but does not delete existing traces; clearing history or app data also erases traces. No extra network requests or backend uploads are performed.

### Message history and Android permissions

**Experimental automatic MMS (sideload only):** Home → **Automatic picture-message filtering** → **Enable automatic MMS filtering**. This is a separate, initially disabled opt-in requiring Android's hard-restricted `READ_SMS` permission (the installer must allowlist it). TextSentry never becomes the default SMS app: Google Messages or the chosen SMS app must download carrier MMS into Android's system MMS inbox first. TextSentry watches those *new* received MMS records when alive, listens for WAP push hints, and also checks them with a 15-minute WorkManager fallback. It combines plain-text parts and on-device ML Kit Latin-script OCR from up to two image attachments into one bounded Jev text analysis, respecting existing selected categories, model, threshold, encrypted local history, 24-hour deduplication and separately enabled STOP setting. Image bytes are **never uploaded**. Group MMS messages are never auto-replied to, and MMS is never deleted. Old MMS are deliberately excluded at the moment of opt-in. The SMS path is unchanged.

**Limits:** This is not a full carrier-independent MMS transport implementation: no download from MMSC, no guaranteed real-time delivery, no video/audio analysis, no non-Latin OCR, and no RCS access. If the current messaging app does not put a completed MMS in the Android provider, or the installer cannot grant `READ_SMS`, it cannot be monitored. Carrier/device field verification remains necessary before claiming reliable automatic MMS coverage.

The new **History** page is linked from Home and Settings. It includes each evaluated SMS's sender, full text, time, TypeSafe Jev yes probability, recommended fixed reply, and whether Android confirmed the SMS send, it was only previewed, it failed, or no reply was recommended. Results are kept in an **AES-256-GCM encrypted**, Keystore-backed, app-private archive of at most 100 entries (newest first). Clearing history wipes the archive and the previous short decision log. Full app data erasure also destroys the history encryption key. These are local records and cannot reconstruct messages evaluated before this update.

Sideload permissions: **RECEIVE_SMS** is requested for monitoring; **SEND_SMS** is requested for optional auto-replies. Home shows live permission statuses, request buttons, a link to Android's TextSentry app-info page, and a troubleshooting dialog. Granting SMS-send permission never enables auto-replies without separate consent. Neither the app's own processed-message history nor send-status callbacks require **READ_SMS**. The Play flavor does not request SMS/Call Log permissions; it tracks only user-consented manual analyses, clearly labeled as unverified suggestions, not sent SMS.

#### Set up SMS access (sideload APK)

1. Open **TextSentry > Home > Permissions** and tap **Allow incoming SMS**. Allow it if Android offers a dialog. To use automatic replies, separately tap **Request outgoing SMS access** and then **Enable auto-replies** after previewing first.
2. If Android does not grant access, tap **Open TextSentry app info**, then **Permissions > SMS > Allow** if that option is available. Return to the app; the statuses refresh automatically. Android menus vary across manufacturers.
3. If SMS is unavailable, permanently denied, or a request closes without a grant, tap **SMS permission troubleshooting**. Android classifies `RECEIVE_SMS` and `SEND_SMS` as **hard-restricted** permissions. The installer or an eligible system role must allowlist them; an ordinary runtime prompt cannot override this. The user cannot manually add an installer allowlist entry. A compatible/authorized installation method may be needed. TextSentry is **not** a full default SMS app, so it does not offer a bogus SMS-role switch as a shortcut.
4. For SMS received successfully but analyzed late, optionally open **Battery / App battery usage** from TextSentry's Android app-info page and select **Unrestricted** (if supported). The home screen also links to Android's battery optimization settings and reports whether Android considers TextSentry exempt. **Unrestricted is not an SMS permission**, does not fix a missing grant, and can consume more battery. Some manufacturers provide additional battery management controls.

On Android 13+, the overflow menu entry **Allow restricted settings** is intended for restricted special settings (such as accessibility or notification listener access); **it is not an installer allowlist for restricted SMS permissions**. Do not enable unrelated sensitive access as a workaround. If SMS grants are blocked by the installation path, TextSentry cannot programmatically bypass the restriction.

**Message deletion:** Each history row says **Not deleted by TextSentry** for monitored SMS, or **Not applicable** for manual analysis. These statuses describe the app's action, not whether another SMS app has subsequently deleted the original message. Clear History and full data reset erase TextSentry records, **not original SMS in Android Messages**. Android permits writing/deleting from the SMS provider only for the device's chosen **default SMS app**. TextSentry is not a complete SMS/MMS replacement and does not claim that role or request unnecessary inbox privileges. See issue #15 for the full default-SMS-handler work needed to enable optional original-message deletion.

**Privacy:** An eligible incoming SMS in the sideload flavor or text explicitly consented in Play is transmitted directly to **TypeSafe** with BYOK or **OpenRouter/TypeSafe** with a managed key, subject to those providers' privacy/retention terms. The app uses a TextSentry AWS account/key-management backend, but never sends SMS text to it. There is no advertising SDK or analytics. Encrypted API keys live on the phone. WorkManager can temporarily store queued SMS data on-device; the encrypted, bounded local history includes full processed message text, sender, match probability and reply status.

**Safety:** Local rules reject OTPs, short codes, common automated stop/quit responses and dangerous or oversized messages before an AI request. Duplicate messages are suppressed for 24 hours and auto-replies are limited to one per sender per day. False positive STOP replies can confirm a number to malicious senders, so keep preview mode on while evaluating.

## Development

Requires Java 17, Android SDK API 36, Gradle wrapper 9.8.2 and Android Gradle Plugin 9.5.0.

```bash
./gradlew :app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:lintSideloadDebug :app:lintPlayDebug :app:assembleSideloadDebug :app:assemblePlayDebug :app:bundlePlayRelease
./gradlew :app:connectedSideloadDebugAndroidTest :app:connectedPlayDebugAndroidTest
```

GitHub Actions uploads the **TextSentry-sideload-debug-apk** and **TextSentry-play-preview-apk** artifacts after successful quality checks. Automated CI runs offline unit tests, Android lint, instrumented tests on both flavors, a Play restricted-permission audit and a trusted live TypeSafe test on main.

CI's `TYPESAFE_AI_API_KEY` is for synthetic integration tests only. It is never embedded in the app. Users must enter their own key.

For Google Play release preparation, see [PLAY_RELEASE.md](PLAY_RELEASE.md), [PRIVACY.md](PRIVACY.md), and [AGENTS.md](AGENTS.md).

## RevenueCat Web Purchase Link (sideload preparation)

The independent sideload flavor has an identified-user checkout integration using the RevenueCat Web Purchase Link `https://pay.rev.cat/mimrlvpobvqnjikw/`. The checkout URL must **never** be opened directly as a generic shared link: the app gets the Cognito `sub` from its authenticated `GET /v1/me` response and URL-encodes it into `https://pay.rev.cat/mimrlvpobvqnjikw/<cognito-sub>`. Checkout opens in the device browser and does not receive Cognito bearer tokens or SMS. The product is `textsentry_monthly`, with entitlement `textsentry`.

**Not live yet.** To prevent taking money before the backend validates paid entitlements, the checkout button is omitted from ordinary release builds.

### Private sandbox checkout test build

The product owner has supplied a separate RevenueCat **sandbox** Purchase Link. Keep the token out of source control, public downloads, release notes and customer messages; it can grant test subscriptions without real payment. Store the **entire URL**, ending in `/`, in the GitHub Actions repository secret `TEXTSENTRY_REVENUECAT_SANDBOX_URL` (Settings → Secrets and variables → Actions). Also configure the existing `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` signing secrets.

Run **Actions → Private RevenueCat sandbox APK → Run workflow** on `main`. The manual workflow rejects non-sandbox checkout URLs, enables checkout only in its sideload build, runs offline tests and Android lint, builds and verifies a signed release APK, and uploads the `TextSentry-SANDBOX-checkout-DO-NOT-DISTRIBUTE` artifact for **seven days**. When this workflow file is changed on `main`, GitHub also builds a replacement artifact automatically, without a manual dispatch; unrelated pushes do not produce sandbox APKs. Downloads require signing into GitHub and choosing the artifact link on the workflow run's Summary page. It never creates a GitHub Release or modifies the regular production build.

Install the artifact only on a controlled test device. It shares the standard `org.textsentry.app` package ID and signing key, so it **replaces** the usual sideload edition rather than coexisting. Sign in with Google, open **Account → Subscribe to TextSentry**, complete checkout in your browser using Stripe's test card details, and return to the app. In the RevenueCat dashboard, search for the same Cognito `sub` displayed by TextSentry's authenticated `GET /v1/me`; verify product `textsentry_monthly` grants entitlement `textsentry`, and test cancellation/expiration.

**Billing verification caveat:** Production Git sync requests paid enforcement (`EnforceSubscriptions: 'true'`), but successful GitHub CI does not prove the AWS deployment. Confirm the stack parameter and RevenueCat live entitlement; an earlier beta API may still grant access without subscription. The app does not currently confirm a successful payment on its own, and a real subscriber should not be charged on this test build.

 Set `TEXTSENTRY_ENABLE_WEB_CHECKOUT=true` **at build time** for controlled sideload testing only. To run a sandbox purchase, set `TEXTSENTRY_WEB_CHECKOUT_URL` to the exact separate RevenueCat sandbox URL during the test build. The build rejects URLs outside `pay.rev.cat`; the Play flavor always excludes this web-checkout button. Signed releases built by normal main CI remain disabled until checkout-to-entitlement verification is complete. Sandbox purchases require RevenueCat's **separate sandbox URL**, which must not be distributed publicly. The provided link has not been independently verified as production or sandbox.

Before activating customer checkout: verify RevenueCat/Stripe live setup and the separate sandbox checkout end-to-end, verify subscription `textsentry` with the same Cognito `sub`, verify AWS RevenueCat secret and key issuance, and then deliberately enable `EnforceSubscriptions: 'true'` in IaC. Until then the backend beta setting bypasses subscription checks. Do not interpret a successful key fetch while enforcement is disabled as proof of payment. BYOK remains independent and free. Do not expose a website-wide generic purchase link that lacks a signed-in user identity.

## Subscription status and return to app

RevenueCat Web Purchase Links **do not automatically return to Android** using the default success page. In the RevenueCat dashboard for the TextSentry Purchase Link, select **Funnels → Purchase Links → TextSentry checkout → Success behavior → Redirect to a custom success page**, and use the exact redirect **`textsentry://billing/return`**. For returning customers choose **Show the success page**. Both production and sandbox links for that purchase-link configuration use the configured success behavior. This is a **RevenueCat dashboard change**; Android cannot override it just by appending query parameters.

The sideload APK alone registers the exact custom-scheme return. Cognito sign-in still uses `textsentry://auth/callback` via AppAuth. On browser return, or when a customer manually comes back to the app, **Account** refreshes `GET /v1/me` with Cognito authentication and shows **TextSentry subscription active**, **Not subscribed**, or **Subscription status unavailable**. Sandbox test builds label an active subscription as **(sandbox)**. Deep-link query parameters (including `app_user_id`) are **never** proof of payment. Android does not enable managed keys based on the checkout return.

The corresponding `text-sentry-iac` Lambda update must be deployed via CloudFormation Git sync, and the RevenueCat secret API key must be configured in AWS Secrets Manager. Prior versions of the API have no `subscription_status` field; the app deliberately shows *unavailable* rather than guessing. Production Git sync requests `EnforceSubscriptions: 'true'`; confirm the actual deployed API and RevenueCat entitlement before assuming verification is live.

## Diagnosing a sandbox "Not subscribed" message

RevenueCat Billing sandbox monthly subscriptions renew around every **five minutes** and end automatically after at most **six renewals** (around 30–35 minutes). These are not real monthly billing intervals. Android's Account screen uses the authenticated `GET /v1/me` response to distinguish **Sandbox subscription expired**, **No purchase found for this account**, and **Purchase is missing the textsentry entitlement**. When available, it shows the selectable `RevenueCat customer ID` (the same Cognito `sub` appended to the Purchase Link). Compare this ID with the customer in RevenueCat. An unrelated customer ID or a secret from the wrong RevenueCat project may cause an apparently empty subscriber because the V1 lookup creates unknown customers.

`subscription_status: unavailable` means verification failed, not an inactive purchase. A separate user-facing status or link alone must never grant access. When diagnosing, avoid a new sandbox purchase until checking the current RevenueCat customer's expiration timestamp and entitlement. The AWS CloudFormation Git-sync deployment must contain the matching diagnostic API changes, and the RevenueCat V1 project secret must be correct. Do not use sandbox receipts as proof of production access.

## Active subscription handoff and monthly-key limit

During first-run Google setup, **Check subscription and continue** opens the signed-in account screen. When authenticated `GET /v1/me` confirms an active **live** RevenueCat entitlement and `managed_access: true`, TextSentry automatically calls authenticated `GET /v1/key` to retrieve the existing current-month key (or provision that month's first key), installs it **only in RAM**, completes the optional billing prompt, and returns to the SMS-permissions step. A purchase redirect, sandbox purchase, or a stale/beta-only status cannot trigger this handoff. An unavailable key leaves the account screen open with a retry action; it never falsely reports protection as active.

**HTTP 429 is not necessarily a spend-limit error.** The backend specifically returns `key_revoked_until_next_month` if an already-issued current-month managed key was later revoked. Because the backend deleted that key's plaintext and must never create a second $1/month key, the app cannot recover it with a login or another purchase. It now explains the condition, and the user may explicitly continue to configure SMS permissions **without a working managed key**, or bring their own key. Real AI analysis still needs a valid credential. A generic 429 means temporary throttling instead. Production support must investigate the revocation cause, without bypassing the budget cap.

## Guided sideload setup and restricted permissions

Sideload first-run wizard now follows **Google sign-in → subscription options → SMS permissions → battery access → start monitoring**. The Google account route presents a dedicated subscription step before permissions; the BYOK route skips subscription. The Play-safe manual edition does not show sideload web billing.

On the SMS-permissions screen, **Android permission settings** is the first action. For sideloaded APKs Android may restrict access to sensitive permissions: open **App info → three-dot menu → Allow restricted settings** (when present), return to TextSentry, then explicitly allow incoming SMS and optional replies. The **Open app battery settings** step follows SMS permissions, and guides the user to **Battery → Unrestricted** if supported by the device. Restricting battery optimizations is separate from SMS permissions and remains optional.

Account shows the subscription status and a subscription section before account controls. In a checkout-enabled sandbox or private production-candidate build, **Subscribe to TextSentry** opens the relevant RevenueCat hosted purchase link using the Cognito user ID. In a checkout-disabled public prerelease, Account explicitly says checkout is unavailable in that build rather than hiding the option; installing the signed subscription-enabled sideload candidate preserves the package ID and local history. Browser return alone never marks a purchase paid. Production billing enforcement has been requested in Git sync; verify the live AWS rollout before public claims.

## Production checkout rollout (staged)

The private **production checkout candidate** workflow builds a **signed sideload APK** with `TEXTSENTRY_ENABLE_WEB_CHECKOUT=true` and the provided production Purchase Link `https://pay.rev.cat/mimrlvpobvqnjikw/`. It never publishes a GitHub Release, cannot be triggered on a PR or non-main branch, and uploads a seven-day `TextSentry-PRODUCTION-checkout-CANDIDATE-NOT-PUBLIC` artifact. It is intentionally **real-payment capable**; do not distribute it broadly or complete a real purchase until the live Stripe/RevenueCat configuration and charge are explicitly accepted. The current sandbox workflow remains separate and never creates production purchases.

**Live subscription rollout enabled:** the signed sideload prerelease workflow now builds with `TEXTSENTRY_ENABLE_WEB_CHECKOUT=true` and the fixed live RevenueCat purchase link. This is a real-payment-capable build: checkout may charge a customer and create a recurring subscription. The matching production CloudFormation deployment requests `EnforceSubscriptions: 'true'`. Always verify AWS Git sync and an actual live entitlement before assuming the server is enforcing paid access. The Play edition always has `WEB_CHECKOUT_ENABLED=false`, no sideload billing intent and no restricted SMS permissions. Do not use sandbox checkout links in normal signed releases.

**Post-merge live billing verification and release checklist:**
1. In RevenueCat, confirm the production Purchase Link uses a live Stripe account, the desired live price for `textsentry_monthly` and associated `textsentry` entitlement, the `textsentry://billing/return` success redirect, and working billing receipts and customer portal management. RevenueCat Billing's customer portal appears in purchase/renewal emails.
2. Install the private signed production candidate. Sign in with a controlled Cognito account; the checkout App User ID is the verified Cognito `sub`. Only complete a real purchase with explicit authorization for the charge. Confirm `GET /v1/me` reports `subscription_status=active`, `subscription_sandbox=false` and that the product grants `textsentry`. An active sandbox purchase alone does not count as paid.
3. Test cancellation through the RevenueCat Customer Portal: entitlement remains active until the paid-through date and expires afterward. Test expiry/refund separately using an appropriately controlled RevenueCat account; do not shorten a real customer's paid access. Confirm the AWS key is not reissued and EventBridge reconciliation eventually disables remotely issued keys.
4. Verify the updated AWS CloudFormation inline Lambda has actually deployed. The production Git sync now requests `EnforceSubscriptions: 'true'`; verify the **deployed** stack reports it before claiming the server enforces paid subscriptions. Existing free-beta managed-key users will lose access and need to subscribe or choose BYOK. BYOK users are not subject to the managed-key entitlement.

Android APK version codes now use the repository-wide GitHub Actions run ID (scaled to fit Android), rather than the independent workflow run numbers. This prevents switching from sandbox to production candidate from being rejected as a version downgrade. Signing keys and `org.textsentry.app` stay the same, so candidates install as in-place updates, not parallel apps.

## Signing and release

Configure GitHub Actions repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` to enable optional signed release publishing on main. Until configured, the signed-release job skips publication while debug APK artifacts still work. Protect main with required quality and emulator checks; merely defining workflows is not branch protection.

## Launcher branding

Both Android editions use the official [TextSentry shared-brand icon](https://github.com/TextSentry/test-sentry/blob/main/assets/textsentry-icon.svg): a blue-to-purple security shield, white messaging bubble and red block symbol on dark navy. The adaptive launcher icon is recreated as Android vector drawables (foreground, gradient background and a monochrome version for themed launchers), so Android can mask and scale it cleanly. The icon source of truth remains the shared branding repository.
