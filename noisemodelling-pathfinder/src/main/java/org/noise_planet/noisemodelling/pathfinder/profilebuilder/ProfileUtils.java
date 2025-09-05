package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.math.Vector2D;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty.SourceType;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.RTreeUtils;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgeService.PropagationType;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Small helper utilities extracted from {@code ProfileBuilder} to centralize
 * line-splitting and obstacle query logic so {@code ProfileBuilder} can
 * delegate responsibilities and remain focused on orchestration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Split long source->receiver segments into shorter pieces for stable
 *       spatial-index queries and for accurate interpolation of Z values.</li>
 *   <li>Query obstacle/service spatial indexes (processed walls) and dispatch
 *       intersection handling to the appropriate service (building, wall,
 *       bridge, ground) to produce profile cut points.</li>
 *   <li>Collect cut points discovered by obstacle handlers and append them
 *       to the provided {@link CutProfile}.</li>
 *   <li>Adjust Z coordinates of bridge cut points through interpolation based on
 *       surrounding valid points for accurate acoustic propagation calculations.</li>
 * </ul>
 *
 * <p>Design notes: methods in this utility are stateless and operate only on
 * their inputs. They require that service arguments (for example
 * {@link ProcessedWallService#getProcessedRtree()} and related indexes) have been
 * prepared by the caller (indexed) before invocation.</p>
 */
public final class ProfileUtils {
    private ProfileUtils() {}

    /**
     * Splits a segment between two coordinates into smaller LineSegments whose length
     * does not exceed the specified maximum length.
     *
     * <p>The method preserves and interpolates Z coordinates linearly when
     * creating intermediate segment endpoints. If the original segment length
     * is less than {@code maxLineLength}, a single segment equal to the full
     * line is returned.</p>
     *
     * @param c0 segment start coordinate (may contain z)
     * @param c1 segment end coordinate (may contain z)
     * @param maxLineLength maximum allowed length per returned segment
     * @return list of LineSegment parts covering the full segment in order
     */
    public static List<LineSegment> splitSegment(Coordinate c0, Coordinate c1, double maxLineLength) {
        List<LineSegment> segments = new ArrayList<>();
    LineSegment fullLine = new LineSegment(c0, c1);
    double l = c0.distance(c1);
        if(l < maxLineLength) {
            segments.add(fullLine);
        }
        else {
            double frac = maxLineLength / l;
            for(int i = 0; i < l / maxLineLength; i++) {
                Coordinate p0 = fullLine.pointAlong(i * frac);
                p0.z = c0.z + (c1.z - c0.z) * i * frac;
                Coordinate p1 = fullLine.pointAlong(Math.min((i + 1) * frac, 1.0));
                p1.z = c0.z + (c1.z - c0.z) * Math.min((i + 1) * frac, 1.0);
                segments.add(new LineSegment(p0, p1));
            }
        }
        return segments;
    }

    /**
     * Add obstacle cut points to the profile. This is a static port of the logic
     * previously implemented inside ProfileBuilder.addObstacleCutPts.
     *
     * <p>Behavior summary:
     * <ul>
     *   <li>Split the full profiling line using {@link #splitSegment} to avoid
     *       oversized envelopes when querying spatial indexes.</li>
     *   <li>For each sub-segment, query {@code ProcessedWallService.getProcessedRtree()}
     *       and for each hit dispatch to the specific handler depending on the
     *       processed wall type (BUILDING/WALL/BRIDGE/GROUND_EFFECT).</li>
     *   <li>Handlers (for example {@link BuildingService#createBuildingCutPointAndCheckObstruction}
     *       or {@link ProcessedWallService#createWallCutPointAndCheckObstruction}) may decide to stop processing
     *       early by returning {@code false}; in that case this method returns
     *       immediately and the pending cut-points discovered so far are still
     *       appended to the profile in the finally block.</li>
     * </ul>
     *
     * <p>Inputs:
     * <ul>
     *   <li>{@code fullLine} the full source->receiver segment.</li>
     *   <li>{@code profile} the CutProfile to which discovered cut points will be appended.</li>
     *   <li>Service parameters provide access to processed walls, buildings,
     *       bridges and ground effects. These services are queried but not
     *       mutated by this method.</li>
     * </ul>
     *
     * <p>Side-effect: discovered cut points are inserted into {@code profile}
     * at the end of the method via {@link CutProfile#insertCutPoint}.</p>
     *
     * @param fullLine the full source->receiver segment to process
     * @param profile the CutProfile to which discovered cut points will be appended
     * @param stopAtObstacleOverSourceReceiver whether to stop processing when obstacles are found over source/receiver
     * @param maxLineLength maximum length for line segment splitting
     * @param buildingService service for handling building obstacles
     * @param wallService service for handling wall obstacles
     * @param bridgeService service for handling bridge obstacles
     * @param groundService service for handling ground effect obstacles
     * @param processedWallService service containing processed wall data
     * @param factory geometry factory for creating new geometries
     */
    public static void addObstacleCutPts(LineSegment fullLine,
                                         CutProfile profile,
                                         boolean stopAtObstacleOverSourceReceiver,
                                         double maxLineLength,
                                         BuildingService buildingService,
                                         WallService wallService,
                                         BridgeService bridgeService,
                                         GroundService groundService,
                                         ProcessedWallService processedWallService,
                                         GeometryFactory factory) {
        java.util.Set<Integer> completedWalls = new HashSet<>();
        List<LineSegment> segments = splitSegment(fullLine.p0, fullLine.p1, maxLineLength);
        List<CutPoint> newCutPoints = new LinkedList<>();
        boolean sortCutPoints = true;
        
        PropagationType propagationType = bridgeService.checkPropagationType(profile);

        if (propagationType == PropagationType.ACTUAL_SOURCE_TO_LOWER_RECEIVER || propagationType == PropagationType.IMAGINARY_SOURCE_TO_UPPER_RECEIVER) {
            CutPointBridgeWall bridgeCutPoint = bridgeService.calculateFirstBridgeCutpoint(profile, propagationType);
            newCutPoints.add(bridgeCutPoint);
            sortCutPoints = false;
            segments = splitSegment(bridgeCutPoint.getCoordinate(), fullLine.p1, maxLineLength);
            completedWalls.add(bridgeCutPoint.getProcessedWallIndex());
        } 


        for (int j = 0; j < segments.size() && !((profile.hasBuildingIntersection() || profile.hasBridgeIntersection()) && stopAtObstacleOverSourceReceiver); j++) {
            LineSegment seg = segments.get(j);

            for (Object wallIndex : RTreeUtils.query(processedWallService.getProcessedRtree(), new Envelope(seg.p0, seg.p1))) {
                if (!(wallIndex instanceof Integer) || completedWalls.contains((Integer) wallIndex)) {
                    continue;
                }
                completedWalls.add((Integer) wallIndex);
                int i = (Integer) wallIndex;
                Wall processedWall = processedWallService.getProcessedWalls().get(i);
                Coordinate intersection = fullLine.intersection(processedWall.getLineSegment());
                if (intersection == null) {continue; }
                intersection = new Coordinate(intersection);
                if (!Double.isNaN(processedWall.getP0().z) && !Double.isNaN(processedWall.getP1().z)) {
                    if (Double.compare(processedWall.getP0().z, processedWall.getP1().z) == 0) {
                        intersection.z = processedWall.getP0().z;
                    } else {
                        intersection.z = Vertex.interpolateZ(intersection, processedWall.getP0(), processedWall.getP1());
                    }
                }
                boolean continueCalculation = createCutPointAndCheckObstruction(buildingService, wallService, bridgeService, groundService, processedWallService, processedWall.type, i, intersection, processedWall, seg, newCutPoints, stopAtObstacleOverSourceReceiver, profile, factory);
                if (!continueCalculation) {
                    break;
                }
            }
        }
        profile.insertCutPoint(sortCutPoints, newCutPoints.toArray(CutPoint[]::new));
    }

    /**
     * Creates cut points and checks for obstruction based on the wall type.
     * Delegates to specific service handlers for different types of obstacles.
     *
     * @param buildingService service for handling building obstacles
     * @param wallService service for handling wall obstacles
     * @param bridgeService service for handling bridge obstacles
     * @param groundService service for handling ground effect obstacles
     * @param processedWallService service containing processed wall data
     * @param wallType type of wall intersection (BUILDING, WALL, BRIDGE, GROUND_EFFECT)
     * @param wallIndex index of the wall in the processed walls list
     * @param intersection coordinate of intersection point
     * @param processedWall the processed wall object
     * @param fullLine the full propagation line segment
     * @param newCutPoints list to add new cut points to
     * @param stopAtObstacleOverSourceReceiver whether to stop processing at obstacles over source/receiver
     * @param profile the cut profile being processed
     * @param factory geometry factory for creating new geometries
     * @return true if calculation should continue, false to stop processing
     */
    private static boolean createCutPointAndCheckObstruction(BuildingService buildingService,
                                         WallService wallService,
                                         BridgeService bridgeService,
                                         GroundService groundService,
                                         ProcessedWallService processedWallService,ProfileBuilder.IntersectionType wallType, int wallIndex, Coordinate intersection, Wall processedWall, LineSegment fullLine, List<CutPoint> newCutPoints, boolean stopAtObstacleOverSourceReceiver, CutProfile profile, GeometryFactory factory) {
        boolean hasObstacleIntersection;
        switch (wallType) {
            case BUILDING:
                hasObstacleIntersection = buildingService.createBuildingCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints);
                profile.hasBuildingIntersection(profile.hasBuildingIntersection() || hasObstacleIntersection);
                return profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver ? false : true;

            case WALL:
                hasObstacleIntersection =  wallService.createWallCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints);
                profile.hasBuildingIntersection(profile.hasBuildingIntersection() || hasObstacleIntersection);
                return profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver ? false : true;
            case BRIDGE:
                hasObstacleIntersection = bridgeService.createBridgeCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints, profile);
                profile.hasBridgeIntersection(profile.hasBridgeIntersection() || hasObstacleIntersection);
                return profile.hasBridgeIntersection() && stopAtObstacleOverSourceReceiver ? false : true;
            case GROUND_EFFECT:
                return groundService.createGroundCutPointAndCheckObstruction(wallIndex, intersection, processedWall, fullLine, newCutPoints, stopAtObstacleOverSourceReceiver, profile, factory);
            default:
                throw new IllegalArgumentException("Unknown wall type: " + wallType);
        }
    }

    private static final double MILLIMETER = 1.0;
    private static final double LEFT_SIDE = Math.PI / 2;

    /**
     * Determines if an intersection point represents an entry into a polygon facet
     * based on the propagation line direction and polygon facet orientation.
     *
     * <p>The method creates a perpendicular vector to the facet line and checks
     * if moving slightly in the exterior direction brings the point closer to
     * the propagation source, indicating an entry intersection.</p>
     *
     * @param intersection the intersection point to check
     * @param propagationLine the line segment representing sound propagation
     * @param polygonFacetLine the line segment representing the polygon facet
     * @return true if the intersection represents an entry, false otherwise
     */
    public static boolean isIntersectionEntry(Coordinate intersection, LineSegment propagationLine, LineSegment polygonFacetLine) {
        Vector2D facetVector = Vector2D.create(polygonFacetLine.p0, polygonFacetLine.p1);
        Vector2D exteriorVector = facetVector.rotate(LEFT_SIDE).normalize().multiply(MILLIMETER);
        Coordinate exteriorPoint = exteriorVector.add(Vector2D.create(intersection)).toCoordinate();
        return  exteriorPoint.distance(propagationLine.p0) < intersection.distance(propagationLine.p0);
    }
    
    /**
     * Determines if an intersection point represents an entry into a wall
     * based on the propagation line direction and wall orientation.
     *
     * @param intersection the intersection point to check
     * @param propagationLine the line segment representing sound propagation
     * @param wall the wall object containing the line segment
     * @return true if the intersection represents an entry, false otherwise
     */
    public static boolean isIntersectionEntry(Coordinate intersection, LineSegment propagationLine, Wall wall) {
        LineSegment polygonFaceLine = wall.getLineSegment();
        return isIntersectionEntry(intersection, propagationLine, polygonFaceLine);
    }

    /**
     * Adjusts Z coordinates of downward-facing bridge cut points through linear interpolation
     * based on surrounding valid cut points in the profile.
     *
     * <p>This method processes cut points sequentially from source to receiver, identifying
     * bridge cut points with DOWNWARD wall direction and interpolating their Z coordinates
     * using the nearest valid points (non-downward bridge points) before and after them.
     * If no downward bridge cut points exist, the original profile is returned unchanged.</p>
     *
     * <p>The interpolation process:
     * <ul>
     *   <li>Identifies all downward-facing bridge cut points in the profile</li>
     *   <li>For each downward bridge point, finds the previous and next valid points</li>
     *   <li>Performs linear interpolation using {@link Vertex#interpolateZ} method</li>
     *   <li>Updates the bridge cut point coordinate with the interpolated Z value</li>
     * </ul>
     *
     * <p>Valid points for interpolation include:
     * <ul>
     *   <li>Source and receiver points (as fallbacks)</li>
     *   <li>Non-bridge cut points (walls, buildings, ground effects, etc.)</li>
     *   <li>Bridge cut points with non-downward wall directions</li>
     * </ul>
     *
     * @param profile the cut profile containing cut points to be processed
     * @return the modified profile with adjusted bridge cut point Z coordinates,
     *         or the original profile if no downward bridge cut points exist
     */
    public static CutProfile adjustBridgeCutpointZ(CutProfile profile){
        List<CutPoint> cutPoints = profile.getCutPoints();
        if(cutPoints.isEmpty()) {
            return profile;
        }
        
        boolean hasDownwardBridgeWall = cutPoints.stream()
                .filter(cp -> cp instanceof CutPointBridgeWall)
                .map(cp -> (CutPointBridgeWall) cp)
                .anyMatch(bridgeWall -> bridgeWall.getWallDirection() == CutPointBridgeWall.WallDirection.DOWNWARD);
                
        if (!hasDownwardBridgeWall) {
            return profile;
        }

        for (int i = 0; i < cutPoints.size(); i++) {
            CutPoint currentPoint = cutPoints.get(i);
            
            if (currentPoint instanceof CutPointBridgeWall && 
                ((CutPointBridgeWall) currentPoint).getWallDirection() == CutPointBridgeWall.WallDirection.DOWNWARD) {
                
                CutPoint prevValidPoint = findPreviousValidPoint(cutPoints, i, profile);
                
                CutPoint nextValidPoint = findNextValidPoint(cutPoints, i, profile);
                
                if (prevValidPoint != null && nextValidPoint != null) {
                    Coordinate currentCoord = currentPoint.getCoordinate();
                    Coordinate prevCoord = prevValidPoint.getCoordinate();
                    Coordinate nextCoord = nextValidPoint.getCoordinate();
                    
                    if (!Double.isNaN(prevCoord.z) && !Double.isNaN(nextCoord.z)) {
                        double interpolatedZ = Vertex.interpolateZ(currentCoord, prevCoord, nextCoord);
                        currentPoint.setCoordinate(new Coordinate(currentCoord.x, currentCoord.y, interpolatedZ));
                    }
                }
            }
        }
        
        return profile;
    }
    
    /**
     * Finds the previous valid cut point suitable for Z coordinate interpolation.
     *
     * <p>Searches backwards through the cut points list from the given index to find
     * a point that is suitable for interpolation (non-downward bridge points or
     * non-bridge points). If no valid point is found in the cut points list,
     * returns the source point as a fallback.</p>
     *
     * @param cutPoints list of all cut points in the profile
     * @param currentIndex current index position in the cut points list
     * @param profile the cut profile containing source information
     * @return the previous valid cut point, or the source point if none found
     */
    private static CutPoint findPreviousValidPoint(List<CutPoint> cutPoints, int currentIndex, CutProfile profile) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            CutPoint point = cutPoints.get(i);
            if (isValidPointForInterpolation(point)) {
                return point;
            }
        }
        return profile.getSource();
    }
    
    /**
     * Finds the next valid cut point suitable for Z coordinate interpolation.
     *
     * <p>Searches forwards through the cut points list from the given index to find
     * a point that is suitable for interpolation (non-downward bridge points or
     * non-bridge points). If no valid point is found in the cut points list,
     * returns the receiver point as a fallback.</p>
     *
     * @param cutPoints list of all cut points in the profile
     * @param currentIndex current index position in the cut points list
     * @param profile the cut profile containing receiver information
     * @return the next valid cut point, or the receiver point if none found
     */
    private static CutPoint findNextValidPoint(List<CutPoint> cutPoints, int currentIndex, CutProfile profile) {
        for (int i = currentIndex + 1; i < cutPoints.size(); i++) {
            CutPoint point = cutPoints.get(i);
            if (isValidPointForInterpolation(point)) {
                return point;
            }
        }
        return profile.getReceiver();
    }
    
    /**
     * Determines if a cut point is valid for use in Z coordinate interpolation.
     *
     * <p>A point is considered valid if:
     * <ul>
     *   <li>It is not a bridge cut point (all non-bridge cut points are valid)</li>
     *   <li>It is a bridge cut point but not with DOWNWARD wall direction</li>
     * </ul>
     *
     * <p>Downward-facing bridge cut points are excluded because they are the ones
     * that need interpolation and should not be used as reference points for
     * interpolating other points.</p>
     *
     * @param point the cut point to check for validity
     * @return true if the point is valid for interpolation, false otherwise
     */
    private static boolean isValidPointForInterpolation(CutPoint point) {
        if (point instanceof CutPointBridgeWall) {
            return ((CutPointBridgeWall) point).getWallDirection() != CutPointBridgeWall.WallDirection.DOWNWARD;
        }
        return true; 
    }
}
