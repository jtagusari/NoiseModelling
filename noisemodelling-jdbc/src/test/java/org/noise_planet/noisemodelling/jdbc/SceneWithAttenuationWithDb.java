package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.noise_planet.noisemodelling.propagation.SceneWithAttenuation;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SceneWithAttenuationWithDb {
	private Connection connection;

	@BeforeEach
	public void setUp() throws Exception {
		connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase("SceneWithAttenuationTestDB", true, ""));
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}

	/**
	 * H2GIS DB上にテーブルを作成し，SceneWithAttenuation.addSourceDb()の動作を検証するテスト
	 */
	@Test
	public void testAddSourceDbWithH2GIS() throws Exception {
		try (Statement st = connection.createStatement()) {
			// テーブル作成
			st.execute("CREATE TABLE SOURCES (PK BIGINT PRIMARY KEY, THE_GEOM GEOMETRY, GS DOUBLE, DIR_ID INTEGER, YAW FLOAT, PITCH FLOAT, ROLL FLOAT, HEIGHT_TYPE VARCHAR(10), BRIDGE_PK BIGINT, EMISSION_TYPE VARCHAR(20))");
			// テスト用LineStringジオメトリ作成
			GeometryFactory gf = new GeometryFactory();
			LineString line = gf.createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)});
			// データ挿入
			st.execute("INSERT INTO SOURCES (PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE) VALUES (1, ST_GeomFromText('LINESTRING(0 0, 10 0)'), 0.7, 2, 0.0, 0.0, 0.0, 'RELATIVE', NULL, 'ROAD')");
		
		}

		// SceneWithAttenuationのセットアップ
		ProfileBuilder profileBuilder = new ProfileBuilder();
		SceneWithAttenuation scene = new SceneWithAttenuation(profileBuilder);

		// データ取得とaddSourceDb呼び出し
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM SOURCES WHERE PK=1");
			 SpatialResultSet rs = ps.executeQuery().unwrap(SpatialResultSet.class)) {
			assertTrue(rs.next());
			long pk = rs.getLong("PK");
			Geometry geom = rs.getGeometry("THE_GEOM");
			scene.addSourceDb(pk, geom, rs);
			// 検証
			assertEquals(1, scene.bridgeRelationships.size());
			assertEquals(0.7, scene.sourceGs.get(pk));
			assertEquals(2, scene.sourceEmissionAttenuation.get(pk));
			assertEquals("RELATIVE", scene.getSourceHeightTypeByPk(pk).name());
			assertEquals("LineString", geom.getGeometryType());
		}
	}
	/**
	 * 複数音源をDBに挿入し，addSourceDbで全て追加・検証するテスト
	 */
	@Test
	public void testAddMultipleSourcesDbWithH2GIS() throws Exception {
		try (Statement st = connection.createStatement()) {
			// テーブル作成
			st.execute("CREATE TABLE SOURCES (PK BIGINT PRIMARY KEY, THE_GEOM GEOMETRY, GS DOUBLE, DIR_ID INTEGER, YAW FLOAT, PITCH FLOAT, ROLL FLOAT, HEIGHT_TYPE VARCHAR(10), BRIDGE_PK BIGINT, EMISSION_TYPE VARCHAR(20))");
			GeometryFactory gf = new GeometryFactory();
			// 1つ目の音源
			st.execute("INSERT INTO SOURCES (PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE) VALUES (1, ST_GeomFromText('LINESTRING(0 0, 10 0)'), 0.7, 2, 0.0, 0.0, 0.0, 'RELATIVE', NULL, 'ROAD')");
			// 2つ目の音源
			st.execute("INSERT INTO SOURCES (PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE) VALUES (2, ST_GeomFromText('LINESTRING(20 0, 30 0)'), 1.2, 3, 10.0, 0.0, 0.0, 'ABSOLUTE', NULL, 'ROAD')");
		}

		ProfileBuilder profileBuilder = new ProfileBuilder();
		SceneWithAttenuation scene = new SceneWithAttenuation(profileBuilder);

		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM SOURCES ORDER BY PK");
				SpatialResultSet rs = ps.executeQuery().unwrap(SpatialResultSet.class)) {
			int count = 0;
			while (rs.next()) {
				long pk = rs.getLong("PK");
				Geometry geom = rs.getGeometry("THE_GEOM");
				scene.addSourceDb(pk, geom, rs);
				if (pk == 1L) {
					assertEquals(0.7, scene.sourceGs.get(pk));
					assertEquals(2, scene.sourceEmissionAttenuation.get(pk));
					assertEquals("RELATIVE", scene.getSourceHeightTypeByPk(pk).name());
					assertEquals("LineString", geom.getGeometryType());
				} else if (pk == 2L) {
					assertEquals(1.2, scene.sourceGs.get(pk));
					assertEquals(3, scene.sourceEmissionAttenuation.get(pk));
					assertEquals("ABSOLUTE", scene.getSourceHeightTypeByPk(pk).name());
					assertEquals("LineString", geom.getGeometryType());
				}
				count++;
			}
			assertEquals(2, count, "2音源が登録されていること");
		}
	}
}

