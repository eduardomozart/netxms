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
** File: resetmonitoring.cpp
**
**/

#include "nxdbmgr.h"

/**
 * Truncate or delete all rows from a table (DB syntax aware)
 */
static bool TruncateTable(const wchar_t *table)
{
   wchar_t query[256];
   if ((g_dbSyntax == DB_SYNTAX_ORACLE) || (g_dbSyntax == DB_SYNTAX_PGSQL) || (g_dbSyntax == DB_SYNTAX_TSDB))
      nx_swprintf(query, 256, L"TRUNCATE TABLE %s", table);
   else
      nx_swprintf(query, 256, L"DELETE FROM %s", table);
   return SQLQuery(query);
}

/**
 * Execute all monitoring state reset SQL operations
 */
static bool DoResetMonitoringState()
{
   // Terminate all alarms
   CHK_EXEC_RB(SQLQuery(L"UPDATE alarms SET alarm_state=3"));
   CHK_EXEC_RB(TruncateTable(L"alarm_events"));
   CHK_EXEC_RB(TruncateTable(L"alarm_state_changes"));

   // Reset thresholds
   CHK_EXEC_RB(SQLQuery(L"UPDATE thresholds SET current_state=0, current_severity=0, "
      L"match_count=0, clear_match_count=0, last_checked_value=NULL, "
      L"last_event_timestamp=0, last_event_message=NULL, state_before_maint='0'"));
   CHK_EXEC_RB(TruncateTable(L"dct_threshold_instances"));

   // Reset object status to UNKNOWN, preserve maintenance mode info
   CHK_EXEC_RB(SQLQuery(L"UPDATE object_properties SET status=5, state_before_maint=0 WHERE status<>6"));

   // Reset node monitoring state
   CHK_EXEC_RB(SQLQuery(L"UPDATE nodes SET down_since=0, fail_time_snmp=0, "
      L"fail_time_agent=0, fail_time_ssh=0, last_agent_comm_time=0, "
      L"syslog_msg_count=0, snmp_trap_count=0, cip_status=0, cip_state=0, "
      L"last_events=NULL"));

   // Reset interface states
   CHK_EXEC_RB(SQLQuery(L"UPDATE interfaces SET admin_state=0, oper_state=0, "
      L"dot1x_pae_state=0, dot1x_backend_state=0, last_known_oper_state=0, "
      L"last_known_admin_state=0, ospf_if_state=0, stp_port_state=0, "
      L"state_before_maintenance=NULL"));

   // Reset NOT_SUPPORTED DCIs to ACTIVE
   CHK_EXEC_RB(SQLQuery(L"UPDATE items SET status=0, state_flags=0, grace_period_start=0 WHERE status=2"));
   CHK_EXEC_RB(SQLQuery(L"UPDATE dc_tables SET status=0, state_flags=0, grace_period_start=0 WHERE status=2"));
   CHK_EXEC_RB(TruncateTable(L"raw_dci_values"));

   // Clear ICMP statistics
   CHK_EXEC_RB(TruncateTable(L"icmp_statistics"));

   // Reset access point state
   CHK_EXEC_RB(SQLQuery(L"UPDATE access_points SET ap_state=0, down_since=0, grace_period_start=0"));

   // Set flag for server to regenerate maintenance mode events on next startup
   CHK_EXEC_RB(CreateConfigParam(L"Internal.MonitoringStateReset", L"1", false, false, true));

   return true;
}

/**
 * Reset all monitoring state
 */
void ResetMonitoringState()
{
   WriteToTerminal(_T("\n\n\x1b[1mWARNING!!!\x1b[0m\n"));
   if (!GetYesNo(_T("This operation will reset all monitoring state in the database.\n")
      _T("All alarms will be terminated, thresholds reset, object statuses set to UNKNOWN,\n")
      _T("and DCI states cleared. The server will rediscover everything on next startup.\n")
      _T("Are you sure?")))
   {
      return;
   }

   if (!ValidateDatabase())
      return;

   if (DBBegin(g_dbHandle))
   {
      if (DoResetMonitoringState())
      {
         if (DBCommit(g_dbHandle))
            WriteToTerminal(_T("Monitoring state successfully reset\n"));
         else
            WriteToTerminal(_T("ERROR: cannot commit transaction\n"));
      }
      else
      {
         DBRollback(g_dbHandle);
      }
   }
   else
   {
      WriteToTerminal(_T("ERROR: cannot start transaction\n"));
   }
}
