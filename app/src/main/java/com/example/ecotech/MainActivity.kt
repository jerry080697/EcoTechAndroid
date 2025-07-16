package com.example.ecotech

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {
    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEVICE_ADDRESS = "00:11:22:33:44:55" // HC‑05 MAC
    }

    private lateinit var btAdapter: BluetoothAdapter
    private var btSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvDistance: TextView
    private lateinit var btnConnect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 화면 안전 영역 처리부분
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        // 뷰 바인딩
        tvStatus   = findViewById(R.id.tvStatus)
        tvDistance = findViewById(R.id.tvDistance)
        btnConnect = findViewById(R.id.btnConnect)

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            tvStatus.text = "블루투스 미지원 기기"
            btnConnect.isEnabled = false
            return
        }

        btnConnect.setOnClickListener {
            if (!btAdapter.isEnabled) {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1
                )
            } else if (btSocket?.isConnected != true) {
                requestBluetoothPermissions()
            } else {
                disconnectBluetooth()
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val granted = perms.all {
                ContextCompat.checkSelfPermission(this, it) ==
                        PackageManager.PERMISSION_GRANTED
            }
            if (!granted) {
                ActivityCompat.requestPermissions(this, perms, 2)
                return
            }
        }
        connectToBluetoothDevice()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            requestBluetoothPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            connectToBluetoothDevice()
        }
    }

    private fun connectToBluetoothDevice() {
        tvStatus.text = "연결 시도 중..."
        btnConnect.isEnabled = false

        Thread {
            try {
                val device: BluetoothDevice =
                    btAdapter.getRemoteDevice(DEVICE_ADDRESS)
                btAdapter.cancelDiscovery()
                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btSocket?.connect()
                inputStream  = btSocket?.inputStream
                outputStream = btSocket?.outputStream

                runOnUiThread {
                    tvStatus.text = "연결 완료!"
                    btnConnect.text = "Disconnect"
                    btnConnect.isEnabled = true
                }
                listenForData()
            } catch (e: IOException) {
                runOnUiThread {
                    tvStatus.text = "연결 실패: ${e.message}"
                    btnConnect.isEnabled = true
                }
            }
        }.start()
    }

    private fun listenForData() {
        val buffer = ByteArray(256)
        while (true) {
            try {
                val bytes = inputStream?.read(buffer) ?: break
                val readMsg = String(buffer, 0, bytes).trim()
                runOnUiThread {
                    tvDistance.text = "$readMsg cm"
                }
            } catch (e: IOException) {
                break
            }
        }
    }

    private fun disconnectBluetooth() {
        try {
            btSocket?.close()
        } catch (ignored: IOException) { }
        btSocket = null
        runOnUiThread {
            tvStatus.text = "연결 해제됨"
            btnConnect.text = "Connect"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectBluetooth()
    }
}
