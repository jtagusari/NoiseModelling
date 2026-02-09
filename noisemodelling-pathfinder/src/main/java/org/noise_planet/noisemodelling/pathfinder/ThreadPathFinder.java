/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.h2gis.api.ProgressVisitor;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;

import java.util.concurrent.Callable;

import static org.noise_planet.noisemodelling.pathfinder.PathFinder.LOGGER;

/**
 * A Thread class to evaluate all receivers cut planes.
 * Return true if the computation is done without issues
 */
public final class ThreadPathFinder implements Callable<Boolean> {
    int startReceiver; // Included
    int endReceiver; // Excluded
    PathFinder pathFinder;
    ProgressVisitor progressVisitor;
    CutPlaneVisitor cutPlaneVisitor;
    Scene scene;


    /**
     * Create the ThreadPathFinder constructor
     * @param startReceiver
     * @param endReceiver
     * @param pathFinder
     * @param progressVisitor
     * @param cutPlaneVisitor
     * @param scene
     */
    public ThreadPathFinder(int startReceiver, int endReceiver, PathFinder pathFinder,
                            ProgressVisitor progressVisitor, CutPlaneVisitor cutPlaneVisitor,
                            Scene scene) {
        this.startReceiver = startReceiver;
        this.endReceiver = endReceiver;
        this.pathFinder = pathFinder;
        this.progressVisitor = progressVisitor;
        this.cutPlaneVisitor = cutPlaneVisitor;
        this.scene = scene;
    }

    /**
     * Executes the computation of ray paths for each receiver in the specified range.
     */
    @Override
    public Boolean call() throws Exception {
        try {
            for (int idReceiver = startReceiver; idReceiver < endReceiver; idReceiver++) {
                // Cancel if requested
                if (progressVisitor != null) {
                    if (progressVisitor.isCanceled()) {
                        break;
                    }
                }
                // Guard against out-of-range receiver index
                if(idReceiver >= scene.countReceivers()) {
                    throw new IllegalArgumentException("Receiver index "+idReceiver+" is out of bounds. Total receivers: "+scene.countReceivers());
                }
                // Resolve receiver attributes used for ray tracing
                long receiverPk = scene.getReceiverPkByIndex(idReceiver);
                double receiverRelativeHeight = scene.getReceiverRelativeHeightByPk(receiverPk);
                Coordinate receiverCoord = scene.receivers.get(idReceiver);

                // Skip invalid receiver heights
                if(receiverRelativeHeight<0){
                    LOGGER.warn(String.format("Receiver with PK %d (%.2f, %.2f, %.2f) has non-positive relative height of %.2f. Skipping computation for this receiver.", receiverPk, receiverCoord.getX(), receiverCoord.getY(), receiverCoord.getZ(), receiverRelativeHeight));
                    
                    continue;
                }
                // Build receiver info object for computation
                ReceiverPointInfo rcv = new ReceiverPointInfo(idReceiver, receiverPk, receiverCoord);

                // Compute all ray paths from this receiver
                pathFinder.computeRaysAtPosition(rcv, cutPlaneVisitor, progressVisitor);

                // Mark progress step complete
                if (progressVisitor != null) {
                    progressVisitor.endStep();
                }
            }
        } catch (Exception ex) {
            // Log and propagate error, canceling progress if needed
            LOGGER.error(ex.getLocalizedMessage(), ex);
            if (progressVisitor != null) {
                progressVisitor.cancel();
            }
            throw ex;
        }
        return true;
    }
}