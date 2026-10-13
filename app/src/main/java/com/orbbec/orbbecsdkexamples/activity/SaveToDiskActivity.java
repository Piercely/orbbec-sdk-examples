package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.orbbec.obsensor.ColorFrame;
import com.orbbec.obsensor.Config;
import com.orbbec.obsensor.DepthFrame;
import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.FormatConvertFilter;
import com.orbbec.obsensor.FormatConvertType;
import com.orbbec.obsensor.Frame;
import com.orbbec.obsensor.FrameSet;
import com.orbbec.obsensor.FrameType;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.Pipeline;
import com.orbbec.obsensor.SensorType;
import com.orbbec.obsensor.StreamProfile;
import com.orbbec.obsensor.StreamProfileList;
import com.orbbec.obsensor.StreamType;
import com.orbbec.obsensor.VideoFrame;
import com.orbbec.obsensor.VideoStreamProfile;
import com.orbbec.orbbecsdkexamples.R;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.orbbec.orbbecsdkexamples.bean.FrameCopy;
import com.orbbec.orbbecsdkexamples.utils.FileUtils;
import com.orbbec.orbbecsdkexamples.utils.ImageUtils;

public class SaveToDiskActivity extends AppCompatActivity {
    private static final String TAG = "SaveToDiskActivity";

    private OBContext mOBContext;
    private Pipeline mPipeline;

    private Thread mStreamThread;
    private Thread mPicSavingThread;
    private volatile boolean mIsStreamRunning;
    private volatile boolean mIsPicSavingRunning;

