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
package de.bund.bfr.knime.nls.creator;

import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;

import com.google.common.base.Objects;

import de.bund.bfr.knime.UI;
import de.bund.bfr.knime.ui.StringTextArea;
import de.bund.bfr.knime.ui.StringTextField;
import de.bund.bfr.knime.ui.TextListener;
import de.bund.bfr.math.MathUtils;

/**
 * <code>NodeDialog</code> for the "FunctionCreator" Node.
 * 
 * @author Christian Thoens
 */
public class FunctionCreatorNodeDialog extends NodeDialogPane implements TextListener, ItemListener {

	private FunctionCreatorSettings set;
	private List<String> usedIndeps;

	private JPanel mainPanel;
	private JPanel functionPanel;

	private StringTextField depVarField;
	private StringTextArea termField;
	private List<JCheckBox> indepVarBoxes;

	/**
	 * New pane for configuring the FormulaCreator node.
	 */
	protected FunctionCreatorNodeDialog() {
		set = new FunctionCreatorSettings();
		usedIndeps = new ArrayList<>();

		functionPanel = createFunctionPanel();
		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		mainPanel.add(functionPanel, BorderLayout.NORTH);

		addTab("Options", UI.createWestPanel(mainPanel));
	}

	@Override
	protected void loadSettingsFrom(NodeSettingsRO settings, DataTableSpec[] specs) throws NotConfigurableException {
		set.loadSettings(settings);
		mainPanel.remove(functionPanel);
		functionPanel = createFunctionPanel();
		mainPanel.add(functionPanel, BorderLayout.NORTH);
		mainPanel.revalidate();
		usedIndeps = set.getIndependentVariables();
	}

	@Override
	protected void saveSettingsTo(NodeSettingsWO settings) throws InvalidSettingsException {
		updateFunction();

		if (!depVarField.isValueValid()) {
			throw new InvalidSettingsException("Dependent Variable Missing");
		}

		if (!termField.isValueValid()) {
			throw new InvalidSettingsException("Formula Missing");
		}

		if (MathUtils.getSymbols(set.getTerm()).isEmpty()) {
			throw new InvalidSettingsException("Formula Invalid");
		}

		if (set.getIndependentVariables().isEmpty()) {
			throw new InvalidSettingsException("Independent Variables Missing");
		}

		set.saveSettings(settings);
	}

	@Override
	public void textChanged(Object source) {
		if (source == depVarField) {
			set.setDependentVariable(depVarField.getValue());
		} else if (source == termField) {
			set.setTerm(termField.getValue());
			mainPanel.remove(functionPanel);
			updateFunction();
			functionPanel = createFunctionPanel();
			mainPanel.add(functionPanel, BorderLayout.NORTH);
			mainPanel.revalidate();
			termField.requestFocus();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (indepVarBoxes != null && indepVarBoxes.contains(e.getSource())) {
			String name = ((JCheckBox) e.getSource()).getText();

			if (e.getStateChange() == ItemEvent.SELECTED) {
				set.getIndependentVariables().add(name);
				usedIndeps.add(name);
			} else if (e.getStateChange() == ItemEvent.DESELECTED) {
				set.getIndependentVariables().remove(name);
				usedIndeps.remove(name);
			}
		}
	}

	private JPanel createFunctionPanel() {
		JPanel editPanel = new JPanel();

		editPanel.setLayout(new GridBagLayout());
		editPanel.add(new JLabel("Term:"), UI.westConstraints(0, 0));
		editPanel.add(createFormulaPanel(), UI.westConstraints(1, 0));
		editPanel.add(new JLabel("Independent Variable:"), UI.westConstraints(0, 1));
		editPanel.add(createIndepBoxPanel(), UI.westConstraints(1, 1));

		JPanel panel = new JPanel();

		panel.setBorder(BorderFactory.createTitledBorder("Function"));
		panel.setLayout(new BorderLayout());
		panel.add(editPanel, BorderLayout.WEST);

		return editPanel;
	}

	private JPanel createFormulaPanel() {
		depVarField = new StringTextField(false, 10);
		depVarField.setValue(set.getDependentVariable());
		depVarField.addTextListener(this);

		if (termField == null || !Objects.equal(termField.getValue(), set.getTerm())) {
			termField = new StringTextArea(false, 3, 100);
			termField.setValue(set.getTerm());
			termField.addTextListener(this);
		}

		JPanel formulaPanel = new JPanel();

		formulaPanel.setLayout(new BoxLayout(formulaPanel, BoxLayout.X_AXIS));
		formulaPanel.add(UI.createCenterPanel(depVarField));
		formulaPanel.add(new JLabel("="));
		formulaPanel.add(termField);

		return formulaPanel;
	}

	private JPanel createIndepBoxPanel() {
		List<String> elements = new ArrayList<>(MathUtils.getSymbols(set.getTerm()));

		Collections.sort(elements);

		JPanel panel = new JPanel();

		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		indepVarBoxes = new ArrayList<>();

		for (String el : elements) {
			JCheckBox box = new JCheckBox(el);

			if (set.getIndependentVariables().contains(el)) {
				box.setSelected(true);
			} else {
				box.setSelected(false);
			}

			box.addItemListener(this);
			panel.add(box);
			indepVarBoxes.add(box);
		}

		return panel;
	}

	private void updateFunction() {
		List<String> symbols = new ArrayList<>(MathUtils.getSymbols(set.getTerm()));

		List<String> indeps = new ArrayList<>();

		for (String symbol : symbols) {
			if (set.getIndependentVariables().contains(symbol) || usedIndeps.contains(symbol)) {
				indeps.add(symbol);
			}
		}

		Collections.sort(indeps);
		set.setIndependentVariables(indeps);
	}
}
