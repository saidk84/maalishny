# طبطبة | Tabtaba

A local-first Android prototype for sharing short messages of comfort. The app runs on Android 8 or newer.

## Build and run

Open this repository in Android Studio with JDK 17 and Android SDK 36. Select a phone or emulator and run the app module. From a terminal with Gradle 8.13 installed, run `gradle :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Using the prototype

1. Open **Settings**, enter a local display name and mobile number, and save.
2. Use **Send comfort** or **Ask for comfort**. Custom comfort messages allow one to three whitespace-separated words.
3. With **Online** selected, **Save and share** records the message locally and opens Android’s share chooser. Select the actual recipient in the destination app. History records prepared messages, including messages from a canceled share.
4. With **Offline** selected, messages can still be saved locally. The toggle changes sharing behavior on this phone and is never broadcast as presence.
5. In **Settings**, choose **Save encrypted backup**. Select Google Drive if the Android document picker offers it. Otherwise save the file locally and upload it to your Google account using the Drive app. Use at least 12 characters for the backup password and keep it separately.
6. To restore, select the backup in **Restore encrypted backup**. Restoration replaces the local profile, settings, and entire history after the password and file contents are validated.

## Storage and delivery

The profile, settings, and history live in a private file in the app’s internal Android storage. Android automatic backup is disabled for the app. Manual backups include the full local dataset and are protected with AES-256-GCM using a key derived from the chosen password. The app keeps at most 16 MiB of local records and displays the latest 100 in its history view. The local file relies on Android’s app sandbox and device protection. It is separate from the encrypted export.

This version uses other apps for delivery. It has no shared phone registration, contact matching, in-app receiving, push notifications, or synchronized presence. Google backup is a manual encrypted file workflow. Automatic scheduled Google Drive backup would require a separate integration.

## Device checks

- Save a profile and verify it survives force-stop and relaunch.
- Save and share an Arabic preset and a three-word custom message. Check that a fourth word is rejected.
- Switch Offline and confirm the share chooser no longer opens.
- Export a backup, change the profile and add a message, then restore the saved backup. Check that the previous profile and history return.
- Try a wrong password and a damaged file. Verify that the existing data remains intact.

## Google Play release

Run `gradle :app:lintRelease :app:bundleRelease` with JDK 17 and SDK 36. GitHub Actions runs both tasks and validates the bundle with bundletool. Download the unsigned AAB and save the separate R8 mapping file for this version. The release targets API 36, enables R8 and resource shrinking, and currently contains Java code with no native libraries.

Sign the AAB with your Play upload key. For a first release, create a dedicated upload key and enroll in Play App Signing. For an existing Play listing, use its registered upload key and confirm that `versionCode` exceeds the previous release. Keep the keystore and passwords outside the repository. Sign and verify locally:

```sh
jarsigner -keystore /path/to/upload-key.jks -signedjar Tabtaba-release-signed.aab app-release.aab upload
jarsigner -verify -verbose -certs Tabtaba-release-signed.aab
```

Before submitting:

- Publish an app-specific public privacy policy with a publisher contact, describing local storage, user-initiated sharing, backup/export and deletion. Add its URL in Play Console. The app includes a Privacy and data screen.
- Complete Data safety, the Health apps declaration, content rating, target audience, ads and app access declarations. Base answers on the actual app: local profile and history, Android sharing and manual backup, without a Tabtaba account service. Describe emotional support without making treatment claims.
- Add the required listing graphics and screenshots. Describe manual sharing and manual backup accurately.
- Upload the signed AAB to internal testing. Exercise sharing, offline mode, and backup/restore in the optimized release; review the pre-launch report and test Android 16. If the Play Console account is subject to the new personal-account testing requirement, run a closed test with at least 12 testers opted in continuously for at least 14 days before applying for production access.

See [Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878), [Play App Signing](https://developer.android.com/studio/publish/app-signing), and [production testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).
