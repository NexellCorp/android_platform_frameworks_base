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

import com.android.internal.R;
import com.android.internal.policy.PolicyManager;
//import com.android.internal.policy.impl.keyguard.KeyguardServiceDelegate;
//import com.android.internal.policy.impl.keyguard.KeyguardServiceDelegate.ShowListener;
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
    static final boolean DEBUG = true;
    static final boolean DEBUG_INPUT = true;
    static final boolean DEBUG_LAYOUT = true;
    static final boolean DEBUG_STARTING_WINDOW = true;

    // HACK : if false, don't show starting window, default change to false
    static final boolean SHOW_STARTING_ANIMATIONS = false;

    /**
     * key hooking
     */
    static final int ACTION_DO_NOTHING = 0;
    static final int ACTION_LCD_ON_OFF = 1;
    static final int ACTION_MUTE_ON_OFF = 2;
    static final int ACTION_VOLUME_UP = 3;
    static final int ACTION_VOLUME_DOWN = 4;
    static final int ACTION_MINI_LAUNCHER = 5;
    static final int ACTION_GO_HOME = 6;
    static final int ACTION_LAUNCH_OTHER = 7;

    private class ButtonAction {
        int mShortPressAction = 0;
        int mLongPressAction = 0;
        int mDoublePressAction = 0;
        int mTriplePressAction = 0;

        String mShortPressLaunchApp = null;
        String mLongPressLaunchApp = null;
        String mDoublePressLaunchApp = null;
        String mTriplePressLaunchApp = null;

        public ButtonAction(int shortPressAction,
                int longPressAction,
                int doublePressAction,
                int triplePressAction,
                String shortPressLaunchApp,
                String longPressLaunchApp,
                String doublePressLaunchApp,
                String triplePressLaunchApp) {
             mShortPressAction = shortPressAction;
        }

        private int runCommon(int action, String app) {
            return 0;
        }

        public int runShort() {
            return 0;
        }

        public int runLong() {
            return 0;
        }

        public int runDouble() {
            return 0;
        }

        public int runTriple() {
            return 0;
        }
    }

    private class ButtonActionManager {
        private Map<Integer, ButtonAction> mButtonActionMap;
        private Context mContext;

        public ButtonActionManager(Context context) {
            mContext = context;
            mButtonActionMap = new HashMap<Integer, ButtonAction>();
        }

        public void init() {
            // read config, add ButtonAction
        }

        public int handleButtonEvent(KeyEvent event, boolean down, boolean canceled) {
            return 0;
        }

    }

    /**
     * Message
     */
    private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 1;

    /**
     * Message Handler
     */
    private class PolicyHandler extends Handler {
         @Override
         public void handleMessage(Message msg) {
             switch (msg.what) {
                 case MSG_WINDOW_MANAGER_DRAWN_COMPLETE:
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

    WindowState mMultiWindowControl = null;
    WindowState mMultiWindowMiniLauncher = null;

    // TODO : flags
    private final BarController mMultiWindowControlBarController = new BarController("MultiWindowControl",
            0, // View.STATUS_BAR_TRANSIENT
            0, // View.STATUS_BAR_UNHIDE
            0, // View.STATUS_BAR_TRANSLUCENT
            0, // StatusBarManager.WINDOW_STATUS_BAR
            0); // WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS

    private final BarController mMultiWindowMiniLauncherBarController = new BarController("MultiWindowMiniLauncher",
            0, // View.STATUS_BAR_TRANSIENT
            0, // View.STATUS_BAR_UNHIDE
            0, // View.STATUS_BAR_TRANSLUCENT
            0, // StatusBarManager.WINDOW_STATUS_BAR
            0); // WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
    // END OF MULTIWINDOW

    /**
     * Private Functions
     */

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

        mSystemGestures = new SystemGesturesPointerEventListener(context,
                new SystemGesturesPointerEventListener.Callbacks() {
                    @Override
                    public void onSwipeFromTop() {
                    }
                    @Override
                    public void onSwipeFromBottom() {
                    }
                    @Override
                    public void onSwipeFromRight() {
                        // requestTransientBars(xxx)
                    }
                    @Override
                    public void onDebug() {
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
                mMultiWindowControl = win;
                mMultiWindowControlBarController.setWindow(win);
                break;
            case TYPE_MULTIWINDOW_MINILAUNCHER:
                if (mMultiWindowMiniLauncher != null && mMultiWindowMiniLauncher.isAlive())
                    return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                mMultiWindowMiniLauncher = win;
                mMultiWindowMiniLauncherBarController.setWindow(win);
                break;
        }
        return WindowManagerGlobal.ADD_OKAY;
    }

    /** {@inheritDoc} */
    @Override
    public void removeWindowLw(WindowState win) {
        if (mMultiWindowControl == win) {
            mMultiWindowControl = null;
            mMultiWindowControlBarController.setWindow(null);
        } else if (mMultiWindowMiniLauncher == win) {
            mMultiWindowMiniLauncher = null;
            mMultiWindowMiniLauncherBarController.setWindow(null);
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
        mMultiWindowControlBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);
        mMultiWindowMiniLauncherBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);
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

    /** {@inheritDoc} */
    @Override
    public void layoutWindowLw(WindowState win, WindowState attached) {
        // core function
        // TODO : handle input method window
        if ((win == mMultiWindowControl) || (win == mMultiWindowMiniLauncher))
            return;

        if (DEBUG_LAYOUT) Slog.d(TAG, "layoutWindowLw");

        Rect parentFrame = new Rect();
        Rect displayFrame = new Rect();
        Rect overscanFrame = new Rect();
        Rect contentFrame = new Rect();
        Rect visibleFrame = new Rect();
        Rect decorFrame = new Rect();
        Rect stableFrame = new Rect();

        parentFrame.left = mSystemLeft;
        parentFrame.top = mSystemTop;
        parentFrame.right = mSystemRight;
        parentFrame.bottom = mSystemBottom;

        displayFrame.set(parentFrame);
        overscanFrame.set(parentFrame);
        contentFrame.set(parentFrame);
        visibleFrame.set(parentFrame);
        decorFrame.set(parentFrame);
        stableFrame.set(parentFrame);

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
        if (DEBUG_LAYOUT) Slog.d(TAG, "focusChangedLw last=" + lastFocus + ", new=" + newFocus);
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
        // TODO : ButtonActionManager handle this?
        if (!mSystemBooted)
            return 0;

        final boolean interactive = (policyFlags & FLAG_INTERACTIVE) != 0;
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final int keyCode = event.getKeyCode();

        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        if (DEBUG_INPUT) {
            Log.d(TAG, "interceptKeyTq keycode=" + keyCode
                    + " interactive=" + interactive
                    + " policyFlags=" + Integer.toHexString(policyFlags));
        }

        return 0;
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
        Slog.d(TAG, "systemReady");
    }

    /** {@inheritDoc} */
    @Override
    public void systemBooted() {
        Slog.d(TAG, "systemBooted");
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
        Slog.d(TAG, "enableScreenAfterBoot");
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
