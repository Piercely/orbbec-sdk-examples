package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.orbbec.obsensor.Config;
import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.DeviceProperty;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.FrameSet;
import com.orbbec.obsensor.IRFrame;
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

/**
 * 红外渲染示例
 */
public class InfraredViewerActivity extends AppCompatActivity {
    private static final String TAG = "InfraredViewerActivity";

    private OBContext mOBContext;
    private Pipeline mPipeline;
    private Thread mStreamThread;
    private volatile boolean mIsStreamRunning;
    private OBGLView mIrView;
    private Device mDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("InfraredViewer");
        setContentView(R.layout.activity_infrared_viewer);
        mIrView = findViewById(R.id.irview_id);
        // 1.初始化SDK, 并监听设备变化
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (null == mPipeline) {
                        // 2.创建Device, 并通过Device初始化Pipeline
                        mDevice = deviceList.getDevice(0);

                        Sensor irSensor = mDevice.getSensor(SensorType.IR);
                        if (null == irSensor) {
                            showToast("本设备不支持Ir");
                            return;
                        }

                        mPipeline = new Pipeline(mDevice);

                        // 3.创建Pipeline配置
                        Config config = new Config();

                        // 4.获取流配置，并配置到Config，此处根据宽和帧率来进行匹配，匹配满足宽为640，帧率为30fps的配置
                        StreamProfileList irProfileList = mPipeline.getStreamProfileList(SensorType.IR);
                        StreamProfile streamProfile = getVideoStreamProfile(irProfileList, 640, 0, Format.UNKNOWN, 30);
                        if (null == streamProfile) {
                            streamProfile = getVideoStreamProfile(irProfileList, 0, 0, Format.UNKNOWN, 30);
                        }
                        if (null != irProfileList) {
                            irProfileList.close();
                        }

                        // 5.使能红外流配置
                        if (null != streamProfile) {
                            printStreamProfile(streamProfile.as(StreamType.VIDEO));
                            config.enableStream(streamProfile);
                            streamProfile.close();
                        } else {
                            Log.w(TAG, "onDeviceAttach: No target stream profile!");
                            mPipeline.close();
                            mPipeline = null;

                            config.close();
                            return;
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
        runOnUiThread(() -> Toast.makeText(InfraredViewerActivity.this, msg, Toast.LENGTH_SHORT).show());
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
        while (mIsStreamRunning) {
            try {
                // 等待100ms后如果获取不到，则超时
                FrameSet frameSet = mPipeline.waitForFrameSet(100);

                if (null == frameSet) {
                    continue;
                }

                // 获取红外流数据
                IRFrame frame = frameSet.getIrFrame();

                if (frame != null) {
                    // 获取红外数据并进行渲染
                    byte[] frameData = new byte[frame.getDataSize()];
                    frame.getData(frameData);
                    mIrView.update(frame.getWidth(), frame.getHeight(), StreamType.IR, frame.getFormat(), frameData, 1.0f);

                    // 释放红外数据帧
                    frame.close();
                }

                // 释放数据集
                frameSet.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

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

            if (null != mDevice) {
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