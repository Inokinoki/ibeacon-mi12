package cc.inoki.beacondemojava;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cc.inoki.beacondemojava.utils.BeaconRssiHelper;
import cc.inoki.beacondemojava.utils.PermissionManager;

public class ScanActivity extends Activity implements Runnable{

    private static final String LOG_TAG = "ScanActivity";

    // Components
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private Handler scanHandler = new Handler();

    // Views
    private FrameLayout layout;

    // Values
    private boolean isScanning = false;

    private String selectedDeviceMacAddress;
    private String selectedDeviceUUID;

    private BeaconRssiHelper beaconRssiHelper;
    private int lastRssi = 0;
    private int rssiTolerance = 2;

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(LOG_TAG, "Failed:" + errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i(LOG_TAG, "Result length:" + results.size());
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // After the filter, the result will be only our device
            if (beaconRssiHelper == null){
                beaconRssiHelper = new BeaconRssiHelper();
                beaconRssiHelper.addRssiRecord(result.getRssi());
                Log.i(LOG_TAG, "RSSI: " + result.getRssi());
            }
            else {
                if (Math.abs(result.getRssi() - beaconRssiHelper.mean()) > 5){
                    if (result.getRssi() < beaconRssiHelper.mean()){
                        layout.setBackgroundColor(getResources().getColor(R.color.colorCold));
                    } else {
                        layout.setBackgroundColor(getResources().getColor(R.color.colorHot));
                    }
                    Log.i(LOG_TAG, "RSSI: " + result.getRssi() + " Mean: " + beaconRssiHelper.mean());
                    beaconRssiHelper = new BeaconRssiHelper();
                    beaconRssiHelper.addRssiRecord(result.getRssi());
                }
                else {
                    Log.i(LOG_TAG, "RSSI: " + result.getRssi() + " Mean: " + beaconRssiHelper.mean());
                    beaconRssiHelper.addRssiRecord(result.getRssi());
                    layout.setBackgroundColor(Color.WHITE);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        Log.i(LOG_TAG, "Test");

        // Get Selected Device
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        Log.i(LOG_TAG, "Test1");

        this.selectedDeviceMacAddress = bundle.getString("mac_address", "123");
        this.selectedDeviceUUID = bundle.getString("uuid", "456");

        Log.i(LOG_TAG, "Test2");

        // Get views
        this.layout = findViewById(R.id.scan_background);
        TextView mac = findViewById(R.id.device_mac);
        TextView uuid = findViewById(R.id.device_uuid);
        mac.setText(this.selectedDeviceMacAddress);
        uuid.setText(this.selectedDeviceUUID);

        // Get adapter
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PermissionManager.PERMISSION_REQUEST_COARSE_LOCATION);
        }
        else {
            scanHandler.post(this);
        }

        Log.i(LOG_TAG, "Test3");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PermissionManager.PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // When we can access location information
                    scanHandler.post(this);
                }
                break;
        }
    }

    @Override
    public void run() {
        if (isScanning) {
            if (btAdapter != null) {
                btAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            }
        } else {
            if (btAdapter != null) {
                Log.i(LOG_TAG, "Scan begin for " + this.selectedDeviceMacAddress);
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                List<ScanFilter> filters = new ArrayList<>();
                filters.add(
                        new ScanFilter.Builder()
                                .setDeviceAddress(this.selectedDeviceMacAddress)
                                .build());
                btAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);
            }
        }

        isScanning = !isScanning;
    }
}
