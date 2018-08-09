package com.wuyr.pathview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

/**
 * Created by wuyr on 17-12-15 下午8:08.
 * GitHub: https://github.com/wuyr/PathView
 */

public class PathView extends View {

    private static final String TAG = "PathView";

    @IntDef({MODE_TRAIN, MODE_AIRPLANE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Mode {
    }

    public static final int MODE_AIRPLANE = 0;
    public static final int MODE_TRAIN = 1;

    private Semaphore mLightLineSemaphore, mDarkLineSemaphore;
    private Keyframes mKeyframes;
    private int mMode;
    private float[] mLightPoints;
    private float[] mDarkPoints;
    private int mLightLineColor;
    private int mDarkLineColor;
    private ValueAnimator mProgressAnimator, mAlphaAnimator;
    private long mAnimationDuration;
    private Paint mPaint;
    private boolean isRepeat;
    private int mAlpha;
    private OnAnimationEndListener mOnAnimationEndListener;
    private volatile boolean isStopped;

    public PathView(Context context) {
        this(context, null);
    }

    public PathView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PathView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        //初始化画笔
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);

        //默认动画时长
        mAnimationDuration = 1000L;

        //默认颜色
        mLightLineColor = Color.parseColor("#F17F94");
        mDarkLineColor = Color.parseColor("#D8D5D7");

        mLightLineSemaphore = new Semaphore(1);
        mDarkLineSemaphore = new Semaphore(1);

    }

    /**
     * 设置条线动画模式
     *
     * @param mode {@link #MODE_AIRPLANE} 一开始不显示灰色线条，粉红色线条走过后才留下灰色线条,
     *             {@link #MODE_TRAIN} 一开始就显示灰色线条，并且一直显示，直到动画结束。
     */
    public PathView setMode(@Mode int mode) {
        if ((mAlphaAnimator != null && mAlphaAnimator.isRunning())
                || (mProgressAnimator != null && mProgressAnimator.isRunning())) {
            Log.e(TAG, "Failed! Animation has been started!", new IllegalStateException());
            return this;
        }
        mMode = mode;
        return this;
    }

    /**
     * 设置Path
     */
    public PathView setPath(Path path) {
        mKeyframes = new Keyframes(path);
        mAlpha = 0;
        return this;
    }

    /**
     * 设置动画时长
     */
    public PathView setDuration(long duration) {
        mAnimationDuration = (duration);
        return this;
    }

    /**
     * 开始播放动画
     */
    public synchronized void start() {
        setDarkLineProgress(mMode == MODE_TRAIN ? 1 : 0, 0);
        if (mAlphaAnimator != null && mAlphaAnimator.isRunning()) {
            mAlphaAnimator.cancel();
        }
        if (mProgressAnimator != null && mProgressAnimator.isRunning()) {
            mProgressAnimator.cancel();
        }
        mAlphaAnimator = ValueAnimator.ofInt(0, 255).setDuration((long) (mAnimationDuration * .2F));// 时长是总时长的20%
        mAlphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAlpha = (int) animation.getAnimatedValue();
                invalidate();
            }
        });
        mAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startUpdateProgress();
            }
        });
        mAlphaAnimator.start();
    }

    /**
     * 停止动画
     */
    public synchronized void stop() {
        isStopped = true;
        mDarkPoints = null;
        if (mDarkLineSemaphore != null) {
            try {
                mDarkLineSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mDarkLineSemaphore.release();
            mDarkLineSemaphore = null;
        }
        mLightPoints = null;
        if (mLightLineSemaphore != null) {
            try {
                mLightLineSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mLightLineSemaphore.release();
            mLightLineSemaphore = null;
        }
        if (mAlphaAnimator != null && mAlphaAnimator.isRunning()) {
            mAlphaAnimator.cancel();
            mAlphaAnimator = null;
        }
        if (mProgressAnimator != null && mProgressAnimator.isRunning()) {
            mProgressAnimator.cancel();
            mProgressAnimator = null;
        }
        if (mKeyframes != null) {
            mKeyframes.release();
            mKeyframes = null;
        }
        mOnAnimationEndListener = null;
    }

    /**
     * 设置重复播放动画
     *
     * @param isRepeat 是否重复
     */
    public void setRepeat(boolean isRepeat) {
        this.isRepeat = isRepeat;
    }

    /**
     * 设置线宽
     *
     * @param width 线条宽度 (单位: px)
     */
    public void setLineWidth(float width) {
        mPaint.setStrokeWidth(width);
    }

    /**
     * 设置高亮线条颜色
     */
    public void setLightLineColor(@ColorInt int color) {
        mLightLineColor = color;
    }

    /**
     * 设置暗色线条颜色
     */
    public void setDarkLineColor(@ColorInt int color) {
        mDarkLineColor = color;
    }

    /**
     * 设置高亮线条颜色
     */
    public void setLightLineColorRes(@ColorRes int color) {
        mLightLineColor = getResources().getColor(color);
    }

    /**
     * 设置暗色线条颜色
     */
    public void setDarkLineColorRes(@ColorRes int color) {
        mDarkLineColor = getResources().getColor(color);
    }

    /**
     * 设置动画播放完毕监听器
     */
    public void setOnAnimationEndListener(OnAnimationEndListener listener) {
        mOnAnimationEndListener = listener;
    }

    public interface OnAnimationEndListener {
        void onAnimationEnd();
    }

    private void startUpdateProgress() {
        mAlphaAnimator = null;
//        底部灰色线条向后加长到原Path的60%
        mProgressAnimator = ValueAnimator.ofFloat(-.6F, 1).setDuration(mAnimationDuration);
        mProgressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float currentProgress = (float) animation.getAnimatedValue();
                float lightLineStartProgress,//粉色线头
                        lightLineEndProgress;//粉色线尾
                float darkLineStartProgress,//灰色线头
                        darkLineEndProgress;//灰色线尾

                darkLineEndProgress = currentProgress;

//                粉色线头从0开始，并且初始速度是灰色线尾的2.5倍
                darkLineStartProgress = lightLineStartProgress = (.6F + currentProgress) * 2.5F;

//                粉色线尾从-0.25开始，速度跟灰色线尾速度一样
                lightLineEndProgress = .35F + currentProgress;

//                粉色线尾走到30%时，速度变为原来速度的2.5倍
                if (lightLineEndProgress > .3F) {
                    lightLineEndProgress = (.35F + currentProgress - .3F) * 2.5F + .3F;
                }

//                当粉色线头走到65%时，速度变为原来速度的0.35倍
                if (darkLineStartProgress > .65F) {
                    darkLineStartProgress = lightLineStartProgress = ((.6F + currentProgress) * 2.5F - .65F) * .35F + .65F;
                }
                if (lightLineEndProgress < 0) {
                    lightLineEndProgress = 0;
                }
                if (darkLineEndProgress < 0) {
                    darkLineEndProgress = 0;
                }

//                当粉色线尾走到90%时，播放透明渐变动画
                if (lightLineEndProgress > .9F) {
                    if (mAlphaAnimator == null) {
                        mAlphaAnimator = ValueAnimator.ofInt(255, 0).setDuration((long) (mAnimationDuration * .3F));// 时长是总时长的30%
                        mAlphaAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                mAlpha = (int) animation.getAnimatedValue();
                            }
                        });
                        mAlphaAnimator.start();
                    }
                }
                if (lightLineStartProgress > 1) {
                    darkLineStartProgress = lightLineStartProgress = 1;
                }

                setLightLineProgress(lightLineStartProgress, lightLineEndProgress);

