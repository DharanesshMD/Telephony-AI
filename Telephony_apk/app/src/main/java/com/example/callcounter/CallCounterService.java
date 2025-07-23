package com.example.callcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.util.Log;

public class CallCounterService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "CallCounterChannel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("CallCounter", "CallCounterService onStartCommand: action=" + (intent != null ? intent.getAction() : "null"));
        if (intent != null && "STOP_ACTION".equals(intent.getAction())) {
            Log.d("CallCounter", "CallCounterService received STOP_ACTION");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();

        Intent stopIntent = new Intent(this, CallCounterService.class);
        stopIntent.setAction("STOP_ACTION");
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Call Counter Running")
                .setContentText("Monitoring incoming calls.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .addAction(R.drawable.ic_stop, "Stop", pendingStopIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Counter Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