    private int colorCount = 0;
    private int depthCount = 0;
    private Device mDevice;
    private FormatConvertFilter mFormatConvertFilter;
    private BlockingQueue<FrameCopy> mFrameSaveQueue = new ArrayBlockingQueue<>(10);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SaveToDisk");
        setContentView(R.layout.activity_save_to_disk);
        // 1.初始化SDK, 并监听设备变化
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (null == mPipeline) {
                        // 2.创建Device, 并通过Device初始化Pipeline
                        mDevice = deviceList.getDevice(0);

                        if (null == mDevice.getSensor(SensorType.DEPTH)) {
                            depthCount = 5;
                            showToast("本设备不支持Depth");
                        }
                        if (null == mDevice.getSensor(SensorType.COLOR)) {
                            colorCount = 5;
                            showToast("本设备不支持Color");
                        }

                        mPipeline = new Pipeline(mDevice);

                        // 3.初始化格式转换filter
                        mFormatConvertFilter = new FormatConvertFilter();

                        // 4.创建Pipeline配置
                        Config config = new Config();

                        // 5.获取彩色流配置，并配置到Config
                        try {
                            StreamProfileList colorProfileList = mPipeline.getStreamProfileList(SensorType.COLOR);
                            StreamProfile colorStreamProfile = null;
                            if (null != colorProfileList) {
                                colorStreamProfile = getVideoStreamProfile(colorProfileList, 640, 0, Format.MJPG, 30);
                                if (null == colorStreamProfile) {
                                    colorStreamProfile = getVideoStreamProfile(colorProfileList, 0, 0, Format.UNKNOWN, 30);
                                }
                                colorProfileList.close();
                            }

                            // 6.通过获取到的彩色流配置使能彩色流
                            if (null != colorStreamProfile) {
                                printStreamProfile(colorStreamProfile.as(StreamType.VIDEO));
                                config.enableStream(colorStreamProfile);
                                colorStreamProfile.close();
                            } else {
                                Log.w(TAG, "onDeviceAttach: No target color stream profile!");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // 7.获取深度流配置，并配置到Config
                        try {
                            StreamProfileList depthProfileList = mPipeline.getStreamProfileList(SensorType.DEPTH);
                            StreamProfile depthStreamProfile = null;
                            if (null != depthProfileList) {
                                depthStreamProfile = getVideoStreamProfile(depthProfileList, 640, 0, Format.UNKNOWN, 30);
                                if (null == depthStreamProfile) {
                                    depthStreamProfile = getVideoStreamProfile(depthProfileList, 0, 0, Format.UNKNOWN, 30);
                                }
                                depthProfileList.close();
                            }

                            // 8.通过获取到的深度流配置使能深度流
                            if (null != depthStreamProfile) {
                                printStreamProfile(depthStreamProfile.as(StreamType.VIDEO));
                                config.enableStream(depthStreamProfile);
                                depthStreamProfile.close();
                            } else {
                                Log.w(TAG, "onDeviceAttach: No target depth stream profile!");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // 9.使用Config开启Pipeline
                        mPipeline.start(config);

                        // 10.释放config资源
                        config.close();

                        // 11.创建获取Pipeline数据线程以及图片保存线程
                        start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 12.释放设备列表资源
                    deviceList.close();
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                try {
                    deviceList.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(SaveToDiskActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private void printStreamProfile(VideoStreamProfile vsp) {
        Log.i(TAG, "printStreamProfile: "
                + vsp.getWidth() + "×" + vsp.getHeight()
                + "@" + vsp.getFps() + "fps " + vsp.getFormat());
    }

    private VideoStreamProfile getVideoStreamProfile(StreamProfileList profileList,
                                                     int width, int height, Format format, int fps) {
        VideoStreamProfile vsp = null;
        try {
            vsp = profileList.getVideoStreamProfile(width, height, format, fps);
        } catch (Exception e) {
            Log.w(TAG, "getVideoStreamProfile: " + e.getMessage());
        }
        return vsp;
    }

    private void start() {
        colorCount = 0;
        depthCount = 0;
        mIsStreamRunning = true;
        mIsPicSavingRunning = true;
        if (null == mStreamThread) {
            mStreamThread = new Thread(mStreamRunnable);
            mStreamThread.start();
        }

        if (null == mPicSavingThread) {
            mPicSavingThread = new Thread(mPicSavingRunnable);
            mPicSavingThread.start();
        }
    }

    private void stop() {
        mIsStreamRunning = false;
        mIsPicSavingRunning = false;
        if (null != mStreamThread) {
            try {
                mStreamThread.join(1000);
            } catch (InterruptedException e) {
            }
            mStreamThread = null;
        }

        if (null != mPicSavingThread) {
            try {
                mPicSavingThread.join(1000);
            } catch (InterruptedException e) {
            }
            mPicSavingThread = null;
        }
    }

    private Runnable mStreamRunnable = () -> {
        int count = 0;
        ByteBuffer colorSrcBuf = null;
        ByteBuffer colorDstBuf = null;
        while (mIsStreamRunning) {
            try {
                // 等待100ms后如果获取不到，则超时
                FrameSet frameSet = mPipeline.waitForFrameSet(100);
                if (null == frameSet) {
                    continue;
                }
                if (count < 5) {
                    frameSet.close();
                    count++;
                    continue;
                }

                // 获取彩色流数据
                ColorFrame colorFrame = frameSet.getColorFrame();
                if (null != colorFrame) {
                    Frame rgbFrame = null;
                    switch (colorFrame.getFormat()) {
                        case MJPG:
                            mFormatConvertFilter.setFormatType(FormatConvertType.FORMAT_MJPEG_TO_RGB888);
                            rgbFrame = mFormatConvertFilter.process(colorFrame);
                            break;
                        case YUYV:
                            mFormatConvertFilter.setFormatType(FormatConvertType.FORMAT_YUYV_TO_RGB888);
                            rgbFrame = mFormatConvertFilter.process(colorFrame);
                            break;
                        case UYVY:
                            FrameCopy frameCopy = copyToFrameT(colorFrame);
                            if (null == colorSrcBuf || colorSrcBuf.capacity() != colorFrame.getDataSize()) {
                                colorSrcBuf = ByteBuffer.allocateDirect(colorFrame.getDataSize());
                            }
                            colorSrcBuf.clear();
                            colorFrame.getData(colorSrcBuf);

                            int colorDstSize = colorFrame.getWidth() * colorFrame.getHeight() * 3;
                            if (null == colorDstBuf || colorDstBuf.capacity() != colorDstSize) {
                                colorDstBuf = ByteBuffer.allocateDirect(colorDstSize);
                            }
                            colorDstBuf.clear();
                            ImageUtils.uyvyToRgb(colorSrcBuf, colorDstBuf, colorFrame.getWidth(), colorFrame.getHeight());
                            frameCopy.data = new byte[colorDstSize];
                            colorDstBuf.get(frameCopy.data);
                            mFrameSaveQueue.offer(frameCopy);
                            break;
                        default:
                            Log.w(TAG, "Unsupported color format!");
                            break;
                    }
                    if (null != rgbFrame) {
                        FrameCopy frameCopy = copyToFrameT(rgbFrame.as(FrameType.VIDEO));
                        mFrameSaveQueue.offer(frameCopy);
                        rgbFrame.close();
                    }
                    colorFrame.close();
                }

                // 获取深度流数据
                DepthFrame depthFrame = frameSet.getDepthFrame();
                if (null != depthFrame) {
                    FrameCopy frameT = copyToFrameT(depthFrame);
                    mFrameSaveQueue.offer(frameT);
                    depthFrame.close();
                }

                // 释放数据集
                frameSet.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable mPicSavingRunnable = () -> {
        while (mIsPicSavingRunning) {
            try {
                FrameCopy frameT = mFrameSaveQueue.poll(300, TimeUnit.MILLISECONDS);
                if (null != frameT) {
                    Log.d(TAG, "colorCount :" + colorCount);
                    if (frameT.getStreamType() == FrameType.COLOR && colorCount < 5) {
                        FileUtils.saveImage(frameT);
                        colorCount++;
                    }

                    Log.d(TAG, "depthCount :" + depthCount);
                    if (frameT.getStreamType() == FrameType.DEPTH && depthCount < 5) {
                        FileUtils.saveImage(frameT);
                        depthCount++;
                    }
                }
            } catch (Exception e) {
            }

            if (colorCount == 5 && depthCount == 5) {
                mIsPicSavingRunning = false;
                break;
            }
        }

        mFrameSaveQueue.clear();
    };

    // Frame数据拷贝
    private FrameCopy copyToFrameT(VideoFrame frame) {
        FrameCopy frameT = new FrameCopy();
        frameT.size = frame.getDataSize();
        frameT.data = new byte[frameT.size];
        frame.getData(frameT.data);
        frameT.format = frame.getFormat();
        frameT.width = frame.getWidth();
        frameT.height = frame.getHeight();
        frameT.frameIndex = frame.getFrameIndex();
        frameT.frameType = frame.getStreamType();
        frameT.timeStamp = frame.getTimeStamp();
        frameT.systemTimeStamp = frame.getSystemTimeStamp();
        return frameT;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // 停止获取Pipeline数据
            stop();

            // 释放filter资源
            if (null != mFormatConvertFilter) {
                try {
                    mFormatConvertFilter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 停止Pipeline，并关闭
            if (null != mPipeline) {
                mPipeline.stop();
                mPipeline.close();
                mPipeline = null;
            }

            // 释放Device资源
            if (null != mDevice) {
                mDevice.close();
                mDevice = null;
            }

            // 释放SDK
            if (null != mOBContext) {
                mOBContext.close();
                mOBContext = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}