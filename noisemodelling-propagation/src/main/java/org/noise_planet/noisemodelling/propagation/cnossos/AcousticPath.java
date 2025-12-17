package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.math.Vector3D;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointVEdgeDiffraction;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.noise_planet.noisemodelling.propagation.cnossos.PointPath.POINT_TYPE.*;

/**
 * Processor for a single acoustic path segment.
 *
 * <p>This class encapsulates the logic required to build the acoustic
 * representation for one horizontal segment between two pivot points derived
 * from the cut profile. It converts cut profile entries into {@code PointPath}
 * instances (source, receiver, reflections, horizontal/vertical diffractions)
 * and creates {@code SegmentPath} objects with computed ground factors and
 * geometric information used by CNOSSOS propagation computations.</p>
 *
 * <p>Typical usage: construct an instance with an {@code
 * AcousticPathConfiguration} and call {@link #updateWithSegmentIndex(int)} for
 * each segment index to populate the internal {@code Path} with points and
 * segments.</p>
 */
public class AcousticPath {

    private final List<PivotPoint> horizontalEdgePivotPoints;
    private final List<Coordinate> pivotPointCoordinates;
    private final AcousticPathConfiguration configuration;
    private final Path path;
    // private final Path acousticPath;
    private int segmentIndex;
    
    // Segment boundary indices
    
    private int startCutPointIndex, endCutPointIndex;
    private int sourceCutPointIndex, receiverCutPointIndex;
    private int sourceExpandedCutPointIndex, receiverExpandedCutPointIndex;
    private int startExpandedCutPointIndex, endExpandedCutPointIndex;
    // Cut points for this segment
    private CutPoint startCutPoint, endCutPoint;


    /**
     * Create a processor bound to the supplied configuration.
     *
     * @param configuration configuration providing cut profiles, elevation
     *                      data and propagation settings used to build the
     *                      acoustic path
     */
    public AcousticPath(List<PivotPoint> horizontalEdgePivotPoints, AcousticPathConfiguration configuration) {
        this.horizontalEdgePivotPoints = horizontalEdgePivotPoints;
        this.pivotPointCoordinates = new ArrayList<>();
        for (PivotPoint pp : horizontalEdgePivotPoints) {
            pivotPointCoordinates.add((Coordinate) pp);
        }

        this.configuration = configuration;
        this.path = new Path(configuration.getCutProfile());
        this.path.setPointList(new ArrayList<>());
        this.path.setSegmentList(new ArrayList<>());

        for (int segmentIndex = 1; segmentIndex < horizontalEdgePivotPoints.size(); segmentIndex++) {
            this.updateWithSegmentIndex(segmentIndex);
        }

        this.setSRSegment();

    }

    private void setSRSegment(){

        Coordinate[] elevationProfileSR = Arrays.copyOfRange(
            configuration.getElevationProfile2D(), 
            sourceExpandedCutPointIndex, 
            receiverExpandedCutPointIndex + 1
        );
        double[] meanPlaneSR = JTSUtility.getMeanPlaneCoefficients(elevationProfileSR);
        
        List<Coordinate> cutPointCoordinatesSR = configuration.getCutPointCoordinates2D().subList(
            sourceCutPointIndex, 
            receiverCutPointIndex + 1
        );

        double weightedGroundAbsorptionSR = configuration.getCutProfile().calculateWeightedGroundAbsorption(
            sourceCutPointIndex, 
            receiverCutPointIndex,
            Scene.DEFAULT_G_BUILDING
        );

        double groundAbsorptionAtSource = configuration.getCutProfile().getCutPoints().get(sourceCutPointIndex).getGroundCoefficient();

        SegmentPath sourceToReceiverPath = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            cutPointCoordinatesSR, 
            meanPlaneSR, 
            weightedGroundAbsorptionSR, 
            groundAbsorptionAtSource
        );

        sourceToReceiverPath.setElevationProfile2D(elevationProfileSR);

        Coordinate sourceCoordinate = configuration.getCutProfile().getCutPoints().get(sourceCutPointIndex).getCoordinate();
        Coordinate receiverCoordinate = configuration.getCutProfile().getCutPoints().get(receiverCutPointIndex).getCoordinate();

