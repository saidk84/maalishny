package com.tabtaba.app;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class Cloud {
    private static boolean initialized;

    static boolean configured(Context context) {
        if (initialized) return true;
        if (FirebaseApp.initializeApp(context) == null) return false;
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(BuildConfig.DEBUG
            ? DebugAppCheckProviderFactory.getInstance() : PlayIntegrityAppCheckProviderFactory.getInstance());
        initialized = true;
        return true;
    }

    static String uid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? "" :
            FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @SuppressWarnings("unchecked")
    static Task<Map<String, Object>> call(String name, Map<String, Object> data) {
        return FirebaseFunctions.getInstance("us-central1").getHttpsCallable(name).call(data)
            .continueWith(task -> (Map<String, Object>) task.getResult().getData());
    }

    static Map<String, Object> args(Object... pairs) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) data.put((String) pairs[i], pairs[i + 1]);
        return data;
    }

    static Task<Void> register(LocalData local) {
        if (uid().isEmpty()) return Tasks.forResult(null);
        return FirebaseMessaging.getInstance().getToken().continueWithTask(token ->
            call("registerDevice", args("token", token.isSuccessful() ? token.getResult() : "",
                "online", local.data.optBoolean("online", true),
                "visible", local.data.optBoolean("visible", false))).continueWith(task -> {
                    task.getResult();
                    return null;
                }));
    }

    // All local mutations run on Android's main thread, including worker callbacks.
    static void sync(Context context, Runnable done) {
        try {
            if (!configured(context) || uid().isEmpty()) { done.run(); return; }
            LocalData local = LocalData.get(context);
            if (!uid().equals(local.data.optString("ownerUid")) ||
                    !local.data.optBoolean("online", true)) { done.run(); return; }
            final String account = uid();
            call("fetchInbox", Collections.emptyMap()).addOnCompleteListener(task -> {
                if (!account.equals(uid())) { done.run(); return; }
                if (!task.isSuccessful()) { done.run(); return; }
                try {
                    List<?> messages = (List<?>) task.getResult().get("messages");
                    JSONObject next = local.copy();
                    JSONArray history = next.getJSONArray("history");
                    Set<String> existing = new HashSet<>();
                    for (int i = 0; i < history.length(); i++)
                        existing.add(history.getJSONObject(i).optString("id"));
                    List<String> ids = new ArrayList<>();
                    int count = 0;
                    for (Object object : messages) {
                        JSONObject message = new JSONObject((Map<?, ?>) object);
                        String id = message.getString("id");
                        ids.add(id);
                        if (existing.add(id)) {
                            message.put("direction", "received");
                            history.put(message);
                            count++;
                        }
                    }
                    // Acknowledge only after durable local storage succeeds.
                    local.save(next);
                    if (count > 0) DeliveryWorker.notifyReceived(context);
                    Task<?> acknowledgement = ids.isEmpty() ? Tasks.forResult(null) :
                        call("acknowledge", args("ids", ids));
                    acknowledgement.addOnCompleteListener(ignored -> {
                        if (!account.equals(uid())) { done.run(); return; }
                        if (acknowledgement.isSuccessful() && messages.size() == 100) sync(context, done);
                        else flush(local, account, done);
                    });
                } catch (Exception failure) { done.run(); }
            });
        } catch (Exception failure) { done.run(); }
    }

    private static void flush(LocalData local, String account, Runnable done) {
        try {
            JSONArray history = local.data.getJSONArray("history");
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                if (!entry.optString("status").equals("pending")) continue;
                int index = i;
                JSONArray targets = entry.getJSONArray("recipients");
                List<String> recipients = new ArrayList<>();
                for (int t = 0; t < targets.length(); t++) recipients.add(targets.getString(t));
                call("sendComfort", args("id", entry.getString("id"), "text", entry.getString("text"),
                    "kind", entry.getString("kind"), "recipients", recipients))
                    .addOnCompleteListener(task -> {
                        if (!account.equals(uid()) || !task.isSuccessful()) { done.run(); return; }
                        try {
                            JSONObject next = local.copy();
                            next.getJSONArray("history").getJSONObject(index).put("status", "queued");
                            local.save(next);
                            flush(local, account, done);
                        } catch (Exception failure) { done.run(); }
                    });
                return;
            }
        } catch (Exception failure) { /* Keep pending messages available for retry. */ }
        done.run();
    }
}
