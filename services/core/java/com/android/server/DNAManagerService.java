/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;

import android.os.IDNAManager;
import android.os.SystemProperties;
import android.os.SystemClock;

import android.util.EventLog;
import android.util.Log;

class DNAManagerService extends IDNAManager.Stub {
//Eroum:hh.shin:140926 - set UUID default value
    static final String DefaultUUID = "810000000001 ";
//Eroum:hh.shin:140926 - set UUID default value

    private static final String TAG = "DNAManagerService";

    private native int nativeWriteSplash(String filepath, String filename);
    private native int nativeCleanSplash();

    private static final int WRITE_COMPLETE = 0;
    private static final int WRITE_YET = 1;
    private static final int WRITE_RUNNING = 2;
    private static final int WRITE_FAILED_NOT_READY = 10;
    private static final int WRITE_FAILED_FILENOTFOUND = 11;
    private static final int WRITE_FAILED_OPENFAIL_DEV = 12;
    private static final int WRITE_FAILED_OPENFAIL_FILE = 13;
    private static final int WRITE_FAILED_WRITEFAIL_FILE = 14;

    private static final int SPLASH_IMG_NONE = 0;
    private static final int SPLASH_IMG_READY = 1;
    private static final int SPLASH_IMG_RUNNING = 2;
    private static final int SPLASH_IMG_OK = 3;
    private static final int SPLASH_IMG_ERR_NOT_READY = 4;
    private static final int SPLASH_IMG_ERR_FILENOTFOUNT_SPLASH_FILE = 5;
    private static final int SPLASH_IMG_ERR_OPENFAIL_SPLASH_DEV = 6;
    private static final int SPLASH_IMG_ERR_OPENFAIL_SPLASH_FILE = 7;
    private static final int SPLASH_IMG_ERR_WRITEFAIL_SPLASH_FILE = 8;

    private int ret_Write() {
        int retValue = WRITE_YET;
        int results = SystemProperties.getInt("sys.bootsplash", 0);

        Log.e(TAG, "ret_Write=" + results);

        switch (results) {
            case SPLASH_IMG_OK:
                retValue = WRITE_COMPLETE;
                break;
            case SPLASH_IMG_RUNNING:
                SystemClock.sleep(500);
                Log.e(TAG, "RUNNING");
                return ret_Write();
            case SPLASH_IMG_ERR_NOT_READY:
                retValue = WRITE_FAILED_NOT_READY;
                break;
            case SPLASH_IMG_ERR_FILENOTFOUNT_SPLASH_FILE:
                retValue = WRITE_FAILED_FILENOTFOUND;
                break;
            case SPLASH_IMG_ERR_OPENFAIL_SPLASH_DEV:
                retValue = WRITE_FAILED_OPENFAIL_DEV;
                break;
            case SPLASH_IMG_ERR_OPENFAIL_SPLASH_FILE:
                retValue = WRITE_FAILED_OPENFAIL_FILE;
                break;
            case SPLASH_IMG_ERR_WRITEFAIL_SPLASH_FILE:
                retValue = WRITE_FAILED_WRITEFAIL_FILE;
                break;
            case SPLASH_IMG_NONE:
            case SPLASH_IMG_READY:
            default:
                retValue = WRITE_YET;
                break;
        }
    
        return retValue;
    }
    
    public int WriteSplash(String filepath, String filename) {
        int retValue = WRITE_YET;

        nativeWriteSplash(filepath, filename);
        SystemClock.sleep(1000);        
        retValue = ret_Write();
        Log.e(TAG, "WriteSplash=" + retValue);
        
        return retValue;
    }

    public int CleanSplash() {
        int retValue = WRITE_YET;

        nativeCleanSplash();
        SystemClock.sleep(500);
        retValue = ret_Write();
        Log.e(TAG, "CleanSplash=" + retValue);
        
        return retValue;
    }

    private native byte[] nativeRead4DCalData(int rlen, int rseek);
    private native void nativeWrite4DCalData(byte[] wdata, int wlen, int wseek);

    public byte[] Read4DCalData(int rlen, int rseek) {
        return nativeRead4DCalData(rlen, rseek);
    }

    public void Write4DCalData(byte[] wdata, int wlen, int wseek) {
        nativeWrite4DCalData(wdata, wlen, wseek);
    }

    private native byte[] nativeReadImage(int rlen, int rseek);
    private native byte[] nativeReadPark(int rlen, int rseek);
    private native void nativeWriteImage(byte[] wdata, int wlen, int wseek);
    private native void nativeWritePark(byte[] wdata, int wlen, int wseek);

