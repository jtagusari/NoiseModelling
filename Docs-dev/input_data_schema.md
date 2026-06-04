# NoiseModelling Input Data Schema

- [NoiseModelling Input Data Schema](#noisemodelling-input-data-schema)
  - [Overview](#overview)
  - [Sources Table](#sources-table)
  - [Buildings Table](#buildings-table)
  - [Bridge Points Table](#bridge-points-table)
  - [Terrain (DEM) Data](#terrain-dem-data)
  - [Ground Areas Table](#ground-areas-table)
  - [Computational Parameters](#computational-parameters)
  - [Data Validation Checklist](#data-validation-checklist)

## Overview

This document provides detailed specifications for all input data tables and parameters required by NoiseModelling. The schemas described here define the logical structure of input data regardless of how it is provided:

- **Approach 1 (File-Based to H2GIS)**: Geometries loaded from files (GeoJSON, Shapefile, GML) into H2GIS database tables
- **Approach 2 (PostGIS Database)**: Geometries pre-stored in a PostGIS spatial database with tables matching these schemas
- **Approach 3 (Direct Value Input)**: Geometry and parameter values specified directly in code/configuration; conceptually represented as in-memory data structures with equivalent logical schemas

For information on how to prepare and load this data, see [computation_scheme.md](computation_scheme.md#phase-1-data-preparation) for overview of preparation approaches, the WPS framework for script-based data loading, or direct code-based construction for Approach 3 (testing).

## Sources Table

Purpose: Brief overview. The `Sources` table holds acoustic source geometries and associated emission data. Depending on input mode it may contain per-period traffic fields, precomputed spectral LW values, or references to external emission tables.

Key notes:
- Geometry types: LineString (road/rail) or Point (point sources). Use projected CRS (meters).
- Emission data: Can be provided as per-band LW columns, overall LW, or derived from traffic-flow fields.
- For full schema definitions, input modes, sampling strategy, and loader behavior see [source_algorithms.md](source_algorithms.md).

The detailed loader logic and per-format examples are documented in `source_algorithms.md`; keep the table here as a logical overview only.

## Buildings Table

**Purpose**: Contains building geometries with acoustic material properties for modeling obstruction and reflection.

**Geometry Requirements**:
- Type: Polygon (closed 2D polygons representing building footprints)
- Coordinate Reference System: Projected CRS, consistent with other tables
- Z coordinate: Optional; if present, represents building height

**Required Columns**:
- `PK` (INTEGER PRIMARY KEY): Unique building identifier
- `THE_GEOM` (GEOMETRY(Polygon, SRID))`: Building footprint
- Acoustic property columns:
  - `ALPHA_63HZ`, `ALPHA_125HZ`, ..., `ALPHA_8000HZ` (absorption coefficients per frequency band)
  - Or: `ALPHA_OVERALL` for broadband absorption coefficient
  - Value range: 0.0 to 1.0 (0.0 = fully reflective, 1.0 = fully absorptive)

**Optional Columns**:
- `HEIGHT` (DOUBLE): Building height (meters above ground)
- `MATERIAL_TYPE` (VARCHAR): Building construction material (concrete, wood, etc.)
- `DESCRIPTION` (VARCHAR): Metadata or building name

**Physical Interpretation**:
- Buildings act as obstacles in path finding (blocking direct sound)
- Building surfaces participate in reflection calculations
- Absorption coefficients determine energy loss at reflections



## Bridge Points Table

**Purpose**: Contains point geometries and structural parameters used to build bridge profiles for propagation.

**Geometry Requirements**:
- Type: Point (location of bridge centerline or representative path)
- Coordinate Reference System: Projected CRS, consistent with other tables
- Z coordinate: Expected (PointZ). Used as the 3D coordinate of the point; deck height is handled by explicit height columns.

**Required Columns** (read by `BridgePoint(ResultSet)`):
- `PK` (INTEGER PRIMARY KEY): Bridge point identifier
- `BRIDGE_PK` (INTEGER): Identifier grouping points into a bridge
- `THE_GEOM` (GEOMETRY(PointZ, SRID)`: Bridge point geometry
- `ABSOLUTE_DECK_HEIGHT` (DOUBLE): Deck height in absolute elevation (nullable)
- `RELATIVE_DECK_HEIGHT` (DOUBLE): Deck height relative to ground (nullable)
- `DECK_THICKNESS` (DOUBLE): Deck thickness (m)
- `RIGHT_WIDTH` (DOUBLE): Right-side deck width (m)
- `LEFT_WIDTH` (DOUBLE): Left-side deck width (m)
- `RIGHT_BARRIER_HEIGHT` (DOUBLE): Right barrier/parapet height (m)
- `LEFT_BARRIER_HEIGHT` (DOUBLE): Left barrier/parapet height (m)
- `POSITION` (VARCHAR): `CENTER`, `LEFT`, or `RIGHT`
- `GIRDER_TYPE` (VARCHAR): e.g. `STEEL_BOX`, `STEEL_PLATE`, `CONCRETE_BOX`
- `SLAB_TYPE` (VARCHAR): e.g. `STEEL`, `CONCRETE`

**Notes**:
- At least one of `ABSOLUTE_DECK_HEIGHT` or `RELATIVE_DECK_HEIGHT` should be populated per row.
- The loader does not read stiffness/damping or frequency-band absorption columns for bridges.

**Physical Interpretation**:
- Bridge points describe the bridge deck profile (height, thickness, widths, barriers) used to build 3D geometry for pathfinding.
- Material types are used to characterize bridge components; acoustic absorption coefficients are not read from this table.

## Terrain (DEM) Data

**Purpose**: Provides digital elevation model for ground elevation queries during path finding.

**Format Options**:
- Raster grid: GeoTIFF, ASCII Grid, or other standard raster format
- Point cloud: TIN (Triangulated Irregular Network) or height point dataset
- Native database: Stored as Raster type in PostgreSQL/PostGIS or equivalent

**Coordinate Requirements**:
- Coordinate Reference System: Projected CRS, consistent with other tables
- Spatial resolution: Appropriate for study area (typically 1m to 25m grid)
- Vertical accuracy: ±0.5m to ±5m typical (depends on source)

**For Raster Grid Storage**:
- Column: `THE_GEOM` (RASTER type in PostGIS)
- Column: `Z` or elevation band containing height values
- Metadata: CRS, pixel size, nodata value

**For Point Cloud Storage**:
- `PK` (INTEGER PRIMARY KEY)
- `THE_GEOM` (GEOMETRY(PointZ, SRID)`: Point location with Z coordinate
- `HEIGHT` (DOUBLE): Ground elevation value

**Coverage Requirements**:
- Must completely cover computation domain (receiver envelope)
- Should extend beyond domain by propagation distance for accurate boundary handling
- No gaps or null values in critical areas

**Physical Interpretation**:
- Used to query ground elevation at receiver and source locations
- Essential for Z-coordinate conversion from RELATIVE to ABSOLUTE elevation
- User to model ground-level sound propagation and diffraction


## Ground Areas Table

**Purpose**: Specifies soil/ground acoustic properties for sound absorption modeling.

**Geometry Requirements**:
- Type: Polygon (geographic areas with consistent ground properties)
- Coordinate Reference System: Projected CRS, consistent with other tables

**Required Columns**:
- `PK` (INTEGER PRIMARY KEY): Unique ground area identifier
- `THE_GEOM` (GEOMETRY(Polygon, SRID)`: Area boundary
- Absorption coefficients per frequency band:
  - `ALPHA_63HZ`, `ALPHA_125HZ`, ..., `ALPHA_8000HZ`
  - Or: `ALPHA_OVERALL` for broadband value
  - Value range: 0.0 to 1.0

**Optional Columns**:
- `GROUND_TYPE` (VARCHAR): Ground classification (grass, concrete, water, etc.)
- `ROUGHNESS` (DOUBLE): Surface roughness parameter (meters)
- `POROSITY` (DOUBLE): Porosity fraction for porous materials (0.0 to 1.0)

**Typical Values by Ground Type**:
- Grass/vegetation: ALPHA ≈ 0.3-0.7 (dependent on height and density)
- Concrete/asphalt: ALPHA ≈ 0.05-0.15 (rigid, reflective)
- Water: ALPHA ≈ 0.01-0.05 (highly reflective)
- Soil: ALPHA ≈ 0.1-0.5 (dependent on moisture and composition)

**Physical Interpretation**:
- Frequency-dependent absorption models sound attenuation at ground surface
- Lower frequencies have lower absorption (travel further over ground)
- Ground effects are major contributor to sound propagation behavior


## Computational Parameters

**Purpose**: Specifies propagation settings and physical constants for acoustic computation.

**Propagation Distance Parameters**:
- `MAXIMUM_PROPAGATION_DISTANCE` (DOUBLE, meters): Maximum distance to compute ray paths
  - Typical range: 750m to 2000m depending on source power and background noise
  - Larger values require more computation but capture distant impacts

- `MAXIMUM_REFLECTION_DISTANCE` (DOUBLE, meters): Maximum distance for reflected ray tracing
  - Typical range: 100m to 500m
  - Limits multi-bounce reflection computation for efficiency

**Diffraction Parameters**:
- `SOUND_REFLECTION_ORDER` (INTEGER): Number of reflection bounces to compute
  - Typical: 1 to 3 (more bounces = more computation)
  - 0: Only direct paths
  - 1: Direct + first reflection
  - 2+: Multiple reflections for complex urban geometry

- `COMPUTE_HORIZONTAL_DIFFRACTION` (BOOLEAN): Enable diffraction over building edges
  - Default: true
  - Consider Fresnel diffraction at building corners

- `COMPUTE_VERTICAL_DIFFRACTION` (BOOLEAN): Enable diffraction over building tops
  - Default: true
  - Consider diffraction paths over building silhouettes

**Physical Constants**:
- `SPEED_OF_SOUND` (DOUBLE, m/s): Typically 343 m/s (20°C, sea level)
  - Varies with temperature and altitude
  - Used for time-of-flight and phase calculations

- `TEMPERATURE` (DOUBLE, °C): Ambient temperature for sound propagation
  - Default: 20°C
  - Affects speed of sound calculation

- `HUMIDITY` (DOUBLE, %): Relative humidity for atmospheric absorption
  - Typical range: 40-60%
  - Affects high-frequency attenuation

**Material Properties**:
- Material-specific impedance values (if using advanced attenuation models)
- Specific heat capacity, density parameters

**Configuration Scope**:
- These parameters are typically set globally for entire computation
- Can sometimes be overridden at grid cell or source level for specialized scenarios
- Stored as configuration parameters rather than database columns

**Java Mapping**:
- Per-period atmospheric parameters are loaded by `DefaultTableLoader.loadAtmosphericTableSettings(...)` using `AttenuationParameters.readFromDatabase(ResultSet, map)`.
- Global propagation settings are read from `NoiseMapByReceiverMaker` and injected into `SceneWithEmission` when `DefaultTableLoader.createScene(...)` builds each cell scene.

## Data Validation Checklist

Before running computation:

**Geometric Validation**:
- [ ] All tables have same Coordinate Reference System (CRS)
- [ ] Coordinates are in projected CRS (meters), not geographic (degrees)
- [ ] No invalid or self-intersecting geometries
- [ ] Required Z coordinates present where specified
- [ ] Spatial index created on THE_GEOM columns for performance

**Completeness Validation**:
- [ ] Sources table has emission spectra for all required frequency bands
- [ ] Buildings table has absorption coefficients for all geometry
- [ ] DEM covers full computation domain with no gaps
- [ ] Ground areas polygon coverage is complete within domain
- [ ] All numeric columns populated (no NULL values in critical fields)

**Range Validation**:
- [ ] Absorption coefficients in range [0.0, 1.0]
- [ ] Source power levels reasonable for source type
- [ ] Propagation distances positive and reasonable
- [ ] Building heights positive (where specified)
- [ ] Temperature in reasonable range (-30°C to 50°C typical)

**Spatial Validation**:
- [ ] Source geometries within or near computation domain
- [ ] Building geometries not overlapping excessively
- [ ] Receiver distribution covers areas of interest
- [ ] DEM spatial resolution appropriate for domain size
