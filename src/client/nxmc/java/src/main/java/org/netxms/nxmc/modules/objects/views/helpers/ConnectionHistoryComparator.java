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

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.netxms.client.NXCSession;
import org.netxms.client.topology.ConnectionHistoryRecord;
import org.netxms.nxmc.Registry;
import org.netxms.nxmc.base.widgets.SortableTableViewer;
import org.netxms.nxmc.modules.objects.views.ConnectionHistoryView;

/**
 * Comparator for connection history records
 */
public class ConnectionHistoryComparator extends ViewerComparator
{
   private NXCSession session = Registry.getSession();

   /**
    * @see org.eclipse.jface.viewers.ViewerComparator#compare(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
    */
   @Override
   public int compare(Viewer viewer, Object e1, Object e2)
   {
      ConnectionHistoryRecord r1 = (ConnectionHistoryRecord)e1;
      ConnectionHistoryRecord r2 = (ConnectionHistoryRecord)e2;

      int result;
      switch((Integer)((SortableTableViewer)viewer).getTable().getSortColumn().getData("ID")) //$NON-NLS-1$
      {
         case ConnectionHistoryView.COLUMN_TIMESTAMP:
            result = r1.getTimestamp().compareTo(r2.getTimestamp());
            break;
         case ConnectionHistoryView.COLUMN_EVENT_TYPE:
            result = r1.getEventType() - r2.getEventType();
            break;
         case ConnectionHistoryView.COLUMN_MAC_ADDRESS:
            result = r1.getMacAddress().compareTo(r2.getMacAddress());
            break;
         case ConnectionHistoryView.COLUMN_IP_ADDRESS:
            String ip1 = (r1.getIpAddress() != null) ? r1.getIpAddress() : "";
            String ip2 = (r2.getIpAddress() != null) ? r2.getIpAddress() : "";
            result = ip1.compareToIgnoreCase(ip2);
            break;
         case ConnectionHistoryView.COLUMN_NODE:
            String n1 = (r1.getNodeId() != 0) ? session.getObjectName(r1.getNodeId()) : "";
            String n2 = (r2.getNodeId() != 0) ? session.getObjectName(r2.getNodeId()) : "";
            result = n1.compareToIgnoreCase(n2);
            break;
         case ConnectionHistoryView.COLUMN_SWITCH:
            String s1 = (r1.getSwitchId() != 0) ? session.getObjectName(r1.getSwitchId()) : "";
            String s2 = (r2.getSwitchId() != 0) ? session.getObjectName(r2.getSwitchId()) : "";
            result = s1.compareToIgnoreCase(s2);
            break;
         case ConnectionHistoryView.COLUMN_PORT:
            String p1 = (r1.getInterfaceId() != 0) ? session.getObjectName(r1.getInterfaceId()) : "";
            String p2 = (r2.getInterfaceId() != 0) ? session.getObjectName(r2.getInterfaceId()) : "";
            result = p1.compareToIgnoreCase(p2);
            break;
         case ConnectionHistoryView.COLUMN_VLAN:
            result = r1.getVlanId() - r2.getVlanId();
            break;
         default:
            result = 0;
            break;
      }
      return (((SortableTableViewer)viewer).getTable().getSortDirection() == SWT.UP) ? result : -result;
   }
}
