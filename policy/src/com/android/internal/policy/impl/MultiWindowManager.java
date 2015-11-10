package com.android.internal.policy.impl;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.IUiModeManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSessionLegacyHelper;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.dreams.DreamManagerInternal;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.speech.RecognizerIntent;
import android.telecom.TelecomManager;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerInternal;
import android.view.WindowManagerPolicy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;

import android.hardware.input.InputManager;

import com.android.internal.R;
import com.android.internal.policy.PolicyManager;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.widget.PointerLocationView;
import com.android.server.LocalServices;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import java.lang.Math;

import static android.view.WindowManager.LayoutParams.*;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_OPEN;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.LID_CLOSED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVER_ABSENT;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_UNCOVERED;
import static android.view.WindowManagerPolicy.WindowManagerFuncs.CAMERA_LENS_COVERED;


public class MultiWindowManager implements WindowManagerPolicy {
    /**
     * Debug options
     */
    static final String TAG = "MultiWindowManager";
    static final boolean DEBUG = false;
    static final boolean DEBUG_INPUT = DEBUG || false;
    static final boolean DEBUG_LAYOUT = DEBUG || false;
    static final boolean DEBUG_STARTING_WINDOW = DEBUG || false;
    static final boolean DEBUG_MULTIWINDOW = DEBUG || false;
    static final boolean DEBUG_MESSAGE = DEBUG || false;
    static final boolean DEBUG_SYSTEM_UI = DEBUG || false;
    static final boolean DEBUG_FLOATING_WINDOW = DEBUG || false;
    static final boolean DEBUG_GESTURE = DEBUG || false;

    // HACK : if false, don't show starting window, default change to false
    static final boolean SHOW_STARTING_ANIMATIONS = false;

    /**
     * layout sizes for SystemUI
     */
    int mMultiWindowControlbarWidth = 64;
    int mMultiWindowControlbarHeight = 256;
    int mMultiWindowControlbarStartY = 172;
    int mMinilauncherWidth = 512;
    int mMinilauncherHeight = 300;
    int mDragControlHeight = 180;
    int mFloatingControlHeight = 80;

    /**
     * key hooking
     */
    /* define pressType */
    static final int KEY_PRESS_TYPE_SHORT = 0;
    static final int KEY_PRESS_TYPE_LONG = 1;
    static final int KEY_PRESS_TYPE_DOUBLE = 2;
    static final int KEY_PRESS_TYPE_TRIPLE = 3;
    static final int KEY_PRESS_TYPE_MAX = 4;

    /* define actions */
    static final int ACTION_DO_NOTHING = 0;
    static final int ACTION_LCD_ON_OFF = 1;
    static final int ACTION_GO_HOME = 2;
    static final int ACTION_BACK = 3;
    static final int ACTION_MINILAUNCHER = 4;
    static final int ACTION_MASTER_MUTE_TOGGLE = 10;
    static final int ACTION_SUB_MUTE_TOGGLE = 11;
    static final int ACTION_STREAM_SYSTEM_VOLUME_UP = 20;
    static final int ACTION_STREAM_MUSIC_VOLUME_UP = 21;
    static final int ACTION_STREAM_ALARM_VOLUME_UP = 22;
    static final int ACTION_STREAM_NOTIFICATION_VOLUME_UP = 23;
    static final int ACTION_STREAM_BLUETOOTH_VOLUME_UP = 24;
    static final int ACTION_STREAM_SYSTEM_ENFORCED_VOLUME_UP = 25;
    static final int ACTION_STREAM_TTS_VOLUME_UP = 26;
    static final int ACTION_STREAM_EXT_SPEAKER_VOLUME_UP = 27;
    static final int ACTION_MASTER_VOLUME_UP = 28;
    static final int ACTION_SUB_VOLUME_UP = 29;
    static final int ACTION_STREAM_SYSTEM_VOLUME_DOWN = 30;
    static final int ACTION_STREAM_MUSIC_VOLUME_DOWN = 31;
    static final int ACTION_STREAM_ALARM_VOLUME_DOWN = 32;
    static final int ACTION_STREAM_NOTIFICATION_VOLUME_DOWN = 33;
    static final int ACTION_STREAM_BLUETOOTH_VOLUME_DOWN = 34;
    static final int ACTION_STREAM_SYSTEM_ENFORCED_VOLUME_DOWN = 35;
    static final int ACTION_STREAM_TTS_VOLUME_DOWN = 36;
    static final int ACTION_STREAM_EXT_SPEAKER_VOLUME_DOWN = 37;
    static final int ACTION_MASTER_VOLUME_DOWN = 38;
    static final int ACTION_SUB_VOLUME_DOWN = 39;
    static final int ACTION_WINDOW0_EXIT = 40;
    static final int ACTION_WINDOW1_EXIT = 41;
    static final int ACTION_CHANGE_WINDOW_0_1 = 50;
    static final int ACTION_WINDOW0_TOGGLE_FULL = 60;
    static final int ACTION_WINDOW1_TOGGLE_FULL = 61;
    static final int ACTION_WINDOW0_TOGGLE_FLOATING = 70;
    static final int ACTION_WINDOW1_TOGGLE_FLOATING = 71;
    static final int ACTION_WINDOW0_MOVE_UP = 80;
    static final int ACTION_WINDOW1_MOVE_UP = 81;
    static final int ACTION_WINDOW0_MOVE_DOWN = 90;
    static final int ACTION_WINDOW1_MOVE_DOWN = 91;
    static final int ACTION_WINDOW0_MOVE_LEFT = 100;
    static final int ACTION_WINDOW1_MOVE_LEFT = 101;
    static final int ACTION_WINDOW0_MOVE_RIGHT = 110;
    static final int ACTION_WINDOW1_MOVE_RIGHT = 111;
    static final int ACTION_WINDOW0_SIZE_UP = 120;
    static final int ACTION_WINDOW1_SIZE_UP = 121;
    static final int ACTION_WINDOW0_SIZE_DOWN = 130;
    static final int ACTION_WINDOW1_SIZE_DOWN = 131;
    static final int ACTION_LAUNCH_APP = 140;
    static final int ACTION_SCREEN_CAPTURE = 150;

    /* KEY HANDLED RETURN */
    static final int KEY_HANDLED = -1;
    static final int KEY_UNHANDLED = 0;

    /* WINDOW MOVE */
    static final int WINDOW_MOVE_UP = 0;
    static final int WINDOW_MOVE_DOWN = 1;
    static final int WINDOW_MOVE_LEFT = 2;
    static final int WINDOW_MOVE_RIGHT = 3;

    static final int WINDOW_MOVE_UNIT = 50;

    private class ButtonActionItem {
        private int mKeyCode;
        private int mPressType;
        private int mAction;
        private String mActionArg;

        public ButtonActionItem(int keyCode, int pressType, int action, String actionArg) {
            mKeyCode = keyCode;
            mPressType = pressType;
            mAction = action;
            mActionArg = actionArg;
        }

        public int getKeyCode() {
            return mKeyCode;
        }

        public int getPressType() {
            return mPressType;
        }

        public int getAction() {
            return mAction;
        }

        public String getActionArg() {
            return mActionArg;
        }

        @Override
        public String toString() {
            String pressType = null;
            switch (mPressType) {
            case KEY_PRESS_TYPE_SHORT:
                pressType = new String("Short Pressed");
                break;
            case KEY_PRESS_TYPE_LONG:
                pressType = new String("Long Pressed");
                break;
            case KEY_PRESS_TYPE_DOUBLE:
                pressType = new String("Double Pressed");
                break;
            case KEY_PRESS_TYPE_TRIPLE:
                pressType = new String("Triple Pressed");
                break;
            }
            return "KeyCode=" + mKeyCode + "==>" + pressType + ", Action=" + mAction + ", ActionArg=" + mActionArg;
        }
    }

    private class ButtonAction {
        private int mKeyCode;
        private ButtonActionItem[] mActions;

        public ButtonAction(int keyCode) {
            mKeyCode = keyCode;
            mActions = new ButtonActionItem[KEY_PRESS_TYPE_MAX];
        }

        public int addItem(ButtonActionItem item) {
            int pressType = item.getPressType();
            if (pressType < KEY_PRESS_TYPE_MAX) {
                mActions[pressType] = item;
            } else {
                Slog.e(TAG, "ButtonAction addItem Error : invalid press type ---> " + item.getPressType());
                return -1;
            }
            return 0;
        }

        public int getKeyCode() {
            return mKeyCode;
        }

