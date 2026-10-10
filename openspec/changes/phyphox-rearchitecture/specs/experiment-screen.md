# Experiment Screen Specification

## Current State

**File**: `Experiment.java` (2,558 lines) - The "God Activity"

### Responsibilities
- **Activity lifecycle management**
- **Measurement control**: start/stop/pause/timed run
- **Sensor input handling**: accelerometer, gyroscope, magnetometer, etc.
- **Bluetooth communication**: GATT connections, characteristic operations
- **Network server**: Remote access via HTTP/MQTT
- **UI rendering**: Tab layout, graph views, input controls
- **File I/O**: Export data (CSV, JSON, XLSX), save state
- **Camera management**: CameraX setup and analytics
- **Audio output**: Beeps during timed runs
- **State management**: onSaveInstanceState, navigation

### Problems
- 2,558 lines doing everything
- No separation between UI, business logic, and data
- Hard to test (all logic in Android component)
- Tight coupling to Android framework
- Mixed concerns scattered throughout class

## Target State

### Architecture

```
MainActivity.kt (shell)
├── Hosts ExperimentFragment via Navigation

ExperimentActivity.kt (renamed from Experiment.java, shell only)
├── onCreate() - setup navigation/hosting
├── onNewIntent() - handle deep links  
├── onOptionsItemSelected() - menu routing to ViewModel
└── Lifecycle callbacks

ExperimentFragment.kt (new, Compose UI)
├── Compose screen
├── ViewModel binding
└── View model event handling

ExperimentViewModel.kt (new, MVVM)
├── State management (measuring, timed run, graphs)
├── Input handling from UI
├── Measurement control commands
└── Export commands

MeasurementController.kt (domain layer, pure Kotlin)
├── startMeasurement() → Boolean
├── stopMeasurement()
├── pauseMeasurement()
├── startTimedMeasurement() → Boolean
├── stopTimedMeasurement()
└── isMeasurementRunning() → Boolean

AnalysisEngine.kt (extracted from Analysis.java, domain layer)
├── orchestrate analysis modules
├── execute formulas
└── manage buffer updates

GraphViewModel.kt (per-graph view models)
├── Graph state (zoom, range, units)
├── Data display logic
└── User interaction handling

BluetoothViewModel.kt (BLE connection state)
├── Connected devices list
├── Connection status
└── Device info display

SensorManager.kt (wrapper around Android Sensor API)
├── registerListener() / unregisterListener()
├── Available sensors query
└── Sensor configuration

BufferManager.kt (DataBuffer wrapper)
├── Buffer operations
├── Thread-safe access
└── Notification handling
```

### Data Models

```kotlin
// Domain model (pure Kotlin, no Android dependencies)
data class Experiment(
    val id: String,
    val title: String,
    val category: String,
    val views: List<ViewConfiguration>,
    val analysisModules: List<AnalysisModuleConfig>,
    val sensors: List<SensorConfig>,
    val bluetoothDevices: List<BluetoothDeviceConfig>
)

@Immutable
data class GraphState(
    val bufferName: String,
    val minRange: Double,
    val maxRange: Double,
    val isFollowing: Boolean,
    val unit: String,
    val isAbsolute: Boolean
)

// Measurement state
sealed interface MeasurementState {
    object Idle : MeasurementState
    object Measuring : MeasurementState
    data class Timed(val timeRemaining: Long) : MeasurementState
}
```

### ViewModel State

```kotlin
@Immutable
data class ExperimentState(
    val experiment: Experiment,
    val measurementState: MeasurementState = MeasurementState.Idle,
    val graphs: List<GraphState> = emptyList(),
    val selectedTab: Int = 0,
    val connectedDevices: List<BluetoothDevice> = emptyList(),
    val serverEnabled: Boolean = false,
    val serverAddress: String? = null
)
```

### ViewModel API

