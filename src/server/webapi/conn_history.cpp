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
** File: conn_history.cpp
**
**/

#include "webapi.h"

/**
 * Handler for GET /v1/connection-history
 */
int H_GetConnectionHistory(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_SEARCH_NETWORK))
   {
      context->setErrorResponse("Access denied");
      return 403;
   }

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   StringBuffer query((g_dbSyntax == DB_SYNTAX_TSDB) ?
      L"SELECT record_id,date_part('epoch',event_timestamp)::int,mac_address,ip_address,node_id,"
      L"switch_id,interface_id,vlan_id,event_type,old_switch_id,old_interface_id "
      L"FROM connection_history WHERE 1=1" :
      L"SELECT record_id,event_timestamp,mac_address,ip_address,node_id,"
      L"switch_id,interface_id,vlan_id,event_type,old_switch_id,old_interface_id "
      L"FROM connection_history WHERE 1=1");

   const char *macAddress = context->getQueryParameter("macAddress");
   if (macAddress != nullptr && *macAddress != 0)
   {
      MacAddress mac = MacAddress::parse(macAddress, true);
      if (mac.length() > 0)
      {
         TCHAR macStr[24];
         mac.toString(macStr);
         query.append(L" AND mac_address='");
         query.append(macStr);
         query.append(L"'");
      }
   }

   int32_t nodeId = context->getQueryParameterAsInt32("nodeId", 0);
   if (nodeId != 0)
   {
      TCHAR buf[64];
      _sntprintf(buf, 64, L" AND (switch_id=%d OR node_id=%d)", nodeId, nodeId);
      query.append(buf);
   }

   int32_t switchId = context->getQueryParameterAsInt32("switchId", 0);
   if (switchId != 0)
   {
      TCHAR buf[64];
      _sntprintf(buf, 64, L" AND switch_id=%d", switchId);
      query.append(buf);
   }

   int32_t interfaceId = context->getQueryParameterAsInt32("interfaceId", 0);
   if (interfaceId != 0)
   {
      TCHAR buf[64];
      _sntprintf(buf, 64, L" AND interface_id=%d", interfaceId);
      query.append(buf);
   }

   time_t timeFrom = context->getQueryParameterAsTime("from", 0);
   if (timeFrom != 0)
   {
      TCHAR buf[128];
      if (g_dbSyntax == DB_SYNTAX_TSDB)
         _sntprintf(buf, 128, L" AND event_timestamp>=to_timestamp(" INT64_FMT L")", static_cast<int64_t>(timeFrom));
      else
         _sntprintf(buf, 128, L" AND event_timestamp>=" INT64_FMT, static_cast<int64_t>(timeFrom));
      query.append(buf);
   }

   time_t timeTo = context->getQueryParameterAsTime("to", 0);
   if (timeTo != 0)
   {
      TCHAR buf[128];
      if (g_dbSyntax == DB_SYNTAX_TSDB)
         _sntprintf(buf, 128, L" AND event_timestamp<=to_timestamp(" INT64_FMT L")", static_cast<int64_t>(timeTo));
      else
         _sntprintf(buf, 128, L" AND event_timestamp<=" INT64_FMT, static_cast<int64_t>(timeTo));
      query.append(buf);
   }

   query.append(L" ORDER BY event_timestamp DESC");

   int32_t limit = context->getQueryParameterAsInt32("limit", 1000);
   if (limit <= 0 || limit > 10000)
      limit = 1000;

   DB_RESULT hResult = DBSelect(hdb, query.cstr());
   json_t *output = json_array();

   if (hResult != nullptr)
   {
      int count = std::min(DBGetNumRows(hResult), limit);
      TCHAR buffer[256];
      for (int i = 0; i < count; i++)
      {
         json_t *record = json_object();
         json_object_set_new(record, "recordId", json_integer(DBGetFieldInt64(hResult, i, 0)));
         json_object_set_new(record, "timestamp", json_integer(DBGetFieldULong(hResult, i, 1)));

         char macStr[24];
         DBGetFieldA(hResult, i, 2, macStr, 24);
         json_object_set_new(record, "macAddress", json_string(macStr));

         char ipStr[64];
         DBGetFieldA(hResult, i, 3, ipStr, 64);
         json_object_set_new(record, "ipAddress", json_string(ipStr));

         json_object_set_new(record, "nodeId", json_integer(DBGetFieldULong(hResult, i, 4)));
         json_object_set_new(record, "switchId", json_integer(DBGetFieldULong(hResult, i, 5)));
         json_object_set_new(record, "interfaceId", json_integer(DBGetFieldULong(hResult, i, 6)));
         json_object_set_new(record, "vlanId", json_integer(DBGetFieldULong(hResult, i, 7)));
         json_object_set_new(record, "eventType", json_integer(DBGetFieldULong(hResult, i, 8)));
         json_object_set_new(record, "oldSwitchId", json_integer(DBGetFieldULong(hResult, i, 9)));
         json_object_set_new(record, "oldInterfaceId", json_integer(DBGetFieldULong(hResult, i, 10)));

         json_array_append_new(output, record);
      }
      DBFreeResult(hResult);
   }

   DBConnectionPoolReleaseConnection(hdb);

   context->setResponseData(output);
   json_decref(output);
   return 200;
}
