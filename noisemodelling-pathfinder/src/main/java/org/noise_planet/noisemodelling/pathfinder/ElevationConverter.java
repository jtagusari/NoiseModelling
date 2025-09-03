package org.noise_planet.noisemodelling.pathfinder;

import java.util.ArrayList;
import java.util.List;
    

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
    

/**
 * Utility responsible for converting Z values of geometries and coordinate lists
 * from relative to absolute representations using a {@link ProfileBuilder}.
 *
 * Single responsibility: convert Z values from relative to absolute form. This class
 * transforms elevation (Z) information by adding ground elevation using the
 * provided {@link ProfileBuilder}. It must not perform sampling, visibility
 * checks, pathfinding, or high-level profile assembly.
 *
 * <p>This class treats simple coordinate lists (receivers) differently from complex
 * geometry objects (sources). Coordinate lists are mutated in-place; geometries are
 * rebuilt when necessary (for example, to project a LineString onto the DEM and
 * insert interpolated vertices).
 */
public class ElevationConverter {
    Scene scene;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double OFFSET = 0.05;


    /**
     * Create a converter with a {@link Scene} used to query ground elevation (DEM) 
     * and bridge properties when converting Z values.
     *
     * @param scene the scene containing ProfileBuilder and bridge properties for elevation conversion
     */
    public ElevationConverter(Scene scene) {
        this.scene = scene;
    }
        

    /**
     * Change Z values of a list of {@link Coordinate} objects from relative to absolute
     * by adding ground elevation.
     *
     * <p>The ground elevation at each coordinate is added to the coordinate's Z.
     * The method mutates the provided list in-place.
     *
     * @param coords list of coordinates to modify (mutated in-place)
     */
    public void changeCoordinates(List<Coordinate> coords) {
        for (Coordinate coord : coords) {
            coord.setZ(coord.getZ() + scene.profileBuilder.getZGround(coord));
        }
    }


    /**
     * Convert a list of geometries from relative to absolute coordinates.
     *
     * <p>Because geometry topologies may need to change when Z values are
     * applied (for example, LineStrings may need to be split and interpolated
     * along the DEM), this method constructs new geometry instances and returns
     * them as a replacement list. The original list passed in is not modified in
     * content; instead a new list with converted geometries is created and
     * assigned locally.
     *
     * <p>Supported geometry types: LineString and MultiLineString. Other
     * geometry types are copied without modification.
     *
     * @param geometries list of input geometries to convert; method produces a
     *        replacement list and assigns it locally (callers should replace the
     *        original if needed)
     */
    public void changeGeometries(List<Geometry> geometries) {
        List<Geometry> convertedGeometries = convertAllGeometries(geometries);
        replaceGeometryList(geometries, convertedGeometries);
    }

    /**
     * Changes source geometry elevation based on the source type and bridge relationship.
     * This method applies different elevation calculation strategies:
     *
     * SOURCE_NOT_RELATED_TO_BRIDGE: Uses DEM ground elevation + 0.05m offset
     * ACTUAL_SOURCE_ON_BRIDGE: Uses bridge deck height (from bridgePkOn) + 0.05m offset
     * IMAGINARY_SOURCE_UNDER_BRIDGE: Uses bridge deck height - deck thickness
     * MIRROR_SOURCE: Uses bridge above calculations (bridgePkAbove)
     *
     * @param sourcePk Primary key of the source to process
     */
    public void changeSourceGeometries(long sourcePk) {
        SourceBridgeProperty sourceBridgeProperty = scene.getSourceBridgeProperty(sourcePk);
        if (sourceBridgeProperty == null) {
            throw new IllegalArgumentException("No bridge property found for source PK: " + sourcePk + 
                ". Source must have associated bridge properties for elevation conversion.");
        }

        // Find the source index by primary key
        List<Long> sourcePks = scene.getSourcePks();
        int sourceIndex = sourcePks.indexOf(sourcePk);
        if (sourceIndex == -1) {
            throw new IllegalArgumentException("Source with PK " + sourcePk + " not found in scene. " +
                "Available source PKs: " + sourcePks);
        }

        SourceBridgeProperty.SourceType sourceType = sourceBridgeProperty.getSourceType();
        Geometry sourceGeometry = scene.getSourceGeometryByIndex(sourceIndex);
        if (sourceGeometry == null) {
            throw new IllegalStateException("Source geometry is null for source PK: " + sourcePk + 
                " at index: " + sourceIndex + ". Scene may be in an inconsistent state.");
        }

        // Convert the source geometry coordinates based on the source type
        Geometry convertedGeometry = convertGeometryBySourceType(sourceGeometry, sourceBridgeProperty, sourceType);
        
        // Update the geometry in the scene by replacing it in the list
        List<Geometry> sourceGeometries = scene.getSourceGeometries();
        sourceGeometries.set(sourceIndex, convertedGeometry);
    }