        sourceToReceiverPath.setDirectRayDistance(
            CGAlgorithms3D.distance(
                receiverCoordinate,
                sourceCoordinate
            )
        );
        
        this.path.setSRSegment(sourceToReceiverPath);
    }
    
    public AcousticPath(AcousticPath other){
        this.horizontalEdgePivotPoints = new ArrayList<>(other.horizontalEdgePivotPoints);
        this.pivotPointCoordinates = new ArrayList<>(other.pivotPointCoordinates);
        this.configuration = new AcousticPathConfiguration(other.configuration);
        this.path = new Path(other.path);
    }


    /**
     * Returns the generated acoustic path for the currently processed
     * segment(s).
     *
     * @return the {@code Path} containing points and segments produced by this
     *         processor
     */
    public Path getPath(){
        return this.path;
    }

    
    /**
     * Build the acoustic representation for this segment and append it to the provided Path.
     *
     * This method is the high-level orchestrator: it may add a source point (for the
     * very first segment), create intermediate reflection/diffraction points and
     * the geometric segments for propagation, and finally add the receiver point.
     * All created points and segments are appended to the supplied {@code acousticPath}.
     *
     * @param acousticPath path object to which points and segments will be appended
     * @return the same {@code acousticPath} instance after the segment has been added
     */
    private void updateWithSegmentIndex(int segmentIndex) {

        setSegmentIndex(segmentIndex);

        if (pivotPointCoordinates.size() == 2) {
            this.addSourcePoint();
            this.addIntermediatePoints();
            this.addReceiverPoint();
        } else {

            if (this.path.hasNoPoints()) { addSourcePoint(); }       
            this.addIntermediatePoints();
            this.addMainSegment();
                    
            boolean isFinalSegment = (segmentIndex == pivotPointCoordinates.size() - 1);
            boolean isUpperEdgeSegment = horizontalEdgePivotPoints.get(segmentIndex).getPivotType() == PivotPoint.PivotType.BOTTOM_OF_OBSTACLE;
            if (isFinalSegment) {
                this.addReceiverPoint();
            } else if (isUpperEdgeSegment) {
                this.addUpperHorizontalEdgeDiffractionPoint();
            } else {
                this.addHorizontalEdgeDiffractionPoint();
            }
        }
        return;
    }
    

    /**
     * Initialize internal indices and cut point references for the provided
     * segment index.
     *
     * @param segmentIndex index of the horizontal pivot segment to process
     */
    private void setSegmentIndex(int segmentIndex) {

        this.segmentIndex = segmentIndex;
        List<Coordinate> cutPointCoordinates = configuration.getCutPointCoordinates2D();

        this.startCutPointIndex = cutPointCoordinates.indexOf(pivotPointCoordinates.get(segmentIndex - 1));
        this.endCutPointIndex = cutPointCoordinates.indexOf(pivotPointCoordinates.get(segmentIndex));

        this.startExpandedCutPointIndex = configuration.getCutPointExpandedIndices().get(startCutPointIndex);
        this.endExpandedCutPointIndex = configuration.getCutPointExpandedIndices().get(endCutPointIndex);
        this.startCutPoint = configuration.getCutProfilePoints().get(startCutPointIndex);
        this.endCutPoint = configuration.getCutProfilePoints().get(endCutPointIndex);
        
        // Adjust ground indices for walls        
        if (endExpandedCutPointIndex - 1 > startExpandedCutPointIndex && endCutPoint instanceof CutPointWall) {
            final CutPointWall endCutPointWall = (CutPointWall) endCutPoint;
            if (endCutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER)) {
                endExpandedCutPointIndex -= 1;
            } else if (endCutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                endExpandedCutPointIndex -= 2;
            }
        }
    }

    
        
    /**
     * Create and append the source {@code PointPath} for the start of the
     * current segment. Sets the initial orientation based on the first
     * reachable reflection or the segment end if none exists.
     */
    private void addSourcePoint() {
        this.sourceCutPointIndex = startCutPointIndex;
        this.sourceExpandedCutPointIndex = startExpandedCutPointIndex;
        PointPath sourcePoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(startCutPointIndex), 
            startCutPoint.getzGround(), 
            SRCE
        );
        
        // Find target for orientation calculation
        Coordinate targetCoordinate = findFirstReflectionTarget();
        
        // Compute emission direction
        Orientation emissionDirection = computeOrientation(
            configuration.getCutProfile().getSource().getOrientation(),
            startCutPoint.getCoordinate(), 
            targetCoordinate
        );
        
        sourcePoint.setOrientation(emissionDirection);
        this.path.addPoint(sourcePoint);
        this.path.setRaySourceReceiverDirectivity(emissionDirection);
    }
    
    
    /**
     * Choose the target coordinate used to compute source emission orientation.
     *
     * The method scans points between the segment endpoints and returns the first
     * elevated reflection or vertical-edge diffraction (with obstacle). If none are
     * found, the segment end coordinate is returned.
     *
     * @return coordinate to use as orientation target for the source
     */
    private Coordinate findFirstReflectionTarget() {
        Coordinate targetCoordinate = configuration.getCutProfilePointCoordinate(endCutPointIndex);
        
        for (int pointIndex = startCutPointIndex + 1; pointIndex < endCutPointIndex; pointIndex++) {
            final CutPoint currentPoint = configuration.getCutProfilePoints().get(pointIndex);
            if (currentPoint instanceof CutPointReflection) {
                targetCoordinate = currentPoint.getCoordinate();
                break;
            } else if (currentPoint instanceof CutPointVEdgeDiffraction && currentPoint.hasObstacle()) {
                targetCoordinate = currentPoint.getCoordinate();
                break;
            }
        }
        
        return targetCoordinate;
    }
    
    
    /**
     * Process cut profile points between the start and end of this segment.
     *
     * <p>Converts reflection cut points into reflection {@code PointPath}
     * instances and handles vertical-edge diffractions by creating diffraction
     * points and intermediate segments as required.</p>
     */
    private void addIntermediatePoints() {
        int previousCutPointIndex = startCutPointIndex;
        
        for (int pointIndex = startCutPointIndex + 1; pointIndex < endCutPointIndex; pointIndex++) {
            final CutPoint currentPoint = configuration.getCutProfilePoints().get(pointIndex);
            final Coordinate currentPointCoordinate2D = configuration.getCutPointCoordinates2D().get(pointIndex);
            
            if (currentPoint instanceof CutPointReflection && currentPoint.hasObstacle()) {
                this.addReflectionPoint(currentPoint, currentPointCoordinate2D);
            } else if (currentPoint instanceof CutPointVEdgeDiffraction) {
                this.addVerticalEdgeDiffractionPoint(currentPoint, currentPointCoordinate2D);                
                this.addIntermediateSegment(previousCutPointIndex, pointIndex);
                previousCutPointIndex = pointIndex;
            }
        }
        
        int lastSegmentIndex = pivotPointCoordinates.size() - 1;
        if (previousCutPointIndex != startCutPointIndex && segmentIndex == lastSegmentIndex) {
            this.addFinalIntermediateSegment(previousCutPointIndex);
        }
        return;
    }

    /**
     * Convert a {@link CutPointReflection} into a {@link PointPath} reflection
     * point and append it to the internal {@code Path}.
     *
     * @param currentPoint reflection cut point
     * @param currentPointCoordinate2D 2D coordinate of the reflection point
     */
    private void addReflectionPoint(CutPoint currentPoint, Coordinate currentPointCoordinate2D) {
        CutPointReflection cutPointReflection = (CutPointReflection) currentPoint;
        double wallAltitudeAtReflectionPoint = Vertex.interpolateZ(
            cutPointReflection.getCoordinate(),
            cutPointReflection.getWall().p0, 
            cutPointReflection.getWall().p1
        );
        
        PointPath reflectionPoint = new PointPath(
            currentPointCoordinate2D,
            cutPointReflection.getzGround(),
            cutPointReflection.getWallAlpha(), 
            REFL
        );
        reflectionPoint.setObstacleZ(wallAltitudeAtReflectionPoint);
        this.path.addPoint(reflectionPoint);
        return;
    }
    
    /**
     * Handle a vertical-edge diffraction cut point by appending a vertical
     * diffraction {@code PointPath} to the internal path.
     *
     * @param currentPoint vertical-edge diffraction cut point
     * @param currentPointCoordinate2D 2D coordinate of the diffraction point
     */
    private void addVerticalEdgeDiffractionPoint(CutPoint currentPoint, Coordinate currentPointCoordinate2D) {
        // Add vertical edge diffraction point
        PointPath diffractionPoint = new PointPath(
            currentPointCoordinate2D,
            currentPoint.getzGround(),
            new ArrayList<>(),
            DIFV
        );
        this.path.addPoint(diffractionPoint);
        
        // Create segment for vertical diffraction
        // this.addVerticalEdgeDiffractionSegment(previousCutPointIndex, pointIndex);

        return;
    }
    
    /**
     * Build a geometric intermediate segment between two cut profile indices.
     *
     * <p>Extracts the elevation profile between the two cut indices, computes a
     * mean ground plane, and creates a {@code SegmentPath} with ground
     * absorption and attenuation coefficients appropriate for the slice.</p>
     *
     * @param startSegmentIndex index of the segment start cut point in the cut profile
     * @param endSegmentIndex   index of the segment end cut point in the cut profile
     */
    private void addIntermediateSegment(int startSegmentIndex, int endSegmentIndex) {
        Coordinate[] segmentGroundPoints = Arrays.copyOfRange(
            configuration.getElevationProfile2D(), 
            configuration.getCut2DGroundIndex().get(startSegmentIndex),
            configuration.getCut2DGroundIndex().get(endSegmentIndex) + 1
        );
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
        
        SegmentPath segment = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            configuration.getCutPointCoordinates2D().get(startSegmentIndex),
            configuration.getCutPointCoordinates2D().get(endSegmentIndex),
            meanPlane, 
            configuration.getCutProfile().calculateWeightedGroundAbsorption(
                configuration.getCutProfilePoints().get(startSegmentIndex), 
                configuration.getCutProfilePoints().get(endSegmentIndex), 
                Scene.DEFAULT_G_BUILDING
            ), 
            configuration.getGroundAttenuationCoefficient()
        );
        segment.setPoints2DGround(segmentGroundPoints);
        this.path.addSegment(segment);
        return;
    }

    /**
     * Append the receiver point for this segment.
     *
     * <p>Creates a {@code PointPath} of type RECV using the end cut point
     * coordinate and ground elevation.</p>
     */
    private void addReceiverPoint() {
        this.receiverCutPointIndex = endCutPointIndex;
        this.receiverExpandedCutPointIndex = endExpandedCutPointIndex;

        PointPath receiverPoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(endCutPointIndex), 
            endCutPoint.getzGround(), 
            RECV
        );
        this.path.addPoint(receiverPoint);
        return;
    }

    
    /**
     * Create the final intermediate segment connecting the last processed pivot
     * to the end of the profile (receiver).
     *
     * @param startSegmentIndex index of the last pivot processed in this segment
     */
    private void addFinalIntermediateSegment(int startSegmentIndex) {

        Coordinate[] segmentGroundPoints = Arrays.copyOfRange(
            configuration.getElevationProfile2D(), 
            endExpandedCutPointIndex, 
            configuration.getElevationProfile2D().length
        );
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
        
        SegmentPath segment = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            configuration.getCutPointCoordinates2D().get(startSegmentIndex), 
            configuration.getCutPointCoordinates2D().get(configuration.getCutPointCoordinates2D().size() - 1),
            meanPlane, 
            configuration.getCutProfile().calculateWeightedGroundAbsorption(
                endCutPoint, 
                configuration.getCutProfilePoints().get(configuration.getCutProfilePoints().size() - 1), 
                Scene.DEFAULT_G_BUILDING
            ),
            configuration.getGroundAttenuationCoefficient()
        );
        segment.setPoints2DGround(segmentGroundPoints);
        this.path.addSegment(segment);
        return;
    }
    

    /**
     * Build the main diffraction propagation segment between the segment endpoints.
     *
     * <p>This constructs the primary {@code SegmentPath} spanning the full
     * segment (from start cut point to end cut point). It computes the direct
     * ray distance, extracts the ground slice and assigns weighted ground
     * absorption for the entire segment.</p>
     */
    private void addMainSegment() {
        
        if (pivotPointCoordinates.size() == 2) {
            throw new IllegalStateException("Main diffraction segment can only be added when there are intermediate pivot points.");
        }

        Coordinate[] segmentGroundPoints = Arrays.copyOfRange(
            configuration.getElevationProfile2D(), 
            startExpandedCutPointIndex, 
            endExpandedCutPointIndex + 1
        );
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
        
        SegmentPath path = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            configuration.getCutPointCoordinates2D().get(startCutPointIndex), 
            configuration.getCutPointCoordinates2D().get(endCutPointIndex), 
            meanPlane,
            configuration.getCutProfile().calculateWeightedGroundAbsorption(
                startCutPoint, 
                endCutPoint, 
                Scene.DEFAULT_G_BUILDING
            ),
            startCutPoint.getGroundCoefficient()
        );

        path.setDirectRayDistance(startCutPoint.getCoordinate().distance3D(endCutPoint.getCoordinate()));
        path.setPoints2DGround(segmentGroundPoints);
        this.path.addSegment(path);
        return;
    }
    
    
    /**
     * Append a horizontal-edge diffraction {@code PointPath} at the segment end.
     *
     * <p>The method sets diffraction-specific flags (e.g. whether a body
     * barrier is present) and attaches wall alpha when the end cut point is a
     * wall.</p>
     */
    private void addHorizontalEdgeDiffractionPoint() {        
        PointPath diffractionPoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(endCutPointIndex), 
            endCutPoint.getzGround(), 
            DIFH
            );
        diffractionPoint.bodyBarrier = configuration.isBodyBarrier();
        if (endCutPoint instanceof CutPointWall) {
            diffractionPoint.alphaWall = ((CutPointWall) endCutPoint).getWallAlpha();
        }
        this.path.addPoint(diffractionPoint);
        return;
    }

    
    private void addUpperHorizontalEdgeDiffractionPoint() {        
        PointPath diffractionPoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(endCutPointIndex), 
            endCutPoint.getzGround(), 
            DIFB
            );
        diffractionPoint.bodyBarrier = configuration.isBodyBarrier();
        if (endCutPoint instanceof CutPointWall) {
            diffractionPoint.alphaWall = ((CutPointWall) endCutPoint).getWallAlpha();
        }
        this.path.addPoint(diffractionPoint);
        return;
    }
    
    /**
     * Compute the orientation used for emission or reflection based on a
     * reference {@code Orientation} and the local outgoing ray vector.
     *
     * <p>If {@code sourceOrientation} is {@code null} this method returns
     * {@code null}. Otherwise the method rotates the supplied orientation to
     * align with the normalized vector from {@code src} to {@code next} and
     * returns a new {@code Orientation}.</p>
     *
     * @param sourceOrientation reference orientation to rotate (may be {@code null})
     * @param src               source coordinate (3D) used to build direction vector
     * @param next              destination coordinate (3D) used to build direction vector
     * @return a new {@code Orientation} aligned with the direction from {@code src}
     *         to {@code next}, or {@code null} when {@code sourceOrientation} is
     *         {@code null}
     */
    private static Orientation computeOrientation(Orientation sourceOrientation, Coordinate src, Coordinate next){
        if(sourceOrientation == null) {
            return null;
        }
        Vector3D outgoingRay = new Vector3D(new Coordinate(next.x - src.x,
                next.y - src.y,
                next.z - src.z)).normalize();
        return Orientation.fromVector(Orientation.rotate(sourceOrientation, outgoingRay, true), 0);
    }
}
