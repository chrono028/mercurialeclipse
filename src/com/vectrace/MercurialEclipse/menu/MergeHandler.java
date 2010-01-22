/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jerome Negre - implementation
 *     Andrei Loskutov (Intland) - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.menu;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgClients;
import com.vectrace.MercurialEclipse.commands.HgMergeClient;
import com.vectrace.MercurialEclipse.commands.HgResolveClient;
import com.vectrace.MercurialEclipse.commands.HgStatusClient;
import com.vectrace.MercurialEclipse.commands.RefreshWorkspaceStatusJob;
import com.vectrace.MercurialEclipse.commands.extensions.HgIMergeClient;
import com.vectrace.MercurialEclipse.dialogs.RevisionChooserDialog;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.Branch;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.FlaggedAdaptable;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.storage.DataLoader;
import com.vectrace.MercurialEclipse.storage.ProjectDataLoader;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;
import com.vectrace.MercurialEclipse.team.ResourceProperties;
import com.vectrace.MercurialEclipse.team.cache.LocalChangesetCache;
import com.vectrace.MercurialEclipse.team.cache.MercurialStatusCache;
import com.vectrace.MercurialEclipse.views.MergeView;

public class MergeHandler extends SingleResourceHandler {

	@Override
	protected void run(IResource resource) throws Exception {
		merge(resource.getProject(), getShell(), new NullProgressMonitor(), false, true);
	}

	public static String merge(IProject project, Shell shell, IProgressMonitor monitor,
			boolean autoPickOtherHead, boolean showCommitDialog) throws HgException, CoreException {
		DataLoader loader = new ProjectDataLoader(project);

		// can we do the equivalent of plain "hg merge"?
		ChangeSet cs = getOtherHeadInCurrentBranch(project, loader);
		boolean forced = false;

		String forceMessage = "Forced merge (this will discard all uncommitted changes!)";
		HgRoot hgRoot = MercurialTeamProvider.getHgRoot(project);
		boolean hasDirtyFiles = HgStatusClient.isDirty(hgRoot);
		if (cs != null) {
			if (!autoPickOtherHead) {

				String csSummary = "    Changeset: " + cs.getRevision().toString().substring(0, 20)
						+ "\n    User: " + cs.getUser() + "\n    Date: "
						+ cs.getDateString() + "\n    Summary: " + cs.getSummary();

				String branch = cs.getBranch();
				if (Branch.isDefault(branch)) {
					branch = Branch.DEFAULT;
				}
				String message = MessageFormat.format(Messages
						.getString("MergeHandler.mergeWithOtherHead"), branch, csSummary);

				if(hasDirtyFiles){
					MessageDialogWithToggle dialog = MessageDialogWithToggle.openYesNoQuestion(
							shell, "Merge", message, forceMessage, false, null, null);
					if(dialog.getReturnCode() != IDialogConstants.YES_ID) {
						cs = null;
					}
					forced = dialog.getToggleState();
				} else {
					MessageBox mb = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
					mb.setText("Merge");
					mb.setMessage(message);
					if (mb.open() == SWT.NO) {
						cs = null;
					}
				}

			}
		}

		// have to open the dialog until we get a valid changeset
		while (cs == null) {
			RevisionChooserDialog dialog = new RevisionChooserDialog(shell,
					Messages.getString("MergeHandler.mergeWith"), loader); //$NON-NLS-1$
			dialog.setDefaultShowingHeads(true);
			dialog.setDisallowSelectingParents(true);
			dialog.showForceButton(hasDirtyFiles);
			dialog.setForceChecked(forced);
			dialog.setForceButtonText(forceMessage);
			if (dialog.open() != IDialogConstants.OK_ID) {
				return "";
			}

			cs = dialog.getChangeSet();
			forced = dialog.isForceChecked();
		}

		boolean useExternalMergeTool = Boolean.valueOf(
				HgClients.getPreference(
						MercurialPreferenceConstants.PREF_USE_EXTERNAL_MERGE,
				"false")).booleanValue(); //$NON-NLS-1$
		String result = ""; //$NON-NLS-1$
		boolean useResolve = isHgResolveAvailable();
		if (useResolve) {
			result = HgMergeClient.merge(project, cs.getRevision().getChangeset(),
					useExternalMergeTool, forced);
		} else {
			result = HgIMergeClient.merge(project, cs.getRevision().getChangeset());
		}

		project.setPersistentProperty(ResourceProperties.MERGING, cs.getChangeset());
		try {
			result += commitMerge(monitor, project, shell, result, showCommitDialog);
		} catch (CoreException e) {
			MercurialEclipsePlugin.logError(e);
			MercurialEclipsePlugin.showError(e);
		}


		// trigger refresh of project decoration
		project.touch(new NullProgressMonitor());
		new RefreshWorkspaceStatusJob(project, true).schedule();
		return result;
	}

