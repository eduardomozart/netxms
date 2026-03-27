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
** File: geoareas.cpp
**
**/

#include "webapi.h"

/**
 * Handler for GET /v1/geo-areas
 */
int H_GeoAreas(Context *context)
{
   json_t *output = GetGeoAreasAsJson();
   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for GET /v1/geo-areas/:area-id
 */
int H_GeoAreaDetails(Context *context)
{
   uint32_t areaId = context->getPlaceholderValueAsUInt32(L"area-id");
   if (areaId == 0)
      return 400;

   json_t *output = GetGeoAreaAsJson(areaId);
   if (output == nullptr)
      return 404;

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for POST /v1/geo-areas
 */
int H_GeoAreaCreate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_MANAGE_GEO_AREAS))
      return 403;

   json_t *request = context->getRequestDocument();
   if (request == nullptr)
      return 400;

   json_t *jsonName = json_object_get(request, "name");
   if (!json_is_string(jsonName))
   {
      context->setErrorResponse("Missing or invalid name field");
      return 400;
   }

   uint32_t areaId = 0;
   uint32_t rcc = ModifyGeoArea(0, request, &areaId);
   if (rcc == RCC_GEO_AREA_NAME_EMPTY)
   {
      context->setErrorResponse("Geo area name cannot be empty");
      return 400;
   }
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Geo area [%u] created via REST API", areaId);

   json_t *output = GetGeoAreaAsJson(areaId);
   if (output != nullptr)
   {
      context->setResponseData(output);
      json_decref(output);
   }
   return 201;
}

/**
 * Handler for PUT /v1/geo-areas/:area-id
 */
int H_GeoAreaUpdate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_MANAGE_GEO_AREAS))
      return 403;

   uint32_t areaId = context->getPlaceholderValueAsUInt32(L"area-id");
   if (areaId == 0)
      return 400;

   if (GetGeoArea(areaId) == nullptr)
      return 404;

   json_t *request = context->getRequestDocument();
   if (request == nullptr)
      return 400;

   uint32_t rcc = ModifyGeoArea(areaId, request, &areaId);
   if (rcc == RCC_GEO_AREA_NAME_EMPTY)
   {
      context->setErrorResponse("Geo area name cannot be empty");
      return 400;
   }
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Geo area [%u] updated via REST API", areaId);

   json_t *output = GetGeoAreaAsJson(areaId);
   if (output != nullptr)
   {
      context->setResponseData(output);
      json_decref(output);
   }
   return 200;
}

/**
 * Handler for DELETE /v1/geo-areas/:area-id
 */
int H_GeoAreaDelete(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_MANAGE_GEO_AREAS))
      return 403;

   uint32_t areaId = context->getPlaceholderValueAsUInt32(L"area-id");
   if (areaId == 0)
      return 400;

   if (GetGeoArea(areaId) == nullptr)
      return 404;

   bool forceDelete = context->getQueryParameterAsBoolean("force");
   uint32_t rcc = DeleteGeoArea(areaId, forceDelete);

   if (rcc == RCC_GEO_AREA_IN_USE)
   {
      context->setErrorResponse("Geo area is in use by one or more objects");
      return 409;
   }

   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Geo area [%u] deleted via REST API", areaId);
   return 204;
}
