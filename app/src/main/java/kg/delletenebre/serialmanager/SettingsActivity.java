package kg.delletenebre.serialmanager;


import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import java.util.List;

import kg.delletenebre.serialmanager.Preferences.AppCompatPreferenceActivity;

public class SettingsActivity extends AppCompatPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

//        MainPreferenceFragment mMainPreferenceFragment = new MainPreferenceFragment();
//        getFragmentManager().beginTransaction()
//                .replace(android.R.id.content, mMainPreferenceFragment).commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.setAliveActivity(this);
    }

    @Override
    protected void onPause() {
        App.setAliveActivity(null);
        App.updateSettings();
        super.onPause();
    }

    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        loadHeadersFromResource(R.xml.pref_main_headers, target);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            this.finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected boolean isValidFragment(String fragmentName) {
        return     PreferenceFragment.class.getName().equals(fragmentName)
                || BluetoothPreferenceFragment.class.getName().equals(fragmentName)
                || UsbPreferenceFragment.class.getName().equals(fragmentName)
                || I2CPreferenceFragment.class.getName().equals(fragmentName)
                || WebServerPreferenceFragment.class.getName().equals(fragmentName)
                || GpioPreferenceFragment.class.getName().equals(fragmentName)
                || HotkeysPreferenceFragment.class.getName().equals(fragmentName)
                || SendingCommandsPreferenceFragment.class.getName().equals(fragmentName)
                || ExtraPreferenceFragment.class.getName().equals(fragmentName)
                || OtherPreferenceFragment.class.getName().equals(fragmentName);
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    String stringValue = value.toString();

                    if (preference instanceof ListPreference) {
                        ListPreference listPreference = (ListPreference) preference;
                        int index = listPreference.findIndexOfValue(stringValue);

                        preference.setSummary(
                                index >= 0
                                        ? listPreference.getEntries()[index]
                                        : null);
                    } else {
                        preference.setSummary(stringValue);
                    }

                    return true;
                }
            };

    private static void bindPreferenceSummaryToValue(Preference preference) {
        if (preference != null) {
            preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        }
    }
//
//    public static class MainPreferenceFragment extends PreferenceFragment {
//
//        @Override
//        public void onCreate(Bundle savedInstanceState) {
//            super.onCreate(savedInstanceState);
//
//            addPreferencesFromResource(R.xml.pref_main);
//            setHasOptionsMenu(true);
//        }
//
////        @Override
////        public boolean onOptionsItemSelected(MenuItem item) {
////            int itemId = item.getItemId();
////            switch (itemId) {
////                case android.R.id.home:
////                    NavUtils.navigateUpFromSameTask(getActivity());
////                    return true;
////            }
////
////            return super.onOptionsItemSelected(item);
////        }
//        @Override
//        public boolean onOptionsItemSelected(MenuItem item) {
//            int id = item.getItemId();
//            if (id == android.R.id.home) {
//                startActivity(new Intent(getActivity(), SettingsActivity.class));
//                return true;
//            }
//            return super.onOptionsItemSelected(item);
//        }
//
//
//    }

    public static class BluetoothPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_bluetooth);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("bluetoothDevice"));
        }
    }

    public static class UsbPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_usb);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("usbDevice"));
            bindPreferenceSummaryToValue(findPreference("baudRate"));
            bindPreferenceSummaryToValue(findPreference("dataBits"));
            bindPreferenceSummaryToValue(findPreference("stopBits"));
            bindPreferenceSummaryToValue(findPreference("parity"));
        }
    }

    public static class I2CPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_i2c);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("i2c_request_data_delay"));
        }
    }

    public static class WebServerPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_webserver);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("webserver_port"));
        }
    }

    public static class GpioPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_gpio);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("gpio_debounce"));
            bindPreferenceSummaryToValue(findPreference("gpio_long_press_delay"));
        }
    }

    public static class HotkeysPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_hotkeys);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("hotkeys_detect_delay"));
        }
    }

    public static class SendingCommandsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_sending_commands);
            setHasOptionsMenu(true);


        }
    }

    public static class ExtraPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_extra);
            setHasOptionsMenu(true);


        }
    }

    public static class OtherPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main_other);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("autostart_delay"));
            bindPreferenceSummaryToValue(findPreference("stop_when_screen_off_delay"));
            bindPreferenceSummaryToValue(findPreference("start_when_screen_on_delay"));
        }
    }
}
