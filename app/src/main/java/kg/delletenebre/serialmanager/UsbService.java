package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xdroid.toaster.Toaster;

public class UsbService extends Service {
    private static final String TAG = "UsbService";

    protected static UsbService service;
    private static JSONArray jsonDevices;
    private static String connectedDeviceName;
    private static UsbDevice connectedDevice;

    private IBinder binder = new UsbBinder();

    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;

    private EventsReceiver receiver;
    private SharedPreferences settings;




    public static String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public static void start(final UsbDevice device) {
        UsbService.stop();

        final Handler h = new Handler();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (service != null) {
                    h.postDelayed(this, App.START_SERVICE_DELAY);
                } else {
                    Context context = App.getAppContext();
                    if (device != null) {
                        connectedDevice = device;
                        connectedDeviceName = device.getDeviceName();
                        context.startService(new Intent(context, UsbService.class));
                    }
                }
            }
        };
        h.postDelayed(r, App.START_SERVICE_DELAY);

    }

    public static void stop() {
        if (service != null) {
            service.stopSelf();
        }
    }


    public static void initializeJsonDevices() {
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
        Context context = App.getAppContext();
        String json = null;

        try {
            InputStream is = context.getAssets().open("devices.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, Charset.forName("UTF8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return json;
    }

    public static void restart() {
        Context context = App.getAppContext();

        initializeJsonDevices();

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean connectionEnabled = prefs.getBoolean("usb", false);
        String usbDeviceToConnect = prefs.getString("usbDevice", "");

        for (UsbDevice usbDevice: usbManager.getDeviceList().values()) {
            boolean vidCheck = false;
            boolean pidCheck = false;

//            int deviceVID = usbDevice.getVendorId();
//            int devicePID = usbDevice.getProductId();

            for (int i = 0; i < jsonDevices.length(); i++) {
                try {
                    JSONObject json = jsonDevices.getJSONObject(i);

                    vidCheck = !json.has("vid")||(usbDevice.getVendorId() == json.getInt("vid"));
                    pidCheck = !json.has("pid")||(usbDevice.getProductId() == json.getInt("pid"));

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
                UsbService.start(usbDevice);
                break;
            } else {
                UsbService.stop();
            }
        }

    }

    /*
     * This function will be called to write data through Serial Port
     */
    public void sendFromWidget(String data, int widgetId) {
        if (serialPort != null && settings != null) {
            if (App.getDebug()) {
                Log.d(TAG, "Data to send: " + data);
            }

            if (settings.getBoolean("crlf", true)) {
                data += "\r\n";
            }
            serialPort.write(data.getBytes());

            Intent i = new Intent(App.ACTION_SEND_DATA_COMPLETE);
            i.putExtra("widgetId", widgetId);
            sendBroadcast(i);

        } else if (App.getDebug()) {
            Toaster.toast(R.string.toast_serial_send_warning);
            Log.w(TAG, "Can not send data [" + data + "]. Serial port is null");
        }
    }

    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */
    private UsbSerialInterface.UsbReadCallback receiveCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = new String(arg0, Charset.forName("UTF8"));
            if(App.getDebug()) {
                Log.d("Receive USB", data);
            }

            Commands.processReceivedData(data);
        }
    };

    /*
     * State changes in the CTS line will be received here
     */
    private UsbSerialInterface.UsbCTSCallback ctsCallback = new UsbSerialInterface.UsbCTSCallback() {
        @Override
        public void onCTSChanged(boolean state) {
            Toaster.toast("CTS_CHANGE");
        }
    };

    /*
     * State changes in the DSR line will be received here
     */
    private UsbSerialInterface.UsbDSRCallback dsrCallback = new UsbSerialInterface.UsbDSRCallback() {
        @Override
        public void onDSRChanged(boolean state) {
            Toaster.toast("DSR_CHANGE");
        }
    };

//    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (action.equals(ACTION_USB_PERMISSION)) {
//                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
//                if (granted) { // User accepted our USB connection. Try to open the device as a serial port
//                    context.sendBroadcast(new Intent(ACTION_USB_PERMISSION_GRANTED));
//                    connection = usbManager.openDevice(device);
//                    serialPortConnected = true;
//                    new ConnectionThread().run();
//                } else { // User not accepted our USB connection. Send an Intent to the Main Activity
//                    context.sendBroadcast(new Intent(ACTION_USB_PERMISSION_NOT_GRANTED));
//                }
//            } else if (action.equals(ACTION_USB_ATTACHED)) {
//                if (!serialPortConnected) {
//                    findSerialPortDevice(); // A USB device has been attached. Try to open it as a Serial port
//                }
//            } else if (action.equals(ACTION_USB_DETACHED)) {
//                // Usb device was disconnected. send an intent to the Main Activity
//                context.sendBroadcast(new Intent(ACTION_USB_DISCONNECTED));
//                serialPortConnected = false;
//                serialPort.close();
//            }
//        }
//    };

    /*
     * onCreate will be executed when service is started
     */
    @Override
    public void onCreate() {
        if (connectedDevice == null) {
            stopSelf();
        } else {
            service = this;
            settings = PreferenceManager.getDefaultSharedPreferences(this);
            receiver = new EventsReceiver();
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(receiver, intentFilter);

            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            device = connectedDevice;
            connection = usbManager.openDevice(device);
            new ConnectionThread().run();

            if (App.getDebug()) {
                Log.d(TAG, "CREATED");
            }
        }
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (serialPort != null) {
            serialPort.close();
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        connectedDevice = null;
        connectedDeviceName = null;
        service = null;

        if (App.getDebug()) {
            Log.d(TAG, "DESTROYED");
        }
    }

//    private void findSerialPortDevice() {
//        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
//        boolean keep = true;
//        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
//            device = usbDevice;
//            int deviceVID = device.getVendorId();
//            int devicePID = device.getProductId();
//
//            if (deviceVID != 0x1d6b && (devicePID != 0x0001 || devicePID != 0x0002 || devicePID != 0x0003)) {
//                // There is a device connected to our Android device. Try to open it as a Serial Port.
//                requestUserPermission();
//                keep = false;
//            } else {
//                connection = null;
//                device = null;
//            }
//
//            if (!keep) {
//                break;
//            }
//        }
//
//
//            Toaster.toast("No USB connected");
//    }

    public class UsbBinder extends Binder {
        public UsbService getService() {
            return UsbService.this;
        }
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. Moving usb operations away from UI thread is a good thing.
     */
    public class ConnectionThread extends Thread {
        @Override
        public void run() {
            if (device != null && connection != null) {
                serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                if (serialPort != null) {
                    if (serialPort.open()) {
                        int baudRate = Integer.parseInt(settings.getString("baudRate", "9600"));
                        int dataBits = Integer.parseInt(settings.getString("dataBits", "8"));
                        int stopBits = Integer.parseInt(settings.getString("stopBits", "1"));
                        int parity = Integer.parseInt(settings.getString("parity", "0"));
                        boolean dtr = settings.getBoolean("dtr", false);
                        boolean rts = settings.getBoolean("rts", false);

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
                        serialPort.getCTS(ctsCallback);
                        serialPort.getDSR(dsrCallback);
                        // Everything went as expected. Send an intent to MainActivity
                        Toaster.toast("USB device connected");
                    } else {
                        // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                        if (serialPort instanceof CDCSerialDevice) {
//                            Intent intent = new Intent(ACTION_CDC_DRIVER_NOT_WORKING);
//                            context.sendBroadcast(intent);
                        } else {
//                            Intent intent = new Intent(ACTION_USB_DEVICE_NOT_WORKING);
//                            context.sendBroadcast(intent);
                        }
                    }
                } else {
                    // No driver for given device, even generic CDC driver could not be loaded
                    Toaster.toast("USB device not supported");
                }
            } else {
                UsbService.restart();
            }
        }
    }

    public void setDTR(boolean state) {
        if (serialPort != null) {
            serialPort.setDTR(state);
        }
    }

    public void setRTS(boolean state) {
        if (serialPort != null) {
            serialPort.setRTS(state);
        }
    }
}
