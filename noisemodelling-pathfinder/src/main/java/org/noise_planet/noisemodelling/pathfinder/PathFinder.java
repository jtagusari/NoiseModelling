/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;
import org.locationtech.jts.geom.*;
import org.noise_planet.noisemodelling.pathfinder.path.*;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.*;
// JTSUtility and Vector3D imports removed — moved logic to SourceCollector
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// static imports LEFT/RIGHT removed — not used in this file

/**
 * High-level orchestrator for acoustic propagation path computations.
 * Coordinates receiver processing and delegates algorithmic work to specialized classes.
 * 
 * Main responsibilities:
 * - Orchestrate parallel execution via PathExecutionManager
 * - Coordinate per-receiver processing via ReceiverProcessor
 * - Provide convenience methods for geometry operations and elevation conversion
 * - Handle profiling and progress reporting
 *
 * Delegation to specialized classes:
 * - PathExecutionManager: Thread pool management, batch scheduling, and parallel task execution
 * - ReceiverProcessor: Per-receiver workflow orchestration and source-receiver pair processing
 * - DirectAndDiffractionEvaluator: Direct path computation and vertical/horizontal diffraction evaluation
 * - DiffractionPathBuilder: Side-hull construction and edge diffraction path building
 * - ReflectionPathBuilder: Reflection path computation and mirror receiver handling
 * - SourceCollector: Visible source point collection and line-source segmentation
 * - GeometryUtils: Low-level geometry operations (planes, coordinate filtering, vector conversion)
 * - LineStringSplitter: LineString segmentation with size constraints
 * - ElevationConverter: Coordinate elevation transformation (relative to absolute)
 * - ProfilerThread: Performance metrics collection and timing measurement
 *
 * @author Nicolas Fortin
 * @author Pierre Aumond
 * @author Sylvain Palominos
 */
public class PathFinder {
    public static final Logger LOGGER = LoggerFactory.getLogger(PathFinder.class);
    public enum ComputationSide {LEFT, RIGHT}

    /** Progress visitor for cancellation and progress reporting. */
    public ProgressVisitor progressVisitor;

    /** Propagation data (immutable). */
    private final Scene data;

    /** Number of threads for parallel execution. */
    private int threadCount;

    /** Optional profiler for performance metrics. */
    private ProfilerThread profilerThread;

    public Scene getData() {
        return data;
    }

    /**
     * Create PathFinder with progress visitor.
     * @param data Propagation scene data used for ray computation.
     * @param progressVisitor Progression information / cancellation handle passed by callers.
     */
    public PathFinder(Scene data, ProgressVisitor progressVisitor) {
        this.data = data;
        this.threadCount = Runtime.getRuntime().availableProcessors();
        this.progressVisitor = progressVisitor;
    }

    /**
     * Create PathFinder with default progress visitor.
     * @param data Propagation scene data used for ray computation.
     */
    public PathFinder(Scene data) {
        this.data = data;
        this.threadCount = Runtime.getRuntime().availableProcessors();
        this.progressVisitor = new EmptyProgressVisitor();
    }

    public ProfilerThread getProfilerThread() {
        return profilerThread;
    }

