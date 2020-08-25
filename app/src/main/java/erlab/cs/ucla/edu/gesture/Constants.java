package erlab.cs.ucla.edu.gesture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class Constants {
    public static SimpleDateFormat sdf_date = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    public static SimpleDateFormat sdf_time = new SimpleDateFormat("HH:mm:ss");
    public static TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");
}
