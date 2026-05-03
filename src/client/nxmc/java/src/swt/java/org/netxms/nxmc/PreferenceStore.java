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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.eclipse.swt.widgets.Display;
import org.netxms.client.NXCSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local preference store - SWT (desktop) variant. A single process-wide instance
 * is held in a static field. All shared logic lives in {@link AbstractPreferenceStore}.
 *
 * In addition to server-side persistence, this variant maintains a local file at
 * {@code <stateDir>/nxmc.preferences} so that machine-level settings (server address,
 * login history, etc.) are available on the login dialog before a server connection
 * has been established.
 */
public class PreferenceStore extends AbstractPreferenceStore
{
   private static final Logger logger = LoggerFactory.getLogger(PreferenceStore.class);

   private static PreferenceStore instance = null;
   private static File localFile = null;

   /**
    * Open preference store, loading any previously-persisted local settings from
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
            try
            {
               String content = new String(Files.readAllBytes(localFile.toPath()), StandardCharsets.UTF_8).trim();
               instance.deserialize(content);
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
    * After merging the server data the local file is updated so that the merged state is
    * available the next time the application starts (before login).
    *
    * @param session active NXCSession
    */
   public static void loadFromServer(NXCSession session)
   {
      loadFromServer(instance, session);
      saveLocalFile();
   }

   /**
    * Persist the current in-memory preferences to the local file.
    */
   private static void saveLocalFile()
   {
      if (localFile == null || instance == null)
         return;
      String encoded = instance.serialize();
      if (encoded == null)
         return;
      try
      {
         Files.write(localFile.toPath(), encoded.getBytes(StandardCharsets.UTF_8));
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
    * Saves to the local file in addition to scheduling the server-side save so that
    * login-dialog settings (server address, login name, etc.) are available before the
    * next login.
    */
   @Override
   protected void onPropertyChange(String property, String oldValue, String newValue)
   {
      super.onPropertyChange(property, oldValue, newValue);
      saveLocalFile();
   }
}
