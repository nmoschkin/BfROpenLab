/*******************************************************************************
 * Copyright (c) 2014 Federal Institute for Risk Assessment (BfR), Germany 
 * 
 * Developers and contributors are 
 * Christian Thoens (BfR)
 * Armin A. Weiser (BfR)
 * Matthias Filter (BfR)
 * Alexander Falenski (BfR)
 * Annemarie Kaesbohrer (BfR)
 * Bernd Appel (BfR)
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
 ******************************************************************************/
package de.bund.bfr.knime.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.node.util.DataColumnSpecListCellRenderer;

public class ColumnComboBox extends JPanel implements ActionListener {

	private static final long serialVersionUID = 1L;

	private boolean optional;
	private List<DataColumnSpec> columns;

	private JComboBox<DataColumnSpec> selectionBox;
	private JCheckBox enableBox;

	public ColumnComboBox(boolean optional, List<DataColumnSpec> columns) {
		this.optional = optional;
		this.columns = columns;

		if (this.columns == null) {
			this.columns = new ArrayList<>();
		}

		selectionBox = new JComboBox<>(new Vector<>(this.columns));
		selectionBox.setRenderer(new DataColumnSpecListCellRenderer());
		enableBox = new JCheckBox();
		enableBox.setSelected(true);
		enableBox.addActionListener(this);

		setLayout(new BorderLayout(5, 5));
		add(selectionBox, BorderLayout.CENTER);

		if (optional) {
			add(enableBox, BorderLayout.WEST);
		}
	}

	public ColumnComboBox(boolean optional) {
		this(optional, null);
	}

	public void removeAllColumns() {
		columns.clear();
		selectionBox.removeAllItems();
	}

	public void addColumn(DataColumnSpec column) {
		columns.add(column);
		selectionBox.addItem(column);
	}

	public DataColumnSpec getSelectedColumn() {
		if (!selectionBox.isEnabled()) {
			return null;
		}

		return (DataColumnSpec) selectionBox.getSelectedItem();
	}

	public void setSelectedColumn(DataColumnSpec column) {
		if (columns.contains(column)) {
			selectionBox.setSelectedItem(column);
		} else if (optional) {
			selectionBox.setSelectedItem(null);
		}

		if (optional) {
			selectionBox.setEnabled(selectionBox.getSelectedItem() != null);
			enableBox.setSelected(selectionBox.getSelectedItem() != null);
		}
	}

	public String getSelectedColumnName() {
		DataColumnSpec selection = getSelectedColumn();

		if (selection != null) {
			return selection.getName();
		}

		return null;
	}

	public void setSelectedColumnName(String columnName) {
		for (int i = 0; i < selectionBox.getItemCount(); i++) {
			DataColumnSpec item = selectionBox.getItemAt(i);

			if (item != null && item.getName().equals(columnName)) {
				setSelectedColumn(item);
				return;
			}
		}

		setSelectedColumn(null);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (enableBox.isSelected()) {
			selectionBox.setEnabled(true);
		} else {
			selectionBox.setEnabled(false);
		}
	}
}