package org.noise_planet.noisemodelling.propagation.cnossos;

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
 * Encapsulates all logic required to build the acoustic representation for one
 * segment between two diffraction points. This includes adding source/receiver
 * points, intermediate reflection points, vertical edge diffractions and
 * creating the geometric segments with ground factors.
 */



public class AcousticPathProcessor {

    private final AcousticPathConfiguration configuration;
    private final Path path = new Path(new ArrayList<>(), new ArrayList<>());
    // private final Path acousticPath;
    private int segmentIndex;
    
    // Segment boundary indices
    private int startCutPointIndex, endCutPointIndex;
    private int startGroundIndex, endGroundIndex;
    
    // Cut points for this segment
    private CutPoint startCutPoint, endCutPoint;

    public AcousticPathProcessor(AcousticPathConfiguration configuration) {
        this.configuration = configuration;
    }

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
    public AcousticPathProcessor buildWithSegmentIndex(int segmentIndex) {

        setSegmentIndex(segmentIndex);

        this.initializePathSource()
                .addIntermediatePoints()
                .addReceiverPoint()
                .addMainDiffractionSegment()
                .configureDiffractionPointProperties();
                
        return this;
    }

    private void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
        List<Coordinate> diffractionPoints = configuration.getDiffractionPoints();
        List<Coordinate> cutPointCoordinates = configuration.getCutPointCoordinates2D();
        this.startCutPointIndex = cutPointCoordinates.indexOf(diffractionPoints.get(segmentIndex - 1));
        this.endCutPointIndex = cutPointCoordinates.indexOf(diffractionPoints.get(segmentIndex));
        
        // Get ground indices and cut points
        this.startGroundIndex = configuration.getCutPointExpandedIndices().get(startCutPointIndex);
        this.endGroundIndex = configuration.getCutPointExpandedIndices().get(endCutPointIndex);
        this.startCutPoint = configuration.getCutProfilePoints().get(startCutPointIndex);
        this.endCutPoint = configuration.getCutProfilePoints().get(endCutPointIndex);
        
