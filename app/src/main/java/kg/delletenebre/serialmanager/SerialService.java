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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xdroid.toaster.Toaster;

public class SerialService extends Service {
    private final static String TAG = "SerialService";
    private static boolean DEBUG;

    protected static SerialService service;

    private static String connectedDevice;
    public static String getConnectedDevice() {
        return connectedDevice;
    }

    private SharedPreferences settings;
    private EventsReceiver receiver;

    private static JSONArray jsonDevices;

    private static UsbSerialDevice serial;


    public static void start(Context context, UsbDevice device, UsbDeviceConnection connection) {
        if (service != null) {
            service.stopSelf();
        }

        if (device != null && connection != null) {
            serial = UsbSerialDevice.createUsbSerialDevice(device, connection);

            connectedDevice = device.getDeviceName();
            context.startService(new Intent(context, SerialService.class));
        }
    }



    public static void restart(Context context) {
        if (jsonDevices == null) {
            getJSONDevices(context);
        }

        if (jsonDevices != null) {
            try {
                UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String connectionType = prefs.getString("connectionType", "USB");
                String deviceToConnect = prefs.getString("device", "");

                for (UsbDevice usbDevice: usbManager.getDeviceList().values()) {
                    boolean vidCheck = false;
                    boolean pidCheck = false;

                    for (int i = 0; i < jsonDevices.length(); i++) {
                        JSONObject json = jsonDevices.getJSONObject(i);

                        vidCheck = !json.has("vid")||(usbDevice.getVendorId() == json.getInt("vid"));
                        pidCheck = !json.has("pid")||(usbDevice.getProductId() == json.getInt("pid"));

                        if (vidCheck && pidCheck) {
                            break;
                        }
                    }

                    if (vidCheck && pidCheck
                            && usbManager.hasPermission(usbDevice)
                            && connectionType.equalsIgnoreCase("usb")
                            && (deviceToConnect.isEmpty()
                                || deviceToConnect.equals(usbDevice.getDeviceName()))) {
                        UsbDeviceConnection connection = usbManager.openDevice(usbDevice);
                        if (connection != null) {
                            start(context, usbDevice, connection);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();

        if (serial == null) {//sPort == null || sConnection == null) {
            Log.w(TAG, "No serial device or connection failed");

            stopSelf();
        } else {
            settings = PreferenceManager.getDefaultSharedPreferences(this);
            DEBUG = settings.getBoolean("debug", false);

            service = this;

            receiver = new EventsReceiver();
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(receiver, intentFilter);

            int baudRate = Integer.parseInt(settings.getString("baudRate", "9600"));
            int dataBits = Integer.parseInt(settings.getString("dataBits", "8"));
            int stopBits = Integer.parseInt(settings.getString("stopBits", "1"));
            int parity   = Integer.parseInt(settings.getString("parity", "0"));
            boolean dtr  =  settings.getBoolean("dtr", false);
            boolean rts  =  settings.getBoolean("rts", false);

            serial.open();
            serial.setBaudRate(baudRate);
            serial.setDataBits(dataBits);
            serial.setStopBits(stopBits);
            serial.setParity(parity);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            serial.setDTR(dtr);
            serial.setRTS(rts);

            try {
                serial.read(readCallback);
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
                restart(this);
            }

            if (DEBUG) {
                Log.d(TAG, "Service successfully CREATED");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (receiver != null) {
            unregisterReceiver(receiver);
        }

        if (serial != null) {
            serial.close();
        }

        connectedDevice = null;
        service = null;

        if (DEBUG) {
            Log.d(TAG, "Service DESTROYED");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public static void setDTR(boolean state) {
        if (serial != null) {
            serial.setDTR(state);
        }
    }

    public static void setRTS(boolean state) {
        if (serial != null) {
            serial.setRTS(state);
        }
    }

    public static void setDEBUG(boolean state) {
        DEBUG = state;
    }

    public void write(String data) {
        if (serial != null && settings != null) {
            if (DEBUG) {
                Log.d(TAG, "Data to send: " + data);
            }

            if (settings.getBoolean("crlf", true)) {
                data += "\r\n";
            }
            serial.write(data.getBytes());

        } else if (DEBUG) {
            Toaster.toast(R.string.toast_serial_send_warning);
            Log.w(TAG, "Can not send data [" + data + "]. Service is null");
        }
    }

    public void writeFromWidget(String data, int widgetId) {
        String originalData = data;
        if (serial != null && settings != null) {
            if (DEBUG) {
                Log.d(TAG, "Data to send: " + data);
            }

            if (settings.getBoolean("crlf", true)) {
                data += "\r\n";
            }
            serial.write(data.getBytes());

            Intent i = new Intent(App.ACTION_SEND_DATA_COMPLETE);
            i.putExtra("data", originalData);
            i.putExtra("widgetId", widgetId);
            sendBroadcast(i);

        } else if (DEBUG) {
            Toaster.toast(R.string.toast_serial_send_warning);
            Log.w(TAG, "Can not send data [" + data + "]. Service is null");
        }
    }

    private UsbSerialInterface.UsbReadCallback readCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            String sData = new String(arg0, Charset.forName("UTF8"));
            final Pattern pattern = Pattern.compile("<(.+?):(.+?)>");
            final Matcher matcher = pattern.matcher(sData);
            if (matcher.find()) {
                final String key = matcher.group(1);
                final String val = matcher.group(2);

                final Activity activity = App.getAliveActivity();

                if (activity != null) {
                    Toaster.toast(String.format(
                            getResources().getString(R.string.toast_received_command),
                            key, val));

                    if (CommandSettingsActivity.autosetPreference != null
                            && CommandSettingsActivity.autosetPreference.isChecked()
                            && CommandSettingsActivity.keyPreference != null
                            && CommandSettingsActivity.valuePreference != null) {

                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                CommandSettingsActivity.keyPreference.setText(key);
                                CommandSettingsActivity.keyPreference.setSummary(key);

                                CommandSettingsActivity.valuePreference.setText(val);
                                CommandSettingsActivity.valuePreference.setSummary(val);
                            }
                        });
                    }

                } else {
                    Commands.detectCommand(key, val);
                }
            }
        }
    };

    public static JSONArray getJSONDevices(Context context) {
        if (jsonDevices == null) {
            try {
                String jsonDevicesFromAssets = loadJSONFromAsset(context);
                if (jsonDevicesFromAssets != null) {
                    jsonDevices = new JSONArray(jsonDevicesFromAssets);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        return jsonDevices;
    }

    public static String loadJSONFromAsset(Context context) {
        String json = null;

        if (context != null) {
            try {
                InputStream is = context.getAssets().open("devices.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                json = new String(buffer, "UTF-8");
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
                return null;
            }
        }
        return json;
    }
}