    /**
     * Converts geometry coordinates based on the source type and bridge properties.
     *
     * @param geometry The geometry to convert
     * @param sourceBridgeProperty Bridge properties for this source
     * @param sourceType Type of source relative to bridge
     * @return Converted geometry with updated elevation
     */
    private Geometry convertGeometryBySourceType(Geometry geometry, SourceBridgeProperty sourceBridgeProperty, 
                                                SourceBridgeProperty.SourceType sourceType) {
        switch (sourceType) {
            case SOURCE_NOT_RELATED_TO_BRIDGE:
                return convertToGroundLevel(geometry);
                
            case ACTUAL_SOURCE_ON_BRIDGE:
                return convertToActualSourceOnBridge(geometry, sourceBridgeProperty);
                
            case IMAGINARY_SOURCE_UNDER_BRIDGE:
                return convertToImaginarySourceUnderBridge(geometry, sourceBridgeProperty);
                
            case MIRROR_SOURCE:
                return convertToMirrorSource(geometry, sourceBridgeProperty);
                
            default:
                // Unknown source type, fall back to ground level
                return convertToGroundLevel(geometry);
        }
    }

    /**
     * Converts geometry to ground level with 0.05m offset.
     */
    private Geometry convertToGroundLevel(Geometry geometry) {
        return applyElevationToGeometry(geometry, this::calculateGroundElevation);
    }

    /**
     * Converts geometry for actual source on bridge (deck height + 0.05m).
     */
    private Geometry convertToActualSourceOnBridge(Geometry geometry, SourceBridgeProperty sourceBridgeProperty) {
        return applyElevationToGeometry(geometry, 
            coord -> calculateBridgeOnElevation(coord, sourceBridgeProperty.getBridgePkOn()));
    }

    /**
     * Converts geometry for imaginary source under bridge (deck height - deck thickness).
     */
    private Geometry convertToImaginarySourceUnderBridge(Geometry geometry, SourceBridgeProperty sourceBridgeProperty) {
        return applyElevationToGeometry(geometry, 
            coord -> calculateBridgeUnderElevation(coord, sourceBridgeProperty.getBridgePkOn()));
    }

    /**
     * Converts geometry for mirror source (using bridgePkAbove).
     */
    private Geometry convertToMirrorSource(Geometry geometry, SourceBridgeProperty sourceBridgeProperty) {
        long bridgePkAbove = sourceBridgeProperty.getBridgePkAbove();
        if (bridgePkAbove < 0) {
            throw new IllegalArgumentException("Invalid bridgePkAbove value: " + bridgePkAbove + 
                ". Mirror source requires a valid bridge above (bridgePkAbove >= 0).");
        }
        return applyElevationToGeometry(geometry, 
            coord -> calculateMirrorSourceElevation(coord, sourceBridgeProperty));
    }

    /**
     * Calculates ground elevation + 0.05m offset.
     */
    private double calculateGroundElevation(Coordinate coord) {
        return scene.profileBuilder.getZGround(coord) + OFFSET;
    }

    /**
     * Calculates bridge deck elevation + 0.05m offset.
     */
    private double calculateBridgeOnElevation(Coordinate coord, long bridgePk) {
        Bridge bridge = scene.profileBuilder.getBridgeByPk(bridgePk);
        if (bridge == null) {
            throw new IllegalStateException("Bridge not found for PK: " + bridgePk + 
                ". Bridge on elevation calculation requires valid bridge.");
        }
        
        double deckHeight = bridge.getDeckHeightAtPoint(coord);
        if (Double.isNaN(deckHeight)) {
            throw new IllegalStateException("Cannot get bridge deck height at coordinate: " + coord + 
                ". Bridge PK: " + bridgePk + ", deckHeight: " + deckHeight);
        }
        
        return deckHeight + OFFSET;
    }

