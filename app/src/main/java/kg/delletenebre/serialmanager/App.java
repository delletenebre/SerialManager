package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

import java.io.IOException;

public class App extends Application {

    public static final String ACTION_NEW_DATA_RECEIVED = "kg.delletenebre.serial.NEW_DATA";
    public static final String ACTION_SEND_DATA = "kg.delletenebre.serial.SEND_DATA";
    public static final String ACTION_SEND_DATA_COMPLETE = "kg.delletenebre.serial.SEND_DATA_COMPLETE";
    public static final String ACTION_SEND_DATA_SUCCESS = "kg.delletenebre.serial.SEND_DATA_SUCCESS";

    protected static final String ACTION_USB_PERMISSION = "kg.delletenebre.serial.usb_permission";
    protected static final String ACTION_USB_ATTACHED = "kg.delletenebre.serial.usb_attached";
    protected static final String ACTION_USB_DETACHED = "kg.delletenebre.serial.usb_detached";
    public static final int REQUEST_CODE_ASK_PERMISSIONS = 666;
    protected static final int START_SERVICE_DELAY = 1000;
    protected static final int BLUETOOTH_RECONNECT_DELAY = 3000;

    private static final int VOLUME_STREAM = AudioManager.STREAM_MUSIC;
    private static AudioManager audioManager;
    private static int lastVolumeLevel = 1;

    private static SharedPreferences prefs;

    private static Context appContext;
    public static Context getAppContext() {
        return appContext;
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
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        appContext = this;

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateSettings();
    }

    public static Activity getAliveActivity() {
        return aliveActiviity;
    }
    public static void setAliveActivity(Activity activity) {
        aliveActiviity = activity;
    }


    public static void updateSettings() {
        volumeShowUI = prefs.getBoolean("volumeShowUI", true);

        debug = prefs.getBoolean("debug", false);
        UsbService.setDTR(prefs.getBoolean("dtr", false));
        UsbService.setRTS(prefs.getBoolean("rts", false));

        if (!prefs.getBoolean("usb", false)) {
            UsbService.stop();
        } else if (UsbService.service == null) {
            UsbService.restart();
        }
        if (!prefs.getBoolean("bluetooth", false)) {
            BluetoothService.stop();
        } else if (BluetoothService.service == null) {
            BluetoothService.start();
        }
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
            appContext.sendOrderedBroadcast(downIntent, null);

            Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, buttonCode));
            appContext.sendOrderedBroadcast(upIntent, null);
        }
    }

    public static void emulateKeyEvent(final String keyEvent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("*******", "input keyevent " + keyEvent);
                try {
                    Runtime.getRuntime().exec(new String[] {"su", "-c", "input keyevent " + keyEvent});
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public static boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) getAppContext().getSystemService(POWER_SERVICE);
        return  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                    && powerManager.isInteractive())
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH
                    && powerManager.isScreenOn());
    }

    public static boolean isScreenOff() {
        return !isScreenOn();
    }

}
