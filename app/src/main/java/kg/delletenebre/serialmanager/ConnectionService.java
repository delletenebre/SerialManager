package kg.delletenebre.serialmanager;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.akexorcist.bluetoothspp.library.BluetoothSPP;
import app.akexorcist.bluetoothspp.library.BluetoothState;
import kg.delletenebre.serialmanager.Commands.Commands;
import kg.delletenebre.serialmanager.helper.FileUtils;
import xdroid.toaster.Toaster;

public class ConnectionService extends Service implements SensorEventListener {
    private final static String TAG = "ConnectionService";
    private final static int NOTIFICATION_ID = 8;

    // *** USB **** //
    private static JSONArray jsonDevices;
    private UsbManager usbManager;
    private static Map<String, UsbSerialDevice> openedSerialPorts;
    protected static boolean usbRestartState = false;

    // **** BLUETOOTH **** //
    private static BluetoothSPP bt;

    // **** WEBSERVER **** //
    private static AsyncHttpServer webServer = new AsyncHttpServer();
    private static List<WebSocket> webSockets = new ArrayList<>();

    // **** GENERAL **** //
    private static NotificationCompat.Builder notification;
    private SettingsContentObserver settingsContentObserver;
    private SensorManager sensorManager;
    private int sensorLightMode = 0;

    private static Map<String, String> receivedDataBuffer;

