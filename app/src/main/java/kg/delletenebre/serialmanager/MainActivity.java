package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();

    protected static final int REQ_NEW_COMMAND = 100;
    protected static final int REQ_EDIT_COMMAND = 101;

    protected static Activity activity;
    private static Context mContext;

    private SharedPreferences _settings;

    private RecyclerView mRecyclerView;
    private static RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private static List<Command> commands;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        _settings = PreferenceManager.getDefaultSharedPreferences(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRecyclerView = (RecyclerView) findViewById(R.id.tasks_list);
        //mRecyclerView.setHasFixedSize(true);

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        initializeData();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), CommandSettingsActivity.class);
                int id = _settings.getInt("counter", 0);
                intent.putExtra("pref_id", id);
                intent.putExtra("pref_uuid", UUID.randomUUID().toString());
                startActivityForResult(intent, REQ_NEW_COMMAND);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            return;
        }

        if (resultCode == RESULT_OK) {
            int id = data.getIntExtra("id", -1);
            String uuid = data.getStringExtra("uuid");
            String key = data.getStringExtra("key");
            String value = data.getStringExtra("value");
            String scatter = data.getStringExtra("scatter");
            boolean isThrough = data.getBooleanExtra("is_through", false);
            String actionCategory = data.getStringExtra("actionCategory");
            int actionCategoryId = Integer.parseInt(actionCategory);

            String actionVolume = data.getStringExtra("actionVolume");

            String actionMedia = data.getStringExtra("actionMedia");

            String actionApplication = data.getStringExtra("actionApplication");

            String action = SerialService.getActionByActionCategoryId(this, actionCategoryId,
                    actionVolume, actionMedia, actionApplication);

            if (requestCode == REQ_NEW_COMMAND) {
                addCommand(id, uuid, key, value, scatter, isThrough, actionCategoryId, action);
            } else if (requestCode == REQ_EDIT_COMMAND) {
                editCommand(id, key, value, scatter, isThrough, actionCategoryId, action);
            }
        }
    }


    private void initializeData() {
        commands = SerialService.initializeCommands(this);

        Collections.sort(commands);
        mAdapter = new CommandsAdapter(this, commands);
        mRecyclerView.setAdapter(mAdapter);
    }

    public void addCommand(int id, String uuid, String key, String value, String scatter,
                           boolean isThrough, int actionCategoryId, String action) {

        SharedPreferences.Editor _settingEditor = _settings.edit();
        _settingEditor.putInt("counter", id + 1);
        _settingEditor.apply();

        commands.add(
                new Command(id, uuid, key, value, scatter, isThrough, actionCategoryId, action));
        mAdapter.notifyDataSetChanged();

        if (SerialService.service != null) {
            SerialService.setCommands(commands);
        }
    }

    public void editCommand(int id, String key, String value, String scatter,
                           boolean isThrough, int actionCategoryId, String action) {

        int i = 0;
        for (; i < commands.size(); i++) {
            if (commands.get(i).getId() == id) {
                commands.get(i).update(key, value, scatter, isThrough, actionCategoryId, action);
                mAdapter.notifyDataSetChanged();
                break;
            }
        }

        if (SerialService.service != null) {
            SerialService.setCommands(commands);
        }
    }

    public static void removeCommand(String uuid) {
        int i = 0;
        for (; i < commands.size(); i++) {
            if (commands.get(i).getUUID().equals(uuid)) {
                commands.remove(i);
                mAdapter.notifyDataSetChanged();
                break;
            }
        }
    }
}
