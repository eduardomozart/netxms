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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;
import org.eclipse.swt.widgets.Display;
import org.netxms.client.NXCSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local preference store - SWT (desktop) variant. A single process-wide instance
 * is held in a static field. All shared logic lives in {@link AbstractPreferenceStore}.
 *
 * In addition to server-side persistence, this variant maintains a local file at
 * {@code <stateDir>/nxmc.preferences} in plain Java {@link Properties} format so that
 * all preferences (including server address, login history, UI state, etc.) are
 * available before a server connection has been established.
 */
public class PreferenceStore extends AbstractPreferenceStore
{
   private static final Logger logger = LoggerFactory.getLogger(PreferenceStore.class);

   private static PreferenceStore instance = null;
   private static File localFile = null;

   /**
    * Open preference store, loading any previously-persisted connection settings from
    * {@code stateDir/nxmc.preferences}.
    *
    * @param stateDir application state directory (e.g. {@code ~/.nxmc4})
    */
   protected static void open(File stateDir)
   {
      instance = new PreferenceStore();
      initializeDefaults(instance);
      if (stateDir != null)
      {
         localFile = new File(stateDir, "nxmc.preferences");
         if (localFile.isFile())
         {
            Properties loaded = new Properties();
            try (InputStream in = Files.newInputStream(localFile.toPath()))
            {
               loaded.load(in);
               instance.properties.putAll(loaded);
               logger.debug("Local preferences loaded from {}", localFile.getAbsolutePath());
            }
            catch(IOException e)
            {
               logger.error("Failed to load local preferences from {}", localFile.getAbsolutePath(), e);
            }
         }
      }
   }

   /**
    * Open preference store without local file support.
    */
   protected static void open()
   {
      open(null);
   }

   /**
    * Get instance of preference store.
    *
    * @return instance of preference store
    */
   public static PreferenceStore getInstance()
   {
      return instance;
   }

   /**
    * Get instance of preference store (RAP compatibility version).
    *
    * @param display owning display (ignored)
    * @return instance of preference store
    */
   public static PreferenceStore getInstance(Display display)
   {
      return instance;
   }

   /**
    * Load preferences from server user attributes and attach session for future saves.
    * Called once after successful login.
    *
    * @param session active NXCSession
    */
   public static void loadFromServer(NXCSession session)
   {
      loadFromServer(instance, session);
   }

   /**
    * Persist all current preferences to the local file in plain Java properties
    * format so that they are available before the next server login.
    */
   private static void saveLocalFile()
   {
      if (localFile == null || instance == null)
         return;
      try (OutputStream out = Files.newOutputStream(localFile.toPath()))
      {
         instance.properties.store(out, "NetXMS Management Console - local preferences");
         logger.debug("Local preferences saved to {}", localFile.getAbsolutePath());
      }
      catch(IOException e)
      {
         logger.warn("Failed to save local preferences to {}", localFile.getAbsolutePath(), e);
      }
   }

   /**
    * {@inheritDoc}
    *
    * Saves all preferences to the local file on every property change so that
    * the current state is available before the next server login.
    */
   @Override
   protected void onPropertyChange(String property, String oldValue, String newValue)
   {
      super.onPropertyChange(property, oldValue, newValue);
      saveLocalFile();
   }
}
