package kg.delletenebre.serialmanager.Widget;


import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import com.afollestad.materialdialogs.folderselector.FileChooserDialog;

import java.io.File;

import kg.delletenebre.serialmanager.AppChooserPreference;
import kg.delletenebre.serialmanager.R;

public class WidgetReceiveSettings extends AppCompatActivity implements FileChooserDialog.FileCallback {

    private final String TAG = getClass().getSimpleName();
    public static final String PREF_PREFIX_KEY = "widget_receive_";
    private static final int REQUEST_PERMISSION = 100;

    private GeneralPreferenceFragment mGeneralPreferenceFragment;
    private Preference fontFilePreference;

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

        mGeneralPreferenceFragment = new GeneralPreferenceFragment();
        mGeneralPreferenceFragment.setWidgetReceiveSettings(this);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mGeneralPreferenceFragment).commit();
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
            mGeneralPreferenceFragment.saveWidget();
        }

        //super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Restarting application");

                // Schedule start after 1 second
                PendingIntent pi = PendingIntent.getActivity(
                        this, 0, getIntent(), PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pi);

                // Stop now
                System.exit(0);
            }
        }
    }

    public void startFileChooser(Preference preference) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        } else {
            fontFilePreference = preference;
            new FileChooserDialog.Builder(this)
                    .chooseButton(R.string.md_choose_label)
                    .initialPath(Environment.getExternalStorageDirectory().getPath())
                    //.mimeType("font/ttf")//application/x-font-ttf
                    .show();
        }
    }

    @Override
    public void onFileSelection(@NonNull FileChooserDialog dialog, @NonNull File file) {
        if (fontFilePreference != null) {
            String filePath = file.getPath();
            SharedPreferences.Editor prefs = mGeneralPreferenceFragment.getSharedPreference().edit();
            prefs.putString("fontFile", filePath);
            prefs.apply();
            fontFilePreference.setSummary(filePath);
        }
    }

    public static class GeneralPreferenceFragment extends PreferenceFragment {
        private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        private WidgetReceiveSettings widgetReceiveSettings;

        public void setWidgetReceiveSettings(WidgetReceiveSettings widgetReceiveSettings) {
            this.widgetReceiveSettings = widgetReceiveSettings;
        }

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

            addPreferencesFromResource(R.xml.pref_widget_receive);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("key"), widgetId);
            bindPreferenceSummaryToValue(findPreference("prefix"), widgetId);
            bindPreferenceSummaryToValue(findPreference("suffix"), widgetId);
            bindPreferenceSummaryToValue(findPreference("position"), widgetId);
            bindPreferenceSummaryToValue(findPreference("fontSize"), widgetId);
            bindPreferenceSummaryToValue(findPreference("textAlign"), widgetId);
            bindPreferenceSummaryToValue(findPreference("fontFile"), widgetId);

            Preference fontFile = findPreference("fontFile");
            fontFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    widgetReceiveSettings.startFileChooser(preference);
                    return true;
                }
            });
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
            WidgetReceive.updateAppWidget(getActivity(), appWidgetManager, widgetId, "---");

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            getActivity().setResult(RESULT_OK, resultValue);
            getActivity().finish();
        }
    }
}
