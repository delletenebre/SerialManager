package kg.delletenebre.serialmanager;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

public class CommandSettingsActivity extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();

    protected static Activity activity;

    public static CheckBoxPreference autosetPreference;
    public static EditTextPreference keyPreference, valuePreference;
    private static Preference actionNavigation, actionVolume, actionMedia, actionApplication;
    private static PreferenceScreen preferenceScreen;

    private GeneralPreferenceFragment mGeneralPreferenceFragment;

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener =
            new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                if ( listPreference.getKey().equals("action_category")) {
                    if (preferenceScreen != null) {
                        preferenceScreen.removePreference(actionNavigation);
                        preferenceScreen.removePreference(actionVolume);
                        preferenceScreen.removePreference(actionMedia);
                        preferenceScreen.removePreference(actionApplication);

                        switch (stringValue) {
                            case "1":
                                preferenceScreen.addPreference(actionNavigation);
                                break;

                            case "2":
                                preferenceScreen.addPreference(actionVolume);
                                break;

                            case "3":
                                preferenceScreen.addPreference(actionMedia);
                                break;

                            case "4":
                                preferenceScreen.addPreference(actionApplication);
                                break;
                        }
                    }
                }
                int index = listPreference.findIndexOfValue(stringValue);

                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof AppChooserPreference) {
                preference.setSummary(AppChooserPreference.getDisplayValue(
                        preference.getContext(), stringValue));
            } else {
                preference.setSummary(stringValue);
            }

            return true;
        }
    };

    private static void bindPreferenceSummaryToValue(Preference preference,
                                                     String spName) {
        if (preference != null) {
            preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    preference.getContext().getSharedPreferences(spName, MODE_PRIVATE)
                            .getString(preference.getKey(), ""));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        mGeneralPreferenceFragment = new GeneralPreferenceFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mGeneralPreferenceFragment).commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.setAliveActivity(this);
        activity = this;
    }

    @Override
    protected void onPause() {
        App.setAliveActivity(null);
        activity = null;
        super.onPause();
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (mGeneralPreferenceFragment != null) {
            mGeneralPreferenceFragment.onPause();
        }

        super.onBackPressed();
    }

//    protected boolean isValidFragment(String fragmentName) {
//        return PreferenceFragment.class.getName().equals(fragmentName)
//                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
//    }

    public static class GeneralPreferenceFragment extends PreferenceFragment {
        private String uuid;
        private int id;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Intent intent = getActivity().getIntent();
            uuid = intent.getStringExtra("pref_uuid");
            id = intent.getIntExtra("pref_id", -1);

            if (uuid != null && !uuid.isEmpty() && id > -1) {
                getPreferenceManager().setSharedPreferencesName(uuid);
                getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
            } else {
                getActivity().finish();
            }


            addPreferencesFromResource(R.xml.pref_command);
            setHasOptionsMenu(true);

            SharedPreferences.Editor editor =
                    getPreferenceManager().getSharedPreferences().edit();
            editor.putInt("id", id);
            editor.apply();

            preferenceScreen = getPreferenceScreen();
            autosetPreference = (CheckBoxPreference) findPreference("autoset");
            keyPreference = (EditTextPreference) findPreference("key");
            valuePreference = (EditTextPreference) findPreference("value");
            actionNavigation = findPreference("action_navigation");
            actionVolume = findPreference("action_volume");
            actionMedia = findPreference("action_media");
            actionApplication = findPreference("action_application");

            bindPreferenceSummaryToValue(keyPreference, uuid);
            bindPreferenceSummaryToValue(valuePreference, uuid);
            bindPreferenceSummaryToValue(findPreference("scatter"), uuid);
            bindPreferenceSummaryToValue(findPreference("action_category"), uuid);
            bindPreferenceSummaryToValue(actionNavigation, uuid);
            bindPreferenceSummaryToValue(actionVolume, uuid);
            bindPreferenceSummaryToValue(actionMedia, uuid);
            bindPreferenceSummaryToValue(actionApplication, uuid);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int itemId = item.getItemId();
            switch (itemId) {
                case android.R.id.home:
                    setResultForActivity();
                    NavUtils.navigateUpFromSameTask(getActivity());
                    return true;
            }

            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onPause() {
            setResultForActivity();

            super.onPause();
        }

        private void setResultForActivity() {
            ListPreference listPreference;
            Intent intent = new Intent();

            intent.putExtra("id", id);
            intent.putExtra("uuid", uuid);
            intent.putExtra("key", keyPreference.getText());
            intent.putExtra("value", valuePreference.getText());
            intent.putExtra("scatter", ((EditTextPreference) findPreference("scatter")).getText());
            intent.putExtra("is_through", ((CheckBoxPreference) findPreference("is_through")).isChecked());

            listPreference = (ListPreference) findPreference("action_category");
            intent.putExtra("actionCategory", listPreference.getValue());

            listPreference = (ListPreference) actionNavigation;
            intent.putExtra("actionNavigation", listPreference.getValue());

            listPreference = (ListPreference) actionVolume;
            intent.putExtra("actionVolume", listPreference.getValue());

            listPreference = (ListPreference) actionMedia;
            intent.putExtra("actionMedia", listPreference.getValue());

            intent.putExtra("actionApplication", ((AppChooserPreference) actionApplication).getValue());

            autosetPreference.setChecked(false);

            getActivity().setResult(RESULT_OK, intent);
        }
    }
}
