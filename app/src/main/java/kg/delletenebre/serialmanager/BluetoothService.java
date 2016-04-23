package kg.delletenebre.serialmanager;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import xdroid.toaster.Toaster;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";

    protected static BluetoothService service;
    BluetoothSPP bt;

    private static String connectDeviceAddress;

    private EventsReceiver receiver;
    private SharedPreferences prefs;


    public static void start() {
        BluetoothService.stop();

        final Handler h = new Handler();
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (service != null) {
                    h.postDelayed(this, App.START_SERVICE_DELAY);
                } else {
                    Context context = App.getAppContext();
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean enabled = prefs.getBoolean("bluetooth", false);
                    String address = prefs.getString("bluetoothDevice", "");

                    if (enabled && !address.isEmpty()) {
                        connectDeviceAddress = address;
                        context.startService(new Intent(context, BluetoothService.class));
                    }
                }
            }
        };
        h.postDelayed(r, App.START_SERVICE_DELAY);
    }

    public static void stop() {
        if (service != null) {
            service.stopSelf();
        }
    }


    /*
     * onCreate will be executed when service is started
     */
    @Override
    public void onCreate() {
        service = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        bt = new BluetoothSPP(this);

        if (connectDeviceAddress == null || connectDeviceAddress.isEmpty()) {
            Toaster.toast("Choose Bluetooth device to connect");
            stopSelf();
        } else if (!bt.isBluetoothAvailable()) {
            Toaster.toast("Bluetooth is not available");
            stopSelf();
        } else if (!bt.isBluetoothEnabled()) {
            Toaster.toast("Bluetooth is not enable");
            stopSelf();
        } else {
            bt.setupService();
            bt.startService(BluetoothState.DEVICE_OTHER);

            bt.connect(connectDeviceAddress);

            bt.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
                public void onDataReceived(byte[] data, String message) {
                    if (App.getDebug()) {
                        Log.d("Receive BT ", message);
                    }

                    Commands.processReceivedData(message);
                }
            });

            receiver = new EventsReceiver();
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(receiver, intentFilter);

            if (App.getDebug()) {
                Log.d(TAG, "CREATED");
            }
        }
    }

    public void send(String message, boolean CRLF) {
        bt.send("Message", CRLF);
    }

    public void sendFromWidget(String data, int widgetId) {
        if (bt != null && prefs != null) {
            if (App.getDebug()) {
                Log.d(TAG, "Data to send: " + data);
            }

            Intent i = new Intent(App.ACTION_SEND_DATA_COMPLETE);
            i.putExtra("widgetId", widgetId);
            sendBroadcast(i);

            bt.send("Message", true);
        } else if (App.getDebug()) {
            Log.w(TAG, "Can not send data [" + data + "]. BT Serial port is null");
            Toaster.toast(R.string.toast_serial_send_warning);
        }
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bt != null) {
            int state = bt.getServiceState();
            if (state == BluetoothState.STATE_CONNECTED) {
                bt.disconnect();
            }

            bt.stopService();
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        connectDeviceAddress = null;
        service = null;

        if (App.getDebug()) {
            Log.d(TAG, "DESTROYED");
        }
    }
}
