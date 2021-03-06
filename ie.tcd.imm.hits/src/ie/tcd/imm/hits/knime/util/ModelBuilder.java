/*
 * All rights reserved. (C) Copyright 2009, Trinity College Dublin
 */
package ie.tcd.imm.hits.knime.util;

import ie.tcd.imm.hits.common.Format;
import ie.tcd.imm.hits.common.PossibleStatistics;
import ie.tcd.imm.hits.common.PublicConstants;
import ie.tcd.imm.hits.common.PublicConstants.StaticUtil;
import ie.tcd.imm.hits.knime.view.heatmap.HeatmapNodeModel.StatTypes;
import ie.tcd.imm.hits.util.swing.colour.ColourSelector.RangeType;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.StringCell;
import org.knime.core.util.Pair;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;

/**
 * Constructs a model of the data from a {@link DataTable}. This data is
 * organised by experiment, normalisation parameters, plate, replicate,
 * parameter, statistics.
 * 
 * @author <a href="mailto:bakosg@tcd.ie">Gabor Bakos</a>
 */
@DefaultAnnotation( { Nonnull.class, CheckReturnValue.class, Nonnegative.class })
public class ModelBuilder extends SimpleModelBuilder {
	private static final long serialVersionUID = 6605965494973262221L;

	/** key, plate, position [0-95] */
	private final Map<String, Pair<Integer, Integer>> keyToPlateAndPosition = new HashMap<String, Pair<Integer, Integer>>();

	/**
	 * Constructs a {@link ModelBuilder} with creating a {@link SpecAnalyser}
	 * for that {@code table}.
	 * 
	 * @param table
	 *            A {@link DataTable}.
	 */
	public ModelBuilder(final DataTable table) {
		this(table, new SpecAnalyser(table.getDataTableSpec()));
	}

	/**
	 * Constructs a {@link ModelBuilder} with a compatible {@code specAnalyser}
	 * for that {@code table}.
	 * 
	 * @param table
	 *            A {@link DataTable}.
	 * @param specAnalyser
	 *            A {@link SpecAnalyser} compatible with {@code table}.
	 */
	public ModelBuilder(final DataTable table, final SpecAnalyser specAnalyser) {
		super(table, specAnalyser);
		generate(specAnalyser);
	}

	/** The prefix for {@link PossibleStatistics#RAW_PER_PLATE_REPLICATE_MEAN}. */
	public static final String RAW_PLATE_REPLICATE_MEDIAN_START = StaticUtil
			.createPrefix(PossibleStatistics.RAW_PER_PLATE_REPLICATE_MEAN);

	/** The prefix for {@link PossibleStatistics#MEAN_OR_DIFF}. */
	public static final String MEAN_OR_DIFF_START = StaticUtil
			.createPrefix(PossibleStatistics.MEAN_OR_DIFF);

	/** The prefix for {@link PossibleStatistics#NORMALISED}. */
	public static final String NORMALISED_START = StaticUtil
			.createPrefix(PossibleStatistics.NORMALISED);

	/** The prefix for {@link PossibleStatistics#MEDIAN}. */
	public static final String MEDIAN_START = StaticUtil
			.createPrefix(PossibleStatistics.MEDIAN);

	/** The prefix for {@link PossibleStatistics#RAW}. */
	public static final String RAW_START = StaticUtil
			.createPrefix(PossibleStatistics.RAW);

	/** The prefix for {@link PossibleStatistics#SCORE}. */
	public static final String SCORE_START = StaticUtil
			.createPrefix(PossibleStatistics.SCORE);

	private final Map<String, Map<String, Map<Integer, Map<Integer, Map<String, Map<StatTypes, double[]>>>>>> replicates = new TreeMap<String, Map<String, Map<Integer, Map<Integer, Map<String, Map<StatTypes, double[]>>>>>>();
	private final Map<String, Map<String, Map<Integer, Map<String, Map<StatTypes, double[]>>>>> scores = new TreeMap<String, Map<String, Map<Integer, Map<String, Map<StatTypes, double[]>>>>>();
	private final Map<String, Map<String, Map<Integer, Map<String, String[]>>>> texts = new TreeMap<String, Map<String, Map<Integer, Map<String, String[]>>>>();
	private final Map<String, Map<String, Map<Integer, Color[]>>> colours = new TreeMap<String, Map<String, Map<Integer, Color[]>>>();

