package com.example.callcounter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView normalCallCountTextView;
    private TextView whatsappCallCountTextView;
    private int normalCallCount = 0;
    private int whatsappCallCount = 0;

    private BroadcastReceiver uiUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("call_count")) {
                normalCallCount = intent.getIntExtra("call_count", 0);
                normalCallCountTextView.setText("Normal Calls: " + normalCallCount);
            }
            if (intent.hasExtra("whatsapp_call_count")) {
                whatsappCallCount = intent.getIntExtra("whatsapp_call_count", 0);
                whatsappCallCountTextView.setText("WhatsApp Calls: " + whatsappCallCount);
            }
        }
    };

    private static final String PREFS_NAME = "CallCounterPrefs";
    private static final String KEY_FIRST_RUN = "first_run";
    private static final String KEY_AUTOSTART_ADDRESSED = "autostart_addressed";
    private static final String KEY_BATTERY_OPTIMIZATION_ADDRESSED = "battery_optimization_addressed";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    };

    private int currentPermissionStep = 0; // 0: basic permissions, 1: notification access, 2: battery optimization, 3: autostart, 4: final steps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("CallCounter", "MainActivity onCreate started");

        normalCallCountTextView = findViewById(R.id.normal_call_count);
        if (normalCallCountTextView == null) {
            Log.e("CallCounter", "normalCallCountTextView is null! Check activity_main.xml for correct ID.");
        }
        whatsappCallCountTextView = findViewById(R.id.whatsapp_call_count);
        if (whatsappCallCountTextView == null) {
            Log.e("CallCounter", "whatsappCallCountTextView is null! Check activity_main.xml for correct ID.");
        }

        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(uiUpdateReceiver, new IntentFilter("UPDATE_UI"));
        } catch (Exception e) {
            Log.e("CallCounter", "Failed to register receiver: " + e.getMessage());
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean(KEY_FIRST_RUN, true);
        if (isFirstRun) {
            try {
                showFirstRunChecklist();
            } catch (Exception e) {
                Log.e("CallCounter", "Error in showFirstRunChecklist: " + e.getMessage());
            }
            prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply();
        }
        // Initial setup and permission checks
        continueSetup();

        // Service will be started after all permissions are granted
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Re-check TextViews in case they were somehow not initialized
        if (normalCallCountTextView == null || whatsappCallCountTextView == null) {
            Log.d("CallCounter", "Reinitializing TextViews in onResume");
            normalCallCountTextView = findViewById(R.id.normal_call_count);
            whatsappCallCountTextView = findViewById(R.id.whatsapp_call_count);
        }

        // Continue setup if needed
        requestNextPermissionOrSetting();
    }

    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uiUpdateReceiver);
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void showFirstRunChecklist() {
        new AlertDialog.Builder(this)
            .setTitle("First Time Setup - Xiaomi/MIUI")
            .setMessage("To ensure Call Counter works properly, please:\n\n" +
                "1. Grant all permissions when prompted.\n" +
                "2. Enable Notification Access for this app.\n" +
                "3. Disable Battery Optimization for this app.\n" +
                "4. Enable Autostart for this app in MIUI settings.\n" +
                "5. Lock the app in Recent Apps (pull down to lock).\n" +
                "6. Enable 'Start in background' and all other permissions in App Info.\n\n" +
                "You will be guided to the relevant settings. After completing all steps, return to the app.")
            .setCancelable(false)
            .setPositiveButton("Start Setup", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    checkAndRequestAllPermissions();
                }
            })
            .show();
    }

    private void checkAndRequestAllPermissions() {
        requestNextPermissionOrSetting();
    }

    private void requestNextPermissionOrSetting() {
        Log.d("CallCounter", "requestNextPermissionOrSetting called, step: " + currentPermissionStep);

        switch (currentPermissionStep) {
            case 0:
                // 1. Check basic permissions
                List<String> missingPermissions = new ArrayList<>();
                for (String perm : REQUIRED_PERMISSIONS) {
                    if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.add(perm);
                    }
                }
                if (!missingPermissions.isEmpty()) {
                    Log.d("CallCounter", "Requesting basic permissions");
                    ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                } else {
                    currentPermissionStep++;
                    requestNextPermissionOrSetting(); // Move to next step
                }
                break;
            case 1:
                // 2. Notification Access
                if (!isNotificationServiceEnabled()) {
                    Log.d("CallCounter", "Requesting Notification Access");
                    new AlertDialog.Builder(this)
                        .setTitle("Enable Notification Access")
                        .setMessage("Please enable notification access for Call Counter.")
                        .setCancelable(false)
                        .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                            }
                        })
                        .show();
                } else {
                    currentPermissionStep++;
                    requestNextPermissionOrSetting(); // Move to next step
                }
                break;
            case 2:
                // 3. Battery Optimization (API 23+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    boolean batteryOptimizationAddressed = prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_ADDRESSED, false);

                    if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                        if (!batteryOptimizationAddressed) {
                            Log.d("CallCounter", "Requesting to disable Battery Optimization");
                            new AlertDialog.Builder(this)
                                .setTitle("Disable Battery Optimization")
                                .setMessage("Please disable battery optimization for Call Counter.")
                                .setCancelable(false)
                                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                        intent.setData(Uri.parse("package:" + getPackageName()));
                                        startActivity(intent);
                                        prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZATION_ADDRESSED, true).apply();
                                    }
                                })
                                .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZATION_ADDRESSED, true).apply();
                                    }
                                })
                                .show();
                            break;
                        } else {
                            Log.d("CallCounter", "Battery optimization already addressed, skipping dialog.");
                        }
                    }
                }
                currentPermissionStep++;
                requestNextPermissionOrSetting(); // Move to next step
                break;
            case 3:
                // 4. Autostart (MIUI-specific, best effort)
                Log.d("CallCounter", "Checking Autostart settings");
                showAutostartDialogIfNeeded();
                currentPermissionStep++;
                requestNextPermissionOrSetting(); // Move to next step
                break;
            case 4:
                // 5. Show reminder for locking in recent apps and enabling background/start permissions
                if (areAllPermissionsGranted()) {
                    Log.d("CallCounter", "All core permissions granted, skipping final steps dialog.");
                    currentPermissionStep++;
                    requestNextPermissionOrSetting(); // Move to next step
                } else {
                    Log.d("CallCounter", "Showing final steps dialog");
                    new AlertDialog.Builder(this)
                        .setTitle("Final Steps")
                        .setMessage("1. Lock the app in Recent Apps (pull down to lock).\n2. In App Info > Other permissions, enable 'Start in background' and all others.\n\nAfter completing these steps, return to the app.")
                        .setCancelable(false)
                        .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                currentPermissionStep++;
                                requestNextPermissionOrSetting(); // Move to next step
                            }
                        })
                        .show();
                }
                break;
            case 5:
                // All permissions and settings are granted, start the service
                if (areAllPermissionsGranted()) {
                    try {
                        Intent serviceIntent = new Intent(this, CallCounterService.class);
                        startService(serviceIntent);
                        Log.d("CallCounter", "CallCounterService started successfully");
                    } catch (Exception e) {
                        Log.e("CallCounter", "Failed to start CallCounterService: " + e.getMessage());
                    }
                } else {
                    Log.d("CallCounter", "Waiting for all permissions before starting service");
                }
                break;
            default:
                Log.d("CallCounter", "All permission steps completed or unknown step.");
                break;
        }
    }

    private void showAutostartDialogIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean autostartAddressed = prefs.getBoolean(KEY_AUTOSTART_ADDRESSED, false);

        if (autostartAddressed) {
            Log.d("CallCounter", "Autostart permission already addressed, skipping dialog.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Enable Autostart (MIUI)")
            .setMessage("To ensure Call Counter works reliably, please enable Autostart for this app in the next screen. After enabling, return here and tap Continue.")
            .setCancelable(false)
            .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.w("CallCounter", "Autostart settings not found: " + e.getMessage());
                    }
                    prefs.edit().putBoolean(KEY_AUTOSTART_ADDRESSED, true).apply();
                }
            })
            .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    prefs.edit().putBoolean(KEY_AUTOSTART_ADDRESSED, true).apply();
                }
            })
            .show();
    }

    private boolean areAllPermissionsGranted() {
        // Check basic permissions
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        
        // Check notification access
        if (!isNotificationServiceEnabled()) {
            return false;
        }
        
        // Check battery optimization (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                return false;
            }
        }
        
        return true;
    }

    private void continueSetup() {
        Log.d("CallCounter", "continueSetup called");
        // Start the sequential permission/setting request flow
        requestNextPermissionOrSetting();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                Log.d("CallCounter", "All basic permissions granted");
                currentPermissionStep++; // Move to next step after basic permissions
                requestNextPermissionOrSetting();
            } else {
                Log.w("CallCounter", "Not all basic permissions granted");
                // Optionally, show a message to the user that permissions are required
                new AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage("Please grant all requested permissions for the app to function correctly.")
                    .setPositiveButton("OK", null)
                    .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // After returning from any settings activity, re-check the current step
        requestNextPermissionOrSetting();
    }
}
