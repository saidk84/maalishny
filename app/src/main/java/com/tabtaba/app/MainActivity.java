package com.tabtaba.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

public final class MainActivity extends Activity {
    private static final int EXPORT = 1;
    private static final int IMPORT = 2;
    private static final int PLUM = Color.rgb(83, 23, 60);
    private static final int CREAM = Color.rgb(255, 248, 239);
    private static final String BACKUP_TYPE = "application/octet-stream";
    private LinearLayout page;
    private LocalData store;
    private boolean home;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            store = new LocalData(this);
            showHome();
        } catch (Exception failure) {
            page("طبطبة | Tabtaba", true);
            label("Tabtaba couldn’t open your local data.", 18);
            button("Try again", this::recreate);
        }
    }

    private void page(String title, boolean isHome) {
        home = isHome;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CREAM);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                    insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);
        label(title, 30).setTypeface(null, Typeface.BOLD);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(PLUM);
        view.setPadding(0, dp(10), 0, dp(10));
        page.addView(view);
        return view;
    }

    private void button(String text, Runnable action) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setMinHeight(dp(48));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(PLUM);
        shape.setCornerRadius(dp(16));
        view.setBackground(shape);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(6), 0, dp(6));
        page.addView(view, layout);
        view.setOnClickListener(v -> action.run());
    }

    private EditText input(String hint, String value, int type) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(type);
        field.setText(value);
        page.addView(field, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private void showHome() {
        page("طبطبة | Tabtaba", true);
        boolean online = store.data.optBoolean("online", true);
        String name = store.data.optString("name");
        if (!name.isEmpty()) label(name, 20);
        label(online ? "Online on this device" : "Offline on this device", 15);
        button("Send comfort", () -> compose(false));
        button("Ask for comfort · معلشني", () -> compose(true));
        button("Message history", this::history);
        button("Settings", this::settings);
    }

    private void settings() {
        page("Settings", false);
        EditText name = input("Display name", store.data.optString("name"),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone = input("Mobile number", store.data.optString("phone"),
            InputType.TYPE_CLASS_PHONE);
        Switch online = new Switch(this);
        online.setText("Online");
        online.setChecked(store.data.optBoolean("online", true));
        page.addView(online);
        label("Offline disables sharing on this phone. Your history stays available.", 15);
        button("Save settings", () -> {
            try {
                JSONObject next = store.copy();
                next.put("name", name.getText().toString().trim());
                next.put("phone", phone.getText().toString().trim());
                next.put("online", online.isChecked());
                store.save(next);
                showHome();
            } catch (Exception failure) {
                notice("Settings couldn’t be saved.");
            }
        });
        label("Use your Google account", 20);
        label("Save a password-protected backup to Google Drive from the file picker when "
            + "Drive is available. You can also save it on your phone and upload it in Drive.", 15);
        button("Save encrypted backup", () -> chooseBackup(true));
        button("Restore encrypted backup", () -> chooseBackup(false));
        button("Privacy and data", this::showPrivacy);
        button("Back", this::showHome);
    }

    private void showPrivacy() {
        page("Privacy and data", false);
        label("Tabtaba keeps your profile, settings and prepared messages on this device. "
            + "Tabtaba has no account server.", 16);
        label("When you share a message, the app you choose receives its text. When you export a "
            + "backup, the destination you choose receives a password-protected copy of your data. "
            + "Those services handle the data according to their own policies.", 16);
        label("To remove Tabtaba’s local data, clear its storage in Android Settings or uninstall "
            + "the app. Delete exported backups separately from the location where you saved them.", 16);
        button("Back to settings", this::settings);
    }

    private void chooseBackup(boolean export) {
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(export ? BACKUP_TYPE : "*/*");
        if (export) intent.putExtra(Intent.EXTRA_TITLE, "Tabtaba-" + System.currentTimeMillis() + ".tabtaba");
        try {
            startActivityForResult(intent, export ? EXPORT : IMPORT);
        } catch (android.content.ActivityNotFoundException failure) {
            notice("The Android file picker is unavailable.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (store == null || result != RESULT_OK || data == null || data.getData() == null) return;
        if (request != EXPORT && request != IMPORT) return;
        Uri uri = data.getData();
        boolean export = request == EXPORT;
        EditText passphrase = new EditText(this);
        passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passphrase.setHint(export ? "At least 12 characters" : "Backup password");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(export ? "Protect your backup" : "Replace current data?")
            .setMessage(export ? "Keep this password to restore your backup."
                : "Restoring replaces the profile, settings, and history on this phone.")
            .setView(passphrase)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(export ? "Save" : "Restore", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                int length = passphrase.length();
                if (length < (export ? 12 : 1)) {
                    passphrase.setError(export ? "Use at least 12 characters" : "Enter the password");
                    return;
                }
                char[] password = new char[length];
                passphrase.getText().getChars(0, length, password, 0);
                passphrase.setText("");
                dialog.dismiss();
                transfer(uri, export, password);
            }));
        dialog.show();
    }

    private void transfer(Uri uri, boolean export, char[] password) {
        byte[] snapshot = export ? store.data.toString().getBytes(StandardCharsets.UTF_8) : null;
        ProgressBar progress = new ProgressBar(this);
        AlertDialog busy = new AlertDialog.Builder(this).setTitle(export ? "Saving backup" : "Checking backup")
            .setView(progress).setCancelable(false).create();
        busy.show();
        new Thread(() -> {
            try {
                if (export) {
                    byte[] encrypted = Backup.encrypt(snapshot, password);
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException("Destination unavailable");
                        output.write(encrypted);
                    }
                    runOnUiThread(() -> finishTransfer(busy, null, true));
                } else {
                    JSONObject restored;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        restored = LocalData.parse(Backup.decrypt(LocalData.read(input), password));
                    }
                    runOnUiThread(() -> finishTransfer(busy, restored, true));
                }
            } catch (Exception failure) {
                runOnUiThread(() -> finishTransfer(busy, null, false));
            } finally {
                Arrays.fill(password, '\0');
            }
        }).start();
    }

    private void finishTransfer(AlertDialog busy, JSONObject restored, boolean success) {
        if (isFinishing() || isDestroyed()) return;
        busy.dismiss();
        if (!success) {
            notice("Couldn’t read or write the backup. Check the location and password.");
            return;
        }
        try {
            if (restored != null) store.save(restored);
            settings();
            notice(restored == null ? "Encrypted backup saved." : "Backup restored.");
        } catch (Exception failure) {
            notice("The backup couldn’t be restored.");
        }
    }

    private void compose(boolean request) {
        String kind = request ? "Comfort request" : "Comfort message";
        page(request ? "Ask for comfort" : "Send comfort", false);
        EditText person = input(request ? "Who are you asking?" : "Who is it for?", "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        String[] presets = request ? new String[] {"معلشني", "Comfort me"} :
            new String[] {"معلش", "معلهش", "It’s okay", "It’ll pass", "Don’t worry", "أنا معك",
                "I’m here for you", "Write your own"};
        Spinner choices = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        choices.setAdapter(adapter);
        page.addView(choices);
        EditText custom = input("Custom message (up to 3 words)", "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        custom.setVisibility(View.GONE);
        choices.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                custom.setVisibility(!request && position == presets.length - 1 ? View.VISIBLE : View.GONE);
            }
        });
        boolean online = store.data.optBoolean("online", true);
        label(online ? "Pick an app and recipient from Android’s share menu." :
            "You’re offline. You can save this message to your history.", 15);
        button(online ? "Save and share" : "Save to history", () -> {
            String text = custom.getVisibility() == View.VISIBLE ? custom.getText().toString()
                .replaceAll("(?U)^\\s+|\\s+$", "") : (String) choices.getSelectedItem();
            if (text == null || text.isEmpty() || text.split("(?U)\\s+").length > 3 &&
                    custom.getVisibility() == View.VISIBLE) {
                custom.setError("Enter one to three words.");
                return;
            }
            String recipient = person.getText().toString().trim();
            if (recipient.isEmpty()) {
                person.setError("Enter a name for your history.");
                return;
            }
            try {
                JSONObject next = store.copy();
                JSONObject entry = new JSONObject();
                entry.put("kind", kind);
                entry.put("text", text);
                entry.put("person", recipient);
                entry.put("time", System.currentTimeMillis());
                next.getJSONArray("history").put(entry);
                store.save(next);
                history();
                if (online) {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, "Share with " + recipient));
                } else notice("Saved on this phone.");
            } catch (Exception failure) {
                notice("Couldn’t complete the action. Check your history.");
            }
        });
        button("Back", this::showHome);
    }

    private void history() {
        page("Message history", false);
        JSONArray entries = store.data.optJSONArray("history");
        if (entries.length() == 0) label("Messages you prepare will appear here.", 16);
        label("Saved on this phone", 15);
        int first = Math.max(0, entries.length() - 100);
        for (int i = entries.length() - 1; i >= first; i--) {
            JSONObject entry = entries.optJSONObject(i);
            label(entry.optString("text"), 23).setTypeface(null, Typeface.BOLD);
            label(entry.optString("kind") + " · " + entry.optString("person") + "\n" +
                DateFormat.getDateTimeInstance().format(new Date(entry.optLong("time"))), 14);
        }
        if (first > 0) label("Showing the latest 100 entries. Backups include all entries.", 14);
        button("Back", this::showHome);
    }

    @Override public void onBackPressed() {
        if (home) super.onBackPressed();
        else showHome();
    }

    private void notice(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
