package com.example.sensorapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), SensorEventListener, OnMapReadyCallback {

    private lateinit var sensorManager: SensorManager
    private var temperatureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private lateinit var temperatureTextView: TextView
    private lateinit var humidityTextView: TextView
    private lateinit var weatherRecommendationTextView: TextView
    private lateinit var weatherImageView: ImageView
    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var addressTextView: TextView
    private lateinit var locationCardView: CardView
    private lateinit var sensorCardView: CardView
    private lateinit var fileOutputStream: FileOutputStream

    private lateinit var mMap: GoogleMap
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private var lastTemperatureValue: Float = 0f
    private var lastHumidityValue: Float = 0f

    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentMarker: Marker? = null
    private var shouldMoveCamera = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        temperatureTextView = findViewById(R.id.temperatureTextView)
        humidityTextView = findViewById(R.id.humidityTextView)
        weatherRecommendationTextView = findViewById(R.id.weatherRecommendationTextView)
        weatherImageView = findViewById(R.id.weatherImageView)
        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        addressTextView = findViewById(R.id.addressTextView)
        locationCardView = findViewById(R.id.locationCardView)
        sensorCardView = findViewById(R.id.sensorCardView)


        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Inisialisasi SensorManager untuk mengelola sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Mengecek dan mendapatkan sensor suhu
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (temperatureSensor == null) {
            temperatureTextView.text = "Temperature sensor not available"
        }

        // Mengecek dan mendapatkan sensor kelembapan
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        if (humiditySensor == null) {
            humidityTextView.text = "Humidity sensor not available"
        }

        // Registrasi listener untuk sensor suhu
        temperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Registrasi listener untuk sensor kelembapan
        humiditySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Tambahkan judul ke MainActivity
        title = "Sensor & Maps Activity"

        // Membuat file untuk menyimpan data
        createFile()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(1000) // Interval pembaruan lokasi: 1 detik

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (locationResult.lastLocation != null) {
                    currentLocation = locationResult.lastLocation
                    updateLocationInfo()
                    val currentLatLng =
                        LatLng(currentLocation!!.latitude, currentLocation!!.longitude)
                    // Hapus marker sebelumnya
                    mMap.clear()
                    // Menambah marker pada lokasi baru
                    mMap.addMarker(MarkerOptions().position(currentLatLng).title("Your Location"))
                    // Pindah kamera ke lokasi baru
                    if (shouldMoveCamera) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM))
                    }

                    shouldMoveCamera = true
                }
            }
        }
        startLocationUpdates()
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_LOCATION
            )
            return
        }

        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLocation = it
                updateLocationInfo()

                if (currentMarker == null) {
                    currentMarker = mMap.addMarker(
                        MarkerOptions().position(
                            LatLng(
                                it.latitude,
                                it.longitude
                            )
                        ).title("Your Location")
                    )
                } else {
                    currentMarker!!.position = LatLng(it.latitude, it.longitude)
                }
                mMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.latitude, it.longitude),
                        DEFAULT_ZOOM
                    )
                )
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    // Fungsi untuk mendapatkan alamat dari koordinat
    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)

            // Check if the list is not null and not empty
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val streetAddress = address.getAddressLine(0) // Nama jalan
                addressTextView.text = "$streetAddress"
            } else {
                // Handle the case where the list is null or empty
                addressTextView.text = "Alamat tidak ditemukan"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi untuk memperbarui tampilan TextView dengan lokasi saat ini
    private fun updateLocationInfo() {
        currentLocation?.let {
            val latitudeText = "Latitude: %.4f".format(it.latitude)
            val longitudeText = "Longitude: %.4f".format(it.longitude)
            latitudeTextView.text = latitudeText
            longitudeTextView.text = longitudeText
            // Ambil alamat berdasarkan koordinat
            getAddressFromLocation(it.latitude, it.longitude)
        }
    }

    private fun createFile() {
        val fileName = "data_sensor.txt"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        fileOutputStream = FileOutputStream(file, true)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Metode ini dipanggil jika akurasi sensor berubah
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = getCurrentTime()
        if (event.sensor == temperatureSensor) {
            // Mendapatkan data suhu
            val temperature = event.values[0]
            lastTemperatureValue = temperature
            val temperatureStatus = getStatusForTemperature(temperature)
            val humidityStatus = getStatusForHumidity(lastHumidityValue)
            temperatureTextView.text = "$temperature °C ($temperatureStatus)"
            // Menyimpan data ke file
            saveDataToFile(temperature,
                lastHumidityValue, latitude = currentLocation?.latitude ?: 0.0, longitude = currentLocation?.longitude ?: 0.0)
            // Memberikan rekomendasi cuaca
            provideWeatherRecommendation(temperature, lastHumidityValue)
        } else if (event.sensor == humiditySensor) {
            // Mendapatkan data kelembapan
            val humidity = event.values[0]
            lastHumidityValue = humidity
            val humidityStatus = getStatusForHumidity(humidity)
            val temperatureStatus = getStatusForTemperature(lastTemperatureValue)
            humidityTextView.text = "$humidity% ($humidityStatus)"
            // Menyimpan data ke file
            saveDataToFile(lastTemperatureValue,
                humidity, latitude = currentLocation?.latitude ?: 0.0, longitude = currentLocation?.longitude ?: 0.0)
            // Memberikan rekomendasi cuaca
            provideWeatherRecommendation(lastTemperatureValue, humidity)
        }
    }

    private fun getStatusForTemperature(temperature: Float): String {
        return when {
            temperature < 20.0 -> "Rendah"
            temperature in 20.0..25.0 -> "Normal"
            else -> "Tinggi"
        }
    }

    private fun getStatusForHumidity(humidity: Float): String {
        return when {
            humidity < 30.0 -> "Rendah"
            humidity in 30.0..60.0 -> "Normal"
            else -> "Tinggi"
        }
    }

    private fun provideWeatherRecommendation(temperature: Float, humidity: Float) {
        val weatherRecommendation: String
        val weatherIcon: Int // ID gambar cuaca
        when {
            temperature < 20.0 && humidity < 40.0 -> {
                weatherRecommendation =
                    "Hari dingin dan kering, gunakan pakaian hangat dan pelembap kulit."
                weatherIcon = R.drawable.snowy_weather
            }
            temperature < 20.0 && humidity >= 40.0 -> {
                weatherRecommendation =
                    "Hari sejuk dengan tingkat kelembapan cukup, kenakan pakaian hangat."
                weatherIcon = R.drawable.snowy_and_sunny
            }
            temperature >= 20.0 && temperature <= 30.0 && humidity >= 40.0 && humidity <= 60.0 -> {
                weatherRecommendation = "Cuaca sangat nyaman hari ini."
                weatherIcon = R.drawable.cloudy
            }
            temperature > 30.0 && humidity > 60.0 -> {
                weatherRecommendation =
                    "Hari panas dan lembap, hindari terlalu banyak aktivitas fisik dan tetap terhidrasi."
                weatherIcon = R.drawable.sunny_day
            }
            else -> {
                weatherRecommendation = "Cuaca sekitar normal, nikmati hari Anda!"
                weatherIcon = R.drawable.blue_cloud_and_sun
            }
        }
        // Tampilkan rekomendasi ke pengguna
        weatherRecommendationTextView.text = weatherRecommendation
        // Set gambar cuaca sesuai dengan kondisi
        weatherImageView.setImageResource(weatherIcon)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Mulai pemantauan lokasi saat aplikasi di-resume
        startLocationUpdates()
        // Registrasi listener untuk sensor suhu
        temperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // Registrasi listener untuk sensor kelembapan
        humiditySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        // Hentikan pemantauan lokasi saat aplikasi di-pause
        stopLocationUpdates()
        // Hentikan pemantauan sensor saat aplikasi di-pause
        sensorManager.unregisterListener(this)
        // Tutup fileOutputStream saat aplikasi di-pause
        fileOutputStream.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        // ... (kode lainnya tetap sama)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = Date()
        return dateFormat.format(currentTime)
    }

    private fun saveDataToFile(temperature: Float, humidity: Float, latitude: Double, longitude: Double) {
        val currentTime = getCurrentTime()
        val data = "$currentTime - Temperature: $temperature °C, Humidity: $humidity%, Latitude: $latitude, Longitude: $longitude\n"

        try {
            fileOutputStream.write(data.toByteArray())
            fileOutputStream.flush() // Pastikan data tersimpan
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val DEFAULT_ZOOM = 15.0f
        private const val PERMISSIONS_REQUEST_LOCATION = 1
    }
}


