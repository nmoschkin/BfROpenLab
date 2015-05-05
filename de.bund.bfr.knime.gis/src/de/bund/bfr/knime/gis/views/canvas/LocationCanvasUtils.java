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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;
import com.vividsolutions.jts.geom.Polygon;

import de.bund.bfr.knime.gis.GisUtils;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.LocationNode;
import edu.uci.ics.jung.algorithms.layout.Layout;

public class LocationCanvasUtils {

	private LocationCanvasUtils() {
	}

	public static Rectangle2D getBounds(Collection<LocationNode> nodes) {
		Rectangle2D bounds = null;

		for (LocationNode node : nodes) {
			Rectangle2D r = new Rectangle2D.Double(node.getCenter().getX(),
					node.getCenter().getY(), 0, 0);

			if (bounds == null) {
				bounds = r;
			} else {
				bounds = bounds.createUnion(r);
			}
		}

		return bounds;
	}

	public static void updateNodeLocations(Collection<LocationNode> nodes,
			Layout<LocationNode, Edge<LocationNode>> layout, Transform transform, int nodeSize,
			boolean avoidOverlay) {
		if (!avoidOverlay) {
			for (LocationNode n : nodes) {
				layout.setLocation(n, n.getCenter());
			}

			return;
		}

		Map<LocationNode, Point2D> positions = new LinkedHashMap<>();

		for (LocationNode n : nodes) {
			positions.put(n, n.getCenter());
		}

		double s = nodeSize / transform.getScaleX();
		double d = s / 5.0;
		int index = 0;

		for (LocationNode n1 : nodes) {
			Point2D p1 = positions.get(n1);
			List<Point2D> neighbors = new ArrayList<>();

			for (LocationNode n2 : nodes) {
				Point2D p2 = positions.get(n2);

				if (n1 != n2 && p1.distance(p2) < 2 * s) {
					neighbors.add(p2);
				}
			}

			double randX = new Random(index++).nextDouble();
			double randY = new Random(index++).nextDouble();
			double x1 = p1.getX() - s;
			double x2 = p1.getX() + s;
			double y1 = p1.getY() - s;
			double y2 = p1.getY() + s;
			double bestDistance = 0.0;
			Point2D bestPoint = null;

			for (double x = x1 + randX * d; x <= x2; x += d) {
				for (double y = y1 + randY * d; y <= y2; y += d) {
					double distance = Double.POSITIVE_INFINITY;

					for (Point2D p : neighbors) {
						distance = Math.min(distance, p.distance(x, y));
					}

					if (distance > bestDistance) {
						bestDistance = distance;
						bestPoint = new Point2D.Double(x, y);
					}
				}
			}

			positions.put(n1, bestPoint);
		}

		for (Map.Entry<LocationNode, Point2D> entry : positions.entrySet()) {
			layout.setLocation(entry.getKey(), entry.getValue());
		}
	}

	public static Polygon placeNodes(Collection<LocationNode> nodes,
			Collection<Edge<LocationNode>> edges, Layout<LocationNode, Edge<LocationNode>> layout) {
		Polygon invalidArea = null;

		Set<LocationNode> validNodes = new LinkedHashSet<>();
		Set<LocationNode> invalidNodes = new LinkedHashSet<>();
		Map<LocationNode, Set<LocationNode>> invalidToValid = new LinkedHashMap<>();
		Map<LocationNode, Set<LocationNode>> invalidToInvalid = new LinkedHashMap<>();

		for (LocationNode node : nodes) {
			if (node.getCenter() != null) {
				layout.setLocation(node, node.getCenter());
				validNodes.add(node);
			} else {
				invalidNodes.add(node);
				invalidToValid.put(node, new LinkedHashSet<LocationNode>());
				invalidToInvalid.put(node, new LinkedHashSet<LocationNode>());
			}
		}

		for (Edge<LocationNode> edge : edges) {
			if (edge.getFrom() == edge.getTo()) {
				continue;
			}

			if (invalidNodes.contains(edge.getFrom())) {
				if (invalidNodes.contains(edge.getTo())) {
					invalidToInvalid.get(edge.getFrom()).add(edge.getTo());
				} else {
					invalidToValid.get(edge.getFrom()).add(edge.getTo());
				}
			}

			if (invalidNodes.contains(edge.getTo())) {
				if (invalidNodes.contains(edge.getFrom())) {
					invalidToInvalid.get(edge.getTo()).add(edge.getFrom());
				} else {
					invalidToValid.get(edge.getTo()).add(edge.getFrom());
				}
			}
		}

		if (!invalidNodes.isEmpty()) {
			Rectangle2D bounds = getBounds(validNodes);
			double size = Math.max(bounds.getWidth(), bounds.getHeight());

			if (size == 0.0) {
				size = 1.0;
			}

			double d = 0.2 * size;
			double r = 0.02 * size;

			invalidArea = GisUtils.createBorderPolygon(new Rectangle2D.Double(bounds.getX() - d,
					bounds.getY() - d, bounds.getWidth() + 2 * d, bounds.getHeight() + 2 * d),
					2 * r);

			Rectangle2D rect = new Rectangle2D.Double(bounds.getX() - d - r, bounds.getY() - d - r,
					bounds.getWidth() + 2 * (d + r), bounds.getHeight() + 2 * (d + r));
			Set<LocationNode> nodesToDo = new LinkedHashSet<>(invalidNodes);

			for (Iterator<LocationNode> iterator = nodesToDo.iterator(); iterator.hasNext();) {
				LocationNode node = iterator.next();
				Set<LocationNode> validConnections = invalidToValid.get(node);

				if (!validConnections.isEmpty()) {
					List<Point2D> points = new ArrayList<>();

					for (LocationNode n : validConnections) {
						points.add(n.getCenter());
					}

					Point2D p = getClosestPointOnRect(CanvasUtils.getCenter(points), rect);

					node.updateCenter(p);
					layout.setLocation(node, p);
					iterator.remove();
				}
			}

			while (true) {
				boolean nothingChanged = true;

				for (Iterator<LocationNode> iterator = nodesToDo.iterator(); iterator.hasNext();) {
					LocationNode node = iterator.next();
					Set<LocationNode> inValidConnections = invalidToInvalid.get(node);
					List<Point2D> points = new ArrayList<>();

					for (LocationNode n : inValidConnections) {
						if (n.getCenter() != null) {
							points.add(n.getCenter());
						}
					}

					if (!points.isEmpty()) {
						Point2D p = getClosestPointOnRect(CanvasUtils.getCenter(points), rect);

						node.updateCenter(p);
						layout.setLocation(node, p);
						iterator.remove();
						nothingChanged = false;
					}
				}

				if (nothingChanged) {
					break;
				}
			}

			for (Iterator<LocationNode> iterator = nodesToDo.iterator(); iterator.hasNext();) {
				LocationNode node = iterator.next();
				Point2D p = new Point2D.Double(bounds.getMinX() - d - r, bounds.getMaxY() - d - r);

				node.updateCenter(p);
				layout.setLocation(node, p);
				iterator.remove();
			}
		}

		return invalidArea;
	}

