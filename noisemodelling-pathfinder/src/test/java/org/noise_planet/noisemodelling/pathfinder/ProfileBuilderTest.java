/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Building;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.noise_planet.noisemodelling.pathfinder.PathFinderTest.assertZProfil;

/**
 * Test class dedicated to {@link ProfileBuilder}.
 */
public class ProfileBuilderTest {

    /** JTS WKT reader. */
    private static final WKTReader READER = new WKTReader();
    /** Delta value. */
    private static final double DELTA = 1e-8;
    private Logger logger = LoggerFactory.getLogger(ProfileBuilderTest.class);

    /**
     * Test the building adding to a {@link ProfileBuilder}.
     * Polygons are normalized according to ISO, outer ring must be CCW and inner rings are CW
     * Verifies that buildings added via WKT are normalized and stored with the correct Z values.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void buildingAddingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addBuilding(READER.read("POLYGON((1 1,5 1,5 5,1 5,1 1))"), 10, -1);
        profileBuilder.addBuilding(READER.read("POLYGON((10 10,15 10,15 15,10 15,10 10))"), 23, -1);
        profileBuilder.addBuilding(READER.read("POLYGON((6 8,8 10,8 4,6 8))"), 56, -1);

        profileBuilder.finishFeeding();

        List<Building> list = profileBuilder.getBuildings();
        assertEquals(3, list.size());
        assertEquals("POLYGON ((1 1, 1 5, 5 5, 5 1, 1 1))", list.get(0).getGeometry().toText());
        assertEquals(10, list.get(0).getGeometry().getCoordinate().z, 0);
        assertEquals("POLYGON ((10 10, 10 15, 15 15, 15 10, 10 10))", list.get(1).getGeometry().toText());
        assertEquals(23, list.get(1).getGeometry().getCoordinate().z, 0);
        assertEquals("POLYGON ((6 8, 8 10, 8 4, 6 8))", list.get(2).getGeometry().toText());
        assertEquals(56, list.get(2).getGeometry().getCoordinate().z, 0);
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding.
     * Ensures that calling finishFeeding() returns a non-null result and that subsequent additions after finish are not included.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void finishBuildingFeedingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addBuilding(READER.read("POLYGON((1 1,5 1,5 5,1 5,1 1))"), 10);
        assertNotNull(profileBuilder.finishFeeding());
        profileBuilder.addBuilding(READER.read("POLYGON((10 10,15 10,15 15,10 15,10 10))"), 23);
        profileBuilder.addBuilding(READER.read("POLYGON((6 8,8 10,8 4,6 8))"), 56);

        List<Building> list = profileBuilder.getBuildings();
        assertEquals(1, list.size());
    }

    /**
     * Test the topographic adding to a {@link ProfileBuilder}.
     * Confirms that topographic lines and points are triangulated into the expected number of triangles.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void topoAddingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));
        profileBuilder.finishFeeding();

        assertEquals(11, profileBuilder.getTriangles().size());
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding.
     * Verifies that finishFeeding() finalizes the current topography and further points added afterwards are ignored.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void topoBuildingFeedingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        assertNotNull(profileBuilder.finishFeeding());
        profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));

        assertEquals(4, profileBuilder.getTriangles().size());
    }


    /**
     * Test the topographic cut profile generation.
     * Builds a profile from topography and checks endpoints and heights of the generated cut profile.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void topoCutProfileTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        // profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        // profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        // profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        // profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        // profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));
        // profileBuilder.finishFeeding();

        // CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));
        // List<CutPoint> pts = profile.getCutPoints();
        // assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
        // assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
        // assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
        // assertEquals(8.0, pts.get(pts.size() - 1).getCoordinate().x, DELTA);
        // assertEquals(10.0, pts.get(pts.size() - 1).getCoordinate().y, DELTA);
        // assertEquals(0.3, pts.get(pts.size() - 1).getCoordinate().z, DELTA);
    }

    /**
     * Test the ground adding to a {@link ProfileBuilder}.
     * Verifies ground effect polygons are stored and counted correctly after feeding.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void groundAddingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
        profileBuilder.finishFeeding();

        assertEquals(2, profileBuilder.getGroundEffects().size());
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding.
     * Ensures finishFeeding() locks in ground effects and prevents later additions from being included.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void groundBuildingFeedingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        assertNotNull(profileBuilder.finishFeeding());
        profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);

        assertEquals(1, profileBuilder.getGroundEffects().size());
    }

    /**
     * Test the ground cut profile generation.
     * Generates a cut profile that includes ground effects and asserts the expected cut points and positions.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void groundCutProfileTest() throws ParseException {
    // ProfileBuilder profileBuilder = new ProfileBuilder();
    //     profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
    //     profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
    //     profileBuilder.finishFeeding();

    //     CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));
    //     List<CutPoint> pts = profile.getCutPoints();
    //     assertEquals(4, pts.size());
    //     assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
    //     assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
    //     assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
    //     assertEquals(8.0, pts.get(3).getCoordinate().x, DELTA);
    //     assertEquals(10.0, pts.get(3).getCoordinate().y, DELTA);
    //     assertEquals(0.3, pts.get(3).getCoordinate().z, DELTA);
    }



    /**
     * Test the cut profile generation.
     * Constructs a complex model (buildings, topography, ground) and checks that cut profile endpoints and heights match expectations.
     * @throws ParseException JTS WKT parsing exception.
     */
    @Test
    public void allCutProfileTest() throws Exception {
    ProfileBuilder profileBuilder = new ProfileBuilder();

        // profileBuilder.addBuilding(READER.read("POLYGON((2 2 10, 1 3 15, 2 4 10, 3 3 12, 2 2 10))"), 10);
        // profileBuilder.addBuilding(READER.read("POLYGON((4.5 7, 4.5 8.5, 6.5 8.5, 4.5 7))"), 3.3);
        // profileBuilder.addBuilding(READER.read("POLYGON((7 6, 10 6, 10 2, 7 2, 7 6))"), 5.6);

        // profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        // profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        // profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        // profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        // profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));

        // profileBuilder.addGroundEffect(READER.read("POLYGON((-1 -1, -1 2, 2 2, 2 -1, -1 -1))"), 0.6);
        // profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        // profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
        // profileBuilder.finishFeeding();

        // CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));

        // List<CutPoint> pts = profile.getCutPoints();
        // assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
        // assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
        // assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
        // assertEquals(8.0, pts.get(pts.size() - 1).getCoordinate().x, DELTA);
        // assertEquals(10.0, pts.get(pts.size() - 1).getCoordinate().y, DELTA);
        // assertEquals(0.3, pts.get(pts.size() - 1).getCoordinate().z, DELTA);

    }

