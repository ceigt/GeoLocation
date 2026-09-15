package io.github.ceigt.geolocation.regression;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** External consumer: never inherits the manager app's exclusion from system hooks. */
public class LocationRegressionTest {
    private Context context;
    private LocationManager manager;
    private Activity activity;
    private final ExecutorService callbacks = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<LocationListener> listeners = new CopyOnWriteArrayList<>();
    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        manager = context.getSystemService(LocationManager.class);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
            new Intent(context, ProbeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    @After public void cleanup() {
        for (LocationListener listener : listeners) manager.removeUpdates(listener);
        callbacks.shutdownNow();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.finish());
    }
    private void requirePermission() {
        assertEquals("Grant fine location to the probe before running the suite",
            PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
    }
    private Location expected() {
        Bundle args = InstrumentationRegistry.getArguments();
        assertNotNull("Pass -e latitude and -e longitude for the selected WGS84 simulation point", args.getString("latitude"));
        assertNotNull(args.getString("longitude"));
        Location point = new Location("expected");
        point.setLatitude(Double.parseDouble(args.getString("latitude")));
        point.setLongitude(Double.parseDouble(args.getString("longitude")));
        return point;
    }
    @Test public void arbitraryLocationObjectsRemainUnchanged() {
        Location history = new Location("history");
        history.setLatitude(12.25); history.setLongitude(23.75);
        history.setAltitude(345); history.setSpeed(6);
        assertEquals(12.25, history.getLatitude(), 0);
        assertEquals(23.75, history.getLongitude(), 0);
        assertEquals(345, history.getAltitude(), 0);
        assertEquals(6, history.getSpeed(), 0);
    }
    @Test public void differentCertificateCannotControlModule() throws Exception {
        assertEquals(PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission("io.github.ceigt.geolocation.CONTROL"));
        android.content.pm.ActivityInfo receiver = context.getPackageManager().getReceiverInfo(
            new ComponentName("io.github.ceigt.geolocation", "io.github.ceigt.geolocation.manager.control.ControlReceiver"),
            PackageManager.MATCH_DISABLED_COMPONENTS);
        assertEquals("io.github.ceigt.geolocation.CONTROL", receiver.permission);
    }
    @Test public void singleUpdateRemainsSingleWithRealAndSyntheticSources() throws Exception {
        requirePermission(); Location point = expected();
        AtomicInteger count = new AtomicInteger(); CountDownLatch first = new CountDownLatch(1);
        CopyOnWriteArrayList<Location> fixes = new CopyOnWriteArrayList<>();
        LocationListener listener = location -> { fixes.add(new Location(location)); count.incrementAndGet(); first.countDown(); };
        listeners.add(listener);
        manager.requestLocationUpdates("gps", new LocationRequest.Builder(1000).setMaxUpdates(1)
            .setDurationMillis(10000).build(), callbacks, listener);
        assertTrue("No simulated fix", first.await(12, TimeUnit.SECONDS));
        Thread.sleep(3500);
        assertEquals("Native and supplemental callbacks exceeded maxUpdates", 1, count.get());
        assertTrue("Unexpected position", fixes.get(0).distanceTo(point) < 200);
    }
    @Test public void sameListenerAcrossProvidersSurvivesOneProviderExpiryAndCancels() throws Exception {
        requirePermission(); Location point = expected();
        ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<Location> fixes = new CopyOnWriteArrayList<>();
        CountDownLatch arrivals = new CountDownLatch(4);
        LocationListener listener = location -> {
            fixes.add(new Location(location));
            counts.computeIfAbsent(location.getProvider(), key -> new AtomicInteger()).incrementAndGet(); arrivals.countDown();
        };
        listeners.add(listener);
        manager.requestLocationUpdates("gps", new LocationRequest.Builder(1000).setMaxUpdates(1).build(), callbacks, listener);
        manager.requestLocationUpdates("network", new LocationRequest.Builder(1000).setDurationMillis(10000).build(), callbacks, listener);
        assertTrue("Multiple-provider request did not progress", arrivals.await(12, TimeUnit.SECONDS));
        assertNotNull(counts.get("gps")); assertEquals(1, counts.get("gps").get());
        assertNotNull(counts.get("network")); assertTrue(counts.get("network").get() >= 3);
        for (Location fix : fixes) assertTrue("Unexpected position", fix.distanceTo(point) < 200);
        manager.removeUpdates(listener);
        Thread.sleep(1000); int afterDrain = fixes.size();
        Thread.sleep(3000); assertEquals("Callbacks continued after cancellation", afterDrain, fixes.size());
    }
    @Test public void stationaryFixDoesNotConsumeDistanceGatedRequest() throws Exception {
        requirePermission(); Location point = expected();
        AtomicInteger count = new AtomicInteger(); CountDownLatch first = new CountDownLatch(1);
        CopyOnWriteArrayList<Location> fixes = new CopyOnWriteArrayList<>();
        LocationListener listener = fix -> { fixes.add(new Location(fix)); count.incrementAndGet(); first.countDown(); };
        listeners.add(listener);
        manager.requestLocationUpdates("gps", new LocationRequest.Builder(1000)
            .setMinUpdateDistanceMeters(1000f).setMaxUpdates(2).setDurationMillis(10000).build(), callbacks, listener);
        assertTrue("No first fix", first.await(12, TimeUnit.SECONDS));
        assertTrue(fixes.get(0).distanceTo(point) < 200);
        Thread.sleep(3500);
        assertEquals("Stationary simulation must not trigger distance-gated updates", 1, count.get());
    }
    @Test public void coarseCacheQueryPreservesGranularity() {
        org.junit.Assume.assumeTrue("Run separately after granting coarse and revoking fine",
            "true".equals(InstrumentationRegistry.getArguments().getString("coarseOnly")));
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        Location point = expected();
        Location fix = manager.getLastKnownLocation("network");
        assertNotNull("Coarse cache absent: precondition/adapter needs investigation; not a pass", fix);
        assertTrue("Coarse accuracy was upgraded", fix.getAccuracy() >= 200);
        assertFalse("Coarse fix exposed altitude", fix.hasAltitude());
        assertFalse("Coarse fix exposed speed", fix.hasSpeed());
        assertTrue("Coarse fix is not near simulation point", fix.distanceTo(point) < 10000);
    }
}
