# NoiseMapByReceiverMaker Algorithms

- [NoiseMapByReceiverMaker Algorithms](#noisemapbyreceivermaker-algorithms)
  - [Concepts \& Overview](#concepts--overview)
  - [GridMapMaker — Base Architecture](#gridmapmaker--base-architecture)
  - [DelaunayReceiversMaker — Receiver Generation](#delaunayreceiversmaker--receiver-generation)
  - [Cell-Based Processing](#cell-based-processing)
  - [Grid Initialization](#grid-initialization)
  - [Scene Preparation](#scene-preparation)
  - [Path Finding Integration](#path-finding-integration)
  - [Attenuation Computation](#attenuation-computation)
  - [Result Aggregation](#result-aggregation)

## Concepts & Overview

The `NoiseMapByReceiverMaker` class is the central coordinator for computing noise maps at specified receiver points. It implements a cell-based processing approach to handle large-scale noise propagation calculations efficiently, integrating geometry loading, path finding, and acoustic attenuation computation.

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

## GridMapMaker — Base Architecture

`NoiseMapByReceiverMaker` extends `GridMapMaker`, which provides the foundational grid-based processing framework.

```plantuml
@startuml
class GridMapMaker {
  + String buildingsTableName
  + String sourcesTableName
  + double maximumPropagationDistance
  + double maximumReflectionDistance
  + int soundReflectionOrder
  + boolean bodyBarrier
  + boolean computeVerticalDiffraction
  + boolean computeHorizontalDiffraction
  
  + initialize(Connection): void
  + run(ProgressVisitor): IComputeRaysOut
  + getCellEnv(): Envelope
  + getCellWidth(): double
  + getCellHeight(): double
}

class NoiseMapByReceiverMaker extends GridMapMaker {
  + String receiverTableName
  + TableLoader tableLoader
  + IComputeRaysOutFactory computeRaysOutFactory
  + NoiseMapDatabaseParameters noiseMapDatabaseParameters
  + SceneDatabaseInputSettings sceneDatabaseInputSettings
  
  + NoiseMapByReceiverMaker(buildings, sources, receivers)
  + run(ProgressVisitor): IComputeRaysOut
  + evaluateCell(Connection, CellIndex, ProgressVisitor): void
  + prepareCell(Connection, CellIndex, Set<Long>): SceneWithEmission
  + searchPopulatedCells(Connection): Map<CellIndex, Integer>
}


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
- **Receiver Management**: Handles receiver table and primary key tracking
- **Scene Preparation**: Creates `SceneWithEmission` objects with source emission data
- **Emission Integration**: Coordinates with emission calculation components

## DelaunayReceiversMaker — Receiver Generation

`DelaunayReceiversMaker` is a specialized implementation that generates receiver points using constrained Delaunay triangulation. This approach creates a triangular mesh that respects building geometries and road constraints, producing receivers distributed according to triangle vertices and an isosurface-based placement strategy.

```plantuml
@startuml
class GridMapMaker {
  + String buildingsTableName
  + String sourcesTableName
  + double maximumPropagationDistance
}

class DelaunayReceiversMaker extends GridMapMaker {
  + String verticesTableName
  + String trianglesTableName
  + double roadWidth
  + double maximumArea
  + double receiverHeight
  + double buildingBuffer
  + double epsilon
  + double geometrySimplificationDistance
  + boolean isoSurfaceInBuildings
  
  + run(Connection, verticesTableName, trianglesTableName): void
  + generateReceivers(Connection, cellI, cellJ, ...): void
  + computeDelaunay(LayerDelaunay, Envelope, ...): void
  + feedDelaunay(List<Building>, LayerDelaunay, ...): void
  + fetchCellSource(Connection, Envelope, ...): void
  + generateResultTable(Connection, receiverTable, trianglesTable, ...): void
}

class LayerTinfour {
  + setEpsilon(double): void
  + setMaxArea(double): void
  + addPolygon(Polygon, int): void
  + addVertex(Coordinate): void
  + processDelaunay(): void
  + getVertices(): List<Coordinate>
  + getTriangles(): List<Triangle>
}

DelaunayReceiversMaker --> LayerTinfour : Uses for triangulation

note right of DelaunayReceiversMaker
  **Specialized for Delaunay-based**
  **receiver generation with:**
  - Constrained triangulation
  - Building/road obstacles
  - Mesh quality control
  - Triangle and vertex output
end note
@enduml
```

**Key Characteristics**:
- **Constrained Delaunay Triangulation**: Generates triangles respecting building boundaries and road centerlines as constraints
- **Mesh Quality Control**: Uses `maximumArea` parameter to control triangle size and ensure adequate receiver density
- **Building-Aware**: Places receivers outside buildings (when `isoSurfaceInBuildings=false`) or includes building interiors (when `true`)
- **Dual Output**: Generates both vertices (receiver points) and triangles (mesh structure) stored in separate database tables

**Configuration Parameters**:
- **roadWidth**: Buffer distance for road centerlines (default: 2 meters)
- **maximumArea**: Maximum allowed triangle area (controls mesh density, default: 75 m²)
- **receiverHeight**: Evaluation height for all receivers above ground (default: 1.6 meters)
- **buildingBuffer**: Exclusion buffer distance from building boundaries (default: 2 meters)
- **epsilon**: Point merging tolerance for duplicate vertices (default: 1e-6)
- **geometrySimplificationDistance**: Distance threshold for simplifying geometries before triangulation
- **isoSurfaceInBuildings**: Boolean flag controlling whether to include receivers inside building polygons

**Processing Workflow**:

```plantuml
@startuml
title DelaunayReceiversMaker Processing Workflow

start

:For each cell (i, j) in grid;;

partition "Cell Processing" {
  :Fetch sources in cell envelope;
  
  :Load buildings in expanded envelope;
  
  partition "Geometry Preparation" {
    :Merge and buffer buildings
with buildingBuffer;
    
    :Simplify geometries;
    
    :Densify based on maximumArea;
    
    :Buffer and process roads
with roadWidth;
  }
  
  partition "Delaunay Triangulation" {
    :Initialize LayerTinfour mesh;
    
    :Add building polygons
as constraints;
    
    :Add road polygons
as constraints;
    
    :Add densified cell envelope
vertices;
    
    :Execute processDelaunay();
  }
  
  partition "Result Generation" {
    :Extract vertices with
receiverHeight;
    
    :Filter triangles (exclude
building triangles if needed);
    
    :Insert vertices into
verticesTableName;
    
    :Insert triangles into
trianglesTableName;
  }
}

stop

@enduml
```

**Geometry Processing Steps**:

1. **Building Preparation**:
   - Fetch buildings within expanded cell envelope
   - Merge overlapping buildings
   - Apply `buildingBuffer` to create exclusion zones
   - Simplify using `TopologyPreservingSimplifier` with `geometrySimplificationDistance`
   - Densify boundaries based on `maximumArea` for consistent triangle sizing

2. **Road Processing**:
   - Fetch road geometries (LineString or MultiLineString) from sources
   - Buffer roads by `roadWidth / 2` to create polygon constraints
   - Apply same simplification and densification steps

3. **Constraint Integration**:
   - Feed buildings and roads as polygonal constraints to `LayerTinfour`
   - Constrained Delaunay ensures triangulation edges respect building/road boundaries
   - Each constraint polygon receives a unique constraint ID for tracking

4. **Triangulation Execution**:
   - Set epsilon-based point merging tolerance
   - Add cell envelope vertices with densification if `maximumArea > 1`
   - Call `processDelaunay()` to compute constrained Delaunay triangulation
   - LayerTinfour uses Tinfour library backend for robust triangulation

5. **Result Table Generation**:
   - Extract triangle vertices, set z-coordinate to `receiverHeight`
   - Filter triangles: exclude those marked with non-zero attribute (building-interior triangles) unless `isoSurfaceInBuildings=true`
   - Create/populate receiver table with vertex points
   - Create/populate triangles table with triangle topology (3 vertex references + cell ID)

**Database Schema**:

Receptor table (`verticesTableName`):
```sql
CREATE TABLE receivers (
  PK SERIAL PRIMARY KEY,
  THE_GEOM GEOMETRY NOT NULL,
  HEIGHT_TYPE VARCHAR(10) DEFAULT 'RELATIVE'
)
```

Triangles table (`trianglesTableName`):
```sql
CREATE TABLE triangles (
  PK SERIAL PRIMARY KEY,
  THE_GEOM GEOMETRY,
  PK_1 INTEGER NOT NULL,    -- Reference to first vertex
  PK_2 INTEGER NOT NULL,    -- Reference to second vertex
  PK_3 INTEGER NOT NULL,    -- Reference to third vertex
  CELL_ID INTEGER NOT NULL, -- Grid cell identifier
  PRIMARY KEY (PK)
)
```

**Integration with NoiseMapByReceiverMaker**:

`DelaunayReceiversMaker` is often used as a preprocessing step before `NoiseMapByReceiverMaker` execution:

1. **Receiver Generation**: Generate receiver points and mesh using `DelaunayReceiversMaker.run()`
2. **Table Creation**: Vertices and triangles are stored in database tables
3. **Receiver Configuration**: Set `receiverTableName` in `NoiseMapByReceiverMaker` to point to generated vertices
4. **Noise Computation**: `NoiseMapByReceiverMaker` processes each receiver and generates noise levels
5. **Result Integration**: Noise levels can be joined with triangles table for visualization as isosurfaces or color-mapped mesh

**Advantages of Delaunay-Based Approach**:
- **Efficient Coverage**: Dense receiver distribution where needed (fine triangles near buildings/roads), coarser in open areas
- **Quality Mesh**: Delaunay property ensures well-shaped triangles for visualization
- **Constraint Handling**: Naturally respects building outlines and road centerlines without artificial grid distortion
- **Visualization-Ready**: Triangle output directly usable for 3D noise surface visualization

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
    :prepareCell() → SceneWithEmission;
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

The `prepareCell()` method creates a complete `SceneWithEmission` object containing all necessary data for propagation computation.

```plantuml
@startuml
title Scene Preparation — prepareCell() Method

[prepareCell()] as prepare

package "Geometry Loading" #LightBlue {
}

package "Receiver Loading" #LightGreen {
}

package "Scene Configuration" #LightYellow {
}

prepare --> "Geometry Loading" : Buildings, Terrain, Ground, Sources
prepare --> "Receiver Loading" : Receivers
prepare --> "Scene Configuration" : Parameters

note right of prepare
  **Returns:** SceneWithEmission
  **Contains:** All geometry, sources,
  receivers, and acoustic parameters
  for the cell
end note

@enduml
```

**Geometry Storage Before Loading**:
- **Primary Storage**: All geometry data (buildings, terrain, ground areas, sources) is stored in a spatial database (typically PostGIS) before processing
- **Table Organization**: Data is organized in dedicated tables (e.g., `buildingsTableName`, `sourcesTableName`) as specified in `GridMapMaker` parameters
- **Pre-Processing Location**: Geometry exists in the database tables; `ProfileBuilder` is not involved in geometry loading but handles propagation path profiling during path finding
- **Loading Mechanism**: `prepareCell()` queries the database using spatial envelopes to retrieve relevant geometry for each cell

**Data Loading Sequence**:
1. **Buildings**: Load buildings within expanded envelope for obstruction modeling
2. **Terrain (DEM)**: Load digital elevation model for ground profile computation
3. **Ground Areas**: Load soil/ground absorption data
4. **Sources**: Load acoustic sources with emission data
5. **Receivers**: Load receivers within cell envelope
6. **Configuration**: Apply acoustic and computational parameters

## Path Finding Integration

`NoiseMapByReceiverMaker` integrates with the PathFinder component to compute propagation paths.
See `pathfinder_algorithm.md` for detailed PathFinder architecture and algorithms. 
The `ProfileBuilder` is invoked during PathFinder execution to construct propagation profiles.

```plantuml
@startuml
title Path Finding Integration

[NoiseMapByReceiverMaker.evaluateCell()] as nm

package "PathFinder" #LightCoral {
  [PathFinder.run()]
  [ThreadPathFinder.call()]
  [PropagationProcess.computeRaysAtPosition()]
}

package "Visitor Pattern" #LightGreen {
  [AttenuationComputeOutput.subProcess()]
  [AttenuationVisitor.onNewCutPlane()]
  [AttenuationVisitor.finalizeReceiver()]
}

nm --> [PathFinder.run()] : scene, visitor

[PathFinder.run()] --> [ThreadPathFinder.call()] : Parallel processing
[ThreadPathFinder.call()] --> [PropagationProcess.computeRaysAtPosition()] : Per receiver

[PropagationProcess.computeRaysAtPosition()] --> [AttenuationVisitor.onNewCutPlane()] : Cut profiles
[AttenuationVisitor.onNewCutPlane()] --> [AttenuationVisitor.finalizeReceiver()] : Results

note right of nm
  **Integration Points:**
  - Creates PathFinder instance
  - Provides SceneWithEmission
  - Supplies AttenuationComputeOutput visitor
  - Handles threading coordination
end note

@enduml
```

**Key Integration Aspects**:
- **Scene Provision**: Passes prepared `SceneWithEmission` to PathFinder
- **Visitor Pattern**: Uses `AttenuationComputeOutput` as the processing visitor
- **Threading**: Coordinates multi-threaded path finding execution
- **Result Flow**: Receives processed attenuation results through visitor callbacks

## Attenuation Computation

The attenuation computation is delegated to the `AttenuationComputeOutput` component, which implements the CNOSSOS-EU algorithms.
See `attenuationcomputeoutput_algorithms.md` for detailed AttenuationComputeOutput architecture and algorithms.

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
