package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.orbbec.obsensor.AlignMode;
import com.orbbec.obsensor.CameraParam;
import com.orbbec.obsensor.CameraParamList;
import com.orbbec.obsensor.Config;
import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.Frame;
import com.orbbec.obsensor.FrameSet;
import com.orbbec.obsensor.FrameSetCallback;
import com.orbbec.obsensor.FrameType;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.Pipeline;
import com.orbbec.obsensor.PointCloudFilter;
import com.orbbec.obsensor.PointFrame;
import com.orbbec.obsensor.SensorType;
import com.orbbec.obsensor.StreamProfileList;
import com.orbbec.obsensor.VideoStreamProfile;
import com.orbbec.orbbecsdkexamples.R;
import com.orbbec.orbbecsdkexamples.utils.FileUtils;

import java.io.File;

public class PointCloudActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "PointCloudActivity";

    private File mSdcardDir = Environment.getExternalStorageDirectory();
    private OBContext mOBContext;
    private Pipeline mPipeline;
    private Device mDevice;
    private Thread mPointFilterThread;
    private PointCloudFilter mPointCloudFilter;
    private Button mSaveDepthPointsBtn;
    private Button mSaveColorPointsBtn;
    private TextView mInfoTv;
    private Format mPointFormat = Format.POINT;
    private FrameSet mPointFrameSet;
    private boolean mIsSavePoints = false;
    private boolean mIsPointCloudRunning = false;

    // Pipeline开流的回调
    private FrameSetCallback mPointCloudFrameSetCallback = frameSet -> {
        if (null != frameSet) {
            if (mIsPointCloudRunning) {
                if (null == mPointFrameSet) {
                    mPointFrameSet = frameSet;
                    return;
                }
            }

            frameSet.close();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("PointCloud");
        setContentView(R.layout.activity_point_cloud);

        mInfoTv = findViewById(R.id.tv_information);
        mSaveDepthPointsBtn = findViewById(R.id.btn_save_depth_points);
        mSaveDepthPointsBtn.setOnClickListener(this);
        mSaveColorPointsBtn = findViewById(R.id.btn_save_rgb_points);
        mSaveColorPointsBtn.setOnClickListener(this);

        // 1.初始化SDK
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (null == mPipeline) {
                        // 2.通过deviceList获取Device
                        mDevice = deviceList.getDevice(0);

                        if (null == mDevice.getSensor(SensorType.COLOR)) {
                            showToast("本设备不支持Color");
                            runOnUiThread(() -> {
                                mSaveColorPointsBtn.setEnabled(false);
                            });
                        }

                        if (null == mDevice.getSensor(SensorType.DEPTH)) {
                            showToast("本设备不支持Depth");
                            return;
                        }

                        // 3.通过Device创建Pipeline
                        mPipeline = new Pipeline(mDevice);

                        // 4.创建Config，用于配置pipeline开流
                        Config config = new Config();

                        // 5.获取深度配置列表
                        StreamProfileList depthProfileList = mPipeline.getStreamProfileList(SensorType.DEPTH);
                        VideoStreamProfile depthProfileTarget = getVideoStreamProfile(depthProfileList, 640, 0, Format.UNKNOWN, 30);
                        if (null == depthProfileTarget) {
                            depthProfileTarget = getVideoStreamProfile(depthProfileList, 0, 0, Format.UNKNOWN, 30);
                        }
                        if (null != depthProfileList) {
                            depthProfileList.close();
                        }

                        // 6.获取到指定深度配置后使能配置
                        int depthProfileW = 0;
                        int depthProfileH = 0;
                        if (null != depthProfileTarget) {
                            printStreamProfile(depthProfileTarget);
                            config.enableStream(depthProfileTarget);
                            depthProfileW = depthProfileTarget.getWidth();
                            depthProfileH = depthProfileTarget.getHeight();
                            depthProfileTarget.close();
                        } else {
                            Log.w(TAG, "onDeviceAttach: No target depth stream profile!");
                        }

                        // 7.获取彩色配置列表
                        try {
                            StreamProfileList colorProfileList = mPipeline.getStreamProfileList(SensorType.COLOR);
                            VideoStreamProfile colorProfileTarget = getVideoStreamProfile(colorProfileList, 640, 0, Format.RGB888, 30);
                            if (null == colorProfileTarget) {
                                colorProfileTarget = getVideoStreamProfile(colorProfileList, 0, 0, Format.UNKNOWN, 30);
                            }
                            if (null != colorProfileList) {
                                colorProfileList.close();
                            }

                            // 8.获取到指定彩色配置后使能配置
                            if (null != colorProfileTarget) {
                                printStreamProfile(colorProfileTarget);
                                config.enableStream(colorProfileTarget);
                                colorProfileTarget.close();
                            } else {
                                Log.w(TAG, "onDeviceAttach: No target color stream profile!");
                            }
                        } catch (Exception e) {
                            // 如果不支持彩色Sensor时获取profile会抛异常，此时为了确保深度点云可以正常运行，需要将
                            // 深度缩放配置为不缩放，D2C目标分辨率配置为与深度一致的分辨率
                            e.printStackTrace();
                            config.setDepthScaleRequire(false);
                            config.setD2CTargetResolution(depthProfileW, depthProfileH);
                        }

                        // 9.开启硬件D2C
                        config.setAlignMode(AlignMode.ALIGN_D2C_HW_ENABLE);

                        // 10.使用回调方式开流
                        mPipeline.start(config, mPointCloudFrameSetCallback);

                        // 11.启动点云异步处理线程
                        start();

                        // 12.创建点云filter
                        mPointCloudFilter = new PointCloudFilter();

                        // 13.设置点云filter的格式
                        mPointCloudFilter.setPointFormat(mPointFormat);

                        // 14.获取相机内参并且将参数设置到点云filter中
                        CameraParam cameraParam = mPipeline.getCameraParam();
                        mPointCloudFilter.setCameraParam(cameraParam);
                        Log.i(TAG, "onDeviceAttach: cameraParam:" + cameraParam);

                        // 15.释放config资源
                        config.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 16.释放设备列表资源
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_save_depth_points:
                mInfoTv.setText("Saving depth points...\n");
                mPointFormat = Format.POINT;
                mIsSavePoints = true;
                break;
            case R.id.btn_save_rgb_points:
                mInfoTv.setText("Saving color points...\n");
                mPointFormat = Format.RGB_POINT;
                mIsSavePoints = true;
                break;
        }
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(PointCloudActivity.this, msg, Toast.LENGTH_SHORT).show());
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
        mIsPointCloudRunning = true;
        if (null == mPointFilterThread) {
            mPointFilterThread = new Thread(mPointFilterRunnable);
            mPointFilterThread.start();
        }
    }

    private void stop() {
        mIsPointCloudRunning = false;
        if (null != mPointFilterThread) {
            try {
                mPointFilterThread.join(300);
            } catch (InterruptedException e) {
            }
            mPointFilterThread = null;
        }
    }

    private final Runnable mPointFilterRunnable = new Runnable() {
        @Override
        public void run() {
            while (mIsPointCloudRunning) {
                try {
                    if (null != mPointFrameSet) {
                        Frame frame = null;
                        if (mPointFormat == Format.POINT) {
                            // 设置保存格式为深度点云
                            mPointCloudFilter.setPointFormat(Format.POINT);
                        } else {
                            // 设置保存格式为彩色点云
                            mPointCloudFilter.setPointFormat(Format.RGB_POINT);
                        }
                        // 点云filter处理生成对应的点云数据
                        frame = mPointCloudFilter.process(mPointFrameSet);

                        if (null != frame) {
                            // 获取点云帧
                            PointFrame pointFrame = frame.as(FrameType.POINTS);

                            if (mIsSavePoints) {
                                if (mPointFormat == Format.POINT) {
                                    // 获取深度点云数据并保存，深度点云的数据大小为w * h * 3
                                    float[] depthPoints = new float[pointFrame.getDataSize() / Float.BYTES];
                                    pointFrame.getPointCloudData(depthPoints);
                                    String depthPointsPath = mSdcardDir.toString() + "/Orbbec/point.ply";
                                    FileUtils.savePointCloud(depthPointsPath, depthPoints);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mInfoTv.append("Save Path:" + depthPointsPath + "\n");
                                        }
                                    });
                                } else {
                                    // 获取彩色点云数据并保存，彩色点云的数据大小为w * h * 6
                                    float[] colorPoints = new float[pointFrame.getDataSize() / Float.BYTES];
                                    pointFrame.getPointCloudData(colorPoints);
                                    String colorPointsPath = mSdcardDir.toString() + "/Orbbec/point_rgb.ply";
                                    FileUtils.saveRGBPointCloud(colorPointsPath, colorPoints);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            mInfoTv.append("Save Path:" + colorPointsPath + "\n");
                                        }
                                    });
                                }

                                mIsSavePoints = false;
                            }

                            // 释放新生成的frame
                            frame.close();
                        }

                        // 释放原始数据frameSet
                        mPointFrameSet.close();
                        mPointFrameSet = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // 停止并释放点云filter处理线程
            stop();

            // 停止Pipeline，并关闭
            if (null != mPipeline) {
                mPipeline.stop();
                mPipeline.close();
                mPipeline = null;
            }

            // 释放点云filter
            if (null != mPointCloudFilter) {
                try {
                    mPointCloudFilter.close();
                } catch (Exception e) {
                }
                mPointCloudFilter = null;
            }

            // 释放Device
            if (mDevice != null) {
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