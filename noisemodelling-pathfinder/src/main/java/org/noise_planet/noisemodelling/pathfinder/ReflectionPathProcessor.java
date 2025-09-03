package org.noise_planet.noisemodelling.pathfinder;

import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiver;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiversCompute;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import java.util.ArrayList;
import java.util.List;

/**
 * Instance-based processor for reflection path assembly. This class now
 * encapsulates the reflection-related responsibilities that were part of
 * PathFinder's per-receiver workflow. Responsibilities include:
 * - Traversing mirror receiver graphs and assembling reflection ray paths
 * - Building cut profiles for reflection legs using the {@link ProfileBuilder}
 * - Inserting reflection point attributes on cut points
 * - Returning path search strategies to the caller (e.g. skip receiver)
 *
 * It is written for testability: {@link ProfileBuilder} and {@link LineIntersector}
 * are injected to allow unit testing without full geometry stack.
 */
public class ReflectionPathProcessor {
    private final ProfileBuilder profileBuilder;
    private final LineIntersector lineIntersector;

    public ReflectionPathProcessor(ProfileBuilder profileBuilder, LineIntersector lineIntersector) {
        this.profileBuilder = profileBuilder;
        this.lineIntersector = lineIntersector;
    }

    /**
     * Processes reflection paths for the given source and receiver pair.
     * 
     * @param rcv Receiver point information
     * @param src Source point information  
     * @param receiverMirrorIndex Mirror receivers computation index
     * @param cutPlaneVisitor Visitor for processing cut planes
     * @param initialStrategy Initial path search strategy
     * @param scene Scene containing geometric and acoustic data
     * @return Updated path search strategy
     */
    public CutPlaneVisitor.PathSearchStrategy process(ReceiverPointInfo rcv,
                                                      SourcePointInfo src,
                                                      MirrorReceiversCompute receiverMirrorIndex,
                                                      CutPlaneVisitor cutPlaneVisitor,
                                                      CutPlaneVisitor.PathSearchStrategy initialStrategy,
                                                      Scene scene) {
        CutPlaneVisitor.PathSearchStrategy strategy = initialStrategy;
        List<MirrorReceiver> mirrorResults = receiverMirrorIndex.findCloseMirrorReceivers(src.getCoordinate());

        for (MirrorReceiver receiverReflection : mirrorResults) {
            List<MirrorReceiver> rayPath = buildReflectionRayPath(receiverReflection, src.getCoordinate());
            
            if (rayPath.isEmpty()) {
                continue;
            }
            
            CutProfile mainProfile = buildReflectionProfile(src, rcv, rayPath, scene);
            if (mainProfile == null) {
                continue;
            }
            
            configureProfileAttributes(mainProfile, src, rcv, scene);
            
            strategy = cutPlaneVisitor.onNewCutPlane(mainProfile);
            if (isTerminalStrategy(strategy)) {
                return strategy;
            }
        }
        return strategy;
    }

    /**
     * Builds a reflection ray path by tracing intersections from source to mirror receivers.
     * 
     * @param receiverReflection Starting mirror receiver
     * @param sourceCoordinates Source coordinates
     * @return List of mirror receivers forming the ray path
     */
    private List<MirrorReceiver> buildReflectionRayPath(MirrorReceiver receiverReflection, Coordinate sourceCoordinates) {
        List<MirrorReceiver> rayPath = new ArrayList<>();
        MirrorReceiver receiverReflectionCursor = receiverReflection;
        Coordinate destinationPt = new Coordinate(sourceCoordinates);
        Wall seg = receiverReflection.getWall();

        lineIntersector.computeIntersection(seg.p0, seg.p1,
                receiverReflection.getReceiverPos(),
                destinationPt);

        while (lineIntersector.hasIntersection()) {
            Coordinate reflectionPt = computeReflectionPoint(receiverReflectionCursor, destinationPt);
            if (reflectionPt.equals(destinationPt)) {
                break;
            }

            MirrorReceiver reflResult = new MirrorReceiver(receiverReflectionCursor);
            reflResult.setReflectionPosition(reflectionPt);
            rayPath.add(reflResult);

            if (receiverReflectionCursor.getParentMirror() == null) {
                break;
            } else {
                destinationPt.setCoordinate(reflectionPt);
                receiverReflectionCursor = receiverReflectionCursor.getParentMirror();
                seg = receiverReflectionCursor.getWall();
                lineIntersector.computeIntersection(seg.p0, seg.p1,
                        receiverReflectionCursor.getReceiverPos(),
                        destinationPt);
            }
        }
        return rayPath;
    }

