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

package com.android.systemui;

import java.io.FileInputStream;

import com.android.internal.os.ProcessCpuTracker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.StrictMode;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;

public class MEMORYUsageService extends Service {
    private View mView;

    private static final class Stats extends ProcessCpuTracker {
        String mLoadText = "";
        int mLoadWidth;

        private final Paint mPaint;

        byte[] mBuffer = new byte[1024];
        private long mTotalSize;
        private long mFreeSize;
        private long mCachedSize;

        private boolean matchText(byte[] buffer, int index, String text) {
            int N = text.length();
            if ((index+N) >= buffer.length) {
                return false;
            }
            for (int i=0; i<N; i++) {
                if (buffer[index+i] != text.charAt(i)) {
                    return false;
                }
            }
            return true;
        }
        private long extractMemValue(byte[] buffer, int index) {
            while (index < buffer.length && buffer[index] != '\n') {
                if (buffer[index] >= '0' && buffer[index] <= '9') {
                    int start = index;
                    index++;
                    while (index < buffer.length && buffer[index] >= '0'
                        && buffer[index] <= '9') {
                        index++;
                    }
                    String str = new String(buffer, 0, start, index-start);
                    return ((long)Integer.parseInt(str)) * 1024;
                }
                index++;
            }
            return 0;
        }
        public void readMemInfo() {
            // Permit disk reads here, as /proc/meminfo isn't really "on
            // disk" and should be fast.  TODO: make BlockGuard ignore
            // /proc/ and /sys/ files perhaps?
            StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();
            try {
                mTotalSize = 0;
                mFreeSize = 0;
                mCachedSize = 0;
                FileInputStream is = new FileInputStream("/proc/meminfo");
                int len = is.read(mBuffer);
                is.close();
                final int BUFLEN = mBuffer.length;
                int count = 0;
                for (int i=0; i<len && count < 3; i++) {
                    if (matchText(mBuffer, i, "MemTotal")) {
                        i += 8;
                        mTotalSize = extractMemValue(mBuffer, i);
                        count++;
                    } else if (matchText(mBuffer, i, "MemFree")) {
                        i += 7;
                        mFreeSize = extractMemValue(mBuffer, i);
                        count++;
                    } else if (matchText(mBuffer, i, "Cached")) {
                        i += 6;
                        mCachedSize = extractMemValue(mBuffer, i);
                        count++;
                    }
                    while (i < BUFLEN && mBuffer[i] != '\n') {
                        i++;
                    }
                }
            } catch (java.io.FileNotFoundException e) {
            } catch (java.io.IOException e) {
            } finally {
                StrictMode.setThreadPolicy(savedPolicy);
            }
        }
        public long getTotalSize() {
            return mTotalSize;
        }
        public long getFreeSize() {
            return mFreeSize;
        }
        public long getCachedSize() {
            return mCachedSize;
        }
        public void updates() {
            int totalMegs = (int)(getTotalSize() / 1048576L);
            int availableMegs = (int)((getFreeSize() + getCachedSize()) / 1048576L);
            int useMegs = (int)((getTotalSize() - (getFreeSize() + getCachedSize())) / 1048576L);
            float mPercentage = ((float)availableMegs * 100) / totalMegs;
            String result_Percentage = String.format("%3.2f", mPercentage);

            mLoadText = "Available : " + availableMegs + " MBytes / Total : " + totalMegs + " MBytes (" + result_Percentage + "%)";
            mLoadWidth = (int)mPaint.measureText(mLoadText);
        }

        Stats(Paint paint) {
            super(false);
            mPaint = paint;
        }

        @Override
        public void onLoadChanged(float load1, float load5, float load15) {

        }

        @Override
        public int onMeasureProcessName(String name) {
            return (int)mPaint.measureText(name);
        }
    }

