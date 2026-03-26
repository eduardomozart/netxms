/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2026 Victor Kirhenshtein
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
package org.netxms.nxmc.modules.snmp.widgets;

import java.util.function.Consumer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.netxms.client.snmp.MibObject;
import org.netxms.nxmc.base.widgets.LabeledText;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.snmp.shared.MibCache;
import org.netxms.nxmc.modules.snmp.widgets.helpers.MibObjectComparator;
import org.netxms.nxmc.modules.snmp.widgets.helpers.MibTreeContentProvider;
import org.netxms.nxmc.modules.snmp.widgets.helpers.MibTreeLabelProvider;
import org.netxms.nxmc.modules.snmp.widgets.helpers.MibTreeNavigator;
import org.netxms.nxmc.modules.snmp.widgets.helpers.MibTreeNavigator.SearchResult;
import org.netxms.nxmc.resources.SharedIcons;
import org.xnap.commons.i18n.I18n;

/**
 * MIB browser widget
 */
public class MibBrowser extends Composite
{
   private I18n i18n = LocalizationHelper.getI18n(MibBrowser.class);

   private Composite searchBar;
   private LabeledText queryEditor;
   private Button previousButton;
   private Button nextButton;
   private TreeViewer mibTree;
   private MibTreeNavigator navigator;
   private Consumer<String> messageHandler;

