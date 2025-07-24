package com.example.callcounter;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

public class WhatsAppCallDetector extends NotificationListenerService {

    private static int whatsAppCallCount = 0;
    // Define keywords for the "Answer" action. Add more for other languages if needed.
    private static final String[] ANSWER_KEYWORDS = {"answer", "responder"};

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d("CallCounter", "WhatsAppCallDetector onNotificationPosted: package=" + sbn.getPackageName());
        if (sbn.getPackageName().equals("com.whatsapp")) {
            Notification notification = sbn.getNotification();
            if (notification == null) return;

            String notificationText = notification.extras.getString("android.text");
            Log.d("CallCounter", "WhatsApp notification text: " + notificationText);
            
            // Check for keywords indicating an incoming call.
            if (notificationText != null && notificationText.toLowerCase().contains("incoming") &&
               (notificationText.toLowerCase().contains("call") || notificationText.toLowerCase().contains("llamada"))) {

                // --- EXISTING LOGIC ---
                whatsAppCallCount++;
                Log.d("CallCounter", "Incremented whatsAppCallCount: " + whatsAppCallCount);
                // Send a broadcast to update the UI
                Intent uiIntent = new Intent("UPDATE_UI");
                uiIntent.putExtra("whatsapp_call_count", whatsAppCallCount);
                LocalBroadcastManager.getInstance(this).sendBroadcast(uiIntent);

                // --- NEW AUTO-ANSWER LOGIC ---
                answerWhatsAppCall(notification);
            }
        }
    }

    private void answerWhatsAppCall(Notification notification) {
        if (notification.actions == null || notification.actions.length == 0) {
            Log.w("CallCounter", "WhatsApp notification has no actions.");
            return;
        }

        for (Notification.Action action : notification.actions) {
            String actionTitle = action.title.toString().toLowerCase();
            for (String keyword : ANSWER_KEYWORDS) {
                if (actionTitle.contains(keyword)) {
                    Log.d("CallCounter", "Found WhatsApp 'Answer' action: " + action.title);
                    try {
                        action.actionIntent.send();
                        Log.d("CallCounter", "Successfully triggered WhatsApp 'Answer' PendingIntent.");
                    } catch (PendingIntent.CanceledException e) {
                        Log.e("CallCounter", "Could not send PendingIntent for WhatsApp: " + e.getMessage());
                    }
                    return; // Exit after finding and triggering the action
                }
            }
        }
        Log.w("CallCounter", "Could not find an 'Answer' action in the WhatsApp notification.");
    }
}
