/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
    List<Integer> ids = RTreeUtils.query(buildingService.getBuildingRtree(), new Envelope(coord));
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.noise_planet.noisemodelling.pathfinder.delaunay.Triangle;
// ...existing imports...
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;

/**
 * Builder constructing profiles from buildings, topography and ground effects.
 *
 * <p>Responsibility split (high level):</p>
 * <ul>
 *   <li><b>BuildingService</b> - stores and manages {@link Building} objects, computes building
 *       elevations from the DEM, precomputes wide-angle/diffraction points, and indexes building
 *       facets into an R-tree for fast spatial queries.</li>
 *   <li><b>WallService</b> - stores and manages {@link Wall} objects, computes wall bottom
 *       elevations, builds processed wall facets, maintains the processed-wall R-tree and
 *       provides wall-on-path queries and intersection visitors.</li>
 *   <li><b>ProcessedWallService</b> - optional sink that stores processed wall facets and
 *       exposes a spatial index (STRtree) optimized for runtime wall/obstacle queries.
 *       This decouples processed-facet storage from raw-wall ingestion and is populated
 *       during the builder "finish" phase by export methods on other services.</li>
 *   <li><b>BridgeService</b> - stores and manages {@link Bridge} objects and indexes bridge
 *       facets; bridges are treated similarly to walls for intersection/indexing purposes.</li>
 *   <li><b>TopographyService</b> - owns the DEM/TIN representation: runs Delaunay meshing,
 *       stores triangles/vertices, exposes DEM queries such as {@code getZGround(...)} and
 *       computes topographic cut points used when building a {@link CutProfile}.</li>
 *   <li><b>GroundService</b> - manages ground absorption areas/effects, indexes ground facets
 *       and resolves ground absorption values for geometry queries.</li>
 *   <li><b>ProfileRetriever / ProfileUtils</b> - (consumer of the services) is responsible for
 *       assembling the {@link CutProfile} along a path by delegating spatial queries to the
 *       services above.</li>
 * </ul>
 *
 * <p>This class is primarily an orchestrator: it accepts input features (buildings, walls,
 * bridges, topography points/lines, ground effects), forwards them to the appropriate service,
 * and when {@link #finishFeeding()} is called coordinates the building of spatial indices and
 * other pre-computations required at runtime. After finishing feeding the builder becomes
 * effectively read-only and services expose fast query methods used by the profile retrieval
 * logic.</p>
 */
/**
 * Builder constructing profiles from buildings, topography and ground effects.
 *
 * <p>This class is an orchestrator over several domain services (building, wall,
 * bridge, topography, ground and processed-wall services). Callers add geometry
 * and attributes using the various {@code add*} methods. Once data feeding is
 * complete the caller must invoke {@link #finishFeeding()} which runs a
 * deterministic, multi-phase preprocessing pipeline:
 * <ol>
 *   <li>construct the TIN/DEM (Delaunay triangulation);</li>
 *   <li>compute elevations for buildings/bridges/walls using the DEM;</li>
 *   <li>export facets and processed wall facets into spatial indices;</li>
 *   <li>finalize spatial indexes for fast runtime queries;</li>
 *   <li>initialize frequency-dependent arrays (alphas, A-weighting) by calling
 *       {@link #setFrequencyArray(Collection)}.</li>
 * </ol>
 *
 * <p>Notes:
 * <ul>
 *   <li>ProfileBuilder is not thread-safe: callers should complete all
 *       modifications before calling {@link #finishFeeding()} and then treat the
 *       builder as effectively read-only for concurrent queries.</li>
 *   <li>Many methods have important side-effects (index builds, internal data
 *       population). These side-effects are documented on the methods that
 *       perform them; callers should not assume silent failures are retried.</li>
 * </ul>
 *
 * <p>Typical usage pattern:
 * <pre>
 *   ProfileBuilder pb = new ProfileBuilder();
 *   pb.addTopographicPoint(...);
 *   pb.addBuilding(...);
 *   pb.finishFeeding();
 *   CutProfile profile = pb.getProfile(src, receiver);
 * </pre>
 */
