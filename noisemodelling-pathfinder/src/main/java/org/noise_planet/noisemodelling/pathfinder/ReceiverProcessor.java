package org.noise_planet.noisemodelling.pathfinder;

import org.h2gis.api.ProgressVisitor;
import org.locationtech.jts.geom.Envelope;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.*;
import org.noise_planet.noisemodelling.pathfinder.path.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReceiverProcessor extracts and owns the per-receiver orchestration that used to
 * live inside {@link PathFinder#computeRaysAtPosition}. In the refactor it now
 * takes responsibility for:
 * - Reflection preprocessing for a receiver
 * - Collecting visible source points
 * - Driving direct/diffraction and reflection evaluation for each source
 * - Managing CutPlaneVisitor lifecycle (startReceiver/finalizeReceiver)
 * - Recording receiver-level profiling metrics
 *
 * The numerical and geometry algorithms remain delegated to existing components
 * (SourceCollector, DirectAndDiffractionEvaluator, ReflectionPathBuilder), while
 * ReceiverProcessor centralizes the orchestration and lifecycle management.
 */
public class ReceiverProcessor {
    private final Scene scene;
    private final org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread profilerThread;
    
    /**
     * Inner class to hold profiling timing information during processing.
     */
    private static class ProfilingTimes {
        long reflectionPreprocessTime = 0;
        long sourceCollectTime = 0;
    }

    public ReceiverProcessor(Scene scene, org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread profilerThread) {
        this.scene = scene;
        this.profilerThread = profilerThread;
    }

    /**
     * Processes a single receiver by orchestrating reflection preprocessing, source collection,
     * and path evaluation for all visible sources.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param cutPlaneVisitor Visitor for processing cut planes
     * @param visitor Progress visitor for cancellation checking
     */
    public void processReceiver(ReceiverPointInfo receiverPointInfo, CutPlaneVisitor cutPlaneVisitor, ProgressVisitor visitor) {
        long startTime = System.nanoTime();
        ProfilingTimes profilingTimes = new ProfilingTimes();
        
        // Preprocessing phase
        MirrorReceiversCompute receiverMirrorIndex = performReflectionPreprocessing(receiverPointInfo, startTime, profilingTimes);
        
        // Source collection phase
        List<SourcePointInfo> sourceList = collectVisibleSources(receiverPointInfo, profilingTimes);
        
        // Initialize visitor
        AtomicInteger cutProfileCount = new AtomicInteger(0);
        cutPlaneVisitor.startReceiver(receiverPointInfo, sourceList, cutProfileCount);
        
        // Process all sources
        AtomicInteger processedSources = processAllSources(receiverPointInfo, sourceList, receiverMirrorIndex, 
                                                          cutPlaneVisitor, visitor);
        
        // Record metrics and finalize
        recordProcessingMetrics(receiverPointInfo, startTime, sourceList, processedSources, cutProfileCount, profilingTimes);
        cutPlaneVisitor.finalizeReceiver(receiverPointInfo);
    }

    /**
     * Performs reflection preprocessing by building mirror receiver index if reflections are enabled.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param startTime Processing start time for profiling
     * @param profilingTimes Object to store profiling measurements
     * @return Mirror receivers computation index or null if reflections disabled
     */
    private MirrorReceiversCompute performReflectionPreprocessing(ReceiverPointInfo receiverPointInfo, long startTime, ProfilingTimes profilingTimes) {
        if (scene.getReflexionOrder() <= 0) {
            return null;
        }
        
        Envelope receiverPropagationEnvelope = new Envelope(receiverPointInfo.getCoordinate());
        receiverPropagationEnvelope.expandBy(scene.getMaxSrcDist());
        List<Wall> buildWalls = scene.profileBuilder.getWallsIn(receiverPropagationEnvelope);
        
        MirrorReceiversCompute receiverMirrorIndex = new MirrorReceiversCompute(buildWalls, 
                receiverPointInfo.getCoordinate(), scene.getReflexionOrder(), scene.getMaxSrcDist(), scene.getMaxRefDist());
        
        if (profilerThread != null) {
            profilingTimes.reflectionPreprocessTime = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
        
        return receiverMirrorIndex;
    }

    /**
     * Collects all visible source points for the given receiver.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param profilingTimes Object to store profiling measurements
     * @return List of visible source points
     */
    private List<SourcePointInfo> collectVisibleSources(ReceiverPointInfo receiverPointInfo, ProfilingTimes profilingTimes) {
        long sourceCollectStart = System.nanoTime();
        List<SourcePointInfo> sourceList = SourceCollector.collectSourcePoints(receiverPointInfo, scene);
        
        if (profilerThread != null) {
            profilingTimes.sourceCollectTime = TimeUnit.MILLISECONDS.convert(System.nanoTime() - sourceCollectStart, TimeUnit.NANOSECONDS);
        }
        
        return sourceList;
    }

    /**
     * Processes all source points for a receiver, evaluating direct, diffraction, and reflection paths.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param sourceList List of source points to process
     * @param receiverMirrorIndex Mirror receivers computation index (may be null)
     * @param cutPlaneVisitor Visitor for processing cut planes
     * @param visitor Progress visitor for cancellation checking
     * @return Number of processed sources
     */
    private AtomicInteger processAllSources(ReceiverPointInfo receiverPointInfo, List<SourcePointInfo> sourceList,
                                           MirrorReceiversCompute receiverMirrorIndex, CutPlaneVisitor cutPlaneVisitor,
                                           ProgressVisitor visitor) {
        AtomicInteger processedSources = new AtomicInteger(0);

        for (SourcePointInfo sourcePointInfo : sourceList) {
            CutPlaneVisitor.PathSearchStrategy strategy = processSingleSource(receiverPointInfo, sourcePointInfo,
                    receiverMirrorIndex, cutPlaneVisitor);
            
            processedSources.incrementAndGet();

            if (shouldStopProcessing(visitor, strategy)) {
                break;
            }
        }
        
        return processedSources;
    }

    /**
     * Processes a single source point, evaluating direct, diffraction, and reflection paths.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param sourcePointInfo Information about the source point
     * @param receiverMirrorIndex Mirror receivers computation index (may be null)
     * @param cutPlaneVisitor Visitor for processing cut planes
     * @return Path search strategy indicating how to proceed
     */
    private CutPlaneVisitor.PathSearchStrategy processSingleSource(ReceiverPointInfo receiverPointInfo,
                                                                   SourcePointInfo sourcePointInfo,
                                                                   MirrorReceiversCompute receiverMirrorIndex,
                                                                   CutPlaneVisitor cutPlaneVisitor) {
        CutPlaneVisitor.PathSearchStrategy strategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;

        double propaDistance = sourcePointInfo.getCoordinate().distance(receiverPointInfo.getCoordinate());
        if (propaDistance >= scene.getMaxSrcDist()) {
            return strategy;
        }

        // Direct and diffraction evaluation
        strategy = evaluateDirectAndDiffractionPaths(sourcePointInfo, receiverPointInfo, cutPlaneVisitor);
        if (isTerminalStrategy(strategy)) {
            return strategy;
        }

        // Reflection evaluation
        if (scene.getReflexionOrder() > 0 && receiverMirrorIndex != null) {
            strategy = evaluateReflectionPaths(receiverPointInfo, sourcePointInfo, receiverMirrorIndex, 
                                             cutPlaneVisitor, strategy);
        }

        return strategy;
    }

    /**
     * Evaluates direct and diffraction paths for a source-receiver pair.
     * 
     * @param sourcePointInfo Information about the source point
     * @param receiverPointInfo Information about the receiver point
     * @param cutPlaneVisitor Visitor for processing cut planes
     * @return Path search strategy indicating how to proceed
     */
    private CutPlaneVisitor.PathSearchStrategy evaluateDirectAndDiffractionPaths(SourcePointInfo sourcePointInfo,
                                                                                 ReceiverPointInfo receiverPointInfo,
                                                                                 CutPlaneVisitor cutPlaneVisitor) {
        return DirectAndDiffractionEvaluator.computeDirectPath(sourcePointInfo, receiverPointInfo,
                scene.computeVerticalDiffraction, scene.computeHorizontalDiffraction, cutPlaneVisitor, scene);
    }

    /**
     * Evaluates reflection paths for a source-receiver pair.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param sourcePointInfo Information about the source point
     * @param receiverMirrorIndex Mirror receivers computation index
     * @param cutPlaneVisitor Visitor for processing cut planes
     * @param currentStrategy Current path search strategy
     * @return Updated path search strategy
     */
    private CutPlaneVisitor.PathSearchStrategy evaluateReflectionPaths(ReceiverPointInfo receiverPointInfo,
                                                                       SourcePointInfo sourcePointInfo,
                                                                       MirrorReceiversCompute receiverMirrorIndex,
                                                                       CutPlaneVisitor cutPlaneVisitor,
                                                                       CutPlaneVisitor.PathSearchStrategy currentStrategy) {
        return ReflectionPathBuilder.computeReflexion(receiverPointInfo, sourcePointInfo, receiverMirrorIndex,
                cutPlaneVisitor, currentStrategy, scene);
    }

    /**
     * Checks if the given strategy is terminal (should stop processing sources).
     * 
     * @param strategy Path search strategy to check
     * @return true if strategy is terminal, false otherwise
     */
    private boolean isTerminalStrategy(CutPlaneVisitor.PathSearchStrategy strategy) {
        return strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE) ||
               strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER);
    }

    /**
     * Determines if processing should stop based on visitor cancellation or strategy.
     * 
     * @param visitor Progress visitor (may be null)
     * @param strategy Current path search strategy
     * @return true if processing should stop, false otherwise
     */
    private boolean shouldStopProcessing(ProgressVisitor visitor, CutPlaneVisitor.PathSearchStrategy strategy) {
        return (visitor != null && visitor.isCanceled()) ||
               strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER) ||
               strategy.equals(CutPlaneVisitor.PathSearchStrategy.PROCESS_SOURCE_BUT_SKIP_RECEIVER);
    }

    /**
     * Records processing metrics if profiling is enabled.
     * 
     * @param receiverPointInfo Information about the receiver point
     * @param startTime Processing start time
     * @param sourceList List of all source points
     * @param processedSources Number of processed sources
     * @param cutProfileCount Number of cut profiles generated
     * @param profilingTimes Object containing profiling measurements
     */
    private void recordProcessingMetrics(ReceiverPointInfo receiverPointInfo, long startTime,
                                        List<SourcePointInfo> sourceList, AtomicInteger processedSources,
                                        AtomicInteger cutProfileCount, ProfilingTimes profilingTimes) {
        if (profilerThread == null) {
            return;
        }

        org.noise_planet.noisemodelling.pathfinder.utils.profiler.ReceiverStatsMetric receiverStatsMetric = 
                profilerThread.getMetric(org.noise_planet.noisemodelling.pathfinder.utils.profiler.ReceiverStatsMetric.class);
        
        if (receiverStatsMetric != null) {
            receiverStatsMetric.onReceiverCutProfiles(receiverPointInfo.getReceiverIndex(), 
                    cutProfileCount.get(), sourceList.size(), processedSources.get());
            
            long totalTime = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            receiverStatsMetric.onEndComputation(
                    new org.noise_planet.noisemodelling.pathfinder.utils.profiler.ReceiverStatsMetric.ReceiverComputationTime(
                            receiverPointInfo.getReceiverIndex(), (int) totalTime, 
                            (int) profilingTimes.reflectionPreprocessTime, (int) profilingTimes.sourceCollectTime));
        }
    }
}