	public static void paintNonLatLonArea(Graphics g, int w, int h, java.awt.Polygon invalidArea) {
		BufferedImage invalidAreaImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics imgGraphics = invalidAreaImage.getGraphics();

		((Graphics2D) imgGraphics).setPaint(CanvasUtils.mixColors(Color.WHITE,
				Arrays.asList(Color.RED, Color.WHITE), Arrays.asList(1.0, 1.0)));
		imgGraphics.fillPolygon(invalidArea);
		imgGraphics.setColor(Color.BLACK);
		imgGraphics.drawPolygon(invalidArea);
		CanvasUtils.drawImageWithAlpha(g, invalidAreaImage, 75);
	}

	public static LocationNode createMetaNode(String id, Collection<LocationNode> nodes,
			NodePropertySchema nodeSchema, String metaNodeProperty,
			Layout<LocationNode, Edge<LocationNode>> layout) {
		Map<String, Object> properties = new LinkedHashMap<>();

		for (LocationNode node : nodes) {
			CanvasUtils.addMapToMap(properties, nodeSchema, node.getProperties());
		}

		properties.put(nodeSchema.getId(), id);
		properties.put(metaNodeProperty, true);
		properties.put(nodeSchema.getLatitude(), null);
		properties.put(nodeSchema.getLongitude(), null);

		List<Double> xList = new ArrayList<Double>();
		List<Double> yList = new ArrayList<Double>();

		for (LocationNode node : nodes) {
			xList.add(node.getCenter().getX());
			yList.add(node.getCenter().getY());
		}

		double x = DoubleMath.mean(Doubles.toArray(xList));
		double y = DoubleMath.mean(Doubles.toArray(yList));
		LocationNode newNode = new LocationNode(id, properties, new Point2D.Double(x, y));

		layout.setLocation(newNode, newNode.getCenter());

		return newNode;
	}

	private static Point2D getClosestPointOnRect(Point2D pointInRect, Rectangle2D rect) {
		Double dx1 = Math.abs(pointInRect.getX() - rect.getMinX());
		Double dx2 = Math.abs(pointInRect.getX() - rect.getMaxX());
		Double dy1 = Math.abs(pointInRect.getY() - rect.getMinY());
		Double dy2 = Math.abs(pointInRect.getY() - rect.getMaxY());
		Double min = Collections.min(Arrays.asList(dx1, dx2, dy1, dy2));

		if (dx1 == min) {
			return new Point2D.Double(rect.getMinX(), pointInRect.getY());
		} else if (dx2 == min) {
			return new Point2D.Double(rect.getMaxX(), pointInRect.getY());
		} else if (dy1 == min) {
			return new Point2D.Double(pointInRect.getX(), rect.getMinY());
		} else if (dy2 == min) {
			return new Point2D.Double(pointInRect.getX(), rect.getMaxY());
		}

		throw new RuntimeException("This should not happen");
	}
}