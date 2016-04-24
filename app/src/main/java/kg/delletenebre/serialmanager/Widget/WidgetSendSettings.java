package kg.delletenebre.serialmanager.Widget;


import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import java.io.File;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.FileChooser;
import kg.delletenebre.serialmanager.Preferences.AppChooserPreference;
import kg.delletenebre.serialmanager.R;

public class WidgetSendSettings extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();
    public static final String PREF_PREFIX_KEY = "widget_send_";

    private GeneralPreferenceFragment preferenceFragment;

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
                                                     int widgetId) {
        if (preference != null) {
            preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    preference.getContext().getSharedPreferences(PREF_PREFIX_KEY + widgetId, MODE_PRIVATE)
                            .getString(preference.getKey(), ""));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        preferenceFragment = new GeneralPreferenceFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, preferenceFragment).commit();
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (preferenceFragment != null) {
            preferenceFragment.saveWidget();
        }

        //super.onBackPressed();
    }


    public static class GeneralPreferenceFragment extends PreferenceFragment {
        private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getActivity().setResult(RESULT_CANCELED);

            // Find the widget id from the intent.
            Intent intent = getActivity().getIntent();
            Bundle extras = intent.getExtras();
            if (extras != null) {
                widgetId = extras.getInt(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            }

            // If this activity was started with an intent without an app widget ID, finish with an error.
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                getActivity().finish();
                return;
            } else {
                getPreferenceManager().setSharedPreferencesName(PREF_PREFIX_KEY + widgetId);
                getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
            }

            addPreferencesFromResource(R.xml.pref_widget_send);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("sendTo"), widgetId);
            bindPreferenceSummaryToValue(findPreference("data"), widgetId);
            bindPreferenceSummaryToValue(findPreference("text"), widgetId);
            bindPreferenceSummaryToValue(findPreference("fontColor"), widgetId);
            bindPreferenceSummaryToValue(findPreference("fontSize"), widgetId);
            bindPreferenceSummaryToValue(findPreference("backgroundColor"), widgetId);
            bindPreferenceSummaryToValue(findPreference("fontFile"), widgetId);

            Preference fontFile = findPreference("fontFile");
            fontFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && ContextCompat.checkSelfPermission(getContext(),
                            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, App.REQUEST_CODE_ASK_PERMISSIONS);
                    } else {
                        new FileChooser(getActivity())
                                .setExtension("ttf")
                                .setFileListener(new FileChooser.FileSelectedListener() {
                                    @Override public void fileSelected(final File file) {
                                        String filePath = file.getPath();
                                        SharedPreferences.Editor prefs = getSharedPreference().edit();
                                        prefs.putString("fontFile", filePath);
                                        prefs.apply();
                                        preference.setSummary(filePath);
                                    }
                                })
                                .showDialog();
                    }

                    return true;
                }
            });

        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            switch (requestCode) {
                case App.REQUEST_CODE_ASK_PERMISSIONS:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        System.exit(0);
                    }
                    break;

                default:
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }

        public SharedPreferences getSharedPreference() {
            return getPreferenceManager().getSharedPreferences();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int itemId = item.getItemId();
            switch (itemId) {
                case android.R.id.home:
                    saveWidget();
            }

            return super.onOptionsItemSelected(item);
        }

        public void saveWidget() {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getActivity());
            WidgetSend.updateAppWidget(getActivity(), appWidgetManager, widgetId);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            getActivity().setResult(RESULT_OK, resultValue);
            getActivity().finish();
        }
    }
}
