package kg.delletenebre.serialmanager;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

public class HiddenActivity extends Activity {
    private final String TAG = getClass().getName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (action != null && action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Intent i = new Intent(getApplicationContext(), ConnectionService.class);
                i.putExtra(UsbManager.EXTRA_DEVICE, device);

                startService(i);
            }
        }

        finish();
    }
}