    private class LoadView extends View {
        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 1) {
                    mStats.readMemInfo();
                    mStats.updates();
                    mStats.update();
                    updateDisplay();
                    Message m = obtainMessage(1);
                    sendMessageDelayed(m, 2000);
                }
            }
        };

        private final Stats mStats;

        private Paint mLoadPaint;
        private Paint mAddedPaint;
        private Paint mRemovedPaint;
        private Paint mShadowPaint;
        private Paint mShadow2Paint;
        private float mAscent;
        private int mFH;

        private int mNeededWidth;
        private int mNeededHeight;

        LoadView(Context c) {
            super(c);

            setPadding(4, 4, 4, 4);
            //setBackgroundResource(com.android.internal.R.drawable.load_average_background);

            // Need to scale text size by density...  but we won't do it
            // linearly, because with higher dps it is nice to squeeze the
            // text a bit to fit more of it.  And with lower dps, trying to
            // go much smaller will result in unreadable text.
            int textSize = 20;

            mLoadPaint = new Paint();
            mLoadPaint.setAntiAlias(true);
            mLoadPaint.setTextSize(textSize);
            mLoadPaint.setARGB(255, 255, 255, 255);

            mAddedPaint = new Paint();
            mAddedPaint.setAntiAlias(true);
            mAddedPaint.setTextSize(textSize);
            mAddedPaint.setARGB(255, 128, 255, 128);

            mRemovedPaint = new Paint();
            mRemovedPaint.setAntiAlias(true);
            mRemovedPaint.setStrikeThruText(true);
            mRemovedPaint.setTextSize(textSize);
            mRemovedPaint.setARGB(255, 255, 128, 128);

            mShadowPaint = new Paint();
            mShadowPaint.setAntiAlias(true);
            mShadowPaint.setTextSize(textSize);
            //mShadowPaint.setFakeBoldText(true);
            mShadowPaint.setARGB(192, 0, 0, 0);
            mLoadPaint.setShadowLayer(4, 0, 0, 0xff000000);

            mShadow2Paint = new Paint();
            mShadow2Paint.setAntiAlias(true);
            mShadow2Paint.setTextSize(textSize);
            //mShadow2Paint.setFakeBoldText(true);
            mShadow2Paint.setARGB(192, 0, 0, 0);
            mLoadPaint.setShadowLayer(2, 0, 0, 0xff000000);

            mAscent = mLoadPaint.ascent();
            float descent = mLoadPaint.descent();
            mFH = (int)(descent - mAscent + .5f);

            mStats = new Stats(mLoadPaint);
            mStats.init();
            updateDisplay();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mHandler.sendEmptyMessage(1);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mHandler.removeMessages(1);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(mNeededWidth, widthMeasureSpec),
                    resolveSize(mNeededHeight, heightMeasureSpec));
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int W = mNeededWidth;
            final int RIGHT = getWidth()-1;

            final Stats stats = mStats;
            final int userTime = stats.getLastUserTime();
            final int systemTime = stats.getLastSystemTime();
            final int iowaitTime = stats.getLastIoWaitTime();
            final int irqTime = stats.getLastIrqTime();
            final int softIrqTime = stats.getLastSoftIrqTime();
            final int idleTime = stats.getLastIdleTime();

            final int totalTime = userTime+systemTime+iowaitTime+irqTime+softIrqTime+idleTime;
            if (totalTime == 0) {
                return;
            }

            int x = RIGHT - mPaddingRight;
            int top = mPaddingTop + 2;
            int bottom = mPaddingTop + mFH - 2;

            int y = mPaddingTop - (int)mAscent + 30;
            canvas.drawText(stats.mLoadText, RIGHT-mPaddingRight-stats.mLoadWidth-1,
                    y-1, mShadowPaint);
            canvas.drawText(stats.mLoadText, RIGHT-mPaddingRight-stats.mLoadWidth-1,
                    y+1, mShadowPaint);
            canvas.drawText(stats.mLoadText, RIGHT-mPaddingRight-stats.mLoadWidth+1,
                    y-1, mShadow2Paint);
            canvas.drawText(stats.mLoadText, RIGHT-mPaddingRight-stats.mLoadWidth+1,
                    y+1, mShadow2Paint);
            canvas.drawText(stats.mLoadText, RIGHT-mPaddingRight-stats.mLoadWidth,
                    y, mLoadPaint);
        }

        void updateDisplay() {
            final Stats stats = mStats;
            final int NW = stats.countWorkingStats();

            int maxWidth = stats.mLoadWidth;
            for (int i=0; i<NW; i++) {
                Stats.Stats st = stats.getWorkingStats(i);
                if (st.nameWidth > maxWidth) {
                    maxWidth = st.nameWidth;
                }
            }

            int neededWidth = mPaddingLeft + mPaddingRight + maxWidth;
            int neededHeight = mPaddingTop + mPaddingBottom + (mFH*(1+NW));
            if (neededWidth != mNeededWidth || neededHeight != mNeededHeight) {
                mNeededWidth = neededWidth;
                mNeededHeight = neededHeight;
                requestLayout();
            } else {
                invalidate();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mView = new LoadView(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        params.setTitle("MEMORYUsageService");
        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        wm.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(mView);
        mView = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