    /**
     * Calculates bridge bottom elevation (deck height - deck thickness).
     */
    private double calculateBridgeUnderElevation(Coordinate coord, long bridgePk) {
        Bridge bridge = scene.profileBuilder.getBridgeByPk(bridgePk);
        if (bridge == null) {
            throw new IllegalStateException("Bridge not found for PK: " + bridgePk + 
                ". Bridge under elevation calculation requires valid bridge.");
        }
        
        double deckHeight = bridge.getDeckHeightAtPoint(coord);
        double deckThickness = bridge.getDeckThicknessAtPoint(coord);
        
        if (Double.isNaN(deckHeight) || Double.isNaN(deckThickness)) {
            throw new IllegalStateException("Cannot get bridge properties at coordinate: " + coord + 
                ". Bridge PK: " + bridgePk + 
                ", deckHeight: " + deckHeight + 
                ", deckThickness: " + deckThickness);
        }
        
        return deckHeight - deckThickness;
    }

    /**
     * Calculates mirror source elevation using complex reflection formula.
     * Formula: deckHeightOn + (deckHeightAbove - deckThicknessAbove - deckHeightOn) * 2
     * 
     * @param coord coordinate point for elevation calculation
     * @param sourceBridgeProperty bridge properties containing bridgePkAbove and bridgePkOn
     * @return calculated mirror source elevation
     */
    private double calculateMirrorSourceElevation(Coordinate coord, SourceBridgeProperty sourceBridgeProperty) {
        long bridgePkAbove = sourceBridgeProperty.getBridgePkAbove();
        long bridgePkOn = sourceBridgeProperty.getBridgePkOn();
        
        // Get bridge above properties
        Bridge bridgeAbove = scene.profileBuilder.getBridgeByPk(bridgePkAbove);
        if (bridgeAbove == null) {
            throw new IllegalStateException("Bridge above not found for PK: " + bridgePkAbove + 
                ". Mirror source calculation requires valid bridge above.");
        }
        
        double deckHeightAbove = bridgeAbove.getDeckHeightAtPoint(coord);
        double deckThicknessAbove = bridgeAbove.getDeckThicknessAtPoint(coord);
        
        if (Double.isNaN(deckHeightAbove) || Double.isNaN(deckThicknessAbove)) {
            throw new IllegalStateException("Cannot get bridge above properties at coordinate: " + coord + 
                ". Bridge PK: " + bridgePkAbove + 
                ", deckHeightAbove: " + deckHeightAbove + 
                ", deckThicknessAbove: " + deckThicknessAbove);
        }
        
        // Get deck height on bridge (or DEM if bridgePkOn < 0)
        double deckHeightOn;
        if (bridgePkOn < 0) {
            // Use DEM ground elevation when bridgePkOn < 0
            deckHeightOn = scene.profileBuilder.getZGround(coord);
        } else {
            Bridge bridgeOn = scene.profileBuilder.getBridgeByPk(bridgePkOn);
            if (bridgeOn == null) {
                throw new IllegalStateException("Bridge on not found for PK: " + bridgePkOn + 
                    ". Mirror source calculation requires valid bridge on when bridgePkOn >= 0.");
            }
            
            deckHeightOn = bridgeOn.getDeckHeightAtPoint(coord);
            if (Double.isNaN(deckHeightOn)) {
                throw new IllegalStateException("Cannot get bridge on deck height at coordinate: " + coord + 
                    ". Bridge PK: " + bridgePkOn + ", deckHeightOn: " + deckHeightOn);
            }
        }
        
        // Apply the mirror source formula:
        // deckHeightOn + (deckHeightAbove - deckThicknessAbove - deckHeightOn) * 2
        return deckHeightOn + (deckHeightAbove - deckThicknessAbove - deckHeightOn) * 2.0;
    }

