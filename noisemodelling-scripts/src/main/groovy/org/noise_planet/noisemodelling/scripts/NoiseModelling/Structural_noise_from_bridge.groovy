/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by the DECIDE team from the Lab-STICC (CNRS) and by the Mixt Research Unit in Environmental Acoustics (Université Gustave Eiffel).
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.scripts.NoiseModelling

import groovy.sql.Sql
import org.h2gis.api.EmptyProgressVisitor
import org.h2gis.api.ProgressVisitor
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBTypes
import org.h2gis.utilities.dbtypes.DBUtils
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsj
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsjParameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

title = 'Structure-radiated (ASJ) noise emission for bridge decks'
description = '&#10145;&#65039; Compute the noise radiated by a bridge deck / girders under traffic, using the ' +
        'ASJ RTN-Model 2023 coefficients (by girder + slab type). </br>' +
        'The deck centre line of each bridge becomes a line source at deck height, carrying a per-period ' +
        'octave-band emission spectrum. Feed the output as <b>tableSources</b> to <b>Noise_level_from_source</b>, ' +
        'alongside (or instead of) the vehicles\' own CNOSSOS emission.<hr>' +
        '&#x2705; The output table is called: <b>BRIDGE_STRUCT_LW</b>'

inputs = [
        tableBridgePoints: [
                name       : 'Bridge points table name',
                title      : 'Bridge points table name',
                description: '<b>Name of the BRIDGE_POINTS table.</b> One POINT row per deck centre-line point. ' +
                        'Recognised columns (* mandatory):<ul>' +
                        '<li>* <b>PK</b> : point identifier, orders the points along the deck (INTEGER)</li>' +
                        '<li>* <b>BRIDGE_PK</b> : deck identifier, groups the points into one deck (INTEGER)</li>' +
                        '<li>* <b>THE_GEOM</b> : POINT geometry, metric projection</li>' +
                        '<li>* <b>GIRDER_TYPE</b> : STEEL_BOX, STEEL_PLATE, CONCRETE_BOX, CONCRETE_PLATE, CONCRETE_HOLLOW_SLAB, OTHERS (VARCHAR)</li>' +
                        '<li>* <b>SLAB_TYPE</b> : STEEL, CONCRETE (VARCHAR)</li>' +
                        '<li><b>ABSOLUTE_DECK_HEIGHT</b> : deck-surface Z used for the line source (DOUBLE, default 0)</li>' +
                        '</ul>',
                type       : String.class
        ],
        tableRoads       : [
                name       : 'Bridge traffic table name',
                title      : 'Bridge traffic table name',
                description: '<b>Name of the traffic table for the deck(s).</b> One row per deck and period. ' +
                        'Recognised columns (* mandatory):<ul>' +
                        '<li>* <b>BRIDGE_PK</b> : deck identifier, matches BRIDGE_POINTS.BRIDGE_PK (INTEGER)</li>' +
                        '<li>* <b>PERIOD</b> : D, E or N (VARCHAR)</li>' +
                        '<li>* <b>MV</b> : hourly medium-heavy vehicle count (DOUBLE)</li>' +
                        '<li>* <b>HGV</b> : hourly heavy-duty vehicle count (DOUBLE)</li>' +
                        '<li>* <b>MV_SPD</b> : medium-heavy vehicle speed, km/h (DOUBLE)</li>' +
                        '<li>* <b>HGV_SPD</b> : heavy-duty vehicle speed, km/h (DOUBLE)</li>' +
                        '</ul>The ASJ bridge source is driven by MV + HGV traffic only (heavy vehicles excite the structure).',
                type       : String.class
        ],
        outputTable      : [
                name       : 'Output table name',
                title      : 'Output table name',
                description: '&#128736; Output table, dropped and recreated. Default: <b>BRIDGE_STRUCT_LW</b>.',
                default    : 'BRIDGE_STRUCT_LW',
                type       : String.class
        ]
]

outputs = [
        result: [
                name       : 'Result output string',
                title      : 'Result output string',
                description: 'Name of the created table.',
                type       : String.class
        ]
]

