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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Base64;
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
 * {@code <stateDir>/nxmc.preferences} in plain Java {@link Properties} format. The
 * local file stores only the {@code Connect.*} keys (server address, login history,
 * per-server credentials, etc.) which are never synced to the server. All other
 * preferences are synced to the server via the {@code .nxmc.preferences} user
 * attribute and are never written to the local file.
 *
 * The local file is written once after a successful login via {@link #saveLocalFile()},
 * not on every individual property change.
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
    * Called once after successful login. The {@code Connect.*} keys already loaded from
    * the local file are preserved across the server-side deserialisation.
    *
    * @param session active NXCSession
    */
   public static void loadFromServer(NXCSession session)
   {
      Properties connectKeys = new Properties();
      for (String key : instance.properties.stringPropertyNames())
      {
         if (key.startsWith("Connect."))
            connectKeys.setProperty(key, instance.properties.getProperty(key));
      }
      loadFromServer(instance, session);
      instance.properties.putAll(connectKeys);
   }

   /**
    * Persist the current {@code Connect.*} preferences to the local file in plain
    * Java properties format. Only these keys are written; all other preferences are
    * managed exclusively via server-side user attributes.
    *
    * Called once after a successful login rather than on every property change.
    */
   public static void saveLocalFile()
   {
      if (localFile == null || instance == null)
         return;
      Properties connectProps = new Properties();
      for (String key : instance.properties.stringPropertyNames())
      {
         if (key.startsWith("Connect."))
            connectProps.setProperty(key, instance.properties.getProperty(key));
      }
      try (OutputStream out = Files.newOutputStream(localFile.toPath()))
      {
         connectProps.store(out, null);
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
    * Excludes {@code Connect.*} keys from the Base64 blob sent to the server so
    * that connection-specific settings (server address, login history, etc.) are
    * never stored in server-side user attributes.
    */
   @Override
   protected String serialize()
   {
      Properties serverProps = new Properties();
      for (String key : properties.stringPropertyNames())
      {
         if (!key.startsWith("Connect."))
            serverProps.setProperty(key, properties.getProperty(key));
      }
      try (ByteArrayOutputStream out = new ByteArrayOutputStream(8192))
      {
         serverProps.store(out, "");
         return Base64.getEncoder().encodeToString(out.toByteArray());
      }
      catch(IOException e)
      {
         return null;
      }
   }
}
