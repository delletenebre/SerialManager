package kg.delletenebre.serialmanager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import kg.delletenebre.serialmanager.Commands.Commands;
import xdroid.toaster.Toaster;

public class UsbService extends Service implements SensorEventListener {
    private final static String TAG = "UsbService";
    private final static int NOTIFICATION_ID = 8;

    private static JSONArray jsonDevices;
    private EventsReceiver receiver;
    private UsbManager usbManager;
    private static Map<String, UsbSerialDevice> openedSerialPorts;

    private static SharedPreferences prefs;
    private static NotificationCompat.Builder notification;

    private SettingsContentObserver settingsContentObserver;

    private SensorManager sensorManager;
    private int sensorLightMode = 0;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (App.isDebug()) {
            Log.d(TAG, "onStartCommand");
        }

        if (intent != null) {
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            String usbDevicePrefs = prefs.getString("usbDevice", "");

            if (usbDevice != null && usbManager.hasPermission(usbDevice)
                    && prefs.getBoolean("usb", false)
                    && (usbDevicePrefs.isEmpty()
                    || usbDevicePrefs.equals(usbDevice.getDeviceName()))) {
                connectToDevice(usbDevice);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        if (App.isDebug()) {
            Log.i(TAG, "Service: onCreate");
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        startForeground(NOTIFICATION_ID, getNotification().build());

        initializeJsonDevices();

        receiver = new EventsReceiver();
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        openedSerialPorts = new HashMap<>();

        findAttachedUsbDevice();

        if (prefs.getBoolean("brightness_settings", false)) {
            settingsContentObserver = new SettingsContentObserver(new Handler());
            getApplicationContext().getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, settingsContentObserver);
        }

        if (prefs.getBoolean("brightness_sensor", false)) {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (sensorLight != null) {
                sensorManager.registerListener(this, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (openedSerialPorts != null) {
            for (Map.Entry<String, UsbSerialDevice> entry : openedSerialPorts.entrySet()) {
                UsbSerialDevice serialPort = entry.getValue();

                try {
                    serialPort.close();
                    entry.setValue(null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        prefs = null;
        openedSerialPorts = null;
        if (notification != null) {
            stopForeground(true);
            notification = null;
        }


        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        if (settingsContentObserver != null) {
            getApplicationContext().getContentResolver()
                    .unregisterContentObserver(settingsContentObserver);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (App.isDebug()) {
            Log.i(TAG, "Service: onDestroy");
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (App.isDebug()) {
            if (sensor.getType() == Sensor.TYPE_LIGHT) {
                Log.i("Sensor Changed", "LightSensor Accuracy: " + accuracy);
            }
        }
    }

    public void onSensorChanged(SensorEvent event) {
        if (App.isDebug()) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                Log.i("Sensor Changed", "LightSensor: " + event.values[0]);
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float value = event.values[0];
            int mode = 0;

            if (value >= SensorManager.LIGHT_SUNLIGHT_MAX) {
                mode = 7;
            } else if (value >= SensorManager.LIGHT_SUNLIGHT) {
                mode = 6;
            } else if (value >= SensorManager.LIGHT_SHADE) {
                mode = 5;
            } else if (value >= SensorManager.LIGHT_OVERCAST) {
                mode = 4;
            } else if (value >= SensorManager.LIGHT_SUNRISE) {
                mode = 3;
            } else if (value >= SensorManager.LIGHT_CLOUDY) {
                mode = 2;
            } else if (value >= SensorManager.LIGHT_FULLMOON) {
                mode = 1;
            }

            if (sensorLightMode != mode
                    && prefs != null && prefs.getBoolean("brightness_sensor", false)) {
                sensorLightMode = mode;
                usbSend("{lightsensormode:" + sensorLightMode + "}");
            }
        }
    }

    private static NotificationCompat.Builder getNotification() {
        Context context = App.getContext();

        if (notification == null) {
            notification = (NotificationCompat.Builder) new NotificationCompat.Builder(context)
                    .setOnlyAlertOnce(true)
                    .setColor(Color.parseColor("#607d8b"))
                    .setSmallIcon(R.drawable.notification_icon)
//                    .setPriority(Notification.PRIORITY_HIGH)
//                    .setVibrate(new long[0])
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.tap_to_open_main_activity))
                    .setContentIntent(
                            PendingIntent.getActivity(context, 0,
                                    new Intent(context, MainActivity.class), 0));
        }

        return notification;
    }

    private static void updateNotificationText() {
        Resources resources = App.getContext().getResources();
        NotificationCompat.Builder notification = getNotification();

        int numberUsb = 0;
        int numberBluetooth = 0;
        if (openedSerialPorts != null) {
            numberUsb = openedSerialPorts.size();
        }

        notification.setContentText(String.format(
                resources.getString(
                        R.string.notification_usb_connected_number), numberUsb));
//        notification.setSubText(String.format(
//                resources.getString(
//                        R.string.notification_bluetooth_connected_number), numberBluetooth));

        NotificationManager mNotificationManager =
                (NotificationManager) App.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICATION_ID, notification.build());
    }

    private static int tickerIndex = 0;
    public static void updateNotificationTickerText() {
        Context context = App.getContext();

//        if (Build.VERSION.SDK_INT < 23
//                || (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(context))) {
//            context.startService(new Intent(context, HUD.class));
//        }
//
//
//        context.startService(new Intent(context, HUD.class));
        //notification.setTicker("Some text " + String.valueOf(tickerIndex));
        tickerIndex++;
    }


    private void initializeJsonDevices() {
        if (jsonDevices == null) {
            try {
                String jsonDevicesFromAssets = loadJsonFromAsset();
                if (jsonDevicesFromAssets != null) {
                    jsonDevices = new JSONArray(jsonDevicesFromAssets);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public static String loadJsonFromAsset() {
        Context context = App.getContext();
        String json = null;

        try {
            InputStream is = context.getAssets().open("devices.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            if (is.read(buffer) == buffer.length) {
                json = new String(buffer, Charset.forName("UTF8"));
            }
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return json;
    }

    private void connectToDevice(UsbDevice device) {
        UsbSerialDevice serialPort = UsbSerialDevice.createUsbSerialDevice(device, usbManager.openDevice(device));
        if (serialPort != null) {
            if (serialPort.open()) {
                int baudRate = Integer.parseInt(prefs.getString("baudRate", "9600"));
                int dataBits = Integer.parseInt(prefs.getString("dataBits", "8"));
                int stopBits = Integer.parseInt(prefs.getString("stopBits", "1"));
                int parity = Integer.parseInt(prefs.getString("parity", "0"));
                boolean dtr = prefs.getBoolean("dtr", false);
                boolean rts = prefs.getBoolean("rts", false);

                serialPort.setBaudRate(baudRate);
                serialPort.setDataBits(dataBits);
                serialPort.setStopBits(stopBits);
                serialPort.setParity(parity);
                /**
                 * Current flow control Options:
                 * UsbSerialInterface.FLOW_CONTROL_OFF
                 * UsbSerialInterface.FLOW_CONTROL_RTS_CTS only for CP2102 and FT232
                 * UsbSerialInterface.FLOW_CONTROL_DSR_DTR only for CP2102 and FT232
                 */
                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialPort.setDTR(dtr);
                serialPort.setRTS(rts);

                serialPort.read(receiveCallback);
//                serialPort.getCTS(ctsCallback);
//                serialPort.getDSR(dsrCallback);

                openedSerialPorts.put(device.getDeviceName(), serialPort);
                updateNotificationText();

                // Everything went as expected
                Toaster.toast("USB device [" + device.getDeviceName() + "] connected");
                if (App.isDebug()) {
                    Log.d(TAG, "USB device [" + device.getDeviceName() + "] connected");
                }
            } else {
                // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                if (serialPort instanceof CDCSerialDevice) {
                    if (App.isDebug()) {
                        Log.d(TAG, "CDC driver not working");
                    }
                    Toaster.toast("CDC driver not working");
                } else {
                    if (App.isDebug()) {
                        Log.d(TAG, "USB device not working");
                    }
                    Toaster.toast("USB device not working");
                }
            }
        } else {
            if (App.isDebug()) {
                Log.d(TAG, "USB device not supported");
            }
            // No driver for given device, even generic CDC driver could not be loaded
            Toaster.toast("USB device not supported");
        }
    }

    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */
    private String receivedDataBuffer = "";
    private UsbSerialInterface.UsbReadCallback receiveCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = new String(arg0, Charset.forName("UTF8"));
            if (App.isDebug()) {
                Log.d(TAG, "Received data: " + data);
            }

            updateNotificationTickerText();

            receivedDataBuffer += data;
            if (data.contains(App.NEW_LINE)
                    || prefs != null && !prefs.getBoolean("usbDetectCommandByNewLine", false)) {
                Commands.processReceivedData(receivedDataBuffer);
                receivedDataBuffer = "";
            }
        }
    };

    private void findAttachedUsbDevice() {
        boolean connectionEnabled = prefs.getBoolean("usb", false);
        String usbDeviceToConnect = prefs.getString("usbDevice", "");

        for (UsbDevice usbDevice: usbManager.getDeviceList().values()) {
            boolean vidCheck = false;
            boolean pidCheck = false;

            int deviceVID = usbDevice.getVendorId();
            int devicePID = usbDevice.getProductId();

            for (int i = 0; i < jsonDevices.length(); i++) {
                try {
                    JSONObject json = jsonDevices.getJSONObject(i);
                    vidCheck = !json.has("vid")||(deviceVID == json.getInt("vid"));
                    pidCheck = !json.has("pid")||(devicePID == json.getInt("pid"));

                    if (vidCheck && pidCheck) {
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            if (vidCheck && pidCheck
                    && usbManager.hasPermission(usbDevice)
                    && connectionEnabled
                    && (usbDeviceToConnect.isEmpty()
                    || usbDeviceToConnect.equals(usbDevice.getDeviceName()))) {

                connectToDevice(usbDevice);
            }
        }

        stopServiceIfNoConnectedDevices();
    }

    public static void detachDevice(UsbDevice detachedDevice) {
        if (openedSerialPorts != null) {
            String detachedDeviceName = detachedDevice.getDeviceName();

            if (openedSerialPorts.containsKey(detachedDeviceName)) {
                try {
                    openedSerialPorts.get(detachedDeviceName).close();
                    openedSerialPorts.put(detachedDeviceName, null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                openedSerialPorts.remove(detachedDeviceName);
            }

            updateNotificationText();
            System.gc();
            stopServiceIfNoConnectedDevices();
        }
    }

    private static void stopServiceIfNoConnectedDevices() {
        if (openedSerialPorts != null && openedSerialPorts.isEmpty()) {
            Context context = App.getContext();
            context.stopService(new Intent(context, UsbService.class));
        }
    }

    /*
     * This function will be called to write data through Serial Port
     */
    public static boolean usbSend(String data) {
        if (openedSerialPorts != null && prefs != null) {
            if (App.isDebug()) {
                Log.d(TAG, "Data to send: " + data);
            }

            if (prefs.getBoolean("crlf", true)) {
                data += "\r\n";
            }

            for (Map.Entry<String, UsbSerialDevice> entry : openedSerialPorts.entrySet()) {
                UsbSerialDevice serialPort = entry.getValue();

                serialPort.write(data.getBytes());
            }

            return true;
        } else {
            if (App.isDebug()) {
                Log.w(TAG, "Can't send data [" + data + "]. No connected devices");
            }

            Toaster.toast(R.string.toast_serial_send_warning);
        }

        return false;
    }
    public static void sendFromWidget(String data, int widgetId) {
        if (usbSend(data)) {
            Intent i = new Intent(App.ACTION_SEND_DATA_SUCCESS);
            i.putExtra("widgetId", widgetId);
            i.putExtra("type", "usb");
            App.getContext().sendBroadcast(i);
        }
    }


}