public class ProfileBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileBuilder.class);

    public enum IntersectionType {BUILDING, WALL, BRIDGE, TOPOGRAPHY, GROUND_EFFECT, SOURCE, RECEIVER, REFLECTION, V_EDGE_DIFFRACTION}

    public static final double epsilon = 1e-7;
    public static final double MILLIMETER = 0.001;
    public static final double LEFT_SIDE = Math.PI / 2;
    private static final int DEFAULT_TREE_NODE_CAPACITY = 5;

    // Use shared GeometryFactory from provider for easier centralization
    // (migrate callers to GeometryFactoryProvider.SHARED)
    
    /** If true, no more data can be add. */
    private boolean isFeedingFinished = false;

    /**
     * Max length of line part used for profile retrieving.
     * @see ProfileBuilder#getProfile(Coordinate, Coordinate)
     */
    private double maxLineLength = 60;

    private BuildingService buildingService;
    private WallService wallService;
    private BridgeService bridgeService;
    private TopographyService topographyService;
    private GroundService groundService;
    private ProcessedWallService processedWallService;
    

    /** Receivers .*/
    private final List<Coordinate> receivers = new ArrayList<>();

    /** Global envelope of the builder. */
    private Envelope envelope;


    // Frequency configuration held in a dedicated instance for easier testing and encapsulation
    public FrequencyConfig frequencyConfig = new FrequencyConfig();

    // Backwards-compatible public fields (kept for callers that access profileBuilder.frequencyArray directly)
    /**
     * Integer third-octave frequency indexes used by the builder (backwards-compatible).
     * Prefer {@link #setFrequencyArray(Collection)} to reconfigure frequencies.
     */
    public List<Integer> frequencyArray = frequencyConfig.getFrequencyArray();

    /**
     * Exact (Hz) frequencies computed from {@link #frequencyArray}. Updated by
     * {@link #setFrequencyArray(Collection)}. Consumers should treat this as
     * read-only.
     */
    public List<Double> exactFrequencyArray = frequencyConfig.getExactFrequencyArray();

    /**
     * A-weighting values corresponding to {@link #exactFrequencyArray}. Updated by
     * {@link #setFrequencyArray(Collection)}. Provided for convenience/compatibility.
     */
    public List<Double> aWeightingArray = frequencyConfig.getAWeightingArray();

    /**
     * Configure which center frequencies (third-octave indexes) will be used during
     * acoustic computations. This method updates internal arrays used by walls and
     * buildings and re-initializes any precomputed frequency-dependent data.
     *
     * <p>Side-effects:
     * <ul>
     *   <li>Recomputes {@link #exactFrequencyArray} and {@link #aWeightingArray} from
     *       the provided integer frequency references.</li>
     *   <li>Calls {@code initialize(...)} on processed and unprocessed walls and
     *       on buildings so that frequency-dependent arrays are available at runtime.</li>
     * </ul>
     *
     * @param frequencyArray Frequency used in the simulation (extracted from
     *                       Scene.DEFAULT_FREQUENCIES_THIRD_OCTAVE). Must be non-null.
     */
    public void setFrequencyArray(Collection<Integer> frequencyArray) {
        LOGGER.info("ProfileBuilder.setFrequencyArray: called with " + (frequencyArray == null ? "null" : ("size=" + frequencyArray.size() + " values=" + frequencyArray)));
        if (frequencyArray == null) {
            throw new IllegalArgumentException("frequencyArray must not be null");
        }
        this.frequencyConfig.setFrequencyArray(frequencyArray);
        this.frequencyArray = frequencyConfig.getFrequencyArray();
        this.exactFrequencyArray = frequencyConfig.getExactFrequencyArray();
        this.aWeightingArray = frequencyConfig.getAWeightingArray();
        // NOTE: This call has side-effects: it initialises per-object
        // frequency-dependent arrays (alphas, etc.) on walls, buildings and
        // bridges. Callers that change frequencies at runtime should ensure
        // services are already fed/available. This method preserves historic
        // behaviour by delegating directly to services.
        if (wallService != null) {
            wallService.initializeFrequencyDependentData(this.exactFrequencyArray);
        } else {
            LOGGER.warn("setFrequencyArray: wallService is null, skipping wall initialization");
        }
        if (buildingService != null) {
            buildingService.initializeFrequencyDependentData(this.exactFrequencyArray);
        } else {
            LOGGER.warn("setFrequencyArray: buildingService is null, skipping building initialization");
        }
        if (bridgeService != null) {
            bridgeService.initializeFrequencyDependentData(this.exactFrequencyArray);
        } else {
            LOGGER.warn("setFrequencyArray: bridgeService is null, skipping bridge initialization");
        }
        if (processedWallService != null) {
            processedWallService.initializeFrequencyDependentData(this.exactFrequencyArray);
        } else {
            LOGGER.warn("setFrequencyArray: processedWallService is null, skipping wall initialization");
        }
    }

    public static void initializeFrequencyArrayFromReference(List<Integer> frequencyArray,
                                                             List<Double> exactFrequencyArray,
                                                             List<Double> aWeightingArray) {
        FrequencyConfig.initializeFrequencyArrayFromReference(frequencyArray, exactFrequencyArray, aWeightingArray);
    }

    /**
     * @param zBuildings if true take into account z value on Buildings Polygons
     * @return this
     */
    public ProfileBuilder setzBuildings(boolean zBuildings) {
        this.buildingService.setZBuildings(zBuildings);
        return this;
    }


    /**
     * Main empty constructor.
     *
     * Initializes services with default R-tree node capacities. Services are the
     * primary holders of geometry and spatial indices; this constructor merely
     * instantiates them so the builder can accept feature inputs.
     */
    public ProfileBuilder() {
        this.buildingService = new BuildingService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.topographyService = new TopographyService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.wallService = new WallService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.bridgeService = new BridgeService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);        
        this.groundService = new GroundService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.processedWallService = new ProcessedWallService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
    }

    /**
     * Main empty constructor.
     *
     * Initializes services with default R-tree node capacities. Services are the
     * primary holders of geometry and spatial indices; this constructor merely
     * instantiates them so the builder can accept feature inputs.
     * @param buildingService BuildingService instance to use
     * @param topographyService TopographyService instance to use
     * @param wallService WallService instance to use
     * @param bridgeService BridgeService instance to use
     * @param groundService GroundService instance to use
     * @param processedWallService ProcessedWallService instance to use
     * 
     */
    public ProfileBuilder(BuildingService buildingService, TopographyService topographyService, WallService wallService, BridgeService bridgeService, GroundService groundService, ProcessedWallService processedWallService) {
        this.buildingService = buildingService;
        this.topographyService = topographyService;
        this.wallService = wallService;
        this.bridgeService = bridgeService;        
        this.groundService = groundService;
        this.processedWallService = processedWallService;
    }

    /**
     * Inject a TopographyService instance. Useful for tests that need to
     * provide a precomputed DEM/TIN to the builder.
     * @param topographyService TopographyService instance to use
     * @return this builder for chaining
     */
    public ProfileBuilder setTopographyService(TopographyService topographyService) {
        if(topographyService != null) {
            this.topographyService = topographyService;
        }
        return this;
    }


    /**
     * Add the given {@link Geometry} footprint.
     * @param building Building.
     */
    public ProfileBuilder addBuilding(Building building) {
        if(building.poly == null || building.poly.isEmpty()) {
            LOGGER.error("Cannot add a building with null or empty geometry.");
        } else if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = building.poly.getEnvelopeInternal();
            } else {
                envelope.expandToInclude(building.poly.getEnvelopeInternal());
            }
            buildingService.addBuilding(building);
            return this;
        } else {
            LOGGER.warn("Cannot add building, feeding is finished.");
        }
        return this;
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param geom   Building footprint.
     */
    public ProfileBuilder addBuilding(Geometry geom) {
        return addBuilding(geom, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords) {
        return addBuilding(coords, -1);
    }

    /**
     * Add the given {@link Geometry} footprint and height as building.
     * @param geom   Building footprint.
     * @param height Building height.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height) {
        return addBuilding(geom, height, new ArrayList<>());
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param height Building height.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height) {
        return addBuilding(coords, height, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param geom   Building footprint.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Geometry geom, int id) {
        return addBuilding(geom, NaN, id);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, int id) {
        return addBuilding(coords, NaN, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as building.
     * @param geom   Building footprint.
     * @param height Building height.
     * @param id     Database id.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, int id) {
        return addBuilding(geom, height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param height Building height.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, int id) {
        return addBuilding(coords, height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height and alphas (absorption coefficients) as building.
     * @param geom   Building footprint.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, List<Double> alphas) {
        return addBuilding(geom, height, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, List<Double> alphas) {
        return addBuilding(coords, height, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint, height and alphas (absorption coefficients) as building.
     * @param geom   Building footprint.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Geometry geom, List<Double> alphas) {
        return addBuilding(geom, NaN, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param alphas Absorption coefficients.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, List<Double> alphas) {
        return addBuilding(coords, NaN, alphas, -1);
    }

    /**
     * Add the given {@link Geometry} footprint, height and alphas (absorption coefficients) as building.
     * @param geom   Building footprint.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Geometry geom, List<Double> alphas, int id) {
        return addBuilding(geom, NaN, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param coords Building footprint coordinates.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, List<Double> alphas, int id) {
        return addBuilding(coords, NaN, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database primary key
     * as building.
     * @param geom   Building footprint.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, List<Double> alphas, int id) {
        if(!(geom instanceof Polygon)) {
            LOGGER.error("Building geometry should be Polygon");
            return this;
        }
        if(!isFeedingFinished) {
            buildingService.addBuilding(geom, height, alphas, id);
            return this;
        } else {
            LOGGER.warn("Cannot add building, feeding is finished.");
            return this;
        }
    }

    /**
     * Add the given {@link Geometry} footprint.
     * @param height Building height.
     * @param alphas Absorption coefficients.
     * @param id     Database primary key.
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, List<Double> alphas, int id) {
        if(!isFeedingFinished) {
            buildingService.addBuilding(coords, height, alphas, id);
            if (coords != null && coords.length > 0) {
                int l = coords.length;
                boolean closed = coords[0].equals2D(coords[l - 1]);
                Coordinate[] polyCoords = closed ? coords : java.util.Arrays.copyOf(coords, l + 1);
                if (!closed) {
                    polyCoords[l] = new Coordinate(coords[0]);
                }
                if(envelope == null) {
                    envelope = GeometryFactoryProvider.SHARED.createPolygon(polyCoords).getEnvelopeInternal();
                } else {
                    envelope.expandToInclude(GeometryFactoryProvider.SHARED.createPolygon(polyCoords).getEnvelopeInternal());
                }
            }
            return this;
        } else {
            LOGGER.warn("Cannot add building, feeding is finished.");
            return this;
        }
    }

    /**
     * Add the given Bridge object.
     * @param bridge Bridge object to add.
     * @return This ProfileBuilder instance for method chaining.
     */
    public ProfileBuilder addBridge(Bridge bridge) {
        if (!isFeedingFinished) {
            // update envelope using bridge footprint
            Geometry footprint = bridge.getFootprintGeometry();
            if (footprint != null) {
                if (envelope == null) {
                    envelope = footprint.getEnvelopeInternal();
                } else {
                    envelope.expandToInclude(footprint.getEnvelopeInternal());
                }
            }
            this.bridgeService.addBridge(bridge);
        }
        return this;
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param geom   Wall footprint.
     * @param height Wall height.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(LineString geom, double height, int id) {
        return addWall(geom, height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param height Wall height.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, double height, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), height, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param geom   Wall footprint.
     * @param id     Database key.
     */
    /*public ProfileBuilder addWall(LineString geom, int id) {
        return addWall(geom, 0.0, new ArrayList<>(), id);
    }*/

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), 0.0, new ArrayList<>(), id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param wall
     */
    public ProfileBuilder addWall(Wall wall) {
        wallService.addWall(wall);
        return this;
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param geom   Wall footprint.
     * @param height Wall height.
     * @param alphas Absorption coefficient.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(LineString geom, double height, List<Double> alphas, int id) {
        if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = geom.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(geom.getEnvelopeInternal());
            }

            for(int i=0; i<geom.getNumPoints()-1; i++) {
                Wall wall = new Wall(geom.getCoordinateN(i), geom.getCoordinateN(i+1), id, IntersectionType.BUILDING);
                wall.setHeight(height);
                wall.setAlpha(alphas);
                addWall(wall);
            }
            return this;
        }
        else{
            LOGGER.warn("Cannot add building, feeding is finished.");
            return this;
        }
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, double height, List<Double> alphas, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), height, alphas, id);
    }

    /**
     * Add the given {@link Geometry} footprint, height, alphas (absorption coefficients) and a database id as wall.
     * @param coords Wall footprint coordinates.
     * @param id     Database key.
     */
    public ProfileBuilder addWall(Coordinate[] coords, List<Double> alphas, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), 0.0, alphas, id);
    }

    /**
     * Add the topographic point in the data, to complete the topographic data.
     * @param point Topographic point.
     */
    public ProfileBuilder addTopographicPoint(Coordinate point) {
        if(!isFeedingFinished) {
            //Force to 3D
            if (isNaN(point.z)) {
                point.setCoordinate(new Coordinate(point.x, point.y, 0.));
            }
            if(envelope == null) {
                envelope = new Envelope(point);
            }
            else {
                envelope.expandToInclude(point);
            }
            this.topographyService.addTopographicPoint(point);
        }
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     */
    public ProfileBuilder addTopographicLine(LineSegment segment) {
        addTopographicLine(segment.p0, segment.p1);
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     */
    public ProfileBuilder addTopographicLine(Coordinate p0, Coordinate p1) {
        addTopographicLine(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z);
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     */
    public ProfileBuilder addTopographicLine(double x0, double y0, double z0, double x1, double y1, double z1) {
        if(!isFeedingFinished) {
            LineString lineSegment = GeometryFactoryProvider.SHARED.createLineString(new Coordinate[]{new Coordinate(x0, y0, z0), new Coordinate(x1, y1, z1)});
            if(envelope == null) {
                envelope = lineSegment.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(lineSegment.getEnvelopeInternal());
            }
            this.topographyService.addTopographicLine(lineSegment);
        }
        return this;
    }

    /**
     * Add the topographic line in the data, to complete the topographic data.
     * @param lineSegment Topographic line.
     */
    public ProfileBuilder addTopographicLine(LineString lineSegment) {
        if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = lineSegment.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(lineSegment.getEnvelopeInternal());
            }
            this.topographyService.addTopographicLine(lineSegment);
        }
        return this;
    }

    /**
     * Add a ground effect.
     * @param geom        Ground effect area footprint.
     * @param coefficient Ground effect coefficient.
     */
    public ProfileBuilder addGroundEffect(Geometry geom, double coefficient) {
        if(!isFeedingFinished) {
            if(envelope == null) {
                envelope = geom.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(geom.getEnvelopeInternal());
            }
            this.groundService.addGroundAbsorption(new GroundAbsorption(geom, coefficient));
        }
        return this;
    }

    /**
     * Add a ground effect.
     * @param minX        Ground effect minimum X.
     * @param maxX        Ground effect maximum X.
     * @param minY        Ground effect minimum Y.
     * @param maxY        Ground effect maximum Y.
     * @param coefficient Ground effect coefficient.
     */
    public ProfileBuilder addGroundEffect(double minX, double maxX, double minY, double maxY, double coefficient) {
        if(!isFeedingFinished) {
            Geometry geom = GeometryFactoryProvider.SHARED.createPolygon(new Coordinate[]{
                    new Coordinate(minX, minY),
                    new Coordinate(minX, maxY),
                    new Coordinate(maxX, maxY),
                    new Coordinate(maxX, minY),
                    new Coordinate(minX, minY)
            });
            if(envelope == null) {
                envelope = geom.getEnvelopeInternal();
            }
            else {
                envelope.expandToInclude(geom.getEnvelopeInternal());
            }
            this.groundService.addGroundAbsorption(new GroundAbsorption(geom, coefficient));
        }
        return this;
    }

    public List<Wall> getProcessedWalls() {
        return processedWallService.getProcessedWalls();
    }

    /**
     * Retrieve the building list.
     * @return The building list.
     */
    public List<Building> getBuildings() {
    return buildingService.getBuildings();
    }

    /**
     * Retrieve the count of building add to this builder.
     * @return The count of building.
     */
    public int getBuildingCount() {
    return buildingService.getBuildingCount();
    }

    /**
     * Retrieve the building with the given id (id is starting from 1).
     * @param id Id of the building
     * @return The building corresponding to the given id.
     */
    public Building getBuilding(int id) {
    return buildingService.getBuilding(id);
    }

    /**
     * Retrieve the wall list.
     * @return The wall list.
     */
    public List<Wall> getWalls() {
        return wallService.getWalls();
    }

    /**
    /** Retrieve the bridge list.
     * @return The bridge list.
     */
    public List<Bridge> getBridges() {
        return bridgeService.getBridges();
    }

    /**
     * Retrieve the count of wall add to this builder.
     * @return The count of wall.
     */
    public int getWallCount() {
        return wallService.getWallCount();
    }

    /**
     * Retrieve the wall with the given id (id is starting from 1).
     * @param id Id of the wall
     * @return The wall corresponding to the given id.
     */
    public Wall getWall(int id) {
        return wallService.getWall(id);
    }

    /**
     * Clear the building list.
     */
    public void clearBuildings() {
        buildingService.clear();
    }

    /**
    /** Retrieve the count of bridges added to this builder.
     * @return The count of bridges.
     */
    public int getBridgeCount() {
        return bridgeService.getBridgeCount();
    }

    /**
    /** Retrieve the bridge with the given id (id is starting from 0).
     * @param id Id of the bridge
     * @return The bridge corresponding to the given id.
     */
    public Bridge getBridge(int id) {
        return bridgeService.getBridge(id);
    }

    /**
    /** Retrieve the bridge with the given id (id is starting from 0).
     * @param id Id of the bridge
     * @return The bridge corresponding to the given id.
     */
    public Bridge getBridgeByPk(long pk) {
        return bridgeService.getBridgeByPk(pk);
    }

    /**
     * Clear the bridge list.
     */
    public void clearBridges() {
       bridgeService.clear();
    }

    /**
     * Retrieve the global profile envelope.
     * @return The global profile envelope.
     */
    public Envelope getMeshEnvelope() {
        return envelope;
    }

    /**
     * Retrieve the topographic triangles.
     * @return The topographic triangles.
     */
    public List<Triangle> getTriangles() {
    return topographyService.getTriangles();
    }

    /**
     * Retrieve the topographic vertices.
     * @return The topographic vertices.
     */
    public List<Coordinate> getVertices() {
    return topographyService.getVertices();
    }

    /**
     * Retrieve the receivers list.
     * @return The receivers list.
     */
    public List<Coordinate> getReceivers() {
        return receivers;
    }

    /**
     * Retrieve the ground effects.
     * @return The ground effects.
     */
    public List<GroundAbsorption> getGroundEffects() {
    return groundService.getGroundAbsorptions();
    }

    /**
     * Finish the data feeding and perform all pre-processing required before
     * runtime profile queries. After calling this method the builder becomes
     * effectively read-only: adding new features will be discouraged and likely
     * ignored by callers.
     *
     * What this method does (orchestrator summary):
     * <ol>
     *   <li>Runs Delaunay meshing on topographic points/lines so the TIN/DEM
     *       representation is available for elevation queries and triangle
     *       intersections.</li>
     *   <li>Asks {@link BuildingService}, {@link BridgeService} and {@link WallService} to compute
     *       building/bridge/wall elevations from the DEM where required.</li>
     *   <li>Indexes building and bridge facets into spatial indices used at
     *       runtime (R-trees) and builds processed wall facets.</li>
     *   <li>Indexes ground absorption areas and finalizes R-trees used for fast
     *       spatial lookup.</li>
     *   <li>Initializes frequency-dependent arrays on walls and buildings by
     *       calling {@link #setFrequencyArray(Collection)}.</li>
     * </ol>
     *
     * Note: Some called methods return success/failure flags but have important
     * side-effects (building indices, triangles population). This method delegates
     * to those services and does not attempt to repeat their internal checks.
     *
     * @return this builder to allow fluent chaining. (The original signature
     *         returned a builder instance; callers should treat it as the same
     *         object with finalized internal state.)
     */
    public ProfileBuilder finishFeeding() {
        isFeedingFinished = true;

        // High-level phased workflow. Each phase is extracted to a small helper
        // to make the intent and ordering explicit and easy to document/test.
        LOGGER.info("finishFeeding: starting topography processing");
        processTopography();

        LOGGER.info("finishFeeding: computing building/wall elevations");
        computeElevations();

        LOGGER.info("finishFeeding: indexing building/bridge facets into wall service");
        exportFacetsToProcessedWalls();

        LOGGER.info("finishFeeding: finalizing processed walls and ground effects indexes");
        finalizeIndexes();

        LOGGER.info("finishFeeding: initializing frequency dependent data");
        initializeFrequencyDependentData();

        LOGGER.info("finishFeeding: completed");
        return this;
    }

    // --- extracted helper phases for clarity ---
    /**
     * Phase 1 — build the topography (TIN/DEM) representation.
     *
     * This method runs the Delaunay triangulation and populates the
     * TopographyService internal structures (triangles, vertices, topo index)
     * used by elevation queries. Must be executed before elevation-dependent
     * computations.
     */
    private void processTopography() {
        // buildDelaunayTriangulation has important side-effects (triangles, vertices, topoTree)
        topographyService.buildDelaunayTriangulation();
    }

    /**
     * Phase 2 — compute elevations for buildings, bridges and walls.
     *
     * Each service reads the topography (via this builder) and updates
     * geometry Z coordinates where necessary. This phase depends on
     * {@link #processTopography()} having been run.
     */
    private void computeElevations() {
        buildingService.computeElevations(this);
        bridgeService.computeElevations(this);
        wallService.computeElevations(this);
    }

    /**
     * Phase 3 — export facets and raw walls into the ProcessedWallService.
     *
     * Buildings and bridges facets, and walls are exported
     * into the ProcessedWallService processed walls list. 
     * GroundServic is asked to index ground absorption features.
     */
    private void exportFacetsToProcessedWalls() {
        buildingService.exportFacetsToProcessedWalls(processedWallService);
        bridgeService.exportFacetsToProcessedWalls(processedWallService);
        wallService.exportFacetsToProcessedWalls(processedWallService);
        groundService.exportFacetsToProcessedWalls(processedWallService);
    }

    /**
     * Phase 4 — finalize spatial indexes used for runtime queries.
     *
     * Build the STRtree structures after all inserts to ensure index
     * integrity and optimal query performance.
     */
    private void finalizeIndexes() {
        processedWallService.buildProcessedWallRtree();
        groundService.buildGroundEffectsRtree();
    }

    /**
     * Phase 5 — initialize per-frequency data on objects.
     *
     * This method initializes frequency-dependent arrays (alpha coefficients
     * and related data) and must run after geometries and indices are final.
     */
    private void initializeFrequencyDependentData() {
        setFrequencyArray(frequencyArray);
    }


    /**
     * Return the altitude (z) at the given coordinate. If the coordinate lies
     * inside a building footprint with an elevation assigned, the building
     * elevation is returned; otherwise the DEM (topography) elevation is
     * returned.
     *
     * @param coord 3D coordinate (x,y[,z]) for which elevation is requested. If
     *              z is NaN the DEM is used to compute ground elevation when
     *              needed.
     * @return elevation in meters above sea level (building elevation if point
     *         is inside a building, otherwise DEM elevation). If no DEM is
     *         available and building elevation cannot be determined, behavior is
     *         delegated to {@link TopographyService#getZGround(Coordinate)}.
     */
    public double getZ(Coordinate coord) {
    List<Integer> ids = RTreeUtils.query(buildingService.getBuildingRtree(), new Envelope(coord));
        if(ids.isEmpty()) {
            return getZGround(coord);
        }
        else {
            for(Integer id : ids) {
                Geometry buildingGeometry =  buildingService.getBuilding(id - 1).getGeometry();
                if(buildingGeometry.getEnvelopeInternal().intersects(coord)) {
                    return buildingGeometry.getCoordinate().z;
                }
            }
            return getZGround(coord);
        }
    }



    /**
     * Retrieve the cutting profile following the line built from the given
     * coordinates. This is a convenience overload that uses a default ground
     * attenuation of 0 and does not stop at obstacles higher than the source-
     * receiver segment.
     *
     * @param c0 Starting point (usually source coordinate).
     * @param c1 Ending point (usually receiver coordinate).
     * @return A {@link CutProfile} describing intersections with DEM, buildings,
     *         walls, bridges and ground effects along the straight segment between
     *         the two coordinates.
     */
    public CutProfile getProfile(Coordinate c0, Coordinate c1) {
        return getProfile(c0, c1, 0.0, false);
    }


    /**
     * Retrieve the cutting profile following the line built from the given
     * coordinates. This method delegates the heavy lifting to
     * {@link ProfileRetriever#getProfile(...)} which composes geometry intersections
     * from the services managed by this builder.
     *
     * @param sourceCoordinate Starting point (3D coordinate expected; if z is
     *                         NaN some services may query the DEM for elevation).
     * @param receiverCoordinate Ending point.
     * @param defaultGroundAttenuation Default ground absorption value to use when
     *                                 no ground absorption feature is found.
     * @param stopAtObstacleOverSourceReceiver If true, profile computation will
     *                                         abort early when an obstacle higher
     *                                         than the straight source-receiver
     *                                         segment is encountered; the
     *                                         returned {@link CutProfile} will
     *                                         contain intersection details.
     * @return Cutting profile assembled by querying building, wall, bridge,
     *         topography and ground services.
     */
    public CutProfile getProfile(Coordinate sourceCoordinate, Coordinate receiverCoordinate, double defaultGroundAttenuation, boolean stopAtObstacleOverSourceReceiver) {
        return ProfileRetriever.getProfile(sourceCoordinate, receiverCoordinate, defaultGroundAttenuation, stopAtObstacleOverSourceReceiver, maxLineLength, buildingService, wallService, bridgeService, topographyService, groundService, processedWallService, GeometryFactoryProvider.SHARED);
    }
    /**
     * Fetch the first intersecting ground absorption object index that intersects
     * with the provided geometry.
     *
     * Delegates to {@link GroundService} which owns the ground effects index.
     *
     * @param query The geometry object to check for intersection.
     * @return Index/identifier of the matched ground absorption object or -1 if
     *         none is found. (The ground service defines the exact return
     *         semantics; callers should consult {@link GroundService#getIntersectingGroundAbsorption}).
     */
    public int getIntersectingGroundAbsorption(Geometry query) {
        // Delegate to GroundService which owns the ground effects index
        return this.groundService.getIntersectingGroundAbsorption(query);
    }
    public Coordinate[] getTriangle(int triIndex) {
        return topographyService.getTriangle(triIndex);
    }


    /**
     * Get coordinates of triangle vertices (last point is first point)
     * @param triIndex Index of triangle
     * @return triangle vertices
     */
    public Coordinate[] getClosedTriangle(int triIndex) {
        return topographyService.getClosedTriangle(triIndex);
    }

    /**
     * Delegate to WallService.getWallsIn
     * @param env envelope to query
     * @return list of walls intersecting envelope
     */
    public List<Wall> getWallsIn(org.locationtech.jts.geom.Envelope env) {
        return processedWallService.getWallsIn(env);
    }

    /**
     * Return the triangle id from a point coordinate inside the triangle
     *
     * @param pt Point test
     * @return Triangle Id, Or -1 if no triangle has been found
     */

    public int getTriangleIdByCoordinate(Coordinate pt) {
        return topographyService.getTriangleIdByCoordinate(pt);
    }

    /**
     * Add topographic cut points to the provided {@link CutProfile} between the
     * two coordinates. This method delegates to {@link TopographyService} which
     * computes intersections between the line p1-p2 and the DEM/TIN and appends
     * meaningful plane-change points to the profile.
     *
     * @param p1 first point of the interrogated segment
     * @param p2 second point of the interrogated segment
     * @param profile profile to receive topographic cut points (modified in place)
     * @param stopAtObstacleOverSourceReceiver If true, topography will stop
     *                                         adding points when an obstacle
     *                                         higher than the source-receiver
     *                                         straight segment is encountered.
     */
    public void addTopoCutPts(Coordinate p1, Coordinate p2, CutProfile profile, boolean stopAtObstacleOverSourceReceiver) {
        // Delegate DEM/TIN cut point computation to TopographyService
        this.topographyService.addTopoCutPts(p1, p2, profile, stopAtObstacleOverSourceReceiver);
    }

    /**
     * Find closest triangle that intersects with segment
     * @param segment Segment to intersects will all triangles
     * @param intersection Found closest intersection point with p0
     * @param intersectionTriangle Found closest intersection triangle
     * @return True if at least one triangle as been found on intersection
     */
    public boolean findClosestTriangleIntersection(LineSegment segment, final Coordinate intersection, AtomicInteger intersectionTriangle) {
        return topographyService.findClosestTriangleIntersection(segment, intersection, intersectionTriangle);
    }

    /**
     * Fetch all intersections with TIN. For simplification only plane change are pushed.
     * @param p1 first point
     * @param p2 second point
     * @param stopAtObstacleOverSourceReceiver Stop fetching intersections if the segment p1-p2 is intersecting with TIN
     * @return True if the segment p1-p2 is not intersecting with DEM
     */
    public boolean fetchTopographicProfile(List<Coordinate> outputPoints,Coordinate p1, Coordinate p2, boolean stopAtObstacleOverSourceReceiver) {
        return this.topographyService.fetchTopographicProfile(outputPoints, p1, p2, stopAtObstacleOverSourceReceiver);
    }


    /**
     * @return True if digital elevation model has been added
     */
    public boolean hasDem() {
        STRtree tree = topographyService.getTopoRtree();
        return tree != null && !tree.isEmpty();
    }

    /**
     * @return Mesh of digital elevation model
     */
    public MultiPolygon demAsMultiPolygon() {
        return this.topographyService.demAsMultiPolygon();
    }


    /**
     * @return Altitude in meters from sea level
     */
    public double getZGround(Coordinate coordinate) {
        return this.topographyService.getZGround(coordinate);
    }

    /**
     * Fetch Altitude in meters from sea level at a location. You can use the triangle hint if you request a lot of
     * positions in the same location
     * @param coordinate X,Y coordinate to fetch
     * @param triangleHint Triangle index hint (if {@literal >=} 0 will be checked, and will be updated with the triangle is found)
     * @return Altitude in meters from sea level
     */
    public double getZGround(Coordinate coordinate, AtomicInteger triangleHint) {
        return this.topographyService.getZGround(coordinate, triangleHint);
    }


    // Buffer around obstacles when computing diffraction (ISO / TR 17534-4 look like using this value)
    public static final double wideAngleTranslationEpsilon = 0.015;

    /**
     * @param build 1-n based building identifier
     * @return
     */
    public ArrayList<Coordinate> getPrecomputedWideAnglePoints(int build) {
    return buildingService.getPrecomputedWideAnglePoints(build);
    }

    /**
     * @param linearRing Coordinates loop
     * @param minAngle
     * @param maxAngle
     * @return
     */
    public ArrayList<Coordinate> getWideAnglePointsOnPolygon(LinearRing linearRing, double minAngle, double maxAngle) {
        // Delegate to BuildingService implementation
        return buildingService.getWideAnglePointsOnPolygon(linearRing, minAngle, maxAngle);
    }

    /**
     * Query walls intersecting the path between p1 and p2 and notify the
     * provided visitor for each intersection. This method delegates the spatial
     * lookup to {@link WallService} which maintains an R-tree of processed wall
     * facets.
     *
     * @param p1 start of path
     * @param p2 end of path
     * @param visitor callback that receives wall intersection events; the
     *                visitor will be invoked in the order discovered by the
     *                wall service.
     */
    public void getWallsOnPath(Coordinate p1, Coordinate p2, BuildingIntersectionPathVisitor visitor) {
        // Delegate to WallService which owns processedRtree
        processedWallService.getWallsOnPath(p1, p2, visitor, maxLineLength);
    }


}