```kotlin
class ExperimentViewModel(
    private val measurementController: MeasurementController,
    private val analysisEngine: AnalysisEngine,
    private val bufferManager: BufferManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(ExperimentState(experiment))
    val state: StateFlow<ExperimentState> = _state.asStateFlow()
    
    // Measurement commands
    fun onStartMeasurement() {
        viewModelScope.launch {
            val success = measurementController.startMeasurement()
            updateState { it.copy(measurementState = if (success) MeasurementState.Measuring else it.measurementState) }
        }
    }
    
    fun onStopMeasurement() {
        measurementController.stopMeasurement()
        updateState { it.copy(measurementState = MeasurementState.Idle) }
    }
    
    // Graph commands
    fun onGraphZoom(graphIndex: Int, min: Double, max: Double, follow: Boolean) {
        updateState { state ->
            state.copy(
                graphs = state.graphs.toMutableList().apply {
                    this[graphIndex] = this[graphIndex].copy(minRange = min, maxRange = max, isFollowing = follow)
                }
            )
        }
    }
    
    // Export commands
    fun onExport() {
        viewModelScope.launch {
            bufferManager.exportData()
        }
    }
    
    private fun updateState(update: ExperimentState.() -> ExperimentState) {
        _state.value = _state.value.update()
    }
}
```

### UI Structure (Compose)

```kotlin
@Composable
fun ExperimentScreen(
    experimentId: String,
    viewModel: ExperimentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = { ExperimentTopBar(state.experiment.title) },
        floatingActionButton = { 
            if (state.measurementState == MeasurementState.Idle)
                StartButton(onClick = { viewModel.onStartMeasurement() })
        }
    ) { padding ->
        
        TabRow(selectedTab = state.selectedTab) {
            state.experiment.views.forEachIndexed { index, view ->
                Tab(
                    selected = state.selectedTab == index,
                    onClick = { viewModel.onTabSelected(index) },
                    text = { Text(view.title) }
                )
            }
        }
        
        Box(modifier = Modifier.padding(padding)) {
            when (state.measurementState) {
                is MeasurementState.Idle -> IdleScreen(state.graphs)
                is MeasurementState.Measuring -> RealtimeScreen(state.graphs)
                is MeasurementState.Timed -> TimedRunScreen(state)
            }
        }
    }
}

@Composable
fun RealtimeScreen(graphs: List<GraphState>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(graphs) { graph ->
            GraphView(
                bufferName = graph.bufferName,
                minRange = graph.minRange,
                maxRange = graph.maxRange,
                isFollowing = graph.isFollowing
            )
        }
    }
}
```

### Domain Layer Classes

```kotlin
// MeasurementController.kt (pure Kotlin)
class MeasurementController(
    private val sensorManager: SensorManager,
    private val analysisEngine: AnalysisEngine,
    private val bufferManager: BufferManager
) {
    fun startMeasurement(): Boolean {
        // Register sensors
        // Initialize buffers
        // Start analysis loop
        
        return true // Success/failure
    }
    
    fun stopMeasurement() {
        sensorManager.unregisterAllListeners()
        bufferManager.clearBuffers()
    }
}

// AnalysisEngine.kt (extracted from Analysis.java)
class AnalysisEngine(
    private val modules: List<AnalysisModule>
) {
    suspend fun execute(bufferData: Map<String, DoubleArray>): Map<String, Double> {
        // Execute analysis chain
        // Apply formulas
        // Return results
    }
}
```

## Migration Strategy

### Phase 2.1: Extract Domain Logic (Week 5)
- [ ] Create `Experiment` domain model from `PhyphoxExperiment.java`
- [ ] Extract `MeasurementController.kt` for start/stop logic
- [ ] Extract `AnalysisEngine.kt` from Analysis.java
- [ ] Write unit tests for domain classes

### Phase 2.2: Create ViewModels (Week 6)
- [ ] Create `ExperimentViewModel.kt` with StateFlow
- [ ] Implement graph state management
- [ ] Add measurement command handlers
- [ ] Test ViewModel in isolation

### Phase 2.3: UI Migration (Week 7)
- [ ] Create `ExperimentFragment.kt` with Compose UI
- [ ] Integrate with ViewModel
- [ ] Test navigation flow
- [ ] Verify all existing functionality preserved

## Acceptance Criteria

- [ ] Start/stop/pause measurement works as before
- [ ] Timed run countdown and beeps work as before
- [ ] Graphs display data correctly
- [ ] Bluetooth device connection works as before
- [ ] Remote server (HTTP/MQTT) works as before
- [ ] Export to CSV/JSON/XLSX works as before
- [ ] Camera preview and analytics work as before
- [ ] All existing tests pass after migration
