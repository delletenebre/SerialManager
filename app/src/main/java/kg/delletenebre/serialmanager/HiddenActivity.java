package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

public class HiddenActivity extends Activity {
    private final String TAG = getClass().getName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isScreenOn()) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

            if (SerialService.service != null) {
                SerialService.service.stopSelf();
            }

            UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            List<UsbSerialDriver> drivers =
                    UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

            if (!drivers.isEmpty()) {
                UsbSerialDriver driver = drivers.get(0);
                UsbDeviceConnection connection = mUsbManager.openDevice(driver.getDevice());
                if (connection != null) {
                    List<UsbSerialPort> ports = driver.getPorts();

                    if (!ports.isEmpty()) {
                        SerialService.start(this, connection, ports.get(0));
                    }

                }
            }
        }

        finish();
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            isScreenOn = pm.isInteractive();
        } else {
            isScreenOn = pm.isScreenOn();
        }

        return isScreenOn;
    }
}
