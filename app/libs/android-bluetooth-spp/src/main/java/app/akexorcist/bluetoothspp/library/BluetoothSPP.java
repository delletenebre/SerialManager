/*
 * Copyright (C) 2014 Akexorcist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.akexorcist.bluetoothspp.library;

import java.util.ArrayList;
import java.util.Set;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

@SuppressLint("NewApi")
public class BluetoothSPP {
    // Listener for Bluetooth Status & Connection
    private BluetoothStateListener bluetoothStateListener = null;
    private OnDataReceivedListener dataReceivedListener = null;
    private BluetoothConnectionListener bluetoothConnectionListener = null;
    private AutoConnectionListener autoConnectionListener = null;

    // Context from activity which call this class
    private Context mContext;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

    // Member object for the chat services
    private BluetoothService btChatService = null;

    // Name and Address of the connected device
    private String mDeviceName = null;
    private String mDeviceAddress = null;

    private boolean isAutoConnecting = false;
    private boolean isAutoConnectionEnabled = false;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private boolean isServiceRunning = false;

    private String keyword = "";
    private boolean isAndroid = BluetoothState.DEVICE_ANDROID;

    private BluetoothConnectionListener bcl;

    public BluetoothSPP(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public interface BluetoothStateListener {
        public void onServiceStateChanged(int state);
    }

    public interface OnDataReceivedListener {
        public void onDataReceived(byte[] data, String message);
    }

    public interface BluetoothConnectionListener {
        public void onDeviceConnected(String name, String address);
        public void onDeviceDisconnected();
        public void onDeviceConnectionFailed();
    }

    public interface AutoConnectionListener {
        public void onAutoConnectionStarted();
        public void onNewConnection(BluetoothDevice bluetoothDevice);
    }

    public boolean isBluetoothAvailable() {
        try {
            if (mBluetoothAdapter == null || mBluetoothAdapter.getAddress() == null) {
                return false;
            }
        } catch (NullPointerException e) {
            return false;
        }

        return true;
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    public boolean isServiceAvailable() {
        return btChatService != null;
    }

    public boolean isAutoConnecting() {
        return isAutoConnecting;
    }

    public boolean startDiscovery() {
        return mBluetoothAdapter.startDiscovery();
    }

    public boolean isDiscovery() {
        return mBluetoothAdapter.isDiscovering();
    }

    public boolean cancelDiscovery() {
        return mBluetoothAdapter.cancelDiscovery();
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    public int getServiceState() {
        if (btChatService != null) {
            return btChatService.getState();
        }

        return -1;
    }

    public void startService(boolean isAndroid) {
        btChatService = new BluetoothService(mHandler);

        if (btChatService.getState() == BluetoothState.STATE_NONE) {
            isServiceRunning = true;
            btChatService.start(isAndroid);
            this.isAndroid = isAndroid;
        }
    }

    public void stopService() {
        if (btChatService != null) {
            btChatService.stop();
            btChatService = null;
        }

        bluetoothStateListener = null;
        dataReceivedListener = null;
        bluetoothConnectionListener = null;
        autoConnectionListener = null;

        isServiceRunning = false;
    }

    public void setDeviceTarget(boolean isAndroid) {
        stopService();
        startService(isAndroid);
        BluetoothSPP.this.isAndroid = isAndroid;
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothState.MESSAGE_WRITE:
                    break;
                case BluetoothState.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf);
                    if (readBuf.length > 0) {
                        if (dataReceivedListener != null) {
                            dataReceivedListener.onDataReceived(readBuf, readMessage);
                        }
                    }
                    break;
                case BluetoothState.MESSAGE_DEVICE_NAME:
                    mDeviceName = msg.getData().getString(BluetoothState.DEVICE_NAME);
                    mDeviceAddress = msg.getData().getString(BluetoothState.DEVICE_ADDRESS);
                    if (bluetoothConnectionListener != null) {
                        bluetoothConnectionListener.onDeviceConnected(mDeviceName, mDeviceAddress);
                    }
                    isConnected = true;
                    break;
                case BluetoothState.MESSAGE_TOAST:
                    Toast.makeText(mContext, msg.getData().getString(BluetoothState.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothState.MESSAGE_STATE_CHANGE:
                    if (bluetoothStateListener != null) {
                        bluetoothStateListener.onServiceStateChanged(msg.arg1);
                    }
                    if (isConnected && msg.arg1 != BluetoothState.STATE_CONNECTED) {
                        if (bluetoothConnectionListener != null) {
                            bluetoothConnectionListener.onDeviceDisconnected();
                        }
                        if (isAutoConnectionEnabled) {
                            isAutoConnectionEnabled = false;
                            autoConnect(keyword);
                        }
                        isConnected = false;
                        mDeviceName = null;
                        mDeviceAddress = null;
                    }

                    if (!isConnecting && msg.arg1 == BluetoothState.STATE_CONNECTING) {
                        isConnecting = true;
                    } else if (isConnecting) {
                        if (msg.arg1 != BluetoothState.STATE_CONNECTED
                                && bluetoothConnectionListener != null) {
                            bluetoothConnectionListener.onDeviceConnectionFailed();
                        }
                        isConnecting = false;
                    }
                    break;
            }
        }
    };

    public void stopAutoConnect() {
        isAutoConnectionEnabled = false;
    }

    public void connect(Intent data) {
        String address = data.getExtras().getString(BluetoothState.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        btChatService.connect(device);
    }

    public void connect(String address) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        btChatService.connect(device);
    }

    public void disconnect() {
        if (btChatService != null) {
            isServiceRunning = false;
            btChatService.stop();
            if (btChatService.getState() == BluetoothState.STATE_NONE) {
                isServiceRunning = true;
                btChatService.start(isAndroid);
            }
        }
    }

    public void setBluetoothStateListener (BluetoothStateListener listener) {
        bluetoothStateListener = listener;
    }

    public void setOnDataReceivedListener (OnDataReceivedListener listener) {
        dataReceivedListener = listener;
    }

    public void setBluetoothConnectionListener (BluetoothConnectionListener listener) {
        bluetoothConnectionListener = listener;
    }

    public void setAutoConnectionListener(AutoConnectionListener listener) {
        autoConnectionListener = listener;
    }

    public void enable() {
        mBluetoothAdapter.enable();
    }

    public void send(byte[] data, boolean CRLF) {
        if(btChatService.getState() == BluetoothState.STATE_CONNECTED) {
            if(CRLF) {
                byte[] data2 = new byte[data.length + 2];
                for(int i = 0 ; i < data.length ; i++)
                    data2[i] = data[i];
                data2[data2.length - 2] = 0x0A;
                data2[data2.length - 1] = 0x0D;
                btChatService.write(data2);
            } else {
                btChatService.write(data);
            }
        }
    }

    public void send(String data, boolean CRLF) {
        if (btChatService.getState() == BluetoothState.STATE_CONNECTED) {
            if(CRLF)
                data += "\r\n";
            btChatService.write(data.getBytes());
        }
    }

    public String getConnectedDeviceName() {
        return mDeviceName;
    }

    public String getConnectedDeviceAddress() {
        return mDeviceAddress;
    }

    public String[] getPairedDeviceName() {
        int c = 0;
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        String[] name_list = new String[devices.size()];
        for(BluetoothDevice device : devices) {
            name_list[c] = device.getName();
            c++;
        }
        return name_list;
    }

    public String[] getPairedDeviceAddress() {
        int c = 0;
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        String[] address_list = new String[devices.size()];
        for(BluetoothDevice device : devices) {
            address_list[c] = device.getAddress();
            c++;
        }
        return address_list;
    }


    public void autoConnect(String keywordName) {
        if (!isAutoConnectionEnabled) {
            keyword = keywordName;
            isAutoConnectionEnabled = true;
            isAutoConnecting = true;
            if (autoConnectionListener != null) {
                autoConnectionListener.onAutoConnectionStarted();
            }

            BluetoothDevice tempBluetoothDeviceToConnect = null;
            for (BluetoothDevice bluetoothDevice : mBluetoothAdapter.getBondedDevices()) {
                if (bluetoothDevice.getAddress().equals(keywordName)) {
                    tempBluetoothDeviceToConnect = bluetoothDevice;
                    break;
                }
            }

            final BluetoothDevice deviceToConnect = tempBluetoothDeviceToConnect;

            bcl = new BluetoothConnectionListener() {
                public void onDeviceConnected(String name, String address) {
                    bcl = null;
                    isAutoConnecting = false;
                }

                public void onDeviceDisconnected() { }
                public void onDeviceConnectionFailed() {
                    if (isServiceRunning) {
                        if (isAutoConnectionEnabled) {
                            if (deviceToConnect != null) {
                                connect(deviceToConnect.getAddress());
                            }

                            if (autoConnectionListener != null) {
                                autoConnectionListener.onNewConnection(deviceToConnect);
                            }
                        } else {
                            bcl = null;
                            isAutoConnecting = false;
                        }
                    }
                }
            };

            setBluetoothConnectionListener(bcl);
            if (autoConnectionListener != null) {
                autoConnectionListener.onNewConnection(deviceToConnect);
            }
            if (deviceToConnect != null) {
                connect(deviceToConnect.getAddress());
            }
        }
    }
}