package com.krrishcoder.app_car

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothHelper {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null


    fun connectToDevice(deviceAddress: String): Boolean {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        return try {
            bluetoothSocket = device?.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            Log.d("Bluetooth", "Connected to $deviceAddress")
            true
        } catch (e: IOException) {
            Log.e("Bluetooth", "Connection failed", e)
            false
        }
    }


    fun sendCommand(command: String) {
        try {
            outputStream?.write(command.toByteArray())
            Log.d("Bluetooth", "Command sent: $command")
        } catch (e: IOException) {
            Log.e("Bluetooth", "Error sending command", e)
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d("Bluetooth", "Disconnected")
        } catch (e: IOException) {
            Log.e("Bluetooth", "Error disconnecting", e)
        }
    }




}