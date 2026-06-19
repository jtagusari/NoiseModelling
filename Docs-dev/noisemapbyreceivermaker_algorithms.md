# NoiseMapByReceiverMaker Algorithms

- [NoiseMapByReceiverMaker Algorithms](#noisemapbyreceivermaker-algorithms)
  - [Concepts \& Overview](#concepts--overview)
  - [GridMapMaker — Base Architecture](#gridmapmaker--base-architecture)
  - [Settings Classes](#settings-classes)
    - [TableInputSettings](#tableinputsettings)
    - [PropagationSettings](#propagationsettings)
    - [ComputationSettings](#computationsettings)
    - [EmissionInputSettings](#emissioninputsettings)
    - [CalculationIOSettings](#calculationiosettings)
  - [Receiver Generation Algorithms](#receiver-generation-algorithms)
  - [Cell-Based Processing](#cell-based-processing)
  - [Grid Initialization](#grid-initialization)
  - [Scene Preparation](#scene-preparation)
    - [Overview](#overview)
    - [Key Responsibilities](#key-responsibilities)
    - [Scene Contents](#scene-contents)
  - [Loader Context Split \& Settings Lifecycle](#loader-context-split--settings-lifecycle)
    - [Overview](#overview-1)
    - [DefaultTableLoader Initialization Behavior](#defaulttableloader-initialization-behavior)
  - [Path Finding Integration](#path-finding-integration)
    - [Overview](#overview-2)
    - [Key Steps](#key-steps)
  - [Attenuation Computation](#attenuation-computation)
  - [Result Aggregation](#result-aggregation)

## Concepts & Overview

The `NoiseMapByReceiverMaker` class is the central coordinator for computing noise maps at specified receiver points. It implements a cell-based processing approach to handle large-scale noise propagation calculations efficiently, integrating geometry loading, path finding, and acoustic attenuation computation.

**Overall Context**: `NoiseMapByReceiverMaker` orchestrates all phases of the NoiseModelling computation scheme. For the complete computation pipeline context including data preparation, receiver generation, and result aggregation, see [computation_scheme.md](computation_scheme.md).

The class extends `GridMapMaker` and orchestrates the complete noise mapping workflow:

1. **Grid Division**: Divides the computation domain into manageable cells
2. **Scene Preparation**: Loads buildings, sources, receivers, and terrain for each cell
3. **Path Finding**: Computes propagation paths from sources to receivers
4. **Attenuation Calculation**: Applies CNOSSOS-EU algorithms to compute sound levels
5. **Result Aggregation**: Combines results across all cells

**Key Responsibilities**:
- Cell-based spatial decomposition for memory efficiency
- Database integration for input data loading
- Multi-threaded processing coordination
- Integration with PathFinder and AttenuationComputeOutput components
- Read-only context exposure for loader initialization and per-cell scene creation

## GridMapMaker — Base Architecture

`NoiseMapByReceiverMaker` extends `GridMapMaker`, which provides the foundational grid-based processing framework.

```plantuml
@startuml
class GridMapMaker {
  # TableInputSettings tableInputSettings
  # PropagationSettings propagationSettings
  # int gridDim
  # Envelope mainEnvelope

  + initialize(Connection, ProgressVisitor): void
  + getCellEnv(CellIndex): Envelope
  + getCellWidth(): double
  + getCellHeight(): double
}

class NoiseMapByReceiverMaker extends GridMapMaker {
  - TableLoader tableLoader
  - IComputeRaysOutFactory computeRaysOutFactory
  - CalculationIOSettings calculationIOSettings
  - EmissionInputSettings emissionInputSettings
  - int threadCount

  + {static} Builder
  + run(Connection, ProgressVisitor): void
  + initialize(Connection, ProgressVisitor): void
  + evaluateCell(Connection, CellIndex, ProgressVisitor, Set<Long>): CutPlaneVisitorFactory
  + requestCellScene(Connection, CellIndex, Set<Long>): SceneWithEmission
  + getLoaderInitContext(): LoaderInitContext
  + getCellSceneContext(): CellSceneContext
  + getEmissionInputSettings(): EmissionInputSettingsView
  + searchPopulatedCells(Connection): Map<CellIndex, Integer>
}

interface LoaderInitContext
interface CellSceneContext

NoiseMapByReceiverMaker ..|> LoaderInitContext
NoiseMapByReceiverMaker ..|> CellSceneContext


note right of NoiseMapByReceiverMaker
  **Specialized for receiver-based**
  **noise mapping with:**
  - Receiver table management
  - Scene preparation per cell
  - Emission-aware processing
end note
@enduml
```

**Key Extensions**:

- **Receiver Management**: Receiver table name is accessed via `tableInputSettings.getReceiverTableName()`
- **Scene Preparation**: Creates `SceneWithEmission` objects with source emission data
- **Emission Integration**: Coordinates with emission calculation components
- **Builder pattern**: Instances are created via `NoiseMapByReceiverMaker.Builder`; table/propagation/IO settings are supplied as dedicated objects (`TableInputSettings`, `PropagationSettings`, `CalculationIOSettings`, `EmissionInputSettings`)

## Settings Classes

`NoiseMapByReceiverMaker` accepts five dedicated settings objects.
All classes follow the same immutable Builder pattern: construct via `new Xxx.Builder()...build()`.

### TableInputSettings

Specifies which database tables and columns hold the geometry and acoustic data.

| Field | Default | Description |
| --- | --- | --- |
| `buildingTableName` | — | Building polygon table; must not be empty when buildings are present |
| `buildingHeightField` | `"HEIGHT"` | Column name for building height above local ground |
| `buildingAlphaField` | `"G"` | Column name for wall absorption coefficient |
| `buildingDefaultAlpha` | `100000` | Fallback absorption value when the per-feature column is absent |
| `buildingGeometryZ` | `false` | When `true`, building polygon Z is treated as absolute altitude (sea level to top of wall) |
| `sourceTableName` | — (required) | Sound source geometry table; must be non-null (`NoiseMapByReceiverMaker.Builder` enforces this) |
| `sourceLevelFieldName` | `"DB_M"` | Column name for source power level |
| `sourceHasAbsoluteZCoordinates` | `false` | When `true`, source Z is absolute altitude; otherwise relative to ground |
| `receiverTableName` | — (required) | Receiver point table; must be non-null (`NoiseMapByReceiverMaker.Builder` enforces this) |
| `receiverHasAbsoluteZCoordinates` | `false` | When `true`, receiver Z is absolute altitude; otherwise relative to ground |
| `groundTableName` | `""` | Ground absorption polygon table (empty = not used) |
| `terrainTableName` | `""` | DEM point table (empty = not used) |
| `bridgePointsTableName` | `""` | Bridge point table (empty = not used) |
| `periodAtmosphericSettingsTableName` | `""` | Table for period-specific atmospheric parameters (empty = not used) |

### PropagationSettings

Defines the physical assumptions under which sound propagation is computed: which phenomena to include and the spatial extent of the calculation domain.

| Field | Default | Description |
| --- | --- | --- |
| `maximumPropagationDistance` | `750` m | Maximum distance from a source at which propagation paths are traced |
| `maximumReflectionDistance` | `100` m | Maximum distance from a source at which specular reflections are searched |
| `gs` | `0` | Default ground absorption coefficient (0 = acoustically hard, 1 = fully absorptive) |
| `soundReflectionOrder` | `2` | Number of successive specular reflections to compute (0 = disabled) |
| `bodyBarrier` | `false` | Enable multiple reflections between a train body and a trackside screen (railway scenarios) |
| `computeHorizontalDiffraction` | `true` | Compute horizontal (plan-view) diffraction around obstacles |
| `computeVerticalDiffraction` | `true` | Compute vertical diffraction over obstacles |
| `coefficientVersion` | `2` | CNOSSOS-EU coefficient version (1 = 2015, 2 = 2020) |

### ComputationSettings

Controls how the computation is executed: domain decomposition and geometry pre-processing for memory efficiency. These parameters do not affect the physical propagation model.

| Field | Default | Description |
| --- | --- | --- |
| `gridDim` | `0` | Number of grid cells per side for domain decomposition (0 = auto-computed from envelope and propagation distance) |
| `groundSurfaceSplitSideLength` | `200` m | Side length for subdividing large ground-absorption polygons into tiles before intersection |

### EmissionInputSettings

Controls how source emission data is read from the database.

| Field | Default | Description |
| --- | --- | --- |
| `inputMode` | `INPUT_MODE_GUESS` | How emission data is located in the database (see table below) |
| `sourcesEmissionTableName` | `""` | Separate emission table name (empty = emission data is in the source geometry table) |
| `sourceEmissionPrimaryKeyField` | `"IDSOURCE"` | Source ID column in the emission table |
| `directivityTableName` | `""` | Source directivity table name (empty = omnidirectional) |
| `useTrainDirectivity` | `false` | Use built-in CNOSSOS railway directivity spheres |
| `frequencyFieldPrepend` | `"HZ"` | Prefix used to identify frequency band columns (e.g. `HZ1000`) |

**`INPUT_MODE` values**:

| Value | Description |
| --- | --- |
| `INPUT_MODE_GUESS` | Auto-detect from available columns at initialization time |
| `INPUT_MODE_LW` | Separate emission table contains per-period power levels (`HZ*` columns) |
| `INPUT_MODE_LW_DEN` | Source geometry table contains DEN power levels |
| `INPUT_MODE_TRAFFIC_FLOW` | Separate emission table contains traffic-flow data (`LV_SPD`, etc.) |
| `INPUT_MODE_TRAFFIC_FLOW_DEN` | Source geometry table contains DEN traffic-flow data |
| `INPUT_MODE_ATTENUATION` | Attenuation-only mode; no emission data is looked up |

When `INPUT_MODE_GUESS` is set, mode inference runs once inside `DefaultTableLoader.initialize()` and the resolved mode is reused for every subsequent `createScene()` call.

### CalculationIOSettings

Controls output configuration and computation accuracy trade-offs.

| Field | Default | Description |
| --- | --- | --- |
| `receiversLevelTable` | `"RECEIVERS_LEVEL"` | Output table name for receiver noise levels |
| `mergeSources` | `true` | When `true`, contributions from all sources are summed at each receiver |
| `maximumError` | `0` dB | Stop adding source contributions when the remaining sum is below this threshold (0 = compute all) |
| `exportRaysMethod` | `NONE` | How to export propagation paths: `NONE` or `TO_RAYS_TABLE` |
| `raysTable` | `"RAYS"` | Output table name for propagation path data |
| `exportAttenuationMatrix` | `false` | Export per-source per-receiver attenuation matrix |
| `keepAbsorption` | `false` | Retain detailed per-path absorption data in exported rays |
| `maximumRaysOutputCount` | `0` | Maximum number of exported rays (0 = unlimited) |
| `computeLAEQOnly` | `false` | When `true`, compute only L_Aeq (faster; skips per-source levels) |
| `noSourceNoiseLevel` | `-99` dB | Noise level assigned to receivers when no source is present |
| `outputMaximumQueue` | `50000` | Maximum size of the database write queue |
| `dropResultsTable` | `true` | Drop the output table before writing results |
| `exportReceiverPosition` | `false` | Include receiver coordinates in the output table |
| `CSVProfilerOutputPath` | `null` | Directory for profiler CSV output (`null` = disabled) |
| `CSVProfilerWriteInterval` | `60` s | Interval between profiler CSV writes |

---

## Receiver Generation Algorithms

`NoiseMapByReceiverMaker` requires receivers to be pre-generated and stored in a database table. Multiple algorithms are available for receiver generation, each suited for different use cases:

- **DelaunayReceiversMaker**: Generates adaptive mesh using constrained Delaunay triangulation
- **Regular Grid**: Creates uniform grid with constant spacing
- **Building Grid**: Places receivers around building facades

For detailed information about receiver generation algorithms, see [receiver_generation_algorithms.md](receiver_generation_algorithms.md).

## Cell-Based Processing

The core processing follows a cell-based decomposition strategy to manage computational complexity and memory usage.

```plantuml
@startuml
title Cell-Based Processing Flow

start

:Initialize computation envelope\nfrom receiver locations;

:Divide envelope into grid cells\n(determine gridDim);

:Identify populated cells\n(cells containing receivers);

:For each populated cell;

partition "Cell Processing" {
  :evaluateCell(cellIndex);
  
  partition "Scene Preparation" {
    :requestCellScene() → SceneWithEmission;
    note right
      Loads:
      - Buildings in expanded envelope
      - Sources in expanded envelope
      - Receivers in cell envelope
      - Terrain data (DEM)
      - Ground areas
    end note
  }
  
  partition "Path Finding" {
    :PathFinder.run(scene, visitor);
    note right
      Computes propagation paths
      for all source-receiver pairs
      in the cell
    end note
  }
  
  partition "Attenuation" {
    :AttenuationComputeOutput processes\nCutProfile objects;
    note right
      Applies CNOSSOS-EU algorithms
      to compute sound levels
    end note
  }
}

:Aggregate results across cells;

stop

@enduml
```

## Grid Initialization

Before processing individual cells, the system initializes the computational grid to organize the spatial domain efficiently.

**Grid Initialization Process**:
- **Computation Envelope Calculation**: Determines the overall bounding box from all receiver locations to define the computation domain
- **Grid Dimension Determination**: Calculates the optimal grid size (`gridDim`) based on cell dimensions and propagation parameters
- **Cell Placement**: Arranges cells in a regular grid pattern covering the entire computation envelope
- **Cell Indexing**: Assigns unique indices to each cell for tracking and processing

**Key Parameters**:
- **Cell Width/Height**: Determined by `getCellWidth()` and `getCellHeight()` methods from base `GridMapMaker`
- **Grid Coverage**: Ensures complete coverage of receiver locations with minimal overhead

**Cell Size and Count Determination**:
- **Cell Size Calculation**: Cell dimensions are typically set based on propagation parameters to balance memory usage and computational efficiency. For example, cell width/height may be derived from `maximumPropagationDistance` to ensure that propagation paths within a cell can be computed without excessive memory overhead.
- **Grid Dimension (gridDim)**: Calculated as the number of cells needed to cover the computation envelope in both x and y directions. This is determined by dividing the envelope width/height by the cell width/height and rounding up to ensure full coverage.
- **Total Cell Count**: Equals `gridDim × gridDim`, representing the total number of cells in the grid. Only cells containing receivers (populated cells) are processed, optimizing performance for sparse receiver distributions.

**Cell Envelope Calculation**:
- **Cell Envelope**: Bounding box of current processing cell
- **Expanded Envelope**: Cell envelope expanded by `maximumPropagationDistance + 2 × maximumReflectionDistance`
- **Purpose**: Ensures all relevant geometry is loaded for accurate propagation

## Scene Preparation

The `requestCellScene()` method creates a complete `SceneWithEmission` object containing all necessary data for propagation computation. This method acts as the bridge between database storage and the in-memory `Scene` representation, delegating the actual scene construction details to the `TableLoader` interface (typically `DefaultTableLoader`).

### Overview

```plantuml
@startuml
title Scene Preparation — Overview

[NoiseMapByReceiverMaker.requestCellScene()] as requestCellScene

[TableLoader.createScene()] as createScene

[SceneWithEmission] as scene

requestCellScene --> createScene : cell envelope,\nexpanded envelope
createScene --> scene : ProfileBuilder,\nsources, receivers,\nacoustic parameters

note right of requestCellScene
  **Input:** Cell index, connection, skip set
  **Output:** SceneWithEmission ready for propagation
end note

@enduml
```

### Key Responsibilities

- **Envelope Computation**: Calculate cell boundary and expanded envelope (expanded by `maximumPropagationDistance + 2 × maximumReflectionDistance`)
- **TableLoader Delegation**: Invoke `TableLoader.createScene()` to construct the complete scene
- **Context Delegation**: Pass `CellSceneContext` for per-cell geometry/physics parameters and `LoaderInitContext` for one-time loader setup
- **Receiver Deduplication**: Track receivers already processed to avoid redundant computation across cell boundaries

### Scene Contents

The returned `SceneWithEmission` contains:
- **Geometry**: Buildings, walls, bridges, terrain (via finalized `ProfileBuilder`)
- **Sources and Receivers**: Acoustic sources and receiver points for the cell
- **Acoustic Configuration**: Frequency arrays, attenuation parameters, directivity attributes, and propagation settings

## Loader Context Split & Settings Lifecycle

The current implementation explicitly splits loader dependencies into two read-only context interfaces:

- `LoaderInitContext`: values required once in `TableLoader.initialize(...)` (source/emission table names, frequency prefix, scene input settings, verbose flag)
- `CellSceneContext`: values required for each cell in `TableLoader.createScene(...)` (cell envelope, geometry factory, propagation flags, input table names)

`NoiseMapByReceiverMaker` implements both interfaces and exposes them through:

- `getLoaderInitContext()`
- `getCellSceneContext()`

### Overview

```plantuml
@startuml
title Loader Context and Settings Flow

[NoiseMapByReceiverMaker.initialize()] as init
[getLoaderInitContext()] as initCtx
[DefaultTableLoader.initialize()] as loaderInit

[NoiseMapByReceiverMaker.requestCellScene()] as request
[getCellSceneContext()] as cellCtx
[DefaultTableLoader.createScene()] as create

init --> initCtx
initCtx --> loaderInit

request --> cellCtx
cellCtx --> create

@enduml
```

### DefaultTableLoader Initialization Behavior

`DefaultTableLoader.initialize(...)` now takes a snapshot copy of `EmissionInputSettingsView` into a mutable `EmissionInputSettings` instance. This has two important consequences:

1. **Input mode guessing is persistent**: when mode is `INPUT_MODE_GUESS`, the guessed mode is stored in the loader snapshot and reused later by `createScene(...)`.
2. **Per-cell behavior remains consistent**: every scene built by the same loader instance uses the same resolved input mode and associated settings.

This avoids a mismatch where mode inference would occur at initialization time but scene construction would still use an unresolved `INPUT_MODE_GUESS` view.

For detailed step-by-step scene construction workflow and implementation patterns, see [Typical workflow of creating Scene](scene.md#typical-workflow-of-creating-scene) in the scene documentation.

## Path Finding Integration

`NoiseMapByReceiverMaker` integrates with the PathFinder component to compute propagation paths for all source-receiver pairs within a computation cell. The `evaluateCell()` method orchestrates this integration by creating a prepared `SceneWithEmission` and passing it to `PathFinder.run()` with an attenuation visitor for result collection.

### Overview

```plantuml
@startuml
title Path Finding Integration — Overview

[NoiseMapByReceiverMaker.evaluateCell()] as eval
[PathFinder.run()] as finder
[AttenuationComputeOutput] as atten

eval --> finder : SceneWithEmission, visitor
finder --> atten : cut-plane results per receiver
atten --> eval : aggregated noise levels

@enduml
```

### Key Steps

1. **Scene Creation**: `requestCellScene()` loads cell geometry, sources, and receivers into a finalized `SceneWithEmission`
2. **PathFinder Invocation**: `PathFinder.run(scene, visitor)` orchestrates per-receiver path-finding and propagation computation
3. **Result Collection**: Results from path-finding are collected by the `AttenuationComputeOutput` visitor which computes acoustic attenuation
4. **Cell Aggregation**: Per-receiver results are aggregated and contribute to final noise map

For detailed path-finding algorithms, receiver processing, and profile computation, see [Cell Evaluation Integration](pathfinder_algorithms.md#cell-evaluation-integration) and [Finding Paths](pathfinder_algorithms.md#finding-paths) in the PathFinder documentation.

## Attenuation Computation

The attenuation computation is delegated to the `AttenuationComputeOutput` component, which implements the CNOSSOS-EU algorithms.
See [AttenuationComputeOutput architecture and algorithms](attenuationcomputeoutput_algorithms.md) for detailed information.

```plantuml
@startuml
title Attenuation Computation Integration

[NoiseMapByReceiverMaker] as nm

package "AttenuationComputeOutput" #LightBlue {
}

package "AttenuationVisitor" #LightGreen {
}

package "AttenuationCnossos" #LightCoral {
}

nm --> "AttenuationComputeOutput" : Creates factory

"AttenuationComputeOutput" --> "AttenuationVisitor" : Creates per thread
"AttenuationVisitor" --> "AttenuationCnossos" : Computes attenuation

"AttenuationVisitor" --> "AttenuationComputeOutput" : Returns results
"AttenuationComputeOutput" --> nm : Final aggregated results

note right of "AttenuationCnossos"
  **CNOSSOS-EU Implementation:**
  - Geometric divergence
  - Atmospheric absorption
  - Ground effects
  - Diffraction
  - Reflection
end note

@enduml
```

**Attenuation Flow**:
1. **Profile Processing**: `AttenuationVisitor` receives `CutProfile` objects from PathFinder
2. **CNOSSOS-EU Application**: Each profile processed using `AttenuationCnossos.computeCnossosAttenuation()`
3. **Component Calculation**: Computes ADiv, AAtm, ABoundary, ARef, and retro-diffraction
4. **Result Accumulation**: Attenuation levels accumulated per receiver
5. **Aggregation**: Results combined across all threads and cells

## Result Aggregation

Final results are aggregated from all processed cells and threads.

```plantuml
@startuml
title Result Aggregation and Output

[NoiseMapByReceiverMaker.run()] as run

package "Cell Processing" #LightBlue {
  [evaluateCell() × N cells]
  [AttenuationComputeOutput per cell]
}

package "Result Collection" #LightGreen {
  [Concurrent data structures]
  [ReceiverNoiseLevel accumulation]
  [Thread-safe merging]
}

package "Output Processing" #LightYellow {
  [IComputeRaysOut implementation]
  [Database result writing]
  [Progress reporting]
}

run --> [evaluateCell() × N cells] : Parallel/cell processing

[evaluateCell() × N cells] --> [Concurrent data structures] : Thread-safe accumulation
[Concurrent data structures] --> [ReceiverNoiseLevel accumulation] : Per receiver results

[ReceiverNoiseLevel accumulation] --> [Output Processing] : Final results
[Output Processing] --> [run] : Completion

note right of [Concurrent data structures]
  **Thread Safety:**
  - ConcurrentLinkedDeque for results
  - Atomic counters for statistics
  - Lock-free accumulation
end note

@enduml
```

**Output Components**:
- **Receiver Noise Levels**: Sound levels at each receiver point
- **Path Statistics**: Computation metrics (paths processed, obstacles tested, etc.)
- **Progress Information**: Processing status for UI feedback
- **Database Integration**: Results written to output tables