    public byte[] ReadImage(int rlen, int rseek) {
        return nativeReadImage(rlen, rseek);
    }

    public byte[] ReadPark(int rlen, int rseek) {
        return nativeReadPark(rlen, rseek);
    }

    public void WriteImage(byte[] wdata, int wlen, int wseek) {
        nativeWriteImage(wdata, wlen, wseek);
    }

    public void WritePark(byte[] wdata, int wlen, int wseek) {
        nativeWritePark(wdata, wlen, wseek);
    }

    private native char nativeReadArchitecture();
    private native void nativeWriteArchitecture(char version);
    private native char nativeReadProcessorType();
    private native void nativeWriteProcessorType(char version);
    private native char nativeReadProcessorCompiler();
    private native void nativeWriteProcessorCompiler(char version);

    public char ReadArchitecture() {
        return nativeReadArchitecture();
    }

    public void WriteArchitecture(char version) {
        nativeWriteArchitecture(version);
    }

    public char ReadProcessorType() {
        return nativeReadProcessorType();
    }

    public void WriteProcessorType(char version) {
        nativeWriteProcessorType(version);
    }

    public char ReadProcessorCompiler() {
        return nativeReadProcessorCompiler();
    }

    public void WriteProcessorCompiler(char version) {
        nativeWriteProcessorCompiler(version);
    }


    private native String nativeReadosVerBuildDate();
    private native void nativeWriteosVerBuildDate(String version);
    private native String nativeReadbootVerBuildDate();
    private native void nativeWritebootVerBuildDate(String version);

    private native String nativeReadpcbVerModelId();
    private native String nativeReadpcbVerPlatformId();
    private native String nativeReadpcbVerPCBVersion();
    private native void nativeWritepcbVerModelId(String version);
    private native void nativeWritepcbVerPlatformId(String version);
    private native void nativeWritepcbVerPCBVersion(String version);

    public String ReadosVerBuildDate() {
        return nativeReadosVerBuildDate();
    }

    public void WriteosVerBuildDate(String version) {
        nativeWriteosVerBuildDate(version);
    }

    public String ReadbootVerBuildDate() {
        return nativeReadbootVerBuildDate();
    }

    public void WritebootVerBuildDate(String version) {
        nativeWritebootVerBuildDate(version);
    }

    public String ReadpcbVerModelId() {
        return nativeReadpcbVerModelId();
    }

    public String ReadpcbVerPlatformId() {
        return nativeReadpcbVerPlatformId();
    }

    public String ReadpcbVerPCBVersion() {
        return nativeReadpcbVerPCBVersion();
    }

    public void WritepcbVerModelId(String version) {
        nativeWritepcbVerModelId(version);
    }

    public void WritepcbVerPlatformId(String version) {
        nativeWritepcbVerPlatformId(version);
    }

    public void WritepcbVerPCBVersion(String version) {
        nativeWritepcbVerPCBVersion(version);
    }

    private native int nativeReadLocked();
    private native void nativeWriteLocked(int mode);
    private native String nativeReadNumLocked();
    private native void nativeWriteNumLocked(String version);
    private native String nativeReadtime();
    private native void nativeWritetime(String version);

    public int ReadLocked() {
        return nativeReadLocked();
    }
    
    public void WriteLocked(int mode) {
        nativeWriteLocked(mode);
    }

    public String ReadNumLocked() {
        return nativeReadNumLocked();
    }
    
    public void WriteNumLocked(String version) {
        nativeWriteNumLocked(version);
    }

    public String Readtime() {
        return nativeReadtime();
    }
    
    public void Writetime(String version) {
        nativeWritetime(version);
    }

    private native String nativeReadBootCount();
    private native void nativeWriteBootCount(String version);
    private native String nativeReadAutoRecoveryScratch1();
    private native void nativeWriteAutoRecoveryScratch1(String version);
    private native String nativeReadAutoRecoveryScratch2();
    private native void nativeWriteAutoRecoveryScratch2(String version);
    private native String nativeReadAuthSDCheckScratch1();
    private native void nativeWriteAuthSDCheckScratch1(String version);
    private native String nativeReadAuthSDCheckScratch2();
    private native void nativeWriteAuthSDCheckScratch2(String version);

    public String ReadBootCount() {
        return nativeReadBootCount();
    }
    
    public void WriteBootCount(String version) {
        nativeWriteBootCount(version);
    }

    public String ReadAutoRecoveryScratch1() {
        return nativeReadAutoRecoveryScratch1();
    }
    
    public void WriteAutoRecoveryScratch1(String version) {
        nativeWriteAutoRecoveryScratch1(version);
    }

