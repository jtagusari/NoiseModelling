package org.noise_planet.noisemodelling.pathfinder;

import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable geometry helper methods extracted from PathFinder.
 *
 * Single responsibility: provide low-level geometry utilities.
 * This class exposes pure geometry operations (plane computation, filtering
 * points by side, plane intersection helpers). It must not perform
 * profile-building, source/receiver management, or I/O.
 */
public final class GeometryUtils {
    private static final double epsilon = 1e-7;

    private GeometryUtils() {}

    public static Plane computeZeroRadPlane(Coordinate p0, Coordinate p1) {
        Vector3D s = new Vector3D(p0.x, p0.y, p0.z);
        Vector3D r = new Vector3D(p1.x, p1.y, p1.z);
        double angle = Math.atan2(p1.y - p0.y, p1.x - p0.x);
        Vector3D rPrime = s.add(new Vector3D(Math.cos(angle - Math.PI / 2), Math.sin(angle - Math.PI / 2), 0));
        Plane p = new Plane(r, s, rPrime, 1e-6);
        if (p.getNormal().getZ() < 0) {
            p.revertSelf();
        }
        return p;
    }

    public static List<Coordinate> filterPointsBySide(LineSegment sr, boolean left,
                                                      List<Coordinate> segmentsCoordinates) {
        List<Coordinate> keptSegments = new ArrayList<>(segmentsCoordinates.size());
        for (Coordinate vertex : segmentsCoordinates) {
            int orientationIndex = sr.orientationIndex(vertex);
            if ((orientationIndex == 1 && left) || (orientationIndex == -1 && !left)) {
                keptSegments.add(vertex);
            }
        }
        return keptSegments;
    }

    public static List<Coordinate> cutRoofPointsWithPlane(Plane plane, List<Coordinate> roofPts) {
        List<Coordinate> polyCut = new ArrayList<>(roofPts.size());
        double lastOffset = 0;
        Coordinate cPrev = null;
        for (Coordinate cCur : roofPts) {
            double offset = plane.getOffset(coordinateToVector(cCur));
            if (cPrev != null && ((offset >= 0 && lastOffset < 0) || (offset < 0 && lastOffset >= 0))) {
                Vector3D i = plane.intersection(new Line(coordinateToVector(cPrev), coordinateToVector(cCur), epsilon));
                polyCut.add(new Coordinate(i.getX(), i.getY(), i.getZ()));
            }
            if (offset >= 0) {
                Vector3D i = plane.intersection(new Line(new Vector3D(cCur.x, cCur.y, Double.MIN_VALUE), coordinateToVector(cCur), epsilon));
                if (i != null) polyCut.add(new Coordinate(i.getX(), i.getY(), i.getZ()));
            }
            lastOffset = offset;
            cPrev = cCur;
        }
        return polyCut;
    }

    public static Vector3D coordinateToVector(Coordinate p) {
        return new Vector3D(p.x, p.y, p.z);
    }
}
