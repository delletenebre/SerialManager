package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import java.io.IOException;

public class App {
    private static App ourInstance = new App();


    private static Activity aliveActiviity;
    private Context context;


    private static boolean muteState = false;
    private static final int VOLUME_STREAM = AudioManager.STREAM_MUSIC;
    private static int currentVolume, maximumVolume;
    private static boolean volumeShowUI;
    private static AudioManager mAudioManager;


    public static App getInstance() {
        return ourInstance;
    }

    private App() {
    }

    public static Activity getAliveActivity() {
        return aliveActiviity;
    }
    public static void setAliveActivity(Activity activity) {
        aliveActiviity = activity;
    }


    public void setContext(Context context) {
        this.context = context;
    }
    public Context getContext() {
        return context;
    }

    public static void updateSettings(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        volumeShowUI = settings.getBoolean("volumeShowUI", false);
    }

    public static void initVolume(Context context) {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

        maximumVolume = mAudioManager.getStreamMaxVolume(VOLUME_STREAM);
        currentVolume = mAudioManager.getStreamVolume(VOLUME_STREAM);
        updateSettings(context);
    }

    public static void changeVolume(String mode) {
        mAudioManager.adjustStreamVolume(VOLUME_STREAM,
                (mode.equalsIgnoreCase("up"))
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                volumeShowUI ? AudioManager.FLAG_SHOW_UI : 0);
    }

    public static void setMute() {
        boolean muteState = !(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioManager.adjustStreamVolume(VOLUME_STREAM,
                    (muteState) ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                    volumeShowUI ? AudioManager.FLAG_SHOW_UI : 0);
        } else {
            mAudioManager.setStreamMute(VOLUME_STREAM, muteState);
        }
    }

    public static void emulateMediaButton(Context context, int buttonCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAudioManager.dispatchMediaKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, buttonCode));
            mAudioManager.dispatchMediaKeyEvent(
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
