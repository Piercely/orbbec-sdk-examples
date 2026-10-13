package com.orbbec.orbbecsdkexamples.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;

import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.StreamType;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

public class OBGLView extends GLSurfaceView {
    private static final String TAG = "OBGLView";

    private OBRender mOBRender;
    private Handler mHandler;

    public OBGLView(Context context) {
        super(context);
        init();
    }

    public OBGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        mOBRender = new OBRender(this);
        setRenderer(mOBRender);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 渲染实际数据帧
     *
     * @param w      宽
     * @param h      高
     * @param type   流的类型{@link StreamType#COLOR}/{@link StreamType#DEPTH}/{@link StreamType#IR}
     * @param format 帧的格式{@link Format}
     * @param data   数据
     */
    public void update(int w, int h, StreamType type, Format format, byte[] data, float scale) {
        if (checkViewSize(w, h) && null != mOBRender) {
            mOBRender.update(w, h, type, format, data, scale);
        }
    }

    /**
     * 渲染实际数据帧
     *
     * @param w      宽
     * @param h      高
     * @param type   流的类型{@link StreamType#COLOR}/{@link StreamType#DEPTH}/{@link StreamType#IR}
     * @param format 帧的格式{@link Format}
     * @param buffer 数据
     */
    public void update(int w, int h, StreamType type, Format format, ByteBuffer buffer, float scale) {
        if (checkViewSize(w, h) && null != mOBRender) {
            mOBRender.update(w, h, type, format, buffer, scale);
        }
    }

    /**
     * 清屏
     */
    public void clearWindow() {
        if (null != mOBRender) {
            mOBRender.clearWindow();
        }
    }

    /**
     * 检查view的宽高比是否和渲染帧一致
     *
     * @param frameW 帧的宽
     * @param frameH 帧的高
     * @return true:等比 false:不等比
     */
    private boolean checkViewSize(int frameW, int frameH) {
        int viewW = this.getWidth();
        int viewH = this.getHeight();
        float viewScale = 0;
        if (0 != viewW && 0 != viewH) {
            viewScale = new BigDecimal(viewW * 1.0f / viewH).setScale(1, BigDecimal.ROUND_HALF_UP).floatValue();
        }
        float frameScale = new BigDecimal(frameW * 1.0f / frameH).setScale(1, BigDecimal.ROUND_HALF_UP).floatValue();
        if (viewScale != frameScale) {
            Log.i(TAG, "checkViewSize: view size:" + viewW + "x" + viewH + " view scale:" + viewScale
                    + " frameSize:" + frameW + "x" + frameH + " frameScale:" + frameScale);
            if (null != mHandler) {
                mHandler.post(() -> updateGLSurfaceViewSize(frameW, frameH));
            }
            return false;
        }
        return true;
    }

    /**
     * 根据帧的分辨率实时更新gl view窗口的大小，确保等比例渲染
     *
     * @param frameW 帧的宽
     * @param frameH 帧的高
     */
    private void updateGLSurfaceViewSize(int frameW, int frameH) {
        ViewGroup parent = (ViewGroup) this.getParent();
        if (null == parent || parent.getWidth() <= 0 || parent.getHeight() <= 0
                || frameH <= 0 || frameW <= 0) {
            Log.w(TAG, "update ob gl view size failed. parent: " + parent + "(" + parent.getWidth()
                    + ", " + parent.getHeight() + "), Frame:" + frameW + "x" + frameH);
            return;
        }

        // 宽度一致，高度比例拉伸
        int w1 = parent.getWidth();
        int h1 = parent.getWidth() * frameH / frameW;

        // 高度一致，宽度比例拉伸
        int h2 = parent.getHeight();
        int w2 = parent.getHeight() * frameW / frameH;

        int targetWidth, targetHeight;
        if (h1 <= parent.getHeight()) {
            targetWidth = w1;
            targetHeight = h1;
        } else {
            targetWidth = w2;
            targetHeight = h2;
        }

        ViewGroup.LayoutParams params = this.getLayoutParams();
        params.width = targetWidth;
        params.height = targetHeight;
        this.setLayoutParams(params);
    }
}
