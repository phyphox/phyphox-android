# Tasks

## Phase 0: Foundation (Priority: HIGH)

### 0.1 Rename Experiment.java → ExperimentActivity.java
- [x] Rename file from `Experiment.java` to `ExperimentActivity.java`
- [x] Update class declaration from `public class Experiment` to `class ExperimentActivity`
- [x] Update AndroidManifest.xml activity name reference
- [x] Update all import statements referencing Experiment
- [x] Verify build succeeds with rename

### 0.2 Add Metro Dependency Injection
- [ ] Add Metro runtime dependency to app/build.gradle
- [ ] Enable kapt plugin for Metro compiler
- [ ] Create `di/AppModule.kt` with AppScope and Android dependencies
- [ ] Create `di/ViewModelFactory.kt` for ViewModel injection
- [ ] Write unit test: Verify Metro DI can provide SensorManager

### 0.3 Set Up Navigation Component
- [ ] Add Navigation Component dependency to app/build.gradle
- [ ] Create `res/navigation/nav_graph.xml`
- [ ] Configure MainActivity as navigation host
- [ ] Add fragment destinations for ExperimentList and ExperimentDetail
- [ ] Write integration test: Verify navigation from list to detail

### 0.4 Extract Pure Domain Model
- [ ] Create `domain/Experiment.kt` (pure Kotlin, no Android dependencies)
- [ ] Copy relevant fields from PhyphoxExperiment.java
- [ ] Remove Android imports, make all fields @Immutable
- [ ] Create wrapper class for Android-specific operations
- [ ] Write unit test: Verify Experiment model can be constructed

### 0.5 Update Build Configuration
- [ ] Add Compose dependency if not present (for future UI migration)
- [ ] Configure Compose compiler plugin
- [ ] Set up resource prefixes for new packages
- [ ] Write build verification test

**Estimated Time**: 2 weeks

---

## Phase 1: Experiment List Migration (Priority: HIGH)

### 1.1 Data Layer - Repository
- [ ] Create `experimentlist/data/Repository.kt` interface
- [ ] Implement `loadExperiments()` returning Flow<LoadResult>
- [ ] Add error handling with Result type
- [ ] Write unit test: Verify repository loads experiments from assets

### 1.2 Data Layer - DataSource
- [ ] Create `experimentlist/data/DataSource.kt` interface
- [ ] Implement `AssetDataSource.kt` (load from bundled .phyphox files)
- [ ] Implement `FileDataSource.kt` (load from external storage, ZIP handling)
- [ ] Write unit tests for both data sources

### 1.3 Domain Model
- [ ] Create `experimentlist/model/ExperimentInfo.kt`
- [ ] Extract relevant fields from ExperimentShortInfo
- [ ] Make all fields immutable
- [ ] Add equals/hashCode for deduplication
- [ ] Write unit test: Verify model can be constructed

### 1.4 ViewModel Layer
- [ ] Create `experimentlist/viewmodel/ExperimentListViewModel.kt`
- [ ] Implement StateFlow<ExperimentListState>
- [ ] Add search/filter logic
- [ ] Add loading state management
- [ ] Write ViewModel tests (state transitions, search filtering)

### 1.5 UI Layer - Fragment
- [ ] Create `experimentlist/ui/ExperimentListFragment.kt`
- [ ] Set up Compose content view
- [ ] Integrate with ViewModel via StateFlow
- [ ] Add error state handling
- [ ] Write integration test: Verify list displays correctly

### 1.6 UI Layer - Components
- [ ] Create `experimentlist/ui/ExperimentItem.kt` composable
- [ ] Implement click handler for experiment selection
- [ ] Add category filtering UI
- [ ] Write UI tests for list items

### 1.7 Deep Link Integration
- [ ] Configure deep links in nav_graph.xml
- [ ] Handle `phyphox://asset=` scheme → open experiment detail
- [ ] Handle file content URIs → check if .phyphox or .zip
- [ ] Write test: Verify deep links navigate to correct screen

**Estimated Time**: 3 weeks

---

## Phase 2: Experiment Screen Migration (Priority: HIGH)

### 2.1 Domain Layer - MeasurementController
- [ ] Extract `measurementController` methods from Experiment.java
- [ ] Create `experiment/domain/MeasurementController.kt`
- [ ] Implement start/stop/pause logic (pure Kotlin)
- [ ] Test without Android dependencies

### 2.2 Domain Layer - AnalysisEngine
- [ ] Extract analysis orchestration from Analysis.java
- [ ] Create `experiment/domain/AnalysisEngine.kt`
- [ ] Keep native code as-is, wrap in Kotlin interface
- [ ] Write unit tests for formula execution

### 2.3 Domain Layer - BufferManager
- [ ] Wrap DataBuffer with thread-safe access
- [ ] Implement buffer operations (append, clear, get)
- [ ] Add notification system for updates
- [ ] Write tests: Verify buffer operations are thread-safe

### 2.4 ViewModel Layer
- [ ] Create `experiment/viewmodel/ExperimentViewModel.kt`
- [ ] Implement StateFlow with measurement state
- [ ] Add graph state management (zoom, range, units)
- [ ] Add Bluetooth device state
- [ ] Write comprehensive ViewModel tests

