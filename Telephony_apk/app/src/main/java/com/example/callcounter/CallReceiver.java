package com.example.callcounter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

public class CallReceiver extends BroadcastReceiver {

    private static int callCount = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        Log.d("CallCounter", "CallReceiver onReceive: state=" + state);
        if (state != null && state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            callCount++;
            Log.d("CallCounter", "Incremented callCount: " + callCount);
            // Send a broadcast to update the UI
            Intent uiIntent = new Intent("UPDATE_UI");
            uiIntent.putExtra("call_count", callCount);
            LocalBroadcastManager.getInstance(context).sendBroadcast(uiIntent);
        }
    }
}
