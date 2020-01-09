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

#define LOG_NDEBUG 0
#define LOG_TAG "BootAnimation"

#include <stdint.h>
#include <sys/inotify.h>
#include <sys/poll.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <math.h>
#include <fcntl.h>
#include <utils/misc.h>
#include <signal.h>
#include <time.h>

#include <cutils/properties.h>

#include <androidfw/AssetManager.h>
#include <binder/IPCThreadState.h>
#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/Log.h>

#include <ui/PixelFormat.h>
#include <ui/Rect.h>
#include <ui/Region.h>
#include <ui/DisplayInfo.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

// TODO: Fix Skia.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#include <SkBitmap.h>
#include <SkStream.h>
#include <SkImageDecoder.h>
#pragma GCC diagnostic pop

#include <GLES/gl.h>
#include <GLES/glext.h>
#include <EGL/eglext.h>

#include "NxBootAnimation.h"
#include "audioplay.h"

#include <linux/input.h>
#define DEFAULT_TOUCH_DEVICE "/dev/input/event0"

#include <android/input.h>

namespace android {

static const char OEM_BOOTANIMATION_FILE[] = "/oem/media/bootanimation.zip";
static const char SYSTEM_BOOTANIMATION_FILE[] = "/system/media/bootanimation.zip";
static const char SYSTEM_ENCRYPTED_BOOTANIMATION_FILE[] = "/system/media/bootanimation-encrypted.zip";
static const char SYSTEM_DATA_DIR_PATH[] = "/data/system";
static const char SYSTEM_TIME_DIR_NAME[] = "time";
static const char SYSTEM_TIME_DIR_PATH[] = "/data/system/time";
static const char LAST_TIME_CHANGED_FILE_NAME[] = "last_time_change";
static const char LAST_TIME_CHANGED_FILE_PATH[] = "/data/system/time/last_time_change";
static const char ACCURATE_TIME_FLAG_FILE_NAME[] = "time_is_accurate";
static const char ACCURATE_TIME_FLAG_FILE_PATH[] = "/data/system/time/time_is_accurate";
static const char TIME_FORMAT_12_HOUR_FLAG_FILE_PATH[] = "/data/system/time/time_format_12_hour";
// Java timestamp format. Don't show the clock if the date is before 2000-01-01 00:00:00.
static const long long ACCURATE_TIME_EPOCH = 946684800000;
static constexpr char FONT_BEGIN_CHAR = ' ';
static constexpr char FONT_END_CHAR = '~' + 1;
static constexpr size_t FONT_NUM_CHARS = FONT_END_CHAR - FONT_BEGIN_CHAR + 1;
static constexpr size_t FONT_NUM_COLS = 16;
static constexpr size_t FONT_NUM_ROWS = FONT_NUM_CHARS / FONT_NUM_COLS;
static const int TEXT_CENTER_VALUE = INT_MAX;
static const int TEXT_MISSING_VALUE = INT_MIN;
static const char EXIT_PROP_NAME[] = "service.bootanim.exit";
static const char PLAY_SOUND_PROP_NAME[] = "persist.sys.bootanim.play_sound";
static const char BOOT_COMPLETED_PROP_NAME[] = "sys.boot_completed";
static const char BOOTREASON_PROP_NAME[] = "ro.boot.bootreason";
// bootreasons list in "system/core/bootstat/bootstat.cpp".
static const std::vector<std::string> PLAY_SOUND_BOOTREASON_BLACKLIST {
  "kernel_panic",
  "Panic",
  "Watchdog",
};
#if 0
static float g_positions[] = {
    -0.25f, -0.7f, 0.0f,
    0.25f, -0.7f, 0.0f,
    -0.25f, -0.9f, 0.0f,
    0.25f, -0.9f, 0.0f,
};
#endif
// ---------------------------------------------------------------------------

BootAnimation::BootAnimation() : Thread(false), mClockEnabled(true), mTimeIsAccurate(false),
        mTimeFormat12Hour(false), mTimeCheckThread(NULL) {
    mSession = new SurfaceComposerClient();

    // If the system has already booted, the animation is not being used for a boot.
    mSystemBoot = !property_get_bool(BOOT_COMPLETED_PROP_NAME, 0);

    mExit = false;
    mButtonState = 2;

    pthread_create(&m_hInputThread, NULL, BootAnimation::ThreadStub, (void *)this);
}

BootAnimation::~BootAnimation() {
    mInputThreadRun = false;
}

void BootAnimation::onFirstRef() {
    status_t err = mSession->linkToComposerDeath(this);
    ALOGE_IF(err, "linkToComposerDeath failed (%s) ", strerror(-err));
    if (err == NO_ERROR) {
        run("BootAnimation", PRIORITY_DISPLAY);
    }
}

sp<SurfaceComposerClient> BootAnimation::session() const {
    return mSession;
}


void BootAnimation::binderDied(const wp<IBinder>&)
{
    // woah, surfaceflinger died!
    ALOGD("SurfaceFlinger died, exiting...");

    // calling requestExit() is not enough here because the Surface code
    // might be blocked on a condition variable that will never be updated.
    kill( getpid(), SIGKILL );
    requestExit();
    audioplay::destroy();
}

status_t BootAnimation::initTexture(Texture* texture, AssetManager& assets,
        const char* name) {
    Asset* asset = assets.open(name, Asset::ACCESS_BUFFER);
    if (asset == NULL)
        return NO_INIT;
    SkBitmap bitmap;
    SkImageDecoder::DecodeMemory(asset->getBuffer(false), asset->getLength(),
            &bitmap, kUnknown_SkColorType, SkImageDecoder::kDecodePixels_Mode);
    asset->close();
    delete asset;

    // ensure we can call getPixels(). No need to call unlock, since the
    // bitmap will go out of scope when we return from this method.
    bitmap.lockPixels();

    const int w = bitmap.width();
    const int h = bitmap.height();
    const void* p = bitmap.getPixels();

    GLint crop[4] = { 0, h, w, -h };
    texture->w = w;
    texture->h = h;

    glGenTextures(1, &texture->name);
    glBindTexture(GL_TEXTURE_2D, texture->name);

    switch (bitmap.colorType()) {
        case kAlpha_8_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, w, h, 0, GL_ALPHA,
                    GL_UNSIGNED_BYTE, p);
            break;
        case kARGB_4444_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_SHORT_4_4_4_4, p);
            break;
        case kN32_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                    GL_UNSIGNED_BYTE, p);
            break;
        case kRGB_565_SkColorType:
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                    GL_UNSIGNED_SHORT_5_6_5, p);
            break;
        default:
            break;
    }

    glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

    return NO_ERROR;
}

