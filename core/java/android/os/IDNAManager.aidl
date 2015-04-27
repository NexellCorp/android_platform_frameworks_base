/* //device/java/android/android/os/IDNAManager.aidl
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package android.os;

/** @hide */
interface IDNAManager
{
    int WriteSplash(String filepath, String filename);
    int CleanSplash();

    byte[] Read4DCalData(int rlen, int rseek);
    void Write4DCalData(in byte[] wdata, int wlen, int wseek);

    byte[] ReadImage(int rlen, int rseek);
    byte[] ReadPark(int rlen, int rseek);
    void WriteImage(in byte[] wdata, int wlen, int wseek);
    void WritePark(in byte[] wdata, int wlen, int wseek);

    char ReadArchitecture();
    void WriteArchitecture(char version);
    char ReadProcessorType();
    void WriteProcessorType(char version);
    char ReadProcessorCompiler(); 
    void WriteProcessorCompiler(char version);
    
    String ReadosVerBuildDate();
    void WriteosVerBuildDate(String version);
    String ReadbootVerBuildDate();
    void WritebootVerBuildDate(String version);

    String ReadpcbVerModelId();
    String ReadpcbVerPlatformId();
    String ReadpcbVerPCBVersion();
    void WritepcbVerModelId(String version);
    void WritepcbVerPlatformId(String version);
    void WritepcbVerPCBVersion(String version);

    int ReadLocked();
    void WriteLocked(int mode);
    String ReadNumLocked();
    void WriteNumLocked(String version);
    String Readtime();
    void Writetime(String version);

    String ReadBootCount();
    void WriteBootCount(String version);
    String ReadAutoRecoveryScratch1();
    void WriteAutoRecoveryScratch1(String version);
    String ReadAutoRecoveryScratch2();
    void WriteAutoRecoveryScratch2(String version);
    String ReadAuthSDCheckScratch1();
    void WriteAuthSDCheckScratch1(String version);
    String ReadAuthSDCheckScratch2();
    void WriteAuthSDCheckScratch2(String version);

    String ReadUUID();
    void WriteUUID(String uuid);

    String ReadTPEG_KEY();
    void WriteTPEG_KEY(String tpeg);

    long ReadRearcamX();
    void WriteRearcamX(long value);
    long ReadRearcamY();
    void WriteRearcamY(long value);
    long ReadWheelAngle();
    void WriteWheelAngle(long value);
    long ReadCamAngle();
    void WriteCamAngle(long value);
    long ReadCamHeight();
    void WriteCamHeight(long value);
    long ReadParklineDisable();
    void WriteParklineDisable(long value);
    long ReadWheelParklineDisable();
    void WriteWheelParklineDisable(long value);
    long ReadFrontCameraOnOff();
    void WriteFrontCameraOnOff(long value);
    long ReadCameraAutoControlOnOff();
    void WriteCameraAutoControlOnOff(long value);
    long ReadRearcamDisable();
    void WriteRearcamDisable(long value);

    String ReadBlackBoxOnlyMode();
    void WriteBlackBoxOnlyMode(String version);
    int ReadDRAngle();
    void WriteDRAngle(int mode);
    String ReadCalibrationCheck();
    void WriteCalibrationCheck(String version);
    String ReadTvoutEnable();
    void WriteTvoutEnable(String version);
    String ReadDrFault();
    void WriteDrFault(String version);

    int ReadMode();
    void WriteMode(int mode);
    int ReadCurrent();
    void WriteCurrent(int current);
    int ReadPercentage();
    void WritePercentage(int percentage);
    int ReadBootCounts();
    void WriteBootCounts(int bootcount);
    int ReadOSCurrent();
    void WriteOSCurrent(int current);
    String ReadUpdate();
    void WriteUpdate(String Update);
    String ReadVersion();
    void WriteVersion(String version);
    String ReadRecovery();
    void WriteRecovery(String recovery);
    int ReadModeUPDATEUSB();
    void WriteModeUPDATEUSB(int mode);
    int ReadUpdateCase();
    void WriteUpdateCase(int current);

    int ReadWaitProgress();
    void WriteWaitProgress(int WaitProgress);
    void WriteTimes(int WaitTime1, int WaitTime2, int WaitTime3, int WaitTime4, int WaitTime5);
    int ReadTime1();
    void WriteTime1(int WaitTime);
    int ReadTime2();
    void WriteTime2(int WaitTime);
    int ReadTime3();
    void WriteTime3(int WaitTime);
    int ReadTime4();
    void WriteTime4(int WaitTime);
    int ReadTime5();
    void WriteTime5(int WaitTime);

    void WriteUSTS(int usts);
    void AudioShutdown();
    int ReadCurrPercentage();
    void SETIMG(int value);
    int WatchDogDisable();

    int FDTHistoryRead();    
    void FDTHistoryWrite(int value);

    int Chmod(String path, String perm, String opt);
    int Chown(String path, String user, String group);
    int Printf(String messages);

    byte[] DES64_Encode(String tid);
    String DES64_Decode(in byte[] encodestream);
}
