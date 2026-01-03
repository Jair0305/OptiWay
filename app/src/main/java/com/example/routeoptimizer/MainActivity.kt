package com.example.routeoptimizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.routeoptimizer.data.local.PreferencesManager
import com.example.routeoptimizer.data.local.SavedRoute
import com.example.routeoptimizer.ui.screens.HistoryScreen
import com.example.routeoptimizer.ui.screens.MapScreen
import com.example.routeoptimizer.ui.screens.OnboardingScreen
import com.example.routeoptimizer.ui.theme.RouteOptimizerTheme
import com.example.routeoptimizer.ui.viewmodel.RouteViewModel
import com.example.routeoptimizer.util.ShareUtils
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var preferencesManager: PreferencesManager
    
    private var onLocationUpdate: ((LatLng) -> Unit)? = null
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                startLocationUpdates()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                startLocationUpdates()
            }
            else -> {
                // Permiso denegado
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        preferencesManager = PreferencesManager(this)
        
        setContent {
            RouteOptimizerTheme {
                RouteOptimizerApp(
                    preferencesManager = preferencesManager,
                    onLocationUpdate = { callback ->
                        onLocationUpdate = callback
                    },
                    checkLocationPermission = { checkLocationPermission() },
                    requestLocationPermission = { requestLocationPermission() },
                    startLocationUpdates = { 
                        if (checkLocationPermission()) {
                            startLocationUpdates()
                        }
                    }
                )
            }
        }
    }
    
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun startLocationUpdates() {
        if (!checkLocationPermission()) return
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).apply {
            setMinUpdateIntervalMillis(5000)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onLocationUpdate?.invoke(
                        LatLng(location.latitude, location.longitude)
                    )
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    onLocationUpdate?.invoke(LatLng(it.latitude, it.longitude))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (checkLocationPermission() && ::fusedLocationClient.isInitialized) {
            startLocationUpdates()
        }
    }
}

// ==================== NAVEGACIÓN DE LA APP ====================

enum class Screen {
    Onboarding,
    Permission,
    Map,
    History
}

@Composable
fun RouteOptimizerApp(
    preferencesManager: PreferencesManager,
    onLocationUpdate: ((LatLng) -> Unit) -> Unit,
    checkLocationPermission: () -> Boolean,
    requestLocationPermission: () -> Unit,
    startLocationUpdates: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: RouteViewModel = viewModel()
    
    // Estados
    var currentScreen by remember { mutableStateOf<Screen?>(null) }
    var hasLocationPermission by remember { mutableStateOf(checkLocationPermission()) }
    val routeHistory by preferencesManager.routeHistory.collectAsState(initial = emptyList())
    
    // Determinar pantalla inicial
    LaunchedEffect(Unit) {
        val hasOnboarded = preferencesManager.hasCompletedOnboarding.first()
        
        currentScreen = when {
            !hasOnboarded -> Screen.Onboarding
            !checkLocationPermission() -> Screen.Permission
            else -> Screen.Map
        }
        
        // Configurar callback de ubicación
        onLocationUpdate { latLng ->
            viewModel.updateCurrentLocation(latLng)
            // Guardar última ubicación
            scope.launch {
                preferencesManager.saveLastLocation(latLng)
            }
        }
        
        if (checkLocationPermission()) {
            startLocationUpdates()
        }
    }
    
    // Navegación
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn() + slideInHorizontally { it } togetherWith 
                fadeOut() + slideOutHorizontally { -it }
        },
        label = "navigation"
    ) { screen ->
        when (screen) {
            Screen.Onboarding -> {
                OnboardingScreen(
                    onComplete = {
                        scope.launch {
                            preferencesManager.setOnboardingCompleted()
                        }
                        currentScreen = if (hasLocationPermission) Screen.Map else Screen.Permission
                    }
                )
            }
            
            Screen.Permission -> {
                PermissionScreen(
                    onRequestPermission = {
                        requestLocationPermission()
                        hasLocationPermission = checkLocationPermission()
                        if (hasLocationPermission) {
                            startLocationUpdates()
                            currentScreen = Screen.Map
                        }
                    }
                )
            }
            
            Screen.Map -> {
                MapScreen(viewModel = viewModel)
                // TODO: Agregar FAB para abrir historial
            }
            
            Screen.History -> {
                HistoryScreen(
                    routes = routeHistory,
                    onRouteClick = { route ->
                        // Cargar ruta en el mapa
                        // TODO: Implementar carga de ruta desde historial
                        currentScreen = Screen.Map
                    },
                    onDeleteRoute = { route ->
                        scope.launch {
                            preferencesManager.deleteRoute(route.id)
                        }
                    },
                    onShareRoute = { route ->
                        ShareUtils.shareSavedRoute(
                            context = context,
                            route = route,
                            formatDuration = viewModel::formatDuration,
                            formatDistance = viewModel::formatDistance
                        )
                    },
                    onClearHistory = {
                        scope.launch {
                            preferencesManager.clearHistory()
                        }
                    },
                    onBack = { currentScreen = Screen.Map },
                    formatDuration = viewModel::formatDuration,
                    formatDistance = viewModel::formatDistance
                )
            }
            
            null -> {
                // Loading
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📍",
                style = MaterialTheme.typography.displayLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Permiso de Ubicación",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Esta app necesita acceso a tu ubicación para mostrar tu posición en el mapa y calcular rutas optimizadas.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Permitir Ubicación")
            }
        }
    }
}