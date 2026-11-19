package com.tabtaba.app;

import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class PushService extends FirebaseMessagingService {
    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        DeliveryWorker.schedule(this);
    }
    @Override public void onNewToken(@NonNull String token) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (Cloud.configured(this) && !Cloud.uid().isEmpty())
                    Cloud.register(LocalData.get(this)).addOnSuccessListener(ignored -> DeliveryWorker.schedule(this));
            } catch (Exception ignored) { /* Registration retries when the app opens. */ }
        });
    }
}
