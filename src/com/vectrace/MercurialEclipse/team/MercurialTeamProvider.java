/*******************************************************************************
 * Copyright (c) 2006-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VecTrace (Zingo Andersen) - implementation
 *     Software Balm Consulting Inc (Peter Hunnisett <peter_hge at softwarebalm dot com>) - some updates
 *     StefanC                   - some updates
 *     Bastian Doetsch           - new qualified name for project sets
 *     Andrei Loskutov (Intland) - bug fixes
 *     Adam Berkes (Intland)     - Fix encoding
 *******************************************************************************/
package com.vectrace.MercurialEclipse.team;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.resources.team.ResourceRuleFactory;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.history.IFileHistoryProvider;
import org.eclipse.ui.IPropertyListener;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.AbstractClient;
import com.vectrace.MercurialEclipse.commands.HgBranchClient;
import com.vectrace.MercurialEclipse.commands.HgDebugInstallClient;
import com.vectrace.MercurialEclipse.commands.HgRootClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.history.MercurialHistoryProvider;
import com.vectrace.MercurialEclipse.model.Branch;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.HgRootContainer;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocationManager;
import com.vectrace.MercurialEclipse.team.cache.HgRootRule;
import com.vectrace.MercurialEclipse.team.cache.MercurialStatusCache;
import com.vectrace.MercurialEclipse.team.cache.RefreshStatusJob;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

/**
 * @author zingo
 */
public class MercurialTeamProvider extends RepositoryProvider {

	public static final String ID = "com.vectrace.MercurialEclipse.team.MercurialTeamProvider"; //$NON-NLS-1$

	/**
	 * The value is one element array, which single element is NON null if the project has
	 * a hg root
	 */
	private static final Map<IProject, HgRoot[]> HG_ROOTS = new ConcurrentHashMap<IProject, HgRoot[]>();

	/** key is hg root, value is the *current* branch */
	private static final Map<HgRoot, String> BRANCH_MAP = new ConcurrentHashMap<HgRoot, String>();

	private MercurialHistoryProvider fileHistoryProvider;

	private static final ListenerList BRANCH_LISTENERS = new ListenerList(ListenerList.IDENTITY);

	/** @see #getRuleFactory() */
	private IResourceRuleFactory resourceRuleFactory;

	public MercurialTeamProvider() {
		super();
	}

	public static SortedSet<HgRoot> getKnownHgRoots(){
		SortedSet<HgRoot> roots = new TreeSet<HgRoot>();
		Collection<HgRoot[]> values = HG_ROOTS.values();
		for (HgRoot[] hgRoots : values) {
			for (HgRoot hgRoot : hgRoots) {
				if(hgRoot != null) {
					roots.add(hgRoot);
				}
			}
		}
		return roots;
	}

