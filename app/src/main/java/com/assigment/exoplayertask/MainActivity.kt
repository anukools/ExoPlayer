package com.assigment.exoplayertask

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.assigment.exoplayertask.databinding.ActivityMainBinding
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), SensorListener.onShakeListener, EasyPermissions.PermissionCallbacks {

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val playbackStateListener: Player.Listener = playbackStateListener()
    private var player: ExoPlayer? = null

    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L

    // shake gesture
    // Declaring sensorManager
    private var sensorManager: SensorManager? = null
    private lateinit var sensorListener: SensorListener

    // handle location
    private val RC_LOCATION_PERM = 101
    private var fusedLocationClient: FusedLocationProviderClient? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        setUpSensorManager()

        handleLocationShare()
    }

    private fun handleLocationShare() {
        viewBinding.btnLocation.setOnClickListener {
            checkForUserLocation()
        }
    }

    private fun checkForUserLocation() {
        if (hasLocationPermissions()) {
            // Have permission, do the thing!
            Toast.makeText(this, "Location Permission available", Toast.LENGTH_LONG).show()

            getUserCurrentLocation()
        }
        else {
            // Ask for one permission
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.rationale_location),
                RC_LOCATION_PERM,
                Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getUserCurrentLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(0)
            .setMinUpdateDistanceMeters(10.0f) // MIN Distance for location updates 10 meters
            .build()
        fusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallBack,
            Looper.getMainLooper())
    }

    var currentLat = 0.0
    var currentLong = 0.0
    private var locationCallBack = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            for (location in locationResult.locations) {
                if (currentLat == 0.0) {
                    currentLat = location.latitude
                    currentLong = location.longitude
                } else {
//                    Toast.makeText(applicationContext, "Location ${location.latitude}", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Location ${location.longitude}")

                    val results = floatArrayOf(10f)
                    Location.distanceBetween(
                        currentLat,
                        currentLong,
                        location.latitude,
                        location.longitude,
                        results
                    )
                    if(results.isNotEmpty()){
                        val distance = results[0];
                        Log.d(TAG, "Distance $distance")
                        if(distance > 10f){
                            Toast.makeText(applicationContext, "Replay Video", Toast.LENGTH_SHORT).show();
                            replayVideo()
                            // reset current location now
                            currentLat = 0.0
                            currentLong = 0.0
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient?.removeLocationUpdates(locationCallBack)
    }

    private fun hasLocationPermissions():Boolean {
        return EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)
    }


    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if(requestCode == RC_LOCATION_PERM){
            getUserCurrentLocation()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        AppSettingsDialog.Builder(this).build().show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            val yes = getString(R.string.yes)
            val no = getString(R.string.no)
            // Do something after user returned from app settings screen, like showing a Toast.
            Toast.makeText(
                this,
                getString(R.string.returned_from_app_settings_to_activity,
                    if (hasLocationPermissions()) yes else no),
                Toast.LENGTH_LONG)
                .show()
        }
    }

    /** Sensor class handling methods */

    private fun setUpSensorManager() {

        // Getting the Sensor Manager instance
        sensorListener = SensorListener(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager?.registerListener(sensorListener, sensorManager!!
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
    }

    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
        }
    }

    public override fun onResume() {
        sensorManager?.registerListener(sensorListener, sensorManager!!.getDefaultSensor(
            Sensor .TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL
        )
        super.onResume()
        hideSystemUi()


        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        sensorManager?.unregisterListener(sensorListener)
        super.onPause()

        if (Util.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            releasePlayer()
        }
    }

    private fun initializePlayer() {
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer

                val mediaItem = MediaItem.Builder()
                    .setUri(getString(R.string.media_url))
                    .setMimeType(MimeTypes.APPLICATION_MP4)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentItem, playbackPosition)
                exoPlayer.addListener(playbackStateListener)
                exoPlayer.prepare()

                exoPlayer.playWhenReady =  true
            }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentItem = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(playbackStateListener)
            exoPlayer.release()
        }
        player = null
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.videoView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onShake() {
        player?.pause()
    }

    fun replayVideo(){
        player?.let {
            it.seekTo(0)
            it.playWhenReady = true
        }
    }
}

private fun playbackStateListener() = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        val stateString: String = when (playbackState) {
            ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE      -"
            ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
            ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY     -"
            ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED     -"
            else -> "UNKNOWN_STATE             -"
        }
        Log.d(TAG, "changed state to $stateString")
    }
}