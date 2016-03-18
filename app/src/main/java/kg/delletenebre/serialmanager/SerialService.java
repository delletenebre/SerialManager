package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class SerialService extends Service {
    private final String TAG = getClass().getSimpleName();
    private static boolean DEBUG;

    protected static Service service;

    private SharedPreferences _settings;
    private EventsReceiver receiver;
    public static final Intent DEFAULT_INTENT = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/"));

    private String firstPartString = "";

    private static List<Command> commands;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped");
                    stopSelf();
                }

                @Override
                public void onNewData(final byte[] data) {
                    updateReceivedData(data);
                }
            };

    private static UsbSerialPort sPort = null;
    private static UsbDeviceConnection sConnection = null;

    public static void start(Context context, UsbDeviceConnection connection, UsbSerialPort port) {
        sConnection = connection;
        sPort = port;
        context.startService(new Intent(context, SerialService.class));
    }


    @Override
    public void onCreate() {
        super.onCreate();

        _settings = PreferenceManager.getDefaultSharedPreferences(this);
        DEBUG = _settings.getBoolean("debug", false);

        service = this;

        receiver = new EventsReceiver();
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, intentFilter);

        if (sPort == null || sConnection == null) {
            Log.w(TAG, "No serial device or connection failed");
            stopSelf();
        } else {
            try {
                sPort.open(sConnection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                sPort.setDTR(_settings.getBoolean("dtr", false));
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sConnection = null;
                sPort = null;
                stopSelf();
            }
        }

        if (commands == null) {
            commands = initializeCommands(this);
        }

        App.initVolume(this);

        onDeviceStateChange();

        if (DEBUG) {
            Log.d(TAG, "Service successfully CREATED");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (receiver != null) {
            unregisterReceiver(receiver);
        }

        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
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

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        DEBUG = _settings.getBoolean("debug", false);
        String sData = new String(data, Charset.forName("UTF8"));
        int start = sData.indexOf("<");
        int end = sData.indexOf(">");

        if (DEBUG) {
            Log.d("ReceivedData: ", sData);
        }

        if ( (start > -1 && end > -1 && start < end && (start + 1) != end)
                || (!firstPartString.isEmpty() && end > -1) ) {

            if (!firstPartString.isEmpty()) {
                sData = firstPartString + sData;

                start = sData.indexOf("<");
                end = sData.indexOf(">");

                firstPartString = "";
            }

            sData = sData.substring(start + 1, end);

            if (sData.contains(":")) {
                String[] key_val = sData.split(":");
                final String key = key_val[0];
                final String val = key_val[1];

                final Activity activity = App.getAliveActivity();

                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(activity, String.format(
                                    getResources().getString(R.string.toast_received_command),
                                    key, val), Toast.LENGTH_SHORT).show();
                        }
                    });

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
        } else if (firstPartString.isEmpty() && start > -1) {
            firstPartString = sData;
        } else {
            firstPartString = "";
        }
    }

    private void sendCommandBroadcast(String key, String value) {
        Intent i = new Intent("kg.delletenebre.serial.NEW_DATA");
        i.putExtra("KEY", key);
        i.putExtra("VALUE", value);
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
                    } else if (command.actionCategoryId == 1) { // Volume
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
                    } else if (command.actionCategoryId == 2) { // Media


                        switch (command.action) {
                            case "0":
                                App.emulateButton(this, KeyEvent.KEYCODE_MEDIA_REWIND);
                                break;

                            case "1":
                                App.emulateButton(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                                break;

                            case "2":
                                App.emulateButton(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                                break;

                            case "3":
                                App.emulateButton(this, KeyEvent.KEYCODE_MEDIA_NEXT);
                                break;

                            case "4":
                                App.emulateButton(this, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                                break;
                        }


                    } else if (command.actionCategoryId == 3) { // Application
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
                    String filename = f.getName();
                    filename = filename.substring(0, filename.length() - 4);
                    if (Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", filename)) {
                        SharedPreferences settings = context.getSharedPreferences(filename, MODE_PRIVATE);
                        int id = settings.getInt("id", -1);
                        String uuid = filename;
                        String key = settings.getString("key", "");
                        String value = settings.getString("value", "");
                        String scatter = settings.getString("scatter", "");
                        boolean isThrough = settings.getBoolean("is_through", false);
                        int actionCategoryId =
                                Integer.parseInt(settings.getString("action_category", "0"));

                        String actionVolume = settings.getString("action_volume", "0");

                        String actionMedia = settings.getString("action_media", "0");


                        String actionApp = settings.getString("action_application", "");
                        String actionApplication = (!actionApp.isEmpty())
                                ? actionApp
                                : context.getString(R.string.pref_shortcut_default);

                        String action = getActionByActionCategoryId(context, actionCategoryId,
                                actionVolume, actionMedia, actionApplication);

                        commands.add(new Command(id, uuid, key, value, scatter, isThrough,
                                actionCategoryId, action));
                    }

                }
            }
        }

        return commands;
    }

    public static String getActionByActionCategoryId(Context context, int id,
                                                     String volume, String media, String app) {
        switch (id) {
            case 0:
                return context.getString(R.string.no_action);

            case 1:
                return volume;

            case 2:
                return media;

            case 3:
                return app;
        }

        return "";
    }
}