    /**
     * Computes the reflection point with epsilon adjustment to avoid coincident points.
     * 
     * @param receiverReflectionCursor Current mirror receiver
     * @param destinationPt Destination point
     * @return Adjusted reflection point
     */
    private Coordinate computeReflectionPoint(MirrorReceiver receiverReflectionCursor, Coordinate destinationPt) {
        Coordinate reflectionPt = new Coordinate(lineIntersector.getIntersection(0));
        
        // Apply epsilon adjustment to avoid coincident points
        Coordinate vec_epsilon = new Coordinate(reflectionPt.x - destinationPt.x,
                reflectionPt.y - destinationPt.y);
        double length = vec_epsilon.distance(new Coordinate(0., 0., 0.));
        vec_epsilon.x /= length;
        vec_epsilon.y /= length;
        vec_epsilon.x *= ProfileBuilder.MILLIMETER;
        vec_epsilon.y *= ProfileBuilder.MILLIMETER;
        reflectionPt.x -= vec_epsilon.x;
        reflectionPt.y -= vec_epsilon.y;
        
        // Interpolate Z coordinate
        reflectionPt.setOrdinate(Coordinate.Z, Vertex.interpolateZ(lineIntersector.getIntersection(0),
                receiverReflectionCursor.getReceiverPos(), destinationPt));
        
        return reflectionPt;
    }

    /**
     * Builds the complete reflection profile by assembling cut profiles for each reflection leg.
     * 
     * @param src Source point information
     * @param rcv Receiver point information
     * @param rayPath List of mirror receivers forming the ray path
     * @param scene Scene containing geometric data
     * @return Complete reflection profile or null if invalid
     */
    private CutProfile buildReflectionProfile(SourcePointInfo src, ReceiverPointInfo rcv, 
                                              List<MirrorReceiver> rayPath, Scene scene) {
        // Build initial profile from source to first reflection point
        CutProfile cutProfile = profileBuilder.getProfile(src.getCoordinate(), rayPath.get(0).getReflectionPosition(),
                scene.getDefaultGroundAttenuation(), !scene.computeVerticalDiffraction);
        if (!isValidProfile(cutProfile, scene)) {
            return null;
        }

        List<CutPoint> mainProfileCutPoints = new ArrayList<>(cutProfile.getCutPoints().subList(0, cutProfile.getCutPoints().size() - 1));

        // Process intermediate reflection legs
        if (!buildIntermediateReflectionLegs(rayPath, mainProfileCutPoints, scene)) {
            return null;
        }

        // Build final leg from last reflection point to receiver
        cutProfile = profileBuilder.getProfile(rayPath.get(rayPath.size() - 1).getReflectionPosition(),
                rcv.getCoordinate(), scene.getDefaultGroundAttenuation(), !scene.computeVerticalDiffraction);
        if (!isValidProfile(cutProfile, scene)) {
            return null;
        }

        ReflectionPathBuilder.insertReflectionPointAttributes(cutProfile.getCutPoints().get(0), 
                mainProfileCutPoints, rayPath.get(rayPath.size() - 1));
        mainProfileCutPoints.addAll(cutProfile.getCutPoints().subList(1, cutProfile.getCutPoints().size()));

        return assembleMainProfile(mainProfileCutPoints);
    }

