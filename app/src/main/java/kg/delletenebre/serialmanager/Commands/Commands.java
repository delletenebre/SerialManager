package kg.delletenebre.serialmanager.Commands;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.App;
import kg.delletenebre.serialmanager.MainActivity;
import kg.delletenebre.serialmanager.Preferences.AppChooserPreference;
import kg.delletenebre.serialmanager.R;
import kg.delletenebre.serialmanager.UsbService;
import xdroid.toaster.Toaster;

public class Commands {
    public static final String TAG = "Commands";
    private static List<Command> commands;
    private static CommandsDatabase database;

    public static void setCommands(List<Command> commands) {
        Commands.commands = commands;
    }
    public static List<Command> getCommands() {
        if (commands == null) {
            commands = loadCommands();
        }

        return commands;
    }

    public static List<Command> loadCommands() {
        database = getDatabase();
        return database.fetchAll();
    }

    public static CommandsDatabase getDatabase() {
        if (database == null) {
            database = new CommandsDatabase(App.getContext());
            database.open();
        }

        return database;
    }

    public static void processReceivedData(String data) {
        final Pattern pattern = Pattern.compile("<(.+?):(.+?)>");
        final Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            final String key = matcher.group(1);
            final String val = matcher.group(2);

            final Activity activity = App.getAliveActivity();

            if (activity != null) {
                Toaster.toast(String.format(
                        activity.getResources().getString(R.string.toast_received_command),
                        key, val));

                if (CommandSettingsActivity.autosetPreference != null
                        && CommandSettingsActivity.autosetPreference.isChecked()
                        && CommandSettingsActivity.keyPreference != null
                        && CommandSettingsActivity.valuePreference != null) {

                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            CommandSettingsActivity.keyPreference.setText(key);
                            CommandSettingsActivity.keyPreference.setSummary(key);

                            CommandSettingsActivity.valuePreference.setText(val);
                            CommandSettingsActivity.valuePreference.setSummary(val);
                        }
                    });
                }

            } else {
                detectCommand(key, val);
            }
        }
    }

    public static void detectCommand(String key, String value) {
        Context context = App.getContext();
        boolean detected = false;
        commands = getCommands();

        for (Command command: commands) {
            boolean inRange = false;
            if (App.isNumber(command.getValue()) && App.isNumber(value)) {
                float commandValue = Float.parseFloat(command.getValue());
                float receivedValue = Float.parseFloat(value);

                inRange = commandValue - command.getScatter() <= receivedValue
                                && receivedValue <= commandValue + command.getScatter();
            }

            String category = command.getCategory();
            String action = command.getAction();

            if (command.getKey().equals(key)
                    && (command.getValue().isEmpty()
                    || (command.getValue().equals(value) || inRange))) {

                if (category.equals("navigation")) {
                    App.emulateKeyEvent(command.getAction());

                } else if (category.equals("volume")) {
                    if (action.equals("up") || action.equals("down")) {
                        App.changeVolume(action);

                    } else if (action.equals("mute")) {
                        App.setMute();
                    }

                } else if (category.equals("media")) {
                    if (App.isNumber(action)) {
                        App.emulateMediaButton(Integer.parseInt(action));
                    }

                } else if (category.equals("application")) {
                    Intent intent = AppChooserPreference.getIntentValue(
                            action, null);

                    if (intent == null) {
                        intent = new Intent(context, MainActivity.class);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);

                } else if (category.equals("shell")) {
                    App.runShellCommand(action);

                } else if (category.equals("send")) { // Send Command
                    UsbService.usbSend(action);

                } else if (category.equals("system")) { // System Management
                    switch (action) {
                        case "shutdown":
                            App.runShellCommand("reboot -p");
                            break;

                        case "reboot":
                            App.runShellCommand("reboot");
                            break;

                        case "set_brightness":
                            App.setScreenBrightness(value);
                            break;
                    }
                }


                detected = !command.getThrough();
                break;
            }
        }


        if (!detected) {
            Intent intent = new Intent(App.ACTION_NEW_DATA_RECEIVED);
            intent.putExtra("key", key);
            intent.putExtra("value", value);
            context.sendBroadcast(intent);
        }
    }

    public static List<String> checkCommandsPreferences() {
        Context context = App.getContext();
        List<String> preferences = new ArrayList<>();

        File prefsDir = new File(context.getFilesDir().getParent(), "/shared_prefs/");
        if (prefsDir.exists()) {
            for (File f : prefsDir.listFiles()) {
                if (f.isFile()) {
                    String uuid = f.getName();
                    uuid = uuid.substring(0, uuid.length() - 4);
                    if (Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", uuid)) {
                        preferences.add(uuid);
                    }
                }
            }
        }

        return preferences;
    }

    public static void deleteAllCommands() {
        if (commands != null && !commands.isEmpty()) {
            database.deleteAll();
            App.restartAliveActivity();
        }
    }

    public static void importCommands(File file) {
        String content = "";
        try {
            FileInputStream is = new FileInputStream(file);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            content = new String(buffer, Charset.forName("UTF8"));
        } catch (IOException ex) {
            Toaster.toast(R.string.message_file_read_error);
            ex.printStackTrace();
        }

        if (!content.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(content);
                for (int i = 0; i < jsonArray.length(); i++) {
                    try {
                        JSONObject json = jsonArray.getJSONObject(i);
                        String key = json.getString("key");
                        String value  = json.getString("value");
                        float scatter = json.getInt("scatter");
                        boolean through = json.getBoolean("through");
                        String category = json.getString("category");
                        String action  = json.getString("action");
                        String actionString  = json.getString("actionString");

                        ((MainActivity) App.getAliveActivity()).commandsListAdapter.createItem(
                                key, value, scatter, through, category, action, actionString);

                    } catch (JSONException ex) {
                        ex.printStackTrace();
                    }
                }

                Toaster.toast(R.string.message_commands_import_success);
            } catch (JSONException ex) {
                Toaster.toast(R.string.message_commands_import_error);
                ex.printStackTrace();
            }
        } else {
            Toaster.toast(R.string.message_file_read_error);
        }
    }

    public static void exportCommands() {
        Log.d(TAG, "exportCommands");
        commands = loadCommands();
        if (commands != null && !commands.isEmpty()) {
            JSONArray jsonArray = new JSONArray();
            for (Command command: commands) {
                Log.d(TAG, command.getKey());
                try {
                    JSONObject json = new JSONObject();
                    json.put("key", command.getKey());
                    json.put("value", command.getValue());
                    json.put("scatter", command.getScatter());
                    json.put("through", command.getThrough());
                    json.put("category", command.getCategory());
                    json.put("action", command.getAction());
                    json.put("actionString", command.getActionString());

                    jsonArray.put(json);
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
            }

            String fileName = "serial_manager_backup.json";
            if (jsonArray.length() > 0
                    && createAndSaveFile(fileName, jsonArray.toString())) {
                Toaster.toastLong(String.format(
                        App.getContext().getResources()
                                .getString(R.string.message_export_success),
                        Environment.getExternalStorageDirectory() + "/" + fileName));
            }
        }
    }

    public static boolean createAndSaveFile(String name, String body) {
        try {
            FileWriter file = new FileWriter(Environment.getExternalStorageDirectory()
                    + "/" + name);
            file.write(body);
            file.flush();
            file.close();

            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return false;
    }


    public static void importOldCommands(List<String> preferences) {
        Context context = App.getContext();
        for (String name : preferences) {
            SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);


            String key = prefs.getString("key", "");
            String value = prefs.getString("value", "");
            float scatter = 0;
            String sScatter = prefs.getString("scatter", "");
            if (App.isNumber(sScatter)) {
                scatter = Float.parseFloat(sScatter);
            }
            boolean through = prefs.getBoolean("is_through", false);
            String category = prefs.getString("action_category", "0");

            Map<String, String> actions = new HashMap<>();

            actions.put("0", App.getContext().getString(R.string.no_action));
            actions.put("1", prefs.getString("action_navigation", "0"));
            actions.put("2", prefs.getString("action_volume", "0"));
            actions.put("3", prefs.getString("action_media", "0"));
            actions.put("4", prefs.getString("action_application",
                    context.getString(R.string.pref_shortcut_default)));
            actions.put("5", prefs.getString("action_shell", ""));
            actions.put("6", prefs.getString("action_send", ""));
            actions.put("8", prefs.getString("action_system", ""));

            String action = actions.get(category);

            Map<String, String> newCategories = new HashMap<>();
            newCategories.put("0", "none");
            newCategories.put("1", "navigation");
            newCategories.put("2", "volume");
            newCategories.put("3", "media");
            newCategories.put("4", "application");
            newCategories.put("5", "shell");
            newCategories.put("6", "send");
            newCategories.put("8", "system");

            category = newCategories.get(category);
            if (category.equals("volume")) {
                switch (action) {
                    case "0":
                        action = "up";
                        break;
                    case "1":
                        action = "down";
                        break;
                    case "2":
                        action = "mute";
                        break;
                }
            } else if (category.equals("media")) {
                switch (action) {
                    case "0":
                        action = "89";
                        break;
                    case "1":
                        action = "88";
                        break;
                    case "2":
                        action = "85";
                        break;
                    case "3":
                        action = "87";
                        break;
                    case "4":
                        action = "90";
                        break;
                }
            }

            ((MainActivity) App.getAliveActivity()).commandsListAdapter.createItem(
                    key, value, scatter, through, category, action, "");
            deleteOldCommand(name);
            Toaster.toast(R.string.message_commands_import_success);
        }
    }

    public static void deleteOldCommands(List<String> preferences) {
        for (String prefName : preferences) {
            deleteOldCommand(prefName);
        }
    }

    public static void deleteOldCommand(String prefName) {
        Context context = App.getContext();

        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        if (!new File(context.getFilesDir().getParent(),
                "/shared_prefs/" + prefName + ".xml").delete() && App.isDebug()) {
            Log.w(TAG, "Error deleting " + "/shared_prefs/" + prefName + ".xml" + " file");
        }
    }
}
