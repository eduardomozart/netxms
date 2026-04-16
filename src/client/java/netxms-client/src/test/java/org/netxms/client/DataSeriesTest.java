/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2026 Raden Solutions
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.netxms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.netxms.client.datacollection.DataSeries;
import org.netxms.client.datacollection.DciDataRow;

/**
 * Tests for DataSeries class
 */
public class DataSeriesTest
{
   /**
    * Test that getCurrentValue() returns the most recent value (first element),
    * not the last added value. Data arrives newest-first, so values[0] is the
    * most recent measurement.
    */
   @Test
   public void testGetCurrentValueReturnsNewestValue()
   {
      DataSeries series = new DataSeries(1, 1);

      // Add values in the order they arrive from server: newest first
      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(100.0)));          // newest
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(200.0)));  // 1 min ago
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(300.0))); // 2 min ago

      // getCurrentValue() must return the newest value (first element)
      assertEquals(100.0, series.getCurrentValue(), 0.001);
   }

   /**
    * Test that getCurrentValueAsString() returns the most recent value as string.
    */
   @Test
   public void testGetCurrentValueAsStringReturnsNewestValue()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), "newest"));
      series.addDataRow(new DciDataRow(new Date(now - 60000), "older"));
      series.addDataRow(new DciDataRow(new Date(now - 120000), "oldest"));

      assertEquals("newest", series.getCurrentValueAsString());
   }

   /**
    * Test that getLastAddedValue() returns the last added value (last element),
    * which is distinct from getCurrentValue().
    */
   @Test
   public void testGetLastAddedValueReturnsLastAdded()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(100.0)));
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(200.0)));
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(300.0)));

      // getLastAddedValue() returns the last added element (oldest in time)
      assertEquals(300.0, series.getLastAddedValue().getValueAsDouble(), 0.001);
   }

   /**
    * Test getCurrentValue() on empty series.
    */
   @Test
   public void testGetCurrentValueOnEmptySeries()
   {
      DataSeries series = new DataSeries(1, 1);
      assertEquals(0.0, series.getCurrentValue(), 0.001);
      assertEquals("", series.getCurrentValueAsString());
   }

   /**
    * Test getCurrentValue() on single-value series.
    */
   @Test
   public void testGetCurrentValueOnSingleValueSeries()
   {
      DataSeries series = new DataSeries(42.5);
      assertEquals(42.5, series.getCurrentValue(), 0.001);
   }

   /**
    * Test getMinValue() returns the smallest value in the series.
    */
   @Test
   public void testGetMinValue()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(50.0)));
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(10.0)));
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(80.0)));

      assertEquals(10.0, series.getMinValue(), 0.001);
   }

   /**
    * Test getMaxValue() returns the largest value in the series.
    */
   @Test
   public void testGetMaxValue()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(50.0)));
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(10.0)));
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(80.0)));

      assertEquals(80.0, series.getMaxValue(), 0.001);
   }

   /**
    * Test getAverageValue() returns the mean of all values.
    */
   @Test
   public void testGetAverageValue()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(10.0)));
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(20.0)));
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(30.0)));

      assertEquals(20.0, series.getAverageValue(), 0.001);
   }

   /**
    * Test min/max/avg on empty series return zero.
    */
   @Test
   public void testAggregationsOnEmptySeries()
   {
      DataSeries series = new DataSeries(1, 1);
      assertEquals(0.0, series.getMinValue(), 0.001);
      assertEquals(0.0, series.getMaxValue(), 0.001);
      assertEquals(0.0, series.getAverageValue(), 0.001);
   }

   /**
    * Test min/max/avg with negative values.
    */
   @Test
   public void testAggregationsWithNegativeValues()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(-5.0)));
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(10.0)));
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(-15.0)));

      assertEquals(-15.0, series.getMinValue(), 0.001);
      assertEquals(10.0, series.getMaxValue(), 0.001);
      assertEquals(-10.0 / 3.0, series.getAverageValue(), 0.001);
   }

   /**
    * Test that invert() negates all values in the series.
    */
   @Test
   public void testInvert()
   {
      DataSeries series = new DataSeries(1, 1);

      long now = System.currentTimeMillis();
      series.addDataRow(new DciDataRow(new Date(now), Double.valueOf(100.0)));
      series.addDataRow(new DciDataRow(new Date(now - 60000), Double.valueOf(-50.0)));
      series.addDataRow(new DciDataRow(new Date(now - 120000), Double.valueOf(0.0)));

      series.invert();

      DciDataRow[] values = series.getValues();
      assertEquals(-100.0, values[0].getValueAsDouble(), 0.001);
      assertEquals(50.0, values[1].getValueAsDouble(), 0.001);
      assertEquals(-0.0, values[2].getValueAsDouble(), 0.001);
   }

   /**
    * Test getLastAddedValue() on empty series returns null.
    */
   @Test
   public void testGetLastAddedValueOnEmptySeries()
   {
      DataSeries series = new DataSeries(1, 1);
      assertNull(series.getLastAddedValue());
   }
}
