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

package com.android.server;

import android.app.ActivityThread;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources.Theme;
import android.os.BaseBundle;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.IMountService;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.os.BinderInternal;
import com.android.internal.os.ZygoteInit;
import com.android.server.am.ActivityManagerService;
import com.android.server.display.DisplayManagerService;
import com.android.server.input.InputManagerService;
import com.android.server.job.JobSchedulerService;
import com.android.server.lights.LightsService;
import com.android.server.os.SchedulingPolicyService;
import com.android.server.pm.BackgroundDexOptService;
import com.android.server.pm.Installer;
import com.android.server.pm.LauncherAppsService;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.UserManagerService;
import com.android.server.power.PowerManagerService;
import com.android.server.wm.WindowManagerService;

import dalvik.system.VMRuntime;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public final class SystemServer {
    private static final String TAG = "SystemServer";

    // The earliest supported time.  We pick one day into 1970, to
    // give any timezone code room without going into negative time.
    private static final long EARLIEST_SUPPORTED_TIME = 86400 * 1000;

    private static final String JOB_SCHEDULER_SERVICE_CLASS =
            "com.android.server.job.JobSchedulerService";
    private static final String MOUNT_SERVICE_CLASS =
            "com.android.server.MountService$Lifecycle";
    private static final String ACCOUNT_SERVICE_CLASS =
            "com.android.server.accounts.AccountManagerService$Lifecycle";
    private static final String CONTENT_SERVICE_CLASS =
            "com.android.server.content.ContentService$Lifecycle";

    // maximum number of binder threads used for system_server
    // will be higher than the system default
    private static final int sMaxBinderThreads = 31;

    /**
     * Default theme used by the system context. This is used to style
     * system-provided dialogs, such as the Power Off dialog, and other
     * visual content.
     */
    private static final int DEFAULT_SYSTEM_THEME =
            com.android.internal.R.style.Theme_DeviceDefault_System;

    private Context mSystemContext;
    private SystemServiceManager mSystemServiceManager;

    // TODO: remove all of these references by improving dependency resolution and boot phases
    private PowerManagerService mPowerManagerService;
    private ActivityManagerService mActivityManagerService;
    private DisplayManagerService mDisplayManagerService;
    private PackageManagerService mPackageManagerService;
    private PackageManager mPackageManager;
    private ContentResolver mContentResolver;

    private boolean mFirstBoot;

    private final boolean mRuntimeRestart;

    /**
     * The main entry point from zygote.
     */
    public static void main(String[] args) {
        new SystemServer().run();
    }

    public SystemServer() {
        // Remember if it's runtime restart(when sys.boot_completed is already set) or reboot
        mRuntimeRestart = "1".equals(SystemProperties.get("sys.boot_completed"));
    }

    private void run() {
        // If a device's clock is before 1970 (before 0), a lot of
        // APIs crash dealing with negative numbers, notably
        // java.io.File#setLastModified, so instead we fake it and
        // hope that time from cell towers or NTP fixes it shortly.
        if (System.currentTimeMillis() < EARLIEST_SUPPORTED_TIME) {
            Slog.w(TAG, "System clock is before 1970; setting to 1970.");
            SystemClock.setCurrentTimeMillis(EARLIEST_SUPPORTED_TIME);
        }

        // Here we go!
        Slog.i(TAG, "[BootProf]Entered the Android system server!");

        // In case the runtime switched since last boot (such as when
        // the old runtime was removed in an OTA), set the system
        // property so that it is in sync. We can't do this in
        // libnativehelper's JniInvocation::Init code where we already
        // had to fallback to a different runtime because it is
        // running as root and we need to be the system user to set
        // the property. http://b/11463182
        SystemProperties.set("persist.sys.dalvik.vm.lib.2", VMRuntime.getRuntime().vmLibrary());

        // Mmmmmm... more memory!
        VMRuntime.getRuntime().clearGrowthLimit();

        // The system server has to run all of the time, so it needs to be
        // as efficient as possible with its memory usage.
        VMRuntime.getRuntime().setTargetHeapUtilization(0.8f);

        // Some devices rely on runtime fingerprint generation, so make sure
        // we've defined it before booting further.
        Build.ensureFingerprintProperty();

        // Within the system server, it is an error to access Environment paths without
        // explicitly specifying a user.
        Environment.setUserRequired(true);

        // Within the system server, any incoming Bundles should be defused
        // to avoid throwing BadParcelableException.
        BaseBundle.setShouldDefuse(true);

        // Ensure binder calls into the system always run at foreground priority.
        BinderInternal.disableBackgroundScheduling(true);

        // Increase the number of binder threads in system_server
        BinderInternal.setMaxThreads(sMaxBinderThreads);

        // Prepare the main looper thread (this thread).
        android.os.Process.setThreadPriority(
                android.os.Process.THREAD_PRIORITY_FOREGROUND);
        android.os.Process.setCanSelfBackground(false);
        Looper.prepareMainLooper();

        // Initialize native services.
        System.loadLibrary("android_servers");

        // Initialize the system context.
        createSystemContext();

        // Create the system service manager.
        mSystemServiceManager = new SystemServiceManager(mSystemContext);
        mSystemServiceManager.setRuntimeRestarted(mRuntimeRestart);
        LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);

        startBootstrapServices();
        startCoreServices();
        startOtherServices();
    }

    /**
     * Starts the small tangle of critical services that are needed to get
     * the system off the ground.  These services have complex mutual dependencies
     * which is why we initialize them all in one place here.  Unless your service
     * is also entwined in these dependencies, it should be initialized in one of
     * the other functions.
     */
    private void startBootstrapServices() {
        Slog.i(TAG, "[BootProf]Entered startBootstrapService!");

        // Wait for installd to finish starting up so that it has a chance to
        // create critical directories such as /data/user with the appropriate
        // permissions.  We need this to complete before we initialize other services.
        Installer installer = mSystemServiceManager.startService(Installer.class);

        // Activity manager runs the show.
        mActivityManagerService = mSystemServiceManager.startService(
                ActivityManagerService.Lifecycle.class).getService();
        mActivityManagerService.setSystemServiceManager(mSystemServiceManager);
        mActivityManagerService.setInstaller(installer);

        // Power manager needs to be started early because other services need it.
        // Native daemons may be watching for it to be registered so it must be ready
        // to handle incoming binder calls immediately (including being able to verify
        // the permissions for those calls).
        mPowerManagerService = mSystemServiceManager.startService(PowerManagerService.class);

        // Now that the power manager has been started, let the activity manager
        // initialize power management features.
        mActivityManagerService.initPowerManagement();

        // Manages LEDs and display backlight so we need it to bring up the display.
        mSystemServiceManager.startService(LightsService.class);

        // Display manager is needed to provide display metrics before package manager
        // starts up.
        mDisplayManagerService = mSystemServiceManager.startService(DisplayManagerService.class);

        // We need the default display before we can initialize the package manager.
        mSystemServiceManager.startBootPhase(SystemService.PHASE_WAIT_FOR_DEFAULT_DISPLAY);

        Slog.d(TAG, "++++++ 236");
        mPackageManagerService = PackageManagerService.main(mSystemContext, installer, false, false);
        Slog.d(TAG, "++++++ 238");
        mFirstBoot = mPackageManagerService.isFirstBoot();
        Slog.d(TAG, "++++++ 240");
        mPackageManager = mSystemContext.getPackageManager();

        mSystemServiceManager.startService(UserManagerService.LifeCycle.class);
        Slog.d(TAG, "++++++ 244");

        // Initialize attribute cache used to cache resources from packages.
        AttributeCache.init(mSystemContext);
        Slog.d(TAG, "++++++ 248");

        // Set up the Application instance for the system process and get started.
        mActivityManagerService.setSystemProcess();
        Slog.d(TAG, "++++++ 252");
    }

    /**
     * Starts some essential services that are not tangled up in the bootstrap process.
     */
    private void startCoreServices() {
        Slog.i(TAG, "[BootProf]Entered startCoreServices!");
    }

    /**
     * Starts a miscellaneous grab bag of stuff that has yet to be refactored
     * and organized.
     */
    private void startOtherServices() {
        Slog.i(TAG, "[BootProf]Entered startOtherService!");

        IMountService mountService = null;
        WindowManagerService wm = null;
        InputManagerService inputManager = null;
        // AssetAtlasService atlas = null;

        final Context context = mSystemContext;

        Slog.i(TAG, "[BootProf]Reading configuration...");
        SystemConfig.getInstance();

        ServiceManager.addService("scheduling_policy", new SchedulingPolicyService());
        Slog.d(TAG, "++++++ 280");

        mContentResolver = context.getContentResolver();
        Slog.d(TAG, "++++++ 283");

        // The AccountManager must come before the ContentService
        mSystemServiceManager.startService(ACCOUNT_SERVICE_CLASS);
        Slog.d(TAG, "++++++ 287");

        mSystemServiceManager.startService(CONTENT_SERVICE_CLASS);
        Slog.d(TAG, "++++++ 290");

        mActivityManagerService.installSystemProviders();
        Slog.d(TAG, "++++++ 293");

        mSystemServiceManager.startService(MOUNT_SERVICE_CLASS);
        Slog.d(TAG, "++++++ 296");
        mountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));
        Slog.d(TAG, "++++++ 298");

        inputManager = new InputManagerService(context);
        Slog.d(TAG, "++++++ 301");

        wm = WindowManagerService.main(context, inputManager, true, false, false);
        Slog.d(TAG, "++++++ 304");
        ServiceManager.addService(Context.WINDOW_SERVICE, wm);
        Slog.d(TAG, "++++++ 306");
        ServiceManager.addService(Context.INPUT_SERVICE, inputManager);
        Slog.d(TAG, "++++++ 308");

        mActivityManagerService.setWindowManager(wm);
        Slog.d(TAG, "++++++ 311");

        inputManager.setWindowManagerCallbacks(wm.getInputMonitor());
        inputManager.start();
        Slog.d(TAG, "++++++ 315");

        mDisplayManagerService.windowManagerAndInputReady();
        Slog.d(TAG, "++++++ 318");

        mSystemServiceManager.startService(InputMethodManagerService.Lifecycle.class);
        Slog.d(TAG, "++++++ 320");

        wm.displayReady();
        Slog.d(TAG, "++++++ 324");

        // mSystemServiceManager.startService(MOUNT_SERVICE_CLASS);
        // Slog.d(TAG, "++++++ 320");
        // mountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));
        // Slog.d(TAG, "++++++ 324");

        // We start this here so that we update our configuration to set watch or television
        // as appropriate.
        mSystemServiceManager.startService(UiModeManagerService.class);
        Slog.d(TAG, "++++++ 334");

        mPackageManagerService.updatePackagesIfNeeded();
        Slog.d(TAG, "++++++ 337");
        mPackageManagerService.performFstrimIfNeeded();
        Slog.d(TAG, "++++++ 339");

        /*
         * MountService has a few dependencies: Notification Manager and
         * AppWidget Provider. Make sure MountService is completely started
         * first before continuing.
         */
        try {
            mountService.waitForAsecScan();
        } catch (RemoteException ignored) {
        }
        Slog.d(TAG, "++++++ 350");

        mSystemServiceManager.startService(JobSchedulerService.class);
        Slog.d(TAG, "++++++ 353");

        // atlas = new AssetAtlasService(context);
        // ServiceManager.addService(AssetAtlasService.ASSET_ATLAS_SERVICE, atlas);
        
        if (mFirstBoot)
            BackgroundDexOptService.schedule(context);

        // Enable the JIT for the system_server process
        VMRuntime.getRuntime().startJitCompilation();
        Slog.d(TAG, "++++++ 363");

        // Skip PHASE_LOCK_SETTINGS_READY
        mSystemServiceManager.startBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        Slog.d(TAG, "++++++ 367");

        wm.systemReady();
        Slog.d(TAG, "++++++ 370");

        // Update the configuration for this context by hand, because we're going
        // to start using it before the config change done in wm.systemReady() will
        // propagate to it.
        Configuration config = wm.computeNewConfiguration();
        Slog.d(TAG, "++++++ 371");
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager w = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        w.getDefaultDisplay().getMetrics(metrics);
        context.getResources().updateConfiguration(config, metrics);
        Slog.d(TAG, "++++++ 376");

        // The system context's theme may be configuration-dependent.
        final Theme systemTheme = context.getTheme();
        if (systemTheme.getChangingConfigurations() != 0) {
            systemTheme.rebase();
        }
        Slog.d(TAG, "++++++ 383");

        mPowerManagerService.systemReady(mActivityManagerService.getAppOpsService());
        Slog.d(TAG, "++++++ 386");
        mPackageManagerService.systemReady();
        Slog.d(TAG, "++++++ 388");
        mDisplayManagerService.systemReady(false, false);
        Slog.d(TAG, "++++++ 390");

        // final AssetAtlasService atlasF = atlas;
        final InputManagerService inputManagerF = inputManager;

        // We now tell the activity manager it is okay to run third party
        // code.  It will call back into us once it has gotten to the state
        // where third party code can really run (but before it has actually
        // started launching the initial applications), for us to complete our
        // initialization.
        try {
            mActivityManagerService.systemReady(new Runnable() {
                @Override
                public void run() {
                    Slog.i(TAG, "[BootProf]Making services ready");
                    mSystemServiceManager.startBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);
                    Slog.d(TAG, "++++++ 406");
                    mSystemServiceManager.startBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
                    Slog.d(TAG, "++++++ 408");

                    // atlasF.systemRunning();
                    inputManagerF.systemRunning();
                    Slog.d(TAG, "++++++ 412");
                }
            });
        } catch (Throwable e) {
            reportWtf("Failed to ActivityManager.systemReady()", e);
        }

        Slog.d(TAG, "++++++ 419");
        // Loop forever.
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }

    private void createSystemContext() {
        ActivityThread activityThread = ActivityThread.systemMain();
        mSystemContext = activityThread.getSystemContext();
        mSystemContext.setTheme(DEFAULT_SYSTEM_THEME);
    }

    private void reportWtf(String msg, Throwable e) {
        Slog.w(TAG, "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }
}
