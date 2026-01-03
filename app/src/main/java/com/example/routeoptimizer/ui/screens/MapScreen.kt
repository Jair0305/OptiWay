package com.example.routeoptimizer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.routeoptimizer.data.model.FixedPosition
import com.example.routeoptimizer.data.model.RouteState
import com.example.routeoptimizer.data.model.TravelMode
import com.example.routeoptimizer.ui.components.*
import com.example.routeoptimizer.ui.viewmodel.RouteViewModel
import com.example.routeoptimizer.util.MarkerUtils
import com.example.routeoptimizer.util.ShareUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: RouteViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Estados
    val uiState by viewModel.uiState.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val routeAlternatives by viewModel.routeAlternatives.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val useCurrentLocation by viewModel.useCurrentLocation.collectAsState()
    val customStartPoint by viewModel.customStartPoint.collectAsState()
    val isSearchingForStart by viewModel.isSearchingForStart.collectAsState()
    val travelMode by viewModel.travelMode.collectAsState()
    val needsReoptimization by viewModel.needsReoptimization.collectAsState()
    val trafficSuggestion by viewModel.trafficSuggestion.collectAsState()
    
    var showPointsSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    
    val defaultLocation = LatLng(25.6866, -100.3161)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation ?: defaultLocation, 13f)
    }
    
    // Centrar en ubicación
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, 14f),
                durationMs = 1000
            )
        }
    }
    
    // MANEJO DEL BOTÓN ATRÁS - Intuitivo
    BackHandler(enabled = true) {
        when {
            showSearch -> {
                showSearch = false
                viewModel.clearSearch()
            }
            showPointsSheet -> showPointsSheet = false
            routeAlternatives != null -> viewModel.clearOptimizedRoute()
            routePoints.isNotEmpty() -> viewModel.clearAllPoints()
            else -> (context as? androidx.activity.ComponentActivity)?.finish()
        }
    }
    
    val mapProperties by remember {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = true,
                mapType = MapType.NORMAL
            )
        )
    }
    
    val mapUiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = true,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true,
                tiltGesturesEnabled = true,
                rotationGesturesEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = true
            )
        )
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState) {
        if (uiState is RouteState.Error) {
            snackbarHostState.showSnackbar(
                (uiState as RouteState.Error).message,
                duration = SnackbarDuration.Short
            )
        }
    }
    
    LaunchedEffect(isSearchingForStart) {
        if (isSearchingForStart) showSearch = true
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            
            // ==================== MAPA ====================
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                onMapClick = { latLng ->
                    // Agregar punto al tocar mapa (útil y rápido)
                    if (!showSearch) {
                        viewModel.addPointFromMap(latLng)
                    }
                }
            ) {
                // ORIGEN - Siempre visible
                val originLocation = if (useCurrentLocation) {
                    currentLocation
                } else {
                    customStartPoint?.latLng
                }
                
                originLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = if (useCurrentLocation) "📍 Tu ubicación" else "📍 ${customStartPoint?.name}",
                        snippet = "Punto de partida",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
                    )
                }
                
                // PARADAS - Arrastrables (solo cuando NO hay ruta optimizada)
                if (routeAlternatives == null) {
                    routePoints.forEachIndexed { idx, point ->
                        val markerState = rememberMarkerState(position = point.latLng)
                        
                        // Actualizar posición cuando se arrastra
                        LaunchedEffect(markerState.position) {
                            if (markerState.position != point.latLng) {
                                viewModel.updatePointLocation(point.id, markerState.position)
                            }
                        }
                        
                        // Usar marcadores numerados también antes de optimizar
                        val markerColor = when (point.fixedPosition) {
                            FixedPosition.FIRST -> MarkerUtils.Colors.FirstStop
                            FixedPosition.LAST -> MarkerUtils.Colors.LastStop
                            FixedPosition.FIXED -> MarkerUtils.Colors.FixedStop
                            else -> MarkerUtils.Colors.NormalStop
                        }
                        
                        Marker(
                            state = markerState,
                            title = "${idx + 1}. ${point.name}",
                            snippet = "Mantén presionado para mover",
                            draggable = true,
                            icon = MarkerUtils.createNumberedMarker(
                                context = context,
                                number = idx + 1,
                                backgroundColor = markerColor
                            )
                        )
                    }
                }
                
                // PREVIEW LINE - Muestra el orden actual ANTES de optimizar
                if (routeAlternatives == null) {
                    val origin = if (useCurrentLocation) currentLocation else customStartPoint?.latLng
                    
                    if (origin != null && routePoints.isNotEmpty()) {
                        val previewPoints = listOf(origin) + routePoints.map { it.latLng }
                        Polyline(
                            points = previewPoints,
                            color = Color.Gray.copy(alpha = 0.5f), // Gris sutil
                            width = 5f, // Línea fina
                            pattern = listOf(
                                com.google.android.gms.maps.model.Dash(10f),
                                com.google.android.gms.maps.model.Gap(10f)
                            )
                        )
                    }
                }

                // POLYLINE - Ruta optimizada
                routeAlternatives?.selectedRoute?.let { route ->
                    val pathPoints = if (route.polylinePoints.isNotEmpty()) {
                        route.polylinePoints // Modo DRIVING
                    } else {
                        route.orderedPoints.map { it.latLng } // Modo DIRECT
                    }
                    
                    Polyline(
                        points = pathPoints,
                        color = if (travelMode == TravelMode.DRIVING) 
                            Color(0xFF4CAF50) else Color(0xFF2196F3),
                        width = 10f
                    )
                    
                    // MARCADORES NUMERADOS para la ruta optimizada
                    route.orderedPoints.forEachIndexed { index, point ->
                        val isFirst = index == 0
                        val isLast = index == route.orderedPoints.lastIndex
                        
                        // Color según posición
                        val markerColor = when {
                            isFirst -> MarkerUtils.Colors.Origin
                            isLast -> MarkerUtils.Colors.LastStop
                            else -> MarkerUtils.Colors.NormalStop
                        }
                        
                        // Crear marcador con número
                        val icon = if (isFirst) {
                            MarkerUtils.createOriginMarker(context)
                        } else {
                            MarkerUtils.createNumberedMarker(
                                context = context,
                                number = index,
                                backgroundColor = markerColor
                            )
                        }
                        
                        Marker(
                            state = MarkerState(position = point.latLng),
                            title = if (isFirst) "Inicio" else if (isLast) "Destino final" else "Parada $index",
                            snippet = point.name,
                            icon = icon
                        )
                    }
                }
            }
            
            // ==================== BARRA DE BÚSQUEDA ====================
            AnimatedVisibility(
                visible = showSearch,
                enter = slideInVertically(tween(300)) { -it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
            ) {
                Column {
                    if (isSearchingForStart) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TripOrigin, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Buscando punto de partida",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onClear = {
                            viewModel.clearSearch()
                            showSearch = false
                        },
                        isSearching = isSearching,
                        placeholder = if (isSearchingForStart) 
                            "Buscar punto de partida..." else "Agregar parada..."
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    SearchResultsList(
                        results = searchResults,
                        onResultClick = { result ->
                            viewModel.selectSearchResult(result)
                            showSearch = false  
                        }
                    )
                }
            }
            
            // ==================== FABs ====================
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = if (showSearch) 120.dp else 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Buscar
                FloatingActionButton(
                    onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.clearSearch()
                    },
                    containerColor = if (showSearch) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (showSearch) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Mi ubicación
                FloatingActionButton(
                    onClick = {
                        currentLocation?.let { loc ->
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(loc, 15f),
                                    durationMs = 500
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.MyLocation, null, Modifier.size(24.dp))
                }
                
                // Lista de paradas
                if (routePoints.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showPointsSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${routePoints.size}")
                                }
                            }
                        ) {
                            Icon(Icons.Default.List, null, Modifier.size(24.dp))
                        }
                    }
                }
            }
            
            // ==================== PANEL INFERIOR ====================
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                
                // BANNER DE RE-OPTIMIZACIÓN (cuando se agregan puntos a ruta activa)
                AnimatedVisibility(
                    visible = needsReoptimization,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    ReoptimizationBanner(
                        onConfirm = { viewModel.confirmReoptimization() },
                        onDismiss = { viewModel.dismissReoptimization() },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                // ANIMACIÓN DE CARGA PREMIUM
                AnimatedVisibility(
                    visible = uiState is RouteState.Optimizing,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    OptimizingAnimation()
                }
                
                if (routeAlternatives != null && uiState !is RouteState.Optimizing) {
                    // RUTAS OPTIMIZADAS
                    Surface(
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(Modifier.padding(top = 8.dp)) {
                            // Tarjeta de ahorro
                            val bestRoute = routeAlternatives!!.bestRoute
                            if (bestRoute.savingsVsOriginalSeconds > 0 || bestRoute.savingsVsOriginalMeters > 0) {
                                SavingsCard(
                                    savingsSeconds = bestRoute.savingsVsOriginalSeconds,
                                    savingsMeters = bestRoute.savingsVsOriginalMeters,
                                    formatDuration = viewModel::formatDuration,
                                    formatDistance = viewModel::formatDistance,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            
                            RouteAlternativesCard(
                                alternatives = routeAlternatives!!,
                                selectedIndex = routeAlternatives!!.selectedIndex,
                                travelMode = travelMode,
                                onSelectRoute = viewModel::selectRoute,
                                onNavigate = { viewModel.startNavigation(context) },
                                onShare = {
                                    ShareUtils.shareRoute(
                                        context = context,
                                        route = routeAlternatives!!.selectedRoute,
                                        formatDuration = viewModel::formatDuration,
                                        formatDistance = viewModel::formatDistance
                                    )
                                },
                                onShowAll = { viewModel.generateAllRoutes() },
                                onToggleMode = {
                                    // Solo cambiar modo, el ViewModel se encarga del resto
                                    val newMode = if (travelMode == TravelMode.DRIVING) 
                                        TravelMode.DIRECT else TravelMode.DRIVING
                                    viewModel.setTravelMode(newMode)
                                },
                                formatDuration = viewModel::formatDuration,
                                formatDistance = viewModel::formatDistance
                            )
                        }
                    }
                } else if (routePoints.isEmpty() && uiState !is RouteState.Optimizing) {
                    // SIN PARADAS
                    InstructionsCard()
                } else if (routePoints.isNotEmpty() && uiState !is RouteState.Optimizing && routeAlternatives == null) {
                    // CON PARADAS, SIN OPTIMIZAR
                    Surface(
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Punto de partida
                            StartPointSelector(
                                useCurrentLocation = useCurrentLocation,
                                customStartPoint = customStartPoint,
                                onToggleCurrentLocation = viewModel::setUseCurrentLocation,
                                onSearchClick = {
                                    viewModel.startSearchingForStartPoint()
                                    showSearch = true
                                }
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            
                            // Info de paradas
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "${routePoints.size} parada${if (routePoints.size > 1) "s" else ""}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Toca el mapa o busca para agregar más",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { showPointsSheet = true }) {
                                    Text("Editar")
                                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Botones de acción
                            ActionButtons(
                                pointCount = routePoints.size,
                                isOptimizing = uiState is RouteState.Optimizing,
                                hasOptimizedRoute = false,
                                onOptimize = {
                                    if (useCurrentLocation && currentLocation == null) {
                                        viewModel.updateCurrentLocation(defaultLocation)
                                    }
                                    viewModel.optimizeRoute()
                                },
                                onClear = viewModel::clearAllPoints
                            )
                        }
                    }
                }
            }
            
            // ==================== MODAL PARADAS ====================
            if (showPointsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showPointsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                        // Header
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${routePoints.size} paradas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Mantén presionado ⋮⋮ para reordenar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showPointsSheet = false }) {
                                Icon(Icons.Default.Close, "Cerrar")
                            }
                        }
                        
                        HorizontalDivider()
                        
                        // Punto de partida
                        StartPointSelector(
                            useCurrentLocation = useCurrentLocation,
                            customStartPoint = customStartPoint,
                            onToggleCurrentLocation = viewModel::setUseCurrentLocation,
                            onSearchClick = {
                                viewModel.startSearchingForStartPoint()
                                showPointsSheet = false
                                showSearch = true
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        
                        HorizontalDivider()
                        
                        // Lista de paradas
                        RoutePointsListDraggable(
                            points = routePoints,
                            onRemovePoint = viewModel::removePoint,
                            onReorder = viewModel::reorderPoints,
                            onSetFirst = viewModel::setPointAsFirst,
                            onSetLast = viewModel::setPointAsLast,
                            onSetFixed = viewModel::setPointAsFixed,
                            onSetFlexible = viewModel::setPointAsFlexible,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .heightIn(max = 350.dp)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Botones de acción
                        ActionButtons(
                            pointCount = routePoints.size,
                            isOptimizing = uiState is RouteState.Optimizing,
                            hasOptimizedRoute = routeAlternatives != null,
                            onOptimize = {
                                if (useCurrentLocation && currentLocation == null) {
                                    viewModel.updateCurrentLocation(defaultLocation)
                                }
                                viewModel.optimizeRoute()
                                showPointsSheet = false
                            },
                            onClear = viewModel::clearAllPoints,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
