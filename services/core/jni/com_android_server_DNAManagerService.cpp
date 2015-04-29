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
#define LOG_TAG "DNA lib"

#include <assert.h>
#include <errno.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/resource.h>

#include <linux/unistd.h>

#include <utils/Log.h>
#include <utils/misc.h>

#include <cutils/properties.h>

#include "jni.h"
#include "JNIHelp.h"

#include "dev_dna_info.h"

//Eroum:hh.shin:141208 - TID encode
#include "des64.h"
//Eroum:hh.shin:141208 - TID encode

#include <unistd.h>

namespace android {

//----------------------------------------------------------------------------------------

#define MERCHAND_IMAGE_ADDR (ONE_KBYTES_SIZE * NAND_PAGE_SIZE * 150)
#define BUFSIZ 1024
char buf[BUFSIZ];

#define SPLASH_IMG_OK 0
#define SPLASH_IMG_ERR_FILENOTFOUND_SPLASH_FILE 1
#define SPLASH_IMG_ERR_OPENFAIL_SPLASH_DEV 2
#define SPLASH_IMG_ERR_OPENFAIL_SPLASH_FILE 3
#define SPLASH_IMG_ERR_WRITEFAIL_SPLASH_FILE 4
#define SPLASH_IMG_ERR_PID 5
#define SPLASH_IMG_ERR_UNKNOWN 6

#define MCUCPU_DRV "/dev/smarta_mcucpu_drv"

#define IOCTL_MCU_UPDATE_DRAW           0x12
#define IOCTL_MCU_SET_USSTATUS          0x13
#define IOCTL_MCU_AUDIO_SHUTDOWN        0x16
#define IOCTL_MCU_CURR_PERCENTAGE       0x17
#define IOCTL_MCU_EXCUTE_SHELL          0x21
#define IOCTL_MCU_SPRINTF               0x23
#define IOCTL_MCU_ALIVE_DISABLE         0x120

int IQ_WATCHDOGDISABLE()
{
#if 0
    int osd_fd;
    int ret;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_ALIVE_DISABLE, NULL);
    }

    if (osd_fd) close(osd_fd);

    return ret;
#else
    return 0;
#endif
}

int IQ_USSTATUS(int usts){
#if 0
    int osd_fd;
    int ret;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_SET_USSTATUS, usts);
    }

    if (osd_fd) close(osd_fd);

    return ret;
#else
    return 0;
#endif
}

int IQ_AUDIO_SHUTDOWN(){
#if 0
    int osd_fd;
    int ret;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_AUDIO_SHUTDOWN, 0);
    }

    if (osd_fd) close(osd_fd);

    return ret;
#else
    return 0;
#endif
}

int IQ_SET_IMG(int value){
#if 0
    int osd_fd;
    int ret;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_UPDATE_DRAW, value);
    }

    if (osd_fd) close(osd_fd);

    return ret;
#else
    return 0;
#endif
}

int IQ_GET_PERCENTAGE()
{
#if 0
    int osd_fd;
    int ret;
    int currpercentage = 0;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_CURR_PERCENTAGE, &currpercentage);
    }

    dgbprintf(stderr, "\033[31m\033[1mcurrpercentage\[%d]\033[0m\r\n", currpercentage);

    if (osd_fd) close(osd_fd);

    return currpercentage;
#else
    return 0;
#endif
}

int IQ_SHELL_EXCUTE(const char *command){
#if 0
    int osd_fd;
    int ret;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_EXCUTE_SHELL, command);
    }

    if (osd_fd) close(osd_fd);

    return ret;
#else
    return 0;
#endif
}

int IQ_PRINTF(const char *messages){
#if 0
    int osd_fd;
    int ret;

    osd_fd = open(MCUCPU_DRV, O_RDWR, 777);

    if (osd_fd < 0) {

    } else {
        ret = ioctl(osd_fd, IOCTL_MCU_SPRINTF, messages);
    }

    if (osd_fd) close(osd_fd);

    return ret;
#else
    return 0;
#endif
}

static jboolean android_server_DNAManagerService_DES64_Encode(JNIEnv* env, jobject clazz, jbyteArray tid,jbyteArray encodestream)
{
	int tidsize = env->GetArrayLength(tid);
	int encodestreamsize = env->GetArrayLength(encodestream);

	if(tidsize != encodestreamsize) {
		ALOGE("TID, ENCODESTREAM size is mismatch(TID : %d, ENCODESTREAM : %d)",tidsize,encodestreamsize);
		return false;
	}

	jbyte* btid = env->GetByteArrayElements(tid,0);
	unsigned char *originalkey = (unsigned char *)malloc(tidsize);
	unsigned char *encodedata = (unsigned char *)malloc(tidsize);

	memset(originalkey,0x0,tidsize);
	memset(encodedata,0x0,tidsize);
	memcpy(originalkey,btid,tidsize);

	if(CDES64::Encode(CDES64::keySerial,originalkey,encodedata,tidsize) == FALSE ) {
		ALOGE("[DES64] encode error\r\n");
		return false;
	}

	env->SetByteArrayRegion(encodestream, 0, (tidsize), (jbyte *)encodedata);
	env->ReleaseByteArrayElements(tid,btid,0);
	return true;
}

static jboolean android_server_DNAManagerService_DES64_Decode(JNIEnv* env, jobject clazz, jbyteArray encodestream, jbyteArray tid)
{
	int tidsize = env->GetArrayLength(tid);
	int encodestreamsize = env->GetArrayLength(encodestream);

	if(tidsize != encodestreamsize) {
		ALOGE("TID, DECODESTREAM size is mismatch(TID : %d, ENCODESTREAM : %d)",tidsize,encodestreamsize);
		return false;
	}

	jbyte* bencodestream = env->GetByteArrayElements(encodestream,0);
	unsigned char *btid = (unsigned char *)malloc(tidsize);
	unsigned char *encodedata = (unsigned char *)malloc(tidsize);

	memset(btid,0x0,tidsize);
	memset(encodedata,0x0,tidsize);

	memcpy(encodedata,bencodestream,tidsize);

	if(CDES64::Decode(CDES64::keySerial,encodedata,btid,tidsize) == FALSE ) {
		ALOGE("decode error\r\n");
		return false;
	}

	env->SetByteArrayRegion(tid, 0, (tidsize), (jbyte *)btid);
	env->ReleaseByteArrayElements(encodestream,bencodestream,0);
	return true;
}


static jint android_server_DNAManagerService_WatchDogDisable(JNIEnv* env, jobject clazz)
{
    int ret = 0;

    ret = IQ_WATCHDOGDISABLE();

    ALOGD("android_server_DNAManagerService_WatchDogDisable: %d", ret);

    return ret;
}

static jint android_server_DNAManagerService_Printf(JNIEnv* env, jobject clazz, jstring jmessages)
{
    const char* messages = (jmessages)?env->GetStringUTFChars(jmessages, NULL):NULL;
    char msg[PATH_MAX + 1] = {0, };

    if (messages == NULL) {
        return -1;
    } else {
        snprintf(msg, sizeof(msg), "%s", messages);
        ALOGD("PRINTF: {%s}", msg);
        IQ_PRINTF(msg);
    }

    env->ReleaseStringUTFChars(jmessages, messages);

    return 0;
}

static jint android_server_DNAManagerService_Chmod(JNIEnv* env, jobject clazz, jstring jpath, jstring jperm, jstring jopt)
{
    const char* path = (jpath)?env->GetStringUTFChars(jpath, NULL):NULL;
    const char* perm = (jperm)?env->GetStringUTFChars(jperm, NULL):NULL;
    const char* opt = (jopt)?env->GetStringUTFChars(jopt, NULL):NULL;
    char cmd[PATH_MAX + 1] = {0, };

    if ((path == NULL) || (perm == NULL)) {
        return -1;
    } else {
        if (strlen(opt) == 0) {
            ALOGD("CHMOD: PATH{%s}/PERMISSION{%s}", path, perm);
            snprintf(cmd, sizeof(cmd), "/system/bin/chmod %s %s", perm, path);
        } else {
            ALOGD("CHMOD: PATH{%s}/PERMISSION{%s}/OPTION(%s)", path, perm, opt);
            snprintf(cmd, sizeof(cmd), "/system/bin/chmod %s %s %s", opt, perm, path);
        }

        ALOGD("CHMOD: {%s}", cmd);
        IQ_SHELL_EXCUTE(cmd);
    }

    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jperm, perm);
    env->ReleaseStringUTFChars(jopt, opt);

    return 0;
}

static jint android_server_DNAManagerService_Chown(JNIEnv* env, jobject clazz, jstring jpath, jstring juser, jstring jgroup)
{
    const char* path = (jpath)?env->GetStringUTFChars(jpath, NULL):NULL;
    const char* user = (juser)?env->GetStringUTFChars(juser, NULL):NULL;
    const char* group = (jgroup)?env->GetStringUTFChars(jgroup, NULL):NULL;
    char cmd[PATH_MAX + 1] = {0, };

    if ((path == NULL) || (user == NULL) || (group == NULL)) {
        return -1;
    } else {
        ALOGD("CHOWN: PATH{%s}/User.Group{%s.%s}", path, user, group);
        snprintf(cmd, sizeof(cmd), "/system/bin/chown %s.%s %s", user, group, path);

        ALOGD("CHOWN: {%s}", cmd);
        IQ_SHELL_EXCUTE(cmd);
    }

    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(juser, user);
    env->ReleaseStringUTFChars(jgroup, group);

    return 0;
}

