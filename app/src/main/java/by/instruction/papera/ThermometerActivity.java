package by.instruction.papera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ThermometerActivity extends AppCompatActivity implements SensorEventListener {
    private final DecimalFormat tempFormat = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private SensorManager sensorManager;
    private Sensor temperatureSensor;
    private TextView textTempValue;
    private TextView textTempStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thermometer);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.thermometer_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textTempValue = findViewById(R.id.textTempValue);
        textTempStatus = findViewById(R.id.textTempStatus);
        FloatingActionButton btnRefreshThermometer = findViewById(R.id.btnRefreshThermometer);
        btnRefreshThermometer.setOnClickListener(v -> refreshTemperatureData(true));

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        }

        if (temperatureSensor == null) {
            textTempValue.setText("--");
            textTempStatus.setText(R.string.thermometer_missing_sensor);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTemperatureData(false);
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
        if (event.sensor.getType() != Sensor.TYPE_AMBIENT_TEMPERATURE || event.values.length == 0) {
            return;
        }
        float temperature = event.values[0];
        textTempValue.setText(tempFormat.format(temperature));
        textTempStatus.setText("");
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {
        // Для термометра достаточно текущего значения датчика.
    }

    private void refreshTemperatureData(boolean userInitiated) {
        if (sensorManager == null || temperatureSensor == null) {
            if (userInitiated) {
                Toast.makeText(this, R.string.thermometer_refresh_no_sensor, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        sensorManager.unregisterListener(this);
        textTempStatus.setText(R.string.thermometer_waiting);
        sensorManager.registerListener(this, temperatureSensor, SensorManager.SENSOR_DELAY_UI);
    }
}