    /**
     * Builds cut profiles for intermediate reflection legs.
     * 
     * @param rayPath List of mirror receivers forming the ray path
     * @param mainProfileCutPoints List to accumulate cut points
     * @param scene Scene containing geometric data
     * @return true if all intermediate legs are valid, false otherwise
     */
    private boolean buildIntermediateReflectionLegs(List<MirrorReceiver> rayPath, 
                                                    List<CutPoint> mainProfileCutPoints, Scene scene) {
        for (int idPt = 0; idPt < rayPath.size() - 1; idPt++) {
            MirrorReceiver firstPoint = rayPath.get(idPt);
            MirrorReceiver secondPoint = rayPath.get(idPt + 1);
            
            CutProfile cutProfile = profileBuilder.getProfile(firstPoint.getReflectionPosition(),
                    secondPoint.getReflectionPosition(), scene.getDefaultGroundAttenuation(), !scene.computeVerticalDiffraction);
            
            if (!isValidProfile(cutProfile, scene)) {
                return false;
            }

            ReflectionPathBuilder.insertReflectionPointAttributes(cutProfile.getCutPoints().get(0), 
                    mainProfileCutPoints, firstPoint);
            mainProfileCutPoints.addAll(cutProfile.getCutPoints().subList(1, cutProfile.getCutPoints().size() - 1));
        }
        return true;
    }

    /**
     * Checks if a cut profile is valid for reflection processing.
     * 
     * @param cutProfile Profile to validate
     * @param scene Scene configuration
     * @return true if profile is valid, false otherwise
     */
    private boolean isValidProfile(CutProfile cutProfile, Scene scene) {
        return cutProfile.isFreeField() || scene.computeVerticalDiffraction;
    }

    /**
     * Assembles the main reflection profile from accumulated cut points.
     * 
     * @param mainProfileCutPoints List of cut points forming the profile
     * @return Assembled main profile
     */
    private CutProfile assembleMainProfile(List<CutPoint> mainProfileCutPoints) {
        CutProfile mainProfile = new CutProfile((CutPointSource) mainProfileCutPoints.get(0),
                (CutPointReceiver) mainProfileCutPoints.get(mainProfileCutPoints.size() - 1));

        mainProfile.insertCutPoint(false, mainProfileCutPoints.subList(1, mainProfileCutPoints.size() - 1).toArray(CutPoint[]::new));
        return mainProfile;
    }

    /**
     * Configures source and receiver attributes for the reflection profile.
     * 
     * @param mainProfile Profile to configure
     * @param src Source point information
     * @param rcv Receiver point information
     * @param scene Scene containing acoustic data
     */
    private void configureProfileAttributes(CutProfile mainProfile, SourcePointInfo src, 
                                           ReceiverPointInfo rcv, Scene scene) {
        CutPointSource cutPointSource = mainProfile.getSource();
        CutPointReceiver cutPointReceiver = mainProfile.getReceiver();
        
        cutPointSource.setSourceId(src.getSourceIndex());
        cutPointReceiver.setReceiverId(rcv.getReceiverIndex());
        cutPointReceiver.setReceiverPk(rcv.getReceiverPk());

        if (src.getSourceIndex() >= 0 && src.getSourceIndex() < scene.getSourceCount()) {
            cutPointSource.setSourcePk(scene.getSourcePkById(src.getSourceIndex()));
        }

        cutPointSource.setOrientation(src.getOrientation());
        cutPointSource.setLineLength(src.getLineLength());
        
        mainProfile.setSource(cutPointSource);
        mainProfile.setReceiver(cutPointReceiver);
    }

    /**
     * Checks if the given strategy is terminal (should stop processing).
     * 
     * @param strategy Path search strategy to check
     * @return true if strategy is terminal, false otherwise
     */
    private boolean isTerminalStrategy(CutPlaneVisitor.PathSearchStrategy strategy) {
        return strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE) ||
               strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER);
    }
}
