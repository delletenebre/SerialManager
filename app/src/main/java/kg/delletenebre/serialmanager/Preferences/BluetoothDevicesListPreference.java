package kg.delletenebre.serialmanager.Preferences;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Resources;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kg.delletenebre.serialmanager.R;

public class BluetoothDevicesListPreference extends ListPreference {
    private final static String TAG = "BluetoothDevicesListPreference";

    public BluetoothDevicesListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        List<CharSequence> entriesList = new ArrayList<>();
        List<CharSequence> entryValuesList = new ArrayList<>();

        Resources res = context.getResources();
        entriesList.add(res.getString(R.string.pref_bt_device_no_device));
        entryValuesList.add("");

        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        if (bta != null) {
            Set<BluetoothDevice> pairedDevices = bta.getBondedDevices();

            for (BluetoothDevice dev : pairedDevices) {
                entriesList.add(String.format(res.getString(R.string.pref_bt_device_template),
                        dev.getName(), dev.getAddress()));
                entryValuesList.add(dev.getAddress());
            }
        }

        setEntries(entriesList.toArray(new CharSequence[entriesList.size()]));
        setEntryValues(entryValuesList.toArray(new CharSequence[entryValuesList.size()]));
    }

    public BluetoothDevicesListPreference(Context context) {
        this(context, null);
    }
}