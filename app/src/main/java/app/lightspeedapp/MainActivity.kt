package app.lightspeedapp

import android.util.Log
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role.Companion.Button
import androidx.compose.ui.semantics.Role.Companion.Switch
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.lightspeedapp.ui.theme.LightSpeedAppTheme
import java.io.IOException
import java.lang.Math.round
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

fun Double.roundTo(decimals: Int): Double {
    val multiplier = 10.0.pow(decimals)
    return round(this * multiplier) / multiplier
}



class MainActivity : ComponentActivity() {

    private lateinit var locationManager: LocationManager

    private lateinit var currentLocation: Location

    var app_latitude by mutableStateOf(0.0)
    var app_longitude by mutableStateOf(0.0)
    var distance by mutableStateOf(0.0)
    var speed by mutableStateOf(0.0)
    var acceleration by mutableStateOf(0.0)
    var last_noted_speed by mutableStateOf(0.0)
    var lastLocationTime by mutableStateOf(0.0.toLong())
    var chosenSpeed by mutableStateOf(0f)
    var music_file_name by mutableStateOf("none")
    private var musicFile by mutableStateOf<Uri?>(null)
    private var mediaPlayer: MediaPlayer? = null
    var time_without_chosen_speed by mutableStateOf(0.0.toLong())
    var music_being_played =false
    var allowPlayingMusic=false
    val context:Context=this

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            onFileSelected(it)
        }
    }

        private fun pickFile() {
                pickFileLauncher.launch("audio/*")
        }



    private fun onFileSelected(uri: Uri) {
        musicFile = uri
        music_file_name=musicFile.toString()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()

        mediaPlayer?.setOnPreparedListener {
            mediaPlayer?.start()
        }

        mediaPlayer?.setOnErrorListener { _, _, _ ->
            false
        }

        try {
            mediaPlayer?.setDataSource(this, musicFile!!)
            mediaPlayer?.prepareAsync()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopMusic() {
        mediaPlayer?.release()
    }

    private val locationListener: LocationListener = LocationListener { location ->
        currentLocation = location
        val latitude = location.latitude
        val longitude = location.longitude

        val dLat = Math.toRadians(latitude - app_latitude)
        val dLon = Math.toRadians(longitude - app_longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(app_latitude)) * cos(Math.toRadians(latitude)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val radius = 6371000.0

        distance = radius * c

        if (allowPlayingMusic)
        {
            if (speed>chosenSpeed && !music_being_played && musicFile!=null)
            {
                initializeMediaPlayer()
                music_being_played=true
                time_without_chosen_speed=0.0.toLong()
            }
            else if (speed<chosenSpeed && music_being_played) {
                time_without_chosen_speed += (location.time - lastLocationTime)
            }

            if (time_without_chosen_speed>5000)
            {
                music_being_played=false
                stopMusic()
                time_without_chosen_speed=0.0.toLong()
            }

        }
        else
        {
            music_being_played=false
            stopMusic()
            time_without_chosen_speed=0.0.toLong()
        }

        this.speed = distance / ((location.time - lastLocationTime))
        this.acceleration=(speed-last_noted_speed)/(location.time - lastLocationTime)
        this.speed=speed*3600
        this.app_latitude = latitude
        this.app_longitude = longitude
        this.lastLocationTime = location.time
        this.last_noted_speed=speed
    }


    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        handler = Handler(Looper.getMainLooper())

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            handler.postDelayed(object : Runnable {
                override fun run() {
                    getLocation()
                    handler.postDelayed(this, 1000)
                }
            }, 1000) }
        else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        setContent {
            LightSpeedAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    View(app_latitude,
                        app_longitude,
                        (speed).roundTo(2),
                        (distance).roundTo(2),
                        (acceleration/9.81).roundTo(2),
                        onChosenSpeedChange = { newSpeed -> chosenSpeed = newSpeed },
                        onSwitchChange ={ checked -> allowPlayingMusic = checked } ,
                        music_file_name,
                        {pickFile()}
                    )
                }
            }
        }

    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Request location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0f,
                locationListener
            )
        }
    }


    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 100
        const val STORAGE_PERMISSION_REQUEST_CODE = 123
    }


}


@Composable
fun View(latitude: Double ,
         longitude:Double ,
         speed: Double,diff: Double,
         acceleration: Double,
         onChosenSpeedChange: (Float) -> Unit,
         onSwitchChange: (Boolean) -> Unit,
         musicFileName:String,
        FilePick: () -> Unit,
         modifier: Modifier = Modifier)
{
    var sliderPosition by remember { mutableStateOf(0f) }
    var checked by remember { mutableStateOf(false) }

    Column()
    {
        Text(
            text = "Latitude: $latitude°",
            modifier = modifier
        )
        Text(
            text = "Latitude: $longitude°",
            modifier = modifier
        )
        Text(
            text = "Km/h: $speed",
            fontSize = 50.sp,
            modifier = modifier
        )
        Text(
            text = "Diff [m]: $diff",
            fontSize = 50.sp,
            modifier = modifier
        )
        Text(
            text = "Acceleration [g]: $acceleration",
            fontSize = 40.sp,
            modifier = modifier
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it.roundToInt().toFloat()
                onChosenSpeedChange(it)},
            valueRange = 0f..120f
        )
        Text(
            text ="Chosen Speed [Km/h]: $sliderPosition",
            fontSize = 25.sp
        )
        Button(
            onClick = {
                FilePick()

            }
        ) {
            Text(text = "Import file")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        )
        {
            Text(
                text ="Play music: ",
                fontSize = 25.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked=it
                    onSwitchChange(it)
                }
            )
        }


        Text(
            text ="Music file: $musicFileName",
            fontSize = 25.sp
        )
    }
}
