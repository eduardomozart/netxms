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
package org.netxms.nxmc.modules.snmp.widgets.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.netxms.client.snmp.MibObject;
import org.netxms.client.snmp.MibTree;
import org.netxms.client.snmp.SnmpObjectId;

/**
 * Navigator for finding MIB objects by text in the MIB tree.
 */
public class MibTreeNavigator
{
   private MibTree mibTree;

   /**
    * Create new MIB tree navigator.
    *
    * @param mibTree MIB tree to search in
    */
   public MibTreeNavigator(MibTree mibTree)
   {
      this.mibTree = mibTree;
   }

   /**
    * Set MIB tree to search in.
    *
    * @param mibTree MIB tree
    */
   public void setMibTree(MibTree mibTree)
   {
      this.mibTree = mibTree;
   }

   /**
    * Find MIB object matching given text.
    *
    * @param text text to search for
    * @param forward true to search forward, false to search backward
    * @param currentSelection currently selected MIB object (can be null)
    * @return search result
    */
   public SearchResult find(String text, boolean forward, MibObject currentSelection)
   {
      if (mibTree == null || text == null || text.isEmpty())
         return new SearchResult(null, false);

      List<MibObject> flatList = new ArrayList<>();
      buildFlatList(mibTree.getRootObject(), flatList);
      if (flatList.isEmpty())
         return new SearchResult(null, false);

      String searchText = text.toUpperCase();

      int startIndex;
      boolean wrapped = false;
      if (currentSelection != null)
      {
         int selectedIndex = flatList.indexOf(currentSelection);
         if (selectedIndex >= 0)
         {
            if (forward)
            {
               startIndex = selectedIndex + 1;
               if (startIndex >= flatList.size())
               {
                  startIndex = 0;
                  wrapped = true;
               }
            }
            else
            {
               startIndex = selectedIndex - 1;
               if (startIndex < 0)
               {
                  startIndex = flatList.size() - 1;
                  wrapped = true;
               }
            }
         }
         else
         {
            startIndex = forward ? 0 : flatList.size() - 1;
         }
      }
      else
      {
         startIndex = forward ? 0 : flatList.size() - 1;
      }

      int i = startIndex;
      do
      {
         MibObject object = flatList.get(i);
         if (matches(object, searchText))
            return new SearchResult(object, wrapped);

         if (forward)
         {
            i = (i + 1) % flatList.size();
            if (i == 0)
               wrapped = true;
         }
         else
         {
            i = (i - 1 + flatList.size()) % flatList.size();
            if (i == flatList.size() - 1)
               wrapped = true;
         }
      }
      while (i != startIndex);

      return new SearchResult(null, false);
   }

   /**
    * Build flat list of all MIB objects in depth-first order.
    *
    * @param object current object
    * @param list list to add objects to
    */
   private void buildFlatList(MibObject object, List<MibObject> list)
   {
      MibObject[] children = object.getChildObjects();
      Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      for(MibObject child : children)
      {
         list.add(child);
         buildFlatList(child, list);
      }
   }

   /**
    * Check if MIB object matches search text.
    *
    * @param object MIB object to check
    * @param searchText search text in upper case
    * @return true if object matches
    */
   private boolean matches(MibObject object, String searchText)
   {
      if (object.getName().toUpperCase().contains(searchText))
         return true;

      if (object.getDescription().toUpperCase().contains(searchText))
         return true;

      SnmpObjectId oid = object.getObjectId();
      if (oid != null && oid.toString().contains(searchText))
         return true;

      return false;
   }

   /**
    * Search result
    */
   public static class SearchResult
   {
      private MibObject object;
      private boolean wrapped;

      /**
       * Create search result.
       *
       * @param object matched object or null if not found
       * @param wrapped true if search wrapped around
       */
      public SearchResult(MibObject object, boolean wrapped)
      {
         this.object = object;
         this.wrapped = wrapped;
      }

      /**
       * @return matched MIB object or null
       */
      public MibObject getObject()
      {
         return object;
      }

      /**
       * @return true if search wrapped around
       */
      public boolean isWrapped()
      {
         return wrapped;
      }
   }
}