        // Adjust ground indices for walls        
        if (endGroundIndex - 1 > startGroundIndex && endCutPoint instanceof CutPointWall) {
            final CutPointWall endCutPointWall = (CutPointWall) endCutPoint;
            if (endCutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.BUILDING_ENTER)) {
                endGroundIndex -= 1;
            } else if (endCutPointWall.intersectionType.equals(CutPointWall.INTERSECTION_TYPE.THIN_WALL_ENTER_EXIT)) {
                endGroundIndex -= 2;
            }
        }
    }

    
    
    /**
     * Index of the source cut point for this segment.
     *
     * The parent builder uses this index to update source coordinates when chaining segments.
     *
     * @return index in the cut profile that represents the start of this segment
     */
    public int getSourceIndex() {
        return startCutPointIndex;
    }
    
    /**
     * If the path is empty, add the source point and set its emission orientation.
     *
     * The emission orientation is computed towards the first relevant elevated
     * reflection or diffraction point; if none exists the receiver position is used.
     */
    private AcousticPathProcessor initializePathSource() {
        if (this.path.hasNoPoints()) {
            PointPath sourcePoint = new PointPath(
                configuration.getCutPointCoordinates2D().get(startCutPointIndex), 
                startCutPoint.getzGround(), 
                SRCE
            );
            
            // Find target for orientation calculation
            Coordinate targetPosition = findFirstReflectionTarget();
            
            // Compute emission direction
            Orientation emissionDirection = computeOrientation(
                configuration.getCutProfile().getSource().getOrientation(),
                configuration.getCutProfilePoints().get(startCutPointIndex).getCoordinate(), 
                targetPosition
            );
            
            sourcePoint.setOrientation(emissionDirection);
            this.path.addPoint(sourcePoint);
            this.path.setRaySourceReceiverDirectivity(emissionDirection);
        }
        return this;
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
        Coordinate targetPosition = configuration.getCutProfilePointCoordinate(endCutPointIndex);
        
        for (int pointIndex = startCutPointIndex + 1; pointIndex < endCutPointIndex; pointIndex++) {
            final CutPoint currentPoint = configuration.getCutProfilePoints().get(pointIndex);
            if (currentPoint instanceof CutPointReflection) {
                targetPosition = currentPoint.getCoordinate();
                break;
            } else if (currentPoint instanceof CutPointVEdgeDiffraction && currentPoint.hasObstacle()) {
                targetPosition = currentPoint.getCoordinate();
                break;
            }
        }
        
        return targetPosition;
    }
    
    /**
     * Process cut profile points between the start and end of this segment.
     *
     * Reflection points are converted to reflection PointPath objects. Vertical
     * edge diffraction points are added and the corresponding vertical diffraction
     * segments are created. The method also ensures the remaining final segment
     * after the last vertical edge is created when applicable.
     */
    private AcousticPathProcessor addIntermediatePoints() {
        int previousPivotPoint = startCutPointIndex;
        
        for (int pointIndex = startCutPointIndex + 1; pointIndex < endCutPointIndex; pointIndex++) {
            final CutPoint currentPoint = configuration.getCutProfilePoints().get(pointIndex);
            
            if (currentPoint instanceof CutPointReflection && currentPoint.hasObstacle()) {
                this.addReflectionPoint(currentPoint, pointIndex);
            } else if (currentPoint instanceof CutPointVEdgeDiffraction) {
                this.addVerticalEdgeDiffraction(currentPoint, pointIndex, previousPivotPoint);
                previousPivotPoint = pointIndex;
            }
        }

        this.addFinalVerticalDiffractionSegment(previousPivotPoint);
        return this;
    }

    /**
     * Convert a CutPointReflection into a PointPath and append it to {@code acousticPath}.
     *
     * Computes the wall altitude at the reflection coordinate and stores the wall
     * orientation (alpha) on the created point.
     *
     * @param currentPoint reflection cut point
     * @param pointIndex index of the cut point in the cut profile arrays
     */
    private void addReflectionPoint(CutPoint currentPoint, int pointIndex) {
        CutPointReflection cutPointReflection = (CutPointReflection) currentPoint;
        double wallAltitudeAtReflectionPoint = Vertex.interpolateZ(
            cutPointReflection.getCoordinate(),
            cutPointReflection.getWall().p0, 
            cutPointReflection.getWall().p1
        );
        
        PointPath reflectionPoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(pointIndex), 
            currentPoint.getzGround(),
            cutPointReflection.getWallAlpha(), 
            REFL
        );
        reflectionPoint.setObstacleZ(wallAltitudeAtReflectionPoint);
        this.path.addPoint(reflectionPoint);
        return;
    }
    
    /**
     * Handle a vertical edge diffraction cut point.
     *
     * This adds a diffraction PointPath at the cut point coordinate and then
     * creates the vertical-diffraction segment that connects the previous pivot
     * to this diffraction.
     *
     * @param currentPoint vertical-edge diffraction cut point
     * @param pointIndex index of the cut point in the cut profile arrays
     * @param previousPivotPoint index of the previous pivot/corner point used to build the segment
     * @return updated {@code acousticPath} containing the new point and segment
     */
    private void addVerticalEdgeDiffraction(CutPoint currentPoint, int pointIndex, int previousPivotPoint) {
        // Add vertical edge diffraction point
        PointPath diffractionPoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(pointIndex), 
            currentPoint.getzGround(), 
            new ArrayList<>(), 
            DIFV
        );
        this.path.addPoint(diffractionPoint);
        
        // Create segment for vertical diffraction
        this.addVerticalDiffractionSegment(previousPivotPoint, pointIndex);

        return;
    }
    
    /**
     * Build a geometric segment for a vertical-edge diffraction between two cut points.
     *
     * The method extracts the ground elevation slice between the two cut indices,
     * computes an average ground plane and creates a SegmentPath populated with
     * the appropriate ground absorption and attenuation factors.
     *
     * @param startCutPointIndex index of the segment start cut point in the cut profile
     * @param endCutPointIndex index of the segment end cut point in the cut profile
     * @return {@code acousticPath} with the newly created segment appended
     */
    private void addVerticalDiffractionSegment(int startCutPointIndex, int endCutPointIndex) {
        Coordinate[] segmentGroundPoints = Arrays.copyOfRange(
            configuration.getElevationProfile2D(), 
            configuration.getCut2DGroundIndex().get(startCutPointIndex),
            configuration.getCut2DGroundIndex().get(endCutPointIndex) + 1
        );
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
        
        SegmentPath segment = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            configuration.getCutPointCoordinates2D().get(startCutPointIndex), 
            configuration.getCutPointCoordinates2D().get(endCutPointIndex),
            meanPlane, 
            configuration.getCutProfile().calculateWeightedGroundAbsorption(
                startCutPoint, 
                configuration.getCutProfilePoints().get(endCutPointIndex), 
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
     * The receiver point is the endpoint of the segment and marked as RECV.
     */
    private AcousticPathProcessor addReceiverPoint() {
        PointPath receiverPoint = new PointPath(
            configuration.getCutPointCoordinates2D().get(endCutPointIndex), 
            endCutPoint.getzGround(), 
            RECV
        );
        this.path.addPoint(receiverPoint);
        return this;
    }
    
    /**
     * When the last processed pivot is not the segment start, create the final
     * segment that connects the last pivot to the receiver (end of profile).
     *
     * This is used for the last diffraction segment to ensure the tail segment
     * after the final vertical-edge diffraction is constructed.
     *
     * @param previousPivotPoint index of the last pivot processed in this segment
     */
    private void addFinalVerticalDiffractionSegment(int previousPivotPoint) {
        if (previousPivotPoint != startCutPointIndex && segmentIndex == configuration.getDiffractionPoints().size() - 1) {
            Coordinate[] segmentGroundPoints = Arrays.copyOfRange(
                configuration.getElevationProfile2D(), 
                endGroundIndex, 
                configuration.getElevationProfile2D().length
            );
            double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
            
            SegmentPath segment = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
                configuration.getCutPointCoordinates2D().get(previousPivotPoint), 
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
        }
        return;
    }
    
        
    /**
     * Build the main diffraction propagation segment between the segment endpoints.
     *
     * This creates a SegmentPath that spans from the start cut point to the end
     * cut point and computes direct-ray distance, ground points and absorption.
     */
    private AcousticPathProcessor addMainDiffractionSegment() {
        
        if (configuration.getDiffractionPoints().size() > 2) {
            Coordinate[] segmentGroundPoints = Arrays.copyOfRange(
                configuration.getElevationProfile2D(), 
                startGroundIndex, 
                endGroundIndex + 1
            );
            double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(segmentGroundPoints);
            
            SegmentPath path = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
                configuration.getCutPointCoordinates2D().get(startCutPointIndex), 
                configuration.getCutPointCoordinates2D().get(endCutPointIndex), 
                meanPlane,
                configuration.getCutProfile().calculateWeightedGroundAbsorption(
                    configuration.getCutProfilePoints().get(startCutPointIndex), 
                    configuration.getCutProfilePoints().get(endCutPointIndex), 
                    Scene.DEFAULT_G_BUILDING
                ),
                configuration.getCutProfilePoints().get(startCutPointIndex).getGroundCoefficient()
            );

            path.setDirectRayDistance(startCutPoint.getCoordinate().distance3D(endCutPoint.getCoordinate()));
            path.setPoints2DGround(segmentGroundPoints);
            this.path.addSegment(path);
        }
        return this;
    }
    
    /**
     * Set properties on the end diffraction point for this segment.
     *
     * If this segment is not the final global segment, mark the point as a
     * horizontal diffraction (DIFH) and copy barrier/wall properties where applicable.
     */
    private AcousticPathProcessor configureDiffractionPointProperties() {
        if (segmentIndex != configuration.getDiffractionPoints().size() - 1) {
            PointPath pt = this.path.getPointList().get(this.path.getPointCount() - 1);
            pt.type = DIFH;
            pt.bodyBarrier = configuration.isBodyBarrier();
            if (endCutPoint instanceof CutPointWall) {
                pt.alphaWall = ((CutPointWall) endCutPoint).getWallAlpha();
            }
        }
        return this;
    }

    
    /**
     *
     * @param sourceOrientation
     * @param src
     * @param next
     * @return
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
