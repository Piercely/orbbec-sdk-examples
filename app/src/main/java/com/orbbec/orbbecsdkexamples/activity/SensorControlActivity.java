package com.orbbec.orbbecsdkexamples.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.orbbec.obsensor.Device;
import com.orbbec.obsensor.DeviceChangedCallback;
import com.orbbec.obsensor.DeviceInfo;
import com.orbbec.obsensor.DeviceList;
import com.orbbec.obsensor.DeviceProperty;
import com.orbbec.obsensor.DevicePropertyInfo;
import com.orbbec.obsensor.OBContext;
import com.orbbec.obsensor.PermissionType;
import com.orbbec.obsensor.PropertyType;
import com.orbbec.orbbecsdkexamples.R;
import com.orbbec.orbbecsdkexamples.adapter.InformationAdapter;
import com.orbbec.orbbecsdkexamples.adapter.SpinnerContentAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SensorControlActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "SensorControlActivity";
    private List<Device> mDeviceList = new ArrayList<>();
    private Map<String, DevicePropertyInfo> mDevicePropertyMap = new HashMap<>();
    private List<String> mDeviceNameList = new ArrayList<>();
    private List<String> mPropertyNameList = new ArrayList<>();
    private SpinnerContentAdapter<String> mDeviceNameAdapter;
    private SpinnerContentAdapter<String> mPropertyAdapter;
    private Button mSetBtn;
    private EditText mSetEt;
    private Button mGetBtn;
    private TextView mGetTv;
    private Spinner mDeviceSp;
    private Spinner mPropertySp;
    private RecyclerView mInformationSectionRV;
    private InformationAdapter mInformationAdapter;
    private OBContext mOBContext;
    private Device mSelectDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SensorControl");
        setContentView(R.layout.activity_sensor_control);
        // 1.初始化界面
        initViews();

        // 2.初始化SDK
        mOBContext = new OBContext(getApplicationContext(), new DeviceChangedCallback() {
            @Override
            public void onDeviceAttach(DeviceList deviceList) {
                try {
                    // 3.将获取到的设备添加到设备列表
                    mDeviceList.add(deviceList.getDevice(0));

                    // 4.更新设备列表
                    updateDeviceSpinnerList();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 5.释放设备列表资源
                    deviceList.close();
                }
            }

            @Override
            public void onDeviceDetach(DeviceList deviceList) {
                try {
                    // 有设备断开连接，释放设备列表资源
                    for (Device device : mDeviceList) {
                        device.close();
                    }
                    mDeviceList.clear();

                    // 重新获取设备并更新设备列表
                    DeviceList curDeviceList = mOBContext.queryDevices();
                    for (int i = 0; i < curDeviceList.getDeviceCount(); i++) {
                        mDeviceList.add(curDeviceList.getDevice(i));
                    }
                    curDeviceList.close();
                    updateDeviceSpinnerList();

                    // 没有设备连接，清空传感器列表和属性列表
                    if (mDeviceList.size() <= 0) {
                        clearPropertySpinnerList();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    deviceList.close();
                }
            }
        });
    }

    private void initViews() {
        mSetBtn = findViewById(R.id.btn_set);
        mSetEt = findViewById(R.id.et_set);
        mGetBtn = findViewById(R.id.btn_get);
        mGetTv = findViewById(R.id.tv_get);

        // 1.1.设备列表初始化
        mDeviceSp = findViewById(R.id.spi_device);
        mDeviceNameAdapter = new SpinnerContentAdapter<String>(this, android.R.layout.simple_spinner_item);
        mDeviceNameAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mDeviceSp.setAdapter(mDeviceNameAdapter);
        mDeviceSp.setOnItemSelectedListener(this);

        // 1.2.属性列表初始化
        mPropertySp = findViewById(R.id.spi_instructions);
        mPropertyAdapter = new SpinnerContentAdapter<String>(this, android.R.layout.simple_spinner_item);
        mPropertyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mPropertySp.setAdapter(mPropertyAdapter);
        mPropertySp.setOnItemSelectedListener(this);

        mSetBtn.setOnClickListener(this);
        mGetBtn.setOnClickListener(this);

        // 1.3.操作日志栏初始化
        mInformationSectionRV = (RecyclerView) findViewById(R.id.rv_information);
        mInformationAdapter = new InformationAdapter(this);
        mInformationSectionRV.setAdapter(mInformationAdapter);
        mInformationSectionRV.setLayoutManager(new LinearLayoutManager(this));
    }

    private void addNewMessage(String message) {
        mInformationAdapter.newMessage(message);
        mInformationSectionRV.scrollToPosition(mInformationAdapter.getItemCount() - 1);
    }

    private void updateDeviceSpinnerList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDeviceNameList.clear();
                try {
                    for (Device device : mDeviceList) {
                        DeviceInfo devInfo = device.getInfo();
                        String deviceName = devInfo.getName();
                        devInfo.close();
                        mDeviceNameList.add(deviceName);
                    }
                    mDeviceNameAdapter.clear();
                    mDeviceNameAdapter.addAll(mDeviceNameList);
                    mDeviceNameAdapter.notifyDataSetChanged();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void updatePropertySpinnerList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDevicePropertyMap.clear();
                mPropertyNameList.clear();
                try {
                    List<DevicePropertyInfo> devicePropertyList = mSelectDevice.getSupportedPropertyList();
                    for (DevicePropertyInfo deviceProperty : devicePropertyList) {
                        if (deviceProperty.getPropertyType() != PropertyType.STRUCT_PROPERTY
                                && deviceProperty.getPermissionType() != PermissionType.OB_PERMISSION_DENY) {
                            mPropertyNameList.add(deviceProperty.getPropertyName());
                            mDevicePropertyMap.put(deviceProperty.getPropertyName(), deviceProperty);
                        }
                    }
                    addNewMessage("选择设备指令");
                    mPropertyAdapter.clear();
                    mPropertyAdapter.addAll(mPropertyNameList);
                    mPropertyAdapter.notifyDataSetChanged();

                    mPropertySp.setSelection(0, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void clearPropertySpinnerList() {
        mPropertyNameList.clear();
        mPropertyAdapter.clear();
        mPropertyAdapter.addAll(mPropertyNameList);
        mPropertyAdapter.notifyDataSetChanged();
    }

    /**
     * 资源释放，释放设备列表中的所有设备（传感器会在设备释放的时候进行释放，无需单独释放传感器资源）
     * 释放OBContext资源
     */
    private void release() {
        try {
            // 设备列表资源释放
            for (Device device : mDeviceList) {
                device.close();
            }
            mDeviceList.clear();

            // OBContext资源释放
            if (null != mOBContext) {
                mOBContext.close();
                mOBContext = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 资源释放
        release();
    }

    //region View.OnClickListener
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_set:
                setProperty();
                break;
            case R.id.btn_get:
                getProperty();
                break;
        }
    }
    //endregion

    /**
     * 设置选择的属性
     */
    private void setProperty() {
        try {
            if (null == mDeviceNameList || mDeviceNameList.size() <= 0) {
                showToast("设备列表为空");
                return;
            }
            if (null == mPropertyNameList || mPropertyNameList.size() <= 0) {
                showToast("操作指令列表为空");
                return;
            }

            if (null == mSetEt.getText() || "".equals(mSetEt.getText()) || mSetEt.getText().toString().isEmpty()) {
                showToast("请按照提示输入正确的值");
                return;
            }

            String setValue = mSetEt.getText().toString();
            // 设置设备指令
            DevicePropertyInfo devProperty = mDevicePropertyMap.get(mPropertySp.getSelectedItem().toString());
            switch (devProperty.getPropertyType()) {
                case INT_PROPERTY:
                    //曝光和增益需要关闭AE才能调节， 亮度需要打开AE才能调节， 白平衡（色温）需要关闭自动白平衡才能调节
                    if (devProperty.getProperty() == DeviceProperty.OB_PROP_COLOR_EXPOSURE_INT
                            || devProperty.getProperty() == DeviceProperty.OB_PROP_COLOR_GAIN_INT) { //曝光或增益
                        //获取自动曝光状态
                        boolean propertyExposureBool = false;
                        try {
                            propertyExposureBool = mSelectDevice.getPropertyValueB(DeviceProperty.OB_PROP_COLOR_AUTO_EXPOSURE_BOOL);
                        } catch (Exception e) {

                        }
                        Log.d(TAG, "propertyExposureBool:" + propertyExposureBool);
                        if (propertyExposureBool) {
                            showToast("自动曝光未关闭，不能设置曝光或增益");
                            return;
                        }
                    }

                    if (devProperty.getProperty() == DeviceProperty.OB_PROP_COLOR_BRIGHTNESS_INT) { //亮度
                        //获取自动曝光状态
                        boolean propertyExposureBool = mSelectDevice.getPropertyValueB(DeviceProperty.OB_PROP_COLOR_AUTO_EXPOSURE_BOOL);
                        Log.d(TAG, "propertyExposureBool:" + propertyExposureBool);
                        if (!propertyExposureBool) {
                            showToast("自动曝光未开启，不能设置亮度");
                            return;
                        }
                    }

                    if (devProperty.getProperty() == DeviceProperty.OB_PROP_COLOR_WHITE_BALANCE_INT) { //白平衡
                        //获取自动白平衡状态
                        boolean propertyWhiteBool = mSelectDevice.getPropertyValueB(DeviceProperty.OB_PROP_COLOR_AUTO_WHITE_BALANCE_BOOL);
                        Log.d(TAG, "propertyWhiteBool:" + propertyWhiteBool);
                        if (propertyWhiteBool) {
                            showToast("自动白平衡未关闭，不能设置白平衡");
                            return;
                        }
                    }
                    mSelectDevice.setPropertyValueI(devProperty.getProperty(), Integer.parseInt(setValue));
                    break;
                case BOOL_PROPERTY:
                    // 0:false 1:true
                    mSelectDevice.setPropertyValueB(devProperty.getProperty(), ("1".equals(setValue) ? true : false));
                    break;
                case FLOAT_PROPERTY:
                    mSelectDevice.setPropertyValueF(devProperty.getProperty(), Float.parseFloat(setValue));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取选择的属性
     */
    private void getProperty() {
        try {
            if (null == mDeviceNameList || mDeviceNameList.size() <= 0) {
                showToast("设备列表为空");
                return;
            }
            if (null == mPropertyNameList || mPropertyNameList.size() <= 0) {
                showToast("操作指令列表为空");
                return;
            }
            // 获取设备指令
            DevicePropertyInfo devProperty = mDevicePropertyMap.get(mPropertySp.getSelectedItem().toString());
            switch (devProperty.getPropertyType()) {
                case INT_PROPERTY:
                    int valueI = mSelectDevice.getPropertyValueI(devProperty.getProperty());
                    mGetTv.setText(Integer.toString(valueI));
                    break;
                case BOOL_PROPERTY:
                    boolean valueB = mSelectDevice.getPropertyValueB(devProperty.getProperty());
                    mGetTv.setText(Boolean.toString(valueB));
                    break;
                case FLOAT_PROPERTY:
                    float valueF = mSelectDevice.getPropertyValueF(devProperty.getProperty());
                    mGetTv.setText(Float.toString(valueF));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            addNewMessage("[error]:"+e.getMessage());
        }
    }

    /**
     * 获取设备信息
     *
     * @param device 待获取信息的设备
     * @return 返回设备信息
     */
    private String getVersionInfo(Device device) {
        StringBuilder sb = new StringBuilder();
        try (DeviceInfo deviceInfo = device.getInfo()) {
            sb.append("固件版本号: " + deviceInfo.getFirmwareVersion() + "\n"
                    + "硬件版本：" + deviceInfo.getHardwareVersion() + "\n"
                    + "SDK版本号: " + OBContext.getVersionName() + "\n"
                    + "SN号: " + deviceInfo.getSerialNumber()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private void showToast(String msg) {
        Log.d(TAG, "msg:" + msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * 根据选择的指令更新设置和获取提示
     *
     * @param instructionTypeName 选择的属性名称
     */
    private void updateSetEditTextHint(String instructionTypeName) {
        mSetEt.setText("");
        mGetTv.setText("");
        try {
            DevicePropertyInfo devicePropertyInfo = mDevicePropertyMap.get(instructionTypeName);
            PropertyType propertyType = devicePropertyInfo.getPropertyType();
            DeviceProperty property = devicePropertyInfo.getProperty();
            Log.i(TAG, "updateSetEditTextHint: instructionTypeName="+instructionTypeName);
            switch (propertyType) {
                case INT_PROPERTY:
                    int minI = mSelectDevice.getMinRangeI(property);
                    int maxI = mSelectDevice.getMaxRangeI(property);
                    mSetEt.setHint("[" + minI + "-" + maxI + "]");
                    break;
                case BOOL_PROPERTY:
                    mSetEt.setHint("[" + 0 + "-" + 1 + "]");
                    break;
                case FLOAT_PROPERTY:
                    float minF = mSelectDevice.getMinRangeF(property);
                    float maxF = mSelectDevice.getMaxRangeF(property);
                    mSetEt.setHint("[" + minF + "-" + maxF + "]");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            addNewMessage("[error]:"+e.getMessage());
        }
    }

    private void setPanelVisibleControl(boolean visible) {
        mSetBtn.setVisibility(visible ? View.VISIBLE : View.GONE);
        mSetEt.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void getPanelVisibleControl(boolean visible) {
        mGetBtn.setVisibility(visible ? View.VISIBLE : View.GONE);
        mGetTv.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updateControlPanel(PermissionType permission) {
        switch (permission) {
            case OB_PERMISSION_READ:
                setPanelVisibleControl(false);
                getPanelVisibleControl(true);
                break;
            case OB_PERMISSION_WRITE:
                setPanelVisibleControl(true);
                getPanelVisibleControl(false);
                break;
            case OB_PERMISSION_READ_WRITE:
                setPanelVisibleControl(true);
                getPanelVisibleControl(true);
                break;
        }
    }

    //region AdapterView.OnItemSelectedListener
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.spi_device:
                // 设备切换，更新传感器列表和属性列表
                mSelectDevice = mDeviceList.get(position);
                updatePropertySpinnerList();

                String deviceName = mDeviceSp.getSelectedItem().toString();
                addNewMessage("选择设备名：" + deviceName);
                addNewMessage(getVersionInfo(mDeviceList.get(position)));
                break;
            case R.id.spi_instructions:
                // 指令切换
                String instructionTypeName = mPropertySp.getSelectedItem().toString();
                PermissionType permissionType = mDevicePropertyMap.get(instructionTypeName).getPermissionType();
                addNewMessage("选择操作指令：" + instructionTypeName + " 读写权限：" + permissionType);
                updateControlPanel(permissionType);
                updateSetEditTextHint(instructionTypeName);
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
    //endregion
}