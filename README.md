# OptiWay 🚗📍

**The Smart Route Optimization App for Multi-Stop Deliveries**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![Android Auto](https://img.shields.io/badge/Supports-Android%20Auto-orange.svg)](https://developer.android.com/cars)

---

## 🎯 Overview

**OptiWay** is a professional-grade route optimization application designed for delivery drivers, field service professionals, and anyone who needs to visit multiple locations efficiently. Using advanced algorithms and real-time traffic data, OptiWay calculates the optimal order to visit all your stops, saving you valuable time and fuel costs.

### The Problem We Solve

Every day, millions of delivery drivers, salespeople, and service technicians face the same challenge: **visiting multiple locations in the most efficient order**. Traditional navigation apps can only route you from point A to point B, but when you have 10, 20, or 50 stops, manually figuring out the best sequence becomes nearly impossible.

**OptiWay solves this problem** by automatically analyzing all possible routes and finding the optimal visiting order based on:
- Real-time traffic conditions
- Distance between all points
- User-defined constraints (fixed first/last stops)

---

## ✨ Key Features

### 🗺️ Intelligent Route Optimization
- **Multi-stop optimization**: Add unlimited waypoints and let the algorithm find the best sequence
- **Multiple route alternatives**: Generate 3-5 different route options to choose from
- **Real-time traffic integration**: Routes are calculated considering current traffic conditions
- **Constraint support**: Lock specific stops as "first", "last", or "fixed position"

### 🚗 Seamless Navigation
- **Google Maps integration**: One-tap navigation to start your optimized route
- **Android Auto support**: Full in-car display with simplified interface for safe driving
- **Turn-by-turn directions**: Detailed navigation instructions for each leg

### 📍 Smart Location Management
- **Drag & drop reordering**: Manually adjust stop order with intuitive gestures
- **Draggable map markers**: Fine-tune stop locations directly on the map
- **Address autocomplete**: Quickly find locations with Google Places integration
- **Custom start point**: Begin from your current location or any specified address

### 🎨 Premium User Experience
- **Dark/Light mode**: Automatic theme switching based on system preferences
- **Interactive onboarding**: Guided tutorial for first-time users
- **Animated transitions**: Smooth, professional UI animations throughout
- **Numbered markers**: Clearly see the optimized order on the map

### 📊 Route Analytics
- **Time savings display**: See how much time the optimization saves vs. original order
- **Distance calculations**: Total route distance with per-leg breakdown
- **ETA estimation**: Estimated time of arrival based on current conditions
- **Route history**: Save and reload previously optimized routes

### 🚦 Real-Time Traffic Monitoring
- **Traffic-aware routing**: Initial routes consider current traffic
- **Dynamic re-routing suggestions**: Get notified when a faster route becomes available
- **Traffic condition indicators**: Visual feedback on route congestion levels

### 📤 Easy Sharing
- **Share optimized routes**: Send route details via WhatsApp, SMS, email
- **Google Maps links**: Include clickable navigation links in shared messages
- **Professional formatting**: Routes are shared with clear, readable formatting

---

## 🏗️ Architecture

OptiWay follows modern Android development best practices:

```
app/
├── data/
│   ├── api/           # Retrofit services (Google APIs)
│   ├── local/         # DataStore persistence
│   ├── model/         # Data classes
│   ├── repository/    # Data layer
│   └── sync/          # State management
├── domain/
│   └── RouteOptimizer # Core optimization algorithm
├── ui/
│   ├── components/    # Reusable Compose components
│   ├── screens/       # Screen composables
│   ├── theme/         # Material 3 theming
│   └── viewmodel/     # ViewModels
├── auto/              # Android Auto integration
└── util/              # Utility classes
```

### Technology Stack

| Layer | Technology |
|-------|------------|
| **UI** | Jetpack Compose, Material 3 |
| **Maps** | Google Maps Compose |
| **Navigation** | Google Directions API |
| **Places** | Google Places API |
| **Networking** | Retrofit + OkHttp |
| **Persistence** | DataStore Preferences |
| **Serialization** | Kotlinx Serialization |
| **Architecture** | MVVM + StateFlow |
| **Car Display** | Android Auto (Car App Library) |

---

## 🧮 How the Optimization Works

OptiWay uses a sophisticated algorithm to find the optimal route:

### 1. Distance Matrix Calculation
First, we calculate the distance and travel time between **every pair of stops** using the Google Distance Matrix API. This gives us an NxN matrix of all possible travel costs.

### 2. Nearest Neighbor Heuristic
We start with the origin and repeatedly select the nearest unvisited stop as the next destination. This provides a good initial solution quickly.

### 3. 2-Opt Improvement
The initial route is then improved using the 2-opt algorithm, which systematically tries reversing segments of the route to find shorter paths.

### 4. Alternative Generation
We generate multiple route alternatives by starting from different points and applying random perturbations, giving users options beyond just the mathematically optimal route.

### 5. Constraint Handling
User-defined constraints (fixed positions) are respected throughout the optimization process, ensuring flexible stops are optimized around locked positions.

---

## 📱 Screenshots

| Map View | Route List | Android Auto |
|----------|------------|--------------|
| Numbered markers showing optimized order | Drag & drop stop management | Simplified driving interface |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Google Maps API key with the following APIs enabled:
  - Maps SDK for Android
  - Directions API
  - Distance Matrix API
  - Places API

### Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/optiway.git
```

2. Create `secrets.properties` in the project root:
```properties
MAPS_API_KEY=your_google_maps_api_key
```

3. Build and run:
```bash
./gradlew assembleDebug
```

---

## 🎯 Use Cases

### 📦 Delivery Drivers
Optimize your daily delivery route to minimize driving time and fuel costs. Handle rush hour traffic with real-time re-routing.

### 🔧 Field Service Technicians
Visit all client locations efficiently. Lock important appointments in place while optimizing the rest.

### 🏠 Real Estate Agents
Show multiple properties in the optimal order. Share the route with clients for transparency.

### 🛒 Sales Representatives
Plan territory visits efficiently. Save historical routes for recurring schedules.

---

## 📈 Performance

- **Optimization time**: < 3 seconds for up to 25 stops
- **Typical time savings**: 15-30% compared to unoptimized routes
- **Fuel savings**: Proportional to distance reduction

---

## 🔮 Roadmap

- [ ] Voice input for adding stops
- [ ] Home screen widget for quick route access
- [ ] Integration with calendar for automatic scheduling
- [ ] Fleet management for multiple drivers
- [ ] Offline route caching

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- Google Maps Platform for comprehensive location APIs
- Jetpack Compose team for the modern UI toolkit
- Android Auto team for car integration capabilities

---

<p align="center">
  <b>OptiWay</b> - Smart Routes, Saved Time ⏱️
</p>
