package org.noise_planet.noisemodelling.jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsj;
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsjParameters;

public class BridgeStructuralEmissionCalculatorTest {
    @Test
    public void testGetStructuralEmissionFromTrafficTable_minimal() throws SQLException {
        // モックのResultSetとBridgeを用意
        ResultSet rs = mock(ResultSet.class);
        Bridge bridge = mock(Bridge.class);
        Map<String, Integer> cache = new HashMap<>();
        // 必要なカラムのみセット
        cache.put("MV_D", 1);
        cache.put("HGV_D", 2);
        when(rs.getDouble(1)).thenReturn(10.0); // MV_D
        when(rs.getDouble(2)).thenReturn(5.0);  // HGV_D
        when(bridge.getGirderType()).thenReturn(null);
        when(bridge.getSlabType()).thenReturn(null);
        double[] result = BridgeStructuralEmissionCalculator.getStructuralEmissionFromTrafficTable(rs, "_D", bridge, cache);
        assertEquals(BridgeStructuralEmissionCalculator.roadOctaveFrequencyBands.size(), result.length);
        // 何らかの値が返ることを確認（-99.0でない）
        assertTrue(result[0] > -99.0);
    }

    @Test
    public void testGetStructuralEmissionFromTrafficTable_withMockedRoadAsj() throws SQLException {
        // モックのResultSetとBridgeを用意
        ResultSet rs = mock(ResultSet.class);
        Bridge bridge = mock(Bridge.class);
        Map<String, Integer> cache = new HashMap<>();
        // MV と HGV のフロー、速度を提供して評価が -99 にならないようにする
        cache.put("MV_D", 1);
        cache.put("HGV_D", 2);
        cache.put("MV_SPD_D", 3);
        cache.put("HGV_SPD_D", 4);
        when(rs.getDouble(1)).thenReturn(10.0); // MV_D
        when(rs.getDouble(2)).thenReturn(5.0);  // HGV_D
        when(rs.getDouble(3)).thenReturn(50.0); // MV_SPD_D
        when(rs.getDouble(4)).thenReturn(40.0); // HGV_SPD_D
        when(bridge.getGirderType()).thenReturn(null);
        when(bridge.getSlabType()).thenReturn(null);

        // 期待する周波数ごとの値を作成
        List<Integer> freqs = BridgeStructuralEmissionCalculator.roadOctaveFrequencyBands;
        double[] expected = new double[freqs.size()];
        for (int i = 0; i < freqs.size(); i++) {
            // 任意の期待値（周波数インデックスに応じた一意の値）
            expected[i] = 30.0 + i;
        }

        // RoadAsj.evaluateBridgeVirtualSource を周波数に応じて返すように静的モック
        try (MockedStatic<RoadAsj> mocked = mockStatic(RoadAsj.class)) {
            mocked.when(() -> RoadAsj.evaluateBridgeVirtualSource(any(RoadAsjParameters.class)))
                  .thenAnswer(invocation -> {
                      RoadAsjParameters p = invocation.getArgument(0);
                      int freq = p.getFrequency();
                      for (int i = 0; i < freqs.size(); i++) {
                          if (freqs.get(i) == freq) return expected[i];
                      }
                      return expected[0];
                  });

            double[] result = BridgeStructuralEmissionCalculator.getStructuralEmissionFromTrafficTable(rs, "_D", bridge, cache);
            assertEquals(expected.length, result.length);
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], result[i], 1e-9, "Frequency index " + i + " mismatch");
            }
        }
    }
}
