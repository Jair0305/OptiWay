package com.example.routeoptimizer.domain

import com.example.routeoptimizer.data.model.FixedPosition
import com.example.routeoptimizer.data.model.OptimizedRoute
import com.example.routeoptimizer.data.model.RouteAlternatives
import com.example.routeoptimizer.data.model.RoutePoint

/**
 * Optimizador de rutas usando el algoritmo del vecino más cercano
 * con mejora 2-opt para el problema del viajante (TSP)
 * 
 * Soporta:
 * - Posiciones fijas (inicio, fin, intermedias ancladas)
 * - Generación de múltiples rutas alternativas
 */
class RouteOptimizer {
    
    /**
     * Optimiza la ruta y genera múltiples alternativas
     */
    fun optimizeWithAlternatives(
        startPoint: RoutePoint,
        points: List<RoutePoint>,
        timeMatrix: Array<IntArray>,
        distanceMatrix: Array<IntArray>,
        alternativesCount: Int = 3
    ): RouteAlternatives {
        if (points.isEmpty()) {
            return RouteAlternatives(
                routes = listOf(OptimizedRoute(listOf(startPoint), 0, 0, 0)),
                selectedIndex = 0
            )
        }
        
        // Separar puntos fijos de los flexibles
        val firstFixed = points.filter { it.fixedPosition == FixedPosition.FIRST }
        val lastFixed = points.filter { it.fixedPosition == FixedPosition.LAST }
        val middleFixed = points.filter { it.fixedPosition == FixedPosition.FIXED }
        val flexible = points.filter { it.fixedPosition == FixedPosition.NONE }
        
        // Generar múltiples rutas
        val allRoutes = mutableListOf<OptimizedRoute>()
        
        // Ruta 1: Optimización principal
        val mainRoute = optimizeWithConstraints(
            startPoint, points, timeMatrix, distanceMatrix,
            firstFixed, lastFixed, middleFixed, flexible
        )
        allRoutes.add(mainRoute.copy(routeIndex = 0))
        
        // Generar alternativas variando el algoritmo
        if (flexible.size >= 2) {
            // Alternativa 2: Empezar por diferentes puntos
            for (i in 1 until minOf(alternativesCount, flexible.size + 1)) {
                val altRoute = generateAlternative(
                    startPoint, points, timeMatrix, distanceMatrix,
                    firstFixed, lastFixed, middleFixed, flexible, i
                )
                if (altRoute != null && !isDuplicateRoute(altRoute, allRoutes)) {
                    allRoutes.add(altRoute.copy(routeIndex = allRoutes.size))
                }
            }
        }
        
        // Ordenar por tiempo total
        val sortedRoutes = allRoutes.sortedBy { it.totalDurationSeconds }
        
        // Recalcular ahorros comparando con la ruta más larga
        val worstTime = sortedRoutes.lastOrNull()?.totalDurationSeconds ?: 0
        val routesWithSavings = sortedRoutes.mapIndexed { index, route ->
            route.copy(
                routeIndex = index,
                savingsVsOriginalSeconds = worstTime - route.totalDurationSeconds
            )
        }
        
        return RouteAlternatives(
            routes = routesWithSavings.take(alternativesCount),
            selectedIndex = 0
        )
    }
    
    /**
     * Genera TODAS las rutas posibles (para conjuntos pequeños)
     * Solo usar con max ~8 puntos flexibles, sino explota exponencialmente
     */
    fun generateAllRoutes(
        startPoint: RoutePoint,
        points: List<RoutePoint>,
        timeMatrix: Array<IntArray>,
        distanceMatrix: Array<IntArray>,
        maxRoutes: Int = 20
    ): RouteAlternatives {
        val firstFixed = points.filter { it.fixedPosition == FixedPosition.FIRST }
        val lastFixed = points.filter { it.fixedPosition == FixedPosition.LAST }
        val middleFixed = points.filter { it.fixedPosition == FixedPosition.FIXED }
        val flexible = points.filter { it.fixedPosition == FixedPosition.NONE }
        
        if (flexible.size > 8) {
            // Demasiadas combinaciones, usar solo alternativas
            return optimizeWithAlternatives(startPoint, points, timeMatrix, distanceMatrix, maxRoutes)
        }
        
        val allPoints = listOf(startPoint) + points
        val permutations = generatePermutations(flexible.indices.toList())
        
        val routesList = permutations.take(maxRoutes * 10).mapNotNull { perm ->
            try {
                val orderedFlexible = perm.map { flexible[it] }
                val fullOrder = buildFullOrder(startPoint, firstFixed, orderedFlexible, middleFixed, lastFixed)
                val (time, distance) = calculateRouteCost(fullOrder, allPoints, timeMatrix, distanceMatrix)
                OptimizedRoute(fullOrder, time, distance, 0, 0)
            } catch (e: Exception) {
                null
            }
        }.toList()
            .sortedBy { it.totalDurationSeconds }
            .take(maxRoutes)
        
        val worstTime = routesList.lastOrNull()?.totalDurationSeconds ?: 0
        val routes = routesList.mapIndexed { index, route ->
            val savings = worstTime - route.totalDurationSeconds
            route.copy(routeIndex = index, savingsVsOriginalSeconds = savings)
        }
        
        return RouteAlternatives(routes, 0)
    }
    
