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
package org.netxms.nxmc.modules.objects.propertypages;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.netxms.client.NXCObjectModificationData;
import org.netxms.client.NXCSession;
import org.netxms.client.objects.AbstractNode;
import org.netxms.client.objects.AbstractObject;
import org.netxms.client.snmp.SnmpVersion;
import org.netxms.nxmc.Registry;
import org.netxms.nxmc.base.jobs.Job;
import org.netxms.nxmc.base.widgets.LabeledCombo;
import org.netxms.nxmc.base.widgets.LabeledText;
import org.netxms.nxmc.base.widgets.PasswordInputField;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.modules.objects.ObjectSelectionFilterFactory;
import org.netxms.nxmc.modules.objects.widgets.ObjectSelector;
import org.netxms.nxmc.tools.WidgetHelper;
import org.xnap.commons.i18n.I18n;

/**
 * "SNMP" property page for node
 */
public class SNMP extends ObjectPropertyPage
{
   private I18n i18n = LocalizationHelper.getI18n(SNMP.class);

   private AbstractNode node;
   private LabeledCombo snmpVersion;
   private LabeledText snmpPort;
   private LabeledCombo snmpAuth;
   private LabeledCombo snmpPriv;
   private ObjectSelector snmpProxy;
   private PasswordInputField snmpAuthName;
   private PasswordInputField snmpAuthPassword;
   private PasswordInputField snmpPrivPassword;
   private LabeledText snmpCodepage;
   private Button snmpSettingsLocked;
   private Button useSeparateTrapCredentials;
   private LabeledCombo trapSnmpVersion;
   private LabeledCombo trapSnmpAuth;
   private LabeledCombo trapSnmpPriv;
   private PasswordInputField trapSnmpAuthName;
   private PasswordInputField trapSnmpAuthPassword;
   private PasswordInputField trapSnmpPrivPassword;

   /**
    * Create new page.
    *
    * @param object object to edit
    */
   public SNMP(AbstractObject object)
   {
      super(LocalizationHelper.getI18n(SNMP.class).tr("SNMP"), object);
   }

   /**
    * @see org.netxms.nxmc.modules.objects.propertypages.ObjectPropertyPage#getId()
    */
   @Override
   public String getId()
   {
      return "communication.snmp";
   }

   /**
    * @see org.netxms.nxmc.modules.objects.propertypages.ObjectPropertyPage#getParentId()
    */
   @Override
   public String getParentId()
   {
      return "communication";
   }

   /**
    * @see org.netxms.nxmc.modules.objects.propertypages.ObjectPropertyPage#isVisible()
    */
   @Override
   public boolean isVisible()
   {
      return object instanceof AbstractNode;
   }

