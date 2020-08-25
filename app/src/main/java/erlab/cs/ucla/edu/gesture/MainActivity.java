package erlab.cs.ucla.edu.gesture;

import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class MainActivity extends AppCompatActivity implements DataClient.OnDataChangedListener {

    private static final String TAG = "MainActivity";
    private static final String DATA_PATH = "/sensor";   //receive from wearable
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US);
    private static final SimpleDateFormat SQL_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);


    private TextView status, sid_text;
    private float setAccelX, setAccelY, setAccelZ, setMagAccel;
    private float setGyroX, setGyroY, setGyroZ, setMagGyro;
    private long setTimestamp;
    private String totalData = "";
    private int sid = 0;
    List<String> motionData = new ArrayList<>();
    private int i = 0;
    private int mode;

    List<Float> accelX = new CopyOnWriteArrayList<>();
    List<Float> accelY = new CopyOnWriteArrayList<>();
    List<Float> accelZ = new CopyOnWriteArrayList<>();
    List<Float> gyroX = new CopyOnWriteArrayList<>();
    List<Float> gyroY = new CopyOnWriteArrayList<>();
    List<Float> gyroZ = new CopyOnWriteArrayList<>();
    List<Long> timestamp = new CopyOnWriteArrayList<>();

    // Send DataItems.
    private ScheduledExecutorService mGeneratorExecutor;
    private ScheduledFuture<?> mDataItemGeneratorFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* UI */
        status = (TextView) findViewById(R.id.data_status);
        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggle_bt);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    totalData = "";

                } else {
                    // Save the file
                    saveToFirebase();

//                    if (totalData != null) {
//                        // Save on local storage
//                        saveLocalStorage(totalData);
//
//                        // Save on AWS
//                        WalkData walkData = new WalkData();
//                        walkData.execute(String.valueOf(sid), SQL_DATE_FORMAT.format(new Date()),
//                                String.valueOf(timestamp), String.valueOf(accelX), String.valueOf(accelY),
//                                String.valueOf(accelZ), String.valueOf(accelM), String.valueOf(gyroX),
//                                String.valueOf(gyroY), String.valueOf(gyroZ), String.valueOf(gyroM));
//
//
//                        timestamp.clear();
//                        accelX.clear();
//                        accelY.clear();
//                        accelZ.clear();
//                        gyroX.clear();
//                        gyroY.clear();
//                        gyroZ.clear();
//                    }
                }
            }
        });

        mGeneratorExecutor = new ScheduledThreadPoolExecutor(1);

    }

    @Override
    public void onResume() {
        super.onResume();
//        mDataItemGeneratorFuture =
//                mGeneratorExecutor.scheduleWithFixedDelay(
//                        new DataItemGenerator(), 1, 5, TimeUnit.SECONDS);

        // Instantiates clients without member variables, as clients are inexpensive to create and
        // won't lose their listeners. (They are cached and shared between GoogleApi instances.)
        Wearable.getDataClient(this).addListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
//        mDataItemGeneratorFuture.cancel(true /* mayInterruptIfRunning */);

        Wearable.getDataClient(this).removeListener(this);
    }

    public void saveToFirebase() {

    }

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "onDataChanged: " + dataEventBuffer);
        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(DATA_PATH)) {
                    setAccelX = dataMap.getFloat("AccelX");
                    accelX.add(setAccelX);
                    setAccelY = dataMap.getFloat("AccelY");
                    accelY.add(setAccelY);
                    setAccelZ = dataMap.getFloat("AccelZ");
                    accelZ.add(setAccelZ);
                    setGyroX = dataMap.getFloat("GyroX");
                    gyroX.add(setGyroX);
                    setGyroY = dataMap.getFloat("GyroY");
                    gyroY.add(setGyroY);
                    setGyroZ = dataMap.getFloat("GyroZ");
                    gyroZ.add(setGyroZ);
                    setTimestamp = dataMap.getLong("Timestamp");
                    timestamp.add(setTimestamp);

//                    final String curData = "Status: Data from smartwatch\n ax = " + setAccelX + "\nay = " + setAccelY + "\naz = " + setAccelZ + "\nmagAccel = " + setMagAccel +
//                            "\ngx = " + setGyroX + "\ngy = " + setGyroY + "\ngz = " + setGyroZ + "\nmagGyro = " + setMagGyro;
                    final String curData = "Status: Data from smartwatch\n ax = " + setAccelX + "  ay = " + setAccelY + "  az = " + setAccelZ +
                            "\ngx = " + setGyroX + "  gy = " + setGyroY + "  gz = " + setGyroZ ;

                    status.setText(curData);
//                    saveLocalStorage(curData);
                    totalData += setTimestamp + "\t" + setAccelX + "\t" + setAccelY + "\t"
                            + setAccelZ + "\t" + setMagAccel + '\t' + setGyroX + '\t' + setGyroY + '\t' + setGyroZ + '\t' +
                            setMagGyro + "\r\n";

                    motionData.add(curData);
                }
            }
        }
    }


}
