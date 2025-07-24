package com.example.callcounter;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CallReceiver extends BroadcastReceiver {

    private static int callCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        Log.d("CallCounter", "CallReceiver onReceive: state=" + state);
        if (state != null && state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            // --- EXISTING LOGIC ---
            callCount++;
            Log.d("CallCounter", "Incremented callCount: " + callCount);
            // Send a broadcast to update the UI
            Intent uiIntent = new Intent("UPDATE_UI");
            uiIntent.putExtra("call_count", callCount);
            LocalBroadcastManager.getInstance(context).sendBroadcast(uiIntent);

            // --- NEW AUTO-ANSWER LOGIC ---
            answerPhoneCall(context);
        }
    }

    private void answerPhoneCall(Context context) {
        // TelecomManager is available from API 21 (Lollipop)
        // acceptRingingCall() requires API 26 (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager == null) {
                Log.e("CallCounter", "TelecomManager is null.");
                return;
            }

            // Check for ANSWER_PHONE_CALLS permission before proceeding
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                Log.e("CallCounter", "ANSWER_PHONE_CALLS permission not granted.");
                return;
            }

            try {
                Log.d("CallCounter", "Attempting to answer call...");
                telecomManager.acceptRingingCall();
                Log.d("CallCounter", "Call answered successfully via TelecomManager.");
            } catch (Exception e) {
                Log.e("CallCounter", "Error answering call: " + e.getMessage());
            }
        } else {
            Log.e("CallCounter", "Device API level is below Oreo (API 26). Cannot auto-answer phone call.");
        }
    }
}
