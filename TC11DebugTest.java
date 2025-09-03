import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilderDecorator;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.DefaultCutPlaneVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Debug test for TC11 with detailed logging
 */
public class TC11DebugTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TC11DebugTest.class);

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS) // 10秒のタイムアウト
    public void debugTC11Performance() throws Exception {
        LOGGER.info("=== Starting TC11 Debug Test ===");
        long startTime = System.currentTimeMillis();
        
        //Profile building
        LOGGER.info("Building profile...");
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBuilding(new Coordinate[]{
                        new Coordinate(55, 5, 10),
                        new Coordinate(65, 5, 10),
                        new Coordinate(65, 15, 10),
                        new Coordinate(55, 15, 10),
                });
        profileBuilder.addGroundEffect(0.0, 100.0, 0.0, 100.0, 0.5);

        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();

        //Propagation data building
        LOGGER.info("Creating scene with high receiver position...");
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(50, 10, 1)      // Source at 1m height
                .addReceiver(70, 10, 15)   // Receiver at 15m height (high above 10m building)
                .hEdgeDiff(true)           // Enable horizontal diffraction
                .vEdgeDiff(true)           // Enable vertical diffraction
                .setGs(0.5)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        LOGGER.info("Starting ray computation...");
        
        // Run computation
        computeRays.run(propDataOut);

        long elapsedTime = System.currentTimeMillis() - startTime;
        LOGGER.info("=== TC11 Debug Test completed in {}ms ===", elapsedTime);
        LOGGER.info("Cut profiles generated: {}", propDataOut.getCutProfiles().size());
        
        // Print summary
        System.out.println("\n=== DEBUG TEST SUMMARY ===");
        System.out.println("Total execution time: " + elapsedTime + "ms");
        System.out.println("Cut profiles generated: " + propDataOut.getCutProfiles().size());
        System.out.println("Check target/diffraction-debug.log for detailed logs");
        System.out.println("=============================\n");
    }
}
