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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

public class Tracing {

	private Map<String, Delivery> deliveries;
	private Map<String, Double> stationWeights;
	private Map<String, Double> deliveryWeights;
	private Set<String> ccStations;
	private Set<String> ccDeliveries;

	private Map<String, Set<String>> allIncoming;
	private Map<String, Set<String>> allOutgoing;
	private Map<String, Set<String>> backwardDeliveries;
	private Map<String, Set<String>> forwardDeliveries;
	private Map<String, Set<String>> sortedStations;
	private Map<String, Set<String>> sortedDeliveries;
	private double weightSum;

	public Tracing(Map<String, Delivery> deliveries) {
		this.deliveries = new LinkedHashMap<>();

		for (Delivery d : deliveries.values()) {
			Delivery copy = new Delivery(d.getId(), d.getSupplierID(), d.getRecipientID(),
					d.getDeliveryDay(), d.getDeliveryMonth(), d.getDeliveryYear());

			copy.getAllNextIDs().addAll(Sets.intersection(d.getAllNextIDs(), deliveries.keySet()));
			copy.getAllPreviousIDs().addAll(
					Sets.intersection(d.getAllPreviousIDs(), deliveries.keySet()));

			this.deliveries.put(copy.getId(), copy);
		}

		this.stationWeights = new LinkedHashMap<>();
		this.ccStations = new LinkedHashSet<>();
		this.deliveryWeights = new LinkedHashMap<>();
		this.ccDeliveries = new LinkedHashSet<>();
		this.weightSum = 0;

		backwardDeliveries = new LinkedHashMap<>();
		forwardDeliveries = new LinkedHashMap<>();
	}

	public Map<String, Delivery> getDeliveries() {
		return deliveries;
	}

	private Map<String, Set<String>> getAllIncoming() {
		if (allIncoming == null) {
			allIncoming = new LinkedHashMap<>();
			for (Delivery d : deliveries.values()) {
				String rid = d.getRecipientID();
				if (!allIncoming.containsKey(rid))
					allIncoming.put(rid, new LinkedHashSet<String>());
				allIncoming.get(rid).add(d.getId());
			}
		}
		return allIncoming;
	}

	private Map<String, Set<String>> getAllOutgoing() {
		if (allOutgoing == null) {
			allOutgoing = new LinkedHashMap<>();
			for (Delivery d : deliveries.values()) {
				String sid = d.getSupplierID();
				if (!allOutgoing.containsKey(sid))
					allOutgoing.put(sid, new LinkedHashSet<String>());
				allOutgoing.get(sid).add(d.getId());
			}
		}
		return allOutgoing;
	}

	public boolean isStationStart(String id) {
		for (Delivery d : deliveries.values()) {
			if (d.getRecipientID().equals(id)) {
				return false;
			}
		}

		return true;
	}

	public boolean isSimpleSupplier(String id) {
		if (isStationStart(id)) {
			String recId = null;
			for (Delivery d : deliveries.values()) {
				if (d.getSupplierID().equals(id)) {
					if (recId == null)
						recId = d.getRecipientID();
					else if (!recId.equals(d.getRecipientID()))
						return false;
				}
			}
			return true;
		}
		return false;
	}

	public boolean isStationEnd(String id) {
		for (Delivery d : deliveries.values()) {
			if (d.getSupplierID().equals(id)) {
				return false;
			}
		}

		return true;
	}

	public double getStationScore(String id) {
		if (sortedStations == null)
			getScores();
		if (weightSum > 0 && sortedStations.get(id) != null) {
			double sum = 0;
			for (String key : sortedStations.get(id)) {
				if (key.startsWith("-")) {
					key = key.substring(1);

					if (deliveryWeights.get(key) != null) {
						sum += deliveryWeights.get(key);
					}
				} else {
					if (stationWeights.get(key) != null) {
						sum += stationWeights.get(key);
					}
				}
			}
			if (stationWeights.containsKey(id))
				sum += stationWeights.get(id);
			return sum / weightSum;
		}
		if (weightSum > 0 && stationWeights.containsKey(id))
			return stationWeights.get(id) / weightSum;
		return 0.0;
	}

	public double getDeliveryScore(String id) {
		if (sortedDeliveries == null)
			getScores();
		if (weightSum > 0 && sortedDeliveries.get(id) != null) {
			double sum = 0;
			for (String key : sortedDeliveries.get(id)) {
				if (key.startsWith("-"))
					sum += deliveryWeights.get(key.substring(1));
				else
					sum += stationWeights.get(key);
			}
			return sum / weightSum;
			// return ((double) sortedDeliveries.get(id).size()) /
			// caseStations.size();
		}
		return 0.0;
	}