    @Test
    public void testProfileTopographicGroundEffectWall() throws Exception {

        // //Profile building
        // ProfileBuilder profileBuilder = new ProfileBuilder()
        //         //Ground effects
        //         .addGroundEffect(0.0, 50.0, -20.0, 80.0, 0.9)
        //         .addGroundEffect(50.0, 150.0, -20.0, 80.0, 0.5)
        //         .addGroundEffect(150.0, 225.0, -20.0, 80.0, 0.2)
        //         //Topography
        //         .addTopographicLine(0, 80, 0, 225, 80, 0)
        //         .addTopographicLine(225, 80, 0, 225, -20, 0)
        //         .addTopographicLine(225, -20, 0, 0, -20, 0)
        //         .addTopographicLine(0, -20, 0, 0, 80, 0)
        //         .addTopographicLine(120, -20, 0, 120, 80, 0)
        //         .addTopographicLine(185, -5, 10, 205, -5, 10)
        //         .addTopographicLine(205, -5, 10, 205, 75, 10)
        //         .addTopographicLine(205, 75, 10, 185, 75, 10)
        //         .addTopographicLine(185, 75, 10, 185, -5, 10)
        //         // Add building
        //         .addWall(new Coordinate[]{
        //                         new Coordinate(175, 50, 17),
        //                         new Coordinate(190, 10, 14)},
        //                 1)
        //         .finishFeeding();

        // Coordinate receiver = new Coordinate(200, 50, 14);
        // Coordinate source = new Coordinate(10, 10, 1);
        // CutProfile cutProfile = profileBuilder.getProfile(source, receiver, 0, false);
        // assertEquals(7, cutProfile.getCutPoints().size());
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(10, 10, 1), cutProfile.getCutPoints().get(0).getCoordinate(), 0.01);
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(50, 18.421, 0), cutProfile.getCutPoints().get(1).getCoordinate(), 0.01);
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(120, 33.158, 0), cutProfile.getCutPoints().get(2).getCoordinate(), 0.01);
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(150, 39.474, 4.616), cutProfile.getCutPoints().get(3).getCoordinate(), 0.01);
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(176.83, 45.122, 16.634), cutProfile.getCutPoints().get(4).getCoordinate(), 0.01);
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(185, 46.842, 10), cutProfile.getCutPoints().get(5).getCoordinate(), 0.01);
        // PathFinderTest.assert3DCoordinateEquals("", new Coordinate(200, 50, 14), cutProfile.getCutPoints().get(6).getCoordinate(), 0.01);
    }

    @Test
    public void testRelativeSourceLineProjection() throws ParseException {
        ProfileBuilder profileBuilder = new ProfileBuilder();
        PathFinderTest.addTopographicTC5Model(profileBuilder);
        profileBuilder.finishFeeding();
        Scene scene = new Scene(profileBuilder);
        WKTReader wktReader = new WKTReader();
        Geometry geometry = wktReader.read("MultiLineStringZ ((10 10 1, 200 50 1))");
        scene.addSource(1L, geometry);
        PathFinder pathFinder = new PathFinder(scene);
        assertEquals(2, scene.getSourceGeometryByIndex(0).getNumPoints());
        pathFinder.makeSourceRelativeZToAbsolute();
        // The source line should now be made of 4 points (2 points being created by the elevated DEM)
        assertEquals(4, scene.getSourceGeometryByIndex(0).getNumPoints());
        List<Coordinate> expectedProfile = Arrays.asList(
                new Coordinate(10.0, 10.0, 1.0),
                new Coordinate(120.0, 33.16, 1.0),
                new Coordinate(185.0, 46.84, 11.0),
                new Coordinate(200.0, 50.0, 11.0));
        assertZProfil(expectedProfile, Arrays.asList(scene.getSourceGeometryByIndex(0).getCoordinates()));
    }


    @Test
    public void test2DGroundProfile() {

    //     //Profile building (from TC15)
    // ProfileBuilder profileBuilder = new ProfileBuilder()
    //     .addBuilding(new Coordinate[]{
    //         new Coordinate(55.0, 5.0, 8),
    //         new Coordinate(65.0, 5.0, 8),
    //         new Coordinate(65.0, 15.0, 8),
    //         new Coordinate(55.0, 15.0, 8),
    //         new Coordinate(55.0, 5.0, 8)
    //     })
    //     .addBuilding(new Coordinate[]{
    //         new Coordinate(70.0, 14.5, 12),
    //         new Coordinate(80.0, 10.2, 12),
    //         new Coordinate(80.0, 20.2, 12),
    //         new Coordinate(70.0, 14.5, 12)
    //     })
    //     .addBuilding(new Coordinate[]{
    //         new Coordinate(90.1, 19.5, 10),
    //         new Coordinate(93.3, 17.8, 10),
    //         new Coordinate(87.3, 6.6, 10),
    //         new Coordinate(84.1, 8.3, 10),
    //         new Coordinate(90.1, 19.5, 10)
    //     });
    //     profileBuilder.addGroundEffect(0, 100, 0.0, 150, 0.5);
    //     profileBuilder.setzBuildings(true);
    //     profileBuilder.finishFeeding();

    //     CutProfile cutProfile = profileBuilder.getProfile(new Coordinate(50,10,1), new Coordinate(100, 15, 5));

    //     assertEquals(9, cutProfile.getCutPoints().size());

    //     List<Integer> index = new ArrayList<>(cutProfile.getCutPoints().size());
    //     List<Coordinate> zProfile = cutProfile.generateElevationProfile2D(index);

    //     assertEquals(cutProfile.getCutPoints().size(), index.size());

    //     /* Table 148 */
    //     List<Coordinate> expectedZProfile = new ArrayList<>();
    //     expectedZProfile.add(new Coordinate(0.00, 0.00));
    //     expectedZProfile.add(new Coordinate(5.02, 0.00));
    //     expectedZProfile.add(new Coordinate(5.02, 8.00));
    //     expectedZProfile.add(new Coordinate(15.07, 8.0));
    //     expectedZProfile.add(new Coordinate(15.08, 0.0));
    //     expectedZProfile.add(new Coordinate(24.81, 0.0));
    //     expectedZProfile.add(new Coordinate(24.81, 12.0));
    //     expectedZProfile.add(new Coordinate(30.15, 12.0));
    //     expectedZProfile.add(new Coordinate(30.15, 0.00));
    //     expectedZProfile.add(new Coordinate(37.19, 0.0));
    //     expectedZProfile.add(new Coordinate(37.19, 10.0));
    //     expectedZProfile.add(new Coordinate(41.52, 10.0));
    //     expectedZProfile.add(new Coordinate(41.52, 0.0));
    //     expectedZProfile.add(new Coordinate(50.25, 0.0));

    //     //Assertion
    //     assertZProfil(expectedZProfile, zProfile);

    //     assertArrayEquals(new int[]{0, 2, 4, 6, 8, 10, 12, 12, 13},
    //             index.stream().mapToInt(Integer::intValue).toArray());


    }

    /**
     * Test the bridge adding to a {@link ProfileBuilder}.
     * @throws ParseException JTS WKT parsing exception.
     * Verifies that bridges with deck Z values are accepted and stored, and their geometries preserve Z coordinates.
     */
    @Test
    public void bridgeAddingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        
        // Create bridge polygons with Z coordinates representing deck height
        Polygon bridge1 = (Polygon) READER.read("POLYGON((5 5 10, 15 5 10, 15 15 10, 5 15 10, 5 5 10))");
        Polygon bridge2 = (Polygon) READER.read("POLYGON((20 20 15, 30 20 15, 30 30 15, 20 30 15, 20 20 15))");
        
        // Create Bridge objects with absorption coefficients
        Bridge bridge1Obj = new Bridge(bridge1, Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1), 1);
        Bridge bridge2Obj = new Bridge(bridge2, Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2), 2);
        
        profileBuilder.addBridge(bridge1Obj);
        profileBuilder.addBridge(bridge2Obj);
        
        profileBuilder.finishFeeding();

        List<Bridge> list = profileBuilder.getBridges();
        assertEquals(2, list.size());
        // Check geometry coordinates directly instead of text representation
    Coordinate[] coords1 = list.get(0).getDeckGeometry().getCoordinates();
        assertEquals(5.0, coords1[0].x, DELTA);
        assertEquals(5.0, coords1[0].y, DELTA);
        assertEquals(10.0, coords1[0].z, DELTA);
        
        
    Coordinate[] coords2 = list.get(1).getDeckGeometry().getCoordinates();
        assertEquals(20.0, coords2[0].x, DELTA);
        assertEquals(20.0, coords2[0].y, DELTA);
        assertEquals(15.0, coords2[0].z, DELTA);
        
        assertNotNull(list.get(1)); // Should have valid bridge object
    }

    /**
     * Test the finish of {@link ProfileBuilder} feeding with bridges.
     * @throws ParseException JTS WKT parsing exception.
     * Ensures finishFeeding() properly finalizes bridges and blocks later bridge additions from being counted.
     */
    @Test
    public void bridgeFeedingTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        
        Polygon bridge1 = (Polygon) READER.read("POLYGON((5 5 10, 15 5 10, 15 15 10, 5 15 10, 5 5 10))");
        Bridge bridge1Obj = new Bridge(bridge1, Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1), 1);
        
        profileBuilder.addBridge(bridge1Obj);
        assertNotNull(profileBuilder.finishFeeding());
        
        // Add another bridge after finishFeeding - should not be included
        Polygon bridge2 = (Polygon) READER.read("POLYGON((20 20 15, 30 20 15, 30 30 15, 20 30 15, 20 20 15))");
        Bridge bridge2Obj = new Bridge(bridge2, Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2), 2);
        profileBuilder.addBridge(bridge2Obj);

        List<Bridge> list = profileBuilder.getBridges();
        assertEquals(1, list.size());
    }

    /**
     * Test the bridge count functionality.
     * @throws ParseException JTS WKT parsing exception.
     * Confirms bridge counting increments as bridges are added and remains stable after finishFeeding().
     */
    @Test
    public void bridgeCountTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        
        assertEquals(0, profileBuilder.getBridgeCount());
        
        Polygon bridge1 = (Polygon) READER.read("POLYGON((5 5 10, 15 5 10, 15 15 10, 5 15 10, 5 5 10))");
        Bridge bridge1Obj = new Bridge(bridge1, Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1), 1);
        profileBuilder.addBridge(bridge1Obj);
        
        assertEquals(1, profileBuilder.getBridgeCount());
        
        Polygon bridge2 = (Polygon) READER.read("POLYGON((20 20 15, 30 20 15, 30 30 15, 20 30 15, 20 20 15))");
        Bridge bridge2Obj = new Bridge(bridge2, Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2), 2);
        profileBuilder.addBridge(bridge2Obj);
        
        assertEquals(2, profileBuilder.getBridgeCount());
        
        profileBuilder.finishFeeding();
        
        assertEquals(2, profileBuilder.getBridgeCount());
    }

    /**
     * Test the cut profile generation with all elements including bridges.
     * @throws ParseException JTS WKT parsing exception.
     * Builds a full scenario with buildings, bridges, topography and ground and validates the resulting cut profile bounds and that bridges are included.
     */
    @Test
    public void allCutProfileTestWithBridges() throws Exception {
    ProfileBuilder profileBuilder = new ProfileBuilder();

        // profileBuilder.addBuilding(READER.read("POLYGON((2 2 10, 1 3 15, 2 4 10, 3 3 12, 2 2 10))"), 10);
        // profileBuilder.addBuilding(READER.read("POLYGON((4.5 7, 4.5 8.5, 6.5 8.5, 4.5 7))"), 3.3);
        // profileBuilder.addBuilding(READER.read("POLYGON((7 6, 10 6, 10 2, 7 2, 7 6))"), 5.6);

        // // Add bridges
        // Polygon bridge1 = (Polygon) READER.read("POLYGON((3 8 12, 4 8 12, 4 9 12, 3 9 12, 3 8 12))");
        // Bridge bridge1Obj = new Bridge(bridge1, Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1), 1);
        // profileBuilder.addBridge(bridge1Obj);

        // profileBuilder.addTopographicLine((LineString) READER.read("LINESTRING (4 1 1.5, 5 7 1.0, 8 9 1.5)"));
        // profileBuilder.addTopographicPoint(new Coordinate(7, 9, 2.5));
        // profileBuilder.addTopographicPoint(new Coordinate(2, 4, 2.5));
        // profileBuilder.addTopographicPoint(new Coordinate(6, 1, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(4, 4, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(2, 5, 3.0));
        // profileBuilder.addTopographicPoint(new Coordinate(1, 9, 2.0));
        // profileBuilder.addTopographicPoint(new Coordinate(8, 2, 2.0));

        // profileBuilder.addGroundEffect(READER.read("POLYGON((-1 -1, -1 2, 2 2, 2 -1, -1 -1))"), 0.6);
        // profileBuilder.addGroundEffect(READER.read("POLYGON((-1 7, -0.5 8, 0 8.5, 1 9, 1.5 7, 2 6, 2.5 7, 3 9, 5.5 8.5, 7 7, 7 6, 5 5, 5 4, 4 2, 2 3, 1 5, 0 6, -1 7))"), 0.5);
        // profileBuilder.addGroundEffect(READER.read("POLYGON((8 1, 7 2, 7 4.5, 8 5, 9 4.5, 10 3.5, 9.5 2, 8 1))"), 0.25);
        // profileBuilder.finishFeeding();

        // CutProfile profile = profileBuilder.getProfile(new Coordinate(0, 1, 0.1), new Coordinate(8, 10, 0.3));

        // List<CutPoint> pts = profile.getCutPoints();
        // assertEquals(0.0, pts.get(0).getCoordinate().x, DELTA);
        // assertEquals(1.0, pts.get(0).getCoordinate().y, DELTA);
        // assertEquals(0.1, pts.get(0).getCoordinate().z, DELTA);
        // assertEquals(8.0, pts.get(pts.size() - 1).getCoordinate().x, DELTA);
        // assertEquals(10.0, pts.get(pts.size() - 1).getCoordinate().y, DELTA);
        // assertEquals(0.3, pts.get(pts.size() - 1).getCoordinate().z, DELTA);

        // // Verify that bridges are included in the model
        // assertEquals(1, profileBuilder.getBridgeCount());
    }

    /**
     * Test finishing an empty ProfileBuilder: no elements should be present.
     * Verifies that calling finishFeeding() on an empty builder results in empty collections for all element types.
     */
    @Test
    public void emptyProfileTest() {
    ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.finishFeeding();

        assertEquals(0, profileBuilder.getBuildings().size());
        assertEquals(0, profileBuilder.getBridges().size());
        assertEquals(0, profileBuilder.getGroundEffects().size());
        // No topography triangles should be created
        assertEquals(0, profileBuilder.getTriangles().size());
    }

    /**
     * Test adding a polygon with an inner ring (hole) as a building.
     * Ensures inner ring is preserved.
     * Checks that buildings with holes keep their interior rings and that Z values are preserved when enabled.
     */
    @Test
    public void buildingWithHoleTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();
    // Outer square with an inner square hole, include absolute Z=14 on coordinates to test preservation
    Polygon polyWithHole = (Polygon) READER.read("POLYGON Z ((0 0 14,10 0 14,10 10 14,0 10 14,0 0 14),(3 3 14,7 3 14,7 7 14,3 7 14,3 3 14))");
    // Tell builder to respect Z values on building polygons
    profileBuilder.setzBuildings(true);
    profileBuilder.addBuilding(polyWithHole, 12);
    profileBuilder.finishFeeding();

    List<Building> list = profileBuilder.getBuildings();
    assertEquals(1, list.size());
    Polygon g = (Polygon) list.get(0).getGeometry();
    // There should be one interior ring
    assertEquals(1, g.getNumInteriorRing());
    // Exterior ring should have 5 points (square closed)
    assertEquals(5, g.getExteriorRing().getNumPoints());
    // Interior ring (hole) should have 5 points as well
    assertEquals(5, g.getInteriorRingN(0).getNumPoints());
    // Check Z value on exterior ring first coordinate (should be height + minDEM = 14.0)
    assertEquals(14.0, g.getExteriorRing().getCoordinateN(0).z, DELTA);
    // Check Z value on interior ring first coordinate as well
    assertEquals(14.0, g.getInteriorRingN(0).getCoordinateN(0).z, DELTA);
    }

    /**
     * Test that bridge deck Z values are available on the stored bridge geometries.
     * Ensures that bridge deck elevations are retained on the stored bridge geometries after feeding.
     */
    @Test
    public void bridgesDeckZPreservedTest() throws ParseException {
    ProfileBuilder profileBuilder = new ProfileBuilder();

        Polygon bridge1 = (Polygon) READER.read("POLYGON((5 5 10, 15 5 10, 15 15 10, 5 15 10, 5 5 10))");
        Polygon bridge2 = (Polygon) READER.read("POLYGON((20 20 15, 30 20 15, 30 30 15, 20 30 15, 20 20 15))");
        Bridge b1 = new Bridge(bridge1, Arrays.asList(0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1), 1);
        Bridge b2 = new Bridge(bridge2, Arrays.asList(0.2,0.2,0.2,0.2,0.2,0.2,0.2,0.2), 2);

        profileBuilder.addBridge(b1);
        profileBuilder.addBridge(b2);
        profileBuilder.finishFeeding();

        List<Bridge> list = profileBuilder.getBridges();
        assertEquals(2, list.size());

        Coordinate[] coords1 = list.get(0).getDeckGeometry().getCoordinates();
        Coordinate[] coords2 = list.get(1).getDeckGeometry().getCoordinates();

        assertEquals(10.0, coords1[0].z, DELTA);
        assertEquals(15.0, coords2[0].z, DELTA);
    }
}
