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

public class BluetoothDevicesListPreference extends ListPreference {
    private final static String TAG = "BluetoothDevicesListPreference";

    public BluetoothDevicesListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bta.getBondedDevices();
        CharSequence[] entries = new CharSequence[pairedDevices.size() + 1];
        CharSequence[] entryValues = new CharSequence[pairedDevices.size() + 1];

        Resources res = context.getResources();
        entries[0] = res.getString(R.string.pref_bt_device_no_device);
        entryValues[0] = "";
        int i = 1;
        for (BluetoothDevice dev : pairedDevices) {
            entries[i] = String.format(res.getString(R.string.pref_bt_device_template),
                    dev.getName(), dev.getAddress());
            entryValues[i] = dev.getAddress();
            i++;
        }
        setEntries(entries);
        setEntryValues(entryValues);
    }

    public BluetoothDevicesListPreference(Context context) {
        this(context, null);
    }
}