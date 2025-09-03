import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilderDecorator;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.DefaultCutPlaneVisitor;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Simplified test for TC11 to demonstrate and fix the performance issue
 */
public class SimpleTC11Test {

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testTC11WithTimeout() throws Exception {
        System.out.println("Starting TC11 test with timeout...");
        long startTime = System.currentTimeMillis();
        
        //Profile building
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
        Scene rayData = new ProfileBuilderDecorator(profileBuilder)
                .addSource(50, 10, 1)
                .addReceiver(70, 10, 15)  // High receiver position (15m) above building (10m)
                .hEdgeDiff(true)
                .vEdgeDiff(true)
                .setGs(0.5)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(rayData);
        computeRays.setThreadCount(1);

        // Run computation
        computeRays.run(propDataOut);

        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("TC11 test completed in " + elapsedTime + "ms");

        assertEquals(3, propDataOut.getCutProfiles().size());
    }
}
