/* 
** nxdbmgr - NetXMS database manager
** Copyright (C) 2013 Alex Kirhenshtein
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
**/

#include "nxdbmgr.h"

/**
 * Reset "system" account password and unlock it
 */
void ResetSystemAccount()
{
   WriteToTerminal(_T("\n\n\x1b[1mWARNING!!!\x1b[0m\n"));
   if (!GetYesNo(_T("This operation will unlock \"system\" user and set a new random password.\nAre you sure?")))
   {
      return;
   }

   if (!ValidateDatabase())
   {
      return;
   }

   TCHAR password[64];
   GenerateRandomPassword(password, 16);

   TCHAR hashStr[128];
   HashPassword(password, hashStr, 128);

   TCHAR query[512];
   _sntprintf(query, 512, _T("UPDATE users SET password='%s', flags=8, grace_logins=5, auth_method=0, auth_failures=0, disabled_until=0, last_login=%u WHERE id=0"),
            hashStr, static_cast<unsigned int>(time(nullptr)));
   SQLQuery(query);

   WriteToTerminal(_T("\nNew \"system\" account password: \x1b[1m"));
   WriteToTerminal(password);
   WriteToTerminal(_T("\x1b[0m\n\n"));
}
