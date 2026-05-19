/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.noise_planet.noisemodelling.pathfinder.delaunay.Triangle;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgeService.PropagationType;
// ...existing imports...
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;

/**
 * Builder constructing profiles from buildings, topography and ground effects.
 *
 * <p>Use this class to ingest geometry/topography, run preprocessing with {@link #finishFeeding()},
 * then build source-receiver profiles with {@link #buildProfile(Coordinate, Coordinate, double, boolean, SourcePointInfo)}.</p>
 *
 * <p>Detailed pipeline, responsibilities and diagrams are documented in:</p>
 * <ul>
 *   <li>Docs-dev/pathfinder_algorithms.md</li>
 *   <li>Docs-dev/scene.md</li>
 * </ul>
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
     * @see ProfileBuilder#buildProfile(Coordinate, Coordinate)
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
    private GeometryFactory geometryFactory = GeometryFactoryProvider.SHARED;
    

    /** Global envelope of the builder. */
    private Envelope envelope;

    private double defaultGroundAttenuation;
    private boolean stopAtObstacleOverSourceReceiver;

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
        this.buildingService = new BuildingService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.topographyService = new TopographyService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.wallService = new WallService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.bridgeService = new BridgeService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.groundService = new GroundService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.processedWallService = new ProcessedWallService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.frequencyConfig = new FrequencyConfig();
    }

    /**
     * Constructor with a frequency configuration.
     *
     * @param frequencyConfig Frequency configuration to use
     */
    
    public ProfileBuilder(FrequencyConfig frequencyConfig) {
        this.buildingService = new BuildingService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.topographyService = new TopographyService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.wallService = new WallService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.bridgeService = new BridgeService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.groundService = new GroundService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
        this.processedWallService = new ProcessedWallService(DEFAULT_TREE_NODE_CAPACITY, this.geometryFactory);
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
                    envelope = this.geometryFactory.createPolygon(polyCoords).getEnvelopeInternal();
                } else {
                    envelope.expandToInclude(this.geometryFactory.createPolygon(polyCoords).getEnvelopeInternal());
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
    return addWall(this.geometryFactory.createLineString(coords), height, new ArrayList<>(), id);
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
    return addWall(this.geometryFactory.createLineString(coords), 0.0, new ArrayList<>(), id);
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
    return addWall(this.geometryFactory.createLineString(coords), height, alphas, id);
    }

    /**
     * Add a wall with absorption coefficients and default height using coordinate array.
     * @param coords Wall coordinates
     * @param alphas Sound absorption coefficients per frequency band
     * @param id Database primary key
     * @return this builder for method chaining
     */
    public ProfileBuilder addWall(Coordinate[] coords, List<Double> alphas, int id) {
    return addWall(this.geometryFactory.createLineString(coords), 0.0, alphas, id);
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
            LineString lineSegment = this.geometryFactory.createLineString(new Coordinate[]{new Coordinate(x0, y0, z0), new Coordinate(x1, y1, z1)});
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
            Geometry geom = this.geometryFactory.createPolygon(new Coordinate[]{
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
     * Finalize ingestion and build runtime indexes/caches required by profile queries.
     *
     * <p>After this call, the builder should be treated as read-only for computation.
     * For the detailed preprocessing pipeline, see Docs-dev/scene.md.</p>
     *
     * @return this builder for fluent chaining
     */
    public ProfileBuilder finishFeeding() {
        isFeedingFinished = true;

        // High-level phased workflow. Each phase is extracted to a small helper
        // to make the intent and ordering explicit and easy to document/test.
        LOGGER.debug("finishFeeding: starting topography processing");
        topographyService.buildDelaunayTriangulation();

        LOGGER.debug("finishFeeding: computing building/wall elevations");
        buildingService.computeElevations(this);
        bridgeService.computeElevations(this);
        wallService.computeElevations(this);

        LOGGER.debug("finishFeeding: indexing building/bridge facets into wall service");
        buildingService.exportFacetsToProcessedWalls(processedWallService);
        bridgeService.exportFacetsToProcessedWalls(processedWallService);
        wallService.exportFacetsToProcessedWalls(processedWallService);
        groundService.exportFacetsToProcessedWalls(processedWallService);

        LOGGER.debug("finishFeeding: finalizing processed walls and ground effects indexes");
        processedWallService.buildProcessedWallRtree();
        groundService.buildGroundEffectsRtree();

        LOGGER.debug("finishFeeding: initializing frequency dependent data");
        // Ensure frequency config is properly initialized
        if (frequencyConfig.getExactFrequencyArray().isEmpty()) {
            LOGGER.warn("FrequencyConfig has empty arrays, initializing with default ONE_THIRD_OCTAVE configuration");
            frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        }
        
        // Log frequency configuration for debugging
        LOGGER.debug("ProfileBuilder.setFrequencyArray: called with size={} values={}", 
                   frequencyConfig.getFrequencyArray().size(), 
                   frequencyConfig.getFrequencyArray());
        
        wallService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
        buildingService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
        bridgeService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());
        processedWallService.initializeFrequencyDependentData(frequencyConfig.getExactFrequencyArray());

        LOGGER.debug("finishFeeding completed");
        return this;
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
     * Build a {@link CutProfile} between source and receiver.
     *
     * <p>The method resolves topography, walls/buildings/bridges and ground effects,
     * and optionally stops early when a blocking obstacle is found. For the full
     * algorithm description and diagrams, see Docs-dev/pathfinder_algorithms.md.</p>
     *
     * @param source source coordinate
     * @param receiver receiver coordinate
     * @param defaultGroundAttenuation fallback ground attenuation coefficient
     * @param stopAtObstacleOverSourceReceiver whether to stop when a blocking obstacle is detected
     * @param sourcePointInfo source metadata
     * @return computed cut profile
     */
    public CutProfile buildProfile(Coordinate source, Coordinate receiver, double defaultGroundAttenuation, boolean stopAtObstacleOverSourceReceiver, SourcePointInfo sourcePointInfo ) {

        this.defaultGroundAttenuation = defaultGroundAttenuation;
        this.stopAtObstacleOverSourceReceiver = stopAtObstacleOverSourceReceiver;
        
        LOGGER.debug("ProfileBuilder.buildProfile - Starting profile calculation");
        LOGGER.debug("  Source: x={}, y={}, z={}", source.x, source.y, source.z);
        LOGGER.debug("  Receiver: x={}, y={}, z={}", receiver.x, receiver.y, receiver.z);
        LOGGER.debug("  DefaultGroundAttenuation: {}", defaultGroundAttenuation);
        LOGGER.debug("  StopAtObstacleOverSourceReceiver: {}", stopAtObstacleOverSourceReceiver);
        LOGGER.debug("  MaxLineLength: {}", maxLineLength);
        
        long totalStartTime = System.currentTimeMillis();

        // Initialize the basic profile with source and receiver
        LOGGER.debug("  Initializing profile...");
        SourcePointInfo sourcePointInfo2 = new SourcePointInfo(sourcePointInfo);
        sourcePointInfo2.setCoordinate(source);
        ReceiverPointInfo receiverPointInfo = new ReceiverPointInfo(receiver);
        CutProfile profile = initializeProfile(sourcePointInfo2, receiverPointInfo);

        // Add topography cut points
        LOGGER.debug("  Adding topography cut points...");
        long topoStartTime = System.currentTimeMillis();
        if (topographyService.getTopoRtree() != null) {
            LOGGER.debug("  TopographyService has RTree - calling addTopoCutPts...");
            topographyService.addTopoCutPts(sourcePointInfo2.getCoordinate(), receiverPointInfo.getCoordinate(), profile, stopAtObstacleOverSourceReceiver);            
            
            if (stopAtObstacleOverSourceReceiver && profile.hasTopographyIntersection()) {
                LOGGER.debug("  Stopping early - topography intersection detected");
            }
        } else {
            LOGGER.debug("  No TopographyService RTree - using fallback with zero elevations");
            // Fallback: set ground elevation to zero
            CutPointSource cutPointSource = profile.getSource();
            cutPointSource.setZGround(0.0);
            profile.setSource(cutPointSource);

            CutPointReceiver cutPointReceiver = profile.getReceiver();
            cutPointReceiver.setZGround(0.0);
            profile.setReceiver(cutPointReceiver);
        }
        
        LOGGER.debug("  addTopoCutPts completed in {} ms", System.currentTimeMillis() - topoStartTime);


        // Add obstacle cut points (buildings, walls, bridges)
        LOGGER.debug("  Adding obstacle cut points...");
        long obstacleStartTime = System.currentTimeMillis();
        if (processedWallService.getProcessedRtree() == null) {
            LOGGER.debug("  No ProcessedWallService RTree - obstacle cut points cannot be added");
        }
        
        LOGGER.debug("  ProcessedWallService has RTree - calling addObstacleCutPts...");

        LineSegment fullLine = new LineSegment(sourcePointInfo2.getCoordinate(), receiverPointInfo.getCoordinate());
        Set<Integer> visitedWallIndices = new HashSet<>();
        List<LineSegment> segments = ProfileUtils.splitToSegments(fullLine.p0, fullLine.p1, maxLineLength);
        List<CutPoint> newCutPoints = new LinkedList<>();
        boolean sortCutPoints = true;

        // Check if the profile has bridge-type cut point
        LOGGER.debug("  Checking for bridge-type cut points...");
        PropagationType propagationType = bridgeService.checkPropagationType(profile);

        if (propagationType == PropagationType.ACTUAL_SOURCE_TO_LOWER_RECEIVER || propagationType == PropagationType.IMAGINARY_SOURCE_TO_UPPER_RECEIVER) {
            LOGGER.debug("  Bridge-type cut point detected - calculating first bridge cut point...");
            CutPointBridgeWall bridgeCutPoint = bridgeService.calculateFirstBridgeCutpoint(profile, propagationType);
            newCutPoints.add(bridgeCutPoint);
            sortCutPoints = false;
            segments = ProfileUtils.splitToSegments(bridgeCutPoint.getCoordinate(), fullLine.p1, maxLineLength);
            visitedWallIndices.add(bridgeCutPoint.getProcessedWallIndex());
        } 

        for (int j = 0; j < segments.size(); j++) {
            LineSegment seg = segments.get(j);

            // Query the processed walls R-tree for potential intersections with the current segment
            for (Object wallIndex : RTreeUtils.query(processedWallService.getProcessedRtree(), new Envelope(seg.p0, seg.p1))) {
                // wallIndex should be an Integer index into the processed walls list
                if (!(wallIndex instanceof Integer)) continue;

                // Check if this wall index has already been visited to avoid duplicate processing
                if (visitedWallIndices.contains((Integer) wallIndex)) {
                    continue; // Skip already visited walls
                } else {
                    visitedWallIndices.add((Integer) wallIndex);
                }

                RayWallIntersection rayWallIntersection = new RayWallIntersection(fullLine, processedWallService, (Integer) wallIndex);
                if (!rayWallIntersection.hasValidIntersection()) continue;

                rayWallIntersection.setZonWall();
                LOGGER.debug("  Found intersection with wall index {}: type={}, point=({}, {}, {})", wallIndex, rayWallIntersection.getType(), rayWallIntersection.getIntersection().x, rayWallIntersection.getIntersection().y, rayWallIntersection.getIntersection().z);

                boolean continueCalculation = true;
                switch (rayWallIntersection.getType()) {
                    case BUILDING:{
                        CutPointWall newCutPoint = buildingService.createBuildingCutPoint(rayWallIntersection);
                        newCutPoints.add(newCutPoint);
                        profile.hasBuildingIntersection(profile.hasBuildingIntersection() || newCutPoint.isObstructingAcousticRay());
                        if (profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver) {
                            continueCalculation = false;
                        }
                        break;
                    }
                    case WALL:{
                        CutPointWall newCutPoint = wallService.createWallCutPoint(rayWallIntersection);
                        newCutPoints.add(newCutPoint);
                        profile.hasBuildingIntersection(profile.hasBuildingIntersection() || newCutPoint.isObstructingAcousticRay());
                        if (profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver) {
                            continueCalculation = false;
                        }
                        break;
                    }
                    case BRIDGE:{
                        CutPointBridgeWall newCutPoint = bridgeService.createBridgeCutPoint(rayWallIntersection, profile);
                        newCutPoints.add(newCutPoint);
                        profile.hasBridgeIntersection(true);
                        if (profile.hasBridgeIntersection() && stopAtObstacleOverSourceReceiver) {
                            continueCalculation = false;
                        }
                        break;
                    }
                    case GROUND_EFFECT:{
                        CutPointGroundEffect newCutPoint = groundService.createGroundEffectCutPoint(rayWallIntersection);
                        if (newCutPoint != null) {
                            newCutPoints.add(newCutPoint);
                        }
                        // Ground effects are not considered obstacles, so we do not stop calculation here
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unknown wall type: " + rayWallIntersection.getType());
                        
                }

                if (!continueCalculation) {
                    LOGGER.debug("  Stopping early - obstacle intersection detected");
                    break;
                }
            }
            if (stopAtObstacleOverSourceReceiver && (profile.hasBuildingIntersection() || profile.hasBridgeIntersection())) {
                LOGGER.debug("  Stopping early - obstacle intersection detected");
                break;
            }
        }
        profile.insertCutPoint(sortCutPoints, newCutPoints.toArray(CutPoint[]::new));
        bridgeService.setEffectiveBridgeCutPoint(profile);
        
        // Stop early if obstacle intersection is found and requested
        if (stopAtObstacleOverSourceReceiver && (profile.hasBuildingIntersection() || profile.hasBridgeIntersection())) {
            LOGGER.debug("  Stopping early - obstacle intersection detected");
            return profile; // Stop processing
        }
        
        LOGGER.debug("  addObstacleCutPts completed in {} ms", System.currentTimeMillis() - obstacleStartTime);

        // Post-process the profile
        LOGGER.debug("  Post-processing profile...");
        propagateGroundCoefficients(profile);
        interpolateGroundElevations(profile);

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        LOGGER.debug("ProfileBuilder.buildProfile - Completed successfully in {} ms", totalDuration);
        return profile;
    }

    private CutProfile initializeProfile(SourcePointInfo sourcePointInfo, ReceiverPointInfo receiverPointInfo) {
                
        CutPointSource sourcePoint = new CutPointSource(sourcePointInfo);
        CutPointReceiver receiverPoint = new CutPointReceiver(receiverPointInfo.getCoordinate());

        // Set ground coefficient for source point
        int groundAbsorptionIndex = groundService.getIntersectingGroundAbsorption(geometryFactory.createPoint(sourcePointInfo.getCoordinate()));
        if (groundAbsorptionIndex >= 0) {
            sourcePoint.setGroundCoefficient(groundService.getGroundAbsorptions().get(groundAbsorptionIndex).getCoefficient());
        } else {
            sourcePoint.setGroundCoefficient(this.defaultGroundAttenuation);
        }

        long bridgePkOn = sourcePoint.getBridgeRelationship().getBridgePkOn();
        if (bridgePkOn >= 0) {
            Bridge bridge = bridgeService.getBridgeByPk(bridgePkOn);
            sourcePoint.setBridgeHeight(bridge.getDeckHeightAtPoint(sourcePointInfo.getCoordinate()));
        }

        return new CutProfile(sourcePoint, receiverPoint);
    }

    /**
     * Propagate ground coefficients throughout the profile.
     * Unknown coefficients are filled with the current coefficient,
     * and the current coefficient is updated at ground effect transition points.
     *
     * @param profile the profile to process
     */
    private void propagateGroundCoefficients(CutProfile profile) {
        CutPointSource sourcePoint = profile.getSource();
        double currentCoefficient = sourcePoint.getGroundCoefficient();
        
        for (CutPoint cutPoint : profile.getCutPoints()) {
            if (Double.isNaN(cutPoint.getGroundCoefficient())) {
                cutPoint.setGroundCoefficient(currentCoefficient);
            } else if (cutPoint instanceof CutPointGroundEffect) {
                currentCoefficient = cutPoint.getGroundCoefficient();
            }
        }
    }

    /**
     * Interpolate ground elevations for points with unknown Z ground values.
     * Uses linear interpolation between known elevation points.
     *
     * @param profile the profile to process
     */
    private void interpolateGroundElevations(CutProfile profile) {
        CutPointSource sourcePoint = profile.getSource();
        CutPoint previousZGround = sourcePoint;
        int nextPointIndex = 0;
        
        for (int pointIndex = 1; pointIndex < profile.getCutPoints().size() - 1; pointIndex++) {
            CutPoint cutPoint = profile.getCutPoints().get(pointIndex);
            
            if (Double.isNaN(cutPoint.zGround)) {
                // Find next reference point with known Z ground
                if (nextPointIndex <= pointIndex) {
                    nextPointIndex = findNextKnownElevationPoint(profile, pointIndex);
                }
                
                if (nextPointIndex < profile.getCutPoints().size()) {
                    CutPoint nextPoint = profile.getCutPoints().get(nextPointIndex);
                    interpolateElevationForPoint(cutPoint, previousZGround, nextPoint);
                }
            } else {
                // Update reference point for future interpolations
                previousZGround = cutPoint;
            }
        }
    }

    
    /**
     * Find the next cut point with a known ground elevation.
     *
     * @param profile the profile to search
     * @param startIndex starting index for the search
     * @return index of the next point with known elevation, or profile size if none found
     */
    private int findNextKnownElevationPoint(CutProfile profile, int startIndex) {
        for (int i = startIndex + 1; i < profile.getCutPoints().size(); i++) {
            CutPoint point = profile.getCutPoints().get(i);
            if (!Double.isNaN(point.zGround)) {
                return i;
            }
        }
        return profile.getCutPoints().size();
    }

    /**
     * Interpolate elevation for a single cut point between two reference points.
     *
     * @param cutPoint the point to interpolate elevation for
     * @param previousZGround previous point with known elevation
     * @param nextPoint next point with known elevation
     */
    private void interpolateElevationForPoint(CutPoint cutPoint, CutPoint previousZGround, CutPoint nextPoint) {
        cutPoint.zGround = Vertex.interpolateZ(cutPoint.coordinate,
                new Coordinate(previousZGround.coordinate.x, previousZGround.coordinate.y, previousZGround.getzGround()),
                new Coordinate(nextPoint.coordinate.x, nextPoint.coordinate.y, nextPoint.getzGround()));
        
        if (Double.isNaN(cutPoint.coordinate.z) || cutPoint instanceof CutPointGroundEffect) {
            // Set Z coordinate for walls and ground effect points
            // Bottom of walls are set to NaN z because it can be computed here at low cost
            // (without fetch dem r-tree)
            // ground effect change points take the Z of ground in coordinate too
            cutPoint.coordinate.setZ(cutPoint.zGround);
        }
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

    public double getMaxLineLength() {
        return maxLineLength;
    }

    public BuildingService getBuildingService() {
        return buildingService;
    }
    public WallService getWallService() {
        return wallService;
    }
    public BridgeService getBridgeService() {
        return bridgeService;
    }
    public TopographyService getTopographyService() {
        return topographyService;
    }
    public GroundService getGroundService() {
        return groundService;
    }
    public ProcessedWallService getProcessedWallService() {
        return processedWallService;
    }

    public GeometryFactory getGeometryFactory() {
        return geometryFactory;
    }

    /**
     * Compute a hash code representing the current state of this ProfileBuilder.
     * The hash combines hashes from all service components and configuration.
     * 
     * @return Hash code representing the builder state
     */
    @Override
    public int hashCode() {
        return Objects.hash(buildingService, wallService, bridgeService, 
                          topographyService, groundService, processedWallService,
                          frequencyConfig, isFeedingFinished, maxLineLength, envelope);
    }

}
