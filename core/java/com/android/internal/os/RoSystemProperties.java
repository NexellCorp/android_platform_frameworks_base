/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.os;

import android.os.SystemProperties;

/**
 * This is a cache of various ro.* properties so that they can be read just once
 * at class init time.
 */
public class RoSystemProperties {
    public static final boolean DEBUGGABLE =
            SystemProperties.getInt("ro.debuggable", 0) == 1;
    public static final int FACTORYTEST =
            SystemProperties.getInt("ro.factorytest", 0);
    public static final String CONTROL_PRIVAPP_PERMISSIONS =
            SystemProperties.get("ro.control_privapp_permissions");
    public static final boolean QUICKBOOT =
            SystemProperties.getInt("ro.quickboot", 0) == 1;
    public static final boolean POWER_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.power_manager", 0) == 1;
    public static final boolean KERNEL_CPU_SPEED_READER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.kernel_cpu_speed_reader", 0) == 1;
    public static final boolean KERNEL_UID_CPU_FREQ_TIME_READER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.kernel_uid_cpu_freq_time_reader", 0) == 1;
    public static final boolean KERNEL_WAKE_LOCK_READER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.kernel_wake_lock_reader", 0) == 1;
    public static final boolean AUDIO_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.audio_manager", 0) == 1;
    public static final boolean KEYGUARD_DISPLAY_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.keyguard_display_manager", 0) == 1;
    public static final boolean KEYGUARD_UPDATE_MONITOR_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.keyguard_update_monitor", 0) == 1;
    public static final boolean KEYGUARD_SLICE_PROVIDER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.keyguard_slice_provider", 0) == 1;
    public static final boolean KEYGUARD_VIEW_MEDIATOR_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.keyguard_view_mediator", 0) == 1;
    public static final boolean CELLULAR_TILE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.cellular_tile", 0) == 1;
    public static final boolean SYSTEM_SERVICES_PROXY_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.system_services_proxy", 0) == 1;
    public static final boolean NOTIFICATION_MEDIA_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.notification_media_manager", 0) == 1;
    public static final boolean AUTO_TILE_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.auto_tile_manager", 0) == 1;
    public static final boolean PHONE_STATUS_BAR_POLICY_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.phone_status_bar_policy", 0) == 1;
    public static final boolean STATUS_BAR_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.status_bar", 0) == 1;
    public static final boolean ACCESS_POINT_CONTROLLER_IMPL_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.access_point_controller_impl", 0) == 1;
    public static final boolean NETWORK_CONTROLLER_IMPL_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.network_controller_impl", 0) == 1;
    public static final boolean WIFI_SIGNAL_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.wifi_signal_controller", 0) == 1;
    public static final boolean NOTIFICATION_CHANNELS_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.notification_channels", 0) == 1;
    public static final boolean ACTIVITY_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.activity_manager_service", 0) == 1;
    public static final boolean DEVICE_IDLE_CONTROLLER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.device_idle_controller", 0) == 1;
    public static final boolean INPUT_METHOD_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.input_method_manager_service", 0) == 1;
    public static final boolean JOB_SCHEDULER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.job_scheduler_service", 0) == 1;
    public static final boolean LOCK_SETTINGS_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.lock_settings_service", 0) == 1;
    public static final boolean LOCK_SETTINGS_STORAGE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.lock_settings_storage", 0) == 1;
    public static final boolean NOTIFICATION_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.notification_manager_service", 0) == 1;
    public static final boolean PHONE_WINDOW_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.phone_window_manager", 0) == 1;
    public static final boolean POWER_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.power_manager_service", 0) == 1;
    public static final boolean STORAGE_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.storage_manager_service", 0) == 1;
    public static final boolean SYSTEM_SERVICE_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.system_service_manager", 0) == 1;
    public static final boolean WINDOW_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.window_manager_service", 0) == 1;
    public static final boolean DEVICE_POLICY_MANAGER_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.device_policy_manager_service", 0) == 1;
    public static final boolean SYSTEM_SERVER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.system_server", 0) == 1;
    public static final boolean USAGE_STATS_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.usage_stats_service", 0) == 1;
    public static final boolean USB_SERVICE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.usb_service", 0) == 1;
    public static final boolean TELEPHONY_MANAGER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.telephony_manager", 0) == 1;
    public static final boolean INBOUND_SMS_HANDLER_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.inbound_sms_handler", 0) == 1;
    public static final boolean RADIO_CONFIG_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.radio_config", 0) == 1;
    public static final boolean RIL_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.ril", 0) == 1;
    public static final boolean WAKE_LOCK_STATE_MACHINE_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.wake_lock_state_machine", 0) == 1;
    public static final boolean WIFI_THREAD_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.wifi_thread", 0) == 1;
    public static final boolean BLUETOOTH_DELAY_QUICKBOOT =
            SystemProperties.getInt("ro.quickboot.bluetooth_delay", 0) == 1;

    // ------ ro.config.* -------- //
    public static final boolean CONFIG_AVOID_GFX_ACCEL =
            SystemProperties.getBoolean("ro.config.avoid_gfx_accel", false);
    public static final boolean CONFIG_LOW_RAM =
            SystemProperties.getBoolean("ro.config.low_ram", false);
    public static final boolean CONFIG_SMALL_BATTERY =
            SystemProperties.getBoolean("ro.config.small_battery", false);

    // ------ ro.fw.* ------------ //
    public static final boolean FW_SYSTEM_USER_SPLIT =
            SystemProperties.getBoolean("ro.fw.system_user_split", false);

    // ------ ro.crypto.* -------- //
    public static final String CRYPTO_STATE = SystemProperties.get("ro.crypto.state");
    public static final String CRYPTO_TYPE = SystemProperties.get("ro.crypto.type");
    // These are pseudo-properties
    public static final boolean CRYPTO_ENCRYPTABLE =
            !CRYPTO_STATE.isEmpty() && !"unsupported".equals(CRYPTO_STATE);
    public static final boolean CRYPTO_ENCRYPTED =
            "encrypted".equalsIgnoreCase(CRYPTO_STATE);
    public static final boolean CRYPTO_FILE_ENCRYPTED =
            "file".equalsIgnoreCase(CRYPTO_TYPE);
    public static final boolean CRYPTO_BLOCK_ENCRYPTED =
            "block".equalsIgnoreCase(CRYPTO_TYPE);

    public static final boolean CONTROL_PRIVAPP_PERMISSIONS_LOG =
            "log".equalsIgnoreCase(CONTROL_PRIVAPP_PERMISSIONS);
    public static final boolean CONTROL_PRIVAPP_PERMISSIONS_ENFORCE =
            "enforce".equalsIgnoreCase(CONTROL_PRIVAPP_PERMISSIONS);
    public static final boolean CONTROL_PRIVAPP_PERMISSIONS_DISABLE =
            !CONTROL_PRIVAPP_PERMISSIONS_LOG && !CONTROL_PRIVAPP_PERMISSIONS_ENFORCE;

}
