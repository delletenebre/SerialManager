package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import java.io.IOException;

public class App extends Application {

    public static final String ACTION_NEW_DATA_RECEIVED = "kg.delletenebre.serial.NEW_DATA";
    public static final String ACTION_SEND_DATA = "kg.delletenebre.serial.SEND_DATA";
    public static final String ACTION_SEND_DATA_COMPLETE = "kg.delletenebre.serial.SEND_DATA_COMPLETE";

    protected static final String ACTION_USB_PERMISSION = "kg.delletenebre.serial.usb_permission";
    protected static final String ACTION_USB_ATTACHED = "kg.delletenebre.serial.usb_attached";
    protected static final String ACTION_USB_DETACHED = "kg.delletenebre.serial.usb_detached";
    protected static final int START_SERVICE_DELAY = 1000;

    private static final int VOLUME_STREAM = AudioManager.STREAM_MUSIC;
    private static AudioManager audioManager;
    private static int lastVolumeLevel = 1;

    private static SharedPreferences settings;

    private static Context appContext;
    public static Context getAppContext() {
        return appContext;
    }

    private static boolean debug;
    public static boolean getDebug() {
        return debug;
    }

    private static Activity aliveActiviity;

    private static boolean volumeShowUI;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        appContext = this;

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        updateSettings();
    }

    public static Activity getAliveActivity() {
        return aliveActiviity;
    }
    public static void setAliveActivity(Activity activity) {
        aliveActiviity = activity;
    }


    public static void updateSettings() {
        volumeShowUI = settings.getBoolean("volumeShowUI", true);

        debug = settings.getBoolean("debug", false);
        if(UsbService.service != null) {
            UsbService.service.setDTR(settings.getBoolean("dtr", false));
            UsbService.service.setRTS(settings.getBoolean("rts", false));
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

    public static void emulateKeyEvent(final int keyEvent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[] {"su", "-c", "input keyevent " + keyEvent});
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }


}
