package kg.delletenebre.serialmanager.Commands;


import android.app.Activity;
import android.content.Context;
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
import android.util.Log;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.Preferences.AppChooserPreference;
import kg.delletenebre.serialmanager.R;

public class CommandSettingsActivity extends AppCompatActivity {

    private static final String TAG = "CommandSettingsActivity";

    protected static Activity activity;

    public static final String COMMAND_PREFERENCE_NAME = "command";
    public static CheckBoxPreference autosetPreference;
    public static EditTextPreference keyPreference, valuePreference;
    private static Map<String, Preference> actionPreferences = new HashMap<>();
    private static PreferenceScreen preferenceScreen;

    private GeneralPreferenceFragment preferenceFragment;
    private static Command command;

    private static Preference.OnPreferenceChangeListener bindPreferenceSummaryToValueListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    String stringValue = value.toString();

                    if (preference instanceof ListPreference) {
                        ListPreference listPreference = (ListPreference) preference;
                        if ( listPreference.getKey().equals("action_category")) {
                            if (preferenceScreen != null) {
                                for (Map.Entry<String, Preference> entry : actionPreferences.entrySet()) {
                                    Preference pref = entry.getValue();
                                    preferenceScreen.removePreference(pref);

                                    if (stringValue.equals(entry.getKey())) {
                                        preferenceScreen.addPreference(pref);
                                    }
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
            preference.setOnPreferenceChangeListener(bindPreferenceSummaryToValueListener);

            bindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    preference.getContext().getSharedPreferences(spName, MODE_PRIVATE)
                            .getString(preference.getKey(), ""));
        }
    }

    private SharedPreferences.Editor setDefaults(SharedPreferences.Editor editor) {
        List<String> actions = new ArrayList<>();
        actions.add("action_navigation");
        actions.add("action_volume");
        actions.add("action_media");
        actions.add("action_application");
        actions.add("action_shell");
        actions.add("action_send");
        actions.add("action_system");

        editor.putString("key", "");
        editor.putString("value", "");
        editor.putString("scatter", "");
        editor.putBoolean("is_through", false);
        editor.putString("action_category", "none");

        for (String action: actions) {
            editor.putString(action, "");
        }

        editor.commit();

        return editor;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        command = (Command) getIntent().getSerializableExtra("command");
        if (command != null) {
            SharedPreferences.Editor editor =
                    getSharedPreferences(COMMAND_PREFERENCE_NAME, Context.MODE_PRIVATE).edit();

            editor = setDefaults(editor);

            float scatter = command.getScatter();
            String category = command.getCategory();
            if (category.isEmpty()) {
                category = "none";
            }

            editor.putString("key", command.getKey());
            editor.putString("value", command.getValue());
            editor.putString("scatter", (scatter != 0) ? String.valueOf(scatter) : "");
            editor.putBoolean("is_through", command.getThrough());
            editor.putString("action_category", category);
            editor.putString("action_" + category, command.getAction());
            editor.putString("actionString", command.getActionString());

            editor.apply();
        } else {
            finish();
        }

        preferenceFragment = new GeneralPreferenceFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, preferenceFragment).commit();
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
        if (preferenceFragment != null) {
            preferenceFragment.onPause();
        }

        super.onBackPressed();
    }

    public static class GeneralPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName(COMMAND_PREFERENCE_NAME);

            addPreferencesFromResource(R.xml.pref_command);
            setHasOptionsMenu(true);

            preferenceScreen = getPreferenceScreen();
            autosetPreference = (CheckBoxPreference) findPreference("autoset");
            keyPreference = (EditTextPreference) findPreference("key");
            valuePreference = (EditTextPreference) findPreference("value");
            actionPreferences.put("navigation", findPreference("action_navigation"));
            actionPreferences.put("volume", findPreference("action_volume"));
            actionPreferences.put("media", findPreference("action_media"));
            actionPreferences.put("application", findPreference("action_application"));
            actionPreferences.put("shell", findPreference("action_shell"));
            actionPreferences.put("send", findPreference("action_send"));
            actionPreferences.put("system", findPreference("action_system"));

            bindPreferenceSummaryToValue(keyPreference, COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(valuePreference, COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("scatter"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("action_category"), COMMAND_PREFERENCE_NAME);
            for (Map.Entry<String, Preference> entry : actionPreferences.entrySet()) {
                bindPreferenceSummaryToValue(entry.getValue(), COMMAND_PREFERENCE_NAME);
            }
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
            Intent intent = new Intent();

            float scatter = 0;
            try {
                scatter = Float.parseFloat(
                        ((EditTextPreference) findPreference("scatter")).getText());
            } catch (Exception ex) {
                if (App.isDebug()) {
                    Log.w(TAG, "Can not cast [scatter] to float");
                }
            }

            String category = ((ListPreference) findPreference("action_category")).getValue();
            String categoryString = findPreference("action_category").getSummary().toString();
            String action;
            String actionString = "";

            switch (category) {
                case "none":
                    action = "";
                    break;
                case "application":
                    action = ((AppChooserPreference) actionPreferences.get(category)).getValue();
                    break;
                case "shell":
                case "send":
                    action = ((EditTextPreference) actionPreferences.get(category)).getText();
                    break;
                default:
                    action = ((ListPreference) actionPreferences.get(category)).getValue();
                    break;
            }

            if (actionPreferences.containsKey(category)) {
                actionString = String.valueOf(actionPreferences.get(category).getSummary());
            }

            actionString = String.format(
                    getResources().getString(R.string.command_item_action_text),
                    categoryString, actionString);

            if (command != null) {
                command.setKey(keyPreference.getText());
                command.setValue(valuePreference.getText());
                command.setScatter(scatter);
                command.setThrough(
                        ((CheckBoxPreference) findPreference("is_through")).isChecked());
                command.setCategory(category);
                command.setAction(action);
                command.setActionString(actionString);
            }

            autosetPreference.setChecked(false);

            intent.putExtra("command", command);

            getActivity().setResult(RESULT_OK, intent);
        }
    }
}