status_t BootAnimation::initTexture(FileMap* map, int* width, int* height)
{
    SkBitmap bitmap;
    SkMemoryStream  stream(map->getDataPtr(), map->getDataLength());
    SkImageDecoder* codec = SkImageDecoder::Factory(&stream);
    if (codec != NULL) {
        codec->setDitherImage(false);
        codec->decode(&stream, &bitmap,
                kN32_SkColorType,
                SkImageDecoder::kDecodePixels_Mode);
        delete codec;
    }

    // FileMap memory is never released until application exit.
    // Release it now as the texture is already loaded and the memory used for
    // the packed resource can be released.
    delete map;

    // ensure we can call getPixels(). No need to call unlock, since the
    // bitmap will go out of scope when we return from this method.
    bitmap.lockPixels();

    const int w = bitmap.width();
    const int h = bitmap.height();
    const void* p = bitmap.getPixels();

    GLint crop[4] = { 0, h, w, -h };
    int tw = 1 << (31 - __builtin_clz(w));
    int th = 1 << (31 - __builtin_clz(h));
    if (tw < w) tw <<= 1;
    if (th < h) th <<= 1;

    switch (bitmap.colorType()) {
        case kN32_SkColorType:
            if (!mUseNpotTextures && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, 0);
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, p);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                        GL_UNSIGNED_BYTE, p);
            }
            break;

        case kRGB_565_SkColorType:
            if (!mUseNpotTextures && (tw != w || th != h)) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, tw, th, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, 0);
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, 0, w, h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, p);
            } else {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB,
                        GL_UNSIGNED_SHORT_5_6_5, p);
            }
            break;
        default:
            break;
    }

    glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);

    *width = w;
    *height = h;

    return NO_ERROR;
}

