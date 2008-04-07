/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jerome Negre              - implementation
 *******************************************************************************/

package com.vectrace.MercurialEclipse.team;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.team.core.TeamException;

import com.vectrace.MercurialEclipse.commands.HgLogClient;
import com.vectrace.MercurialEclipse.commands.HgTagClient;
import com.vectrace.MercurialEclipse.dialogs.RevisionChooserDialog;

/**
 * @author Jerome Negre <jerome+hg@jnegre.org>
 * 
 */
public class CompareWithAction extends CompareAction {

	@Override
	public void run(IFile file) throws TeamException {
		RevisionChooserDialog dialog = new RevisionChooserDialog(
				getShell(),
				"Compare With Revision...",
				HgLogClient.getRevisions(file),
				HgTagClient.getTags(file.getProject()));
		int result = dialog.open();
		if(result == IDialogConstants.OK_ID) {
			openEditor(file, dialog.getRevision());
		}
	}

}
