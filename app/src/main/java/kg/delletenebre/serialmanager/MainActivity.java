package kg.delletenebre.serialmanager;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.File;
import java.util.List;

import kg.delletenebre.serialmanager.Commands.Command;
import kg.delletenebre.serialmanager.Commands.CommandSettingsActivity;
import kg.delletenebre.serialmanager.Commands.Commands;
import kg.delletenebre.serialmanager.Commands.CommandsListAdapter;
import kg.delletenebre.serialmanager.helper.SimpleItemTouchHelperCallback;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public CommandsListAdapter commandsListAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final List<String> oldCommands = Commands.checkCommandsPreferences();
        if (!oldCommands.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_confirm_import_old_commands)
                    .setMessage(R.string.dialog_content_import_old_commands)
                    .setNegativeButton(R.string.dialog_negative, null)
                    .setNeutralButton(R.string.dialog_delete,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Commands.deleteOldCommands(oldCommands);
                                }
                            })
                    .setPositiveButton(R.string.dialog_import_positive,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Commands.importOldCommands(oldCommands);
                                }
                            })
                    .show();
        }


        commandsListAdapter = new CommandsListAdapter();
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.commands_list);
        if (recyclerView != null) {
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(commandsListAdapter);

            ItemTouchHelper touchHelper = new ItemTouchHelper(
                    new SimpleItemTouchHelperCallback(commandsListAdapter));
            touchHelper.attachToRecyclerView(recyclerView);
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Command command = commandsListAdapter.createItem(new Command());
                    if (command != null) {
                        Intent intent = new Intent(view.getContext(), CommandSettingsActivity.class);
                        intent.putExtra("id", command.getId());
                        intent.putExtra("command", command);
                        startActivityForResult(intent, App.REQUEST_CODE_UPDATE_COMMAND);
                    }
                }
            });
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), 1);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case R.id.action_delete_all_commands:
                Commands.deleteAllCommands();
                break;

            case R.id.action_export_commands:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, App.REQUEST_CODE_ASK_PERMISSIONS_WRITE);
                } else {
                    Commands.exportCommands();
                }
                break;

            case R.id.action_import_commands:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, App.REQUEST_CODE_ASK_PERMISSIONS_READ);
                } else {
                    new FileChooser(this)
                            .setExtension("json")
                            .setFileListener(new FileChooser.FileSelectedListener() {
                                @Override public void fileSelected(final File file) {
                                    Commands.importCommands(file);
                                }
                            })
                            .showDialog();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.setAliveActivity(this);

        // DELETE
//        if (Build.VERSION.SDK_INT < 23
//                || (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this))) {
//            Log.d(TAG, "onResume abrakadabra");
//            Overlay.show(Commands.getCommands().get(0), "abrakadabra");
//        }

    }

    @Override
    protected void onPause() {
        App.setAliveActivity(null);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            if (App.isDebug()) {
                Log.w(TAG, "onActivityResult(): empty data");
            }
            return;
        }

        if (resultCode == RESULT_OK) {
            if (requestCode == App.REQUEST_CODE_UPDATE_COMMAND) {
                commandsListAdapter.updateItem((Command) data.getSerializableExtra("command"));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case App.REQUEST_CODE_ASK_PERMISSIONS_READ:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    restart();
                }
                break;

            case App.REQUEST_CODE_ASK_PERMISSIONS_WRITE:
                restart();
                break;

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void restart() {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, getIntent(), PendingIntent.FLAG_CANCEL_CURRENT);
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(
                AlarmManager.RTC, System.currentTimeMillis() + 500, pi);

        System.exit(0);
    }
}
