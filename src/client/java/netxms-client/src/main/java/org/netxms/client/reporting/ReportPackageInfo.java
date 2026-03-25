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
package org.netxms.client.reporting;

import java.util.Date;
import java.util.UUID;
import org.netxms.base.NXCPMessage;

/**
 * Information about a report definition package on the reporting server.
 */
public class ReportPackageInfo
{
   private String fileName;
   private long fileSize;
   private Date modificationTime;
   private UUID guid;
   private String reportName;
   private boolean deployed;

   /**
    * Create report package info from NXCP message.
    *
    * @param msg NXCP message
    * @param baseId base variable ID
    */
   public ReportPackageInfo(NXCPMessage msg, long baseId)
   {
      fileName = msg.getFieldAsString(baseId);
      fileSize = msg.getFieldAsInt64(baseId + 1);
      modificationTime = new Date(msg.getFieldAsInt64(baseId + 2) * 1000);
      guid = msg.getFieldAsUUID(baseId + 3);
      reportName = msg.getFieldAsString(baseId + 4);
      deployed = msg.getFieldAsBoolean(baseId + 5);
   }

   /**
    * @return the file name
    */
   public String getFileName()
   {
      return fileName;
   }

   /**
    * @return the file size in bytes
    */
   public long getFileSize()
   {
      return fileSize;
   }

   /**
    * @return the file modification time
    */
   public Date getModificationTime()
   {
      return modificationTime;
   }

   /**
    * @return the report GUID (Build-Id from manifest), or null if manifest could not be read
    */
   public UUID getGuid()
   {
      return guid;
   }

   /**
    * @return the report name from the compiled report definition, or null if not deployed
    */
   public String getReportName()
   {
      return reportName;
   }

   /**
    * @return true if the package is deployed and active
    */
   public boolean isDeployed()
   {
      return deployed;
   }
}
