/**
 * NetXMS - open source network management system
 * Copyright (C) 2003-2024 Raden Solutions
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
package org.netxms.nxmc.modules.users.dialogs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.netxms.nxmc.base.widgets.LabeledText;
import org.netxms.nxmc.localization.LocalizationHelper;
import org.netxms.nxmc.tools.MessageDialogHelper;
import org.netxms.nxmc.tools.WidgetHelper;
import org.xnap.commons.i18n.I18n;

/**
 * Edit custom attribute
 */
public class CustomAttributeEditDialog extends Dialog
{
   private I18n i18n = LocalizationHelper.getI18n(CustomAttributeEditDialog.class);

   private LabeledText nameText;
   private LabeledText valueText;
   private String name;
   private String value;

   /**
    * Dialog for issuing authentication tokens.
    *
    * @param parentShell parent shell
    * @param name existing name
    * @param value existing value
    */
   public CustomAttributeEditDialog(Shell parentShell, String name, String value)
   {
      super(parentShell);
      this.name = name;
      this.value = value;
   }

   /**
    * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
    */
   @Override
   protected void configureShell(Shell newShell)
   {
      super.configureShell(newShell);
      newShell.setText((name != null) ? i18n.tr("Edit Custom Attribute") : i18n.tr("Add Custom Attribute"));
   }

   /**
    * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
    */
   @Override
   protected Control createDialogArea(Composite parent)
   {
      Composite dialogArea = (Composite)super.createDialogArea(parent);

      final GridLayout layout = new GridLayout();

      layout.marginWidth = WidgetHelper.DIALOG_WIDTH_MARGIN;
      layout.marginHeight = WidgetHelper.DIALOG_HEIGHT_MARGIN;
      dialogArea.setLayout(layout);

      nameText = new LabeledText(dialogArea, SWT.NONE);
      nameText.setLabel(i18n.tr("Name"));
      GridData gd = new GridData();
      gd.grabExcessHorizontalSpace = true;
      gd.horizontalAlignment = SWT.FILL;
      gd.widthHint = 400;
      nameText.setLayoutData(gd);
      if (name != null)
         nameText.setText(name);

      boolean isPreferences = ".nxmc.preferences".equals(name);
      valueText = new LabeledText(dialogArea, SWT.NONE,
            isPreferences ? SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL : SWT.BORDER | SWT.SINGLE);
      valueText.setLabel(i18n.tr("Value"));
      gd = new GridData();
      gd.grabExcessHorizontalSpace = true;
      gd.horizontalAlignment = SWT.FILL;
      if (isPreferences)
      {
         gd.grabExcessVerticalSpace = true;
         gd.verticalAlignment = SWT.FILL;
         gd.heightHint = 300;
      }
      valueText.setLayoutData(gd);
      if (value != null)
      {
         if (isPreferences)
         {
            try
            {
               valueText.setText(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
            }
            catch(Exception e)
            {
               valueText.setText("");
            }
         }
         else
         {
            valueText.setText(value);
         }
      }

      return dialogArea;
   }

   /**
    * @see org.eclipse.jface.dialogs.Dialog#okPressed()
    */
   @Override
   protected void okPressed()
   {
      name = nameText.getText().trim();
      if (name.isEmpty())
      {
         MessageDialogHelper.openWarning(getShell(), i18n.tr("Warning"), i18n.tr("Custom attribute name cannot be empty"));
         return;
      }
      value = ".nxmc.preferences".equals(name)
            ? Base64.getEncoder().encodeToString(valueText.getText().getBytes(StandardCharsets.UTF_8))
            : valueText.getText();
      super.okPressed();
   }

   /**
    * @return the name
    */
   public String getName()
   {
      return name;
   }

   /**
    * @return the value
    */
   public String getValue()
   {
      return value;
   }
}
