# Experiment List Screen Specification

## Current State

**File**: `ExperimentListActivity.java` (1,516 lines)

### Responsibilities
- Main entry point
- Display experiment list
- Handle file opening (local files, network URLs, QR codes)
- Bluetooth device scanning and connection
- Create new experiments (simple, Bluetooth, QR code)
- Settings navigation

### Problems
- Too many responsibilities (entry point + UI + I/O + Bluetooth)
- No separation of concerns
- Hard to test
- Tightly coupled to Android components

## Target State

### Architecture
```
MainActivity.kt (shell, renamed from ExperimentListActivity.java)
├── Navigation host
└── Deep link routing

ExperimentListFragment.kt (new)
├── Compose UI
└── ViewModel binding

ExperimentListViewModel.kt (new)
├── List state management
├── Search/filter logic
└── Loading state handling

Repository.kt (new)
├── Experiment data orchestration
└── DataSource delegation

DataSource.kt (interface)
├── loadExperiments() → Flow<List<ExperimentInfo>>
└── getCategories() → List<String>

AssetDataSource.kt (implementation)
├── Load from assets (.phyphox files bundled with app)

FileDataSource.kt (implementation)  
├── Load from external storage
├── Handle ZIP extraction
```

### Data Models

```kotlin
// Domain model (pure Kotlin, no Android dependencies)
@Immutable
data class ExperimentInfo(
    val id: String,                    // Unique identifier
    val title: String,                 // Display title
    val category: String,              // Category name
    val description: String?,          // Optional description
    val icon: String?,                 // Base64 or path to icon
    val isLocal: Boolean,              // Loaded from local storage?
    val crc32: Long                    // File checksum for deduplication
)

// Result type for loading experiments
sealed interface LoadResult {
    data class Success(val experiments: List<ExperimentInfo>) : LoadResult
    data class Error(val message: String) : LoadResult
}

// Navigation destinations
object ExperimentListDestination
data class ExperimentDetailDestination(val experimentId: String)
```

### ViewModel State

```kotlin
@Immutable
data class ExperimentListState(
    val experiments: List<ExperimentInfo> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val error: String? = null,
    val isSelectionActive: Boolean = false,
    val selectedExperiments: Set<String> = emptySet()
)
```

### UI Structure

```kotlin
@Composable
fun ExperimentListScreen(
    viewModel: ExperimentListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = { ExperimentListTopBar(state.searchQuery, onSearchChange = ...) },
        floatingActionButton = { NewExperimentMenu(onNewExperimentClicked = ...) }
    ) {
        LazyColumn {
            items(state.experiments) { experiment ->
                ExperimentItem(experiment) {
                    viewModel.onExperimentSelected(it)
                }
            }
        }
        
        // Loading state
        if (state.isLoading) {
            CircularProgressIndicator()
        }
        
        // Error state  
        if (state.error != null) {
            ErrorRetry(error = state.error, onRetry = { viewModel.loadExperiments() })
        }
    }
}

@Composable
fun ExperimentItem(
    experiment: ExperimentInfo,
    onClick: () -> Unit
) {
    Card(onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(experiment.icon)
            Column {
                Text(text = experiment.title, style = MaterialTheme.typography.headlineSmall)
                Text(text = experiment.category, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```

### Repository Interface

```kotlin
interface ExperimentRepository {
    suspend fun loadExperiments(): Flow<LoadResult>
    suspend fun getCategories(): List<String>
    suspend fun saveExperiment(experiment: ExperimentInfo): Result<Unit>
    suspend fun deleteExperiment(id: String): Result<Unit>
}

// Implementation
class ExperimentRepositoryImpl(
    private val assetDataSource: AssetDataSource,
    private val fileDataSource: FileDataSource
) : ExperimentRepository {
    override suspend fun loadExperiments(): Flow<LoadResult> = flow {
        emit(Resource.loading())
        
        try {
            val assets = assetDataSource.loadExperiments()
            val files = fileDataSource.loadExperiments()
            
            val allExperiments = (assets + files).distinctBy { it.id }
            emit(Resource.success(allExperiments))
        } catch (e: Exception) {
            emit(Resource.error(e.message ?: "Unknown error"))
        }
    }.catch { emit(Resource.error(it.message ?: "Loading failed")) }
    
    // ... other methods
}
```

### Deep Link Support

```kotlin
// MainActivity handles these schemes:
// - phyphox://asset=path/to/experiment.phyphox → Open experiment detail
// - phyphox://file=/path/to/file.phyphox → Open experiment detail  
// - content://... → Check if .phyphox or .zip, then open

class MainActivity : AppCompatActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        val navController = findNavController(R.id.nav_host_fragment)
        when (intent.data?.scheme) {
            "phyphox" -> handlePhyphoxDeepLink(intent.data, navController)
            ContentResolver.SCHEME_FILE -> handleFileDeepLink(intent.data, navController)
            ContentResolver.SCHEME_CONTENT -> handleContentDeepLink(intent.data, navController)
        }
    }
}
```

## Migration Strategy

### Phase 1.1: Extract Data Layer (Week 2)
- [ ] Create `ExperimentInfo.kt` domain model
- [ ] Create `DataSource` interface and implementations
- [ ] Create `Repository` interface
- [ ] Write unit tests for data sources

### Phase 1.2: Extract ViewModel (Week 3)  
- [ ] Create `ExperimentListViewModel.kt`
- [ ] Implement StateFlow state management
- [ ] Add search/filter logic
- [ ] Write ViewModel tests

### Phase 1.3: UI Migration (Week 4)
- [ ] Create `ExperimentListFragment.kt` with Compose
- [ ] Integrate ViewModel with UI
- [ ] Test navigation from MainActivity
- [ ] Verify existing functionality preserved

## Acceptance Criteria

- [ ] Experiment list displays correctly
- [ ] Search/filter works as before
- [ ] File opening (local/network/QR) works as before  
- [ ] Bluetooth scanning works as before
- [ ] New experiment creation flows work as before
- [ ] Settings navigation works as before
- [ ] Deep links open correct screens
- [ ] All existing tests pass after migration
