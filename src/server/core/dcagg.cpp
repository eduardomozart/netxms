/*
** NetXMS - Network Management System
** Copyright (C) 2003-2026 Victor Kirhenshtein
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
** File: dcagg.cpp
**
**/

#include "nxcore.h"

#define DEBUG_TAG L"dc.aggregation"

/**
 * Create per-object hourly and daily aggregate tables (idata_1h_<N>, idata_1d_<N>).
 * Non-TSDB only - TSDB aggregation uses global continuous aggregates, not per-object tables.
 *
 * Callers (phase 2 rollup path) are responsible for setting ODF_HAS_IDATA_1H_TABLE /
 * ODF_HAS_IDATA_1D_TABLE on the data collection target so the tables are dropped on
 * object deletion.
 */
bool CreateAggregateTables(DB_HANDLE hdb, uint32_t objectId)
{
   const wchar_t *int64Type = (g_dbSyntax == DB_SYNTAX_ORACLE) ? L"number(20)" : L"bigint";
   const wchar_t *doubleType;
   switch(g_dbSyntax)
   {
      case DB_SYNTAX_MSSQL:
         doubleType = L"float";
         break;
      case DB_SYNTAX_ORACLE:
         doubleType = L"binary_double";
         break;
      case DB_SYNTAX_SQLITE:
         doubleType = L"real";
         break;
      default:
         doubleType = L"double precision";
         break;
   }

   wchar_t query[512];
   nx_swprintf(query, 512,
      L"CREATE TABLE idata_1h_%u (item_id integer not null,bucket_start %s not null,"
      L"min_value %s null,max_value %s null,avg_value %s null,sample_count integer not null,"
      L"PRIMARY KEY(item_id,bucket_start))",
      objectId, int64Type, doubleType, doubleType, doubleType);
   if (!DBQuery(hdb, query))
      return false;

   nx_swprintf(query, 512,
      L"CREATE TABLE idata_1d_%u (item_id integer not null,bucket_start %s not null,"
      L"min_value %s null,max_value %s null,avg_value %s null,sample_count integer not null,"
      L"PRIMARY KEY(item_id,bucket_start))",
      objectId, int64Type, doubleType, doubleType, doubleType);
   return DBQuery(hdb, query);
}

/**
 * Scheduled task handler - hourly DCI data aggregation rollup.
 *
 * Phase 1 scaffolding: registered and scheduled, but returns immediately
 * until DataCollection.Aggregation.Enabled is set. Rollup logic lands in phase 2.
 */
void HourlyDataAggregationRollup(const shared_ptr<ScheduledTaskParameters>& parameters)
{
   if (!ConfigReadBoolean(L"DataCollection.Aggregation.Enabled", false))
   {
      nxlog_debug_tag(DEBUG_TAG, 7, L"Hourly rollup skipped - master switch disabled");
      return;
   }
   nxlog_debug_tag(DEBUG_TAG, 5, L"Hourly rollup not implemented yet (phase 1 stub)");
}

/**
 * Scheduled task handler - daily DCI data aggregation rollup.
 *
 * Phase 1 scaffolding: registered and scheduled, but returns immediately
 * until DataCollection.Aggregation.Enabled is set. Rollup logic lands in phase 2.
 */
void DailyDataAggregationRollup(const shared_ptr<ScheduledTaskParameters>& parameters)
{
   if (!ConfigReadBoolean(L"DataCollection.Aggregation.Enabled", false))
   {
      nxlog_debug_tag(DEBUG_TAG, 7, L"Daily rollup skipped - master switch disabled");
      return;
   }
   nxlog_debug_tag(DEBUG_TAG, 5, L"Daily rollup not implemented yet (phase 1 stub)");
}