    private fun optimizeWithConstraints(
        startPoint: RoutePoint,
        points: List<RoutePoint>,
        timeMatrix: Array<IntArray>,
        distanceMatrix: Array<IntArray>,
        firstFixed: List<RoutePoint>,
        lastFixed: List<RoutePoint>,
        middleFixed: List<RoutePoint>,
        flexible: List<RoutePoint>
    ): OptimizedRoute {
        val allPoints = listOf(startPoint) + points
        
        // Optimizar solo los puntos flexibles
        val optimizedFlexible = if (flexible.size > 1) {
            optimizeSubset(startPoint, flexible, allPoints, timeMatrix)
        } else {
            flexible
        }
        
        // Construir ruta final respetando restricciones
        val orderedPoints = buildFullOrder(startPoint, firstFixed, optimizedFlexible, middleFixed, lastFixed)
        
        // Calcular tiempo y distancia total
        val (totalTime, totalDistance) = calculateRouteCost(orderedPoints, allPoints, timeMatrix, distanceMatrix)
        
        return OptimizedRoute(
            orderedPoints = orderedPoints,
            totalDurationSeconds = totalTime,
            totalDistanceMeters = totalDistance,
            savingsVsOriginalSeconds = 0
        )
    }
    
    private fun buildFullOrder(
        startPoint: RoutePoint,
        firstFixed: List<RoutePoint>,
        flexible: List<RoutePoint>,
        middleFixed: List<RoutePoint>,
        lastFixed: List<RoutePoint>
    ): List<RoutePoint> {
        val result = mutableListOf<RoutePoint>()
        result.add(startPoint)
        result.addAll(firstFixed)
        
        // Intercalar los puntos fijos intermedios con los flexibles
        if (middleFixed.isEmpty()) {
            result.addAll(flexible)
        } else {
            // Los puntos FIXED mantienen su orden relativo original
            val combined = (flexible + middleFixed).sortedBy { 
                if (it.fixedPosition == FixedPosition.FIXED) it.order else Int.MAX_VALUE 
            }
            result.addAll(combined)
        }
        
        result.addAll(lastFixed)
        
        return result.mapIndexed { index, point -> point.copy(order = index) }
    }
    
    private fun calculateRouteCost(
        orderedPoints: List<RoutePoint>,
        allPoints: List<RoutePoint>,
        timeMatrix: Array<IntArray>,
        distanceMatrix: Array<IntArray>
    ): Pair<Int, Int> {
        var totalTime = 0
        var totalDistance = 0
        
        for (i in 0 until orderedPoints.size - 1) {
            val fromIndex = allPoints.indexOfFirst { it.id == orderedPoints[i].id }
            val toIndex = allPoints.indexOfFirst { it.id == orderedPoints[i + 1].id }
            
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < timeMatrix.size && toIndex < timeMatrix[0].size) {
                totalTime += timeMatrix[fromIndex][toIndex]
                totalDistance += distanceMatrix[fromIndex][toIndex]
            }
        }
        
