package by.instruction.papera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

public class CompassActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] gravityData = new float[3];
    private final float[] magneticData = new float[3];
    private boolean hasGravityData = false;
    private boolean hasMagneticData = false;
    private TextView textCompassDegrees;
    private TextView textCompassDirection;
    private TextView textCompassStatus;
    private ImageView imageCompassNeedle;
    private float currentNeedleRotation = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.compass_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textCompassDegrees = findViewById(R.id.textCompassDegrees);
        textCompassDirection = findViewById(R.id.textCompassDirection);
        textCompassStatus = findViewById(R.id.textCompassStatus);
        imageCompassNeedle = findViewById(R.id.imageCompassNeedle);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        if (accelerometer == null || magnetometer == null) {
            textCompassDegrees.setText("--");
            textCompassDirection.setText("--");
            textCompassStatus.setText(R.string.compass_missing_sensors);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            System.arraycopy(event.values, 0, gravityData, 0, 3);
            hasGravityData = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD && event.values.length >= 3) {
            System.arraycopy(event.values, 0, magneticData, 0, 3);
            hasMagneticData = true;
        }

        if (!hasGravityData || !hasMagneticData) {
            return;
        }

        float[] rotationMatrix = new float[9];
        float[] orientation = new float[3];
        boolean ok = SensorManager.getRotationMatrix(rotationMatrix, null, gravityData, magneticData);
        if (!ok) {
            return;
        }

        SensorManager.getOrientation(rotationMatrix, orientation);
        float azimuthRad = orientation[0];
        int azimuthDeg = Math.round((float) Math.toDegrees(azimuthRad));
        if (azimuthDeg < 0) {
            azimuthDeg += 360;
        }

        textCompassDegrees.setText(String.format(Locale.US, "%d°", azimuthDeg));
        textCompassDirection.setText(directionFromAzimuth(azimuthDeg));
        textCompassStatus.setText("");
        smoothRotateNeedle(-azimuthDeg);
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {
        // Для компаса достаточно обновления текущего направления.
    }

    private String directionFromAzimuth(int deg) {
        if (deg >= 337 || deg < 23) return "N";
        if (deg < 68) return "NE";
        if (deg < 113) return "E";
        if (deg < 158) return "SE";
        if (deg < 203) return "S";
        if (deg < 248) return "SW";
        if (deg < 293) return "W";
        return "NW";
    }

    private void smoothRotateNeedle(float targetRotation) {
        if (imageCompassNeedle == null) {
            return;
        }

        float delta = targetRotation - currentNeedleRotation;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;

        currentNeedleRotation += delta * 0.18f;
        imageCompassNeedle.setRotation(currentNeedleRotation);
    }
}
