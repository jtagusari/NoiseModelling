/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by the DECIDE team from the Lab-STICC (CNRS) and by the Mixt Research Unit in Environmental Acoustics (Université Gustave Eiffel).
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */

package org.noise_planet.noisemodelling.wps

import groovy.sql.Sql
import org.h2gis.utilities.JDBCUtilities
import org.noise_planet.noisemodelling.jdbc.CalculationIOSettings
import org.noise_planet.noisemodelling.wps.Import_and_Export.Export_Table
import org.noise_planet.noisemodelling.wps.Import_and_Export.Import_File
import org.noise_planet.noisemodelling.wps.NoiseModelling.Noise_level_from_traffic

/**
 * Integration tests for the Noise_level_from_traffic WPS script,
 * using the TutoBridge dataset (Seishin Bypass, Shizuoka, SRID=6676).
 *
 * ROADS.geojson, RECEIVERS.geojson, and BUILDINGS.geojson
 * are imported from the TutoBridge resource folder.
 * ROADS.geojson does not contain a BRIDGE_PK column, so it is added
 * via SQL after import.  Bridge point data is created inline via SQL
 * (one point per road segment placed at mid-span, deck height 8 m absolute).
 */
class TestNoiseLevelFromBridgeTraffic extends JdbcTestCase {

    /**
     * Happy-path: imports roads, buildings, and receivers from TutoBridge,
     * assigns all road segments to BRIDGE_PK=1, creates matching bridge points,
     * then runs Noise_level_from_traffic.
     * Verifies that RECEIVERS_LEVEL is created, is non-empty, and contains D/E/N/DEN periods.
     */ 
    void testBridgeTraffic() {
        Sql sql = new Sql(connection)

        new Import_File().exec(connection, [
                "pathFile" : TestNoiseLevelFromBridgeTraffic.getResource("TutoBridge/roads.geojson").getPath(),
                "tableName": "ROADS"])

        new Import_File().exec(connection, [
                "pathFile" : TestNoiseLevelFromBridgeTraffic.getResource("TutoBridge/bridgepoints.geojson").getPath(),
                "tableName": "BRIDGE_POINTS"])

        new Import_File().exec(connection, [
                "pathFile" : TestNoiseLevelFromBridgeTraffic.getResource("TutoBridge/receivers.geojson").getPath(),
                "tableName": "RECEIVERS"])

        new Import_File().exec(connection, [
                "pathFile" : TestNoiseLevelFromBridgeTraffic.getResource("TutoBridge/buildings.geojson").getPath(),
                "tableName": "BUILDINGS"])

        new Import_File().exec(connection, [
                "pathFile" : TestNoiseLevelFromBridgeTraffic.getResource("TutoBridge/dem.geojson").getPath(),
                "tableName": "DEM"])

        // ROADS.geojson has no BRIDGE_PK column — add and link all segments to bridge 1
        sql.execute("ALTER TABLE ROADS ADD COLUMN BRIDGE_PK INT")
        sql.execute("UPDATE ROADS SET BRIDGE_PK = 1")

        String res = new Noise_level_from_traffic().exec(connection,
                ["tableRoads"        : "ROADS",
                 "tableBuilding"     : "BUILDINGS",
                 "tableReceivers"    : "RECEIVERS",
                 "tableDEM"          : "DEM",
                 "tableBridgePoints" : "BRIDGE_POINTS",
                 "confMaxSrcDist"    : 500.0,
                 "confDiffHorizontal" : true,
                 "confReflOrder"     : 0,
                 "confMaxError"      : 0.0,
                 "confRaysName"      : "RAYS"])

        assertTrue(res.contains(CalculationIOSettings.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME))
        assertTrue(JDBCUtilities.tableExists(connection, CalculationIOSettings.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME))

        def rowCount = sql.firstRow(
                "SELECT COUNT(*) CNT FROM " + CalculationIOSettings.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME)["CNT"] as Integer
        assertTrue(rowCount > 0)

        def periods = sql.rows(
                "SELECT DISTINCT PERIOD FROM " + CalculationIOSettings.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME +
                " ORDER BY PERIOD").collect { it["PERIOD"] as String }
        assertTrue(periods.contains("D"))
        assertTrue(periods.contains("E"))
        assertTrue(periods.contains("N"))
        assertTrue(periods.contains("DEN"))

        new File("build/tmp").mkdirs()
        new Export_Table().exec(connection,
                ["exportPath"   : "build/tmp/TutoBridge_receivers_level.geojson",
                 "tableToExport": CalculationIOSettings.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME])
        new Export_Table().exec(connection,
                ["exportPath"   : "build/tmp/TutoBridge_rays.geojson",
                 "tableToExport": "RAYS"])
    }

}
