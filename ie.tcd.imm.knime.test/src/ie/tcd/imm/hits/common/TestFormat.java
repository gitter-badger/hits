/*
 * All rights reserved. (C) Copyright 2009, Trinity College Dublin
 */
package ie.tcd.imm.hits.common;

import java.text.spi.NumberFormatProvider;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for {@link Format}'s {@link Format#convertWellToPosition(String) well
 * parsing method}.
 * 
 * @author <a href="bakosg@tcd.ie">Gabor Bakos</a>
 */
public class TestFormat {
	/**
	 * @return Good wells with the expected results.
	 */
	@SuppressWarnings("boxing")
	@DataProvider(name = "goodWells")
	public Object[][] goodWellsProvider() {
		return new Object[][] { { "A01", Format._96, 0 },
				{ "A01", Format._384, 0 }, { "B01", Format._96, 12 },
				{ "B01", Format._384, 24 }, { "H12", Format._96, 95 },
				{ "P24", Format._384, 383 }, { "A1", Format._96, 0 },
				{ "A1", Format._384, 0 }, { "B1", Format._96, 12 },
				{ "B1", Format._384, 24 }, { "A - 01", Format._96, 0 },
				{ "A - 01", Format._384, 0 }, { "B - 01", Format._96, 12 },
				{ "B - 01", Format._384, 24 }, { "H - 12", Format._96, 95 },
				{ "P - 24", Format._384, 383 }, { "A - 1", Format._96, 0 },
				{ "A - 1", Format._384, 0 }, { "B - 1", Format._96, 12 },
				{ "B - 1", Format._384, 24 }, };
	}

	/**
	 * @return All possible values of {@link Format}.
	 */
	@DataProvider(name = "allFormats")
	public Object[][] allFormatsProvider() {
		final Format[] values = Format.values();
		final Object[][] ret = new Object[values.length][1];
		for (int i = values.length; i-- > 0;) {
			ret[i][0] = values[i];
		}
		return ret;
	}

	/**
	 * @return Well-like values which are problematic.
	 */
	@DataProvider(name = "problemProvider")
	public Object[][] problemProvider() {
		return new Object[][] { { "", Format._96 }, { "", Format._384 },
				{ "A", Format._96 }, { "A", Format._384 }, { "J", Format._96 },
				{ "Q", Format._384 }, { "J1", Format._96 },
				{ "Q1", Format._384 }, { "A0", Format._96 },
				{ "A0", Format._384 }, { "A13", Format._96 },
				{ "A25", Format._384 }, };
	}

	/**
	 * Tests the good values.
	 * 
	 * @param well
	 *            The well {@link String}.
	 * @param format
	 *            The {@link Format}.
	 * @param position
	 *            The expected position.
	 */
	@Test(dataProvider = "goodWellsProvider")
	public void goodWells(final String well, final Format format,
			final int position) {
		final int pos = format.convertWellToPosition(well);
		Assert.assertEquals(pos, position);
		Assert.assertTrue(pos >= 0, "Too small: " + pos);
		Assert.assertTrue(pos < format.getRow() * format.getCol(),
				"Too large: " + pos);
	}

	/**
	 * Checks every {@link Format} with null input.
	 * 
	 * @param format
	 *            A {@link Format}.
	 */
	@Test(dataProvider = "allFormats", expectedExceptions = { NullPointerException.class })
	public void nullWell(final Format format) {
		format.convertWellToPosition(null);
	}

	/**
	 * Checks whether the method gives {@link Exception}s or not.
	 * 
	 * @param well
	 *            A well-like {@link String}.
	 * @param format
	 *            A {@link Format}.
	 */
	@Test(dataProvider = "problemProvider", expectedExceptions = {
			StringIndexOutOfBoundsException.class,
			IllegalArgumentException.class, NumberFormatProvider.class })
	public void badWellsStringIndex(final String well, final Format format) {
		format.convertWellToPosition(well);
	}
}