    /**
     * Applies elevation calculation to geometry using the provided elevation function.
     */
    private Geometry applyElevationToGeometry(Geometry geometry, ElevationFunction elevationFunction) {
        if (geometry instanceof LineString) {
            return applyElevationToLineString((LineString) geometry, elevationFunction);
        } else if (geometry instanceof MultiLineString) {
            return applyElevationToMultiLineString((MultiLineString) geometry, elevationFunction);
        }
        
        // For other geometry types, apply elevation to all coordinates
        return applyElevationToGenericGeometry(geometry, elevationFunction);
    }

    /**
     * Applies elevation to LineString coordinates.
     */
    private LineString applyElevationToLineString(LineString lineString, ElevationFunction elevationFunction) {
        Coordinate[] coords = lineString.getCoordinates();
        Coordinate[] newCoords = new Coordinate[coords.length];
        
        for (int i = 0; i < coords.length; i++) {
            newCoords[i] = new Coordinate(coords[i].x, coords[i].y, elevationFunction.calculateElevation(coords[i]));
        }
        
        return GEOMETRY_FACTORY.createLineString(newCoords);
    }

    /**
     * Applies elevation to MultiLineString coordinates.
     */
    private MultiLineString applyElevationToMultiLineString(MultiLineString multiLineString, ElevationFunction elevationFunction) {
        LineString[] lineStrings = new LineString[multiLineString.getNumGeometries()];
        
        for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
            lineStrings[i] = applyElevationToLineString((LineString) multiLineString.getGeometryN(i), elevationFunction);
        }
        
