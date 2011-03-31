/*******************************************************************************
 * Copyright (c) 2006-2009 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Software Balm Consulting Inc (Peter Hunnisett <peter_hge at softwarebalm dot com>) - implementation
 *     VecTrace (Zingo Andersen) - some updates
 *     Stefan Groschupf          - logError
 *     Stefan C                  - Code cleanup
 *     Bastian Doetsch           - Code reformatting to code style and refreshes
 *     Andrei Loskutov           - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.team;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.resources.team.IResourceTree;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgRemoveClient;
import com.vectrace.MercurialEclipse.commands.HgRenameClient;
import com.vectrace.MercurialEclipse.commands.HgRevertClient;
import com.vectrace.MercurialEclipse.dialogs.CommitDialog.Options;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.menu.CommitHandler;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.team.cache.MercurialStatusCache;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;


/**
 * @author Peter Hunnisett <peter_hge at softwarebalm dot com>
 *
 *         Hook into Eclipse rename and delete file operations so that the
 *         appropriate changes can be tracked in Mercurial as well.
 */
public class HgMoveDeleteHook implements IMoveDeleteHook {

	private static final MercurialStatusCache CACHE = MercurialStatusCache.getInstance();

	/**
	 * @returns <code>true</code> if this file under this under Mercurial
	 *          control.
	 */
	private static boolean isInMercurialRepo(IResource file, IProgressMonitor monitor) {
		return CACHE.isSupervised(file);
	}

	/**
	 * Determines if a folder has supervised files
	 *
	 * @returns <code>true</code> if there are files under this folder that are
	 *          under Mercurial control.
	 */
	private static boolean folderHasMercurialFiles(IFolder folder,
			IProgressMonitor monitor) {
		if (!isInMercurialRepo(folder, monitor)) {
			// Resource could be inside a link or something do nothing
			// in the future this could check is this is another repository
			return false;
		}

		try {
			IResource[] children = folder.members();
			for (IResource resource : children) {
				if (resource.getType() == IResource.FILE) {
					if (resource.exists() && isInMercurialRepo(resource, monitor)) {
						return true;
					}
				} else {
					if(folderHasMercurialFiles((IFolder) resource, monitor)){
						return true;
					}
				}
			}
		} catch (CoreException e) {
			/*
			 * Let's assume that this means there are no resources under this
			 * one as it probably doesn't properly exist. Let eclipse do
			 * everything.
			 */
			return false;
		}

		return false;
	}

	public boolean deleteFile(IResourceTree tree, IFile file, int updateFlags,
			IProgressMonitor monitor) {
		/*
		 * Returning false indicates that the caller should invoke
		 * tree.standardDeleteFile to actually remove the resource from the file
		 * system and eclipse.
		 */

		if (!isInMercurialRepo(file, monitor) || file.isDerived()) {
			return false;
		}

		boolean keepHistory = (updateFlags & IResource.KEEP_HISTORY) != 0;
		if (keepHistory) {
			tree.addToLocalHistory(file);
		}
		return deleteHgFiles(tree, file, monitor);
	}

	public boolean deleteFolder(IResourceTree tree, IFolder folder,
			int updateFlags, IProgressMonitor monitor) {
		/*
		 * Mercurial doesn't control directories. However, as a short cut
		 * performing an operation on a folder will affect all subtending files.
		 * Check that there is at least 1 file and if so there is Mercurial work
		 * to do, otherwise there is no Mercurial work to be done.
		 */
		if (!folderHasMercurialFiles(folder, monitor)) {
			return false;
		}

		/*
		 * NOTE: There are bugs with Mercurial 0.9.1 on Windows and folder
		 * delete/rename operation. See:
		 * http://www.selenic.com/mercurial/bts/issue343,
		 * http://www.selenic.com/mercurial/bts/issue303, etc. Returning false
		 * indicates that the caller should invoke tree.standardDeleteFile to
		 * actually remove the resource from the file system and eclipse.
		 */
		return deleteHgFiles(tree, folder, monitor);
	}

	/**
	 * Perform the file or folder (ie multiple file) delete.
	 *
	 * @returns <code>false</code> if the action succeeds, <code>true</code>
	 *          otherwise. This syntax is to match the desired return code for
	 *          <code>deleteFile</code> and <code>deleteFolder</code>.
	 */
	private static boolean deleteHgFiles(IResourceTree tree, IResource resource, IProgressMonitor monitor) {
		// TODO: Decide if we should have different Hg behaviour based on the
		// force flag provided in updateFlags.
		try {
			// Delete the file(s) from the Mercurial repository.
			HgRemoveClient.removeResource(resource, monitor);
		} catch (HgException e) {
			MercurialEclipsePlugin.logError(e);
			return false;
		}

		// We removed the file ourselves, need to tell.
		if(resource.getType() == IResource.FOLDER) {
			tree.deletedFolder((IFolder) resource);
		} else if(resource.getType() == IResource.PROJECT){
			tree.deletedProject((IProject) resource);
		} else{
			// hg deletes the parent folder too if the deleted file was the only one in the folder
			// we have to tell Eclipse that the folder (and probably all subsequent parents)
			// are deleted too...
			File dir = ResourceUtils.getFileHandle(resource).getParentFile();
			IContainer parent = resource.getParent();
			tree.deletedFile((IFile) resource);
			while(parent instanceof IFolder && dir != null && !dir.exists()){
				IContainer backup = parent.getParent();
				tree.deletedFolder((IFolder) parent);
				parent = backup;
				dir = dir.getParentFile();
			}
		}

		// Returning true indicates that this method has removed resource in both
		// the file system and eclipse.
		return true;
	}