	private int minReplicate;

	private int maxReplicate;

	private int minPlate;

	private int maxPlate;

	/**
	 * This class is to analyse the structure of the table only by the columns.
	 * It will try to find the proper indices of the most important columns, and
	 * the present {@link SpecAnalyser#getStatistics() statistics},
	 * {@link SpecAnalyser#getParameters() parameters} (union of them).
	 */
	public static class SpecAnalyser implements Serializable {
		private static final long serialVersionUID = -8068918974431507830L;
		private int plateIndex;
		private int replicateIndex;
		private int wellIndex;
		private int experimentIndex;
		private int normMethodIndex;
		private int logTransformIndex;
		private int normKindIndex;
		private int varianceAdjustmentIndex;
		private int scoreMethodIndex;
		private int sumMethodIndex;
		private final EnumSet<StatTypes> statistics;
		private final List<String> parameters;
		private boolean hasReplicate;
		private boolean hasPlate;
		private final EnumMap<StatTypes, Map<String, Integer>> indices;
		private final Map<String, Integer> stringIndices;
		private final Map<String, Integer> valueIndices;

		private final Format predictedFormat;

		/**
		 * Constructs a {@link SpecAnalyser} using {@code tableSpec}.
		 * 
		 * @param tableSpec
		 *            A {@link DataTableSpec}.
		 */
		public SpecAnalyser(final DataTableSpec tableSpec) {
			this(tableSpec, true);
		}

