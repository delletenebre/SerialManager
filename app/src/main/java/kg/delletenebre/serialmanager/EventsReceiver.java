package kg.delletenebre.serialmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import xdroid.toaster.Toaster;

public class EventsReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        switch (action) {
            case Intent.ACTION_USER_PRESENT:
                SerialService.restart(context);
                break;

            case Intent.ACTION_SCREEN_OFF:
                context.stopService(new Intent(context, SerialService.class));
                break;

            case App.ACTION_USB_ATTACHED:
                Log.i(TAG, "****ACTION_USB_DEVICE_ATTACHED****");
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

                    if (usbManager.hasPermission(device)) {
                        UsbDeviceConnection connection = usbManager.openDevice(device);
                        if (connection != null) {
                            SerialService.start(context, device, connection);
                        }
                    }
//                    } else {
//                        Intent intentUsbPermission = new Intent(App.ACTION_USB_PERMISSION);
//
//                        usbManager.requestPermission(device,
//                                PendingIntent.getBroadcast(context, 0, intentUsbPermission, 0));
//                    }
                }
                break;

            case SerialService.WIDGET_SEND_ACTION:
                Log.i(TAG, "****WIDGET_SEND_ACTION****");
                int widgetId = intent.getIntExtra("widgetId", -1);
                String data = intent.getStringExtra("data");

                if (!data.isEmpty()) {
                    if (SerialService.service != null) {
                        SerialService.service.writeFromWidget(data, widgetId);
                    } else {
                        Toaster.toast(R.string.toast_serial_send_warning);
                        Log.w(TAG, "SerialService is null");
                    }
                } else {
                    Toaster.toast(R.string.toast_serial_send_warning_empty_data);
                    Log.w(TAG, "Data is empty");
                }
                break;

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
}
