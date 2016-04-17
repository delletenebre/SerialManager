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
    public static final String MY_BROADCAST_INTENT = "kg.delletenebre.serial.NEW_DATA";
    public static final String WIDGET_SEND_ACTION = "kg.delletenebre.serial.SEND_DATA";
    public static final String SERVICE_SEND_ACTION_COMPLETE = "kg.delletenebre.serial.SEND_DATA_COMPLETE";
    private static boolean DEBUG;

    protected static SerialService service;

    private SharedPreferences settings;
    private EventsReceiver receiver;

    private static List<Command> commands;

    private static JSONArray jsonDevices;

    private static UsbSerialDevice serial;


    public static void start(Context context, UsbDevice device, UsbDeviceConnection connection) {
        if (service != null) {
            service.stopSelf();
        }

        if (device != null && connection != null) {
            serial = UsbSerialDevice.createUsbSerialDevice(device, connection);

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
                for (UsbDevice device: usbManager.getDeviceList().values()) {
                    boolean vidCheck = false;
                    boolean pidCheck = false;

                    for (int i = 0; i < jsonDevices.length(); i++) {
                        JSONObject json = jsonDevices.getJSONObject(i);

                        vidCheck = !json.has("vid")||(device.getVendorId() == json.getInt("vid"));
                        pidCheck = !json.has("pid")||(device.getProductId() == json.getInt("pid"));

                        if (vidCheck && pidCheck) {
                            break;
                        }
                    }

                    if (vidCheck && pidCheck && usbManager.hasPermission(device)) {
                        UsbDeviceConnection connection = usbManager.openDevice(device);
                        if (connection != null) {
                            start(context, device, connection);
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

            if (commands == null) {
                commands = initializeCommands(this);
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

            Intent i = new Intent(SERVICE_SEND_ACTION_COMPLETE);
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
                    if (!detectCommand(key, val)) {
                        sendCommandBroadcast(key, val);
                    }
                }
            }
        }
    };

    private void sendCommandBroadcast(String key, String value) {
        Intent i = new Intent(MY_BROADCAST_INTENT);
        i.putExtra("key", key);
        i.putExtra("value", value);
        sendBroadcast(i);
    }

    private boolean detectCommand(String key, String value) {
        boolean result = false;
        if (commands != null) {
            int i = 0;
            for (; i < commands.size(); i++) {
                Command command = commands.get(i);
                boolean inRange = false;
                if (NumberUtils.isNumber(command.value) && NumberUtils.isNumber(command.scatter) && NumberUtils.isNumber(value)) {
                    float commandScatter = Float.parseFloat(command.scatter);
                    float commandValue = Float.parseFloat(command.value);
                    float receivedValue = Float.parseFloat(value);

                    inRange = commandValue - commandScatter <= receivedValue && receivedValue <= commandValue + commandScatter;
                }

                if (command.key.equals(key) && (command.value.equals(value) || inRange)) {

                    if (command.actionCategoryId == 0) { // No action
                        // Nothing to do

                    } else if (command.actionCategoryId == 1) { // Navigation

                        switch (command.action) {
                            case "0":
                                App.emulateKeyEvent(KeyEvent.KEYCODE_BACK);
                                break;

                            case "1":
                                App.emulateKeyEvent(KeyEvent.KEYCODE_HOME);
                                break;

                        }

                    } else if (command.actionCategoryId == 2) { // Volume

                        switch (command.action) {
                            case "0":
                                App.changeVolume("UP");
                                break;

                            case "1":
                                App.changeVolume("DOWN");
                                break;

                            case "2":
                                App.setMute();
                                break;
                        }

                    } else if (command.actionCategoryId == 3) { // Media

                        switch (command.action) {
                            case "0":
                                App.emulateMediaButton(this, KeyEvent.KEYCODE_MEDIA_REWIND);
                                break;

                            case "1":
                                App.emulateMediaButton(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                                break;

                            case "2":
                                App.emulateMediaButton(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                                break;

                            case "3":
                                App.emulateMediaButton(this, KeyEvent.KEYCODE_MEDIA_NEXT);
                                break;

                            case "4":
                                App.emulateMediaButton(this, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                                break;
                        }

                    } else if (command.actionCategoryId == 4) { // Application

                        Intent intent = AppChooserPreference.getIntentValue(
                                command.action, null);

                        if (intent == null) {
                            intent = new Intent(this, MainActivity.class);
                        }

                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);

                    }

                    result = !command.isThrough;
                    break;
                }
            }
        }

        return result;
    }

    public static void setCommands(List<Command> commands) {
        SerialService.commands = commands;
    }

    public static List<Command> initializeCommands(Context context) {
        List<Command> commands = new ArrayList<>();

        File prefsDir = new File(context.getFilesDir().getParent(), "/shared_prefs/");
        if (prefsDir.exists()) {
            for (File f : prefsDir.listFiles()) {
                if (f.isFile()) {
                    String uuid = f.getName();
                    uuid = uuid.substring(0, uuid.length() - 4);
                    if (Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", uuid)) {
                        SharedPreferences settings = context.getSharedPreferences(uuid, MODE_PRIVATE);
                        int id = settings.getInt("id", -1);

                        String key = settings.getString("key", "");
                        String value = settings.getString("value", "");
                        String scatter = settings.getString("scatter", "");
                        boolean isThrough = settings.getBoolean("is_through", false);
                        int actionCategoryId =
                                Integer.parseInt(settings.getString("action_category", "0"));

                        String actionNavigation = settings.getString("action_navigation", "0");

                        String actionVolume = settings.getString("action_volume", "0");

                        String actionMedia = settings.getString("action_media", "0");


                        String actionApp = settings.getString("action_application", "");
                        String actionApplication = (!actionApp.isEmpty())
                                ? actionApp
                                : context.getString(R.string.pref_shortcut_default);

                        String action = getActionByActionCategoryId(context, actionCategoryId,
                                actionNavigation, actionVolume, actionMedia, actionApplication);

                        commands.add(new Command(id, uuid, key, value, scatter, isThrough,
                                actionCategoryId, action));
                    }

                }
            }
        }

        return commands;
    }

    public static String getActionByActionCategoryId(Context context, int id,
                                                     String navigation, String volume,
                                                     String media, String app) {
        switch (id) {
            case 0:
                return context.getString(R.string.no_action);

            case 1:
                return navigation;

            case 2:
                return volume;

            case 3:
                return media;

            case 4:
                return app;
        }

        return "";
    }


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
