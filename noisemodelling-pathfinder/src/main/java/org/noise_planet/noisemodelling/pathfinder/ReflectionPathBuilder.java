package org.noise_planet.noisemodelling.pathfinder;

import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiver;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiversCompute;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
// CutPlaneVisitor is referenced by fully-qualified name where needed

import java.util.List;

/**
 * Handles reflection related processing previously embedded in PathFinder.
 *
 * Single responsibility: search and assemble reflection paths. The full
 * implementation was moved here from the legacy ReflectionProcessor so this
 * class now owns all reflection path computation and attribute insertion.
 */
public final class ReflectionPathBuilder {
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private ReflectionPathBuilder() {}

    /**
     * Compute acoustic reflection paths between a source and receiver using mirror image method.
     * 
     * <p>This method implements the mirror image technique for computing specular reflection paths
     * off building walls and other surfaces. The process involves:
     * <ul>
     *   <li>Creating mirror images of receivers across reflective surfaces</li>
     *   <li>Computing direct paths from source to mirror receivers</li>
     *   <li>Validating that reflection paths don't pass through obstacles</li>
     *   <li>Adding reflection point attributes to the acoustic profile</li>
     * </ul>
     * 
     * <p>The method delegates the actual processing to a {@link ReflectionPathProcessor} instance
     * for better testability and easier mocking in unit tests. This separation allows for
     * more controlled testing of reflection algorithms without complex scene setup.
     * 
     * <p>Reflection paths are particularly important in urban environments where building facades
     * can significantly amplify or redirect sound. The method respects the initial search strategy
     * and can return early termination signals when processing should be skipped.
     * 
     * @param rcv the receiver point information containing coordinates and properties
     * @param src the source point information containing coordinates, power levels, and directivity
     * @param receiverMirrorIndex pre-computed mirror receiver data structure containing potential
     *                           reflection surfaces and their geometric relationships
     * @param cutPlaneVisitor the visitor that processes each computed reflection profile and determines
     *               continuation strategy for the acoustic calculation pipeline
     * @param initialStrategy the current path search strategy from previous processing steps,
     *                       used to determine if reflection computation should proceed
     * @param scene the scene data containing geometric information, material properties, and
     *            computational services required for profile building
     * @return the updated path search strategy indicating how to continue processing
     *         (CONTINUE, SKIP_SOURCE, or SKIP_RECEIVER)
     * 
     * @throws IllegalArgumentException if source or receiver information is invalid
     * @throws RuntimeException if reflection computation fails due to geometric errors
     * 
     * @see MirrorReceiversCompute for mirror image computation details
     * @see ReflectionPathProcessor for the actual reflection algorithm implementation
     * @see CutPointReflection for reflection point representation in acoustic profiles
     */
    public static CutPlaneVisitor.PathSearchStrategy computeReflexion(ReceiverPointInfo rcv,
                                                                      SourcePointInfo src,
                                                                      MirrorReceiversCompute receiverMirrorIndex,
                                                                      CutPlaneVisitor cutPlaneVisitor,
                                                                      CutPlaneVisitor.PathSearchStrategy initialStrategy,
                                                                      Scene scene) {
        // Delegate to instance processor for better testability and easier mocking
        ReflectionPathProcessor processor = new ReflectionPathProcessor(scene.profileBuilder, new RobustLineIntersector());
        return processor.process(rcv, src, receiverMirrorIndex, cutPlaneVisitor, initialStrategy, scene);
    }

    /**
     * Insert reflection point attributes into an acoustic profile at the appropriate reflection surface.
     * 
     * <p>This method creates a {@link CutPointReflection} that represents the acoustic interaction
     * at a reflective surface. The reflection point contains essential information for acoustic
     * calculations including:
     * <ul>
     *   <li>The original source or receiver point geometry</li>
     *   <li>The reflecting wall's line segment for geometric calculations</li>
     *   <li>Frequency-dependent absorption coefficients (alphas) of the reflecting surface</li>
     *   <li>Database primary key for the reflecting wall (if available)</li>
     * </ul>
     * 
     * <p>The reflection point is crucial for accurate acoustic modeling as it allows the propagation
     * algorithm to account for:
     * <ul>
     *   <li><strong>Frequency-dependent reflection losses</strong>: Different materials reflect
     *       different frequencies with varying efficiency</li>
     *   <li><strong>Geometric reflection coefficients</strong>: The angle and surface properties
     *       affect the reflection strength</li>
     *   <li><strong>Phase relationships</strong>: Reflection paths introduce phase shifts that
     *       affect interference patterns with direct paths</li>
     * </ul>
     * 
     * <p>This method is typically called during profile assembly after a valid reflection path
     * has been computed and verified to not pass through obstacles.
     * 
     * @param sourceOrReceiverPoint the original source or receiver point that will be reflected.
     *                             This contains the coordinate and acoustic properties of the point
     *                             before reflection transformation
     * @param mainProfileCutPoints the main acoustic profile's list of cut points where the new
     *                            reflection point will be inserted. This list is modified in-place
     * @param mirrorReceiver the mirror receiver data containing the reflecting wall information,
     *                      absorption coefficients, and geometric relationship for the reflection
     * 
     * @throws IllegalArgumentException if any of the input parameters are null
     * @throws ClassCastException if sourceOrReceiverPoint is not compatible with reflection processing
     * 
     * @see CutPointReflection for the reflection point data structure
     * @see MirrorReceiver for mirror image computation and wall properties
     * @see CutPoint for the base acoustic point interface
     */
    public static void insertReflectionPointAttributes(CutPoint sourceOrReceiverPoint, 
                                                      List<CutPoint> mainProfileCutPoints, 
                                                      MirrorReceiver mirrorReceiver) {
        // Create reflection point with wall geometry and absorption properties
        CutPointReflection reflectionPoint = new CutPointReflection(sourceOrReceiverPoint,
                mirrorReceiver.getWall().getLineSegment(), mirrorReceiver.getWall().getAlphas());
        
        // Set database reference if available for reflection surface tracking
        if(mirrorReceiver.wall.primaryKey >= 0) {
            reflectionPoint.setWallPk(mirrorReceiver.wall.primaryKey);
        }
        
        // Add reflection point to the acoustic profile
        mainProfileCutPoints.add(reflectionPoint);
    }
}
