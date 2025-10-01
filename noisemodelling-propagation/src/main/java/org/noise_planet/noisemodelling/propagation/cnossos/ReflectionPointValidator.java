package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;

import java.util.List;

/**
 * Handles validation and adjustment of reflection points for acoustic path computation.
 * This class ensures that reflection points satisfy geometric constraints and 
 * acoustic propagation requirements.
 */
public class ReflectionPointValidator {
    
    private static final double EPSILON = 1e-7;

    /**
     * Validate and adjust reflection points based on wall constraints.
     * Checks if reflection points on the acoustic path satisfy geometric constraints 
     * and wall constraints, adjusting reflection point positions as needed.
     * 
     * @param horizontalEdgePivotPoints List of diffraction points to validate (points on acoustic path)
     * @param cutProfilePoints List of cut points from the profile
     * @param cutPointCoordinates2D List of 2D coordinates of cut points
     * @return true if validation succeeds and points are valid for computation
     */
    public static boolean validateAndAdjustReflectionPoints(List<Coordinate> horizontalEdgePivotPoints, 
                                                           List<CutPoint> cutProfilePoints,
                                                           List<Coordinate> cutPointCoordinates2D) {
        // If the path is direct (no diffraction), no need to validate reflection points
        if (horizontalEdgePivotPoints.size() <= 2) {
            return true;
        }
        
        // Validate and adjust reflection points for each acoustic path segment
        return validateAllPathSegments(horizontalEdgePivotPoints, cutProfilePoints, cutPointCoordinates2D);
    }
    /**
     * Execute validation and adjustment of reflection points for all acoustic path segments.
     * 
     * @param horizontalEdgePivotPoints List of acoustic path points
     * @param cutProfilePoints Profile cut points
     * @param cutPointCoordinates2D List of 2D coordinates
     * @return true if validation succeeds for all segments
     */
    private static boolean validateAllPathSegments(List<Coordinate> horizontalEdgePivotPoints, 
                                                  List<CutPoint> cutProfilePoints,
                                                  List<Coordinate> cutPointCoordinates2D) {
        // Process consecutive acoustic path point pairs (source→1st diffraction, 1st→2nd diffraction, ..., last diffraction→receiver)
        for (int segmentIndex = 1; segmentIndex < horizontalEdgePivotPoints.size(); segmentIndex++) {
            if (!validateSegmentReflectionPoints(segmentIndex, horizontalEdgePivotPoints, cutProfilePoints, cutPointCoordinates2D)) {
                return false; // Validation failed for one of the segments
            }
        }
        return true;
    }
    
    /**
     * Validate and adjust reflection points within a specified acoustic path segment.
     * 
     * @param segmentIndex Index of the segment to process
     * @param horizontalEdgePivotPoints List of acoustic path points
     * @param cutProfilePoints Profile cut points
     * @param cutPointCoordinates2D List of 2D coordinates
     * @return true if all reflection points within the segment are valid
     */
    private static boolean validateSegmentReflectionPoints(int segmentIndex, 
                                                          List<Coordinate> horizontalEdgePivotPoints,
                                                          List<CutPoint> cutProfilePoints,
                                                          List<Coordinate> cutPointCoordinates2D) {
        // Get start and end point indices for the current segment
        int startPointIndex = cutPointCoordinates2D.indexOf(horizontalEdgePivotPoints.get(segmentIndex - 1));
        int endPointIndex = cutPointCoordinates2D.indexOf(horizontalEdgePivotPoints.get(segmentIndex));
        
        // Create geometric line segment for the acoustic path segment
        LineSegment acousticPathSegment = new LineSegment(horizontalEdgePivotPoints.get(segmentIndex - 1), horizontalEdgePivotPoints.get(segmentIndex));
        
        // Validate intermediate points (reflection point candidates) within the segment
        for (int pointIndex = startPointIndex + 1; pointIndex < endPointIndex; pointIndex++) {
            if (!validateSingleReflectionPoint(pointIndex, acousticPathSegment, cutProfilePoints, cutPointCoordinates2D)) {
                return false; // Reflection point is invalid
            }
        }
        return true;
    }
    
