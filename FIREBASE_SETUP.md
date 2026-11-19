# Firebase setup for Tabtaba 0.2.0

This release adds verified phone accounts, contact matching, a temporary message relay and push notifications. History and contact names stay on the phone. The downloadable build works locally before Firebase is configured; its online screen explains that setup is pending.

## 1. Create your Firebase project

1. Open https://console.firebase.google.com and create a project for Tabtaba.
2. Register an Android app with package name `com.tabtaba.app`.
3. Download `google-services.json` and put it in `app/google-services.json` in your local checkout. This file identifies your project. Keep service-account private keys outside the repository.
4. Add the signing certificate SHA-1 and SHA-256 for each APK used in testing and for the Play App Signing certificate used by Google Play. Find the latter in Play Console. Run `gradle :app:signingReport` for local debug certificates.
5. Add an administrator email and budget alerts. Budget alerts notify you about spending; they do not stop charges.

## 2. Configure authentication and billing

- Enable the Phone provider under Authentication > Sign-in method.
- Enable billing (Blaze) for production SMS and Cloud Functions. Start with Firebase fictional phone numbers and test codes to exercise verification without sending paid SMS. Never ship automatic verification bypasses or hard-coded test codes.
- Restrict the SMS region policy to countries in your launch. Start with UAE if that is your first market.
- Set the lowest SMS quota suitable for your pilot where Firebase permits it, and review Authentication usage regularly.
- Google Identity Platform currently lists the first ten SMS per day as unbilled, with UAE delivery at US$0.08 per SMS above that allowance. Check your project's current pricing and billing configuration. This is a limited allowance, and production SMS needs a billing-enabled project.
- Firebase Cloud Messaging has no per-message charge. Firestore and Functions have free allowances, while SMS, scheduler jobs, build artifacts and excess usage can incur charges. Default backend maximum instance counts and application rate limits reduce usage; they are not an account spending cap.

## 3. Set up the backend

1. Install Node.js 22 and the Firebase CLI on your computer.
2. In this repository run `firebase login`, then `firebase use --add` and select your project.
3. Create the default Cloud Firestore database. Select a suitable location before creating it. The included functions run in `us-central1`; if you choose another function region, change it in both `functions/index.js` and `Cloud.java`.
4. Run `npm install --prefix functions` and `npm test --prefix functions`.
5. Generate a random secret of at least 32 bytes and run `firebase functions:secrets:set DIRECTORY_HMAC_KEY`. Supply that secret when prompted. Keep it stable, since rotating it changes directory lookup keys.
6. Run `firebase deploy --only firestore:rules,functions`.
7. Keep the supplied Firestore rules, which reject all direct client reads and writes. Callable functions authorize users through verified Firebase phone identity and App Check.
8. Enable App Check for the Android app. Register Play Integrity for release builds. For Android Studio debug builds, register the App Check debug token shown in Logcat. Do not publish debug tokens.
9. For Play Integrity, configure the Firebase project and Play Console linkage and certificate according to Google's instructions. Test the release installed through a Play internal track. A sideloaded test APK may fail Play Integrity unless the provider is configured for your distribution.
10. Check deployment results for the hourly cleanup job. It deletes expired queues, deduplication receipts and rate records in bounded batches. Message access stops after seven days; physical deletion normally occurs at the next successful cleanup run. Monitor cleanup failures and backlog.

## 4. Build an online-enabled version

The Google Services plugin is applied only when `app/google-services.json` exists. Release artifacts generated without that file are setup builds and cannot register real users.

For GitHub builds, add the contents of this file to the repository Actions secret `GOOGLE_SERVICES_JSON`. The release workflow writes the configuration before compiling. Enable App Check, deploy the backend, then publish a new version.

Version `0.2.0` has Android `versionCode 3`. Before each subsequent release, increase both `versionName` and `versionCode` in `app/build.gradle`. The publish workflow reads these values and refuses to overwrite an existing release tag.

Keep a permanent test-signing keystore to install later test updates over an older APK. Optional Actions secrets are `TEST_KEYSTORE_BASE64`, `TEST_KEYSTORE_PASSWORD`, and `TEST_KEY_ALIAS`. Without them, the build uses an ephemeral test key, so a later APK can require uninstalling the old one. Export local history before uninstalling. The AAB remains unsigned until you apply your Google Play upload signature.

## 5. Verify on two phones

- Register two different Firebase fictional phone accounts on debug builds with registered App Check debug tokens.
- Verify real SMS delivery separately to a small number of allowed pilot numbers.
- Give contact access on one phone and find the other. Contact names must remain local; the backend only receives normalized numbers for matching.
- Send a preset and a three-word custom message. Send a comfort request to several users; each should see only their own incoming request.
- Open local history on the recipient, then inspect Firestore: received queue records should disappear after local receipt, while local history remains.
- Put the recipient Offline, send a message, then switch Online and refresh. Check deferred delivery, notification permission denial, killed-app delivery and duplicate push delivery.
- Disable online visibility and confirm the sender sees no status. The status is a user-selected availability preference; it does not claim the app is currently connected.
- Export and restore an encrypted backup. A backup contains local history, settings and pending messages, and excludes Firebase login tokens. Reverify the same account after restoring on a new phone.
- Sign out and reverify before deleting an account if the sign-in is older than five minutes. Verify directory, profile and queued messages are removed. Local copies and exported backups can be removed separately.
- Check Cloud Functions logs and request quotas. Validate account isolation and send retries before wider distribution.

## Data handling

The backend holds Firebase Auth accounts, a keyed phone-number directory, device tokens, availability preferences, temporary queued messages and bounded anti-abuse records. It does not receive contact names or upload existing local history. Messages are protected in transit but the server can read queued content. End-to-end encryption has not been implemented.

The most recently registered receiving device is used for an account. Firebase may process phone numbers for authentication and abuse prevention. Include that processing, contact matching, push delivery, retention and manual backup in your public privacy policy and Play Console Data safety disclosures. Account deletion is available in the app; also provide a public account-deletion request page before a Play release with accounts.

## Sources

- https://firebase.google.com/docs/auth/android/phone-auth
- https://firebase.google.com/docs/auth/limits
- https://cloud.google.com/identity-platform/pricing
- https://firebase.google.com/docs/app-check/android/play-integrity-provider
- https://firebase.google.com/docs/functions/get-started
- https://firebase.google.com/docs/firestore/pricing
