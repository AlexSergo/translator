package com.res.bluetooth_translator;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends Activity implements ServiceConnection {

    private Button btn;

    public DataTransfer dataTransfer = new DataTransfer();

    private final int FPS = 100;

    private static final int REQUEST_PERMISSION_CODE = 12345;

    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
            Manifest.permission.BLUETOOTH, // Bluetooth connected products
            Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
            Manifest.permission.BLUETOOTH_CONNECT
    };

    private final List<String> missingPermission = new ArrayList<>();

    public String uuidstr = "00001101-0000-1000-8000-00805f9b34fb";
    private final static int RC_INTGPS = 9876;
    public boolean running;
    public float textSize;
    private Intent jsessionserviceintent;
    public JSessionService jSessionService;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        checkAndRequestPermissions();
        super.onCreate(savedInstanceState);
        havePermission();
        StartBt();

        setContentView(R.layout.activity_main);

        if (!dataTransfer.isAlive()) {
            dataTransfer.start();
        }

        btn = findViewById(R.id.button1);

        btn.setOnClickListener(view -> onViewClick(btn));
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
        } else {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[0]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService();
    }

    /**
     * We are all done with the jSessionService object.
     */
    private void unbindService() {
        // tell jSessionService to stop updating our screen
        if (jSessionService != null) {
            jSessionService.closingScreen();
            jSessionService = null;

            // tell android we just got rid of our jSessionService pointer
            // ...but service keeps running with whatever connections it has
            unbindService(this);
        }
    }

    /**
     * User clicked Start - start the service listening for connections
     */
    private void StartBt() {
        if (!running) {
            running = true;
            startService(jsessionserviceintent);

            if (!bindService(jsessionserviceintent, this, 0)) {
                throw new RuntimeException("failed to bind to service");
            }
        }
    }


    /**
     * User clicked Stop - abort any connections and stop listening and stop service
     */
    private void StopBt() {
        if (jSessionService != null) {
            jSessionService.stopListening();
            unbindService();
        }
        stopService(jsessionserviceintent);
        running = false;
    }

    public void hasAgreed() {
        havePermission();
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        jSessionService = ((JSessionService.MyBinder) service).getService();
        jSessionService.openingScreen(this);

        UUID uuid = UUID.fromString(uuidstr);

        jSessionService.startListening(uuid);
    }

    // the service crashed (should not happen)
    @Override
    public void onServiceDisconnected(ComponentName name) {
        throw new IllegalStateException("service crashed");
    }

    // all licenses agreed to and all permissions granted
    // if service is started, bind to it, otherwise we are ready for menu clicks
    private void havePermission() {
        jsessionserviceintent = new Intent(this, JSessionService.class);

        // https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (JSessionService.class.getName().equals(service.service.getClassName())) {
                StartBt();
                return;
            }
        }
        StopBt();
    }

    private void onViewClick(View view) {
        if (view == btn) {
            if (null != jSessionService) {
                ArrayList<Entity> myList = new ArrayList();
                myList.add(new Entity(55.53433, 37.23232, 323.12, 30.0, 0, -15, true));
                myList.add(new Entity(55.01433, 37.20232, 123.12, 30.0));
                jSessionService.LocationReceived(myList);
            }
        }
    }

    // Permission granting
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == RC_INTGPS) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                finish();
            } else {
                havePermission();
            }
        }
    }

    private class DataTransfer extends Thread {
        @Override
        public void run() {
            while (true) {
                ArrayList<Entity> myList = new ArrayList();
                myList.add(new Entity(55.53433, 45.23232, 323.12, 30.0, 0, -1.2, true));
                myList.add(new Entity(52.01433, 45.23232, 123.12, 30.0));
                if (jSessionService != null) {
                    jSessionService.LocationReceived(myList);
                }
                try {
                    Thread.sleep(FPS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}