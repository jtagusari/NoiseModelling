package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.api.EmptyProgressVisitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.utils.IsoSurface;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple integration test for DelaunayReceiversMaker.
 * Loads `buildings.geojson` and `roads.geojson` into an H2GIS database,
 * runs the Delaunay receiver generator and verifies receivers are created.
 */
public class DelaunayReceiversMakerTest {

	private Connection connection;

	@BeforeEach
	public void setUp() throws Exception {
		connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(DelaunayReceiversMakerTest.class.getSimpleName(), true, ""));
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}

	@Test
	public void testGenerateReceiversFromGeoJson() throws Exception {
		try (Statement st = connection.createStatement()) {
			// Load test resources (GeoJSON) into H2GIS
			st.execute(String.format("CALL GeoJsonRead('%s', 'BUILDINGS')",
					DelaunayReceiversMakerTest.class.getResource("/buildings.geojson").getFile()));
			    st.execute(String.format("CALL GeoJsonRead('%s', 'ROADS')",
				    DelaunayReceiversMakerTest.class.getResource("/roads.geojson").getFile()));
			    // Ensure ROADS has an integer primary key column named PK as expected by fetchCellSource
			    st.execute("ALTER TABLE ROADS ALTER COLUMN PK SET NOT NULL");
			    st.execute("ALTER TABLE ROADS ADD CONSTRAINT PK_SET PRIMARY KEY (PK)");

			int srid = org.h2gis.utilities.GeometryTableUtilities.getSRID(connection, "BUILDINGS");
			IsoSurface isoSurface = new IsoSurface(IsoSurface.NF31_133_ISO, srid);

			TableInputSettings tableInputSettings = new TableInputSettings.Builder()
					.setSourceTableName("ROADS")
					.build();
			
			PropagationSettings propagationSettings = new PropagationSettings.Builder()
					.setMaximumPropagationDistance(800.0)
					.build();
			
			ComputationSettings computationSettings = new ComputationSettings.Builder()
					.setGridDim(1)
					.build();

			DelaunayReceiversMaker delaunayReceiversMaker = new DelaunayReceiversMaker.Builder()
					.setTableInputSettings(tableInputSettings)
					.setPropagationSettings(propagationSettings)
					.setComputationSettings(computationSettings)
					.build();
					
			// Generate receivers into table RECEIVERS using triangle table name from isoSurface
			delaunayReceiversMaker.run(connection, "RECEIVERS", isoSurface.getTriangleTable());

			int receiversRowCount = JDBCUtilities.getRowCount(connection, "RECEIVERS");
			assertTrue(receiversRowCount > 0, "DelaunayReceiversMaker should create at least one receiver");
		}
	}
}
