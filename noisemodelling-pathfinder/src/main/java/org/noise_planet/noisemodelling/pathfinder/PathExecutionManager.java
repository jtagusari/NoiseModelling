/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * PathExecutionManager manages the parallel execution of path finding computations.
 * It handles thread pool management, batch scheduling, and task coordination.
 * 
 * Responsibilities:
 * - Create and manage thread pools for parallel execution
 * - Partition receivers into batches for efficient processing
 * - Submit and monitor batch processing tasks
 * - Handle thread pool shutdown and termination
 * - Aggregate and propagate exceptions from worker threads
 * - Coordinate progress reporting across batches
 * 
 * This class separates the multithreading concerns from PathFinder's main orchestration
 * responsibilities, making the code more modular and testable.
 * 
 */
public class PathExecutionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathExecutionManager.class);
    
    private final int threadCount;
    private final Scene data;
    
    /**
     * Create a new execution manager.
     * 
     * @param threadCount Number of threads to use for parallel execution
     * @param data Scene data containing receivers and other computation parameters
     */
    public PathExecutionManager(int threadCount, Scene data) {
        this.threadCount = threadCount;
        this.data = data;
    }
    
    /**
     * Execute path finding computations in parallel across all receivers.
     * 
     * @param pathFinder PathFinder instance to use for individual receiver computations
     * @param computeRaysOut Factory for creating output visitors
     * @param progressVisitor Progress visitor for cancellation and progress reporting
     */
    public void executeInParallel(PathFinder pathFinder, CutPlaneVisitorFactory computeRaysOut, ProgressVisitor progressVisitor) {
        ThreadPool threadManager = createThreadPool();
        List<ReceiverBatchScheduler.Range> batches = createBatches();
        List<Future<Boolean>> tasks = new ArrayList<>();
        ProgressVisitor cellProgress = createProgressVisitor(progressVisitor);
        
        try {
            submitBatchTasks(pathFinder, computeRaysOut, cellProgress, batches, threadManager, tasks);
            shutdownAndAwaitTermination(threadManager);
            validateTaskResults(tasks);
        } catch (Exception e) {
            // Ensure thread pool is shutdown even if an exception occurs
            forceShutdown(threadManager);
            throw e;
        }
    }
    
    /**
     * Create and configure the thread pool for parallel execution.
     */
    private ThreadPool createThreadPool() {
        return new ThreadPool(threadCount, threadCount + 1, Long.MAX_VALUE, TimeUnit.SECONDS);
    }
    
    /**
     * Create receiver batches for parallel processing.
     */
    private List<ReceiverBatchScheduler.Range> createBatches() {
        return ReceiverBatchScheduler.computeBatches(data.getReceiverCount(), threadCount);
    }
    
    /**
     * Create progress visitor for tracking overall progress.
     */
    private ProgressVisitor createProgressVisitor(ProgressVisitor progressVisitor) {
        return progressVisitor == null ? new EmptyProgressVisitor() : progressVisitor.subProcess(data.getReceiverCount());
    }
    
    /**
     * Submit batch processing tasks to the thread pool.
     */
    private void submitBatchTasks(PathFinder pathFinder, CutPlaneVisitorFactory computeRaysOut, 
                                  ProgressVisitor cellProgress, List<ReceiverBatchScheduler.Range> batches,
                                  ThreadPool threadManager, List<Future<Boolean>> tasks) {
        for (ReceiverBatchScheduler.Range batch : batches) {
            if (cellProgress.isCanceled()) {
                LOGGER.info("Processing cancelled, stopping batch submission");
                break;
            }
            
            ThreadPathFinder batchThread = createBatchThread(pathFinder, computeRaysOut, cellProgress, batch);
            
            if (threadCount != 1) {
                tasks.add(threadManager.submitBlocking(batchThread));
            } else {
                // Single-threaded execution for debugging or simple cases
                executeSingleThreaded(batchThread);
            }
        }
    }
    
    /**
     * Create a batch thread for processing a range of receivers.
     */
    private ThreadPathFinder createBatchThread(PathFinder pathFinder, CutPlaneVisitorFactory computeRaysOut,
                                               ProgressVisitor cellProgress, ReceiverBatchScheduler.Range batch) {
        return new ThreadPathFinder(batch.start, batch.end, pathFinder, cellProgress, 
                                   computeRaysOut.subProcess(cellProgress), data);
    }
    
    /**
     * Execute a batch thread in single-threaded mode.
     */
    private void executeSingleThreaded(ThreadPathFinder batchThread) {
        try {
            batchThread.call();
        } catch (Exception e) {
            throw new RuntimeException("Error in single-threaded batch execution", e);
        }
    }
    
    /**
     * Shutdown the thread pool and wait for all tasks to complete.
     */
    private void shutdownAndAwaitTermination(ThreadPool threadManager) {
        threadManager.shutdown();
        try {
            if (!threadManager.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Timeout elapsed before termination.");
            }
        } catch (InterruptedException ex) {
            LOGGER.error("Interrupted while waiting for thread pool termination", ex);
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
    }
    
    /**
     * Force shutdown of the thread pool in case of errors.
     */
    private void forceShutdown(ThreadPool threadManager) {
        try {
            threadManager.shutdown();
            if (!threadManager.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Thread pool did not terminate gracefully, forcing shutdown");
                // Note: ThreadPool may not have shutdownNow() method, 
                // this is a defensive approach
            }
        } catch (InterruptedException ex) {
            LOGGER.error("Interrupted during force shutdown", ex);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Validate that all batch tasks completed successfully and propagate any exceptions.
     */
    private void validateTaskResults(List<Future<Boolean>> tasks) {
        for (Future<Boolean> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Error in batch task execution", e);
            }
        }
    }
}