static jint android_server_DNAManagerService_ReadCurrPercentage(JNIEnv* env, jobject clazz)
{
    int currpercentage = 0;

    currpercentage = IQ_GET_PERCENTAGE();

    ALOGD("android_server_DNAManagerService_ReadCurrPercentage: %d", currpercentage);

    return currpercentage;
}

static void android_server_DNAManagerService_SETIMG(JNIEnv* env, jobject clazz, jint value)
{
    int mValue = value;

    IQ_SET_IMG(mValue);

    ALOGD("android_server_DNAManagerService_WriteSETIMG: %d", mValue);
}

static void android_server_DNAManagerService_WriteUSTS(JNIEnv* env, jobject clazz, jint usts)
{
    int mUsts = usts;

    IQ_USSTATUS(usts);

    ALOGD("android_server_DNAManagerService_WriteUSTS: %d", mUsts);
}

static void android_server_DNAManagerService_AudioShutdown(JNIEnv* env, jobject clazz)
{
    IQ_AUDIO_SHUTDOWN();

    ALOGD("android_server_DNAManagerService_AudioShutdown");
}

int fork_writesplash(const char* path, const char* file) {
    int ret = -1;
    int myuid;
    unsigned char buffer[BUFSIZ];

#if 1
    pid_t pid = fork();

    ALOGD("fork_writesplash: [%d] %s/%s", pid, path, file);
    if (pid == 0) {
        ret = execl("/system/bin/dnawrite", "dnawrite", "-p", path, file, NULL);
    }
#else
    ALOGD("fork_writesplash: %s/%s", path, file);
    myuid = getuid();
    setuid(0);
    sprintf(buffer, "/system/bin/dnawrite -p %s %s", path, file);

    if (system(buffer) != 0)
        ALOGD ("ERROR:dnawrite");
    else
        ALOGD("dnawrite write success\n");

    setuid(myuid);
#endif

    if (ret == -1) {
        return SPLASH_IMG_ERR_PID;
    }

    return SPLASH_IMG_OK;
}

int fork_writesplash_clean(void) {
    int ret = -1;
    int myuid;

#if 1
    pid_t pid = fork();

    ALOGD("fork_writesplash_clean: [%d]", pid);
    if (pid == 0) {
        ret = execl("/system/bin/dnawrite", "dnawrite", "-c", "splash.img", NULL);
    }
#else
    ALOGD("fork_writesplash_clean");
    myuid = getuid();
    setuid(0);

    if (system("/system/bin/dnawrite dnawrite -c splash.img") != 0)
        ALOGD ("ERROR:dnawrite");
    else
        ALOGD("dnawrite write success\n");

    setuid(myuid);
#endif

    if (ret == -1) {
        return SPLASH_IMG_ERR_PID;
    }

    return SPLASH_IMG_OK;
}

static jint android_server_DNAManagerService_WriteSplash(JNIEnv* env, jobject clazz, jstring jfilepath, jstring jfilename)
{
    const char* filepath = (jfilepath)?env->GetStringUTFChars(jfilepath, NULL):NULL;
    const char* filename = (jfilename)?env->GetStringUTFChars(jfilename, NULL):NULL;
    int ret = SPLASH_IMG_ERR_UNKNOWN;
    char buffer[PATH_MAX + 1] = {0, };

    if (filepath == NULL) {
        ret = SPLASH_IMG_ERR_FILENOTFOUND_SPLASH_FILE;
    } else {
        if (filename == NULL) {
            ret = SPLASH_IMG_ERR_FILENOTFOUND_SPLASH_FILE;
        } else {
            ALOGD("android_server_DNAManagerService_WriteSplash: %s/%s", filepath, filename);

            snprintf(buffer, sizeof(buffer), "/system/bin/splashwrite -p %s %s", filepath, filename);

            ALOGD("WriteSplash: {%s}", buffer);
            IQ_SHELL_EXCUTE(buffer);

            ret = SPLASH_IMG_OK;
        }
    }

    env->ReleaseStringUTFChars(jfilename, filename);
    env->ReleaseStringUTFChars(jfilepath, filepath);

    return ret;
}

static jint android_server_DNAManagerService_CleanSplash(JNIEnv* env, jobject clazz)
{
    int ret = SPLASH_IMG_ERR_UNKNOWN;
    char buffer[PATH_MAX + 1] = {0, };

    ALOGD("android_server_DNAManagerService_CleanSplash");

    snprintf(buffer, sizeof(buffer), "/system/bin/splashwrite -c splash.img");

    ALOGD("CleanSplash: {%s}", buffer);
    IQ_SHELL_EXCUTE(buffer);

    ret = SPLASH_IMG_OK;

    return ret;
}

//----------------------------------------------------------------------------------------

static jbyteArray android_server_DNAManagerService_ReadPark(JNIEnv* env, jobject clazz, jint rlen, jint rseek)
{
    int osd_fd;
    jbyteArray vmStrRead;
    unsigned char *nativeStrRead = (unsigned char *)malloc(rlen);
    memset(nativeStrRead, 0x0, (rlen));
    vmStrRead = env->NewByteArray(rlen);


    osd_fd = open(PARK_DEVICE_NAME, O_RDWR);
    if (osd_fd < 0) {
        ALOGE("%s open error(%d)", PARK_DEVICE_NAME, osd_fd);
        return NULL;
    } else {
        lseek(osd_fd, rseek, SEEK_SET);
        read(osd_fd, nativeStrRead, rlen);

        if (osd_fd) close(osd_fd);
    }

    ALOGE("nativeStrRead [0x%x]", nativeStrRead);

    env->SetByteArrayRegion(vmStrRead, 0, (rlen), (jbyte *)nativeStrRead);

    free(nativeStrRead);

    return vmStrRead;
}

static void android_server_DNAManagerService_WritePark(JNIEnv* env, jobject clazz, jbyteArray wdata, jint wlen, jint wseek)
{
    int osd_fd;
    jbyte *nativeBytesWrite = env->GetByteArrayElements(wdata, 0);
    unsigned char *nativeStrWrite = (unsigned char *)malloc(wlen);
    memset(nativeStrWrite, 0x0, wlen);
    memcpy(nativeStrWrite, nativeBytesWrite, wlen);

    ALOGE("nativeStrWrite [0x%x] wlen [%d]", nativeStrWrite, wlen);

    osd_fd = open(PARK_DEVICE_NAME, O_RDWR);
    if (osd_fd < 0) {
        ALOGE("%s open error(%d)", PARK_DEVICE_NAME, osd_fd);
        return;
    } else {
        lseek(osd_fd, wseek, SEEK_SET);
        write(osd_fd, nativeStrWrite, wlen);

        if (osd_fd) close(osd_fd);
    }

    env->ReleaseByteArrayElements(wdata, nativeBytesWrite, JNI_ABORT);
    free(nativeStrWrite);
}

//----------------------------------------------------------------------------------------

static jbyteArray android_server_DNAManagerService_Read4DCalData(JNIEnv* env, jobject clazz, jint rlen, jint rseek)
{
    int osd_fd;
    jbyteArray vmStrRead;
    unsigned char *nativeStrRead = (unsigned char *)malloc(rlen + 1);
    memset(nativeStrRead, 0x0, (rlen));
    vmStrRead = env->NewByteArray(rlen);


    osd_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if (osd_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, osd_fd);
        return NULL;
    } else {
        lseek(osd_fd, FINE_4DCALDATA_ADDR + rseek, SEEK_SET);
        read(osd_fd, nativeStrRead, rlen);

        if (osd_fd) close(osd_fd);
    }

    ALOGE("android_server_DNAManagerService_Read4DCalData [0x%x]", nativeStrRead);

    env->SetByteArrayRegion(vmStrRead, 0, (rlen), (jbyte *)nativeStrRead);

    free(nativeStrRead);

    return vmStrRead;
}

static void android_server_DNAManagerService_Write4DCalData(JNIEnv* env, jobject clazz, jbyteArray wdata, jint wlen, jint wseek)
{
    int osd_fd;
    jbyte *nativeBytesWrite = env->GetByteArrayElements(wdata, 0);
    unsigned char *nativeStrWrite = (unsigned char *)malloc(wlen);
    memset(nativeStrWrite, 0x0, wlen);
    memcpy(nativeStrWrite, nativeBytesWrite, wlen);

    ALOGE("android_server_DNAManagerService_Write4DCalData [0x%x] wlen [%d]", nativeStrWrite, wlen);

    osd_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if (osd_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, osd_fd);
        return;
    } else {
        lseek(osd_fd, FINE_4DCALDATA_ADDR + wseek, SEEK_SET);
        write(osd_fd, nativeStrWrite, wlen);

        if (osd_fd) close(osd_fd);
    }

    env->ReleaseByteArrayElements(wdata, nativeBytesWrite, JNI_ABORT);
    free(nativeStrWrite);
}

