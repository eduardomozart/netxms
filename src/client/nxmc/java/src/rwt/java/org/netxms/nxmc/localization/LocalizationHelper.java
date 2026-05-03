/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2024 Raden Solutions
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
package org.netxms.nxmc.localization;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.rap.rwt.RWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * Helper class for localization tasks
 */
public final class LocalizationHelper
{
   private static final Logger logger = LoggerFactory.getLogger(LocalizationHelper.class);

   /**
    * Tracks locales that have already been checked for a translation bundle, to avoid
    * repeating the same diagnostic log message on every {@link #getI18n} call.
    */
   private static final Set<String> checkedLocales = Collections.newSetFromMap(new ConcurrentHashMap<>());

   /**
    * Prevent construction
    */
   private LocalizationHelper()
   {
   }

   /**
    * Get I18n object for given class
    *
    * @param c Java class
    * @return I18n object for translation
    */
   public static I18n getI18n(Class<?> c)
   {
      Locale locale = RWT.getLocale();
      if (locale == null)
         locale = Locale.getDefault();

      // Log once per unique locale string to help diagnose missing translation bundles.
      if (checkedLocales.add(locale.toString()))
      {
         String bundleResource = "i18n/Messages_" + locale + ".class";
         if (c.getClassLoader().getResource(bundleResource) != null)
         {
            logger.debug("Translation bundle found in classpath: {} (language={}, country={}, tag={})",
                  bundleResource, locale.getLanguage(), locale.getCountry(), locale.toLanguageTag());
         }
         else
         {
            logger.warn("Translation bundle NOT found in classpath: {} (language={}, country={}, tag={})",
                  bundleResource, locale.getLanguage(), locale.getCountry(), locale.toLanguageTag());
         }
      }

      return I18nFactory.getI18n(c, locale, I18nFactory.FALLBACK);
   }

   /**
    * Convert a language code (e.g. {@code "pt_BR"} or {@code "pt-BR"}) to a
    * {@link Locale}.  Language codes stored in preferences use underscores as
    * separator, but {@link Locale#forLanguageTag(String)} requires the BCP 47
    * hyphen separator, so we normalise before parsing.
    *
    * @param languageCode language code in any supported format
    * @return the corresponding locale
    */
   public static Locale localeFromLanguageCode(String languageCode)
   {
      Locale locale = Locale.forLanguageTag(languageCode);
      logger.debug("localeFromLanguageCode(\"{}\") -> language={}, country={}, toString={}, toLanguageTag={}",
            languageCode, locale.getLanguage(), locale.getCountry(), locale, locale.toLanguageTag());
      return locale;
   }

   /**
    * Get user's locale.
    *
    * @return user's locale
    */
   public static Locale getLocale()
   {
      return RWT.getLocale();
   }
}
