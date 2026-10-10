# Architecture Specification

## Target Architecture

### Layer Structure

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (Compose/View)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ExperimentList│  │ Experiment   │  │  Settings    │      │
│  │   Screen     │  │   Screen     │  │   Screen     │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│                   ViewModel Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   List VM    │  │ ExperimentVM │  │ Settings VM  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer (Pure Kotlin)               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │Measurement   │  │ Analysis     │  │  Export      │      │
│  │Controller    │  │ Engine       │  │ Service      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │Repository    │  │ DataSource   │  │  Native Wrappers│   │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Package Structure

```kotlin
de.rwth_aachen.phyphox/
├── MainActivity.kt                    // Single Activity (renamed from ExperimentListActivity)
├── experimentlist/                    // Experiment List screen
│   ├── ui/
│   │   └── ExperimentListScreen.kt   // Compose UI
│   ├── viewmodel/
│   │   └── ExperimentListViewModel.kt
│   ├── data/
│   │   ├── Repository.kt
│   │   ├── DataSource.kt
│   │   ├── AssetDataSource.kt
│   │   └── FileDataSource.kt
│   └── model/
│       └── ExperimentInfo.kt         // Domain model (renamed from ExperimentShortInfo)
│
├── experiment/                        // Experiment screen (from Experiment.java → ExperimentActivity + Fragment)
│   ├── ui/
│   │   └── ExperimentScreen.kt       // Compose UI
│   ├── viewmodel/
│   │   ├── ExperimentViewModel.kt    // Main state (start/stop/pause/timed run)
│   │   ├── GraphViewModel.kt         // Per-graph logic
│   │   └── BluetoothViewModel.kt     // BLE connection state
│   ├── data/
│   │   ├── AnalysisManager.kt        // Analysis orchestration
│   │   ├── BufferManager.kt          // DataBuffer wrapper
│   │   └── SensorManager.kt          // Android sensor API wrapper
│   └── domain/
│       ├── Experiment.kt             // Domain model (from PhyphoxExperiment)
│       ├── MeasurementController.kt  // Start/stop/pause logic
│       └── ExportService.kt          // Export operations
│
├── settings/                          // Settings screen
│   ├── ui/
│   │   └── SettingsScreen.kt         // Compose UI
│   ├── viewmodel/
│   │   └── SettingsViewModel.kt      // Theme, preferences state
│   └── data/
│       └── PreferenceRepository.kt   // SharedPrefs abstraction
│
├── bluetooth/                         // Bluetooth flow
│   ├── ui/
│   │   ├── BluetoothScanScreen.kt
│   │   └── DeviceListScreen.kt
│   ├── viewmodel/
│   │   └── BluetoothViewModel.kt     // Scan, connect state
│   ├── data/
│   │   ├── BLEConnection.kt          // GATT connection manager
│   │   └── CharacteristicMapper.kt   // UUID to buffer mapping
│   └── domain/
│       └── ConnectionManager.kt      // Reconnection logic
│
├── io/                                // File I/O & Export
│   ├── file/
│   │   ├── ExperimentLoader.kt       // Load .phyphox files
│   │   ├── ExperimentSaver.kt        // Save experiments
│   │   └── ZipHandler.kt             // ZIP extraction
│   └── export/
│       ├── CSVExporter.kt
│       ├── JSONExporter.kt
│       └── XLSXExporter.kt
│
└── formula/                           // Formula parser (pure Kotlin)
    ├── parser/
    │   ├── Lexer.kt
    │   └── Parser.kt
    ├── ast/
    │   ├── Expression.kt
    │   ├── BinaryOperation.kt
    │   └── FunctionCall.kt
    └── evaluator/
        ├── Evaluator.kt
        └── BufferReference.kt

// Native code (unchanged, just wrapped):
native/
├── FFTW3Wrapper.kt
└── AnalysisNative.kt
```

### Key Changes

#### Before
```java
Experiment.java (2,558 lines)
├── Activity lifecycle
├── Sensor input handling  
├── Bluetooth communication
├── Network server (remote access)
├── Measurement control
└── UI rendering
```

#### After
```kotlin
MainActivity.kt (shell only)
├── Navigation host
└── Deep link routing

ExperimentActivity.kt (renamed from Experiment.java, shell)
├── Hosts ExperimentFragment
└── Lifecycle management

ExperimentFragment.kt (new)
├── Compose UI
└── ViewModel binding

ExperimentViewModel.kt (new)
├── State management
├── Start/stop/pause/timed run
└── Input handling

AnalysisEngine.kt (extracted from Analysis.java)
├── Module orchestration
└── Formula evaluation

MeasurementController.kt (new, domain layer)
├── Start measurement logic
├── Stop measurement logic
└── Timed run control
```

### Navigation Graph

```xml
<!-- res/navigation/nav_graph.xml -->
<navigation>
    <fragment android:id="@+id/experimentList"
              android:name="de.rwth_aachen.phyphox.experimentlist.ui.ExperimentListFragment" />
    
    <fragment android:id="@+id/experimentDetail"
              android:name="de.rwth_aachen.phyphox.experiment.ui.ExperimentFragment">
        <argument android:name="experimentId"
                  app:argType="string" />
    </fragment>
    
    <fragment android:id="@+id/settings"
              android:name="de.rwth_aachen.phyphox.settings.ui.SettingsFragment" />
</navigation>
```

### Metro DI Structure

```kotlin
@MetroScope
interface AppScope

@Module
object AppModule {
    @Provides @IntoAppScope
    fun provideSensorManager(app: Application): SensorManager
    
    @Provides @IntoAppScope
    fun provideBluetoothAdapter(): BluetoothAdapter
}

@Module  
object ExperimentModule {
    @Provides @IntoFragmentScope
    fun provideExperimentViewModel(
        analysisEngine: AnalysisEngine,
        bufferManager: BufferManager
    ): ExperimentViewModel
}
```
