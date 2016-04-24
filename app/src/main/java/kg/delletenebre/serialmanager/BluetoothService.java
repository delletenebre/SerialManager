package kg.delletenebre.serialmanager;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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


    private final Handler reconnectHandler = new Handler();
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (App.isDebug()) {
                Log.d(TAG, "Reconnecting bluetooth");
            }

            if (service != null && prefs != null && bt.getConnectedDeviceAddress() == null) {
                boolean enabled = prefs.getBoolean("bluetooth", false);
                String address = prefs.getString("bluetoothDevice", "");

                if (bt.isServiceAvailable() && enabled && !address.isEmpty()) {
                    connectDeviceAddress = address;
                    bt.connect(connectDeviceAddress);
                }
            } else {
                reconnectHandler.removeCallbacks(this);
            }
        }
    };


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
                    if (App.isDebug()) {
                        Log.d("Receive BT", message);
                    }

                    Commands.processReceivedData(message);
                }
            });


            bt.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
                public void onDeviceConnected(String name, String address) {
                    if (App.isDebug()) {
                        Log.d(TAG, "Connected to " + name + " [" + address + "]");
                        Log.d(TAG, "^^ " + String.valueOf(bt.getConnectedDeviceAddress()));
                        Log.d(TAG, "^^ " + String.valueOf(bt.getServiceState()));
                    }
                }

                public void onDeviceDisconnected() {
                    if (App.isDebug()) {
                        Log.d(TAG, "onDeviceDisconnected");
                    }

                    if ((!prefs.getBoolean("stopWhenScreenOff", true) || App.isScreenOn())
                            && prefs.getBoolean("bluetooth_autoconnect", true)) {
                        start();
                    } else {
                        stop();
                    }
                }

                public void onDeviceConnectionFailed() {
                    if (App.isDebug()) {
                        Log.d(TAG, "onDeviceConnectionFailed");
                    }

                    if ((!prefs.getBoolean("stopWhenScreenOff", true) || App.isScreenOn())
                            && prefs.getBoolean("bluetooth_autoconnect", true)) {
                        start();
                    } else {
                        stop();
                    }
                }
            });

            receiver = new EventsReceiver();
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(receiver, intentFilter);

            if (App.isDebug()) {
                Log.d(TAG, "CREATED");
            }
        }
    }

    public void send(String data, boolean CRLF) {
        bt.send(data, CRLF);
    }

    public static void sendFromWidget(String data, int widgetId) {
        if (service != null) {
            if (service.bt != null && service.prefs != null
                    && service.bt.getConnectedDeviceAddress() != null) {
                if (App.isDebug()) {
                    Log.d(TAG, "Data to send: " + data);
                }

                service.bt.send(data, service.prefs.getBoolean("crlf", true));

                Intent i = new Intent(App.ACTION_SEND_DATA_SUCCESS);
                i.putExtra("widgetId", widgetId);
                i.putExtra("type", "bluetooth");
                service.sendBroadcast(i);

            } else if (App.isDebug()) {
                if (App.isDebug()) {
                    Log.w(TAG, "Can't send data [" + data + "]. Bluetooth serial port is null or no communication with device");
                }
                Toaster.toast(R.string.toast_serial_send_warning);

            }
        } else {
            if (App.isDebug()) {
                Log.w(TAG, "Service is null");
            }
            Toaster.toast(R.string.toast_serial_send_warning);
        }

    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bt != null) {
            if (bt.getServiceState() == BluetoothState.STATE_CONNECTED) {
                bt.disconnect();
            }

            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.removeMessages(0);

            bt.stopService();
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        connectDeviceAddress = null;
        service = null;

        if (App.isDebug()) {
            Log.d(TAG, "DESTROYED");
        }
    }
}
