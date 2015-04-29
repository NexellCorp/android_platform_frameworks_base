/*
 * Author:  <dw.choi@e-roum.com>
 * Description: DisplayControl JNI for IQ9000
*/

#include <stdio.h>
#include <utils/misc.h>
#include <linux/videodev2.h>
#include <cutils/log.h>
#include <cutils/atomic.h>
#include <stdlib.h>
#include "android_runtime/AndroidRuntime.h"
#include "jni.h"
#include "JNIHelp.h"
#include <fcntl.h>
#include <linux/ioctl.h>
#include <unistd.h>
#include <binder/IMemory.h>
#include <utils/Log.h>
#include <errno.h>
#include <cutils/log.h>
#include <cutils/atomic.h>
#include <stdlib.h>

#include <cutils/log.h>
#include <cutils/memory.h>
#include <cutils/misc.h>
#include <cutils/properties.h>

#include <errno.h>
#include <poll.h>
#include <hardware_legacy/uevent.h>

#define LOG_TAG "USBPowerControlJNI"

//#define USB_POWER_DEV_NAME		"/dev/fineusbhostpower"

//IOCTLs
#define IOCTL_USB_HOST_POWER_ONOFF				0x10
#define IOCTL_USB_HOST_POWER_ONOFF_GET			0x11

#ifdef __cplusplus
 extern "C" {
#endif
extern int init_module(void *, unsigned long, const char *);
extern int delete_module(const char *, unsigned int);


#define EHCI_MODULE_FILE_NAME		"/system/lib/modules/ehci-hcd.ko"
#define EHCI_MODULE_NAME			"ehci_hcd"
#define OHCI_MODULE_FILE_NAME		"/system/lib/modules/ohci-hcd.ko"
#define OHCI_MODULE_NAME			"ohci_hcd"
#define XHCI_MODULE_FILE_NAME		"lib/modules/xhci-hcd.ko"
#define XHCI_MODULE_NAME			"xhci_hcd"

static const char MODULE_FILE[]         = "/proc/modules";


static int insmod(const char *filename, const char *args)
{
    void *module;
    unsigned int size;
    int ret;

    module = load_file(filename, &size);
    if (!module)
        return -1;

    ret = init_module(module, size, args);

    free(module);

    return ret;
}

static int rmmod(const char *modname)
{
    int ret = -1;
    int maxtry = 10;

    while (maxtry-- > 0) {
        ret = delete_module(modname, O_NONBLOCK | O_EXCL);
        if (ret < 0 && errno == EAGAIN)
            usleep(500000);
        else
            break;
    }

    if (ret != 0)
        ALOGD("Unable to unload driver module \"%s\": %s\n",
             modname, strerror(errno));
    return ret;
}

static int is_module_loaded(const char *modname)
{
    FILE *proc;
    char line[255];

    /*
     * If the property says the driver is loaded, check to
     * make sure that the property setting isn't just left
     * over from a previous manual shutdown or a runtime
     * crash.
     */
    if ((proc = fopen(MODULE_FILE,"r")) == NULL) {
        ALOGE("[%s] Could not open %s: %s",__FUNCTION__,MODULE_FILE,strerror(errno));
        return 0;
    }
    while ((fgets(line,sizeof(line),proc)) != NULL) {
        if (strncmp(line,modname,strlen(modname)) == 0) {
            fclose(proc);
            return 1;
        }
    }
    fclose(proc);

    return 0;
}

#ifdef __cplusplus
}
#endif

#define POWER_CONTROL_GPIO_NUM      15

static int s_gpio_fd = -1;
static int InitGpio(int num)
{
    static bool inited = false;
    if (!inited) {
#if 0
        int fd = open("/sys/class/gpio/export", O_WRONLY);
        if (fd < 0) {
            ALOGE("can't open file %s, error %s", "/sys/class/gpio/export", strerror(errno));
            return -EINVAL;
        }
        char buf[64] = {0, };
        int len = snprintf(buf, sizeof(buf), "%d", num);
        int write_len = write(fd, buf, len);
        if (write_len < 0) {
             ALOGE("failed to write %s", buf);
             close(fd);
             return -EINVAL;
        }

        len = snprintf(buf, sizeof(buf), "/sys/class/gpio/gpio%d/direction", num);
        fd = open(buf, O_WRONLY);
        if (fd < 0) {
        }
#else
        char buf[64] = {0, };
#endif
        int len = snprintf(buf, sizeof(buf), "/sys/class/gpio/gpio%d/value", num);
        s_gpio_fd = open(buf, O_RDWR);
        if (s_gpio_fd < 0) {
             ALOGE("failed to %s, error %s", buf, strerror(errno));
             return -EINVAL;
        }
        inited = true;
    }

    return 0;
}

