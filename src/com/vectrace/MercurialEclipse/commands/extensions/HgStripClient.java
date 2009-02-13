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
package com.vectrace.MercurialEclipse.commands.extensions;

import org.eclipse.core.resources.IProject;

import com.vectrace.MercurialEclipse.commands.HgCommand;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;

/**
 * Calls hg strip
 * 
 * @author bastian
 * 
 */
public class HgStripClient {
    /**
     * strip a revision and all later revs on the same branch
     * 
     * @param proj
     * @param backup
     * @param changeset
     * @return
     * @throws HgException
     */
    public static String strip(IProject proj, boolean saveUnrelated,
            boolean backup, boolean stripHeads, ChangeSet changeset)
            throws HgException {
        HgCommand command = new HgCommand("strip", proj, true); //$NON-NLS-1$
        command
                .setUsePreferenceTimeout(MercurialPreferenceConstants.COMMIT_TIMEOUT);
        
        command.addOptions("--config", "extensions.hgext.mq="); //$NON-NLS-1$ //$NON-NLS-2$
        
        if (saveUnrelated) {
            command.addOptions("--backup"); //$NON-NLS-1$
        }
        if (!backup) {
            command.addOptions("--nobackup"); //$NON-NLS-1$
        }
        if (stripHeads) {
            command.addOptions("-f"); //$NON-NLS-1$
        }
        command.addOptions(changeset.getChangeset());
        return command.executeToString();
    }
}