    /**
     * Validate and adjust a single reflection point.
     * 
     * @param pointIndex Index of the point to validate
     * @param acousticPathSegment Acoustic path segment
     * @param cutProfilePoints Profile cut points
     * @param cutPointCoordinates2D List of 2D coordinates
     * @return true if reflection point is valid (or not a reflection point)
     */
    private static boolean validateSingleReflectionPoint(int pointIndex,
                                                        LineSegment acousticPathSegment,
                                                        List<CutPoint> cutProfilePoints,
                                                        List<Coordinate> cutPointCoordinates2D) {
        final CutPoint currentPoint = cutProfilePoints.get(pointIndex);
        
        // Check if it's a reflection point (not at ground level)
        if (!isElevatedReflectionPoint(currentPoint)) {
            return true; // No validation needed if not a reflection point
        }
        
        // Execute reflection point validation and adjustment
        return adjustReflectionPointHeight(currentPoint, pointIndex, acousticPathSegment, cutPointCoordinates2D);
    }
    
    /**
     * Determine if the specified point is a reflection point above ground level.
     * 
     * @param point Point to evaluate
     * @return true if it's a reflection point above ground level
     */
    private static boolean isElevatedReflectionPoint(CutPoint point) {
        return point instanceof CutPointReflection &&
               Double.compare(point.getCoordinate().z, point.getzGround()) != 0;
    }
    
    /**
     * Adjust reflection point height and verify compliance with wall constraints.
     * 
     * @param currentPoint Reflection point to adjust
     * @param pointIndex Point index
     * @param acousticPathSegment Acoustic path segment
     * @param cutPointCoordinates2D List of 2D coordinates
     * @return true if adjusted reflection point is valid
     */
    private static boolean adjustReflectionPointHeight(CutPoint currentPoint,
                                                      int pointIndex,
                                                      LineSegment acousticPathSegment,
                                                      List<Coordinate> cutPointCoordinates2D) {
        CutPointReflection reflectionPoint = (CutPointReflection) currentPoint;
        
        // Calculate optimal position for reflection point on acoustic path segment
        Coordinate interpolatedPosition = acousticPathSegment.closestPoint(cutPointCoordinates2D.get(pointIndex));
        
        // Get wall height at reflection position
        double wallHeightAtReflection = calculateWallHeightAtPosition(currentPoint, reflectionPoint);
        
        // Check if reflection point height is below or equal to wall height
        if (isReflectionBelowWallHeight(wallHeightAtReflection, interpolatedPosition.y)) {
            // Valid reflection: update position
            updateReflectionPosition(currentPoint, pointIndex, interpolatedPosition, cutPointCoordinates2D);
            return true;
        } else {
            // Invalid reflection: reflection above wall height is impossible
            return false;
        }
    }
    
    /**
     * Calculate wall height at the specified position.
     * 
     * @param currentPoint Current reflection point
     * @param reflectionPoint Reflection point details
     * @return Wall height
     */
    private static double calculateWallHeightAtPosition(CutPoint currentPoint, 
                                                       CutPointReflection reflectionPoint) {
        return Vertex.interpolateZ(currentPoint.coordinate,
                                 reflectionPoint.wall.p0, 
                                 reflectionPoint.wall.p1);
    }
    
    /**
     * Check if reflection point height is below or equal to wall height (considering numerical errors).
     * 
     * @param wallHeight Wall height
     * @param reflectionHeight Reflection point height
     * @return true if reflection point is below or equal to wall
     */
    private static boolean isReflectionBelowWallHeight(double wallHeight, double reflectionHeight) {
        return wallHeight + EPSILON >= reflectionHeight;
    }
    
    /**
     * Update reflection point position.
     * 
     * @param currentPoint Reflection point to update
     * @param pointIndex Point index
     * @param newPosition New position
     * @param cutPointCoordinates2D List of 2D coordinates
     */
    private static void updateReflectionPosition(CutPoint currentPoint,
                                               int pointIndex,
                                               Coordinate newPosition,
                                               List<Coordinate> cutPointCoordinates2D) {
        // Update Z value of 3D coordinate
        currentPoint.getCoordinate().setZ(newPosition.y);
        // Update Y value of 2D coordinate (height information)
        cutPointCoordinates2D.get(pointIndex).setY(newPosition.y);
    }
}