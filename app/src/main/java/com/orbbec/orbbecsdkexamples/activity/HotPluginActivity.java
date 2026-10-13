package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceInfo;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.FrameCallback;
import com.orbbec.obsensor.FrameType;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.Sensor;
import com.orbbec.obsensor.SensorType;
import com.orbbec.obsensor.StreamProfile;
import com.orbbec.obsensor.StreamProfileList;
import com.orbbec.obsensor.StreamType;
import com.orbbec.obsensor.VideoFrame;
import com.orbbec.obsensor.VideoStreamProfile;
import com.orbbec.orbbecsdkexamples.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class HotPluginActivity extends AppCompatActivity {
    private static final String TAG = "HotPluginActivity";

    private OBContext mOBContext;

    private Device mDevice;

    private Sensor mDepthSensor;
    private Sensor mColorSensor;
    private Sensor mIrSensor;

    private TextView mNameTv;
    private TextView mProfileInfoTv;

    private int mDepthFps;
    private int mColorFps;
    private int mIrFps;

    private FrameCallback mDepthFrameCallback = frame -> {
        printFrameInfo(frame.as(FrameType.DEPTH), mDepthFps);

        // 释放frame资源
        frame.close();
    };

    private FrameCallback mColorFrameCallback = frame -> {
        printFrameInfo(frame.as(FrameType.COLOR), mColorFps);

        // 释放frame资源
        frame.close();
    };

    private FrameCallback mIrFrameCallback = frame -> {
        printFrameInfo(frame.as(FrameType.IR), mIrFps);

        // 释放frame资源
        frame.close();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("HotPlugin");
        setContentView(R.layout.activity_hot_plugin);
        mNameTv = findViewById(R.id.tv_device_name);
        mProfileInfoTv = findViewById(R.id.tv_profile_info);

        // 1.初始化SDK, 并监听设备变化
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (deviceList == null || deviceList.getDeviceCount() <= 0) {
                        setText(mNameTv, "No device connected !");
                    }

                    // 2.创建设备，并获取设备名称
                    mDevice = deviceList.getDevice(0);
                    DeviceInfo devInfo = mDevice.getInfo();
                    String deviceName = devInfo.getName();
                    setText(mNameTv, deviceName);
                    devInfo.close();

                    // 3.获取深度传感器
                    mDepthSensor = mDevice.getSensor(SensorType.DEPTH);

                    // 4.打开深度流，profile传入null，表示使用配置文件中配置的参数开流，
                    // 如果设备中没有该配置，或不存在配置文件，则表示使用Profile列表中的第一个配置
                    if (null != mDepthSensor) {
                        mDepthSensor.start(null, mDepthFrameCallback);
                    } else {
                        Log.w(TAG, "onDeviceAttach: depth sensor is unsupported!");
                    }

                    // 5.获取彩色传感器
                    mColorSensor = mDevice.getSensor(SensorType.COLOR);

                    // 6.打开彩色流，profile传入null，表示使用配置文件中配置的参数开流，
                    // 如果设备中没有该配置，或不存在配置文件，则表示使用Profile列表中的第一个配置
                    if (null != mColorSensor) {
                        mColorSensor.start(null, mColorFrameCallback);
                    } else {
                        Log.w(TAG, "onDeviceAttach: color sensor is unsupported!");
                    }

                    // 7.获取红外传感器
                    mIrSensor = mDevice.getSensor(SensorType.IR);

                    // 8.打开红外流，profile传入null，表示使用配置文件中配置的参数开流，
                    // 如果设备中没有该配置，或不存在配置文件，则表示使用Profile列表中的第一个配置
                    if (null != mIrSensor) {
                        mIrSensor.start(null, mIrFrameCallback);
                    } else {
                        Log.w(TAG, "onDeviceAttach: ir sensor is unsupported!");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 9.更新开流配置信息
                    setText(mProfileInfoTv, formatProfileInfo());

                    // 10.释放deviceList资源
                    deviceList.close();
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                try {
                    setText(mNameTv, "No device connected !");
                    setText(mProfileInfoTv, "");

                    mDepthFps = 0;
                    mColorFps = 0;
                    mIrFps = 0;

                    // 停止深度流
                    if (null != mDepthSensor) {
                        mDepthSensor.stop();
                    }

                    // 停止彩色流
                    if (null != mColorSensor) {
                        mColorSensor.stop();
                    }

                    // 停止红外流
                    if (null != mIrSensor) {
                        mIrSensor.stop();
                    }

                    // 释放Device
                    if (null != mDevice) {
                        mDevice.close();
                        mDevice = null;
                    }

                    // 释放deviceList
                    deviceList.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setText(TextView tv, String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv.setText(text);
            }
        });
    }

    private String formatProfileInfo() {
        String profileInfo = "";
        if (null != mDepthSensor) {
            try (StreamProfileList depthList = mDepthSensor.getStreamProfileList();
                 StreamProfile depthProfile = depthList.getStreamProfile(0)) {
                VideoStreamProfile depthVsp = depthProfile.as(StreamType.VIDEO);
                int depthW = depthVsp.getWidth();
                int depthH = depthVsp.getHeight();
                int depthFps = depthVsp.getFps();
                Format depthFormat = depthVsp.getFormat();
                mDepthFps = depthFps;
                profileInfo += "Depth:" + depthW + "x" + depthH + "@" + depthFps + "fps " + depthFormat + "\n";
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (null != mColorSensor) {
            try (StreamProfileList colorList = mColorSensor.getStreamProfileList();
                 StreamProfile colorProfile = colorList.getStreamProfile(0)) {
                VideoStreamProfile colorVsp = colorProfile.as(StreamType.VIDEO);
                int colorW = colorVsp.getWidth();
                int colorH = colorVsp.getHeight();
                int colorFps = colorVsp.getFps();
                Format colorFormat = colorVsp.getFormat();
                mColorFps = colorFps;
                profileInfo += "Color:" + colorW + "x" + colorH + "@" + colorFps + "fps " + colorFormat + "\n";
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (null != mIrSensor) {
            try (StreamProfileList irList = mIrSensor.getStreamProfileList();
                 StreamProfile irProfile = irList.getStreamProfile(0)) {
                VideoStreamProfile irVsp = irProfile.as(StreamType.VIDEO);
                int irW = irVsp.getWidth();
                int irH = irVsp.getHeight();
                int irFps = irVsp.getFps();
                Format irFormat = irVsp.getFormat();
                mIrFps = irFps;
                profileInfo += "IR:" + irW + "x" + irH + "@" + irFps + "fps " + irFormat;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return profileInfo;
    }

    private void printFrameInfo(VideoFrame frame, int fps) {
        try {
            String frameInfo = "FrameType:" + frame.getStreamType()
                    + ", index:" + frame.getFrameIndex()
                    + ", width:" + frame.getWidth()
                    + ", height:" + frame.getHeight()
                    + ", format:" + frame.getFormat()
                    + ", fps:" + fps
                    + ", timeStampUs:" + frame.getTimeStampUs();
            if (frame.getStreamType() == FrameType.DEPTH) {
                frameInfo += ", middlePixelValue:" + getMiddlePixelValue(frame);
            }
            Log.i(TAG, frameInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取深度帧中心点的深度值
     *
     * @param depthFrame 深度帧
     * @return 返回中心点的深度值
     */
    private short getMiddlePixelValue(VideoFrame depthFrame) {
        int w = depthFrame.getWidth();
        int h = depthFrame.getHeight();
        byte[] depthBytes = new byte[depthFrame.getDataSize()];
        depthFrame.getData(depthBytes);
        ByteBuffer depthBuffer = ByteBuffer.wrap(depthBytes);
        depthBuffer.order(ByteOrder.nativeOrder());
        ShortBuffer depthShortBuffer = depthBuffer.asShortBuffer();
        return depthShortBuffer.get(w * h / 2 + w / 2);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // 停止深度流
            if (null != mDepthSensor) {
                mDepthSensor.stop();
            }

            // 停止彩色流
            if (null != mColorSensor) {
                mColorSensor.stop();
            }

            // 停止红外流
            if (null != mIrSensor) {
                mIrSensor.stop();
            }

            // 释放Device
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