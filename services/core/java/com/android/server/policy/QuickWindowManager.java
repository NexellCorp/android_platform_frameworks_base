/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.policy;

import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.content.res.Configuration.EMPTY;
import static android.content.res.Configuration.UI_MODE_TYPE_MASK;
import static android.view.WindowManager.TAKE_SCREENSHOT_FULLSCREEN;
import static android.view.WindowManager.TAKE_SCREENSHOT_SELECTED_REGION;
import static android.view.WindowManager.LayoutParams.*;

import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.graphics.Rect;

import android.hardware.input.InputManagerInternal;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IAudioService;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.Slog;
import android.util.SparseArray;
import android.util.LongSparseArray;
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
import com.android.internal.annotations.GuardedBy;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.policy.IShortcutService;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ScreenShapeHelper;
import com.android.internal.widget.PointerLocationView;
import com.android.server.LocalServices;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.policy.keyguard.KeyguardServiceDelegate.DrawnListener;
import com.android.server.statusbar.StatusBarManagerInternal;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;

public class QuickWindowManager implements WindowManagerPolicy {
    static final String TAG = "QuickWindowManager";

    public static final int SYSTEM_UI_FLAG_ZH_NAVIGATION = 16384;

    public static final int TOAST_WINDOW_TIMEOUT = 3500; // 3.5 seconds

    private static final int NAV_BAR_BOTTOM = 0;
    private static final int NAV_BAR_RIGHT = 1;
    private static final int NAV_BAR_LEFT = 2;

    static final int APPLICATION_MEDIA_SUBLAYER = -2;
    static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
    static final int APPLICATION_PANEL_SUBLAYER = 1;
    static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;
    static final int APPLICATION_ABOVE_SUB_PANEL_SUBLAYER = 3;

    static final int SYSTEM_UI_CHANGING_LAYOUT =
              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.STATUS_BAR_TRANSLUCENT
            | View.NAVIGATION_BAR_TRANSLUCENT
            | View.STATUS_BAR_TRANSPARENT
            | View.NAVIGATION_BAR_TRANSPARENT;

    // Controls navigation bar opacity depending on which workspace stacks are currently
    // visible.
    // Nav bar is always opaque when either the freeform stack or docked stack is visible.
    static final int NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED = 0;
    // Nav bar is always translucent when the freeform stack is visible, otherwise always opaque.
    static final int NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE = 1;

    static final int PENDING_KEY_NULL = -1;

    private final Object mLock = new Object();

    Context mContext;
    IWindowManager mWindowManager;
    WindowManagerFuncs mWindowManagerFuncs;
    WindowManagerInternal mWindowManagerInternal;
    PowerManager mPowerManager;
    ActivityManagerInternal mActivityManagerInternal;
    InputManagerInternal mInputManagerInternal;
    PowerManagerInternal mPowerManagerInternal;
    IStatusBarService mStatusBarService;
    StatusBarManagerInternal mStatusBarManagerInternal;
    IUiModeManager mUiModeManager;
    int mUiMode;

    // The last window we were told about in focusChanged.
    WindowState mFocusedWindow;
    IApplicationToken mFocusedApp;

    Handler mHandler;
    WindowState mLastInputMethodWindow = null;
    WindowState mLastInputMethodTargetWindow = null;

    WindowState mStatusBar = null;
    WindowState mNavigationBar = null;

    PowerManager.WakeLock mPowerKeyWakeLock;

    private final LogDecelerateInterpolator mLogDecelerateInterpolator
            = new LogDecelerateInterpolator(100, 0);

    private final StatusBarController mStatusBarController = new StatusBarController();
    private final BarController mNavigationBarController = new BarController("NavigationBar",
            View.NAVIGATION_BAR_TRANSIENT,
            View.NAVIGATION_BAR_UNHIDE,
            View.NAVIGATION_BAR_TRANSLUCENT,
            StatusBarManager.WINDOW_NAVIGATION_BAR,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            View.NAVIGATION_BAR_TRANSPARENT);

    int mNavigationBarPosition = NAV_BAR_BOTTOM;
    Intent mHomeIntent;
    boolean mSystemBooted;

    Display mDisplay;
    private int mDisplayRotation;

    boolean mHasNavigationBar = false;

    int mLandscapeRotation = 0;  // default landscape rotation
    int mSeascapeRotation = 0;   // "other" landscape rotation, 180 degrees from mLandscapeRotation
    int mPortraitRotation = 0;   // default portrait rotation
    int mUpsideDownRotation = 0; // "other" portrait rotation

    int mOverscanLeft = 0;
    int mOverscanTop = 0;
    int mOverscanRight = 0;
    int mOverscanBottom = 0;

    // The current size of the screen; really; extends into the overscan area of
    // the screen and doesn't account for any system elements like the status bar.
    int mOverscanScreenLeft, mOverscanScreenTop;
    int mOverscanScreenWidth, mOverscanScreenHeight;
    // The current visible size of the screen; really; (ir)regardless of whether the status
    // bar can be hidden but not extending into the overscan area.
    int mUnrestrictedScreenLeft, mUnrestrictedScreenTop;
    int mUnrestrictedScreenWidth, mUnrestrictedScreenHeight;
    // Like mOverscanScreen*, but allowed to move into the overscan region where appropriate.
    int mRestrictedOverscanScreenLeft, mRestrictedOverscanScreenTop;
    int mRestrictedOverscanScreenWidth, mRestrictedOverscanScreenHeight;
    // The current size of the screen; these may be different than (0,0)-(dw,dh)
    // if the status bar can't be hidden; in that case it effectively carves out
    // that area of the display from all other windows.
    int mRestrictedScreenLeft, mRestrictedScreenTop;
    int mRestrictedScreenWidth, mRestrictedScreenHeight;
    // During layout, the current screen borders accounting for any currently
    // visible system UI elements.
    int mSystemLeft, mSystemTop, mSystemRight, mSystemBottom;
    // For applications requesting stable content insets, these are them.
    int mStableLeft, mStableTop, mStableRight, mStableBottom;
    // For applications requesting stable content insets but have also set the
    // fullscreen window flag, these are the stable dimensions without the status bar.
    int mStableFullscreenLeft, mStableFullscreenTop;
    int mStableFullscreenRight, mStableFullscreenBottom;
    // During layout, the current screen borders with all outer decoration
    // (status bar, input method dock) accounted for.
    int mCurLeft, mCurTop, mCurRight, mCurBottom;
    // During layout, the frame in which content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.  This is usually
    // the same as mCur*, but may be larger if the screen decor has supplied
    // content insets.
    int mContentLeft, mContentTop, mContentRight, mContentBottom;
    // During layout, the frame in which voice content should be displayed
    // to the user, accounting for all screen decoration except for any
    // space they deem as available for other content.
    int mVoiceContentLeft, mVoiceContentTop, mVoiceContentRight, mVoiceContentBottom;
    // During layout, the current screen borders along which input method
    // windows are placed.
    int mDockLeft, mDockTop, mDockRight, mDockBottom;
    // During layout, the layer at which the doc window is placed.
    int mDockLayer;
    // During layout, this is the layer of the status bar.
    int mStatusBarLayer;
    int mLastSystemUiFlags;
    boolean mForceShowSystemBars;
    // Bits that we are in the process of clearing, so we want to prevent
    // them from being set by applications until everything has been updated
    // to have them clear.
    int mResettingSystemUiFlags = 0;
    // Bits that we are currently always keeping cleared.
    int mForceClearedSystemUiFlags = 0;
    int mLastFullscreenStackSysUiFlags;
    int mLastDockedStackSysUiFlags;
    final Rect mNonDockedStackBounds = new Rect();
    final Rect mDockedStackBounds = new Rect();
    final Rect mLastNonDockedStackBounds = new Rect();
    final Rect mLastDockedStackBounds = new Rect();

    InputConsumer mInputConsumer = null;

    static final Rect mTmpParentFrame = new Rect();
    static final Rect mTmpDisplayFrame = new Rect();
    static final Rect mTmpOverscanFrame = new Rect();
    static final Rect mTmpContentFrame = new Rect();
    static final Rect mTmpVisibleFrame = new Rect();
    static final Rect mTmpDecorFrame = new Rect();
    static final Rect mTmpStableFrame = new Rect();
    static final Rect mTmpNavigationFrame = new Rect();
    static final Rect mTmpOutsetFrame = new Rect();
    private static final Rect mTmpRect = new Rect();

    WindowState mTopFullscreenOpaqueWindowState;
    WindowState mTopFullscreenOpaqueOrDimmingWindowState;
    WindowState mTopDockedOpaqueWindowState;
    WindowState mTopDockedOpaqueOrDimmingWindowState;
    HashSet<IApplicationToken> mAppsToBeHidden = new HashSet<IApplicationToken>();
    HashSet<IApplicationToken> mAppsThatDismissKeyguard = new HashSet<IApplicationToken>();
    boolean mTopIsFullscreen;
    boolean mForceStatusBar;
    boolean mForceStatusBarFromKeyguard;
    private boolean mForceStatusBarTransparent;
    int mNavBarOpacityMode = NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED;
    boolean mForcingShowNavBar;
    int mForcingShowNavBarLayer;
    boolean mLastFocusNeedsMenu = false;

    int[] mNavigationBarHeightForRotationDefault = new int[4];
    int[] mNavigationBarWidthForRotationDefault = new int[4];

    int mStatusBarHeight;
    boolean mTranslucentDecorEnabled = true;
    SleepToken mScreenOffSleepToken;
    boolean mScreenOnEarly;
    boolean mScreenOnFully;
    ScreenOnListener mScreenOnListener;

    volatile int mPendingWakeKey = PENDING_KEY_NULL;
    volatile boolean mPowerKeyHandled;

    private final Runnable mClearHideNavigationFlag = new Runnable() {
        @Override
        public void run() {
            synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                // Clear flags.
                mForceClearedSystemUiFlags &=
                        ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            }
            mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    };

