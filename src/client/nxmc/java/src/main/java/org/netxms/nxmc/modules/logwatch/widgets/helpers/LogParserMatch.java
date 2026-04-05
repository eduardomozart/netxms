/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2012 Victor Kirhenshtein
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
package org.netxms.nxmc.modules.logwatch.widgets.helpers;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

/**
 * Event in log parser rule
 */
@Root(name="match", strict=false)
public class LogParserMatch
{
	@Text
	private String match = ".*"; //$NON-NLS-1$

	@Attribute(required=false)
	private String invert = null;

   @Attribute(required=false)
   private Integer repeatCount = null;

   @Attribute(required=false)
   private Integer repeatInterval = null;

   @Attribute(required=false)
   private String reset = null;

   @Attribute(required=false)
   private String absence = null;

   @Attribute(required=false)
   private Integer absenceInterval = null;

   @Attribute(required=false)
   private Integer absenceRealertInterval = null;

	/**
	 * Protected constructor for XML parser
	 */
	protected LogParserMatch()
	{
	}

	/**
	 * @param match regular expression
	 * @param invert invert match
	 * @param repeatCount repeat count
	 * @param repeatInterval repeat interval
	 * @param reset reset repeat count on match
	 */
	public LogParserMatch(String match, boolean invert, Integer repeatCount, Integer repeatInterval, boolean reset)
	{
		this.match = match;
		setInvert(invert);
		this.repeatCount = repeatCount;
		this.repeatInterval = repeatInterval;
		setReset(reset);
	}

   /**
    * @return the repeatCount
    */
   public Integer getRepeatCount()
   {
      return repeatCount == null ? 0 : repeatCount;
   }

   /**
    * @param repeatCount the repeatCount to set
    */
   public void setRepeatCount(Integer repeatCount)
   {
      this.repeatCount = repeatCount;
   }

   /**
    * @return the repeatInterval
    */
   public Integer getRepeatInterval()
   {
      return repeatInterval == null ? 0 : repeatInterval;
   }

   /**
    * @param repeatInterval the repeatInterval to set
    */
   public void setRepeatInterval(Integer repeatInterval)
   {
      this.repeatInterval = repeatInterval;
   }

   /**
    * @return the reset
    */
   public boolean getReset()
   {
      return reset == null ? true : LogParser.stringToBoolean(reset);
   }

   /**
    * @param reset the reset to set
    */
   public void setReset(boolean reset)
   {
      this.reset = reset ? null : "false";
   }

   /**
    * @return the match
    */
   public String getMatch()
   {
      return match;
   }

   /**
    * @param match the match to set
    */
   public void setMatch(String match)
   {
      this.match = match;
   }

   /**
    * @return the invert
    */
   public boolean getInvert()
   {
      return LogParser.stringToBoolean(invert);
   }

   /**
    * @param invert the invert to set
    */
   public void setInvert(boolean invert)
   {
      this.invert = LogParser.booleanToString(invert);
   }

   /**
    * Convert seconds to the best-fit time range value (dividing by the largest fitting unit).
    *
    * @param seconds total seconds (null treated as 0)
    * @param includeDays if true, also consider days as a unit
    * @return the display value in the best-fit unit
    */
   private static int toTimeRange(Integer seconds, boolean includeDays)
   {
      if (seconds == null || seconds == 0)
         return 0;
      int interval = seconds;
      if ((interval % 60) == 0)
      {
         interval /= 60;
         if ((interval % 60) == 0)
         {
            interval /= 60;
            if (includeDays && (interval % 24) == 0)
            {
               interval /= 24;
            }
         }
      }
      return interval;
   }

   /**
    * Determine the best-fit time unit index for a value in seconds.
    * 0=seconds, 1=minutes, 2=hours, 3=days (if includeDays).
    *
    * @param seconds total seconds (null treated as 0)
    * @param includeDays if true, also consider days as a unit
    * @return the unit index
    */
   private static int toTimeUnit(Integer seconds, boolean includeDays)
   {
      if (seconds == null || seconds == 0)
         return 0;
      int unit = 0;
      if ((seconds % 60) == 0)
      {
         unit++;
         if ((seconds % 3600) == 0)
         {
            unit++;
            if (includeDays && (seconds % 86400) == 0)
            {
               unit++;
            }
         }
      }
      return unit;
   }

   public int getTimeRagne()
   {
      return toTimeRange(repeatInterval, false);
   }

   public int getTimeUnit()
   {
      return toTimeUnit(repeatInterval, false);
   }

   /**
    * @return the absence detection flag
    */
   public boolean getAbsence()
   {
      return LogParser.stringToBoolean(absence);
   }

   /**
    * @param absence the absence detection flag to set
    */
   public void setAbsence(boolean absence)
   {
      this.absence = LogParser.booleanToString(absence);
   }

   /**
    * @return the absence interval in seconds
    */
   public Integer getAbsenceInterval()
   {
      return absenceInterval == null ? 0 : absenceInterval;
   }

   /**
    * @param absenceInterval the absence interval in seconds to set
    */
   public void setAbsenceInterval(Integer absenceInterval)
   {
      this.absenceInterval = absenceInterval;
   }

   /**
    * @return the absence re-alert interval in seconds
    */
   public Integer getAbsenceRealertInterval()
   {
      return absenceRealertInterval == null ? 0 : absenceRealertInterval;
   }

   /**
    * @param absenceRealertInterval the absence re-alert interval in seconds to set
    */
   public void setAbsenceRealertInterval(Integer absenceRealertInterval)
   {
      this.absenceRealertInterval = absenceRealertInterval;
   }

   /**
    * @return the absence interval time range value (converted from seconds to appropriate unit)
    */
   public int getAbsenceTimeRange()
   {
      return toTimeRange(absenceInterval, true);
   }

   /**
    * @return the absence interval time unit (0=seconds, 1=minutes, 2=hours, 3=days)
    */
   public int getAbsenceTimeUnit()
   {
      return toTimeUnit(absenceInterval, true);
   }

   /**
    * @return the absence re-alert time range value (converted from seconds to appropriate unit)
    */
   public int getAbsenceRealertTimeRange()
   {
      return toTimeRange(absenceRealertInterval, true);
   }

   /**
    * @return the absence re-alert time unit (0=seconds, 1=minutes, 2=hours, 3=days)
    */
   public int getAbsenceRealertTimeUnit()
   {
      return toTimeUnit(absenceRealertInterval, true);
   }
}
