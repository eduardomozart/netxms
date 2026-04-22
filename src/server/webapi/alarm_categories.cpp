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
** File: alarm_categories.cpp
**
**/

#include "webapi.h"

/**
 * Handler for GET /v1/alarm-categories
 */
int H_AlarmCategories(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_EPP))
      return 403;

   json_t *output = GetAlarmCategoriesAsJson();
   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for GET /v1/alarm-categories/:category-id
 */
int H_AlarmCategoryDetails(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_EPP))
      return 403;

   uint32_t categoryId = context->getPlaceholderValueAsUInt32(L"category-id");
   if (categoryId == 0)
      return 400;

   json_t *output = GetAlarmCategoryAsJson(categoryId);
   if (output == nullptr)
      return 404;

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for POST /v1/alarm-categories
 */
int H_AlarmCategoryCreate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_EPP))
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

   uint32_t categoryId = 0;
   uint32_t rcc = ModifyAlarmCategoryFromJson(0, request, &categoryId);
   if (rcc == RCC_CATEGORY_NAME_EMPTY)
   {
      context->setErrorResponse("Alarm category name cannot be empty");
      return 400;
   }
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Alarm category [%u] created via REST API", categoryId);

   json_t *output = GetAlarmCategoryAsJson(categoryId);
   if (output != nullptr)
   {
      context->setResponseData(output);
      json_decref(output);
   }
   return 201;
}

/**
 * Handler for PUT /v1/alarm-categories/:category-id
 */
int H_AlarmCategoryUpdate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_EPP))
      return 403;

   uint32_t categoryId = context->getPlaceholderValueAsUInt32(L"category-id");
   if (categoryId == 0)
      return 400;

   json_t *existing = GetAlarmCategoryAsJson(categoryId);
   if (existing == nullptr)
      return 404;
   json_decref(existing);

   json_t *request = context->getRequestDocument();
   if (request == nullptr)
      return 400;

   uint32_t rcc = ModifyAlarmCategoryFromJson(categoryId, request, &categoryId);
   if (rcc == RCC_CATEGORY_NAME_EMPTY)
   {
      context->setErrorResponse("Alarm category name cannot be empty");
      return 400;
   }
   if (rcc == RCC_INVALID_OBJECT_ID)
      return 404;
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Alarm category [%u] updated via REST API", categoryId);

   json_t *output = GetAlarmCategoryAsJson(categoryId);
   if (output != nullptr)
   {
      context->setResponseData(output);
      json_decref(output);
   }
   return 200;
}

/**
 * Handler for DELETE /v1/alarm-categories/:category-id
 */
int H_AlarmCategoryDelete(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_EPP))
      return 403;

   uint32_t categoryId = context->getPlaceholderValueAsUInt32(L"category-id");
   if (categoryId == 0)
      return 400;

   json_t *existing = GetAlarmCategoryAsJson(categoryId);
   if (existing == nullptr)
      return 404;
   json_decref(existing);

   if (GetEventProcessingPolicy()->isCategoryInUse(categoryId))
   {
      context->setErrorResponse("Alarm category is in use by one or more event processing policy rules");
      return 409;
   }

   uint32_t rcc = DeleteAlarmCategory(categoryId);
   if (rcc == RCC_INVALID_OBJECT_ID)
      return 404;
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Alarm category [%u] deleted via REST API", categoryId);
   return 204;
}
