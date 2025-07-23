package com.example.callcounter;

import android.app.Notification;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

public class WhatsAppCallDetector extends NotificationListenerService {

    private static int whatsAppCallCount = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d("CallCounter", "WhatsAppCallDetector onNotificationPosted: package=" + sbn.getPackageName());
        if (sbn.getPackageName().equals("com.whatsapp")) {
            Notification notification = sbn.getNotification();
            String notificationText = notification.extras.getString("android.text");
            Log.d("CallCounter", "WhatsApp notification text: " + notificationText);
            if (notificationText != null && notificationText.toLowerCase().contains("incoming voice call")) {
                whatsAppCallCount++;
                Log.d("CallCounter", "Incremented whatsAppCallCount: " + whatsAppCallCount);
                // Send a broadcast to update the UI
                Intent uiIntent = new Intent("UPDATE_UI");
                uiIntent.putExtra("whatsapp_call_count", whatsAppCallCount);
                LocalBroadcastManager.getInstance(this).sendBroadcast(uiIntent);
            }
        }
    }
}