	public static List<IProject> getKnownHgProjects(){
		List<IProject> projects = new ArrayList<IProject>();
		IProject[] iProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : iProjects) {
			if(isHgTeamProviderFor(project)){
				projects.add(project);
			}
		}
		return projects;
	}

	public static List<IProject> getKnownHgProjects(HgRoot hgRoot){
		List<IProject> hgProjects = getKnownHgProjects();
		List<IProject> projects = new ArrayList<IProject>();
		for (IProject project : hgProjects) {
			if(hgRoot.equals(hasHgRoot(project))){
				projects.add(project);
			}
		}
		return projects;
	}

	@Override
	public void setProject(IProject project) {
		super.setProject(project);
		HgRoot hgRoot = getHgRoot(project);
		if(hgRoot != null){
			return;
		}
		try {
			configureProject();
		} catch (CoreException e) {
			MercurialEclipsePlugin.logError(e);
		}
	}

	@Override
	public void configureProject() throws CoreException {
		IProject project = getProject();
		HgRoot hgRoot = getAndStoreHgRoot(project);
		HG_ROOTS.put(project, new HgRoot[] { hgRoot });
		// try to find .hg directory to set it as private member
		final IResource hgDir = project.getFolder(".hg"); //$NON-NLS-1$
		if (hgDir != null) {
			setTeamPrivate(hgDir);
		}
		if(!MercurialStatusCache.getInstance().isStatusKnown(project)) {
			new RefreshStatusJob("Initializing hg cache for: " + hgRoot.getName(), project, hgRoot)
					.schedule(50);
		}
		if(MercurialEclipsePlugin.getRepoManager().getAllRepoLocations(hgRoot).isEmpty()){
			loadRootRepos(hgRoot);
		}
	}

	private void setTeamPrivate(final IResource hgDir) throws CoreException {
		if(!hgDir.exists() && ResourceUtils.getFileHandle(hgDir).exists()){
			Job refreshJob = new Job("Refreshing .hg folder") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						hgDir.refreshLocal(IResource.DEPTH_ZERO, monitor);
						if(hgDir.exists() && !hgDir.isTeamPrivateMember()) {
							hgDir.setTeamPrivateMember(true);
							hgDir.setDerived(true);
						}
					} catch (CoreException e) {
						MercurialEclipsePlugin.logError(e);
					}
					return Status.OK_STATUS;
				}
			};
			refreshJob.schedule();
		} else if(!hgDir.isTeamPrivateMember()){
			hgDir.setTeamPrivateMember(true);
			hgDir.setDerived(true);
		}
	}

	/**
	 * @param hgRoot non null
	 */
	private void loadRootRepos(final HgRoot hgRoot) {
		Job job = new Job("Reading root repositories") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				HgRepositoryLocationManager repoManager = MercurialEclipsePlugin.getRepoManager();
				if(!repoManager.getAllRepoLocations(hgRoot).isEmpty()){
					return Status.OK_STATUS;
				}
				try {
					repoManager.loadRepos(hgRoot);
				} catch (HgException e) {
					MercurialEclipsePlugin.logError(e);
					return e.getStatus();
				}
				return Status.OK_STATUS;
			}

			@Override
			public boolean shouldSchedule() {
				return MercurialEclipsePlugin.getRepoManager().getAllRepoLocations(hgRoot)
				.isEmpty();
			}
		};
		job.setSystem(true);
		job.setRule(new HgRootRule(hgRoot));
		job.schedule(100);
	}
	public void deconfigure() throws CoreException {
		IProject project = getProject();
		Assert.isNotNull(project);
		// cleanup
		HG_ROOTS.put(project, new HgRoot[1]);
		MercurialStatusCache.getInstance().clearMergeStatus(project);
	}

	/**
	 * Checks if the given project is controlled by MercurialEclipse.
	 *
	 * @return true, if MercurialEclipse provides team functions to this
	 *         project, false otherwise.
	 */
	public static boolean isHgTeamProviderFor(IProject project){
		Assert.isNotNull(project);
		HgRoot[] result = HG_ROOTS.get(project);
		if(result == null){
			return RepositoryProvider.getProvider(project, ID) != null;
		}
		return result[0] != null;
	}

	public static void addBranchListener(IPropertyListener listener){
		BRANCH_LISTENERS.add(listener);
	}

	public static void removeBranchListener(IPropertyListener listener){
		BRANCH_LISTENERS.remove(listener);
	}

	/**
	 * Determines if the resources hg root is known.
	 * If it isn't known, Mercurial is called to determine it.
	 *
	 * @param resource
	 *            the resource to get the hg root for, not null
	 * @return the canonical file path of the hg root, never null
	 * @throws HgException
	 */
	private static HgRoot getAndStoreHgRoot(IResource resource) throws HgException {
		IProject project = resource.getProject();
		if (project == null || !resource.exists()) {
			return AbstractClient.getHgRoot(resource);
		}
		HgRoot[] rootElt = HG_ROOTS.get(project);
		if(rootElt == null){
			rootElt = new HgRoot[1];
			HG_ROOTS.put(project, rootElt);
		}
		HgRoot hgRoot = rootElt[0];
		if (hgRoot == null) {
			hgRoot = AbstractClient.getHgRoot(resource);
			rootElt[0] = hgRoot;
			setRepositoryEncoding(project, hgRoot);
		}
		return hgRoot;
	}

	private static void setRepositoryEncoding(IProject project, final HgRoot hgRoot) {
		// if a user EXPLICITLY states he wants a certain encoding
		// in a project, we should respect it (if its supported)
		final String defaultCharset;
		try {
			defaultCharset = project.getDefaultCharset();
			if (!Charset.isSupported(defaultCharset)) {
				return;
			}
		} catch (CoreException ex) {
			MercurialEclipsePlugin.logError(ex);
			return;
		}

		// This code is running on very beginning of the eclipse startup. ALWAYS run
		// hg commands in a job, to awoid deadlocks of Eclipse at this point of time!

		// Deadlocks seen already: if running in UI thread, or if running inside the
		// lock acquired by the RepositoryProvider.mapExistingProvider
		Job job = new Job("Changeset detection") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (HgDebugInstallClient.hgSupportsEncoding(defaultCharset)) {
					hgRoot.setEncoding(Charset.forName(defaultCharset));
				}
				monitor.done();
				return Status.OK_STATUS;
			}
			@Override
			public boolean shouldSchedule() {
				Boolean supports = HgDebugInstallClient.ENCODINGS.get(defaultCharset);
				if(supports != null && supports.booleanValue()){
					hgRoot.setEncoding(Charset.forName(defaultCharset));
				}
				return supports == null;
			}
		};
		class CharsetRule implements ISchedulingRule {
			private final String cs;
			CharsetRule(String cs){
				this.cs = cs;
			}
			public boolean isConflicting(ISchedulingRule rule) {
				return contains(rule);
			}
			public boolean contains(ISchedulingRule rule) {
				return rule instanceof CharsetRule && cs.equals(((CharsetRule) rule).cs);
			}
		}
		job.setRule(new CharsetRule(defaultCharset));
		job.setSystem(true);
		job.schedule();
	}


	private static HgRoot getHgRootFile(File file) throws HgException {
		return HgRootClient.getHgRoot(file);
	}

	/**
	 * Gets the hg root of a resource as {@link java.io.File}.
	 *
	 * @param resource
	 *            the resource to get the hg root for, not null
	 * @return the {@link java.io.File} referencing the hg root directory
	 * @throws HgException
	 *             if an error occurred (e.g. no root could be found)
	 */
	public static HgRoot getHgRoot(IResource resource) throws HgException {
		if(resource == null){
			return null;
		}

		if(resource instanceof HgRootContainer){
			HgRootContainer rootContainer = (HgRootContainer) resource;
			return rootContainer.getHgRoot();
		}

		IProject project = resource.getProject();
		if (project == null && resource.getLocation() != null) {
			// happens in case of IResource instanceof IWorkspaceRoot
			return AbstractClient.getHgRoot(resource);
		}
		return getHgRoot(project);
	}

	/**
	 * Gets the hg root of a resource as {@link java.io.File}.
	 *
	 * @param project
	 *            the project to get the hg root for, not null
	 * @return the {@link java.io.File} referencing the hg root directory
	 */
	public static HgRoot getHgRoot(IProject project) {
		HgRoot[] rootElt = HG_ROOTS.get(project);
		if(rootElt == null){
			rootElt = new HgRoot[1];
			HG_ROOTS.put(project, rootElt);
		}
		return rootElt[0];
	}

	/**
	 * Gets the hg root of a resource as {@link java.io.File}.
	 *
	 * @param resource
	 *            the resource to get the hg root for
	 * @return the {@link java.io.File} referencing the hg root directory, or null if no
	 * hg root can't be found
	 */
	public static HgRoot hasHgRoot(IResource resource) {
		if(resource == null || resource instanceof IWorkspaceRoot){
			return null;
		}
		IProject project = resource.getProject();
		HgRoot[] result = HG_ROOTS.get(project);
		if(result != null && result[0] != null){
			return result[0];
		}
		HgRoot hgRoot = HgRootClient.hasHgRoot(ResourceUtils.getFileHandle(resource));
		if(hgRoot != null && (RepositoryProvider.getProvider(project, ID) != null)) {
			HG_ROOTS.put(project, new HgRoot[]{hgRoot});
		}
		return hgRoot;
	}

	/**
	 * Gets the hg root of a resource as {@link java.io.File}.
	 *
	 * @param file
	 *            a {@link java.io.File}, not null
	 * @return the file object of the root.
	 * @throws HgException
	 */
	public static HgRoot getHgRoot(File file) throws HgException {
		IResource resource = ResourceUtils.convert(file);
		if (resource != null) {
			return getHgRoot(resource);
		}
		return getHgRootFile(file);
	}

	/**
	 * @param hgRoot non null
	 * @return current root branch, never null.
	 */
	public static String getCurrentBranch(HgRoot hgRoot){
		Assert.isNotNull(hgRoot);
		String branch = BRANCH_MAP.get(hgRoot);
		if(branch == null){
			try {
				branch = HgBranchClient.getActiveBranch(hgRoot);
				BRANCH_MAP.put(hgRoot, branch);
			} catch (HgException e) {
				MercurialEclipsePlugin.logError(e);
				return Branch.DEFAULT;
			}
		}
		return branch;
	}

	/**
	 * @param res non null
	 * @return current resource branch, never null.
	 */
	public static String getCurrentBranch(IResource res){
		Assert.isNotNull(res);
		HgRoot hgRoot;
		try {
			hgRoot = getHgRoot(res);
		} catch (HgException e) {
			MercurialEclipsePlugin.logError(e);
			return Branch.DEFAULT;
		}
		if(hgRoot == null){
			return Branch.DEFAULT;
		}
		return getCurrentBranch(hgRoot);
	}

	/**
	 * Set the root branch and notifies the branch listeners
	 * @param branch current branch. If null is given, cache will be cleaned up
	 * @param hgRoot non null
	 */
	public static void setCurrentBranch(String branch, HgRoot hgRoot){
		Assert.isNotNull(hgRoot);
		String oldBranch = null;
		if(branch != null){
			oldBranch = BRANCH_MAP.put(hgRoot, branch);
		} else {
			BRANCH_MAP.remove(hgRoot);
		}
		if(branch != null && !Branch.same(branch, oldBranch)){
			Object[] listeners = BRANCH_LISTENERS.getListeners();
			for (int i = 0; i < listeners.length; i++) {
				IPropertyListener listener = (IPropertyListener) listeners[i];
				listener.propertyChanged(hgRoot, 0);
			}
		}
	}

	@Override
	public String getID() {
		return ID;
	}

	@Override
	public IMoveDeleteHook getMoveDeleteHook() {
		return new HgMoveDeleteHook();
	}

	@Override
	public IFileHistoryProvider getFileHistoryProvider() {
		if (fileHistoryProvider == null) {
			fileHistoryProvider = new MercurialHistoryProvider();
		}
		return fileHistoryProvider;
	}

	@Override
	public boolean canHandleLinkedResources() {
		return true;
	}

	@Override
	public boolean canHandleLinkedResourceURI() {
		return canHandleLinkedResources();
	}

	/**
	 * Overrides the default pessimistic resource rule factory which locks the workspace for all
	 * operations. This causes problems when opening a project. This method returns the default
	 * non-pessimistic resource rule factory which locks on a finer level.
	 */
	@Override
	public IResourceRuleFactory getRuleFactory() {
		if (resourceRuleFactory == null) {
			resourceRuleFactory = new ResourceRuleFactory() {};
		}
		return resourceRuleFactory;
	}
}
