# طبطبة | Tabtaba

Tabtaba is an Android app for exchanging short messages of comfort with people you know.

Current version: **0.2.1** — Android versionCode **4**. Each future release must increase both values in `app/build.gradle`. Tagged releases are preserved.

## Download

[APK, unsigned AAB and R8 mapping](https://github.com/saidk84/maalishny/releases/latest)

The APK uses a test signing key for sideloading. Keep a backup before replacing an installation signed by a different key. Sign the AAB with your Google Play upload key before submission.

## Features

- Local profile, settings and message history, including incoming messages and pending outgoing messages.
- Country selection, international phone number formatting and Firebase SMS verification when Firebase is configured.
- Contact permission prompt and matching by normalized phone number. Contact names remain on the device.
- Private direct comfort messages and requests for up to ten selected contacts.
- Arabic/English presets and custom comfort messages limited to three words and 160 characters.
- Offline preference pauses direct sending and receipt. Optional visibility shows this preference to contacts, rather than claiming a live connection.
- Temporary server queue, local-save-before-acknowledgement and deduplication of incoming deliveries.
- Firebase push notifications, with discreet text and an Android notification channel for sound control.
- Manual encrypted export and restore through Android's file picker, including Google Drive when available.
- In-app online account deletion and a separate option to clear local data.

## Firebase setup

**[Follow FIREBASE_SETUP.md](FIREBASE_SETUP.md).** Firebase project setup is required before phone verification, discovery, direct delivery and push can work. Without `app/google-services.json`, the downloaded app supports local history, manual sharing and backup, and explains the pending online setup. This release has not been live-tested against an owner-configured Firebase project.

Use Firebase test phone numbers for initial testing. Real SMS requires billing. Cloud Functions also requires Blaze, with no-cost usage allowances. Read the setup guide for region restrictions, App Check, budget alerts, quotas and rollout checks.

Accounts and device routing settings live on Firebase. Incoming message content waits on the server until the phone saves it and sends an acknowledgement. Undelivered messages expire after seven days, with physical deletion by the hourly cleanup job. End-to-end encryption is not implemented. Only the latest registered receiving device is used for each account.

Local storage uses Android's private app files and atomic writes. Exported backups are password protected with AES-256-GCM. Backup restores replace local data. Firebase login tokens are excluded, and restoring on a different phone requires verification of the same account.

## Build

Use JDK 17, Gradle 8.13 and Android SDK 36. Open in Android Studio or run:

```sh
gradle :app:lintRelease :app:bundleRelease
node --test functions/validation.test.js
```

Release builds enable R8 and resource shrinking. The publish workflow validates the AAB, derives an installable APK from the same optimized bundle, and publishes the matching mapping file. It supports the `GOOGLE_SERVICES_JSON` repository secret for configured builds.

## Google Play

The app targets API 36. Sign the AAB with your upload key and enroll in Play App Signing. Complete internal testing and the pre-launch report, publish an app-specific privacy policy and public account-deletion request page, and complete Data safety, Health apps, content rating, target audience, ads and app access declarations. Describe the features actually enabled in your release. Personal developer accounts subject to Google's testing policy must meet the required closed-test period before production access.

See [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878), [Play App Signing](https://developer.android.com/studio/publish/app-signing) and [testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).

## Release history

See [CHANGELOG.md](CHANGELOG.md).
