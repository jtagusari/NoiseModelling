package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import static org.junit.jupiter.api.Assertions.*;

class ElevationConverterTest {

    private ElevationConverter elevationConverter;
    private TestProfileBuilder testProfileBuilder;
    private TestScene testScene;
    private GeometryFactory geometryFactory;

    /**
     * Test implementation of ProfileBuilder for testing purposes.
     */
    private static class TestProfileBuilder extends ProfileBuilder {
        private double groundElevation = 100.0;
        private TestBridge testBridge;
        private TestBridge testBridgeAbove;

        public TestProfileBuilder() {
            super();
            this.testBridge = new TestBridge();
            this.testBridgeAbove = new TestBridge();
        }

        @Override
        public double getZGround(Coordinate coordinate) {
            return groundElevation;
        }

        @Override
        public Bridge getBridgeByPk(long pk) {
            if (pk == -1L) {
                return null;
            }
            if (pk == 200L) {
                // Return bridge above for PK 200L
                return testBridgeAbove;
            }
            if (pk == 100L) {
                // Return default bridge for PK 100L
                return testBridge;
            }
            // Return null for any other PKs (e.g., 999L)
            return null;
        }

        public void setGroundElevation(double elevation) {
            this.groundElevation = elevation;
        }

        public void setBridgeDeckHeight(double deckHeight) {
            testBridge.setDeckHeight(deckHeight);
        }

        public void setBridgeDeckThickness(double deckThickness) {
            testBridge.setDeckThickness(deckThickness);
        }

        public void setBridgeAboveDeckHeight(double deckHeight) {
            testBridgeAbove.setDeckHeight(deckHeight);
        }

        public void setBridgeAboveDeckThickness(double deckThickness) {
            testBridgeAbove.setDeckThickness(deckThickness);
        }

        public void setInvalidBridgeDeckHeight() {
            testBridge.setDeckHeight(Double.NaN);
        }

        public void setInvalidBridgeDeckThickness() {
            testBridge.setDeckThickness(Double.NaN);
        }

        public void setInvalidBridgeAboveDeckHeight() {
            testBridgeAbove.setDeckHeight(Double.NaN);
        }

        public void setInvalidBridgeAboveDeckThickness() {
            testBridgeAbove.setDeckThickness(Double.NaN);
        }
    }

    /**
     * Test implementation of Bridge for testing purposes.
     */
    private static class TestBridge extends Bridge {
        private double deckHeight = 110.0;
        private double deckThickness = 0.5;

        public TestBridge() {
            super(new java.util.ArrayList<>(), null, 1L);
        }

        @Override
        public double getDeckHeightAtPoint(Coordinate point) {
            return deckHeight;
        }

        @Override
        public double getDeckThicknessAtPoint(Coordinate point) {
            return deckThickness;
        }

        public void setDeckHeight(double deckHeight) {
            this.deckHeight = deckHeight;
        }

        public void setDeckThickness(double deckThickness) {
            this.deckThickness = deckThickness;
        }
    }

    /**
     * Test implementation of Scene for testing purposes.
     */
    private static class TestScene extends Scene {
        private SourceBridgeProperty sourceBridgeProperty;
        private java.util.List<Long> sourcePks;
        private java.util.List<Geometry> sourceGeometries;

        public TestScene() {
            super();
            this.sourcePks = new java.util.ArrayList<>();
            this.sourceGeometries = new java.util.ArrayList<>();
        }

        public void setTestProfileBuilder(ProfileBuilder profileBuilder) {
            // Set the profileBuilder field inherited from Scene
            this.profileBuilder = profileBuilder;
        }

        @Override
        public SourceBridgeProperty getSourceBridgePropertyByPk(long pk) {
            return sourceBridgeProperty;
        }

        @Override
        public java.util.List<Long> getSourcePks() {
            return sourcePks;
        }

        @Override
        public Geometry getSourceGeometryByIndex(int index) {
            if (index >= 0 && index < sourceGeometries.size()) {
                return sourceGeometries.get(index);
            }
            return null;
        }