status_t BootAnimation::readyToRun() {
    mAssets.addDefaultAssets();

    sp<IBinder> dtoken(SurfaceComposerClient::getBuiltInDisplay(
            ISurfaceComposer::eDisplayIdMain));
    DisplayInfo dinfo;
    status_t status = SurfaceComposerClient::getDisplayInfo(dtoken, &dinfo);
    if (status)
        return -1;

    // create the native surface
    sp<SurfaceControl> control = session()->createSurface(String8("BootAnimation"),
            dinfo.w, dinfo.h, PIXEL_FORMAT_RGB_565);

    SurfaceComposerClient::openGlobalTransaction();
    control->setLayer(0x40000000);
    SurfaceComposerClient::closeGlobalTransaction();

    sp<Surface> s = control->getSurface();

    // initialize opengl and egl
    const EGLint attribs[] = {
            EGL_RED_SIZE,   8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE,  8,
            EGL_DEPTH_SIZE, 0,
            EGL_NONE
    };
    EGLint w, h;
    EGLint numConfigs;
    EGLConfig config;
    EGLSurface surface;
    EGLContext context;

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);

    eglInitialize(display, 0, 0);
    eglChooseConfig(display, attribs, &config, 1, &numConfigs);
    surface = eglCreateWindowSurface(display, config, s.get(), NULL);
    context = eglCreateContext(display, config, NULL, NULL);
    eglQuerySurface(display, surface, EGL_WIDTH, &w);
    eglQuerySurface(display, surface, EGL_HEIGHT, &h);

    if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE)
        return NO_INIT;

    mDisplay = display;
    mContext = context;
    mSurface = surface;
    mWidth = w;
    mHeight = h;
    mFlingerSurfaceControl = control;
    mFlingerSurface = s;

    // If the device has encryption turned on or is in process
    // of being encrypted we show the encrypted boot animation.
    char decrypt[PROPERTY_VALUE_MAX];
    property_get("vold.decrypt", decrypt, "");

    bool encryptedAnimation = atoi(decrypt) != 0 || !strcmp("trigger_restart_min_framework", decrypt);

    if (encryptedAnimation && (access(SYSTEM_ENCRYPTED_BOOTANIMATION_FILE, R_OK) == 0)) {
        mZipFileName = SYSTEM_ENCRYPTED_BOOTANIMATION_FILE;
    }
    else if (access(OEM_BOOTANIMATION_FILE, R_OK) == 0) {
        mZipFileName = OEM_BOOTANIMATION_FILE;
    }
    else if (access(SYSTEM_BOOTANIMATION_FILE, R_OK) == 0) {
        mZipFileName = SYSTEM_BOOTANIMATION_FILE;
    }
    return NO_ERROR;
}

bool BootAnimation::threadLoop()
{
    bool r;
    r = android();

    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(mDisplay, mContext);
    eglDestroySurface(mDisplay, mSurface);
    mFlingerSurface.clear();
    mFlingerSurfaceControl.clear();
    eglTerminate(mDisplay);
    IPCThreadState::self()->stopProcess();
    return r;
}