    public String ReadAutoRecoveryScratch2() {
        return nativeReadAutoRecoveryScratch2();
    }
    
    public void WriteAutoRecoveryScratch2(String version) {
        nativeWriteAutoRecoveryScratch2(version);
    }

    public String ReadAuthSDCheckScratch1() {
        return nativeReadAuthSDCheckScratch1();
    }
    
    public void WriteAuthSDCheckScratch1(String version) {
        nativeWriteAuthSDCheckScratch1(version);
    }

    public String ReadAuthSDCheckScratch2() {
        return nativeReadAuthSDCheckScratch2();
    }
    
    public void WriteAuthSDCheckScratch2(String version) {
        nativeWriteAuthSDCheckScratch2(version);
    }

    private native String nativeReadUUID();
    private native void nativeWriteUUID(String uuid);

    public String ReadUUID() {
        String uuid = nativeReadUUID();

//Eroum:hh.shin:140926 - set UUID default value
        if(uuid == null) {
            Log.e(TAG,"uuid is null");
            uuid = DefaultUUID;
        } else if(uuid.isEmpty()) {
            Log.e(TAG,"uuid is Empty");
            uuid = DefaultUUID;
        }
//Eroum:hh.shin:140926 - set UUID default value

        return uuid;
    }

    public void WriteUUID(String uuid) {
        nativeWriteUUID(uuid);
    }

    private native String nativeReadTPEG_KEY();
    private native void nativeWriteTPEG_KEY(String tpeg);

    public String ReadTPEG_KEY() {
        return nativeReadTPEG_KEY();
    }

    public void WriteTPEG_KEY(String tpeg) {
        nativeWriteTPEG_KEY(tpeg);
    }

    private native long nativeReadRearcamX();
    private native void nativeWriteRearcamX(long value);
    private native long nativeReadRearcamY();
    private native void nativeWriteRearcamY(long value);
    private native long nativeReadWheelAngle();
    private native void nativeWriteWheelAngle(long value);
    private native long nativeReadCamAngle();
    private native void nativeWriteCamAngle(long value);
    private native long nativeReadCamHeight();
    private native void nativeWriteCamHeight(long value);
    private native long nativeReadParklineDisable();
    private native void nativeWriteParklineDisable(long value);
    private native long nativeReadWheelParklineDisable();
    private native void nativeWriteWheelParklineDisable(long value);
    private native long nativeReadFrontCameraOnOff();
    private native void nativeWriteFrontCameraOnOff(long value);
    private native long nativeReadCameraAutoControlOnOff();
    private native void nativeWriteCameraAutoControlOnOff(long value);
    private native long nativeReadRearcamDisable();
    private native void nativeWriteRearcamDisable(long value);
    
    public long ReadRearcamX() {
        return nativeReadRearcamX();
    }
     
    public void WriteRearcamX(long value) {
        nativeWriteRearcamX(value);
    }
    
    public long ReadRearcamY() {
        return nativeReadRearcamY();
    }
     
    public void WriteRearcamY(long value) {
        nativeWriteRearcamY(value);
    }
    
    public long ReadWheelAngle() {
        return nativeReadWheelAngle();
    }
     
    public void WriteWheelAngle(long value) {
        nativeWriteWheelAngle(value);
    }
    
    public long ReadCamAngle() {
        return nativeReadCamAngle();
    }
     
    public void WriteCamAngle(long value) {
        nativeWriteCamAngle(value);
    }
    
    public long ReadCamHeight() {
        return nativeReadCamHeight();
    }
     
    public void WriteCamHeight(long value) {
        nativeWriteCamHeight(value);
    }
    
    public long ReadParklineDisable() {
        return nativeReadParklineDisable();
    }
     
    public void WriteParklineDisable(long value) {
        nativeWriteParklineDisable(value);
    }
    
    public long ReadWheelParklineDisable() {
        return nativeReadWheelParklineDisable();
    }
     
    public void WriteWheelParklineDisable(long value) {
        nativeWriteWheelParklineDisable(value);
    }
    
    public long ReadFrontCameraOnOff() {
        return nativeReadFrontCameraOnOff();
    }
     
    public void WriteFrontCameraOnOff(long value) {
        nativeWriteFrontCameraOnOff(value);
    }
    
    public long ReadCameraAutoControlOnOff() {
        return nativeReadCameraAutoControlOnOff();
    }
     
    public void WriteCameraAutoControlOnOff(long value) {
        nativeWriteCameraAutoControlOnOff(value);
    }
    
    public long ReadRearcamDisable() {
        return nativeReadRearcamDisable();
    }
     
