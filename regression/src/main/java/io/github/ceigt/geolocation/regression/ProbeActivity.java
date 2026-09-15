package io.github.ceigt.geolocation.regression;
public class ProbeActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        android.widget.TextView view = new android.widget.TextView(this);
        view.setText("GeoLocation regression in progress. Keep this test activity in the foreground.");
        setContentView(view);
    }
}
