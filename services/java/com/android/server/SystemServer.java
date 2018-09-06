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
import com.android.server.webkit.WebViewUpdateService;
import com.android.server.telecom.TelecomLoaderService;
import com.android.server.connectivity.IpConnectivityMetrics;
import com.android.server.accessibility.AccessibilityManagerService;
import com.android.server.devicepolicy.DevicePolicyManagerService;
import com.android.server.net.NetworkStatsService;
import com.android.server.net.NetworkPolicyManagerService;
import com.android.server.notification.NotificationManagerService;
import com.android.server.audio.AudioService;
import com.android.server.twilight.TwilightService;
import com.android.server.restrictions.RestrictionsManagerService;
import com.android.server.media.MediaSessionService;
import com.android.server.media.MediaRouterService;
import com.android.server.pm.ShortcutService;

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
    private static final String WIFI_P2P_SERVICE_CLASS =
            "com.android.server.wifi.p2p.WifiP2pService";
    private static final String WIFI_SERVICE_CLASS =
            "com.android.server.wifi.WifiService";
    private static final String SEARCH_MANAGER_SERVICE_CLASS =
            "com.android.server.search.SearchManagerService$Lifecycle";
    private static final String USB_SERVICE_CLASS =
            "com.android.server.usb.UsbService$Lifecycle";
    private static final String APPWIDGET_SERVICE_CLASS =
            "com.android.server.appwidget.AppWidgetService";

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
    private WebViewUpdateService mWebViewUpdateService;

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

        Slog.i(TAG, "[BootProf]call PackageManagerService.main");
        mPackageManagerService = PackageManagerService.main(mSystemContext, installer, false, false);
        mFirstBoot = mPackageManagerService.isFirstBoot();
        mPackageManager = mSystemContext.getPackageManager();

        Slog.i(TAG, "[BootProf]start UserManagerService");
        mSystemServiceManager.startService(UserManagerService.LifeCycle.class);

        // Initialize attribute cache used to cache resources from packages.
        Slog.i(TAG, "[BootProf]start AttributeCache");
        AttributeCache.init(mSystemContext);

        // Set up the Application instance for the system process and get started.
        Slog.i(TAG, "[BootProf]ActivityManagerService setSystemProcess");
        mActivityManagerService.setSystemProcess();
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

        Slog.i(TAG, "[BootProf]start SchedulingPolicyService");
        ServiceManager.addService("scheduling_policy", new SchedulingPolicyService());

        Slog.i(TAG, "[BootProf]start getContentResolver");
        mContentResolver = context.getContentResolver();

        // The AccountManager must come before the ContentService
        Slog.i(TAG, "[BootProf]start AccountService");
        mSystemServiceManager.startService(ACCOUNT_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf]start ContentService");
        mSystemServiceManager.startService(CONTENT_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf]ActivityManager installSystemProviders");
        mActivityManagerService.installSystemProviders();

        Slog.i(TAG, "[BootProf]start MountService");
        mSystemServiceManager.startService(MOUNT_SERVICE_CLASS);
        mountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));

        Slog.i(TAG, "[BootProf]start InputManagerService");
        inputManager = new InputManagerService(context);

        Slog.i(TAG, "[BootProf]start WindowManagerService");
        wm = WindowManagerService.main(context, inputManager, true, false, false);
        ServiceManager.addService(Context.WINDOW_SERVICE, wm);
        ServiceManager.addService(Context.INPUT_SERVICE, inputManager);

        mActivityManagerService.setWindowManager(wm);

        inputManager.setWindowManagerCallbacks(wm.getInputMonitor());
        inputManager.start();

        mDisplayManagerService.windowManagerAndInputReady();

        Slog.i(TAG, "[BootProf]start InputMethodManagerService");
        mSystemServiceManager.startService(InputMethodManagerService.Lifecycle.class);

        Slog.i(TAG, "[BootProf]wm.displayReady");
        wm.displayReady();

        // We start this here so that we update our configuration to set watch or television
        // as appropriate.
        Slog.i(TAG, "[BootProf]start UiModeManagerService");
        mSystemServiceManager.startService(UiModeManagerService.class);

        Slog.i(TAG, "[BootProf]pm updatePackagesIfNeeded");
        mPackageManagerService.updatePackagesIfNeeded();
        Slog.i(TAG, "[BootProf]pm performFstrimIfNeeded");
        mPackageManagerService.performFstrimIfNeeded();

        /*
         * MountService has a few dependencies: Notification Manager and
         * AppWidget Provider. Make sure MountService is completely started
         * first before continuing.
         */
        Slog.i(TAG, "[BootProf]mount waitForAsecScan");
        try {
            mountService.waitForAsecScan();
        } catch (RemoteException ignored) {
        }

        Slog.i(TAG, "[BootProf]start JobSchedulerService");
        mSystemServiceManager.startService(JobSchedulerService.class);

        // atlas = new AssetAtlasService(context);
        // ServiceManager.addService(AssetAtlasService.ASSET_ATLAS_SERVICE, atlas);
        
        if (mFirstBoot)
            BackgroundDexOptService.schedule(context);

        // Enable the JIT for the system_server process
        Slog.i(TAG, "[BootProf] startJitCompilation");
        VMRuntime.getRuntime().startJitCompilation();
        Slog.d(TAG, "++++++ 363");

        Slog.i(TAG, "[BootProf] PHASE_SYSTEM_SERVICES_READY");
        mSystemServiceManager.startBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);

        Slog.i(TAG, "[BootProf] wm.systemReady");
        wm.systemReady();

        // Update the configuration for this context by hand, because we're going
        // to start using it before the config change done in wm.systemReady() will
        // propagate to it.
        Slog.i(TAG, "[BootProf] wm.computeNewConfiguration");
        Configuration config = wm.computeNewConfiguration();
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager w = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        w.getDefaultDisplay().getMetrics(metrics);
        Slog.i(TAG, "[BootProf] updateConfiguration");
        context.getResources().updateConfiguration(config, metrics);

        // The system context's theme may be configuration-dependent.
        final Theme systemTheme = context.getTheme();
        if (systemTheme.getChangingConfigurations() != 0) {
            systemTheme.rebase();
        }

        Slog.i(TAG, "[BootProf] power systemReady");
        mPowerManagerService.systemReady(mActivityManagerService.getAppOpsService());
        Slog.i(TAG, "[BootProf] pm systemReady");
        mPackageManagerService.systemReady();
        Slog.i(TAG, "[BootProf] disp systemReady");
        mDisplayManagerService.systemReady(false, false);

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
                    Slog.i(TAG, "[BootProf] PHASE_ACTIVITY_MANAGER_READY");
                    mSystemServiceManager.startBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);
                    Slog.i(TAG, "[BootProf] PHASE_THIRD_PARTY_APPS_CAN_START");
                    mSystemServiceManager.startBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

                    // atlasF.systemRunning();
                    Slog.i(TAG, "[BootProf] inputManager systemRunning");
                    inputManagerF.systemRunning();
                }
            });
        } catch (Throwable e) {
            reportWtf("Failed to ActivityManager.systemReady()", e);
        }

        Slog.i(TAG, "[BootProf] start WebViewUpdateService");
        mWebViewUpdateService = mSystemServiceManager.startService(WebViewUpdateService.class);
        mWebViewUpdateService.prepareWebViewInSystemServer();

        Slog.i(TAG, "[BootProf] start TelecomLoaderService");
        final TelecomLoaderService telecomLoaderService = mSystemServiceManager.startService(TelecomLoaderService.class);
        telecomLoaderService.onBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);

        Slog.i(TAG, "[BootProf] start TelephonyRegistry");
        final TelephonyRegistry telephonyRegistry = new TelephonyRegistry(context);
        ServiceManager.addService("telephony.registry", telephonyRegistry);

        Slog.i(TAG, "[BootProf] start AlarmManagerService");
        final AlarmManagerService alarmManager = mSystemServiceManager.startService(AlarmManagerService.class);

        Slog.i(TAG, "[BootProf] start BluetoothService");
        final BluetoothService btService = mSystemServiceManager.startService(BluetoothService.class);

        Slog.i(TAG, "[BootProf] start IpConnectivityMetrics");
        final IpConnectivityMetrics ipConnectivityMetrics = mSystemServiceManager.startService(IpConnectivityMetrics.class);

        Slog.i(TAG, "[BootProf] start AccessibilityManagerService");
        ServiceManager.addService(Context.ACCESSIBILITY_SERVICE, new AccessibilityManagerService(context));

        Slog.i(TAG, "[BootProf] start DevicePolicyManagerService");
        final SystemService devicePolicyManager = mSystemServiceManager.startService(DevicePolicyManagerService.Lifecycle.class);

        // final NetworkManagementService networkManagement;
        Slog.i(TAG, "[BootProf] start NetworkManagementService");
        NetworkManagementService networkManagement = null;
        try {
            networkManagement= NetworkManagementService.create(context);
            ServiceManager.addService(Context.NETWORKMANAGEMENT_SERVICE, networkManagement);
        } catch (Throwable e) {
            reportWtf("starting NetworkManagement Service", e);
        }

        Slog.i(TAG, "[BootProf] start NetworkScoreService");
        final NetworkScoreService networkScore = new NetworkScoreService(context);
        ServiceManager.addService(Context.NETWORK_SCORE_SERVICE, networkScore);

        Slog.i(TAG, "[BootProf] start NetworkStatsService");
        final NetworkStatsService networkStats = NetworkStatsService.create(context, networkManagement);
        ServiceManager.addService(Context.NETWORK_STATS_SERVICE, networkStats);

        Slog.i(TAG, "[BootProf] start NetworkPolicyManagerService");
        final NetworkPolicyManagerService networkPolicy = new NetworkPolicyManagerService(context,
                mActivityManagerService, networkStats, networkManagement);
        ServiceManager.addService(Context.NETWORK_POLICY_SERVICE, networkPolicy);

        Slog.i(TAG, "[BootProf] start WifiP2pService");
        final SystemService wifiP2pService = mSystemServiceManager.startService(WIFI_P2P_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf] start WifiService");
        final SystemService wifiService = mSystemServiceManager.startService(WIFI_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf] start WifiScanningService");
        final SystemService wifiScanningService = mSystemServiceManager.startService("com.android.server.wifi.scanner.WifiScanningService");

        Slog.i(TAG, "[BootProf] start ConnectivityService");
        final ConnectivityService connectivity = new ConnectivityService(
                context, networkManagement, networkStats, networkPolicy);
        ServiceManager.addService(Context.CONNECTIVITY_SERVICE, connectivity);
        networkStats.bindConnectivityManager(connectivity);
        networkPolicy.bindConnectivityManager(connectivity);

        Slog.i(TAG, "[BootProf] start RecoverySystemService");
        mSystemServiceManager.startService(RecoverySystemService.class);

        Slog.i(TAG, "[BootProf] start NotificationManagerService");
        final SystemService notificationService = mSystemServiceManager.startService(NotificationManagerService.class);
        INotificationManager notification = INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        networkPolicy.bindNotificationManager(notification);

        Slog.i(TAG, "[BootProf] start LocationManagerService");
        LocationManagerService location = new LocationManagerService(context);
        ServiceManager.addService(Context.LOCATION_SERVICE, location);

        Slog.i(TAG, "[BootProf] start CountryDetectorService");
        final CountryDetectorService countryDetector = new CountryDetectorService(context);
        ServiceManager.addService(Context.COUNTRY_DETECTOR, countryDetector);

        Slog.i(TAG, "[BootProf] start SearchManagerService");
        final SystemService searchManager = mSystemServiceManager.startService(SEARCH_MANAGER_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf] start AudioService");
        final SystemService audioService = mSystemServiceManager.startService(AudioService.Lifecycle.class);

        Slog.i(TAG, "[BootProf] start UsbService");
        final SystemService usbService = mSystemServiceManager.startService(USB_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf] start TwilightService");
        final SystemService twilightService = mSystemServiceManager.startService(TwilightService.class);

        Slog.i(TAG, "[BootProf] start AppWidgetService");
        final SystemService appWidgetService = mSystemServiceManager.startService(APPWIDGET_SERVICE_CLASS);

        Slog.i(TAG, "[BootProf] start NetworkTimeUpdateService");
        final NetworkTimeUpdateService networkTimeUpdater = new NetworkTimeUpdateService(context);
        ServiceManager.addService("network_time_update_service", networkTimeUpdater);

        Slog.i(TAG, "[BootProf] start CommonTimeManagementService");
        final CommonTimeManagementService commonTimeMgmtService = new CommonTimeManagementService(context);
        ServiceManager.addService("commontime_management", commonTimeMgmtService);

        Slog.i(TAG, "[BootProf] start RestrictionsManagerService");
        mSystemServiceManager.startService(RestrictionsManagerService.class);

        Slog.i(TAG, "[BootProf] start MediaSessionService");
        mSystemServiceManager.startService(MediaSessionService.class);

        Slog.i(TAG, "[BootProf] start MediaRouterService");
        final MediaRouterService mediaRouter = new MediaRouterService(context);
        ServiceManager.addService(Context.MEDIA_ROUTER_SERVICE, mediaRouter);

        Slog.i(TAG, "[BootProf] start ShortcutService");
        final SystemService shortcutService = mSystemServiceManager.startService(ShortcutService.Lifecycle.class);

        Slog.i(TAG, "[BootProf] start LauncherAppsService");
        mSystemServiceManager.startService(LauncherAppsService.class);

        // PHASE_LOCK_SETTINGS_READY
        Slog.d(TAG, "++++++ late PHASE_LOCK_SETTINGS_READY");
        devicePolicyManager.onBootPhase(SystemService.PHASE_LOCK_SETTINGS_READY);
        shortcutService.onBootPhase(SystemService.PHASE_LOCK_SETTINGS_READY);
        Slog.d(TAG, "++++++ End late PHASE_LOCK_SETTINGS_READY");

        // PHASE_SYSTEM_SERVICES_READY
        Slog.d(TAG, "++++++ late PHASE_SYSTEM_SERVICE_READY");
        alarmManager.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        wifiP2pService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        wifiService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        wifiScanningService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        ipConnectivityMetrics.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        notificationService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        btService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);
        Slog.d(TAG, "++++++ End late PHASE_SYSTEM_SERVICE_READY");

        // PHASE_ACTIVITY_MANAGER_READY
        Slog.d(TAG, "++++++ late PHASE_ACTIVITY_MANAGER_READY");
        btService.onBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);
        audioService.onBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);
        usbService.onBootPhase(SystemService.PHASE_ACTIVITY_MANAGER_READY);
        Slog.d(TAG, "++++++ End late PHASE_ACTIVITY_MANAGER_READY");

        // systemReady
        Slog.d(TAG, "++++++ late systemReady()");
        networkScore.systemReady();
        networkManagement.systemReady();
        networkStats.systemReady();
        networkPolicy.systemReady();
        connectivity.systemReady();
        Slog.d(TAG, "++++++ End late systemReady()");
        
        // PHASE_THIRD_PARTY_APPS_CAN_START
        Slog.d(TAG, "++++++ late PHASE_THIRD_PARTY_APPS_CAN_START");
        notificationService.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        appWidgetService.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        Slog.d(TAG, "++++++ End late PHASE_THIRD_PARTY_APPS_CAN_START");

        // systeRunning
        Slog.d(TAG, "++++++ late systemRunning()");
        location.systemRunning();
        countryDetector.systemRunning();
        networkTimeUpdater.systemRunning();
        commonTimeMgmtService.systemRunning();
        telephonyRegistry.systemRunning();
        mediaRouter.systemRunning();
        networkScore.systemRunning();
        Slog.d(TAG, "++++++ End late systemRunning()");

        // PHASE_BOOT_COMPLETED
        Slog.d(TAG, "++++++ late PHASE_BOOT_COMPLETED");
        devicePolicyManager.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        usbService.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        twilightService.onBootPhase(SystemService.PHASE_BOOT_COMPLETED);
        Slog.d(TAG, "++++++ End late PHASE_BOOT_COMPLETED");

        // onStartUser
        Slog.d(TAG, "++++++ Start onStartUser");
        devicePolicyManager.onStartUser(0);
        Slog.d(TAG, "++++++ End onStartUser");

        // onUnlockUser
        Slog.d(TAG, "++++++ Start onUnlockUser");
        btService.onUnlockUser(0);
        searchManager.onUnlockUser(0);
        appWidgetService.onUnlockUser(0);
        shortcutService.onUnlockUser(0);
        Slog.d(TAG, "++++++ End onUnlockUser");

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
