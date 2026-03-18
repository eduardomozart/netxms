/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2026 Raden Solutions
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.netxms.nxmc.modules.objects.views;

import java.util.Date;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.netxms.client.objects.AbstractObject;
import org.netxms.client.objects.Node;
import org.netxms.client.topology.ConnectionHistoryRecord;
import org.netxms.nxmc.base.actions.CopyTableCellsAction;
import org.netxms.nxmc.base.actions.CopyTableRowsAction;
import org.netxms.nxmc.base.actions.ExportToCsvAction;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.widgets.SortableTableViewer;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.objects.views.helpers.ConnectionHistoryComparator;
import org.netxms.nxmc.modules.objects.views.helpers.ConnectionHistoryFilter;
import org.netxms.nxmc.modules.objects.views.helpers.ConnectionHistoryLabelProvider;
import org.netxms.nxmc.resources.ResourceManager;
import org.netxms.nxmc.tools.WidgetHelper;
import org.xnap.commons.i18n.I18n;

/**
 * Connection history view
 */
public class ConnectionHistoryView extends ObjectView
{
   private final I18n i18n = LocalizationHelper.getI18n(ConnectionHistoryView.class);

   public static final int COLUMN_TIMESTAMP = 0;
   public static final int COLUMN_EVENT_TYPE = 1;
   public static final int COLUMN_MAC_ADDRESS = 2;
   public static final int COLUMN_IP_ADDRESS = 3;
   public static final int COLUMN_NODE = 4;
   public static final int COLUMN_SWITCH = 5;
   public static final int COLUMN_PORT = 6;
   public static final int COLUMN_VLAN = 7;

   private SortableTableViewer viewer;
   private boolean refreshPending = true;
   private Action actionExportToCsv;
   private Action actionExportAllToCsv;
   private Action actionCopyRowToClipboard;
   private Action actionCopyMACToClipboard;

   /**
    * Default constructor
    */
   public ConnectionHistoryView()
   {
      super(LocalizationHelper.getI18n(ConnectionHistoryView.class).tr("Connection History"), ResourceManager.getImageDescriptor("icons/object-views/fdb.gif"), "objects.conn-history", true);
   }

   /**
    * @see org.netxms.nxmc.modules.objects.views.ObjectView#isValidForContext(java.lang.Object)
    */
   @Override
   public boolean isValidForContext(Object context)
   {
      return (context != null) && (context instanceof Node) && ((Node)context).isBridge();
   }

   /**
    * @see org.netxms.nxmc.base.views.View#getPriority()
    */
   @Override
   public int getPriority()
   {
      return 151;
   }

   /**
    * @see org.netxms.nxmc.base.views.View#createContent(org.eclipse.swt.widgets.Composite)
    */
   @Override
   protected void createContent(Composite parent)
   {
      final String[] names = {
         i18n.tr("Timestamp"), i18n.tr("Event"), i18n.tr("MAC Address"), i18n.tr("IP Address"),
         i18n.tr("Node"), i18n.tr("Switch"), i18n.tr("Port"), i18n.tr("VLAN")
      };
      final int[] widths = { 160, 110, 180, 150, 200, 200, 200, 80 };
      viewer = new SortableTableViewer(parent, names, widths, COLUMN_TIMESTAMP, SWT.DOWN, SWT.FULL_SELECTION | SWT.MULTI);
      viewer.setContentProvider(new ArrayContentProvider());
      viewer.setLabelProvider(new ConnectionHistoryLabelProvider());
      viewer.setComparator(new ConnectionHistoryComparator());
      ConnectionHistoryFilter filter = new ConnectionHistoryFilter();
      setFilterClient(viewer, filter);
      viewer.addFilter(filter);

      viewer.enableColumnReordering();
      WidgetHelper.restoreTableViewerSettings(viewer, "ConnectionHistory");
      viewer.getTable().addDisposeListener(new DisposeListener() {
         @Override
         public void widgetDisposed(DisposeEvent e)
         {
            WidgetHelper.saveTableViewerSettings(viewer, "ConnectionHistory");
         }
      });

      createActions();
      createPopupMenu();
   }

   /**
    * Create actions
    */
   private void createActions()
   {
      actionCopyRowToClipboard = new CopyTableRowsAction(viewer, true);
      actionCopyMACToClipboard = new CopyTableCellsAction(viewer, COLUMN_MAC_ADDRESS, true, i18n.tr("Copy MAC address to clipboard"));
      actionExportToCsv = new ExportToCsvAction(this, viewer, true);
      actionExportAllToCsv = new ExportToCsvAction(this, viewer, false);
   }

   /**
    * Create pop-up menu
    */
   private void createPopupMenu()
   {
      MenuManager menuMgr = new MenuManager();
      menuMgr.setRemoveAllWhenShown(true);
      menuMgr.addMenuListener(new IMenuListener() {
         public void menuAboutToShow(IMenuManager mgr)
         {
            fillContextMenu(mgr);
         }
      });

      Menu menu = menuMgr.createContextMenu(viewer.getControl());
      viewer.getControl().setMenu(menu);
   }

   /**
    * Fill context menu
    */
   protected void fillContextMenu(IMenuManager manager)
   {
      manager.add(actionCopyRowToClipboard);
      manager.add(actionCopyMACToClipboard);
      manager.add(actionExportToCsv);
      manager.add(actionExportAllToCsv);
   }

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalToolBar(org.eclipse.jface.action.IToolBarManager)
    */
   @Override
   protected void fillLocalToolBar(IToolBarManager manager)
   {
      manager.add(actionExportAllToCsv);
   }

   /**
    * @see org.netxms.nxmc.base.views.View#fillLocalMenu(org.eclipse.jface.action.IMenuManager)
    */
   @Override
   protected void fillLocalMenu(IMenuManager manager)
   {
      Action resetAction = viewer.getResetColumnOrderAction();
      if (resetAction != null)
         manager.add(resetAction);
      Action showAllAction = viewer.getShowAllColumnsAction();
      if (showAllAction != null)
         manager.add(showAllAction);
      manager.add(actionExportAllToCsv);
   }

   /**
    * @see org.netxms.nxmc.base.views.View#refresh()
    */
   @Override
   public void refresh()
   {
      final long objectId = getObjectId();
      if (objectId == 0)
         return;

      new Job(i18n.tr("Reading connection history"), this) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            // Get last 30 days by default
            Date from = new Date(System.currentTimeMillis() - 30L * 86400000L);
            final List<ConnectionHistoryRecord> records = session.getConnectionHistory(null, objectId, 0, from, null, 5000);
            runInUIThread(() -> viewer.setInput(records.toArray()));
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot get connection history for node {0}", session.getObjectName(objectId));
         }
      }.start();
   }

   /**
    * @see org.netxms.nxmc.base.views.View#activate()
    */
   @Override
   public void activate()
   {
      super.activate();
      if (refreshPending)
      {
         refreshPending = false;
         refresh();
      }
   }

   /**
    * @see org.netxms.nxmc.modules.objects.views.ObjectView#onObjectChange(org.netxms.client.objects.AbstractObject)
    */
   @Override
   protected void onObjectChange(AbstractObject object)
   {
      if (isActive())
         refresh();
      else
         refreshPending = true;
   }
}
