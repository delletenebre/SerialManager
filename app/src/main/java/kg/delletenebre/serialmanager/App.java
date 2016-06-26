package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;

//import com.squareup.leakcanary.LeakCanary;

import com.stericson.RootShell.RootShell;
import com.stericson.RootShell.execution.Command;

import java.io.Externalizable;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.Map;

import kg.delletenebre.serialmanager.Commands.Commands;
import xdroid.toaster.Toaster;

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

    private static String uinputDevice;
    private static Integer uinputId;
    public static void createUinput() {
        if (uinputId == null) {
            uinputId = initializateUinput();
            if (uinputId > 0 && isDebug()) {
                Toaster.toast("Виртуальная клавиатура создана");
                Log.d(TAG, "Virtual keyboard (uinput) id: " + String.valueOf(uinputId));
            }
        }
    }
    public static void destroyUinput() {
        if (uinputId != null) {
            destroyUinput(uinputId);
            uinputId = null;
            uinputDevice = null;
        }
    }

    static {
        System.loadLibrary("serial-manager");
    }
    private static native int initializateUinput();
    private static native int destroyUinput(int fd);
    private static native void sendEvent(int fd, int code);
    private static native void sendEventDouble(int fd, int code1, int code2);

    @Override
    public void onCreate() {
        super.onCreate();
        //LeakCanary.install(this);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        context = this;

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateSettings();

        initializeKeymap();

        if (RootShell.isRootAvailable() && RootShell.isAccessGiven()) {
            Log.d(TAG, "Root access granted");
        } else {
            Log.d(TAG, "Root not available or access didn't grant");
        }

        if (App.isScreenOn() || !App.getPrefs().getBoolean("stopWhenScreenOff", true)) {
            context.startService(new Intent(context, ConnectionService.class));
        }
    }

    public static Activity getAliveActivity() {
        return aliveActiviity;
    }
    public static void setAliveActivity(Activity activity) {
        aliveActiviity = activity;
    }
//    public static void restart() {
//        Activity activity = aliveActiviity;
//        if (activity != null) {
////            activity.finish();
////            Intent intent = new Intent(getContext(), activity.getClass());
////            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////            getContext().startActivity(intent);
//            // Schedule start
//            PendingIntent pi = PendingIntent.getActivity(
//                    context, 0, activity.getIntent(), PendingIntent.FLAG_CANCEL_CURRENT);
//            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).set(
//                    AlarmManager.RTC, System.currentTimeMillis() + 500, pi);
//
//            // Stop now
//            System.exit(0);
//        }
//
//
//    }


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

    public static void emulateKeyEvent(final String keyName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDebug()) {
                    Log.d(TAG, "Emulated key is " + keyName);
                }

                KeyboardCode keyboardCode = keymap.get(keyName);
                if (keyboardCode != null) {
                    int linuxCode = keyboardCode.linuxCode;
                    int[] linuxCodes = keyboardCode.linuxCodes;
                    int androidCode = keyboardCode.androidCode;

                    if (uinputId != null && (linuxCode > 0 || linuxCodes != null)) {
                        if (linuxCode > 0) {
                            if (isDebug()) {
                                Log.d(TAG, "Emulated key in fast mode");
                            }
                            sendEvent(uinputId, linuxCode);
                        } else {
                            if (isDebug()) {
                                Log.d(TAG, "Emulated shortcut in fast mode");
                            }
                            sendEventDouble(uinputId, linuxCodes[0], linuxCodes[1]);
                        }
                    } else if (androidCode > 0) {
                        if (isDebug()) {
                            Log.d(TAG, "Emulated key in slow mode");
                        }
                        try {
                            RootShell.getShell(true).add(
                                    new Command(0, "input keyevent " + String.valueOf(androidCode)));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (isDebug()) {
                    Log.d(TAG, "Emulated key not found");
                }
            }
        }).start();

    }

    public static void sendKeyEvent(final String keyEvent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDebug()) {
                    Log.d(TAG, "inject keyevent " + keyEvent);
                }
                try {
                    Instrumentation instrumentation = new Instrumentation();
                    instrumentation.sendKeyDownUpSync(Integer.parseInt(keyEvent));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }



    public static void runShellCommand(final String commandToExecute) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDebug()) {
                    Log.d(TAG, "run shell: " + commandToExecute);
                }
                try {
                    //Runtime.getRuntime().exec(new String[] {"su", "-c", commandToExecute});
                    Command command = new Command(0, commandToExecute);
                    RootShell.getShell(true).add(command);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public static boolean isScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            boolean screenOn = false;
            for (Display display : dm.getDisplays()) {
                if (display.getState() != Display.STATE_OFF) {
                    screenOn = true;
                }
            }
            return screenOn;
        } else {
            PowerManager powerManager = (PowerManager) getContext().getSystemService(POWER_SERVICE);
            return powerManager.isScreenOn();
        }

