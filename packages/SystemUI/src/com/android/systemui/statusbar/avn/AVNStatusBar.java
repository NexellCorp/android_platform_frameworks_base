/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.avn;

import android.os.IBinder;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.util.Log;
import android.util.Slog;
import android.graphics.PixelFormat;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;


import com.android.internal.statusbar.StatusBarIcon;
import com.android.systemui.statusbar.ActivatableNotificationView;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.R;

import java.util.Map;
import java.util.HashMap;

/*
 * Status bar implementation for "large screen" products that mostly present no on-screen nav
 */

public class AVNStatusBar extends BaseStatusBar {
    static final String TAG = "AVNStatusBar";
    //public static final boolean DEBUG = BaseStatusBar.DEBUG;
    public static final boolean DEBUG = true;

    private MultiWindowControlBarView mControlBar = null;

    private MiniLauncherView mMiniLauncher = null;

    private MultiWindowDragControlView mDragControl = null;

    private FloatingWindowControlView mFloatingControl = null;

    /**
     * Key-Action Binding
     */
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
                case 0:
                    pressType = new String("Short Pressed");
                    break;
                case 1:
                    pressType = new String("Long Pressed");
                    break;
                case 2:
                    pressType = new String("Double Pressed");
                    break;
                case 3:
                    pressType = new String("Triple Pressed");
                    break;
            }
            return "KeyCode=" + mKeyCode + "==>" + pressType + ", Action=" + mAction + ", ActionArg=" + mActionArg;
        }
    }
    private Map<Integer, ButtonActionItem> mButtonActionMap = null;

    private void addMultiWindowControlBar() {
        if (DEBUG) Slog.d(TAG, "addMultiWindowControlBar: " + mControlBar);
        if (mControlBar == null) return;

        mControlBar.setVisibility(View.GONE);
        mWindowManager.addView(mControlBar, getMultiWindowControlBarLayoutParams());

        mMiniLauncher.setVisibility(View.GONE);
        mWindowManager.addView(mMiniLauncher, getMiniLauncherLayoutParams());

        mDragControl.setVisibility(View.GONE);
        mWindowManager.addView(mDragControl, getMultiWindowDragControlLayoutParams());

        mFloatingControl.setVisibility(View.GONE);
        mWindowManager.addView(mFloatingControl, getMultiWindowFloatingControlLayoutParams());
    }

    private WindowManager.LayoutParams getMultiWindowControlBarLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_MULTIWINDOW_CONTROL,
                    0
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);

        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("MultiWindowControlBar");
        lp.windowAnimations = 0;
        return lp;
    }

    private WindowManager.LayoutParams getMiniLauncherLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_MULTIWINDOW_MINILAUNCHER,
                0 
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("MiniLauncher");
        lp.windowAnimations = 0;
        return lp;
    }

    private WindowManager.LayoutParams getMultiWindowDragControlLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_MULTIWINDOW_DRAGCONTROL,
                    0
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);

        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("MultiWindowDragControl");
        lp.windowAnimations = 0;
        return lp;
    }

    private WindowManager.LayoutParams getMultiWindowFloatingControlLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_MULTIWINDOW_FLOATINGCONTROL,
                    0
                    | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);

        if (ActivityManager.isHighEndGfx()) {
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }

        lp.setTitle("MultiWindowFloatingControl");
        lp.windowAnimations = 0;
        return lp;
    }

    public void notifyUiVisibilityChanged(int vis) {
        try {
            mWindowManagerService.statusBarVisibilityChanged(vis);
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void start() {
        super.start(); // call createAndAddWindows()

        if (DEBUG) Slog.d(TAG, "start");

        addMultiWindowControlBar();
    }

    @Override
    public void addIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
    }

    @Override
    public void updateIcon(String slot, int index, int viewIndex, StatusBarIcon old,
            StatusBarIcon icon) {
    }

    @Override
    public void removeIcon(String slot, int index, int viewIndex) {
    }

    @Override
    public void addNotification(StatusBarNotification notification, RankingMap ranking) {
    }

    @Override
    protected void updateNotificationRanking(RankingMap ranking) {
    }

    @Override
    public void removeNotification(String key, RankingMap ranking) {
    }

    @Override
    public void disable(int state, boolean animate) {
    }

    @Override
    public void animateExpandNotificationsPanel() {
    }

    @Override
    public void animateCollapsePanels(int flags) {
    }

    @Override
    public void setSystemUiVisibility(int vis, int mask) {
      if (DEBUG) Slog.d(TAG, "setSystemUiVisibility: " + vis);

      if ((vis & View.MULTIWINDOW_CONTROL_VISIBLE) == View.MULTIWINDOW_CONTROL_VISIBLE) {
          mControlBar.setVisibility(View.VISIBLE);
      } else if ((vis & View.MULTIWINDOW_CONTROL_HIDDEN) == View.MULTIWINDOW_CONTROL_HIDDEN) {
          mControlBar.setVisibility(View.GONE);
      }

      if ((vis & View.MULTIWINDOW_MINILAUNCHER_VISIBLE) == View.MULTIWINDOW_MINILAUNCHER_VISIBLE) {
          mMiniLauncher.setVisibility(View.VISIBLE);
      } else if ((vis & View.MULTIWINDOW_MINILAUNCHER_HIDDEN) == View.MULTIWINDOW_MINILAUNCHER_HIDDEN) {
          mMiniLauncher.setVisibility(View.GONE);
      }

      if ((vis & View.MULTIWINDOW_DRAG_VISIBLE) == View.MULTIWINDOW_DRAG_VISIBLE) {
          mDragControl.setVisibility(View.VISIBLE);
      } else if ((vis & View.MULTIWINDOW_DRAG_HIDDEN) == View.MULTIWINDOW_DRAG_HIDDEN) {
          mDragControl.setVisibility(View.GONE);
      }

      if ((vis & View.MULTIWINDOW_FLOATING_VISIBLE) == View.MULTIWINDOW_FLOATING_VISIBLE) {
          mFloatingControl.setVisibility(View.VISIBLE);
      } else if ((vis & View.MULTIWINDOW_FLOATING_HIDDEN) == View.MULTIWINDOW_FLOATING_HIDDEN) {
          mFloatingControl.setVisibility(View.GONE);
      }
    }

    @Override
    public void topAppWindowChanged(boolean visible) {
    }

    @Override
    public void setImeWindowStatus(IBinder token, int vis, int backDisposition,
            boolean showImeSwitcher) {
    }

    @Override
    public void toggleRecentApps() {
    }

    @Override // CommandQueue
    public void setWindowState(int window, int state) {
    }

    @Override // CommandQueue
    public void buzzBeepBlinked() {
    }

    @Override // CommandQueue
    public void notificationLightOff() {
    }

    @Override // CommandQueue
    public void notificationLightPulse(int argb, int onMillis, int offMillis) {
    }

    @Override
    protected WindowManager.LayoutParams getSearchLayoutParams(
            LayoutParams layoutParams) {
        return null;
    }

    @Override
    protected void haltTicker() {
    }

    @Override
    protected void setAreThereNotifications() {
    }

    @Override
    protected void updateNotifications() {
    }

    @Override
    protected void tick(StatusBarNotification n, boolean firstTime) {
    }

    @Override
    protected void updateExpandedViewPos(int expandedPosition) {
    }

    @Override
    protected boolean shouldDisableNavbarGestures() {
        return true;
    }

    public View getStatusBarView() {
        return null;
    }

    @Override
    public void resetHeadsUpDecayTimer() {
    }

    @Override
    public void scheduleHeadsUpOpen() {
    }

    @Override
    public void scheduleHeadsUpEscalation() {
    }

    @Override
    public void scheduleHeadsUpClose() {
    }

    @Override
    protected int getMaxKeyguardNotifications() {
        return 0;
    }

    @Override
    public void animateExpandSettingsPanel() {
    }

    @Override
    protected void createAndAddWindows() {
        if (DEBUG) Slog.d(TAG, "createAndAddWindows");

        mControlBar = (MultiWindowControlBarView) View.inflate(mContext, R.layout.multiwindowcontrol_bar, null);
        if (mControlBar == null) {
            Slog.e(TAG, "FATAL ERROR : failed to create MultiWindowControlBarView");
        }

        mDragControl = (MultiWindowDragControlView) View.inflate(mContext, R.layout.multiwindow_dragcontrol, null);
        if (mDragControl == null) {
          Slog.e(TAG, "FATAL ERROR : failed to create MultiWindowDragControlView");
        }

        mFloatingControl = (FloatingWindowControlView) View.inflate(mContext, R.layout.multiwindow_floatingcontrol, null);
        if (mFloatingControl == null) {
            Slog.e(TAG, "FATAL ERROR : failed to create FloatingWindowControlView");
        }

        mMiniLauncher = (MiniLauncherView) View.inflate(mContext, R.layout.minilauncher, null);
        if (mMiniLauncher == null) {
            Slog.e(TAG, "FATAL ERROR : failed to create MiniLauncherView");
        }
        mMiniLauncher.setParent(this);

        boolean buttonMapping = initButtonMapping();
        if (buttonMapping) {
            applyButtonMapping();
        }
    }
    
    private void applyButtonMapping() {
        // mControlBar
        ButtonActionItem item = getButtonActionItem(4); // ACTION_MINILAUNCHER
        if (item != null) {
            mControlBar.setMiniLauncherKeyCode(item.getKeyCode());
        }
        item = getButtonActionItem(60); // ACTION_WINDOW0_TOGGLE_FULL
        if (item != null) {
            mControlBar.setLeftFullToggleKeyCode(item.getKeyCode());
        }
        item = getButtonActionItem(61); // ACTION_WINDOW1_TOGGLE_FULL
        if (item != null) {
            mControlBar.setRightFullToggleKeyCode(item.getKeyCode());
        }

        // mFloatingControl
        item = getButtonActionItem(121); // ACTION_WINDOW1_SIZE_UP
        if (item != null) {
            mFloatingControl.setFloatingSizeUpKeyCode(item.getKeyCode());
        }
        item = getButtonActionItem(131); // ACTION_WINDOW1_SIZE_DOWN
        if (item != null) {
            mFloatingControl.setFloatingSizeDownKeyCode(item.getKeyCode());
        }
        item = getButtonActionItem(41); // ACTION_WINDOW1_EXIT
        if (item != null) {
            mFloatingControl.setFloatingExitKeyCode(item.getKeyCode());
        }
        item = getButtonActionItem(71); // ACTION_WINDOW1_TOGGLE_FLOATING
        if (item != null) {
            mFloatingControl.setFloatingDockingKeyCode(item.getKeyCode());
        }
    }

    private boolean initButtonMapping() {
        String[] items = mContext.getResources().getStringArray(com.android.internal.R.array.config_nexell_avn_keyActionMappingTable);
        if (items == null) {
            Slog.e(TAG, "initButtonMapping Error --> can't get config_nexell_avn_keyActionMappingTable");
            return false;
        }

        mButtonActionMap = new HashMap<Integer, ButtonActionItem>();

        for (int i = 0; i < items.length; i++) {
            String keyAction = items[i];
            ButtonActionItem item = parseActionString(keyAction);
            if (item != null) {
                setButtonActionItem(item.getAction(), item);
            }
        }

        return true;
    }

    private ButtonActionItem getButtonActionItem(int action) {
        return mButtonActionMap.get(action);
    }

    private void setButtonActionItem(int action, ButtonActionItem item) {
        mButtonActionMap.put(action, item);
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

    @Override
    protected void refreshLayout(int layoutDirection) {
    }

    @Override
    public void onActivated(ActivatableNotificationView view) {
    }

    @Override
    public void onActivationReset(ActivatableNotificationView view) {
    }

    @Override
    public void showScreenPinningRequest() {
    }
}
