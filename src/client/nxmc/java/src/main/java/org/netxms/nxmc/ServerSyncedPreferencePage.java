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

/**
 * Interface for preference pages whose settings are persisted as individual
 * server-side user attributes — one attribute per preference key, named
 * {@code "." + key} (e.g. preference key {@code "nxmc.language"} is stored
 * in user attribute {@code ".nxmc.language"}).
 * <p>
 * Each implementing page is responsible for reading and writing exactly the
 * set of PreferenceStore keys it manages.  Use the static helpers in
 * {@link PreferenceServerSync} to reduce boilerplate.
 */
public interface ServerSyncedPreferencePage
{
   /**
    * Load this page's preferences from the server into the given store.
    * Only the keys managed by this page should be touched.
    *
    * @param session active NXCSession
    * @param store   the application preference store
    */
   void loadFromServer(NXCSession session, PreferenceStore store);

   /**
    * Save this page's preferences from the given store to the server.
    * Only the keys managed by this page should be written.
    *
    * @param session active NXCSession
    * @param store   the application preference store
    */
   void saveToServer(NXCSession session, PreferenceStore store);
}
