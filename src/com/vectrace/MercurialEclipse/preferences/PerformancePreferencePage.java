/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gunnar Ahlberg            - implementation
 *     VecTrace (Zingo Andersen) - updateing it
 *     Jérôme Nègre              - adding label decorator section
 *     Stefan C                  - Code cleanup
 *******************************************************************************/

package com.vectrace.MercurialEclipse.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By sub classing <samp>FieldEditorPreferencePage</samp>,
 * we can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class PerformancePreferencePage extends FieldEditorPreferencePage
implements IWorkbenchPreferencePage {


    public PerformancePreferencePage() {
        super(GRID);
        setPreferenceStore(MercurialEclipsePlugin.getDefault()
                .getPreferenceStore());
        setDescription(Messages.getString("PerformancePreferencePage.description")); //$NON-NLS-1$
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    @Override
    public void createFieldEditors() {
        // batch size preferences

        IntegerFieldEditor batchLogRevisionEditor = new IntegerFieldEditor(
                MercurialPreferenceConstants.LOG_BATCH_SIZE,
                Messages.getString("PerformancePreferencePage.field.revisionLimit"), getFieldEditorParent()); //$NON-NLS-1$
        addField(batchLogRevisionEditor);
        batchLogRevisionEditor.setValidRange(1, Integer.MAX_VALUE);

        IntegerFieldEditor batchStatusSeditor = new IntegerFieldEditor(
                MercurialPreferenceConstants.STATUS_BATCH_SIZE,
                Messages.getString("PerformancePreferencePage.field.statusBatchSize"), //$NON-NLS-1$
                getFieldEditorParent());
        addField(batchStatusSeditor);
        batchStatusSeditor.setValidRange(1, Integer.MAX_VALUE);

        addField(new BooleanFieldEditor(
                MercurialPreferenceConstants.RESOURCE_DECORATOR_COMPLETE_STATUS,
                Messages.getString("PerformancePreferencePage.field.completeStatus"), //$NON-NLS-1$
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(
                MercurialPreferenceConstants.RESOURCE_DECORATOR_COMPUTE_DEEP_STATUS,
                Messages.getString("PerformancePreferencePage.field.computeDeep"), //$NON-NLS-1$
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(
                MercurialPreferenceConstants.RESOURCE_DECORATOR_SHOW_CHANGESET,
                Messages.getString("PerformancePreferencePage.field.showChangesetOnFiles"), //$NON-NLS-1$
                getFieldEditorParent()));

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench) {
    }

}