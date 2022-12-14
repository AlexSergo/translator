package com.res.bluetooth_translator;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class JSessionService extends Service {
    public final static String TAG = "GPSBlue";

    private final static String APP_NAME = "GPSBlue";

    public BluetoothServer bluetoothServer;
    private boolean gpsStarted;
    private boolean listening;
    private MainActivity gpsBlue;

    private final MyBinder myBinder = new MyBinder();
    public final Object connectionLock = new Object();
    private PowerManager.WakeLock partialWakeLock;
    private SimpleDateFormat sdfhms;
    private SimpleDateFormat sdfdmy;
    public String latestStatusText;
    private String pendingAlertMessage;
    private String pendingAlertTitle;

    /***************************\
     *  Service-context calls  *
     \***************************/

    // service just brought into memory
    @Override
    public void onCreate() {
        Log.d(TAG, "JSessionService created");

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                APP_NAME + ":bluetooth connections");

        sdfhms = new SimpleDateFormat("HHmmss.SSS", Locale.US);
        sdfhms.setTimeZone(TimeZone.getTimeZone("UTC"));

        sdfdmy = new SimpleDateFormat("ddMMyy", Locale.US);
        sdfdmy.setTimeZone(TimeZone.getTimeZone("UTC"));

        bluetoothServer = new BluetoothServer(this);
        //       internalGps = new InternalGps (this);
    }

    // service being taken out of memory
    @Override
    public void onDestroy() {
        Log.d(TAG, "JSessionService destroyed");
        bluetoothServer.shutdown();
        if (gpsStarted) {
            partialWakeLock.release();
            gpsStarted = false;
        }
        bluetoothServer = null;
        partialWakeLock = null;
    }

    // GPSBlue app was just started and called startService()
    // We stay in memory even after the app is destroyed
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "JSessionService started");
        return Service.START_STICKY;
    }

    // GPSBlue app was just started and called bindService()
    // Return a pointer to this object for GPSBlue to use to call us
    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    public class MyBinder extends Binder {
        public JSessionService getService() {
            return JSessionService.this;
        }
    }

    /****************************\
     *  Activity-context calls  *
     \
     * @param gpsb****************************/

    public void openingScreen(MainActivity gpsb) {
        if (gpsBlue != null) {
            throw new IllegalStateException("service already bound");
        }
        gpsBlue = gpsb;

        if (pendingAlertTitle != null) {
            gpsb.fatalError(pendingAlertTitle, pendingAlertMessage);
        }
    }

    public void closingScreen() {
        gpsBlue = null;
    }

    public void startListening(UUID uuid) {
        if (!listening) {
            Log.d(TAG, "JSessionService start listening");
            listening = true;
            bluetoothServer.startup(uuid);
        }
    }

    public void stopListening() {
        if (listening) {
            listening = false;
            bluetoothServer.shutdown();
            Log.d(TAG, "JSessionService stop listening");
        }
    }

    /**
     * Start up the app, display error message then stop the service.
     */
    public void fatalError(final String tit, final String msg) {
        MainActivity gpsb = gpsBlue;
        if (gpsb != null) {
            gpsb.fatalError(tit, msg);
        } else {
            pendingAlertMessage = msg;
            pendingAlertTitle = tit;
            startActivity(getPackageManager().getLaunchIntentForPackage(getPackageName()));
        }
    }

    /**
     * Current number of connections has changed.
     * If zero, let CPU and screen go to sleep.
     * If non-zero, keep CPU on, let screen shut off.
     * Update in-app count and notification count.
     * Called with connectionLock locked.
     */
    @SuppressLint("WakelockTimeout")
    public void updateConnectionCount(UUID sppUUID, int count) {
        if (count > 0) {
            if (!gpsStarted) {
                partialWakeLock.acquire();
                gpsStarted = true;
            }
        } else {
            if (gpsStarted) {
                partialWakeLock.release();
                gpsStarted = false;
            }
        }

        latestStatusText = "uuid: " + sppUUID.toString().toUpperCase() +
                "\nconnections: " + count;
    }

    public void LocationReceived(double lat, double lon, double alt, double asim) {
        long start = System.currentTimeMillis();
        Date date = new Date(start);

        String strhms = sdfhms.format(date); // hhmmss.sss
        String strdmy = sdfdmy.format(date); // ddmmyy

        // http://www.gpsinformation.org/dale/nmea.htm#GGA
        StringBuilder sb = new StringBuilder();
        sb.append("$GPGGA,");
        sb.append(strhms);
        sb.append(',');
        LatLonDegMin(sb, lat, 'N', 'S');
        sb.append(',');
        LatLonDegMin(sb, lon, 'E', 'W');
        sb.append(String.format(Locale.US, ",1,%d,0.9,%.1f,M,,,,", 0, alt));
        NMEAChecksum(sb);

        // http://www.gpsinformation.org/dale/nmea.htm#RMC
        sb.append("$GPRMC,");
        sb.append(strhms);
        sb.append(",A,");
        LatLonDegMin(sb, lat, 'N', 'S');
        sb.append(',');
        LatLonDegMin(sb, lon, 'E', 'W');
        sb.append(String.format(Locale.US, ",%.1f,%.1f,", 00.0, asim)); // loc.getSpeed () * KtPerMPS, loc.getBearing ()));
        sb.append(strdmy);
        sb.append(",,");
        NMEAChecksum(sb);

        TransmitString(sb.toString());
    }


    // convert a number of degrees to ddmm.1000s string
    private static void LatLonDegMin(StringBuilder sb, double ll, char pos, char neg) {
        int min1000 = (int) Math.round(ll * 60000.0);
        if (min1000 < 0) {
            min1000 = -min1000;
            pos = neg;
        }
        int deg = min1000 / 60000;
        min1000 %= 60000;
        int min = min1000 / 1000;
        min1000 %= 1000;
        sb.append(String.format(Locale.US, "%d%02d.%03d", deg, min, min1000));
        sb.append(',');
        sb.append(pos);
    }

    // append NMEA checksum and CRLF to a string
    private static void NMEAChecksum(StringBuilder sb) {
        int len = sb.length();
        int xor = 0;
        for (int i = len; --i > 0; ) {
            char c = sb.charAt(i);
            if (c == '$') break;
            xor ^= c;
        }
        sb.append(String.format(Locale.US, "*%02X\r\n", xor));
    }

    // transmit to all connected bluetooth EFB apps
    // called in InternalGps.GPSRcvrThread.
    private void TransmitString(String st) {
        byte[] bytes = st.getBytes();
        BluetoothServer bs = bluetoothServer;
        if (bs != null) bs.write(bytes, 0, bytes.length);
    }
}
