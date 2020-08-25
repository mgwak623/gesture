package erlab.cs.ucla.edu.gesture;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static erlab.cs.ucla.edu.gesture.Constants.sdf_date;
import static erlab.cs.ucla.edu.gesture.Constants.sdf_time;
import static erlab.cs.ucla.edu.gesture.Constants.timeZone;

public class GarminActivity extends AppCompatActivity {

    public static final String IQDEVICE = "IQDevice";
//    public static final String MY_APP = "a3421feed289106a538cb9547ab12095";  // comm app
    public static final String MY_APP = "82f68cae6837490dbcc6bc0c9ffc97ad";  // motionSensor app
    private static final String TAG = GarminActivity.class.getSimpleName();
    public static final String FIREBASE_DOC = "gesture";
    private static final int RC_SIGN_IN = 123;

    private FirebaseAuth mAuth;

    private ConnectIQ mConnectIQ;
    private TextView mTextView;
    private TextView mDataView;
    private Button startMeasure;
    private Button finishMeasure;
    private Button mOpenAppButton;
    private EditText mLabel;
    private boolean mSdkReady = false;
    private IQDevice mDevice;
    private IQApp mMyApp;
    private boolean mAppIsOpen;

    // Firebase
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    List<String> accelX = new CopyOnWriteArrayList<String>();
    List<String> accelY = new CopyOnWriteArrayList<String>();
    List<String> accelZ = new CopyOnWriteArrayList<String>();

    private ConnectIQ.ConnectIQListener mListener = new ConnectIQ.ConnectIQListener() {

        @SuppressLint("SetTextI18n")
        @Override
        public void onInitializeError(ConnectIQ.IQSdkErrorStatus errStatus) {
            if( null != mTextView )
                mTextView.setText(R.string.initialization_error + errStatus.name());
            mSdkReady = false;
        }

        @Override
        public void onSdkReady() {
            loadDevices();
            mSdkReady = true;
        }

        @Override
        public void onSdkShutDown() {
            mSdkReady = false;
        }

    };

    private ConnectIQ.IQOpenApplicationListener mOpenAppListener = new ConnectIQ.IQOpenApplicationListener() {
        @Override
        public void onOpenApplicationResponse(IQDevice device, IQApp app, ConnectIQ.IQOpenApplicationStatus status) {
            Toast.makeText(getApplicationContext(), "App Status: " + status.name(), Toast.LENGTH_SHORT).show();

            if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
                mAppIsOpen = true;
                mOpenAppButton.setText(R.string.open_app_already_open);
            } else {
                mAppIsOpen = false;
                mOpenAppButton.setText(R.string.open_app_open);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_garmin);

        mAuth = FirebaseAuth.getInstance();


        // Here we are specifying that we want to use a WIRELESS bluetooth connection.
        // We could have just called getInstance() which would by default create a version
        // for WIRELESS, unless we had previously gotten an instance passing TETHERED
        // as the connection type.
        mConnectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS);

        // Initialize the SDK
        mConnectIQ.initialize(this, true, mListener);

