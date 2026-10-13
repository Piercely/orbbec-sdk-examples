package com.orbbec.orbbecsdkexamples.utils;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    public static Bitmap createBitmap(byte[] data, int width, int height) {
        int[] colors = rgbToArgb(data);
        if (colors == null) {
            return null;
        }
        Bitmap bmp = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
        return bmp;
    }

    private static int byteToInt(byte data) {
        int highBit = (int) ((data >> 4) & 0x0F);
        int lowBit = (int) (0x0F & data);
        return highBit * 16 + lowBit;
    }


    /**
     * RGB格式的数据转ARGB格式
     *
     * @param data 原始数据
     * @return 转换后的数据
     */
    public static int[] rgbToArgb(byte[] data) {
        int size = data.length;
        if (size == 0) {
            return null;
        }

        int arg = 0;
        if (size % 3 != 0) {
            arg = 1;
        }

        int[] color = new int[size / 3 + arg];
        int r, g, b;

        if (arg == 0) {
            for (int i = 0; i < color.length; ++i) {
                r = byteToInt(data[i * 3]);
                g = byteToInt(data[i * 3 + 1]);
                b = byteToInt(data[i * 3 + 2]);

                color[i] = (r << 16) | (g << 8) | b | 0xFF000000;
            }
        } else {
            for (int i = 0; i < color.length - 1; ++i) {
                r = byteToInt(data[i * 3]);
                g = byteToInt(data[i * 3 + 1]);
                b = byteToInt(data[i * 3 + 2]);
                color[i] = (r << 16) | (g << 8) | b | 0xFF000000;
            }
            color[color.length - 1] = 0xFF000000;
        }
        return color;
    }

    /**
     * 深度数据转RGB
     *
     * @param src 原始数据
     * @param dst 转换后数据
     */
    public static void depthToRgb(ByteBuffer src, ByteBuffer dst) {
        nDepthToRgb(src, dst);
    }

    /**
     * 将彩色图和深度图进行叠加
     *
     * @param colorData 彩色数据
     * @param depthData 深度数据
     * @param colorW    彩色宽
     * @param colorH    彩色高
     * @param depthW    深度宽
     * @param depthH    深度高
     * @param alpha     深度图的透明度
     * @return 叠加后的数据
     */
    public static byte[] depthAlignToColor(ByteBuffer colorData, ByteBuffer depthData, int colorW, int colorH,
                                           int depthW, int depthH, float alpha) {
        return nDepthAlignToColor(colorData, depthData, colorW, colorH, depthW, depthH, alpha);
    }

    /**
     * uyvy格式转rgb
     *
     * @param srcBuffer 原始数据
     * @param dstBuffer 转换后数据
     * @param width     宽
     * @param height    高
     */
    public static void uyvyToRgb(ByteBuffer srcBuffer, ByteBuffer dstBuffer, int width, int height) {
        nUyvyToRgb(srcBuffer, dstBuffer, width, height);
    }

    public static void y8ToRgb(ByteBuffer srcBuffer, ByteBuffer dstBuffer, int width, int height) {
        nY8ToRgb(srcBuffer, dstBuffer, width, height);
    }

    private static native void nDepthToRgb(ByteBuffer srcBuffer, ByteBuffer dstBuffer);

    private static native byte[] nDepthAlignToColor(ByteBuffer colorData, ByteBuffer depthData, int colorW, int colorH,
                                                    int depthW, int depthH, float alpha);

    private static native void nUyvyToRgb(ByteBuffer srcBuffer, ByteBuffer dstBuffer, int width, int height);

    private static native void nY8ToRgb(ByteBuffer srcBuffer, ByteBuffer dstBuffer, int width, int height);

    public static native void nScalePrecisionToDepthPixel(ByteBuffer depthBuffer,
                                                          int w, int h, int size, float scale);
}
