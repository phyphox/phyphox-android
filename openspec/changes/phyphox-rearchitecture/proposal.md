# phyphox-rearchitecture Proposal

## Overview

Refactor phyphox-android from monolithic Activities to a modern MVVM architecture with Clean Architecture principles, using Metro for dependency injection and Navigation 3 for navigation.

## Goals

- **Single Activity**: Replace multiple Activities with one Activity hosting Fragments
- **MVVM Pattern**: Separate ViewModel from View (Compose/View system)
- **Clean Architecture**: Domain → Data → UI layer separation
- **Metro DI**: Replace manual dependency management with Metro
- **Incremental Migration**: Migrate screen-by-screen without breaking functionality

## Scope

### In Scope
- Experiment List → Fragment + MVVM + Compose
- Experiment Screen → Fragment + MVVM (split from 2,558 line Activity)
- Settings → Fragment + MVVM
- Bluetooth scanning flow → MVVM
- File I/O & Export → Clean Architecture layers
- Formula Parser → Pure Kotlin implementation

### Out of Scope (Future Phases)
- Complete UI rewrite to Compose (can be incremental per screen)
- Backend server changes
- iOS platform changes
- Major feature additions

## Constraints

1. **Logic preservation**: All existing behavior must remain identical
2. **Incremental migration**: Each phase must be testable and shippable
3. **No big bang**: Migrate one screen at a time
4. **Test coverage**: Add tests before removing old implementation

## Success Criteria

- [ ] No file > 500 lines (excluding generated code)
- [ ] Business logic unit testable without Android
- [ ] Single Activity with Navigation Component
- [ ] Metro DI manages all dependencies
- [ ] >80% code coverage on core modules
