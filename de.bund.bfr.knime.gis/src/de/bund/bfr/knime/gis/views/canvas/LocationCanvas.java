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
package de.bund.bfr.knime.gis.views.canvas;

import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Polygon;

import de.bund.bfr.knime.gis.GisUtils;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.LocationNode;
import de.bund.bfr.knime.gis.views.canvas.element.RegionNode;
import de.bund.bfr.knime.gis.views.canvas.transformer.NodeShapeTransformer;

public class LocationCanvas extends ShapefileCanvas<LocationNode> {

	private static final long serialVersionUID = 1L;

	private List<RegionNode> regions;
	private Polygon invalidArea;

	public LocationCanvas(boolean allowEdges, Naming naming) {
		this(new ArrayList<LocationNode>(), new ArrayList<Edge<LocationNode>>(),
				new NodePropertySchema(), new EdgePropertySchema(), naming,
				new ArrayList<RegionNode>(), allowEdges);
	}

	public LocationCanvas(List<LocationNode> nodes, NodePropertySchema nodeSchema, Naming naming,
			List<RegionNode> regions) {
		this(nodes, new ArrayList<Edge<LocationNode>>(), nodeSchema, new EdgePropertySchema(),
				naming, regions, false);
	}

	public LocationCanvas(List<LocationNode> nodes, List<Edge<LocationNode>> edges,
			NodePropertySchema nodeSchema, EdgePropertySchema edgeSchema, Naming naming,
			List<RegionNode> regions) {
		this(nodes, edges, nodeSchema, edgeSchema, naming, regions, true);
	}

	private LocationCanvas(List<LocationNode> nodes, List<Edge<LocationNode>> edges,
			NodePropertySchema nodeSchema, EdgePropertySchema edgeSchema, Naming naming,
			List<RegionNode> regions, boolean allowEdges) {
		super(nodes, edges, nodeSchema, edgeSchema, naming);
		this.regions = regions;
		invalidArea = null;

		setPopupMenu(new CanvasPopupMenu(this, allowEdges, false, true));
		setOptionsPanel(new CanvasOptionsPanel(this, allowEdges, true, true));
		viewer.getRenderContext().setVertexShapeTransformer(
				new NodeShapeTransformer<LocationNode>(getNodeSize()));

		for (LocationNode node : this.nodes) {
			if (node.getCenter() != null) {
				node.updateCenter(GisUtils.latLonToViz(node.getCenter()));
			}
		}

		for (RegionNode region : this.regions) {
			region.updatePolygon(GisUtils.latLonToViz(region.getPolygon()));
		}

		invalidArea = LocationCanvasUtils.placeNodes(this.nodes, this.edges,
				viewer.getGraphLayout());
	}

	@Override
	public Collection<RegionNode> getRegions() {
		return regions;
	}

	@Override
	public void resetLayoutItemClicked() {
		Rectangle2D bounds = LocationCanvasUtils.getBounds(nodes);

		if (bounds != null) {
			zoomTo(bounds);
		} else {
			super.resetLayoutItemClicked();
		}
	}

	@Override
	protected void paintGis(Graphics g, boolean toSvg) {
		super.paintGis(g, toSvg);

		if (invalidArea != null) {
			LocationCanvasUtils.paintNonLatLonArea(g, getCanvasSize().width,
					getCanvasSize().height, transform.apply(invalidArea, true));
		}
	}

	@Override
	protected LocationNode createMetaNode(String id, Collection<LocationNode> nodes) {
		return LocationCanvasUtils.createMetaNode(id, nodes, nodeSchema, metaNodeProperty,
				viewer.getGraphLayout());
	}
}
