package org.noise_planet.noisemodelling.jdbc;

import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsj;
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsjParameters;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.h2gis.utilities.SpatialResultSet;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BridgeStructuralEmissionCalculator {
    public static final List<Integer> roadOctaveFrequencyBands = EmissionTableGenerator.roadOctaveFrequencyBands;


    /**
     * Compute structural noise emission from traffic data using ASJ methodology for bridge virtual sources.
     * This method follows the same pattern as getEmissionFromTrafficTable but focuses on structural sound transmission through bridges.
     * 
     * @param rs Result set containing traffic and bridge data
     * @param period Time period suffix ("_D", "_E", "_N")
     * @param bridge Bridge object containing girder type, slab type, and other bridge properties
     * @param sourceFieldsCache SQL fields cache
     * @return Structural emission spectrum in dB
     * @throws SQLException If database access fails
     */
    public static double[] getStructuralEmissionFromTrafficTable(ResultSet rs, String period,
                                                               Bridge bridge,
                                                               Map<String, Integer> sourceFieldsCache) throws SQLException {
        String bridgeGirderType = bridge.getGirderType() != null ? bridge.getGirderType().name() : "STEEL_BOX";
        String bridgeSlabType = bridge.getSlabType() != null ? bridge.getSlabType().name() : "STEEL";

        // EmissionTableGenerator.cacheFields(sourceFieldsCache, rs); // not cached
        double lvPerHour = 0;
        double mvPerHour = 0;
        double hgvPerHour = 0;
        double wavPerHour = 0;
        double wbvPerHour = 0;
        double lv_speed = 0;
        double mv_speed = 0;
        double hgv_speed = 0;
        double wav_speed = 0;
        double wbv_speed = 0;
        double temperature = 20.0;
        String roadSurface = "NL08";
        if(sourceFieldsCache.containsKey("LV"+period)) {
            lvPerHour = rs.getDouble(sourceFieldsCache.get("LV"+period));
        }
        if(sourceFieldsCache.containsKey("MV"+period)) {
            mvPerHour = rs.getDouble(sourceFieldsCache.get("MV"+period));
        }
        if(sourceFieldsCache.containsKey("HGV"+period)) {
            hgvPerHour = rs.getDouble(sourceFieldsCache.get("HGV"+period));
        }
        if(sourceFieldsCache.containsKey("WAV"+period)) {
            wavPerHour = rs.getDouble(sourceFieldsCache.get("WAV"+period));
        }
        if(sourceFieldsCache.containsKey("WBV"+period)) {
            wbvPerHour = rs.getDouble(sourceFieldsCache.get("WBV"+period));
        }
        if(sourceFieldsCache.containsKey("LV_SPD"+period)) {
            lv_speed = Math.max(20.0, rs.getDouble(sourceFieldsCache.get("LV_SPD"+period)));
        }
        if(sourceFieldsCache.containsKey("MV_SPD"+period)) {
            mv_speed = Math.max(20.0, rs.getDouble(sourceFieldsCache.get("MV_SPD"+period)));
        }
        if(sourceFieldsCache.containsKey("HGV_SPD"+period)) {
            hgv_speed = Math.max(20.0, rs.getDouble(sourceFieldsCache.get("HGV_SPD"+period)));
        }
        if(sourceFieldsCache.containsKey("WAV_SPD"+period)) {
            wav_speed = Math.max(20.0, rs.getDouble(sourceFieldsCache.get("WAV_SPD"+period)));
        }
        if(sourceFieldsCache.containsKey("WBV_SPD"+period)) {
            wbv_speed = Math.max(20.0, rs.getDouble(sourceFieldsCache.get("WBV_SPD"+period)));
        }
        if(sourceFieldsCache.containsKey("TEMP"+period)) {
            temperature = rs.getDouble(sourceFieldsCache.get("TEMP"+period));
        }
        if(sourceFieldsCache.containsKey("PVMT")) {
            roadSurface = rs.getString(sourceFieldsCache.get("PVMT"));
        }
        double mvHgvFlow = mvPerHour + hgvPerHour;
        if (mvHgvFlow < 1.0) {
            double[] silentLevels = new double[roadOctaveFrequencyBands.size()];
            Arrays.fill(silentLevels, -99.0);
            return silentLevels;
        }
        double[] lvl = new double[roadOctaveFrequencyBands.size()];
        for (int idFreq = 0; idFreq < roadOctaveFrequencyBands.size(); idFreq++) {
            int freq = roadOctaveFrequencyBands.get(idFreq);
            RoadAsjParameters asjParams = new RoadAsjParameters(
                lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed,
                lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour,
                freq, temperature, roadSurface
            );
            asjParams.setHasBridge(true);
            asjParams.setBridgeGirderType(bridgeGirderType);
            asjParams.setBridgeSlabType(bridgeSlabType);
            try {
                lvl[idFreq] = RoadAsj.evaluateBridgeVirtualSource(asjParams);
            } catch (IOException ex) {
                throw new SQLException(ex);
            }
        }
        return lvl;
    }
    
    /**
     * Computes the structural sound levels (Lw) for different periods based on the provided spatial result set.
     * This method calculates bridge virtual source emissions using ASJ methodology.
     * 
     * @param rs Result set on a road record
     * @param bridge Bridge object containing girder type, slab type, and other bridge properties
     * @param sourceFieldsCache SQL Fields cache
     * @return a two-dimensional array containing the structural sound levels (Ld, Le, Ln) for each frequency level.
     * @throws SQLException Exception while evaluating the structural lw
     */
    public static double[][] computeStructuralLw(SpatialResultSet rs, Bridge bridge,
                                                Map<String, Integer> sourceFieldsCache) throws SQLException {
        double[] ld = AcousticIndicatorsFunctions.dBToW(getStructuralEmissionFromTrafficTable(rs, "_D", bridge, sourceFieldsCache));
        double[] le = AcousticIndicatorsFunctions.dBToW(getStructuralEmissionFromTrafficTable(rs, "_E", bridge, sourceFieldsCache));
        double[] ln = AcousticIndicatorsFunctions.dBToW(getStructuralEmissionFromTrafficTable(rs, "_N", bridge, sourceFieldsCache));
        return new double[][] {ld, le, ln};
    }
}