	private void getScores() {
		// getForwardStationsWithCases counts for each delivery. But: it might
		// be the case that a station delivers into "different" directions
		// (deliveries), and all of them have cases!!!
		// Therefore, we sum here based on the suppliers (supplierSum), not on
		// the deliveries!!!
		sortedStations = new LinkedHashMap<>();
		sortedDeliveries = new LinkedHashMap<>();

		for (Delivery md : deliveries.values()) {
			Set<String> fwc = new LinkedHashSet<>();

			fwc.addAll(getForwardStationsWithCases(md));
			fwc.addAll(getForwardDeliveriesWithCases(md));

			if (sortedStations.containsKey(md.getSupplierID())) {
				sortedStations.get(md.getSupplierID()).addAll(fwc);
			} else {
				sortedStations.put(md.getSupplierID(), new LinkedHashSet<>(fwc));
			}

			sortedDeliveries.put(md.getId(), new LinkedHashSet<>(fwc));
		}
	}

	public Set<String> getForwardStations(String stationID) {
		Set<String> stations = new LinkedHashSet<>();

		if (getAllOutgoing().get(stationID) != null) {
			for (String i : getAllOutgoing().get(stationID)) {
				Delivery md = deliveries.get(i);
				stations.addAll(getForwardStations(md));
			}
		}

		return stations;
	}

	public Set<String> getBackwardStations(String stationID) {
		Set<String> stations = new LinkedHashSet<>();

		if (getAllIncoming().get(stationID) != null) {
			for (String i : getAllIncoming().get(stationID)) {
				Delivery md = deliveries.get(i);
				stations.addAll(getBackwardStations(md));
			}
		}

		return stations;
	}

	public Set<String> getForwardDeliveries(String stationID) {
		Set<String> forward = new LinkedHashSet<>();

		if (getAllOutgoing().get(stationID) != null) {
			for (String i : getAllOutgoing().get(stationID)) {
				forward.addAll(getForwardDeliveries(deliveries.get(i)));
			}
		}

		return forward;
	}

	public Set<String> getBackwardDeliveries(String stationID) {
		Set<String> backward = new LinkedHashSet<>();

		if (getAllIncoming().get(stationID) != null) {
			for (String i : getAllIncoming().get(stationID)) {
				backward.addAll(getBackwardDeliveries(deliveries.get(i)));
			}
		}

		return backward;
	}

	public Set<String> getForwardStations2(String deliveryId) {
		return getForwardStations(deliveries.get(deliveryId));
	}

	public Set<String> getBackwardStations2(String deliveryId) {
		return getBackwardStations(deliveries.get(deliveryId));
	}

	public Set<String> getForwardDeliveries2(String deliveryId) {
		Set<String> f = new LinkedHashSet<>(getForwardDeliveries(deliveries.get(deliveryId)));

		f.remove(deliveryId);

		return f;
	}

	public Set<String> getBackwardDeliveries2(String deliveryId) {
		Set<String> b = new LinkedHashSet<>(getBackwardDeliveries(deliveries.get(deliveryId)));

		b.remove(deliveryId);

		return b;
	}

	public void init(boolean enforceTemporalOrder) {
		allIncoming = null;
		allOutgoing = null;
		sortedStations = null;
		sortedDeliveries = null;
		backwardDeliveries.clear();
		forwardDeliveries.clear();
		weightSum = 0.0;

		for (double w : stationWeights.values()) {
			weightSum += w;
		}

		for (double w : deliveryWeights.values()) {
			weightSum += w;
		}

		for (String stationId : ccStations) {
			for (String inId : getAllIncoming().get(stationId)) {
				Delivery in = deliveries.get(inId);

				for (String outId : getAllOutgoing().get(stationId)) {
					if (inId.equals(outId)) {
						continue;
					}

					Delivery out = deliveries.get(outId);

					if (!enforceTemporalOrder || (is1MaybeNewer(out, in))) {
						in.getAllNextIDs().add(outId);
						out.getAllPreviousIDs().add(inId);
					}
				}
			}
		}

		// delivery cc: all incoming-ccs are mixed
		for (String in1Id : ccDeliveries) {
			Delivery in1 = deliveries.get(in1Id);

			for (String in2Id : ccDeliveries) {
				if (in1Id.equals(in2Id)) {
					continue;
				}

				Delivery in2 = deliveries.get(in2Id);

				if (!in1.getRecipientID().equals(in2.getRecipientID())) {
					continue;
				}

				for (String out1Id : in1.getAllNextIDs()) {
					Delivery out1 = deliveries.get(out1Id);

					if (!enforceTemporalOrder || (is1MaybeNewer(out1, in2))) {
						in2.getAllNextIDs().add(out1Id);
						out1.getAllPreviousIDs().add(in2Id);
					}
				}

				for (String out2Id : in2.getAllNextIDs()) {
					Delivery out2 = deliveries.get(out2Id);

					if (!enforceTemporalOrder || (is1MaybeNewer(out2, in1))) {
						in1.getAllNextIDs().add(out2Id);
						out2.getAllPreviousIDs().add(in1Id);
					}
				}
			}
		}
	}

	public void setCaseDelivery(String deliveryID, double priority) {
		if (priority > 0) {
			deliveryWeights.put(deliveryID, priority);
		} else {
			deliveryWeights.remove(deliveryID);
		}
	}

