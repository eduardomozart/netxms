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
** File: images.cpp
**
**/

#include "webapi.h"

/**
 * Handler for GET /v1/image-library
 * Returns list of all images in the library (metadata only).
 * Optional query parameter: category (filter by category)
 */
int H_ImageLibrary(Context *context)
{
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   StringBuffer query(L"SELECT guid,name,category,mimetype,protected FROM images");
   const char *category = context->getQueryParameter("category");
   if ((category != nullptr) && (*category != 0))
   {
      TCHAR categoryW[256];
      utf8_to_tchar(category, -1, categoryW, 256);
      query.append(L" WHERE category=");
      query.append(DBPrepareString(hdb, categoryW));
   }
   query.append(L" ORDER BY name");

   DB_RESULT result = DBSelect(hdb, query);
   if (result == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   int count = DBGetNumRows(result);
   json_t *output = json_array();
   TCHAR buffer[256];
   for (int i = 0; i < count; i++)
   {
      json_t *entry = json_object();

      uuid guid = DBGetFieldGUID(result, i, 0);
      char guidText[64];
      guid.toStringA(guidText);
      json_object_set_new(entry, "guid", json_string(guidText));

      DBGetField(result, i, 1, buffer, 256);
      json_object_set_new(entry, "name", json_string_t(buffer));

      DBGetField(result, i, 2, buffer, 256);
      json_object_set_new(entry, "category", json_string_t(buffer));

      DBGetField(result, i, 3, buffer, 256);
      json_object_set_new(entry, "mimeType", json_string_t(buffer));

      json_object_set_new(entry, "isProtected", json_boolean(DBGetFieldLong(result, i, 4) != 0));

      json_array_append_new(output, entry);
   }

   DBFreeResult(result);
   DBConnectionPoolReleaseConnection(hdb);

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for GET /v1/image-library/:guid
 * Returns image metadata as JSON.
 */
int H_ImageLibraryDetails(Context *context)
{
   const TCHAR *guidText = context->getPlaceholderValue(L"guid");
   if (guidText == nullptr)
      return 400;

   uuid guid = uuid::parse(guidText);
   if (guid.isNull())
      return 400;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   TCHAR query[256];
   _sntprintf(query, 256, L"SELECT name,category,mimetype,protected FROM images WHERE guid='%s'", guidText);
   DB_RESULT result = DBSelect(hdb, query);
   if (result == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   if (DBGetNumRows(result) == 0)
   {
      DBFreeResult(result);
      DBConnectionPoolReleaseConnection(hdb);
      return 404;
   }

   TCHAR buffer[256];
   json_t *output = json_object();

   char guidTextA[64];
   guid.toStringA(guidTextA);
   json_object_set_new(output, "guid", json_string(guidTextA));

   DBGetField(result, 0, 0, buffer, 256);
   json_object_set_new(output, "name", json_string_t(buffer));

   DBGetField(result, 0, 1, buffer, 256);
   json_object_set_new(output, "category", json_string_t(buffer));

   DBGetField(result, 0, 2, buffer, 256);
   json_object_set_new(output, "mimeType", json_string_t(buffer));

   json_object_set_new(output, "isProtected", json_boolean(DBGetFieldLong(result, 0, 3) != 0));

   DBFreeResult(result);
   DBConnectionPoolReleaseConnection(hdb);

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for GET /v1/image-library/:guid/data
 * Returns the raw image binary data with correct Content-Type.
 */
int H_ImageLibraryData(Context *context)
{
   const TCHAR *guidText = context->getPlaceholderValue(L"guid");
   if (guidText == nullptr)
      return 400;

   uuid guid = uuid::parse(guidText);
   if (guid.isNull())
      return 400;

   // Get MIME type from database
   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   TCHAR query[256];
   _sntprintf(query, 256, L"SELECT mimetype FROM images WHERE guid='%s'", guidText);
   DB_RESULT result = DBSelect(hdb, query);
   if (result == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   if (DBGetNumRows(result) == 0)
   {
      DBFreeResult(result);
      DBConnectionPoolReleaseConnection(hdb);
      return 404;
   }

   TCHAR mimeType[64];
   DBGetField(result, 0, 0, mimeType, 64);
   DBFreeResult(result);
   DBConnectionPoolReleaseConnection(hdb);

   // Load file from disk
   TCHAR absFileName[MAX_PATH];
   _sntprintf(absFileName, MAX_PATH, L"%s" DDIR_IMAGES FS_PATH_SEPARATOR L"%s", g_netxmsdDataDir, guidText);

   size_t fileSize;
   BYTE *data = LoadFile(absFileName, &fileSize);
   if (data == nullptr)
      return 404;

   char mimeTypeA[64];
   tchar_to_utf8(mimeType, -1, mimeTypeA, 64);
   context->setResponseData(data, fileSize, mimeTypeA);
   MemFree(data);
   return 200;
}
