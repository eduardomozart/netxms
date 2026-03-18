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
package org.netxms.client.topology;

import java.util.Date;
import org.netxms.base.MacAddress;
import org.netxms.base.NXCPMessage;

/**
 * Connection history record
 */
public class ConnectionHistoryRecord
{
   public static final int CONNECT = 0;
   public static final int DISCONNECT = 1;
   public static final int MOVE = 2;

   private long recordId;
   private Date timestamp;
   private MacAddress macAddress;
   private String ipAddress;
   private long nodeId;
   private long switchId;
   private long interfaceId;
   private int vlanId;
   private int eventType;
   private long oldSwitchId;
   private long oldInterfaceId;

   /**
    * Create from NXCP message.
    *
    * @param msg NXCP message
    * @param baseId base field ID
    */
   public ConnectionHistoryRecord(NXCPMessage msg, long baseId)
   {
      recordId = msg.getFieldAsInt64(baseId);
      timestamp = msg.getFieldAsDate(baseId + 1);
      macAddress = msg.getFieldAsMacAddress(baseId + 2);
      ipAddress = msg.getFieldAsString(baseId + 3);
      nodeId = msg.getFieldAsInt64(baseId + 4);
      switchId = msg.getFieldAsInt64(baseId + 5);
      interfaceId = msg.getFieldAsInt64(baseId + 6);
      vlanId = msg.getFieldAsInt32(baseId + 7);
      eventType = msg.getFieldAsInt32(baseId + 8);
      oldSwitchId = msg.getFieldAsInt64(baseId + 9);
      oldInterfaceId = msg.getFieldAsInt64(baseId + 10);
   }

   /**
    * @return the recordId
    */
   public long getRecordId()
   {
      return recordId;
   }

   /**
    * @return the timestamp
    */
   public Date getTimestamp()
   {
      return timestamp;
   }

   /**
    * @return the macAddress
    */
   public MacAddress getMacAddress()
   {
      return macAddress;
   }

   /**
    * @return the ipAddress
    */
   public String getIpAddress()
   {
      return ipAddress;
   }

   /**
    * @return the nodeId
    */
   public long getNodeId()
   {
      return nodeId;
   }

   /**
    * @return the switchId
    */
   public long getSwitchId()
   {
      return switchId;
   }

   /**
    * @return the interfaceId
    */
   public long getInterfaceId()
   {
      return interfaceId;
   }

   /**
    * @return the vlanId
    */
   public int getVlanId()
   {
      return vlanId;
   }

   /**
    * @return the eventType
    */
   public int getEventType()
   {
      return eventType;
   }

   /**
    * @return the oldSwitchId
    */
   public long getOldSwitchId()
   {
      return oldSwitchId;
   }

   /**
    * @return the oldInterfaceId
    */
   public long getOldInterfaceId()
   {
      return oldInterfaceId;
   }

   /**
    * Get event type as text
    *
    * @return event type text
    */
   public String getEventTypeText()
   {
      switch(eventType)
      {
         case CONNECT:
            return "CONNECT";
         case DISCONNECT:
            return "DISCONNECT";
         case MOVE:
            return "MOVE";
         default:
            return "UNKNOWN";
      }
   }
}
