package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

//import com.squareup.leakcanary.LeakCanary;

import java.io.IOException;

public class App extends Application {
    public static final String TAG = "SerialManagerApp";
    public static final String ACTION_NEW_DATA_RECEIVED = "kg.delletenebre.serial.NEW_DATA";
    public static final String ACTION_SEND_DATA = "kg.delletenebre.serial.SEND_DATA";
    public static final String ACTION_SEND_DATA_COMPLETE = "kg.delletenebre.serial.SEND_DATA_COMPLETE";
    public static final String ACTION_SEND_DATA_SUCCESS = "kg.delletenebre.serial.SEND_DATA_SUCCESS";
    public static final String ACTION_EXTERNAL_SEND = "serial.manager.send";

    protected static final String ACTION_USB_PERMISSION = "kg.delletenebre.serial.usb_permission";
    protected static final String ACTION_USB_ATTACHED = "kg.delletenebre.serial.usb_attached";
    protected static final String ACTION_USB_DETACHED = "kg.delletenebre.serial.usb_detached";
    public static final int REQUEST_CODE_ASK_PERMISSIONS_READ = 86;
    public static final int REQUEST_CODE_ASK_PERMISSIONS_WRITE = 87;
    public static final int REQUEST_CODE_UPDATE_COMMAND = 88;
    protected static final int START_SERVICE_DELAY = 1000;
    protected static final int BLUETOOTH_RECONNECT_DELAY = 3000;

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static final int VOLUME_STREAM = AudioManager.STREAM_MUSIC;
    private static AudioManager audioManager;
    private static int lastVolumeLevel = 1;

    private static SharedPreferences prefs;
    public static SharedPreferences getPrefs() {
        return prefs;
    }

    private static Context context;
    public static Context getContext() {
        return context;
    }

    private static boolean debug;
    public static boolean isDebug() {
        return debug;
    }

    private static Activity aliveActiviity;

    private static boolean volumeShowUI;

    @Override
    public void onCreate() {
        super.onCreate();
        //LeakCanary.install(this);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        context = this;

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateSettings();

        getContext().startService(new Intent(getContext(), ConnectionService.class));
    }

    public static Activity getAliveActivity() {
        return aliveActiviity;
    }
    public static void setAliveActivity(Activity activity) {
        aliveActiviity = activity;
    }
    public static void restartAliveActivity() {
        Activity activity = aliveActiviity;
        if (activity != null) {
            activity.finish();
            Intent intent = new Intent(getContext(), activity.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }
    }


    public static void updateSettings() {
        volumeShowUI = prefs.getBoolean("volumeShowUI", true);

        debug = prefs.getBoolean("debug", false);
    }

    public static boolean isNumber(String string) {
        return string.matches("-?\\d+(\\.\\d+)?");
    }

    public static void changeVolume(String mode) {
        audioManager.adjustStreamVolume(VOLUME_STREAM,
                (mode.equalsIgnoreCase("up"))
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                volumeShowUI ? AudioManager.FLAG_SHOW_UI : 0);
    }

    public static void setMute() {
        int currentVolumeLevel = audioManager.getStreamVolume(VOLUME_STREAM);
        boolean muteState = !(currentVolumeLevel == 0);

        if (muteState) {
            lastVolumeLevel = currentVolumeLevel;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.adjustStreamVolume(VOLUME_STREAM,
                    (muteState) ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                    volumeShowUI ? AudioManager.FLAG_SHOW_UI : 0);
        } else {
            audioManager.setStreamVolume(VOLUME_STREAM,
                    (muteState) ? 0 : lastVolumeLevel,
                    volumeShowUI ? AudioManager.FLAG_SHOW_UI : 0);
            //mAudioManager.setStreamMute(VOLUME_STREAM, muteState);
        }
    }

    public static void emulateMediaButton(int buttonCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            audioManager.dispatchMediaKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, buttonCode));
            audioManager.dispatchMediaKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_UP, buttonCode));
        } else {
            Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, buttonCode));
            context.sendOrderedBroadcast(downIntent, null);

            Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, buttonCode));
            context.sendOrderedBroadcast(upIntent, null);
        }
    }

    public static void emulateKeyEvent(final String keyEvent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDebug()) {
                    Log.d(TAG, "input keyevent " + keyEvent);
                }
                try {
                    Runtime.getRuntime().exec(new String[] {"su", "-c", "input keyevent " + keyEvent});
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public static void runShellCommand(final String command) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDebug()) {
                    Log.d(TAG, "run shell: " + command);
                }
                try {
                    Runtime.getRuntime().exec(new String[] {"su", "-c", command});
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public static boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) getContext().getSystemService(POWER_SERVICE);
        return  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                    && powerManager.isInteractive())
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH
                    && powerManager.isScreenOn());
    }

    public static boolean isScreenOff() {
        return !isScreenOn();
    }

    public static int getScreenBrightness() {

        if (isScreenBrightnessModeManual()) {
            return Settings.System.getInt(App.getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
        }

        return -1;
    }

    public static void setScreenBrightness(Object value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(getContext())) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                return;
            }
        }

        if (!isScreenBrightnessModeManual()) {
            Settings.System.putInt(App.getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

        boolean isPercent = false;
        float level;
        String stringValue = String.valueOf(value);

        if (!stringValue.isEmpty()) {
            if (stringValue.charAt(stringValue.length() - 1) == '%') {
                stringValue = stringValue.substring(0, stringValue.length() - 1);
                isPercent = true;
            }

            try {
                level = Float.parseFloat(stringValue);
            } catch (Exception ex) {
                Log.e(TAG, "Error while setting brightness level: " + ex.getLocalizedMessage());
                return;
            }

            if (isPercent) {
                level = 255 / 100.0f * level;
            }

            if (level > 255) {
                level = 255;
            } else if (level < 5) {
                level = 5;
            }

            Settings.System.putInt(App.getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, (int) level);

            if (isDebug()) {
                Log.i(TAG, "Screen brightness set to " + (int) level);
            }
        }
    }

    public static boolean isScreenBrightnessModeManual() {
        return (Settings.System.getInt(App.getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                            == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    public static int getStatusBarHeight() {
        Context context = App.getContext();
        int result = 0;
        int resourceId = context.getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

}