//        return  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
//                    && powerManager.isInteractive())
//                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH
//                    && powerManager.isScreenOn());
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










    private static Map<String, KeyboardCode> keymap;
    private void initializeKeymap() {
        keymap = new HashMap<>();
        keymap.put("UNKNOWN", new KeyboardCode(240, 0));

        keymap.put("APP_SWITCH", new KeyboardCode(580, 187));
        keymap.put("ALT + TAB", new KeyboardCode(new int[] {56, 15}));
        keymap.put("TAB", new KeyboardCode(15, 61));

        keymap.put("ASSIST", new KeyboardCode(-1, 219));
        keymap.put("VOICE_ASSIST (>= 5.0)", new KeyboardCode(582, 231));
        keymap.put("SEARCH", new KeyboardCode(217, 84));

        keymap.put("MENU", new KeyboardCode(139, 82));
        keymap.put("HOME", new KeyboardCode(102, 3));
        keymap.put("BACK", new KeyboardCode(158, 4));
        keymap.put("FORWARD", new KeyboardCode(159, 125));

        keymap.put("BRIGHTNESS_DOWN", new KeyboardCode(224, 220));
        keymap.put("BRIGHTNESS_UP", new KeyboardCode(225, 221));

        keymap.put("CALL", new KeyboardCode(169, 5));
        keymap.put("ENDCALL", new KeyboardCode(-1, 6));
        keymap.put("HEADSETHOOK", new KeyboardCode(226, 79));

        keymap.put("DPAD_UP", new KeyboardCode(103, 19));
        keymap.put("DPAD_DOWN", new KeyboardCode(108, 20));
        keymap.put("DPAD_LEFT", new KeyboardCode(105, 21));
        keymap.put("DPAD_RIGHT", new KeyboardCode(106, 22));
        keymap.put("DPAD_CENTER", new KeyboardCode(353, 23));

        keymap.put("ENTER", new KeyboardCode(28, 66));
        keymap.put("ESCAPE", new KeyboardCode(1, 111));

        keymap.put("MEDIA_FAST_FORWARD", new KeyboardCode(208, 90));
        keymap.put("MEDIA_NEXT", new KeyboardCode(163, 87));
        keymap.put("MEDIA_PLAY_PAUSE", new KeyboardCode(164, 85));
        keymap.put("MEDIA_PLAY", new KeyboardCode(207, 126));
        keymap.put("MEDIA_PAUSE", new KeyboardCode(119, 127));
        keymap.put("MEDIA_STOP", new KeyboardCode(128, 86));
        keymap.put("MEDIA_PREVIOUS", new KeyboardCode(165, 88));
        keymap.put("MEDIA_REWIND", new KeyboardCode(168, 89));
        keymap.put("MEDIA_RECORD", new KeyboardCode(167, 130));

        keymap.put("VOLUME_DOWN", new KeyboardCode(114, 25));
        keymap.put("VOLUME_UP", new KeyboardCode(115, 24));
        keymap.put("VOLUME_MUTE", new KeyboardCode(113, 164));
        keymap.put("MUTE_MICROPHONE", new KeyboardCode(248, 91));

        keymap.put("NOTIFICATION", new KeyboardCode(-1, 83));

        keymap.put("MUSIC", new KeyboardCode(213, 209));
        keymap.put("CAMERA", new KeyboardCode(212, 27));
        keymap.put("CONTACTS", new KeyboardCode(429, 207));
        keymap.put("BROWSER", new KeyboardCode(150, 64));
        keymap.put("SETTINGS", new KeyboardCode(-1, 176));

        keymap.put("POWER", new KeyboardCode(116, 26));
        keymap.put("POWER2", new KeyboardCode(356, -1));
        keymap.put("SLEEP (>= 4.4W)", new KeyboardCode(142, 223));
        keymap.put("WAKEUP (>= 5.0)", new KeyboardCode(143, 224));

        keymap.put("ZOOM_IN", new KeyboardCode(418, 168));
        keymap.put("ZOOM_OUT", new KeyboardCode(419, 169));

    }

    private class KeyboardCode {
        protected int linuxCode = -1;
        protected int[] linuxCodes = null;
        protected int androidCode = -1;


        public KeyboardCode(int linuxCode, int androidCode) {
            this.linuxCode = linuxCode;
            this.androidCode = androidCode;
        }

        public KeyboardCode(int[] linuxCodes) {
            this.linuxCodes = linuxCodes;
        }
    }

}