	public boolean deleteProject(IResourceTree tree, final IProject project,
			int updateFlags, IProgressMonitor monitor) {

		if ((updateFlags & IResource.ALWAYS_DELETE_PROJECT_CONTENT) == 0) {
			disconnect(project);
			tree.deletedProject(project);
			return true;
		}

		HgRoot hgRoot = MercurialTeamProvider.getHgRoot(project);
		if(hgRoot == null){
			return false;
		}

		if(!hgRoot.getIPath().equals(ResourceUtils.getPath(project))){
			final Set<IResource> allFiles = ResourceUtils.getMembers(project);

			try {
				HgRemoveClient.removeResources(new ArrayList<IResource>(allFiles));
				MercurialStatusCache.getInstance().refreshStatus(project, monitor);
			} catch (HgException e1) {
				MercurialEclipsePlugin.logError(e1);
				MercurialEclipsePlugin.showError(e1);
				return true;
			}

			final boolean [] continueDelete = new boolean[]{ false };
			Display.getDefault().syncExec(new Runnable(){
				public void run() {
					MessageDialog.openInformation(MercurialEclipsePlugin.getActiveShell(),
							"Project removed",
							"All project files are now removed from Mercurial repository.\n"
							+ "A commit is highly recommended.");
					CommitHandler ch = new CommitHandler();
					Options options = new Options();
					options.defaultCommitMessage = "Removed project '" + project.getName() + "' from repository.";
					options.filesSelectable = false;
					options.showAmend = false;
					options.showCloseBranch = false;
					options.showDiff = false;
					options.showRevert = false;
					ch.setOptions(options);
					try {
						ch.run(new ArrayList<IResource>(allFiles));
					} catch (HgException e) {
						MercurialEclipsePlugin.logError(e);
					}
					continueDelete[0] = ch.getResult() == Window.OK;
				}
			});
			if(continueDelete[0]){
				disconnect(project);
				// if user committed deleted files, mercurial part is done
				// now we must say Eclipse please delete the project
				tree.deletedProject(project);
			}
			// delete was NOT done by hg. Anyway, let the files and project there.
			return true;
		}

		IFolder folder = project.getFolder(".hg"); //$NON-NLS-1$
		try {
			folder.delete(updateFlags, monitor);
			disconnect(project);
			// say Eclipse it should do the delete of now unmanaged project files for us
			return false;
		} catch (CoreException e) {
			MercurialEclipsePlugin.logError(e);
			return true;
		}
	}

	/**
	 * @param project non null
	 */
	private static void disconnect(final IProject project) {
		if (RepositoryProvider.isShared(project)) {
			try {
				RepositoryProvider.unmap(project);
			} catch (TeamException e) {
				MercurialEclipsePlugin.logError(e);
			}
		}
	}

	public boolean moveFile(IResourceTree tree, IFile source,
			IFile destination, int updateFlags, IProgressMonitor monitor) {

		if (!isInMercurialRepo(source, monitor)) {
			return false;
		}
		boolean keepHistory = (updateFlags & IResource.KEEP_HISTORY) != 0;
		if (keepHistory) {
			tree.addToLocalHistory(source);
		}

		// Move the file in the Mercurial repository.
		if (!moveHgFiles(source, destination, monitor)) {
			return true;
		}

		// We moved the file ourselves, need to tell.
		tree.movedFile(source, destination);
		tree.updateMovedFileTimestamp(destination, tree.computeTimestamp(destination));

		// Returning true indicates that this method has moved resource in both
		// the file system and eclipse.
		return true;
	}

	public boolean moveFolder(IResourceTree tree, IFolder source,
			IFolder destination, int updateFlags, IProgressMonitor monitor) {
		/*
		 * Mercurial doesn't control directories. However, as a short cut
		 * performing an operation on a folder will affect all subtending files.
		 * Check that there is at least 1 file and if so there is Mercurial work
		 * to do, otherwise there is no Mercurial work to be done.
		 */
		if (!folderHasMercurialFiles(source, monitor)) {
			return false;
		}

		// Move the folder (ie all subtending files) in the Mercurial
		// repository.
		if (!moveHgFiles(source, destination, monitor)) {
			return true;
		}

		// We moved the file ourselves, need to tell.
		tree.movedFolderSubtree(source, destination);

		// Returning true indicates that this method has moved resource in both
		// the file system and eclipse.
		return true;
	}

	/**
	 * Move the file or folder (ie multiple file).
	 *
	 * @returns <code>true</code> if the action succeeds, <code>false</code>
	 *          otherwise.
	 */
	private static boolean moveHgFiles(IResource source, IResource destination,
			IProgressMonitor monitor) {
		// Rename the file in the Mercurial repository.

		// TODO: Decide if we should have different Hg behavior based on the
		// force flag provided in updateFlags.
		try {
			HgRenameClient.renameResource(source, destination, monitor);
		} catch (final HgException e) {
			MercurialEclipsePlugin.logError(e);
			if (MercurialUtilities.isWindows()
					&& source.getName().equalsIgnoreCase(destination.getName())) {
				try {
					HgRoot hgRoot = MercurialTeamProvider.getHgRoot(source);
					List<IResource> res = new ArrayList<IResource>();
					res.add(source);
					HgRevertClient.performRevert(monitor, hgRoot, res, null);
				} catch (HgException e1) {
					MercurialEclipsePlugin.logError(e1);
				}
				HgException ex = new HgException(
						"Mercurial does not support renaming of files on Windows,"
								+ " if file names differs only by the lower/upper case letters!", e);
				MercurialEclipsePlugin.showError(ex);
			}
			return false;
		}
		return true;
	}

	public boolean moveProject(IResourceTree tree, IProject source,
			IProjectDescription description, int updateFlags,
			IProgressMonitor monitor) {
		// Punting to eclipse is fine as presumably all resources in the .hg
		// folder are relative to the root and will remain intact.
		return false;
	}

}
