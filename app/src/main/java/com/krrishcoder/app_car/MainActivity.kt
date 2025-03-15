package com.krrishcoder.app_car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.things.bluetooth.BluetoothProfile
import com.krrishcoder.app_car.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {
    //binding class, yto access views
    private lateinit var binding : ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private val ESP32_MAC = "5C:01:3B:33:92:E6"  // Replace with your ESP32 MAC Address

    private var isButtonPressed = false // Flag to prevent sensor data from sending while button is pressed

    private var  turnValue = 0


//sensor______________________________________________
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gravity: Sensor? = null
    //______________________________________
    private val stopHandler = Handler(Looper.getMainLooper())

    private var lastCommand: String = ""


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }




        setTouchListener(binding.right, R.drawable.right, "right")
        setTouchListener(binding.left, R.drawable.left, "left")
        setTouchListener(binding.forward, R.drawable.forward, "forward")
        setTouchListener(binding.back, R.drawable.back, "back")



        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)


        binding.zeroTurn.setOnClickListener {
            binding.knob.progress = 100F
            val progress = binding.knob.progress  // Get knob's current progress
            turnValue  = (progress - 100).roundToInt()
            binding.turnValueText.text = "Turn: $turnValue"
        }




        binding.carHorn.setOnTouchListener { _, event ->

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    toggleColorHorn(binding.carHorn, R.drawable.car_horn, Color.RED)
                    binding.textHorn.setTextColor(Color.RED)
                    sendCommand(gatt, "bo")   // buzzer on
                    true  // Ensure event is consumed
                }
                MotionEvent.ACTION_UP -> {
                    toggleColorHorn(binding.carHorn  , R.drawable.car_horn, Color.BLACK)
                    binding.textHorn.setTextColor(Color.BLACK)
                    sendCommand(gatt, "bf")  // buzzer off
                    true  // Ensure event is consumed
                }
                else -> false  // Do not consume other events
            }
        }





        binding.knob.setOnTouchListener { _, event ->
            if (  event.action == MotionEvent.ACTION_DOWN ||  event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP) {
                val progress = binding.knob.progress  // Get knob's current progress
               turnValue  = (progress - 100).roundToInt()  // Convert range 0-200 to -100 to +100

                binding.turnValueText.text = "Turn: $turnValue"


                Log.d("KnobDebug", "Progress: $progress | Turn: $turnValue")
            }
            false  // Return false to allow normal knob behavior
        }


        //=========================================================================== Dont change it=========================================================================================================================================
   //      bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
       val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter =  bluetoothManager.getAdapter()


        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        val device = bluetoothAdapter!!.getRemoteDevice(ESP32_MAC)
        gatt = device.connectGatt(this, false, gattCallback)

        //====================================================================================================================================================================================================================

    }



    //================================================== Dont change it=============================================================================================================


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("BLE", "Connected to ESP32")
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    return
                }
                gatt?.discoverServices() // Discover services after connection
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from ESP32")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Services Discovered")
            }
        }


    }
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(gatt: BluetoothGatt?, command: String) {
        val serviceUUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E") // NUS Service
        val characteristicUUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // RX Characteristic

        val service = gatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)

        if (characteristic != null) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // or WRITE_TYPE_NO_RESPONSE
            val commandBytes = command.toByteArray(Charsets.UTF_8)

            gatt.writeCharacteristic(characteristic, commandBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            Log.e("BLE", "Characteristic not found")
        }
    }



    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()  // Close connection when app closes
    }


 //====================================================================================================================================================================================================================



    // new code______________________________ inserted
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun setTouchListener(button: ImageView, drawableRes: Int, buttonName: String) {
        val stopHandler = Handler(Looper.getMainLooper()) // For stop command
        val updateHandler = Handler(Looper.getMainLooper()) // For continuous updates

        val updateRunnable = object : Runnable {
            override fun run() {
                if (isButtonPressed) {
                    val latestTurnValue = (binding.knob.progress - 100).roundToInt()  // Get real-time value
                    Log.d("KnobDebug", "Latest Turn Value (Holding Button): $latestTurnValue")

                    when (buttonName) {
                        "forward" -> sendCommand(gatt, "f$latestTurnValue")
                        "back" -> sendCommand(gatt, "b$latestTurnValue")
                        "right" -> sendCommand(gatt, "r")
                        "left" -> sendCommand(gatt, "l")
                    }

                    updateHandler.postDelayed(this, 100) // Keep updating every 100ms
                }
            }
        }

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isButtonPressed = true // Block sensor data
                    Log.d("TouchEvent", "Finger on $buttonName button!")
                    toggleColor(button, drawableRes, Color.RED)

                    // Start sending continuous updates
                    updateHandler.post(updateRunnable)

                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isButtonPressed = false // Stop updates
                    Log.d("TouchEvent", "Finger lifted from $buttonName!")
                    toggleColor(button, drawableRes, Color.BLUE)

                    updateHandler.removeCallbacks(updateRunnable) // Stop continuous updates

                    sendCommand(gatt, "s")  // Stop command immediately

                    stopHandler.postDelayed({
                        sendCommand(gatt, "s")
                        Log.d("TouchEvent", "Backup Stop Command Sent for $buttonName")
                    }, 110) // Delay of 150ms

                    true
                }

                else -> false
            }
        }
    }


    private fun toggleColorHorn(button: ImageView, drawableRes: Int, color: Int) {
        ContextCompat.getDrawable(this, drawableRes)?.apply {
            setTint(color)
            button.setImageDrawable(this)
        }
    }



    private fun toggleColor(button: ImageView, drawableRes: Int, color: Int) {
        ContextCompat.getDrawable(this, drawableRes)?.apply {
            setTint(color)
            button.setImageDrawable(this)
        }
    }



    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gravity?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onSensorChanged(event: SensorEvent?) {


        if (event == null || isButtonPressed) return // Block sensor commands if a button is pressed

     /*   if (event.sensor.type == Sensor.TYPE_ACCELEROMETER || event.sensor.type == Sensor.TYPE_GRAVITY) {
            val x = event.values[0]  // Left-Right tilt
            val y = event.values[1]

            Log.d("TiltControl", "y: $y  x : $x")

            // Calculate roll angle
            val roll = Math.toDegrees(atan2(x, sqrt(event.values[1] * event.values[1])).toDouble())

            // Convert tilt angle to speed (0-100)
            val speed = ((Math.abs(roll) - 15) * 4).toInt().coerceIn(0, 100)

            val stopHandler = Handler(Looper.getMainLooper()) // Handler for delayed stop

            when {
                roll < -10 -> {
                    sendCommand(gatt, "y$speed")  // Left Turn
                    stopHandler.postDelayed({ sendCommand(gatt, "s") }, 250)  // Stop after 500ms
                }
                roll > 10 -> {
                    sendCommand(gatt, "x$speed")  // Right Turn
                    stopHandler.postDelayed({ sendCommand(gatt, "s") }, 250)  // Stop after 500ms
                }
            }


        }

        */
    }




    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }










}