package kg.delletenebre.serialmanager.Commands;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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

import com.rarepebble.colorpicker.ColorPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.Hotkey;
import kg.delletenebre.serialmanager.Preferences.AppChooserPreference;
import kg.delletenebre.serialmanager.R;

public class CommandSettingsActivity extends AppCompatActivity {

    private static final String TAG = "CommandSettingsActivity";

    public static final String COMMAND_PREFERENCE_NAME = "command";
    public static CheckBoxPreference autosetPreference;
    public static ListPreference typePreference;
    public static EditTextPreference keyPreference, valuePreference,
            keyboardNamePreference, keyboardEvPreference;

    private static PreferenceScreen preferenceScreen;

    private GeneralPreferenceFragment preferenceFragment;
    private static Command command;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        command = (Command) getIntent().getSerializableExtra("command");
        if (command != null) {
            SharedPreferences.Editor editor =
                    getSharedPreferences(COMMAND_PREFERENCE_NAME, Context.MODE_PRIVATE).edit();

            float scatter = command.getScatter();
            int gpioPinNumber = command.getGpioPinNumber();
            String category = command.getCategory();
            if (category.isEmpty()) {
                category = "none";
            }

            editor.putString("type", command.getType());
            editor.putString("command_typed_gpio_pin_number", (gpioPinNumber > -1) ? String.valueOf(gpioPinNumber) : "");
            editor.putString("command_typed_keyboard_name", command.getKeyboardName());
            editor.putString("command_typed_keyboard_ev", command.getKeyboardEv());
            editor.putString("key", command.getKey());
            editor.putString("value", command.getValue());
            editor.putString("scatter", (scatter != 0) ? String.valueOf(scatter) : "");
            editor.putBoolean("is_through", command.getThrough());
            editor.putString("action_category", category);
            editor.putString("action_" + category, command.getAction());
            editor.putString("actionString", command.getActionString());

            Command.Overlay overlay = command.getOverlay();
            editor.putBoolean("overlay_enabled", overlay.isEnabled());
            editor.putString("overlay_text", overlay.getText());
            editor.putString("overlay_timer", String.valueOf(overlay.getTimer()));
            editor.putBoolean("overlay_hide_on_click", overlay.isHideOnClick());
            editor.putString("overlay_show_animation", overlay.getShowAnimation());
            editor.putString("overlay_hide_animation", overlay.getHideAnimation());
            editor.putString("overlay_position", overlay.getPosition());
            editor.putString("overlay_position_x", String.valueOf(overlay.getPositionX()));
            editor.putString("overlay_position_y", String.valueOf(overlay.getPositionY()));
            editor.putBoolean("overlay_height_equals_status_bar", overlay.isHeightEqualsStatusBar());
            editor.putBoolean("overlay_width_full", overlay.isWidthEqualsScreen());
            editor.putString("overlay_text_align", overlay.getTextAlign());
            editor.putString("overlay_font_size", String.valueOf(overlay.getFontSize()));
            editor.putInt("overlay_font_color", overlay.getFontColor());
            editor.putInt("overlay_background_color", overlay.getBackgroundColor());

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
    }

    @Override
    protected void onPause() {
        App.setAliveActivity(null);
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        typePreference = null;
        keyboardNamePreference = null;
        keyboardEvPreference = null;
        autosetPreference = null;
        keyPreference = null;
        valuePreference = null;
        preferenceScreen = null;
    }

    public static class GeneralPreferenceFragment extends PreferenceFragment {
        private Map<String, List<Preference>> typedPreferences;
        private Map<String, Preference> actionPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName(COMMAND_PREFERENCE_NAME);

            addPreferencesFromResource(R.xml.pref_command);
            setHasOptionsMenu(true);

            preferenceScreen = getPreferenceScreen();
            typePreference = (ListPreference) findPreference("type");
            keyboardNamePreference = (EditTextPreference) findPreference("command_typed_keyboard_name");
            keyboardEvPreference = (EditTextPreference) findPreference("command_typed_keyboard_ev");
            autosetPreference = (CheckBoxPreference) findPreference("autoset");
            keyPreference = (EditTextPreference) findPreference("key");
            valuePreference = (EditTextPreference) findPreference("value");

            actionPreferences = new HashMap<>();
            actionPreferences.put("navigation", findPreference("action_navigation"));
            actionPreferences.put("volume", findPreference("action_volume"));
            actionPreferences.put("media", findPreference("action_media"));
            actionPreferences.put("application", findPreference("action_application"));
            actionPreferences.put("shell", findPreference("action_shell"));
            actionPreferences.put("send", findPreference("action_send"));
            actionPreferences.put("system", findPreference("action_system"));
            actionPreferences.put("gpio", findPreference("action_gpio"));

            typedPreferences = new HashMap<>();
            typedPreferences.put("default", Collections.singletonList(
                    findPreference("key")));

            typedPreferences.put("gpio", Collections.singletonList(
                    findPreference("command_typed_gpio_pin_number")));

            typedPreferences.put("keyboard", Arrays.asList(
                    findPreference("command_typed_keyboard_name"),
                    findPreference("command_typed_keyboard_ev")));

