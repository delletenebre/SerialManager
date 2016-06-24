package kg.delletenebre.serialmanager.Commands;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class CommandsDatabase {
    private static final String TAG = "CommandsDatabase";

    public static final String KEY_ID = "_id";
    public static final String KEY_POSITION = "position";
    public static final String KEY_COMMAND = "command";

    private DatabaseHelper databaseHelper;
    private SQLiteDatabase database;

    private static final String DATABASE_NAME = "SerialManager";
    private static final String COMMANDS_TABLE = "commands";
    private static final int DATABASE_VERSION = 3;

    private final static Gson gson = new Gson();
    private final Context context;

    private static final String CREATE_TABLE_COMMANDS =
            "CREATE TABLE if not exists " + COMMANDS_TABLE + " (" +
                    KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    KEY_POSITION + " INTEGER DEFAULT 0," +
                    KEY_COMMAND + " TEXT NOT NULL DEFAULT ''" +
                    ");";

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, CREATE_TABLE_COMMANDS);
            db.execSQL(CREATE_TABLE_COMMANDS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + COMMANDS_TABLE);
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

    public Command create(Command command) {

        ContentValues initialValues = new ContentValues();

        initialValues.put(KEY_POSITION, command.getPosition());
        initialValues.put(KEY_COMMAND, gson.toJson(command));

        long id = database.insert(COMMANDS_TABLE, null, initialValues);
        if (id > -1) {
            command.setId(id);
            return command;
        }

        return null;
    }

    public boolean deleteAll() {
        int doneDelete = database.delete(COMMANDS_TABLE, null, null);
        return doneDelete > 0;
    }

    public boolean delete(long id) {
        return database.delete(COMMANDS_TABLE, KEY_ID +  "=" + id, null) > 0;
    }

    public int update(Command command) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_COMMAND, gson.toJson(command));

        return database.update(COMMANDS_TABLE, contentValues,
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
                    database.update(COMMANDS_TABLE, contentValues,
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

        Cursor cursor = database.query(COMMANDS_TABLE, new String[] {KEY_ID,
                    KEY_COMMAND, KEY_POSITION},
                null, null, null, null, KEY_POSITION);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndex(KEY_ID));
                int position = cursor.getInt(cursor.getColumnIndex(KEY_POSITION));
                Command command = gson.fromJson(
                        cursor.getString(cursor.getColumnIndex(KEY_COMMAND)), Command.class);

                list.add(command.setId(id).setPosition(position));
            }

            cursor.close();
        }

        return list;
    }

}