        @Override
        public java.util.List<Geometry> getSourceGeometries() {
            return sourceGeometries;
        }

        public void setSourceBridgeProperty(SourceBridgeProperty property) {
            this.sourceBridgeProperty = property;
        }

        public void addTestSource(long pk, Geometry geometry) {
            sourcePks.add(pk);
            sourceGeometries.add(geometry);
        }
    }

    @BeforeEach
    void setUp() {
        testProfileBuilder = new TestProfileBuilder();
        testScene = new TestScene();
        testScene.setTestProfileBuilder(testProfileBuilder);
        geometryFactory = new GeometryFactory();
        
        elevationConverter = new ElevationConverter(testScene);
    }

    @Test
    @DisplayName("changeSourceGeometries with SOURCE_NOT_RELATED_TO_BRIDGE should use ground elevation + 0.05m")
    void testChangeSourceGeometriesNotRelatedToBridge() {
        // Arrange
        long sourcePk = 1L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setGroundElevation(100.0);

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Check that Z coordinates are updated to ground + 0.05
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(100.05, updatedCoords[0].getZ(), 0.001);
        assertEquals(100.05, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with ACTUAL_SOURCE_ON_BRIDGE should use bridge deck height + 0.05m")
    void testChangeSourceGeometriesActualSourceOnBridge() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 100L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setBridgeDeckHeight(110.0);

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Check that Z coordinates are updated to deck height + 0.05
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(110.05, updatedCoords[0].getZ(), 0.001);
        assertEquals(110.05, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with IMAGINARY_SOURCE_UNDER_BRIDGE should use deck height - deck thickness")
    void testChangeSourceGeometriesImaginarySourceUnderBridge() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 100L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setBridgeDeckHeight(110.0);
        testProfileBuilder.setBridgeDeckThickness(0.5);

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Check that Z coordinates are updated to deck height - deck thickness
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(109.5, updatedCoords[0].getZ(), 0.001); // 110.0 - 0.5
        assertEquals(109.5, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with MIRROR_SOURCE should use complex reflection formula")
    void testChangeSourceGeometriesMirrorSource() {
        // Arrange
        long sourcePk = 1L;
        long bridgePkOn = 100L;
        long bridgePkAbove = 200L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.MIRROR_SOURCE, bridgePkOn, bridgePkAbove);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        
        // Set up bridge on properties (bridgePkOn = 100L)
        testProfileBuilder.setBridgeDeckHeight(110.0); // deckHeightOn = 110.0
        
        // Set up bridge above properties (bridgePkAbove = 200L)
        testProfileBuilder.setBridgeAboveDeckHeight(120.0); // deckHeightAbove = 120.0
        testProfileBuilder.setBridgeAboveDeckThickness(1.0); // deckThicknessAbove = 1.0
        
        // Expected calculation:
        // deckHeightOn + (deckHeightAbove - deckThicknessAbove - deckHeightOn) * 2
        // = 110.0 + (120.0 - 1.0 - 110.0) * 2
        // = 110.0 + 9.0 * 2 = 110.0 + 18.0 = 128.0

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Check that Z coordinates are updated using mirror source formula
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(128.0, updatedCoords[0].getZ(), 0.001);
        assertEquals(128.0, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with MIRROR_SOURCE and negative bridgePkOn should use DEM")
    void testChangeSourceGeometriesMirrorSourceWithDEM() {
        // Arrange
        long sourcePk = 1L;
        long bridgePkOn = -1L; // Negative, should use DEM
        long bridgePkAbove = 200L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.MIRROR_SOURCE, bridgePkOn, bridgePkAbove);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        
        // Set up ground elevation (used as deckHeightOn when bridgePkOn < 0)
        testProfileBuilder.setGroundElevation(105.0); // deckHeightOn = 105.0 (from DEM)
        
        // Set up bridge above properties (bridgePkAbove = 200L)
        testProfileBuilder.setBridgeAboveDeckHeight(120.0); // deckHeightAbove = 120.0
        testProfileBuilder.setBridgeAboveDeckThickness(1.0); // deckThicknessAbove = 1.0
        
        // Expected calculation:
        // deckHeightOn + (deckHeightAbove - deckThicknessAbove - deckHeightOn) * 2
        // = 105.0 + (120.0 - 1.0 - 105.0) * 2
        // = 105.0 + 14.0 * 2 = 105.0 + 28.0 = 133.0

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Check that Z coordinates are updated using mirror source formula with DEM
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(133.0, updatedCoords[0].getZ(), 0.001);
        assertEquals(133.0, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with MIRROR_SOURCE should throw exception for negative bridgePkAbove")
    void testChangeSourceGeometriesMirrorSourceNegativeBridgePkAbove() {
        // Arrange
        long sourcePk = 1L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.MIRROR_SOURCE, -1L, -1L); // Negative bridgePkAbove

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);

        // Act & Assert - should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Invalid bridgePkAbove value: -1"));
        assertTrue(exception.getMessage().contains("Mirror source requires a valid bridge above"));
    }

    @Test
    @DisplayName("changeSourceGeometries with MIRROR_SOURCE should throw exception when bridge above is null")
    void testChangeSourceGeometriesMirrorSourceNullBridgeAbove() {
        // Arrange
        long sourcePk = 1L;
        long bridgePkAbove = 999L; // Non-existent bridge PK
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.MIRROR_SOURCE, -1L, bridgePkAbove);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Bridge above not found for PK: 999"));
        assertTrue(exception.getMessage().contains("Mirror source calculation requires valid bridge above"));
    }

    @Test
    @DisplayName("changeSourceGeometries with MIRROR_SOURCE should throw exception when bridgeOn is null")
    void testChangeSourceGeometriesMirrorSourceNullBridgeOn() {
        // Arrange
        long sourcePk = 1L;
        long bridgePkOn = 999L; // Non-existent bridge PK
        long bridgePkAbove = 200L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.MIRROR_SOURCE, bridgePkOn, bridgePkAbove);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        
        // Set up bridge above properties (bridgePkAbove = 200L)
        testProfileBuilder.setBridgeAboveDeckHeight(120.0);
        testProfileBuilder.setBridgeAboveDeckThickness(1.0);

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Bridge on not found for PK: 999"));
        assertTrue(exception.getMessage().contains("Mirror source calculation requires valid bridge on"));
    }

    @Test
    @DisplayName("changeSourceGeometries with MIRROR_SOURCE should handle edge case with zero differences")
    void testChangeSourceGeometriesMirrorSourceZeroDifference() {
        // Arrange
        long sourcePk = 1L;
        long bridgePkOn = 100L;
        long bridgePkAbove = 200L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.MIRROR_SOURCE, bridgePkOn, bridgePkAbove);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        
        // Set up identical heights to create zero difference
        testProfileBuilder.setBridgeDeckHeight(110.0); // deckHeightOn = 110.0
        testProfileBuilder.setBridgeAboveDeckHeight(111.0); // deckHeightAbove = 111.0
        testProfileBuilder.setBridgeAboveDeckThickness(1.0); // deckThicknessAbove = 1.0
        
        // Expected calculation:
        // deckHeightOn + (deckHeightAbove - deckThicknessAbove - deckHeightOn) * 2
        // = 110.0 + (111.0 - 1.0 - 110.0) * 2
        // = 110.0 + 0.0 * 2 = 110.0

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(110.0, updatedCoords[0].getZ(), 0.001);
        assertEquals(110.0, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with ACTUAL_SOURCE_ON_BRIDGE should throw exception for null bridge")
    void testChangeSourceGeometriesActualSourceOnBridgeNullBridge() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 999L; // Non-existent bridge PK
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Bridge not found for PK: 999"));
        assertTrue(exception.getMessage().contains("Bridge on elevation calculation requires valid bridge"));
    }

    @Test
    @DisplayName("changeSourceGeometries with IMAGINARY_SOURCE_UNDER_BRIDGE should throw exception for null bridge")
    void testChangeSourceGeometriesImaginarySourceUnderBridgeNullBridge() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 999L; // Non-existent bridge PK
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Bridge not found for PK: 999"));
        assertTrue(exception.getMessage().contains("Bridge under elevation calculation requires valid bridge"));
    }

    @Test
    @DisplayName("changeSourceGeometries with ACTUAL_SOURCE_ON_BRIDGE should throw exception for NaN deck height")
    void testChangeSourceGeometriesActualSourceOnBridgeNaNDeckHeight() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 100L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setInvalidBridgeDeckHeight(); // Set NaN value

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Cannot get bridge deck height at coordinate"));
        assertTrue(exception.getMessage().contains("Bridge PK: 100"));
        assertTrue(exception.getMessage().contains("deckHeight: NaN"));
    }

