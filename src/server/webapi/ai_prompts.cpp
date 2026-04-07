/*
** NetXMS - Network Management System
** Copyright (C) 2025-2026 Raden Solutions
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
** File: ai_prompts.cpp
**
**/

#include "webapi.h"

/**
 * Build JSON object from a saved prompt database row.
 * Column order: id, name, description, prompt_text
 */
static json_t *BuildPromptJson(DB_RESULT hResult, int row)
{
   json_t *obj = json_object();
   json_object_set_new(obj, "id", json_integer(DBGetFieldULong(hResult, row, 0)));

   TCHAR name[256];
   DBGetField(hResult, row, 1, name, 256);
   json_object_set_new(obj, "name", json_string_t(name));

   char *description = DBGetFieldUTF8(hResult, row, 2, nullptr, 0);
   if (description != nullptr && *description != 0)
      json_object_set_new(obj, "description", json_string(description));
   else
      json_object_set_new(obj, "description", json_null());
   MemFree(description);

   char *promptText = DBGetFieldUTF8(hResult, row, 3, nullptr, 0);
   json_object_set_new(obj, "promptText", (promptText != nullptr) ? json_string(promptText) : json_null());
   MemFree(promptText);

   return obj;
}

/**
 * Handler for GET /v1/ai/saved-prompts - list all saved prompts for current user
 */
int H_AiSavedPrompts(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_USE_AI_ASSISTANT))
      return 403;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb,
      _T("SELECT id,name,description,prompt_text FROM ai_saved_prompts WHERE user_id=? ORDER BY name"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, context->getUserId());
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   DBFreeStatement(hStmt);

   if (hResult == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   json_t *output = json_array();
   int count = DBGetNumRows(hResult);
   for (int i = 0; i < count; i++)
      json_array_append_new(output, BuildPromptJson(hResult, i));

   DBFreeResult(hResult);
   DBConnectionPoolReleaseConnection(hdb);

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for GET /v1/ai/saved-prompts/:prompt-id - get a single saved prompt
 */
int H_AiSavedPromptDetails(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_USE_AI_ASSISTANT))
      return 403;

   uint32_t promptId = context->getPlaceholderValueAsUInt32(L"prompt-id");
   if (promptId == 0)
      return 400;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb,
      _T("SELECT id,name,description,prompt_text FROM ai_saved_prompts WHERE id=? AND user_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, promptId);
   DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, context->getUserId());
   DB_RESULT hResult = DBSelectPrepared(hStmt);
   DBFreeStatement(hStmt);

   if (hResult == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   if (DBGetNumRows(hResult) == 0)
   {
      DBFreeResult(hResult);
      DBConnectionPoolReleaseConnection(hdb);
      return 404;
   }

   json_t *output = BuildPromptJson(hResult, 0);
   DBFreeResult(hResult);
   DBConnectionPoolReleaseConnection(hdb);

   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for POST /v1/ai/saved-prompts - create a new saved prompt
 */
int H_AiSavedPromptCreate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_USE_AI_ASSISTANT))
      return 403;

   json_t *request = context->getRequestDocument();
   if (request == nullptr)
      return 400;

   const char *name = json_object_get_string_utf8(request, "name", nullptr);
   if (name == nullptr || *name == '\0')
   {
      context->setErrorResponse("Missing or empty \"name\" field");
      return 400;
   }

   const char *promptText = json_object_get_string_utf8(request, "promptText", nullptr);
   if (promptText == nullptr || *promptText == '\0')
   {
      context->setErrorResponse("Missing or empty \"promptText\" field");
      return 400;
   }

   const char *description = json_object_get_string_utf8(request, "description", nullptr);

   uint32_t promptId = CreateUniqueId(IDG_AI_SAVED_PROMPT);
   if (promptId == 0)
   {
      context->setErrorResponse("Unable to allocate unique ID");
      return 500;
   }

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb,
      _T("INSERT INTO ai_saved_prompts (id,user_id,name,description,prompt_text) VALUES (?,?,?,?,?)"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, promptId);
   DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, context->getUserId());
   DBBind(hStmt, 3, DB_SQLTYPE_VARCHAR, DB_CTYPE_UTF8_STRING, name, DB_BIND_STATIC);
   DBBind(hStmt, 4, DB_SQLTYPE_VARCHAR, DB_CTYPE_UTF8_STRING, (description != nullptr) ? description : "", DB_BIND_STATIC);
   DBBind(hStmt, 5, DB_SQLTYPE_TEXT, DB_CTYPE_UTF8_STRING, promptText, DB_BIND_STATIC);

   bool success = DBExecute(hStmt);
   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);

   if (!success)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   json_t *output = json_object();
   json_object_set_new(output, "id", json_integer(promptId));
   json_object_set_new(output, "name", json_string(name));
   json_object_set_new(output, "description", (description != nullptr) ? json_string(description) : json_null());
   json_object_set_new(output, "promptText", json_string(promptText));
   context->setResponseData(output);
   json_decref(output);
   return 201;
}

