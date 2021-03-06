package kg.delletenebre.serialmanager;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import kg.delletenebre.serialmanager.Commands.Commands;
import xdroid.toaster.Toaster;

public class EventsReceiver extends BroadcastReceiver {
    private static final String TAG = "EventsReceiver";
    public static boolean autostartActive = false;

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        String data;

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                if (App.isDebug()) {
                    Log.i(TAG, "**** ACTION_BOOT_COMPLETED ****");
                }

                if (App.getPrefs().getBoolean("autostart", true)) {
                    autostartActive = true;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            context.startService(new Intent(context, ConnectionService.class));
                            autostartActive = false;
                        }
                    }, App.getIntPreference("autostart_delay", 5) * 1000);
                }

                break;

            //case Intent.ACTION_USER_PRESENT:
            case Intent.ACTION_SCREEN_ON:
                if (App.isDebug()) {
                    Log.i(TAG, "**** ACTION_USER_PRESENT || ACTION_SCREEN_ON ****");
                    Log.i(TAG, action);
                }

                ConnectionService.sendInfoScreenState("on");

                if (!autostartActive && App.getPrefs().getBoolean("start_when_screen_on", true)) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (App.isScreenOn()) {
                                context.startService(new Intent(context, ConnectionService.class));
                            }
                        }
                    }, App.getIntPreference("start_when_screen_on_delay", 2) * 1000);
                }

                break;

            case Intent.ACTION_SCREEN_OFF:
                if (App.isDebug()) {
                    Log.i(TAG, "**** ACTION_SCREEN_OFF ****");
                }

                ConnectionService.sendInfoScreenState("off");

                if (App.getPrefs().getBoolean("stop_when_screen_off", true)) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (App.isScreenOff()) {
                                context.stopService(new Intent(context, ConnectionService.class));
                            }
                        }
                    }, App.getIntPreference("stop_when_screen_off_delay", 2) * 1000);
                }
                break;

            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                if (App.isDebug()) {
                    Log.i(TAG, "****ACTION_USB_DEVICE_DETACHED****");
                }

                ConnectionService.detachDevice(
                        (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                break;

            case App.ACTION_SEND_DATA:
                if (App.isDebug()) {
                    Log.i(TAG, "****WIDGET_SEND_ACTION****");
                }

                int widgetId = intent.getIntExtra("widgetId", -1);
                data = intent.getStringExtra("data");

                if (!data.isEmpty()) {
                    ConnectionService.sendDataToTarget(data);

                    Intent i = new Intent(App.ACTION_SEND_DATA_SUCCESS);
                    i.putExtra("widgetId", widgetId);
                    App.getContext().sendBroadcast(i);

                } else {
                    if (App.isDebug()) {
                        Log.w(TAG, "Data is empty");
                    }
                    Toaster.toast(R.string.toast_serial_send_warning_empty_data);
                }
                break;

            case BluetoothAdapter.ACTION_STATE_CHANGED:
                if (App.isDebug()) {
                    Log.i(TAG, "**** BluetoothAdapter.ACTION_STATE_CHANGED ****");
                }

                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        ConnectionService.onBluetoothDisabled();
                        break;

                    case BluetoothAdapter.STATE_ON:
                        ConnectionService.onBluetoothEnabled();
                        break;
                }

                break;

            case App.ACTION_EXTERNAL_SEND:
                if (App.isDebug()) {
                    Log.i(TAG, "**** App.ACTION_EXTERNAL_SEND ****");
                }

                if (intent.hasExtra("data")) {
                    data = String.valueOf(intent.getExtras().get("data"));

                    if (App.isDebug()) {
                        Log.d(TAG, data);
                    }

                    ConnectionService.sendDataToTarget(data);
                }

                break;

            case App.ACTION_CONNECTED_DEVICES_REQUEST:
                if (App.isDebug()) {
                    Log.i(TAG, "****ACTION_CONNECTED_DEVICES_REQUEST****");
                }

                ConnectionService.updateNotificationText();

                break;

            case App.ACTION_EXTERNAL_KEY_VALUE:
                if (App.isDebug()) {
                    Log.i(TAG, "**** App.ACTION_EXTERNAL_NEW_DATA ****");
                }

                if (intent.hasExtra("key") && intent.hasExtra("value")) {
                    String key = String.valueOf(intent.getExtras().get("key"));
                    String value = String.valueOf(intent.getExtras().get("value"));

                    StringBuilder sb = (new StringBuilder())
                            .append("<")
                            .append(key)
                            .append(":")
                            .append(value)
                            .append(">");

                    Commands.processReceivedData(sb.toString());
                }

                break;
        }

    }
}
