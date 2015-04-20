package org.hsh.bfr.db.imports.custom.bfrnewformat;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map.Entry;

import org.hsh.bfr.db.DBKernel;

public class DeliveryLot {

	private HashMap<String, String> flexibles = new HashMap<>();

	public void addFlexibleField(String key, String value) {
		flexibles.put(key, value);
	}

	public void getId(Integer dbId, Integer dbPId, Integer miDbId) throws Exception {
		String sql = "SELECT " + DBKernel.delimitL("ID") + " FROM " + DBKernel.delimitL("ChargenVerbindungen") +
				" WHERE " + DBKernel.delimitL("Zutat") + "=" + dbId + " AND " + DBKernel.delimitL("Produkt") + "=" + dbPId;
		ResultSet rs = DBKernel.getResultSet(sql, false);
		Integer result = null;
		if (rs != null && rs.first()) {
			result = rs.getInt(1);
		}
		
		if (result != null) {
			DBKernel.sendRequest("UPDATE " + DBKernel.delimitL("ChargenVerbindungen") + " SET " + DBKernel.delimitL("ImportSources") + "=CASEWHEN(INSTR(';" + miDbId + ";'," + DBKernel.delimitL("ImportSources") + ")=0,CONCAT(" + DBKernel.delimitL("ImportSources") + ", '" + miDbId + ";'), " + DBKernel.delimitL("ImportSources") + ") WHERE " + DBKernel.delimitL("ID") + "=" + result, false);
		}
		else {
			sql = "INSERT INTO " + DBKernel.delimitL("ChargenVerbindungen") +
					" (" + DBKernel.delimitL("Zutat") + "," + DBKernel.delimitL("Produkt") + "," + DBKernel.delimitL("ImportSources") +
					") VALUES (" + dbId + "," + dbPId + ",';" + miDbId + ";')";
			//DBKernel.sendRequest(sql, false);
			PreparedStatement ps = DBKernel.getDBConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			if (ps.executeUpdate() > 0) {
				result = DBKernel.getLastInsertedID(ps);
				
				// Further flexible cells
				if (result != null) {
					for (Entry<String, String> es : flexibles.entrySet()) {
						DBKernel.sendRequest("INSERT INTO " + DBKernel.delimitL("ExtraFields") +
								" (" + DBKernel.delimitL("tablename") + "," + DBKernel.delimitL("id") + "," + DBKernel.delimitL("attribute") + "," + DBKernel.delimitL("value") +
								") VALUES ('ChargenVerbindungen'," + result + ",'" + es.getKey() + "','" + es.getValue() + "')", false);
					}
				}
			}
		}		

	}
}
