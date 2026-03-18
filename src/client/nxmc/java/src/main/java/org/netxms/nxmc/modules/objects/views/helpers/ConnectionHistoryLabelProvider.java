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
package org.netxms.nxmc.modules.objects.views.helpers;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.netxms.client.NXCSession;
import org.netxms.client.topology.ConnectionHistoryRecord;
import org.netxms.nxmc.Registry;
import org.netxms.nxmc.localization.DateFormatFactory;
import org.netxms.nxmc.modules.objects.views.ConnectionHistoryView;

/**
 * Label provider for connection history view
 */
public class ConnectionHistoryLabelProvider extends LabelProvider implements ITableLabelProvider
{
   private NXCSession session = Registry.getSession();

   /**
    * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
    */
   @Override
   public Image getColumnImage(Object element, int columnIndex)
   {
      return null;
   }

   /**
    * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
    */
   @Override
   public String getColumnText(Object element, int columnIndex)
   {
      ConnectionHistoryRecord r = (ConnectionHistoryRecord)element;
      switch(columnIndex)
      {
         case ConnectionHistoryView.COLUMN_TIMESTAMP:
            return DateFormatFactory.getDateTimeFormat().format(r.getTimestamp());
         case ConnectionHistoryView.COLUMN_EVENT_TYPE:
            return r.getEventTypeText();
         case ConnectionHistoryView.COLUMN_MAC_ADDRESS:
            return r.getMacAddress().toString();
         case ConnectionHistoryView.COLUMN_IP_ADDRESS:
            return (r.getIpAddress() != null) ? r.getIpAddress() : "";
         case ConnectionHistoryView.COLUMN_NODE:
            return (r.getNodeId() != 0) ? session.getObjectNameWithAlias(r.getNodeId()) : "";
         case ConnectionHistoryView.COLUMN_SWITCH:
            return (r.getSwitchId() != 0) ? session.getObjectNameWithAlias(r.getSwitchId()) : "";
         case ConnectionHistoryView.COLUMN_PORT:
            return (r.getInterfaceId() != 0) ? session.getObjectNameWithAlias(r.getInterfaceId()) : "";
         case ConnectionHistoryView.COLUMN_VLAN:
            return (r.getVlanId() != 0) ? Integer.toString(r.getVlanId()) : "";
      }
      return null;
   }
}
