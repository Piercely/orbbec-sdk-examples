package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.orbbec.obsensor.Config;
import com.orbbec.obsensor.DepthFrame;
import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceInfo;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.DeviceProperty;
import com.orbbec.obsensor.Format;
import com.orbbec.obsensor.FrameSet;
import com.orbbec.obsensor.MediaState;
import com.orbbec.obsensor.MediaStateCallback;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.PermissionType;
import com.orbbec.obsensor.Pipeline;
import com.orbbec.obsensor.Playback;
import com.orbbec.obsensor.SensorType;
import com.orbbec.obsensor.StreamProfile;
import com.orbbec.obsensor.StreamProfileList;
import com.orbbec.obsensor.StreamType;
import com.orbbec.obsensor.VideoStreamProfile;
import com.orbbec.orbbecsdkexamples.R;
import com.orbbec.orbbecsdkexamples.utils.FileUtils;
import com.orbbec.orbbecsdkexamples.utils.LocalUtils;
import com.orbbec.orbbecsdkexamples.view.OBGLView;

import java.io.File;

/**
 * 录制回放示例
 */
public class RecordPlaybackActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "RecordPlaybackActivity";

    private static final String BAG_FILE_PATH = "/sdcard/Orbbec/recorder.bag";

    private LinearLayout mRecordCtlPanelLL;
    private LinearLayout mPlaybackCtlPanelLL;
    private OBGLView mDepthGLView;
    private TextView mDeviceInfoTv;
    private Button mStartRecordBtn;
    private Button mStopRecordBtn;
    private Button mStartPlaybackBtn;
    private Button mStopPlaybackBtn;

    private Pipeline mPipeline;
    private Pipeline mPlaybackPipe;
    private Playback mPlayback;
    private Config mConfig;
    private Device mDevice;
    private OBContext mOBContext;
    private Thread mStreamThread;
    private Thread mPlaybackThread;
    private volatile boolean mIsStreamRunning;

    private volatile boolean mIsPlaying;
    private volatile boolean mIsRecording;

    private final Object mSync = new Object();

    // 回放状态回调
    private MediaStateCallback mMediaStateCallback = new MediaStateCallback() {
        @Override
        public void onState(MediaState state) {
            if (state == MediaState.OB_MEDIA_END) {
                stopPlayback();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("RecordPlayback");
        setContentView(R.layout.activity_record_playback);
        // 初始化ui界面
        initViews();

        // 初始化sdk
        initSdk();
    }

    private void initViews() {
        mRecordCtlPanelLL = findViewById(R.id.ll_record_control_panel);
        mPlaybackCtlPanelLL = findViewById(R.id.ll_playback_control_panel);
        mDepthGLView = findViewById(R.id.glv_depth);
        mDeviceInfoTv = findViewById(R.id.tv_device_info);
        mDeviceInfoTv.setText(getString(R.string.device_info, "DepthStreamPreview", "", "", "", ""));
        mStartRecordBtn = findViewById(R.id.btn_start_record);
        mStartRecordBtn.setOnClickListener(this);
        mStopRecordBtn = findViewById(R.id.btn_stop_record);
        mStopRecordBtn.setOnClickListener(this);

        mStartPlaybackBtn = findViewById(R.id.btn_start_playback);
        mStartPlaybackBtn.setOnClickListener(this);
        mStopPlaybackBtn = findViewById(R.id.btn_stop_playback);
        mStopPlaybackBtn.setOnClickListener(this);

        mStartRecordBtn.setEnabled(false);
        mStopRecordBtn.setEnabled(false);
        mStartPlaybackBtn.setEnabled(isPlayFileValid());
        mStopPlaybackBtn.setEnabled(false);
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(RecordPlaybackActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    private void initSdk() {
        // 1.初始化SDK, 并监听设备变化
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    if (null == mPipeline) {
                        // 2.创建Device, 并通过Device初始化Pipeline
                        mDevice = deviceList.getDevice(0);

                        if (null == mDevice.getSensor(SensorType.DEPTH)) {
                            showToast("本设备不支持Depth");
                            return;
                        }

                        mPipeline = new Pipeline(mDevice);

                        // 3.更新设备信息ui
                        updateDeviceInfoView(false);

                        // 4.创建Pipeline配置
                        mConfig = new Config();

                        // 5.获取深度流配置，并配置到Config
                        StreamProfileList depthProfileList = mPipeline.getStreamProfileList(SensorType.DEPTH);
                        StreamProfile streamProfile = getVideoStreamProfile(depthProfileList, 640, 0, Format.UNKNOWN, 30);
                        if (null == streamProfile) {
                            streamProfile = getVideoStreamProfile(depthProfileList, 0, 0, Format.UNKNOWN, 30);
                        }
                        if (null != depthProfileList) {
                            depthProfileList.close();
                        }

                        // 6.使能深度流配置
                        if (null != streamProfile) {
                            printStreamProfile(streamProfile.as(StreamType.VIDEO));
                            mConfig.enableStream(streamProfile);
                            streamProfile.close();
                        } else {
                            Log.w(TAG, "onDeviceAttach: No target stream profile!");
                        }

                        // 7.设置镜像
                        if (mDevice.isPropertySupported(DeviceProperty.OB_PROP_DEPTH_MIRROR_BOOL, PermissionType.OB_PERMISSION_WRITE)) {
                            mDevice.setPropertyValueB(DeviceProperty.OB_PROP_DEPTH_MIRROR_BOOL, true);
                        }

                        // 8.开流
                        mPipeline.start(mConfig);

                        // 9.创建获取Pipeline数据线程
                        start();

                        runOnUiThread(() -> {
                            mStartRecordBtn.setEnabled(true);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 10.释放deviceList
                    deviceList.close();
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                try {
                    release();

                    runOnUiThread(() -> {
                        mStartRecordBtn.setEnabled(false);
                        mStopRecordBtn.setEnabled(false);
                        mStartPlaybackBtn.setEnabled(isPlayFileValid());
                        mStopPlaybackBtn.setEnabled(false);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    deviceList.close();
                }
            }
        });
    }

    private void updateDeviceInfoView(boolean isPlayback) {
        try {
            DeviceInfo deviceInfo = null;
            if (isPlayback) {
                if (null != mPlayback) {
                    // 获取回放的设备的设备信息
                    deviceInfo = mPlayback.getDeviceInfo();
                }
            } else {
                // 获取预览设备的设备信息
                if (null != mPipeline) {
                    deviceInfo = mDevice.getInfo();
                }
            }

            // 更新ui
            if (null != deviceInfo) {
                String name = deviceInfo.getName();
                String sn = deviceInfo.getSerialNumber();
                String pid = LocalUtils.formatHex04(deviceInfo.getPid());
                String vid = LocalUtils.formatHex04(deviceInfo.getVid());
                runOnUiThread(() -> {
                    mDeviceInfoTv.setText(getString(R.string.device_info, (isPlayback ? "DepthStreamPlayback" : "DepthStreamPreview"),
                            name, sn, pid, vid));
                });
                deviceInfo.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启数据流预览线程
     */
    private void start() {
        mIsStreamRunning = true;
        if (null == mStreamThread) {
            mStreamThread = new Thread(mStreamPrevRunnable);
            mStreamThread.start();
        }
    }

    /**
     * 停止数据流预览线程
     */
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

    /**
     * 开始回放
     */
    private void startPlayback() {
        try {
            if (!FileUtils.isFileExists(BAG_FILE_PATH)) {
                Toast.makeText(RecordPlaybackActivity.this, "File not found!", Toast.LENGTH_LONG).show();
                return;
            }
            if (!mIsPlaying) {
                mIsPlaying = true;

                // 释放Playback资源
                if (null != mPlayback) {
                    mPlayback.close();
                    mPlayback = null;
                }

                // 创建回放pipeline
                if (null != mPlaybackPipe) {
                    mPlaybackPipe.close();
                    mPlaybackPipe = null;
                }
                mPlaybackPipe = new Pipeline(BAG_FILE_PATH);

                // 通过回放Pipeline获取回放器
                mPlayback = mPlaybackPipe.getPlayback();

                // 设置回放状态回调
                mPlayback.setMediaStateCallback(mMediaStateCallback);

                // 开始回放
                mPlaybackPipe.start(null);

                // 创建回放线程
                if (null == mPlaybackThread) {
                    mPlaybackThread = new Thread(mPlaybackRunnable);
                    mPlaybackThread.start();
                }

                updateDeviceInfoView(true);
                mStartRecordBtn.setEnabled(false);
                mStopRecordBtn.setEnabled(false);
                mStartPlaybackBtn.setEnabled(false);
                mStopPlaybackBtn.setEnabled(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    /**
     * 停止回放
     */
    private void stopPlayback() {
        try {
            if (mIsPlaying) {
                mIsPlaying = false;
                // 停止回放线程
                if (null != mPlaybackThread) {
                    try {
                        mPlaybackThread.join(300);
                    } catch (InterruptedException e) {
                    }
                    mPlaybackThread = null;
                }

                // 停止回放
                if (null != mPlaybackPipe) {
                    mPlaybackPipe.stop();
                }
                runOnUiThread(() -> {
                    updateDeviceInfoView(false);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            runOnUiThread(() -> {
                mStartRecordBtn.setEnabled(null != mDevice);
                mStopRecordBtn.setEnabled(false);
                mStartPlaybackBtn.setEnabled(true);
                mStopPlaybackBtn.setEnabled(false);
            });
        }
    }

    /**
     * 开始录制
     */
    private void startRecord() {
        try {
            if (!mIsRecording) {
                if (null != mPipeline) {
                    // 开始录制
                    mPipeline.startRecord(BAG_FILE_PATH);
                    mIsRecording = true;

                    mStartRecordBtn.setEnabled(false);
                    mStopRecordBtn.setEnabled(true);
                    mStartPlaybackBtn.setEnabled(false);
                } else {
                    Log.w(TAG, "mPipeline is null !");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            mStartRecordBtn.setEnabled(true);
            mStopRecordBtn.setEnabled(false);
            mStartPlaybackBtn.setEnabled(isPlayFileValid());
        }
    }

    /**
     * 停止录制
     */
    private void stopRecord() {
        try {
            if (mIsRecording) {
                mIsRecording = false;
                if (null != mPipeline) {
                    // 停止录制
                    mPipeline.stopRecord();
                }

                mStartPlaybackBtn.setEnabled(true);
                mStopPlaybackBtn.setEnabled(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mStartRecordBtn.setEnabled(null != mDevice);
            mStopRecordBtn.setEnabled(false);
        }
    }

    // 回放线程
    private Runnable mPlaybackRunnable = () -> {
        while (mIsPlaying) {
            try {
                // 等待100ms后如果获取不到，则超时
                FrameSet frameSet = mPlaybackPipe.waitForFrameSet(100);
                if (null == frameSet) {
                    continue;
                }

                // 获取深度流数据
                DepthFrame depthFrame = frameSet.getDepthFrame();
                if (null != depthFrame) {
                    // 获取深度数据并进行渲染
                    byte[] frameData = new byte[depthFrame.getDataSize()];
                    depthFrame.getData(frameData);
                    synchronized (mSync) {
                        mDepthGLView.update(depthFrame.getWidth(), depthFrame.getHeight(), StreamType.DEPTH, depthFrame.getFormat(), frameData, depthFrame.getValueScale());
                    }

                    // 释放深度数据帧
                    depthFrame.close();
                }

                // 释放数据集
                frameSet.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // 预览线程
    private Runnable mStreamPrevRunnable = () -> {
        while (mIsStreamRunning) {
            try {
                // 等待100ms后如果获取不到，则超时
                FrameSet frameSet = mPipeline.waitForFrameSet(100);

                if (null == frameSet) {
                    continue;
                }

                // 获取深度流数据
                if (!mIsPlaying) {
                    DepthFrame frame = frameSet.getDepthFrame();

                    if (frame != null) {
                        // 获取深度数据并进行渲染
                        byte[] frameData = new byte[frame.getDataSize()];
                        frame.getData(frameData);
                        synchronized (mSync) {
                            mDepthGLView.update(frame.getWidth(), frame.getHeight(), StreamType.DEPTH, frame.getFormat(), frameData, frame.getValueScale());
                        }

                        // 释放深度数据帧
                        frame.close();
                    }
                }

                // 释放数据集
                frameSet.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onClick(View v) {
        try {
            switch (v.getId()) {
                case R.id.btn_start_record:
                    startRecord();
                    break;
                case R.id.btn_stop_record:
                    stopRecord();
                    break;
                case R.id.btn_start_playback:
                    startPlayback();
                    break;
                case R.id.btn_stop_playback:
                    stopPlayback();
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void release() {
        try {
            // 停止获取Pipeline数据
            stop();

            // 停止回放线程
            if (mIsPlaying) {
                mIsPlaying = false;
                if (null != mPlaybackThread) {
                    try {
                        mPlaybackThread.join(300);
                    } catch (InterruptedException e) {
                    }
                    mPlaybackThread = null;
                }
            }

            // 释放回放器
            if (null != mPlayback) {
                mPlayback.close();
                mPlayback = null;
            }

            // 释放回放pipeline
            if (null != mPlaybackPipe) {
                mPlaybackPipe.close();
                mPlaybackPipe = null;
            }

            // 释放config
            if (null != mConfig) {
                mConfig.close();
                mConfig = null;
            }

            // 释放预览pipeline
            if (null != mPipeline) {
                mPipeline.close();
                mPipeline = null;
            }

            // 释放Device
            if (null != mDevice) {
                mDevice.close();
                mDevice = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            release();

            // 释放SDK
            if (null != mOBContext) {
                mOBContext.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isPlayFileValid() {
        File file = new File(BAG_FILE_PATH);
        return file.exists() && file.isFile() && file.canRead() && file.length() > 0;
    }
}