    static {
        System.loadLibrary("serial-manager");
    }
    public static native int resetUsbDevice(String filename);


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
            if (App.getPrefs().getBoolean("usb", true)) {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String usbDevicePrefs = App.getPrefs().getString("usbDevice", "");

                if (usbDevice != null && usbManager.hasPermission(usbDevice)
                        && App.getPrefs().getBoolean("usb", true)
                        && (usbDevicePrefs.isEmpty()
                        || usbDevicePrefs.equals(usbDevice.getDeviceName()))) {
                    connectToUsbDevice(usbDevice);
                } else {
                    findAttachedUsbDevice();
                }
            }
        }

        connectToBluetoothDevice();

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        if (App.isDebug()) {
            Log.i(TAG, "Service: onCreate");
        }

        startForeground(NOTIFICATION_ID, getNotification().build());

        initializeJsonDevices();

        receivedDataBuffer = new HashMap<>();
        receivedDataBuffer.put("usb", "");
        receivedDataBuffer.put("bluetooth", "");


        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        openedSerialPorts = new HashMap<>();

        if (App.getPrefs().getBoolean("send_brightness_settings", false)) {
            settingsContentObserver = new SettingsContentObserver(new Handler());
            App.getContext().getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, settingsContentObserver);

            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (sensorLight != null) {
                sensorManager.registerListener(this, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        App.createUinput();

        onBluetoothEnabled();

        sendInfoScreenState(null);

        NativeGpio.createGpiosFromCommands();

        startWebserver();

        if (App.isDebug()) {
            Log.d(TAG, "created");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        closeUsbConnections();
        onBluetoothDisabled();

        if (settingsContentObserver != null) {
            getApplicationContext().getContentResolver()
                    .unregisterContentObserver(settingsContentObserver);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (notification != null) {
            stopForeground(true);
            notification = null;
        }

        App.destroyUinput();

        NativeGpio.destroyGpios();

        stopWebserver();

        if (App.isDebug()) {
            Log.i(TAG, "destroyed");
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
                    && App.getPrefs().getBoolean("send_brightness_sensor", false)) {
                sensorLightMode = mode;
                sendDataToTarget("{lightsensormode:" + sensorLightMode + "}");
            }
        }
    }

    private static void onDataReceive(String type, byte[] bytes) {
        if (bytes.length > 0) {
            String data = new String(bytes, 0, bytes.length);
            if (App.isDebug()) {
                Log.d("Receive [ " + type + " ]", data);
            }

            receivedDataBuffer.put(type, receivedDataBuffer.get(type) + data);
            if (data.contains(App.LINE_SEPARATOR)
                    || !App.getPrefs().getBoolean(type + "DetectCommandByNewLine", false)) {
                Commands.processReceivedData(receivedDataBuffer.get(type));
                receivedDataBuffer.put(type, "");
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

    public static void updateNotificationText() {
        Resources resources = App.getContext().getResources();
        NotificationCompat.Builder notification = getNotification();

        boolean usbEnabled = App.getPrefs().getBoolean("usb", true);
        boolean bluetoothEnabled = App.getPrefs().getBoolean("bluetooth", false);


        int numberUsb = 0;
        if (openedSerialPorts != null) {
            numberUsb = openedSerialPorts.size();
        }
        String usbText = String.format(
                resources.getString(
                        R.string.notification_usb_connected_number), numberUsb);

        String bluetoothText = "";
        int numberBluetooth = 0;
        if (bt != null) {
            bluetoothText = bt.getConnectedDeviceName();
        }

        if (bluetoothText == null || bluetoothText.isEmpty()) {
            bluetoothText = resources.getString(
                    R.string.notification_bluetooth_not_connected);
        } else {
            bluetoothText = String.format(resources.getString(
                    R.string.notification_bluetooth_connected_device),
                    bluetoothText);
            numberBluetooth = 1;
        }


        if (usbEnabled) {
            notification.setContentText(usbText);

            if (bluetoothEnabled) {
                notification.setSubText(bluetoothText);
            } else {
                notification.setSubText(null);
            }
        } else if (bluetoothEnabled) {
            notification.setContentText(bluetoothText);
            notification.setSubText(null);
        } else {
            notification.setContentText(resources.getString(R.string.tap_to_open_main_activity));
            notification.setSubText(null);
        }

        NotificationManager notificationManager =
                (NotificationManager) App.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification.build());

        Intent intent = new Intent(App.ACTION_CONNECTED_DEVICES);
        intent.putExtra("all", numberUsb + numberBluetooth);
        intent.putExtra("usb", numberUsb);
        intent.putExtra("bluetooth", numberBluetooth);
        App.getContext().sendBroadcast(intent);
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

    private void connectToUsbDevice(UsbDevice device) {
        UsbSerialDevice serialPort = UsbSerialDevice.createUsbSerialDevice(device, usbManager.openDevice(device));
        if (serialPort != null) {
            if (serialPort.open()) {
                int baudRate = Integer.parseInt(App.getPrefs().getString("baudRate", "9600"));
                int dataBits = Integer.parseInt(App.getPrefs().getString("dataBits", "8"));
                int stopBits = Integer.parseInt(App.getPrefs().getString("stopBits", "1"));
                int parity = Integer.parseInt(App.getPrefs().getString("parity", "0"));
                boolean dtr = App.getPrefs().getBoolean("dtr", false);
                boolean rts = App.getPrefs().getBoolean("rts", false);

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

                serialPort.read(usbReceiveCallback);
                //serialPort.getCTS(usbCtsCallback);
                //serialPort.getDSR(usbDsrCallback);

                openedSerialPorts.put(device.getDeviceName(), serialPort);
                updateNotificationText();

                sendInfoScreenState(null);

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

            if (App.getPrefs().getBoolean("usb_reset_hub", false)) {
                if (!usbRestartState) {
                    resetUsbHubs();
                }
            } else {
                Toaster.toast("USB device not supported");
            }
        }
    }

    private UsbSerialInterface.UsbReadCallback usbReceiveCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            onDataReceive("usb", arg0);
        }
    };
//    private UsbSerialInterface.UsbCTSCallback usbCtsCallback = new UsbSerialInterface.UsbCTSCallback() {
//        @Override
//        public void onCTSChanged(boolean state) {
//            //Toaster.toast("CTS_CHANGE");
//        }
//    };
//    private UsbSerialInterface.UsbDSRCallback usbDsrCallback = new UsbSerialInterface.UsbDSRCallback() {
//        @Override
//        public void onDSRChanged(boolean state) {
//            //Toaster.toast("DSR_CHANGE");
//        }
//    };


    private void findAttachedUsbDevice() {
        boolean connectionEnabled = App.getPrefs().getBoolean("usb", false);
        String usbDeviceToConnect = App.getPrefs().getString("usbDevice", "");

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
                    && !openedSerialPorts.containsKey(usbDevice.getDeviceName())
                    && (usbDeviceToConnect.isEmpty()
                    || usbDeviceToConnect.equals(usbDevice.getDeviceName()))) {

                connectToUsbDevice(usbDevice);
            }
        }
    }

    public static void detachDevice(UsbDevice detachedDevice) {
        if (openedSerialPorts != null) {
            String detachedDeviceName = detachedDevice.getDeviceName();

            if (openedSerialPorts.containsKey(detachedDeviceName)) {
                try {
                    openedSerialPorts.get(detachedDeviceName).close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                openedSerialPorts.remove(detachedDeviceName);

                updateNotificationText();
            }
        }
    }


    public static String usbSend(String data, boolean showToast) {
        final String mode = "usb";

        if (openedSerialPorts != null && openedSerialPorts.size() > 0) {
            if (App.isDebug()) {
                Log.d(TAG, "Data to send [ " + mode + " ]: " + data);
            }

            if (App.getPrefs().getBoolean("crlf", true)) {
                data += "\r\n";
            }

            for (Map.Entry<String, UsbSerialDevice> entry : openedSerialPorts.entrySet()) {
                UsbSerialDevice serialPort = entry.getValue();

                serialPort.write(data.getBytes());
            }

            return mode;
        } else {
            if (App.isDebug()) {
                Log.w(TAG, "Can't send data [" + data + "] via " + mode + ". No connected devices");
            }

            if (showToast) {
                Toaster.toast(R.string.toast_serial_send_warning);
            }
        }

        return null;
    }
    public static String bluetoothSend(String data, boolean showToast) {
        final String mode = "bluetooth";

        if (bt != null && bt.getConnectedDeviceAddress() != null) {
            if (App.isDebug()) {
                Log.d(TAG, "Data to send [ " + mode + " ]: " + data);
            }

            bt.send(data, App.getPrefs().getBoolean("crlf", true));

            return mode;
        } else {
            if (App.isDebug()) {
                Log.w(TAG, "Can't send data [" + data + "] via " + mode + ". No connected devices");
            }

            if (showToast) {
                Toaster.toast(R.string.toast_serial_send_warning);
            }
        }

        return null;
    }

    public static void sendBy(String type, String data) {
        switch (type) {
            case "usb":
                usbSend(data, false);
                break;
            case "bluetooth":
                bluetoothSend(data, false);
                break;
            case "websocket":
                websocketSend(data);
                break;
        }
    }

    public static void sendDataToTarget(String data) {
        Pattern pattern = Pattern.compile("^(usb|bluetooth|websocket):(.+?)$");
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            sendBy(matcher.group(1), matcher.group(2));
        } else {
            usbSend(data, false);
            bluetoothSend(data, false);
            websocketSend(data);
        }
    }

    public static void resetUsbHubs() {
        try {
            File hubs = new File("/sys/bus/usb/devices").getCanonicalFile();
            if (hubs.isDirectory()) {
                usbRestartState = true;
                for (File element : hubs.listFiles()) {
                    if (!element.getName().startsWith("usb")) {
                        File cn = element.getCanonicalFile();
                        if (cn.isDirectory()) {
                            String idProduct = FileUtils.ReadFile(cn, "idProduct");
                            String idVendor = FileUtils.ReadFile(cn, "idVendor");

                            String deviceClass = String.valueOf(FileUtils.ReadFile(cn, "bDeviceClass"));
                            //Device Class 09 == Hub

                            String busnum = String.valueOf(FileUtils.ReadFile(cn, "busnum"));
                            switch (busnum.length()) {
                                case 1:
                                    busnum = "00" + busnum;
                                    break;

                                case 2:
                                    busnum = "0" + busnum;
                                    break;
                            }

                            String devnum = String.valueOf(FileUtils.ReadFile(cn, "devnum"));
                            switch (devnum.length()) {
                                case 1:
                                    devnum = "00" + devnum;
                                    break;

                                case 2:
                                    devnum = "0" + devnum;
                                    break;
                            }


                            if (!(idProduct == null || idVendor == null) && deviceClass.equals("09")) {
                                String path = "/dev/bus/usb/" + busnum + "/" + devnum;
                                resetUsbDevice(path);
                            }
                        }
                    }
                }
                usbRestartState = false;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        usbRestartState = false;
    }

    public static void startWebserver() {
        stopWebserver();

        if (App.getPrefs().getBoolean("webserver", true)) {
            webServer.get("/", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                    //request.getQuery()
                    String webSocketInfo = String.format(App.getContext().getString(R.string.websocket_info),
                            App.getIpAddress("wlan"), App.getIntPreference("webserver_port", 5000));
                    //wlan, eth, sit, lo
                    response.send("<!DOCTYPE html><head><title>SerialManager</title><meta charset=\"utf-8\" /></head><body><h1>SerialManager</h1><i>version: <b>" + App.getVersion() + "</b></i><br><br>" + webSocketInfo + "<br><br><a href=\"/test-websocket\">WebSocket test</a></body></html>");
                }
            });

            webServer.get("/test-websocket", new HttpServerRequestCallback() {
                @Override
                public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                    AssetManager assetManager = App.getContext().getAssets();

                    String html = "<h1>404 Not found</h1>";
                    InputStream input;
                    try {
                        input = assetManager.open("websoket_test.html");
                        int size = input.available();
                        byte[] buffer = new byte[size];
                        input.read(buffer);
                        input.close();

                        html = new String(buffer);
                        html = html.replace("{{address}}", App.getIpAddress("wlan") + ":" + App.getPrefs().getString("webserver_port", "5000") + "/ws");
                    } catch(IOException e) {
                        e.printStackTrace();
                    }

                    response.send(html);
                }
            });

            webServer.listen(App.getIntPreference("webserver_port", 5000));

            webServer.websocket("/ws", new AsyncHttpServer.WebSocketRequestCallback() {
                @Override
                public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
                    if (App.isDebug()) {
                        Log.d(TAG, "New WebSocket client connected");
                    }
                    webSockets.add(webSocket);

                    webSocket.setClosedCallback(new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception e) {
                            try {
                                if (e != null) {
                                    e.printStackTrace();
                                }
                            } finally {
                                webSockets.remove(webSocket);
                            }
                        }
                    });

                    webSocket.setStringCallback(new WebSocket.StringCallback() {
                        @Override
                        public void onStringAvailable(String message) {
                            if (App.isDebug()) {
                                Log.d(TAG, "Receive from WebSocket: " + message);
                            }

                            Commands.processReceivedData(message);
                        }
                    });

                }
            });
        }
    }

    private static void stopWebserver() {
        if (webSockets != null) {
            for (WebSocket socket : webSockets) {
                socket.close();
            }
        }

        if (webServer != null) {
            webServer.stop();
            AsyncServer.getDefault().stop();
        }
    }

    public static void websocketSend(String message) {
        for (WebSocket socket : webSockets) {
            socket.send(message);
        }
    }




    public static void closeUsbConnections() {
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
            openedSerialPorts = null;
        }
    }


    public static void onBluetoothDisabled() {
        if (bt != null) {
            bt.stopAutoConnect();
//            if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
//                bt.disconnect();
//            }
            bt.stopService();

            bt = null;
        }
    }

    public static void onBluetoothEnabled() {
        if (App.getPrefs().getBoolean("bluetooth", false)) {
            if (bt == null) {
                bt = new BluetoothSPP(App.getContext());
                bt.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
                    public void onDataReceived(byte[] arg0, String message) {
                        onDataReceive("bluetooth", arg0);
                    }
                });
            }

            if (!bt.isBluetoothAvailable()) {
                Toaster.toast("Bluetooth is not available");
                onBluetoothDisabled();
            } else {
                if (bt.isBluetoothEnabled()) {
                    if (!bt.isServiceAvailable()) {
                        bt.startService(BluetoothState.DEVICE_OTHER);
                        bt.setBluetoothStateListener(new BluetoothSPP.BluetoothStateListener() {
                            public void onServiceStateChanged(int state) {
                                updateNotificationText();

                                if (state == BluetoothState.STATE_CONNECTED) {
                                    sendInfoScreenState(null);
                                }
                            }
                        });
                    }

                    connectToBluetoothDevice();

                } else if (App.getPrefs().getBoolean("bluetoothAutoEnable", true)) {
                    bt.enable();

                } else {
                    Toaster.toast("Bluetooth is disabled");
                    onBluetoothDisabled();
                }
            }
        }
    }

    private static void connectToBluetoothDevice() {
        if (App.getPrefs().getBoolean("bluetooth", false)) {
            if (bt == null || !bt.isServiceAvailable()) {
                onBluetoothEnabled();
            } else {
                String deviceAddress = App.getPrefs().getString("bluetoothDevice", "");
                if (deviceAddress.isEmpty()) {
                    Toaster.toast("Choose Bluetooth device to connect");
                } else if (bt.getServiceState() != BluetoothState.STATE_CONNECTING
                        && bt.getServiceState() != BluetoothState.STATE_CONNECTED) {
                    BluetoothDevice bluetoothDevice =
                            bt.getBluetoothAdapter().getRemoteDevice(deviceAddress);

                    if (bluetoothDevice != null) {
                        bt.autoConnect(bluetoothDevice.getAddress());
                    } else {
                        Toaster.toast("Choose Bluetooth device to connect");
                    }
                }
            }
        }
    }



    public static void sendInfoScreenState(String state) {
        if (App.getPrefs().getBoolean("send_screen_state", false)) {
            if (state == null) {
                state = App.isScreenOn() ? "on" : "off";
            }

            ConnectionService.sendDataToTarget(String.format(
                    App.getContext().getString(R.string.send_data_screen_state), state));
        }
    }
}