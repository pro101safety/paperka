package by.instruction.papera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class VibrometerActivity extends AppCompatActivity implements SensorEventListener {
    private final DecimalFormat valueFormat = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private TextView textVibrationValue;
    private TextView textVibrationUnit;
    private TextView textVibrationLevel;
    private TextView textVibrationAxes;
    private TextView textVibrationStatus;
    private VibrometerGraphView viewVibrationGraph;
    private final float[] gravity = new float[3];
    private boolean gravityInitialized = false;
    private int lowColor;
    private int mediumColor;
    private int highColor;
    private long lastGraphSampleMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vibrometer);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.vibrometer_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textVibrationValue = findViewById(R.id.textVibrationValue);
        textVibrationUnit = findViewById(R.id.textVibrationUnit);
        textVibrationLevel = findViewById(R.id.textVibrationLevel);
        textVibrationAxes = findViewById(R.id.textVibrationAxes);
        textVibrationStatus = findViewById(R.id.textVibrationStatus);
        viewVibrationGraph = findViewById(R.id.viewVibrationGraph);
        textVibrationAxes.setText(getString(R.string.vibrometer_axes, "0.00", "0.00", "0.00"));
        textVibrationLevel.setText(getString(R.string.vibrometer_level, getString(R.string.vibrometer_level_low)));

        lowColor = getColor(R.color.vibration_low);
        mediumColor = getColor(R.color.vibration_medium);
        highColor = getColor(R.color.vibration_high);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        if (accelerometer == null) {
            textVibrationValue.setText("--");
            textVibrationStatus.setText(R.string.vibrometer_missing_sensor);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
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
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || event.values.length < 3) {
            return;
        }

        final float alpha = 0.8f;
        if (!gravityInitialized) {
            gravity[0] = event.values[0];
            gravity[1] = event.values[1];
            gravity[2] = event.values[2];
            gravityInitialized = true;
            return;
        }

        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        float linearX = event.values[0] - gravity[0];
        float linearY = event.values[1] - gravity[1];
        float linearZ = event.values[2] - gravity[2];

        double vibration = Math.sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ);

        textVibrationValue.setText(valueFormat.format(vibration));
        textVibrationAxes.setText(getString(
                R.string.vibrometer_axes,
                valueFormat.format(linearX),
                valueFormat.format(linearY),
                valueFormat.format(linearZ)
        ));
        textVibrationStatus.setText("");
        updateColorAndLevel(vibration);
        long now = SystemClock.elapsedRealtime();
        if (viewVibrationGraph != null && (now - lastGraphSampleMs) >= 120L) {
            viewVibrationGraph.addSample((float) vibration);
            lastGraphSampleMs = now;
        }
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {
        // Для виброметра достаточно текущего потока значений.
    }

    private void updateColorAndLevel(double vibration) {
        int color;
        int levelTextRes;
        if (vibration < 0.6) {
            color = lowColor;
            levelTextRes = R.string.vibrometer_level_low;
        } else if (vibration < 1.8) {
            color = mediumColor;
            levelTextRes = R.string.vibrometer_level_medium;
        } else {
            color = highColor;
            levelTextRes = R.string.vibrometer_level_high;
        }

        textVibrationValue.setTextColor(color);
        textVibrationUnit.setTextColor(color);
        textVibrationAxes.setTextColor(color);
        textVibrationLevel.setTextColor(color);
        textVibrationLevel.setText(getString(R.string.vibrometer_level, getString(levelTextRes)));
    }
}
