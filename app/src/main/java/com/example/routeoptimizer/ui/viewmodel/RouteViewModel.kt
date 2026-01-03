package com.example.routeoptimizer.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routeoptimizer.BuildConfig
import com.example.routeoptimizer.data.api.RetrofitInstance
import com.example.routeoptimizer.data.model.*
import com.example.routeoptimizer.data.repository.RouteRepository
import com.example.routeoptimizer.data.sync.RouteStateManager
import com.example.routeoptimizer.domain.RouteOptimizer
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RouteViewModel : ViewModel() {
    
    private val repository = RouteRepository()
    private val optimizer = RouteOptimizer()
    private val placesService = RetrofitInstance.placesService
    private val directionsService = RetrofitInstance.directionsService
    
    // Estado de la UI
    private val _uiState = MutableStateFlow<RouteState>(RouteState.Idle)
    val uiState: StateFlow<RouteState> = _uiState.asStateFlow()
    
    // Ubicación actual del usuario
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()
    
    // Puntos de la ruta
    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()
    
    // Rutas alternativas
    private val _routeAlternatives = MutableStateFlow<RouteAlternatives?>(null)
    val routeAlternatives: StateFlow<RouteAlternatives?> = _routeAlternatives.asStateFlow()
    
    // Ruta seleccionada (para compatibilidad)
    val optimizedRoute: StateFlow<OptimizedRoute?> 
        get() = MutableStateFlow(_routeAlternatives.value?.selectedRoute).asStateFlow()
    
    // Búsqueda de lugares
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<PlaceSearchResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceSearchResult>> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private var searchJob: Job? = null
    
    // Mostrar todas las rutas
    private val _showAllRoutes = MutableStateFlow(false)
    val showAllRoutes: StateFlow<Boolean> = _showAllRoutes.asStateFlow()
    
    // Modo de viaje
    private val _travelMode = MutableStateFlow(TravelMode.DRIVING)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()
    
    // Punto de partida personalizado
    private val _useCurrentLocation = MutableStateFlow(true)
    val useCurrentLocation: StateFlow<Boolean> = _useCurrentLocation.asStateFlow()
    
    private val _customStartPoint = MutableStateFlow<RoutePoint?>(null)
    val customStartPoint: StateFlow<RoutePoint?> = _customStartPoint.asStateFlow()
    
    // Modo de búsqueda (para paradas o para punto de inicio)
    private val _isSearchingForStart = MutableStateFlow(false)
    val isSearchingForStart: StateFlow<Boolean> = _isSearchingForStart.asStateFlow()
    
    // Flag para sugerir re-optimización cuando se agregan/quitan puntos a ruta activa
    private val _needsReoptimization = MutableStateFlow(false)
    val needsReoptimization: StateFlow<Boolean> = _needsReoptimization.asStateFlow()
    
    // Sugerencia de mejor ruta por tráfico
    private val _trafficSuggestion = MutableStateFlow<TrafficSuggestion?>(null)
    val trafficSuggestion: StateFlow<TrafficSuggestion?> = _trafficSuggestion.asStateFlow()
    
    // ==================== UBICACIÓN ====================
    
    fun updateCurrentLocation(latLng: LatLng) {
        _currentLocation.value = latLng
    }
    
    fun setTravelMode(mode: TravelMode) {
        _travelMode.value = mode
        // Actualizar polylines sin regenerar rutas
        _routeAlternatives.value?.let { alternatives ->
            viewModelScope.launch {
                if (mode == TravelMode.DIRECT) {
                    // Modo directo: limpiar polylines (usar líneas rectas)
                    val updatedRoutes = alternatives.routes.map { it.copy(polylinePoints = emptyList()) }
                    _routeAlternatives.value = alternatives.copy(routes = updatedRoutes)
                } else {
                    // Modo carro: obtener polyline solo de la ruta seleccionada
                    val updatedRoutes = alternatives.routes.mapIndexed { idx, route ->
                        if (idx == alternatives.selectedIndex && route.polylinePoints.isEmpty()) {
                            val polyline = getRoutePolyline(route)
                            route.copy(polylinePoints = polyline)
                        } else {
                            route
                        }
                    }
                    _routeAlternatives.value = alternatives.copy(routes = updatedRoutes)
                }
            }
        }
    }
    
    fun setUseCurrentLocation(use: Boolean) {
        _useCurrentLocation.value = use
        if (use) {
            // Al volver a ubicación actual, limpiar búsqueda de inicio
            _customStartPoint.value = null
            _isSearchingForStart.value = false
        }
        _routeAlternatives.value = null
    }
    
    fun setCustomStartPoint(point: RoutePoint) {
        _customStartPoint.value = point
        _useCurrentLocation.value = false
        _routeAlternatives.value = null
    }
    
    fun startSearchingForStartPoint() {
        _isSearchingForStart.value = true
    }
    
    fun stopSearchingForStartPoint() {
        _isSearchingForStart.value = false
    }
    
    // ==================== GESTIÓN DE PUNTOS ====================
    
    fun addPoint(name: String, address: String, latLng: LatLng, fixedPosition: FixedPosition = FixedPosition.NONE) {
        val point = RoutePoint(
            name = name,
            address = address,
            latLng = latLng,
            order = _routePoints.value.size,
            fixedPosition = fixedPosition
        )
        _routePoints.value = _routePoints.value + point
        
        // Si hay ruta activa, solo mostrar sugerencia de re-optimizar
        if (_routeAlternatives.value != null) {
            _needsReoptimization.value = true
        } else {
            _uiState.value = RouteState.AddingPoints
        }
        
        RouteStateManager.updateRoutePoints(_routePoints.value)
    }
    
    fun addPointFromMap(latLng: LatLng, address: String = "Punto ${_routePoints.value.size + 1}") {
        addPoint(
            name = "Parada ${_routePoints.value.size + 1}",
            address = address,
            latLng = latLng
        )
    }
    
    /**
     * Agrega un punto a la ruta activa y re-optimiza automáticamente
     */
    fun addPointAndReoptimize(name: String, address: String, latLng: LatLng) {
        // Agregar punto sin limpiar alternativas todavía
        val point = RoutePoint(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            address = address,
            latLng = latLng,
            order = _routePoints.value.size
        )
        _routePoints.value = _routePoints.value + point
        RouteStateManager.updateRoutePoints(_routePoints.value)
        
        // Re-optimizar
        optimizeRoute()
    }
    
    /**
     * Descarta los cambios pendientes y vuelve a la ruta original
     */
    fun dismissReoptimization() {
        _needsReoptimization.value = false
    }
    
    /**
     * Acepta re-optimizar la ruta con los nuevos puntos
     */
    fun confirmReoptimization() {
        _needsReoptimization.value = false
        optimizeRoute()
    }
    
    fun removePoint(point: RoutePoint) {
        _routePoints.value = _routePoints.value.filter { it.id != point.id }
        
        // Si hay ruta activa, sugerir re-optimizar
        if (_routeAlternatives.value != null && _routePoints.value.isNotEmpty()) {
            _needsReoptimization.value = true
        } else {
            _routeAlternatives.value = null
            if (_routePoints.value.isEmpty()) {
                _uiState.value = RouteState.Idle
            }
        }
        
        RouteStateManager.updateRoutePoints(_routePoints.value)
    }
    
    // Actualiza la ubicación de un punto cuando se arrastra en el mapa
    fun updatePointLocation(pointId: String, newLatLng: LatLng) {
        _routePoints.value = _routePoints.value.map { point ->
            if (point.id == pointId) {
                point.copy(latLng = newLatLng)
            } else {
                point
            }
        }
        _routeAlternatives.value = null
        RouteStateManager.updateRoutePoints(_routePoints.value)
    }
    
    fun clearAllPoints() {
        _routePoints.value = emptyList()
        _routeAlternatives.value = null
        _uiState.value = RouteState.Idle
        RouteStateManager.updateRoutePoints(emptyList())
    }
    
    fun clearOptimizedRoute() {
        _routeAlternatives.value = null
        _uiState.value = RouteState.AddingPoints
    }
    
    // ==================== POSICIONES FIJAS ====================
    
    fun setPointAsFirst(point: RoutePoint) {
        updatePointFixedPosition(point, FixedPosition.FIRST)
    }
    
    fun setPointAsLast(point: RoutePoint) {
        updatePointFixedPosition(point, FixedPosition.LAST)
    }
    
    fun setPointAsFixed(point: RoutePoint) {
        updatePointFixedPosition(point, FixedPosition.FIXED)
    }
    
    fun setPointAsFlexible(point: RoutePoint) {
        updatePointFixedPosition(point, FixedPosition.NONE)
    }
    
    private fun updatePointFixedPosition(point: RoutePoint, position: FixedPosition) {
        _routePoints.value = _routePoints.value.map {
            if (it.id == point.id) {
                it.copy(fixedPosition = position)
            } else {
                // Si es FIRST o LAST, quitar esa posición de otros puntos
                when (position) {
                    FixedPosition.FIRST -> if (it.fixedPosition == FixedPosition.FIRST) it.copy(fixedPosition = FixedPosition.NONE) else it
                    FixedPosition.LAST -> if (it.fixedPosition == FixedPosition.LAST) it.copy(fixedPosition = FixedPosition.NONE) else it
                    else -> it
                }
            }
        }
        _routeAlternatives.value = null
        RouteStateManager.updateRoutePoints(_routePoints.value)
    }
    
    // ==================== REORDENAR ====================
    
    fun movePointUp(point: RoutePoint) {
        val currentList = _routePoints.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == point.id }
        if (index > 0) {
            val temp = currentList[index - 1]
            currentList[index - 1] = currentList[index].copy(order = index - 1)
            currentList[index] = temp.copy(order = index)
            _routePoints.value = currentList
            _routeAlternatives.value = null
            RouteStateManager.updateRoutePoints(_routePoints.value)
        }
    }
    
    fun movePointDown(point: RoutePoint) {
        val currentList = _routePoints.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == point.id }
        if (index < currentList.size - 1) {
            val temp = currentList[index + 1]
            currentList[index + 1] = currentList[index].copy(order = index + 1)
            currentList[index] = temp.copy(order = index)
            _routePoints.value = currentList
            _routeAlternatives.value = null
            RouteStateManager.updateRoutePoints(_routePoints.value)
        }
    }
    
    fun reorderPoints(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        
        val currentList = _routePoints.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val movingPoint = currentList[fromIndex]
        val targetPoint = currentList[toIndex]
        
        // NO permitir mover puntos que están fijos
        if (movingPoint.fixedPosition != FixedPosition.NONE) return
        
        // NO permitir mover a una posición donde hay un punto fijo
        if (targetPoint.fixedPosition != FixedPosition.NONE) return
        
        // Verificar que no estamos cruzando un punto FIXED
        val minIdx = minOf(fromIndex, toIndex)
        val maxIdx = maxOf(fromIndex, toIndex)
        for (i in minIdx..maxIdx) {
            if (i != fromIndex && currentList[i].fixedPosition == FixedPosition.FIXED) {
                // Hay un punto fijo en el camino, no permitir
                return
            }
        }
        
        // Realizar el movimiento
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        
        _routePoints.value = currentList.mapIndexed { index, point -> 
            point.copy(order = index) 
        }
        _routeAlternatives.value = null
        RouteStateManager.updateRoutePoints(_routePoints.value)
    }
    
    // Verifica si un punto puede moverse
    fun canMovePoint(point: RoutePoint): Boolean {
        return point.fixedPosition == FixedPosition.NONE
    }
    
    // ==================== BÚSQUEDA DE LUGARES ====================
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        
        searchJob?.cancel()
        
        if (query.length < 3) {
            _searchResults.value = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            searchPlaces(query)
        }
    }
    
    private suspend fun searchPlaces(query: String) {
        _isSearching.value = true
        try {
            val locationParam = _currentLocation.value?.let {
                "${it.latitude},${it.longitude}"
            }
            
            val response = placesService.autocomplete(
                input = query,
                apiKey = BuildConfig.MAPS_API_KEY,
                location = locationParam
            )
            
            if (response.status == "OK") {
                _searchResults.value = response.predictions.map { prediction ->
                    PlaceSearchResult(
                        placeId = prediction.place_id,
                        name = prediction.structured_formatting?.main_text ?: prediction.description,
                        address = prediction.structured_formatting?.secondary_text ?: prediction.description
                    )
                }
            } else {
                _searchResults.value = emptyList()
            }
        } catch (e: Exception) {
            _searchResults.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }
    
    fun selectSearchResult(result: PlaceSearchResult) {
        viewModelScope.launch {
            try {
                val details = placesService.getPlaceDetails(
                    placeId = result.placeId,
                    apiKey = BuildConfig.MAPS_API_KEY
                )
                
                if (details.status == "OK" && details.result?.geometry != null) {
                    val location = details.result.geometry.location
                    val latLng = LatLng(location.lat, location.lng)
                    
                    val point = RoutePoint(
                        name = details.result.name ?: result.name,
                        address = details.result.formatted_address ?: result.address,
                        latLng = latLng
                    )
                    
                    // Si estamos buscando punto de inicio, establecerlo como tal
                    if (_isSearchingForStart.value) {
                        setCustomStartPoint(point)
                        stopSearchingForStartPoint()
                    } else {
                        addPoint(point.name, point.address, point.latLng)
                    }
                    
                    clearSearch()
                }
            } catch (e: Exception) {
                _uiState.value = RouteState.Error("Error al obtener detalles del lugar: ${e.message}")
            }
        }
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchingForStart.value = false
    }
    
    // ==================== OPTIMIZACIÓN ====================
    
    fun optimizeRoute() {
        // Determinar punto de partida
        val startLocation = if (_useCurrentLocation.value) {
            _currentLocation.value
        } else {
            _customStartPoint.value?.latLng
        }
        
        if (startLocation == null) {
            _uiState.value = RouteState.Error(
                if (_useCurrentLocation.value) "No se pudo obtener tu ubicación actual"
                else "Selecciona un punto de partida"
            )
            return
        }
        
        val points = _routePoints.value
        if (points.isEmpty()) {
            _uiState.value = RouteState.Error("Agrega al menos un punto a la ruta")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = RouteState.Optimizing
            
            try {
                val startPoint = if (_useCurrentLocation.value) {
                    RoutePoint(
                        id = "start",
                        name = "Tu ubicación",
                        address = "Ubicación actual",
                        latLng = startLocation,
                        order = 0
                    )
                } else {
                    _customStartPoint.value!!.copy(id = "start", order = 0)
                }
                
                val allPoints = listOf(startPoint) + points
                
                val response = repository.getDistanceMatrix(
                    points = allPoints,
                    apiKey = BuildConfig.MAPS_API_KEY
                )
                
                if (response.status != "OK") {
                    _uiState.value = RouteState.Error("Error de API: ${response.status}")
                    return@launch
                }
                
                val (timeMatrix, distanceMatrix) = repository.parseMatrixResponse(
                    response = response,
                    pointCount = allPoints.size
                )
                
                // Generar 3 alternativas
                val alternatives = optimizer.optimizeWithAlternatives(
                    startPoint = startPoint,
                    points = points,
                    timeMatrix = timeMatrix,
                    distanceMatrix = distanceMatrix,
                    alternativesCount = 3
                )
                
                // Obtener polyline real solo si está en modo DRIVING
                val routesWithPolyline = if (_travelMode.value == TravelMode.DRIVING) {
                    alternatives.routes.mapIndexed { idx, route ->
                        if (idx == 0) { // Solo para la mejor ruta (para no hacer muchas llamadas API)
                            val polyline = getRoutePolyline(route)
                            route.copy(polylinePoints = polyline)
                        } else {
                            route
                        }
                    }
                } else {
                    // Modo DIRECT: sin polyline, usar líneas rectas
                    alternatives.routes
                }
                
                val alternativesWithPolyline = alternatives.copy(routes = routesWithPolyline)
                
                _routeAlternatives.value = alternativesWithPolyline
                _uiState.value = RouteState.Optimized(alternativesWithPolyline)
                
                RouteStateManager.setOptimizedRoute(alternativesWithPolyline.bestRoute)
                
            } catch (e: Exception) {
                _uiState.value = RouteState.Error("Error: ${e.message}")
            }
        }
    }
    
    // Obtiene la polyline real de una ruta usando Directions API
    private suspend fun getRoutePolyline(route: OptimizedRoute): List<LatLng> {
        if (route.orderedPoints.size < 2) return emptyList()
        
        try {
            val origin = route.orderedPoints.first().let { "${it.latLng.latitude},${it.latLng.longitude}" }
            val destination = route.orderedPoints.last().let { "${it.latLng.latitude},${it.latLng.longitude}" }
            
            val waypoints = if (route.orderedPoints.size > 2) {
                route.orderedPoints.drop(1).dropLast(1).joinToString("|") { 
                    "${it.latLng.latitude},${it.latLng.longitude}" 
                }
            } else null
            
            val response = directionsService.getDirections(
                origin = origin,
                destination = destination,
                waypoints = waypoints ?: "",
                apiKey = BuildConfig.MAPS_API_KEY
            )
            
            if (response.status == "OK" && response.routes.isNotEmpty()) {
                val encodedPolyline = response.routes.first().overview_polyline.points
                return decodePolyline(encodedPolyline)
            }
        } catch (e: Exception) {
            // Si falla, usamos líneas rectas
        }
        
        return emptyList()
    }
    
    // Decodifica el polyline codificado de Google
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
    
    fun generateAllRoutes() {
        val currentLoc = _currentLocation.value ?: return
        val points = _routePoints.value
        if (points.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = RouteState.Optimizing
            
            try {
                val startPoint = RoutePoint(
                    id = "start",
                    name = "Tu ubicación",
                    address = "Ubicación actual",
                    latLng = currentLoc,
                    order = 0
                )
                
                val allPoints = listOf(startPoint) + points
                
                val response = repository.getDistanceMatrix(
                    points = allPoints,
                    apiKey = BuildConfig.MAPS_API_KEY
                )
                
                if (response.status != "OK") {
                    _uiState.value = RouteState.Error("Error de API: ${response.status}")
                    return@launch
                }
                
                val (timeMatrix, distanceMatrix) = repository.parseMatrixResponse(
                    response = response,
                    pointCount = allPoints.size
                )
                
                val alternatives = optimizer.generateAllRoutes(
                    startPoint = startPoint,
                    points = points,
                    timeMatrix = timeMatrix,
                    distanceMatrix = distanceMatrix,
                    maxRoutes = 20
                )
                
                _routeAlternatives.value = alternatives
                _showAllRoutes.value = true
                _uiState.value = RouteState.Optimized(alternatives)
                
            } catch (e: Exception) {
                _uiState.value = RouteState.Error("Error: ${e.message}")
            }
        }
    }
    
    fun selectRoute(index: Int) {
        val currentAlternatives = _routeAlternatives.value ?: return
        
        // Si estamos en modo DRIVING y la ruta no tiene polyline, hay que cargarla
        if (_travelMode.value == TravelMode.DRIVING) {
            val selectedRoute = currentAlternatives.routes.getOrNull(index)
            if (selectedRoute != null && selectedRoute.polylinePoints.isEmpty()) {
                viewModelScope.launch {
                    val polyline = getRoutePolyline(selectedRoute)
                    val updatedRoute = selectedRoute.copy(polylinePoints = polyline)
                    
                    val updatedRoutes = currentAlternatives.routes.mapIndexed { idx, route ->
                        if (idx == index) updatedRoute else route
                    }
                    
                    _routeAlternatives.value = currentAlternatives.copy(
                        routes = updatedRoutes,
                        selectedIndex = index
                    )
                }
                // Actualizar selección inmediatamente (aunque la polyline tarde un poco)
                _routeAlternatives.value = currentAlternatives.copy(selectedIndex = index)
                return
            }
        }
        
        // Si no hay que cargar nada, solo actualizar índice
        _routeAlternatives.value = currentAlternatives.copy(selectedIndex = index)
        
        // Actualizar estado persistente
        currentAlternatives.routes.getOrNull(index)?.let { 
            RouteStateManager.setOptimizedRoute(it) 
        }
    }
    
    fun toggleShowAllRoutes() {
        _showAllRoutes.value = !_showAllRoutes.value
    }
    
    // ==================== NAVEGACIÓN ====================
    
    fun startNavigation(context: Context) {
        RouteStateManager.startNavigation()
        launchNavigationOptimized(context)
    }
    
    fun launchNavigation(context: Context) {
        val route = _routeAlternatives.value?.selectedRoute ?: return
        
        val orderedPoints = route.orderedPoints.drop(1)
        if (orderedPoints.isEmpty()) return
        
        val destination = orderedPoints.last()
        val waypoints = orderedPoints.dropLast(1)
        
        val waypointsString = if (waypoints.isNotEmpty()) {
            "&waypoints=" + waypoints.joinToString("|") { 
                "${it.latLng.latitude},${it.latLng.longitude}" 
            }
        } else ""
        
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
            "&destination=${destination.latLng.latitude},${destination.latLng.longitude}" +
            waypointsString +
            "&travelmode=driving" +
            "&dir_action=navigate"
        )
        
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        
        context.startActivity(intent)
    }
    
    fun launchNavigationOptimized(context: Context) {
        val route = _routeAlternatives.value?.selectedRoute ?: return
        
        val orderedPoints = route.orderedPoints.drop(1)
        if (orderedPoints.isEmpty()) return
        
        val destination = orderedPoints.last()
        val waypoints = orderedPoints.dropLast(1)
        
        if (waypoints.isNotEmpty()) {
            val uri = Uri.parse(
                "https://www.google.com/maps/dir/" +
                "${_currentLocation.value?.latitude},${_currentLocation.value?.longitude}/" +
                waypoints.joinToString("/") { "${it.latLng.latitude},${it.latLng.longitude}" } +
                "/${destination.latLng.latitude},${destination.latLng.longitude}" +
                "/@?dirflg=d"
            )
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            
            context.startActivity(intent)
        } else {
            val uri = Uri.parse("google.navigation:q=${destination.latLng.latitude},${destination.latLng.longitude}&mode=d")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
        }
    }
    
    // ==================== FORMATEO ====================
    
    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}min"
            else -> "${minutes} min"
        }
    }
    
    fun formatDistance(meters: Int): String {
        return when {
            meters >= 1000 -> String.format("%.1f km", meters / 1000.0)
            else -> "$meters m"
        }
    }
    
    // ==================== MONITOREO DE TRÁFICO ====================
    
    private var trafficMonitorJob: Job? = null
    
    /**
     * Inicia monitoreo periódico de tráfico (cada 5 minutos)
     */
    fun startTrafficMonitor() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000) // 5 minutos
                checkForBetterRoute()
            }
        }
    }
    
    /**
     * Detiene el monitoreo de tráfico
     */
    fun stopTrafficMonitor() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = null
    }
    
    /**
     * Verifica si hay una ruta mejor debido al tráfico
     */
    private suspend fun checkForBetterRoute() {
        val alternatives = _routeAlternatives.value ?: return
        val currentRoute = alternatives.selectedRoute
        
        // Si no estamos en modo DRIVING, no verificar tráfico
        if (_travelMode.value != TravelMode.DRIVING) return
        
        try {
            // Re-obtener duración actual con tráfico
            val origin = if (_useCurrentLocation.value) {
                _currentLocation.value
            } else {
                _customStartPoint.value?.latLng
            } ?: return
            
            val points = currentRoute.orderedPoints
            if (points.isEmpty()) return
            
            val destination = points.last().latLng
            val waypoints = if (points.size > 1) {
                points.dropLast(1).joinToString("|") { "${it.latLng.latitude},${it.latLng.longitude}" }
            } else ""
            
            val response = directionsService.getDirections(
                origin = "${origin.latitude},${origin.longitude}",
                destination = "${destination.latitude},${destination.longitude}",
                waypoints = if (waypoints.isNotEmpty()) "optimize:false|$waypoints" else "",
                mode = "driving",
                departureTime = "now", // Esto incluye datos de tráfico
                apiKey = BuildConfig.MAPS_API_KEY
            )
            
            if (response.status == "OK" && response.routes.isNotEmpty()) {
                val route = response.routes.first()
                val newDuration = route.legs.sumOf { it.duration_in_traffic?.value ?: it.duration?.value ?: 0 }
                val currentDuration = currentRoute.totalDurationSeconds
                
                // Si la nueva duración es significativamente mayor, hay tráfico
                val trafficDelay = newDuration - currentDuration
                
                if (trafficDelay > 300) { // Más de 5 minutos de retraso
                    // Buscar ruta alternativa
                    val alternativeResponse = directionsService.getDirections(
                        origin = "${origin.latitude},${origin.longitude}",
                        destination = "${destination.latitude},${destination.longitude}",
                        waypoints = if (waypoints.isNotEmpty()) "optimize:true|$waypoints" else "",
                        mode = "driving",
                        departureTime = "now",
                        alternatives = "true",
                        apiKey = BuildConfig.MAPS_API_KEY
                    )
                    
                    if (alternativeResponse.status == "OK" && alternativeResponse.routes.size > 1) {
                        // Encontrar la ruta más rápida
                        val bestAlternative = alternativeResponse.routes.minByOrNull { r ->
                            r.legs.sumOf { it.duration_in_traffic?.value ?: it.duration?.value ?: 0 }
                        }
                        
                        bestAlternative?.let { alt ->
                            val altDuration = alt.legs.sumOf { it.duration_in_traffic?.value ?: it.duration?.value ?: 0 }
                            
                            if (altDuration < newDuration - 120) { // Al menos 2 min más rápida
                                _trafficSuggestion.value = TrafficSuggestion(
                                    currentRouteDuration = newDuration,
                                    suggestedRouteDuration = altDuration,
                                    trafficDelay = trafficDelay,
                                    suggestedRouteIndex = 0,
                                    reason = "Tráfico detectado"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silencioso - el monitoreo de tráfico es opcional
        }
    }
    
    /**
     * Acepta la sugerencia de tráfico y cambia a la ruta sugerida
     */
    fun acceptTrafficSuggestion() {
        val suggestion = _trafficSuggestion.value ?: return
        selectRoute(suggestion.suggestedRouteIndex)
        _trafficSuggestion.value = null
    }
    
    /**
     * Descarta la sugerencia de tráfico
     */
    fun dismissTrafficSuggestion() {
        _trafficSuggestion.value = null
    }
    
    /**
     * Calcula ETA (hora estimada de llegada)
     */
    fun calculateETA(): Long {
        val route = _routeAlternatives.value?.selectedRoute ?: return 0
        return System.currentTimeMillis() + (route.totalDurationSeconds * 1000L)
    }
}
