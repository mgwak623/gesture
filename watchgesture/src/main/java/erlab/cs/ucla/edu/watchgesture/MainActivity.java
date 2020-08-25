package erlab.cs.ucla.edu.watchgesture;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends WearableActivity
        implements DataClient.OnDataChangedListener, SensorEventListener {

    private static final String TAG = "MainActivity";

    private TextView mTextView;

    /* Sensor Settings */
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;

    /* Variables */
    private float getAccelX, getAccelY, getAccelZ;
    private float getGyroX, getGyroY, getGyroZ;
    private String sensorValue = "";

    private HandlerThread mSensorThread;
    private Handler mSensorHandler;

    DataClient dataClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = findViewById(R.id.text);

        // Enables Always-on
        setAmbientEnabled();

        /* Initialize Sensor Manager */
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        dataClient = Wearable.getDataClient(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorThread = new HandlerThread("Sensor thread", Thread.MAX_PRIORITY);
        mSensorThread.start();
        mSensorHandler = new Handler(mSensorThread.getLooper()); //Blocks until looper is prepared, which is fairly quick
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL, mSensorHandler);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL, mSensorHandler);

        Wearable.getDataClient(this).addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSensorManager.unregisterListener(this);
        if (mSensorThread != null) {
            mSensorThread.quitSafely();
        }

        Wearable.getDataClient(this).removeListener(this);
    }




    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {

    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
// If the sensor is unreliable, then just return
        if (sensorEvent.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return;
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            Log.i(TAG, "Accel pass");
            getAccelX = sensorEvent.values[0];
            getAccelY = sensorEvent.values[1];
            getAccelZ = sensorEvent.values[2];
        }

        if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            Log.i(TAG, "Gyro pass");
            getGyroX = sensorEvent.values[0];
            getGyroY = sensorEvent.values[1];
            getGyroZ = sensorEvent.values[2];
        }

        float magnitudeAccel = (float)Math.sqrt((getAccelX*getAccelX) + (getAccelY*getAccelY) + (getAccelZ*getAccelZ));
        float magnitudeGyro = (float)Math.sqrt((getGyroX*getGyroX) + (getGyroY*getGyroY) + (getGyroZ*getGyroZ));
        long timestamp = System.currentTimeMillis();

        sensorValue = "ax = " + getAccelX + "\n" +
                "ay = " + getAccelY + "\n" +
                "az = " + getAccelZ;
        Log.d(TAG, sensorValue);

        sendSensorValue(getAccelX, getAccelY, getAccelZ, getGyroX, getGyroY, getGyroZ, magnitudeAccel, magnitudeGyro, timestamp);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void sendSensorValue(float ax, float ay, float az, float gx, float gy, float gz, float magaccel, float maggyro, long ts) {
        Log.d(TAG, "sendSensorValue");
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/sensor");
        putDataMapRequest.getDataMap().putFloat("AccelX", ax);
        putDataMapRequest.getDataMap().putFloat("AccelY", ay);
        putDataMapRequest.getDataMap().putFloat("AccelZ", az);
        putDataMapRequest.getDataMap().putFloat("GyroX", gx);
        putDataMapRequest.getDataMap().putFloat("GyroY", gy);
        putDataMapRequest.getDataMap().putFloat("GyroZ", gx);
        putDataMapRequest.getDataMap().putLong("Timestamp", ts);

        PutDataRequest request = putDataMapRequest.asPutDataRequest();

        Task<DataItem> putDataTask = dataClient.putDataItem(request);

        putDataTask.addOnSuccessListener(
                new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        Log.d(TAG, "Sending data was successful: " + dataItem);
                        mTextView.setText("Sending data was successful");
                    }
                });

//        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
//                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
//                    @Override
//                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
//                        if(!dataItemResult.getStatus().isSuccess()) {
//                            Log.e(TAG, "Failed to send data item");
//                        } else {
//                            Log.d(TAG, "Success to send data item");
//                        }
//                    }
//                });
    }
}
