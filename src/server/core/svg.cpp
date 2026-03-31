/*
** NetXMS - Network Management System
** Copyright (C) 2003-2026 Raden Solutions
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 2 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
**
** File: svg.cpp
**
**/

#include "nxcore.h"
#include <netxms-xml.h>

#define DEBUG_TAG L"image.library"

/**
 * Allowlist of safe SVG element names
 */
static bool IsSafeSVGElement(const char *name)
{
   static const char *s_safeElements[] =
   {
      // Root and structural
      "svg", "g", "defs", "symbol", "use",
      // Shapes
      "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
      // Text
      "text", "tspan", "textPath",
      // Paint servers
      "linearGradient", "radialGradient", "stop", "pattern",
      // Clipping and masking
      "clipPath", "mask",
      // Filters
      "filter", "feBlend", "feColorMatrix", "feComponentTransfer", "feComposite",
      "feConvolveMatrix", "feDiffuseLighting", "feDisplacementMap", "feDropShadow",
      "feFlood", "feGaussianBlur", "feImage", "feMerge", "feMergeNode",
      "feMorphology", "feOffset", "fePointLight", "feSpecularLighting",
      "feSpotLight", "feTile", "feTurbulence",
      "feFuncR", "feFuncG", "feFuncB", "feFuncA",
      // Markers
      "marker",
      // Style (content validated separately)
      "style",
      // Descriptive
      "title", "desc", "metadata",
      // Image (href validated separately)
      "image",
      nullptr
   };

   for (int i = 0; s_safeElements[i] != nullptr; i++)
   {
      if (!strcmp(name, s_safeElements[i]))
         return true;
   }
   return false;
}

/**
 * Check if href value is safe (internal reference or embedded data URI)
 */
static bool IsSafeHref(const char *value)
{
   if (value[0] == '#')
      return true;
   if (!strncmp(value, "data:image/", 11))
      return true;
   return false;
}

/**
 * Check if attribute name is an event handler (on*)
 */
static bool IsEventHandler(const char *name)
{
   return (name[0] == 'o') && (name[1] == 'n') && (name[2] >= 'a') && (name[2] <= 'z');
}

/**
 * Check if style content contains dangerous directives
 */
static bool HasDangerousStyleContent(const char *content)
{
   if (strstr(content, "@import") != nullptr)
      return true;
   if (strstr(content, "javascript:") != nullptr)
      return true;
   if (strstr(content, "expression(") != nullptr)
      return true;
   return false;
}

/**
 * Sanitize attributes on a single SVG element
 */
static void SanitizeAttributes(pugi::xml_node node)
{
   pugi::xml_attribute attr = node.first_attribute();
   while (attr)
   {
      pugi::xml_attribute next = attr.next_attribute();

      if (IsEventHandler(attr.name()))
      {
         nxlog_debug_tag(DEBUG_TAG, 5, L"SVG sanitizer: removing event handler \"%hs\" from <%hs>", attr.name(), node.name());
         node.remove_attribute(attr);
      }
      else if ((!strcmp(attr.name(), "href") || !strcmp(attr.name(), "xlink:href")) && !IsSafeHref(attr.value()))
      {
         nxlog_debug_tag(DEBUG_TAG, 5, L"SVG sanitizer: removing external reference \"%hs\" from <%hs>", attr.name(), node.name());
         node.remove_attribute(attr);
      }

      attr = next;
   }
}

/**
 * Recursively sanitize SVG node tree
 */
static void SanitizeNode(pugi::xml_node parent)
{
   pugi::xml_node child = parent.first_child();
   while (child)
   {
      pugi::xml_node next = child.next_sibling();

      if (child.type() == pugi::node_element)
      {
         if (!IsSafeSVGElement(child.name()))
         {
            nxlog_debug_tag(DEBUG_TAG, 4, L"SVG sanitizer: removing element <%hs>", child.name());
            parent.remove_child(child);
         }
         else
         {
            SanitizeAttributes(child);

            // Check <style> element content for dangerous directives
            if (!strcmp(child.name(), "style"))
            {
               const char *content = child.child_value();
               if ((content != nullptr) && HasDangerousStyleContent(content))
               {
                  nxlog_debug_tag(DEBUG_TAG, 4, L"SVG sanitizer: removing <style> with dangerous content");
                  parent.remove_child(child);
                  child = next;
                  continue;
               }
            }

            SanitizeNode(child);
         }
      }
      else if (child.type() == pugi::node_pi)
      {
         // Remove processing instructions
         nxlog_debug_tag(DEBUG_TAG, 5, L"SVG sanitizer: removing processing instruction");
         parent.remove_child(child);
      }

      child = next;
   }
}

/**
 * Sanitize SVG file by removing dangerous elements and attributes.
 * File is read, parsed, sanitized, and written back in place.
 *
 * @param fileName full path to the SVG file
 * @return true on success, false on parse or I/O error
 */
bool SanitizeSVGFile(const TCHAR *fileName)
{
   char *content = LoadFileAsUTF8String(fileName);
   if (content == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, L"SanitizeSVGFile(%s): cannot read file", fileName);
      return false;
   }

   pugi::xml_document doc;
   pugi::xml_parse_result result = doc.load_buffer(content, strlen(content),
      pugi::parse_default & ~pugi::parse_pi);
   MemFree(content);

   if (!result)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, L"SanitizeSVGFile(%s): XML parse error: %hs", fileName, result.description());
      return false;
   }

   // Verify root element is <svg>
   pugi::xml_node root = doc.first_child();
   if (!root || strcmp(root.name(), "svg") != 0)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, L"SanitizeSVGFile(%s): root element is not <svg>", fileName);
      return false;
   }

   SanitizeAttributes(root);
   SanitizeNode(root);

   if (!doc.save_file(fileName, "  ", pugi::format_default, pugi::encoding_utf8))
   {
      nxlog_debug_tag(DEBUG_TAG, 4, L"SanitizeSVGFile(%s): cannot write sanitized file", fileName);
      return false;
   }

   nxlog_debug_tag(DEBUG_TAG, 4, L"SanitizeSVGFile(%s): file sanitized successfully", fileName);
   return true;
}