int USBPowerSet(int onoff)
{
    InitGpio(POWER_CONTROL_GPIO_NUM);
	ALOGE("[USBPowerSet] onoff : %d",onoff);
    char buf[2] = {0, };
    if (onoff > 0)
        buf[0] = '1';
    else
        buf[0] = '0';

    write(s_gpio_fd, buf, sizeof(buf));

	return 0;
}

int USBPowerGet(void)
{
    InitGpio(POWER_CONTROL_GPIO_NUM);
    int is_on;

    char buf[2] = {0, };
    read(s_gpio_fd, buf, sizeof(buf));
    if (buf[0] == '1')
        return 1;
    return 0;
}

namespace android
{

//JNI functions
static jint JNI_USBPowerSet(JNIEnv *env, jobject thiz,jint onoff)
{
	ALOGE("[JNI_USBPowerSet] onoff : %d",onoff);
	USBPowerSet(onoff);
    return 0;
}

static jint JNI_USBPowerGet(JNIEnv *env, jobject thiz)
{
	int onoff;

	onoff = USBPowerGet();

	ALOGE("[JNI_DisplayGet] onoff  : %d",onoff);

    return onoff;
}

#if 0
int insert_module(char *module_file_name)
{
	int ret;

	if((ret = insmod(module_file_name,"")) <0) {
		ALOGE("[%s] insmod failed(%d)",__FUNCTION__,ret);
		return ret;
	}

	return 0;
}

int remove_module(char *module_name)
{
	int ret;

	if ((ret = rmmod(module_name)) == 0) {
		int count = 20; /* wait at most 10 seconds for completion */

		while (count-- > 0) {
			if (!is_module_loaded(module_name))
				break;
				usleep(500000);
		}
		usleep(500000); /* allow card removal */

		if (count) {
			return 0;
		}
		ALOGE("[%s] rmmod success but, module unload failed in 10 secs",__FUNCTION__);
		return -1;

	} else {
		ALOGE("[%s] rmmod failed(%d)",__FUNCTION__,ret);
		return ret;
	}

}
#endif

static jint JNI_USBHostEnableSet(JNIEnv *env, jobject thiz,jint enable)
{
#if 0
	int ret;
	ALOGD("[%s] enable : %d",__FUNCTION__,enable);

	if(enable) {
		insert_module(EHCI_MODULE_FILE_NAME);
		insert_module(OHCI_MODULE_FILE_NAME);
	} else {
		remove_module(OHCI_MODULE_NAME);
		remove_module(EHCI_MODULE_NAME);
	}
    return 0;
#else
    return 0;
#endif
}

static jint JNI_USBHostEnableGet(JNIEnv *env, jobject thiz)
{
#if 0
	int enable;

	enable = is_module_loaded(EHCI_MODULE_NAME);

	ALOGD("[%s] enable  : %d",__FUNCTION__,enable);

    return enable;
#else
    return 1;
#endif
}

static jint JNI_USBXHCIHostEnableSet(JNIEnv *env, jobject thiz,jint enable)
{
#if 0
	int ret;
	ALOGD("[%s] enable : %d",__FUNCTION__,enable);

	if(enable) {
		insert_module(XHCI_MODULE_FILE_NAME);
	} else {
		remove_module(XHCI_MODULE_NAME);
	}
    return 0;
#else
    return 0;
#endif
}

#define ADD_STR "add@/devices/platform/nxp-ehci/usb1/1-1"
#define REMOVE_STR "remove@/devices/platform/nxp-ehci/usb1/1-1"
#define LAST_STR_PORT1 "1-1.1"
#define LAST_STR_PORT2 "1-1.2"

struct USBPortStatus {
    int attached[2];
    int category[2];
};

static struct USBPortStatus s_port_status;

#define PORT0_IDPRODUCT_FILE    "/sys/bus/usb/devices/1-1/1-1.1/idProduct"
#define PORT1_IDPRODUCT_FILE    "/sys/bus/usb/devices/1-1/1-1.2/idProduct"
#define WIFI_PRODUCT_ID         "8176"
#define BT_PRODUCT_ID           "0001"
#define CATEGORY_WIFI           0
#define CATEGORY_BT             1

//#define USB_SYS_FILE            "/sys/bus/usb/devices/1-1"
#define USB_SYS_FILE            "/sys/devices/platform/nxp-ehci"

static void initUSBPortStatus(void)
{
    struct USBPortStatus *pstatus = &s_port_status;
    memset(pstatus, 0, sizeof(struct USBPortStatus));
    pstatus->category[0] = -1;
    pstatus->category[1] = -1;
}

static void getUSBPortStatus(struct USBPortStatus *pstatus)
{
    memset(pstatus, 0, sizeof(struct USBPortStatus));
    char *idFile = PORT0_IDPRODUCT_FILE;
    if (!access(idFile, R_OK)) {
        pstatus->attached[0] = 1;
        int fd = open(idFile, O_RDONLY);
        if (fd < 0) {
            //ALOGE("can't open %s", PORT0_IDPRODUCT_FILE);
            return;
        }
        char buf[64] = {0, };
        int ret = read(fd, buf, sizeof(buf));
        if (ret <= 0) {
             ALOGE("can't read %s, ret %d", idFile, ret);
             close(fd);
             return;
        }
        close(fd);
        if (!strncmp(buf, WIFI_PRODUCT_ID, strlen(WIFI_PRODUCT_ID))) {
            pstatus->category[0] = CATEGORY_WIFI; // WIFI
        } else if (!strncmp(buf, BT_PRODUCT_ID, strlen(BT_PRODUCT_ID))) {
            pstatus->category[0] = CATEGORY_BT; // WIFI
        } else {
            ALOGE("unknown product id: %s", buf);
            pstatus->category[0] = -1; // unknown
        }
    } else {
        //ALOGD("can't access %s", idFile);
        pstatus->attached[0] = 0;
        pstatus->category[0] = -1;
    }

    idFile = PORT1_IDPRODUCT_FILE;
    if (!access(idFile, R_OK)) {
        pstatus->attached[1] = 1;
        int fd = open(idFile, O_RDONLY);
        if (fd < 0) {
            //ALOGE("can't open %s", PORT0_IDPRODUCT_FILE);
            return;
        }
        char buf[64] = {0, };
        int ret = read(fd, buf, sizeof(buf));
        if (ret <= 0) {
             ALOGE("can't read %s, ret %d", idFile, ret);
             close(fd);
             return;
        }
        close(fd);
        if (!strncmp(buf, WIFI_PRODUCT_ID, strlen(WIFI_PRODUCT_ID))) {
            pstatus->category[1] = CATEGORY_WIFI; // WIFI
        } else if (!strncmp(buf, BT_PRODUCT_ID, strlen(BT_PRODUCT_ID))) {
            pstatus->category[1] = CATEGORY_BT; // WIFI
        } else {
            ALOGE("unknown product id: %s", buf);
            pstatus->category[1] = -1; // unknown
        }
    } else {
        //ALOGD("can't access %s", idFile);
        pstatus->attached[1] = 0;
        pstatus->category[1] = -1;
    }

    //ALOGD("port1 status: attached %d, category %d", pstatus->attached[0], pstatus->category[0]);
    //ALOGD("port2 status: attached %d, category %d", pstatus->attached[1], pstatus->category[1]);
}

static char *get_last_str(char *str)
{
    char *token = NULL;
    char *prev_token = NULL;
    char delim[] = "/";
    token = strtok(str, delim);
    while (token != NULL) {
        prev_token = token;
        token = strtok(NULL, delim);
    }

    return prev_token;
}

static int getChanged(struct USBPortStatus *oldStatus, struct USBPortStatus *newStatus, int *attach)
{
    if (oldStatus->attached[0] == newStatus->attached[0] &&
        oldStatus->attached[1] == newStatus->attached[1] &&
        oldStatus->category[0] == newStatus->category[0] &&
        oldStatus->category[1] == newStatus->category[1])
        return -1;

    if (oldStatus->attached[0] != newStatus->attached[0]) {
        if (newStatus->attached[0]) {
            *attach = 1;
            return newStatus->category[0];
        } else {
            *attach = 0;
            return oldStatus->category[0];
        }
    }

    if (oldStatus->attached[1] != newStatus->attached[1]) {
        if (newStatus->attached[1]) {
            *attach = 1;
            return newStatus->category[1];
        } else {
            *attach = 0;
            return oldStatus->category[1];
        }
    }

    return -1;
}

static void syncStatus(struct USBPortStatus *oldStatus, struct USBPortStatus *newStatus)
{
     memcpy(oldStatus, newStatus, sizeof(struct USBPortStatus));
}

static jint JNI_getUSBStatusChangedEvent(JNIEnv *env, jobject thiz, jintArray info)
{
    char uevent_desc[4096] = {0, };
    uevent_init();

    struct pollfd pollfd;
    pollfd.fd = uevent_get_fd();
    pollfd.events = POLLIN;


    while (true) {
        int ret = poll(&pollfd, 1, -1);
        if (ret > 0) {
            if (pollfd.revents & POLLIN) {
                int len = uevent_next_event(uevent_desc, sizeof(uevent_desc) - 2);
                //ALOGD("uevent: %s", uevent_desc);
                if (!strncmp(uevent_desc, ADD_STR, strlen(ADD_STR)) ||
                        !strncmp(uevent_desc, REMOVE_STR, strlen(REMOVE_STR))) {
                    char *last_str = get_last_str(uevent_desc);
                    if (last_str) {
                        //ALOGD("last_str: %s", last_str);
                        if (!strncmp(last_str, LAST_STR_PORT1, strlen(LAST_STR_PORT1)) ||
                                !strncmp(last_str, LAST_STR_PORT2, strlen(LAST_STR_PORT2)))
                            break;
                    }
                }
            }
        } else if (ret == -1) {
             if (errno == EINTR)
                 return -1;
        }
    }

    struct USBPortStatus status;
    getUSBPortStatus(&status);
    int attach = 0;
    int changed = getChanged(&s_port_status, &status, &attach);
    if (changed != -1) {
        int size = env->GetArrayLength(info);
        if(size != 2) {
            ALOGE("Invalid argument size : %d(expect 2)\r\n", size);
            return -2;
        }
        jint *int_buf = env->GetIntArrayElements(info, NULL);
        int_buf[0] = changed;
        int_buf[1] = attach;
        env->ReleaseIntArrayElements(info, int_buf, 0);
        syncStatus(&s_port_status, &status);
        return 0;
    }

    return -1;
}

static JNINativeMethod methods[] = {
    { "_USBPowerSet", "(I)I", (int*)JNI_USBPowerSet },
    { "_USBPowerGet", "()I", (int*)JNI_USBPowerGet },
    { "_USBHostEnableSet", "(I)I", (int*)JNI_USBHostEnableSet },
    { "_USBHostEnableGet", "()I", (int*)JNI_USBHostEnableGet },
    { "_USBXHCIHostEnableSet", "(I)I", (int*)JNI_USBXHCIHostEnableSet },
    { "_getUSBStatusChangedEvent", "([I)I", (int*)JNI_getUSBStatusChangedEvent },
};

/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

int register_android_server_USBPowerControlServer(JNIEnv *env)
{
    jclass clazz = env->FindClass("com/android/server/USBPowerControlService");
    if (clazz == NULL) {
        ALOGE("Can't find com/android/server/USBPowerControlService");
        return -1;
    }

    initUSBPortStatus();

    return jniRegisterNativeMethods(env, "com/android/server/USBPowerControlService",
            methods, sizeof(methods) / sizeof(methods[0]));
}

}
