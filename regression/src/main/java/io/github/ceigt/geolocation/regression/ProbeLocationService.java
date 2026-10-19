package io.github.ceigt.geolocation.regression;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-only location FGS; no background-location permission is declared. */
public class ProbeLocationService extends Service {
    public static final CopyOnWriteArrayList<Location> fixes = new CopyOnWriteArrayList<>();
    public static volatile boolean running;
    private LocationManager manager;
    private final LocationListener listener = fix -> fixes.add(new Location(fix));

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("location-regression", "Location regression", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "location-regression")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Location regression running").build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        manager = getSystemService(LocationManager.class);
        manager.requestLocationUpdates("gps", new LocationRequest.Builder(1000).setDurationMillis(45000).build(),
            getMainExecutor(), listener);
        running = true;
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        running = false;
        if (manager != null) manager.removeUpdates(listener);
        super.onDestroy();
    }
}
