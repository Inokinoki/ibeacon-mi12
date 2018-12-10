package cc.inoki.beacondemojava;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cc.inoki.beacondemojava.utils.PermissionManager;

public class MainActivity extends AppCompatActivity {
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    /**
     * bytesToHex method
     */
    static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static final String LOG_TAG = "MainActivity";

    // Components
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private Handler scanHandler = new Handler();

    // Views
    private RelativeLayout layout;
    private ListView deviceList;
    private Button startButton;

    // Values
    private boolean isScanning = false;

    private Set<String> deviceSet = new HashSet<>();

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
            if (deviceSet.add(result.getDevice().getAddress())) {
                String[] deviceListItems = {};
                deviceListItems = deviceSet.toArray(deviceListItems);
                deviceList.setAdapter(
                        new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, deviceListItems));
            }

            final byte[] scanRecord = result.getScanRecord().getBytes();

            int startByte = 2;
            boolean patternFound = false;
            while (startByte <= 5) {
                if (((int) scanRecord[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                        ((int) scanRecord[startByte + 3] & 0xff) == 0x15) { //Identifies correct data length
                    patternFound = true;
                    break;
                }
                startByte++;
            }

            if (patternFound) {
                //Convert to hex String
                byte[] uuidBytes = new byte[16];
                System.arraycopy(scanRecord, startByte + 4, uuidBytes, 0, 16);
                String hexString = bytesToHex(uuidBytes);

                //UUID detection
                String uuid = hexString.substring(0, 8) + "-" +
                        hexString.substring(8, 12) + "-" +
                        hexString.substring(12, 16) + "-" +
                        hexString.substring(16, 20) + "-" +
                        hexString.substring(20, 32);

                // major
                final int major = (scanRecord[startByte + 20] & 0xff) * 0x100 + (scanRecord[startByte + 21] & 0xff);

                // minor
                final int minor = (scanRecord[startByte + 22] & 0xff) * 0x100 + (scanRecord[startByte + 23] & 0xff);

                Log.i(LOG_TAG, "UUID: " + uuid + "\\nmajor: " + major + "\\nminor" + minor + " RSSI: " + result.getRssi());
            }
        }
    };

    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {

        if (isScanning) {
            if (btAdapter != null) {
                btAdapter.getBluetoothLeScanner().stopScan(scanCallback);
                //btAdapter.stopLeScan(leScanCallback);
            }
        } else {
            if (btAdapter != null) {
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                List<ScanFilter> filters = new ArrayList<ScanFilter>();
                btAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);
                //btAdapter.startLeScan(leScanCallback);
            }
        }

        isScanning = !isScanning;
        }
    };

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    // ------------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.layout = findViewById(R.id.layout_background);
        this.deviceList = findViewById(R.id.device_list);
        this.startButton = findViewById(R.id.start_button);

        // init BLE
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PermissionManager.PERMISSION_REQUEST_COARSE_LOCATION);
            }
            else {
                scanHandler.post(scanRunnable);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PermissionManager.PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // When we can access location information
                    scanHandler.post(scanRunnable);
                }
                break;
        }
    }
}
