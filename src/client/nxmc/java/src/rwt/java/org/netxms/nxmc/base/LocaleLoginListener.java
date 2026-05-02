/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2025 Raden Solutions
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
package org.netxms.nxmc.base;

import java.util.Locale;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.swt.widgets.Display;
import org.netxms.client.NXCSession;
import org.netxms.nxmc.PreferenceStore;
import org.netxms.nxmc.services.LoginListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Login listener that restores the user's language preference from the server.
 * This ensures the language setting survives container restarts and is stored
 * per-user rather than per-browser-cookie.
 */
public class LocaleLoginListener implements LoginListener
{
   private static final Logger logger = LoggerFactory.getLogger(LocaleLoginListener.class);
   private static final String LANGUAGE_ATTRIBUTE = ".nxmc.language";

   /**
    * @see org.netxms.nxmc.services.LoginListener#afterLogin(org.netxms.client.NXCSession, org.eclipse.swt.widgets.Display)
    */
   @Override
   public void afterLogin(NXCSession session, Display display)
   {
      try
      {
         String serverLanguage = session.getAttributeForCurrentUser(LANGUAGE_ATTRIBUTE);
         if ((serverLanguage != null) && !serverLanguage.isEmpty())
         {
            logger.debug("Restoring language preference from server: {}", serverLanguage);
            final String lang = serverLanguage;
            // Use syncExec to ensure locale is applied before doLogin() returns
            // and MainWindow is constructed. This is safe because afterLogin() runs in
            // the ModalContext background thread while the UI thread runs the
            // ProgressMonitorDialog event loop (not blocked), so no deadlock occurs.
            display.syncExec(() -> {
               PreferenceStore.getInstance(display).set("nxmc.language", lang);
               RWT.setLocale(Locale.forLanguageTag(lang.replace('_', '-')));
            });
         }
      }
      catch(Exception e)
      {
         logger.warn("Failed to retrieve language preference from server", e);
      }
   }
}