bool BootAnimation::android()
{
    int pending = 0;

    initTexture(&mAndroid[0], mAssets, "images/avn_agree_cn.png");
    initTexture(&mButton[0], mAssets, "images/avn_agree_button_normal.png");
    initTexture(&mButton[1], mAssets, "images/avn_agree_button_pressed.png");
    initTexture(&mButton[2], mAssets, "images/avn_agree_button_disabled.png");

    mButtonRect.x1 = mWidth/2-mButton[mButtonState].w/2;
    mButtonRect.x2 = mButtonRect.x1 + mButton[mButtonState].w;
    mButtonRect.y1 = mHeight*0.9 - mButton[mButtonState].h/2;
    mButtonRect.y2 = mButtonRect.y1 + mButton[mButtonState].h;

    // clear screen
    glShadeModel(GL_FLAT);
    glDisable(GL_DITHER);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(1.0,0,0,1);
    glClear(GL_COLOR_BUFFER_BIT);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    printf("mWidthxmHeight (%dx%d), mAndroid( %dx%d )\n", mWidth, mHeight, mAndroid[0].w, mAndroid[0].h);

    do {
        nsecs_t now = systemTime();

        glClear(GL_COLOR_BUFFER_BIT);
	    glEnable(GL_TEXTURE_2D);
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);

        glBindTexture(GL_TEXTURE_2D, mAndroid[0].name);
        glDrawTexiOES( 0, 0, 0, mAndroid[0].w, mAndroid[0].h);

        glBindTexture(GL_TEXTURE_2D, mButton[mButtonState].name);
	    glDrawTexiOES(mWidth/2-mButton[mButtonState].w/2, mHeight*0.1-mButton[mButtonState].h/2, 0, mButton[mButtonState].w, mButton[mButtonState].h);

	    EGLBoolean res = eglSwapBuffers(mDisplay, mSurface);
        if (res == EGL_FALSE)
            break;

        // 12fps: don't animate too fast to preserve CPU
        const nsecs_t sleepTime = 83333 - ns2us(systemTime() - now);
        if (sleepTime > 0)
            usleep(sleepTime);
        
	checkExit();

        if (mExit) {
            pending += ns2us(systemTime() - now);
            
            if (pending >= 5000000) {
                requestExit();
            }
        }
    } while (!exitPending());

    glDeleteTextures(1, &mAndroid[0].name);
    glDeleteTextures(1, &mButton[0].name);
    glDeleteTextures(1, &mButton[1].name);
    glDeleteTextures(1, &mButton[2].name);
    return false;
}

void BootAnimation::checkExit() {
    // Allow surface flinger to gracefully request shutdown
    char value[PROPERTY_VALUE_MAX];
    property_get(EXIT_PROP_NAME, value, "0");
    int exitnow = atoi(value);
    if (exitnow) {
        if (!mExit) {
            mExit = true;
            mButtonState = 0;
        }
    }
}

bool parseTextCoord(const char* str, int* dest) {
    if (strcmp("c", str) == 0) {
        *dest = TEXT_CENTER_VALUE;
        return true;
    }

    char* end;
    int val = (int) strtol(str, &end, 0);
    if (end == str || *end != '\0' || val == INT_MAX || val == INT_MIN) {
        return false;
    }
    *dest = val;
    return true;
}

// Parse two position coordinates. If only string is non-empty, treat it as the y value.
void parsePosition(const char* str1, const char* str2, int* x, int* y) {
    bool success = false;
    if (strlen(str1) == 0) {  // No values were specified
        // success = false
    } else if (strlen(str2) == 0) {  // we have only one value
        if (parseTextCoord(str1, y)) {
            *x = TEXT_CENTER_VALUE;
            success = true;
        }
    } else {
        if (parseTextCoord(str1, x) && parseTextCoord(str2, y)) {
            success = true;
        }
    }

    if (!success) {
        *x = TEXT_MISSING_VALUE;
        *y = TEXT_MISSING_VALUE;
    }
}


// The font image should be a 96x2 array of character images.  The
// columns are the printable ASCII characters 0x20 - 0x7f.  The
// top row is regular text; the bottom row is bold.
status_t BootAnimation::initFont(Font* font, const char* fallback) {
    status_t status = NO_ERROR;

    if (font->map != nullptr) {
        glGenTextures(1, &font->texture.name);
        glBindTexture(GL_TEXTURE_2D, font->texture.name);

        status = initTexture(font->map, &font->texture.w, &font->texture.h);

        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    } else if (fallback != nullptr) {
        status = initTexture(&font->texture, mAssets, fallback);
    } else {
        return NO_INIT;
    }

    if (status == NO_ERROR) {
        font->char_width = font->texture.w / FONT_NUM_COLS;
        font->char_height = font->texture.h / FONT_NUM_ROWS / 2;  // There are bold and regular rows
    }

    return status;
}


