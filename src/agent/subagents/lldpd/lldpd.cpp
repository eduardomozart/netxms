/*
** NetXMS subagent for lldpd integration
** Copyright (C) 2026 Raden Solutions
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
** File: lldpd.cpp
** Exposes LLDP neighbor information collected by lldpd via the liblldpctl
** control socket API.
**/

#include <nms_common.h>
#include <nms_agent.h>
#include <nms_util.h>
#include <netxms-version.h>

#include <lldpctl.h>

#define DEBUG_TAG _T("lldpd")

/**
 * Set a UTF-8 string returned by lldpctl into a table cell.
 * No-op when the source value is null.
 */
static inline void SetUTF8Cell(Table *table, int column, const char *value)
{
   if (value == nullptr)
      return;
#ifdef UNICODE
   table->setPreallocated(column, WideStringFromUTF8String(value));
#else
   table->set(column, value);
#endif
}

/**
 * Handler for LLDP.Neighbors table
 */
static LONG H_LldpNeighbors(const TCHAR *cmd, const TCHAR *arg, Table *table, AbstractCommSession *session)
{
   lldpctl_conn_t *conn = lldpctl_new_name(lldpctl_get_default_transport(), nullptr, nullptr, nullptr);
   if (conn == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("H_LldpNeighbors: lldpctl_new_name failed (out of memory)"));
      return SYSINFO_RC_ERROR;
   }
   if (lldpctl_last_error(conn) != LLDPCTL_NO_ERROR)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("H_LldpNeighbors: cannot connect to lldpd (%hs)"),
            lldpctl_last_strerror(conn));
      lldpctl_release(conn);
      return SYSINFO_RC_ERROR;
   }

   lldpctl_atom_t *interfaces = lldpctl_get_interfaces(conn);
   if (interfaces == nullptr)
   {
      nxlog_debug_tag(DEBUG_TAG, 4, _T("H_LldpNeighbors: lldpctl_get_interfaces failed (%hs)"),
            lldpctl_last_strerror(conn));
      lldpctl_release(conn);
      return SYSINFO_RC_ERROR;
   }

   table->addColumn(_T("LOCAL_IF_INDEX"), DCI_DT_UINT, _T("Local Interface Index"), true);
   table->addColumn(_T("LOCAL_IF_NAME"), DCI_DT_STRING, _T("Local Interface Name"), true);
   table->addColumn(_T("NEIGHBOR_INDEX"), DCI_DT_UINT, _T("Neighbor Index"), true);
   table->addColumn(_T("CHASSIS_ID_SUBTYPE"), DCI_DT_UINT, _T("Chassis ID Subtype"));
   table->addColumn(_T("CHASSIS_ID"), DCI_DT_STRING, _T("Chassis ID"));
   table->addColumn(_T("SYS_NAME"), DCI_DT_STRING, _T("System Name"));
   table->addColumn(_T("SYS_DESCRIPTION"), DCI_DT_STRING, _T("System Description"));
   table->addColumn(_T("PORT_ID_SUBTYPE"), DCI_DT_UINT, _T("Port ID Subtype"));
   table->addColumn(_T("PORT_ID"), DCI_DT_STRING, _T("Port ID"));
   table->addColumn(_T("PORT_DESCRIPTION"), DCI_DT_STRING, _T("Port Description"));

   lldpctl_atom_t *iface;
   lldpctl_atom_foreach(interfaces, iface)
   {
      lldpctl_atom_t *port = lldpctl_get_port(iface);
      if (port == nullptr)
         continue;

      const char *ifName = lldpctl_atom_get_str(port, lldpctl_k_port_name);
      uint32_t ifIndex = static_cast<uint32_t>(lldpctl_atom_get_int(port, lldpctl_k_port_index));

      lldpctl_atom_t *neighbors = lldpctl_atom_get(port, lldpctl_k_port_neighbors);
      if (neighbors != nullptr)
      {
         uint32_t neighborIndex = 0;
         lldpctl_atom_t *neighbor;
         lldpctl_atom_foreach(neighbors, neighbor)
         {
            table->addRow();
            table->set(0, ifIndex);
            SetUTF8Cell(table, 1, ifName);
            table->set(2, neighborIndex);

            lldpctl_atom_t *chassis = lldpctl_atom_get(neighbor, lldpctl_k_port_chassis);
            if (chassis != nullptr)
            {
               table->set(3, static_cast<uint32_t>(lldpctl_atom_get_int(chassis, lldpctl_k_chassis_id_subtype)));
               SetUTF8Cell(table, 4, lldpctl_atom_get_str(chassis, lldpctl_k_chassis_id));
               SetUTF8Cell(table, 5, lldpctl_atom_get_str(chassis, lldpctl_k_chassis_name));
               SetUTF8Cell(table, 6, lldpctl_atom_get_str(chassis, lldpctl_k_chassis_descr));
               lldpctl_atom_dec_ref(chassis);
            }

            table->set(7, static_cast<uint32_t>(lldpctl_atom_get_int(neighbor, lldpctl_k_port_id_subtype)));
            SetUTF8Cell(table, 8, lldpctl_atom_get_str(neighbor, lldpctl_k_port_id));
            SetUTF8Cell(table, 9, lldpctl_atom_get_str(neighbor, lldpctl_k_port_descr));

            neighborIndex++;
         }
         lldpctl_atom_dec_ref(neighbors);
      }

      lldpctl_atom_dec_ref(port);
   }

   lldpctl_atom_dec_ref(interfaces);
   lldpctl_release(conn);
   return SYSINFO_RC_SUCCESS;
}

/**
 * Subagent tables
 */
static NETXMS_SUBAGENT_TABLE s_tables[] =
{
   { _T("LLDP.Neighbors"), H_LldpNeighbors, nullptr, _T("LOCAL_IF_INDEX,NEIGHBOR_INDEX"), DCTDESC_LLDP_NEIGHBORS }
};

/**
 * Subagent information
 */
static NETXMS_SUBAGENT_INFO s_info =
{
   NETXMS_SUBAGENT_INFO_MAGIC,
   _T("LLDPD"), NETXMS_VERSION_STRING,
   nullptr,                   // init
   nullptr,                   // shutdown
   nullptr,                   // command handler
   nullptr,                   // notify
   nullptr,                   // metric filter
   0, nullptr,                // parameters
   0, nullptr,                // lists
   sizeof(s_tables) / sizeof(NETXMS_SUBAGENT_TABLE),
   s_tables,
   0, nullptr,                // actions
   0, nullptr                 // push parameters
};

/**
 * Entry point for NetXMS agent
 */
DECLARE_SUBAGENT_ENTRY_POINT(LLDPD)
{
   *ppInfo = &s_info;
   return true;
}
