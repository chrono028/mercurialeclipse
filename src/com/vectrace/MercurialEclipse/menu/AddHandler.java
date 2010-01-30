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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

import com.vectrace.MercurialEclipse.commands.HgAddClient;
import com.vectrace.MercurialEclipse.commands.HgStatusClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.team.cache.RefreshStatusJob;
import com.vectrace.MercurialEclipse.ui.ResourcesTreeContentProvider;
import com.vectrace.MercurialEclipse.ui.UntrackedResourcesFilter;

public class AddHandler extends MultipleResourcesHandler {

	@Override
	public void run(final List<IResource> resources) throws HgException {

		Set<IProject> roots = getRoots(resources);

		Map<IProject, Set<IPath>> untrackedFiles = new HashMap<IProject, Set<IPath>>();
		Map<IProject, Set<IPath>> untrackedFolders = new HashMap<IProject, Set<IPath>>();

		for (IProject project : roots) {
			String[] rawFiles = HgStatusClient.getUntrackedFiles(project);
			Set<IPath> files = new HashSet<IPath>();
			Set<IPath> folders = new HashSet<IPath>();

			for (String raw : rawFiles) {
				IPath path = new Path(raw);
				files.add(path);
				int count = path.segmentCount();
				for (int i = 1; i < count; i++) {
					folders.add(path.removeLastSegments(i));
				}
			}

			untrackedFiles.put(project, files);
			untrackedFolders.put(project, folders);
		}

		ViewerFilter untrackedFilter = new UntrackedResourcesFilter(untrackedFiles,
				untrackedFolders);

		CheckedTreeSelectionDialog dialog = new CheckedTreeSelectionDialog(getShell(),
				new WorkbenchLabelProvider(),
				new ResourcesTreeContentProvider(roots));

		dialog.setInput(ResourcesTreeContentProvider.ROOT);
		dialog.setTitle(Messages.getString("AddHandler.addToVersionControl")); //$NON-NLS-1$
		dialog.setMessage(Messages.getString("AddHandler.selectFiles")); //$NON-NLS-1$
		dialog.setContainerMode(true);
		dialog.setInitialElementSelections(resources);
		dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
		dialog.addFilter(untrackedFilter);
		Set<IContainer> expanded = new HashSet<IContainer>();
		for (IResource resource : resources) {
			IContainer parent = resource.getParent();
			while(parent != null && !expanded.contains(parent)){
				if(parent.getType() == IResource.ROOT){
					break;
				}
				expanded.add(parent);
				parent = parent.getParent();
			}
		}
		dialog.setExpandedElements(expanded.toArray(new IContainer[0]));
		if (dialog.open() == IDialogConstants.OK_ID) {
			HgAddClient.addResources(keepFiles(dialog.getResult()), null);
			for (IProject proj : roots) {
				new RefreshStatusJob(Messages.getString("AddHandler.refreshStatus"), proj).schedule();     //$NON-NLS-1$
			}
		}
	}

	/**
	 * Only keep IFiles
	 */
	private List<IResource> keepFiles(Object[] objects) {
		List<IResource> files = new ArrayList<IResource>();
		for (Object object : objects) {
			if (object instanceof IFile) {
				files.add((IFile) object);
			}
		}
		return files;
	}

	private Set<IProject> getRoots(List<IResource> resources) {
		Set<IProject> roots = new TreeSet<IProject>(new Comparator<IProject>() {
			public int compare(IProject p1, IProject p2) {
				return p1.getName().compareTo(p2.getName());
			}
		});
		for (IResource resource : resources) {
			roots.add(resource.getProject());
		}
		return roots;
	}

}
