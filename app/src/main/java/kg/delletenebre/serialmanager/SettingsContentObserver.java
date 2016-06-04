package kg.delletenebre.serialmanager;

import android.database.ContentObserver;
import android.os.Handler;
import android.util.Log;

public class SettingsContentObserver extends ContentObserver {
    public static final String TAG = "SettingsContentObserver";

    private int lastBrightness;

    public SettingsContentObserver(Handler handler) {
        super(handler);

        lastBrightness = App.getScreenBrightness();
    }


    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        int brightness = App.getScreenBrightness();

        if (brightness > -1 && brightness != lastBrightness) {
            if (App.isDebug()) {
                Log.d(TAG, "{brightness:" + String.valueOf(brightness) + "}");
            }
            ConnectionService.usbAndBluetoothSend("{brightness:" + String.valueOf(brightness) + "}", false);
            lastBrightness = brightness;
        }
    }


}