   /**
    * @see org.eclipse.jface.preference.PreferencePage#createContents(org.eclipse.swt.widgets.Composite)
    */
   @Override
   protected Control createContents(Composite parent)
   {
      node = (AbstractNode)object;

      Composite dialogArea = new Composite(parent, SWT.NONE);
      GridLayout dialogLayout = new GridLayout();
      dialogLayout.marginWidth = 0;
      dialogLayout.marginHeight = 0;
      dialogLayout.horizontalSpacing = WidgetHelper.DIALOG_SPACING;
      dialogLayout.verticalSpacing = WidgetHelper.DIALOG_SPACING;
      dialogLayout.numColumns = 4;
      dialogArea.setLayout(dialogLayout);

      snmpVersion = new LabeledCombo(dialogArea, SWT.NONE, SWT.BORDER | SWT.READ_ONLY);
      snmpVersion.setLabel(i18n.tr("Version"));
      snmpVersion.add("1");
      snmpVersion.add("2c");
      snmpVersion.add("3");
      snmpVersion.select(snmpVersionToIndex(node.getSnmpVersion()));
      snmpVersion.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
      snmpVersion.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            onSnmpVersionChange();
         }
      });

      snmpPort = new LabeledText(dialogArea, SWT.NONE);
      snmpPort.setLabel(i18n.tr("UDP Port"));
      snmpPort.setText(Integer.toString(node.getSnmpPort()));
      snmpPort.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

      snmpAuthName = new PasswordInputField(dialogArea, SWT.NONE);
      snmpAuthName.setLabel((node.getSnmpVersion() == SnmpVersion.V3) ? i18n.tr("User name") : i18n.tr("Community string"));
      snmpAuthName.setText(node.getSnmpAuthName());
      snmpAuthName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

      snmpAuth = new LabeledCombo(dialogArea, SWT.NONE, SWT.BORDER | SWT.READ_ONLY);
      snmpAuth.setLabel(i18n.tr("Authentication"));
      snmpAuth.add(i18n.tr("NONE"));
      snmpAuth.add(i18n.tr("MD5"));
      snmpAuth.add(i18n.tr("SHA1"));
      snmpAuth.add("SHA224");
      snmpAuth.add("SHA256");
      snmpAuth.add("SHA384");
      snmpAuth.add("SHA512");
      snmpAuth.select(node.getSnmpAuthMethod());
      snmpAuth.setEnabled(node.getSnmpVersion() == SnmpVersion.V3);
      snmpAuth.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

      snmpAuthPassword = new PasswordInputField(dialogArea, SWT.NONE);
      snmpAuthPassword.setLabel(i18n.tr("Authentication password"));
      snmpAuthPassword.setText(node.getSnmpAuthPassword());
      snmpAuthPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
      snmpAuthPassword.setInputControlsEnabled(node.getSnmpVersion() == SnmpVersion.V3);

      snmpPriv = new LabeledCombo(dialogArea, SWT.NONE, SWT.BORDER | SWT.READ_ONLY);
      snmpPriv.setLabel(i18n.tr("Encryption"));
      snmpPriv.add(i18n.tr("NONE"));
      snmpPriv.add("DES");
      snmpPriv.add("AES-128");
      snmpPriv.add("AES-192");
      snmpPriv.add("AES-256");
      snmpPriv.select(node.getSnmpPrivMethod());
      snmpPriv.setEnabled(node.getSnmpVersion() == SnmpVersion.V3);
      snmpPriv.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

      snmpPrivPassword = new PasswordInputField(dialogArea, SWT.NONE);
      snmpPrivPassword.setLabel(i18n.tr("Encryption password"));
      snmpPrivPassword.setText(node.getSnmpPrivPassword());
      snmpPrivPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
      snmpPrivPassword.setInputControlsEnabled(node.getSnmpVersion() == SnmpVersion.V3);

      snmpProxy = new ObjectSelector(dialogArea, SWT.NONE, true);
      snmpProxy.setLabel(i18n.tr("Proxy"));
      snmpProxy.setClassFilter(ObjectSelectionFilterFactory.getInstance().createNodeSelectionFilter(false));
      snmpProxy.setObjectId(node.getSnmpProxyId());
      snmpProxy.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

      snmpCodepage = new LabeledText(dialogArea, SWT.NONE);
      snmpCodepage.setLabel(i18n.tr("Codepage"));
      snmpCodepage.setText(node.getSNMPCodepage());
      snmpCodepage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

      snmpSettingsLocked = new Button(dialogArea, SWT.CHECK);
      snmpSettingsLocked.setText(i18n.tr("&Prevent automatic SNMP configuration changes"));
      snmpSettingsLocked.setSelection(node.isSnmpSettingsLocked());
      snmpSettingsLocked.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, dialogLayout.numColumns, 1));

      useSeparateTrapCredentials = new Button(dialogArea, SWT.CHECK);
      useSeparateTrapCredentials.setText(i18n.tr("Use separate credentials for SNMP trap reception"));
      useSeparateTrapCredentials.setSelection(node.hasSnmpTrapCredentials());
      useSeparateTrapCredentials.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, dialogLayout.numColumns, 1));
      useSeparateTrapCredentials.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            onTrapCredentialCheckChange();
         }
      });

      trapSnmpVersion = new LabeledCombo(dialogArea, SWT.NONE, SWT.BORDER | SWT.READ_ONLY);
      trapSnmpVersion.setLabel(i18n.tr("Version"));
      trapSnmpVersion.add("1");
      trapSnmpVersion.add("2c");
      trapSnmpVersion.add("3");
      trapSnmpVersion.select(node.hasSnmpTrapCredentials() ? snmpVersionToIndex(node.getSnmpTrapVersion()) : 1);
      trapSnmpVersion.setEnabled(node.hasSnmpTrapCredentials());
      trapSnmpVersion.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
      trapSnmpVersion.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e)
         {
            onTrapSnmpVersionChange();
         }
      });

      trapSnmpAuthName = new PasswordInputField(dialogArea, SWT.NONE);
      boolean trapIsV3 = node.hasSnmpTrapCredentials() && node.getSnmpTrapVersion() == SnmpVersion.V3;
      trapSnmpAuthName.setLabel(trapIsV3 ? i18n.tr("Trap user name") : i18n.tr("Trap community string"));
      trapSnmpAuthName.setText(node.hasSnmpTrapCredentials() ? node.getSnmpTrapAuthName() : "");
      trapSnmpAuthName.setEnabled(node.hasSnmpTrapCredentials());
      trapSnmpAuthName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

      trapSnmpAuth = new LabeledCombo(dialogArea, SWT.NONE, SWT.BORDER | SWT.READ_ONLY);
      trapSnmpAuth.setLabel(i18n.tr("Authentication"));
      trapSnmpAuth.add(i18n.tr("NONE"));
      trapSnmpAuth.add(i18n.tr("MD5"));
      trapSnmpAuth.add(i18n.tr("SHA1"));
      trapSnmpAuth.add("SHA224");
      trapSnmpAuth.add("SHA256");
      trapSnmpAuth.add("SHA384");
      trapSnmpAuth.add("SHA512");
      trapSnmpAuth.select(node.hasSnmpTrapCredentials() ? node.getSnmpTrapAuthMethod() : 0);
      trapSnmpAuth.setEnabled(node.hasSnmpTrapCredentials() && trapIsV3);
      trapSnmpAuth.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

      trapSnmpAuthPassword = new PasswordInputField(dialogArea, SWT.NONE);
      trapSnmpAuthPassword.setLabel(i18n.tr("Trap authentication password"));
      trapSnmpAuthPassword.setText(node.hasSnmpTrapCredentials() && node.getSnmpTrapAuthPassword() != null ? node.getSnmpTrapAuthPassword() : "");
      trapSnmpAuthPassword.setEnabled(node.hasSnmpTrapCredentials());
      trapSnmpAuthPassword.setInputControlsEnabled(node.hasSnmpTrapCredentials() && trapIsV3);
      trapSnmpAuthPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

      trapSnmpPriv = new LabeledCombo(dialogArea, SWT.NONE, SWT.BORDER | SWT.READ_ONLY);
      trapSnmpPriv.setLabel(i18n.tr("Encryption"));
      trapSnmpPriv.add(i18n.tr("NONE"));
      trapSnmpPriv.add("DES");
      trapSnmpPriv.add("AES-128");
      trapSnmpPriv.add("AES-192");
      trapSnmpPriv.add("AES-256");
      trapSnmpPriv.select(node.hasSnmpTrapCredentials() ? node.getSnmpTrapPrivMethod() : 0);
      trapSnmpPriv.setEnabled(node.hasSnmpTrapCredentials() && trapIsV3);
      trapSnmpPriv.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

      trapSnmpPrivPassword = new PasswordInputField(dialogArea, SWT.NONE);
      trapSnmpPrivPassword.setLabel(i18n.tr("Trap encryption password"));
      trapSnmpPrivPassword.setText(node.hasSnmpTrapCredentials() && node.getSnmpTrapPrivPassword() != null ? node.getSnmpTrapPrivPassword() : "");
      trapSnmpPrivPassword.setEnabled(node.hasSnmpTrapCredentials());
      trapSnmpPrivPassword.setInputControlsEnabled(node.hasSnmpTrapCredentials() && trapIsV3);
      trapSnmpPrivPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

      return dialogArea;
   }

   /**
    * Convert SNMP version to index in combo box
    * 
    * @param version SNMP version
    * @return index in combo box
    */
   private int snmpVersionToIndex(SnmpVersion version)
   {
      switch(version)
      {
         case V1:
            return 0;
         case V2C:
            return 1;
         case V3:
            return 2;
         default:
            return 0;
      }
   }
   
   /**
    * Convert selection index in SNMP version combo box to SNMP version
    */
   private SnmpVersion snmpIndexToVersion(int index)
   {
      final SnmpVersion[] versions = { SnmpVersion.V1, SnmpVersion.V2C, SnmpVersion.V3 };
      return versions[index];
   }
   
   /**
    * Handler for SNMP version change
    */
   private void onSnmpVersionChange()
   {
      boolean isV3 = (snmpVersion.getSelectionIndex() == 2);
      snmpAuthName.setLabel(isV3 ? i18n.tr("User name") : i18n.tr("Community string"));
      snmpAuth.setEnabled(isV3);
      snmpPriv.setEnabled(isV3);
      snmpAuthPassword.setInputControlsEnabled(isV3);
      snmpPrivPassword.setInputControlsEnabled(isV3);
   }

   /**
    * Handler for trap credential checkbox change
    */
   private void onTrapCredentialCheckChange()
   {
      boolean enabled = useSeparateTrapCredentials.getSelection();
      trapSnmpVersion.setEnabled(enabled);
      trapSnmpAuthName.setEnabled(enabled);
      trapSnmpAuthPassword.setEnabled(enabled);
      trapSnmpPrivPassword.setEnabled(enabled);
      if (enabled)
      {
         onTrapSnmpVersionChange();
      }
      else
      {
         trapSnmpAuth.setEnabled(false);
         trapSnmpPriv.setEnabled(false);
         trapSnmpAuthPassword.setInputControlsEnabled(false);
         trapSnmpPrivPassword.setInputControlsEnabled(false);
      }
   }

   /**
    * Handler for trap SNMP version change
    */
   private void onTrapSnmpVersionChange()
   {
      boolean isV3 = (trapSnmpVersion.getSelectionIndex() == 2);
      trapSnmpAuthName.setLabel(isV3 ? i18n.tr("Trap user name") : i18n.tr("Trap community string"));
      trapSnmpAuth.setEnabled(isV3);
      trapSnmpPriv.setEnabled(isV3);
      trapSnmpAuthPassword.setInputControlsEnabled(isV3);
      trapSnmpPrivPassword.setInputControlsEnabled(isV3);
   }

   /**
    * @see org.netxms.nxmc.modules.objects.propertypages.ObjectPropertyPage#applyChanges(boolean)
    */
   @Override
   protected boolean applyChanges(final boolean isApply)
   {
      final NXCObjectModificationData md = new NXCObjectModificationData(node.getObjectId());

      if (isApply)
         setValid(false);

      md.setSnmpVersion(snmpIndexToVersion(snmpVersion.getSelectionIndex()));
      try
      {
         md.setSnmpPort(Integer.parseInt(snmpPort.getText(), 10));
      }
      catch(NumberFormatException e)
      {
         MessageDialog.openWarning(getShell(), i18n.tr("Warning"), i18n.tr("Please enter valid SNMP port number"));
         if (isApply)
            setValid(true);
         return false;
      }
      md.setSnmpProxy(snmpProxy.getObjectId());
      md.setSnmpAuthentication(snmpAuthName.getText(), snmpAuth.getSelectionIndex(), snmpAuthPassword.getText(), snmpPriv.getSelectionIndex(), snmpPrivPassword.getText());
      md.setSNMPCodepage(snmpCodepage.getText());

      if (useSeparateTrapCredentials.getSelection())
      {
         md.setSnmpTrapVersion(snmpIndexToVersion(trapSnmpVersion.getSelectionIndex()));
         md.setSnmpTrapAuthentication(trapSnmpAuthName.getText(), trapSnmpAuth.getSelectionIndex(),
               trapSnmpAuthPassword.getText(), trapSnmpPriv.getSelectionIndex(), trapSnmpPrivPassword.getText());
      }
      else
      {
         md.clearSnmpTrapAuthentication();
      }

      int flags = node.getFlags();
      if (snmpSettingsLocked.getSelection())
         flags |= AbstractNode.NF_SNMP_SETTINGS_LOCKED;
      else
         flags &= ~AbstractNode.NF_SNMP_SETTINGS_LOCKED;
      md.setObjectFlags(flags, AbstractNode.NF_SNMP_SETTINGS_LOCKED);

      final NXCSession session = Registry.getSession();
      new Job(i18n.tr("Updating SNMP settings for node {0}", node.getObjectName()), null, messageArea) {
         @Override
         protected void run(IProgressMonitor monitor) throws Exception
         {
            session.modifyObject(md);
         }

         @Override
         protected String getErrorMessage()
         {
            return i18n.tr("Cannot update SNMP settings for node {0}", node.getObjectName());
         }

         @Override
         protected void jobFinalize()
         {
            if (isApply)
               runInUIThread(() -> SNMP.this.setValid(true));
         }
      }.start();
      return true;
   }

   /**
    * @see org.eclipse.jface.preference.PreferencePage#performDefaults()
    */
   @Override
   protected void performDefaults()
   {
      super.performDefaults();
      
      snmpVersion.select(0);
      snmpAuth.select(0);
      snmpPriv.select(0);
      snmpAuthName.setText("public");
      snmpAuthPassword.setText("");
      snmpPrivPassword.setText("");
      snmpProxy.setObjectId(0);
      onSnmpVersionChange();
      snmpCodepage.setText("");
      useSeparateTrapCredentials.setSelection(false);
      trapSnmpVersion.select(1);
      trapSnmpAuth.select(0);
      trapSnmpPriv.select(0);
      trapSnmpAuthName.setText("");
      trapSnmpAuthPassword.setText("");
      trapSnmpPrivPassword.setText("");
      onTrapCredentialCheckChange();
   }
}
