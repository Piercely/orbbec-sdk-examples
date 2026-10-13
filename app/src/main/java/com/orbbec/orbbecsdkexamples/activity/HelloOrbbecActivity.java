package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceInfo;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.LogSeverity;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.Sensor;
import com.orbbec.orbbecsdkexamples.R;
import com.orbbec.orbbecsdkexamples.utils.LocalUtils;

public class HelloOrbbecActivity extends AppCompatActivity {
    private static final String TAG = "HelloOrbbecActivity";

    private static boolean hasConfigLogFile = false;
    private OBContext mOBContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("HelloOrbbec");
        setContentView(R.layout.activity_hello_orbbec);
        TextView txtInfo = findViewById(R.id.txt_info);
        // 配置日志存储（可选），日志文件只能配置一次，并且必须在OBContext对象构建之前
        if (!hasConfigLogFile) {
            OBContext.setLoggerToFile(LogSeverity.DEBUG, getExternalCacheDir().getAbsolutePath(), 20, 3);
            hasConfigLogFile = true;
        }

        // 1.初始化SDK, 并监听设备变化
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                Log.d(TAG, "onDeviceAttach");
                try {
                    StringBuilder builder = new StringBuilder();
                    // 2.查看SDK版本
                    builder.append("SDK VersionName = " + OBContext.getVersionName() + "\n");
                    builder.append("SDK StageVersion = " + OBContext.getStageVersion() + "\n");

                    // 3.获取设备个数
                    int deviceCount = deviceList.getDeviceCount();

                    for (int i = 0; i < deviceCount; ++i) {
                        // 4.根据设备索引创建设备
                        Device device = deviceList.getDevice(i);

                        // 5.获取设备信息
                        DeviceInfo info = device.getInfo();

                        // 6.获取设备版本信息
                        builder.append("Name: " + info.getName() + "\n");
                        builder.append("Vid: " + LocalUtils.formatHex04(info.getVid()) + "\n");
                        builder.append("Pid: " + LocalUtils.formatHex04(info.getPid()) + "\n");
                        builder.append("Uid: " + info.getUid() + "\n");
                        builder.append("SN: " + info.getSerialNumber() + "\n");
                        builder.append("connectType: " + info.getConnectionType() + "\n");
                        String firmwareVersion = info.getFirmwareVersion();
                        builder.append("FirmwareVersion: " + firmwareVersion + "\n");

                        // 7.遍历当前设备的传感器
                        for (Sensor sensor : device.querySensors()) {
                            // 8.查询传感器类型
                            builder.append("Sensor : type = " + sensor.getType() + "\n");
                        }
                        runOnUiThread(() -> txtInfo.setText(builder.toString()));

                        // 9.释放设备信息
                        info.close();

                        // 10.释放设备资源
                        device.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 11.释放设备列表资源
                    deviceList.close();
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                Log.d(TAG, "onDeviceDetach");
                try {
                    deviceList.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        try {
            // 释放SDK
            if (null != mOBContext) {
                mOBContext.close();
                mOBContext = null;
            }
            super.onDestroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}