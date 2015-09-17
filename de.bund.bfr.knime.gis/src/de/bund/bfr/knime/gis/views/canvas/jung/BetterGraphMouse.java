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
package de.bund.bfr.knime.gis.views.canvas.jung;

import edu.uci.ics.jung.visualization.control.AbstractModalGraphMouse;

public class BetterGraphMouse<V, E> extends AbstractModalGraphMouse {

	private boolean pickingDeactivated;

	public BetterGraphMouse(BetterPickingGraphMousePlugin<V, E> pickingPlugin,
			BetterScalingGraphMousePlugin scalingPlugin) {
		super(1, 1);

		translatingPlugin = new BetterTranslatingGraphMousePlugin();
		this.pickingPlugin = pickingPlugin;
		this.scalingPlugin = scalingPlugin;
		pickingDeactivated = false;
		add(scalingPlugin);
	}

	@Override
	protected void loadPlugins() {
	}

	@Override
	protected void setPickingMode() {
		remove(translatingPlugin);

		if (!pickingDeactivated) {
			add(pickingPlugin);
		}
	}

	@Override
	protected void setTransformingMode() {
		remove(pickingPlugin);
		add(translatingPlugin);
	}

	public void addTranslatingChangeListener(BetterTranslatingGraphMousePlugin.ChangeListener listener) {
		((BetterTranslatingGraphMousePlugin) translatingPlugin).addChangeListener(listener);
	}

	public void removeTranslatingChangeListener(BetterTranslatingGraphMousePlugin.ChangeListener listener) {
		((BetterTranslatingGraphMousePlugin) translatingPlugin).removeChangeListener(listener);
	}

	public void addScalingChangeListener(BetterScalingGraphMousePlugin.ChangeListener listener) {
		((BetterScalingGraphMousePlugin) scalingPlugin).addChangeListener(listener);
	}

	public void removeScalingChangeListener(BetterScalingGraphMousePlugin.ChangeListener listener) {
		((BetterScalingGraphMousePlugin) scalingPlugin).removeChangeListener(listener);
	}

	@SuppressWarnings("unchecked")
	public void addPickingChangeListener(BetterPickingGraphMousePlugin.ChangeListener listener) {
		((BetterPickingGraphMousePlugin<V, E>) pickingPlugin).addChangeListener(listener);
	}

	@SuppressWarnings("unchecked")
	public void removePickingChangeListener(BetterPickingGraphMousePlugin.ChangeListener listener) {
		((BetterPickingGraphMousePlugin<V, E>) pickingPlugin).removeChangeListener(listener);
	}

	@SuppressWarnings("unchecked")
	public void addPickingMoveListener(BetterPickingGraphMousePlugin.MoveListener listener) {
		((BetterPickingGraphMousePlugin<V, E>) pickingPlugin).addMoveListener(listener);
	}

	@SuppressWarnings("unchecked")
	public void removePickingMoveListener(BetterPickingGraphMousePlugin.MoveListener listener) {
		((BetterPickingGraphMousePlugin<V, E>) pickingPlugin).removeMoveListener(listener);
	}

	public boolean isPickingDeactivated() {
		return pickingDeactivated;
	}

	public void setPickingDeactivated(boolean pickingDeactivated) {
		if (pickingDeactivated != this.pickingDeactivated && mode == Mode.PICKING) {
			if (pickingDeactivated) {
				remove(pickingPlugin);
			} else {
				add(pickingPlugin);
			}
		}

		this.pickingDeactivated = pickingDeactivated;
	}
}