    final class HideNavInputEventReceiver extends InputEventReceiver {
        public HideNavInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent
                        && (event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    final MotionEvent motionEvent = (MotionEvent)event;
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        // When the user taps down, we re-show the nav bar.
                        boolean changed = false;
                        synchronized (mWindowManagerFuncs.getWindowManagerLock()) {
                            if (mInputConsumer == null) {
                                return;
                            }
                            // Any user activity always causes us to show the
                            // navigation controls, if they had been hidden.
                            // We also clear the low profile and only content
                            // flags so that tapping on the screen will atomically
                            // restore all currently hidden screen decorations.
                            int newVal = mResettingSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                    View.SYSTEM_UI_FLAG_LOW_PROFILE |
                                    View.SYSTEM_UI_FLAG_FULLSCREEN;
                            if (mResettingSystemUiFlags != newVal) {
                                mResettingSystemUiFlags = newVal;
                                changed = true;
                            }
                            // We don't allow the system's nav bar to be hidden
                            // again for 1 second, to prevent applications from
                            // spamming us and keeping it from being shown.
                            newVal = mForceClearedSystemUiFlags |
                                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            if (mForceClearedSystemUiFlags != newVal) {
                                mForceClearedSystemUiFlags = newVal;
                                changed = true;
                                mHandler.postDelayed(mClearHideNavigationFlag, 1000);
                            }
                        }
                        if (changed) {
                            mWindowManagerFuncs.reevaluateStatusBarVisibility();
                        }
                    }
                }
            } finally {
                finishInputEvent(event, handled);
            }
        }
    }

    final InputEventReceiver.Factory mHideNavInputEventReceiverFactory =
            new InputEventReceiver.Factory() {
        @Override
        public InputEventReceiver createInputEventReceiver(
                InputChannel inputChannel, Looper looper) {
            return new HideNavInputEventReceiver(inputChannel, looper);
        }
    };

    private static final int MSG_ENABLE_POINTER_LOCATION = 1;
    private static final int MSG_DISABLE_POINTER_LOCATION = 2;
    private static final int MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK = 3;
    private static final int MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK = 4;
    private static final int MSG_KEYGUARD_DRAWN_COMPLETE = 5;
    private static final int MSG_KEYGUARD_DRAWN_TIMEOUT = 6;
    private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 7;
    private static final int MSG_DISPATCH_SHOW_RECENTS = 9;
    private static final int MSG_DISPATCH_SHOW_GLOBAL_ACTIONS = 10;
    private static final int MSG_HIDE_BOOT_MESSAGE = 11;
    private static final int MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK = 12;
    private static final int MSG_POWER_DELAYED_PRESS = 13;
    private static final int MSG_POWER_LONG_PRESS = 14;
    private static final int MSG_UPDATE_DREAMING_SLEEP_TOKEN = 15;
    private static final int MSG_REQUEST_TRANSIENT_BARS = 16;
    private static final int MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU = 17;
    private static final int MSG_BACK_LONG_PRESS = 18;
    private static final int MSG_DISPOSE_INPUT_CONSUMER = 19;
    private static final int MSG_BACK_DELAYED_PRESS = 20;

    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS = 0;
    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_NAVIGATION = 1;

    /**
     * local event handler
     */
    private class PolicyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ENABLE_POINTER_LOCATION:
                    break;
                case MSG_DISABLE_POINTER_LOCATION:
                    break;
                case MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK:
                    break;
                case MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK:
                    break;
                case MSG_DISPATCH_SHOW_RECENTS:
                    break;
                case MSG_DISPATCH_SHOW_GLOBAL_ACTIONS:
                    break;
                case MSG_KEYGUARD_DRAWN_COMPLETE:
                    break;
                case MSG_KEYGUARD_DRAWN_TIMEOUT:
                    break;
                case MSG_WINDOW_MANAGER_DRAWN_COMPLETE:
                    break;
                case MSG_HIDE_BOOT_MESSAGE:
                    break;
                case MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK:
                    break;
                case MSG_POWER_DELAYED_PRESS:
                    break;
                case MSG_POWER_LONG_PRESS:
                    break;
                case MSG_UPDATE_DREAMING_SLEEP_TOKEN:
                    break;
                case MSG_REQUEST_TRANSIENT_BARS:
                    break;
                case MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU:
                    break;
                case MSG_BACK_LONG_PRESS:
                    break;
                case MSG_DISPOSE_INPUT_CONSUMER:
                    break;
                case MSG_BACK_DELAYED_PRESS:
                    break;
            }
        }
    }

    /**
     * here are private methods
     */
    private boolean areTranslucentBarsAllowed() {
        return mTranslucentDecorEnabled;
    }

    private int updateLightStatusBarLw(int vis, WindowState opaque, WindowState opaqueOrDimming) {
        WindowState statusColorWin = opaqueOrDimming;

        if (statusColorWin != null) {
            if (statusColorWin == opaque) {
                // If the top fullscreen-or-dimming window is also the top fullscreen, respect
                // its light flag.
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                vis |= PolicyControl.getSystemUiVisibility(statusColorWin, null)
                        & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else if (statusColorWin != null && statusColorWin.isDimming()) {
                // Otherwise if it's dimming, clear the light flag.
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        return vis;
    }

    private void startActivityAsUser(Intent intent, UserHandle handle) {
        mContext.startActivityAsUser(intent, handle);
    }

    private void wakeUp(long wakeTime, String reason) {
        mPowerManager.wakeUp(wakeTime, reason);
    }

    private void gotoSleep(long eventTime) {
        mPowerManager.goToSleep(eventTime, PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON, 0);
    }

    private boolean canHideNavigationBar() {
        return mHasNavigationBar;
    }

    private int navigationBarPosition(int displayWidth, int displayHeight, int displayRotation) {
        return NAV_BAR_RIGHT;
    }

    private int getNavigationBarWidth(int rotation, int uiMode) {
        return mNavigationBarWidthForRotationDefault[rotation];
    }

    private int getNavigationBarHeight(int rotation, int uiMode) {
        return mNavigationBarHeightForRotationDefault[rotation];
    }

    private boolean isFullscreen(WindowManager.LayoutParams attrs) {
        return attrs.x == 0 && attrs.y == 0
                && attrs.width == WindowManager.LayoutParams.MATCH_PARENT
                && attrs.height == WindowManager.LayoutParams.MATCH_PARENT;
    }

    StatusBarManagerInternal getStatusBarManagerInternal() {
        if (mStatusBarManagerInternal == null) {
            mStatusBarManagerInternal =
                LocalServices.getService(StatusBarManagerInternal.class);
        }
        return mStatusBarManagerInternal;
    }

    private boolean canReceiveInput(WindowState win) {
        boolean notFocusable =
                (win.getAttrs().flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0;
        boolean altFocusableIm =
                (win.getAttrs().flags & WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) != 0;
        boolean notFocusableForIm = notFocusable ^ altFocusableIm;
        return !notFocusableForIm;
    }

    private void offsetInputMethodWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top);
        top += win.getGivenContentInsetsLw().top;
        if (mContentBottom > top) {
            mContentBottom = top;
        }
        if (mVoiceContentBottom > top) {
            mVoiceContentBottom = top;
        }
        top = win.getVisibleFrameLw().top;
        top += win.getGivenVisibleInsetsLw().top;
        if (mCurBottom > top) {
            mCurBottom = top;
        }
    }

    private void offsetVoiceInputWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top);
        top += win.getGivenContentInsetsLw().top;
        if (mVoiceContentBottom > top) {
            mVoiceContentBottom = top;
        }
    }

    private void calculateRelevantTaskInsets(Rect taskBounds, Rect inOutInsets, int displayWidth,
            int displayHeight) {
        mTmpRect.set(0, 0, displayWidth, displayHeight);
        mTmpRect.inset(inOutInsets);
        mTmpRect.intersect(taskBounds);
        int leftInset = mTmpRect.left - taskBounds.left;
        int topInset = mTmpRect.top - taskBounds.top;
        int rightInset = taskBounds.right - mTmpRect.right;
        int bottomInset = taskBounds.bottom - mTmpRect.bottom;
        inOutInsets.set(leftInset, topInset, rightInset, bottomInset);
    }

    private void updateScreenOffSleepToken(boolean acquire) {
        if (acquire) {
            if (mScreenOffSleepToken == null) {
                mScreenOffSleepToken = mActivityManagerInternal.acquireSleepToken("ScreenOff");
            }
        } else {
            if (mScreenOffSleepToken != null) {
                mScreenOffSleepToken.release();
                mScreenOffSleepToken = null;
            }
        }
    }

    private void layoutWallpaper(WindowState win, Rect pf, Rect df, Rect of, Rect cf) {
        // The wallpaper also has Real Ultimate Power, but we want to tell
        // it about the overscan area.
        pf.left = df.left = mOverscanScreenLeft;
        pf.top = df.top = mOverscanScreenTop;
        pf.right = df.right = mOverscanScreenLeft + mOverscanScreenWidth;
        pf.bottom = df.bottom = mOverscanScreenTop + mOverscanScreenHeight;
        of.left = cf.left = mUnrestrictedScreenLeft;
        of.top = cf.top = mUnrestrictedScreenTop;
        of.right = cf.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
        of.bottom = cf.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
    }

    void setAttachedWindowFrames(WindowState win, int fl, int adjust, WindowState attached,
            boolean insetDecors, Rect pf, Rect df, Rect of, Rect cf, Rect vf) {
        if (win.getSurfaceLayer() > mDockLayer && attached.getSurfaceLayer() < mDockLayer) {
            // Here's a special case: if this attached window is a panel that is
            // above the dock window, and the window it is attached to is below
            // the dock window, then the frames we computed for the window it is
            // attached to can not be used because the dock is effectively part
            // of the underlying window and the attached window is floating on top
            // of the whole thing.  So, we ignore the attached window and explicitly
            // compute the frames that would be appropriate without the dock.
            df.left = of.left = cf.left = vf.left = mDockLeft;
            df.top = of.top = cf.top = vf.top = mDockTop;
            df.right = of.right = cf.right = vf.right = mDockRight;
            df.bottom = of.bottom = cf.bottom = vf.bottom = mDockBottom;
        } else {
            // The effective display frame of the attached window depends on
            // whether it is taking care of insetting its content.  If not,
            // we need to use the parent's content frame so that the entire
            // window is positioned within that content.  Otherwise we can use
            // the overscan frame and let the attached window take care of
            // positioning its content appropriately.
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                // Set the content frame of the attached window to the parent's decor frame
                // (same as content frame when IME isn't present) if specifically requested by
                // setting {@link WindowManager.LayoutParams#FLAG_LAYOUT_ATTACHED_IN_DECOR} flag.
                // Otherwise, use the overscan frame.
                cf.set((fl & FLAG_LAYOUT_ATTACHED_IN_DECOR) != 0
                        ? attached.getContentFrameLw() : attached.getOverscanFrameLw());
            } else {
                // If the window is resizing, then we want to base the content
                // frame on our attached content frame to resize...  however,
                // things can be tricky if the attached window is NOT in resize
                // mode, in which case its content frame will be larger.
                // Ungh.  So to deal with that, make sure the content frame
                // we end up using is not covering the IM dock.
                cf.set(attached.getContentFrameLw());
                if (attached.isVoiceInteraction()) {
                    if (cf.left < mVoiceContentLeft) cf.left = mVoiceContentLeft;
                    if (cf.top < mVoiceContentTop) cf.top = mVoiceContentTop;
                    if (cf.right > mVoiceContentRight) cf.right = mVoiceContentRight;
                    if (cf.bottom > mVoiceContentBottom) cf.bottom = mVoiceContentBottom;
                } else if (attached.getSurfaceLayer() < mDockLayer) {
                    if (cf.left < mContentLeft) cf.left = mContentLeft;
                    if (cf.top < mContentTop) cf.top = mContentTop;
                    if (cf.right > mContentRight) cf.right = mContentRight;
                    if (cf.bottom > mContentBottom) cf.bottom = mContentBottom;
                }
            }
            df.set(insetDecors ? attached.getDisplayFrameLw() : cf);
            of.set(insetDecors ? attached.getOverscanFrameLw() : cf);
            vf.set(attached.getVisibleFrameLw());
        }
        // The LAYOUT_IN_SCREEN flag is used to determine whether the attached
        // window should be positioned relative to its parent or the entire
        // screen.
        pf.set((fl & FLAG_LAYOUT_IN_SCREEN) == 0
                ? attached.getFrameLw() : df);
    }

    private void applyStableConstraints(int sysui, int fl, Rect r) {
        if ((sysui & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
            // If app is requesting a stable layout, don't let the
            // content insets go below the stable values.
            if ((fl & FLAG_FULLSCREEN) != 0) {
                if (r.left < mStableFullscreenLeft) r.left = mStableFullscreenLeft;
                if (r.top < mStableFullscreenTop) r.top = mStableFullscreenTop;
                if (r.right > mStableFullscreenRight) r.right = mStableFullscreenRight;
                if (r.bottom > mStableFullscreenBottom) r.bottom = mStableFullscreenBottom;
            } else {
                if (r.left < mStableLeft) r.left = mStableLeft;
                if (r.top < mStableTop) r.top = mStableTop;
                if (r.right > mStableRight) r.right = mStableRight;
                if (r.bottom > mStableBottom) r.bottom = mStableBottom;
            }
        }
    }

    void updateUiMode() {
        if (mUiModeManager == null) {
            mUiModeManager = IUiModeManager.Stub.asInterface(
                    ServiceManager.getService(Context.UI_MODE_SERVICE));
        }
        try {
            mUiMode = mUiModeManager.getCurrentModeType();
        } catch (RemoteException e) {
        }
    }

    private boolean shouldUseOutsets(WindowManager.LayoutParams attrs, int fl) {
        return attrs.type == TYPE_WALLPAPER || (fl & (WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN)) != 0;
    }

    private boolean layoutNavigationBar(int displayWidth, int displayHeight, int displayRotation,
            int uiMode, int overscanLeft, int overscanRight, int overscanBottom, Rect dcf,
            boolean navVisible, boolean navTranslucent, boolean navAllowedHidden,
            boolean statusBarExpandedNotKeyguard) {
        if (mNavigationBar != null) {
            boolean transientNavBarShowing = mNavigationBarController.isTransientShowing();
            // Force the navigation bar to its appropriate place and
            // size.  We need to do this directly, instead of relying on
            // it to bubble up from the nav bar, because this needs to
            // change atomically with screen rotations.
            mNavigationBarPosition = navigationBarPosition(displayWidth, displayHeight,
                    displayRotation);
            // Hard coded: support only navigation bar position of NAV_BAR_RIGHT
            // Landscape screen; nav bar goes to the right.
            int left = displayWidth - overscanRight
                - getNavigationBarWidth(displayRotation, uiMode);

            //modify by zhonghong lihw for navigationBar
            if (mStatusBar.isVisibleLw()) {
                mTmpNavigationFrame.set(left, mStatusBarHeight, displayWidth - overscanRight, displayHeight);
            } else {
                mTmpNavigationFrame.set(left, 0, displayWidth - overscanRight, displayHeight);
            }
            //modify end
            
            mStableRight = mStableFullscreenRight = mTmpNavigationFrame.left;
            if (transientNavBarShowing) {
                mNavigationBarController.setBarShowingLw(true);
            } else if (navVisible) {
                mNavigationBarController.setBarShowingLw(true);
                mDockRight = mTmpNavigationFrame.left;
                mRestrictedScreenWidth = mDockRight - mRestrictedScreenLeft;
                mRestrictedOverscanScreenWidth = mDockRight - mRestrictedOverscanScreenLeft;
            } else {
                // We currently want to hide the navigation UI - unless we expanded the status
                // bar.
                mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard);
            }
            if (navVisible && !navTranslucent && !navAllowedHidden
                    && !mNavigationBar.isAnimatingLw()
                    && !mNavigationBarController.wasRecentlyTranslucent()) {
                // If the nav bar is currently requested to be visible,
                // and not in the process of animating on or off, then
                // we can tell the app that it is covered by it.
                mSystemRight = mTmpNavigationFrame.left;
            }
            // Make sure the content and current rectangles are updated to
            // account for the restrictions from the navigation bar.
            mContentTop = mVoiceContentTop = mCurTop = mDockTop;
            mContentBottom = mVoiceContentBottom = mCurBottom = mDockBottom;
            mContentLeft = mVoiceContentLeft = mCurLeft = mDockLeft;
            mContentRight = mVoiceContentRight = mCurRight = mDockRight;
            mStatusBarLayer = mNavigationBar.getSurfaceLayer();
            // And compute the final frame.
            mNavigationBar.computeFrameLw(mTmpNavigationFrame, mTmpNavigationFrame,
                    mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, dcf,
                    mTmpNavigationFrame, mTmpNavigationFrame);
            if (mNavigationBarController.checkHiddenLw()) {
                return true;
            }
        }
        return false;
    }

    private boolean layoutStatusBar(Rect pf, Rect df, Rect of, Rect vf, Rect dcf, int sysui) {
        // decide where the status bar goes ahead of time
        if (mStatusBar != null) {
            // apply any navigation bar insets
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenWidth + mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenHeight
                    + mUnrestrictedScreenTop;
            vf.left = mStableLeft;
            vf.top = mStableTop;

			//add by zhonghong lihw for navigationBar
            vf.right = mUnrestrictedScreenWidth;
			//add end

            vf.bottom = mStableBottom;

            mStatusBarLayer = mStatusBar.getSurfaceLayer();

            // Let the status bar determine its size.
            mStatusBar.computeFrameLw(pf /* parentFrame */, df /* displayFrame */,
                    vf /* overlayFrame */, vf /* contentFrame */, vf /* visibleFrame */,
                    dcf /* decorFrame */, vf /* stableFrame */, vf /* outsetFrame */);

            // For layout, the status bar is always at the top with our fixed height.
            mStableTop = mUnrestrictedScreenTop + mStatusBarHeight;

            boolean statusBarTransient = (sysui & View.STATUS_BAR_TRANSIENT) != 0;
            boolean statusBarTranslucent = (sysui
                    & (View.STATUS_BAR_TRANSLUCENT | View.STATUS_BAR_TRANSPARENT)) != 0;
            statusBarTranslucent &= areTranslucentBarsAllowed();

            // If the status bar is hidden, we don't want to cause
            // windows behind it to scroll.
            if (mStatusBar.isVisibleLw() && !statusBarTransient) {
                // Status bar may go away, so the screen area it occupies
                // is available to apps but just covering them when the
                // status bar is visible.
                mDockTop = mUnrestrictedScreenTop + mStatusBarHeight;

                mContentTop = mVoiceContentTop = mCurTop = mDockTop;
                mContentBottom = mVoiceContentBottom = mCurBottom = mDockBottom;
                mContentLeft = mVoiceContentLeft = mCurLeft = mDockLeft;
                mContentRight = mVoiceContentRight = mCurRight = mDockRight;
            }
            if (mStatusBar.isVisibleLw() && !mStatusBar.isAnimatingLw()
                    && !statusBarTransient && !statusBarTranslucent
                    && !mStatusBarController.wasRecentlyTranslucent()) {
                // If the opaque status bar is currently requested to be visible,
                // and not in the process of animating on or off, then
                // we can tell the app that it is covered by it.
                mSystemTop = mUnrestrictedScreenTop + mStatusBarHeight;
            }
            if (mStatusBarController.checkHiddenLw()) {
                return true;
            }
        }
        return false;
    }

    private boolean drawsSystemBarBackground(WindowState win) {
        return win == null || (win.getAttrs().flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;
    }

    private boolean forcesDrawStatusBarBackground(WindowState win) {
        return win == null || (win.getAttrs().privateFlags
                & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) != 0;
    }

    private int setNavBarOpaqueFlag(int visibility) {
        return visibility &= ~(View.NAVIGATION_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSPARENT);
    }

    private int setNavBarTranslucentFlag(int visibility) {
        visibility &= ~View.NAVIGATION_BAR_TRANSPARENT;
        return visibility |= View.NAVIGATION_BAR_TRANSLUCENT;
    }

    private int configureNavBarOpacity(int visibility, boolean dockedStackVisible,
            boolean freeformStackVisible, boolean isDockedDividerResizing) {
        if (mNavBarOpacityMode == NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED) {
            if (dockedStackVisible || freeformStackVisible || isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        } else if (mNavBarOpacityMode == NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE) {
            if (isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            } else if (freeformStackVisible) {
                visibility = setNavBarTranslucentFlag(visibility);
            } else {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        }

        if (!areTranslucentBarsAllowed()) {
            visibility &= ~View.NAVIGATION_BAR_TRANSLUCENT;
        }
        return visibility;
    }

    private void clearClearableFlagsLw() {
        int newVal = mResettingSystemUiFlags | View.SYSTEM_UI_CLEARABLE_FLAGS;
        if (newVal != mResettingSystemUiFlags) {
            mResettingSystemUiFlags = newVal;
            mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    }

    private int updateSystemBarsLw(WindowState win, int oldVis, int vis) {
        final boolean dockedStackVisible = mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID);
        final boolean freeformStackVisible =
                mWindowManagerInternal.isStackVisible(FREEFORM_WORKSPACE_STACK_ID);
        final boolean resizing = mWindowManagerInternal.isDockedDividerResizing();

        // We need to force system bars when the docked stack is visible, when the freeform stack
        // is visible but also when we are resizing for the transitions when docked stack
        // visibility changes.
        mForceShowSystemBars = dockedStackVisible || freeformStackVisible || resizing;
        final boolean forceOpaqueStatusBar = mForceShowSystemBars;

        // apply translucent bar vis flags
        WindowState fullscreenTransWin = mTopFullscreenOpaqueWindowState;
        vis = mStatusBarController.applyTranslucentFlagLw(fullscreenTransWin, vis, oldVis);
        vis = mNavigationBarController.applyTranslucentFlagLw(fullscreenTransWin, vis, oldVis);
        final int dockedVis = mStatusBarController.applyTranslucentFlagLw(
                mTopDockedOpaqueWindowState, 0, 0);

        final boolean fullscreenDrawsStatusBarBackground =
                (drawsSystemBarBackground(mTopFullscreenOpaqueWindowState)
                        && (vis & View.STATUS_BAR_TRANSLUCENT) == 0)
                || forcesDrawStatusBarBackground(mTopFullscreenOpaqueWindowState);
        final boolean dockedDrawsStatusBarBackground =
                (drawsSystemBarBackground(mTopDockedOpaqueWindowState)
                        && (dockedVis & View.STATUS_BAR_TRANSLUCENT) == 0)
                || forcesDrawStatusBarBackground(mTopDockedOpaqueWindowState);

        // prevent status bar interaction from clearing certain flags
        int type = win.getAttrs().type;
        boolean statusBarHasFocus = type == TYPE_STATUS_BAR;
        if (statusBarHasFocus) {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.STATUS_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSLUCENT;
            vis = (vis & ~flags) | (oldVis & flags);
        }

        if (fullscreenDrawsStatusBarBackground && dockedDrawsStatusBarBackground) {
            vis |= View.STATUS_BAR_TRANSPARENT;
            vis &= ~View.STATUS_BAR_TRANSLUCENT;
        } else if ((!areTranslucentBarsAllowed() && fullscreenTransWin != mStatusBar)
                || forceOpaqueStatusBar) {
            vis &= ~(View.STATUS_BAR_TRANSLUCENT | View.STATUS_BAR_TRANSPARENT);
        }

        vis = configureNavBarOpacity(vis, dockedStackVisible, freeformStackVisible, resizing);

        // update status bar
        boolean immersiveSticky =
                (vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
        final boolean hideStatusBarWM =
                mTopFullscreenOpaqueWindowState != null
                && (PolicyControl.getWindowFlags(mTopFullscreenOpaqueWindowState, null)
                        & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
        final boolean hideStatusBarSysui =
                (vis & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
        final boolean hideNavBarSysui =
                (vis & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0;

        final boolean transientStatusBarAllowed = mStatusBar != null
                && (statusBarHasFocus || (!mForceShowSystemBars
                        && (hideStatusBarWM || (hideStatusBarSysui && immersiveSticky))));

        final boolean transientNavBarAllowed = mNavigationBar != null
                && !mForceShowSystemBars && hideNavBarSysui && immersiveSticky;

        final long now = SystemClock.uptimeMillis();

        final boolean denyTransientStatus = mStatusBarController.isTransientShowRequested()
                && !transientStatusBarAllowed && hideStatusBarSysui;
        final boolean denyTransientNav = mNavigationBarController.isTransientShowRequested()
                && !transientNavBarAllowed;
        if (denyTransientStatus || denyTransientNav || mForceShowSystemBars) {
            // clear the clearable flags instead
            clearClearableFlagsLw();
            vis &= ~View.SYSTEM_UI_CLEARABLE_FLAGS;
        }

        final boolean immersive = (vis & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
        immersiveSticky = (vis & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
        final boolean navAllowedHidden = immersive || immersiveSticky;

        if (hideNavBarSysui && !navAllowedHidden && windowTypeToLayerLw(win.getBaseType())
                > windowTypeToLayerLw(TYPE_INPUT_CONSUMER)) {
            // We can't hide the navbar from this window otherwise the input consumer would not get
            // the input events.
            vis = (vis & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        vis = mStatusBarController.updateVisibilityLw(transientStatusBarAllowed, oldVis, vis);

        // update navigation bar
        vis = mNavigationBarController.updateVisibilityLw(transientNavBarAllowed, oldVis, vis);

        return vis;
    }

    private int updateSystemUiVisibilityLw() {
        // If there is no window focused, there will be nobody to handle the events
        // anyway, so just hang on in whatever state we're in until things settle down.
        WindowState winCandidate = mFocusedWindow != null ? mFocusedWindow
                : mTopFullscreenOpaqueWindowState;
        if (winCandidate == null) {
            return 0;
        }

        final WindowState win = winCandidate;
        int tmpVisibility = PolicyControl.getSystemUiVisibility(win, null)
                & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;        

        //add by lihw 20180624
        if ((tmpVisibility & SYSTEM_UI_FLAG_ZH_NAVIGATION) == 0){
        	if ((mLastSystemUiFlags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0) {
        		tmpVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE;
            } else if (!mNavigationBarController.getVisbile()) {
                    tmpVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE;
            }
        } else {
        	tmpVisibility &= ~SYSTEM_UI_FLAG_ZH_NAVIGATION; 
        }
        //add end

        if (mForcingShowNavBar && win.getSurfaceLayer() < mForcingShowNavBarLayer) {
            tmpVisibility &= ~PolicyControl.adjustClearableFlags(win, View.SYSTEM_UI_CLEARABLE_FLAGS);
        }

        final int fullscreenVisibility = updateLightStatusBarLw(0 /* vis */,
                mTopFullscreenOpaqueWindowState, mTopFullscreenOpaqueOrDimmingWindowState);
        final int dockedVisibility = updateLightStatusBarLw(0 /* vis */,
                mTopDockedOpaqueWindowState, mTopDockedOpaqueOrDimmingWindowState);
        mWindowManagerFuncs.getStackBounds(HOME_STACK_ID, mNonDockedStackBounds);
        mWindowManagerFuncs.getStackBounds(DOCKED_STACK_ID, mDockedStackBounds);
        final int visibility = updateSystemBarsLw(win, mLastSystemUiFlags, tmpVisibility);
        final int diff = visibility ^ mLastSystemUiFlags;
        final int fullscreenDiff = fullscreenVisibility ^ mLastFullscreenStackSysUiFlags;
        final int dockedDiff = dockedVisibility ^ mLastDockedStackSysUiFlags;
        final boolean needsMenu = win.getNeedsMenuLw(mTopFullscreenOpaqueWindowState);
        if (diff == 0 && fullscreenDiff == 0 && dockedDiff == 0 && mLastFocusNeedsMenu == needsMenu
                && mFocusedApp == win.getAppToken()
                && mLastNonDockedStackBounds.equals(mNonDockedStackBounds)
                && mLastDockedStackBounds.equals(mDockedStackBounds)) {
            return 0;
        }
        mLastSystemUiFlags = visibility;
        mLastFullscreenStackSysUiFlags = fullscreenVisibility;
        mLastDockedStackSysUiFlags = dockedVisibility;
        mLastFocusNeedsMenu = needsMenu;
        mFocusedApp = win.getAppToken();
        final Rect fullscreenStackBounds = new Rect(mNonDockedStackBounds);
        final Rect dockedStackBounds = new Rect(mDockedStackBounds);
        mHandler.post(new Runnable() {
                @Override
                public void run() {
                    StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
                    if (statusbar != null) {
                        statusbar.setSystemUiVisibility(visibility, fullscreenVisibility,
                                dockedVisibility, 0xffffffff, fullscreenStackBounds,
                                dockedStackBounds, win.toString());
                        statusbar.topAppWindowChanged(needsMenu);
                    }
                }
            });
        return diff;
    }
    /**
     * here are public override methods
     */
    @Override
    public void registerShortcutKey(long shortcutCode, IShortcutService shortcutService)
            throws RemoteException {
        // do nothing
    }

    @Override
    public boolean canShowDismissingWindowWhileLockedLw() {
        return false;
    }

    @Override
    public void init(Context context, IWindowManager windowManager,
            WindowManagerFuncs windowManagerFuncs) {
        mContext = context;
        mWindowManager = windowManager;
        mWindowManagerFuncs = windowManagerFuncs;
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);

        mHandler = new PolicyHandler();
        mPowerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);

        mWindowManagerInternal.registerAppTransitionListener(
                mStatusBarController.getAppTransitionListener());

        mHomeIntent =  new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        mPowerKeyWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PhoneWindowManager.mPowerKeyWakeLock");

        mTranslucentDecorEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableTranslucentDecor);
    }

    @Override
    public boolean isDefaultOrientationForced() {
        return true;
    }

    @Override
    public void setInitialDisplaySize(Display display, int width, int height, int density) {
        if (mContext == null || display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            return;
        }
        mDisplay = display;

        mLandscapeRotation = Surface.ROTATION_0;
        mSeascapeRotation = Surface.ROTATION_180;
        mPortraitRotation = Surface.ROTATION_90;
        mUpsideDownRotation = Surface.ROTATION_270;

        final Resources res = mContext.getResources();
        mHasNavigationBar = res.getBoolean(com.android.internal.R.bool.config_showNavigationBar);
    }

    @Override
    public void setDisplayOverscan(Display display, int left, int top, int right, int bottom) {
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            mOverscanLeft = left;
            mOverscanTop = top;
            mOverscanRight = right;
            mOverscanBottom = bottom;
        }
    }

    @Override
    public int checkAddPermission(WindowManager.LayoutParams attrs, int[] outAppOp) {
        return WindowManagerGlobal.ADD_OKAY;
    }

    @Override
    public boolean checkShowToOwnerOnly(WindowManager.LayoutParams attrs) {
        return false;
    }

    @Override
    public void adjustWindowParamsLw(WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_SYSTEM_OVERLAY:
            case TYPE_SECURE_SYSTEM_OVERLAY:
                // These types of windows can't receive input events.
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                break;
            case TYPE_STATUS_BAR:
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                attrs.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
                break;

            case TYPE_SCREENSHOT:
                attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                break;

            case TYPE_TOAST:
                // While apps should use the dedicated toast APIs to add such windows
                // it possible legacy apps to add the window directly. Therefore, we
                // make windows added directly by the app behave as a toast as much
                // as possible in terms of timeout and animation.
                if (attrs.hideTimeoutMilliseconds < 0
                        || attrs.hideTimeoutMilliseconds > TOAST_WINDOW_TIMEOUT) {
                    attrs.hideTimeoutMilliseconds = TOAST_WINDOW_TIMEOUT;
                }
                attrs.windowAnimations = com.android.internal.R.style.Animation_Toast;
                break;
        }

        if (attrs.type != TYPE_STATUS_BAR) {
            // The status bar is the only window allowed to exhibit keyguard behavior.
            attrs.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
        }

        if ((attrs.flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0) {
            attrs.subtreeSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        final boolean forceWindowDrawsStatusBarBackground =
            (attrs.privateFlags & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) != 0;
        if ((attrs.flags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0
                || forceWindowDrawsStatusBarBackground
                && attrs.height == MATCH_PARENT && attrs.width == MATCH_PARENT) {
            attrs.subtreeSystemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        }
    }

    @Override
    public void adjustConfigurationLw(Configuration config, int keyboardPresence,
            int navigationPresence) {
        if (config.navigation == Configuration.NAVIGATION_NONAV
                || navigationPresence == PRESENCE_INTERNAL) {
            config.navigationHidden = Configuration.NAVIGATIONHIDDEN_YES;
        }
    }

    @Override
    public int windowTypeToLayerLw(int type) {
        if (type >= FIRST_APPLICATION_WINDOW && type <= LAST_APPLICATION_WINDOW) {
            return 2;
        }
        switch (type) {
        case TYPE_PRIVATE_PRESENTATION:
            return 2;
        case TYPE_WALLPAPER:
            // wallpaper is at the bottom, though the window manager may move it.
            return 2;
        case TYPE_DOCK_DIVIDER:
            return 2;
        case TYPE_QS_DIALOG:
            return 2;
        case TYPE_PHONE:
            return 3;
        case TYPE_SEARCH_BAR:
        case TYPE_VOICE_INTERACTION_STARTING:
            return 4;
        case TYPE_VOICE_INTERACTION:
            // voice interaction layer is almost immediately above apps.
            return 5;
        case TYPE_INPUT_CONSUMER:
            return 6;
        case TYPE_SYSTEM_DIALOG:
            return 7;
        case TYPE_TOAST:
            // toasts and the plugged-in battery thing
            return 8;
        case TYPE_PRIORITY_PHONE:
            // SIM errors and unlock.  Not sure if this really should be in a high layer.
            return 9;
        case TYPE_DREAM:
            // used for Dreams (screensavers with TYPE_DREAM windows)
            return 10;
        case TYPE_SYSTEM_ALERT:
            // like the ANR / app crashed dialogs
            return 11;
        case TYPE_INPUT_METHOD:
            // on-screen keyboards and other such input method user interfaces go here.
            return 12;
        case TYPE_INPUT_METHOD_DIALOG:
            // on-screen keyboards and other such input method user interfaces go here.
            return 13;
        case TYPE_KEYGUARD_SCRIM:
            // the safety window that shows behind keyguard while keyguard is starting
            return 14;
        case TYPE_STATUS_BAR_SUB_PANEL:
            return 15;
        case TYPE_STATUS_BAR:
            return 16;
        case TYPE_STATUS_BAR_PANEL:
            return 17;
        case TYPE_KEYGUARD_DIALOG:
            return 18;
        case TYPE_VOLUME_OVERLAY:
            // the on-screen volume indicator and controller shown when the user
            // changes the device volume
            return 19;
        case TYPE_SYSTEM_OVERLAY:
            // the on-screen volume indicator and controller shown when the user
            // changes the device volume
            return 20;
        case TYPE_NAVIGATION_BAR:
            // the navigation bar, if available, shows atop most things
            return 21;
        case TYPE_NAVIGATION_BAR_PANEL:
            // some panels (e.g. search) need to show on top of the navigation bar
            return 22;
        case TYPE_SCREENSHOT:
            // screenshot selection layer shouldn't go above system error, but it should cover
            // navigation bars at the very least.
            return 23;
        case TYPE_SYSTEM_ERROR:
            // system-level error dialogs
            return 24;
        case TYPE_MAGNIFICATION_OVERLAY:
            // used to highlight the magnified portion of a display
            return 25;
        case TYPE_DISPLAY_OVERLAY:
            // used to simulate secondary display devices
            return 26;
        case TYPE_DRAG:
            // the drag layer: input for drag-and-drop is associated with this window,
            // which sits above all other focusable windows
            return 27;
        case TYPE_ACCESSIBILITY_OVERLAY:
            // overlay put by accessibility services to intercept user interaction
            return 28;
        case TYPE_SECURE_SYSTEM_OVERLAY:
            return 29;
        case TYPE_BOOT_PROGRESS:
            return 30;
        case TYPE_POINTER:
            // the (mouse) pointer layer
            return 31;
        }
        Log.e(TAG, "Unknown window type: " + type);
        return 2;
    }

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
        case TYPE_APPLICATION_ABOVE_SUB_PANEL:
            return APPLICATION_ABOVE_SUB_PANEL_SUBLAYER;
        }
        Log.e(TAG, "Unknown sub-window type: " + type);
        return 0;
    }

    @Override
    public int getMaxWallpaperLayer() {
        return windowTypeToLayerLw(TYPE_STATUS_BAR);
    }

    @Override
    public int getNonDecorDisplayWidth(int fullWidth, int fullHeight, int rotation,
            int uiMode) {
        return fullWidth;
    }

    @Override
    public int getNonDecorDisplayHeight(int fullWidth, int fullHeight, int rotation,
            int uiMode) {
        return fullHeight;
    }

    @Override
    public int getConfigDisplayWidth(int fullWidth, int fullHeight, int rotation, int uiMode) {
        return fullWidth;
    }

    @Override
    public int getConfigDisplayHeight(int fullWidth, int fullHeight, int rotation, int uiMode) {
        return fullHeight;
    }

    @Override
    public boolean isForceHiding(WindowManager.LayoutParams attrs) {
        return false;
    }

    @Override
    public boolean isKeyguardHostWindow(WindowManager.LayoutParams attrs) {
        return attrs.type == TYPE_STATUS_BAR;
    }

    @Override
    public boolean canBeForceHidden(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
            case TYPE_NAVIGATION_BAR:
            case TYPE_WALLPAPER:
            case TYPE_DREAM:
            case TYPE_KEYGUARD_SCRIM:
                return false;
            default:
                // Hide only windows below the keyguard host window.
                return windowTypeToLayerLw(win.getBaseType())
                        < windowTypeToLayerLw(TYPE_STATUS_BAR);
        }
    }

    @Override
    public WindowState getWinShowWhenLockedLw() {
        return null;
    }

    @Override
    public View addStartingWindow(IBinder appToken, String packageName, int theme,
            CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes,
            int icon, int logo, int windowFlags, Configuration overrideConfig) {
        return null;
    }

    @Override
    public void removeStartingWindow(IBinder appToken, View window) {
    }

    @Override
    public int prepareAddWindowLw(WindowState win, WindowManager.LayoutParams attrs) {
        switch (attrs.type) {
            case TYPE_STATUS_BAR:
                if (mStatusBar != null) {
                    if (mStatusBar.isAlive()) {
                        return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mStatusBar = win;
                mStatusBarController.setWindow(win);
                break;
            case TYPE_NAVIGATION_BAR:
                if (mNavigationBar != null) {
                    if (mNavigationBar.isAlive()) {
                        return WindowManagerGlobal.ADD_MULTIPLE_SINGLETON;
                    }
                }
                mNavigationBar = win;
                mNavigationBarController.setWindow(win);
                break;
        }
        return WindowManagerGlobal.ADD_OKAY;
    }

    @Override
    public void removeWindowLw(WindowState win) {
        if (mStatusBar == win) {
            mStatusBar = null;
            mStatusBarController.setWindow(null);
        } else if (mNavigationBar == win) {
            mNavigationBar = null;
            mNavigationBarController.setWindow(null);
        }
    }

    @Override
    public int selectAnimationLw(WindowState win, int transit) {
        if (win == mStatusBar) {
            if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                return R.anim.dock_top_exit;
            } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                return R.anim.dock_top_enter;
            }
        } else if (win == mNavigationBar) {
            if (win.getAttrs().windowAnimations != 0) {
                return 0;
            }
            // This can be on either the bottom or the right or the left.
            if (mNavigationBarPosition == NAV_BAR_BOTTOM) {
                if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                    return R.anim.dock_bottom_exit;
                } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                    return R.anim.dock_bottom_enter;
                }
            } else if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                    return R.anim.dock_right_exit;
                } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                    return R.anim.dock_right_enter;
                }
            } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                if (transit == TRANSIT_EXIT || transit == TRANSIT_HIDE) {
                    return R.anim.dock_left_exit;
                } else if (transit == TRANSIT_ENTER || transit == TRANSIT_SHOW) {
                    return R.anim.dock_left_enter;
                }
            }
        }

        if (transit == TRANSIT_PREVIEW_DONE) {
            if (win.hasAppShownWindows()) {
                return com.android.internal.R.anim.app_starting_exit;
            }
        }

        return 0;
    }

    @Override
    public void selectRotationAnimationLw(int anim[]) {
        anim[0] = anim[1] = 0;
    }

    @Override
    public boolean validateRotationAnimationLw(int exitAnimId, int enterAnimId,
            boolean forceDefault) {
        return true;
    }

    @Override
    public Animation createForceHideEnterAnimation(boolean onWallpaper,
            boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return AnimationUtils.loadAnimation(mContext, R.anim.lock_screen_behind_enter_fade_in);
        }

        AnimationSet set = (AnimationSet) AnimationUtils.loadAnimation(mContext, onWallpaper ?
                    R.anim.lock_screen_behind_enter_wallpaper :
                    R.anim.lock_screen_behind_enter);

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

    void launchHome(boolean fromHomeKey) {
        try {
            ActivityManagerNative.getDefault().stopAppSwitches();
        } catch (RemoteException e) {
        }

        PhoneWindow.sendCloseSystemWindows(mContext, null);

        Intent intent;
        if (fromHomeKey) {
            intent = new Intent(mHomeIntent);
            intent.putExtra(WindowManagerPolicy.EXTRA_FROM_HOME_KEY, fromHomeKey);
        } else {
            intent = mHomeIntent;
        }

        startActivityAsUser(intent, UserHandle.CURRENT);
    }

    @Override
    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        if (!mSystemBooted) {
            // If we have not yet booted, don't let key events do anything.
            return 0;
        }

        final boolean interactive = (policyFlags & FLAG_INTERACTIVE) != 0;
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();
        final int keyCode = event.getKeyCode();
        final boolean isInjected = (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0;

        int result;
        boolean isWakeKey = (policyFlags & WindowManagerPolicy.FLAG_WAKE) != 0
                || event.isWakeKey();
        if (interactive || (isInjected && !isWakeKey)) {
            // When the device is interactive or the key is injected pass the
            // key to the application.
            result = ACTION_PASS_TO_USER;
            isWakeKey = false;

            if (interactive) {
                // If the screen is awake, but the button pressed was the one that woke the device
                // then don't pass it to the application
                if (keyCode == mPendingWakeKey && !down) {
                    result = 0;
                }
                // Reset the pending key
                mPendingWakeKey = PENDING_KEY_NULL;
            }
        } else if (!interactive) {
            // in AVN, always enable key dispatching
            result = ACTION_PASS_TO_USER;
            mPendingWakeKey = PENDING_KEY_NULL;
        } else {
            // When the screen is off and the key is not injected, determine whether
            // to wake the device but don't pass the key to the application.
            result = 0;
            if (isWakeKey && !down) {
                isWakeKey = false;
            }
            // Cache the wake key on down event so we can also avoid sending the up event to the app
            if (isWakeKey && down) {
                mPendingWakeKey = keyCode;
            }
        }

        // Handle special keys.
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (!down) {
                    result &= ~ACTION_PASS_TO_USER;
                }
                break;
            }
            case KeyEvent.KEYCODE_POWER: {
                result &= ~ACTION_PASS_TO_USER;
                isWakeKey = false;
                if (down) {
                    if (!interactive) {
                        if (!mPowerKeyWakeLock.isHeld()) {
                            mPowerKeyWakeLock.acquire();
                        }
                        mPowerKeyHandled = true;
                        wakeUp(event.getEventTime(), "android.policy:POWER");
                    }
                } else {
                    if (interactive) {
                        mPowerKeyHandled = false;
                        gotoSleep(event.getEventTime());
                        if (mPowerKeyWakeLock.isHeld()) {
                            mPowerKeyWakeLock.release();
                        }
                    }
                }
                break;
            }
        }

        return result;
    }

    @Override
    public int interceptMotionBeforeQueueingNonInteractive(long whenNanos, int policyFlags) {
        if ((policyFlags & FLAG_WAKE) != 0) {
            wakeUp(whenNanos / 1000000, "android.policy:MOTION");
        }

        return 0;
    }

    @Override
    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        final int keyCode = event.getKeyCode();
        final int repeatCount = event.getRepeatCount();
        final int metaState = event.getMetaState();
        final int flags = event.getFlags();
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final boolean canceled = event.isCanceled();

        if (keyCode == KeyEvent.KEYCODE_HOME) {
            // If we have released the home key, and didn't do anything else
            // while it was pressed, then it is time to go home!
            if (!down) {
                if (canceled) {
                    Log.i(TAG, "Ignoring HOME; event canceled.");
                    return -1;
                }

                launchHome(true);
                return -1;
            }
            return -1;
        }

        // Let the application handle the key
        return 0;
    }

    @Override
    public KeyEvent dispatchUnhandledKey(WindowState win, KeyEvent event, int policyFlags) {
        return null;
    }

    @Override
    public void beginLayoutLw(boolean isDefaultDisplay, int displayWidth, int displayHeight,
                              int displayRotation, int uiMode) {
        mDisplayRotation = displayRotation;
        final int overscanLeft, overscanTop, overscanRight, overscanBottom;
        if (isDefaultDisplay) {
            switch (displayRotation) {
                case Surface.ROTATION_90:
                    overscanLeft = mOverscanTop;
                    overscanTop = mOverscanRight;
                    overscanRight = mOverscanBottom;
                    overscanBottom = mOverscanLeft;
                    break;
                case Surface.ROTATION_180:
                    overscanLeft = mOverscanRight;
                    overscanTop = mOverscanBottom;
                    overscanRight = mOverscanLeft;
                    overscanBottom = mOverscanTop;
                    break;
                case Surface.ROTATION_270:
                    overscanLeft = mOverscanBottom;
                    overscanTop = mOverscanLeft;
                    overscanRight = mOverscanTop;
                    overscanBottom = mOverscanRight;
                    break;
                default:
                    overscanLeft = mOverscanLeft;
                    overscanTop = mOverscanTop;
                    overscanRight = mOverscanRight;
                    overscanBottom = mOverscanBottom;
                    break;
            }
        } else {
            overscanLeft = 0;
            overscanTop = 0;
            overscanRight = 0;
            overscanBottom = 0;
        }
        mOverscanScreenLeft = mRestrictedOverscanScreenLeft = 0;
        mOverscanScreenTop = mRestrictedOverscanScreenTop = 0;
        mOverscanScreenWidth = mRestrictedOverscanScreenWidth = displayWidth;
        mOverscanScreenHeight = mRestrictedOverscanScreenHeight = displayHeight;
        mSystemLeft = 0;
        mSystemTop = 0;
        mSystemRight = displayWidth;
        mSystemBottom = displayHeight;
        mUnrestrictedScreenLeft = overscanLeft;
        mUnrestrictedScreenTop = overscanTop;
        mUnrestrictedScreenWidth = displayWidth - overscanLeft - overscanRight;
        mUnrestrictedScreenHeight = displayHeight - overscanTop - overscanBottom;
        mRestrictedScreenLeft = mUnrestrictedScreenLeft;
        mRestrictedScreenTop = mUnrestrictedScreenTop;
        mRestrictedScreenWidth = mUnrestrictedScreenWidth;
        mRestrictedScreenHeight = mUnrestrictedScreenHeight;
        mDockLeft = mContentLeft = mVoiceContentLeft = mStableLeft = mStableFullscreenLeft
                = mCurLeft = mUnrestrictedScreenLeft;
        mDockTop = mContentTop = mVoiceContentTop = mStableTop = mStableFullscreenTop
                = mCurTop = mUnrestrictedScreenTop;
        mDockRight = mContentRight = mVoiceContentRight = mStableRight = mStableFullscreenRight
                = mCurRight = displayWidth - overscanRight;
        mDockBottom = mContentBottom = mVoiceContentBottom = mStableBottom = mStableFullscreenBottom
                = mCurBottom = displayHeight - overscanBottom;
        mDockLayer = 0x10000000;
        mStatusBarLayer = -1;

        // start with the current dock rect, which will be (0,0,displayWidth,displayHeight)
        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect of = mTmpOverscanFrame;
        final Rect vf = mTmpVisibleFrame;
        final Rect dcf = mTmpDecorFrame;
        pf.left = df.left = of.left = vf.left = mDockLeft;
        pf.top = df.top = of.top = vf.top = mDockTop;
        pf.right = df.right = of.right = vf.right = mDockRight;
        pf.bottom = df.bottom = of.bottom = vf.bottom = mDockBottom;
        dcf.setEmpty();  // Decor frame N/A for system bars.

        if (isDefaultDisplay) {
            // For purposes of putting out fake window up to steal focus, we will
            // drive nav being hidden only by whether it is requested.
            final int sysui = mLastSystemUiFlags;
            boolean navVisible = (sysui & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            boolean navTranslucent = (sysui
                    & (View.NAVIGATION_BAR_TRANSLUCENT | View.NAVIGATION_BAR_TRANSPARENT)) != 0;
            boolean immersive = (sysui & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0;
            boolean immersiveSticky = (sysui & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0;
            boolean navAllowedHidden = immersive || immersiveSticky;
            navTranslucent &= !immersiveSticky;  // transient trumps translucent
            navTranslucent &= areTranslucentBarsAllowed();
            boolean statusBarExpandedNotKeyguard = mStatusBar != null
                    && mStatusBar.getAttrs().height == MATCH_PARENT
                    && mStatusBar.getAttrs().width == MATCH_PARENT;

            // When the navigation bar isn't visible, we put up a fake
            // input window to catch all touch events.  This way we can
            // detect when the user presses anywhere to bring back the nav
            // bar and ensure the application doesn't see the event.
            if (navVisible || navAllowedHidden) {
                if (mInputConsumer != null) {
                    mHandler.sendMessage(
                            mHandler.obtainMessage(MSG_DISPOSE_INPUT_CONSUMER, mInputConsumer));
                    mInputConsumer = null;
                }
            } else if (mInputConsumer == null) {
                mInputConsumer = mWindowManagerFuncs.addInputConsumer(mHandler.getLooper(),
                        mHideNavInputEventReceiverFactory);
            }

            // For purposes of positioning and showing the nav bar, if we have
            // decided that it can't be hidden (because of the screen aspect ratio),
            // then take that into account.
            navVisible |= !canHideNavigationBar();

            boolean updateSysUiVisibility = layoutNavigationBar(displayWidth, displayHeight,
                    displayRotation, uiMode, overscanLeft, overscanRight, overscanBottom, dcf, navVisible, navTranslucent,
                    navAllowedHidden, statusBarExpandedNotKeyguard);
            updateSysUiVisibility |= layoutStatusBar(pf, df, of, vf, dcf, sysui);
            if (updateSysUiVisibility) {
                updateSystemUiVisibilityLw();
            }
        }
    }

    @Override
    public int getSystemDecorLayerLw() {
        if (mStatusBar != null && mStatusBar.isVisibleLw()) {
            return mStatusBar.getSurfaceLayer();
        }

        if (mNavigationBar != null && mNavigationBar.isVisibleLw()) {
            return mNavigationBar.getSurfaceLayer();
        }

        return 0;
    }

    @Override
    public void getContentRectLw(Rect r) {
        r.set(mContentLeft, mContentTop, mContentRight, mContentBottom);
    }

    @Override
    public void layoutWindowLw(WindowState win, WindowState attached) {
        // We've already done the navigation bar and status bar. If the status bar can receive
        // input, we need to layout it again to accomodate for the IME window.
        if ((win == mStatusBar && !canReceiveInput(win)) || win == mNavigationBar) {
            return;
        }
        final WindowManager.LayoutParams attrs = win.getAttrs();
        final boolean isDefaultDisplay = win.isDefaultDisplay();
        final boolean needsToOffsetInputMethodTarget = isDefaultDisplay &&
                (win == mLastInputMethodTargetWindow && mLastInputMethodWindow != null);
        if (needsToOffsetInputMethodTarget) {
            offsetInputMethodWindowLw(mLastInputMethodWindow);
        }

        final int fl = PolicyControl.getWindowFlags(win, attrs);
        final int pfl = attrs.privateFlags;
        final int sim = attrs.softInputMode;
        int sysUiFl = PolicyControl.getSystemUiVisibility(win, null);

        //add by lihw 20180624
        if ((sysUiFl & SYSTEM_UI_FLAG_ZH_NAVIGATION) == 0) {
            if ((mLastSystemUiFlags & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                    || !mNavigationBarController.getVisbile()) {
                sysUiFl |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE;
            }
        } else {
            sysUiFl &= ~SYSTEM_UI_FLAG_ZH_NAVIGATION; 
        }
        //add end

        final Rect pf = mTmpParentFrame;
        final Rect df = mTmpDisplayFrame;
        final Rect of = mTmpOverscanFrame;
        final Rect cf = mTmpContentFrame;
        final Rect vf = mTmpVisibleFrame;
        final Rect dcf = mTmpDecorFrame;
        final Rect sf = mTmpStableFrame;
        Rect osf = null;
        dcf.setEmpty();

        final boolean hasNavBar = (isDefaultDisplay && mHasNavigationBar
                && mNavigationBar != null && mNavigationBar.isVisibleLw());

        final int adjust = sim & SOFT_INPUT_MASK_ADJUST;

        if (isDefaultDisplay) {
            sf.set(mStableLeft, mStableTop, mStableRight, mStableBottom);
        } else {
            sf.set(mOverscanLeft, mOverscanTop, mOverscanRight, mOverscanBottom);
        }

        if (!isDefaultDisplay) {
            if (attached != null) {
                // If this window is attached to another, our display
                // frame is the same as the one we are attached to.
                setAttachedWindowFrames(win, fl, adjust, attached, true, pf, df, of, cf, vf);
            } else {
                // Give the window full screen.
                pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                pf.right = df.right = of.right = cf.right
                        = mOverscanScreenLeft + mOverscanScreenWidth;
                pf.bottom = df.bottom = of.bottom = cf.bottom
                        = mOverscanScreenTop + mOverscanScreenHeight;
            }
        } else if (attrs.type == TYPE_INPUT_METHOD) {
            pf.left = df.left = of.left = cf.left = vf.left = mDockLeft;
            pf.top = df.top = of.top = cf.top = vf.top = mDockTop;
            pf.right = df.right = of.right = cf.right = vf.right = mDockRight;
            // IM dock windows layout below the nav bar...
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            // ...with content insets above the nav bar
            cf.bottom = vf.bottom = mStableBottom;

            // The status bar forces the navigation bar while it's visible. Make sure the IME
            // avoids the navigation bar in that case.
            if (mNavigationBarPosition == NAV_BAR_RIGHT) {
                pf.right = df.right = of.right = cf.right = vf.right = mStableRight;
            } else if (mNavigationBarPosition == NAV_BAR_LEFT) {
                pf.left = df.left = of.left = cf.left = vf.left = mStableLeft;
            }

            // IM dock windows always go to the bottom of the screen.
            attrs.gravity = Gravity.BOTTOM;
            mDockLayer = win.getSurfaceLayer();
        } else if (attrs.type == TYPE_VOICE_INTERACTION) {
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                cf.left = mDockLeft;
                cf.top = mDockTop;
                cf.right = mDockRight;
                cf.bottom = mDockBottom;
            } else {
                cf.left = mContentLeft;
                cf.top = mContentTop;
                cf.right = mContentRight;
                cf.bottom = mContentBottom;
            }
            if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                vf.left = mCurLeft;
                vf.top = mCurTop;
                vf.right = mCurRight;
                vf.bottom = mCurBottom;
            } else {
                vf.set(cf);
            }
        } else if (attrs.type == TYPE_WALLPAPER) {
           layoutWallpaper(win, pf, df, of, cf);
        } else if (win == mStatusBar) {
            pf.left = df.left = of.left = mUnrestrictedScreenLeft;
            pf.top = df.top = of.top = mUnrestrictedScreenTop;
            pf.right = df.right = of.right = mUnrestrictedScreenWidth + mUnrestrictedScreenLeft;
            pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenHeight + mUnrestrictedScreenTop;
            cf.left = vf.left = mStableLeft;
            cf.top = vf.top = mStableTop;
            cf.right = vf.right = mStableRight;
            vf.bottom = mStableBottom;

            if (adjust == SOFT_INPUT_ADJUST_RESIZE) {
                cf.bottom = mContentBottom;
            } else {
                cf.bottom = mDockBottom;
                vf.bottom = mContentBottom;
            }
        } else {
            // Default policy decor for the default display
            dcf.left = mSystemLeft;
            dcf.top = mSystemTop;
            dcf.right = mSystemRight;
            dcf.bottom = mSystemBottom;
            final boolean inheritTranslucentDecor = (attrs.privateFlags
                    & WindowManager.LayoutParams.PRIVATE_FLAG_INHERIT_TRANSLUCENT_DECOR) != 0;
            final boolean isAppWindow =
                    attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW &&
                    attrs.type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
            final boolean topAtRest =
                    win == mTopFullscreenOpaqueWindowState && !win.isAnimatingLw();
            if (isAppWindow && !inheritTranslucentDecor && !topAtRest) {
                if ((sysUiFl & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
                        && (fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) == 0
                        && (fl & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) == 0
                        && (fl & WindowManager.LayoutParams.
                                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0
                        && (pfl & PRIVATE_FLAG_FORCE_DRAW_STATUS_BAR_BACKGROUND) == 0) {
                    // Ensure policy decor includes status bar
                    dcf.top = mStableTop;
                }
                if ((fl & WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION) == 0
                        && (sysUiFl & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                        && (fl & WindowManager.LayoutParams.
                                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0) {
                    // Ensure policy decor includes navigation bar
                    dcf.bottom = mStableBottom;
                    dcf.right = mStableRight;
                }
            }

            if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                    == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
                // This is the case for a normal activity window: we want it
                // to cover all of the screen space, and it can take care of
                // moving its contents to account for screen decorations that
                // intrude into that space.
                if (attached != null) {
                    // If this window is attached to another, our display
                    // frame is the same as the one we are attached to.
                    setAttachedWindowFrames(win, fl, adjust, attached, true, pf, df, of, cf, vf);
                } else {
                    if (attrs.type == TYPE_STATUS_BAR_PANEL
                            || attrs.type == TYPE_STATUS_BAR_SUB_PANEL) {
                        // Status bar panels are the only windows who can go on top of
                        // the status bar.  They are protected by the STATUS_BAR_SERVICE
                        // permission, so they have the same privileges as the status
                        // bar itself.
                        //
                        // However, they should still dodge the navigation bar if it exists.

                        pf.left = df.left = of.left = hasNavBar
                                ? mDockLeft : mUnrestrictedScreenLeft;
                        pf.top = df.top = of.top = mUnrestrictedScreenTop;
                        pf.right = df.right = of.right = hasNavBar
                                ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                : mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        pf.bottom = df.bottom = of.bottom = hasNavBar
                                ? mRestrictedScreenTop+mRestrictedScreenHeight
                                : mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    } else if ((fl & FLAG_LAYOUT_IN_OVERSCAN) != 0
                            && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                        // Asking to layout into the overscan region, so give it that pure
                        // unrestricted area.
                        pf.left = df.left = of.left = mOverscanScreenLeft;
                        pf.top = df.top = of.top = mOverscanScreenTop;
                        pf.right = df.right = of.right = mOverscanScreenLeft + mOverscanScreenWidth;
                        pf.bottom = df.bottom = of.bottom = mOverscanScreenTop
                                + mOverscanScreenHeight;
                    } else if (canHideNavigationBar()
                            && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                            && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                        // Asking for layout as if the nav bar is hidden, lets the
                        // application extend into the unrestricted overscan screen area.  We
                        // only do this for application windows to ensure no window that
                        // can be above the nav bar can do this.
                        pf.left = df.left = mOverscanScreenLeft;
                        pf.top = df.top = mOverscanScreenTop;
                        pf.right = df.right = mOverscanScreenLeft + mOverscanScreenWidth;
                        pf.bottom = df.bottom = mOverscanScreenTop + mOverscanScreenHeight;
                        // We need to tell the app about where the frame inside the overscan
                        // is, so it can inset its content by that amount -- it didn't ask
                        // to actually extend itself into the overscan region.
                        of.left = mUnrestrictedScreenLeft;
                        of.top = mUnrestrictedScreenTop;
                        of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    } else {
                        pf.left = df.left = mRestrictedOverscanScreenLeft;
                        pf.top = df.top = mRestrictedOverscanScreenTop;
                        pf.right = df.right = mRestrictedOverscanScreenLeft
                                + mRestrictedOverscanScreenWidth;
                        pf.bottom = df.bottom = mRestrictedOverscanScreenTop
                                + mRestrictedOverscanScreenHeight;
                        // We need to tell the app about where the frame inside the overscan
                        // is, so it can inset its content by that amount -- it didn't ask
                        // to actually extend itself into the overscan region.
                        of.left = mUnrestrictedScreenLeft;
                        of.top = mUnrestrictedScreenTop;
                        of.right = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                        of.bottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                    }

                    if ((fl & FLAG_FULLSCREEN) == 0) {
                        if (win.isVoiceInteraction()) {
                            cf.left = mVoiceContentLeft;
                            cf.top = mVoiceContentTop;
                            cf.right = mVoiceContentRight;
                            cf.bottom = mVoiceContentBottom;
                        } else {
                            if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                                cf.left = mDockLeft;
                                cf.top = mDockTop;
                                cf.right = mDockRight;
                                cf.bottom = mDockBottom;
                            } else {
                                cf.left = mContentLeft;
                                cf.top = mContentTop;
                                cf.right = mContentRight;
                                cf.bottom = mContentBottom;
                            }
                        }
                    } else {
                        // Full screen windows are always given a layout that is as if the
                        // status bar and other transient decors are gone.  This is to avoid
                        // bad states when moving from a window that is not hding the
                        // status bar to one that is.
                        cf.left = mRestrictedScreenLeft;
                        cf.top = mRestrictedScreenTop;
                        cf.right = mRestrictedScreenLeft + mRestrictedScreenWidth;
                        cf.bottom = mRestrictedScreenTop + mRestrictedScreenHeight;
                    }
                    applyStableConstraints(sysUiFl, fl, cf);
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                }
            } else if ((fl & FLAG_LAYOUT_IN_SCREEN) != 0 || (sysUiFl
                    & (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)) != 0) {
                // A window that has requested to fill the entire screen just
                // gets everything, period.
                if (attrs.type == TYPE_STATUS_BAR_PANEL
                        || attrs.type == TYPE_STATUS_BAR_SUB_PANEL
                        || attrs.type == TYPE_VOLUME_OVERLAY) {
                    pf.left = df.left = of.left = cf.left = hasNavBar
                            ? mDockLeft : mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = hasNavBar
                                        ? mRestrictedScreenLeft+mRestrictedScreenWidth
                                        : mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = hasNavBar
                                          ? mRestrictedScreenTop+mRestrictedScreenHeight
                                          : mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
                } else if (attrs.type == TYPE_NAVIGATION_BAR
                        || attrs.type == TYPE_NAVIGATION_BAR_PANEL) {
                    // The navigation bar has Real Ultimate Power.
                    pf.left = df.left = of.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                } else if ((attrs.type == TYPE_SECURE_SYSTEM_OVERLAY
                                || attrs.type == TYPE_BOOT_PROGRESS
                                || attrs.type == TYPE_SCREENSHOT)
                        && ((fl & FLAG_FULLSCREEN) != 0)) {
                    // Fullscreen secure system overlays get what they ask for. Screenshot region
                    // selection overlay should also expand to full screen.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right = mOverscanScreenLeft
                            + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mOverscanScreenTop
                            + mOverscanScreenHeight;
                } else if (attrs.type == TYPE_BOOT_PROGRESS) {
                    // Boot progress screen always covers entire display.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right = mOverscanScreenLeft
                            + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mOverscanScreenTop
                            + mOverscanScreenHeight;
                } else if ((fl & FLAG_LAYOUT_IN_OVERSCAN) != 0
                        && attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                        && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                    // Asking to layout into the overscan region, so give it that pure
                    // unrestricted area.
                    pf.left = df.left = of.left = cf.left = mOverscanScreenLeft;
                    pf.top = df.top = of.top = cf.top = mOverscanScreenTop;
                    pf.right = df.right = of.right = cf.right
                            = mOverscanScreenLeft + mOverscanScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom
                            = mOverscanScreenTop + mOverscanScreenHeight;
                } else if (canHideNavigationBar()
                        && (sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0
                        && (attrs.type == TYPE_STATUS_BAR
                            || attrs.type == TYPE_TOAST
                            || attrs.type == TYPE_DOCK_DIVIDER
                            || attrs.type == TYPE_VOICE_INTERACTION_STARTING
                            || (attrs.type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                            && attrs.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW))) {
                    // Asking for layout as if the nav bar is hidden, lets the
                    // application extend into the unrestricted screen area.  We
                    // only do this for application windows (or toasts) to ensure no window that
                    // can be above the nav bar can do this.
                    // XXX This assumes that an app asking for this will also
                    // ask for layout in only content.  We can't currently figure out
                    // what the screen would be if only laying out to hide the nav bar.
                    pf.left = df.left = of.left = cf.left = mUnrestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mUnrestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mUnrestrictedScreenLeft
                            + mUnrestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mUnrestrictedScreenTop
                            + mUnrestrictedScreenHeight;
                } else if ((sysUiFl & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0) {
                    pf.left = df.left = of.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top  = mRestrictedScreenTop;
                    pf.right = df.right = of.right = mRestrictedScreenLeft + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                    if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                        cf.left = mDockLeft;
                        cf.top = mDockTop;
                        cf.right = mDockRight;
                        cf.bottom = mDockBottom;
                    } else {
                        cf.left = mContentLeft;
                        cf.top = mContentTop;
                        cf.right = mContentRight;
                        cf.bottom = mContentBottom;
                    }
                } else {
                    pf.left = df.left = of.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mRestrictedScreenLeft
                            + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                }

                applyStableConstraints(sysUiFl, fl, cf);

                if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                    vf.left = mCurLeft;
                    vf.top = mCurTop;
                    vf.right = mCurRight;
                    vf.bottom = mCurBottom;
                } else {
                    vf.set(cf);
                }
            } else if (attached != null) {
                // A child window should be placed inside of the same visible
                // frame that its parent had.
                setAttachedWindowFrames(win, fl, adjust, attached, false, pf, df, of, cf, vf);
            } else {
                // Otherwise, a normal window must be placed inside the content
                // of all screen decorations.
                if (attrs.type == TYPE_STATUS_BAR_PANEL || attrs.type == TYPE_VOLUME_OVERLAY) {
                    // Status bar panels and the volume dialog are the only windows who can go on
                    // top of the status bar.  They are protected by the STATUS_BAR_SERVICE
                    // permission, so they have the same privileges as the status
                    // bar itself.
                    pf.left = df.left = of.left = cf.left = mRestrictedScreenLeft;
                    pf.top = df.top = of.top = cf.top = mRestrictedScreenTop;
                    pf.right = df.right = of.right = cf.right = mRestrictedScreenLeft
                            + mRestrictedScreenWidth;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mRestrictedScreenTop
                            + mRestrictedScreenHeight;
                } else if (attrs.type == TYPE_TOAST || attrs.type == TYPE_SYSTEM_ALERT) {
                    // These dialogs are stable to interim decor changes.
                    pf.left = df.left = of.left = cf.left = mStableLeft;
                    pf.top = df.top = of.top = cf.top = mStableTop;
                    pf.right = df.right = of.right = cf.right = mStableRight;
                    pf.bottom = df.bottom = of.bottom = cf.bottom = mStableBottom;
                } else {
                    pf.left = mContentLeft;
                    pf.top = mContentTop;
                    pf.right = mContentRight;
                    pf.bottom = mContentBottom;
                    if (win.isVoiceInteraction()) {
                        df.left = of.left = cf.left = mVoiceContentLeft;
                        df.top = of.top = cf.top = mVoiceContentTop;
                        df.right = of.right = cf.right = mVoiceContentRight;
                        df.bottom = of.bottom = cf.bottom = mVoiceContentBottom;
                    } else if (adjust != SOFT_INPUT_ADJUST_RESIZE) {
                        df.left = of.left = cf.left = mDockLeft;
                        df.top = of.top = cf.top = mDockTop;
                        df.right = of.right = cf.right = mDockRight;
                        df.bottom = of.bottom = cf.bottom = mDockBottom;
                    } else {
                        df.left = of.left = cf.left = mContentLeft;
                        df.top = of.top = cf.top = mContentTop;
                        df.right = of.right = cf.right = mContentRight;
                        df.bottom = of.bottom = cf.bottom = mContentBottom;
                    }
                    if (adjust != SOFT_INPUT_ADJUST_NOTHING) {
                        vf.left = mCurLeft;
                        vf.top = mCurTop;
                        vf.right = mCurRight;
                        vf.bottom = mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                }
            }
        }

        // TYPE_SYSTEM_ERROR is above the NavigationBar so it can't be allowed to extend over it.
        // Also, we don't allow windows in multi-window mode to extend out of the screen.
        if ((fl & FLAG_LAYOUT_NO_LIMITS) != 0 && attrs.type != TYPE_SYSTEM_ERROR
                && !win.isInMultiWindowMode()) {
            df.left = df.top = -10000;
            df.right = df.bottom = 10000;
            if (attrs.type != TYPE_WALLPAPER) {
                of.left = of.top = cf.left = cf.top = vf.left = vf.top = -10000;
                of.right = of.bottom = cf.right = cf.bottom = vf.right = vf.bottom = 10000;
            }
        }

        // If the device has a chin (e.g. some watches), a dead area at the bottom of the screen we
        // need to provide information to the clients that want to pretend that you can draw there.
        // We only want to apply outsets to certain types of windows. For example, we never want to
        // apply the outsets to floating dialogs, because they wouldn't make sense there.
        final boolean useOutsets = shouldUseOutsets(attrs, fl);
        if (isDefaultDisplay && useOutsets) {
            osf = mTmpOutsetFrame;
            osf.set(cf.left, cf.top, cf.right, cf.bottom);
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(mContext.getResources());
            if (outset > 0) {
                int rotation = mDisplayRotation;
                if (rotation == Surface.ROTATION_0) {
                    osf.bottom += outset;
                } else if (rotation == Surface.ROTATION_90) {
                    osf.right += outset;
                } else if (rotation == Surface.ROTATION_180) {
                    osf.top -= outset;
                } else if (rotation == Surface.ROTATION_270) {
                    osf.left -= outset;
                }
            }
        }

        win.computeFrameLw(pf, df, of, cf, vf, dcf, sf, osf);

        // Dock windows carve out the bottom of the screen, so normal windows
        // can't appear underneath them.
        if (attrs.type == TYPE_INPUT_METHOD && win.isVisibleOrBehindKeyguardLw()
                && win.isDisplayedLw() && !win.getGivenInsetsPendingLw()) {
            setLastInputMethodWindowLw(null, null);
            offsetInputMethodWindowLw(win);
        }
        if (attrs.type == TYPE_VOICE_INTERACTION && win.isVisibleOrBehindKeyguardLw()
                && !win.getGivenInsetsPendingLw()) {
            offsetVoiceInputWindowLw(win);
        }
    }

    @Override
    public boolean getInsetHintLw(WindowManager.LayoutParams attrs, Rect taskBounds,
            int displayRotation, int displayWidth, int displayHeight, Rect outContentInsets,
            Rect outStableInsets, Rect outOutsets) {
        final int fl = PolicyControl.getWindowFlags(null, attrs);
        final int sysuiVis = PolicyControl.getSystemUiVisibility(null, attrs);
        final int systemUiVisibility = (sysuiVis | attrs.subtreeSystemUiVisibility);

        final boolean useOutsets = outOutsets != null && shouldUseOutsets(attrs, fl);
        if (useOutsets) {
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(mContext.getResources());
            if (outset > 0) {
                if (displayRotation == Surface.ROTATION_0) {
                    outOutsets.bottom += outset;
                } else if (displayRotation == Surface.ROTATION_90) {
                    outOutsets.right += outset;
                } else if (displayRotation == Surface.ROTATION_180) {
                    outOutsets.top += outset;
                } else if (displayRotation == Surface.ROTATION_270) {
                    outOutsets.left += outset;
                }
            }
        }

        if ((fl & (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR))
                == (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR)) {
            int availRight, availBottom;
            if (canHideNavigationBar() &&
                    (systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0) {
                availRight = mUnrestrictedScreenLeft + mUnrestrictedScreenWidth;
                availBottom = mUnrestrictedScreenTop + mUnrestrictedScreenHeight;
            } else {
                availRight = mRestrictedScreenLeft + mRestrictedScreenWidth;
                availBottom = mRestrictedScreenTop + mRestrictedScreenHeight;
            }
            if ((systemUiVisibility & View.SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0) {
                if ((fl & FLAG_FULLSCREEN) != 0) {
                    outContentInsets.set(mStableFullscreenLeft, mStableFullscreenTop,
                            availRight - mStableFullscreenRight,
                            availBottom - mStableFullscreenBottom);
                } else {
                    outContentInsets.set(mStableLeft, mStableTop,
                            availRight - mStableRight, availBottom - mStableBottom);
                }
            } else if ((fl & FLAG_FULLSCREEN) != 0 || (fl & FLAG_LAYOUT_IN_OVERSCAN) != 0) {
                outContentInsets.setEmpty();
            } else if ((systemUiVisibility & (View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)) == 0) {
                outContentInsets.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            } else {
                outContentInsets.set(mCurLeft, mCurTop,
                        availRight - mCurRight, availBottom - mCurBottom);
            }

            outStableInsets.set(mStableLeft, mStableTop,
                    availRight - mStableRight, availBottom - mStableBottom);
            if (taskBounds != null) {
                calculateRelevantTaskInsets(taskBounds, outContentInsets,
                        displayWidth, displayHeight);
                calculateRelevantTaskInsets(taskBounds, outStableInsets,
                        displayWidth, displayHeight);
            }
            return mForceShowSystemBars;
        }
        outContentInsets.setEmpty();
        outStableInsets.setEmpty();
        return mForceShowSystemBars;
    }

    @Override
    public void finishLayoutLw() {
        return;
    }

    @Override
    public void beginPostLayoutPolicyLw(int displayWidth, int displayHeight) {
        mTopFullscreenOpaqueWindowState = null;
        mTopFullscreenOpaqueOrDimmingWindowState = null;
        mTopDockedOpaqueWindowState = null;
        mTopDockedOpaqueOrDimmingWindowState = null;
        mAppsToBeHidden.clear();
        mAppsThatDismissKeyguard.clear();
        mForceStatusBar = false;
        mForceStatusBarFromKeyguard = false;
        mForceStatusBarTransparent = false;
        mForcingShowNavBar = false;
        mForcingShowNavBarLayer = -1;
    }

    @Override
    public void applyPostLayoutPolicyLw(WindowState win, WindowManager.LayoutParams attrs,
            WindowState attached) {
        final int fl = PolicyControl.getWindowFlags(win, attrs);
        // if (mTopFullscreenOpaqueWindowState == null
        //         && win.isVisibleLw() && attrs.type == TYPE_INPUT_METHOD) {
		// 	// remove by zhonghong lihw begin
        //     mForcingShowNavBar = true;
        //     mForcingShowNavBarLayer = win.getSurfaceLayer();
		// 	// remove end
        // }
        if (attrs.type == TYPE_STATUS_BAR) {
            if ((attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                mForceStatusBarFromKeyguard = true;
            }
            if ((attrs.privateFlags & PRIVATE_FLAG_FORCE_STATUS_BAR_VISIBLE_TRANSPARENT) != 0) {
                mForceStatusBarTransparent = true;
            }
        }

        boolean appWindow = attrs.type >= FIRST_APPLICATION_WINDOW
                && attrs.type < FIRST_SYSTEM_WINDOW;
        final boolean showWhenLocked = (fl & FLAG_SHOW_WHEN_LOCKED) != 0;
        final boolean dismissKeyguard = (fl & FLAG_DISMISS_KEYGUARD) != 0;
        final int stackId = win.getStackId();
        if (mTopFullscreenOpaqueWindowState == null &&
                win.isVisibleOrBehindKeyguardLw() && !win.isGoneForLayoutLw()) {
            if ((fl & FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                if ((attrs.privateFlags & PRIVATE_FLAG_KEYGUARD) != 0) {
                    mForceStatusBarFromKeyguard = true;
                } else {
                    mForceStatusBar = true;
                }
            }

            final IApplicationToken appToken = win.getAppToken();

            // For app windows that are not attached, we decide if all windows in the app they
            // represent should be hidden or if we should hide the lockscreen. For attached app
            // windows we defer the decision to the window it is attached to.
            if (appWindow && attached == null) {
                if (showWhenLocked) {
                    // Remove any previous windows with the same appToken.
                    mAppsToBeHidden.remove(appToken);
                    mAppsThatDismissKeyguard.remove(appToken);
                    if (mAppsToBeHidden.isEmpty()) {
                        if (dismissKeyguard) {
                            mAppsThatDismissKeyguard.add(appToken);
                        } else if (win.isDrawnLw() || win.hasAppShownWindows()) {
                            mForceStatusBarFromKeyguard = false;
                        }
                    }
                } else if (dismissKeyguard) {
                    mAppsToBeHidden.remove(appToken);
                    mAppsThatDismissKeyguard.add(appToken);
                } else {
                    mAppsToBeHidden.add(appToken);
                }
                if (isFullscreen(attrs) && StackId.normallyFullscreenWindows(stackId)) {
                    mTopFullscreenOpaqueWindowState = win;
                    if (mTopFullscreenOpaqueOrDimmingWindowState == null) {
                        mTopFullscreenOpaqueOrDimmingWindowState = win;
                    }
                    if (mAppsToBeHidden.isEmpty() && showWhenLocked
                            && (win.isDrawnLw() || win.hasAppShownWindows())) {
                        mForceStatusBarFromKeyguard = false;
                    }
                }
            }
        }

        final boolean reallyVisible = win.isVisibleOrBehindKeyguardLw() && !win.isGoneForLayoutLw();

        // Voice interaction overrides both top fullscreen and top docked.
        if (reallyVisible && win.getAttrs().type == TYPE_VOICE_INTERACTION) {
            if (mTopFullscreenOpaqueWindowState == null) {
                mTopFullscreenOpaqueWindowState = win;
                if (mTopFullscreenOpaqueOrDimmingWindowState == null) {
                    mTopFullscreenOpaqueOrDimmingWindowState = win;
                }
            }
            if (mTopDockedOpaqueWindowState == null) {
                mTopDockedOpaqueWindowState = win;
                if (mTopDockedOpaqueOrDimmingWindowState == null) {
                    mTopDockedOpaqueOrDimmingWindowState = win;
                }
            }
        }

        // Keep track of the window if it's dimming but not necessarily fullscreen.
        if (mTopFullscreenOpaqueOrDimmingWindowState == null && reallyVisible
                && win.isDimming() && StackId.normallyFullscreenWindows(stackId)) {
            mTopFullscreenOpaqueOrDimmingWindowState = win;
        }

        // We need to keep track of the top "fullscreen" opaque window for the docked stack
        // separately, because both the "real fullscreen" opaque window and the one for the docked
        // stack can control View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.
        if (mTopDockedOpaqueWindowState == null && reallyVisible && appWindow && attached == null
                && isFullscreen(attrs) && stackId == DOCKED_STACK_ID) {
            mTopDockedOpaqueWindowState = win;
            if (mTopDockedOpaqueOrDimmingWindowState == null) {
                mTopDockedOpaqueOrDimmingWindowState = win;
            }
        }

        // Also keep track of any windows that are dimming but not necessarily fullscreen in the
        // docked stack.
        if (mTopDockedOpaqueOrDimmingWindowState == null && reallyVisible && win.isDimming()
                && stackId == DOCKED_STACK_ID) {
            mTopDockedOpaqueOrDimmingWindowState = win;
        }
    }

    @Override
    public int finishPostLayoutPolicyLw() {
        int changes = 0;
        boolean topIsFullscreen = false;

        final WindowManager.LayoutParams lp = (mTopFullscreenOpaqueWindowState != null)
                ? mTopFullscreenOpaqueWindowState.getAttrs()
                : null;

        if (mStatusBar != null) {
            boolean shouldBeTransparent = mForceStatusBarTransparent
                    && !mForceStatusBar
                    && !mForceStatusBarFromKeyguard;
            if (!shouldBeTransparent) {
                mStatusBarController.setShowTransparent(false /* transparent */);
            } else if (!mStatusBar.isVisibleLw()) {
                mStatusBarController.setShowTransparent(true /* transparent */);
            }

            WindowManager.LayoutParams statusBarAttrs = mStatusBar.getAttrs();
            boolean statusBarExpanded = statusBarAttrs.height == MATCH_PARENT
                    && statusBarAttrs.width == MATCH_PARENT;
            if (mForceStatusBar || mForceStatusBarFromKeyguard || mForceStatusBarTransparent
                    || statusBarExpanded) {
                if (mStatusBarController.setBarShowingLw(true)) {
                    changes |= FINISH_LAYOUT_REDO_LAYOUT;
                }
                // Maintain fullscreen layout until incoming animation is complete.
                topIsFullscreen = mTopIsFullscreen && mStatusBar.isAnimatingLw();
                // Transient status bar on the lockscreen is not allowed
                if (mForceStatusBarFromKeyguard && mStatusBarController.isTransientShowing()) {
                    mStatusBarController.updateVisibilityLw(false /*transientAllowed*/,
                            mLastSystemUiFlags, mLastSystemUiFlags);
                }
                if (statusBarExpanded && mNavigationBar != null) {
                    if (mNavigationBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                }
            } else if (mTopFullscreenOpaqueWindowState != null) {
                final int fl = PolicyControl.getWindowFlags(null, lp);
                topIsFullscreen = (fl & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
                        || (mLastSystemUiFlags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
                // The subtle difference between the window for mTopFullscreenOpaqueWindowState
                // and mTopIsFullscreen is that mTopIsFullscreen is set only if the window
                // has the FLAG_FULLSCREEN set.  Not sure if there is another way that to be the
                // case though.
                if (mStatusBarController.isTransientShowing()) {
                    if (mStatusBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                } else if (topIsFullscreen
                        && !mWindowManagerInternal.isStackVisible(FREEFORM_WORKSPACE_STACK_ID)
                        && !mWindowManagerInternal.isStackVisible(DOCKED_STACK_ID)) {
                    if (mStatusBarController.setBarShowingLw(false)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                } else {
                    if (mStatusBarController.setBarShowingLw(true)) {
                        changes |= FINISH_LAYOUT_REDO_LAYOUT;
                    }
                }
            }
        }

        if (mTopIsFullscreen != topIsFullscreen) {
            if (!topIsFullscreen) {
                // Force another layout when status bar becomes fully shown.
                changes |= FINISH_LAYOUT_REDO_LAYOUT;
            }
            mTopIsFullscreen = topIsFullscreen;
        }


        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            changes |= FINISH_LAYOUT_REDO_LAYOUT;
        }

        return changes;
    }

    @Override
    public boolean allowAppAnimationsLw() {
        // This api can enable/disable application animation globally
        // If you want to disable animation of all application, return false
        return true;
    }

    @Override
    public int focusChangedLw(WindowState lastFocus, WindowState newFocus) {
        mFocusedWindow = newFocus;
        if ((updateSystemUiVisibilityLw()&SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            // If the navigation bar has been hidden or shown, we need to do another
            // layout pass to update that window.
            return FINISH_LAYOUT_REDO_LAYOUT;
        }
        return 0;
    }

    @Override
    public void startedWakingUp() {
        // do nothing
    }

    @Override
    public void finishedWakingUp() {
        // do nothing
    }

    @Override
    public void startedGoingToSleep(int why) {
        // do nothing
    }

    @Override
    public void finishedGoingToSleep(int why) {
        // do nothing
    }

    @Override
    public void screenTurningOn(final ScreenOnListener screenOnListener) {
        updateScreenOffSleepToken(false);
        synchronized (mLock) {
            mScreenOnEarly = true;
            mScreenOnFully = false;
            mScreenOnListener = screenOnListener;
        }
    }

    @Override
    public void screenTurnedOn() {
        if (mScreenOnListener != null) {
            mScreenOnListener.onScreenOn();
            mScreenOnListener = null;
        }

        try {
            mWindowManager.enableScreenIfNeeded();
        } catch (RemoteException unhandled) {
        }

        synchronized (mLock) {
            mScreenOnEarly = false;
            mScreenOnFully = true;
        }
    }

    @Override
    public void screenTurnedOff() {
        updateScreenOffSleepToken(true);
        synchronized (mLock) {
            mScreenOnEarly = false;
            mScreenOnFully = false;
            mScreenOnListener = null;
        }
    }

    @Override
    public boolean isScreenOn() {
        return mScreenOnFully;
    }

    @Override
    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
        // do nothing
    }

    @Override
    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {
        // do nothing
    }

    @Override
    public void enableKeyguard(boolean enabled) {
        // do nothing
    }

    @Override
    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        // do nothing
    }

    @Override
    public boolean isKeyguardLocked() {
        return false;
    }

    @Override
    public boolean isKeyguardSecure(int userId) {
        return false;
    }

    @Override
    public boolean isKeyguardShowingOrOccluded() {
        return false;
    }

    @Override
    public boolean inKeyguardRestrictedKeyInputMode() {
        return false;
    }

    @Override
    public void dismissKeyguardLw() {
        // do nothing
    }

    @Override
    public void notifyActivityDrawnForKeyguardLw() {
        // do nothing
    }

    @Override
    public boolean isKeyguardDrawnLw() {
        return false;
    }

    @Override
    public int rotationForOrientationLw(int orientation, int lastRotation) {
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
    public void setSafeMode(boolean safeMode) {
        // do nothing
    }

    @Override
    public void systemReady() {
        updateUiMode();
    }

    @Override
    public void systemBooted() {
        mSystemBooted = true;
        screenTurningOn(null);
        screenTurnedOn();
    }

    @Override
    public void showBootMessage(final CharSequence msg, final boolean always) {
        // do nothing
    }

    @Override
    public void hideBootMessages() {
        // do nothing
    }

    @Override
    public void userActivity() {
        // do nothing
    }

    @Override
    public void enableScreenAfterBoot() {
        // do nothing
    }

    @Override
    public void setCurrentOrientationLw(int newOrientation) {
        // do nothing
    }

    @Override
    public boolean performHapticFeedbackLw(WindowState win, int effectId, boolean always) {
        return false;
    }

    @Override
    public void keepScreenOnStartedLw() {
    }

    @Override
    public void keepScreenOnStoppedLw() {
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
    public int adjustSystemUiVisibilityLw(int visibility) {
        mStatusBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);
        mNavigationBarController.adjustSystemUiVisibilityLw(mLastSystemUiFlags, visibility);

        // Reset any bits in mForceClearingStatusBarVisibility that
        // are now clear.
        mResettingSystemUiFlags &= visibility;
        // Clear any bits in the new visibility that are currently being
        // force cleared, before reporting it.
        return visibility & ~mResettingSystemUiFlags
                & ~mForceClearedSystemUiFlags;
    }

    @Override
    public void setRecentsVisibilityLw(boolean visible) {
        // do nothing
    }

    @Override
    public void setTvPipVisibilityLw(boolean visible) {
        // do nothing
    }

    @Override
    public boolean hasNavigationBar() {
        return mHasNavigationBar;
    }

    @Override
    public void lockNow(Bundle options) {
        // do nothing
    }

    @Override
    public void setLastInputMethodWindowLw(WindowState ime, WindowState target) {
        mLastInputMethodWindow = ime;
        mLastInputMethodTargetWindow = target;
    }

    @Override
    public void showRecentApps(boolean fromHome) {
        // do nothing
    }

    @Override
    public void showGlobalActions() {
        // do nothing
    }

    @Override
    public int getInputMethodWindowVisibleHeightLw() {
        return mDockBottom - mCurBottom;
    }

    @Override
    public void setCurrentUserLw(int newUserId) {
        // do nothing
    }

    @Override
    public void dump(String prefix, PrintWriter pw, String[] args) {
        // do nothing
    }

    @Override
    public boolean canMagnifyWindow(int windowType) {
        switch (windowType) {
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD:
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG:
            case WindowManager.LayoutParams.TYPE_NAVIGATION_BAR:
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
    public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        // do nothing
    }

    @Override
    public void getStableInsetsLw(int displayRotation, int displayWidth, int displayHeight,
            Rect outInsets) {
        outInsets.setEmpty();

        // Navigation bar and status bar.
        getNonDecorInsetsLw(displayRotation, displayWidth, displayHeight, outInsets);
        if (mStatusBar != null) {
            outInsets.top = mStatusBarHeight;
        }
    }

    @Override
    public boolean isNavBarForcedShownLw(WindowState windowState) {
        return mForceShowSystemBars;
    }

    @Override
    public void getNonDecorInsetsLw(int displayRotation, int displayWidth, int displayHeight,
            Rect outInsets) {
        outInsets.setEmpty();

        // Only navigation bar
        if (mNavigationBar != null) {
            int position = navigationBarPosition(displayWidth, displayHeight, displayRotation);
            if (position == NAV_BAR_BOTTOM) {
                outInsets.bottom = getNavigationBarHeight(displayRotation, mUiMode);
            } else if (position == NAV_BAR_RIGHT) {
                outInsets.right = getNavigationBarWidth(displayRotation, mUiMode);
            } else if (position == NAV_BAR_LEFT) {
                outInsets.left = getNavigationBarWidth(displayRotation, mUiMode);
            }
        }
    }

    @Override
    public boolean isDockSideAllowed(int dockSide) {
        return false;
    }

    @Override
    public void onConfigurationChanged() {
        final Resources res = mContext.getResources();

        mStatusBarHeight =
                res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height);

        // Height of the navigation bar when presented horizontally at bottom
        mNavigationBarHeightForRotationDefault[mPortraitRotation] =
        mNavigationBarHeightForRotationDefault[mUpsideDownRotation] =
                res.getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height);
        mNavigationBarHeightForRotationDefault[mLandscapeRotation] =
        mNavigationBarHeightForRotationDefault[mSeascapeRotation] = res.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height_landscape);

        // Width of the navigation bar when presented vertically along one side
        mNavigationBarWidthForRotationDefault[mPortraitRotation] =
        mNavigationBarWidthForRotationDefault[mUpsideDownRotation] =
        mNavigationBarWidthForRotationDefault[mLandscapeRotation] =
        mNavigationBarWidthForRotationDefault[mSeascapeRotation] =
                res.getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_width);
    }

    @Override
    public boolean shouldRotateSeamlessly(int oldRotation, int newRotation) {
        return false;
    }
}