    @Test
    @DisplayName("changeSourceGeometries with IMAGINARY_SOURCE_UNDER_BRIDGE should throw exception for NaN values")
    void testChangeSourceGeometriesImaginarySourceUnderBridgeNaNValues() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 100L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setBridgeDeckHeight(110.0); // Valid height
        testProfileBuilder.setInvalidBridgeDeckThickness(); // Set NaN thickness

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Cannot get bridge properties at coordinate"));
        assertTrue(exception.getMessage().contains("Bridge PK: 100"));
        assertTrue(exception.getMessage().contains("deckThickness: NaN"));
    }

    @Test
    @DisplayName("changeSourceGeometries should throw exception for null bridge property")
    void testChangeSourceGeometriesNullBridgeProperty() {
        // Arrange
        long sourcePk = 1L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);

        testScene.setSourceBridgeProperty(null);
        testScene.addTestSource(sourcePk, sourceGeometry);

        // Act & Assert - should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("No bridge property found"));
        assertTrue(exception.getMessage().contains("source PK: " + sourcePk));
    }

    @Test
    @DisplayName("changeSourceGeometries should throw exception for non-existent source PK")
    void testChangeSourceGeometriesNonExistentPk() {
        // Arrange
        long sourcePk = 1L;
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        // Don't add the source to the scene

        // Act & Assert - should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Source with PK " + sourcePk + " not found"));
        assertTrue(exception.getMessage().contains("Available source PKs:"));
    }

    @Test
    @DisplayName("changeSourceGeometries should throw exception for null source geometry")
    void testChangeSourceGeometriesNullSourceGeometry() {
        // Arrange
        long sourcePk = 1L;
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, null); // Add null geometry

        // Act & Assert - should throw IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> elevationConverter.changeSourceGeometries(sourcePk));
        
        assertTrue(exception.getMessage().contains("Source geometry is null"));
        assertTrue(exception.getMessage().contains("source PK: " + sourcePk));
        assertTrue(exception.getMessage().contains("Scene may be in an inconsistent state"));
    }

    @Test
    @DisplayName("changeSourceGeometries should handle MultiLineString geometry correctly")
    void testChangeSourceGeometriesWithMultiLineString() {
        // Arrange
        long sourcePk = 1L;
        LineString line1 = geometryFactory.createLineString(new Coordinate[]{
            new Coordinate(0, 0, 1.0), new Coordinate(5, 0, 1.5)
        });
        LineString line2 = geometryFactory.createLineString(new Coordinate[]{
            new Coordinate(10, 0, 2.0), new Coordinate(15, 0, 2.5)
        });
        MultiLineString multiLineString = geometryFactory.createMultiLineString(new LineString[]{line1, line2});
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, multiLineString);
        testProfileBuilder.setGroundElevation(100.0);

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        MultiLineString updatedGeometry = (MultiLineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Check first line
        LineString updatedLine1 = (LineString) updatedGeometry.getGeometryN(0);
        assertEquals(100.05, updatedLine1.getCoordinateN(0).getZ(), 0.001);
        assertEquals(100.05, updatedLine1.getCoordinateN(1).getZ(), 0.001);
        
        // Check second line
        LineString updatedLine2 = (LineString) updatedGeometry.getGeometryN(1);
        assertEquals(100.05, updatedLine2.getCoordinateN(0).getZ(), 0.001);
        assertEquals(100.05, updatedLine2.getCoordinateN(1).getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries should handle minimal LineString (2 coordinates)")
    void testChangeSourceGeometriesMinimalLineString() {
        // Arrange
        long sourcePk = 1L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(0, 0, 1.0)}; // Minimum 2 coords
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setGroundElevation(100.0);

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        assertEquals(100.05, updatedGeometry.getCoordinateN(0).getZ(), 0.001);
        assertEquals(100.05, updatedGeometry.getCoordinateN(1).getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries should preserve XY coordinates while updating Z")
    void testChangeSourceGeometriesPreservesXY() {
        // Arrange
        long sourcePk = 1L;
        double originalX = 123.456;
        double originalY = 789.012;
        Coordinate[] coords = {
            new Coordinate(originalX, originalY, 1.0), 
            new Coordinate(originalX + 1, originalY + 1, 1.5)
        }; // Need 2 coords for valid LineString
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setGroundElevation(100.0);

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        Coordinate updatedCoord1 = updatedGeometry.getCoordinateN(0);
        Coordinate updatedCoord2 = updatedGeometry.getCoordinateN(1);
        
        assertEquals(originalX, updatedCoord1.getX(), 0.001);
        assertEquals(originalY, updatedCoord1.getY(), 0.001);
        assertEquals(100.05, updatedCoord1.getZ(), 0.001);
        
        assertEquals(originalX + 1, updatedCoord2.getX(), 0.001);
        assertEquals(originalY + 1, updatedCoord2.getY(), 0.001);
        assertEquals(100.05, updatedCoord2.getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with zero deck thickness should handle edge case")
    void testChangeSourceGeometriesZeroDeckThickness() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 100L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setBridgeDeckHeight(110.0);
        testProfileBuilder.setBridgeDeckThickness(0.0); // Zero thickness

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        // Should be deck height - 0 = deck height
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(110.0, updatedCoords[0].getZ(), 0.001);
        assertEquals(110.0, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with very large elevation values should work correctly")
    void testChangeSourceGeometriesLargeElevationValues() {
        // Arrange
        long sourcePk = 1L;
        long bridgePk = 100L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, bridgePk, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setBridgeDeckHeight(8848.86); // Mount Everest height

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(8848.91, updatedCoords[0].getZ(), 0.001); // 8848.86 + 0.05
        assertEquals(8848.91, updatedCoords[1].getZ(), 0.001);
    }

    @Test
    @DisplayName("changeSourceGeometries with negative elevation values should work correctly")
    void testChangeSourceGeometriesNegativeElevationValues() {
        // Arrange
        long sourcePk = 1L;
        Coordinate[] coords = {new Coordinate(0, 0, 1.0), new Coordinate(10, 0, 1.5)};
        LineString sourceGeometry = geometryFactory.createLineString(coords);
        
        SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(
            SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);

        testScene.setSourceBridgeProperty(sourceBridgeProperty);
        testScene.addTestSource(sourcePk, sourceGeometry);
        testProfileBuilder.setGroundElevation(-100.0); // Below sea level

        // Act
        elevationConverter.changeSourceGeometries(sourcePk);

        // Assert
        LineString updatedGeometry = (LineString) testScene.getSourceGeometryByIndex(0);
        assertNotNull(updatedGeometry);
        
        Coordinate[] updatedCoords = updatedGeometry.getCoordinates();
        assertEquals(-99.95, updatedCoords[0].getZ(), 0.001); // -100.0 + 0.05
        assertEquals(-99.95, updatedCoords[1].getZ(), 0.001);
    }
}