### 2.5 UI Layer - Fragment
- [ ] Create `experiment/ui/ExperimentFragment.kt`
- [ ] Set up Compose screen with tabs
- [ ] Implement start/stop/pause buttons
- [ ] Add real-time graph display
- [ ] Write integration test: Verify measurement flow

### 2.6 UI Layer - Graph Components
- [ ] Create `experiment/ui/GraphView.kt` composable
- [ ] Implement zoom functionality
- [ ] Add range slider controls
- [ ] Write tests for graph rendering

**Estimated Time**: 4 weeks

---

## Phase 3: Settings Migration (Priority: MEDIUM)

### 3.1 Data Layer
- [ ] Create `settings/data/PreferenceRepository.kt`
- [ ] Wrap SharedPreferences with type-safe access
- [ ] Add dark mode state management
- [ ] Write tests for preference operations

### 3.2 ViewModel Layer
- [ ] Create `settings/viewmodel/SettingsViewModel.kt`
- [ ] Implement theme switching logic
- [ ] Add settings state management
- [ ] Write ViewModel tests

### 3.3 UI Layer
- [ ] Create `settings/ui/SettingsScreen.kt` (Compose)
- [ ] Implement dark mode toggle
- [ ] Add preference headers if needed
- [ ] Write UI tests for settings flow

**Estimated Time**: 2 weeks

---

## Phase 4: Bluetooth Flow Migration (Priority: MEDIUM)

### 4.1 Domain Layer
- [ ] Extract BLE connection logic from Bluetooth.kt
- [ ] Create `bluetooth/domain/ConnectionManager.kt`
- [ ] Implement reconnection logic
- [ ] Write tests for connection state machine

### 4.2 Data Layer
- [ ] Create `bluetooth/data/BLEConnection.kt`
- [ ] Wrap GATT operations with async callbacks
- [ ] Add characteristic mapping
- [ ] Write tests for GATT operations

### 4.3 ViewModel Layer
- [ ] Create `bluetooth/viewmodel/BluetoothViewModel.kt`
- [ ] Implement scan state management
- [ ] Add device list state
- [ ] Write tests for Bluetooth state transitions

### 4.4 UI Layer
- [ ] Create `bluetooth/ui/BluetoothScanScreen.kt` (Compose)
- [ ] Implement device scanning
- [ ] Add device selection flow
- [ ] Write UI tests for scan and connect

**Estimated Time**: 2 weeks

---

## Phase 5: File I/O & Export Migration (Priority: MEDIUM)

### 5.1 File Operations
- [ ] Create `io/file/ExperimentLoader.kt`
- [ ] Extract XML parsing from PhyphoxFile.java
- [ ] Add ZIP extraction handler
- [ ] Write tests for file loading

### 5.2 Export System
- [ ] Create `io/export/CSVExporter.kt`
- [ ] Create `io/export/JSONExporter.kt`
- [ ] Create `io/export/XLSXExporter.kt`
- [ ] Keep existing DataExport.java as fallback during migration
- [ ] Write tests for each export format

### 5.3 State Save/Load
- [ ] Extract state file writing from Experiment.java
- [ ] Create `io/state/StateSaver.kt` / `StateLoader.kt`
- [ ] Implement async save/load
- [ ] Write tests for state persistence

**Estimated Time**: 2 weeks

---

## Phase 6: Formula Parser Migration (Priority: LOW)

### 6.1 Lexer & Parser
- [ ] Create `formula/parser/Lexer.kt`
- [ ] Create `formula/parser/Parser.kt`
- [ ] Implement expression parsing logic
- [ ] Write parser tests with various expressions

### 6.2 AST Definitions
- [ ] Create `formula/ast/Expression.kt` (sealed interface)
- [ ] Create binary operation nodes
- [ ] Create function call nodes
- [ ] Add visitor pattern for evaluation

### 6.3 Evaluator
- [ ] Create `formula/evaluator/Evaluator.kt`
- [ ] Implement buffer references ([1], [1_])
- [ ] Handle error cases (empty buffers, invalid refs)
- [ ] Write tests matching existing FormulaParserTest cases

**Estimated Time**: 2 weeks

---

## Testing & Verification

### Per-Phase Testing
Each phase must include:
- Unit tests for domain classes
- ViewModel state transition tests  
- UI integration tests (Compose or View system)
- End-to-end workflow tests

### Migration Verification Checklist
Before considering a phase complete:
- [ ] All existing tests pass with new implementation
- [ ] No functionality regression detected in manual testing
- [ ] Code coverage ≥ 80% for migrated modules
- [ ] Performance metrics within acceptable range
- [ ] Documentation updated (comments, README)

---

## Total Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| Phase 0: Foundation | 2 weeks | Pending |
| Phase 1: Experiment List | 3 weeks | Pending |
| Phase 2: Experiment Screen | 4 weeks | Pending |
| Phase 3: Settings | 2 weeks | Pending |
| Phase 4: Bluetooth | 2 weeks | Pending |
| Phase 5: I/O & Export | 2 weeks | Pending |
| Phase 6: Formula Parser | 2 weeks | Pending |
| **Total** | **17 weeks (~4 months)** | |
