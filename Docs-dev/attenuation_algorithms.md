# NoiseModelling-Propagation Attenuation Algorithms

- [NoiseModelling-Propagation Attenuation Algorithms](#noisemodelling-propagation-attenuation-algorithms)
  - [Concepts \& Overview](#concepts--overview)
  - [AttenuationVisitor — Lightweight Attenuation Processor](#attenuationvisitor--lightweight-attenuation-processor)
    - [AttenuationVisitor — Overview](#attenuationvisitor--overview)
    - [Key Features](#key-features)
    - [Processing Flow](#processing-flow)
  - [AttenuationComputeOutput — Multi-threaded Coordinator](#attenuationcomputeoutput--multi-threaded-coordinator)
    - [AttenuationComputeOutput — Overview](#attenuationcomputeoutput--overview)
    - [Thread-Safe Data Collection](#thread-safe-data-collection)
    - [Typical Workflow](#typical-workflow)
  - [AttenuationCnossos — CNOSSOS-EU Computation Engine](#attenuationcnossos--cnossos-eu-computation-engine)
    - [AttenuationCnossos — Overview](#attenuationcnossos--overview)
    - [Method Relationships and Call Hierarchy](#method-relationships-and-call-hierarchy)
      - [computeCnossosAttenuation() - Direct Method Calls](#computecnossosattenuation---direct-method-calls)
      - [evaluate() - Direct Method Calls](#evaluate---direct-method-calls)
    - [Favorable vs. Homogeneous Conditions](#favorable-vs-homogeneous-conditions)
    - [Attenuation Components](#attenuation-components)
      - [Geometric Divergence (ADiv)](#geometric-divergence-adiv)
        - [Overview](#overview)
        - [Structure](#structure)
        - [Computation Logic](#computation-logic)
      - [Atmospheric Absorption (AAtm)](#atmospheric-absorption-aatm)
        - [Overview](#overview-1)
        - [Structure](#structure-1)
        - [Computation Logic](#computation-logic-1)
      - [Reflection (ARef)](#reflection-aref)
        - [Overview](#overview-2)
        - [Structure](#structure-2)
        - [Computation Logic](#computation-logic-2)
      - [Boundary Effects (ABoundary)](#boundary-effects-aboundary)
        - [Overview](#overview-3)
        - [Structure](#structure-3)
        - [Computation Logic](#computation-logic-3)
      - [Boundary Effects (Legacy)](#boundary-effects-legacy)
      - [Ground Effect (AGround)](#ground-effect-aground)
        - [Overview](#overview-4)
        - [Structure](#structure-4)
        - [Computation Logic](#computation-logic-4)
      - [Diffraction (ADif)](#diffraction-adif)
        - [Overview](#overview-5)
        - [Structure](#structure-5)
        - [Computation Logic](#computation-logic-5)
      - [Retro-diffraction (ΔRetrodif)](#retro-diffraction-δretrodif)
        - [Overview](#overview-6)
        - [Structure](#structure-6)
        - [Computation Logic](#computation-logic-6)
  - [Tests \& Examples](#tests--examples)

## Concepts & Overview

The NoiseModelling-Propagation module implements acoustic attenuation computation following the CNOSSOS-EU.
The module processes vertical cut profiles produced by the PathFinder module and computes per-frequency attenuation values based on geometric divergence, atmospheric absorption, ground effects, diffraction, and reflection.

The module provides two main processing approaches:

1. **Lightweight approach** (`AttenuationVisitor` + `AttenuationComputeOutput`) — Suitable for standalone applications, tests, and tutorials. No database dependencies. Computes attenuation values only.

2. **Database-integrated approach** (`AttenuationOutputSingleThread` + `AttenuationOutputMultiThread`) — Used in production noise mapping workflows (e.g., WPS services). Integrates with JDBC, combines attenuation with emission data, manages result caching, and implements optimization strategies like MaxError DB processing.

The core acoustic computation logic is centralized in the `AttenuationCnossos` class, which implements the CNOSSOS-EU standard algorithms.

## AttenuationVisitor — Lightweight Attenuation Processor

`AttenuationVisitor` is a single-threaded visitor that processes vertical cut profiles and computes per-frequency attenuation values. It implements the `CutPlaneVisitor` interface and is designed to work without database dependencies.

### AttenuationVisitor — Overview

```plantuml
@startuml
class AttenuationVisitor implements CutPlaneVisitor {
  + AttenuationComputeOutput multiThreadParent
  + List<ReceiverNoiseLevel> receiverAttenuationLevels
  + List<CnossosPathExt> pathParameters
  + boolean keepRays
  
  + AttenuationVisitor(AttenuationComputeOutput)
  + onNewCutPlane(CutProfile): PathSearchStrategy
  + startReceiver(ReceiverPointInfo, Collection<SourcePointInfo>, AtomicInteger): void
  + finalizeReceiver(ReceiverPointInfo): void
  - processPath(String, AttenuationParameters, CnossosPathExt): void
  - addPropagationPath(CnossosPathExt): void
}

class AttenuationComputeOutput implements CutPlaneVisitorFactory {
  + ConcurrentLinkedDeque<ReceiverNoiseLevel> receiversAttenuationLevels
  + Deque<CnossosPathExt> pathParameters
  + boolean exportPaths
  + boolean exportAttenuationMatrix
  + SceneWithAttenuation scene
  + AtomicLong cnossosPathCount
  
  + AttenuationComputeOutput(boolean, SceneWithAttenuation)
  + subProcess(ProgressVisitor): CutPlaneVisitor
  + getVerticesSoundLevel(): List<ReceiverNoiseLevel>
}

AttenuationVisitor --> AttenuationComputeOutput : references parent
AttenuationComputeOutput --> AttenuationVisitor : creates instances via subProcess()

interface CutPlaneVisitor {
  + onNewCutPlane(CutProfile): PathSearchStrategy
  + startReceiver(ReceiverPointInfo, Collection<SourcePointInfo>, AtomicInteger): void
  + finalizeReceiver(ReceiverPointInfo): void
}

@enduml
```

### Key Features

- **JDBC-free implementation** — No database dependencies, suitable for standalone applications and unit tests
- **Per-period processing** — Supports multiple time periods (Day, Evening, Night) via `cnossosParametersPerPeriod`
- **Source line merging** — Combines attenuation levels from line source segments for the same receiver
- **Ray export support** — Optionally exports propagation paths (`CnossosPathExt`) for visualization or debugging
- **Simple data flow** — Accumulates results in memory lists, no queue management required


### Processing Flow

```plantuml
@startuml
title AttenuationVisitor — Processing Flow

start

:Receive CutProfile;

:Build CnossosPath using\nCnossosPathBuilder.buildCnossosPath();

if (cnossosPath != null?) then (yes)
  if (Multiple periods configured?) then (yes)
    :Loop through each period;
    :Get period-specific\nAttenuationParameters;
    :Call AttenuationCnossos.computeCnossosAttenuation();
    :Store attenuation in\nreceiverAttenuationLevels;
  else (no)
    :Use default AttenuationParameters;
    :Call AttenuationCnossos.computeCnossosAttenuation();
    :Store attenuation in\nreceiverAttenuationLevels;
  endif
  
  if (keepRays?) then (yes)
    :Add path to pathParameters list;
  endif
else (no)
  :Skip this profile;
endif

:Return CONTINUE strategy;

stop

note right
  Per-frequency attenuation
  computed using CNOSSOS-EU
  algorithms
end note

@enduml
```

When `finalizeReceiver()` is called:
1. Export ray paths to parent (if `keepRays` is true)
2. Merge attenuation levels from line source segments
3. Push merged results to parent's `receiversAttenuationLevels`
4. Clear local caches

## AttenuationComputeOutput — Multi-threaded Coordinator

`AttenuationComputeOutput` is the factory and coordinator for multi-threaded attenuation computation. It implements `CutPlaneVisitorFactory` and creates `AttenuationVisitor` instances for each processing thread.

### AttenuationComputeOutput — Overview

**Purpose**: 
- Thread-safe container for collecting attenuation results from multiple worker threads
- Factory for creating `AttenuationVisitor` instances
- Maintains shared statistics (path counts, computation metrics)

**Key Fields**:
- `receiversAttenuationLevels: ConcurrentLinkedDeque<ReceiverNoiseLevel>` — Thread-safe queue for attenuation results
- `pathParameters: Deque<CnossosPathExt>` — Ray path storage for visualization/export
- `scene: SceneWithAttenuation` — Reference to scene containing geometry and acoustic parameters
- `cnossosPathCount: AtomicLong` — Counter for total CNOSSOS paths processed
- Statistics counters: `nb_couple_receiver_src`, `nb_obstr_test`, `nb_image_receiver`, etc.

### Thread-Safe Data Collection

```plantuml
@startuml
title Multi-threaded Processing Architecture

package "Main Thread" {
  [AttenuationComputeOutput\n(Factory & Collector)]
}

package "Worker Thread 1" {
  [AttenuationVisitor 1]
}

package "Worker Thread 2" {
  [AttenuationVisitor 2]
}

package "Worker Thread N" {
  [AttenuationVisitor N]
}

[AttenuationComputeOutput\n(Factory & Collector)] --> [AttenuationVisitor 1] : creates
[AttenuationComputeOutput\n(Factory & Collector)] --> [AttenuationVisitor 2] : creates
[AttenuationComputeOutput\n(Factory & Collector)] --> [AttenuationVisitor N] : creates

[AttenuationVisitor 1] --> [ConcurrentLinkedDeque\nReceiver Levels] : thread-safe\npush
[AttenuationVisitor 2] --> [ConcurrentLinkedDeque\nReceiver Levels] : thread-safe\npush
[AttenuationVisitor N] --> [ConcurrentLinkedDeque\nReceiver Levels] : thread-safe\npush

[ConcurrentLinkedDeque\nReceiver Levels] --> [AttenuationComputeOutput\n(Factory & Collector)] : contained in

note right of [AttenuationComputeOutput\n(Factory & Collector)]
  Uses concurrent data structures
  for thread-safe result collection
end note

@enduml
```

### Typical Workflow

1. Create `AttenuationComputeOutput` with scene configuration
2. PathFinder calls `subProcess()` to create worker-thread visitors
3. Each worker processes its receiver range independently
4. Results are accumulated in shared concurrent collections
5. After all workers complete, retrieve results via `getVerticesSoundLevel()`

## AttenuationCnossos — CNOSSOS-EU Computation Engine

`AttenuationCnossos` is a utility class providing static methods for computing acoustic attenuation according to the CNOSSOS-EU.
All attenuation computation logic is centralized here.

### AttenuationCnossos — Overview

**Core Responsibility**: Implement CNOSSOS-EU attenuation algorithms for outdoor sound propagation

**Key Methods**:
- `computeCnossosAttenuation()` — Main entry point for complete attenuation calculation
- `aDiv()` — Geometric divergence
- `aAtm()` — Atmospheric absorption
- `aBoundary()` — Ground effect + diffraction (combined boundary attenuation)
- `aGroundH()` / `aGroundF()` — Ground effect for homogeneous/favorable conditions
- `aDif()` — Diffraction attenuation
- `evaluateAref()` — Reflection attenuation
- `deltaRetrodif()` — Retro-diffraction correction

### Method Relationships and Call Hierarchy

The methods in `AttenuationCnossos` work together in a hierarchical structure. Below are the direct method call relationships for the two main entry points: `computeCnossosAttenuation()` is the current algorithm used in production, while `evaluate()` represents the legacy algorithm.

#### computeCnossosAttenuation() - Direct Method Calls

```plantuml
@startuml
title computeCnossosAttenuation() - Direct Method Calls

[computeCnossosAttenuation()] #LightBlue

[init()]
[aDiv()]
[aAtm()]
[evaluateAref()]
[aBoundary()]
[deltaRetrodif()]

[computeCnossosAttenuation()] --> [init()]
[computeCnossosAttenuation()] --> [aDiv()]
[computeCnossosAttenuation()] --> [aAtm()]
[computeCnossosAttenuation()] --> [evaluateAref()]
[computeCnossosAttenuation()] --> [aBoundary()]
[computeCnossosAttenuation()] --> [deltaRetrodif()]

note right of [computeCnossosAttenuation()]
  Current algorithm (2015/996/EN)
  Calls aBoundary() and deltaRetrodif()
  twice (H & F conditions)
end note

@enduml
```

#### evaluate() - Direct Method Calls

```plantuml
@startuml
title evaluate() - Direct Method Calls (Legacy)

[evaluate()] #LightCoral

[init()]
[getADiv()]
[getAAtm()]
[getABoundary()]
[getARef()]

[evaluate()] --> [init()]
[evaluate()] --> [getADiv()]
[evaluate()] --> [getAAtm()]
[evaluate()] --> [getABoundary()]
[evaluate()] --> [getARef()]

note right of [evaluate()]
  Legacy algorithm
  Uses private helper methods
end note

@enduml
```

### Favorable vs. Homogeneous Conditions

CNOSSOS-EU distinguishes between two meteorological propagation conditions:

**Homogeneous (H)**: 
- Neutral atmospheric conditions
- No significant wind or temperature gradients
- Sound rays propagate in straight lines

**Favorable (F)**: 
- Downwind propagation or temperature inversion
- Sound rays curve downward (increased ground interaction)
- Enhanced propagation distance

**Wind Rose Integration**:

The final attenuation is a weighted average based on wind direction probability:

```
A_final(freq) = p·A_favorable(freq) + (1-p)·A_homogeneous(freq)
```

Where `p` is the probability of favorable conditions in the source-receiver direction (from wind rose data).

```plantuml
@startuml
title Meteorological Condition Processing

start

:Get source-receiver direction angle;
:Compute rose index from angle;
:Lookup wind rose probability\np = windRose[roseIndex];

if (p < 1.0) then (yes)
  :Compute homogeneous attenuation\naGlobalMeteoHom;
else (no)
  :Skip homogeneous computation;
endif

if (p > 0.0) then (yes)
  :Compute favorable attenuation\naGlobalMeteoFav;
else (no)
  :Skip favorable computation;
endif

:Weight results:\naGlobal = p·aFav + (1-p)·aHom;

:Return weighted attenuation;

stop

@enduml
```

### Attenuation Components

#### Geometric Divergence (ADiv)

##### Overview

Geometric divergence accounts for the spherical spreading of sound waves as they propagate away from the source. This is a frequency-independent attenuation that increases with distance.

**Physical Principle**: As sound spreads spherically from a point source, the sound energy is distributed over an increasingly larger surface area, resulting in a decrease in sound intensity.

##### Structure

```plantuml
@startuml
title Geometric Divergence - Method Structure

[aDiv()] --> [getADiv()] : calls

note right of [aDiv()]
  Current implementation
  Used by computeCnossosAttenuation()
end note

note right of [getADiv()]
  Legacy implementation
  Used by evaluate()
end note

@enduml
```

##### Computation Logic

**Formula**: `ADiv = 20·log₁₀(distance) + 11` dB

**Parameters**:
- `distance`: Propagation distance in meters (minimum 1m)
- For paths with vertical diffraction (DIFV): uses `dc` (curved path distance)
- Otherwise: uses `d` (direct distance)

**Implementation Notes**:
- The constant `11` accounts for reference distance normalization
- Minimum distance is enforced to avoid logarithm of zero
- Distance selection depends on presence of vertical diffraction points

#### Atmospheric Absorption (AAtm)

##### Overview

Atmospheric absorption accounts for the frequency-dependent attenuation of sound energy as it travels through air. Higher frequencies are absorbed more than lower frequencies due to molecular relaxation processes in atmospheric gases.

**Physical Principle**: Sound energy is converted to heat through molecular vibration and relaxation processes in oxygen and nitrogen molecules. The absorption is strongly frequency-dependent and also affected by temperature, humidity, and atmospheric pressure.

##### Structure

```plantuml
@startuml
title Atmospheric Absorption - Method Structure

[aAtm(alphaAtm, distance)] as aAtm
[getAAtm(dist, alpha)] as getAAtm

aAtm --> getAAtm : calls for\neach frequency

note right of aAtm
  Current implementation
  Processes array of frequencies
  Calls getAAtm() for each
end note

note right of getAAtm
  Private helper method
  Computes single frequency
  Formula: α × distance / 1000
end note

@enduml
```

##### Computation Logic

**Formula**: `AAtm = α·distance / 1000` dB

**Parameters**:
- `α`: Frequency-dependent atmospheric absorption coefficient (dB/km)
- `distance`: Propagation distance in meters
- Division by 1000 converts distance from meters to kilometers

**Frequency Dependency**:
- Absorption coefficients are pre-computed based on:
  - Air temperature
  - Relative humidity
  - Atmospheric pressure
  - Frequency (Hz)
- Typically increases with frequency (higher frequencies attenuate more)

**Implementation Notes**:
- Absorption coefficients are stored in `data.getAlpha_atmo()` array
- One coefficient per frequency band
- Distance used is the same as for geometric divergence

#### Reflection (ARef)

##### Overview

Reflection attenuation accounts for sound energy loss when sound waves reflect off surfaces such as building facades, walls, or other obstacles. Each reflection reduces sound energy based on the absorption properties of the reflecting surface.

**Physical Principle**: When sound encounters a surface, part of the energy is absorbed by the material and converted to heat, while the remainder is reflected. The absorption coefficient determines the fraction of energy absorbed.

##### Structure

```plantuml
@startuml
title Reflection - Method Structure

[evaluateAref()] --> [getARef()] : calls

note right of [evaluateAref()]
  Public interface
  Current implementation
end note

note right of [getARef()]
  Protected method
  Iterates over all REFL points
  Accumulates reflection losses
end note

@enduml
```

##### Computation Logic

**Formula**: `ARef = Σ 10·log₁₀(1 - α)` dB (for all reflection points)

**Parameters**:
- `α`: Frequency-dependent absorption coefficient of the reflecting surface (0 to 1)
- Multiple reflections are summed (in dB domain)

**Processing Steps**:
1. Iterate through all points in the propagation path
2. Identify points with type `REFL` (reflection points)
3. For each reflection point and each frequency:
   - Retrieve absorption coefficient `α` from `pointPath.alphaWall`
   - Compute reflection loss: `10·log₁₀(1 - α)`
   - Accumulate to total reflection attenuation
4. Return array of reflection attenuation per frequency

**Implementation Notes**:
- If no reflection points exist, `ARef = 0` for all frequencies
- If `alphaWall` is null or empty, reflection is skipped
- The formula `10·log₁₀(1 - α)` is negative (attenuation increases sound level reduction)
- Multiple reflections result in cumulative energy loss


#### Boundary Effects (ABoundary)

##### Overview

Boundary effects combine ground effect and diffraction into a single unified attenuation term. This is the most complex component of the CNOSSOS-EU algorithm, as it handles the interaction between sound waves, the ground surface, and obstacles.

**Physical Principle**: Sound propagation near boundaries involves multiple phenomena:
- **Ground effect**: Interference between direct and ground-reflected sound waves
- **Diffraction**: Bending of sound waves around obstacle edges
- **Interaction**: When diffraction occurs, the ground effect on each segment (source-to-obstacle and obstacle-to-receiver) must be considered separately

##### Structure

```plantuml
@startuml
title Boundary Effects - aBoundary() Method Structure

[aBoundary()] as boundary

package "Validation" #LightGreen {
  [isValidRcrit()]
}

package "Ground Effect Methods" #LightYellow {
  [aGroundH()]
  [aGroundF()]
}

package "Diffraction Method" #LightCoral {
  [aDif()]
}

boundary --> [isValidRcrit()] : Check R-criterion

boundary --> [aGroundH()] : if !favorable\n(always called)
boundary --> [aGroundF()] : if favorable\n(always called)
boundary ..> [aDif()] : ONLY if diffraction\npoints present

note right of boundary
  **Main logic:**
  1. Loop through frequencies
  2. Check if diffraction valid (isValidRcrit)
  3. **Always** compute ground effect
  4. Compute diffraction **conditionally**
  5. Combine: AGround + ADif (or AGround only)
end note

note bottom of [aDif()]
  **Conditional calling:**
  
  aDif() is called ONLY when:
  - Diffraction points exist, AND
  - isValidRcrit() returns true
  
  Otherwise, aDif() is NOT called
  and aBoundary = aGround only
end note

note bottom of [isValidRcrit()]
  **Checks:**
  δ > -λ/20 AND
  δ > λ/4 - δ' OR δ > 0
end note

@enduml
```

##### Computation Logic

**Processing Steps**:

1. Compute ground effect (`aGround`) for the full SR segment (always called)
2. Check for diffraction points:
   - If none exist, set `aDif = 0` and return `ABoundary = aGround`
   - If they exist, find the first valid diffraction point and check `isValidRcrit()`
3. Compute diffraction attenuation (`aDif`) using the identified diffraction type
4. Apply type-specific rules:
   - For `DIFH` or `DIFH_RCRIT` (top edge) with valid R-criterion: set `aGround = 0` (ground effect included in `aDif`)
   - For `DIFV` (lateral edge) or `DIFB` (bottom edge): keep `aGround` as computed (ground effect not included in `aDif`)
5. Combine components: `ABoundary = aGround + aDif`


```plantuml
@startuml
title ABoundary Computation Logic with Diffraction Type Handling

start

:For each frequency;

partition "Step 1: Compute Ground Effect" {
  if (Favorable conditions?) then (yes)
    :aGround = aGroundF(SR segment);
  else (no)
    :aGround = aGroundH(SR segment);
  endif
}

partition "Step 2: Check Diffraction Points" {
  if (Diffraction points exist?) then (yes)
    :Find first valid diffraction point\n(DIFH, DIFV, DIFB, or DIFH_RCRIT);
    :Check isValidRcrit();
    
    partition "Step 3: Compute Diffraction" {
      :aDif = aDif(path, freq, type);
    }
    
    partition "Step 4: Apply Type-Specific Rules" {
      if (Diffraction type?) then (DIFH or DIFH_RCRIT\n(top edge)\nAND isValidRcrit)
        :Set aGround = 0;
      else (DIFV or DIFB\n(lateral/bottom edge))
        :Keep aGround as computed;
      endif
    }
  else (no)
    :aDif = 0;
    :Keep aGround as computed;
  endif
}

partition "Step 5: Combine Components" {
  :ABoundary = aGround + aDif;
  
  note right
    **Final combination rules:**
    
    DIFH/DIFH_RCRIT (top edge, valid):
      ABoundary = 0 + aDif = aDif
    
    DIFV (lateral edge):
      ABoundary = aGround + aDif
    
    DIFB (bottom edge):
      ABoundary = aGround + aDif
    
    No diffraction:
      ABoundary = aGround + 0 = aGround
  end note
}

:Return ABoundary per frequency;

stop

@enduml
```

**Type-Specific Handling Summary**:

| Diffraction Type | aGround | aDif | ABoundary | Reason |
|-----------------|---------|------|-----------|---------|
| **DIFH** (top edge, valid R-criterion) | **Set to 0** | Computed | aDif only | Ground effect already included in aDif |
| **DIFH_RCRIT** (top edge, valid R-criterion) | **Set to 0** | Computed | aDif only | Ground effect already included in aDif |
| **DIFV** (vertical/lateral edge) | Kept | Computed | aGround + aDif | Lateral diffraction excludes ground effect |
| **DIFB** (bottom edge) | Kept | Computed | aGround + aDif | Bottom edge diffraction excludes ground effect |
| **No diffraction** | Kept | **Set to 0** | aGround only | Pure ground effect propagation |

**Diffraction Point Types**:
- **DIFH**: Diffraction over the **top edge** of an obstacle (horizontal diffraction)
- **DIFH_RCRIT**: Diffraction over the top edge with R-criterion validation
- **DIFV**: Diffraction around **vertical/lateral edges** (side diffraction)
- **DIFB**: Diffraction under the **bottom edge** of an obstacle (bilateral/bottom diffraction)


#### Boundary Effects (Legacy)

`getABoundary()` is the core method called by the legacy `evaluate()` function.

```plantuml
@startuml
title Level 5: Legacy Algorithm - evaluate() Method

  [getABoundary()]
  [getDeltaDif()]
  [getDeltaGround()]
  [aGround()]

[getABoundary()] --> [getDeltaDif()] : Calculate Δdif
[getABoundary()] --> [aGround()] : for SO & OR segments
[getABoundary()] --> [getDeltaGround()] : Combine dif + ground

note bottom of [getDeltaGround()]
  **Formula:**
  Δground = -20·log₁₀(
    1 + (10^(-Aground/20) - 1)
      · 10^(-(Δdif' - Δdif)/20)
  )
end note

@enduml
```

#### Ground Effect (AGround)

##### Overview

Ground effect accounts for the interference between direct sound waves and sound waves reflected from the ground surface. This interference creates a frequency-dependent attenuation pattern that depends on the acoustic impedance of the ground surface and the geometry of the propagation path.

**Physical Principle**: When sound propagates near the ground, the direct wave from source to receiver interferes with the ground-reflected wave. Depending on the path length difference and phase relationship, this interference can be constructive or destructive. The ground's acoustic impedance determines how much sound energy is absorbed versus reflected.

**Meteorological Dependency**: Ground effect is computed separately for:
- **Homogeneous (H)** conditions: Neutral atmospheric profile
- **Favorable (F)** conditions: Downward-refracting atmosphere

##### Structure

```plantuml
@startuml
title Ground Effect - Current Methods

package "Current Ground Effect Methods" #LightBlue {
  [aGroundH()]
  [aGroundF()]
}

package "Shared Helper" #LightGreen {
  [computeCfKValues()]
}

[aGroundH()] --> [computeCfKValues()] : Get cf, k, w
[aGroundF()] --> [computeCfKValues()] : Get cf, k, w


note bottom of [computeCfKValues()]
  **Returns:** [cf, k, w]
  - cf: Ground impedance factor
  - k: Wave number (2π·f/c)
  - w: Impedance parameter
end note

@enduml
```

**Legacy Implementation**:

`aGround()` is a method called by the legacy `getABoundary()`. It delegates the core computation to `getAGroundCore()`, which handles both homogeneous and favorable conditions in a single method based on the `isFavorable()` flag.

```plantuml
@startuml
title Ground Effect - Legacy Methods

package "Legacy Ground Effect Methods" #LightCoral {
  [aGround()]
  [getAGroundCore()]
}

package "Shared Helper" #LightGreen {
  [computeCfKValues()]
}

[aGround()] --> [getAGroundCore()]
[getAGroundCore()] ..> [computeCfKValues()] : Implicit\ncalculation


note bottom of [computeCfKValues()]
  **Implicit calculation**
  Formula embedded in getAGroundCore()
  (not explicit method call)
end note

@enduml
```

##### Computation Logic

**Key Parameters**:
- `gPath` / `gPathPrime` — Ground absorption coefficient (0 to 1, frequency-dependent)
- `dp` — Mean plane height (average ground height along path)
- `zs`, `zr` — Source and receiver heights above mean plane
  - For homogeneous: `zsH`, `zrH`
  - For favorable: `zsF`, `zrF`
- `cf` — Ground impedance factor (computed from frequency and ground properties)
- `k` — Wave number = 2π·f/c
- `w` — Impedance parameter (depends on frequency and ground absorption)

**Formula** (Homogeneous conditions):
```
AGroundH = -10·log₁₀(4·k²/dp² · 
           (zs² - √(2·cf/k)·zs + cf/k) · 
           (zr² - √(2·cf/k)·zr + cf/k))
```

Subject to minimum constraint: `AGroundH >= -3(1 - gm)`

**Formula** (Favorable conditions):
Similar to homogeneous but uses favorable heights (`zsF`, `zrF`) and includes additional test form factor for minimum bound.

**Special Cases**:
- If `gPath = 0` (perfectly reflective): `AGround = -3` dB (homogeneous) or computed minimum (favorable)
- If `gPath = 1` (perfectly absorptive): Full formula applies without reflection component

#### Diffraction (ADif)

##### Overview

Diffraction attenuation accounts for the reduction in sound level when sound waves bend around obstacles. The `aDif()` method computes the complete diffraction attenuation including ground effect corrections for both source-to-obstacle (SO) and obstacle-to-receiver (OR) segments.

**Physical Principle**: When sound encounters an obstacle edge, it diffracts (bends) around the edge rather than being completely blocked. The amount of diffraction depends on:
- Wavelength (frequency): Lower frequencies diffract more easily
- Path length difference: Difference between diffracted path and direct path
- Edge geometry: Top edge, bottom edge, or lateral edge

**Diffraction Types**:
- **DIFH** (top edge): Most common, includes ground effect in calculation
- **DIFB** (bottom edge): Under suspended obstacles, excludes ground effect
- **DIFV** (lateral edge): Around vertical sides, excludes ground effect

##### Structure

```plantuml
@startuml
title aDif() Method Dependencies

[aDif()] as dif

package "Ground Effect Methods" #LightYellow {
  [aGroundH() for SO]
  [aGroundF() for SO]
  [aGroundH() for OR]
  [aGroundF() for OR]
}

dif --> [aGroundH() for SO] : Homogeneous
dif --> [aGroundF() for SO] : Favorable
dif --> [aGroundH() for OR] : Homogeneous
dif --> [aGroundF() for OR] : Favorable

note right of dif
  Calls ground methods twice:
  1. For SO segment
  2. For OR segment
  
  Selection based on
  meteorological conditions
end note

note bottom of [aGroundH() for SO]
  Ground effect for
  Source → Obstacle segment
end note

note bottom of [aGroundH() for OR]
  Ground effect for
  Obstacle → Receiver segment
end note

@enduml
```

##### Computation Logic

**Formula Components**:
- `δ` — Path length difference
- `λ` — Wavelength (340/f)
- `Ch` — Correction factor (typically 1)
- `C"` — Second correction factor (depends on path geometry)

**Processing Steps**:

`aDif()` combines pure diffraction attenuation with ground effect corrections through a multi-step process:

```plantuml
@startuml
title aDif() Detailed Computation Flow

start

partition "Step 1: Path Geometry" {
  :Calculate path length differences;
  note right
    - δSR: Direct path difference
    - δ(S',R): Image source to receiver
    - δ(S,R'): Source to image receiver
  end note
}

partition "Step 2: Pure Diffraction" {
  :Compute Ch correction factor;
  :Compute C" correction factor;
  :Calculate testForm = 40/λ × C" × δSR;
  
  if (testForm >= -2) then (yes)
    :deltaDiffSR = 10×Ch×log₁₀(3 + testForm);
  else (no)
    :deltaDiffSR = 0;
  endif
  
  note right
    **Early Return Conditions:**
    
    1. **DIFV or DIFB**: Return deltaDiffSR
       (no ground effect computation)
    
    2. **DIFH with invalid delta**:
       δSR < 0 AND
       (δSR ≤ -λ/20 OR δSR ≤ λ/4 - δ(S',R'))
       Return 0
  end note
  
  if (Type is DIFV or DIFB?) then (yes)
    :Return deltaDiffSR;
    stop
  endif
  
  if (Type is DIFH AND small delta?) then (yes)
    :Return 0;
    stop
  endif
  
  :Similarly compute:
  - deltaDiffSPrimeR
  - deltaDiffSRPrime;
}

partition "Step 3: Ground Effect for SO Segment" {
  if (Favorable conditions?) then (yes)
    :aGroundSO = aGroundF(SO segment);
  else (no)
    :aGroundSO = aGroundH(SO segment);
  endif
}

partition "Step 4: Ground Effect for OR Segment" {
  if (Favorable conditions?) then (yes)
    :aGroundOR = aGroundF(OR segment);
  else (no)
    :aGroundOR = aGroundH(OR segment);
  endif
}

partition "Step 5: Combined Ground Effect" {
  :deltaGroundSO = -20×log₁₀(
    1 + (10^(-aGroundSO/20) - 1) ×
    10^(-(deltaDiffSPrimeR - deltaDiffSR)/20)
  );
  
  :deltaGroundOR = -20×log₁₀(
    1 + (10^(-aGroundOR/20) - 1) ×
    10^(-(deltaDiffSRPrime - deltaDiffSR)/20)
  );
  
  if (deltaGroundSO is NaN?) then (yes)
    :deltaGroundSO = 0;
  endif
  
  if (deltaGroundOR is NaN?) then (yes)
    :deltaGroundOR = 0;
  endif
}

partition "Step 6: Final Combination" {
  :aDiff = min(25, max(0, deltaDiffSR))
         + deltaGroundSO
         + deltaGroundOR;
}

:Return aDiff;


stop

@enduml
```

**Key Formulas**:

- **Pure Diffraction**: `ΔDiff = 10×Ch×log₁₀(3 + 40/λ × C" × δ)`
- **Ground Correction**: `ΔGround = -20×log₁₀(1 + (10^(-AGround/20) - 1) × 10^(-(Δdif'-Δdif)/20))`
- **Final Result**: `ADif = min(25, max(0, ΔDiff)) + ΔGroundSO + ΔGroundOR`

**Special Handling**:
- **Early returns**: When certain conditions are met: (1) DIFV/DIFB types, (2) small delta for DIFH, (3) invalid R-criterion for DIFH
- **DIFV/DIFB**: Returns `deltaDiffSR` only (no ground effect)
- **Small delta condition**: Returns 0 if `δSR < 0` and meets specific criteria
- **NaN handling**: Sets ground corrections to 0 if calculation produces NaN
- **25 dB cap**: Pure diffraction term capped at 25 dB per CNOSSOS-EU

#### Retro-diffraction (ΔRetrodif)

##### Overview

Retro-diffraction accounts for the additional attenuation that occurs when sound reflects off a vertical surface and then diffracts over an obstacle on its return path. This phenomenon is illustrated in CNOSSOS-EU Figure 2.5.36.

**Physical Principle**: When a reflected sound path encounters a diffraction obstacle, the diffracted path length is longer than the direct reflected path. This additional path length difference causes extra attenuation that must be accounted for separately from the primary diffraction calculation.

**Application Scope**: 
- Applied **only** to reflection paths (containing REFL point types)
- Requires both reflection points and diffraction obstacles in the path
- Computed separately for homogeneous (H) and favorable (F) conditions
- Typically results in small correction (few dB) but important for accuracy

##### Structure

```plantuml
@startuml
title Retro-diffraction - Method Structure

[deltaRetrodif()]

note right of [deltaRetrodif()]
  Standalone method
  No dependencies on other methods
  
  Applied only to reflection paths
  with diffraction obstacles
end note

note bottom of [deltaRetrodif()]
  **Computes:**
  Additional attenuation for
  reflected-then-diffracted paths
  
  **Returns:**
  Array of ΔRetrodif per frequency
end note

@enduml
```

##### Computation Logic

**Processing Steps**:

1. **Path Analysis**:
   - Iterate through all points in the propagation path
   - Identify REFL (reflection) points
   - For each reflection, find associated diffraction points (DIFH)
   - Track source position (updated at each diffraction point)

2. **Geometry Calculation**:
   - Locate obstacle top point: `P = (reflection.x, obstacleZ)`
   - Calculate distances:
     - `SP`: Source to obstacle top
     - `PR`: Obstacle top to receiver
     - `SR`: Direct source to receiver

3. **Path Difference Computation**:
   - **Homogeneous**: `δ' = SR - SP - PR` (simple geometric difference)
   - **Favorable**: Account for curved ray paths using arc-sine approximation
     - `γ = 2 × max(1000, 8×SR)` (curvature radius)
     - `δ' = -(γ·asin(SP/γ) + γ·asin(PR/γ) - γ·asin(SR/γ))`

4. **Retro-diffraction Attenuation**:
   - Compute test form: `testForm = 40/λ × C" × δ'`
   - If `testForm >= -2`: `ΔRetrodif = 10×Ch×log₁₀(3 + testForm)`
   - Otherwise: `ΔRetrodif = 0`

**Integration in Total Attenuation**:

Retro-diffraction is computed twice per reflection path and integrated as follows:

```plantuml
@startuml
title Retro-diffraction Integration in Attenuation Computation

start

:Process reflection path;

if (Has REFL points?) then (yes)
  partition "Homogeneous Condition" {
    :Compute aBoundaryH;
    :Compute deltaRetrodifH;
    :aGlobalMeteoHom =\nADiv + AAtm + ABoundaryH +\ndeltaRetrodifH + ARef;
  }
  
  partition "Favorable Condition" {
    :Compute aBoundaryF;
    :Compute deltaRetrodifF;
    :aGlobalMeteoFav =\nADiv + AAtm + ABoundaryF +\ndeltaRetrodifF + ARef;
  }
else (no)
  :Skip deltaRetrodif\n(no reflection);
endif

:Weight by wind rose probability;

stop

note right
  deltaRetrodif is only
  computed for reflection
  paths with diffraction
  obstacles
end note

@enduml
```

**Key Characteristics**:
- Always positive value (increases total attenuation)
- Frequency-dependent (higher frequencies attenuate more)
- Depends on obstacle height and reflection surface position
- Sensitive to meteorological conditions (favorable vs. homogeneous)
- Accounts for `e` parameter (obstacle length) when `e >= 0.3`

## Tests & Examples

**Test Files**:
- `AttenuationComputeOutputCnossosTest.java` — Unit tests for attenuation computation
  - Tests individual attenuation components (ADiv, AAtm, AGround, ADif)
  - Validates CNOSSOS-EU algorithm implementation
  - Verifies favorable vs. homogeneous condition handling

**Tutorial Files**:
- `noisemodelling-tutorial-01/GenerateReferenceDeviation.java` — Example usage
  - Demonstrates `AttenuationVisitor` in standalone application
  - Shows how to create scene, process profiles, and retrieve results
  - No database dependencies required
