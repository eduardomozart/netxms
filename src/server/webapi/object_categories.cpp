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
** File: object_categories.cpp
**
**/

#include "webapi.h"

/**
 * Handler for GET /v1/object-categories
 */
int H_ObjectCategories(Context *context)
{
   json_t *output = GetObjectCategoriesAsJson();
   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for GET /v1/object-categories/:category-id
 */
int H_ObjectCategoryDetails(Context *context)
{
   uint32_t categoryId = context->getPlaceholderValueAsUInt32(L"category-id");
   if (categoryId == 0)
      return 400;

   json_t *output = GetObjectCategoryAsJson(categoryId);
   if (output == nullptr)
      return 404;

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for POST /v1/object-categories
 */
int H_ObjectCategoryCreate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_OBJECT_CATEGORIES))
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
   uint32_t rcc = ModifyObjectCategory(0, request, &categoryId);
   if (rcc == RCC_CATEGORY_NAME_EMPTY)
   {
      context->setErrorResponse("Category name cannot be empty");
      return 400;
   }
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Object category [%u] created via REST API", categoryId);

   json_t *output = GetObjectCategoryAsJson(categoryId);
   if (output != nullptr)
   {
      context->setResponseData(output);
      json_decref(output);
   }
   return 201;
}

/**
 * Handler for PUT /v1/object-categories/:category-id
 */
int H_ObjectCategoryUpdate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_OBJECT_CATEGORIES))
      return 403;

   uint32_t categoryId = context->getPlaceholderValueAsUInt32(L"category-id");
   if (categoryId == 0)
      return 400;

   if (GetObjectCategory(categoryId) == nullptr)
      return 404;

   json_t *request = context->getRequestDocument();
   if (request == nullptr)
      return 400;

   uint32_t rcc = ModifyObjectCategory(categoryId, request, &categoryId);
   if (rcc == RCC_CATEGORY_NAME_EMPTY)
   {
      context->setErrorResponse("Category name cannot be empty");
      return 400;
   }
   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Object category [%u] updated via REST API", categoryId);

   json_t *output = GetObjectCategoryAsJson(categoryId);
   if (output != nullptr)
   {
      context->setResponseData(output);
      json_decref(output);
   }
   return 200;
}

/**
 * Handler for DELETE /v1/object-categories/:category-id
 */
int H_ObjectCategoryDelete(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_OBJECT_CATEGORIES))
      return 403;

   uint32_t categoryId = context->getPlaceholderValueAsUInt32(L"category-id");
   if (categoryId == 0)
      return 400;

   if (GetObjectCategory(categoryId) == nullptr)
      return 404;

   bool forceDelete = context->getQueryParameterAsBoolean("force");
   uint32_t rcc = DeleteObjectCategory(categoryId, forceDelete);

   if (rcc == RCC_CATEGORY_IN_USE)
   {
      context->setErrorResponse("Object category is in use by one or more objects");
      return 409;
   }

   if (rcc != RCC_SUCCESS)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   context->writeAuditLog(AUDIT_SYSCFG, true, 0, L"Object category [%u] deleted via REST API", categoryId);
   return 204;
}