		/**
		 * Constructs a {@link SpecAnalyser} using {@code tableSpec}.
		 * 
		 * @param tableSpec
		 *            A {@link DataTableSpec}.
		 * @param checks
		 *            Checks some constraints if {@code true}.
		 */
		public SpecAnalyser(final DataTableSpec tableSpec, final boolean checks) {
			super();
			hasPlate = false;
			hasReplicate = false;
			parameters = new ArrayList<String>();
			statistics = EnumSet.noneOf(StatTypes.class);
			plateIndex = -1;
			replicateIndex = -1;
			wellIndex = -1;
			experimentIndex = -1;
			normMethodIndex = -1;
			logTransformIndex = -1;
			normKindIndex = -1;
			varianceAdjustmentIndex = -1;
			scoreMethodIndex = -1;
			sumMethodIndex = -1;
			indices = new EnumMap<StatTypes, Map<String, Integer>>(
					StatTypes.class);
			for (final StatTypes stat : StatTypes.values()) {
				indices.put(stat, new LinkedHashMap<String, Integer>());
			}
			stringIndices = new TreeMap<String, Integer>();
			valueIndices = new LinkedHashMap<String, Integer>();

			Format format = Format._1536;
			int idx = -1;
			for (final DataColumnSpec spec : tableSpec) {
				++idx;
				final String specName = spec.getName();
				if (specName.startsWith(SCORE_START)) {
					parameters.add(specName.substring(SCORE_START.length()));
					statistics.add(StatTypes.score);
					indices.get(StatTypes.score).put(
							specName.substring(SCORE_START.length()),
							Integer.valueOf(idx));
					continue;
				}
				if (specName.startsWith(RAW_START)) {
					statistics.add(StatTypes.raw);
					indices.get(StatTypes.raw).put(
							specName.substring(RAW_START.length()),
							Integer.valueOf(idx));
					continue;
				}
				if (specName.startsWith(MEDIAN_START)) {
					statistics.add(StatTypes.median);
					indices.get(StatTypes.median).put(
							specName.substring(MEDIAN_START.length()),
							Integer.valueOf(idx));
					continue;
				}
				if (specName.startsWith(NORMALISED_START)) {
					statistics.add(StatTypes.normalised);
					indices.get(StatTypes.normalised).put(
							specName.substring(NORMALISED_START.length()),
							Integer.valueOf(idx));
					continue;
				}
				if (specName.startsWith(MEAN_OR_DIFF_START)) {
					statistics.add(StatTypes.meanOrDiff);
					indices.get(StatTypes.meanOrDiff).put(
							specName.substring(MEAN_OR_DIFF_START.length()),
							Integer.valueOf(idx));
					continue;
				}
				if (specName.startsWith(RAW_PLATE_REPLICATE_MEDIAN_START)) {
					statistics.add(StatTypes.rawPerMedian);
					indices.get(StatTypes.rawPerMedian).put(
							specName.substring(RAW_PLATE_REPLICATE_MEDIAN_START
									.length()), Integer.valueOf(idx));
					continue;
				}
				if (specName.equalsIgnoreCase(PublicConstants.PLATE_COLUMN)) {
					hasPlate = true;
					plateIndex = Integer.valueOf(idx);
					continue;
				}
				if (specName.equalsIgnoreCase(PublicConstants.REPLICATE_COLUMN)) {
					hasReplicate = true;
					replicateIndex = idx;
					continue;
				}
				if (specName.equalsIgnoreCase("well")) {
					wellIndex = idx;
					int maxRow = -1, maxCol = -1;
					final Set<DataCell> values = spec.getDomain().getValues();
					if (values != null) {
						for (final DataCell cell : values) {
							if (cell instanceof StringValue) {
								final StringValue stringCell = (StringValue) cell;
								try {
									final int position = Format._1536
											.convertWellToPosition(stringCell
													.getStringValue());
									maxRow = Math.max(maxRow, position
											% Format._1536.getCol());
									maxCol = Math.max(maxCol, position
											/ Format._1536.getCol());
								} catch (final RuntimeException e) {
									// Do not mind.
								}
							}
						}
					}
					if (maxCol >= Format._1536.getCol()
							|| maxRow >= Format._1536.getCol()) {
						throw new IllegalStateException("Too large plate!");
					}
					if (maxCol >= Format._384.getCol()
							|| maxRow >= Format._384.getCol()) {
						format = Format._1536;
					} else if (maxCol >= Format._96.getCol()
							|| maxRow >= Format._96.getCol()) {
						format = Format._384;
					} else if (maxCol >= 0 && maxRow >= 0) {
						format = Format._96;
					}
					continue;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.EXPERIMENT_COLUMN)) {
					experimentIndex = idx;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.NORMALISATION_METHOD_COLUMN)) {
					normMethodIndex = idx;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.LOG_TRANSFORM_COLUMN)) {
					logTransformIndex = idx;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.NORMALISATION_KIND_COLUMN)) {
					normKindIndex = idx;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.VARIANCE_ADJUSTMENT_COLUMN)) {
					varianceAdjustmentIndex = idx;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.SCORING_METHOD_COLUMN)) {
					scoreMethodIndex = idx;
				}
				if (specName
						.equalsIgnoreCase(PublicConstants.SUMMARISE_METHOD_COLUMN)) {
					sumMethodIndex = idx;
				}
				if (spec.getType().isCompatible(StringValue.class)) {
					stringIndices.put(specName, Integer.valueOf(idx));
					continue;
				}
				if (spec.getType().isCompatible(DoubleValue.class)) {
					valueIndices.put(specName, Integer.valueOf(idx));
				}
			}
			if (!hasPlate && checks) {
				throw new IllegalStateException("No plate information found");
			}
			predictedFormat = format;
		}

		/**
		 * @return The range map for with based on specs.
		 */
		public Map<String, Map<StatTypes, Map<RangeType, Double>>> initialRanges() {
			final Map<String, Map<StatTypes, Map<RangeType, Double>>> ret = new HashMap<String, Map<StatTypes, Map<RangeType, Double>>>();
			for (final String str : getParameters()) {
				final EnumMap<StatTypes, Map<RangeType, Double>> map;
				ret.put(str,
						map = new EnumMap<StatTypes, Map<RangeType, Double>>(
								StatTypes.class));
				for (final StatTypes stat : getStatistics()) {
					map.put(stat, new EnumMap<RangeType, Double>(
							RangeType.class));
				}
			}
			return ret;
		}

		/**
		 * @return The parameters (like Cell 1/Form factor, ...) present in the
		 *         table.
		 */
		public List<String> getParameters() {
			return parameters.isEmpty() ? new ArrayList<String>(valueIndices
					.keySet()) : Collections.unmodifiableList(parameters);
		}

		/**
		 * @return The {@link StatTypes} present in the table.
		 */
		public EnumSet<StatTypes> getStatistics() {
			return statistics.isEmpty() ? EnumSet.of(StatTypes.otherNumeric)
					: statistics;
		}

		/**
		 * @return the plateIndex
		 */
		public int getPlateIndex() {
			return plateIndex;
		}

		/**
		 * @return the replicateIndex
		 */
		public int getReplicateIndex() {
			return replicateIndex;
		}

		/**
		 * @return the wellIndex
		 */
		public int getWellIndex() {
			return wellIndex;
		}

		/**
		 * @return the experimentIndex
		 */
		public int getExperimentIndex() {
			return experimentIndex;
		}

		/**
		 * @return the normMethodIndex
		 */
		public int getNormMethodIndex() {
			return normMethodIndex;
		}

		/**
		 * @return the logTransformIndex
		 */
		public int getLogTransformIndex() {
			return logTransformIndex;
		}

		/**
		 * @return the normKindIndex
		 */
		public int getNormKindIndex() {
			return normKindIndex;
		}

		/**
		 * @return the varianceAdjustmentIndex
		 */
		public int getVarianceAdjustmentIndex() {
			return varianceAdjustmentIndex;
		}

		/**
		 * @return the scoreMethodIndex
		 */
		public int getScoreMethodIndex() {
			return scoreMethodIndex;
		}

		/**
		 * @return the sumMethodIndex
		 */
		public int getSumMethodIndex() {
			return sumMethodIndex;
		}

		/**
		 * @return the hasReplicate
		 */
		public boolean isHasReplicate() {
			return hasReplicate;
		}

		/**
		 * @return the hasPlate
		 */
		public boolean isHasPlate() {
			return hasPlate;
		}

		/**
		 * @return the indices (stat, parameter, index (from {@code 0}))
		 */
		public EnumMap<StatTypes, Map<String, Integer>> getIndices() {
			return indices;
		}

		/**
		 * @return the stringIndices
		 */
		public Map<String, Integer> getStringIndices() {
			return stringIndices;
		}

		/**
		 * @return the valueIndices
		 */
		public Map<String, Integer> getValueIndices() {
			return valueIndices;
		}

		/**
		 * @return The predicted {@link Format} of plates.
		 */
		public Format getPredictedFormat() {
			return predictedFormat;
		}
	}

	private void generate(final SpecAnalyser specAnalyser) {
		minReplicate = Integer.MAX_VALUE;
		maxReplicate = Integer.MIN_VALUE;
		final int experimentIndex = specAnalyser.getExperimentIndex();
		final int normMethodIndex = specAnalyser.getNormMethodIndex();
		final int logTransformIndex = specAnalyser.getLogTransformIndex();
		final int normKindIndex = specAnalyser.getNormKindIndex();
		final int varianceAdjustmentIndex = specAnalyser
				.getVarianceAdjustmentIndex();
		final int scoreMethodIndex = specAnalyser.getScoreMethodIndex();
		final int sumMethodIndex = specAnalyser.getSumMethodIndex();
		final int plateIndex = specAnalyser.getPlateIndex();
		final int replicateIndex = specAnalyser.getReplicateIndex();
		final int wellIndex = specAnalyser.getWellIndex();
		if (wellIndex == -1) {
			throw new IllegalStateException("No "
					+ PublicConstants.WELL_COL_NAME + " column present!");
		}
		final EnumMap<StatTypes, Map<String, Integer>> indices = specAnalyser
				.getIndices();
		final Map<String, Integer> stringIndices = specAnalyser
				.getStringIndices();
		final List<String> parameters = specAnalyser.getParameters();
		final EnumSet<StatTypes> statistics = specAnalyser.getStatistics();
		final boolean hasReplicate = specAnalyser.isHasReplicate();
		minPlate = Integer.MAX_VALUE;
		maxPlate = Integer.MIN_VALUE;
		for (final DataRow dataRow : getTable()) {
			final String experiment = experimentIndex == -1 ? ""
					: ((StringCell) dataRow.getCell(experimentIndex))
							.getStringValue();
			checkAndPutTreeMap(experiment, replicates);
			checkAndPutTreeMap(experiment, scores);
			checkAndPutTreeMap(experiment, texts);
			checkAndPutTreeMap(experiment, colours);
			final Map<String, Map<Integer, Map<Integer, Map<String, Map<StatTypes, double[]>>>>> normMethodValues = replicates
					.get(experiment);
			final Map<String, Map<Integer, Map<String, Map<StatTypes, double[]>>>> scoreNormMethodValues = scores
					.get(experiment);
			final Map<String, Map<Integer, Map<String, String[]>>> textsNormMethodValues = texts
					.get(experiment);
			final Map<String, Map<Integer, Color[]>> colourNormMethodValues = colours
					.get(experiment);
			final String normKey = getNormKey(dataRow, normMethodIndex,
					logTransformIndex, normKindIndex, varianceAdjustmentIndex,
					scoreMethodIndex, sumMethodIndex);
			checkAndPutHashMap(normKey, normMethodValues);
			checkAndPutHashMap(normKey, scoreNormMethodValues);
			checkAndPutHashMap(normKey, textsNormMethodValues);
			checkAndPutHashMap(normKey, colourNormMethodValues);
			final Map<Integer, Map<Integer, Map<String, Map<StatTypes, double[]>>>> replicateValues = normMethodValues
					.get(normKey);
			final Map<Integer, Map<String, Map<StatTypes, double[]>>> scoreValues = scoreNormMethodValues
					.get(normKey);
			final Map<Integer, Map<String, String[]>> textValues = textsNormMethodValues
					.get(normKey);
			final Map<Integer, Color[]> colourValues = colourNormMethodValues
					.get(normKey);
			final Integer plate = getInt(dataRow, plateIndex);
			minPlate = Math.min(minPlate, plate.intValue());
			maxPlate = Math.max(maxPlate, plate.intValue());
			checkAndPutHashMap(plate, replicateValues);
			checkAndPutHashMap(plate, scoreValues);
			if (!textValues.containsKey(plate)) {
				final HashMap<String, String[]> map = new HashMap<String, String[]>();
				textValues.put(plate, map);
				for (final String colName : stringIndices.keySet()) {
					map.put(colName, new String[specAnalyser
							.getPredictedFormat().getWellCount()]);
				}
			}
			if (!colourValues.containsKey(plate)) {
				colourValues.put(plate, new Color[specAnalyser
						.getPredictedFormat().getWellCount()]);
			}
			final Map<String, String[]> textColumns = textValues.get(plate);
			final int well = specAnalyser.getPredictedFormat()
					.convertWellToPosition(
							((StringCell) dataRow.getCell(wellIndex))
									.getStringValue());
			colourValues.get(plate)[well] = getTable().getDataTableSpec()
					.getRowColor(dataRow).getColor();
			for (final Entry<String, Integer> entry : stringIndices.entrySet()) {
				final DataCell cell = dataRow.getCell(entry.getValue()
						.intValue());
				textColumns.get(entry.getKey())[well] = cell == DataType
						.getMissingCell() ? "" : ((StringValue) cell)
						.getStringValue();
			}
			keyToPlateAndPosition.put(dataRow.getKey().getString(),
					new Pair<Integer, Integer>(plate, Integer.valueOf(well)));
			if (hasReplicate) {
				final Integer replicate = getInt(dataRow, replicateIndex);
				minReplicate = Math.min(replicate.intValue(), minReplicate);
				maxReplicate = Math.max(replicate.intValue(), maxReplicate);
				final Map<Integer, Map<String, Map<StatTypes, double[]>>> outerMap = replicateValues
						.get(plate);
				checkAndPutHashMap(replicate, outerMap);
				final Map<String, Map<StatTypes, double[]>> paramMap = outerMap
						.get(replicate);
				for (final String param : specAnalyser.getValueIndices()
						.keySet()) {
					checkAndPutEnumMap(param, paramMap, StatTypes.class);
					if (!paramMap.containsKey(param)) {
						paramMap.put(param, new EnumMap<StatTypes, double[]>(
								StatTypes.class));
					}
					final Map<StatTypes, double[]> enumMap = paramMap
							.get(param);
					final StatTypes stat = StatTypes.otherNumeric;
					if (!statistics.contains(stat)) {
						continue;
					}
					if (!enumMap.containsKey(stat)) {
						enumMap.put(stat, createPlateValues(specAnalyser
								.getPredictedFormat().getWellCount()));
					}
					final DataCell cell = dataRow
							.getCell(specAnalyser.valueIndices.get(param)
									.intValue());
					enumMap.get(stat)[well] = cell instanceof DoubleValue ? ((DoubleValue) cell)
							.getDoubleValue()
							: Double.NaN;
				}
				for (final String param : parameters) {
					if (!paramMap.containsKey(param)) {
						paramMap.put(param, new EnumMap<StatTypes, double[]>(
								StatTypes.class));
					}
					final Map<StatTypes, double[]> enumMap = paramMap
							.get(param);
					for (final StatTypes stat : StatTypes.replicateTypes) {
						if (!statistics.contains(stat)) {
							continue;
						}
						if (!enumMap.containsKey(stat)) {
							enumMap.put(stat, createPlateValues(specAnalyser
									.getPredictedFormat().getWellCount()));
						}
						final DataCell cell = dataRow.getCell(indices.get(stat)
								.get(param).intValue());
						enumMap.get(stat)[well] = cell instanceof DoubleValue ? ((DoubleValue) cell)
								.getDoubleValue()
								: Double.NaN;
					}
				}
			}
			if (statistics.contains(StatTypes.score)
					|| statistics.contains(StatTypes.meanOrDiff)
					|| statistics.contains(StatTypes.median)) {
				final Map<String, Map<StatTypes, double[]>> map = scoreValues
						.get(plate);
				for (final String param : parameters) {
					if (!map.containsKey(param)) {
						map.put(param, new EnumMap<StatTypes, double[]>(
								StatTypes.class));
						for (final StatTypes type : StatTypes.scoreTypes) {
							map.get(param).put(
									type,
									createPlateValues(specAnalyser
											.getPredictedFormat()
											.getWellCount()));
						}
					}
					final Map<StatTypes, double[]> values = map.get(param);
					for (final StatTypes type : StatTypes.scoreTypes) {
						if (!statistics.contains(type)) {
							continue;
						}
						final double[] vals = values.get(type);
						final DataCell cell = dataRow.getCell(indices.get(type)
								.get(param).intValue());
						vals[well] = cell instanceof DoubleValue ? ((DoubleValue) cell)
								.getDoubleValue()
								: Double.NaN;
					}
				}
			}
		}
	}

	/**
	 * Add an {@link EnumMap} to {@code map} if {@code key} was not previously
	 * contained.
	 * 
	 * @param <K1>
	 *            Type of keys in outer map.
	 * @param <K2>
	 *            Type of keys in inner map.
	 * @param <V>
	 *            Type of values in inner map.
	 * @param key
	 *            A key of {@code map}.
	 * @param map
	 *            The map to check the key.
	 * @param cls
	 *            The class of the inner (enum) map's keys.
	 */
	private static <K1, K2 extends Enum<K2>, V> void checkAndPutEnumMap(
			final K1 key, final Map<K1, Map<K2, V>> map, final Class<K2> cls) {
		if (!map.containsKey(key)) {
			map.put(key, new EnumMap<K2, V>(cls));
		}
	}

	/**
	 * Add a {@link HashMap} to {@code map} if {@code key} was not previously
	 * contained.
	 * 
	 * @param <K1>
	 *            Type of keys in outer map.
	 * @param <K2>
	 *            Type of keys in inner map.
	 * @param <V>
	 *            Type of values in inner map.
	 * @param key
	 *            A key of {@code map}.
	 * @param map
	 *            The map to check the key.
	 */
	private static <K1, K2, V> void checkAndPutHashMap(final K1 key,
			final Map<K1, Map<K2, V>> map) {
		if (!map.containsKey(key)) {
			map.put(key, new HashMap<K2, V>());
		}
	}

	/**
	 * Add a {@link TreeMap} to {@code map} if {@code key} was not previously
	 * contained.
	 * 
	 * @param <K1>
	 *            Type of keys in outer map.
	 * @param <K2>
	 *            Type of keys in inner map.
	 * @param <V>
	 *            Type of values in inner map.
	 * @param key
	 *            A key of {@code map}.
	 * @param map
	 *            The map to check the key.
	 */
	private static <K1, K2 extends Comparable<K2>, V> void checkAndPutTreeMap(
			final K1 key, final Map<K1, Map<K2, V>> map) {
		if (!map.containsKey(key)) {
			map.put(key, new TreeMap<K2, V>());
		}
	}

	/**
	 * Creates a {@code double} array with length {@code count} and fills with
	 * {@link Double#NaN}.
	 * 
	 * @param count
	 *            The length of the returned {@code double} array.
	 * @return An array with {@link Double#NaN}s.
	 */
	public static double[] createPlateValues(final int count) {
		final double[] ds = new double[count];
		Arrays.fill(ds, Double.NaN);
		return ds;
	}

	/**
	 * Creates a {@link String} for the normalisation/scoring parameters.
	 * 
	 * @param dataRow
	 *            A {@link DataRow}.
	 * @param normMethodIdx
	 *            A {@code 0} based index for normalisation method.
	 * @param logTransformIdx
	 *            A {@code 0} based index for log transform.
	 * @param normKindIdx
	 *            A {@code 0} based index for multiplicative/additive
	 *            normalisation.
	 * @param varianceAdjustmentIdx
	 *            A {@code 0} based index for variance adjustment.
	 * @param scoreMethodIdx
	 *            A {@code 0} based index for scoring method.
	 * @param summariseMethodIdx
	 *            A {@code 0} based index for summarisation method of
	 *            replicates.
	 * @return A {@link String} created by the values from {@link DataRow}
	 *         separated by {@code _}.
	 */
	public static String getNormKey(final DataRow dataRow,
			final int normMethodIdx, final int logTransformIdx,
			final int normKindIdx, final int varianceAdjustmentIdx,
			final int scoreMethodIdx, final int summariseMethodIdx) {
		return getOrEmpty(dataRow, normMethodIdx, "_")
				+ getOrEmpty(dataRow, logTransformIdx, "_")
				+ getOrEmpty(dataRow, normKindIdx, "_")
				+ getOrEmpty(dataRow, varianceAdjustmentIdx, "_")
				+ getOrEmpty(dataRow, scoreMethodIdx, "_")
				+ getOrEmpty(dataRow, summariseMethodIdx, "");
	}

	/**
	 * @param dataRow
	 *            A {@link DataRow}.
	 * @param index
	 *            Index of column in {@link DataRow}. (Might be {@code -1}.)
	 * @param suffix
	 *            The suffix to append to the result if {@code index} is not
	 *            {@code -1}.
	 * @return The selected value from {@code dataRow} with the specified
	 *         {@code suffix}, or empty {@link String} if not the {@code index}
	 *         is {@code -1}.
	 */
	private static String getOrEmpty(final DataRow dataRow, final int index,
			final String suffix) {
		return index == -1 ? "" : dataRow.getCell(index) + suffix;
	}

	/**
	 * @return the replicate dependent {@link StatTypes statistics} of
	 *         {@link #getTable() table}, the dimensions are these:
	 *         <ul>
	 *         <li>experiment</li>
	 *         <li>normalisations (
	 *         {@link #getNormKey(DataRow, int, int, int, int, int, int)})</li>
	 *         <li>plate</li>
	 *         <li>replicate</li>
	 *         <li>parameter</li>
	 *         <li>statistics type</li>
	 *         <li>{@double double}s to for each position on plate. (May contain
	 *         {@link Double#NaN} values)</li>
	 *         </ul>
	 * @see StatTypes#isUseReplicates() {@code true}.
	 */
	public Map<String, Map<String, Map<Integer, Map<Integer, Map<String, Map<StatTypes, double[]>>>>>> getReplicates() {
		return replicates;
	}

	/**
	 * @return the replicate independent {@link StatTypes statistics} of
	 *         {@link #getTable() table}, the dimensions are these:
	 *         <ul>
	 *         <li>experiment</li>
	 *         <li>normalisations (
	 *         {@link #getNormKey(DataRow, int, int, int, int, int, int)})</li>
	 *         <li>plate</li>
	 *         <li>parameter</li>
	 *         <li>statistics type</li>
	 *         <li>{@double double}s to for each position on plate. (May contain
	 *         {@link Double#NaN} values)</li>
	 *         </ul>
	 * @see StatTypes#isUseReplicates() {@code false}.
	 */
	public Map<String, Map<String, Map<Integer, Map<String, Map<StatTypes, double[]>>>>> getScores() {
		return scores;
	}

	/**
	 * @return the texts of {@link #getTable() table}, the dimensions are these:
	 *         <ul>
	 *         <li>experiment</li>
	 *         <li>normalisations (
	 *         {@link #getNormKey(DataRow, int, int, int, int, int, int)})</li>
	 *         <li>plate</li>
	 *         <li>column name</li>
	 *         <li>{@link String}s for each position on plate. (May contain
	 *         {@code null} values)</li>
	 *         </ul>
	 */
	public Map<String, Map<String, Map<Integer, Map<String, String[]>>>> getTexts() {
		return texts;
	}

	/**
	 * @return the colours of {@link #getTable() table}, the dimensions are
	 *         these:
	 *         <ul>
	 *         <li>experiment</li>
	 *         <li>normalisations (
	 *         {@link #getNormKey(DataRow, int, int, int, int, int, int)})</li>
	 *         <li>plate</li>
	 *         <li>{@link Color}s for each position on plate. (May contain
	 *         {@code null} values)</li>
	 *         </ul>
	 */
	public Map<String, Map<String, Map<Integer, Color[]>>> getColours() {
		return colours;
	}

	/**
	 * @return the smallest plate value
	 */
	public int getMinPlate() {
		return minPlate;
	}

	/**
	 * @return the largest plate value
	 */
	public int getMaxPlate() {
		return maxPlate;
	}

	/**
	 * @return the smallest replicate value
	 */
	public int getMinReplicate() {
		return minReplicate;
	}

	/**
	 * @return the largest replicate value
	 */
	public int getMaxReplicate() {
		return maxReplicate;
	}

	/**
	 * @return An unmodifiable {@link Map} from the row ids to a {@link Pair} of
	 *         plate, position ({@code 0-95}) values.
	 */
	public Map<String, Pair<Integer, Integer>> getKeyToPlateAndPosition() {
		return Collections.unmodifiableMap(keyToPlateAndPosition);
	}
}
