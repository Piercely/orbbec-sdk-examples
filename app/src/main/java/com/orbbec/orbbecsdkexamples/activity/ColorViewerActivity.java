package com.orbbec.orbbecsdkexamples.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.orbbec.obsensor.ColorFrame;
import com.orbbec.obsensor.Config;
import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.DeviceProperty;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.FrameSet;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.PermissionType;
import com.orbbec.obsensor.Pipeline;
import com.orbbec.obsensor.Sensor;
import com.orbbec.obsensor.SensorType;
import com.orbbec.obsensor.StreamProfile;
import com.orbbec.obsensor.StreamProfileList;
import com.orbbec.obsensor.StreamType;
import com.orbbec.obsensor.VideoStreamProfile;
import com.orbbec.orbbecsdkexamples.R;
import com.orbbec.orbbecsdkexamples.view.OBGLView;

import java.nio.ByteBuffer;

/**
 * 彩色渲染示例
 */
public class ColorViewerActivity extends AppCompatActivity {
    private static final String TAG = "ColorViewerActivity";

    private OBContext mOBContext;
    private Pipeline mPipeline;
    private Thread mStreamThread;
    private volatile boolean mIsStreamRunning;
    private OBGLView mColorView;
    private Device mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("ColorView");
        setContentView(R.layout.activity_color_viewer);
        mColorView = findViewById(R.id.colorview_id);
        // 1.初始化SDK, 并监听设备变化
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (null == mPipeline) {
                        // 2.创建Device，并通过Device初始化Pipeline
                        mDevice = deviceList.getDevice(0);
                        Sensor colorSensor = mDevice.getSensor(SensorType.COLOR);
                        if (null == colorSensor) {
                            showToast("本设备不支持Color");
                            return;
                        }
                        mPipeline = new Pipeline(mDevice);

                        // 3.创建Pipeline配置
                        Config config = new Config();

                        // 4.获取彩色流配置，并配置到Config
                        // 此处根据宽和帧率及RGB888格式来进行匹配，如果没有改配置，则匹配满足宽为640，帧率为30fps的配置
                        StreamProfileList colorProfileList = mPipeline.getStreamProfileList(SensorType.COLOR);
                        StreamProfile streamProfile = getVideoStreamProfile(colorProfileList, 640, 0, Format.RGB888, 30);
                        if (null == streamProfile) {
                            streamProfile = getVideoStreamProfile(colorProfileList, 0, 0, Format.RGB888, 30);
                        }
                        if (null != colorProfileList) {
                            colorProfileList.close();
                        }

                        // 5.使能彩色配置
                        if (null != streamProfile) {
                            printStreamProfile(streamProfile.as(StreamType.VIDEO));
                            config.enableStream(streamProfile);
                            streamProfile.close();
                        } else {
                            Log.w(TAG, "No target stream profile!");
                        }

                        // 6.开流
                        mPipeline.start(config);

                        // 7.释放config
                        config.close();

                        // 8.创建获取Pipeline数据线程
                        start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 9.释放设备列表资源
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
        runOnUiThread(() -> Toast.makeText(ColorViewerActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private void start() {
        mIsStreamRunning = true;
        if (null == mStreamThread) {
            mStreamThread = new Thread(mStreamRunnable);
            mStreamThread.start();
        }
    }

    private void stop() {
        mIsStreamRunning = false;
        if (null != mStreamThread) {
            try {
                mStreamThread.join(300);
            } catch (InterruptedException e) {
            }
            mStreamThread = null;
        }
    }

    private Runnable mStreamRunnable = () -> {
        ByteBuffer buffer = null;
        while (mIsStreamRunning) {
            try {
                // 阻塞方式获取数据集，等待100ms后如果获取不到，则超时
                FrameSet frameSet = mPipeline.waitForFrameSet(100);

                if (null == frameSet) {
                    continue;
                }

                // 获取彩色流数据
                ColorFrame colorFrame = frameSet.getColorFrame();
                if (null != buffer) {
                    buffer.clear();
                }

                if (null != colorFrame) {
                    // 初始化buffer
                    int dataSize = colorFrame.getDataSize();
                    if (null == buffer || buffer.capacity() != dataSize) {
                        buffer = ByteBuffer.allocateDirect(dataSize);
                    }
                    // 获取数据并进行渲染
                    colorFrame.getData(buffer);
                    mColorView.update(colorFrame.getWidth(), colorFrame.getHeight(), StreamType.COLOR, colorFrame.getFormat(), buffer, 1.0f);

                    // 释放彩色数据帧
                    colorFrame.close();
                }
                // 释放数据集
                frameSet.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // 停止获取Pipeline数据
            stop();

            // 停止Pipeline，并释放
            if (null != mPipeline) {
                mPipeline.stop();
                mPipeline.close();
            }

            // 释放Device
            if (mDevice != null) {
                mDevice.close();
            }

            // 释放SDK
            if (null != mOBContext) {
                mOBContext.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}