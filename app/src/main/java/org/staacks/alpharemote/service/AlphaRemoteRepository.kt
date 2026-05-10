package org.staacks.alpharemote.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.location.LocationManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.ui.settings.CompanionDeviceHelper

class AlphaRemoteRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Gone)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _locationEnabled = MutableStateFlow(false)
    val locationEnabled: StateFlow<Boolean> = _locationEnabled.asStateFlow()

    private val _associations = MutableStateFlow<List<String>>(emptyList())
    val associations: StateFlow<List<String>> = _associations.asStateFlow()

    private var boundService: AlphaRemoteService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AlphaRemoteService.LocalBinder
            boundService = binder.getService()
            scope.launch {
                boundService?.internalServiceState?.collectLatest {
                    _serviceState.value = it
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            _serviceState.value = ServiceState.Gone
        }
    }

    init {
        checkInitialStates()
        registerReceivers()
        bindToService()
    }

    private fun checkInitialStates() {
        val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
        _bluetoothEnabled.value = bluetoothManager?.adapter?.isEnabled == true

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _locationEnabled.value = locationManager.isLocationEnabled

        _associations.value = CompanionDeviceHelper.getAssociation(context)
    }

    private fun registerReceivers() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        _bluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                    }
                    LocationManager.MODE_CHANGED_ACTION -> {
                        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        _locationEnabled.value = locationManager?.isLocationEnabled == true
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        _associations.value = CompanionDeviceHelper.getAssociation(context!!)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
    }

    private fun bindToService() {
        val intent = Intent(context, AlphaRemoteService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun sendCameraAction(action: CameraAction, event: Int?) {
        boundService?.executeCameraAction(action, 
            down = event == android.view.MotionEvent.ACTION_DOWN,
            up = event == android.view.MotionEvent.ACTION_UP || event == android.view.MotionEvent.ACTION_CANCEL
        )
    }

    fun startAdvancedSequence(bulbDuration: Float, intervalCount: Int, intervalDuration: Float) {
        // We could also call this via binder if we expose a method in AlphaRemoteService
        val intent = Intent(context, AlphaRemoteService::class.java).apply {
            action = AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_ACTION
            putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA, bulbDuration)
            putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA, intervalCount)
            putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA, intervalDuration)
        }
        context.startService(intent)
    }

    companion object {
        private const val TAG = "AlphaRemoteRepository"
        
        @Volatile
        private var INSTANCE: AlphaRemoteRepository? = null

        fun getInstance(context: Context): AlphaRemoteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlphaRemoteRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