/**
 * Handler for PUT /v1/ai/saved-prompts/:prompt-id - update a saved prompt
 */
int H_AiSavedPromptUpdate(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_USE_AI_ASSISTANT))
      return 403;

   uint32_t promptId = context->getPlaceholderValueAsUInt32(L"prompt-id");
   if (promptId == 0)
      return 400;

   json_t *request = context->getRequestDocument();
   if (request == nullptr)
      return 400;

   const char *name = json_object_get_string_utf8(request, "name", nullptr);
   if (name == nullptr || *name == '\0')
   {
      context->setErrorResponse("Missing or empty \"name\" field");
      return 400;
   }

   const char *promptText = json_object_get_string_utf8(request, "promptText", nullptr);
   if (promptText == nullptr || *promptText == '\0')
   {
      context->setErrorResponse("Missing or empty \"promptText\" field");
      return 400;
   }

   const char *description = json_object_get_string_utf8(request, "description", nullptr);

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb,
      _T("UPDATE ai_saved_prompts SET name=?,description=?,prompt_text=? WHERE id=? AND user_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_VARCHAR, DB_CTYPE_UTF8_STRING, name, DB_BIND_STATIC);
   DBBind(hStmt, 2, DB_SQLTYPE_VARCHAR, DB_CTYPE_UTF8_STRING, (description != nullptr) ? description : "", DB_BIND_STATIC);
   DBBind(hStmt, 3, DB_SQLTYPE_TEXT, DB_CTYPE_UTF8_STRING, promptText, DB_BIND_STATIC);
   DBBind(hStmt, 4, DB_SQLTYPE_INTEGER, promptId);
   DBBind(hStmt, 5, DB_SQLTYPE_INTEGER, context->getUserId());

   bool success = DBExecute(hStmt);
   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);

   if (!success)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   json_t *output = json_object();
   json_object_set_new(output, "id", json_integer(promptId));
   json_object_set_new(output, "name", json_string(name));
   json_object_set_new(output, "description", (description != nullptr) ? json_string(description) : json_null());
   json_object_set_new(output, "promptText", json_string(promptText));
   context->setResponseData(output);
   json_decref(output);
   return 200;
}

/**
 * Handler for DELETE /v1/ai/saved-prompts/:prompt-id - delete a saved prompt
 */
int H_AiSavedPromptDelete(Context *context)
{
   if (!context->checkSystemAccessRights(SYSTEM_ACCESS_USE_AI_ASSISTANT))
      return 403;

   uint32_t promptId = context->getPlaceholderValueAsUInt32(L"prompt-id");
   if (promptId == 0)
      return 400;

   DB_HANDLE hdb = DBConnectionPoolAcquireConnection();

   DB_STATEMENT hStmt = DBPrepare(hdb,
      _T("DELETE FROM ai_saved_prompts WHERE id=? AND user_id=?"));
   if (hStmt == nullptr)
   {
      DBConnectionPoolReleaseConnection(hdb);
      return 500;
   }

   DBBind(hStmt, 1, DB_SQLTYPE_INTEGER, promptId);
   DBBind(hStmt, 2, DB_SQLTYPE_INTEGER, context->getUserId());

   bool success = DBExecute(hStmt);
   DBFreeStatement(hStmt);
   DBConnectionPoolReleaseConnection(hdb);

   if (!success)
   {
      context->setErrorResponse("Database failure");
      return 500;
   }

   return 204;
}