        return GEOMETRY_FACTORY.createMultiLineString(lineStrings);
    }

    /**
     * Applies elevation to generic geometry coordinates.
     */
    private Geometry applyElevationToGenericGeometry(Geometry geometry, ElevationFunction elevationFunction) {
        Geometry newGeometry = geometry.copy();
        Coordinate[] coords = newGeometry.getCoordinates();
        
        for (Coordinate coord : coords) {
            coord.setZ(elevationFunction.calculateElevation(coord));
        }
        
        return newGeometry;
    }

    /**
     * Functional interface for elevation calculation strategies.
     */
    @FunctionalInterface
    private interface ElevationFunction {
        double calculateElevation(Coordinate coord);
    }

    /**
     * Converts all geometries in the list from relative to absolute coordinates.
     * 
     * @param geometries list of input geometries to convert
     * @return new list containing converted geometries
     */
    private List<Geometry> convertAllGeometries(List<Geometry> geometries) {
        List<Geometry> geometriesCopy = new ArrayList<>(geometries.size());
        for (Geometry geom : geometries) {
            Geometry convertedGeom = convertSingleGeometry(geom);
            geometriesCopy.add(convertedGeom);
        }
        return geometriesCopy;
    }

    /**
     * Converts a single geometry from relative to absolute coordinates.
     * 
     * @param geom geometry to convert
     * @return converted geometry
     */
    private Geometry convertSingleGeometry(Geometry geom) {
        return convertToAbsolute(geom);
    }

    /**
     * Converts geometry from relative to absolute coordinates by projecting onto DEM.
     * 
     * @param geom geometry to convert
     * @return geometry with absolute coordinates
     */
    private Geometry convertToAbsolute(Geometry geom) {
        if (geom instanceof LineString) {
            return projectLineStringOntoDEM((LineString) geom, ProfileBuilder.MILLIMETER);
        } else if (geom instanceof MultiLineString) {
            return convertMultiLineStringToAbsolute((MultiLineString) geom);
        }
        return geom.copy();
    }

    /**
     * Converts MultiLineString from relative to absolute coordinates.
     * 
     * @param multiLineString MultiLineString to convert
     * @return converted MultiLineString with absolute coordinates
     */
    private Geometry convertMultiLineStringToAbsolute(MultiLineString multiLineString) {
        LineString[] newGeom = new LineString[multiLineString.getNumGeometries()];
        for (int idGeom = 0; idGeom < multiLineString.getNumGeometries(); idGeom++) {
            newGeom[idGeom] = projectLineStringOntoDEM(
                (LineString) multiLineString.getGeometryN(idGeom), ProfileBuilder.MILLIMETER);
        }
        return GEOMETRY_FACTORY.createMultiLineString(newGeom);
    }

    /**
     * Replaces the contents of the original geometry list with converted geometries.
     * 
     * @param originalList original list to be modified
     * @param convertedList list containing converted geometries
     */
    private void replaceGeometryList(List<Geometry> originalList, List<Geometry> convertedList) {
        originalList.clear();
        originalList.addAll(convertedList);
    }

    
    // splitLineStringWithBridge was extracted to BridgeSourceBuilder

    /**
     * Project a LineString onto the digital elevation model (DEM) by applying ground elevation to Z and
     * interpolating topographic points along each segment. Removes topographic points that are within
     * minInterpolationDistanceMm (in millimetres) of a linear interpolation between neighbours to avoid
     * redundant vertices produced by DEM triangulation.
     *
     * @param lineString the input LineString
     * @param minInterpolationDistanceMm minimum interpolation distance in millimetres; points whose deviation
     *        from the linear interpolation is less than this value are ignored
     * @return a new LineString with adjusted Z values and possibly additional vertices
     */
    private LineString projectLineStringOntoDEM(LineString lineString, double minInterpolationDistanceMm) {
        ArrayList<Coordinate> newGeomCoordinates = new ArrayList<>();
        Coordinate[] coordinates = lineString.getCoordinates();
        
        for (int idPoint = 0; idPoint < coordinates.length - 1; idPoint++) {
            processLineSegment(coordinates[idPoint], coordinates[idPoint + 1], 
                             newGeomCoordinates, minInterpolationDistanceMm, idPoint == 0);
        }
        
        return GEOMETRY_FACTORY.createLineString(newGeomCoordinates.toArray(new Coordinate[0]));
    }

    /**
     * Processes a single line segment by fetching topographic profile and adding interpolated points.
     * 
     * @param p0 start point of the segment
     * @param p1 end point of the segment
     * @param newGeomCoordinates output list to accumulate coordinates
     * @param minInterpolationDistanceMm minimum interpolation distance in millimetres
     * @param isFirstSegment true if this is the first segment in the line
     */
    private void processLineSegment(Coordinate p0, Coordinate p1, ArrayList<Coordinate> newGeomCoordinates,
                                   double minInterpolationDistanceMm, boolean isFirstSegment) {
        List<Coordinate> groundProfileCoordinates = new ArrayList<>();
        scene.profileBuilder.fetchTopographicProfile(groundProfileCoordinates, p0, p1, false);
        newGeomCoordinates.ensureCapacity(newGeomCoordinates.size() + groundProfileCoordinates.size());

        if (groundProfileCoordinates.size() < 2) {
            handleSimpleSegment(p0, p1, newGeomCoordinates);
        } else {
            handleComplexSegment(p0, p1, groundProfileCoordinates, newGeomCoordinates, 
                               minInterpolationDistanceMm, isFirstSegment);
        }
    }

    /**
     * Handles a simple segment without sufficient topographic profile data.
     * 
     * @param p0 start point of the segment
     * @param p1 end point of the segment
     * @param newGeomCoordinates output list to accumulate coordinates
     */
    private void handleSimpleSegment(Coordinate p0, Coordinate p1, ArrayList<Coordinate> newGeomCoordinates) {
        // if(profileBuilder.hasDem()) {
        //     if(!warned) {
        //         LOGGER.warn( "Source line out of DEM area {}",
        //                 new WKTWriter(3).write(lineString));
        //         warned = true;
        //     }
        // }
        newGeomCoordinates.add(p0);
        newGeomCoordinates.add(p1);
    }

    /**
     * Handles a complex segment with topographic profile data by interpolating and filtering points.
     * 
     * @param p0 start point of the segment
     * @param p1 end point of the segment
     * @param groundProfileCoordinates topographic profile coordinates
     * @param newGeomCoordinates output list to accumulate coordinates
     * @param minInterpolationDistanceMm minimum interpolation distance in millimetres
     * @param isFirstSegment true if this is the first segment in the line
     */
    private void handleComplexSegment(Coordinate p0, Coordinate p1, List<Coordinate> groundProfileCoordinates,
                                     ArrayList<Coordinate> newGeomCoordinates, double minInterpolationDistanceMm,
                                     boolean isFirstSegment) {
        if (isFirstSegment) {
            addFirstSegmentStartPoint(p0, groundProfileCoordinates, newGeomCoordinates);
        }

        addFilteredIntermediatePoints(p0, p1, groundProfileCoordinates, newGeomCoordinates, minInterpolationDistanceMm);
        addSegmentEndPoint(p1, groundProfileCoordinates, newGeomCoordinates);
    }

    /**
     * Adds the start point of the first segment with ground elevation applied.
     * 
     * @param p0 start point of the segment
     * @param groundProfileCoordinates topographic profile coordinates
     * @param newGeomCoordinates output list to accumulate coordinates
     */
    private void addFirstSegmentStartPoint(Coordinate p0, List<Coordinate> groundProfileCoordinates,
                                          ArrayList<Coordinate> newGeomCoordinates) {
        newGeomCoordinates.add(new Coordinate(p0.x, p0.y, p0.z + groundProfileCoordinates.get(0).z));
    }

    /**
     * Adds filtered intermediate points that are not simply linear interpolations.
     * 
     * @param p0 start point of the segment for Z interpolation
     * @param p1 end point of the segment for Z interpolation
     * @param groundProfileCoordinates topographic profile coordinates
     * @param newGeomCoordinates output list to accumulate coordinates
     * @param minInterpolationDistanceMm minimum interpolation distance in millimetres
     */
    private void addFilteredIntermediatePoints(Coordinate p0, Coordinate p1, List<Coordinate> groundProfileCoordinates,
                                              ArrayList<Coordinate> newGeomCoordinates, double minInterpolationDistanceMm) {
        Coordinate previous = groundProfileCoordinates.get(0);
        
        for (int groundPoint = 1; groundPoint < groundProfileCoordinates.size() - 1; groundPoint++) {
            final Coordinate current = groundProfileCoordinates.get(groundPoint);
            final Coordinate next = groundProfileCoordinates.get(groundPoint + 1);
            
            if (shouldIncludeIntermediatePoint(current, previous, next, minInterpolationDistanceMm)) {
                previous = current;
                Coordinate interpolatedPoint = createInterpolatedPoint(current, p0, p1);
                newGeomCoordinates.add(interpolatedPoint);
            }
        }
    }

    /**
     * Determines whether an intermediate point should be included based on distance filtering.
     * 
     * @param current current topographic point
     * @param previous previous topographic point
     * @param next next topographic point
     * @param minInterpolationDistanceMm minimum interpolation distance in millimetres
     * @return true if the point should be included, false otherwise
     */
    private boolean shouldIncludeIntermediatePoint(Coordinate current, Coordinate previous, Coordinate next,
                                                  double minInterpolationDistanceMm) {
        // Do not add topographic points which are simply the linear interpolation between two points
        // triangulation add a lot of interpolated lines from line segment DEM
        return CGAlgorithms3D.distancePointSegment(current, previous, next) >= minInterpolationDistanceMm;
    }

    /**
     * Creates an interpolated point with ground elevation and interpolated Z value.
     * 
     * @param current current topographic point (provides X, Y, and ground Z)
     * @param p0 start point of original segment for Z interpolation
     * @param p1 end point of original segment for Z interpolation
     * @return new coordinate with interpolated Z value
     */
    private Coordinate createInterpolatedPoint(Coordinate current, Coordinate p0, Coordinate p1) {
        // interpolate the Z (height) values of the source then add the altitude
        return new Coordinate(current.x, current.y, current.z + Vertex.interpolateZ(current, p0, p1));
    }

    /**
     * Adds the end point of the segment with ground elevation applied.
     * 
     * @param p1 end point of the segment
     * @param groundProfileCoordinates topographic profile coordinates
     * @param newGeomCoordinates output list to accumulate coordinates
     */
    private void addSegmentEndPoint(Coordinate p1, List<Coordinate> groundProfileCoordinates,
                                   ArrayList<Coordinate> newGeomCoordinates) {
        newGeomCoordinates.add(new Coordinate(p1.x, p1.y, p1.z +
                groundProfileCoordinates.get(groundProfileCoordinates.size() - 1).z));
    }

    

}
