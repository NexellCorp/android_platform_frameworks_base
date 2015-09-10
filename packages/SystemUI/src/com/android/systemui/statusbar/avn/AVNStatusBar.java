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

    private void addMultiWindowControlBar() {
        if (DEBUG) Slog.d(TAG, "addMultiWindowControlBar: " + mControlBar);
        if (mControlBar == null) return;

        mControlBar.setVisibility(View.GONE);
        mWindowManager.addView(mControlBar, getMultiWindowControlBarLayoutParams());

        mMiniLauncher.setVisibility(View.GONE);
        mWindowManager.addView(mMiniLauncher, getMiniLauncherLayoutParams());

        mDragControl.setVisibility(View.GONE);
        mWindowManager.addView(mDragControl, getMultiWindowDragControlLayoutParams());
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

        mMiniLauncher = (MiniLauncherView) View.inflate(mContext, R.layout.minilauncher, null);
        if (mMiniLauncher == null) {
            Slog.e(TAG, "FATAL ERROR : failed to create MiniLauncherView");
        }
        mMiniLauncher.setParent(this);
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