        return Pair(totalTime, totalDistance)
    }
    
    private fun optimizeSubset(
        startPoint: RoutePoint,
        subset: List<RoutePoint>,
        allPoints: List<RoutePoint>,
        timeMatrix: Array<IntArray>
    ): List<RoutePoint> {
        if (subset.size <= 1) return subset
        
        // Crear una sub-matriz para el subset
        val indices = subset.map { point -> allPoints.indexOfFirst { it.id == point.id } }
        val startIndex = allPoints.indexOfFirst { it.id == startPoint.id }
        
        // Algoritmo del vecino más cercano para el subset
        val visited = BooleanArray(subset.size)
        val route = mutableListOf<Int>()
        
        // Encontrar el punto más cercano al inicio
        var currentIdx = startIndex
        
        repeat(subset.size) {
            var nearest = -1
            var minTime = Int.MAX_VALUE
            
            for (j in subset.indices) {
                if (!visited[j]) {
                    val subsetIdx = indices[j]
                    val time = if (currentIdx < timeMatrix.size && subsetIdx < timeMatrix[0].size) {
                        timeMatrix[currentIdx][subsetIdx]
                    } else Int.MAX_VALUE
                    
                    if (time < minTime) {
                        minTime = time
                        nearest = j
                    }
                }
            }
            
            if (nearest != -1) {
                route.add(nearest)
                visited[nearest] = true
                currentIdx = indices[nearest]
            }
        }
        
        // Mejora 2-opt
        val improvedRoute = twoOptForSubset(route, indices, timeMatrix)
        
        return improvedRoute.map { subset[it] }
    }
    
    private fun twoOptForSubset(
        route: List<Int>,
        indices: List<Int>,
        timeMatrix: Array<IntArray>
    ): List<Int> {
        if (route.size < 3) return route
        
        var currentRoute = route.toMutableList()
        var improved = true
        
        while (improved) {
            improved = false
            
            for (i in 0 until currentRoute.size - 1) {
                for (j in i + 2 until currentRoute.size) {
                    val idxI = indices[currentRoute[i]]
                    val idxI1 = indices[currentRoute[i + 1]]
                    val idxJ = indices[currentRoute[j]]
                    val idxJ1 = if (j + 1 < currentRoute.size) indices[currentRoute[j + 1]] else idxI
                    
                    if (idxI >= timeMatrix.size || idxI1 >= timeMatrix.size || 
                        idxJ >= timeMatrix.size || idxJ1 >= timeMatrix.size) continue
                    
                    val currentCost = timeMatrix[idxI][idxI1] + timeMatrix[idxJ][idxJ1]
                    val newCost = timeMatrix[idxI][idxJ] + timeMatrix[idxI1][idxJ1]
                    
                    if (newCost < currentCost) {
                        // Invertir segmento
                        currentRoute = (currentRoute.subList(0, i + 1) +
                                currentRoute.subList(i + 1, j + 1).reversed() +
                                currentRoute.subList(j + 1, currentRoute.size)).toMutableList()
                        improved = true
                    }
                }
            }
        }
        
        return currentRoute
    }
    
    private fun generateAlternative(
        startPoint: RoutePoint,
        points: List<RoutePoint>,
        timeMatrix: Array<IntArray>,
        distanceMatrix: Array<IntArray>,
        firstFixed: List<RoutePoint>,
        lastFixed: List<RoutePoint>,
        middleFixed: List<RoutePoint>,
        flexible: List<RoutePoint>,
        seed: Int
    ): OptimizedRoute? {
        if (flexible.size < 2) return null
        
        // Rotar los puntos flexibles para empezar por uno diferente
        val rotated = flexible.drop(seed % flexible.size) + flexible.take(seed % flexible.size)
        
        val allPoints = listOf(startPoint) + points
        val orderedPoints = buildFullOrder(startPoint, firstFixed, rotated, middleFixed, lastFixed)
        val (totalTime, totalDistance) = calculateRouteCost(orderedPoints, allPoints, timeMatrix, distanceMatrix)
        
        return OptimizedRoute(orderedPoints, totalTime, totalDistance, 0, 0)
    }
    
    private fun isDuplicateRoute(route: OptimizedRoute, existing: List<OptimizedRoute>): Boolean {
        val routeIds = route.orderedPoints.map { it.id }
        return existing.any { existingRoute ->
            existingRoute.orderedPoints.map { it.id } == routeIds
        }
    }
    
    private fun generatePermutations(list: List<Int>): Sequence<List<Int>> = sequence {
        if (list.size <= 1) {
            yield(list)
            return@sequence
        }
        
        val first = list.first()
        val rest = list.drop(1)
        
        for (perm in generatePermutations(rest)) {
            for (i in 0..perm.size) {
                yield(perm.subList(0, i) + first + perm.subList(i, perm.size))
            }
        }
    }
    
    // Mantener compatibilidad con el método original
    fun optimize(
        startPoint: RoutePoint,
        points: List<RoutePoint>,
        timeMatrix: Array<IntArray>,
        distanceMatrix: Array<IntArray>
    ): OptimizedRoute {
        return optimizeWithAlternatives(startPoint, points, timeMatrix, distanceMatrix, 1).bestRoute
    }
}