//----------------------------------------------------------------------------------------

static jbyteArray android_server_DNAManagerService_ReadImage(JNIEnv* env, jobject clazz, jint rlen, jint rseek)
{
    int osd_fd;
    jbyteArray vmStrRead;
    unsigned char *nativeStrRead = (unsigned char *)malloc(rlen + 1);
    memset(nativeStrRead, 0x0, (rlen));
    vmStrRead = env->NewByteArray(rlen);


    osd_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if (osd_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, osd_fd);
        return NULL;
    } else {
        lseek(osd_fd, FINE_IMAGE_ADDR + rseek, SEEK_SET);
        read(osd_fd, nativeStrRead, rlen);

        if (osd_fd) close(osd_fd);
    }

    ALOGE("nativeStrRead [0x%x]", nativeStrRead);

    env->SetByteArrayRegion(vmStrRead, 0, (rlen), (jbyte *)nativeStrRead);

    free(nativeStrRead);

    return vmStrRead;
}

static void android_server_DNAManagerService_WriteImage(JNIEnv* env, jobject clazz, jbyteArray wdata, jint wlen, jint wseek)
{
    int osd_fd;
    jbyte *nativeBytesWrite = env->GetByteArrayElements(wdata, 0);
    unsigned char *nativeStrWrite = (unsigned char *)malloc(wlen);
    memset(nativeStrWrite, 0x0, wlen);
    memcpy(nativeStrWrite, nativeBytesWrite, wlen);

    ALOGE("nativeStrWrite [0x%x] wlen [%d]", nativeStrWrite, wlen);

    osd_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if (osd_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, osd_fd);
        return;
    } else {
        lseek(osd_fd, FINE_IMAGE_ADDR + wseek, SEEK_SET);
        write(osd_fd, nativeStrWrite, wlen);

        if (osd_fd) close(osd_fd);
    }

    env->ReleaseByteArrayElements(wdata, nativeBytesWrite, JNI_ABORT);
    free(nativeStrWrite);
}

//----------------------------------------------------------------------------------------

static jchar android_server_DNAManagerService_ReadArchitecture(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_CPU_INFO versions;
    char date;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_CPU_ADDR;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_CPU_INFO);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    date = versions.wArchitecture;

    ALOGD("android_server_DNAManagerService_ReadArchitecture: %c", date);

    close(nand_fd);

    return date;
}

