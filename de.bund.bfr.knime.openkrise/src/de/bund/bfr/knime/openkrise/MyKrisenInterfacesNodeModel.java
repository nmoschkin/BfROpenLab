/*******************************************************************************
 * Copyright (c) 2015 Federal Institute for Risk Assessment (BfR), Germany
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Department Biological Safety - BfR
 *******************************************************************************/
package de.bund.bfr.knime.openkrise;

import static de.bund.bfr.knime.openkrise.generated.public_.Tables.CHARGEN;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.CHARGENVERBINDUNGEN;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.EXTRAFIELDS;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.LIEFERUNGEN;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.PRODUKTKATALOG;
import static de.bund.bfr.knime.openkrise.generated.public_.Tables.STATION;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

import de.bund.bfr.knime.IO;
import de.bund.bfr.knime.KnimeUtils;
import de.bund.bfr.knime.openkrise.db.DBKernel;
import de.bund.bfr.knime.openkrise.db.MyDBI;

/**
 * This is the model implementation of MyKrisenInterfaces.
 * 
 * 
 * @author draaw
 */
public class MyKrisenInterfacesNodeModel extends NodeModel {

	static final String PARAM_FILENAME = "filename";
	static final String PARAM_OVERRIDE = "override";
	static final String PARAM_ANONYMIZE = "anonymize";

	private String filename;
	private boolean override;
	private boolean doAnonymize;