	private static String commitMerge(IProgressMonitor monitor, final IProject project,
			final Shell shell,  String mergeResult, boolean showCommitDialog) throws CoreException {
		boolean commit = true;

		String output = "";
		if (!HgResolveClient.checkAvailable()) {
			// conflicts, auto-merge could not resolve all succeeded and old old Mercurial
			if (!mergeResult.contains("all conflicts resolved")) { //$NON-NLS-1$
				commit = false;
			}
			// else auto commit
		} else {
			// check if auto-commit is possible
			List<FlaggedAdaptable> mergeAdaptables = HgResolveClient.list(project);
			monitor.subTask(com.vectrace.MercurialEclipse.wizards.Messages.getString("PullRepoWizard.pullOperation.mergeStatus")); //$NON-NLS-1$
			for (FlaggedAdaptable flaggedAdaptable : mergeAdaptables) {
				if (flaggedAdaptable.getFlag() == MercurialStatusCache.CHAR_UNRESOLVED) {
					// unresolved files, no auto-commit
					commit = false;
					break;
				}
			}
			monitor.worked(1);
		}

		// always show Merge view, as it offers to abort a merge and revise the automatically merged files
		MergeView view = (MergeView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView(MergeView.ID);
		view.clearView();
		view.setCurrentProject(project);

		// auto-commit if desired
		if (commit) {
			monitor.subTask(com.vectrace.MercurialEclipse.wizards.Messages.getString("PullRepoWizard.pullOperation.commit")); //$NON-NLS-1$
			output += com.vectrace.MercurialEclipse.wizards.Messages.getString("PullRepoWizard.pullOperation.commit.header"); //$NON-NLS-1$
			if (!showCommitDialog) {
				output += CommitMergeHandler.commitMerge(project);
			} else {
				output += new CommitMergeHandler().commitMergeWithCommitDialog(project, shell);
			}
			monitor.worked(1);
		}
		return output;
	}

	private static ChangeSet getOtherHeadInCurrentBranch(IProject project, DataLoader loader) throws HgException {
		ChangeSet[] heads = loader.getHeads();
		// have to be at least two heads total to do easy merge
		if (heads.length < 2) {
			return null;
		}

		ChangeSet currentRevision = LocalChangesetCache.getInstance().getChangesetByRootId(project);
		if(currentRevision == null){
			return null;
		}
		String branch = currentRevision.getBranch();

		ChangeSet candidate = null;
		for (ChangeSet cs : heads) {
			// must match branch
			if (!Branch.same(branch, cs.getBranch())) {
				continue;
			}
			// can't be the current
			if (cs.equals(currentRevision)) {
				continue;
			}
			// if we have more than one candidate, then have to ask user anyway.
			if (candidate != null) {
				return null;
			}
			candidate = cs;
		}


		return candidate;
	}

	private static boolean isHgResolveAvailable() throws HgException {
		return HgResolveClient.checkAvailable();
	}

}