def exec(Connection connection, Map input, ProgressVisitor progress) {

    final List<Integer> BANDS = [63, 125, 250, 500, 1000, 2000, 4000, 8000]

    if (!connection.isWrapperFor(ConnectionWrapper.class)) {
        connection = new ConnectionWrapper(connection)
    }
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    DBTypes dbType = DBUtils.getDBType(connection)
    Sql sql = new Sql(connection)

    String bridgePointsTable = TableLocation.capsIdentifier(input['tableBridgePoints'] as String, dbType)
    String roadsTable = TableLocation.capsIdentifier(input['tableRoads'] as String, dbType)
    String outputTable = TableLocation.capsIdentifier(
            (input['outputTable'] ?: 'BRIDGE_STRUCT_LW') as String, dbType)

    int srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse(bridgePointsTable, dbType))
    if (srid == 0) {
        throw new IllegalArgumentException("Please use a metric projection for " + bridgePointsTable)
    }
    GeometryFactory factory = new GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), srid)

    // ---- 1. deck centre line + type, per BRIDGE_PK -------------------------------------------------
    Map<Integer, List<Coordinate>> deckLine = new LinkedHashMap<>()
    Map<Integer, String> girder = [:]
    Map<Integer, String> slab = [:]
    sql.eachRow("SELECT * FROM " + bridgePointsTable + " ORDER BY BRIDGE_PK, PK") { row ->
        int bpk = row.getInt('BRIDGE_PK')
        def geom = row.getObject('THE_GEOM')
        Coordinate c = new Coordinate(geom.getCoordinate())
        double deckZ = 0.0
        try { deckZ = row.getDouble('ABSOLUTE_DECK_HEIGHT') } catch (ignored) { }
        if (row.getObject('ABSOLUTE_DECK_HEIGHT') == null) { deckZ = 0.0 }
        c.setZ(deckZ)
        deckLine.computeIfAbsent(bpk, { [] }).add(c)
        girder.putIfAbsent(bpk, (row.getString('GIRDER_TYPE') ?: 'OTHERS').toUpperCase())
        slab.putIfAbsent(bpk, (row.getString('SLAB_TYPE') ?: 'CONCRETE').toUpperCase())
    }
    if (deckLine.isEmpty()) {
        throw new IllegalArgumentException(bridgePointsTable + " holds no bridge points")
    }

    // ---- 2. output table --------------------------------------------------------------------------
    sql.execute("DROP TABLE IF EXISTS " + outputTable)
    StringBuilder cols = new StringBuilder("PK INT PRIMARY KEY, BRIDGE_PK INT, THE_GEOM GEOMETRY(LINESTRINGZ, "
            + srid + "), PERIOD VARCHAR")
    BANDS.each { cols.append(", HZ").append(it).append(" DOUBLE") }
    sql.execute("CREATE TABLE " + outputTable + " (" + cols + ")")

    String placeholders = (["?"] * (4 + BANDS.size())).join(", ")
    int pk = 0

    // ---- 3. one line source per deck and period --------------------------------------------------
    ProgressVisitor sub = progress.subProcess(deckLine.size())
    deckLine.each { Integer bpk, List<Coordinate> pts ->
        if (pts.size() < 2) { sub.endStep(); return }
        LineString line = factory.createLineString(pts as Coordinate[])
        String g = girder.get(bpk)
        String s = slab.get(bpk)

        sql.eachRow("SELECT * FROM " + roadsTable + " WHERE BRIDGE_PK = " + bpk) { tr ->
            String period = tr.getString('PERIOD')
            double mv = safeDouble(tr, 'MV')
            double hgv = safeDouble(tr, 'HGV')
            double mvSpd = safeDouble(tr, 'MV_SPD')
            double hgvSpd = safeDouble(tr, 'HGV_SPD')

            List<Object> params = [++pk, bpk, line, period]
            BANDS.each { int f ->
                RoadAsjParameters p = new RoadAsjParameters(
                        0.0, mvSpd, hgvSpd, 0.0, 0.0,   // speeds lv/mv/hgv/wav/wbv
                        0.0, mv, hgv, 0.0, 0.0,          // flows  lv/mv/hgv/wav/wbv
                        f, 20.0, "DEF")
                p.setHasBridge(true)
                p.setBridgeGirderType(g)
                p.setBridgeSlabType(s)
                double lw = RoadAsj.evaluateBridgeVirtualSource(p)
                params.add(Double.isFinite(lw) ? lw : -99.0)
            }
            sql.executeInsert("INSERT INTO " + outputTable + " VALUES (" + placeholders + ")", params)
        }
        sub.endStep()
    }

    sql.execute("CREATE SPATIAL INDEX ON " + outputTable + "(THE_GEOM)")
    logger.info("Structural_noise_from_bridge: {} rows written to {}", pk, outputTable)
    return [result: outputTable]
}

def exec(Connection connection, Map input) {
    return exec(connection, input, new EmptyProgressVisitor())
}

private static double safeDouble(row, String col) {
    try {
        def v = row.getObject(col)
        return v == null ? 0.0 : (v as Double)
    } catch (ignored) {
        return 0.0
    }
}
