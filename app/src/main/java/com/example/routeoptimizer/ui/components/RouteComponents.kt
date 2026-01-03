package com.example.routeoptimizer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.routeoptimizer.data.model.*
import kotlin.math.roundToInt

// ==================== SELECTOR DE PUNTO DE PARTIDA ====================

@Composable
fun StartPointSelector(
    useCurrentLocation: Boolean,
    customStartPoint: RoutePoint?,
    onToggleCurrentLocation: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.TripOrigin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Desde:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        FilterChip(
            selected = useCurrentLocation,
            onClick = { onToggleCurrentLocation(true) },
            label = { Text("Mi ubicación", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.height(28.dp)
        )
        
        FilterChip(
            selected = !useCurrentLocation,
            onClick = { onToggleCurrentLocation(false); if (customStartPoint == null) onSearchClick() },
            label = { 
                Text(
                    if (!useCurrentLocation && customStartPoint != null) customStartPoint.name.take(10) + "…" else "Otra",
                    style = MaterialTheme.typography.labelSmall
                ) 
            },
            modifier = Modifier.height(28.dp)
        )
    }
}

// ==================== BARRA DE BÚSQUEDA ====================

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    placeholder: String = "Buscar...",
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun SearchResultsList(results: List<PlaceSearchResult>, onResultClick: (PlaceSearchResult) -> Unit, modifier: Modifier = Modifier) {
    if (results.isEmpty()) return
    
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            items(results.size) { i ->
                val r = results[i]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onResultClick(r) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (i < results.size - 1) HorizontalDivider(Modifier.padding(horizontal = 10.dp))
            }
        }
    }
}

// ==================== LISTA CON DRAG & DROP ====================