        mTextView = (TextView)findViewById(R.id.status);
        mDataView = (TextView)findViewById(R.id.received_data);
        mLabel = (EditText)findViewById(R.id.label);
        mOpenAppButton = (Button)findViewById(R.id.openapp);
        mOpenAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Opening app...", Toast.LENGTH_SHORT).show();
                // Send a message to open the app
                try {
                    mConnectIQ.openApplication(mDevice, mMyApp, mOpenAppListener);
                } catch (Exception ex) {
                }
            }
        });

        startMeasure = findViewById(R.id.start_btn);
        startMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accelX.clear();
                accelY.clear();
                accelZ.clear();
            }
        });

        finishMeasure = findViewById(R.id.finish_btn);
        finishMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pushToDB();
            }
        });

        createSignInIntent();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSdkReady) {
            loadDevices();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mDevice != null) {
            // It is a good idea to unregister everything and shut things down to
            // release resources and prevent unwanted callbacks.
            try {
                mConnectIQ.unregisterForDeviceEvents(mDevice);

                if (mMyApp != null) {
                    mConnectIQ.unregisterForApplicationEvents(mDevice, mMyApp);
                }
            } catch (InvalidStateException e) {
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // It is a good idea to unregister everything and shut things down to
        // release resources and prevent unwanted callbacks.
        try {
            mConnectIQ.unregisterAllForEvents();
            mConnectIQ.shutdown(this);
        } catch (InvalidStateException e) {
            // This is usually because the SDK was already shut down
            // so no worries.
        }
    }

    public void loadDevices() {
        // Retrieve the list of known devices
        try {
            List<IQDevice> devices = mConnectIQ.getKnownDevices();

            if (devices != null) {
//                mAdapter.setDevices(devices);

                // Let's register for device status updates.  By doing so we will
                // automatically get a status update for each device so we do not
                // need to call getStatus()
                for (IQDevice device : devices) {
//                    mConnectIQ.registerForDeviceEvents(device, mDeviceEventListener);

                    if (device.getFriendlyName().equals("vívoactive 4S")) {
                        connectDevice(device);
                    }
                }
            }

        } catch (InvalidStateException e) {
            // This generally means you forgot to call initialize(), but since
            // we are in the callback for initialize(), this should never happen
        } catch (ServiceUnavailableException e) {
            // This will happen if for some reason your app was not able to connect
            // to the ConnectIQ service running within Garmin Connect Mobile.  This
            // could be because Garmin Connect Mobile is not installed or needs to
            // be upgraded.
            if( null != mTextView )
                mTextView.setText(R.string.service_unavailable);
        }
    }

    public void connectDevice(IQDevice device) {
        mDevice = device;
        mMyApp = new IQApp(MY_APP);
        mAppIsOpen = false;
        Log.d(TAG, "^^Connected " + device.getFriendlyName());

        if (mDevice != null) {
            mTextView.setText(mDevice.getFriendlyName() + " - " + mDevice.getStatus().name());

            try {
                mConnectIQ.registerForDeviceEvents(mDevice, new ConnectIQ.IQDeviceEventListener() {

                    @Override
                    public void onDeviceStatusChanged(IQDevice iqDevice, IQDevice.IQDeviceStatus iqDeviceStatus) {
                        mTextView.setText(mDevice.getFriendlyName() + " - " + iqDeviceStatus.name());
                    }
                });
            } catch (InvalidStateException e) {
                Log.wtf(TAG, "InvalidStateException:  We should not be here!");
            }

            getGarminApp();
        }
    }

    public void getGarminApp() {
        // Let's check the status of our application on the device.
        try {
            mConnectIQ.getApplicationInfo(MY_APP, mDevice, new ConnectIQ.IQApplicationInfoListener() {

                @Override
                public void onApplicationInfoReceived(IQApp app) {
                    // This is a good thing. Now we can show our list of message options.
                    Log.d(TAG, "Garmin App is ready! :D");

                    // Send a message to open the app
                    try {
                        Toast.makeText(getApplicationContext(), "Opening app...", Toast.LENGTH_SHORT).show();
                        mConnectIQ.openApplication(mDevice, app, mOpenAppListener);
                    } catch (Exception ex) {
                    }
                }

                @Override
                public void onApplicationNotInstalled(String applicationId) {
                    // The Comm widget is not installed on the device so we have
                    // to let the user know to install it.
                    AlertDialog.Builder dialog = new AlertDialog.Builder(GarminActivity.this);
                    dialog.setTitle(R.string.missing_widget);
                    dialog.setMessage(R.string.missing_widget_message);
                    dialog.setPositiveButton(android.R.string.ok, null);
                    dialog.create().show();
                }

            });
        } catch (InvalidStateException e1) {
        } catch (ServiceUnavailableException e1) {
        }

        receiveFromGarmin();
    }

    public void receiveFromGarmin() {
        // Let's register to receive messages from our application on the device.
        try {
            mConnectIQ.registerForAppEvents(mDevice, mMyApp, new ConnectIQ.IQApplicationEventListener() {

                @Override
                public void onMessageReceived(IQDevice device, IQApp app, List<Object> message, ConnectIQ.IQMessageStatus status) {

                    // We know from our Comm sample widget that it will only ever send us strings, but in case
                    // we get something else, we are simply going to do a toString() on each object in the
                    // message list.
                    StringBuilder builder = new StringBuilder();

                    if (message.size() > 0) {
                        for (Object o : message) {
                            builder.append(o.toString());
                            builder.append("\r\n");
                        }
                    } else {
                        builder.append("Received an empty message from the application");
                    }

                    String received = builder.toString();
                    mDataView.setText(received);
                    categorizeDate(received);

//                    AlertDialog.Builder dialog = new AlertDialog.Builder(GarminActivity.this);
//                    dialog.setTitle(R.string.received_message);
//                    dialog.setMessage(builder.toString());
//                    dialog.setPositiveButton(android.R.string.ok, null);
//                    dialog.create().show();
                }

            });
        } catch (InvalidStateException e) {
            Toast.makeText(this, "ConnectIQ is not in a valid state", Toast.LENGTH_SHORT).show();
        }
    }

    public void categorizeDate(String received) {
        String[] arrSplit = received.split("/");
        for (int i=0; i < arrSplit.length; i++) {
            if (arrSplit[i].substring(0,1).equals("X")) {
                String x_samples = arrSplit[i].substring(3, arrSplit[i].length() - 1);
                String x_str[] = x_samples.split(",");
//                int[] x_int = Arrays.stream(x_samples.split(",")).mapToInt(Integer::parseInt).toArray();
                accelX.addAll(Arrays.asList(x_str));

                Log.d(TAG, "x_samples: " + x_samples);
            } else if (arrSplit[i].substring(0,1).equals("Y")) {
                String y_samples = arrSplit[i].substring(3, arrSplit[i].length() - 1);
                String y_str[] = y_samples.split(",");
                accelY.addAll(Arrays.asList(y_str));

                Log.d(TAG, "y_samples: " + y_samples);
            } else if (arrSplit[i].substring(0,1).equals("Z")) {
                String z_samples = arrSplit[i].substring(3, arrSplit[i].length() - 3);
                String z_str[] = z_samples.split(",");
                accelZ.addAll(Arrays.asList(z_str));

                Log.d(TAG, "z_samples: " + z_samples);
            }
        }
    }

    public void pushToDB() {
        Date current = Calendar.getInstance(timeZone).getTime();
        sdf_date.setTimeZone(timeZone);
        sdf_time.setTimeZone(timeZone);
        final String currentDate = sdf_date.format(current);
        final String currentTime = sdf_time.format(current);
        final String dataTimestamp = currentDate + "_" + currentTime;
        final String label = mLabel.getText().toString();

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .setPersistenceEnabled(true)
                .build();
        db.setFirestoreSettings(settings);

        try {
            StringBuilder listStringRAccelX = new StringBuilder();
            StringBuilder listStringRAccelY = new StringBuilder();
            StringBuilder listStringRAccelZ = new StringBuilder();

            for (String v : accelX) { listStringRAccelX.append(v); listStringRAccelX.append(","); }
            for (String v : accelY) { listStringRAccelY.append(v); listStringRAccelY.append(","); }
            for (String v : accelZ) { listStringRAccelZ.append(v); listStringRAccelZ.append(","); }

            Map<String, Object> docData = new HashMap<>();
            docData.put("StartDate", currentDate);
            docData.put("StartTime", currentTime);
            docData.put("Label", label);

            docData.put("AccelX", String.valueOf(listStringRAccelX));
            docData.put("AccelY", String.valueOf(listStringRAccelY));
            docData.put("AccelZ", String.valueOf(listStringRAccelZ));



            db.collection(FIREBASE_DOC).document(dataTimestamp)
                    .set(docData)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(TAG, "DocumentSnapshot successfully written!");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@androidx.annotation.NonNull Exception e) {
                            Log.w(TAG, "Error writing document", e);
                        }
                    });

            // Reset variables
            accelX.clear();
            accelY.clear();
            accelZ.clear();
        } catch (Exception e) {
            Log.w("Save Failed", e.getMessage());
            Toast.makeText(this, "Save Failed", Toast.LENGTH_LONG).show();
        }
    }

    public void createSignInIntent() {
        // [START auth_fui_create_intent]
        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.PhoneBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
        // [END auth_fui_create_intent]
    }

    // [START auth_fui_result]
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                Log.d(TAG, "onActivityResult: " + "Successfully signed in");
                // ...
            } else {
                Log.d(TAG, "onActivityResult: " + response.getError().getErrorCode());
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }
    // [END auth_fui_result]

}