        public int runCommon(int pressType, KeyEvent event) {
            ButtonActionItem item = mActions[pressType];
            if (DEBUG_INPUT) {
                Slog.d(TAG, "runCommon --> pressType=" + pressType + ", event=" + event);
                Slog.d(TAG, "item=" + item);
            }

            int res = KEY_UNHANDLED;
            if (item != null) {
                int action = item.getAction();
                switch (action) {
                case ACTION_LCD_ON_OFF:
                    res = handlePowerKey(event.getEventTime());
                    break;
                case ACTION_GO_HOME:
                    res = handleHomeKey();
                    break;
                case ACTION_BACK:
                    res = handleBack();
                    break;
                case ACTION_MINILAUNCHER:
                    res = handleMiniLauncherToggle();
                    break;
                case ACTION_MASTER_MUTE_TOGGLE:
                    res = handleMasterMute();
                    break;
                case ACTION_SUB_MUTE_TOGGLE:
                    res = handleSubMute();
                    break;
                case ACTION_MASTER_VOLUME_UP:
                    res = handleMasterVolumeUp();
                    break;
                case ACTION_MASTER_VOLUME_DOWN:
                    res = handleMasterVolumeDown();
                    break;
                case ACTION_STREAM_SYSTEM_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_SYSTEM);
                    break;
                case ACTION_STREAM_MUSIC_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_MUSIC);
                    break;
                case ACTION_STREAM_ALARM_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_ALARM);
                    break;
                case ACTION_STREAM_NOTIFICATION_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_NOTIFICATION);
                    break;
                case ACTION_STREAM_BLUETOOTH_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_BLUETOOTH_SCO);
                    break;
                case ACTION_STREAM_SYSTEM_ENFORCED_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_SYSTEM_ENFORCED);
                    break;
                case ACTION_STREAM_TTS_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_TTS);
                    break;
                case ACTION_STREAM_EXT_SPEAKER_VOLUME_UP:
                case ACTION_SUB_VOLUME_UP:
                    res = handleStreamVolumeUp(AudioManager.STREAM_EXT_SPEAKER);
                    break;
                case ACTION_STREAM_SYSTEM_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_SYSTEM);
                    break;
                case ACTION_STREAM_MUSIC_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_MUSIC);
                    break;
                case ACTION_STREAM_ALARM_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_ALARM);
                    break;
                case ACTION_STREAM_NOTIFICATION_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_NOTIFICATION);
                    break;
                case ACTION_STREAM_BLUETOOTH_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_BLUETOOTH_SCO);
                    break;
                case ACTION_STREAM_SYSTEM_ENFORCED_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_SYSTEM_ENFORCED);
                    break;
                case ACTION_STREAM_TTS_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_TTS);
                    break;
                case ACTION_STREAM_EXT_SPEAKER_VOLUME_DOWN:
                case ACTION_SUB_VOLUME_DOWN:
                    res = handleStreamVolumeDown(AudioManager.STREAM_EXT_SPEAKER);
                    break;
                case ACTION_WINDOW0_EXIT:
                    res = handleWindowExit(0);
                    break;
                case ACTION_WINDOW1_EXIT:
                    res = handleWindowExit(1);
                    break;
                case ACTION_CHANGE_WINDOW_0_1:
                    res = handleChangeWindow(0, 1);
                    break;
                case ACTION_WINDOW0_TOGGLE_FULL:
                    res = handleWindowToggleFull(0);
                    break;
                case ACTION_WINDOW1_TOGGLE_FULL:
                    res = handleWindowToggleFull(1);
                    break;
                case ACTION_WINDOW0_TOGGLE_FLOATING:
                    res = handleWindowToggleFloating(0);
                    break;
                case ACTION_WINDOW1_TOGGLE_FLOATING:
                    res = handleWindowToggleFloating(1);
                    break;
                case ACTION_WINDOW0_MOVE_UP:
                    res = handleWindowMove(0, WINDOW_MOVE_UP);
                    break;
                case ACTION_WINDOW1_MOVE_UP:
                    res = handleWindowMove(1, WINDOW_MOVE_UP);
                    break;
                case ACTION_WINDOW0_MOVE_DOWN:
                    res = handleWindowMove(0, WINDOW_MOVE_DOWN);
                    break;
                case ACTION_WINDOW1_MOVE_DOWN:
                    res = handleWindowMove(1, WINDOW_MOVE_DOWN);
                    break;
                case ACTION_WINDOW0_MOVE_LEFT:
                    res = handleWindowMove(0, WINDOW_MOVE_LEFT);
                    break;
                case ACTION_WINDOW1_MOVE_LEFT:
                    res = handleWindowMove(1, WINDOW_MOVE_LEFT);
                    break;
                case ACTION_WINDOW0_MOVE_RIGHT:
                    res = handleWindowMove(0, WINDOW_MOVE_RIGHT);
                    break;
                case ACTION_WINDOW1_MOVE_RIGHT:
                    res = handleWindowMove(1, WINDOW_MOVE_RIGHT);
                    break;
                case ACTION_WINDOW0_SIZE_UP:
                    res = handleWindowSizeControl(0, true);
                    break;
                case ACTION_WINDOW1_SIZE_UP:
                    res = handleWindowSizeControl(1, true);
                    break;
                case ACTION_WINDOW0_SIZE_DOWN:
                    res = handleWindowSizeControl(0, false);
                    break;
                case ACTION_WINDOW1_SIZE_DOWN:
                    res = handleWindowSizeControl(1, false);
                    break;
                case ACTION_LAUNCH_APP:
                    res = handleLaunchApp(item.getActionArg());
                    break;
                case ACTION_SCREEN_CAPTURE:
                    res = handleScreenCapture();
                    break;
                default:
                    break;
                }
            }

            return res;
        }
    }

    private class ButtonActionManager {
        private Map<Integer, ButtonAction> mButtonActionMap;
        private Context mContext;
        private int mPrevRepeatCount;

        public ButtonActionManager(Context context) {
            mContext = context;
            mButtonActionMap = new HashMap<Integer, ButtonAction>();
            mPrevRepeatCount = 0;
        }

        public void init() {
            // read config, add ButtonAction
            String[] items = mContext.getResources().getStringArray(com.android.internal.R.array.config_nexell_avn_keyActionMappingTable);
            if (items == null) {
                Slog.e(TAG, "FATAL ERROR ===> can't get config_nexell_avn_keyActionMappingTable!!!");
                return;
            }
            
            for (int i = 0; i < items.length; i++) {
                String keyAction = items[i];
                ButtonActionItem bai = parseActionString(keyAction);
                if (bai != null) {
                    ButtonAction ba = getButtonAction(bai.getKeyCode());
                    if (ba == null) {
                        ba = new ButtonAction(bai.getKeyCode());
                        addButtonAction(ba);
                    }
                    ba.addItem(bai);
                    if (DEBUG_INPUT) Slog.d(TAG, "Added ButtonActionItem --> " + bai);
                }
            }
        }

        public int handleButtonEvent(KeyEvent event, int policyFlags) {
            final int keyCode = event.getKeyCode();

            ButtonAction action = getButtonAction(keyCode);
            if (action == null)
                return KEY_UNHANDLED; //ACTION_PASS_TO_USER;

            final boolean interactive = (policyFlags & FLAG_INTERACTIVE) != 0;
            final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            final boolean canceled = event.isCanceled();
            final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;
            final int repeatCount = event.getRepeatCount();
            if (down) {
                mPrevRepeatCount = repeatCount;
                return KEY_HANDLED;
            } else {
                final int pressType = pickPressType(event);
                if (DEBUG_INPUT) {
                    Slog.d(TAG, "handleButtonEvent ===>  keyCode=" + keyCode + " down=" + down + " repeatCount="
                            + repeatCount + " canceled=" + canceled + " isInjected=" + isInjected + " pressType=" 
                            + pressType);
                }
                mPrevRepeatCount = 0;
                return action.runCommon(pressType, event);
            }

            // return KEY_UNHANDLED;
            // return ACTION_PASS_TO_USER;
        }

        private ButtonAction getButtonAction(int keyCode) {
            return mButtonActionMap.get(keyCode);
        }

        private void addButtonAction(ButtonAction ba) {
            mButtonActionMap.put(ba.getKeyCode(), ba);
        }

        private int pickPressType(KeyEvent event) {
            // boolean longPress = (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) == KeyEvent.FLAG_LONG_PRESS;
            if (mPrevRepeatCount == 0) {
                return KEY_PRESS_TYPE_SHORT;
            } else {
                return KEY_PRESS_TYPE_LONG;
            }
        }

        private ButtonActionItem parseActionString(String actionString) {
            String delims = ",";
            String[] tokens = actionString.split(delims);
            if (tokens.length < 3) {
                Slog.e(TAG, "parseActionString Error : invalid Action String ---> " + actionString);
                return null;
            }

            int keyCode = Integer.parseInt(tokens[0]);
            int pressType = Integer.parseInt(tokens[1]);
            int action = Integer.parseInt(tokens[2]);
            String actionArg = null;
            if (tokens.length >= 4) {
                actionArg = tokens[3];
            }
            ButtonActionItem item = new ButtonActionItem(keyCode, pressType, action, actionArg);
            return item;
        }
    }

    /**
     * ScreenShot
     */
    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    // Assume this is called from the Handler thread.
    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHandler.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };
            if (mContext.bindServiceAsUser(
                    intent, conn, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)) {
                mScreenshotConnection = conn;
                mHandler.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private final Runnable mScreenshotRunnable = new Runnable() {
        @Override
        public void run() {
            takeScreenshot();
        }
    };

    private void cancelPendingScreenshot() {
        mHandler.removeCallbacks(mScreenshotRunnable);
    }

    /**
     * Message
     */
    private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 1;
    private static final int MSG_HIDE_MULTIWINDOW_CONTROL = 2;
    private static final int MSG_HIDE_MINILAUNCHER = 3;
    private static final int MSG_HIDE_DRAGCONTROL = 4;
    private static final int MSG_HIDE_FLOATINGCONTROL = 5;

    private static final int MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS = 3000;
    private static final int MULTIWINDOW_MINILAUNCHER_SHOW_TIMEOUT_MS = 5000;
    /**
     * Message Handler
     */
    private class PolicyHandler extends Handler {
         @Override
         public void handleMessage(Message msg) {
             switch (msg.what) {
                 case MSG_WINDOW_MANAGER_DRAWN_COMPLETE:
                     break;
                 case MSG_HIDE_MULTIWINDOW_CONTROL:
                     if (DEBUG_MESSAGE) Slog.d(TAG, "MSG_HIDE_MULTIWINDOW_CONTROL");

                     if (isShowMultiWindowControl()) { 
                         int visibility = mMultiWindowVisibility;
                         visibility &= View.MULTIWINDOW_CONTROL_CLEAR_MASK;
                         visibility |= View.MULTIWINDOW_CONTROL_HIDDEN;
                         updateSystemUIVisibility(visibility, mMultiWindowControl);
                    }
                    break;
                 case MSG_HIDE_MINILAUNCHER:
                    if (DEBUG_MESSAGE) Slog.d(TAG, "MSG_HIDE_MINILAUNCHER");

                    if (isShowMiniLauncher()) {
                        int visibility = mMultiWindowVisibility;
                        visibility &= View.MULTIWINDOW_MINILAUNCHER_CLEAR_MASK;
                        visibility |= View.MULTIWINDOW_MINILAUNCHER_HIDDEN;
                        updateSystemUIVisibility(visibility, mMultiWindowMiniLauncher);
                    }
                    break;
                case MSG_HIDE_DRAGCONTROL:
                    if (DEBUG_MESSAGE) Slog.d(TAG, "MSG_HIDE_DRAGCONTROL");

                    if (isShowDragControl()) {
                        int visibility = mMultiWindowVisibility;
                        visibility &= View.MULTIWINDOW_DRAG_CLEAR_MASK;
                        visibility |= View.MULTIWINDOW_DRAG_HIDDEN;
                        updateSystemUIVisibility(visibility, mMultiWindowDragControl);
                    }
                    break;
                case MSG_HIDE_FLOATINGCONTROL:
                    if (DEBUG_MESSAGE) Slog.d(TAG, "MSG_HIDE_FLOATINGCONTROL");

                    if (isShowFloatingControl()) {
                        int visibility = mMultiWindowVisibility;
                        visibility &= View.MULTIWINDOW_FLOATING_CLEAR_MASK;
                        visibility |= View.MULTIWINDOW_FLOATING_HIDDEN;
                        updateSystemUIVisibility(visibility, mMultiWindowFloatingControl);
                    }
                    break;
             }
         }
    }

    /**
     * Member Variables
     */
    Context mContext;
    IWindowManager mWindowManager;
    WindowManagerFuncs mWindowManagerFuncs;
    WindowManagerInternal mWindowManagerInternal;
    ButtonActionManager mButtonActionManager;
    SystemGesturesPointerEventListener mSystemGestures;
    PowerManager mPowerManager;
    boolean mScreenOff = false;
    Intent mHomeIntent;

    Display mDisplay;

    int mLandscapeRotation = 0;
    int mPortraitRotation = 0;

    int mLastSystemUiFlags;

    int mSystemLeft, mSystemTop, mSystemRight, mSystemBottom;

    boolean mSystemBooted;

    /**
     * MultiWindow
     */
    static final String PROPERTY_MULTIWINDOW_ENABLE = "persist.multiwindow_enable";
    static final String PROPERTY_MULTIWINDOW_MAX_NUM = "persist.multiwindow_max_num";
    static final String PROPERTY_MULTIWINDOW_POLICY = "persist.multiwindow_policy";

    static final int MULTIWINDOW_POLICY_PRIORITIZED = 0;
    static final int MULTIWINDOW_POLICY_FIFO = 1;

    boolean mMultiWindowEnable = false;
    int mMultiWindowMaxNum = 1;
    int mMultiWindowPolicy = MULTIWINDOW_POLICY_PRIORITIZED;
    int mCurrentLayoutWindowNumber = 0;

    WindowState mLeftWin = null;
    WindowState mRightWin = null;
    // for restore LeftWin
    WindowState mPrevLeftWin = null;
    WindowState mPrevRightWin = null;

    boolean mIsLeftFull = false;
    boolean mIsRightFull = false;

    WindowState mMultiWindowControl = null;
    WindowState mMultiWindowMiniLauncher = null;
    WindowState mMultiWindowDragControl = null;
    WindowState mMultiWindowFloatingControl = null;
    int mMultiWindowVisibility = 0;

    // Floating Mode
    boolean mIsFloatingMode = false;
    final int FLOATING_WINDOW_DEFAULT_WIDTH = 480;
    final int FLOATING_WINDOW_DEFAULT_HEIGHT = 270;
    final int FLOATING_WINDOW_SCALE_WIDTH_FACTOR = 160;
    final int FLOATING_WINDOW_SCALE_HEIGHT_FACTOR = 90;
    int mFloatingWindowLeft = 0;
    int mFloatingWindowTop = 0;
    int mFloatingWindowRight = 0;
    int mFloatingWindowBottom = 0;
    int mFloatingWindowScaleFactor = 0; 
    // width = FLOATING_WINDOW_DEFAULT_WIDTH * mFloatingWindowScaleFactor
    // height = FLOATING_WINDOW_DEFAULT_HEIGHT * mFloatingWindowScaleFactor
    // MoveMent
    private static final int MAX_FLOATING_MOVE_TRACKING_POINTERS = 32;
    private static final int UNTRACKED_POINTER = -1;
    private static final int FLOATING_WINDOW_STATE_NONE = 0;
    private static final int FLOATING_WINDOW_STATE_MOVING = 1;

    private final int[] mFloatingMovePointerId = new int[MAX_FLOATING_MOVE_TRACKING_POINTERS];
    private final float[] mFloatingDownX = new float[MAX_FLOATING_MOVE_TRACKING_POINTERS];
    private final float[] mFloatingDownY = new float[MAX_FLOATING_MOVE_TRACKING_POINTERS];
    private float mFloatingWindowX;
    private float mFloatingWindowY;
    private int mFloatingDownPointers = 0;
    private int mFloatingWindowState = FLOATING_WINDOW_STATE_NONE;

    Handler mHandler;
    final Object mServiceAcquireLock = new Object();
    IStatusBarService mStatusBarService;

    /**
     * Private Functions
     */
    private IStatusBarService getStatusBarService() {
      synchronized (mServiceAcquireLock) {
        if (mStatusBarService == null) {
          mStatusBarService = IStatusBarService.Stub.asInterface(
              ServiceManager.getService("statusbar"));
        }
        return mStatusBarService;
      }
    }

    private void loadMultiWindowSystemUiConfig() {
        final Resources res = mContext.getResources();
        mMultiWindowControlbarWidth = res.getDimensionPixelSize(com.android.internal.R.dimen.config_multiWindowControlbarWidth);
        mMultiWindowControlbarHeight = res.getDimensionPixelSize(com.android.internal.R.dimen.config_multiWindowControlbarHeight);
        mMultiWindowControlbarStartY = res.getDimensionPixelSize(com.android.internal.R.dimen.config_multiWindowControlbarStartY);
        mMinilauncherWidth = res.getDimensionPixelSize(com.android.internal.R.dimen.config_minilauncherWidth);
        mMinilauncherHeight = res.getDimensionPixelSize(com.android.internal.R.dimen.config_minilauncherHeight);
        mDragControlHeight = res.getDimensionPixelSize(com.android.internal.R.dimen.config_dragControlHeight);
        mFloatingControlHeight = res.getDimensionPixelSize(com.android.internal.R.dimen.config_floatingControlHeight);

        if (DEBUG_SYSTEM_UI) Slog.d(TAG, "MultiWindowControlbar(" + mMultiWindowControlbarWidth + "x" + mMultiWindowControlbarHeight
                + ", " + mMultiWindowControlbarStartY + ")"
                + "\n"
                + "Minilauncher(" + mMinilauncherWidth + "x" + mMinilauncherHeight + ")"
                + "\n"
                + "DragControlHeight(" + mDragControlHeight + ")"
                + "\n"
                + "FloatingControlHeight(" + mFloatingControlHeight + ")");
    }

    private boolean isShowMultiWindowControl() {
        int vis = mMultiWindowVisibility;
        return (vis & View.MULTIWINDOW_CONTROL_VISIBLE) == View.MULTIWINDOW_CONTROL_VISIBLE;
    }

    private boolean isShowMiniLauncher() {
        int vis = mMultiWindowVisibility;
        return (vis & View.MULTIWINDOW_MINILAUNCHER_VISIBLE) == View.MULTIWINDOW_MINILAUNCHER_VISIBLE;
    }

    private boolean isShowDragControl() {
        int vis = mMultiWindowVisibility;
        return (vis & View.MULTIWINDOW_DRAG_VISIBLE) == View.MULTIWINDOW_DRAG_VISIBLE;
    }

    private boolean isShowFloatingControl() {
        int vis = mMultiWindowVisibility;
        return (vis & View.MULTIWINDOW_FLOATING_VISIBLE) == View.MULTIWINDOW_FLOATING_VISIBLE;
    }

    private boolean isShowSystemUI() {
        int vis = mMultiWindowVisibility;
        return isShowMultiWindowControl() || isShowMiniLauncher() || isShowDragControl() || isShowFloatingControl();
    }

    private void updateSystemUIVisibility(int vis, WindowState win) {
      if (vis != mMultiWindowVisibility) {
        mMultiWindowVisibility = vis;
        if (DEBUG_SYSTEM_UI) Slog.d(TAG, "updateSystemUIVisibility: vis " + vis + ", win " + win);
        try {
          IStatusBarService statusbar = getStatusBarService();
          if (statusbar != null) {
            statusbar.setSystemUiVisibility(vis, 0xffffffff, win.toString());
            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
              if (isShowSystemUI()) {
                if (!win.isDisplayedLw()) {
                  win.showLw(true);
                }
              } else {
                if (win.isDisplayedLw()) {
                  win.hideLw(true);
                }
              }
            }
          }
        } catch (RemoteException e) {
          mStatusBarService = null;
        }
      }
    }

    private void showSystemUI(WindowState swipeTarget) {
      final WindowState win = swipeTarget;
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          int visibility = mMultiWindowVisibility;
          if (win == mMultiWindowControl) {
              visibility &= View.MULTIWINDOW_CONTROL_CLEAR_MASK;
              visibility |= View.MULTIWINDOW_CONTROL_VISIBLE;
          } else if (win == mMultiWindowMiniLauncher) {
              visibility &= View.MULTIWINDOW_MINILAUNCHER_CLEAR_MASK;
              visibility |= View.MULTIWINDOW_MINILAUNCHER_VISIBLE;
          } else if (win == mMultiWindowDragControl) {
              visibility &= View.MULTIWINDOW_DRAG_CLEAR_MASK;
              visibility |= View.MULTIWINDOW_DRAG_VISIBLE;
          } else if (win == mMultiWindowFloatingControl) {
              visibility &= View.MULTIWINDOW_FLOATING_CLEAR_MASK;
              visibility |= View.MULTIWINDOW_FLOATING_VISIBLE;
          }

          if (DEBUG_SYSTEM_UI) Slog.d(TAG, "showSystemUI --> win " + win);
          updateSystemUIVisibility(visibility, win);
        }
      });
    }

    private void hideSystemUI(WindowState win, long delayMillis) {
        int msg = -1;
        if (win == mMultiWindowControl) {
            msg = MSG_HIDE_MULTIWINDOW_CONTROL;
        } else if (win == mMultiWindowMiniLauncher) {
            msg = MSG_HIDE_MINILAUNCHER;
        } else if (win == mMultiWindowDragControl) {
            msg = MSG_HIDE_DRAGCONTROL;
        } else if (win == mMultiWindowFloatingControl) {
            msg = MSG_HIDE_FLOATINGCONTROL;
        }

        if (msg != -1) {
            mHandler.removeMessages(msg);
            if (delayMillis != 0)
                mHandler.sendMessageDelayed(mHandler.obtainMessage(msg), delayMillis);
            else
                mHandler.sendMessage(mHandler.obtainMessage(msg));
        }
    }

    private void hideSystemUI(WindowState win) {
        hideSystemUI(win, 0);
    }

    private int findFloatingPointerIndex(int pointerId) {
        for (int i = 0; i < mFloatingDownPointers; i++) {
            if (mFloatingMovePointerId[i] == pointerId) {
                return i;
            }
        }
        if (mFloatingDownPointers == MAX_FLOATING_MOVE_TRACKING_POINTERS || pointerId == MotionEvent.INVALID_POINTER_ID) {
            return UNTRACKED_POINTER;
        }
        mFloatingMovePointerId[mFloatingDownPointers++] = pointerId;
        return mFloatingDownPointers - 1;
    }

    private void captureFloatingControlDown(MotionEvent event, int pointerIndex) {
        final int pointerId = event.getPointerId(pointerIndex);
        final int i = findFloatingPointerIndex(pointerIndex);
        if (i != UNTRACKED_POINTER) {
            mFloatingDownX[i] = event.getX(pointerIndex);
            mFloatingDownY[i] = event.getY(pointerIndex);
        }
    }

    private void moveFloatingWindow(int xDiff, int yDiff) {
        if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "moveFloatingWindow: xDiff " + xDiff + ", yDiff " + yDiff);
        int nextLeft = mFloatingWindowLeft + xDiff;
        int nextTop = mFloatingWindowTop + yDiff;
        int nextRight = mFloatingWindowRight + xDiff;
        int nextBottom = mFloatingWindowBottom + yDiff;
        if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "moveFloatingWindow : [" + mFloatingWindowLeft + "," + mFloatingWindowTop + "] --> [" + nextLeft + "," + nextTop + "]");
        mFloatingWindowLeft = nextLeft;
        mFloatingWindowTop = nextTop;
        mFloatingWindowRight = nextRight;
        mFloatingWindowBottom = nextBottom;
        try {
            mWindowManager.prepareAppTransition(10, false);
            mWindowManager.executeAppTransition();
        } catch (Exception e) {
        }
    }

    private void layoutSpecialWindow(WindowState win, Rect parentFrame) {
        final WindowManager.LayoutParams attrs = win.getAttrs();
        final String title = attrs.getTitle().toString();
        if (title.startsWith("Starting")) {
            if (DEBUG_LAYOUT) Slog.d(TAG, "layout Starting...");
            layoutFull(parentFrame);
        } else if (title.startsWith("Toast")) {
            if (DEBUG_LAYOUT) Slog.d(TAG, "layout Toast...");
            layoutFull(parentFrame);
        } else if (title.startsWith("Error")) {
            if (DEBUG_LAYOUT) Slog.d(TAG, "layout Error...");
            layoutFull(parentFrame);
        } else if (title.startsWith("Select input method")) {
            if (DEBUG_LAYOUT) Slog.d(TAG, "layout Select input method...");
            layoutFull(parentFrame);
        } else if (title.startsWith("ScreenshotAnimation")) {
            if (DEBUG_LAYOUT) Slog.d(TAG, "ScreenshotAnimation...");
            layoutFull(parentFrame);
        } else {
            Slog.e(TAG, "Error: window is not left nor right nor Starting nor Error not Select input nor ScreenshotAnimation:  title --> " + title);
        }
    }

    private boolean isSameGroupWindow(WindowState win, String title) {
        String winTitle = win.getAttrs().getTitle().toString();
        String shortName = winTitle.split("/")[0];
        return title.startsWith(shortName);
    }

    private boolean isSameGroupWindow(WindowState win1, WindowState win2) {
        String win1Title = win1.getAttrs().getTitle().toString();
        String win2Title = win2.getAttrs().getTitle().toString();
        String shortName = win1Title.split("/")[0];
        return win2Title.startsWith(shortName);
    }

    private void layoutFloating(Rect parentFrame) {
        int scaleFactor = mFloatingWindowScaleFactor;
        if (mFloatingWindowLeft == 0) {
            mFloatingWindowLeft = mSystemRight - FLOATING_WINDOW_DEFAULT_WIDTH - (scaleFactor * FLOATING_WINDOW_SCALE_WIDTH_FACTOR);
            mFloatingWindowTop = mSystemBottom - FLOATING_WINDOW_DEFAULT_HEIGHT - (scaleFactor * FLOATING_WINDOW_SCALE_HEIGHT_FACTOR);
            mFloatingWindowRight = mSystemRight;
            mFloatingWindowBottom = mSystemBottom;
        } else {
            mFloatingWindowLeft = mFloatingWindowRight - FLOATING_WINDOW_DEFAULT_WIDTH - (scaleFactor * FLOATING_WINDOW_SCALE_WIDTH_FACTOR);
            mFloatingWindowTop = mFloatingWindowBottom - FLOATING_WINDOW_DEFAULT_HEIGHT - (scaleFactor * FLOATING_WINDOW_SCALE_HEIGHT_FACTOR);
        }

        parentFrame.left = mFloatingWindowLeft;
        parentFrame.top = mFloatingWindowTop;
        parentFrame.right = mFloatingWindowRight;
        parentFrame.bottom = mFloatingWindowBottom;
        if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Floating Layout --> " + mFloatingWindowLeft + "," + mFloatingWindowTop + "," + mFloatingWindowRight + "," + mFloatingWindowBottom);
    }

    /**
     * return TASK_UNKNOWN : no task
     * return TASK_LEFT : left
     * return TASK_RIGHT : right
     */
    private static final int TASK_UNKNOWN = -1;
    private static final int TASK_LEFT = 0;
    private static final int TASK_RIGHT = 1;
    private int whichTask(WindowState win) {
        int leftTaskId = -1;
        if (mLeftWin != null)
            leftTaskId = mLeftWin.getTaskId();
        int rightTaskId = -1;
        if (mRightWin != null)
            rightTaskId = mRightWin.getTaskId();
        int taskId = win.getTaskId();
        if (taskId == leftTaskId)
            return TASK_LEFT;
        else if (taskId == rightTaskId)
            return TASK_RIGHT;

        if (mLeftWin != null && isSameGroupWindow(mLeftWin, win))
            return TASK_LEFT;

        if (mRightWin != null && isSameGroupWindow(mRightWin, win))
            return TASK_RIGHT;

        return TASK_UNKNOWN;
    }

    private void injectKeyEvent(KeyEvent event) {
        if (DEBUG_INPUT) Slog.d(TAG, "injectKeyEvent: " + event);
        InputManager.getInstance().injectInputEvent(event,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private void sendKeyEvent(int inputSource, int keyCode, boolean longpress) {
        long now = SystemClock.uptimeMillis();
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource));
        if (longpress) {
            injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 1, 0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_LONG_PRESS,
                        inputSource));
        }
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, inputSource));
    }   

    private int handleLeftFullToggle() {
        if (!mIsLeftFull) {
            // on left full
            if (mRightWin != null) {
                if (DEBUG_MULTIWINDOW) Slog.d(TAG, "On Left Full");
                mRightWin.pauseActivityOfWindow(false);
                mPrevRightWin = mRightWin;
                mIsLeftFull = true;
            }
        } else {
            // off left full
            if (mPrevRightWin != null) {
                if (DEBUG_MULTIWINDOW) Slog.d(TAG, "Off Left Full");
                mPrevRightWin.restoreActivity();
                mPrevRightWin = null;
                mIsLeftFull = false;
            }
        }

        hideSystemUI(mMultiWindowControl);
        hideSystemUI(mMultiWindowFloatingControl);
        return KEY_HANDLED;
    }

    private int handleRightFullToggle() {
        if (!mIsRightFull) {
            // on Right full
            if (mLeftWin != null && mRightWin != null) {
                if (DEBUG_MULTIWINDOW) Slog.d(TAG, "On Right Full");
                mLeftWin.pauseActivityOfWindow(false);
                mPrevLeftWin = mLeftWin;
                mIsRightFull = true;
            }
        } else {
            // off Right full
            if (mPrevLeftWin != null) {
                if (DEBUG_MULTIWINDOW) Slog.d(TAG, "Off Right Full");
                mPrevLeftWin.restoreActivity();
                mPrevLeftWin = null;
                mIsRightFull = false;
            }
        }

        hideSystemUI(mMultiWindowControl);
        hideSystemUI(mMultiWindowFloatingControl);
        return KEY_HANDLED;
    }

    private int handleMiniLauncherToggle() {
        if (!isShowMiniLauncher()) {
            hideSystemUI(mMultiWindowControl);
            showSystemUI(mMultiWindowMiniLauncher);
            hideSystemUI(mMultiWindowMiniLauncher, MULTIWINDOW_MINILAUNCHER_SHOW_TIMEOUT_MS);
        } else {
            hideSystemUI(mMultiWindowMiniLauncher);
        }
        return KEY_HANDLED;
    }

    private int handleFloatingDocking() {
        if (mIsFloatingMode) {
            if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Off FloatingWindow Mode");
            mIsFloatingMode = false;
            mSystemGestures.enableTracking(false);
            hideSystemUI(mMultiWindowFloatingControl);
            try {
                mWindowManager.executeAppTransition();
            } catch (Exception e) {
            }
            return KEY_HANDLED;
        }
        return KEY_UNHANDLED;
    }

    private int handleFloatingExit() {
        if (mIsFloatingMode) {
            if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "FloatingWindow exit");
            if (isShowFloatingControl())
                hideSystemUI(mMultiWindowFloatingControl);
            if (mRightWin != null) {
                mRightWin.pauseActivityOfWindow(true);
            }
            return KEY_HANDLED;
        }
        return KEY_UNHANDLED;
    }

    private int handleFloatingSizeUp() {
        if (mIsFloatingMode) {
            if (mRightWin != null) {
                int floatingWindowWidth = mFloatingWindowRight - mFloatingWindowLeft;
                int floatingWindowHeight = mFloatingWindowBottom - mFloatingWindowTop;
                if (((floatingWindowWidth + FLOATING_WINDOW_SCALE_WIDTH_FACTOR) < mSystemRight) 
                        && ((floatingWindowHeight + FLOATING_WINDOW_SCALE_HEIGHT_FACTOR) < mSystemBottom)) {
                    if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "FloatingWindow Size Up: scaleFactor --> " + mFloatingWindowScaleFactor);
                    try {
                        mFloatingWindowScaleFactor++;
                        mRightWin.setLayoutNeeded(true);
                        mWindowManager.prepareAppTransition(17, false);
                        mWindowManager.executeAppTransition();
                    } catch (Exception e) {
                        mFloatingWindowScaleFactor--;
                    }
                    if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Size Up End");
                }
            }
            return KEY_HANDLED;
        }
        return KEY_UNHANDLED;
    }

    private int handleFloatingSizeDown() {
        if (mIsFloatingMode) {
            if (mRightWin != null) {
                int floatingWindowWidth = mFloatingWindowRight - mFloatingWindowLeft;
                int floatingWindowHeight = mFloatingWindowBottom - mFloatingWindowTop;
                if (((floatingWindowWidth - FLOATING_WINDOW_SCALE_WIDTH_FACTOR) >= FLOATING_WINDOW_DEFAULT_WIDTH) 
                        && ((floatingWindowHeight - FLOATING_WINDOW_SCALE_HEIGHT_FACTOR) >= FLOATING_WINDOW_DEFAULT_HEIGHT)) {
                    if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "FloatingWindow Size Down: scaleFactor --> " + mFloatingWindowScaleFactor);
                    try {
                        // mWindowManager.prepareAppTransition(10, false);
                        mFloatingWindowScaleFactor--;
                        mRightWin.setLayoutNeeded(true);
                        mWindowManager.prepareAppTransition(17, false);
                        mWindowManager.executeAppTransition();
                    } catch (Exception e) {
                        mFloatingWindowScaleFactor++;
                    }
                    if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Size Down End");
                }
            }
            return KEY_HANDLED;
        }
        return KEY_UNHANDLED;
    }

    private int handleVolumeKey(KeyEvent event) {
        MediaSessionLegacyHelper.getHelper(mContext).sendVolumeKeyEvent(event, false);
        return KEY_HANDLED;
    }

    private int handlePowerKey(long when) {
        if (mScreenOff) {
            // turn on
            mScreenOff = false;
            mPowerManager.wakeUp(when);
        } else {
            // turn off
            mScreenOff = true;
            mPowerManager.goToSleep(when, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
        }
        return KEY_HANDLED;
    }

    private int handleHomeKey() {
        mContext.startActivityAsUser(mHomeIntent, UserHandle.CURRENT);
        if (mMultiWindowEnable && mCurrentLayoutWindowNumber > 0) {
            if (DEBUG_MULTIWINDOW) Slog.d(TAG, "Home pressed, pause all windows");
            if (mLeftWin != null) {
                mLeftWin.pauseActivityOfWindow(true);
            }
            if (mRightWin != null) {
                mRightWin.pauseActivityOfWindow(true);
            }
        }
        return KEY_HANDLED;
    }

    private int handleBack() {
        sendKeyEvent(InputDevice.SOURCE_KEYBOARD, KeyEvent.KEYCODE_BACK, false);
        return KEY_HANDLED;
    }

    private int handleMasterMute() {
        MediaSessionLegacyHelper.getHelper(mContext).sendAdjustVolumeBy(AudioManager.USE_DEFAULT_STREAM_TYPE,
                MediaSessionManager.DIRECTION_MUTE, AudioManager.FLAG_SHOW_UI);
        return KEY_HANDLED;
    }

    private int handleSubMute() {
        MediaSessionLegacyHelper.getHelper(mContext).sendAdjustVolumeBy(AudioManager.STREAM_EXT_SPEAKER,
                MediaSessionManager.DIRECTION_MUTE, AudioManager.FLAG_SHOW_UI);
        return KEY_HANDLED;
    }

    private int handleVolumeCommon(int streamType, boolean isUp) {
        int direction = isUp ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER; 
        MediaSessionLegacyHelper.getHelper(mContext).sendAdjustVolumeBy(streamType,
                direction, AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND);
        return KEY_HANDLED;
    }

    private int handleMasterVolumeUp() {
        return handleVolumeCommon(AudioManager.USE_DEFAULT_STREAM_TYPE, true);
    }

    private int handleMasterVolumeDown() {
        return handleVolumeCommon(AudioManager.USE_DEFAULT_STREAM_TYPE, false);
    }

    private int handleStreamVolumeUp(int streamType) {
        return handleVolumeCommon(streamType, true);
    }

    private int handleStreamVolumeDown(int streamType) {
        return handleVolumeCommon(streamType, false);
    }

    private int handleWindowExit(int winnum) {
        WindowState win = null;

        switch (winnum) {
        case 0:
            if (mLeftWin != null)
                win = mLeftWin;
            break;
        case 1:
            if (mRightWin != null) {
                win = mRightWin;
                if (isShowFloatingControl())
                    hideSystemUI(mMultiWindowFloatingControl);
            }
            break;
        default:
            break;
        }

        if (win != null) {
            win.pauseActivityOfWindow(true);
            return KEY_HANDLED;
        }

        return KEY_UNHANDLED;
    }

    private int handleChangeWindow(int win0, int win1) {
        if (win0 == 0 && win1 == 1) {
            if (DEBUG_MULTIWINDOW) Slog.d(TAG, "handleChangeWindow --> current window number:" + mCurrentLayoutWindowNumber
                    + ", left:" + mLeftWin
                    + ", right:" + mRightWin);
            if (isMultiWindowActivated()) {
                mLeftWin.changeLeftRight();
                try {
                    mWindowManager.prepareAppTransition(10, false);
                    mWindowManager.executeAppTransition();
                } catch (Exception e) {
                }
                return KEY_HANDLED;
            }
        }
        return KEY_UNHANDLED;
    }

    private int handleWindowToggleFull(int winnum) {
        if (winnum == 0) {
            return handleLeftFullToggle();
        } else if (winnum == 1) {
            return handleRightFullToggle();
        }
        return KEY_UNHANDLED;
    }

    private boolean isMultiWindowActivated() {
        return mCurrentLayoutWindowNumber > 1 && mLeftWin != null && mRightWin != null;
    }

    private int handleWindowMove(int winnum, int direction) {
        if (winnum == 1) {
            if (mIsFloatingMode) {
                int xDiff = 0;
                int yDiff = 0;
                switch (direction) {
                case WINDOW_MOVE_UP:
                    yDiff -= WINDOW_MOVE_UNIT;
                    break;
                case WINDOW_MOVE_DOWN:
                    yDiff += WINDOW_MOVE_UNIT;
                    break;
                case WINDOW_MOVE_LEFT:
                    xDiff -= WINDOW_MOVE_UNIT;
                    break;
                case WINDOW_MOVE_RIGHT:
                    xDiff += WINDOW_MOVE_UNIT;
                    break;
                default:
                    return KEY_UNHANDLED;
                }
                mFloatingWindowX += xDiff;
                mFloatingWindowY += yDiff;
                moveFloatingWindow(xDiff, yDiff);
                return KEY_HANDLED;
            }
        }
        return KEY_UNHANDLED;
    }

    private int handleWindowSizeControl(int winnum, boolean isUp) {
        if (winnum == 1) {
            return isUp ? handleFloatingSizeUp() : handleFloatingSizeDown();
        }
        return KEY_UNHANDLED;
    }

    private int handleWindowToggleFloating(int winnum) {
        if (winnum == 1) {
            if (DEBUG_MULTIWINDOW) Slog.d(TAG, "handleWindowToggleFloating --> isMultiWindowActivated(): " + isMultiWindowActivated()
                    + ", mFloatingMode: " + mIsFloatingMode); 
            if (isMultiWindowActivated()) {
                if (!mIsFloatingMode) {
                    mIsFloatingMode = true;
                    mSystemGestures.enableTracking(true);
                    try {
                        mWindowManager.prepareAppTransition(10, false);
                        mWindowManager.executeAppTransition();
                    } catch (Exception e) {
                    }
                } else {
                    mIsFloatingMode = false;
                    mSystemGestures.enableTracking(false);
                    try {
                        mWindowManager.prepareAppTransition(10, false);
                        mWindowManager.executeAppTransition();
                    } catch (Exception e) {
                    }
                }
                return KEY_HANDLED;
            }
        }
        return KEY_UNHANDLED;
    }

    private int handleLaunchApp(String app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(ComponentName.unflattenFromString(app));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        return KEY_HANDLED;
    }

    private int handleScreenCapture() {
        cancelPendingScreenshot();
        mHandler.post(mScreenshotRunnable);
        return KEY_HANDLED;
    }

    private boolean handleKeyEvent(KeyEvent event, int keyCode, boolean down, boolean canceled, boolean interactive, boolean isInjected) {
        boolean handled = false;
        if (down) {
            if (keyCode == 96) {
                handled = true;
                handleLeftFullToggle();
            } else if (keyCode == 97) {
                handled = true;
                handleRightFullToggle();
            } else if (keyCode == 131) {
                handled = true;
                handleMiniLauncherToggle();
            } else if (keyCode == 100) {
                handled = true;
                handleFloatingDocking();
            } else if (keyCode == 101) {
                handled = true;
                handleFloatingExit();
            } else if (keyCode == 98) {
                handled = true;
                handleFloatingSizeUp();
            } else if (keyCode == 99) {
                handled = true;
                handleFloatingSizeDown();
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                handled = true;
                handleVolumeKey(event);
            } else if (keyCode == KeyEvent.KEYCODE_POWER) {
                handled = true;
                handlePowerKey(event.getEventTime());
            } else if (keyCode == KeyEvent.KEYCODE_HOME) {
                handled = true;
                handleHomeKey();
            }
        } else {
            // up
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                handled = true;
            } else if (keyCode == KeyEvent.KEYCODE_POWER) {
                handled = true;
            } else if (keyCode == KeyEvent.KEYCODE_HOME) {
                handled = true;
            }
        }

        return handled;
    }
    /**
     * Public Interface
     */
    /** {@inheritDoc} */
    @Override
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowManager = windowManager;
        mWindowManagerFuncs = windowManagerFuncs;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);

        mButtonActionManager = new ButtonActionManager(context);
        mButtonActionManager.init();

        loadMultiWindowSystemUiConfig();

        mSystemGestures = new SystemGesturesPointerEventListener(context,
                new SystemGesturesPointerEventListener.Callbacks() {
                    @Override
                    public void onSwipeFromTop() {
                      if (DEBUG_GESTURE) Slog.d(TAG, "onSwipeFromTop ---> ");
                      if (mCurrentLayoutWindowNumber > 0) {
                          // if (mCurrentLayoutWindowNumber > 1
                          if (isMultiWindowActivated()
                              && (!mIsFloatingMode)
                              && isShowDragControl()
                              && mSystemGestures.getLastX() > (mSystemRight/2)) {
                              if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Enter to FloatingMode!!!");
                              mIsFloatingMode = true;
                              hideSystemUI(mMultiWindowDragControl);
                              mSystemGestures.enableTracking(true);
                              try {
                                  mWindowManager.executeAppTransition();
                              } catch (Exception e) {
                              }
                          } else {
                              showSystemUI(mMultiWindowDragControl);
                              hideSystemUI(mMultiWindowDragControl, MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS);
                          }
                      }
                    }
                    @Override
                    public void onSwipeFromBottom() {
                    }
                    @Override
                    public void onSwipeFromRight() {
                      if (DEBUG_GESTURE) Slog.d(TAG, "onSwipeFromRight ---> ");
                      showSystemUI(mMultiWindowControl);
                      hideSystemUI(mMultiWindowControl, MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS);
                    }
                    @Override
                    public void onSwipeFromRightAtTop(float fromX, float toX) {
                      if (DEBUG_GESTURE) Slog.d(TAG, "onSwipeFromRightAtTop ---> " + "from: " + fromX + ", to: " + toX);

                      if (isShowDragControl()) {
                          long hideDelay = MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS;
                          // if (mCurrentLayoutWindowNumber > 1) {
                          if (isMultiWindowActivated()) {
                              if (fromX < (mSystemRight/2)) {
                                  // remove left win
                                  if (mLeftWin != null) {
                                      mPrevLeftWin = mLeftWin;
                                      if (DEBUG_MULTIWINDOW) Slog.d(TAG, "Backup LeftWin --> " + mPrevLeftWin);
                                      mLeftWin.pauseActivityOfWindow(true);
                                      // mLeftWin.pauseActivityOfWindow(false);
                                      hideDelay = 0;
                                  }
                              } else if (fromX > (mSystemRight/2)) {
                                  // change left, right
                                  if (mLeftWin != null) {
                                      mLeftWin.changeLeftRight();
                                      hideDelay = 0;
                                  }
                              }
                          } else if (mLeftWin != null) {
                              mLeftWin.pauseActivityOfWindow(true);
                              hideDelay = 0;
                          }
                          hideSystemUI(mMultiWindowDragControl, hideDelay);
                      }
                    }
                    @Override
                    public void onSwipeFromLeftAtTop(float fromX, float toX) {
                      if (DEBUG_GESTURE) Slog.d(TAG, "onSwipeFromLeftAtTop ---> " + "from: " + fromX + ", to: " + toX);

                      if (isShowDragControl()) {
                          long hideDelay = MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS;
                          // if (mCurrentLayoutWindowNumber > 1) {
                          if (isMultiWindowActivated()) {
                              if (fromX > (mSystemRight/2)) {
                                  // remove right win
                                  if (mRightWin != null) {
                                      mRightWin.pauseActivityOfWindow(true);
                                      hideDelay = 0;
                                  }
                              } else if (fromX < (mSystemRight/2)) {
                                  // change left, right
                                  if (mLeftWin != null) {
                                      mLeftWin.changeLeftRight();
                                      hideDelay = 0;
                                  }
                              }
                          } else if (mLeftWin != null) {
                              mLeftWin.pauseActivityOfWindow(true);
                              hideDelay = 0;
                          }
                          hideSystemUI(mMultiWindowDragControl, hideDelay);
                      }
                    }
                    @Override
                    public void onSwipeFromTopAtMid() {
                      if (DEBUG_GESTURE) Slog.d(TAG, "onSwipeFromTopAtMid ---> ");
                      if (mCurrentLayoutWindowNumber > 0) {
                          showSystemUI(mMultiWindowDragControl);
                          hideSystemUI(mMultiWindowDragControl, MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS);
                      }
                    }
                    @Override
                    public void onDebug() {
                    }
                    @Override
                    public void onTracking(MotionEvent event) {
                        if ((!mIsFloatingMode) || (mCurrentLayoutWindowNumber < 2))
                            return;

                        int action = event.getActionMasked();
                        float x, y;
                        long time;
                        if (action == MotionEvent.ACTION_DOWN) {
                            x = event.getX(0);
                            y = event.getY(0);
                            if (x > mFloatingWindowLeft && x < mFloatingWindowRight
                                && y > mFloatingWindowTop && y < mFloatingWindowBottom) {
                                if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "ACTION_DOWN On Floating Window: x " + x + ", y " + y);
                                // show SystemUI
                                if (isShowFloatingControl()) {
                                    hideSystemUI(mMultiWindowFloatingControl, MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS);
                                } else {
                                    showSystemUI(mMultiWindowFloatingControl);
                                    hideSystemUI(mMultiWindowFloatingControl, MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS);
                                }
                            } else if (x > mFloatingWindowLeft && x < mFloatingWindowRight
                                       && y > (mFloatingWindowTop - mFloatingControlHeight) && y < mFloatingWindowTop) {
                                if (isShowFloatingControl()) {
                                    if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "ACTION_DOWN On FloatingControl: x " + x + ", y " + y);
                                    mFloatingDownPointers = 0;
                                    // captureFloatingControlDown(event, 0);
                                    mFloatingWindowX = x;
                                    mFloatingWindowY = y;
                                    mHandler.removeMessages(MSG_HIDE_FLOATINGCONTROL);
                                    mFloatingWindowState = FLOATING_WINDOW_STATE_MOVING;
                                }
                            }
                        } else if (action == MotionEvent.ACTION_MOVE) {
                            if (isShowFloatingControl()) {
                                if (mFloatingWindowState == FLOATING_WINDOW_STATE_MOVING) {
                                    // get last pointer
                                    final int pointerCount = event.getPointerCount();
                                    final int lastPointer = pointerCount - 1;
                                    final float lastX = event.getX(lastPointer);
                                    final float lastY = event.getY(lastPointer);
                                    Slog.v(TAG, "Move Event: x " + lastX + ", y " + lastY);
                                    int xDiff = (int)(lastX - mFloatingWindowX);
                                    int yDiff = (int)(lastY - mFloatingWindowY);
                                    if (Math.abs(xDiff) > 5 || Math.abs(yDiff) > 5) {
                                        mFloatingWindowX = lastX;
                                        mFloatingWindowY = lastY;
                                        moveFloatingWindow(xDiff, yDiff);
                                    }
                                // } else {
                                //     Slog.e(TAG, "Why get MotionEvent.ACTION_MOVE when FloatingWindow State is not moving?");
                                }
                            }
                        } else if (action == MotionEvent.ACTION_UP) {
                            if (isShowFloatingControl()) {
                                if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "ACTION_UP On FloatingControl");
                                mFloatingWindowState = FLOATING_WINDOW_STATE_NONE;
                                hideSystemUI(mMultiWindowFloatingControl, MULTIWINDOW_CONTROL_SHOW_TIMEOUT_MS);
                            }
                        }
                    }
                });
        mWindowManagerFuncs.registerPointerEventListener(mSystemGestures);

        // MULTIWINDOW
        try {
            mMultiWindowEnable = "1".equals(SystemProperties.get(PROPERTY_MULTIWINDOW_ENABLE));
            if (mMultiWindowEnable) {
                mMultiWindowMaxNum = SystemProperties.getInt(PROPERTY_MULTIWINDOW_MAX_NUM, 2);
                mMultiWindowPolicy = SystemProperties.getInt(PROPERTY_MULTIWINDOW_POLICY, 0);
            }
        } catch (Exception e) {
        }

        mMultiWindowVisibility = View.MULTIWINDOW_CONTROL_HIDDEN 
            | View.MULTIWINDOW_MINILAUNCHER_HIDDEN
            | View.MULTIWINDOW_DRAG_HIDDEN;

        mHandler = new PolicyHandler();

        mPowerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);

        mHomeIntent = new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @Override
    public void setInitialDisplaySize(Display display, int width, int height, int density) {
        if (mContext == null || display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            return;
        }

        mDisplay = display;
        if (width > height) {
            mLandscapeRotation = Surface.ROTATION_0;
            mPortraitRotation = Surface.ROTATION_270;
        } else {
            mPortraitRotation = Surface.ROTATION_0;
            mLandscapeRotation = Surface.ROTATION_90;
        }
    }

    @Override
    public boolean isDefaultOrientationForced() {
        // TODO : check landscape --> portrait display policy for Portrait AVN Device
        return true;
    }

    @Override
    public void setDisplayOverscan(Display display, int left, int top, int right, int bottom) {
        // not use overscan in AVN, do nothing
    }

    /** {@inheritDoc} */
    @Override
    public int checkAddPermission(WindowManager.LayoutParams attrs, int[] outAppOp) {
        int type = attrs.type;

        outAppOp[0] = AppOpsManager.OP_NONE;

        if (!((type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW)
                || (type >= FIRST_SUB_WINDOW && type <= LAST_SUB_WINDOW)
                || (type >= FIRST_SYSTEM_WINDOW && type <= LAST_SYSTEM_WINDOW))) {
            return WindowManagerGlobal.ADD_INVALID_TYPE;
        }

        if (type < FIRST_SYSTEM_WINDOW || type > LAST_SYSTEM_WINDOW) {
            // Window manager will make sure these are okay.
            return WindowManagerGlobal.ADD_OKAY;
        }
        String permission = null;
        switch (type) {
            case TYPE_TOAST:
                // XXX right now the app process has complete control over
                // this...  should introduce a token to let the system
                // monitor/control what they are doing.
                outAppOp[0] = AppOpsManager.OP_TOAST_WINDOW;
                break;
            case TYPE_DREAM:
            case TYPE_INPUT_METHOD:
            case TYPE_WALLPAPER:
            case TYPE_PRIVATE_PRESENTATION:
            case TYPE_VOICE_INTERACTION:
            case TYPE_ACCESSIBILITY_OVERLAY:
                // The window manager will check these.
                break;
            case TYPE_PHONE:
            case TYPE_PRIORITY_PHONE:
            case TYPE_SYSTEM_ALERT:
            case TYPE_SYSTEM_ERROR:
            case TYPE_SYSTEM_OVERLAY:
                permission = android.Manifest.permission.SYSTEM_ALERT_WINDOW;
                outAppOp[0] = AppOpsManager.OP_SYSTEM_ALERT_WINDOW;
                break;
            default:
                permission = android.Manifest.permission.INTERNAL_SYSTEM_WINDOW;
        }
        if (permission != null) {
            if (mContext.checkCallingOrSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return WindowManagerGlobal.ADD_PERMISSION_DENIED;
            }
        }
        return WindowManagerGlobal.ADD_OKAY;
    }

    @Override
    public boolean checkShowToOwnerOnly(WindowManager.LayoutParams attrs) {

        // If this switch statement is modified, modify the comment in the declarations of
        // the type in {@link WindowManager.LayoutParams} as well.
        switch (attrs.type) {
            default:
                // These are the windows that by default are shown only to the user that created
                // them. If this needs to be overridden, set
                // {@link WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS} in
                // {@link WindowManager.LayoutParams}. Note that permission
                // {@link android.Manifest.permission.INTERNAL_SYSTEM_WINDOW} is required as well.
                if ((attrs.privateFlags & PRIVATE_FLAG_SHOW_FOR_ALL_USERS) == 0) {
                    return true;
                }
                break;

            // These are the windows that by default are shown to all users. However, to
            // protect against spoofing, check permissions below.
            case TYPE_APPLICATION_STARTING:
            case TYPE_BOOT_PROGRESS:
            case TYPE_DISPLAY_OVERLAY:
            case TYPE_HIDDEN_NAV_CONSUMER:
            case TYPE_KEYGUARD_SCRIM:
            case TYPE_KEYGUARD_DIALOG:
            case TYPE_MAGNIFICATION_OVERLAY:
            case TYPE_NAVIGATION_BAR:
            case TYPE_NAVIGATION_BAR_PANEL:
            case TYPE_PHONE:
            case TYPE_POINTER:
            case TYPE_PRIORITY_PHONE:
            case TYPE_SEARCH_BAR:
            case TYPE_STATUS_BAR:
            case TYPE_STATUS_BAR_PANEL:
            case TYPE_STATUS_BAR_SUB_PANEL:
            case TYPE_SYSTEM_DIALOG:
            case TYPE_UNIVERSE_BACKGROUND:
            case TYPE_VOLUME_OVERLAY:
            case TYPE_PRIVATE_PRESENTATION:
            case TYPE_MULTIWINDOW_CONTROL:
            case TYPE_MULTIWINDOW_MINILAUNCHER:
                break;
        }

        // Check if third party app has set window to system window type.
        return mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INTERNAL_SYSTEM_WINDOW)
                        != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void adjustWindowParamsLw(WindowManager.LayoutParams attrs) {
        // TODO: called from relayoutWindow() in WindowManagerService
        // Currently, do nothing
    }

    /** {@inheritDoc} */
    @Override
    public void adjustConfigurationLw(Configuration config, int keyboardPresence,
            int navigationPresence) {
        // TODO: called from computeScreenConfigurationLocked() in WindowManagerService
        // Currently, do nothing
    }

    /** {@inheritDoc} */
    @Override
    public int windowTypeToLayerLw(int type) {
        if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
            return 2;
        }
        switch (type) {
        case TYPE_UNIVERSE_BACKGROUND:
            return 1;
        case TYPE_PRIVATE_PRESENTATION:
            return 2;
        case TYPE_WALLPAPER:
            // wallpaper is at the bottom, though the window manager may move it.
            return 2;
        case TYPE_PHONE:
            return 3;
        case TYPE_SEARCH_BAR:
            return 4;
        case TYPE_VOICE_INTERACTION:
            // voice interaction layer is almost immediately above apps.
            return 5;
        case TYPE_SYSTEM_DIALOG:
            return 6;
        case TYPE_TOAST:
            // toasts and the plugged-in battery thing
            return 7;
        case TYPE_PRIORITY_PHONE:
            // SIM errors and unlock.  Not sure if this really should be in a high layer.
            return 8;
        case TYPE_DREAM:
            // used for Dreams (screensavers with TYPE_DREAM windows)
            return 9;
        case TYPE_SYSTEM_ALERT:
            // like the ANR / app crashed dialogs
            return 10;
        case TYPE_INPUT_METHOD:
            // on-screen keyboards and other such input method user interfaces go here.
            return 11;
        case TYPE_INPUT_METHOD_DIALOG:
            // on-screen keyboards and other such input method user interfaces go here.
            return 12;
        case TYPE_KEYGUARD_SCRIM:
            // the safety window that shows behind keyguard while keyguard is starting
            return 13;
        case TYPE_STATUS_BAR_SUB_PANEL:
            return 14;
        case TYPE_STATUS_BAR:
            return 15;
        case TYPE_STATUS_BAR_PANEL:
            return 16;
        case TYPE_KEYGUARD_DIALOG:
            return 17;
        case TYPE_VOLUME_OVERLAY:
            // the on-screen volume indicator and controller shown when the user
            // changes the device volume
            return 18;
        case TYPE_SYSTEM_OVERLAY:
            // the on-screen volume indicator and controller shown when the user
            // changes the device volume
            return 19;
        case TYPE_NAVIGATION_BAR:
        case TYPE_MULTIWINDOW_CONTROL:
        case TYPE_MULTIWINDOW_MINILAUNCHER:
            // the navigation bar, if available, shows atop most things
            return 20;
        case TYPE_NAVIGATION_BAR_PANEL:
            // some panels (e.g. search) need to show on top of the navigation bar
            return 21;
        case TYPE_SYSTEM_ERROR:
            // system-level error dialogs
            return 22;
        case TYPE_MAGNIFICATION_OVERLAY:
            // used to highlight the magnified portion of a display
            return 23;
        case TYPE_DISPLAY_OVERLAY:
            // used to simulate secondary display devices
            return 24;
        case TYPE_DRAG:
            // the drag layer: input for drag-and-drop is associated with this window,
            // which sits above all other focusable windows
            return 25;
        case TYPE_ACCESSIBILITY_OVERLAY:
            // overlay put by accessibility services to intercept user interaction
            return 26;
        case TYPE_SECURE_SYSTEM_OVERLAY:
            return 27;
        case TYPE_BOOT_PROGRESS:
            return 28;
        case TYPE_POINTER:
            // the (mouse) pointer layer
            return 29;
        case TYPE_HIDDEN_NAV_CONSUMER:
            return 30;
        }
        Log.e(TAG, "Unknown window type: " + type);
        return 2;
    }

    static final int APPLICATION_MEDIA_SUBLAYER = -2;
    static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
    static final int APPLICATION_PANEL_SUBLAYER = 1;
    static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;

    /** {@inheritDoc} */
    @Override
    public int subWindowTypeToLayerLw(int type) {
        switch (type) {
        case TYPE_APPLICATION_PANEL:
        case TYPE_APPLICATION_ATTACHED_DIALOG:
            return APPLICATION_PANEL_SUBLAYER;
        case TYPE_APPLICATION_MEDIA:
            return APPLICATION_MEDIA_SUBLAYER;
        case TYPE_APPLICATION_MEDIA_OVERLAY:
            return APPLICATION_MEDIA_OVERLAY_SUBLAYER;
        case TYPE_APPLICATION_SUB_PANEL:
            return APPLICATION_SUB_PANEL_SUBLAYER;
        }
        Log.e(TAG, "Unknown sub-window type: " + type);
        return 0;
    }

    @Override
    public int getMaxWallpaperLayer() {
        return windowTypeToLayerLw(TYPE_STATUS_BAR);
    }

    @Override
    public int getAboveUniverseLayer() {
        return windowTypeToLayerLw(TYPE_SYSTEM_ERROR);
    }

    @Override
    public int getNonDecorDisplayWidth(int fullWidth, int fullHeight, int rotation) {
        return fullWidth;
    }

    @Override
    public int getNonDecorDisplayHeight(int fullWidth, int fullHeight, int rotation) {
        return fullHeight;
    }

    @Override
    public int getConfigDisplayWidth(int fullWidth, int fullHeight, int rotation) {
        return fullWidth;
    }

    @Override
    public int getConfigDisplayHeight(int fullWidth, int fullHeight, int rotation) {
        return fullHeight;
    }

    @Override
    public boolean isForceHiding(WindowManager.LayoutParams attrs) {
        return false;
    }

    @Override
    public boolean isKeyguardHostWindow(WindowManager.LayoutParams attrs) {
        return false;
    }

    @Override
    public boolean canBeForceHidden(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
            case TYPE_NAVIGATION_BAR:
            case TYPE_WALLPAPER:
            case TYPE_DREAM:
            case TYPE_UNIVERSE_BACKGROUND:
            case TYPE_KEYGUARD_SCRIM:
                return false;
            default:
                return true;
        }
    }

    @Override
    public WindowState getWinShowWhenLockedLw() {
        // called by WindowAnimator
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public View addStartingWindow(IBinder appToken, String packageName, int theme,
            CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes,
            int icon, int logo, int windowFlags) {
        if (!SHOW_STARTING_ANIMATIONS) {
            return null;
        }
        if (packageName == null) {
            return null;
        }

        WindowManager wm = null;
        View view = null;

        try {
            Context context = mContext;
            if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "addStartingWindow " + packageName
                    + ": nonLocalizedLabel=" + nonLocalizedLabel + " theme="
                    + Integer.toHexString(theme));
            if (theme != context.getThemeResId() || labelRes != 0) {
                try {
                    context = context.createPackageContext(packageName, 0);
                    context.setTheme(theme);
                } catch (PackageManager.NameNotFoundException e) {
                    // Ignore
                }
            }

            Window win = PolicyManager.makeNewWindow(context);
            final TypedArray ta = win.getWindowStyle();
            if (ta.getBoolean(
                        com.android.internal.R.styleable.Window_windowDisablePreview, false)
                || ta.getBoolean(
                        com.android.internal.R.styleable.Window_windowShowWallpaper,false)) {
                return null;
            }

            Resources r = context.getResources();
            win.setTitle(r.getText(labelRes, nonLocalizedLabel));

            win.setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING);
            // Force the window flags: this is a fake window, so it is not really
            // touchable or focusable by the user.  We also add in the ALT_FOCUSABLE_IM
            // flag because we do know that the next window will take input
            // focus, so we want to get the IME window up on top of us right away.
            win.setFlags(
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                windowFlags|
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            win.setDefaultIcon(icon);
            win.setDefaultLogo(logo);

            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            final WindowManager.LayoutParams params = win.getAttributes();
            params.token = appToken;
            params.packageName = packageName;
            params.windowAnimations = win.getWindowStyle().getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
            params.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_FAKE_HARDWARE_ACCELERATED;
            params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;

            if (!compatInfo.supportsScreen()) {
                params.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_COMPATIBLE_WINDOW;
            }

            params.setTitle("Starting " + packageName);

            wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            view = win.getDecorView();

            if (win.isFloating()) {
                // Whoops, there is no way to display an animation/preview
                // of such a thing!  After all that work...  let's skip it.
                // (Note that we must do this here because it is in
                // getDecorView() where the theme is evaluated...  maybe
                // we should peek the floating attribute from the theme
                // earlier.)
                return null;
            }

            if (DEBUG_STARTING_WINDOW) Slog.d(
                TAG, "Adding starting window for " + packageName
                + " / " + appToken + ": "
                + (view.getParent() != null ? view : null));

            wm.addView(view, params);

            // Only return the view if it was successfully added to the
            // window manager... which we can tell by it having a parent.
            return view.getParent() != null ? view : null;
        } catch (WindowManager.BadTokenException e) {
            // ignore
            Log.w(TAG, appToken + " already running, starting window not displayed. " +
                    e.getMessage());
        } catch (RuntimeException e) {
            // don't crash if something else bad happens, for example a
            // failure loading resources because we are loading from an app
            // on external storage that has been unmounted.
            Log.w(TAG, appToken + " failed creating starting window", e);
        } finally {
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                wm.removeViewImmediate(view);
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void removeStartingWindow(IBinder appToken, View window) {
        if (DEBUG_STARTING_WINDOW) Slog.d(TAG, "Removing starting window for " + appToken + ": "
                + window + " Callers=" + Debug.getCallers(4));

        if (window != null) {
            WindowManager wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(window);
        }
    }

    @Override
    public int prepareAddWindowLw(WindowState win, WindowManager.LayoutParams attrs) {
        // called by WindowManager, addWindow
        switch (attrs.type) {
            case TYPE_MULTIWINDOW_CONTROL:
                if (mMultiWindowControl != null && mMultiWindowControl.isAlive())
                    return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                if (DEBUG_MULTIWINDOW) Slog.d(TAG, "prepareAddWindowLw --> add MultiWindowControl " + win);
                mMultiWindowControl = win;
                break;
            case TYPE_MULTIWINDOW_MINILAUNCHER:
                if (mMultiWindowMiniLauncher != null && mMultiWindowMiniLauncher.isAlive())
                    return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                mMultiWindowMiniLauncher = win;
                break;
            case TYPE_MULTIWINDOW_DRAGCONTROL:
                if (mMultiWindowDragControl != null && mMultiWindowDragControl.isAlive())
                    return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                mMultiWindowDragControl = win;
                break;
            case TYPE_MULTIWINDOW_FLOATINGCONTROL:
                if (mMultiWindowFloatingControl != null && mMultiWindowFloatingControl.isAlive())
                    return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                mMultiWindowFloatingControl = win;
                break;
        }
        return WindowManagerGlobal.ADD_OKAY;
    }

    /** {@inheritDoc} */
    @Override
    public void removeWindowLw(WindowState win) {
        if (mMultiWindowControl == win) {
            mMultiWindowControl = null;
        } else if (mMultiWindowMiniLauncher == win) {
            mMultiWindowMiniLauncher = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int selectAnimationLw(WindowState win, int transit) {
        // called by WindowStateAnimator, applyAnimationLocked().
        return 0; // selected by WindowStateAnimator
    }

    @Override
    public void selectRotationAnimationLw(int anim[]) {
        // do nothing, set default
        anim[0] = anim[1] = 0;
    }

    @Override
    public boolean validateRotationAnimationLw(int exitAnimId, int enterAnimId,
            boolean forceDefault) {
        // do nothing, use default
        return true;
    }

    private final LogDecelerateInterpolator mLogDecelerateInterpolator
            = new LogDecelerateInterpolator(100, 0);

    @Override
    public Animation createForceHideEnterAnimation(boolean onWallpaper,
            boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_behind_enter_fade_in);
        }

        AnimationSet set = (AnimationSet) AnimationUtils.loadAnimation(mContext, onWallpaper ?
                    R.anim.lock_screen_behind_enter_wallpaper :
                    R.anim.lock_screen_behind_enter);

        // TODO: Use XML interpolators when we have log interpolators available in XML.
        final List<Animation> animations = set.getAnimations();
        for (int i = animations.size() - 1; i >= 0; --i) {
            animations.get(i).setInterpolator(mLogDecelerateInterpolator);
        }

        return set;
    }

    @Override
    public Animation createForceHideWallpaperExitAnimation(boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return null;
        } else {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_wallpaper_exit);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        // called by InputFlinger, InputDispatcher::doInterceptKeyBeforeDispatchingLockedInterruptible()
        // TODO : must be handled ButtonActionManager

        final int keyCode = event.getKeyCode();
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTi keyCode=" + keyCode + " down=" + down + " repeatCount="
                    + repeatCount + " canceled=" + canceled);
        }

        if (true)
            return mButtonActionManager.handleButtonEvent(event, policyFlags);
        
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public KeyEvent dispatchUnhandledKey(WindowState win, KeyEvent event, int policyFlags) {
        // Note: This method is only called if the initial down was unhandled.
        // called by InputFlinger, InputDispatcher::afterKeyEventLockedInterruptible()
        // TODO : must be handled ButtonActionManager
        if (DEBUG_INPUT) {
            Slog.d(TAG, "Unhandled key: win=" + win + ", action=" + event.getAction()
                    + ", flags=" + event.getFlags()
                    + ", keyCode=" + event.getKeyCode()
                    + ", scanCode=" + event.getScanCode()
                    + ", metaState=" + event.getMetaState()
                    + ", repeatCount=" + event.getRepeatCount()
                    + ", policyFlags=" + policyFlags);
        }

        return null;
    }

    @Override
    public void showRecentApps() {
    }

    @Override
    public int adjustSystemUiVisibilityLw(int visibility) {
        // TODO : handle MultiWindowControl, MultiWindowMiniLauncher
        if (DEBUG_SYSTEM_UI) Slog.d(TAG, "adjustSystemUiVisibilityLw : vis " + visibility);

        if (isShowMultiWindowControl()) {
            if ((visibility & View.MULTIWINDOW_CONTROL_HIDDEN) == View.MULTIWINDOW_CONTROL_HIDDEN) {
                hideSystemUI(mMultiWindowControl);
            }
        } else {
            if ((visibility & View.MULTIWINDOW_CONTROL_VISIBLE) == View.MULTIWINDOW_CONTROL_VISIBLE) {
                showSystemUI(mMultiWindowControl);
            }
        }

        if (isShowMiniLauncher()) {
            if ((visibility & View.MULTIWINDOW_MINILAUNCHER_HIDDEN) == View.MULTIWINDOW_MINILAUNCHER_HIDDEN) {
                hideSystemUI(mMultiWindowMiniLauncher);
            }
        } else {
            if ((visibility & View.MULTIWINDOW_CONTROL_VISIBLE) == View.MULTIWINDOW_CONTROL_VISIBLE) {
                showSystemUI(mMultiWindowMiniLauncher);
            }
        }

        if (isShowDragControl()) {
            if ((visibility & View.MULTIWINDOW_DRAG_HIDDEN) == View.MULTIWINDOW_DRAG_HIDDEN) {
                hideSystemUI(mMultiWindowDragControl);
            }
        } else {
            if ((visibility & View.MULTIWINDOW_DRAG_VISIBLE) == View.MULTIWINDOW_DRAG_VISIBLE) {
                showSystemUI(mMultiWindowDragControl);
            }
        }

        if (isShowFloatingControl()) {
            if ((visibility & View.MULTIWINDOW_FLOATING_HIDDEN) == View.MULTIWINDOW_FLOATING_HIDDEN) {
                hideSystemUI(mMultiWindowFloatingControl);
            }
        } else {
            if ((visibility & View.MULTIWINDOW_FLOATING_VISIBLE) == View.MULTIWINDOW_FLOATING_VISIBLE) {
                showSystemUI(mMultiWindowFloatingControl);
            }
        }

        return visibility;
    }

    @Override
    public void getInsetHintLw(WindowManager.LayoutParams attrs, Rect outContentInsets,
            Rect outStableInsets) {
        // called by WindowManagerService, addWindow
        // TODO : What to do?, check context
        // set default
        outContentInsets.setEmpty();
        outStableInsets.setEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public void beginLayoutLw(boolean isDefaultDisplay, int displayWidth, int displayHeight,
                              int displayRotation) {
        // called by WindowManagerService, performLayoutLockedInner()
        mSystemLeft = 0;
        mSystemTop = 0;
        mSystemRight = displayWidth;
        mSystemBottom = displayHeight;

        mSystemGestures.screenWidth = displayWidth;
        mSystemGestures.screenHeight = displayHeight;
        // TODO : handle MultiWindowControl, MultiWindowMiniLauncher

        if (DEBUG_LAYOUT) Slog.d(TAG, "beginLayoutLw: ["
                + mSystemLeft + ":" + mSystemTop + ":" + mSystemRight + ":" + mSystemBottom
                + "]");
    }

    /** {@inheritDoc} */
    @Override
    public int getSystemDecorLayerLw() {
        // called by WindowManagerService, performLayoutLockedInner()
        // TODO : what is meaning of layer?
        return 0;
    }

    @Override
    public void getContentRectLw(Rect r) {
        r.set(mSystemLeft, mSystemTop, mSystemRight, mSystemBottom);
    }

    private void layoutMultiWindowControl(Rect parentFrame) {
        parentFrame.left = mSystemRight - mMultiWindowControlbarWidth;
        parentFrame.top = mSystemTop + mMultiWindowControlbarStartY;
        parentFrame.right = mSystemRight;
        parentFrame.bottom = parentFrame.top + mMultiWindowControlbarHeight;
    }

    private void layoutMiniLauncher(Rect parentFrame) {
        int width = mMinilauncherWidth;
        int height = mMinilauncherHeight;
        parentFrame.left = (mSystemRight - width)/2;
        parentFrame.top = (mSystemBottom - height)/2;
        parentFrame.right = parentFrame.left + width;
        parentFrame.bottom = parentFrame.top + height;
    }

    private void layoutDragControl(Rect parentFrame) {
        parentFrame.left = mSystemLeft;
        parentFrame.top = mSystemTop;
        parentFrame.right = mSystemRight;
        parentFrame.bottom = mDragControlHeight;
    }

    private void layoutFloatingControl(Rect parentFrame) {
        parentFrame.left = mFloatingWindowLeft;
        parentFrame.top = mFloatingWindowTop - mFloatingControlHeight;
        parentFrame.right = mFloatingWindowRight;
        parentFrame.bottom = mFloatingWindowTop;
    }

    private void layoutFull(Rect parentFrame) {
        parentFrame.left = mSystemLeft;
        parentFrame.top = mSystemTop;
        parentFrame.right = mSystemRight;
        parentFrame.bottom = mSystemBottom;
    }

    private void layoutLeft(Rect parentFrame) {
        parentFrame.left = mSystemLeft;
        parentFrame.top = mSystemTop;
        parentFrame.right = mSystemRight/2;
        parentFrame.bottom = mSystemBottom;
    }

    private void layoutRight(Rect parentFrame) {
        parentFrame.left = mSystemLeft + (mSystemRight/2);
        parentFrame.top = mSystemTop;
        parentFrame.right = mSystemRight;
        parentFrame.bottom = mSystemBottom;
    }

    private void layoutAttached(WindowState attached,
            Rect parentFrame,
            Rect displayFrame,
            Rect overscanFrame,
            Rect contentFrame,
            Rect visibleFrame,
            Rect decorFrame,
            Rect stableFrame) {
        contentFrame.set(attached.getContentFrameLw());
        displayFrame.set(attached.getDisplayFrameLw());
        overscanFrame.set(attached.getOverscanFrameLw());
        visibleFrame.set(attached.getVisibleFrameLw());
        parentFrame.set(attached.getFrameLw());
        decorFrame.set(parentFrame);
        stableFrame.set(parentFrame);

        if (mMultiWindowEnable && mCurrentLayoutWindowNumber > 1) {

            int which = whichTask(attached);
            if (which != TASK_UNKNOWN) {
                // HACK!!! Workaround code for autonavi
                if ((!mIsFloatingMode) && mCurrentLayoutWindowNumber > 1 && which == TASK_LEFT) {
                    final WindowManager.LayoutParams attachedAttrs = attached.getAttrs();
                    final String attachedTitle = attachedAttrs.getTitle().toString();
                    if (attachedTitle.startsWith("com.autonavi.xmgd.navigator/com.autonavi.xmgd.navigator.Map")) {
                        Slog.e(TAG, "This is Test Code ---> Fixup for autonavi!!!");
                        int factor = -256;
                        contentFrame.left = factor;
                        displayFrame.left = factor;
                        overscanFrame.left = factor;
                        visibleFrame.left = factor;
                        decorFrame.left = factor;
                        stableFrame.left = factor;
                        parentFrame.left = factor;
                        int right = 768;
                        contentFrame.right = right;
                        displayFrame.right = right;
                        overscanFrame.right = right;
                        visibleFrame.right = right;
                        decorFrame.right = right;
                        stableFrame.right = right;
                        parentFrame.right = right;
                    }
                }
            } else {
                Slog.e(TAG, "attached win is not left nor right --> " + attached);
            }
        }
        if (DEBUG_LAYOUT) {
            Slog.d(TAG, "Attached set ---> ");
            Slog.d(TAG, "content:" + contentFrame);
            Slog.d(TAG, "display:" + displayFrame);
            Slog.d(TAG, "overscan:" + overscanFrame);
            Slog.d(TAG, "visible:" + visibleFrame);
            Slog.d(TAG, "decor:" + decorFrame);
            Slog.d(TAG, "stable:" + stableFrame);
            Slog.d(TAG, "parent:" + parentFrame);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void layoutWindowLw(WindowState win, WindowState attached) {
        // core function
        // TODO : handle input method window

        if (DEBUG_LAYOUT) Slog.d(TAG, "layoutWindowLw : win " + win);
        if (DEBUG_LAYOUT) Slog.d(TAG, "attached " + attached);

        Rect parentFrame = new Rect();
        Rect displayFrame = new Rect();
        Rect overscanFrame = new Rect();
        Rect contentFrame = new Rect();
        Rect visibleFrame = new Rect();
        Rect decorFrame = new Rect();
        Rect stableFrame = new Rect();

        if (attached == null) {
            if (win == mMultiWindowControl) {
                layoutMultiWindowControl(parentFrame);
            } else if (win == mMultiWindowMiniLauncher) {
                layoutMiniLauncher(parentFrame);
            } else if (win == mMultiWindowDragControl) {
                layoutDragControl(parentFrame);
            } else if (win == mMultiWindowFloatingControl) {
                if (mIsFloatingMode) {
                    layoutFloatingControl(parentFrame);
                } else {
                    Slog.e(TAG, "FATAL ERROR: Current Mode is not Floating Mode, Why window layout requested?");
                }
            } else {
                if (mMultiWindowEnable && mCurrentLayoutWindowNumber > 1) {
                    if (mIsFloatingMode) {
                        // floating mode
                        if (win == mRightWin) {
                            layoutFloating(parentFrame);
                        } else if (win == mLeftWin) {
                            layoutFull(parentFrame);
                        } else {
                            int which = whichTask(win);
                            if (which == TASK_LEFT) {
                                if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Floating Mode --> Task same to Left");
                                layoutLeft(parentFrame);
                            } else if (which == TASK_RIGHT) {
                                if (DEBUG_FLOATING_WINDOW) Slog.d(TAG, "Floating Mode --> Task same to Right");
                                layoutFloating(parentFrame);
                            } else {
                                Slog.e(TAG, "How can I deal this window when floating mode ? win --> " + win);
                                layoutFloating(parentFrame);
                            }
                        }
                    } else {
                        // normal mode
                        if (win == mRightWin) {
                            layoutRight(parentFrame);
                        } else if (win == mLeftWin) {
                            layoutLeft(parentFrame);
                        } else {
                            int which = whichTask(win);
                            if (which == TASK_LEFT) {
                                if (DEBUG_LAYOUT) Slog.d(TAG, "Task same to Left");
                                layoutLeft(parentFrame);

                                // HACK!!! Workaround code for autonavi
                                if (win.getAttrs().getTitle().toString().startsWith("com.autonavi.xmgd.navigator/com.autonavi.xmgd.navigator.Map")) {
                                    Slog.e(TAG, "Fixup for autonavi");
                                    parentFrame.left = -256;
                                    parentFrame.right = 768;
                                }
                            } else if (which == TASK_RIGHT) {
                                if (DEBUG_LAYOUT) Slog.d(TAG, "Task same to Right");
                                layoutRight(parentFrame);
                            } else {
                                if (DEBUG_LAYOUT) Slog.d(TAG, "Unknown Task");
                                layoutSpecialWindow(win, parentFrame);
                            }
                        }
                    }
                } else {
                    layoutFull(parentFrame);
                }
            }

            displayFrame.set(parentFrame);
            overscanFrame.set(parentFrame);
            contentFrame.set(parentFrame);
            visibleFrame.set(parentFrame);
            decorFrame.set(parentFrame);
            stableFrame.set(parentFrame);
        } else {
            layoutAttached(attached, parentFrame, displayFrame, overscanFrame, contentFrame, visibleFrame, decorFrame, stableFrame);
        }

        win.computeFrameLw(parentFrame,
                displayFrame,
                overscanFrame,
                contentFrame,
                visibleFrame,
                decorFrame,
                stableFrame);
    }

    /** {@inheritDoc} */
    @Override
    public void finishLayoutLw() {
        if (DEBUG_LAYOUT) Slog.d(TAG, "finishLayoutLw");
        return;
    }

    /** {@inheritDoc} */
    @Override
    public void beginPostLayoutPolicyLw(int displayWidth, int displayHeight) {
        if (DEBUG_LAYOUT) Slog.d(TAG, "beginPostLayoutPolicyLw");
    }

    /** {@inheritDoc} */
    @Override
    public void applyPostLayoutPolicyLw(WindowState win, WindowManager.LayoutParams attrs,
            WindowState attached) {
        if (DEBUG_LAYOUT) Slog.d(TAG, "applyPostLayoutPolicyLw");
    }

    /** {@inheritDoc} */
    @Override
    public int finishPostLayoutPolicyLw() {
        // if return not 0, WindowManagerService redo layout loop!!!
        if (DEBUG_LAYOUT) Slog.d(TAG, "finishPostLayoutPolicyLw");
        int changes = 0;
        return changes;
    }

    @Override
    public boolean allowAppAnimationsLw() {
        return true;
    }

    @Override
    public int focusChangedLw(WindowState lastFocus, WindowState newFocus) {
        // TODO : handle MultiWindowControl, MultiWindowMiniLauncher?
        if (DEBUG) Slog.d(TAG, "focusChangedLw last=" + lastFocus + ", new=" + newFocus);
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
    }

    @Override
    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {
    }


    /** {@inheritDoc} */
    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        // called by InputFlinger, InputDispatcher::notifyKey()
        
        if (!mSystemBooted)
            return 0;

        final boolean interactive = (policyFlags & FLAG_INTERACTIVE) != 0;
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final int keyCode = event.getKeyCode();
        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        if (DEBUG_INPUT)
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                    + " interactive=" + interactive
                    + " policyFlags=" + Integer.toHexString(policyFlags));

        // boolean handled = handleKeyEvent(event, keyCode, down, canceled, interactive, isInjected);
        // if (handled)
        //     return 0;
        // else
        //     return ACTION_PASS_TO_USER;
        return ACTION_PASS_TO_USER;
    }

    /** {@inheritDoc} */
    @Override
    public int interceptMotionBeforeQueueingNonInteractive(long whenNanos, int policyFlags) {
        // called by InputFlinger, InputDispatcher::notifyMotion()
        return 0;
    }

    @Override
    public void goingToSleep(int why) {
        // do nothing
    }

    @Override
    public void wakingUp() {
        // do nothing
    }

    @Override
    public void screenTurnedOff() {
        // do nothing
    }

    @Override
    public void screenTurningOn(final ScreenOnListener screenOnListener) {
        // do nothing
    }

    @Override
    public boolean isScreenOn() {
        // always screen on
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void enableKeyguard(boolean enabled) {
        // do nothing
    }

    /** {@inheritDoc} */
    @Override
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        // do nothing
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardLocked() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyguardSecure() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean inKeyguardRestrictedKeyInputMode() {
        return false;
    }

    @Override
    public void dismissKeyguardLw() {
        // do nothing
    }

    @Override
    public boolean isKeyguardDrawnLw() {
        return false;
    }

    @Override
    public void notifyActivityDrawnForKeyguardLw() {
    }

    @Override
    public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        // do nothing
    }

    @Override
    public int rotationForOrientationLw(int orientation, int lastRotation) {
        // TODO : handle portrait
        return Surface.ROTATION_0;
    }

    @Override
    public boolean rotationHasCompatibleMetricsLw(int orientation, int rotation) {
        return true;
    }

    @Override
    public void setRotationLw(int rotation) {
        // do nothing
    }

    @Override
    public int getUserRotationMode() {
        return WindowManagerPolicy.USER_ROTATION_FREE;
    }

    @Override
    public void setUserRotationMode(int mode, int rot) {
        // do nothing
    }

    @Override
    public void setSafeMode(boolean safeMode) {
        // do nothing
    }

    /** {@inheritDoc} */
    @Override
    public void systemReady() {
        if (DEBUG) Slog.d(TAG, "systemReady");
    }

    /** {@inheritDoc} */
    @Override
    public void systemBooted() {
        if (DEBUG) Slog.d(TAG, "systemBooted");
        mSystemBooted = true;
    }

    /** {@inheritDoc} */
    @Override
    public void showBootMessage(final CharSequence msg, final boolean always) {
        // do nothing
    }

    /** {@inheritDoc} */
    @Override
    public void hideBootMessages() {
        // do nothing
    }

    /** {@inheritDoc} */
    @Override
    public void userActivity() {
        // do nothing
    }

    @Override
    public void lockNow(Bundle options) {
        // do nothing
    }

    /** {@inheritDoc} */
    @Override
    public void enableScreenAfterBoot() {
        if (DEBUG) Slog.d(TAG, "enableScreenAfterBoot");
    }

    @Override
    public void setCurrentOrientationLw(int newOrientation) {
        // TODO : handle portrait
    }

    @Override
    public boolean performHapticFeedbackLw(WindowState win, int effectId, boolean always) {
        // do nothing
        return false;
    }

    @Override
    public void keepScreenOnStartedLw() {
    }

    @Override
    public void keepScreenOnStoppedLw() {
    }

    @Override
    public boolean hasNavigationBar() {
        return false;
    }

    @Override
    public void setLastInputMethodWindowLw(WindowState ime, WindowState target) {
        // TODO
    }

    @Override
    public int getInputMethodWindowVisibleHeightLw() {
        // TODO
        return 0;
    }

    @Override
    public void setCurrentUserLw(int newUserId) {
        // TODO : What to do?
    }

    @Override
    public boolean canMagnifyWindow(int windowType) {
        switch (windowType) {
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD:
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG:
            case WindowManager.LayoutParams.TYPE_NAVIGATION_BAR:
            case WindowManager.LayoutParams.TYPE_MULTIWINDOW_CONTROL:
            case WindowManager.LayoutParams.TYPE_MULTIWINDOW_MINILAUNCHER:
            case WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY: {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTopLevelWindow(int windowType) {
        if (windowType >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
                && windowType <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            return (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
        }
        return true;
    }

    @Override
    public void showGlobalActions() {
    }

    @Override
    public void dump(String prefix, PrintWriter pw, String[] args) {
    }

    // MULTIWINDOW
    @Override
    public boolean getMultiWindowEnabled() {
        return mMultiWindowEnable;
    }

    @Override
    public void setLayoutWindowNumber(int num) {
        mCurrentLayoutWindowNumber = num;
    }

    @Override
    public void setLeftWindow(WindowState win) {
        mLeftWin = win;
    }

    @Override
    public void setRightWindow(WindowState win) {
        mRightWin = win;
    }
}