    public void WriteRearcamDisable(long value) {
        nativeWriteRearcamDisable(value);
    }

    private native String nativeReadBlackBoxOnlyMode();
    private native void nativeWriteBlackBoxOnlyMode(String version);
    private native int nativeReadDRAngle();
    private native void nativeWriteDRAngle(int mode);
    private native String nativeReadCalibrationCheck();
    private native void nativeWriteCalibrationCheck(String version);
    private native String nativeReadTvoutEnable();
    private native void nativeWriteTvoutEnable(String version);
    private native String nativeReadDrFault();
    private native void nativeWriteDrFault(String version);

    public String ReadBlackBoxOnlyMode() {
        return nativeReadBlackBoxOnlyMode();
    }
     
    public void WriteBlackBoxOnlyMode(String version) {
        nativeWriteBlackBoxOnlyMode(version);
    }

    public int ReadDRAngle() {
        return nativeReadDRAngle();
    }
    
    public void WriteDRAngle(int mode) {
        nativeWriteDRAngle(mode);
    }

    public String ReadCalibrationCheck() {
        return nativeReadCalibrationCheck();
    }
     
    public void WriteCalibrationCheck(String version) {
        nativeWriteCalibrationCheck(version);
    }

    public String ReadTvoutEnable() {
        return nativeReadTvoutEnable();
    }
     
    public void WriteTvoutEnable(String version) {
        nativeWriteTvoutEnable(version);
    }

    public String ReadDrFault() {
        return nativeReadDrFault();
    }
     
    public void WriteDrFault(String version) {
        nativeWriteDrFault(version);
    }

    private native int nativeReadMode();
    private native void nativeWriteMode(int mode);
    private native int nativeReadCurrent();
    private native void nativeWriteCurrent(int current);
    private native int nativeReadPercentage();
    private native void nativeWritePercentage(int percentage);
    private native int nativeReadBootCounts();
    private native void nativeWriteBootCounts(int bootcount);
    private native int nativeReadOSCurrent();
    private native void nativeWriteOSCurrent(int current);
    private native String nativeReadUpdate();
    private native void nativeWriteUpdate(String update);
    private native String nativeReadVersion();
    private native void nativeWriteVersion(String version);
    private native String nativeReadRecovery();
    private native void nativeWriteRecovery(String recovery);
    private native int nativeReadModeUPDATEUSB();
    private native void nativeWriteModeUPDATEUSB(int mode);
    private native int nativeReadUpdateCase();
    private native void nativeWriteUpdateCase(int UpdateCase);

    public int ReadMode() {
        return nativeReadMode();
    }
     
    public void WriteMode(int mode) {
        nativeWriteMode(mode);
    }

    public int ReadCurrent() {
        return nativeReadCurrent();
    }
     
    public void WriteCurrent(int current) {
        nativeWriteCurrent(current);
    }

    public int ReadPercentage() {
        return nativeReadPercentage();
    }
     
    public void WritePercentage(int percentage) {
        nativeWritePercentage(percentage);
    }

    public int ReadBootCounts() {
        return nativeReadBootCounts();
    }
     
    public void WriteBootCounts(int bootcount) {
        nativeWriteBootCounts(bootcount);
    }

    public int ReadOSCurrent() {
        return nativeReadOSCurrent();
    }
     
    public void WriteOSCurrent(int current) {
        nativeWriteOSCurrent(current);
    }

    public String ReadUpdate() {
        return nativeReadUpdate();
    }

    public void WriteUpdate(String update) {
        nativeWriteUpdate(update);
    }

    public String ReadVersion() {
        return nativeReadVersion();
    }

    public void WriteVersion(String version) {
        nativeWriteVersion(version);
    }

    public String ReadRecovery() {
        return nativeReadRecovery();
    }

    public void WriteRecovery(String recovery) {
        nativeWriteRecovery(recovery);
    }

    public int ReadModeUPDATEUSB() {
        return nativeReadModeUPDATEUSB();
    }
     
    public void WriteModeUPDATEUSB(int mode) {
        nativeWriteModeUPDATEUSB(mode);
    }

    public int ReadUpdateCase() {
        return nativeReadUpdateCase();
    }
     
    public void WriteUpdateCase(int UpdateCase) {
        nativeWriteUpdateCase(UpdateCase);
    }
    
