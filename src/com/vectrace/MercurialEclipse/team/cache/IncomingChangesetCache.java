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
package com.vectrace.MercurialEclipse.team.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.team.core.RepositoryProvider;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.ChangeSet.Direction;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocation;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;

/**
 * The cache keeps the state automatically (and fetches the data on client request only), to avoid
 * unneeded client-server communication.
 * <p>
 * There is no guarantee that the data in the cache is up-to-date with the server. To get the
 * latest data, clients have explicitely refresh or clean the cache before using it.
 * <p>
 * The cache has empty ("invalid" state) before the first client request and automatically
 * retrieves the data on first client request. So it becames "valid" state and does not refresh the
 * data until some operation "clears" or explicitely requests a "refresh" of the cache. After the
 * "clear" operation the cache is going to the initial "invalid" state again. After "clear" and
 * "refresh", a notification is sent to the observing clients.
 * <p>
 * <b>Implementation note 1</b> this cache <b>automatically</b> keeps the "valid" state for given
 * project/repository pair. Before each "get" request the cache validates itself. If the cached
 * value is NULL, then the cache state is invalid, and new data is fetched. If the cached value is
 * an object (even empty set), then the cache is "valid" (there is simply no data on the server).
 * <p>
 * <b>Implementation note 2</b> the cache sends different notifications depending on what kind of
 * "state change" happened. After "clear", a set with only one "project" object is sent. After
 * "refresh", a set with all changed elements is sent, which may also include a project.
 *
 * @author bastian
 * @author Andrei Loskutov
 */
public class IncomingChangesetCache extends AbstractRemoteCache {

    private static IncomingChangesetCache instance;

    /**
     * The Map has the following structure: RepositoryLocation -> IResource ->
     * Changeset-Set
     */
    private final Map<HgRepositoryLocation, Map<IPath, SortedSet<ChangeSet>>> changeSets;

    private IncomingChangesetCache() {
        super();
        changeSets = new HashMap<HgRepositoryLocation, Map<IPath, SortedSet<ChangeSet>>>();
    }

    public synchronized static IncomingChangesetCache getInstance() {
        if (instance == null) {
            instance = new IncomingChangesetCache();
        }
        return instance;
    }

    public void clear(HgRepositoryLocation repo) {
        synchronized (changeSets) {
            changeSets.remove(repo);
        }
        Set<IProject> projects = MercurialEclipsePlugin.getRepoManager().getAllRepoLocationProjects(repo);
        for (IProject project : projects) {
            clearChangesets(project);
        }
    }

    /**
     * @param notify true to send a notification if the cache state changes after this operation,
     * false to supress the event notification
     */
    public void clear(HgRepositoryLocation repo, IProject project, boolean notify) {
        synchronized (changeSets) {
            Map<IPath, SortedSet<ChangeSet>> map = changeSets.get(repo);
            if(map != null){
                map.remove(project.getLocation());
                clearChangesets(project);
            }
        }
        if(notify) {
            notifyChanged(project, false);
        }
    }

    @Override
    protected void clearProjectCache(IProject project) {
        super.clearProjectCache(project);
        Set<HgRepositoryLocation> repos = MercurialEclipsePlugin.getRepoManager()
                .getAllProjectRepoLocations(project);
        for (HgRepositoryLocation repo : repos) {
            clear(repo, project, false);
        }
    }

    /**
     * Gets all incoming changesets of the given location for the given
     * IResource.
     *
     * @return never null
     */
    public SortedSet<ChangeSet> getChangeSets(IResource resource,
            HgRepositoryLocation repository) throws HgException {
        Map<IPath, SortedSet<ChangeSet>> repoMap;
        synchronized (changeSets){
            repoMap = changeSets.get(repository);
            if (repoMap == null
                    || ((resource instanceof IProject) && repoMap.get(resource.getLocation()) == null)) {
                // lazy loading: refresh cache on demand only.
                refreshChangeSets(resource.getProject(), repository);
                repoMap = changeSets.get(repository);
            }
            if (repoMap != null) {
                SortedSet<ChangeSet> revisions = repoMap.get(resource.getLocation());
                if (revisions != null) {
                    return Collections.unmodifiableSortedSet(revisions);
                }
            }
        }
        return new TreeSet<ChangeSet>();
    }

    /**
     * Gets all resources that are changed in incoming changesets of given
     * repository, even resources not known in local workspace.
     *
     * @return never null
     */
    public Set<IResource> getMembers(IResource resource,
            HgRepositoryLocation repository) throws HgException {
        synchronized (changeSets){
            return getMembers(resource, getMap(resource, repository));
        }
    }

    private Map<IPath, SortedSet<ChangeSet>> getMap(IResource resource, HgRepositoryLocation repository)
            throws HgException {
        // make sure data is there: will refresh incoming if needed
        getChangeSets(resource, repository);
        return changeSets.get(repository);
    }


    /**
     * Gets all incoming changesets by querying Mercurial and adds them to the caches.
     */
    private void refreshChangeSets(IProject project, HgRepositoryLocation repository) throws HgException {
        Assert.isNotNull(project);

        // check if mercurial is team provider and if we're working on an open project
        if (null != RepositoryProvider.getProvider(project, MercurialTeamProvider.ID) && project.isOpen()) {

            // lock the cache till update is complete
            synchronized (changeSets){
                addResourcesToCache(project, repository, changeSets, Direction.INCOMING);
            }
            notifyChanged(project, true);
        }
    }
    
    
    public ChangeSet getNewestChangeSet(IResource resource,
            HgRepositoryLocation repository) throws HgException {
        
        if (MercurialStatusCache.getInstance().isSupervised(resource) || !resource.exists()) {
            synchronized (changeSets){
                Map<IPath, SortedSet<ChangeSet>> repoMap = getMap(resource, repository);
                
                if (repoMap != null) {
                    SortedSet<ChangeSet> revisions = repoMap.get(resource.getLocation());
                    if (revisions != null && revisions.size() > 0) {
                        return revisions.last();
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Gets the newest incoming changeset of <b>all repositories</b>.
     *
     * @param resource
     *            the resource to get the changeset for
     */
    public ChangeSet getNewestChangeSet(IResource resource) throws HgException {
        Set<HgRepositoryLocation> locs = MercurialEclipsePlugin
        .getRepoManager().getAllProjectRepoLocations(resource.getProject());
        SortedSet<ChangeSet> changeSets1 = new TreeSet<ChangeSet>();
        for (HgRepositoryLocation repository : locs) {
            ChangeSet candidate = getNewestChangeSet(resource, repository);
            if (candidate != null) {
                changeSets1.add(candidate);
            }
        }
        if (changeSets1.size() > 0) {
            return changeSets1.last();
        }
        return null;
    }
}