@Composable
fun RoutePointsListDraggable(
    points: List<RoutePoint>,
    onRemovePoint: (RoutePoint) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onSetFirst: (RoutePoint) -> Unit,
    onSetLast: (RoutePoint) -> Unit,
    onSetFixed: (RoutePoint) -> Unit,
    onSetFlexible: (RoutePoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingItemIndex by remember { mutableIntStateOf(-1) }
    var draggingItemOffset by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    
    // Usamos Column en lugar de LazyColumn para mejor control del drag
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        points.forEachIndexed { index, point ->
            val canMove = point.fixedPosition == FixedPosition.NONE
            val isDragging = draggingItemIndex == index
            
            key(point.id) {
                DraggableItem(
                    point = point,
                    index = index,
                    totalItems = points.size,
                    isDragging = isDragging,
                    canDrag = canMove,
                    dragOffset = if (isDragging) draggingItemOffset else 0f,
                    onDragStart = {
                        if (canMove) {
                            draggingItemIndex = index
                            draggingItemOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { delta ->
                        if (canMove && draggingItemIndex == index) {
                            draggingItemOffset += delta
                            
                            // Calcular nuevo índice basado en el offset
                            val itemHeight = 72f // Altura aproximada de cada item
                            val moveThreshold = itemHeight * 0.6f
                            
                            if (kotlin.math.abs(draggingItemOffset) > moveThreshold) {
                                val direction = if (draggingItemOffset > 0) 1 else -1
                                val targetIndex = (index + direction).coerceIn(0, points.size - 1)
                                
                                // Solo mover si el target no está fijo
                                val targetPoint = points.getOrNull(targetIndex)
                                if (targetIndex != index && targetPoint?.fixedPosition == FixedPosition.NONE) {
                                    onReorder(index, targetIndex)
                                    draggingItemIndex = targetIndex
                                    draggingItemOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingItemIndex = -1
                        draggingItemOffset = 0f
                    },
                    onRemove = { onRemovePoint(point) },
                    onSetFirst = { onSetFirst(point) },
                    onSetLast = { onSetLast(point) },
                    onSetFixed = { onSetFixed(point) },
                    onSetFlexible = { onSetFlexible(point) }
                )
            }
        }
    }
}

@Composable
private fun DraggableItem(
    point: RoutePoint,
    index: Int,
    totalItems: Int,
    isDragging: Boolean,
    canDrag: Boolean,
    dragOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    onSetFirst: () -> Unit,
    onSetLast: () -> Unit,
    onSetFixed: () -> Unit,
    onSetFlexible: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val bgColor = when (point.fixedPosition) {
        FixedPosition.FIRST -> MaterialTheme.colorScheme.surface
        FixedPosition.LAST -> MaterialTheme.colorScheme.surface
        FixedPosition.FIXED -> MaterialTheme.colorScheme.surface
        FixedPosition.NONE -> if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    }
    
    val borderColor = when (point.fixedPosition) {
        FixedPosition.FIRST -> Color(0xFF4CAF50)
        FixedPosition.LAST -> Color(0xFF2196F3)
        FixedPosition.FIXED -> Color(0xFFFF9800)
        FixedPosition.NONE -> if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent
    }
    
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 1.dp,
        label = "elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "scale"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(12.dp),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            .animateContentSize(),
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = if (borderColor != Color.Transparent) BorderStroke(1.5.dp, borderColor) else null
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // DRAG HANDLE - solo si puede moverse
                if (canDrag) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() },
                                    onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            "Arrastrar",
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Icono de candado si está fijo
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            when (point.fixedPosition) {
                                FixedPosition.FIRST -> Icons.Default.Start
                                FixedPosition.LAST -> Icons.Default.Flag
                                else -> Icons.Default.Lock
                            },
                            null,
                            tint = when (point.fixedPosition) {
                                FixedPosition.FIRST -> Color(0xFF4CAF50)
                                FixedPosition.LAST -> Color(0xFF2196F3)
                                else -> Color(0xFFFF9800)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Número
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(
                        when (point.fixedPosition) {
                            FixedPosition.FIRST -> Color(0xFF4CAF50)
                            FixedPosition.LAST -> Color(0xFF2196F3)
                            FixedPosition.FIXED -> Color(0xFFFF9800)
                            FixedPosition.NONE -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.width(8.dp))
                
                // Info
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(point.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(point.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                
                // Expandir
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = !expanded }.size(24.dp)
                )
            }
            
            // Opciones expandidas
            AnimatedVisibility(expanded) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniChip("1ª", point.fixedPosition == FixedPosition.FIRST, Color(0xFF4CAF50)) {
                            if (point.fixedPosition == FixedPosition.FIRST) onSetFlexible() else onSetFirst()
                        }
                        MiniChip("🔒", point.fixedPosition == FixedPosition.FIXED, Color(0xFFFF9800)) {
                            if (point.fixedPosition == FixedPosition.FIXED) onSetFlexible() else onSetFixed()
                        }
                        MiniChip("Fin", point.fixedPosition == FixedPosition.LAST, Color(0xFF2196F3)) {
                            if (point.fixedPosition == FixedPosition.LAST) onSetFlexible() else onSetLast()
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color else color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== RUTAS ALTERNATIVAS (EXPANDIBLES) ====================

@Composable
fun RouteAlternativesCard(
    alternatives: RouteAlternatives,
    selectedIndex: Int,
    travelMode: TravelMode,
    onSelectRoute: (Int) -> Unit,
    onNavigate: () -> Unit,
    onShare: () -> Unit,
    onShowAll: () -> Unit,
    onToggleMode: () -> Unit,
    formatDuration: (Int) -> String,
    formatDistance: (Int) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Header compacto
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${alternatives.routes.size} rutas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // Toggle discreto
                FilterChip(
                    selected = false,
                    onClick = onToggleMode,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (travelMode == TravelMode.DRIVING) "🚗 Carro" else "✈️ Línea recta",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.SwapHoriz, null, Modifier.size(14.dp))
                        }
                    },
                    modifier = Modifier.height(28.dp)
                )
            }
            TextButton(onClick = onShowAll, contentPadding = PaddingValues(4.dp)) {
                Text("Ver más", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        
        Spacer(Modifier.height(6.dp))
        
        // LISTA SCROLLABLE LIMITADA
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(alternatives.routes) { idx, route ->
                ExpandableRouteCard(
                    route = route,
                    index = idx,
                    isSelected = idx == selectedIndex,
                    isBest = idx == 0,
                    onSelect = { onSelectRoute(idx) },
                    formatDuration = formatDuration,
                    formatDistance = formatDistance
                )
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compartir
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Compartir", style = MaterialTheme.typography.labelMedium)
            }
            
            // Navegar
            Button(
                onClick = onNavigate,
                modifier = Modifier.weight(1.5f).height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Navigation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Navegar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ExpandableRouteCard(
    route: OptimizedRoute,
    index: Int,
    isSelected: Boolean,
    isBest: Boolean,
    onSelect: () -> Unit,
    formatDuration: (Int) -> String,
    formatDistance: (Int) -> String
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Diseño Premium
    Surface(
        onClick = onSelect,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de selección (Radio o Check)
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Ruta ${index + 1}", 
                            style = MaterialTheme.typography.titleSmall, 
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isBest) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF4CAF50),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "MEJOR OPCIÓN", 
                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = Color.White, 
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp // Texto muy pequeño
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(formatDuration(route.totalDurationSeconds), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("  •  ${formatDistance(route.totalDistanceMeters)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                // Botón expandir discreto
                IconButton(onClick = { expanded = !expanded }, Modifier.size(24.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, 
                        null, 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Detalle expandido (Timeline)
            if (expanded) {
                Column(Modifier.padding(start = 44.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    
                    route.orderedPoints.forEachIndexed { i, p ->
                        // Línea conectora
                        if (i > 0) {
                            Box(
                                Modifier
                                    .padding(start = 3.dp)
                                    .width(2.dp)
                                    .height(12.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Punto
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (i == 0 || i == route.orderedPoints.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p.name, 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== BOTONES E INSTRUCCIONES ====================

@Composable
fun ActionButtons(
    pointCount: Int,
    isOptimizing: Boolean,
    hasOptimizedRoute: Boolean,
    onOptimize: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClear, Modifier.weight(1f).height(40.dp), enabled = pointCount > 0, shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Limpiar", style = MaterialTheme.typography.labelMedium)
        }
        
        Button(
            onClick = onOptimize,
            Modifier.weight(2f).height(40.dp),
            enabled = pointCount >= 1 && !isOptimizing,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isOptimizing) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("...", style = MaterialTheme.typography.labelMedium)
            } else {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (hasOptimizedRoute) "Recalcular" else "Optimizar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun InstructionsCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp), 
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), 
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.TouchApp, 
                    contentDescription = null, 
                    modifier = Modifier.size(24.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Toca el mapa para agregar paradas", 
                    style = MaterialTheme.typography.bodyMedium, 
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "O usa el buscador 🔍 para buscar direcciones", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==================== COMPONENTES PREMIUM ====================

/**
 * Animación de carga mientras optimiza - Estilo premium
 */
@Composable
fun OptimizingAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "optimizing")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                contentAlignment = Alignment.Center
            ) {
                // Círculo exterior rotando
                Box(
                    Modifier
                        .size(70.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50),
                                    Color(0xFF2196F3),
                                    Color(0xFF4CAF50)
                                )
                            ),
                            shape = CircleShape
                        )
                )
                
                // Icono central con fondo
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                "Optimizando ruta...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                "Calculando la mejor secuencia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tarjeta de ahorro - Muestra cuánto tiempo/distancia ahorraste
 */
@Composable
fun SavingsCard(
    savingsSeconds: Int,
    savingsMeters: Int,
    formatDuration: (Int) -> String,
    formatDistance: (Int) -> String,
    modifier: Modifier = Modifier
) {
    if (savingsSeconds <= 0 && savingsMeters <= 0) return
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    "¡Ruta optimizada!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Row {
                    if (savingsSeconds > 0) {
                        Text(
                            "Ahorraste ${formatDuration(savingsSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF388E3C)
                        )
                    }
                    if (savingsSeconds > 0 && savingsMeters > 0) {
                        Text(" y ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
                    }
                    if (savingsMeters > 0) {
                        Text(
                            "${formatDistance(savingsMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF388E3C)
                        )
                    }
                }
            }
            
            Text(
                "🎉",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

/**
 * Chip de estadística mejorado con icono
 */
@Composable
fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = color
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== BANNERS Y SUGERENCIAS ====================

/**
 * Banner discreto para sugerir re-optimización cuando se agregan puntos
 */
@Composable
fun ReoptimizationBanner(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFF3E0), // Naranja claro
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    "Ruta modificada",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
                Text(
                    "¿Re-optimizar con los nuevos puntos?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00)
                )
            }
            
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("No", color = Color(0xFF757575))
            }
            
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                contentPadding = PaddingValues(horizontal = 12.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sí", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Card de sugerencia de tráfico - No invasiva
 */
@Composable
fun TrafficSuggestionCard(
    suggestion: TrafficSuggestion,
    formatDuration: (Int) -> String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = suggestion.hasSignificantSavings,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFFE3F2FD),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f)),
            shadowElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2196F3)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Ruta más rápida disponible",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            "Ahorra ${formatDuration(suggestion.savingsSeconds)} • ${suggestion.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1976D2)
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar a ruta más rápida", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Card compacta mostrando ETA (Hora estimada de llegada)
 */
@Composable
fun ETACard(
    etaTimestamp: Long,
    trafficCondition: TrafficCondition,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val etaString = remember(etaTimestamp) { timeFormat.format(java.util.Date(etaTimestamp)) }
    
    val trafficColor = when (trafficCondition) {
        TrafficCondition.LIGHT -> Color(0xFF4CAF50)
        TrafficCondition.MODERATE -> Color(0xFFFF9800)
        TrafficCondition.HEAVY -> Color(0xFFF44336)
        TrafficCondition.UNKNOWN -> Color(0xFF9E9E9E)
    }
    
    val trafficText = when (trafficCondition) {
        TrafficCondition.LIGHT -> "Tráfico fluido"
        TrafficCondition.MODERATE -> "Tráfico moderado"
        TrafficCondition.HEAVY -> "Tráfico intenso"
        TrafficCondition.UNKNOWN -> "Sin datos"
    }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ETA
            Column {
                Text(
                    "Llegada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    etaString,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Indicador de tráfico
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(trafficColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    trafficText,
                    style = MaterialTheme.typography.labelSmall,
                    color = trafficColor
                )
            }
        }
    }
}

/**
 * FAB para agregar paradas rápidamente
 */
@Composable
fun AddStopFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            Icons.Default.AddLocation,
            contentDescription = "Agregar parada",
            modifier = Modifier.size(24.dp)
        )
    }
}