static void android_server_DNAManagerService_WriteArchitecture(JNIEnv* env, jobject clazz, jchar jdate)
{
    const char date = jdate;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_CPU_INFO versions;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_CPU_ADDR;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    versions.wArchitecture = date;

    ALOGD("android_server_DNAManagerService_WriteArchitecture: %c", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jchar android_server_DNAManagerService_ReadProcessorType(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_CPU_INFO versions;
    char date;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_CPU_ADDR;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_CPU_INFO);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    date = versions.wProcessorType;

    ALOGD("android_server_DNAManagerService_ReadProcessorType: %c", date);

    close(nand_fd);

    return date;
}

static void android_server_DNAManagerService_WriteProcessorType(JNIEnv* env, jobject clazz, jchar jdate)
{
    const char date = jdate;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_CPU_INFO versions;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_CPU_ADDR;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    versions.wProcessorType = date;

    ALOGD("android_server_DNAManagerService_WriteProcessorType: %c", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jchar android_server_DNAManagerService_ReadProcessorCompiler(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_CPU_INFO versions;
    char date;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_CPU_ADDR;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_CPU_INFO);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    date = versions.wProcessorCompiler;

    ALOGD("android_server_DNAManagerService_ReadProcessorCompiler: %c", date);

    close(nand_fd);

    return date;
}

static void android_server_DNAManagerService_WriteProcessorCompiler(JNIEnv* env, jobject clazz, jchar jdate)
{
    const char date = jdate;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_CPU_INFO versions;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_CPU_ADDR;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    versions.wProcessorCompiler = date;

    ALOGD("android_server_DNAManagerService_WriteProcessorCompiler: %c", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

//----------------------------------------------------------------------------------------

static jstring android_server_DNAManagerService_ReadosVerBuildDate(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char buildDateSize[DATE_SIZE + 1] = {0, };
    char build_date[14] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char *)&versions.dwSig;
    q = (unsigned char *)&versions.osVer.dwSig;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&versions.osVer.dwSig, FINE_OS_SIG, sizeof(versions.osVer.dwSig))) {
        for(int i = 0; i < DATE_SIZE; i++) {
            if(versions.osVer.wBuildDate[i] < 0x30 || versions.osVer.wBuildDate[i] > 0x39) {
                buildDateSize[i] = 0;
                break;
            }
            buildDateSize[i] = versions.osVer.wBuildDate[i];
        }
        buildDateSize[DATE_SIZE] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&versions.osVer.dwSig, FINE_OS_SIG, sizeof(versions.osVer.dwSig))) {
            for(int i=0; i<DATE_SIZE; i++) {
                if(versions.osVer.wBuildDate[i] < 0x30 || versions.osVer.wBuildDate[i] > 0x39) {
                    buildDateSize[i] = 0;
                    break;
                }
                buildDateSize[i] = versions.osVer.wBuildDate[i];
            }
            buildDateSize[DATE_SIZE] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    // psw0523 fix for compile error
#if 0
    if(buildDateSize[0] == 0) {
        property_get("ro.build.display.date", build_date, "");
        sprintf(buildDateSize, "%s", build_date);
    }
#endif

    ALOGD("android_server_DNAManagerService_ReadosVerBuildDate: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static jstring android_server_DNAManagerService_ReadbootVerBuildDate(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char buildDateSize[DATE_SIZE + 1] = {0, };
    char build_date[14] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char *)&versions.dwSig;
    q = (unsigned char *)&versions.bootVer.dwSig;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&versions.bootVer.dwSig, FINE_BOOT_SIG, sizeof(versions.bootVer.dwSig))) {
        for(int i=0; i<DATE_SIZE; i++) {
            if(versions.bootVer.wBuildDate[i] < 0x30 || versions.bootVer.wBuildDate[i] > 0x39) {
                buildDateSize[i] = 0;
                break;
            }
            buildDateSize[i] = versions.bootVer.wBuildDate[i];
        }
        buildDateSize[DATE_SIZE] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&versions.bootVer.dwSig, FINE_BOOT_SIG, sizeof(versions.bootVer.dwSig))) {
            for(int i=0; i<DATE_SIZE; i++) {
                if(versions.bootVer.wBuildDate[i] < 0x30 || versions.bootVer.wBuildDate[i] > 0x39) {
                    buildDateSize[i] = 0;
                    break;
                }
                buildDateSize[i] = versions.bootVer.wBuildDate[i];
            }
            buildDateSize[DATE_SIZE] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    // psw0523 fix : compile error
#if 0
    if(buildDateSize[0] == 0) {
        property_get("ro.build.display.date", build_date, "");
        sprintf(buildDateSize, "%s", build_date);
    }
#endif

    ALOGD("android_server_DNAManagerService_ReadbootVerBuildDate: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static jstring android_server_DNAManagerService_ReadpcbVerModelId(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char buildDateSize[5] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char*)&versions.dwSig;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig))) {
        q = (unsigned char*)&versions.pcbVer.dwModelId;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig))) {
            q = (unsigned char*)&versions.pcbVer.dwModelId;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadpcbVerModelId: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static jstring android_server_DNAManagerService_ReadpcbVerPlatformId(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char buildDateSize[5] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char*)&versions.dwSig;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig))) {
        q = (unsigned char*)&versions.pcbVer.dwPlatformId;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        p = (unsigned char*)&versions.dwSig;

        if(!memcmp(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig))) {
             q = (unsigned char*)&versions.pcbVer.dwPlatformId;
             for(int i = 0; i < 4; i++) {
                 buildDateSize[i] = q[i];
             }
             buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadpcbVerPlatformId: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static jstring android_server_DNAManagerService_ReadpcbVerPCBVersion(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char buildDateSize[5] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char*)&versions.dwSig;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig))) {
        q = (unsigned char*)&versions.pcbVer.dwPCBVersion;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        p = (unsigned char*)&versions.dwSig;

        if(!memcmp(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig))) {
            q = (unsigned char*)&versions.pcbVer.dwPCBVersion;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadpcbVerPCBVersion: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

//----------------------------------------------------------------------------------------

static void android_server_DNAManagerService_WriteosVerBuildDate(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char temp[DATE_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    memcpy(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig));
    memcpy(&versions.osVer.dwSig, FINE_OS_SIG, sizeof(versions.osVer.dwSig));
    strcpy(temp, date);
    for(int i=0; i < DATE_SIZE; i++) {
        if(temp[i] < 0x30 || temp[i] > 0x39) {
            versions.osVer.wBuildDate[i] = 0;
            break;
        }
        versions.osVer.wBuildDate[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteosVerBuildDate: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static void android_server_DNAManagerService_WritebootVerBuildDate(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char temp[DATE_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    memcpy(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig));
    memcpy(&versions.bootVer.dwSig, FINE_BOOT_SIG, sizeof(versions.bootVer.dwSig));
    strcpy(temp, date);
    for(int i=0; i < DATE_SIZE; i++) {
        if(temp[i] < 0x30 || temp[i] > 0x39) {
            versions.bootVer.wBuildDate[i] = 0;
            break;
        }
        versions.bootVer.wBuildDate[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WritebootVerBuildDate: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static void android_server_DNAManagerService_WritepcbVerModelId(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&versions.pcbVer.dwModelId;

    memcpy(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WritepcbVerModelId: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static void android_server_DNAManagerService_WritepcbVerPlatformId(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&versions.pcbVer.dwPlatformId;

    memcpy(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WritepcbVerPlatformId: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static void android_server_DNAManagerService_WritepcbVerPCBVersion(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_VERSIONS versions;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_VERSIONS_ADDR1;
    hd_info.SeekAddress2 = FINE_VERSIONS_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&versions;
    hd_info.DataBufferSize = sizeof(FINE_VERSIONS);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&versions.pcbVer.dwPCBVersion;

    memcpy(&versions.dwSig, FINE_VERSION_SIG, sizeof(versions.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WritePCBVersion: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

//----------------------------------------------------------------------------------------

static jint android_server_DNAManagerService_ReadLocked(JNIEnv* env, jobject clazz)
{
    int ret = ANTI_THIEF_UNKNOWN;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_ANTI_THIEF thief;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_ANTI_THIEF_ADDR1;
    hd_info.SeekAddress2 = FINE_ANTI_THIEF_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&thief;
    hd_info.DataBufferSize = sizeof(FINE_ANTI_THIEF);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    if(!memcmp(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig))) {
        if(memcmp(&thief.dwLocked, FINE_ANTI_THIEF_LOCK_SIG, sizeof(thief.dwLocked))) {
            ret = ANTI_THIEF_UNLOCK;
        } else {
            ret = ANTI_THIEF_LOCK;
        }

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(memcmp(&thief.dwLocked, FINE_ANTI_THIEF_LOCK_SIG, sizeof(thief.dwLocked))) {
            ret = ANTI_THIEF_UNLOCK;
        } else {
            ret = ANTI_THIEF_LOCK;
        }

        lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    }

    ALOGD("android_server_DNAManagerService_ReadLocked: %d", ret);

    close(nand_fd);

    return ret;
}

static jstring android_server_DNAManagerService_ReadNumLocked(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_ANTI_THIEF thief;
    char buildDateSize[5] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_ANTI_THIEF_ADDR1;
    hd_info.SeekAddress2 = FINE_ANTI_THIEF_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&thief;
    hd_info.DataBufferSize = sizeof(FINE_ANTI_THIEF);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char*)&thief.dwSig;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig))) {
        q = (unsigned char*)&thief.dwNumLocked;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        p = (unsigned char*)&thief.dwNumLocked;

        if(!memcmp(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig))) {
            q = (unsigned char*)&thief.dwNumLocked;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadNumLocked: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static jstring android_server_DNAManagerService_Readtime(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_ANTI_THIEF thief;
    char buildDateSize[SYSTEMTIME_SIZE + 1] = {0, };
    char build_date[10] = {0, };
    unsigned char * p;
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_ANTI_THIEF_ADDR1;
    hd_info.SeekAddress2 = FINE_ANTI_THIEF_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&thief;
    hd_info.DataBufferSize = sizeof(FINE_ANTI_THIEF);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    p = (unsigned char *)&thief.dwSig;
    q = (unsigned char *)&thief.time;

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig))) {
        for(int i = 0; i < SYSTEMTIME_SIZE; i++) {
            if(q[i] < 0x30 || q[i] > 0x39) {
                buildDateSize[i] = 0;
                break;
            }
            buildDateSize[i] = q[i];
        }
        buildDateSize[SYSTEMTIME_SIZE] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig))) {
            for(int i = 0; i < SYSTEMTIME_SIZE; i++) {
                if(q[i] < 0x30 || q[i] > 0x39) {
                    buildDateSize[i] = 0;
                    break;
                }
                buildDateSize[i] = q[i];
            }
            buildDateSize[SYSTEMTIME_SIZE] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_Readtime: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteLocked(JNIEnv* env, jobject clazz, jint mode)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_ANTI_THIEF thief;
    int mMode = mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_ANTI_THIEF_ADDR1;
    hd_info.SeekAddress2 = FINE_ANTI_THIEF_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&thief;
    hd_info.DataBufferSize = sizeof(FINE_ANTI_THIEF);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    memcpy(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig));
    if(mMode == ANTI_THIEF_LOCK) {
        memcpy(&thief.dwLocked, FINE_ANTI_THIEF_LOCK_SIG, sizeof(thief.dwLocked));
    } else {
        memcpy(&thief.dwLocked, FINE_ANTI_THIEF_UNLOCK_SIG, sizeof(thief.dwLocked));
    }

    ALOGD("android_server_DNAManagerService_WriteLocked: %d", mMode);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteNumLocked(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_ANTI_THIEF thief;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_ANTI_THIEF_ADDR1;
    hd_info.SeekAddress2 = FINE_ANTI_THIEF_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&thief;
    hd_info.DataBufferSize = sizeof(FINE_ANTI_THIEF);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&thief.dwNumLocked;

    memcpy(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteNumLocked: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static void android_server_DNAManagerService_Writetime(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_ANTI_THIEF thief;
    char temp[SYSTEMTIME_SIZE + 1] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_ANTI_THIEF_ADDR1;
    hd_info.SeekAddress2 = FINE_ANTI_THIEF_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&thief;
    hd_info.DataBufferSize = sizeof(FINE_ANTI_THIEF);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char *)&thief.time;

    memcpy(&thief.dwSig, FINE_ANTI_THIEF_SIG, sizeof(thief.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < SYSTEMTIME_SIZE; i++) {
        if(temp[i] < 0x30 || temp[i] > 0x39) {
            q[i] = 0;
            break;
        }
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_Writetime: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

//----------------------------------------------------------------------------------------

static jstring android_server_DNAManagerService_ReadBootCount(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
        q = (unsigned char*)&util.dwBootCount;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
            q = (unsigned char*)&util.dwBootCount;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadBootCount: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteBootCount(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&util.dwBootCount;

    memcpy(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteBootCount: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jstring android_server_DNAManagerService_ReadAutoRecoveryScratch1(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
        q = (unsigned char*)&util.AutoRecoveryScratch1;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
            q = (unsigned char*)&util.AutoRecoveryScratch1;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadAutoRecoveryScratch1: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteAutoRecoveryScratch1(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&util.AutoRecoveryScratch1;

    memcpy(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteAutoRecoveryScratch1: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jstring android_server_DNAManagerService_ReadAutoRecoveryScratch2(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
        q = (unsigned char*)&util.AutoRecoveryScratch2;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
            q = (unsigned char*)&util.AutoRecoveryScratch2;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadAutoRecoveryScratch2: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteAutoRecoveryScratch2(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&util.AutoRecoveryScratch2;

    memcpy(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteAutoRecoveryScratch2: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jstring android_server_DNAManagerService_ReadAuthSDCheckScratch1(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
        q = (unsigned char*)&util.AuthSDCheckScratch1;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
            q = (unsigned char*)&util.AuthSDCheckScratch1;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadAuthSDCheckScratch1: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteAuthSDCheckScratch1(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&util.AuthSDCheckScratch1;

    memcpy(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteAuthSDCheckScratch1: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jstring android_server_DNAManagerService_ReadAuthSDCheckScratch2(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
        q = (unsigned char*)&util.AuthSDCheckScratch2;
        for(int i = 0; i < 4; i++) {
            buildDateSize[i] = q[i];
        }
        buildDateSize[4] = NULL;

        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig))) {
            q = (unsigned char*)&util.AuthSDCheckScratch2;
            for(int i = 0; i < 4; i++) {
                buildDateSize[i] = q[i];
            }
            buildDateSize[4] = NULL;

            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadAuthSDCheckScratch2: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteAuthSDCheckScratch2(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_UTIL util;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_FINE_UTIL_ADDR1;
    hd_info.SeekAddress2 = FINE_FINE_UTIL_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&util;
    hd_info.DataBufferSize = sizeof(FINE_UTIL);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&util.AuthSDCheckScratch2;

    memcpy(&util.dwSig, FINE_UTIL_SIG, sizeof(util.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteAuthSDCheckScratch2: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

//----------------------------------------------------------------------------------------

static jstring android_server_DNAManagerService_ReadUUID(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_PERMENENT_INFO perm;
    char UUID[UUID_DATE_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_PERMENENT_ADDR1;
    hd_info.SeekAddress2 = FINE_PERMENENT_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&perm;
    hd_info.DataBufferSize = sizeof(FINE_PERMENENT_INFO);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    if(!memcmp(&perm.dwSig, FINE_PERMENENT_SIG, sizeof(perm.dwSig)) && !memcmp(&perm.uuid.dwSig, FINE_UUID_SIG, sizeof(perm.uuid.dwSig))) {

#if 1 //Eroum:hh.shin:141208 - TID encode
{
	unsigned char enc[128] = {0x0,};
	unsigned char deckey[128] = {0x0,};

	memcpy(enc,perm.uuid.arrUUID,sizeof(enc));
	ALOGD("Decode1");
	if( CDES64::Decode(CDES64::keySerial, enc, deckey, 128) == FALSE ) {
		ALOGE("decode error\r\n");
		if(nand_fd) close(nand_fd);
		return NULL;
	}

	for(int i=0; i < UUID_DATE_SIZE-1; i++) {
		UUID[i] = deckey[i];
            if(deckey[i] < 0x30 || deckey[i] > 0x39) {
			UUID[i] = 0;
		}
	}
}
#else
        for(int i=0; i < UUID_DATE_SIZE; i++) {
            UUID[i] = perm.uuid.arrUUID[i];
            if(perm.uuid.arrUUID[i] < 0x30 || perm.uuid.arrUUID[i] > 0x39) {
                UUID[i] = 0;
                break;
            }
        }
#endif //Eroum:hh.shin:141208 - TID encode
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
    } else {
        memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
        lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
        readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

        if(!memcmp(&perm.dwSig, FINE_PERMENENT_SIG, sizeof(perm.dwSig)) && !memcmp(&perm.uuid.dwSig, FINE_UUID_SIG, sizeof(perm.uuid.dwSig))) {

#if 1 //Eroum:hh.shin:141208 - TID encode
		{
			unsigned char enc[128] = {0x0,};
			unsigned char deckey[128] = {0x0,};

			memcpy(enc,perm.uuid.arrUUID,sizeof(enc));
			ALOGD("Decode2");
			if( CDES64::Decode(CDES64::keySerial, enc, deckey, 128) == FALSE ) {
				ALOGE("decode error\r\n");
				if(nand_fd) close(nand_fd);
				return NULL;
			}

			for(int i=0; i < UUID_DATE_SIZE; i++) {
				UUID[i] = deckey[i];
			}
		}
#else
            for(int i=0; i < UUID_DATE_SIZE; i++) {
                UUID[i] = perm.uuid.arrUUID[i];
                if(perm.uuid.arrUUID[i] < 0x30 || perm.uuid.arrUUID[i] > 0x39) {
                    UUID[i] = 0;
                    break;
                }
            }
#endif //Eroum:hh.shin:141208 - TID encode


            lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
            writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);
        }
    }

    ALOGD("android_server_DNAManagerService_ReadUUID: %s", UUID);

    close(nand_fd);

    return env->NewStringUTF(UUID);
}

static void android_server_DNAManagerService_WriteUUID(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_PERMENENT_INFO perm;
    char temp[UUID_DATE_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_PERMENENT_ADDR1;
    hd_info.SeekAddress2 = FINE_PERMENENT_ADDR2;
    hd_info.DataBuffer = (unsigned char *)&perm;
    hd_info.DataBufferSize = sizeof(FINE_PERMENENT_INFO);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    memcpy(&perm.dwSig, FINE_PERMENENT_SIG, sizeof(perm.dwSig));
    memcpy(&perm.uuid.dwSig, FINE_UUID_SIG, sizeof(perm.uuid.dwSig));

#if 1 //Eroum:hh.shin:141208 - TID encode
{
	unsigned char originalkey[128]={0x0,};

	strcpy((char *)originalkey, date);

	ALOGD("Encode");
	if( CDES64::Encode(CDES64::keySerial, originalkey,(unsigned char *)temp, 128) == FALSE ) {
		ALOGE("[DES64] encode error\r\n");
		return;
	}
	for(int i = 0; i < UUID_DATE_SIZE; i++) {
        perm.uuid.arrUUID[i] = temp[i];
      }
}
#else
    strcpy(temp, date);
    for(int i = 0; i < UUID_DATE_SIZE; i++) {
        perm.uuid.arrUUID[i] = temp[i];
        if(temp[i] < 0x30 || temp[i] > 0x39) {
            perm.uuid.arrUUID[i] = 0;
        }
    }
#endif ////Eroum:hh.shin:141208 - TID encode

    ALOGD("android_server_DNAManagerService_WriteUUID: %s", perm.uuid.arrUUID);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    lseek(nand_fd, hd_info.SeekAddress2, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

//----------------------------------------------------------------------------------------

static jstring android_server_DNAManagerService_ReadTPEG_KEY(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_TPEG tpeg;
    char TPEG_BUFFER[TPEG_KEY_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_TPEG_ADDR;
    hd_info.DataBuffer = (unsigned char *)&tpeg;
    hd_info.DataBufferSize = sizeof(FINE_TPEG);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    if(!memcmp(&tpeg.dwSig, FINE_TPEG_SIG, sizeof(tpeg.dwSig))) {
        for(int i=0; i < UUID_DATE_SIZE; i++) {
            TPEG_BUFFER[i] = tpeg.arrTPEG_KEY[i];
        }
    }

    ALOGD("android_server_DNAManagerService_ReadTPEG_KEY: %s", TPEG_BUFFER);

    close(nand_fd);

    return env->NewStringUTF(TPEG_BUFFER);
}

static void android_server_DNAManagerService_WriteTPEG_KEY(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_TPEG tpeg;
    char temp[TPEG_KEY_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_TPEG_ADDR;
    hd_info.DataBuffer = (unsigned char *)&tpeg;
    hd_info.DataBufferSize = sizeof(FINE_TPEG);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    memcpy(&tpeg.dwSig, FINE_TPEG_SIG, sizeof(tpeg.dwSig));
    strcpy(temp, date);
    for(int i = 0; i < TPEG_KEY_SIZE; i++) {
        tpeg.arrTPEG_KEY[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteTPEG_KEY: %s", tpeg.arrTPEG_KEY);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

//----------------------------------------------------------------------------------------

static jlong android_server_DNAManagerService_ReadRearcamX(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadRearcamX: %ld", rearcam.dwRearcamX);

    close(nand_fd);

    return rearcam.dwRearcamX;
}

static void android_server_DNAManagerService_WriteRearcamX(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwRearcamX = value;

    ALOGD("android_server_DNAManagerService_WriteRearcamX: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadRearcamY(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadRearcamY: %ld", rearcam.dwRearcamY);

    close(nand_fd);

    return rearcam.dwRearcamY;
}

static void android_server_DNAManagerService_WriteRearcamY(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwRearcamY = value;

    ALOGD("android_server_DNAManagerService_WriteRearcamY: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}


static jlong android_server_DNAManagerService_ReadWheelAngle(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadWheelAngle: %ld", rearcam.dwWheelAngle);

    close(nand_fd);

    return rearcam.dwWheelAngle;
}

static void android_server_DNAManagerService_WriteWheelAngle(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwWheelAngle = value;

    ALOGD("android_server_DNAManagerService_WriteWheelAngle: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadCamAngle(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadCamAngle: %ld", rearcam.dwCamAngle);

    close(nand_fd);

    return rearcam.dwCamAngle;
}

static void android_server_DNAManagerService_WriteCamAngle(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwCamAngle = value;

    ALOGD("android_server_DNAManagerService_WriteCamAngle: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadCamHeight(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadCamHeight: %ld", rearcam.dwCamHeight);

    close(nand_fd);

    return rearcam.dwCamHeight;
}

static void android_server_DNAManagerService_WriteCamHeight(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwCamHeight = value;

    ALOGD("android_server_DNAManagerService_WriteCamHeight: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadParklineDisable(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadParklineDisable: %ld", rearcam.dwParklineDisable);

    close(nand_fd);

    return rearcam.dwParklineDisable;
}

static void android_server_DNAManagerService_WriteParklineDisable(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwParklineDisable = value;

    ALOGD("android_server_DNAManagerService_WriteParklineDisable: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadWheelParklineDisable(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadWheelParklineDisable: %ld", rearcam.dwWheelParklineDisable);

    close(nand_fd);

    return rearcam.dwWheelParklineDisable;
}

static void android_server_DNAManagerService_WriteWheelParklineDisable(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwWheelParklineDisable = value;

    ALOGD("android_server_DNAManagerService_WriteWheelParklineDisable: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}


static jlong android_server_DNAManagerService_ReadFrontCameraOnOff(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadFrontCameraOnOff: %ld", rearcam.dwFrontCameraOnOff);

    close(nand_fd);

    return rearcam.dwFrontCameraOnOff;
}

static void android_server_DNAManagerService_WriteFrontCameraOnOff(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwFrontCameraOnOff = value;

    ALOGD("android_server_DNAManagerService_WriteFrontCameraOnOff: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadCameraAutoControlOnOff(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadCameraAutoControlOnOff: %ld", rearcam.dwCameraAutoControlOnOff);

    close(nand_fd);

    return rearcam.dwCameraAutoControlOnOff;
}

static void android_server_DNAManagerService_WriteCameraAutoControlOnOff(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwCameraAutoControlOnOff = value;

    ALOGD("android_server_DNAManagerService_WriteCameraAutoControlOnOff: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jlong android_server_DNAManagerService_ReadRearcamDisable(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGD("android_server_DNAManagerService_ReadRearcamDisable: %ld", rearcam.dwRearcamDisable);

    close(nand_fd);

    return rearcam.dwRearcamDisable;
}

static void android_server_DNAManagerService_WriteRearcamDisable(JNIEnv* env, jobject clazz, jlong jdate)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_REARCAM rearcam;
    unsigned long value = jdate;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_REARCAM_ADDR;
    hd_info.DataBuffer = (unsigned char *)&rearcam;
    hd_info.DataBufferSize = sizeof(FINE_REARCAM);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    rearcam.dwRearcamDisable = value;

    ALOGD("android_server_DNAManagerService_WriteRearcamDisable: %ld", value);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

//----------------------------------------------------------------------------------------

static jstring android_server_DNAManagerService_ReadBlackBoxOnlyMode(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwBlackBoxOnlyMode;
    for(int i = 0; i < 4; i++) {
        buildDateSize[i] = q[i];
    }
    buildDateSize[4] = NULL;

    ALOGD("android_server_DNAManagerService_ReadBlackBoxOnlyMode: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteBlackBoxOnlyMode(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwBlackBoxOnlyMode;

    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteBlackBoxOnlyMode: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jint android_server_DNAManagerService_ReadDRAngle(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mise.nDRAngle;

    ALOGD("android_server_DNAManagerService_ReadDRAngle: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteDRAngle(JNIEnv* env, jobject clazz, jint mode)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    int mMode = mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

     mise.nDRAngle = mMode;

    ALOGD("android_server_DNAManagerService_WriteDRAngle: %d", mMode);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jstring android_server_DNAManagerService_ReadCalibrationCheck(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwCalibrationCheck;
    for(int i = 0; i < 4; i++) {
        buildDateSize[i] = q[i];
    }
    buildDateSize[4] = NULL;

    ALOGD("android_server_DNAManagerService_ReadCalibrationCheck: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteCalibrationCheck(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwCalibrationCheck;

    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteCalibrationCheck: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jstring android_server_DNAManagerService_ReadTvoutEnable(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwTvoutEnable;
    for(int i = 0; i < 4; i++) {
        buildDateSize[i] = q[i];
    }
    buildDateSize[4] = NULL;

    ALOGD("android_server_DNAManagerService_ReadTvoutEnable: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteTvoutEnable(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwTvoutEnable;

    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteTvoutEnable: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

static jstring android_server_DNAManagerService_ReadDrFault(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char buildDateSize[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ALOGE("%s readcnt(%d) sizeof(%d)", DNA_DEVICE_NAME, readcnt, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwDrFault;
    for(int i = 0; i < 4; i++) {
        buildDateSize[i] = q[i];
    }
    buildDateSize[4] = NULL;

    ALOGD("android_server_DNAManagerService_ReadDrFault: %s", buildDateSize);

    close(nand_fd);

    return env->NewStringUTF(buildDateSize);
}

static void android_server_DNAManagerService_WriteDrFault(JNIEnv* env, jobject clazz, jstring jdate)
{
    const char* date = (jdate)?env->GetStringUTFChars(jdate, NULL):NULL;
    if(date == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MISE mise;
    char temp[5] = {0, };
    unsigned char * q;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MISC_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mise;
    hd_info.DataBufferSize = sizeof(FINE_MISE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    q = (unsigned char*)&mise.dwDrFault;

    strcpy(temp, date);
    for(int i = 0; i < 4; i++) {
        q[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteDrFault: %s", date);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);

    env->ReleaseStringUTFChars(jdate, date);
}

//----------------------------------------------------------------------------------------

static jint android_server_DNAManagerService_ReadMode(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwMode;

    ALOGD("android_server_DNAManagerService_ReadMode: %d", ret);

    close(nand_fd);

    return ret;
}

static jint android_server_DNAManagerService_ReadCurrent(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwCurrent;

    ALOGD("android_server_DNAManagerService_ReadCurrent: %d", ret);

    close(nand_fd);

    return ret;
}

static jint android_server_DNAManagerService_ReadPercentage(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwPercentage;

    ALOGD("android_server_DNAManagerService_ReadPercentage: %d", ret);

    close(nand_fd);

    return ret;
}

static jint android_server_DNAManagerService_FDTHistoryRead(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwFDTHistory;

    ALOGD("android_server_DNAManagerService_FTDHistoryRead: %d", ret);

    close(nand_fd);

    return ret;
}

static jint android_server_DNAManagerService_ReadBootCounts(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwBootCount;

    ALOGD("android_server_DNAManagerService_ReadBootCounts: %d", ret);

    close(nand_fd);

    return ret;
}

static jint android_server_DNAManagerService_ReadOSCurrent(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwOSCurrent;

    ALOGD("android_server_DNAManagerService_ReadOSCurrent: %d", ret);

    close(nand_fd);

    return ret;
}

static jstring android_server_DNAManagerService_ReadUpdate(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    char Update_BUFFER[UPDATE_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    for(int i = 0; i < UPDATE_SIZE; i++) {
        Update_BUFFER[i] = mode.dwUpdate[i];
    }

    ALOGD("android_server_DNAManagerService_ReadUpdate: %s", Update_BUFFER);

    close(nand_fd);

    return env->NewStringUTF(Update_BUFFER);
}

static jstring android_server_DNAManagerService_ReadVersion(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    char Version_BUFFER[VERSION_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    for(int i = 0; i < VERSION_SIZE; i++) {
        Version_BUFFER[i] = mode.dwVersion[i];
    }

    ALOGD("android_server_DNAManagerService_ReadVersion: %s", Version_BUFFER);

    close(nand_fd);

    return env->NewStringUTF(Version_BUFFER);
}

static jstring android_server_DNAManagerService_ReadRecovery(JNIEnv* env, jobject clazz)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    char Recovery_BUFFER[RECOVERY_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    for(int i = 0; i < VERSION_SIZE; i++) {
        Recovery_BUFFER[i] = mode.dwRecovery[i];
    }

    ALOGD("android_server_DNAManagerService_ReadRecovery: %s", Recovery_BUFFER);

    close(nand_fd);

    return env->NewStringUTF(Recovery_BUFFER);
}

static jint android_server_DNAManagerService_ReadModeUPDATEUSB(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwModeUPDATEUSB;

    ALOGD("android_server_DNAManagerService_ReadModeUPDATEUSB: %d", ret);

    close(nand_fd);

    return ret;
}

static jint android_server_DNAManagerService_ReadUpdateCase(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwUpdateCase;

    ALOGD("android_server_DNAManagerService_ReadUpdateCase: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteMode(JNIEnv* env, jobject clazz, jint wMode)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mMode = wMode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwMode = mMode;

    ALOGD("android_server_DNAManagerService_WriteMode: %d", mMode);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteCurrent(JNIEnv* env, jobject clazz, jint wCurrent)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mCurrent = wCurrent;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwCurrent = mCurrent;

    ALOGD("android_server_DNAManagerService_WriteCurrent: %d", mCurrent);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WritePercentage(JNIEnv* env, jobject clazz, jint wPercentage)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mPercentage = wPercentage;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwPercentage = mPercentage;

    ALOGD("android_server_DNAManagerService_WritePercentage: %d", mPercentage);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_FDTHistoryWrite(JNIEnv* env, jobject clazz, jint wFDTHistory)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mFDTHistory = wFDTHistory;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwFDTHistory = mFDTHistory;

    ALOGD("android_server_DNAManagerService_FDTHistoryWrite: %d", mFDTHistory);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteBootCounts(JNIEnv* env, jobject clazz, jint wBootCount)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mBootCount = wBootCount;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwBootCount = mBootCount;

    ALOGD("android_server_DNAManagerService_WriteBootCounts: %d", mBootCount);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteOSCurrent(JNIEnv* env, jobject clazz, jint wOSCurrent)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mOSCurrent = wOSCurrent;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwOSCurrent = mOSCurrent;

    ALOGD("android_server_DNAManagerService_WriteOSCurrent: %d", mOSCurrent);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteUpdate(JNIEnv* env, jobject clazz, jstring jUpdate)
{
    const char* Update = (jUpdate)?env->GetStringUTFChars(jUpdate, NULL):NULL;
    if(Update == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    char temp[UPDATE_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    strcpy(temp, Update);
    for(int i = 0; i < UPDATE_SIZE; i++) {
        mode.dwUpdate[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteUpdate: %s", mode.dwUpdate);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteVersion(JNIEnv* env, jobject clazz, jstring jVersion)
{
    const char* Version = (jVersion)?env->GetStringUTFChars(jVersion, NULL):NULL;
    if(Version == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    char temp[VERSION_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    strcpy(temp, Version);
    for(int i = 0; i < VERSION_SIZE; i++) {
        mode.dwVersion[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteVersion: %s", mode.dwVersion);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteRecovery(JNIEnv* env, jobject clazz, jstring jRecovery)
{
    const char* Recovery = (jRecovery)?env->GetStringUTFChars(jRecovery, NULL):NULL;
    if(Recovery == NULL)
        return;

    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    char temp[RECOVERY_SIZE + 1] = {0, };

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    strcpy(temp, Recovery);
    for(int i = 0; i < RECOVERY_SIZE; i++) {
        mode.dwRecovery[i] = temp[i];
    }

    ALOGD("android_server_DNAManagerService_WriteRecovery: %s", mode.dwRecovery);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteModeUPDATEUSB(JNIEnv* env, jobject clazz, jint wMode)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mMode = wMode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwModeUPDATEUSB = mMode;

    ALOGD("android_server_DNAManagerService_WriteMode: %d", mMode);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteUpdateCase(JNIEnv* env, jobject clazz, jint wUpdateCase)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mUpdateCase = wUpdateCase;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwUpdateCase = mUpdateCase;

    ALOGD("android_server_DNAManagerService_WriteUpdateCase: %d", mUpdateCase);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jint android_server_DNAManagerService_ReadWaitProgress(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwWaitProgress;

    ALOGD("android_server_DNAManagerService_ReadWaitProgress: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteWaitProgress(JNIEnv* env, jobject clazz, jint wWaitProgress)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mWaitProgress = wWaitProgress;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwWaitProgress = mWaitProgress;

    ALOGD("android_server_DNAManagerService_WriteWaitProgress: %d", mWaitProgress);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jint android_server_DNAManagerService_ReadTime1(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwTime1;

    ALOGD("android_server_DNAManagerService_ReadTime1: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteTimes(JNIEnv* env, jobject clazz, jint wTime1, jint wTime2, jint wTime3, jint wTime4, jint wTime5)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mTime1 = wTime1;
    unsigned int mTime2 = wTime2;
    unsigned int mTime3 = wTime3;
    unsigned int mTime4 = wTime4;
    unsigned int mTime5 = wTime5;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwTime1 = mTime1;
    mode.dwTime2 = mTime2;
    mode.dwTime3 = mTime3;
    mode.dwTime4 = mTime4;
    mode.dwTime5 = mTime5;

    ALOGD("android_server_DNAManagerService_WriteTimes: %d %d %d %d %d", mTime1, mTime2, mTime3, mTime4, mTime5);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static void android_server_DNAManagerService_WriteTime1(JNIEnv* env, jobject clazz, jint wTime1)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mTime1 = wTime1;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwTime1 = mTime1;

    ALOGD("android_server_DNAManagerService_WriteTime1: %d", mTime1);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jint android_server_DNAManagerService_ReadTime2(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwTime2;

    ALOGD("android_server_DNAManagerService_ReadTime2: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteTime2(JNIEnv* env, jobject clazz, jint wTime2)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mTime2 = wTime2;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwTime2 = mTime2;

    ALOGD("android_server_DNAManagerService_WriteTime2: %d", mTime2);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jint android_server_DNAManagerService_ReadTime3(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwTime3;

    ALOGD("android_server_DNAManagerService_ReadTime3: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteTime3(JNIEnv* env, jobject clazz, jint wTime3)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mTime3 = wTime3;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwTime3 = mTime3;

    ALOGD("android_server_DNAManagerService_WriteTime3: %d", mTime3);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jint android_server_DNAManagerService_ReadTime4(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwTime4;

    ALOGD("android_server_DNAManagerService_ReadTime4: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteTime4(JNIEnv* env, jobject clazz, jint wTime4)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mTime4 = wTime4;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwTime4 = mTime4;

    ALOGD("android_server_DNAManagerService_WriteTime4: %d", mTime4);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

static jint android_server_DNAManagerService_ReadTime5(JNIEnv* env, jobject clazz)
{
    int ret = 0;
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return NULL;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    ret = mode.dwTime5;

    ALOGD("android_server_DNAManagerService_ReadTime5: %d", ret);

    close(nand_fd);

    return ret;
}

static void android_server_DNAManagerService_WriteTime5(JNIEnv* env, jobject clazz, jint wTime5)
{
    int nand_fd;
    int readcnt = 0;
    int writecnt = 0;
    struct dna_info hd_info;
    FINE_MODE mode;
    unsigned int mTime5 = wTime5;

    nand_fd = open(DNA_DEVICE_NAME, O_RDWR);
    if(nand_fd < 0) {
        ALOGE("%s open error(%d)", DNA_DEVICE_NAME, nand_fd);
        return;
    }

    hd_info.SeekAddress1 = FINE_MODE_ADDR;
    hd_info.DataBuffer = (unsigned char *)&mode;
    hd_info.DataBufferSize = sizeof(FINE_MODE);

    memset(hd_info.DataBuffer, '\0', hd_info.DataBufferSize);
    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    readcnt = read(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    mode.dwTime5 = mTime5;

    ALOGD("android_server_DNAManagerService_WriteTime5: %d", mTime5);

    lseek(nand_fd, hd_info.SeekAddress1, SEEK_SET);
    writecnt = write(nand_fd, hd_info.DataBuffer, hd_info.DataBufferSize);

    close(nand_fd);
}

//----------------------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "nativeChmod",  "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",    (void*) android_server_DNAManagerService_Chmod },
    { "nativeChown",  "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",    (void*) android_server_DNAManagerService_Chown },
    { "nativePrintf",                      "(Ljava/lang/String;)I",                   (void*) android_server_DNAManagerService_Printf },

    { "nativeWatchDogDisable",             "()I",                                     (void*) android_server_DNAManagerService_WatchDogDisable },
    { "nativeReadCurrPercentage",          "()I",                                     (void*) android_server_DNAManagerService_ReadCurrPercentage },

    { "nativeRead4DCalData",               "(II)[B",                                  (void*) android_server_DNAManagerService_Read4DCalData },

    { "nativeReadImage",                   "(II)[B",                                  (void*) android_server_DNAManagerService_ReadImage },

    { "nativeReadImage",                   "(II)[B",                                  (void*) android_server_DNAManagerService_ReadImage },
    { "nativeReadPark",                    "(II)[B",                                  (void*) android_server_DNAManagerService_ReadPark },

    { "nativeReadArchitecture",            "()C",                                     (void*) android_server_DNAManagerService_ReadArchitecture },
    { "nativeReadProcessorType",           "()C",                                     (void*) android_server_DNAManagerService_ReadProcessorType },
    { "nativeReadProcessorCompiler",       "()C",                                     (void*) android_server_DNAManagerService_ReadProcessorCompiler },

    { "nativeReadosVerBuildDate",          "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadosVerBuildDate },
    { "nativeReadbootVerBuildDate",        "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadbootVerBuildDate },
    { "nativeReadpcbVerModelId",           "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadpcbVerModelId },
    { "nativeReadpcbVerPlatformId",        "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadpcbVerPlatformId },
    { "nativeReadpcbVerPCBVersion",        "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadpcbVerPCBVersion },

    { "nativeReadLocked",                  "()I",                                     (void*) android_server_DNAManagerService_ReadLocked },
    { "nativeReadNumLocked",               "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadNumLocked },
    { "nativeReadtime",                    "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_Readtime },

    { "nativeReadBootCount",               "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadBootCount },
    { "nativeReadAutoRecoveryScratch1",    "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadAutoRecoveryScratch1 },
    { "nativeReadAutoRecoveryScratch2",    "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadAutoRecoveryScratch2 },
    { "nativeReadAuthSDCheckScratch1",     "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadAuthSDCheckScratch1 },
    { "nativeReadAuthSDCheckScratch2",     "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadAuthSDCheckScratch2 },

    { "nativeReadUUID",                    "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadUUID },

    { "nativeReadTPEG_KEY",                "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadTPEG_KEY },

    { "nativeReadRearcamX",                "()J",                                     (void*) android_server_DNAManagerService_ReadRearcamX },
    { "nativeReadRearcamY",                "()J",                                     (void*) android_server_DNAManagerService_ReadRearcamY },
    { "nativeReadWheelAngle",              "()J",                                     (void*) android_server_DNAManagerService_ReadWheelAngle },
    { "nativeReadCamAngle",                "()J",                                     (void*) android_server_DNAManagerService_ReadCamAngle },
    { "nativeReadCamHeight",               "()J",                                     (void*) android_server_DNAManagerService_ReadCamHeight },
    { "nativeReadParklineDisable",         "()J",                                     (void*) android_server_DNAManagerService_ReadParklineDisable },
    { "nativeReadWheelParklineDisable",    "()J",                                     (void*) android_server_DNAManagerService_ReadWheelParklineDisable },
    { "nativeReadFrontCameraOnOff",        "()J",                                     (void*) android_server_DNAManagerService_ReadFrontCameraOnOff },
    { "nativeReadCameraAutoControlOnOff",  "()J",                                     (void*) android_server_DNAManagerService_ReadCameraAutoControlOnOff },
    { "nativeReadRearcamDisable",          "()J",                                     (void*) android_server_DNAManagerService_ReadRearcamDisable },

    { "nativeReadBlackBoxOnlyMode",        "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadBlackBoxOnlyMode },
    { "nativeReadDRAngle",                 "()I",                                     (void*) android_server_DNAManagerService_ReadDRAngle },
    { "nativeReadCalibrationCheck",        "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadCalibrationCheck },
    { "nativeReadTvoutEnable",             "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadTvoutEnable },
    { "nativeReadDrFault",                 "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadDrFault },

    { "nativeReadMode",                    "()I",                                     (void*) android_server_DNAManagerService_ReadMode },
    { "nativeReadCurrent",                 "()I",                                     (void*) android_server_DNAManagerService_ReadCurrent },
    { "nativeReadPercentage",              "()I",                                     (void*) android_server_DNAManagerService_ReadPercentage },
    { "nativeFDTHistoryRead",              "()I",                                     (void*) android_server_DNAManagerService_FDTHistoryRead },
    { "nativeReadBootCounts",              "()I",                                     (void*) android_server_DNAManagerService_ReadBootCounts },
    { "nativeReadOSCurrent",               "()I",                                     (void*) android_server_DNAManagerService_ReadOSCurrent },
    { "nativeReadUpdate",                  "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadUpdate },
    { "nativeReadVersion",                 "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadVersion },
    { "nativeReadRecovery",                "()Ljava/lang/String;",                    (void*) android_server_DNAManagerService_ReadRecovery },
    { "nativeReadModeUPDATEUSB",           "()I",                                     (void*) android_server_DNAManagerService_ReadModeUPDATEUSB },
    { "nativeReadUpdateCase",              "()I",                                     (void*) android_server_DNAManagerService_ReadUpdateCase },
    { "nativeReadWaitProgress",            "()I",                                     (void*) android_server_DNAManagerService_ReadWaitProgress },
    { "nativeReadTime1",                   "()I",                                     (void*) android_server_DNAManagerService_ReadTime1 },
    { "nativeReadTime2",                   "()I",                                     (void*) android_server_DNAManagerService_ReadTime2 },
    { "nativeReadTime3",                   "()I",                                     (void*) android_server_DNAManagerService_ReadTime3 },
    { "nativeReadTime4",                   "()I",                                     (void*) android_server_DNAManagerService_ReadTime4 },
    { "nativeReadTime5",                   "()I",                                     (void*) android_server_DNAManagerService_ReadTime5 },

//----------------------------------------------------------------------------------------

    { "nativeSETIMG",                      "(I)V",                                    (void*) android_server_DNAManagerService_SETIMG },
    { "nativeWriteUSTS",                   "(I)V",                                    (void*) android_server_DNAManagerService_WriteUSTS },
    { "nativeAudioShutdown",               "()V",                                     (void*) android_server_DNAManagerService_AudioShutdown },

    { "nativeWriteSplash",                 "(Ljava/lang/String;Ljava/lang/String;)I", (void*) android_server_DNAManagerService_WriteSplash },
    { "nativeCleanSplash",                 "()I",                                     (void*) android_server_DNAManagerService_CleanSplash },

    { "nativeWrite4DCalData",              "([BII)V",                                 (void*) android_server_DNAManagerService_Write4DCalData },

    { "nativeWriteImage",                  "([BII)V",                                 (void*) android_server_DNAManagerService_WriteImage },
    { "nativeWritePark",                   "([BII)V",                                 (void*) android_server_DNAManagerService_WritePark },

    { "nativeWriteArchitecture",           "(C)V",                                    (void*) android_server_DNAManagerService_WriteArchitecture },
    { "nativeWriteProcessorType",          "(C)V",                                    (void*) android_server_DNAManagerService_WriteProcessorType },
    { "nativeWriteProcessorCompiler",      "(C)V",                                    (void*) android_server_DNAManagerService_WriteProcessorCompiler },

    { "nativeWriteosVerBuildDate",         "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteosVerBuildDate },
    { "nativeWritebootVerBuildDate",       "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WritebootVerBuildDate },
    { "nativeWritepcbVerModelId",          "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WritepcbVerModelId },
    { "nativeWritepcbVerPlatformId",       "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WritepcbVerPlatformId },
    { "nativeWritepcbVerPCBVersion",       "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WritepcbVerPCBVersion },

    { "nativeWriteLocked",                 "(I)V",                                    (void*) android_server_DNAManagerService_WriteLocked },
    { "nativeWriteNumLocked",              "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteNumLocked },
    { "nativeWritetime",                   "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_Writetime },

    { "nativeWriteBootCount",              "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteBootCount },
    { "nativeWriteAutoRecoveryScratch1",   "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteAutoRecoveryScratch1 },
    { "nativeWriteAutoRecoveryScratch2",   "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteAutoRecoveryScratch2 },
    { "nativeWriteAuthSDCheckScratch1",    "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteAuthSDCheckScratch1 },
    { "nativeWriteAuthSDCheckScratch2",    "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteAuthSDCheckScratch2 },

    { "nativeWriteUUID",                   "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteUUID },

    { "nativeWriteTPEG_KEY",               "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteTPEG_KEY },

    { "nativeWriteRearcamX",               "(J)V",                                    (void*) android_server_DNAManagerService_WriteRearcamX },
    { "nativeWriteRearcamY",               "(J)V",                                    (void*) android_server_DNAManagerService_WriteRearcamY },
    { "nativeWriteWheelAngle",             "(J)V",                                    (void*) android_server_DNAManagerService_WriteWheelAngle },
    { "nativeWriteCamAngle",               "(J)V",                                    (void*) android_server_DNAManagerService_WriteCamAngle },
    { "nativeWriteCamHeight",              "(J)V",                                    (void*) android_server_DNAManagerService_WriteCamHeight },
    { "nativeWriteParklineDisable",        "(J)V",                                    (void*) android_server_DNAManagerService_WriteParklineDisable },
    { "nativeWriteWheelParklineDisable",   "(J)V",                                    (void*) android_server_DNAManagerService_WriteWheelParklineDisable },
    { "nativeWriteFrontCameraOnOff",       "(J)V",                                    (void*) android_server_DNAManagerService_WriteFrontCameraOnOff },
    { "nativeWriteCameraAutoControlOnOff", "(J)V",                                    (void*) android_server_DNAManagerService_WriteCameraAutoControlOnOff },
    { "nativeWriteRearcamDisable",         "(J)V",                                    (void*) android_server_DNAManagerService_WriteRearcamDisable },

    { "nativeWriteBlackBoxOnlyMode",       "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteBlackBoxOnlyMode },
    { "nativeWriteDRAngle",                "(I)V",                                    (void*) android_server_DNAManagerService_WriteDRAngle },
    { "nativeWriteCalibrationCheck",       "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteCalibrationCheck },
    { "nativeWriteTvoutEnable",            "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteTvoutEnable },
    { "nativeWriteDrFault",                "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteDrFault },

    { "nativeWriteMode",                   "(I)V",                                    (void*) android_server_DNAManagerService_WriteMode },
    { "nativeWriteCurrent",                "(I)V",                                    (void*) android_server_DNAManagerService_WriteCurrent },
    { "nativeWritePercentage",             "(I)V",                                    (void*) android_server_DNAManagerService_WritePercentage },
    { "nativeFDTHistoryWrite",             "(I)V",                                    (void*) android_server_DNAManagerService_FDTHistoryWrite },
    { "nativeWriteBootCounts",             "(I)V",                                    (void*) android_server_DNAManagerService_WriteBootCounts },
    { "nativeWriteOSCurrent",              "(I)V",                                    (void*) android_server_DNAManagerService_WriteOSCurrent },
    { "nativeWriteUpdate",                 "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteUpdate },
    { "nativeWriteVersion",                "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteVersion },
    { "nativeWriteRecovery",               "(Ljava/lang/String;)V",                   (void*) android_server_DNAManagerService_WriteRecovery },
    { "nativeWriteModeUPDATEUSB",          "(I)V",                                    (void*) android_server_DNAManagerService_WriteModeUPDATEUSB },
    { "nativeWriteUpdateCase",             "(I)V",                                    (void*) android_server_DNAManagerService_WriteUpdateCase },
    { "nativeWriteWaitProgress",           "(I)V",                                    (void*) android_server_DNAManagerService_WriteWaitProgress },
    { "nativeWriteTimes",                  "(IIIII)V",                                (void*) android_server_DNAManagerService_WriteTimes },
    { "nativeWriteTime1",                  "(I)V",                                    (void*) android_server_DNAManagerService_WriteTime1 },
    { "nativeWriteTime2",                  "(I)V",                                    (void*) android_server_DNAManagerService_WriteTime2 },
    { "nativeWriteTime3",                  "(I)V",                                    (void*) android_server_DNAManagerService_WriteTime3 },
    { "nativeWriteTime4",                  "(I)V",                                    (void*) android_server_DNAManagerService_WriteTime4 },
    { "nativeWriteTime5",                  "(I)V",                                    (void*) android_server_DNAManagerService_WriteTime5 },
    { "_DES64_Encode",                  "([B[B)Z",                                    (void*) android_server_DNAManagerService_DES64_Encode },
    { "_DES64_Decode",                  "([B[B)Z",                                    (void*) android_server_DNAManagerService_DES64_Decode },
};

int register_android_server_DNAManagerService(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/DNAManagerService",
            gMethods, NELEM(gMethods));
}

}; // namespace android


