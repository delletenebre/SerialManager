package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.delletenebre.serialmanager.Preferences.AppChooserPreference;
import xdroid.toaster.Toaster;

public class Commands {
    private static List<Command> commands;

    public static void setCommands(List<Command> commands) {
        Commands.commands = commands;
    }
    public static List<Command> getCommands() {
        if (commands == null) {
            initializeCommands();
        }

        return commands;
    }

    public static void initializeCommands() {
        if (commands == null) {
            Context context = App.getAppContext();
            commands = new ArrayList<>();

            File prefsDir = new File(context.getFilesDir().getParent(), "/shared_prefs/");
            if (prefsDir.exists()) {
                for (File f : prefsDir.listFiles()) {
                    if (f.isFile()) {
                        String uuid = f.getName();
                        uuid = uuid.substring(0, uuid.length() - 4);
                        if (Pattern.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", uuid)) {
                            SharedPreferences settings = context.getSharedPreferences(uuid, Context.MODE_PRIVATE);
                            int id = settings.getInt("id", -1);

                            String key = settings.getString("key", "");
                            String value = settings.getString("value", "");
                            String scatter = settings.getString("scatter", "");
                            boolean isThrough = settings.getBoolean("is_through", false);
                            int actionCategoryId =
                                    Integer.parseInt(settings.getString("action_category", "0"));

                            String actionNavigation = settings.getString("action_navigation", "0");

                            String actionVolume = settings.getString("action_volume", "0");

                            String actionMedia = settings.getString("action_media", "0");


                            String actionApp = settings.getString("action_application", "");
                            String actionApplication = (!actionApp.isEmpty())
                                    ? actionApp
                                    : context.getString(R.string.pref_shortcut_default);

                            String action = getActionByActionCategoryId(actionCategoryId,
                                    actionNavigation, actionVolume, actionMedia, actionApplication);

                            commands.add(new Command(id, uuid, key, value, scatter, isThrough,
                                    actionCategoryId, action));
                        }

                    }
                }
            }
        }
    }

    public static String getActionByActionCategoryId(int id,
                                                     String navigation, String volume,
                                                     String media, String app) {
        switch (id) {
            case 0:
                return App.getAppContext().getString(R.string.no_action);

            case 1:
                return navigation;

            case 2:
                return volume;

            case 3:
                return media;

            case 4:
                return app;
        }

        return "";
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
                        App.getAppContext().getResources().getString(R.string.toast_received_command),
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
        Context context = App.getAppContext();
        boolean detected = false;
        if (commands == null) {
            initializeCommands();
        }

        for (Command command: commands) {
            boolean inRange = false;
            if (NumberUtils.isNumber(command.value) && NumberUtils.isNumber(command.scatter) && NumberUtils.isNumber(value)) {
                float commandScatter = Float.parseFloat(command.scatter);
                float commandValue = Float.parseFloat(command.value);
                float receivedValue = Float.parseFloat(value);

                inRange = commandValue - commandScatter <= receivedValue && receivedValue <= commandValue + commandScatter;
            }

            if (command.key.equals(key) && (command.value.equals(value) || inRange)) {

                if (command.actionCategoryId == 0) { // No action
                    // Nothing to do

                } else if (command.actionCategoryId == 1) { // Navigation
                    App.emulateKeyEvent(command.action);

                } else if (command.actionCategoryId == 2) { // Volume

                    switch (command.action) {
                        case "0":
                            App.changeVolume("UP");
                            break;

                        case "1":
                            App.changeVolume("DOWN");
                            break;

                        case "2":
                            App.setMute();
                            break;
                    }

                } else if (command.actionCategoryId == 3) { // Media

                    switch (command.action) {
                        case "0":
                            App.emulateMediaButton(KeyEvent.KEYCODE_MEDIA_REWIND);
                            break;

                        case "1":
                            App.emulateMediaButton(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                            break;

                        case "2":
                            App.emulateMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                            break;

                        case "3":
                            App.emulateMediaButton(KeyEvent.KEYCODE_MEDIA_NEXT);
                            break;

                        case "4":
                            App.emulateMediaButton(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                            break;
                    }

                } else if (command.actionCategoryId == 4) { // Application


                    Intent intent = AppChooserPreference.getIntentValue(
                            command.action, null);

                    if (intent == null) {
                        intent = new Intent(context, MainActivity.class);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);

                }

                detected = !command.isThrough;
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
}
