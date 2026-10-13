package com.orbbec.orbbecsdkexamples.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceInfo;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.OBContext;
import com.orbbec.orbbecsdkexamples.R;
import com.orbbec.orbbecsdkexamples.adapter.DeviceControllerAdapter;
import com.orbbec.orbbecsdkexamples.bean.DeviceBean;

import java.util.ArrayList;
import java.util.List;

public class MultiDeviceActivity extends AppCompatActivity {
    private static final String TAG = "MultiDeviceCopyActivity";
    private OBContext mOBContext;

    private ListView mListView;
    private DeviceControllerAdapter mDeviceControllerAdapter;
    private List<DeviceBean> mDeviceBeanList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("MultiDevice");
        setContentView(R.layout.activity_multi_device);

        mListView = (ListView) findViewById(R.id.lv_multi_device);
        // 创建设备控制适配器
        mDeviceControllerAdapter = new DeviceControllerAdapter(mDeviceBeanList, this);

        mListView.setAdapter(mDeviceControllerAdapter);

        // 初始化SDK
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    int count = deviceList.getDeviceCount();
                    for (int i = 0; i < count; i++) {
                        // 创建设备
                        Device device = deviceList.getDevice(i);
                        // 获取DeviceInfo
                        DeviceInfo devInfo = device.getInfo();
                        // 获取设备名称
                        String name = devInfo.getName();
                        // 获取设备uid
                        String uid = devInfo.getUid();
                        // 获取设备连接类型
                        String connectionType = devInfo.getConnectionType();
                        // 释放DeviceInfo资源
                        devInfo.close();
                        runOnUiThread(() -> {
                            mDeviceControllerAdapter.addItem(new DeviceBean(name, uid, connectionType, device));
                        });

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 释放设备列表资源
                    deviceList.close();
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                try {
                    for (DeviceBean deviceBean : mDeviceBeanList) {
                        // 通过uid判断下线设备
                        if (deviceBean.getDeviceUid().equals(deviceList.getUid(0))) {
                            // 释放下线设备资源
                            deviceBean.getDevice().close();
                            runOnUiThread(() -> {
                                mDeviceControllerAdapter.deleteItem(deviceBean);
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "onDeviceDetach: " + e.getMessage());
                } finally {
                    // 释放设备列表资源
                    deviceList.close();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // 释放资源
            for (DeviceBean deviceBean : mDeviceBeanList) {
                try {
                    // 释放设备资源
                    deviceBean.getDevice().close();
                } catch (Exception e) {
                    Log.w(TAG, "onDestroy: " + e.getMessage());
                }
            }
            mDeviceBeanList.clear();

            // 释放SDK
            if (null != mOBContext) {
                mOBContext.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}