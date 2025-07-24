package com.example.callcounter;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.pm.PackageManager;

public class WhatsAppCallDetector extends NotificationListenerService {

    private static int whatsAppCallCount = 0;
    // Define keywords for the "Answer" action in multiple languages
    private static final String[] ANSWER_KEYWORDS = {
        "answer", "responder", "atender", "accept", "acceptar", 
        "décrocher", "antworten", "rispondere", "接听", "응답"
    };
    
    // Keywords to detect incoming calls
    private static final String[] CALL_KEYWORDS = {
        "incoming", "call", "llamada", "appel", "anruf", "chiamata", "来电", "전화"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Log.d("CallCounter", "WhatsAppCallDetector onNotificationPosted: package=" + packageName);
        
        if (packageName.equals("com.whatsapp")) {
            Notification notification = sbn.getNotification();
            if (notification == null) return;

            String notificationText = extractNotificationText(notification);
            Log.d("CallCounter", "WhatsApp notification text: " + notificationText);
            
            if (isIncomingCall(notificationText)) {
                // --- EXISTING LOGIC ---
                whatsAppCallCount++;
                Log.d("CallCounter", "Incremented whatsAppCallCount: " + whatsAppCallCount);
                
                // Send a broadcast to update the UI
                Intent uiIntent = new Intent("UPDATE_UI");
                uiIntent.putExtra("whatsapp_call_count", whatsAppCallCount);
                LocalBroadcastManager.getInstance(this).sendBroadcast(uiIntent);

                // --- ENHANCED AUTO-ANSWER LOGIC ---
                attemptWhatsAppAutoAnswer(notification, sbn);
            }
        }
    }

    private String extractNotificationText(Notification notification) {
        StringBuilder text = new StringBuilder();
        
        // Try multiple ways to extract notification text
        if (notification.extras != null) {
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence content = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
            
            if (title != null) text.append(title).append(" ");
            if (content != null) text.append(content).append(" ");
            if (bigText != null) text.append(bigText).append(" ");
            if (subText != null) text.append(subText).append(" ");
        }
        
        return text.toString().toLowerCase();
    }

    private boolean isIncomingCall(String notificationText) {
        if (notificationText == null || notificationText.isEmpty()) return false;
        
        // Check for call-related keywords
        boolean hasCallKeyword = false;
        for (String keyword : CALL_KEYWORDS) {
            if (notificationText.contains(keyword.toLowerCase())) {
                hasCallKeyword = true;
                break;
            }
        }
        
        // Additional checks for WhatsApp call patterns
        return hasCallKeyword || 
               notificationText.contains("voice call") ||
               notificationText.contains("video call") ||
               notificationText.contains("calling") ||
               notificationText.contains("ringing");
    }

    private void attemptWhatsAppAutoAnswer(Notification notification, StatusBarNotification sbn) {
        // Method 1: Try notification actions (existing approach, but improved)
        boolean actionSuccess = tryNotificationAction(notification);
        
        if (!actionSuccess) {
            // Method 2: Try to launch WhatsApp directly with delay
            tryDirectWhatsAppLaunch();
            
            // Method 3: Send broadcast to accessibility service (if implemented)
            sendAccessibilityBroadcast();
        }
    }

    private boolean tryNotificationAction(Notification notification) {
        if (notification.actions == null || notification.actions.length == 0) {
            Log.w("CallCounter", "WhatsApp notification has no actions.");
            return false;
        }

        for (Notification.Action action : notification.actions) {
            if (action.title == null) continue;
            
            String actionTitle = action.title.toString().toLowerCase();
            Log.d("CallCounter", "Checking action: " + actionTitle);
            
            for (String keyword : ANSWER_KEYWORDS) {
                if (actionTitle.contains(keyword)) {
                    Log.d("CallCounter", "Found WhatsApp 'Answer' action: " + action.title);
                    try {
                        if (action.actionIntent != null) {
                            action.actionIntent.send();
                            Log.d("CallCounter", "Successfully triggered WhatsApp 'Answer' PendingIntent.");
                            return true;
                        }
                    } catch (PendingIntent.CanceledException e) {
                        Log.e("CallCounter", "Could not send PendingIntent for WhatsApp: " + e.getMessage());
                    }
                }
            }
        }
        
        // Try to click on the notification itself as fallback
        try {
            if (notification.contentIntent != null) {
                notification.contentIntent.send();
                Log.d("CallCounter", "Triggered WhatsApp notification contentIntent as fallback.");
                
                // Add delay then try to find answer button via accessibility
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    sendAccessibilityBroadcast();
                }, 2000); // 2 second delay
                
                return true;
            }
        } catch (PendingIntent.CanceledException e) {
            Log.e("CallCounter", "Could not send contentIntent: " + e.getMessage());
        }
        
        return false;
    }

    private void tryDirectWhatsAppLaunch() {
        try {
            // Launch WhatsApp directly
            PackageManager pm = getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage("com.whatsapp");
            
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
                Log.d("CallCounter", "Launched WhatsApp directly");
                
                // Send accessibility broadcast after delay
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    sendAccessibilityBroadcast();
                }, 3000); // 3 second delay to let WhatsApp load
            } else {
                Log.w("CallCounter", "WhatsApp launch intent is null.");
            }
        } catch (Exception e) {
            Log.e("CallCounter", "Failed to launch WhatsApp: " + e.getMessage());
        }
    }

    private void sendAccessibilityBroadcast() {
        // Send broadcast to accessibility service to try clicking answer button
        Intent accessibilityIntent = new Intent("WHATSAPP_AUTO_ANSWER");
        sendBroadcast(accessibilityIntent);
        Log.d("CallCounter", "Sent accessibility broadcast for WhatsApp auto-answer");
    }

    // Additional method to handle notification removal (call ended)
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals("com.whatsapp")) {
            Log.d("CallCounter", "WhatsApp notification removed - call may have ended");
        }
    }
}
