package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility encapsulating the profile-building algorithm used to compute a
 * {@link CutProfile} between a source and a receiver.
 *
 * <p>Rationale: the procedural logic that assembles cut-points, interpolates
 * ground/topography elevations and resolves obstacle interactions is complex
 * and was extracted from {@code ProfileBuilder} to keep that class small and
 * focused. This final helper is stateless and provides a single static
 * entry-point {@link #getProfile(...)}.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Initialize the cut profile and source/receiver ground coefficients.</li>
 *   <li>Fetch and append topography cut points using {@link TopographyService}.</li>
 *   <li>Detect and append building, wall, bridge and ground-effect intersections
 *       using the corresponding services and utility helpers.</li>
 *   <li>Propagate ground coefficients across cut points and interpolate
 *       intermediate ground Z values when needed.</li>
 * </ul>
 *</p>
 */
public class ProfileRetriever {
    private ProfileRetriever() {}

    /**
     * Build a {@link CutProfile} between {@code sourceCoordinate} and
     * {@code receiverCoordinate}.
     *
     * <p>Inputs:
     * <ul>
     *   <li>{@code defaultGroundAttenuation} is the fallback ground coefficient
     *       applied when no ground-effect feature is found.</li>
     *   <li>{@code stopAtObstacleOverSourceReceiver} controls whether the
     *       algorithm must stop early when encountering an obstacle that is
     *       above the source->receiver ray.</li>
     *   <li>Service parameters ({@code buildingService}, {@code wallService},
     *       {@code bridgeService}, {@code topographyService}, {@code groundService})
     *       provide geometry and spatial indexes required to detect intersections
     *       and to populate cut points.</li>
     *   <li>{@code maxLineLength} influences how long segments are split when
     *       querying spatial indexes.</li>
     * </ul>
     *
     * Outputs and side-effects:
     * <ul>
     *   <li>Returns a fully populated {@link CutProfile} with source, receiver
     *       and a sequenced list of {@link CutPoint}s representing topography,
     *       building/wall/bridge intersections and ground-effect transitions.</li>
     *   <li>The method does not mutate the provided service instances (it only
     *       queries them), but it relies on them being pre-populated and indexed
     *       (for example, {@link TopographyService#processDelaunay()} and
     *       {@link WallService#buildProcessedWallRtree()} should have been called
     *       by callers preparing the scene).</li>
     * </ul>
     *
     * Error modes and guarantees:
     * <ul>
     *   <li>If a service index is null (for example no topo tree), the method
     *       falls back to sensible defaults (zero ground elevation).</li>
     *   <li>The algorithm attempts to fill unknown ground coefficients and
     *       interpolate missing ground Z values; it does not throw on missing
     *       geometry but the resulting profile may be less detailed.</li>
     * </ul>
     *
     * @return built {@link CutProfile}
     */
    public static CutProfile getProfile(Coordinate sourceCoordinate, Coordinate receiverCoordinate, 
            double defaultGroundAttenuation, boolean stopAtObstacleOverSourceReceiver, double maxLineLength, 
            BuildingService buildingService, WallService wallService, BridgeService bridgeService, 
            TopographyService topographyService, GroundService groundService, 
            ProcessedWallService processedWallService, GeometryFactory factory, SourcePointInfo sourcePointInfo) {

        Logger logger = LoggerFactory.getLogger(ProfileRetriever.class);
        logger.debug("ProfileRetriever.getProfile - Starting profile calculation");
        logger.debug("  Source: x={}, y={}, z={}", sourceCoordinate.x, sourceCoordinate.y, sourceCoordinate.z);
        logger.debug("  Receiver: x={}, y={}, z={}", receiverCoordinate.x, receiverCoordinate.y, receiverCoordinate.z);
        logger.debug("  DefaultGroundAttenuation: {}", defaultGroundAttenuation);
        logger.debug("  StopAtObstacleOverSourceReceiver: {}", stopAtObstacleOverSourceReceiver);
        logger.debug("  MaxLineLength: {}", maxLineLength);
        
        long totalStartTime = System.currentTimeMillis();

        // Initialize the basic profile with source and receiver
        logger.debug("  Initializing profile...");
        CutProfile profile = initializeProfile(sourceCoordinate, receiverCoordinate, defaultGroundAttenuation, groundService, factory, sourcePointInfo);

        // Add topography cut points
        logger.debug("  Adding topography cut points...");
        long topoStartTime = System.currentTimeMillis();
        if (!addTopographyCutPoints(profile, sourceCoordinate, receiverCoordinate, topographyService, stopAtObstacleOverSourceReceiver)) {
            long topoDuration = System.currentTimeMillis() - topoStartTime;
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            logger.debug("ProfileRetriever.getProfile - Early return after topography (topo: {} ms, total: {} ms)", topoDuration, totalDuration);
            return profile; // Early return if stop requested and topography intersection found
        }
        long topoDuration = System.currentTimeMillis() - topoStartTime;
        logger.debug("  Topography cut points completed in {} ms", topoDuration);

        // Add obstacle cut points (buildings, walls, bridges)
        if (!addObstacleCutPoints(profile, sourceCoordinate, receiverCoordinate, stopAtObstacleOverSourceReceiver, 
                maxLineLength, buildingService, wallService, bridgeService, groundService, processedWallService, factory)) {
            return profile; // Early return if stop requested and obstacle intersection found
        }

        // Post-process the profile
        logger.debug("  Post-processing profile...");
        propagateGroundCoefficients(profile);
        interpolateGroundElevations(profile);

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        logger.debug("ProfileRetriever.getProfile - Completed successfully in {} ms", totalDuration);
        return profile;
    }

    /**
     * Initialize a basic CutProfile with source and receiver points.
     * Sets up ground coefficients for the source point.
     *
     * @param sourceCoordinate source location
     * @param receiverCoordinate receiver location
     * @param defaultGroundAttenuation default ground coefficient
     * @param groundService ground service for coefficient lookup
     * @param factory geometry factory
     * @return initialized CutProfile
     */
    private static CutProfile initializeProfile(Coordinate sourceCoordinate, Coordinate receiverCoordinate,
            double defaultGroundAttenuation, GroundService groundService, GeometryFactory factory, SourcePointInfo sourcePointInfo) {
                
        CutPointSource sourcePoint = new CutPointSource(sourceCoordinate).migrateFromSourcePointInfo(sourcePointInfo);
        CutPointReceiver receiverPoint = new CutPointReceiver(receiverCoordinate);

        // Set ground coefficient for source point
        int groundAbsorptionIndex = groundService.getIntersectingGroundAbsorption(factory.createPoint(sourceCoordinate));
        if (groundAbsorptionIndex >= 0) {
            sourcePoint.setGroundCoefficient(groundService.getGroundAbsorptions().get(groundAbsorptionIndex).getCoefficient());
        } else {
            sourcePoint.setGroundCoefficient(defaultGroundAttenuation);
        }

        return new CutProfile(sourcePoint, receiverPoint);
    }

    /**
     * Add topography cut points to the profile.
     *
     * @param profile the profile to modify
     * @param sourceCoordinate source location
     * @param receiverCoordinate receiver location
     * @param topographyService topography service
     * @param stopAtObstacleOverSourceReceiver whether to stop early on intersection
     * @return true to continue processing, false if early stop requested
     */
    private static boolean addTopographyCutPoints(CutProfile profile, Coordinate sourceCoordinate, 
            Coordinate receiverCoordinate, TopographyService topographyService, boolean stopAtObstacleOverSourceReceiver) {
        
        Logger logger = LoggerFactory.getLogger(ProfileRetriever.class);
        logger.debug("ProfileRetriever.addTopographyCutPoints - Starting topography profile calculation");
        logger.debug("  Source: x={}, y={}, z={}", sourceCoordinate.x, sourceCoordinate.y, sourceCoordinate.z);
        logger.debug("  Receiver: x={}, y={}, z={}", receiverCoordinate.x, receiverCoordinate.y, receiverCoordinate.z);
        logger.debug("  StopAtObstacleOverSourceReceiver: {}", stopAtObstacleOverSourceReceiver);
        
        long startTime = System.currentTimeMillis();
        
        if (topographyService.getTopoRtree() != null) {
            logger.debug("  TopographyService has RTree - calling addTopoCutPts...");
            topographyService.addTopoCutPts(sourceCoordinate, receiverCoordinate, profile, stopAtObstacleOverSourceReceiver);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("  addTopoCutPts completed in {} ms", duration);
            
            if (stopAtObstacleOverSourceReceiver && profile.hasTopographyIntersection()) {
                logger.debug("  Stopping early - topography intersection detected");
                return false; // Stop processing
            }
        } else {
            logger.debug("  No TopographyService RTree - using fallback with zero elevations");
            // Fallback: set ground elevation to zero
            setDefaultGroundElevations(profile);
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        logger.debug("ProfileRetriever.addTopographyCutPoints - Total duration: {} ms", totalDuration);
        return true; // Continue processing
    }

    /**
     * Set default ground elevations (zero) when no topography service is available.
     *
     * @param profile the profile to modify
     */
    private static void setDefaultGroundElevations(CutProfile profile) {
        CutPointSource cutPointSource = profile.getSource();
        cutPointSource.setZGround(0.0);
        profile.setSource(cutPointSource);

        CutPointReceiver cutPointReceiver = profile.getReceiver();
        cutPointReceiver.setZGround(0.0);
        profile.setReceiver(cutPointReceiver);
    }

    /**
     * Add obstacle cut points (buildings, walls, bridges) to the profile.
     *
     * @param profile the profile to modify
     * @param sourceCoordinate source location
     * @param receiverCoordinate receiver location
     * @param stopAtObstacleOverSourceReceiver whether to stop early on intersection
     * @param maxLineLength maximum line length for segment splitting
     * @param buildingService building service
     * @param wallService wall service
     * @param bridgeService bridge service
     * @param groundService ground service
     * @param processedWallService processed wall service
     * @param factory geometry factory
     * @return true to continue processing, false if early stop requested
     */
    private static boolean addObstacleCutPoints(CutProfile profile, Coordinate sourceCoordinate, 
            Coordinate receiverCoordinate, boolean stopAtObstacleOverSourceReceiver, double maxLineLength,
            BuildingService buildingService, WallService wallService, BridgeService bridgeService,
            GroundService groundService, ProcessedWallService processedWallService, GeometryFactory factory) {
        
        if (processedWallService.getProcessedRtree() == null) {
            throw new IllegalStateException("ProcessedWallService RTree is not initialized");
        }
        LineSegment fullLine = new LineSegment(sourceCoordinate, receiverCoordinate);
        ProfileUtils.addObstacleCutPts(fullLine, profile, stopAtObstacleOverSourceReceiver, maxLineLength, 
                buildingService, wallService, bridgeService, groundService, processedWallService, factory);
        
        // Stop early if obstacle intersection is found and requested
        if (stopAtObstacleOverSourceReceiver && (profile.hasBuildingIntersection() || profile.hasBridgeIntersection())) {
            return false; // Stop processing
        }
        return true; // Continue processing
    }

    /**
     * Propagate ground coefficients throughout the profile.
     * Unknown coefficients are filled with the current coefficient,
     * and the current coefficient is updated at ground effect transition points.
     *
     * @param profile the profile to process
     */
    private static void propagateGroundCoefficients(CutProfile profile) {
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
    private static void interpolateGroundElevations(CutProfile profile) {
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
    private static int findNextKnownElevationPoint(CutProfile profile, int startIndex) {
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
    private static void interpolateElevationForPoint(CutPoint cutPoint, CutPoint previousZGround, CutPoint nextPoint) {
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
}
