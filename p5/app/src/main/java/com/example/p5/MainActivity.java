package com.example.p5;

/* --------------- from eChook --------------*/
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
/* -----------------------------------------*/

import android.Manifest;
import android.provider.Settings;
import android.util.Log;

import static android.R.id.message;

import java.util.ArrayList;
import java.util.UUID;
import java.nio.charset.Charset;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;

import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;

import android.widget.Button;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.Chronometer;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothClass;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "MainActivity";

    /* ------------------- BLUETOOTH ----------------- */
    BluetoothAdapter mBluetoothAdapter;
    Button btnEnableDisable_Discoverable;
    Button btnStartConnection;
    Button btnSend;
    /* -------- SENDING MESSAGES BACK AND FORTH BETWEEN TWO PHONES -------- */
    TextView incomingMessages; /* go to onCreate method */
    StringBuilder messages; /* appending the messages and posting them to the textview in activity_main */


    /* --------------- FIREBASE ------------ */
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private EditText mEmail, mPassword;
    private Button btnSignIn, btnSignOut;
    private Button mAddToDB;
    private EditText mNewVar;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference myRef;


    /* ------------ GPS ------------ */
    private Button button_gps;
    private TextView gps_text; /* Square for viewing coordinates */
    private LocationManager locationManager;
    private LocationListener listener;


    /* -------------------------- BLUETOOTH ----------------------- */
    private static final UUID MY_UUID_INSECURE =
            /*UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");*/  /* for connections with other phones */
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");      /* for arduino connection with HC-06 Module */

    BluetoothDevice mBTDevice;
    EditText etSend;
    BluetoothConnectionService mBluetoothConnection;
    public ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();
    public DeviceListAdapter mDeviceListAdapter;
    ListView lvNewDevices;


    /* ------------------- BLUETOOTH - Create a BroadcastReceiver for ACTION_FOUND ------------------ */
    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (action.equals(mBluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, mBluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING ON");
                        break;
                }
            }
        }
    };


    /* ------- BLUETOOTH - Broadcast Receiver for changes made to bluetooth states - Discoverability mode on/off or expire. ------ */
    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                switch (mode) {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE: /* Device is in Discoverable Mode */
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE: /* Device not in discoverable mode */
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting....");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        break;
                }
            }
        }
    };

    /* ------- BLUETOOTH - Broadcast Receiver for listing devices that are not yet paired -Executed by btnDiscover() method ------*/
    private BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mBTDevices.add(device);
                Log.d(TAG, "onReceive: " + device.getName() + ": " + device.getAddress());
                mDeviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, mBTDevices);
                lvNewDevices.setAdapter(mDeviceListAdapter);
            }
        }
    };


    /* ------- BLUETOOTH - Broadcast Receiver that detects bond state changes (Pairing status changes) -------- */
    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED) {  /* case1: bonded already */
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDED.");
                    //inside BroadcastReceiver4
                    mBTDevice = mDevice; /* device that it is paired with */
                }
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING) {  /* case2: creating a bond */
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDING.");
                }
                if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {  /* case3: breaking a bond */
                    Log.d(TAG, "BroadcastReceiver: BOND_NONE.");
                }
            }
        }
    };


    /* ---------------- BLUETOOTH - ON DESTROY --------------- */
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called.");
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver1);
        unregisterReceiver(mBroadcastReceiver2);
        unregisterReceiver(mBroadcastReceiver3);
        unregisterReceiver(mBroadcastReceiver4);
        //mBluetoothAdapter.cancelDiscovery();
    }


    /* -------------------------------------------------------------------- onCreate METHOD -------------------------------------------------------------------------- */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* ----------------- BLUETOOTH --------------- */
        //Button btnONOFF = (Button) findViewById(R.id.btnONOFF);
        btnEnableDisable_Discoverable = (Button) findViewById(R.id.btnEnableDisable_Discoverable);
        lvNewDevices = (ListView) findViewById(R.id.lvNewDevices);
        mBTDevices = new ArrayList<>();
        btnStartConnection = (Button) findViewById(R.id.btnStartConnection);
        btnSend = (Button) findViewById(R.id.btnSend);
        etSend = (EditText) findViewById(R.id.editText);


        /* --------------- GPS -------------- */
        button_gps = (Button) findViewById(R.id.button_gps);
        gps_text = (TextView) findViewById(R.id.gps_text);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);


        /* --------------- FIREBASE --------------- */
        mEmail = (EditText) findViewById(R.id.email);
        mPassword = (EditText) findViewById(R.id.password);
        btnSignIn = (Button) findViewById(R.id.email_sign_in_button);
        btnSignOut = (Button) findViewById(R.id.email_sign_out_button);
        mAuth = FirebaseAuth.getInstance();
        mAddToDB = (Button) findViewById(R.id.btnAddNewVar);
        mNewVar = (EditText) findViewById(R.id.add_new_var);
        /* -- Unless you are signed in, this will not be useable -- */
        mAuth = FirebaseAuth.getInstance();
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        myRef = mFirebaseDatabase.getReference();


        /* ------------ BLUETOOTH RELATED - received information ------------ */
        incomingMessages = (TextView) findViewById(R.id.incomingMessage);
        messages = new StringBuilder();
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter("incomingMessage"));

        /* ------------- BLUETOOTH - Broadcasts when bond state changes (ie:pairing) ------------ */
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver4, filter);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        lvNewDevices.setOnItemClickListener(MainActivity.this);




        /* ------------ BLUETOOTH - ON/OFF BUTTON EVENT HANDLER - not necessary -------------- */
        /*btnONOFF.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: enabling/disabling bluetooth.");
                enableDisableBT();
            }
        }); */

        /* ------------ BLUETOOTH - START CONNECTION BUTTON ------------- */
        btnStartConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startConnection();
            }
        });

        /* ---------------------- BLUETOOTH - SEND BUTTON ---------------------------- */
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                byte[] bytes = etSend.getText().toString().getBytes(Charset.defaultCharset());
                mBluetoothConnection.write(bytes);
                etSend.setText(" ");
                toastMessage("Sending");
            }
        });



        /* ---------------------- GPS - changes to buttons and textviews ---------------------*/
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) { /* whenever location is updated */
                gps_text.append("\n " + location.getLongitude() + " " + location.getLatitude());
            }
            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {
            }

            @Override
            public void onProviderEnabled(String s) {
            }

            @Override
            public void onProviderDisabled(String s) { /* checks if gps is turned off */
                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(i);
            }
        };
        configureButton();



        /* ----------------- FIREBASE - Authentication listener ------------- */
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) { // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                    toastMessage("Successfully signed in with: " + user.getEmail());
                } else { // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    toastMessage("Successfully signed out");
                }
            }
        };

        /* ---------------------- FIREBASE - sign in button --------------------- */
        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = mEmail.getText().toString();
                String pass = mPassword.getText().toString();
                if (!email.equals("") && !pass.equals("")) {
                    mAuth.signInWithEmailAndPassword(email, pass);
                } else {
                    toastMessage("You didn't fill in all the fields");
                }
            }
        });

        /* ---------------------- FIREBASE - sign out button --------------------- */
        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mAuth.signOut();
                toastMessage("Signing Out");
            }
        });

        /* ---------------- FIREBASE - Button to go to add to database base - not necessary ---------------- */
        /*btnAddToDatabase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AddToDatabase.class);
                startActivity(intent);
            }
        });*/

        /* ----------------------- FIREBASE ------------------------------ */
        // Read from the database
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                /* This method is called once with the initial value and again whenever data at this location is updated */
                Object value = dataSnapshot.getValue();
                Log.d(TAG, "Value is: " + value);
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

        /* ----------------------- FIREBASE - button to add to databse ------------------------------ */
        mAddToDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: Attempting to add object to database.");
                String newVar = mNewVar.getText().toString();
                if(!newVar.equals("")){
                    FirebaseUser user = mAuth.getCurrentUser();
                    String userID = user.getUid();
                    //myRef.child(userID).child("Variable").child(newVar).setValue("true");
                    myRef.child(userID).child("Variable").child("Power").child(newVar).setValue("true");
                    myRef.child(userID).child("Variable").child(newVar).child("Speed").setValue("true");
                    toastMessage("Adding " + newVar + " to database...");
                    //reset the text
                    mNewVar.setText("");
                }
            }
        });

    }/* ------------------------------------------------------ END OF onCreate METHOD -------------------------------------------------------------------------- */


    /* ---------------------------------- GPS - Permissions ---------------------------------------------*/
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 10:
                configureButton();
                break;
            default:
                break;
        }
    }

    /* ------------------------------------ GPS - CONFIGURE BUTTON ---------------------------------------- */
    void configureButton() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { /* first check for permissions */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.INTERNET}
                        , 10);
            }
            return;
        }
        /* this code won't execute IF permissions are not allowed, because in the line above there is return statement */
        button_gps.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                /* noinspection MissingPermission */
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                locationManager.requestLocationUpdates("gps", 5000, 0, listener);
                /* requestLocationUpdates args - provider, minTime--refresh interval in ms,
                    minDistance--will update when the location has changed by minDistance meters, locationListener */
                toastMessage("Getting Location");
            }
        });
    }


    /* --------------------BLUETOOTH - Broadcast Receiver - will pass the strings to textview --------------------- */
    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("theMessage");
            messages.append(text + "\n");
            incomingMessages.setText(messages);
        }
    };


    /* ----------- BLUETOOTH - create method for starting connection
     the connection will fail and app will crash if you haven't paired first ---- */
    public void startConnection() {
        startBTConnection(mBTDevice, MY_UUID_INSECURE);
        toastMessage("Starting Connection");
    }

    /* ------------------ BLUETOOTH - starting chat service method --------------------- */
    public void startBTConnection(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection.");
        mBluetoothConnection.startClient(device, uuid);
    }


    /* ------------------------- BLUETOOTH - ENABLE/DISABLE -------------------------- */
    public void enableDisableBT() {
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "enableDisableBT: Does not have BT capabilities.");
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "enableDisableBT: enabling BT.");
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBTIntent);
            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }
        if (mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "enableDisableBT: disabling BT.");
            mBluetoothAdapter.disable();
            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }
    }


    /* ------------------------------ BLUETOOTH - DISCOVERABLE BUTTON EVENT HANDLER ----------------------------- */
    public void button_discoverable(View view) {
        Log.d(TAG, "btnEnableDisable_Discoverable: Making device discoverable for 300 seconds.");
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); /* 300 seconds */
        startActivity(discoverableIntent);
        IntentFilter intentFilter = new IntentFilter(mBluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver2, intentFilter);
        toastMessage("Discovery Enabled");
    }


    /* -------------------------- BLUETOOTH - Button to discover devices --------------------------- */
    public void btnDiscover(View view) {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.");
        if (mBluetoothAdapter.isDiscovering()) { /* is it already looking for devices */
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "btnDiscover: Canceling discovery.");
            checkBTPermissions();  /* check BT permissions in manifest */
            mBluetoothAdapter.startDiscovery();
            Log.d(TAG, "btnDiscover: start discovery first if.");
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
        if (!mBluetoothAdapter.isDiscovering()) {
            checkBTPermissions();   /* check BT permissions in manifest */
            mBluetoothAdapter.startDiscovery();
            Log.d(TAG, "btnDiscover: start discovery second if.");
            toastMessage("Discovering");
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
    }


    /* --------------- BLUETOOTH - This method is required for all devices running API23+
    Android must programmatically check the permissions for bluetooth. Putting the proper permissions in the manifest is not enough.
    NOTE: This will only execute on versions > LOLLIPOP because it is not needed otherwise -------- */
    private void checkBTPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
            }
        } else {
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }


    /* ----------------------------------------- BLUETOOTH ------------------------------------------------ */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        mBluetoothAdapter.cancelDiscovery(); /* first cancel discovery because its very memory intensive */
        Log.d(TAG, "onItemClick: You Clicked on a device.");
        String deviceName = mBTDevices.get(i).getName();
        String deviceAddress = mBTDevices.get(i).getAddress();
        Log.d(TAG, "onItemClick: deviceName = " + deviceName);
        Log.d(TAG, "onItemClick: deviceAddress = " + deviceAddress);

        /* create the bond */
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) { /* NOTE: Requires API 17+? I think this is JellyBean */
            Log.d(TAG, "Trying to pair with " + deviceName);
            mBTDevices.get(i).createBond();
            /* create a bond and start a connection service */
            mBTDevice = mBTDevices.get(i);
            mBluetoothConnection = new BluetoothConnectionService(MainActivity.this);
            /* bluetooth connection starts and the accept thread will be waiting for a connection until you click on
            the connect button (start connection) */
        }
    }


    /* ---------------- FIREBASE -------------- */
    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    /* ---------------- FIREBASE -------------- */
    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    /* -------------------- TOAST FUNCTIONS - show short android messages --------------------- */
    private void toastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}