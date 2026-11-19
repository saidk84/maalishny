package com.tabtaba.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@androidx.annotation.Keep
public final class DeliveryWorker extends Worker {
    public DeliveryWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    static void schedule(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork("tabtaba-inbox",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            new OneTimeWorkRequest.Builder(DeliveryWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build());
    }

    @NonNull @Override public Result doWork() {
        CountDownLatch finished = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean successful = new java.util.concurrent.atomic.AtomicBoolean(false);
        new Handler(Looper.getMainLooper()).post(() -> Cloud.syncResult(getApplicationContext(), result -> {
            successful.set(result);
            finished.countDown();
        }));
        try {
            return finished.await(90, TimeUnit.SECONDS) && successful.get() ? Result.success() : Result.retry();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
    }

    static void notifyReceived(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("comfort", "Comfort messages",
            NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent action = PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(1, new Notification.Builder(context, "comfort")
            .setSmallIcon(com.tabtaba.app.R.drawable.ic_tabtaba)
            .setContentTitle("طبطبة | Tabtaba").setContentText("You received a little comfort")
            .setContentIntent(action).setAutoCancel(true).build());
    }
}