//                飞机模式才更新灰色线条
                if (mMode == MODE_AIRPLANE) {
                    setDarkLineProgress(darkLineStartProgress, darkLineEndProgress);
                }
                invalidate();
            }
        });
        mProgressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                invalidate();
                if (mOnAnimationEndListener != null) {
                    mOnAnimationEndListener.onAnimationEnd();
                }
                if (isRepeat) {
                    start();
                }
            }
        });
        mProgressAnimator.start();
    }

    private void setLightLineProgress(float start, float end) {
        updateLineProgress(start, end, true);
    }

    private void setDarkLineProgress(float start, float end) {
        updateLineProgress(start, end, false);
    }

    private void updateLineProgress(float start, float end, boolean isLightPoints) {
        if (isStopped) {
            return;
        }
        if (mKeyframes == null) {
            throw new IllegalStateException("path not set yet!");
        }
        if (isLightPoints) {
            try {
                mLightLineSemaphore.acquire();
            } catch (Exception e) {
                return;
            }
            mLightPoints = mKeyframes.getRangeValue(start, end);
            mLightLineSemaphore.release();
        } else {
            try {
                mDarkLineSemaphore.acquire();
            } catch (Exception e) {
                return;
            }
            mDarkPoints = mKeyframes.getRangeValue(start, end);
            mDarkLineSemaphore.release();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        try {
            mDarkLineSemaphore.acquire();
        } catch (Exception e) {
            return;
        }
        if (mDarkPoints != null) {
            mPaint.setColor(mDarkLineColor);
            mPaint.setAlpha(mAlpha);
            canvas.drawPoints(mDarkPoints, mPaint);
        }
        mDarkLineSemaphore.release();
        try {
            mLightLineSemaphore.acquire();
        } catch (Exception e) {
            return;
        }
        if (mLightPoints != null) {
            mPaint.setColor(mLightLineColor);
            mPaint.setAlpha(mAlpha);
            canvas.drawPoints(mLightPoints, mPaint);
        }
        mLightLineSemaphore.release();
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    private static class Keyframes {

        static final float PRECISION = 1f; //精度我们用1就够了 (数值越少 numPoints 就越大)
        int numPoints;
        float[] mData;

        Keyframes(Path path) {
            init(path);
        }

        void init(Path path) {
            final PathMeasure pathMeasure = new PathMeasure(path, false);
            final float pathLength = pathMeasure.getLength();
            numPoints = (int) (pathLength / PRECISION) + 1;
            mData = new float[numPoints * 2];
            final float[] position = new float[2];
            int index = 0;
            for (int i = 0; i < numPoints; ++i) {
                final float distance = (i * pathLength) / (numPoints - 1);
                pathMeasure.getPosTan(distance, position, null);
                mData[index] = position[0];
                mData[index + 1] = position[1];
                index += 2;
            }
            numPoints = mData.length;
        }

        /**
         * 拿到start和end之间的x,y数据
         *
         * @param start 开始百分比
         * @param end   结束百分比
         * @return 裁剪后的数据
         */
        float[] getRangeValue(float start, float end) {
            int startIndex = (int) (numPoints * start);
            int endIndex = (int) (numPoints * end);

            //必须是偶数，因为需要float[]{x,y}这样x和y要配对的
            if (startIndex % 2 != 0) {
                //直接减，不用担心 < 0  因为0是偶数，哈哈
                --startIndex;
            }
            if (endIndex % 2 != 0) {
                //不用检查越界
                ++endIndex;
            }
            //根据起止点裁剪
            return startIndex > endIndex ? Arrays.copyOfRange(mData, endIndex, startIndex) : null;
        }

        void release() {
            mData = null;
        }
    }
}
