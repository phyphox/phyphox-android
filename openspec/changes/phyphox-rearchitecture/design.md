# Design Specification

## Phase 0: Foundation Setup

### Tasks

#### 0.1 Rename Experiment.java → ExperimentActivity.java

**Why First?**
- Clears confusion between Activity class and domain model
- Enables creating `Experiment` as domain model
- Must happen before any other refactoring to avoid naming conflicts

**Steps:**
```bash
# 1. Rename file
mv app/src/main/java/de/rwth_aachen/phyphox/Experiment.java \
   app/src/main/java/de/rwth_aachen/phyphox/ExperimentActivity.java

# 2. Update class declaration
sed -i 's/^public class Experiment /class ExperimentActivity /' \
    app/src/main/java/de/rwth_aachen/phyphox/ExperimentActivity.java

# 3. Update AndroidManifest.xml
# android:name="de.rwth_aachen.phyphox.Experiment"
# To: "de.rwth_aachen.phyphox.ExperimentActivity"

# 4. Update all imports and references
./gradlew build  # Find missing references
```

**Files to Check for References:**
- `AndroidManifest.xml`
- `app/build.gradle` (if any test references)
- All Java/Kotlin files that import Experiment

#### 0.2 Add Metro Dependency Injection

**Build.gradle Changes:**
```kotlin
plugins {
    id("org.jetbrains.kotlin.kapt") version "1.9.0"
}

dependencies {
    implementation("io.github.zacsweers:metro-runtime:2.0.0")
    kapt("io.github.zacsweers:metro-compiler:2.0.0")
}
```

**Create DI Modules:**
```kotlin
// AppModule.kt
@MetroScope
interface AppScope

@Module
object AppModule {
    @Provides @IntoAppScope
    fun provideSensorManager(app: Application): SensorManager =
        app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    @Provides @IntoAppScope  
    fun provideBluetoothAdapter(): BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()
    
    @Provides @IntoAppScope
    fun provideLocationManager(app: Application): LocationManager =
        app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
}

// ExperimentModule.kt (to be extended in Phase 2)
@Module
object ExperimentModule {
    // Will add ViewModel, Controller bindings later
}
```

#### 0.3 Set Up Navigation Component

**Navigation Graph:**
```xml
<!-- res/navigation/nav_graph.xml -->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto">
    
    <fragment android:id="@+id/experimentListFragment"
              android:name="de.rwth_aachen.phyphox.experimentlist.ui.ExperimentListFragment">
        <action android:id="@+id/action_open_experiment"
                app:destination="@id/experimentDetail" />
    </fragment>
    
    <fragment android:id="@+id/experimentDetail"
              android:name="de.rwth_aachen.phyphox.experiment.ui.ExperimentFragment">
        <argument android:name="experimentId"
                  app:argType="string" />
    </fragment>
    
    <fragment android:id="@+id/settingsFragment"
              android:name="de.rwth_aachen.phyphox.settings.ui.SettingsFragment" />
</navigation>
```

**MainActivity Setup:**
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val navController = findNavController(R.id.nav_host_fragment)
        setupActionBarWithNavController(navController)
        
        // Handle deep links from ExperimentListActivity's intent filters
        // (keep existing intent filters in manifest, route to correct fragment)
    }
    
    override fun onSupportNavigateUp() =
        findNavController(R.id.nav_host_fragment).navigateUp()
}
```

**Manifest Updates:**
```xml
<!-- Keep ExperimentListActivity but make it a shell that navigates -->
<activity android:name=".MainActivity"
          android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- Or keep ExperimentListActivity as shell that delegates to MainActivity -->
<activity android:name=".ExperimentListActivity"
          android:exported="true">
    <!-- Deep link intent filters here, then navigate to MainActivity -->
</activity>
```

#### 0.4 Extract Pure Domain Model

**Create `Experiment.kt` (domain model):**
```kotlin
// domain/Experiment.kt (pure Kotlin, no Android dependencies)
@Immutable
data class Experiment(
    val id: String,
    val title: String,
    val category: String,
    val views: List<ViewConfiguration>,
    val analysisModules: List<AnalysisModuleConfig>,
    val sensors: List<SensorConfig>
)

// domain/ViewConfiguration.kt
@Immutable
data class ViewConfiguration(
    val title: String,
    val elements: List<ElementConfig>
)

// domain/SensorConfig.kt
@Immutable  
data class SensorConfig(
    val type: SensorType,
    val rate: Int
)
```

**Create `PhyphoxExperiment` wrapper (Android layer):**
```kotlin
// data/PhyphoxExperimentWrapper.kt
class PhyphoxExperimentWrapper(
    private val experiment: domain.Experiment,
    private val sensorManager: SensorManager
) {
    // Android-specific operations
}
```

### Files to Create

```
app/src/main/java/de/rwth_aachen/phyphox/
├── di/
│   ├── AppModule.kt
│   ├── ExperimentModule.kt
│   └── ViewModelFactory.kt
├── navigation/
│   └── NavGraphBuilder.kt
└── domain/  (new package)
    ├── Experiment.kt
    ├── ViewConfiguration.kt
    ├── SensorConfig.kt
    └── AnalysisModuleConfig.kt
```

## Phase 1: Experiment List Migration

### Tasks

#### 1.1 Data Layer

**Repository Interface:**
```kotlin
interface ExperimentRepository {
    suspend fun loadExperiments(): Flow<LoadResult>
    suspend fun getCategories(): List<String>
}
```

**DataSource Implementations:**
- `AssetDataSource` - Load from assets (bundled experiments)
- `FileDataSource` - Load from external storage

#### 1.2 ViewModel Layer

```kotlin
class ExperimentListViewModel(
    private val repository: ExperimentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ExperimentListState())
    val state: StateFlow<ExperimentListState> = _state.asStateFlow()
    
    fun loadExperiments() { /* ... */ }
    fun onSearchChanged(query: String) { /* ... */ }
}
```

#### 1.3 UI Layer

**Fragment + Compose:**
- `ExperimentListFragment.kt`
- `ExperimentListScreen.kt`
- `ExperimentItem.kt`

## Phase 2: Experiment Screen Migration

### Tasks

#### 2.1 Domain Layer Extraction

```kotlin
// domain/MeasurementController.kt
class MeasurementController(
    private val sensorManager: SensorManager,
    private val analysisEngine: AnalysisEngine,
    private val bufferManager: BufferManager
) {
    fun startMeasurement(): Boolean
    fun stopMeasurement()
}

// domain/AnalysisEngine.kt (extracted from Analysis.java)
```

#### 2.2 ViewModel Layer

```kotlin
class ExperimentViewModel(
    private val measurementController: MeasurementController,
    private val bufferManager: BufferManager
) : ViewModel() {
    // State, commands, exports
}
```

## Phase 3-6: Remaining Screens

Follow same pattern:
1. Extract domain models
2. Create repositories/data sources  
3. Build ViewModels with StateFlow
4. Migrate UI to Compose (per screen)
