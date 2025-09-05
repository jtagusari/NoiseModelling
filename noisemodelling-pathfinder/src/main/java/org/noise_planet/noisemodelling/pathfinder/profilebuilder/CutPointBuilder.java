package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.algorithm.Intersection;
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
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder.IntersectionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Small helper utilities extracted from {@code ProfileBuilder} to centralize
 * line-splitting and obstacle query logic so {@code ProfileBuilder} can
 * delegate responsibilities and remain focused on orchestration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Split long source-to-receiver segments into shorter pieces for stable
 *       spatial-index queries and for accurate interpolation of Z values.</li>
 *   <li>Query obstacle/service spatial indexes (processed walls) and dispatch
 *       intersection handling to the appropriate service (building, wall,
 *       bridge, ground) to produce profile cut points.</li>
 *   <li>Collect cut points discovered by obstacle handlers and append them
 *       to the provided {@link CutProfile}.</li>
 * </ul>
 *
 * <p>Design notes: methods in this utility are stateless and operate only on
 * their inputs. They require that service arguments (for example
 * {@link ProcessedWallService#getProcessedRtree()} and related indexes) have been
 * prepared by the caller (indexed) before invocation.</p>
 */
public final class CutPointBuilder {
//     private final BuildingService buildingService;
//     private final WallService wallService;
//     private final BridgeService bridgeService;
//     private final GroundService groundService;
//     private final ProcessedWallService processedWallService;
//     private final GeometryFactory geometryFactory;
//     private double maxLineLength;
//     private boolean stopAtObstacleOverSourceReceiver;
//     private CutProfile profile;
//     private List<CutPoint> newCutPoints;

//     public CutPointBuilder(BuildingService buildingService, WallService wallService, BridgeService bridgeService, GroundService groundService, ProcessedWallService processedWallService, ProfileBuilder.IntersectionType wallType, boolean stopAtObstacleOverSourceReceiver, GeometryFactory factory) {
//         this.buildingService = buildingService;
//         this.wallService = wallService;
//         this.bridgeService = bridgeService;
//         this.groundService = groundService;
//         this.processedWallService = processedWallService;
//         this.geometryFactory = factory;
//         this.maxLineLength = maxLineLength;
//     }

//     public void setInitialProfile(CutProfile profile) {
//         this.profile = profile;
//         this.newCutPoints = new LinkedList<>();
//     }

//     public List<LineSegment> splitSegment(Coordinate c0, Coordinate c1) {
//         List<LineSegment> segments = new ArrayList<>();
//         LineSegment fullLine = new LineSegment(c0, c1);
//         double l = c0.distance(c1);
//         if(l < maxLineLength) {
//             segments.add(fullLine);
//         }
//         else {
//             double frac = maxLineLength / l;
//             for(int i = 0; i < l / maxLineLength; i++) {
//                 Coordinate p0 = fullLine.pointAlong(i * frac);
//                 p0.z = c0.z + (c1.z - c0.z) * i * frac;
//                 Coordinate p1 = fullLine.pointAlong(Math.min((i + 1) * frac, 1.0));
//                 p1.z = c0.z + (c1.z - c0.z) * Math.min((i + 1) * frac, 1.0);
//                 segments.add(new LineSegment(p0, p1));
//             }
//         }
//         return segments;
//     }

    /**
     * Split the segment between {@code c0} and {@code c1} into smaller
     * {@link LineSegment}s whose length does not exceed {@code maxLineLength}.
     *
     * <p>The method preserves and interpolates Z coordinates linearly when
     * creating intermediate segment endpoints. If the original segment length
     * is less than {@code maxLineLength}, a single segment equal to the full
     * line is returned.</p>
     *
     * @param c0 segment start coordinate (may contain z)
     * @param c1 segment end coordinate (may contain z)
     * @return list of LineSegment parts covering the full segment in order
     */

    /**
     * Add obstacle cut points to the profile. This is a static port of the logic
     * previously implemented inside ProfileBuilder.addObstacleCutPts.
     */
    // public void addObstacleCutPts(LineSegment fullLine) {
    //     java.util.Set<Integer> completedWalls = new HashSet<>();
    //     List<LineSegment> segments = splitSegment(fullLine.p0, fullLine.p1);
    //     boolean sortCutPoints = true;
        
    //     SourceType sourceType = profile.getSource().getSourceBridgeProperty().getSourceType();
    //     PropagationType propagationType = bridgeService.checkPropagationType(profile);

    //     if (propagationType == PropagationType.ACTUAL_SOURCE_TO_LOWER_RECEIVER || propagationType == PropagationType.IMAGINARY_SOURCE_TO_UPPER_RECEIVER) {
    //         CutPointBridgeWall bridgeCutPoint = bridgeService.calculateFirstBridgeCutpoint(profile, propagationType);
    //         newCutPoints.add(bridgeCutPoint);
    //         sortCutPoints = false;
    //         segments = splitSegment(bridgeCutPoint.getCoordinate(), fullLine.p1);
    //         completedWalls.add(bridgeCutPoint.getProcessedWallIndex());
    //     } 


    //     for (LineSegment seg : segments) {

    //         if (!((profile.hasBuildingIntersection() || profile.hasBridgeIntersection()) && stopAtObstacleOverSourceReceiver)) {
    //             break;
    //         }

    //         List<Integer> wallIndexs = RTreeUtils.query(
    //             processedWallService.getProcessedRtree(), 
    //             new Envelope(seg.p0, seg.p1)
    //         );

    //         for (Integer wallIndex : wallIndexs) {
    //             if (completedWalls.contains(wallIndex)) { continue; }
    //             completedWalls.add(wallIndex);
    //             Wall processedWall = processedWallService.getProcessedWalls().get(wallIndex);

    //             Coordinate intersection = fullLine.intersection(processedWall.getLineSegment());
    //             if (intersection == null) { continue; }
                
    //             if (!Double.isNaN(processedWall.p0.z) && !Double.isNaN(processedWall.p1.z)) {
    //                 if (Double.compare(processedWall.p0.z, processedWall.p1.z) == 0) {
    //                     intersection.z = processedWall.p0.z;
    //                 } else {
    //                     intersection.z = Vertex.interpolateZ(intersection, processedWall.p0, processedWall.p1);
    //                 }
    //             }
    //             boolean continueCalculation = registerNewCutPoints(seg, processedWall, intersection);
    //             if (!continueCalculation) {
    //                 return;
    //             }
    //         }
    //     }
    //     profile.insertCutPoint(sortCutPoints, newCutPoints.toArray(CutPoint[]::new));
    // }


    // private boolean registerNewCutPoints(LineSegment segment, Wall processedWall, Coordinate intersection) {
    //     IntersectionType wallType = processedWall.type;
    //     int wallIndex = processedWall.getOriginId();
    //     switch (wallType) {
    //         case BUILDING:
    //             hasObstacleIntersection = buildingService.createBuildingCutPointAndCheckObstruction(wallIndex, intersection, processedWall, segment, newCutPoints);
    //             profile.hasBuildingIntersection(profile.hasBuildingIntersection() || hasObstacleIntersection);
    //             return profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver ? false : true;

    //         case WALL:
    //             hasObstacleIntersection =  wallService.createWallCutPointAndCheckObstruction(wallIndex, intersection, processedWall, segment, newCutPoints);
    //             profile.hasBuildingIntersection(profile.hasBuildingIntersection() || hasObstacleIntersection);
    //             return profile.hasBuildingIntersection() && stopAtObstacleOverSourceReceiver ? false : true;
    //         case BRIDGE:
    //             return bridgeService.createBridgeCutPointAndCheckObstruction(wallIndex, intersection, processedWall, segment, newCutPoints, profile);
    //         case GROUND_EFFECT:
    //             return groundService.createGroundCutPointAndCheckObstruction(wallIndex, intersection, processedWall, segment, newCutPoints, stopAtObstacleOverSourceReceiver, profile, geometryFactory);
    //         default:
    //             throw new IllegalArgumentException("Unknown wall type: " + wallType);
    //     }
    // }

    // private static final double MILLIMETER = 1.0;
    // private static final double LEFT_SIDE = Math.PI / 2;

    // public static boolean isIntersectionEntry(Coordinate intersection, LineSegment propagationLine, LineSegment polygonFacetLine) {
    //     Vector2D facetVector = Vector2D.create(polygonFacetLine.p0, polygonFacetLine.p1);
    //     Vector2D exteriorVector = facetVector.rotate(LEFT_SIDE).normalize().multiply(MILLIMETER);
    //     Coordinate exteriorPoint = exteriorVector.add(Vector2D.create(intersection)).toCoordinate();
    //     return  exteriorPoint.distance(propagationLine.p0) < intersection.distance(propagationLine.p0);
    // }
    
    // public static boolean isIntersectionEntry(Coordinate intersection, LineSegment propagationLine, Wall wall) {
    //     LineSegment polygonFaceLine = wall.getLineSegment();
    //     return isIntersectionEntry(intersection, propagationLine, polygonFaceLine);
    // }
}
