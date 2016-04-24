package kg.delletenebre.serialmanager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import kg.delletenebre.serialmanager.Widget.WidgetSendSettings;
import xdroid.toaster.Toaster;

public class EventsReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbDevice usbDevice;

        final boolean connectionUsb = prefs.getBoolean("usb", false);
        String usbDeviceToConnect = prefs.getString("usbDevice", "");
        final boolean connectionBluetooth = prefs.getBoolean("bluetooth", false);

        switch (action) {
            case Intent.ACTION_USER_PRESENT:
                if (App.isDebug()) {
                    Log.i(TAG, "****ACTION_USER_PRESENT****");
                }

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (App.isScreenOn()) {
                            if (connectionUsb && UsbService.service == null) {
                                UsbService.restart();
                            }
                            if (connectionBluetooth && BluetoothService.service == null) {
                                BluetoothService.start();
                            }
                        }
                    }
                }, 2000);

                break;

            case Intent.ACTION_SCREEN_OFF:
                if (App.isDebug()) {
                    Log.i(TAG, "****ACTION_SCREEN_OFF****");
                }

                if (prefs.getBoolean("stopWhenScreenOff", false)) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (App.isScreenOff()) {
                                UsbService.stop();
                                BluetoothService.stop();
                            }
                        }
                    }, 2000);
                }
                break;

            case App.ACTION_USB_ATTACHED:
                if (App.isDebug()) {
                    Log.i(TAG, "****ACTION_USB_DEVICE_ATTACHED****");
                }

                usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice != null) {
                    if (usbManager.hasPermission(usbDevice)
                            && connectionUsb
                            && (usbDeviceToConnect.isEmpty()
                                    || usbDeviceToConnect.equals(usbDevice.getDeviceName()))) {
                        UsbService.start(usbDevice);
                    }
//                    } else {
//                        Intent intentUsbPermission = new Intent(App.ACTION_USB_PERMISSION);
//
//                        usbManager.requestPermission(device,
//                                PendingIntent.getBroadcast(context, 0, intentUsbPermission, 0));
//                    }
                }
                break;

            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                if (App.isDebug()) {
                    Log.i(TAG, "****ACTION_USB_DEVICE_DETACHED****");
                }

                usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String connectedDeviceName = UsbService.getConnectedDeviceName();

                if (connectedDeviceName != null && connectedDeviceName.equals(usbDevice.getDeviceName())) {
                    UsbService.stop();
                }
                break;

            case App.ACTION_SEND_DATA:
                if (App.isDebug()) {
                    Log.i(TAG, "****WIDGET_SEND_ACTION****");
                }

                int widgetId = intent.getIntExtra("widgetId", -1);
                String data = intent.getStringExtra("data");
                String sendTo = intent.getStringExtra("sendTo");

                if (sendTo.equals("usb_bt")) {
                    SharedPreferences widgetPrefs = context.getSharedPreferences(
                            WidgetSendSettings.PREF_PREFIX_KEY + widgetId, Context.MODE_PRIVATE);

                    SharedPreferences.Editor editor = widgetPrefs.edit();
                    editor.putBoolean("status", false);
                    editor.apply();
                }

                if (!data.isEmpty()) {
                    if ((sendTo.equals("usb_bt") || sendTo.equals("usb")) && connectionUsb) {
                        UsbService.sendFromWidget(data, widgetId);
                    }
                    if ((sendTo.equals("usb_bt") || sendTo.equals("bt")) && connectionBluetooth) {
                        BluetoothService.sendFromWidget(data, widgetId);
                    }
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
                        BluetoothService.stop();
                        break;

                    case BluetoothAdapter.STATE_ON:
                        BluetoothService.start();
                        break;
                }

                break;
        }

    }

//        } else if (action.equals(App.ACTION_USB_PERMISSION)) {
//            Log.i(TAG, "****ACTION_USB_DEVICE_PERMISSIONS****");
//            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
//            if (device != null) {
//                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
//                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
//                    UsbDeviceConnection connection = usbManager.openDevice(device);
//                    if (connection != null) {
//                        SerialService.start(context, device, connection);
//                    }
//
//                    //                    Toast.makeText(MainActivity.this,
//                    //                            "ACTION_USB_PERMISSION accepted",
//                    //                            Toast.LENGTH_LONG).show();
//                } else {
//                    //                    Toast.makeText(MainActivity.this,
//                    //                            "ACTION_USB_PERMISSION rejected",
//                    //                            Toast.LENGTH_LONG).show();
//                }
//            }
//        }
}
