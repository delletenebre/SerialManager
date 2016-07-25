package kg.delletenebre.serialmanager;


import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        MainPreferenceFragment mMainPreferenceFragment = new MainPreferenceFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mMainPreferenceFragment).commit();
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

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class MainPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.pref_main);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("bluetoothDevice"));
            bindPreferenceSummaryToValue(findPreference("usbDevice"));
            bindPreferenceSummaryToValue(findPreference("baudRate"));
            bindPreferenceSummaryToValue(findPreference("dataBits"));
            bindPreferenceSummaryToValue(findPreference("stopBits"));
            bindPreferenceSummaryToValue(findPreference("parity"));
            bindPreferenceSummaryToValue(findPreference("gpio_debounce"));
            bindPreferenceSummaryToValue(findPreference("gpio_long_press_delay"));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int itemId = item.getItemId();
            switch (itemId) {
                case android.R.id.home:
                    NavUtils.navigateUpFromSameTask(getActivity());
                    return true;
            }

            return super.onOptionsItemSelected(item);
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
    }
}
