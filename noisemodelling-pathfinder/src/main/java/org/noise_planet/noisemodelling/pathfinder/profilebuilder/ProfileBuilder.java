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
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
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
 *   <li><b>FrequencyConfig</b> - manages frequency-dependent configuration including frequency
 *       bands, exact frequency arrays, and frequency-dependent acoustic parameters used throughout
 *       the noise modeling calculations. Provides frequency settings for absorption coefficients
 *       and A-weighting computations.</li>
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



    public static final double EPSILON = 1e-7;
    public static final double MILLIMETER = 0.001;
    private static final int DEFAULT_TREE_NODE_CAPACITY = 5;

    /** If true, no more data can be add. */
    private boolean isFeedingFinished = false;

    /**
     * Max length of line part used for profile retrieving.
     * @see ProfileBuilder#getProfile(Coordinate, Coordinate)
     */
    private double maxLineLength = 60;

    /** Services */
    private BuildingService buildingService;
    private WallService wallService;
    private BridgeService bridgeService;
    private TopographyService topographyService;
    private GroundService groundService;
    private ProcessedWallService processedWallService;
    private FrequencyConfig frequencyConfig;
    

    /** Global envelope of the builder. */
    private Envelope envelope;

    /**
     * Get the current frequency configuration object.
     * @return The frequency configuration used by this builder
     */
    public FrequencyConfig getFrequencyConfig() {
        return frequencyConfig;
    }
    
    /**
     * Set the frequency configuration for this builder.
     * @param frequencyConfig The frequency configuration to use
     */
    public void setFrequencyConfig(FrequencyConfig frequencyConfig) {
        this.frequencyConfig = frequencyConfig;
    }

    /**
     * Get the current frequency band configuration.
     * @return The frequency band setting from the frequency configuration
     */
    public FrequencyConfig.FrequencyBand getFrequencyBand() {
        return frequencyConfig.getFrequencyBand();
    }
    
    /**
     * Set the frequency band configuration.
     * @param frequencyBand The frequency band to use for calculations
     */
    public void setFrequencyBand(FrequencyConfig.FrequencyBand frequencyBand) {
        frequencyConfig.setFrequencyBand(frequencyBand);
    }

    /**
     * Get the frequency array as integer values.
     * @return List of frequency values in Hz
     */
    public List<Integer> getFrequencyArray() {
        return frequencyConfig.getFrequencyArray();
    }

    /**
     * Set the frequency array for acoustic calculations.
     * @param frequencyArray Collection of frequency values in Hz
     */
    public void setFrequencyArray(Collection<Integer> frequencyArray) {
        this.frequencyConfig.setFrequencyArray(frequencyArray);
    }

    /**
     * Get the exact frequency array as double values.
     * @return List of exact frequency values in Hz
     */
    public List<Double> getExactFrequencyArray() {
        return frequencyConfig.getExactFrequencyArray();
    }

    /**
     * Configure whether to use Z-values from building polygon vertices.
     * @param zBuildings if true take into account z value on Buildings Polygons
     * @return this builder for method chaining
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
        this.frequencyConfig = new FrequencyConfig();
    }

    /**
     * Constructor with a frequency configuration.
     *
     * @param frequencyConfig Frequency configuration to use
     */
    
    public ProfileBuilder(FrequencyConfig frequencyConfig) {
        this.buildingService = new BuildingService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.topographyService = new TopographyService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.wallService = new WallService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.bridgeService = new BridgeService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);        
        this.groundService = new GroundService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.processedWallService = new ProcessedWallService(DEFAULT_TREE_NODE_CAPACITY, GeometryFactoryProvider.SHARED);
        this.frequencyConfig = frequencyConfig;
    }

    /**
     * Constructor accepting all service dependencies for dependency injection.
     * Allows external control over service instantiation and configuration.
     *
     * @param buildingService BuildingService instance to use
     * @param topographyService TopographyService instance to use
     * @param wallService WallService instance to use
     * @param bridgeService BridgeService instance to use
     * @param groundService GroundService instance to use
     * @param processedWallService ProcessedWallService instance to use
     * @param frequencyConfig FrequencyConfig instance to use
     */
    public ProfileBuilder(BuildingService buildingService, TopographyService topographyService, WallService wallService, BridgeService bridgeService, GroundService groundService, ProcessedWallService processedWallService, FrequencyConfig frequencyConfig) {
        this.buildingService = buildingService;
        this.topographyService = topographyService;
        this.wallService = wallService;
        this.bridgeService = bridgeService;        
        this.groundService = groundService;
        this.processedWallService = processedWallService;
        this.frequencyConfig = frequencyConfig;
    }

    /**
     * Inject a TopographyService instance. Useful for tests that need to
     * provide a precomputed DEM/TIN to the builder.
     * @param topographyService TopographyService instance to use
     * @return this builder for chaining
     */
    public ProfileBuilder setTopographyService(TopographyService topographyService) {
        this.topographyService = topographyService;
        return this;
    }


    /**
     * Add a building object to the model. Validates geometry and updates the global envelope.
     * @param building Building object containing geometry and properties
     * @return this builder for method chaining
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
     * Add a building with default properties using geometry footprint.
     * @param geom Building footprint geometry
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom) {
        return addBuilding(geom, -1);
    }

    /**
     * Add a building with default properties using coordinate array.
     * @param coords Building footprint coordinates
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords) {
        return addBuilding(coords, -1);
    }

    /**
     * Add a building with specified height using geometry footprint.
     * @param geom Building footprint geometry
     * @param height Building height in meters
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom, double height) {
        return addBuilding(geom, height, new ArrayList<>());
    }

    /**
     * Add a building with specified height using coordinate array.
     * @param coords Building footprint coordinates
     * @param height Building height in meters
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height) {
        return addBuilding(coords, height, -1);
    }

    /**
     * Add a building with database ID using geometry footprint.
     * @param geom Building footprint geometry
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom, int id) {
        return addBuilding(geom, NaN, id);
    }

    /**
     * Add a building with database ID using coordinate array.
     * @param coords Building footprint coordinates
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, int id) {
        return addBuilding(coords, NaN, id);
    }

    /**
     * Add a building with height and database ID using geometry footprint.
     * @param geom Building footprint geometry
     * @param height Building height in meters
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, int id) {
        return addBuilding(geom, height, new ArrayList<>(), id);
    }

    /**
     * Add a building with height and database ID using coordinate array.
     * @param coords Building footprint coordinates
     * @param height Building height in meters
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, int id) {
        return addBuilding(coords, height, new ArrayList<>(), id);
    }

    /**
     * Add a building with height and absorption coefficients using geometry footprint.
     * @param geom Building footprint geometry
     * @param height Building height in meters
     * @param alphas Sound absorption coefficients per frequency band
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom, double height, List<Double> alphas) {
        return addBuilding(geom, height, alphas, -1);
    }

    /**
     * Add a building with height and absorption coefficients using coordinate array.
     * @param coords Building footprint coordinates
     * @param height Building height in meters
     * @param alphas Sound absorption coefficients per frequency band
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, double height, List<Double> alphas) {
        return addBuilding(coords, height, alphas, -1);
    }

    /**
     * Add a building with absorption coefficients using geometry footprint (default height).
     * @param geom Building footprint geometry
     * @param alphas Sound absorption coefficients per frequency band
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom, List<Double> alphas) {
        return addBuilding(geom, NaN, alphas, -1);
    }

    /**
     * Add a building with absorption coefficients using coordinate array (default height).
     * @param coords Building footprint coordinates
     * @param alphas Sound absorption coefficients per frequency band
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, List<Double> alphas) {
        return addBuilding(coords, NaN, alphas, -1);
    }

    /**
     * Add a building with absorption coefficients and database ID using geometry footprint.
     * @param geom Building footprint geometry
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Geometry geom, List<Double> alphas, int id) {
        return addBuilding(geom, NaN, alphas, id);
    }

    /**
     * Add a building with absorption coefficients and database ID using coordinate array.
     * @param coords Building footprint coordinates
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addBuilding(Coordinate[] coords, List<Double> alphas, int id) {
        return addBuilding(coords, NaN, alphas, id);
    }

    /**
     * Add a building with complete properties specification (the main implementation method).
     * Validates that geometry is a Polygon and adds building to the service if feeding is not finished.
     *
     * @param geom Building footprint geometry (must be Polygon)
     * @param height Building height in meters (NaN for default)
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key (-1 for auto-generated)
     * @return this builder for method chaining
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
     * Add a building with complete properties using coordinate array (main implementation).
     * Creates closed polygon if necessary and updates global envelope.
     *
     * @param coords Building footprint coordinates
     * @param height Building height in meters (NaN for default)
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key (-1 for auto-generated)
     * @return this builder for method chaining
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
     * Add a wall with specified height and database ID using LineString geometry.
     * @param geom Wall geometry as LineString
     * @param height Wall height in meters
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addWall(LineString geom, double height, int id) {
        return addWall(geom, height, new ArrayList<>(), id);
    }

    /**
     * Add a wall with specified height and database ID using coordinate array.
     * @param coords Wall coordinates
     * @param height Wall height in meters
     * @param id Database primary key
     * @return this builder for method chaining
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
     * Add a wall with default height using coordinate array.
     * @param coords Wall coordinates
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addWall(Coordinate[] coords, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), 0.0, new ArrayList<>(), id);
    }

    /**
     * Add a pre-constructed Wall object to the model.
     * @param wall Wall object containing all properties
     * @return this builder for method chaining
     */
    public ProfileBuilder addWall(Wall wall) {
        wallService.addWall(wall);
        return this;
    }

    /**
     * Add a wall with complete properties specification (main implementation method).
     * Creates individual wall segments from LineString geometry and updates global envelope.
     *
     * @param geom Wall geometry as LineString
     * @param height Wall height in meters
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key
     * @return this builder for method chaining
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
            LOGGER.warn("Cannot add wall, feeding is finished.");
            return this;
        }
    }

    /**
     * Add a wall using coordinate array with complete properties.
     * @param coords Wall coordinates
     * @param height Wall height in meters
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addWall(Coordinate[] coords, double height, List<Double> alphas, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), height, alphas, id);
    }

    /**
     * Add a wall with absorption coefficients and default height using coordinate array.
     * @param coords Wall coordinates
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addWall(Coordinate[] coords, List<Double> alphas, int id) {
    return addWall(GeometryFactoryProvider.SHARED.createLineString(coords), 0.0, alphas, id);
    }

    /**
     * Add a topographic point to complete the digital elevation model.
     * Forces 3D coordinates and updates the global envelope.
     *
     * @param point Topographic point coordinate (Z value will be set to 0 if NaN)
     * @return this builder for method chaining
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
     * Add a topographic line segment to the digital elevation model.
     * @param segment Line segment containing two endpoints
     * @return this builder for method chaining
     */
    public ProfileBuilder addTopographicLine(LineSegment segment) {
        addTopographicLine(segment.p0, segment.p1);
        return this;
    }

    /**
     * Add a topographic line between two coordinates to the digital elevation model.
     * @param p0 First endpoint coordinate
     * @param p1 Second endpoint coordinate
     * @return this builder for method chaining
     */
    public ProfileBuilder addTopographicLine(Coordinate p0, Coordinate p1) {
        addTopographicLine(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z);
        return this;
    }

    /**
     * Add a topographic line using explicit coordinate values.
     * @param x0 X coordinate of first point
     * @param y0 Y coordinate of first point
     * @param z0 Z coordinate of first point (elevation)
     * @param x1 X coordinate of second point
     * @param y1 Y coordinate of second point
     * @param z1 Z coordinate of second point (elevation)
     * @return this builder for method chaining
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
     * Add a topographic line using LineString geometry to the digital elevation model.
     * Updates the global envelope and forwards to the topography service.
     *
     * @param lineSegment LineString representing the topographic line
     * @return this builder for method chaining
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
     * Add a ground absorption area with specified sound absorption coefficient.
     * Updates the global envelope and adds to the ground service.
     *
     * @param geom Ground effect area geometry
     * @param coefficient Sound absorption coefficient (0.0 to 1.0)
     * @return this builder for method chaining
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
     * Add a rectangular ground absorption area using bounding coordinates.
     * Creates a polygon from the bounding box and adds it with the specified coefficient.
     *
     * @param minX Ground effect minimum X coordinate
     * @param maxX Ground effect maximum X coordinate
     * @param minY Ground effect minimum Y coordinate
     * @param maxY Ground effect maximum Y coordinate
     * @param coefficient Sound absorption coefficient (0.0 to 1.0)
     * @return this builder for method chaining
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

    /**
     * Get the list of processed walls from the processed wall service.
     * @return List of processed Wall objects
     */
    public List<Wall> getProcessedWalls() {
        return processedWallService.getProcessedWalls();
    }

    /**
     * Retrieve the list of all buildings added to this builder.
     * @return The complete list of Building objects
     */
    public List<Building> getBuildings() {
    return buildingService.getBuildings();
    }

    /**
     * Retrieve the total count of buildings added to this builder.
     * @return The number of buildings
     */
    public int getBuildingCount() {
    return buildingService.getBuildingCount();
    }

    /**
     * Retrieve a specific building by its index (0-based indexing).
     * @param id Index of the building (0-based)
     * @return The Building object at the specified index
     */
    public Building getBuilding(int id) {
    return buildingService.getBuilding(id);
    }

    /**
     * Retrieve the list of all walls added to this builder.
     * @return The complete list of Wall objects
     */
    public List<Wall> getWalls() {
        return wallService.getWalls();
    }

    /**
     * Retrieve the list of all bridges added to this builder.
     * @return The complete list of Bridge objects
     */
    public List<Bridge> getBridges() {
        return bridgeService.getBridges();
    }

    /**
     * Check if any bridges have been added to this builder.
     * @return true if bridges are present, false otherwise
     */
    public boolean hasBridges() {
        return getBridges().size() > 0;
    }

    /**
     * Retrieve the total count of walls added to this builder.
     * @return The number of walls
     */
    public int getWallCount() {
        return wallService.getWallCount();
    }

    /**
     * Retrieve a specific wall by its index (0-based indexing).
     * @param id Index of the wall (0-based)
     * @return The Wall object at the specified index
     */
    public Wall getWall(int id) {
        return wallService.getWall(id);
    }

    /**
     * Clear all buildings from the building service.
     */
    public void clearBuildings() {
        buildingService.clear();
    }

    /**
     * Retrieve the total count of bridges added to this builder.
     * @return The number of bridges
     */
    public int getBridgeCount() {
        return bridgeService.getBridgeCount();
    }

    /**
     * Retrieve a specific bridge by its index (0-based indexing).
     * @param id Index of the bridge (0-based)
     * @return The Bridge object at the specified index
     */
    public Bridge getBridge(int id) {
        return bridgeService.getBridge(id);
    }

    /**
     * Retrieve a bridge by its primary key identifier.
     * @param pk Primary key of the bridge in the database
     * @return The Bridge object with the specified primary key
     */
    public Bridge getBridgeByPk(long pk) {
        return bridgeService.getBridgeByPk(pk);
    }

    /**
     * Clear all bridges from the bridge service.
     */
    public void clearBridges() {
       bridgeService.clear();
    }

    /**
     * Get the global bounding envelope covering all added geometries.
     * @return The envelope bounding all features in the model
     */
    public Envelope getMeshEnvelope() {
        return envelope;
    }

    /**
     * Retrieve the triangles from the digital elevation model (DEM).
     * @return List of Triangle objects from the topographic mesh
     */
    public List<Triangle> getTriangles() {
    return topographyService.getTriangles();
    }

    /**
     * Retrieve the vertices from the digital elevation model (DEM).
     * @return List of Coordinate objects representing mesh vertices
     */
    public List<Coordinate> getVertices() {
    return topographyService.getVertices();
    }

    /**
     * Retrieve all ground absorption areas added to this builder.
     * @return List of GroundAbsorption objects with their geometries and coefficients
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
        LOGGER.debug("finishFeeding: starting topography processing");
        processTopography();

        LOGGER.debug("finishFeeding: computing building/wall elevations");
        computeElevations();

        LOGGER.debug("finishFeeding: indexing building/bridge facets into wall service");
        exportFacetsToProcessedWalls();

        LOGGER.debug("finishFeeding: finalizing processed walls and ground effects indexes");
        finalizeIndexes();

        LOGGER.debug("finishFeeding: initializing frequency dependent data");
        initializeFrequencyDependentData();

        LOGGER.info("finishFeeding completed");
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
        // Ensure frequency config is properly initialized
        if (frequencyConfig.getExactFrequencyArray().isEmpty()) {
            LOGGER.warn("FrequencyConfig has empty arrays, initializing with default ONE_THIRD_OCTAVE configuration");
            frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        }
        
        // Log frequency configuration for debugging
        LOGGER.info("ProfileBuilder.setFrequencyArray: called with size={} values={}", 
                   frequencyConfig.getFrequencyArray().size(), 
                   frequencyConfig.getFrequencyArray());
        
        wallService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
        buildingService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
        bridgeService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
        processedWallService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
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
    public double getZAtPoint(Coordinate coord) {
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
    public CutProfile getProfile(Coordinate c0, Coordinate c1, SourcePointInfo sourcePointInfo) {
        return getProfile(c0, c1, 0.0, false, sourcePointInfo);
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
    public CutProfile getProfile(Coordinate sourceCoordinate, Coordinate receiverCoordinate, double defaultGroundAttenuation, boolean stopAtObstacleOverSourceReceiver, SourcePointInfo sourcePointInfo) {
        return ProfileRetriever.getProfile(sourceCoordinate, receiverCoordinate, defaultGroundAttenuation, stopAtObstacleOverSourceReceiver, maxLineLength, buildingService, wallService, bridgeService, topographyService, groundService, processedWallService, GeometryFactoryProvider.SHARED, sourcePointInfo);
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
    
    /**
     * Get the coordinates of a triangle by its index.
     * @param triIndex Index of the triangle in the topographic mesh
     * @return Array of coordinates representing the triangle vertices
     */
    public Coordinate[] getTriangle(int triIndex) {
        return topographyService.getTriangle(triIndex);
    }


    /**
     * Get coordinates of triangle vertices with the last point being the first point (closed polygon).
     * @param triIndex Index of triangle in the topographic mesh
     * @return Array of coordinates forming a closed triangle
     */
    public Coordinate[] getClosedTriangle(int triIndex) {
        return topographyService.getClosedTriangle(triIndex);
    }

    /**
     * Get all walls that intersect with the specified envelope.
     * @param env Envelope to query for wall intersections
     * @return List of walls intersecting the envelope
     */
    public List<Wall> getWallsIn(org.locationtech.jts.geom.Envelope env) {
        return processedWallService.getWallsIn(env);
    }

    /**
     * Get all bridges that intersect with the specified envelope.
     * @param env Envelope to query for bridge intersections
     * @return List of bridges intersecting the envelope
     */
    public List<Bridge> getBridgesIn(org.locationtech.jts.geom.Envelope env) {
        return bridgeService.getBridgesIn(env);
    }
    
    /**
     * Find the triangle ID that contains the given point coordinate.
     * @param pt Point coordinate to test for triangle containment
     * @return Triangle ID if found, or -1 if no triangle contains the point
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
     * Check if a digital elevation model has been added to this builder.
     * @return true if DEM triangles are available, false otherwise
     */
    public boolean hasDem() {
        STRtree tree = topographyService.getTopoRtree();
        return tree != null && !tree.isEmpty();
    }

    /**
     * Convert the digital elevation model to a MultiPolygon geometry.
     * @return MultiPolygon representation of the DEM mesh
     */
    public MultiPolygon demAsMultiPolygon() {
        return this.topographyService.demAsMultiPolygon();
    }

    /**
     * Get the ground elevation at a specific coordinate from the DEM.
     * @param coordinate X,Y coordinate to query for elevation
     * @return Altitude in meters above sea level
     */
    public double getZGround(Coordinate coordinate) {
        return this.topographyService.getZGround(coordinate);
    }

    /**
     * Get ground elevation with triangle optimization hint for repeated queries in same area.
     * @param coordinate X,Y coordinate to query for elevation
     * @param triangleHint Triangle index hint (if >= 0 will be checked first, updated with found triangle)
     * @return Altitude in meters above sea level
     */
    public double getZGround(Coordinate coordinate, AtomicInteger triangleHint) {
        return this.topographyService.getZGround(coordinate, triangleHint);
    }


    // Buffer distance around obstacles when computing diffraction (ISO / TR 17534-4 standard)
    public static final double wideAngleTranslationEpsilon = 0.015;

    /**
     * Get precomputed wide-angle diffraction points for a building.
     * @param build Building identifier (1-based indexing)
     * @return List of coordinates representing wide-angle diffraction points
     */
    public ArrayList<Coordinate> getPrecomputedWideAnglePoints(int build) {
    return buildingService.getPrecomputedWideAnglePoints(build);
    }

    /**
     * Calculate wide-angle diffraction points on a polygon boundary within specified angle range.
     * @param linearRing Coordinate ring defining the polygon boundary
     * @param minAngle Minimum angle threshold for diffraction point detection
     * @param maxAngle Maximum angle threshold for diffraction point detection
     * @return List of coordinates representing wide-angle diffraction points
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
