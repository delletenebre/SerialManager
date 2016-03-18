package kg.delletenebre.serialmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EventsReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)
                || intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {

            if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                Log.i(TAG, "****ACTION_BOOT_COMPLETED****");

//                APP mAPP = APP.getInstance(PreferenceManager.getDefaultSharedPreferences(context));
//                if (mAPP.isTOAST()) {
//                    Toast.makeText(context.getApplicationContext(),
//                            R.string.toast_bootup,
//                            Toast.LENGTH_LONG).show();
//                }

            } else {
                Log.i(TAG, "****ACTION_USER_PRESENT****");
            }

            Intent mIntent = new Intent(context, HiddenActivity.class);
            mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mIntent);

        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            Log.i(TAG, "****ACTION_SCREEN_OFF****");
            context.stopService(new Intent(context, SerialService.class));
        }

    }
}
