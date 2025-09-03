package org.noise_planet.noisemodelling.pathfinder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * Helper to collect source sample points visible to a receiver. Extracted from PathFinder.
 *
 * Single responsibility: enumerate and sample raw sources near a receiver.
 * This class queries the spatial index, samples LineString sources into
 * point samples (via {@link LineStringSplitter}), and attaches basic
 * metadata (orientation, primary key). It must not compute propagation
 * profiles, visibility tests beyond simple distance checks, or modify
 * global scene state.
 */
public final class SourceCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(SourceCollector.class);
    
    private SourceCollector() {}

    public static List<SourcePointInfo> collectSourcePoints(ReceiverPointInfo receiverPointInfo, Scene scene) {
        double searchSourceDistance = scene.getMaxSrcDist();
        Envelope receiverSourceRegion = new Envelope(receiverPointInfo.getCoordinate());
        receiverSourceRegion.expandBy(searchSourceDistance);

        Iterator<Integer> regionSourcesList = scene.getSourceQuery().query(receiverSourceRegion);
        List<SourcePointInfo> sourceList = new ArrayList<>();

        //Already processed Raw source (line and/or points)
        HashSet<Integer> processedLineSources = new HashSet<>();
        while (regionSourcesList.hasNext()) {
            Integer srcIndex = regionSourcesList.next();
            if (!processedLineSources.contains(srcIndex)) {
                processedLineSources.add(srcIndex);
                Geometry source = scene.getSourceGeometryByIndex(srcIndex);
                handleSourceGeometryForReceiver(srcIndex, source, receiverPointInfo, sourceList, scene);
            }
        }
        // Sort sources by distance to receiver (keeps original behavior)
        sourceList.sort(
            Comparator.comparingDouble(
                src -> receiverPointInfo.getCoordinate().distance3D(src.getCoordinate())
            )
        );
        return sourceList;
    }

    /**
     * Dispatch handling depending on the geometry type. Keeps collectSourcePoints concise.
     */
    private static void handleSourceGeometryForReceiver(int srcIndex, Geometry source, ReceiverPointInfo receiverPointInfo, List<SourcePointInfo> sourceList, Scene scene) {
        if (source instanceof org.locationtech.jts.geom.Point) {
            handlePointSource((org.locationtech.jts.geom.Point) source, srcIndex, receiverPointInfo, sourceList, scene);
        } else if (source instanceof LineString) {
            addLineSource((LineString) source, receiverPointInfo.getCoordinate(), srcIndex, sourceList, scene);
        } else if (source instanceof MultiLineString) {
            handleMultiLineString((MultiLineString) source, srcIndex, receiverPointInfo, sourceList, scene);
        } else {
            throw new IllegalArgumentException(
                    String.format("Sound source %s geometry are not supported", source.getGeometryType()));
        }
    }

    private static void handlePointSource(org.locationtech.jts.geom.Point sourcePoint, int srcIndex, ReceiverPointInfo receiverPointInfo, List<SourcePointInfo> sourceList, Scene scene) {
        Coordinate sourceCoordinates = sourcePoint.getCoordinate();
        if (sourceCoordinates.distance(receiverPointInfo.getCoordinate()) < scene.getMaxSrcDist()) {
            long sourcePk = scene.getSourcePkById(srcIndex);
            Orientation orientation = scene.getSourceOrientationByPk(sourcePk);
            sourceList.add(new SourcePointInfo(srcIndex, sourcePk, sourceCoordinates, 1., orientation));
        }
    }

    private static void handleMultiLineString(MultiLineString mls, int srcIndex, ReceiverPointInfo receiverPointInfo, List<SourcePointInfo> sourceList, Scene scene) {
        for (int id = 0; id < mls.getNumGeometries(); id++) {
            Geometry subGeom = mls.getGeometryN(id);
            if (subGeom instanceof LineString) {
                addLineSource((LineString) subGeom, receiverPointInfo.getCoordinate(), srcIndex, sourceList, scene);
            }
        }
    }

    private static void addLineSource(LineString lineSource, Coordinate receiverCoord, int srcIndex, List<SourcePointInfo> sourceList, Scene scene) {
        ArrayList<Coordinate> sampledSourcePoints = new ArrayList<>();
        Coordinate nearestPoint = JTSUtility.getNearestPoint(receiverCoord, lineSource);
        double segmentSizeConstraint = Math.max(1, receiverCoord.distance3D(nearestPoint) / 2.0); //3D
        if (Double.isNaN(segmentSizeConstraint)) {
            segmentSizeConstraint = Math.max(1, receiverCoord.distance(nearestPoint) / 2.0); //2D
        }
        double li = LineStringSplitter.splitLineStringIntoPoints(lineSource, segmentSizeConstraint, sampledSourcePoints);

        LOGGER.debug("SourceCollector.addLineSource - processing {} points", sampledSourcePoints.size());
        for (int ptIndex = 0; ptIndex < sampledSourcePoints.size(); ptIndex++) {
            LOGGER.debug("Processing point {} of {}", ptIndex, sampledSourcePoints.size());
            Coordinate pt = sampledSourcePoints.get(ptIndex);
            if (pt.distance(receiverCoord) < scene.getMaxSrcDist()) {
                LOGGER.debug("Point {} is within distance, creating vector", ptIndex);
                // use the orientation computed from the line lineSource coordinates
                Vector3D orientationVector;
                if(ptIndex == 0) {
                    LOGGER.debug("First point, using lineSource coordinates");
                    orientationVector = new Vector3D(lineSource.getCoordinates()[0], sampledSourcePoints.get(ptIndex));
                } else {
                    LOGGER.debug("Non-first point, using previous point");
                    orientationVector = new Vector3D(sampledSourcePoints.get(ptIndex - 1), sampledSourcePoints.get(ptIndex));
                }
                LOGGER.debug("Created vector: {}", orientationVector);
                
                // Check for zero-length vector to prevent normalization issues
                if (orientationVector.getX() == 0.0 && orientationVector.getY() == 0.0 && orientationVector.getZ() == 0.0) {
                    LOGGER.debug("Zero-length vector detected, skipping");
                    // Skip this source point if it has zero length
                    continue;
                }
                
                // Normalize the vector safely
                try {
                    LOGGER.debug("About to normalize vector");
                    orientationVector = orientationVector.normalize();
                    LOGGER.debug("Vector normalized successfully: {}", orientationVector);
                    if (orientationVector == null) {
                        LOGGER.debug("Normalization returned null, skipping");
                        // Skip if normalization returns null
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Exception during normalization: {}", e.getMessage());
                    // Skip if normalization throws an exception
                    continue;
                }
                
                Orientation orientation;
                LOGGER.debug("About to check orientation");
                if(scene.getSourceCount() > srcIndex && scene.getSourceOrientations().containsKey(scene.getSourcePkById(srcIndex))) {
                    // If the line source already provide an orientation then alter the line orientation
                    orientation = scene.getSourceOrientationByPk(scene.getSourcePkById(srcIndex));
                    if (orientation != null) {
                        try {
                            Vector3D rotatedVector = Orientation.rotate(new Orientation(orientation.yaw, orientation.roll, 0), orientationVector);
                            if (rotatedVector != null) {
                                orientation = Orientation.fromVector(rotatedVector, orientation.roll);
                            } else {
                                // Fallback to default orientation
                                orientation = Orientation.fromVector(Orientation.rotate(new Orientation(0,0,0), orientationVector), 0);
                            }
                        } catch (Exception e) {
                            // Fallback to default orientation
                            orientation = Orientation.fromVector(Orientation.rotate(new Orientation(0,0,0), orientationVector), 0);
                        }
                    } else {
                        orientation = Orientation.fromVector(Orientation.rotate(new Orientation(0,0,0), orientationVector), 0);
                    }
                } else {
                    orientation = Orientation.fromVector(Orientation.rotate(new Orientation(0,0,0), orientationVector), 0);
                }
                long sourcePk = scene.getSourcePkById(srcIndex);
                sourceList.add(new SourcePointInfo(srcIndex, sourcePk, pt, li, orientation));
            }
        }
    }
}
