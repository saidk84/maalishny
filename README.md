# طبطبة | Tabtaba

A local-first Android prototype for sharing short messages of comfort. The app runs on Android 8 or newer.

## Build and run

Open this repository in Android Studio with JDK 17 and Android SDK 35. Select a phone or emulator and run the app module. From a terminal with Gradle 8.9 installed, run `gradle :app:assembleDebug`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

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
