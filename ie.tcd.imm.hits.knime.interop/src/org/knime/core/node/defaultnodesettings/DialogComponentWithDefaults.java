/*
 * All rights reserved. (C) Copyright 2009, Trinity College Dublin
 */
package org.knime.core.node.defaultnodesettings;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;

/**
 * This {@link DialogComponent} allows to combine various
 * {@link DialogComponent}s and set their defaults.
 * <p>
 * 
 * @author <a href="mailto:bakosg@tcd.ie">Gabor Bakos</a>
 */
// TODO save last used values. <br/>
// TODO add option to guess the initial value.
@Nonnull
@CheckReturnValue
public class DialogComponentWithDefaults extends DialogComponent {

	private final JComboBox<String> selectionBox = new JComboBox<>();
	private final Map<String, Boolean[]> enablementOptions;
	private final Map<String, Object[]> defaultValues;
	private final DialogComponent[] components;
	@Nullable
	private final String noClearValue;

	/**
	 * @param model
	 *            The {@link SettingsModelString} to use.
	 * @param label
	 *            The label to show.
	 * @param enablementOptions
	 *            The enabled components for different options.
	 * @param defaultValues
	 *            The values for the options.
	 * @param components
	 *            The handled {@link DialogComponent}s.
	 */
	public DialogComponentWithDefaults(final SettingsModelString model,
			final String label, final Map<String, Boolean[]> enablementOptions,
			final Map<String, Object[]> defaultValues,
			final DialogComponent... components) {
		this(model, label, enablementOptions, defaultValues, null, components);
	}

	/**
	 * @param model
	 *            The {@link SettingsModelString} to use.
	 * @param label
	 *            The label to show.
	 * @param enablementOptions
	 *            The enabled components for different options.
	 * @param defaultValues
	 *            The values for the options.
	 * @param noClearValue
	 *            When this value is selected it will not modify the associated
	 *            components.
	 * @param components
	 *            The handled {@link DialogComponent}s.
	 */
	public DialogComponentWithDefaults(final SettingsModelString model,
			final String label, final Map<String, Boolean[]> enablementOptions,
			final Map<String, Object[]> defaultValues,
			@Nullable final String noClearValue,
			final DialogComponent[] components) {
		super(model);
		this.noClearValue = noClearValue;
		this.components = components;
		this.enablementOptions = clone(enablementOptions);
		this.defaultValues = clone(defaultValues);
		checkMaps(enablementOptions, components.length);
		checkMaps(defaultValues, components.length);
		getComponentPanel().add(new JLabel(label));
		getComponentPanel().add(selectionBox);
		for (final String option : defaultValues.keySet()) {
			selectionBox.addItem(option);
		}
		selectionBox.setEditable(false);
		selectionBox.addItemListener(new ItemListener() {
			@SuppressWarnings("synthetic-access")
			@Override
			public void itemStateChanged(final ItemEvent e) {
				((SettingsModelString) getModel())
						.setStringValue((String) selectionBox.getSelectedItem());
				updateComponent();
			}
		});
		getModel().addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				updateComponent();
			}
		});
		updateComponent();
	}

	/**
	 * Creates a {@link Map} with cloned values.
	 * 
	 * @param map
	 *            The map to clone.
	 * @param <T>
	 *            Type of the values array elements.
	 * @return The cloned (not deep, just the array is cloned) map.
	 */
	private <T> Map<String, T[]> clone(final Map<String, T[]> map) {
		final Map<String, T[]> ret = new LinkedHashMap<String, T[]>();
		for (final Entry<String, T[]> entry : map.entrySet()) {
			ret.put(entry.getKey(), entry.getValue().clone());
		}
		return ret;
	}

	/**
	 * Checks whether all of the associated {@code values} have the same,
	 * {@code valueCount} array length .
	 * 
	 * @param <K>
	 *            Type of the keys.
	 * 
	 * @param values
	 *            A {@link Map} to arrays.
	 * @param valueCount
	 *            The expected array length.
	 */
	private <K> void checkMaps(final Map<K, ? extends Object[]> values,
			final int valueCount) {
		for (final Entry<K, ? extends Object[]> entry : values.entrySet()) {
			if (entry.getValue().length != valueCount) {
				throw new IllegalArgumentException("Wrong number of values: "
						+ entry.getValue().length + " expected: " + valueCount
						+ " at option: " + entry.getKey());
			}
		}
	}

	@Override
	protected void updateComponent() {
		final String newSelection = ((SettingsModelString) getModel())
				.getStringValue();
		if (!newSelection.equals(selectionBox.getSelectedItem())) {
			selectionBox.setSelectedItem(newSelection);
		}
		final String selection = (String) selectionBox.getSelectedItem();
		final Boolean[] enablements = enablementOptions.get(selection);
		final Object[] defaults = defaultValues.get(selection);
		for (int i = components.length; i-- > 0;) {
			components[i].getModel().setEnabled(enablements[i].booleanValue());
			if (selection.equals(noClearValue)) {
				// (components[i]).updateComponent();
				continue;
			}
			if (defaults[i] instanceof String) {
				final String newValue = (String) defaults[i];
				if (components[i] instanceof DialogComponentColumnNameSelection) {
					final DialogComponentColumnNameSelection nameSelector = (DialogComponentColumnNameSelection) components[i];
					((SettingsModelColumnName) nameSelector.getModel())
							.setSelection(newValue, false);
				} else if (components[i].getModel() instanceof SettingsModelString) {
					((SettingsModelString) components[i].getModel())
							.setStringValue(newValue);

				} else if (components[i] instanceof DialogComponentNumber
						|| components[i] instanceof DialogComponentNumberEdit) {
					((SettingsModelInteger) components[i].getModel())
							.setIntValue(Integer.parseInt(newValue));
				}
			} else if (defaults[i] instanceof String[]) {
				final String[] newValue = (String[]) defaults[i];
				((SettingsModelStringArray) components[i].getModel())
						.setStringArrayValue(newValue);
			} else if (defaults[i] instanceof Integer) {
				final Integer newValue = (Integer) defaults[i];
				((SettingsModelInteger) components[i].getModel())
						.setIntValue(newValue.intValue());
			} else if (defaults[i] instanceof Double) {
				final Double newValue = (Double) defaults[i];
				((SettingsModelDouble) components[i].getModel())
						.setDoubleValue(newValue.doubleValue());
			} else {
				if (defaults[i] != null) {
					throw new UnsupportedOperationException(
							"Not supported value type: "
									+ (defaults[i] == null ? null : defaults[i]
											.getClass()));
				}
			}
			// components[i].updateComponent();
		}
		selectionBox.setEnabled(getModel().isEnabled());
	}

	@Override
	protected void validateSettingsBeforeSave() throws InvalidSettingsException {
		// Done in construction.
		final String selection = (String) selectionBox.getSelectedItem();
		if (!defaultValues.containsKey(selection)) {
			throw new InvalidSettingsException("No valid defaults found for "
					+ selection);
		}
	}

	@Override
	protected void checkConfigurabilityBeforeLoad(final PortObjectSpec[] specs)
			throws NotConfigurableException {
		// Should be OK, put here the save of last used values.
	}

	@Override
	protected void setEnabledComponents(final boolean enabled) {
		selectionBox.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(final String text) {
		getComponentPanel().setToolTipText(text);
	}

}