   /**
    * Create new MIB tree browser
    *
    * @param parent parent composite
    * @param style SWT style
    */
   public MibBrowser(Composite parent, int style)
   {
      super(parent, style);

      setLayout(new FormLayout());

      searchBar = new Composite(this, SWT.NONE);
      GridLayout gridLayout = new GridLayout();
      gridLayout.numColumns = 3;
      searchBar.setLayout(gridLayout);

      FormData fd = new FormData();
      fd.left = new FormAttachment(0, 0);
      fd.top = new FormAttachment(0, 0);
      fd.right = new FormAttachment(100, 0);
      searchBar.setLayoutData(fd);

      queryEditor = new LabeledText(searchBar, SWT.NONE);
      queryEditor.setLabel(i18n.tr("Search string"));
      queryEditor.getTextControl().addKeyListener(new KeyListener() {
         @Override
         public void keyReleased(KeyEvent e)
         {
         }

         @Override
         public void keyPressed(KeyEvent e)
         {
            if (e.stateMask == 0)
            {
               if (e.keyCode == SWT.ARROW_DOWN || e.keyCode == 13)
               {
                  findObject(true);
               }
               else if (e.keyCode == SWT.ARROW_UP)
               {
                  findObject(false);
               }
            }
         }
      });
      queryEditor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

      previousButton = new Button(searchBar, SWT.PUSH);
      previousButton.setImage(SharedIcons.IMG_UP);
      previousButton.setToolTipText(i18n.tr("Find previous (Arrow Up)"));
      previousButton.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            findObject(false);
         }
      });
      previousButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false));

      nextButton = new Button(searchBar, SWT.PUSH);
      nextButton.setImage(SharedIcons.IMG_DOWN);
      nextButton.setToolTipText(i18n.tr("Find next (Arrow Down)"));
      nextButton.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            findObject(true);
         }
      });
      nextButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false));

      mibTree = new TreeViewer(this, SWT.NONE);
      mibTree.setContentProvider(new MibTreeContentProvider());
      mibTree.setLabelProvider(new MibTreeLabelProvider());
      mibTree.setComparator(new MibObjectComparator());
      mibTree.setInput(MibCache.getMibTree());

      fd = new FormData();
      fd.left = new FormAttachment(0, 0);
      fd.top = new FormAttachment(0, 0);
      fd.right = new FormAttachment(100, 0);
      fd.bottom = new FormAttachment(100, 0);
      mibTree.getTree().setLayoutData(fd);

      navigator = new MibTreeNavigator(MibCache.getMibTree());

      // Initially hide search bar
      searchBar.setVisible(false);
      previousButton.setEnabled(false);
      nextButton.setEnabled(false);
      queryEditor.setEnabled(false);
   }

   /**
    * Find object in MIB tree based on current search text.
    *
    * @param forward true to search forward, false to search backward
    */
   private void findObject(boolean forward)
   {
      String text = queryEditor.getText().trim();
      if (text.isEmpty())
         return;

      SearchResult result = navigator.find(text, forward, getSelection());
      if (result.getObject() != null)
      {
         mibTree.setSelection(new StructuredSelection(result.getObject()), true);
         if (result.isWrapped() && messageHandler != null)
         {
            if (forward)
               messageHandler.accept(i18n.tr("Search continued from the beginning of the tree."));
            else
               messageHandler.accept(i18n.tr("Search continued from the end of the tree."));
         }
      }
      else
      {
         if (messageHandler != null)
            messageHandler.accept(i18n.tr("No matching objects found in MIB tree."));
      }
   }

   /**
    * Show or hide search bar.
    *
    * @param visible true to show search bar
    */
   public void showSearchBar(boolean visible)
   {
      if (visible)
      {
         searchBar.setVisible(true);
         previousButton.setEnabled(true);
         nextButton.setEnabled(true);
         queryEditor.setEnabled(true);
         queryEditor.setFocus();

         FormData fd = new FormData();
         fd.left = new FormAttachment(0, 0);
         fd.top = new FormAttachment(searchBar);
         fd.right = new FormAttachment(100, 0);
         fd.bottom = new FormAttachment(100, 0);
         mibTree.getTree().setLayoutData(fd);
      }
      else
      {
         previousButton.setEnabled(false);
         nextButton.setEnabled(false);
         queryEditor.setEnabled(false);
         searchBar.setVisible(false);

         FormData fd = new FormData();
         fd.left = new FormAttachment(0, 0);
         fd.top = new FormAttachment(0, 0);
         fd.right = new FormAttachment(100, 0);
         fd.bottom = new FormAttachment(100, 0);
         mibTree.getTree().setLayoutData(fd);
      }
      layout(true, true);
   }

   /**
    * Check if search bar is currently visible.
    *
    * @return true if search bar is visible
    */
   public boolean isSearchBarVisible()
   {
      return searchBar.getVisible();
   }

   /**
    * Set message handler for search notifications (not found, wrapped around, etc.).
    *
    * @param handler message handler
    */
   public void setMessageHandler(Consumer<String> handler)
   {
      this.messageHandler = handler;
   }

   /**
    * Get currently selected MIB object.
    *
    * @return currently selected MIB object or null if there are no selection
    */
   public MibObject getSelection()
   {
      IStructuredSelection selection = mibTree.getStructuredSelection();
      return (MibObject)selection.getFirstElement();
   }

   /**
    * Add tree selection listener.
    *
    * @param listener Listener
    */
   public void addSelectionChangedListener(ISelectionChangedListener listener)
   {
      mibTree.addSelectionChangedListener(listener);
   }

   /**
    * Set current selection
    * @param object object to be selected
    */
   public void setSelection(MibObject object)
   {
      IStructuredSelection selection = mibTree.getStructuredSelection();
      if ((selection.size() == 1) && object.equals(selection.getFirstElement()))
         return; // Same object already selected
      mibTree.setSelection(new StructuredSelection(object), true);
   }

   /**
    * Refresh MIB tree
    */
   public void refreshTree()
   {
      mibTree.setInput(MibCache.getMibTree());
      navigator.setMibTree(MibCache.getMibTree());
   }

   /**
    * Get underlying tree control
    *
    * @return tree control
    */
   public Tree getTreeControl()
   {
      return mibTree.getTree();
   }

   /**
    * Get underlying tree viewer
    *
    * @return tree viewer
    */
   public TreeViewer getTreeViewer()
   {
      return mibTree;
   }
}
