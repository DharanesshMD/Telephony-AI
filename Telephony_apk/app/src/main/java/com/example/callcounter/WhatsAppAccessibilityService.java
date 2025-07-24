package com.example.callcounter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class WhatsAppAccessibilityService extends AccessibilityService {

    private static final String TAG = "WhatsAppAccessibility";
    private BroadcastReceiver autoAnswerReceiver;
    private Handler handler;
    private boolean isServiceActive = false;

    // Keywords for answer button in different languages
    private static final String[] ANSWER_BUTTON_TEXTS = {
        "answer", "accept", "pick up", "responder", "atender", "acceptar",
        "décrocher", "accepter", "antworten", "annehmen", "rispondere", "accettare",
        "接听", "接受", "응답", "수락", "받기", "उत्तर", "जवाब"
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        
        try {
            Log.d(TAG, "WhatsApp Accessibility Service connecting...");
            
            // Initialize handler safely
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
            
            // Register broadcast receiver with try-catch
            registerAutoAnswerReceiver();
            
            isServiceActive = true;
            Log.d(TAG, "WhatsApp Accessibility Service connected successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onServiceConnected: " + e.getMessage(), e);
            // Don't let the service crash, just log the error
        }
    }

    private void registerAutoAnswerReceiver() {
        try {
            if (autoAnswerReceiver == null) {
                autoAnswerReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        try {
                            if ("WHATSAPP_AUTO_ANSWER".equals(intent.getAction())) {
                                Log.d(TAG, "Received auto-answer broadcast");
                                
                                if (handler != null && isServiceActive) {
                                    handler.postDelayed(() -> {
                                        try {
                                            attemptToAnswerCall();
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error in delayed answer attempt: " + e.getMessage());
                                        }
                                    }, 1500); // Increased delay for MIUI
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in broadcast receiver: " + e.getMessage());
                        }
                    }
                };
                
                IntentFilter filter = new IntentFilter("WHATSAPP_AUTO_ANSWER");
                registerReceiver(autoAnswerReceiver, filter);
                Log.d(TAG, "Broadcast receiver registered successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register broadcast receiver: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isServiceActive) return;
        
        try {
            // Only process WhatsApp events
            if (event.getPackageName() == null || 
                !event.getPackageName().toString().equals("com.whatsapp")) {
                return;
            }
            
            Log.d(TAG, "WhatsApp accessibility event: " + event.getEventType() + 
                       " Class: " + event.getClassName());
            
            // Check for call-related events
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                
                // Use handler to avoid blocking the accessibility event
                if (handler != null) {
                    handler.postDelayed(() -> {
                        try {
                            checkForIncomingCall();
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking for incoming call: " + e.getMessage());
                        }
                    }, 800);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onAccessibilityEvent: " + e.getMessage());
            // Don't crash the service, just log the error
        }
    }

    private void checkForIncomingCall() {
        if (!isServiceActive) return;
        
        AccessibilityNodeInfo rootNode = null;
        try {
            rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.d(TAG, "No root node available");
                return;
            }

            // Look for incoming call indicators
            if (isIncomingCallScreen(rootNode)) {
                Log.d(TAG, "Detected incoming call screen, attempting to answer");
                attemptToAnswerCall();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in checkForIncomingCall: " + e.getMessage());
        } finally {
            if (rootNode != null) {
                try {
                    rootNode.recycle();
                } catch (Exception e) {
                    Log.e(TAG, "Error recycling root node: " + e.getMessage());
                }
            }
        }
    }

    private boolean isIncomingCallScreen(AccessibilityNodeInfo rootNode) {
        try {
            // Method 1: Check for "Incoming" text
            List<AccessibilityNodeInfo> incomingNodes = rootNode.findAccessibilityNodeInfosByText("Incoming");
            if (incomingNodes != null && !incomingNodes.isEmpty()) {
                for (AccessibilityNodeInfo node : incomingNodes) {
                    if (node != null) node.recycle();
                }
                return true;
            }

            // Method 2: Check for call-related text
            String[] callIndicators = {"voice call", "video call", "calling", "incoming call", "रही है"};
            for (String indicator : callIndicators) {
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText(indicator);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node != null) node.recycle();
                    }
                    return true;
                }
            }

            // Method 3: Check class names for call activity
            String className = rootNode.getClassName() != null ? rootNode.getClassName().toString() : "";
            if (className.contains("Call") || className.contains("Voice") || className.contains("Video")) {
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in isIncomingCallScreen: " + e.getMessage());
        }
        
        return false;
    }

    private void attemptToAnswerCall() {
        if (!isServiceActive) return;
        
        AccessibilityNodeInfo rootNode = null;
        try {
            rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.w(TAG, "No root node available for answering");
                return;
            }

            boolean answered = false;

            // Method 1: Try to find answer button by text
            for (String answerText : ANSWER_BUTTON_TEXTS) {
                if (answered) break;
                
                try {
                    List<AccessibilityNodeInfo> answerNodes = rootNode.findAccessibilityNodeInfosByText(answerText);
                    if (answerNodes != null) {
                        for (AccessibilityNodeInfo answerNode : answerNodes) {
                            if (answerNode != null && answerNode.isClickable()) {
                                Log.d(TAG, "Found clickable answer button with text: " + answerText);
                                boolean clicked = answerNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.d(TAG, "Click result: " + clicked);
                                if (clicked) {
                                    answered = true;
                                    break;
                                }
                            }
                            if (answerNode != null) answerNode.recycle();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error finding answer button by text: " + e.getMessage());
                }
            }

            // Method 2: Try to find clickable elements in the bottom area
            if (!answered) {
                answered = findAndClickBottomButtons(rootNode);
            }

            // Method 3: Try gesture-based approach (only on API 24+)
            if (!answered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Log.d(TAG, "Trying gesture-based answer approach");
                performAnswerGesture();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in attemptToAnswerCall: " + e.getMessage());
        } finally {
            if (rootNode != null) {
                try {
                    rootNode.recycle();
                } catch (Exception e) {
                    Log.e(TAG, "Error recycling root node in attemptToAnswerCall: " + e.getMessage());
                }
            }
        }
    }

    private boolean findAndClickBottomButtons(AccessibilityNodeInfo rootNode) {
        try {
            return searchForClickableElements(rootNode);
        } catch (Exception e) {
            Log.e(TAG, "Error in findAndClickBottomButtons: " + e.getMessage());
            return false;
        }
    }

    private boolean searchForClickableElements(AccessibilityNodeInfo node) {
        try {
            // Check if this node is a likely answer button
            if (node.isClickable() && isLikelyAnswerButton(node)) {
                Log.d(TAG, "Found likely answer button, attempting click");
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // Recursively search children
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null) {
                        boolean result = searchForClickableElements(child);
                        if (result) return true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error accessing child node: " + e.getMessage());
                } finally {
                    if (child != null) {
                        try {
                            child.recycle();
                        } catch (Exception e) {
                            Log.e(TAG, "Error recycling child node: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in searchForClickableElements: " + e.getMessage());
        }

        return false;
    }

    private boolean isLikelyAnswerButton(AccessibilityNodeInfo node) {
        try {
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            String contentDesc = node.getContentDescription() != null ? 
                                node.getContentDescription().toString().toLowerCase() : "";
            
            // Check if it's a button or ImageView (common for call buttons)
            if (className.contains("Button") || className.contains("ImageView") || 
                className.contains("ImageButton") || className.contains("View")) {
                
                // Check if it's positioned in the lower part of screen
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                
                int screenHeight = getScreenHeight();
                if (screenHeight > 0 && bounds.centerY() > screenHeight * 0.6) {
                    Log.d(TAG, "Found button in lower screen area: " + className);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in isLikelyAnswerButton: " + e.getMessage());
        }

        return false;
    }

    private void performAnswerGesture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Gestures not supported on this API level");
            return;
        }

        try {
            int screenWidth = getScreenWidth();
            int screenHeight = getScreenHeight();
            
            if (screenWidth <= 0 || screenHeight <= 0) {
                Log.w(TAG, "Invalid screen dimensions for gesture");
                return;
            }
            
            // Common positions for answer buttons
            int[][] tapPositions = {
                {screenWidth / 2, (int)(screenHeight * 0.85)}, // Bottom center
                {screenWidth / 4, (int)(screenHeight * 0.80)}, // Bottom left
                {(int)(screenWidth * 0.75), (int)(screenHeight * 0.80)} // Bottom right
            };
            
            for (int[] position : tapPositions) {
                performTapGesture(position[0], position[1]);
                
                // Add delay between taps
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Sleep interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in performAnswerGesture: " + e.getMessage());
        }
    }

    private void performTapGesture(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        try {
            Path path = new Path();
            path.moveTo(x, y);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 150));
            
            GestureDescription gesture = builder.build();
            
            boolean result = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "Tap gesture completed at (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.w(TAG, "Tap gesture cancelled at (" + x + ", " + y + ")");
                }
            }, null);
            
            Log.d(TAG, "Dispatched tap gesture at (" + x + ", " + y + "): " + result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error performing tap gesture at (" + x + ", " + y + "): " + e.getMessage());
        }
    }

    private int getScreenWidth() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                return metrics.widthPixels;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen width: " + e.getMessage());
        }
        return 0;
    }

    private int getScreenHeight() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                return metrics.heightPixels;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen height: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
        isServiceActive = false;
    }

    @Override
    public void onDestroy() {
        try {
            isServiceActive = false;
            
            if (autoAnswerReceiver != null) {
                try {
                    unregisterReceiver(autoAnswerReceiver);
                    autoAnswerReceiver = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
                }
            }
            
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
                handler = null;
            }
            
            Log.d(TAG, "WhatsApp Accessibility Service destroyed");
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage());
        } finally {
            super.onDestroy();
        }
    }
}