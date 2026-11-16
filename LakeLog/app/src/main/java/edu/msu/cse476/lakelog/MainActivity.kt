package edu.msu.cse476.lakelog

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import edu.msu.cse476.lakelog.databinding.ActivityMainBinding
import kotlin.math.roundToInt

/**
 * Shows the ambient light level and the observer's name (1a-1d).
 * The light sensor is registered in onResume and released in onPause, so it is used only
 * while this Activity is visible (1b).
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: Storage
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        storage = Storage(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            binding.lightValue.setText(R.string.no_light_sensor)
        }

        // The EditText restores its own text on rotation; on a fresh start, load the saved name.
        if (savedInstanceState == null) {
            binding.observerInput.setText(storage.observerName)
        }
        binding.observerInput.doAfterTextChanged { storage.observerName = it?.toString().orEmpty() }

        binding.readingsButton.setOnClickListener {
            startActivity(Intent(this, ReadingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this) // release the sensor whenever we leave the screen
        storage.observerName = binding.observerInput.text?.toString().orEmpty()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            binding.lightValue.text = getString(R.string.lux_value, event.values[0].roundToInt())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
