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

import org.eclipse.swt.widgets.Display;
import org.netxms.client.NXCSession;

/**
 * Local preference store - SWT (desktop) variant. A single process-wide instance
 * is held in a static field. All shared logic lives in {@link AbstractPreferenceStore}.
 */
public class PreferenceStore extends AbstractPreferenceStore
{
   private static PreferenceStore instance = null;

   /**
    * Open preference store.
    */
   protected static void open()
   {
      instance = new PreferenceStore();
      initializeDefaults(instance);
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
}