bool BootAnimation::playSoundsAllowed() const {
    // Only play sounds for system boots, not runtime restarts.
    if (!mSystemBoot) {
        return false;
    }

    // Read the system property to see if we should play the sound.
    // If it's not present, default to allowed.
    if (!property_get_bool(PLAY_SOUND_PROP_NAME, 1)) {
        return false;
    }

    // Don't play sounds if this is a reboot due to an error.
    char bootreason[PROPERTY_VALUE_MAX];
    if (property_get(BOOTREASON_PROP_NAME, bootreason, nullptr) > 0) {
        for (const auto& str : PLAY_SOUND_BOOTREASON_BLACKLIST) {
            if (strcasecmp(str.c_str(), bootreason) == 0) {
                return false;
            }
        }
    }
    return true;
}

bool BootAnimation::updateIsTimeAccurate() {
    static constexpr long long MAX_TIME_IN_PAST =   60000LL * 60LL * 24LL * 30LL;  // 30 days
    static constexpr long long MAX_TIME_IN_FUTURE = 60000LL * 90LL;  // 90 minutes

    if (mTimeIsAccurate) {
        return true;
    }

    struct stat statResult;

    if(stat(TIME_FORMAT_12_HOUR_FLAG_FILE_PATH, &statResult) == 0) {
        mTimeFormat12Hour = true;
    }

    if(stat(ACCURATE_TIME_FLAG_FILE_PATH, &statResult) == 0) {
        mTimeIsAccurate = true;
        return true;
    }

    FILE* file = fopen(LAST_TIME_CHANGED_FILE_PATH, "r");
    if (file != NULL) {
      long long lastChangedTime = 0;
      fscanf(file, "%lld", &lastChangedTime);
      fclose(file);
      if (lastChangedTime > 0) {
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        // Match the Java timestamp format
        long long rtcNow = (now.tv_sec * 1000LL) + (now.tv_nsec / 1000000LL);
        if (ACCURATE_TIME_EPOCH < rtcNow
            && lastChangedTime > (rtcNow - MAX_TIME_IN_PAST)
            && lastChangedTime < (rtcNow + MAX_TIME_IN_FUTURE)) {
            mTimeIsAccurate = true;
        }
      }
    }

    return mTimeIsAccurate;
}

BootAnimation::TimeCheckThread::TimeCheckThread(BootAnimation* bootAnimation) : Thread(false),
    mInotifyFd(-1), mSystemWd(-1), mTimeWd(-1), mBootAnimation(bootAnimation) {}

BootAnimation::TimeCheckThread::~TimeCheckThread() {
    // mInotifyFd may be -1 but that's ok since we're not at risk of attempting to close a valid FD.
    close(mInotifyFd);
}

bool BootAnimation::TimeCheckThread::threadLoop() {
    bool shouldLoop = doThreadLoop() && !mBootAnimation->mTimeIsAccurate
        && mBootAnimation->mClockEnabled;
    if (!shouldLoop) {
        close(mInotifyFd);
        mInotifyFd = -1;
    }
    return shouldLoop;
}

bool BootAnimation::TimeCheckThread::doThreadLoop() {
    static constexpr int BUFF_LEN (10 * (sizeof(struct inotify_event) + NAME_MAX + 1));

    // Poll instead of doing a blocking read so the Thread can exit if requested.
    struct pollfd pfd = { mInotifyFd, POLLIN, 0 };
    ssize_t pollResult = poll(&pfd, 1, 1000);

    if (pollResult == 0) {
        return true;
    } else if (pollResult < 0) {
        ALOGE("Could not poll inotify events");
        return false;
    }

    char buff[BUFF_LEN] __attribute__ ((aligned(__alignof__(struct inotify_event))));;
    ssize_t length = read(mInotifyFd, buff, BUFF_LEN);
    if (length == 0) {
        return true;
    } else if (length < 0) {
        ALOGE("Could not read inotify events");
        return false;
    }

    const struct inotify_event *event;
    for (char* ptr = buff; ptr < buff + length; ptr += sizeof(struct inotify_event) + event->len) {
        event = (const struct inotify_event *) ptr;
        if (event->wd == mSystemWd && strcmp(SYSTEM_TIME_DIR_NAME, event->name) == 0) {
            addTimeDirWatch();
        } else if (event->wd == mTimeWd && (strcmp(LAST_TIME_CHANGED_FILE_NAME, event->name) == 0
                || strcmp(ACCURATE_TIME_FLAG_FILE_NAME, event->name) == 0)) {
            return !mBootAnimation->updateIsTimeAccurate();
        }
    }

    return true;
}

