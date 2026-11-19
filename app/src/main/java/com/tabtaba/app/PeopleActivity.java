package com.tabtaba.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.widget.*;
import androidx.activity.ComponentActivity;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class PeopleActivity extends ComponentActivity {
    private LinearLayout page;
    private LocalData local;
    private String verificationId;
    private long resendAfter;
    private final Map<String, String> contacts = new LinkedHashMap<>();
    private final List<String> selected = new ArrayList<>();
    private final List<String> selectedNames = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { local = LocalData.get(this); }
        catch (Exception failure) { finish(); return; }
        if (state != null) {
            verificationId = state.getString("verificationId");
            resendAfter = state.getLong("resendAfter");
        }
        if (!Cloud.configured(this)) {
            screen("Connect Tabtaba");
            text("Phone verification and direct delivery will be available after Tabtaba’s online service is set up.");
            action("Back", this::finish);
            return;
        }
        if (Cloud.uid().isEmpty()) login();
        else connected();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("verificationId", verificationId);
        state.putLong("resendAfter", resendAfter);
    }

    private void screen(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(android.graphics.Color.rgb(255, 248, 239));
        scroll.setFitsSystemWindows(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        page.setPadding(padding, padding, padding, padding);
        scroll.addView(page);
        setContentView(scroll);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(26);
        heading.setTextColor(android.graphics.Color.rgb(83, 23, 60));
        page.addView(heading);
    }

    private void text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setPadding(0, 18, 0, 18);
        page.addView(view);
    }

    private EditText field(String hint, int type) {
        EditText value = new EditText(this);
        value.setHint(hint);
        value.setInputType(type);
        page.addView(value);
        return value;
    }

    private Button action(String title, Runnable run) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        page.addView(button);
        button.setOnClickListener(view -> run.run());
        return button;
    }

    private void error(Exception failure) {
        String message = failure == null ? "Try again when you have a connection." : failure.getLocalizedMessage();
        new AlertDialog.Builder(this).setTitle("Couldn’t complete this step")
            .setMessage(message).setPositiveButton("OK", null).show();
    }

    private void login() {
        screen("Verify your mobile number");
        text("Choose the country of your number. Your number is sent to Google for verification and account security. SMS charges may apply.");
        Spinner country = new Spinner(this);
        List<String> regions = new ArrayList<>(PhoneNumberUtil.getInstance().getSupportedRegions());
        Collections.sort(regions);
        country.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, regions));
        country.setSelection(regions.indexOf("AE"));
        page.addView(country);
        EditText number = field("Mobile number", InputType.TYPE_CLASS_PHONE);
        action("Send SMS code", () -> {
            if (System.currentTimeMillis() < resendAfter) {
                text("Please wait one minute before requesting another code.");
                return;
            }
            try {
                PhoneNumberUtil util = PhoneNumberUtil.getInstance();
                com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    util.parse(number.getText().toString(), (String) country.getSelectedItem());
                if (!util.isValidNumber(parsed)) { number.setError("Check the number and country."); return; }
                String phone = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
                resendAfter = System.currentTimeMillis() + 60_000;
                PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                    .setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS).setActivity(this)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override public void onVerificationCompleted(PhoneAuthCredential credential) {
                            signIn(credential);
                        }
                        @Override public void onVerificationFailed(FirebaseException failure) {
                            error(failure);
                        }
                        @Override public void onCodeSent(String id, PhoneAuthProvider.ForceResendingToken token) {
                            verificationId = id;
                            code();
                        }
                    }).build());
            } catch (Exception failure) { error(failure); }
        });
        if (verificationId != null) action("Enter the code I received", this::code);
        action("Back", this::finish);
    }

    private void code() {
        screen("Enter your SMS code");
        EditText otp = field("Six-digit code", InputType.TYPE_CLASS_NUMBER);
        action("Verify", () -> {
            String value = otp.getText().toString().trim();
            if (!value.matches("[0-9]{6}")) { otp.setError("Enter all six digits."); return; }
            signIn(PhoneAuthProvider.getCredential(verificationId, value));
        });
        action("Change number / resend", this::login);
    }

    private void signIn(PhoneAuthCredential credential) {
        FirebaseAuth.getInstance().signInWithCredential(credential).addOnSuccessListener(result -> {
            try {
                String owner = local.data.optString("ownerUid");
                if (!owner.isEmpty() && !owner.equals(Cloud.uid())) {
                    FirebaseAuth.getInstance().signOut();
                    error(new IllegalStateException("This phone has another account’s history. Export it and clear Tabtaba’s storage before switching accounts."));
                    return;
                }
                JSONObject next = local.copy();
                next.put("ownerUid", Cloud.uid());
                next.put("phone", result.getUser().getPhoneNumber());
                local.save(next);
                connected();
            } catch (Exception failure) { error(failure); }
        }).addOnFailureListener(this::error);
    }

    private void connected() {
        screen("Your Tabtaba account");
        text(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());
        text("Only the latest device registered for this account receives new messages.");
        if (!Cloud.uid().equals(local.data.optString("ownerUid"))) {
            text("Restore this account’s backup or sign in with the account that owns this phone’s history.");
            action("Sign out", () -> { FirebaseAuth.getInstance().signOut(); login(); });
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            action("Allow message notifications", () -> requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5));
        action("Refresh messages", () -> Cloud.register(local)
            .addOnSuccessListener(ignored -> Cloud.sync(this, () ->
                Toast.makeText(this, "Refresh finished. Open message history to view received messages.", Toast.LENGTH_LONG).show()))
            .addOnFailureListener(this::error));
        action("Find people in contacts", () -> new AlertDialog.Builder(this)
            .setTitle("Find your people")
            .setMessage("Tabtaba will check up to 500 phone numbers against registered accounts. Contact names stay on your phone.")
            .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) readContacts();
                else requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 4);
            }).show());
        EditText phone = field("Find one number, including +country code", InputType.TYPE_CLASS_PHONE);
        action("Find number", () -> {
            try {
                PhoneNumberUtil util = PhoneNumberUtil.getInstance();
                com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    util.parse(phone.getText().toString(), "ZZ");
                if (!util.isValidNumber(parsed)) { phone.setError("Enter a valid international number."); return; }
                contacts.clear();
                String canonical = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
                contacts.put(canonical, canonical);
                find(new ArrayList<>(contacts.keySet()), 0, new ArrayList<>());
            } catch (Exception failure) { error(failure); }
        });
        action("Sign out", () -> {
            FirebaseAuth.getInstance().signOut();
            login();
        });
        action("Delete online account", () -> new AlertDialog.Builder(this).setTitle("Delete your account?")
            .setMessage("Your account and queued messages will be deleted. Your local history and exported backups remain under your control.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) ->
                Cloud.call("deleteAccount", Collections.emptyMap()).addOnSuccessListener(result -> {
                    FirebaseAuth.getInstance().signOut();
                    login();
                }).addOnFailureListener(failure -> error(new IllegalStateException(
                    "If you signed in more than five minutes ago, sign out and verify again, then retry. " +
                    failure.getLocalizedMessage())))).show());
        action("Back", this::finish);
        Cloud.register(local).addOnSuccessListener(ignored -> Cloud.sync(this, () -> {}))
            .addOnFailureListener(this::error);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 4 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) readContacts();
    }

    private void readContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        text("Reading your contacts…");
        new Thread(() -> {
            Map<String, String> found = new LinkedHashMap<>();
            try {
                String ownPhone = FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber();
                String region = PhoneNumberUtil.getInstance().getRegionCodeForNumber(
                    PhoneNumberUtil.getInstance().parse(ownPhone, "ZZ"));
                try (Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
                    while (cursor != null && cursor.moveToNext() && found.size() < 500) {
                        try {
                            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
                            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed = util.parse(cursor.getString(1), region);
                            if (util.isValidNumber(parsed))
                                found.put(util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164), cursor.getString(0));
                        } catch (Exception invalid) { /* Skip invalid address book numbers. */ }
                    }
                }
                runOnUiThread(() -> {
                    contacts.clear();
                    contacts.putAll(found);
                    find(new ArrayList<>(found.keySet()), 0, new ArrayList<>());
                });
            } catch (Exception failure) { runOnUiThread(() -> error(failure)); }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void find(List<String> phones, int offset, List<Map<String, Object>> matches) {
        if (offset >= phones.size()) { people(matches); return; }
        int end = Math.min(offset + 100, phones.size());
        Cloud.call("findContacts", Cloud.args("phones", phones.subList(offset, end)))
            .addOnSuccessListener(result -> {
                matches.addAll((List<Map<String, Object>>) result.get("matches"));
                find(phones, end, matches);
            }).addOnFailureListener(this::error);
    }

    private void people(List<Map<String, Object>> matches) {
        screen("Your people");
        text("Choose one person to send comfort, or up to ten to ask for comfort. Each request is private.");
        selected.clear();
        selectedNames.clear();
        Set<String> joined = new HashSet<>();
        for (Map<String, Object> match : matches) {
            String phone = (String) match.get("phone");
            String uid = (String) match.get("uid");
            String name = contacts.get(phone);
            joined.add(phone);
            CheckBox choice = new CheckBox(this);
            Object online = match.get("online");
            choice.setText(name + (online == null ? "" : Boolean.TRUE.equals(online) ? " · Online" : " · Offline"));
            page.addView(choice);
            choice.setOnCheckedChangeListener((button, checked) -> {
                if (checked) { selected.add(uid); selectedNames.add(name); }
                else { int index = selected.indexOf(uid); selected.remove(index); selectedNames.remove(index); }
            });
        }
        action("Send comfort", () -> compose(false));
        action("Ask for comfort · معلشني", () -> compose(true));
        text("Invite someone to join Tabtaba");
        for (Map.Entry<String, String> contact : contacts.entrySet()) {
            if (!joined.contains(contact.getKey())) action("Invite " + contact.getValue(), () -> {
                Intent invite = new Intent(Intent.ACTION_SEND).setType("text/plain");
                invite.putExtra(Intent.EXTRA_TEXT, "Join me on طبطبة | Tabtaba: https://github.com/saidk84/maalishny/releases/latest");
                startActivity(Intent.createChooser(invite, "Invite " + contact.getValue()));
            });
        }
        action("Back", this::connected);
    }

    private void compose(boolean request) {
        if (selected.isEmpty() || selected.size() > (request ? 10 : 1)) {
            error(new IllegalArgumentException(request ? "Choose one to ten people." : "Choose one person."));
            return;
        }
        screen(request ? "Ask for comfort" : "Send comfort");
        text(String.join(", ", selectedNames));
        String[] presets = request ? new String[]{"معلشني", "Comfort me"} :
            new String[]{"معلش", "معلهش", "It’s okay", "It’ll pass", "Don’t worry", "أنا معك", "I’m here for you", "Write your own"};
        Spinner choice = new Spinner(this);
        choice.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, presets));
        page.addView(choice);
        EditText custom = field("Custom comfort message · up to 3 words", InputType.TYPE_CLASS_TEXT);
        custom.setVisibility(android.view.View.GONE);
        choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                custom.setVisibility(!request && position == presets.length - 1 ? android.view.View.VISIBLE : android.view.View.GONE);
            }
        });
        Button send = action("Send", () -> {});
        send.setOnClickListener(view -> {
            String message = (String) choice.getSelectedItem();
            if (custom.getVisibility() == android.view.View.VISIBLE) {
                message = custom.getText().toString().trim();
                if (message.isEmpty() || message.split("\\s+").length > 3 || message.length() > 160) {
                    custom.setError("Use one to three words, up to 160 characters."); return;
                }
            }
            try {
                JSONObject next = local.copy();
                JSONObject entry = new JSONObject();
                entry.put("id", UUID.randomUUID().toString());
                entry.put("recipients", new JSONArray(selected));
                entry.put("person", String.join(", ", selectedNames));
                entry.put("kind", request ? "Comfort request" : "Comfort message");
                entry.put("text", message);
                entry.put("time", System.currentTimeMillis());
                entry.put("direction", "sent");
                entry.put("status", "pending");
                next.getJSONArray("history").put(entry);
                local.save(next);
                send.setEnabled(false);
                DeliveryWorker.schedule(this);
                Cloud.sync(this, () -> {
                    Toast.makeText(this, "Saved. Pending messages retry when you refresh online.", Toast.LENGTH_LONG).show();
                    connected();
                });
            } catch (Exception failure) { error(failure); }
        });
        action("Back", this::connected);
    }
}
