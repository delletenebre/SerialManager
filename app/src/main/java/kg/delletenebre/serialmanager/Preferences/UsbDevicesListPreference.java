package kg.delletenebre.serialmanager.Preferences;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kg.delletenebre.serialmanager.R;
import kg.delletenebre.serialmanager.SerialService;

public class UsbDevicesListPreference extends ListPreference {
    private final static String TAG = "UsbDevicesListPreference";

    public UsbDevicesListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        List<CharSequence> entriesList = new ArrayList<>();
        List<CharSequence> entryValuesList = new ArrayList<>();

        JSONArray jsonDevices = SerialService.getJSONDevices(context);
        if (jsonDevices != null) {
            try {
                UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

                Resources res = context.getResources();
                entriesList.add(res.getString(R.string.pref_usb_device_autoconnect));
                entryValuesList.add("");

                for (UsbDevice device : usbManager.getDeviceList().values()) {
                    for (int i = 0; i < jsonDevices.length(); i++) {
                        JSONObject json = jsonDevices.getJSONObject(i);

                        boolean vidCheck = !json.has("vid") || (device.getVendorId() == json.getInt("vid"));
                        boolean pidCheck = !json.has("pid") || (device.getProductId() == json.getInt("pid"));

                        if (vidCheck && pidCheck) {
                            entriesList.add(device.getDeviceName());
                            entryValuesList.add(device.getDeviceName());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        setEntries(entriesList.toArray(new CharSequence[entriesList.size()]));
        setEntryValues(entryValuesList.toArray(new CharSequence[entryValuesList.size()]));
    }

    public UsbDevicesListPreference(Context context) {
        this(context, null);
    }

}