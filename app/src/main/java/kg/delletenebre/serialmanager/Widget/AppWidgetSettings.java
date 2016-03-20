package kg.delletenebre.serialmanager.Widget;


import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import kg.delletenebre.serialmanager.AppChooserPreference;
import kg.delletenebre.serialmanager.R;

public class AppWidgetSettings extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();
    public static final String PREF_PREFIX_KEY = "appwidget_";

    private GeneralPreferenceFragment mGeneralPreferenceFragment;

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



        GeneralPreferenceFragment mGeneralPreferenceFragment = new GeneralPreferenceFragment();
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

    public static class GeneralPreferenceFragment extends PreferenceFragment {
        private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getActivity().setResult(RESULT_CANCELED);

            // Find the widget id from the intent.
            Intent intent = getActivity().getIntent();
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mAppWidgetId = extras.getInt(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            }


            // If this activity was started with an intent without an app widget ID, finish with an error.
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                getActivity().finish();
                return;
            } else {
                getPreferenceManager().setSharedPreferencesName(PREF_PREFIX_KEY + mAppWidgetId);
                getPreferenceManager().setSharedPreferencesMode(MODE_PRIVATE);
            }

            addPreferencesFromResource(R.xml.pref_widget);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference("title"), mAppWidgetId);
            bindPreferenceSummaryToValue(findPreference("fontSize"), mAppWidgetId);
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
            AppWidget.updateAppWidget(getActivity(), appWidgetManager, mAppWidgetId);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            getActivity().setResult(RESULT_OK, resultValue);
            //NavUtils.navigateUpFromSameTask(getActivity());
            getActivity().finish();
        }
    }
}