	/**
	 * Constructor for the node model.
	 */
	protected MyKrisenInterfacesNodeModel() {
		super(0, 3);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
			throws Exception {
		BufferedDataContainer output33Nodes = null;
		BufferedDataContainer output33Links = null;
		BufferedDataContainer deliveryDelivery = exec.createDataContainer(getDataModelSpec());

		String f = KnimeUtils.getFile(filename + "/DB").getAbsolutePath();
		Connection conn = override ? getNewLocalConnection("SA", "", f) : DBKernel.getLocalConn(true);
		Map<String, Delivery> allDeliveries = getNewTracingModel(DBKernel.myDBi, conn);
		output33Nodes = exec.createDataContainer(getSpec33Nodes(conn));
		output33Links = exec.createDataContainer(getSpec33Links(conn));

		boolean warningsThere = false;
		// Date_In <= Date_Out???
		for (Delivery d : allDeliveries.values()) {
			for (String nextId : d.getAllNextIds()) {
				Delivery next = allDeliveries.get(nextId);

				if (!d.isBefore(next)) {
					String warningMessage = "Dates correct?? In: " + d.getId() + " ("
							+ sdfFormat(d.getArrivalDay(), d.getArrivalMonth(), d.getArrivalYear()) + ") vs. Out: "
							+ next.getId() + " ("
							+ sdfFormat(next.getDepartureDay(), next.getDepartureMonth(), next.getDepartureYear())
							+ ")";

					System.err.println(warningMessage);
					this.setWarningMessage(warningMessage);
					warningsThere = true;
				}
			}
		}
		// Sum(In) <=> Sum(Out)???
		String sql = "select GROUP_CONCAT(\"id1\") AS \"ids_in\",sum(\"Amount_In\") AS \"Amount_In\",min(\"Amount_Out\") AS \"Amount_Out\",min(\"id2\") as \"ids_out\" from (SELECT min(\"L1\".\"Serial\") AS \"id1\",GROUP_CONCAT(\"L2\".\"Serial\") AS \"id2\",min(\"L1\".\"Unitmenge\") AS \"Amount_In\",sum(\"L2\".\"Unitmenge\") AS \"Amount_Out\" FROM \"Lieferungen\" AS \"L1\" LEFT JOIN \"ChargenVerbindungen\" ON \"L1\".\"ID\"=\"ChargenVerbindungen\".\"Zutat\" LEFT JOIN \"Lieferungen\" AS \"L2\" ON \"L2\".\"Charge\"=\"ChargenVerbindungen\".\"Produkt\" WHERE \"ChargenVerbindungen\".\"ID\" IS NOT NULL GROUP BY \"L1\".\"ID\") GROUP BY \"id2\"";
		ResultSet rsp = DBKernel.getResultSet(conn, sql, false);
		if (rsp != null && rsp.first()) {
			do {
				if (rsp.getObject("Amount_In") != null && rsp.getObject("Amount_Out") != null) {
					double in = rsp.getDouble("Amount_In");
					double out = rsp.getDouble("Amount_Out");
					if (in > out * 2 || out > in * 2) { // 1.1
						String warningMessage = "Amounts correct?? In: " + rsp.getString("ids_in") + " (" + in
								+ ") vs. Out: " + rsp.getString("ids_out") + " (" + out + ")";
						System.err.println(warningMessage);
						this.setWarningMessage(warningMessage);
						warningsThere = true;
					}
				}
			} while (rsp.next());
		}
		// numPU, typePU
		sql = "SELECT GROUP_CONCAT(\"id1\") AS \"ids_in\",SUM(\"Amount_In\") AS \"Amount_In\",MIN(\"Amount_Out\") AS \"Amount_Out\",MIN(\"id2\") AS \"ids_out\",\"Type_In\",\"Type_Out\" FROM "
				+ " (SELECT MIN(\"L1\".\"Serial\") AS \"id1\",GROUP_CONCAT(\"L2\".\"Serial\") AS \"id2\",MIN(\"L1\".\"numPU\") AS \"Amount_In\",\"L1\".\"typePU\" AS \"Type_In\",SUM(\"L2\".\"numPU\") AS \"Amount_Out\",\"L2\".\"typePU\" AS \"Type_Out\" FROM "
				+ " \"Lieferungen\" AS \"L1\" LEFT JOIN \"ChargenVerbindungen\" ON \"L1\".\"ID\"=\"ChargenVerbindungen\".\"Zutat\" LEFT JOIN \"Lieferungen\" AS \"L2\" ON \"L2\".\"Charge\"=\"ChargenVerbindungen\".\"Produkt\" "
				+ " WHERE \"ChargenVerbindungen\".\"ID\" IS NOT NULL AND \"L1\".\"typePU\" = \"L2\".\"typePU\" GROUP BY \"L1\".\"ID\",\"L1\".\"typePU\",\"L2\".\"typePU\") "
				+ " WHERE \"Type_In\" = \"Type_Out\" " + " GROUP BY \"id2\",\"Type_In\",\"Type_Out\"";
		rsp = DBKernel.getResultSet(conn, sql, false);
		if (rsp != null && rsp.first()) {
			do {
				if (rsp.getObject("Amount_In") != null && rsp.getObject("Amount_Out") != null) {
					double in = rsp.getDouble("Amount_In");
					double out = rsp.getDouble("Amount_Out");
					if (in > out * 2 || out > in * 2) { // 1.1
						String warningMessage = "Amounts correct?? In: " + rsp.getString("ids_in") + " (" + in
								+ ") vs. Out: " + rsp.getString("ids_out") + " (" + out + ")";
						System.err.println(warningMessage);
						this.setWarningMessage(warningMessage);
						warningsThere = true;
					}
				}
			} while (rsp.next());
		}
		if (warningsThere)
			this.setWarningMessage("Look into the console - there are plausibility issues...");

		boolean useSerialAsID = serialPossible(conn);
		HashMap<String, String> hmStationIDs = new HashMap<>();
		HashMap<String, String> hmDeliveryIDs = new HashMap<>();
		int nodeIndex = 0;
		boolean isBVLFormat = false;
		DataTableSpec nodeSpec = output33Nodes.getTableSpec();

		for (Record r : DSL.using(conn, SQLDialect.HSQLDB).select().from(STATION)) {
			String sID = r.getValue(STATION.ID).toString();
			String stationID = useSerialAsID ? r.getValue(STATION.SERIAL) : sID;
			if (useSerialAsID)
				hmStationIDs.put(sID, stationID);
			String district = null;
			String bll = clean(r.getValue(STATION.BUNDESLAND));
			if (nodeIndex == 0 && bll != null && (bll.equals("Altenburger Land") || bll.equals("Wesel")))
				isBVLFormat = true;
			// if (!antiArticle ||
			// !checkCompanyReceivedArticle(stationID,
			// articleFilterList) || !checkCase(stationID)) {
			String country = clean(r.getValue(STATION.LAND));
			// getBL(clean(rs.getString("Land"),
			// 3);
			String zip = clean(r.getValue(STATION.PLZ));
			// Integer cp = rs.getObject("CasePriority") == null ? null
			// : rs.getInt("CasePriority");
			if (isBVLFormat) {
				district = bll;
				bll = country;
				if (zip != null && zip.length() == 4)
					country = "BE";
				else
					country = "DE";
			}
			String bl = getBL(bll);
			String company = (r.getValue(STATION.NAME) == null || doAnonymize)
					? getAnonymizedStation(bl, sID.hashCode(), country) : clean(r.getValue(STATION.NAME));
			DataCell[] cells = new DataCell[nodeSpec.getNumColumns()];

			fillCell(nodeSpec, cells, TracingColumns.ID, new StringCell(stationID));

			fillCell(nodeSpec, cells, TracingColumns.STATION_NODE, new StringCell(company));
			fillCell(nodeSpec, cells, TracingColumns.STATION_NAME, new StringCell(company));
			fillCell(nodeSpec, cells, TracingColumns.STATION_STREET,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(STATION.STRASSE)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_HOUSENO,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(STATION.HAUSNUMMER)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_ZIP,
					zip == null ? DataType.getMissingCell() : new StringCell(zip));
			fillCell(nodeSpec, cells, TracingColumns.STATION_CITY,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(STATION.ORT)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_DISTRICT,
					doAnonymize || district == null ? DataType.getMissingCell() : new StringCell(district));
			fillCell(nodeSpec, cells, TracingColumns.STATION_STATE,
					doAnonymize || bll == null ? DataType.getMissingCell() : new StringCell(bll));
			fillCell(nodeSpec, cells, TracingColumns.STATION_COUNTRY,
					doAnonymize || country == null ? DataType.getMissingCell() : new StringCell(country));

			fillCell(nodeSpec, cells, TracingColumns.STATION_VAT,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(STATION.VATNUMBER)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_TOB, createCell(r.getValue(STATION.BETRIEBSART)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_NUMCASES, createCell(r.getValue(STATION.ANZAHLFAELLE)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_DATESTART, createCell(r.getValue(STATION.DATUMBEGINN)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_DATEPEAK, createCell(r.getValue(STATION.DATUMHOEHEPUNKT)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_DATEEND, createCell(r.getValue(STATION.DATUMENDE)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_SERIAL, createCell(r.getValue(STATION.SERIAL)));
			fillCell(nodeSpec, cells, TracingColumns.STATION_SIMPLESUPPLIER,
					isSimpleSupplier(allDeliveries, sID) ? BooleanCell.TRUE : BooleanCell.FALSE);
			fillCell(nodeSpec, cells, TracingColumns.STATION_DEADSTART,
					isStationStart(allDeliveries, sID) ? BooleanCell.TRUE : BooleanCell.FALSE);
			fillCell(nodeSpec, cells, TracingColumns.STATION_DEADEND,
					isStationEnd(allDeliveries, sID) ? BooleanCell.TRUE : BooleanCell.FALSE);

			fillCell(nodeSpec, cells, TracingColumns.FILESOURCES, createCell(r.getValue(STATION.IMPORTSOURCES)));

			fillCell(nodeSpec, cells, TracingColumns.STATION_COUNTY,
					doAnonymize || bll == null ? DataType.getMissingCell() : new StringCell(bll));

			// Extras
			for (String extraCol : nodeSpec.getColumnNames()) {
				if (extraCol.startsWith("_")) {
					String attribute = extraCol.substring(1);
					Result<Record1<String>> result = DSL.using(conn, SQLDialect.HSQLDB).select(EXTRAFIELDS.VALUE)
							.from(EXTRAFIELDS)
							.where(EXTRAFIELDS.TABLENAME.equal(STATION.getName()),
									EXTRAFIELDS.ID.equal(Integer.parseInt(sID)), EXTRAFIELDS.ATTRIBUTE.equal(attribute))
							.fetch();

					if (!result.isEmpty()) {
						fillCell(nodeSpec, cells, extraCol, createCell(result.get(0).value1()));
					} else {
						fillCell(nodeSpec, cells, extraCol, DataType.getMissingCell());
					}
				}
			}

			DataRow outputRow = new DefaultRow("Row" + nodeIndex++, cells);

			output33Nodes.addRowToTable(outputRow);
			exec.checkCanceled();
		}

		// Alle Lieferungen -> Links33
		int edgeIndex = 0;
		DataTableSpec edgeSpec = output33Links.getTableSpec();

		for (Record r : DSL.using(conn, SQLDialect.HSQLDB).select().from(LIEFERUNGEN).leftOuterJoin(CHARGEN)
				.on(LIEFERUNGEN.CHARGE.equal(CHARGEN.ID)).leftOuterJoin(PRODUKTKATALOG)
				.on(CHARGEN.ARTIKEL.equal(PRODUKTKATALOG.ID)).orderBy(PRODUKTKATALOG.ID)) {
			String lID = r.getValue(LIEFERUNGEN.ID).toString();
			String lieferID = useSerialAsID ? r.getValue(LIEFERUNGEN.SERIAL) : lID;
			String id1 = r.getValue(PRODUKTKATALOG.STATION).toString();
			String id2 = r.getValue(LIEFERUNGEN.EMPFÄNGER).toString();
			if (useSerialAsID) {
				hmDeliveryIDs.put(lID, lieferID);
				id1 = hmStationIDs.get(id1);
				id2 = hmStationIDs.get(id2);
			}
			String from = id1;
			String to = id2;
			DataCell[] cells = new DataCell[edgeSpec.getNumColumns()];

			fillCell(edgeSpec, cells, TracingColumns.ID, new StringCell(lieferID));
			fillCell(edgeSpec, cells, TracingColumns.FROM, new StringCell(from));
			fillCell(edgeSpec, cells, TracingColumns.TO, new StringCell(to));

			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_ITEMNAME,
					createCell(r.getValue(PRODUKTKATALOG.BEZEICHNUNG)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_ITEMNUM,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(PRODUKTKATALOG.ARTIKELNUMMER)));
			String dd = sdfFormat(r.getValue(LIEFERUNGEN.DD_DAY), r.getValue(LIEFERUNGEN.DD_MONTH),
					r.getValue(LIEFERUNGEN.DD_YEAR));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_DEPARTURE,
					dd == null ? DataType.getMissingCell() : new StringCell(dd));
			String ad = sdfFormat(r.getValue(LIEFERUNGEN.AD_DAY), r.getValue(LIEFERUNGEN.AD_MONTH),
					r.getValue(LIEFERUNGEN.AD_YEAR));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_ARRIVAL,
					ad == null ? DataType.getMissingCell() : new StringCell(ad));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_SERIAL, createCell(r.getValue(LIEFERUNGEN.SERIAL)));

			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_PROCESSING,
					createCell(r.getValue(PRODUKTKATALOG.PROZESSIERUNG)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_USAGE,
					createCell(r.getValue(PRODUKTKATALOG.INTENDEDUSE)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_LOTNUM,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(CHARGEN.CHARGENNR)));
			String mhd = sdfFormat(r.getValue(CHARGEN.MHD_DAY), r.getValue(CHARGEN.MHD_MONTH),
					r.getValue(CHARGEN.MHD_YEAR));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_DATEEXP,
					mhd == null ? DataType.getMissingCell() : new StringCell(mhd));
			String pd = sdfFormat(r.getValue(CHARGEN.PD_DAY), r.getValue(CHARGEN.PD_MONTH),
					r.getValue(CHARGEN.PD_YEAR));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_DATEMANU,
					pd == null ? DataType.getMissingCell() : new StringCell(pd));
			Double menge = calcMenge(r.getValue(LIEFERUNGEN.UNITMENGE), r.getValue(LIEFERUNGEN.UNITEINHEIT));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_AMOUNT,
					menge == null ? DataType.getMissingCell() : new DoubleCell(menge / 1000.0));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_NUM_PU, createCell(r.getValue(LIEFERUNGEN.NUMPU)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_TYPE_PU, createCell(r.getValue(LIEFERUNGEN.TYPEPU)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_ENDCHAIN, createCell(r.getValue(LIEFERUNGEN.ENDCHAIN)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_ORIGIN, createCell(r.getValue(CHARGEN.ORIGINCOUNTRY)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_ENDCHAINWHY,
					createCell(r.getValue(LIEFERUNGEN.EXPLANATION_ENDCHAIN)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_REMARKS,
					createCell(r.getValue(LIEFERUNGEN.CONTACT_QUESTIONS_REMARKS)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_FURTHERTB,
					createCell(r.getValue(LIEFERUNGEN.FURTHER_TRACEBACK)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_MICROSAMPLE,
					createCell(r.getValue(CHARGEN.MICROBIOSAMPLE)));
			fillCell(edgeSpec, cells, TracingColumns.FILESOURCES, createCell(r.getValue(LIEFERUNGEN.IMPORTSOURCES)));
			fillCell(edgeSpec, cells, TracingColumns.DELIVERY_CHARGENUM,
					doAnonymize ? DataType.getMissingCell() : createCell(r.getValue(CHARGEN.CHARGENNR)));

			// Extras
			for (String extraCol : edgeSpec.getColumnNames()) {
				if (extraCol.startsWith("_")) {
					String attribute = extraCol.substring(1);
					int index = attribute.indexOf(".");
					String tn = attribute.substring(0, index);
					String fn = attribute.substring(index + 1);

					Result<Record1<String>> result = DSL.using(conn, SQLDialect.HSQLDB).select(EXTRAFIELDS.VALUE)
							.from(EXTRAFIELDS).where(EXTRAFIELDS.TABLENAME.equal(tn),
									EXTRAFIELDS.ID.equal(Integer.parseInt(lID)), EXTRAFIELDS.ATTRIBUTE.equal(fn))
							.fetch();

					if (!result.isEmpty()) {
						fillCell(edgeSpec, cells, extraCol, createCell(result.get(0).value1()));
					} else {
						fillCell(edgeSpec, cells, extraCol, DataType.getMissingCell());
					}
				}
			}

			DataRow outputRow = new DefaultRow("Row" + edgeIndex++, cells);

			output33Links.addRowToTable(outputRow);
			exec.checkCanceled();
		}

		int i = 0;

		for (Delivery delivery : allDeliveries.values()) {
			for (String next : delivery.getAllNextIds()) {
				if (useSerialAsID)
					deliveryDelivery
							.addRowToTable(new DefaultRow(i + "", IO.createCell(hmDeliveryIDs.get(delivery.getId())),
									IO.createCell(hmDeliveryIDs.get(next))));
				else
					deliveryDelivery.addRowToTable(
							new DefaultRow(i + "", IO.createCell(delivery.getId()), IO.createCell(next)));
				i++;
			}
		}

		output33Nodes.close();
		output33Links.close();
		deliveryDelivery.close();

		return new BufferedDataTable[] { output33Nodes.getTable(), output33Links.getTable(),
				deliveryDelivery.getTable() };
	}

	private void fillCell(DataTableSpec spec, DataCell[] cells, String columnname, DataCell value) {
		int index = spec.findColumnIndex(columnname);
		if (index >= 0)
			cells[index] = value;
	}

	private static DataCell createCell(String s) {
		return s != null ? new StringCell(clean(s)) : DataType.getMissingCell();
	}

	private static DataCell createCell(Date d) {
		return d != null ? new StringCell(d.toString()) : DataType.getMissingCell();
	}

	private static DataCell createCell(Integer i) {
		return i != null ? new IntCell(i) : DataType.getMissingCell();
	}

	private static DataCell createCell(Double d) {
		return d != null ? new DoubleCell(d) : DataType.getMissingCell();
	}

	private static String clean(String s) {
		if (s == null || s.equalsIgnoreCase("null")) {
			return null;
		}

		return s.replace("\n", "|").replaceAll("\\p{C}", "").replace("\u00A0", "").replace("\t", " ").trim();
	}

	private String getISO3166_2(String country, String bl) {
		Locale locale = Locale.ENGLISH;// Locale.GERMAN;
		for (String code : Locale.getISOCountries()) {
			if (new Locale("", code).getDisplayCountry(locale).equals(country)) {
				return code;
			}
		}
		if (bl != null && bl.length() > 1)
			return getBL(bl);
		return "N.N";
	}

	private String getAnonymizedStation(String bl, int stationID, String country) {
		return getISO3166_2(country, bl) + "#" + stationID;// bl + stationID +
															// "(" + country +
															// ")";
	}

	private String sdfFormat(Integer day, Integer month, Integer year) {
		if (year == null)
			return null;

		String thisYear = new SimpleDateFormat("yyyy").format(new Date());

		if (year.toString().length() == 2)
			year = year > Integer.parseInt(thisYear.substring(2)) ? 1900 : 2000 + year;

		if (month == null) {
			return year.toString();
		} else if (day == null) {
			return year + "-" + new DecimalFormat("00").format(month);
		}

		return year + "-" + new DecimalFormat("00").format(month) + "-" + new DecimalFormat("00").format(day);
	}

	private Double calcMenge(Double u3, String bu3) {
		Double result = null;
		if (u3 != null && bu3 != null) {
			if (bu3.equalsIgnoreCase("t"))
				result = u3 * 1000000;
			else if (bu3.equalsIgnoreCase("kg"))
				result = u3 * 1000;
			else
				result = u3; // if (bu3s.equalsIgnoreCase("g"))
		}
		return result;
	}

	private DataTableSpec getDataModelSpec() {
		DataColumnSpec[] spec = new DataColumnSpec[2];
		spec[0] = new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec();
		spec[1] = new DataColumnSpecCreator(TracingColumns.NEXT, StringCell.TYPE).createSpec();
		return new DataTableSpec(spec);
	}

	private DataTableSpec getSpec33Nodes(Connection conn) {
		List<DataColumnSpec> columns = new ArrayList<>();
		columns.add(new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_NODE, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_NAME, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_STREET, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_HOUSENO, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_ZIP, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_CITY, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DISTRICT, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_STATE, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_COUNTRY, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_SERIAL, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_SIMPLESUPPLIER, BooleanCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DEADSTART, BooleanCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DEADEND, BooleanCell.TYPE).createSpec());

		if (containsValues(conn, STATION.VATNUMBER))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_VAT, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.BETRIEBSART))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_TOB, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.ANZAHLFAELLE))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_NUMCASES, IntCell.TYPE).createSpec());
		if (containsValues(conn, STATION.DATUMBEGINN))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DATESTART, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.DATUMHOEHEPUNKT))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DATEPEAK, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.DATUMENDE))
			columns.add(new DataColumnSpecCreator(TracingColumns.STATION_DATEEND, StringCell.TYPE).createSpec());
		if (containsValues(conn, STATION.IMPORTSOURCES))
			columns.add(new DataColumnSpecCreator(TracingColumns.FILESOURCES, StringCell.TYPE).createSpec());

		// due to backward compatibility:
		columns.add(new DataColumnSpecCreator(TracingColumns.STATION_COUNTY, StringCell.TYPE).createSpec());

		// ExtraFields
		for (Record1<String> r : DSL.using(conn, SQLDialect.HSQLDB).selectDistinct(EXTRAFIELDS.ATTRIBUTE)
				.from(EXTRAFIELDS).where(EXTRAFIELDS.TABLENAME.equal(STATION.getName()))) {
			columns.add(new DataColumnSpecCreator("_" + r.value1(), StringCell.TYPE).createSpec());
		}

		return new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
	}

	private DataTableSpec getSpec33Links(Connection conn) {
		List<DataColumnSpec> columns = new ArrayList<>();
		columns.add(new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.FROM, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.TO, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ITEMNUM, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ITEMNAME, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_DEPARTURE, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ARRIVAL, StringCell.TYPE).createSpec());
		columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_SERIAL, StringCell.TYPE).createSpec());

		if (containsValues(conn, PRODUKTKATALOG.PROZESSIERUNG))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_PROCESSING, StringCell.TYPE).createSpec());
		if (containsValues(conn, PRODUKTKATALOG.INTENDEDUSE))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_USAGE, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.CHARGENNR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_LOTNUM, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.MHD_DAY, CHARGEN.MHD_MONTH, CHARGEN.MHD_YEAR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_DATEEXP, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.PD_DAY, CHARGEN.PD_MONTH, CHARGEN.PD_YEAR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_DATEMANU, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.UNITMENGE))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_AMOUNT, DoubleCell.TYPE).createSpec());

		if (containsValues(conn, LIEFERUNGEN.NUMPU)) {
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_NUM_PU, DoubleCell.TYPE).createSpec());
			if (containsValues(conn, LIEFERUNGEN.TYPEPU))
				columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_TYPE_PU, StringCell.TYPE).createSpec());
		}

		if (containsValues(conn, CHARGEN.ORIGINCOUNTRY))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ORIGIN, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.ENDCHAIN))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ENDCHAIN, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.EXPLANATION_ENDCHAIN))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_ENDCHAINWHY, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.CONTACT_QUESTIONS_REMARKS))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_REMARKS, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.FURTHER_TRACEBACK))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_FURTHERTB, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.MICROBIOSAMPLE))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_MICROSAMPLE, StringCell.TYPE).createSpec());
		if (containsValues(conn, LIEFERUNGEN.IMPORTSOURCES))
			columns.add(new DataColumnSpecCreator(TracingColumns.FILESOURCES, StringCell.TYPE).createSpec());
		if (containsValues(conn, CHARGEN.CHARGENNR))
			columns.add(new DataColumnSpecCreator(TracingColumns.DELIVERY_CHARGENUM, StringCell.TYPE).createSpec());

		// ExtraFields
		for (Record2<String, String> r : DSL.using(conn, SQLDialect.HSQLDB)
				.selectDistinct(EXTRAFIELDS.TABLENAME, EXTRAFIELDS.ATTRIBUTE).from(EXTRAFIELDS)
				.where(EXTRAFIELDS.TABLENAME.equal(PRODUKTKATALOG.getName()))
				.or(EXTRAFIELDS.TABLENAME.equal(CHARGEN.getName()))
				.or(EXTRAFIELDS.TABLENAME.equal(LIEFERUNGEN.getName()))) {
			columns.add(new DataColumnSpecCreator("_" + r.value1() + "." + r.value2(), StringCell.TYPE).createSpec());
		}

		return new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
	}

	private boolean containsValues(Connection conn, TableField<?, ?>... fields) {
		for (TableField<?, ?> field : fields) {
			for (Record1<?> r : DSL.using(conn, SQLDialect.HSQLDB).selectDistinct(field).from(field.getTable())) {
				if (r.value1() != null) {
					return true;
				}
			}
		}

		return false;
	}

	private String getBL(String bl) {
		return getBL(bl, 2);
	}

	private String getBL(String bl, int numCharsMax) {
		String result = bl;
		if (result == null || result.trim().isEmpty() || result.trim().equalsIgnoreCase("null"))
			result = "NN";
		if (result.length() > numCharsMax) {
			result = result.substring(0, numCharsMax);
		}
		return result;
	}

	private static Connection getNewLocalConnection(final String dbUsername, final String dbPassword,
			final String dbFile) throws Exception {
		Connection result = null;
		Class.forName("org.hsqldb.jdbc.JDBCDriver").newInstance();
		String connStr = "jdbc:hsqldb:file:" + dbFile;
		try {
			result = DriverManager.getConnection(connStr, dbUsername, dbPassword);
			result.setReadOnly(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
		return new DataTableSpec[] { null, null, null };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		settings.addString(PARAM_FILENAME, filename);
		settings.addBoolean(PARAM_OVERRIDE, override);
		settings.addBoolean(PARAM_ANONYMIZE, doAnonymize);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		filename = settings.getString(PARAM_FILENAME);
		override = settings.getBoolean(PARAM_OVERRIDE);
		doAnonymize = settings.getBoolean(PARAM_ANONYMIZE, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	private static boolean isStationStart(Map<String, Delivery> deliveries, String id) {
		for (Delivery d : deliveries.values()) {
			if (d.getRecipientId().equals(id)) {
				return false;
			}
		}

		return true;
	}

	private static boolean isSimpleSupplier(Map<String, Delivery> deliveries, String id) {
		if (isStationStart(deliveries, id)) {
			String recId = null;
			for (Delivery d : deliveries.values()) {
				if (d.getSupplierId().equals(id)) {
					if (recId == null)
						recId = d.getRecipientId();
					else if (!recId.equals(d.getRecipientId()))
						return false;
				}
			}
			return true;
		}
		return false;
	}

	private static boolean isStationEnd(Map<String, Delivery> deliveries, String id) {
		for (Delivery d : deliveries.values()) {
			if (d.getSupplierId().equals(id)) {
				return false;
			}
		}

		return true;
	}

	private static Map<String, Delivery> getNewTracingModel(MyDBI myDBi, Connection conn) {
		Map<String, Delivery> allDeliveries = new LinkedHashMap<>();

		Select<Record> deliverySelect = DSL.using(conn, SQLDialect.HSQLDB).select().from(LIEFERUNGEN)
				.leftOuterJoin(CHARGEN).on(LIEFERUNGEN.CHARGE.equal(CHARGEN.ID)).leftOuterJoin(PRODUKTKATALOG)
				.on(CHARGEN.ARTIKEL.equal(PRODUKTKATALOG.ID));

		for (Record r : deliverySelect) {
			Delivery d = new Delivery(r.getValue(LIEFERUNGEN.ID).toString(),
					r.getValue(PRODUKTKATALOG.STATION).toString(), r.getValue(LIEFERUNGEN.EMPFÄNGER).toString(),
					r.getValue(LIEFERUNGEN.DD_DAY), r.getValue(LIEFERUNGEN.DD_MONTH), r.getValue(LIEFERUNGEN.DD_YEAR),
					r.getValue(LIEFERUNGEN.AD_DAY), r.getValue(LIEFERUNGEN.AD_MONTH), r.getValue(LIEFERUNGEN.AD_YEAR));

			allDeliveries.put(d.getId(), d);
		}

		Select<Record> deliveryToDeliverySelect = DSL.using(conn, SQLDialect.HSQLDB).select().from(CHARGENVERBINDUNGEN)
				.leftOuterJoin(LIEFERUNGEN).on(CHARGENVERBINDUNGEN.PRODUKT.equal(LIEFERUNGEN.CHARGE));

		for (Record r : deliveryToDeliverySelect) {
			Delivery from = allDeliveries.get(r.getValue(CHARGENVERBINDUNGEN.ZUTAT).toString());
			Delivery to = allDeliveries.get(r.getValue(LIEFERUNGEN.ID).toString());

			if (from != null && to != null) {
				from.getAllNextIds().add(to.getId());
				to.getAllPreviousIds().add(from.getId());
			}
		}

		return allDeliveries;
	}

	private static boolean serialPossible(Connection conn) {
		HashSet<String> hs = new HashSet<>();
		String sql = "SELECT " + DBKernel.delimitL("Serial") + " FROM " + DBKernel.delimitL("Station");
		try {
			ResultSet rs = DBKernel.getResultSet(conn, sql, false);
			if (rs != null && rs.first()) {
				do {
					if (rs.getObject("Serial") == null) {
						return false;
					}
					String s = rs.getString("Serial");
					if (hs.contains(s))
						return false;
					hs.add(s);
				} while (rs.next());
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
		hs.clear();
		sql = "SELECT " + DBKernel.delimitL("Serial") + "," + DBKernel.delimitL("UnitEinheit") + " FROM "
				+ DBKernel.delimitL("Lieferungen");
		try {
			boolean alwaysUEkg = true;
			ResultSet rs = DBKernel.getResultSet(conn, sql, false);
			if (rs != null && rs.first()) {
				do {
					if (rs.getObject("Serial") == null) {
						return false;
					}
					String s = rs.getString("Serial");
					if (hs.contains(s))
						return false;
					hs.add(s);
					if (rs.getObject("UnitEinheit") == null || !rs.getString("UnitEinheit").equals("kg"))
						alwaysUEkg = false;
				} while (rs.next());
			}
			if (alwaysUEkg) {
				return false;
				// beim EFSA Importer wurde immer kg eingetragen, später beim
				// bfrnewimporter wurde nur noch "numPU" und "typePU" benutzt
				// und UnitEinheit müsste immer NULL sein, daher ist das ein
				// sehr gutes Indiz daafür, dass wir es mit alten Daten zu tun
				// haben
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
