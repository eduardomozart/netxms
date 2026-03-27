/*
** NetXMS - Network Management System
** Copyright (C) 2003-2024 Raden Solutions
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
** File: geo_areas.cpp
**
**/

#include "nxcore.h"

/**
 * Create geo area object from NXCP message
 */
GeoArea::GeoArea(const NXCPMessage& msg) : m_border(64, 64, Ownership::True)
{
   m_id = msg.getFieldAsUInt32(VID_AREA_ID);
   if (m_id == 0)
      m_id = CreateUniqueId(IDG_GEO_AREA);
   msg.getFieldAsString(VID_NAME, m_name, 128);
   m_comments = msg.getFieldAsString(VID_COMMENTS);

   int count = msg.getFieldAsInt32(VID_NUM_ELEMENTS);
   uint32_t fieldId = VID_ELEMENT_LIST_BASE;
   for(int i = 0; i < count; i++)
   {
      double lat = msg.getFieldAsDouble(fieldId++);
      double lon = msg.getFieldAsDouble(fieldId++);
      m_border.add(new GeoLocation(GL_MANUAL, lat, lon));
   }
}

/**
 * Create geo area object from database record
 * Expected field order: id,name.comments,configuration
 */
GeoArea::GeoArea(DB_RESULT hResult, int row) : m_border(64, 64, Ownership::True)
{
   m_id = DBGetFieldULong(hResult, row, 0);
   DBGetField(hResult, row, 1, m_name, 128);
   m_comments = DBGetField(hResult, row, 2, nullptr, 0);

   wchar_t *configuration = DBGetField(hResult, row, 3, nullptr, 0);
   wchar_t *curr = configuration;
   while(true)
   {
      wchar_t *next = wcschr(curr, L';');
      if (next != nullptr)
         *next = 0;

      wchar_t *s = wcschr(curr, L'/');
      if (s != nullptr)
      {
         *s = 0;
         s++;

         TCHAR *eptr1, *eptr2;
         double lat = wcstod(curr, &eptr1);
         double lon = wcstod(s, &eptr2);
         if ((*eptr1 == 0) && (*eptr2 == 0))
         {
            m_border.add(new GeoLocation(GL_MANUAL, lat, lon));
         }
      }

      if (next == nullptr)
         break;

      curr = next + 1;
   }
   MemFree(configuration);
}

/**
 * Geo area destructor
 */
GeoArea::~GeoArea()
{
   MemFree(m_comments);
}

/**
 * Fill NXCP message with geo area data
 */
void GeoArea::fillMessage(NXCPMessage *msg, uint32_t baseId) const
{
   msg->setField(baseId, m_id);
   msg->setField(baseId + 1, m_name);
   msg->setField(baseId + 2, m_comments);

   // Allow up to 2000 points
   int32_t count = std::min(m_border.size(), 2000);
   msg->setField(baseId + 3, static_cast<int16_t>(count));
   uint32_t fieldId = baseId + 10;
   for(int i = 0; i < count; i++)
   {
      GeoLocation *p = m_border.get(i);
      msg->setField(fieldId++, p->getLatitude());
      msg->setField(fieldId++, p->getLongitude());
   }
}

/**
 * Get area border as string with coordinates in format lat/lon;lat/lon;...
 */
StringBuffer GeoArea::getBorderAsString() const
{
   StringBuffer sb;
   for(int i = 0; i < m_border.size(); i++)
   {
      GeoLocation *p = m_border.get(i);
      sb.append(p->getLatitude());
      sb.append(_T('/'));
      sb.append(p->getLongitude());
      sb.append(_T(';'));
   }
   sb.shrink();   // Remove last ;
   return sb;
}

/**
 * Create JSON representation of geo area
 */
json_t *GeoArea::toJson() const
{
   json_t *root = json_object();
   json_object_set_new(root, "id", json_integer(m_id));
   json_object_set_new(root, "name", json_string_w(m_name));
   json_object_set_new(root, "comments", (m_comments != nullptr) ? json_string_w(m_comments) : json_string(""));
   json_t *border = json_array();
   for(int i = 0; i < m_border.size(); i++)
   {
      GeoLocation *p = m_border.get(i);
      json_t *point = json_object();
      json_object_set_new(point, "latitude", json_real(p->getLatitude()));
      json_object_set_new(point, "longitude", json_real(p->getLongitude()));
      json_array_append_new(border, point);
   }
   json_object_set_new(root, "border", border);
   return root;
}

