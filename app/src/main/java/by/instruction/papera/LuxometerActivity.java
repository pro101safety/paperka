package by.instruction.papera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class LuxometerActivity extends AppCompatActivity implements SensorEventListener {
    private final DecimalFormat luxFormat = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private TextView textLuxValue;
    private TextView textLuxStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_luxometer);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        EdgeToEdgeHelper.applyToolbarScreenInsets(toolbar, findViewById(android.R.id.content));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.luxometer_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textLuxValue = findViewById(R.id.textLuxValue);
        textLuxStatus = findViewById(R.id.textLuxStatus);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        if (lightSensor == null) {
            textLuxValue.setText("--");
            textLuxStatus.setText(R.string.luxometer_missing_sensor);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
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
        if (event.sensor.getType() != Sensor.TYPE_LIGHT || event.values.length == 0) {
            return;
        }
        float lux = Math.max(0f, event.values[0]);
        textLuxValue.setText(luxFormat.format(lux));
        textLuxStatus.setText("");
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {
        // Для люксометра достаточно только текущего значения освещенности.
    }
}
