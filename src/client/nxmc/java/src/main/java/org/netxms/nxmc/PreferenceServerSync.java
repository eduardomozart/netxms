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

import org.netxms.client.NXCSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static helpers for {@link ServerSyncedPreferencePage} implementations.
 * <p>
 * Every preference key {@code key} is stored as the server user attribute
 * {@code "." + key} (plain string, no Base64 encoding).  For counted lists
 * (e.g. menu entry arrays) use {@link #loadCountedGroup}/{@link #saveCountedGroup}.
 */
public final class PreferenceServerSync
{
   private static final Logger logger = LoggerFactory.getLogger(PreferenceServerSync.class);

   private PreferenceServerSync()
   {
   }

   /**
    * Load a single preference key from the server.
    * The server attribute name is {@code "." + key}.
    * The store is updated only when the attribute exists on the server.
    *
    * @param session active NXCSession
    * @param store   preference store to update
    * @param key     preference key (also determines the attribute name)
    */
   public static void loadKey(NXCSession session, PreferenceStore store, String key)
   {
      try
      {
         String value = session.getAttributeForCurrentUser("." + key);
         if (value != null)
            store.set(key, value);
      }
      catch(Exception e)
      {
         logger.error("Failed to load preference ." + key + " from server", e);
      }
   }

   /**
    * Save a single preference key to the server.
    * The server attribute name is {@code "." + key}.
    * Nothing is written when the key is absent from the store.
    *
    * @param session active NXCSession
    * @param store   preference store to read from
    * @param key     preference key (also determines the attribute name)
    */
   public static void saveKey(NXCSession session, PreferenceStore store, String key)
   {
      try
      {
         String value = store.getAsString(key);
         if (value != null)
            session.setAttributeForCurrentUser("." + key, value);
      }
      catch(Exception e)
      {
         logger.error("Failed to save preference ." + key + " to server", e);
      }
   }

   /**
    * Load a counted list of preference entries from the server.
    * Reads {@code sizeKey} first to determine the entry count, then loads
    * each entry {@code entryKeyPrefix + i} for {@code i} in [0, count).
    *
    * @param session        active NXCSession
    * @param store          preference store to update
    * @param sizeKey        preference key that holds the entry count
    * @param entryKeyPrefix prefix for individual entry keys (index appended)
    */
   public static void loadCountedGroup(NXCSession session, PreferenceStore store, String sizeKey, String entryKeyPrefix)
   {
      loadKey(session, store, sizeKey);
      int count = store.getAsInteger(sizeKey, 0);
      for(int i = 0; i < count; i++)
         loadKey(session, store, entryKeyPrefix + i);
   }

   /**
    * Save a counted list of preference entries to the server.
    * Writes {@code sizeKey} and then each entry {@code entryKeyPrefix + i}
    * for {@code i} in [0, count) where count is the current value of {@code sizeKey}.
    *
    * @param session        active NXCSession
    * @param store          preference store to read from
    * @param sizeKey        preference key that holds the entry count
    * @param entryKeyPrefix prefix for individual entry keys (index appended)
    */
   public static void saveCountedGroup(NXCSession session, PreferenceStore store, String sizeKey, String entryKeyPrefix)
   {
      saveKey(session, store, sizeKey);
      int count = store.getAsInteger(sizeKey, 0);
      for(int i = 0; i < count; i++)
         saveKey(session, store, entryKeyPrefix + i);
   }
}