/**
 * Index of geo areas
 */
static SharedPointerIndex<GeoArea> s_geoAreas;

/**
 * Load geo areas from database
 */
void LoadGeoAreas()
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   DB_RESULT hResult = DBSelect(hdb, L"SELECT id,name,comments,configuration FROM geo_areas");
   if (hResult != nullptr)
   {
      int count = DBGetNumRows(hResult);
      for(int i = 0; i < count; i++)
      {
         auto area = make_shared<GeoArea>(hResult, i);
         s_geoAreas.put(area->getId(), area);
      }
      DBFreeResult(hResult);
   }
   DBConnectionPoolReleaseConnection(hdb);
}

/**
 * Get object category by ID
 */
shared_ptr<GeoArea> NXCORE_EXPORTABLE GetGeoArea(uint32_t id)
{
   return s_geoAreas.get(id);
}

/**
 * Delete geo area
 */
uint32_t NXCORE_EXPORTABLE DeleteGeoArea(uint32_t id, bool forceDelete)
{
   if (forceDelete)
   {
      g_idxObjectById.forEach(
         [id] (NetObj *object) -> EnumerationCallbackResult
         {
            if (object->isDataCollectionTarget())
               static_cast<DataCollectionTarget*>(object)->removeGeoArea(id);
            return _CONTINUE;
         });
   }
   else
   {
      shared_ptr<NetObj> object = g_idxObjectById.find(
         [id] (NetObj *object) -> bool
         {
            if (object->isDataCollectionTarget())
               return static_cast<DataCollectionTarget*>(object)->isGeoAreaReferenced(id);
            return false;
         });
      if (object != nullptr)
         return RCC_GEO_AREA_IN_USE;
   }

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   bool success = ExecuteQueryOnObject(hdb, id, L"DELETE FROM geo_areas WHERE id=?");
   DBConnectionPoolReleaseConnection(hdb);
   if (!success)
      return RCC_DB_FAILURE;

   s_geoAreas.remove(id);

   NotifyClientSessions(NX_NOTIFY_GEO_AREA_DELETED, id, NXC_CHANNEL_GEO_AREAS);
   return RCC_SUCCESS;
}

/**
 * Create or modify geo area from NXCP message
 */
uint32_t ModifyGeoArea(const NXCPMessage& msg, uint32_t *areaId)
{
   auto area = make_shared<GeoArea>(msg);
   if (*area->getName() == 0)
      return RCC_GEO_AREA_NAME_EMPTY;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   static const TCHAR *columns[] = {
      _T("name") ,_T("comments"), _T("configuration"), nullptr
   };
   DB_STATEMENT hStmt = DBPrepareMerge(hdb, _T("geo_areas"), _T("id"), area->getId(), columns);
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return RCC_DB_FAILURE;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_VARCHAR, area->getName(), DB_BIND_STATIC);
   DBBind(hStmt, 2, DB_SQLTYPE_VARCHAR, area->getComments(), DB_BIND_STATIC);
   DBBind(hStmt, 3, DB_SQLTYPE_VARCHAR, area->getBorderAsString(), DB_BIND_TRANSIENT);
   DBBind(hStmt, 4, DB_SQLTYPE_INTEGER, area->getId());
   bool success = DBExecute(hStmt);
   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);

   if (!success)
      return RCC_DB_FAILURE;

   s_geoAreas.put(area->getId(), area);
   *areaId = area->getId();

   NXCPMessage notificationMessage(CMD_GEO_AREA_UPDATE, 0);
   area->fillMessage(&notificationMessage, VID_ELEMENT_LIST_BASE);
   NotifyClientSessions(notificationMessage, NXC_CHANNEL_GEO_AREAS);

   return RCC_SUCCESS;
}

/**
 * Create or modify geo area from JSON
 */
