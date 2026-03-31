/*
** nxdbmgr - NetXMS database manager
** Copyright (C) 2004-2026 Victor Kirhenshtein
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
** File: usermgmt.cpp
**
**/

#include "nxdbmgr.h"

/**
 * Compute SHA256 password hash in database storage format ($A<salt><hash>)
 */
void HashPassword(const TCHAR *password, TCHAR *hashStr, size_t hashStrSize)
{
   char mbPassword[1024];
   wchar_to_utf8(password, -1, mbPassword, 1024);
   mbPassword[1023] = 0;

   BYTE buffer[1024];
   GenerateRandomBytes(buffer, 8);
   strcpy(reinterpret_cast<char*>(&buffer[8]), mbPassword);

   BYTE hash[SHA256_DIGEST_SIZE];
   CalculateSHA256Hash(buffer, strlen(mbPassword) + 8, hash);

   _tcscpy(hashStr, _T("$A"));
   BinToStr(buffer, 8, &hashStr[2]);
   BinToStr(hash, SHA256_DIGEST_SIZE, &hashStr[18]);
}

/**
 * Set user password
 */
void SetUserPassword(const TCHAR *login)
{
   if (!ValidateDatabase())
      return;

   TCHAR query[1024];
   _sntprintf(query, 1024, _T("SELECT id FROM users WHERE name=%s"), DBPrepareString(g_dbHandle, login).cstr());
   DB_RESULT hResult = SQLSelect(query);
   if (hResult == nullptr)
      return;

   if (DBGetNumRows(hResult) == 0)
   {
      DBFreeResult(hResult);
      _tprintf(_T("User \"%s\" not found\n"), login);
      return;
   }

   uint32_t userId = DBGetFieldULong(hResult, 0, 0);
   DBFreeResult(hResult);

   TCHAR password1[256], password2[256];
   if (!ReadPassword(_T("New password: "), password1, 256))
   {
      _tprintf(_T("Cannot read password\n"));
      return;
   }
   if (!ReadPassword(_T("Confirm password: "), password2, 256))
   {
      _tprintf(_T("Cannot read password\n"));
      return;
   }

   if (_tcscmp(password1, password2) != 0)
   {
      _tprintf(_T("ERROR: Passwords do not match\n"));
      return;
   }

   TCHAR hashStr[128];
   HashPassword(password1, hashStr, 128);

   _sntprintf(query, 1024, _T("UPDATE users SET password='%s' WHERE id=%u"), hashStr, userId);
   if (SQLQuery(query))
      _tprintf(_T("Password for user \"%s\" changed successfully\n"), login);
}

/**
 * Unlock user account
 */
void UnlockUser(const TCHAR *login)
{
   if (!ValidateDatabase())
      return;

   TCHAR query[1024];
   _sntprintf(query, 1024, _T("SELECT id,flags FROM users WHERE name=%s"), DBPrepareString(g_dbHandle, login).cstr());
   DB_RESULT hResult = SQLSelect(query);
   if (hResult == nullptr)
      return;

   if (DBGetNumRows(hResult) == 0)
   {
      DBFreeResult(hResult);
      _tprintf(_T("User \"%s\" not found\n"), login);
      return;
   }

   uint32_t userId = DBGetFieldULong(hResult, 0, 0);
   uint32_t flags = DBGetFieldULong(hResult, 0, 1);
   DBFreeResult(hResult);

   flags &= ~UF_INTRUDER_LOCKOUT;

   _sntprintf(query, 1024, _T("UPDATE users SET flags=%u,auth_failures=0,disabled_until=0 WHERE id=%u"), flags, userId);
   if (SQLQuery(query))
      _tprintf(_T("User \"%s\" unlocked successfully\n"), login);
}
