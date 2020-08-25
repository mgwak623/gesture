package erlab.cs.ucla.edu.gesture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ListenerService extends Service {
    public ListenerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
