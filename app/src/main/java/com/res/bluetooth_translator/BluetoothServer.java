package com.res.bluetooth_translator;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.UUID;

public class BluetoothServer {
    private final static ReceiveThread[] nullrtarray = new ReceiveThread[0];

    private AcceptThread acceptThread;
    private HashSet<ReceiveThread> rtlist;
    private final JSessionService jSessionService;
    private ReceiveThread[] rtarray;
    private UUID sppUUID;

    public BluetoothServer(JSessionService jss) {
        jSessionService = jss;
        rtarray = nullrtarray;
    }

    /**
     * Start listening on the given bluetooth socket UUID.
     * Called in app UI thread.
     */
    public void startup(UUID uuid) {
        if ((acceptThread == null) || !sppUUID.equals(uuid)) {
            shutdown();
            sppUUID = uuid;
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    /**
     * Stop listening for new inbound connections.
     * Terminate any existing connections.
     * Called in app UI thread.
     */
    public void shutdown() {
        if (acceptThread != null) {
            acceptThread.finish();
            acceptThread = null;
        }
        while (true) {
            ReceiveThread rt;
            synchronized (jSessionService.connectionLock) {
                if (rtarray.length == 0) break;
                rt = rtarray[0];
            }
            rt.finish();
        }
    }

    /**
     * Send message to all connections.
     * Called in InternalGps.GPSRcvrThread.
     */
    public void write(byte[] buf, int ofs, int len) {
        ReceiveThread[] rts = rtarray;
        for (ReceiveThread rt : rts) {
            if (!rt.senderr) {
                try {
                    rt.os.write(buf, ofs, len);
                } catch (IOException ioe) {
                    rt.senderr = true;
                }
            }
        }
    }

    /**
     * Thread what listens for incoming bluetooth connections.
     * Spawns receiver thread for each found.
     */
    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
        private boolean finished;

        // stop listening and get thread to exit
        public void finish() {
            finished = true;
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            try {
                join();
            } catch (InterruptedException ignored) {
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            try {
                BluetoothManager bm = (BluetoothManager) jSessionService.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter ba = bm.getAdapter();
                if (ba == null) throw new Exception("no bluetooth on this device");
                serverSocket = ba.listenUsingInsecureRfcommWithServiceRecord("GPSBlue", sppUUID);
                synchronized (jSessionService.connectionLock) {
                    rtlist = new HashSet<>();
                    rtarray = nullrtarray;
                    jSessionService.updateConnectionCount(sppUUID, 0);
                }
                //noinspection InfiniteLoopStatement
                while (true) {
                    ReceiveThread rt = new ReceiveThread();
                    rt.bs = serverSocket.accept();
                    rt.start();
                }
            } catch (Exception e) {
                try {
                    serverSocket.close();
                } catch (Exception ignored) {
                }
                serverSocket = null;
                if (!finished) {
//                    jSessionService.fatalError("Bluetooth Error",
//                            "try starting bluetooth\nor try different UUID\n\n" + e.getMessage());
                }
            }
        }
    }

    // we have a new inbound connection
    // this thread runs as long as that device is connected
    // we don't really receive anything meaningful from the device,
    // ...but we use this thread to sense when it disconnects
    private class ReceiveThread extends Thread {
        public BluetoothSocket bs;
        public boolean senderr;
        public OutputStream os;

        // drop connection and get thread to exit
        public void finish() {
            try {
                bs.close();
            } catch (IOException ignored) {
            }
            try {
                join();
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void run() {
            try {
                os = bs.getOutputStream();

                // update list of who to send NMEA messages to
                // update the total number of inbound connections
                // this also makes sure the GPS is turned on and locks the CPU on
                synchronized (jSessionService.connectionLock) {
                    rtlist.add(this);
                    rtarray = rtlist.toArray(nullrtarray);
                    jSessionService.updateConnectionCount(sppUUID, rtarray.length);
                }

                // read from the connection simply to detect when it disconnects
                byte[] buf = new byte[4096];
                InputStream is = bs.getInputStream();
                while (!senderr) {
                    int rc = is.read(buf);
                    if (rc <= 0) break;
                }
            } catch (IOException ignored) {
            } finally {

                // close the socket and tell service one less connection being handled
                // if no connections, turn the GPS receiver off and unlock CPU
                try {
                    bs.close();
                } catch (IOException ignored) {
                }
                synchronized (jSessionService.connectionLock) {
                    rtlist.remove(this);
                    rtarray = rtlist.toArray(nullrtarray);
                    jSessionService.updateConnectionCount(sppUUID, rtarray.length);
                }
            }
        }
    }
}
