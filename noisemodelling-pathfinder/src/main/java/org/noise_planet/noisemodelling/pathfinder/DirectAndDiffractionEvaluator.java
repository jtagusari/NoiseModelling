package org.noise_planet.noisemodelling.pathfinder;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates direct-path processing extracted from PathFinder.
 *
 * Single responsibility: evaluate the direct path and immediate diffraction
 * alternatives between a source sample and a receiver. It builds the direct
 * CutProfile and delegates extended diffraction handling to the
 * {DiffractionProcessor} when needed. It must not perform source
 * sampling or mirror-searching.
 */
public final class DirectAndDiffractionEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectAndDiffractionEvaluator.class);
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private DirectAndDiffractionEvaluator() {}

    /**
     * Compute the direct acoustic propagation path and optionally evaluate diffraction paths
     * between a source point and a receiver point.
     * 
     * <p>This method performs the core acoustic path calculation by:
     * <ul>
     *   <li>Building the direct propagation profile between source and receiver</li>
     *   <li>Setting source-specific information (ID, line length, orientation, primary key)</li>
     *   <li>Setting receiver-specific information (ID, primary key)</li>
     *   <li>Evaluating vertical diffraction when enabled or in free field conditions</li>
     *   <li>Computing horizontal diffraction paths (left and right) when enabled and obstacles are present</li>
     * </ul>
     * 
     * <p>The method delegates the actual profile building to the Scene's getProfile method,
     * which handles topography, building intersections, and ground effects. It then enriches
     * the profile with source and receiver metadata before passing it to the visitor for processing.
     * 
     * <p>Diffraction handling:
     * <ul>
     *   <li><strong>Vertical diffraction</strong>: Processed when explicitly enabled or when the direct path is unobstructed</li>
     *   <li><strong>Horizontal diffraction</strong>: Only computed when enabled and obstacles are present, evaluating both left and right edge diffraction paths</li>
     * </ul>
     * 
     * <p>The method respects early termination strategies returned by the visitor, allowing
     * efficient processing when certain conditions are met (e.g., skip source or receiver).
     * 
     * @param src the source point information containing coordinates, source index, line length, 
     *            orientation, and other source-specific data
     * @param rcv the receiver point information containing coordinates, receiver ID, and primary key
     * @param verticalDiffraction if true, enables vertical diffraction evaluation over obstacles
     * @param horizontalDiffraction if true, enables horizontal diffraction evaluation around obstacles
     * @param cutPlaneVisitor the visitor that processes each computed cut profile and determines continuation strategy
     * @param scene the scene scene containing geometric information, material properties, and computational services
     * @return the path search strategy indicating how to continue processing (CONTINUE, SKIP_SOURCE, or SKIP_RECEIVER)
     * 
     * @throws IllegalArgumentException if source or receiver information is invalid
     * @throws RuntimeException if profile computation fails due to geometric or computational errors
     * 
     * @see CutProfile for details on acoustic path representation
     * @see DiffractionPathBuilder#computeVEdgeDiffraction for horizontal diffraction computation
     * @see CutPlaneVisitor.PathSearchStrategy for processing control flow
     */
    public static CutPlaneVisitor.PathSearchStrategy computeDirectPath(SourcePointInfo src, ReceiverPointInfo rcv, boolean verticalDiffraction, boolean horizontalDiffraction, CutPlaneVisitor cutPlaneVisitor, org.noise_planet.noisemodelling.pathfinder.path.Scene scene) {

        // Initialize with default continuation strategy
        CutPlaneVisitor.PathSearchStrategy strategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;

        // Build the direct acoustic propagation profile between source and receiver
        // This includes topography, building intersections, ground effects, and material properties
        CutProfile cutProfile = scene.getProfile(src.getCoordinate(), rcv.getCoordinate(), scene.getDefaultGroundAttenuation(), !verticalDiffraction, src);
        
        // Enrich source point with metadata if profile was successfully created
        if(cutProfile.getSource() != null) {
            cutProfile.setSource(cutProfile.getSource().migrateFromSourcePointInfo(src));
        }

        // Enrich receiver point with metadata if profile was successfully created
        if(cutProfile.getReceiver() != null) {
            cutProfile.setReceiver(cutProfile.getReceiver().migrateFromReceiverPointInfo(rcv));
        }

        // Process vertical diffraction or free field direct path
        // Vertical diffraction is evaluated when explicitly enabled or when no obstacles block the direct path
        if(verticalDiffraction || cutProfile.isFreeField()) {
            strategy = cutPlaneVisitor.onNewCutPlane(cutProfile);
            // Check for early termination strategies
            if(strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE) ||
                    strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER)) {
                return strategy;
            }
        }

        // Process horizontal diffraction around obstacles
        // Only computed when obstacles are present and horizontal diffraction is enabled
        if (horizontalDiffraction && !cutProfile.isFreeField()) {
            LOGGER.debug("Starting horizontal diffraction computation for src={}, rcv={}", src.getCoordinate(), rcv.getCoordinate());
            long diffStart = System.currentTimeMillis();
            
            // Compute right-side edge diffraction path
            LOGGER.debug("Computing RIGHT side diffraction");
            long rightStart = System.currentTimeMillis();
            CutProfile cutProfileRight = DiffractionPathBuilder.computeVEdgeDiffraction(rcv, src, scene, PathFinder.ComputationSide.RIGHT);
            long rightTime = System.currentTimeMillis() - rightStart;
            LOGGER.debug("RIGHT side diffraction completed in {}ms, result: {}", rightTime, cutProfileRight != null ? "success" : "null");
            
            if (cutProfileRight != null) {
                strategy = cutPlaneVisitor.onNewCutPlane(cutProfileRight);
                // Check for early termination after right diffraction
                if(strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE) ||
                        strategy.equals(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER)) {
                    return strategy;
                }
            }
            
            // Compute left-side edge diffraction path
            LOGGER.debug("Computing LEFT side diffraction");
            long leftStart = System.currentTimeMillis();
            CutProfile cutProfileLeft = DiffractionPathBuilder.computeVEdgeDiffraction(rcv, src, scene, PathFinder.ComputationSide.LEFT);
            long leftTime = System.currentTimeMillis() - leftStart;
            LOGGER.debug("LEFT side diffraction completed in {}ms, result: {}", leftTime, cutProfileLeft != null ? "success" : "null");
            
            if (cutProfileLeft != null) {
                strategy = cutPlaneVisitor.onNewCutPlane(cutProfileLeft);
            }
            
            long totalDiffTime = System.currentTimeMillis() - diffStart;
            LOGGER.debug("Horizontal diffraction computation completed in {}ms total", totalDiffTime);
        }

        return strategy;
    }
}
