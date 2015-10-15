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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.bund.bfr.knime.gis.views.canvas.element.RegionNode;
import de.bund.bfr.knime.gis.views.canvas.highlighting.HighlightCondition;
import de.bund.bfr.knime.gis.views.canvas.highlighting.HighlightConditionList;

public class RegionCanvasUtils {

	private RegionCanvasUtils() {
	}

	public static Rectangle2D getBounds(Collection<RegionNode> nodes) {
		Rectangle2D bounds = null;

		for (RegionNode node : nodes) {
			bounds = bounds != null ? bounds.createUnion(node.getBoundingBox()) : node.getBoundingBox();
		}

		return bounds;
	}

	public static void paintRegions(Graphics g, Collection<RegionNode> nodes, Set<RegionNode> selectedNodes,
			HighlightConditionList nodeHighlightConditions) {
		for (RegionNode node : selectedNodes) {
			g.setColor(Color.BLUE);
			((Graphics2D) g).fill(node.getTransformedPolygon());
		}

		List<Color> nodeColors = new ArrayList<>();
		Map<RegionNode, List<Double>> nodeAlphas = new LinkedHashMap<>();
		boolean prioritize = nodeHighlightConditions.isPrioritizeColors();

		for (RegionNode node : nodes) {
			nodeAlphas.put(node, new ArrayList<Double>());
		}

		for (HighlightCondition condition : nodeHighlightConditions.getConditions()) {
			Map<RegionNode, Double> values = condition.getValues(nodes);

			nodeColors.add(condition.getColor());

			for (RegionNode node : nodes) {
				List<Double> alphas = nodeAlphas.get(node);

				if (!prioritize || alphas.isEmpty() || Collections.max(alphas) == 0.0) {
					alphas.add(values.get(node));
				} else {
					alphas.add(0.0);
				}
			}
		}

		for (RegionNode node : nodes) {
			Paint color = CanvasUtils.mixColors(Color.WHITE, nodeColors, nodeAlphas.get(node), false);

			if (!color.equals(Color.WHITE) && !selectedNodes.contains(node)) {
				((Graphics2D) g).setPaint(color);
				((Graphics2D) g).fill(node.getTransformedPolygon());
			}
		}
	}
}
