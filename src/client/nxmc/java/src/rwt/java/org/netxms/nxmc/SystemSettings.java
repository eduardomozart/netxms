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
package org.netxms.nxmc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global system settings shared across all user sessions.
 * Unlike the per-browser PreferenceStore, settings stored here apply to every
 * user that connects to this web console instance. The backing file is
 * {@code nxmc.system.properties} inside the application state directory.
 */
public final class SystemSettings
{
   private static final Logger logger = LoggerFactory.getLogger(SystemSettings.class);
   private static final String FILE_NAME = "nxmc.system.properties";

   private static SystemSettings instance = null;

   private final File settingsFile;
   private final Properties properties = new Properties();

   /**
    * Initialize global system settings from the given state directory.
    * Safe to call from multiple sessions concurrently; only the first call
    * takes effect.
    *
    * @param stateDir application state directory
    */
   public static synchronized void open(File stateDir)
   {
      if (instance != null)
         return;

      instance = new SystemSettings(new File(stateDir, FILE_NAME));
   }

   /**
    * Get a setting value.
    *
    * @param key setting key
    * @param defaultValue value returned when the key is absent
    * @return current value of the setting, or {@code defaultValue}
    */
   public static String get(String key, String defaultValue)
   {
      if (instance == null)
         return defaultValue;
      return instance.properties.getProperty(key, defaultValue);
   }

   /**
    * Set a setting value and persist immediately to disk.
    *
    * @param key setting key
    * @param value new value
    */
   public static synchronized void set(String key, String value)
   {
      if (instance == null)
         return;
      instance.properties.setProperty(key, value);
      instance.save();
   }

   private SystemSettings(File settingsFile)
   {
      this.settingsFile = settingsFile;
      if (settingsFile.exists())
      {
         try (FileReader reader = new FileReader(settingsFile))
         {
            properties.load(reader);
            logger.debug("System settings loaded from {}", settingsFile.getAbsolutePath());
         }
         catch(IOException e)
         {
            logger.error("Error reading system settings from " + settingsFile.getAbsolutePath(), e);
         }
      }
   }

   private void save()
   {
      try (FileWriter writer = new FileWriter(settingsFile))
      {
         properties.store(writer, "NXMC system settings");
      }
      catch(IOException e)
      {
         logger.error("Error writing system settings to " + settingsFile.getAbsolutePath(), e);
      }
   }
}