            for (Map.Entry<String, List<Preference>> entry : typedPreferences.entrySet()) {
                for (Preference pref : entry.getValue()) {
                    //if (!pref.getClass().getName().equals("android.preference.CheckBoxPreference")) {
                    bindPreferenceSummaryToValue(pref, COMMAND_PREFERENCE_NAME);
                    //}
                }
            }

            bindPreferenceSummaryToValue(findPreference("type"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(keyPreference, COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(valuePreference, COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("scatter"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("action_category"), COMMAND_PREFERENCE_NAME);
            for (Map.Entry<String, Preference> entry : actionPreferences.entrySet()) {
                bindPreferenceSummaryToValue(entry.getValue(), COMMAND_PREFERENCE_NAME);
            }

            bindPreferenceSummaryToValue(findPreference("overlay_text"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_timer"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_show_animation"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_hide_animation"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_position"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_position_x"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_position_y"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_text_align"), COMMAND_PREFERENCE_NAME);
            bindPreferenceSummaryToValue(findPreference("overlay_font_size"), COMMAND_PREFERENCE_NAME);

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

        private Preference.OnPreferenceChangeListener bindPreferenceSummaryToValueListener =
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
                            } else if (listPreference.getKey().equals("type")) {
                                if (preferenceScreen != null) {
                                    for (Map.Entry<String, List<Preference>> entry : typedPreferences.entrySet()) {
                                        for (Preference pref : entry.getValue()) {
                                            preferenceScreen.removePreference(pref);

                                            if (stringValue.equals(entry.getKey())) {
                                                preferenceScreen.addPreference(pref);
                                            }
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

        private void bindPreferenceSummaryToValue(Preference preference, String name) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(bindPreferenceSummaryToValueListener);

                bindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                        preference.getContext().getSharedPreferences(name, MODE_PRIVATE)
                                .getString(preference.getKey(), ""));
            }
        }

        private void setResultForActivity() {
            Intent intent = new Intent();

            float scatter = 0;
            try {
                scatter = Float.parseFloat(
                        ((EditTextPreference) findPreference("scatter")).getText());
            } catch (NumberFormatException e) {}

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
                case "gpio":
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

            Command.Overlay overlay = command.getOverlay();

            if (command != null) {
                String commandType = ((ListPreference) findPreference("type")).getValue();
                command.setType(commandType)
                        .setKey(keyPreference.getText())
                        .setValue(valuePreference.getText())
                        .setScatter(scatter)
                        .setThrough(
                            ((CheckBoxPreference) findPreference("is_through")).isChecked())
                        .setCategory(category)
                        .setAction(action)
                        .setActionString(actionString);

                List<Preference> typedPreferencesList = typedPreferences.get(commandType);
                switch (commandType) {
                    case "gpio":
                        int gpioPinNumber = -1;
                        try {
                            gpioPinNumber = Integer.parseInt(((EditTextPreference) typedPreferencesList.get(0)).getText());
                        } catch (Exception e) {}
                        command.setGpioPinNumber(gpioPinNumber);
                        break;

                    case "keyboard":
                        command.setKeyboardName(((EditTextPreference) typedPreferencesList.get(0)).getText());
                        command.setKeyboardEv(((EditTextPreference) typedPreferencesList.get(1)).getText());
                        command.setKey(Hotkey.createCommandIdentifier(command.getKeyboardName(), command.getKeyboardEv()));
                        break;
                }

                overlay.setEnabled(
                        ((CheckBoxPreference) findPreference("overlay_enabled")).isChecked());
                overlay.setText(
                        ((EditTextPreference) findPreference("overlay_text")).getText());
                overlay.setTimer(Integer.parseInt(
                        ((EditTextPreference) findPreference("overlay_timer")).getText()));
                overlay.setHideOnClick(
                        ((CheckBoxPreference) findPreference("overlay_hide_on_click")).isChecked());
                overlay.setShowAnimation(
                        ((ListPreference) findPreference("overlay_show_animation")).getValue());
                overlay.setHideAnimation(
                        ((ListPreference) findPreference("overlay_hide_animation")).getValue());
                overlay.setPosition(
                        ((ListPreference) findPreference("overlay_position")).getValue());
                overlay.setPositionX(Integer.parseInt(
                        ((EditTextPreference) findPreference("overlay_position_x")).getText()));
                overlay.setPositionY(Integer.parseInt(
                        ((EditTextPreference) findPreference("overlay_position_y")).getText()));
                overlay.setHeightEqualsStatusBar(
                        ((CheckBoxPreference) findPreference("overlay_height_equals_status_bar")).isChecked());
                overlay.setWidthEqualsScreen(
                        ((CheckBoxPreference) findPreference("overlay_width_full")).isChecked());
                overlay.setTextAlign(
                        ((ListPreference) findPreference("overlay_text_align")).getValue());
                overlay.setFontSize(Integer.parseInt(
                        ((EditTextPreference) findPreference("overlay_font_size")).getText()));
                overlay.setFontColor(
                        ((ColorPreference) findPreference("overlay_font_color")).getColor());
                overlay.setBackgroundColor(
                        ((ColorPreference) findPreference("overlay_background_color")).getColor());
            }

            autosetPreference.setChecked(false);

            intent.putExtra("command", command);

            getActivity().setResult(RESULT_OK, intent);
        }
    }
}