	public void setCase(String stationID, double priority) {
		if (priority > 0) {
			stationWeights.put(stationID, priority);
		} else {
			stationWeights.remove(stationID);
		}
	}

	public void setCrossContaminationDelivery(String deliveryID, boolean possible) {
		if (possible) {
			ccDeliveries.add(deliveryID);
		} else {
			ccDeliveries.remove(deliveryID);
		}
	}

	public void setCrossContamination(String stationID, boolean possible) {
		if (possible) {
			ccStations.add(stationID);
		} else {
			ccStations.remove(stationID);
		}
	}

	public void mergeStations(Set<String> toBeMerged, String mergedStationID) {
		if (toBeMerged != null && toBeMerged.size() > 0) {
			for (Delivery md : deliveries.values()) {
				if (toBeMerged.contains(md.getSupplierID())) {
					md.setSupplierID(mergedStationID);
				}
				if (toBeMerged.contains(md.getRecipientID())) {
					md.setRecipientID(mergedStationID);
				}
			}
		}
	}

	// e.g. Jan 2012 vs. 18.Jan 2012 - be generous
	private boolean is1MaybeNewer(Delivery md1, Delivery md2) {
		Integer year1 = md1.getDeliveryYear();
		Integer year2 = md2.getDeliveryYear();
		if (year1 == null || year2 == null)
			return true;
		if (year1 > year2)
			return true;
		if (year1 < year2)
			return false;
		Integer month1 = md1.getDeliveryMonth();
		Integer month2 = md2.getDeliveryMonth();
		if (month1 == null || month2 == null)
			return true;
		if (month1 > month2)
			return true;
		if (month1 < month2)
			return false;
		Integer day1 = md1.getDeliveryDay();
		Integer day2 = md2.getDeliveryDay();
		if (day1 == null || day2 == null)
			return true;
		if (day1 >= day2)
			return true;
		return false;
	}

	private Set<String> getBackwardDeliveries(Delivery d) {
		if (d == null) {
			return null;
		}

		Set<String> backward = backwardDeliveries.get(d.getId());

		if (backward != null) {
			return backward;
		}

		backward = new LinkedHashSet<>();
		backward.add(d.getId());

		for (String prev : d.getAllPreviousIDs()) {
			backward.addAll(getBackwardDeliveries(deliveries.get(prev)));
		}

		backwardDeliveries.put(d.getId(), backward);

		return backward;
	}

	private Set<String> getForwardDeliveries(Delivery d) {
		if (d == null) {
			return null;
		}

		Set<String> forward = forwardDeliveries.get(d.getId());

		if (forward != null) {
			return forward;
		}

		forward = new LinkedHashSet<>();
		forward.add(d.getId());

		for (String next : d.getAllNextIDs()) {
			forward.addAll(getForwardDeliveries(deliveries.get(next)));
		}

		forwardDeliveries.put(d.getId(), forward);

		return forward;
	}

	private Set<String> getBackwardStations(Delivery md) {
		Set<String> result = null;
		if (md != null) {
			Set<String> fd = getBackwardDeliveries(md);
			if (fd != null && fd.size() > 0) {
				result = new LinkedHashSet<>();
				for (String i : fd) {
					Delivery mdn = deliveries.get(i);
					result.add(mdn.getSupplierID());
				}
			}
		}
		return result;
	}

	private Set<String> getForwardStations(Delivery md) {
		Set<String> result = null;
		if (md != null) {
			Set<String> fd = getForwardDeliveries(md);
			if (fd != null && fd.size() > 0) {
				result = new LinkedHashSet<>();
				for (String i : fd) {
					Delivery mdn = deliveries.get(i);
					result.add(mdn.getRecipientID());
				}
			}
		}
		return result;
	}

	private Set<String> getForwardStationsWithCases(Delivery md) {
		Set<String> result = null;
		if (md != null) {
			Set<String> fd = getForwardDeliveries(md);
			if (fd != null && fd.size() > 0) {
				result = new LinkedHashSet<>();
				for (String i : fd) {
					Delivery mdn = deliveries.get(i);
					if (stationWeights.containsKey(mdn.getRecipientID()))
						result.add(mdn.getRecipientID());
				}
			}
		}
		return result;
	}

	private Set<String> getForwardDeliveriesWithCases(Delivery md) {
		Set<String> result = null;
		if (md != null) {
			Set<String> fd = getForwardDeliveries(md);
			if (fd != null && fd.size() > 0) {
				result = new LinkedHashSet<>();
				for (String i : fd) {
					Delivery mdn = deliveries.get(i);
					if (deliveryWeights.containsKey(mdn.getId()))
						result.add("-" + mdn.getId());
					// hier minus, damit nachher unterschieden werden kann
					// zwischen Delivery und Station, siehe in Funktion
					// getStationScore bzw. getDeliveryScore
				}
			}
		}
		return result;
	}
}