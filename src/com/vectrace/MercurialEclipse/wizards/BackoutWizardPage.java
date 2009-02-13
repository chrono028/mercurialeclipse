/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Bastian Doetsch	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.wizards;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgBackoutClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.team.MercurialUtilities;
import com.vectrace.MercurialEclipse.ui.ChangesetTable;
import com.vectrace.MercurialEclipse.ui.SWTWidgetHelper;

/**
 * @author bastian
 * 
 */
public class BackoutWizardPage extends HgWizardPage {

    private ChangesetTable changesetTable;
    private Text messageTextField;
    private Button mergeCheckBox;
    protected ChangeSet backoutRevision;
    private IProject project;
    private Text userTextField;

    /**
     * @param string
     * @param string2
     * @param object
     * @param project
     */
    public BackoutWizardPage(String pageName, String title,
            ImageDescriptor image, IProject project) {
        super(pageName, title, image);
        this.project = project;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets
     * .Composite)
     */
    public void createControl(Composite parent) {
        Composite composite = SWTWidgetHelper.createComposite(parent, 2);

        // list view of changesets
        Group changeSetGroup = SWTWidgetHelper
                .createGroup(
                        composite,
                        Messages
                                .getString("BackoutWizardPage.changeSetGroup.title"), GridData.FILL_BOTH); //$NON-NLS-1$

        changesetTable = new ChangesetTable(changeSetGroup, project);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.heightHint = 200;
        gridData.minimumHeight = 50;
        changesetTable.setLayoutData(gridData);

        SelectionListener listener = new SelectionListener() {
            public void widgetSelected(SelectionEvent e) {
                backoutRevision = changesetTable.getSelection();
                messageTextField.setText(Messages.getString(
                        "BackoutWizardPage.defaultCommitMessage") //$NON-NLS-1$
                        .concat(backoutRevision.toString()));
                setPageComplete(true);
            }

            public void widgetDefaultSelected(SelectionEvent e) {
                widgetSelected(e);
            }

        };

        changesetTable.addSelectionListener(listener);
        changesetTable.setEnabled(true);

        // now the options
        Group optionGroup = SWTWidgetHelper.createGroup(composite, Messages
                .getString("BackoutWizardPage.optionGroup.title")); //$NON-NLS-1$

        SWTWidgetHelper.createLabel(optionGroup, Messages
                .getString("BackoutWizardPage.userLabel.text")); //$NON-NLS-1$
        this.userTextField = SWTWidgetHelper.createTextField(optionGroup);
        this.userTextField.setText(MercurialUtilities.getHGUsername());

        SWTWidgetHelper.createLabel(optionGroup, Messages
                .getString("BackoutWizardPage.commitLabel.text")); //$NON-NLS-1$
        this.messageTextField = SWTWidgetHelper.createTextField(optionGroup);

        // --merge merge with old dirstate parent after backout
        this.mergeCheckBox = SWTWidgetHelper.createCheckBox(optionGroup,
                Messages.getString("BackoutWizardPage.mergeCheckBox.text")); //$NON-NLS-1$
        this.mergeCheckBox.setSelection(true);

        
        setControl(composite);
    }

    @Override
    public boolean finish(IProgressMonitor monitor) {
        String msg = messageTextField.getText();
        boolean merge = mergeCheckBox.getSelection();
        backoutRevision = changesetTable.getSelection();
        try {           
            String result = HgBackoutClient.backout(project, backoutRevision,
                    merge, msg, userTextField.getText());
            MessageDialog.openInformation(getShell(), Messages
                    .getString("BackoutWizardPage.backoutOutput"), //$NON-NLS-1$
                    result);
        } catch (HgException e) {
            MessageDialog.openError(getShell(), Messages
                    .getString("BackoutWizardPage.backoutError"), e //$NON-NLS-1$
                    .getMessage());
            MercurialEclipsePlugin.logError(e);
            return false;
        }
        return true;
    }

}
