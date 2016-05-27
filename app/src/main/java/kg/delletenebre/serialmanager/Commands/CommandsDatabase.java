package kg.delletenebre.serialmanager.Commands;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class CommandsDatabase {
    private static final String TAG = "CommandsDatabase";

    public static final String KEY_ID = "_id";
    public static final String KEY_KEY = "key";
    public static final String KEY_VALUE = "value";
    public static final String KEY_SCATTER = "scatter";
    public static final String KEY_THROUGH = "through";//Boolean flag = (cursor.getInt(cursor.getColumnIndex("flag")) == 1);
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_ACTION = "action";
    public static final String KEY_ACTION_STRING = "action_string";
    public static final String KEY_POSITION = "position";

    private DatabaseHelper databaseHelper;
    private SQLiteDatabase database;

    private static final String DATABASE_NAME = "SerialManager";
    private static final String SQLITE_TABLE = "commands";
    private static final int DATABASE_VERSION = 2;

    private final Context context;

    private static final String DATABASE_CREATE =
            "CREATE TABLE if not exists " + SQLITE_TABLE + " (" +
                    KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    KEY_KEY + "," +
                    KEY_VALUE + "," +
                    KEY_SCATTER + " REAL DEFAULT 0," +
                    KEY_THROUGH + " INTEGER DEFAULT 0," +
                    KEY_CATEGORY + "," +
                    KEY_ACTION + "," +
                    KEY_ACTION_STRING + "," +
                    KEY_POSITION + " INTEGER DEFAULT 0" +
                    ");";

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.w(TAG, DATABASE_CREATE);
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + SQLITE_TABLE);
            onCreate(db);
        }
    }

    public CommandsDatabase(Context context) {
        this.context = context;
    }

    public CommandsDatabase open() throws SQLException {
        databaseHelper = new DatabaseHelper(context);
        database = databaseHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }

    public Command create(String key, String value, float scatter, boolean through, String category,
                       String action, String actionString, int position) {

        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_KEY, key);
        initialValues.put(KEY_VALUE, value);
        initialValues.put(KEY_SCATTER, scatter);
        initialValues.put(KEY_THROUGH, through ? 1 : 0);
        initialValues.put(KEY_CATEGORY, category);
        initialValues.put(KEY_ACTION, action);
        initialValues.put(KEY_ACTION_STRING, actionString);
        initialValues.put(KEY_POSITION, position);

        long id = database.insert(SQLITE_TABLE, null, initialValues);
        if (id > -1) {
            return new Command(id, key, value, scatter, through, category, action,
                    actionString, position);
        }

        return null;
    }

    public boolean deleteAll() {
        int doneDelete = 0;
        doneDelete = database.delete(SQLITE_TABLE, null , null);
        Log.w(TAG, Integer.toString(doneDelete));
        return doneDelete > 0;

    }

    public boolean delete(long id) {
        return database.delete(SQLITE_TABLE, KEY_ID +  "=" + id, null) > 0;
    }

    public int update(Command command) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_KEY, command.getKey());
        contentValues.put(KEY_VALUE, command.getValue());
        contentValues.put(KEY_SCATTER, command.getScatter());
        contentValues.put(KEY_THROUGH, command.getThrough() ? 1 : 0);
        contentValues.put(KEY_CATEGORY, command.getCategory());
        contentValues.put(KEY_ACTION, command.getAction());
        contentValues.put(KEY_ACTION_STRING, command.getActionString());

        return database.update(SQLITE_TABLE, contentValues,
                KEY_ID +  "=" + command.getId(), null);
    }

    public void updatePositions(List<Command> commands) {
        database.beginTransaction();
        ContentValues contentValues = new ContentValues();

        try {
            int position = 0;
            for (Command command: commands) {
                if (command.getPosition() != position) {
                    command.setPosition(position);
                    contentValues.put(KEY_POSITION, position);
                    database.update(SQLITE_TABLE, contentValues,
                            KEY_ID +  "=" + command.getId(), null);
                }
                position++;
            }

            database.setTransactionSuccessful();

        } catch(Exception ex) {
            Log.e("Error in transaction", ex.getLocalizedMessage());

        } finally {
            database.endTransaction();
        }
    }

    public List<Command> fetchAll() {
        List<Command> list = new ArrayList<>();

        Cursor cursor = database.query(SQLITE_TABLE, new String[] {KEY_ID,
                    KEY_KEY, KEY_VALUE, KEY_SCATTER, KEY_THROUGH, KEY_CATEGORY, KEY_ACTION,
                    KEY_ACTION_STRING, KEY_POSITION},
                null, null, null, null, KEY_POSITION);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndex(KEY_ID));
                String key = cursor.getString(cursor.getColumnIndex(KEY_KEY));
                String value = cursor.getString(cursor.getColumnIndex(KEY_VALUE));
                float scatter = cursor.getFloat(cursor.getColumnIndex(KEY_SCATTER));
                boolean through = cursor.getInt(cursor.getColumnIndex(KEY_THROUGH)) == 1;
                String category = cursor.getString(cursor.getColumnIndex(KEY_CATEGORY));
                String action = cursor.getString(cursor.getColumnIndex(KEY_ACTION));
                String actionString = cursor.getString(cursor.getColumnIndex(KEY_ACTION_STRING));
                int position = cursor.getInt(cursor.getColumnIndex(KEY_POSITION));

                list.add(new Command(id, key, value, scatter, through, category, action,
                                actionString, position));
            }
        }

        return list;
    }

}