    public void setProfilerThread(ProfilerThread profilerThread) {
        this.profilerThread = profilerThread;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Execute path computations for all receivers in parallel.
     * @param computeRaysOut Result output factory.
     */
    public void run(CutPlaneVisitorFactory computeRaysOut) {
        PathExecutionManager executionManager = new PathExecutionManager(threadCount, data);
        executionManager.executeInParallel(this, computeRaysOut, progressVisitor);
    }

    /**
     * Process a single receiver by delegating to ReceiverProcessor.
     * @param receiverPointInfo Receiver information.
     * @param dataOut Result output factory.
     * @param visitor Progress visitor.
     */
    public void computeRaysAtPosition(ReceiverPointInfo receiverPointInfo, CutPlaneVisitor dataOut, ProgressVisitor visitor) {
        ReceiverProcessor processor = new ReceiverProcessor(data, profilerThread);
        processor.processReceiver(receiverPointInfo, dataOut, visitor);
    }

    /**
     * Compute direct path and diffraction.
     * @param src Source information.
     * @param rcv Receiver information.
     * @param verticalDiffraction Enable vertical diffraction.
     * @param horizontalDiffraction Enable horizontal diffraction.
     * @param dataOut Result output factory.
     */
    public CutPlaneVisitor.PathSearchStrategy directPath(SourcePointInfo src, ReceiverPointInfo rcv,
                                                         boolean verticalDiffraction, boolean horizontalDiffraction,
                                                         CutPlaneVisitor dataOut) {
        return DirectAndDiffractionEvaluator.computeDirectPath(src, rcv, verticalDiffraction, horizontalDiffraction, dataOut, data);
    }

    /**
     * Compute vertical edge diffraction.
     * @param rcv Receiver information.
     * @param src Source information.
     * @param data Propagation scene data.
     * @param side Computation side (left or right).
     */
    public CutProfile computeVEdgeDiffraction(ReceiverPointInfo rcv, SourcePointInfo src,
                                               Scene data, ComputationSide side) {
        return DiffractionPathBuilder.computeVEdgeDiffraction(rcv, src, data, side);
    }

    /**
     * Compute side hull for diffraction path search.
     * @param left Compute left side hull.
     * @param p1 First point.
     * @param p2 Second point.
     * @param profileBuilder Profile builder.
     */
    public List<Coordinate> computeSideHull(boolean left, Coordinate p1, Coordinate p2, ProfileBuilder profileBuilder) {
        return DiffractionPathBuilder.computeSideHull(left, p1, p2, profileBuilder, data.maxSrcDist);
    }

    /**
     * Geometry utility methods (delegate to GeometryUtils)
     * @param p0 First point.
     * @param p1 Second point.
     */
    public static Plane computeZeroRadPlane(Coordinate p0, Coordinate p1) {
        return GeometryUtils.computeZeroRadPlane(p0, p1);
    }

    /**
     * Filter points by side of a line segment.
     * @param sr Line segment.
     * @param left Compute left side.
     * @param segmentsCoordinates List of segment coordinates.
     * @return Filtered list of coordinates.
     */
    public static List<Coordinate> filterPointsBySide(LineSegment sr, boolean left,
                                                      List<Coordinate> segmentsCoordinates) {
        return GeometryUtils.filterPointsBySide(sr, left, segmentsCoordinates);
    }

    /**
     * Cut roof points with a plane.
     * @param plane Cutting plane.
     * @param roofPts List of roof points.
     * @return List of cut points.
     */
    public static List<Coordinate> cutRoofPointsWithPlane(Plane plane, List<Coordinate> roofPts) {
        return GeometryUtils.cutRoofPointsWithPlane(plane, roofPts);
    }

    /**
     * Convert a coordinate to a 3D vector.
     * @param p Coordinate to convert.
     * @return 3D vector representation of the coordinate.
     */
    public static org.apache.commons.math3.geometry.euclidean.threed.Vector3D coordinateToVector(Coordinate p) {
        return GeometryUtils.coordinateToVector(p);
    }

    /**
     * Compute reflection paths.
     * @param rcv Receiver information.
     * @param src Source information.
     * @param receiverMirrorIndex Mirror receiver index.
     * @param dataOut Data output.
     * @param initialStrategy Initial path search strategy.
     */
    public CutPlaneVisitor.PathSearchStrategy computeReflexion(ReceiverPointInfo rcv,
                                                               SourcePointInfo src,
                                                               MirrorReceiversCompute receiverMirrorIndex,
                                                               CutPlaneVisitor dataOut, CutPlaneVisitor.PathSearchStrategy initialStrategy) {
        return ReflectionPathBuilder.computeReflexion(rcv, src, receiverMirrorIndex, dataOut, initialStrategy, data);
    }

    /**
     * Split LineString into points with maximum segment size.
     * @param geom LineString geometry.
     * @param segmentSizeConstraint Maximum segment size constraint.
     * @param pts List to store the resulting points.
     * @return Total length of the split segments.
     */
    public static double splitLineStringIntoPoints(LineString geom, double segmentSizeConstraint,
                                                   List<Coordinate> pts) {
        return LineStringSplitter.splitLineStringIntoPoints(geom, segmentSizeConstraint, pts);
    }

    // Elevation conversion methods
    
    /**
     * Convert source Z coordinates from relative to absolute elevation.
     */
    public void makeSourceRelativeZToAbsolute() {
        ElevationConverter converter = new ElevationConverter(data);
        converter.changeGeometries(data.getSourceGeometries());
    }

    /**
     * Convert receiver Z coordinates from relative to absolute elevation.
     */
    public void makeReceiverRelativeZToAbsolute() {
        ElevationConverter receiverConverter = new ElevationConverter(data);
        receiverConverter.changeCoordinates(data.receivers);
        
        for (int i = 0; i < data.countReceivers(); i++) {
            Coordinate coord = data.getReceiverCoordinateByIndex(i);
            long pk = data.getReceiverPkByIndex(i);
            Scene.HeightType heightType = data.getReceiverHeightTypeByPk(pk);
            if(heightType == Scene.HeightType.RELATIVE) {
                coord.setZ(coord.getZ() + data.profileBuilder.getZGround(coord));
                heightType = Scene.HeightType.ABSOLUTE;
            }
        }
    }
}
