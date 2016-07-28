/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

@SuppressLint("NewApi")
public class BluetoothService {
    // Debugging
    private static final String TAG = "Bluetooth Service";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "Bluetooth Secure";

    // Unique UUID for this application
    private static final UUID UUID_ANDROID_DEVICE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID UUID_OTHER_DEVICE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private boolean isAndroid = BluetoothState.DEVICE_ANDROID;

    // Constructor. Prepares a new BluetoothChat session
    // context : The UI Activity Context
    // handler : A Handler to send messages back to the UI Activity
    public BluetoothService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = BluetoothState.STATE_NONE;
        mHandler = handler;
    }


    // Set the current state of the chat connection
    // state : An integer defining the current connection state
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothState.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    // Return the current connection state.
    public synchronized int getState() {
        return mState;
    }

    // Start the chat service. Specifically start AcceptThread to begin a
    // session in listening (server) mode. Called by the Activity onResume()
    public synchronized void start(boolean isAndroid) {
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(BluetoothState.STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(isAndroid);
            mSecureAcceptThread.start();
            this.isAndroid = isAndroid;
        }
    }

    // Start the ConnectThread to initiate a connection to a remote device
    // device : The BluetoothDevice to connect
    // secure : Socket Security type - Secure (true) , Insecure (false)
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread attempting to make a connection
        if (mState == BluetoothState.STATE_CONNECTING && mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(BluetoothState.STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(BluetoothState.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothState.DEVICE_NAME, device.getName());
        bundle.putString(BluetoothState.DEVICE_ADDRESS, device.getAddress());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(BluetoothState.STATE_CONNECTED);
    }

    // Stop all threads
    public synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        setState(BluetoothState.STATE_NONE);

        mHandler.removeCallbacksAndMessages(null);
    }

    // Write to the ConnectedThread in an unsynchronized manner
    // out : The bytes to write
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread connectedThread;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != BluetoothState.STATE_CONNECTED) {
                return;
            }
            connectedThread = mConnectedThread;
        }
        // Perform the write unsynchronized
        connectedThread.write(out);
    }

    // Indicate that the connection attempt failed and notify the UI Activity
    private void connectionFailed() {
        // Start the service over to restart listening mode
        start(isAndroid);
    }

    // Indicate that the connection was lost and notify the UI Activity
    private void connectionLost() {
        // Start the service over to restart listening mode
        start(isAndroid);
    }

    // This thread runs while listening for incoming connections. It behaves
    // like a server-side client. It runs until a connection is accepted
    // (or until cancelled)
    private class AcceptThread extends Thread {
        // The local server socket
        private BluetoothServerSocket btServerSocket;

        public AcceptThread(boolean isAndroid) {
            try {
                btServerSocket = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                        (isAndroid) ? UUID_ANDROID_DEVICE : UUID_OTHER_DEVICE);
            } catch (IOException e) {
                // TODO
            }
        }

        public void run() {
            BluetoothSocket btSocket = null;

            // Listen to the server socket if we're not connected
            while (!Thread.currentThread().isInterrupted() || mState != BluetoothState.STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    if (btServerSocket != null) {
                        btSocket = btServerSocket.accept();
                    }
                } catch (Exception e) {
                    break;
                }

                // If a connection was accepted
                if (btSocket != null) {
                    synchronized (BluetoothService.this) {
                        switch (mState) {
                        case BluetoothState.STATE_LISTEN:
                        case BluetoothState.STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                            connected(btSocket, btSocket.getRemoteDevice());
                            break;
                        case BluetoothState.STATE_NONE:
                        case BluetoothState.STATE_CONNECTED:
                            // Either not ready or already connected. Terminate new socket.
                            try {
                                btSocket.close();
                            } catch (IOException e) {
                                // TODO
                            }
                            break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            if (btServerSocket != null) {
                try {
                    btServerSocket.close();
                    btServerSocket = null;
                } catch (IOException e) {
                    // TODO
                }
            }

            Thread.currentThread().interrupt();
        }
    }


    // This thread runs while attempting to make an outgoing connection
    // with a device. It runs straight through
    // the connection either succeeds or fails
    private class ConnectThread extends Thread {
        private BluetoothSocket btSocket;
        private final BluetoothDevice btDevice;

        public ConnectThread(BluetoothDevice device) {
            btDevice = device;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                btSocket = device.createRfcommSocketToServiceRecord (
                        (isAndroid) ? UUID_ANDROID_DEVICE : UUID_OTHER_DEVICE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                btSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    // TODO
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(btSocket, btDevice);
        }

        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                // TODO
            }
            Thread.currentThread().interrupt();
        }
    }

    // This thread runs during a connection with a remote device.
    // It handles all incoming and outgoing transmissions.
    private class ConnectedThread extends Thread {
        private final BluetoothSocket btSocket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            btSocket = socket;

            // Get the BluetoothSocket input and output streams
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                // TODO
            }
        }

        public void run() {
            byte[] buffer;
            ArrayList<Integer> arr_byte = new ArrayList<Integer>();

            // Keep listening to the InputStream while connected
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int data = inputStream.read();

                    if (data == 0x0D) {
                        buffer = new byte[arr_byte.size()];
                        for(int i = 0 ; i < arr_byte.size() ; i++) {
                            buffer[i] = arr_byte.get(i).byteValue();
                        }
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(BluetoothState.MESSAGE_READ
                                , buffer.length, -1, buffer).sendToTarget();
                        arr_byte = new ArrayList<Integer>();
                    } else if (data != 0x0A) {
                        arr_byte.add(data);
                    }
                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothService.this.start(isAndroid);
                    break;
                }
            }
        }

        // Write to the connected OutStream.
        // @param buffer  The bytes to write
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothState.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                // TODO
            }
        }

        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
                // TODO
            }
            Thread.currentThread().interrupt();
        }
    }
}