/*
 * All rights reserved. (C) Copyright 2009, Trinity College Dublin
 */
package ie.tcd.imm.hits.knime.cellhts2.worker;

import java.awt.Color;
import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.html.HTMLEditorKit;

import org.knime.core.node.defaultnodesettings.DialogComponent;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.UpdatableComponent;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;

/**
 * This kind of {@link DialogComponent} allows you to show images with
 * descriptions from a similar source.
 * 
 * @author <a href="mailto:bakosg@tcd.ie">Gabor Bakos</a>
 */
@DefaultAnnotation( { Nonnull.class, CheckReturnValue.class })
class DialogComponentFormula extends UpdatableComponent implements
		ChangeListener {
	/** The type of the formula. */
	public static enum FormulaType {
		/** Normalisation */
		Normalisation,
		/** Summarisation */
		Summarisation,
		/** Scoring */
		Scoring;
	}

	private static final String CONFIGURATION_FILE_NAME = "help.xml";

	private static final String DEFAULT = "default";
	private static final Map<String, String> paths = new HashMap<String, String>();
	private static final Map<String, String> tooltips = new HashMap<String, String>();
	private static final Map<String, String> detailedDescriptions = new HashMap<String, String>();

	static {
		init();
	}

	private static void init() {
		final InputStream is = DialogComponentFormula.class
				.getResourceAsStream(CONFIGURATION_FILE_NAME);
		if (is != null) {
			try {
				readConfiguration(is);
			} finally {
				try {
					is.close();
				} catch (final IOException e) {
					CellHTS2NodeModel.logger.warn("Unable to load help file.",
							e);
				}
			}
		}
	}

	private final SettingsWrapper settingsWrapper;

	private final JLabel image = new JLabel();
	private final JTextPane detailedDescription = new JTextPane();
	private final JTextArea generatedDescription = new JTextArea();

	@Nullable
	private DialogComponent helpDialog;

	/**
	 * Wraps some {@link SettingsModel}s and allows to get a key based on their
	 * values.
	 */
	static class SettingsWrapper implements ChangeListener {
		private final SettingsModel[] models;
		@Nullable
		private ChangeListener listener;

		/**
		 * Constructs {@link SettingsWrapper}.
		 * 
		 * @param models
		 *            The {@link SettingsModel}s to wrap.
		 */
		public SettingsWrapper(final SettingsModel... models) {
			super();
			this.models = models;
			for (final SettingsModel model : models) {
				model.addChangeListener(this);
			}
		}

		/**
		 * @return The actual key based on the actual values of the wrapped
		 *         {@link SettingsModel}s. (Generated by appending {@code .}
		 *         characters between the different models and replacing any
		 *         {@code .} characters to {@code _} characters in the values to
		 *         reduce possible ambiguities.)
		 */
		public String actualKey() {
			final StringBuilder sb = new StringBuilder();
			for (final SettingsModel model : models) {
				if (model instanceof SettingsModelString) {
					final SettingsModelString val = (SettingsModelString) model;
					sb.append(val.getStringValue().replace('.', '_'));
				}
				if (model instanceof SettingsModelBoolean) {
					final SettingsModelBoolean val = (SettingsModelBoolean) model;
					sb.append(val.getBooleanValue());
				}
				sb.append('.');
			}
			if (sb.length() > 0) {
				sb.setLength(sb.length() - 1);
			}
			return sb.toString();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @seejavax.swing.event.ChangeListener#stateChanged(javax.swing.event.
		 * ChangeEvent)
		 */
		@Override
		public void stateChanged(final ChangeEvent e) {
			if (listener != null) {
				listener.stateChanged(e);
			}
		}

		/**
		 * Sets the {@link ChangeListener} of this {@link SettingsModel}.
		 * 
		 * @param listener
		 *            A {@link ChangeListener}. (May be {@code null}.)
		 */
		public void setListener(@Nullable final ChangeListener listener) {
			this.listener = listener;
		}
	}

	/**
	 * @param settingsWrapper
	 *            The wrapper for settings to find the proper keys for the
	 *            images, tooltips and detailed descriptions.
	 */
	public DialogComponentFormula(final SettingsWrapper settingsWrapper) {
		this.settingsWrapper = settingsWrapper;
		getComponentPanel().setLayout(
				new BoxLayout(getComponentPanel(), BoxLayout.Y_AXIS));
		getComponentPanel().add(new JScrollPane(image));
		getComponentPanel().add(generatedDescription);
		generatedDescription.setLineWrap(true);
		generatedDescription.setBackground(Color.getHSBColor(.33333f, .5f, 1f));
		detailedDescription.setEditorKit(new HTMLEditorKit());
		getComponentPanel().add(detailedDescription);
		settingsWrapper.setListener(this);
		getComponentPanel().setPreferredSize(new Dimension(600, 200));
	}

	/**
	 * @param is
	 *            The configuration input as an XML in {@link InputStream} form.
	 */
	private static void readConfiguration(final InputStream is) {
		try {
			final XMLReader xmlReader = XMLReaderFactory.createXMLReader();
			xmlReader.setContentHandler(new DefaultHandler() {
				private static final String KEY = "key";
				private static final String PAIR = "pair";
				private static final String TYPE = "type";
				private static final String VALUE = "value";
				private static final String htmlValue = "HTML";
				private static final String imageValue = "image";
				private static final String genValue = "generatedExplanation";

				private final StringBuilder content = new StringBuilder();

				private boolean isHTML = false;
				private String key;

				@Override
				public void startElement(final String uri,
						final String localName, final String name,
						final Attributes atts) throws SAXException {
					if (PAIR.equals(localName)) {
						if (content.length() != 0) {
							throw new SAXException("Unexpected element: " + KEY);
						}
						key = atts.getValue(KEY);
						final String type = atts.getValue(TYPE);
						if (imageValue.equals(type)) {
							paths.put(key, atts.getValue(VALUE));
						} else if (htmlValue.equals(type)) {
							isHTML = true;
						} else if (genValue.equals(type)) {
							tooltips.put(key, atts.getValue(VALUE));
						}
					} else if (isHTML) {
						content.append('<');
						content.append(localName);
						for (int i = 0; i < atts.getLength(); ++i) {
							content.append(' ').append(atts.getLocalName(i));
							content.append("=\"");
							content.append(atts.getValue(i)).append('\"');
						}
						content.append('>');
					}
				}

				@Override
				public void ignorableWhitespace(final char[] ch,
						final int start, final int length) throws SAXException {
					if (isHTML) {
						content.append(ch, start, length);
					}
				}

				@Override
				public void endElement(final String uri,
						final String localName, final String name)
						throws SAXException {
					if (isHTML) {
						if (PAIR.equals(localName)) {
							detailedDescriptions.put(key, content.toString());
							content.setLength(0);
							isHTML = false;
						} else {
							content.append("</").append(localName).append(">");
						}
					}
				}

				@Override
				public void characters(final char[] ch, final int start,
						final int length) throws SAXException {
					if (isHTML) {
						content.append(ch, start, length);
					}
				}
			});
			xmlReader.parse(new InputSource(is));
		} catch (final Exception e) {
			CellHTS2NodeModel.logger.warn(
					"Problem processing the tooltip help file.", e);
		}
		if (!detailedDescriptions.containsKey(DEFAULT)) {
			detailedDescriptions.put(DEFAULT,
					"No detailed description available.");
		}
		if (!tooltips.containsKey(DEFAULT)) {
			tooltips.put(DEFAULT, "No generated description available.");
		}
		if (!paths.containsKey(DEFAULT)) {
			paths.put(DEFAULT, "res/noFormulaFound.png");
		}
	}

	@Override
	protected void updateComponent() {
		final String key = settingsWrapper.actualKey();

		final String generatedDesc = getOrDefault(tooltips, key);
		final String imagePath = getOrDefault(paths, key);
		final String detailedDesc = getOrDefault(detailedDescriptions, key);
		try {
			image
					.setIcon(new ImageIcon(readImageData(imagePath),
							generatedDesc));
		} catch (final Exception e) {
			CellHTS2NodeModel.logger.warn("Problem loading formula data: "
					+ imagePath, e);
			try {
				final String defaultPath = paths.get(DEFAULT);
				image.setIcon(new ImageIcon(readImageData(defaultPath),
						generatedDesc));
			} catch (final Exception e0) {
				CellHTS2NodeModel.logger.warn(
						"Problem loading the default formula image: "
								+ imagePath, e0);
			}
		}
		final URL imageUrl = CellHTS2NodeModel.class.getResource(imagePath);
		image.setToolTipText("<html><img src=\"" + imageUrl + "\"/><p>"
				+ generatedDesc.replaceAll("\\n", "</p><p>") + "</p></html>");
		detailedDescription.setText("<html>" + detailedDesc + "</html>");
		generatedDescription.setText(generatedDesc);
		if (helpDialog != null) {
			helpDialog.setToolTipText("<html><img src=\"" + imageUrl
					+ "\"/><p>" + detailedDesc + "</p></html>");
		}
	}

	/**
	 * Reads the image data from the specified {@code imagePath} relative to
	 * {@link CellHTS2NodeModel}.
	 * 
	 * @param imagePath
	 *            A path to an existing image.
	 * @return A byte array of the image file's content.
	 * @throws IOException
	 *             If there was a problem reading the file.
	 */
	private byte[] readImageData(final String imagePath) throws IOException {
		final InputStream is = CellHTS2NodeModel.class
				.getResourceAsStream(imagePath);
		try {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				final byte[] buffer = new byte[8192];
				int len;
				while ((len = is.read(buffer)) >= 0) {
					bos.write(buffer, 0, len);
				}
				return bos.toByteArray();
			} finally {
				bos.close();
			}
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	/**
	 * Gets a value from {@code map} using {@code key}, if not found return with
	 * the value associated to {@link #DEFAULT}.
	 * 
	 * @param map
	 *            A {@link String} &Rarr; {@link String} {@link Map}.
	 * @param key
	 *            A {@link String} key.
	 * @return The associated value to {@code key}, or if not found, the
	 *         associated value to {@link #DEFAULT} in {@code map}.
	 */
	private String getOrDefault(final Map<String, String> map, final String key) {
		return map.containsKey(key) ? map.get(key) : map.get(DEFAULT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent
	 * )
	 */
	@Override
	public void stateChanged(final ChangeEvent e) {
		updateComponent();
	}

	/**
	 * Sets the possible help {@link DialogComponent}.
	 * 
	 * @param helpDialog
	 *            A {@link DialogComponent}. May be {@code null}.
	 */
	public void setHelpComponent(@Nullable final DialogComponent helpDialog) {
		this.helpDialog = helpDialog;
	}
}