    private native int nativeReadWaitProgress();
    private native void nativeWriteWaitProgress(int WaitProgress);
    private native void nativeWriteTimes(int WaitWaitTime1, int WaitWaitTime2, int WaitWaitTime3, int WaitWaitTime4, int WaitWaitTime5);
    private native int nativeReadTime1();
    private native void nativeWriteTime1(int WaitWaitTime);
    private native int nativeReadTime2();
    private native void nativeWriteTime2(int WaitWaitTime);
    private native int nativeReadTime3();
    private native void nativeWriteTime3(int WaitWaitTime);
    private native int nativeReadTime4();
    private native void nativeWriteTime4(int WaitWaitTime);
    private native int nativeReadTime5();
    private native void nativeWriteTime5(int WaitWaitTime);

	private native boolean _DES64_Encode(byte[] tid,byte[] encodestream);
	private native boolean _DES64_Decode(byte[] encodestream,byte[] tid);
    

	public byte[] DES64_Encode(String tid)
	{
		byte[] btid = new byte[128];
		byte[] bencodedata = new byte[128];
		byte[] tempbyte = tid.getBytes();

		for(int i=0;i<tempbyte.length;i++) {
			btid[i] = tempbyte[i];
//			Log.e(TAG,""+i+" : "+btid[i]);
		}

		if(!_DES64_Encode(btid,bencodedata)) {
			Log.e(TAG,"DES64_Encode failed");
			return null;
		}
		return bencodedata;
	}

	public String DES64_Decode(byte[] encodestream)
	{
		int i;
		byte[] btid = new byte[128];
		int inputsize = encodestream.length;
		
		if(inputsize != 128) {
			Log.e(TAG,"encodestream size is not 128 bytes");
			return null;
		}
		
		if(!_DES64_Decode(encodestream,btid)) {
			Log.e(TAG,"DES64_Decode failed");
			return null;
		}

		for(i=0;i<128;i++) {
			if(btid[i] < 0x30 || btid[i] > 0x39) {
				btid[i] = 0x0;
				break;
			}
		}
		String ret = new String(btid,0,i);
		
		return ret;
	}

	

    public int ReadWaitProgress() {
        return nativeReadWaitProgress();
    }
     
    public void WriteWaitProgress(int WaitProgress) {
        nativeWriteWaitProgress(WaitProgress);
    }

    public void WriteTimes(int WaitTime1, int WaitTime2, int WaitTime3, int WaitTime4, int WaitTime5) {
        nativeWriteTimes(WaitTime1, WaitTime2, WaitTime3, WaitTime4, WaitTime5);
    }

    public int ReadTime1() {
        return nativeReadTime1();
    }
     
    public void WriteTime1(int WaitTime) {
        nativeWriteTime1(WaitTime);
    }

    public int ReadTime2() {
        return nativeReadTime2();
    }
     
    public void WriteTime2(int WaitTime) {
        nativeWriteTime2(WaitTime);
    }

    public int ReadTime3() {
        return nativeReadTime3();
    }
     
    public void WriteTime3(int WaitTime) {
        nativeWriteTime3(WaitTime);
    }

    public int ReadTime4() {
        return nativeReadTime4();
    }
     
    public void WriteTime4(int WaitTime) {
        nativeWriteTime4(WaitTime);
    }

    public int ReadTime5() {
        return nativeReadTime5();
    }
     
    public void WriteTime5(int WaitTime) {
        nativeWriteTime5(WaitTime);
    }

    private native void nativeWriteUSTS(int usts);
    private native void nativeAudioShutdown();
    private native int nativeReadCurrPercentage();
    private native void nativeSETIMG(int value);
    private native int nativeWatchDogDisable();

    public void WriteUSTS(int usts) {
        nativeWriteUSTS(usts);
    }

    public void AudioShutdown() {
        nativeAudioShutdown();
    }

    public int ReadCurrPercentage() {
        return nativeReadCurrPercentage();
    }

    public void SETIMG(int value) {
        nativeSETIMG(value);
    }

    public int WatchDogDisable() {
        return nativeWatchDogDisable();
    }

    private native int nativeFDTHistoryRead();
    private native void nativeFDTHistoryWrite(int value);

    public int FDTHistoryRead(){
        return nativeFDTHistoryRead();
    }
    
    public void FDTHistoryWrite(int value) {
        nativeFDTHistoryWrite(value);
    }

    private native int nativeChmod(String path, String perm, String opt);
    private native int nativeChown(String path, String user, String group);
    private native int nativePrintf(String messages);

    public int Chmod(String path, String perm, String opt) {
        return nativeChmod(path, perm, opt);
    }

    public int Chown(String path, String user, String group) {
        return nativeChown(path, user, group);
    }

    public int Printf(String messages) {
        return nativePrintf(messages);
    }

    public DNAManagerService(Context context) {
        
    }
}