void BootAnimation::TimeCheckThread::addTimeDirWatch() {
        mTimeWd = inotify_add_watch(mInotifyFd, SYSTEM_TIME_DIR_PATH,
                IN_CLOSE_WRITE | IN_MOVED_TO | IN_ATTRIB);
        if (mTimeWd > 0) {
            // No need to watch for the time directory to be created if it already exists
            inotify_rm_watch(mInotifyFd, mSystemWd);
            mSystemWd = -1;
        }
}

status_t BootAnimation::TimeCheckThread::readyToRun() {
    mInotifyFd = inotify_init();
    if (mInotifyFd < 0) {
        ALOGE("Could not initialize inotify fd");
        return NO_INIT;
    }

    mSystemWd = inotify_add_watch(mInotifyFd, SYSTEM_DATA_DIR_PATH, IN_CREATE | IN_ATTRIB);
    if (mSystemWd < 0) {
        close(mInotifyFd);
        mInotifyFd = -1;
        ALOGE("Could not add watch for %s", SYSTEM_DATA_DIR_PATH);
        return NO_INIT;
    }

    addTimeDirWatch();

    if (mBootAnimation->updateIsTimeAccurate()) {
        close(mInotifyFd);
        mInotifyFd = -1;
        return ALREADY_EXISTS;
    }

    return NO_ERROR;
}

// ---------------------------------------------------------------------------

void *BootAnimation::ThreadStub(void *pObj)
{
    if (pObj) {
        BootAnimation *pInstance = (BootAnimation *)pObj;
        pInstance->ThreadProc();
        pthread_join(pInstance->m_hInputThread, NULL);
    }
    return NULL;
}

void BootAnimation::ThreadProc()
{
    struct input_event sInputEvent[64];
    char *pcDevName = const_cast<char *>(DEFAULT_TOUCH_DEVICE);
    int fd = open(pcDevName, O_RDONLY);
    int cnt = 0;
    int pressed = 0;
    int state = 0;
    int x = 0;
    int y = 0;
    mInputThreadRun = true;
    

    if (fd < 0) {
        printf("open failed : %s\n", pcDevName);
        return;
    }

    while (mInputThreadRun) {
        cnt = read(fd, &sInputEvent, sizeof(struct input_event)*64);
        if (cnt < 0) {
             usleep(1000);
             continue;
        }

        if (!mExit) {
            usleep(1000);
            continue;
        }

        for (int i = 0; i < 64; ++i) {
            if (sInputEvent[i].type == EV_ABS || sInputEvent[i].type == EV_KEY) {
                if (sInputEvent[i].code == ABS_MT_POSITION_X) {
                    x = ((float)sInputEvent[i].value / 4096) * 1920;
                } else if (sInputEvent[i].code == ABS_MT_POSITION_Y) {
                    y = ((float)sInputEvent[i].value / 4096) * 720;
                } else if (sInputEvent[i].code == ABS_MT_TRACKING_ID) {
                    if (sInputEvent[i].value >= 0) {
                        state = 1;
                    } else {
                        state = 0;
                    }
                }
            }
        }

        if (x >= mButtonRect.x1 && x < mButtonRect.x2 && y >= mButtonRect.y1 && y < mButtonRect.y2) {
            if (state && !pressed) {
                pressed = 1;
                mButtonState = 1;
            } else if (!state && pressed) {
                pressed = 0;
                mButtonState = 0;
                mInputThreadRun = false;
                requestExit();
            }
        } else {
            state = 0;
            mButtonState = 0;
        }
    }

    close(fd);
}

}
; // namespace android