uint32_t NXCORE_EXPORTABLE ModifyGeoArea(uint32_t id, json_t *json, uint32_t *areaId)
{
   json_t *jsonName = json_object_get(json, "name");
   if (!json_is_string(jsonName))
      return RCC_GEO_AREA_NAME_EMPTY;

   wchar_t name[128];
   utf8_to_wchar(json_string_value(jsonName), -1, name, 128);
   if (name[0] == 0)
      return RCC_GEO_AREA_NAME_EMPTY;

   if (id == 0)
      id = CreateUniqueId(IDG_GEO_AREA);

   wchar_t comments[MAX_DB_STRING];
   json_t *jsonComments = json_object_get(json, "comments");
   if (json_is_string(jsonComments))
      utf8_to_wchar(json_string_value(jsonComments), -1, comments, MAX_DB_STRING);
   else
      comments[0] = 0;

   ObjectArray<GeoLocation> border(64, 64, Ownership::True);
   json_t *jsonBorder = json_object_get(json, "border");
   if (json_is_array(jsonBorder))
   {
      size_t index;
      json_t *point;
      json_array_foreach(jsonBorder, index, point)
      {
         json_t *lat = json_object_get(point, "latitude");
         json_t *lon = json_object_get(point, "longitude");
         if (json_is_number(lat) && json_is_number(lon))
            border.add(new GeoLocation(GL_MANUAL, json_real_value(lat), json_real_value(lon)));
      }
   }

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();
   static const wchar_t *columns[] = {
      L"name", L"comments", L"configuration", nullptr
   };
   DB_STATEMENT hStmt = DBPrepareMerge(hdb, L"geo_areas", L"id", id, columns);
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return RCC_DB_FAILURE;
   }

   StringBuffer borderStr;
   for(int i = 0; i < border.size(); i++)
   {
      GeoLocation *p = border.get(i);
      borderStr.append(p->getLatitude());
      borderStr.append(L'/');
      borderStr.append(p->getLongitude());
      borderStr.append(L';');
   }
   borderStr.shrink();

   DBBind(hStmt, 1, DB_SQLTYPE_VARCHAR, name, DB_BIND_STATIC);
   DBBind(hStmt, 2, DB_SQLTYPE_VARCHAR, comments, DB_BIND_STATIC);
   DBBind(hStmt, 3, DB_SQLTYPE_VARCHAR, borderStr, DB_BIND_TRANSIENT);
   DBBind(hStmt, 4, DB_SQLTYPE_INTEGER, id);
   bool success = DBExecute(hStmt);
   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);

   if (!success)
      return RCC_DB_FAILURE;

   // Re-read from DB to construct proper GeoArea object
   DB_HANDLE hdb2 = DBConnectionPoolAcquireConnection();
   wchar_t query[256];
   swprintf(query, 256, L"SELECT id,name,comments,configuration FROM geo_areas WHERE id=%u", id);
   DB_RESULT hResult = DBSelect(hdb2, query);
   if (hResult != nullptr)
   {
      if (DBGetNumRows(hResult) > 0)
      {
         auto area = make_shared<GeoArea>(hResult, 0);
         s_geoAreas.put(area->getId(), area);

         NXCPMessage notificationMessage(CMD_GEO_AREA_UPDATE, 0);
         area->fillMessage(&notificationMessage, VID_ELEMENT_LIST_BASE);
         NotifyClientSessions(notificationMessage, NXC_CHANNEL_GEO_AREAS);
      }
      DBFreeResult(hResult);
   }
   DBConnectionPoolReleaseConnection(hdb2);

   *areaId = id;
   return RCC_SUCCESS;
}

/**
 * Get all geo areas as JSON array
 */
json_t NXCORE_EXPORTABLE *GetGeoAreasAsJson()
{
   json_t *output = json_array();
   s_geoAreas.forEach(
      [output](GeoArea *area) -> EnumerationCallbackResult
      {
         json_array_append_new(output, area->toJson());
         return _CONTINUE;
      });
   return output;
}

/**
 * Get single geo area as JSON object or nullptr if not found
 */
json_t NXCORE_EXPORTABLE *GetGeoAreaAsJson(uint32_t id)
{
   shared_ptr<GeoArea> area = s_geoAreas.get(id);
   return (area != nullptr) ? area->toJson() : nullptr;
}

/**
 * Callback for filling NXCP message
 */
static EnumerationCallbackResult FillMessage(GeoArea *area, std::pair<NXCPMessage*, uint32_t> *context)
{
   area->fillMessage(context->first, context->second);
   context->second += 4096;
   return _CONTINUE;
}

/**
 * Get list of all geo areas into NXCP message
 */
void GeoAreasToMessage(NXCPMessage *msg)
{
   std::pair<NXCPMessage*, uint32_t> context(msg, VID_ELEMENT_LIST_BASE);
   s_geoAreas.forEach(FillMessage, &context);
   msg->setField(VID_NUM_ELEMENTS, (context.second - VID_ELEMENT_LIST_BASE) / 4096);
}
