package com.android.server;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import android.os.IUSBPowerControl;


/**
 * UsbService manages all USB related state, including both host and device support.
 * Host related events and calls are delegated to UsbHostManager, and device related
 * support is delegated to UsbDeviceManager.
 */
public class USBPowerControlService extends IUSBPowerControl.Stub {
    private static final String TAG = "USBPowerControlService";
    private final Context mContext;
    private static USBDetectThread mUSBDetectThread = null;


	private native int _USBPowerSet(int onoff);
	private native int _USBPowerGet();

	private native int _USBHostEnableSet(int enable);
	private native int _USBHostEnableGet();

	private native int _USBXHCIHostEnableSet(int enable);

    // psw0523 add to monitor usb port change
    private native int _getUSBStatusChangedEvent(int[] info);

    public USBPowerControlService(Context context) {
        mContext = context;

        if (mUSBDetectThread == null) {
             Log.d(TAG, "USBDetectThread start");
             mUSBDetectThread = new USBDetectThread();
             mUSBDetectThread.start();
        }
    }

	public int USBPowerControlSet(int onoff)
    {
    	return _USBPowerSet(onoff);
    }

	public int USBPowerControlGet()
    {
    	return _USBPowerGet();
    }

    public int USBHostEnableSet(int enable)
    {
    	return _USBHostEnableSet(enable);
    }

    public int USBHostEnableGet()
    {
    	return _USBHostEnableGet();
    }

    public int USBXHCIHostEnableSet(int enable)
    {
    	return _USBXHCIHostEnableSet(enable);
    }

    private void sendBroadCast(String intentName)
    {
         Intent i = new Intent(intentName);
         mContext.sendBroadcast(i);
    }

    private class USBDetectThread extends Thread {

        public void run() {
            int[] usb_info = new int[2];
            int ret;

            Log.d(TAG, "USBDetectThread started");
            while (true) {
                ret = _getUSBStatusChangedEvent(usb_info);
                if (ret == 0) {
                    switch (usb_info[0]) {
                    case 0: // WiFi
                        if (usb_info[1] == 1) {
                            Log.d(TAG, "WiFi dongle attached");
                            sendBroadCast(Intent.ACTION_WIFI_DONGLE_ATTACHED);
                        } else {
                            Log.d(TAG, "WiFi dongle detached");
                            sendBroadCast(Intent.ACTION_WIFI_DONGLE_DETACHED);
                        }
                        break;
                    case 1: // Bluetooth
                        if (usb_info[1] == 1) {
                            Log.d(TAG, "Bluetooth dongle attached");
                            sendBroadCast(Intent.ACTION_BT_DONGLE_ATTACHED);
                        } else {
                            Log.d(TAG, "Bluetooth dongle detached");
                            sendBroadCast(Intent.ACTION_BT_DONGLE_DETACHED);
                        }
                        break;
                    }
                }
            }
        }
    }
}
