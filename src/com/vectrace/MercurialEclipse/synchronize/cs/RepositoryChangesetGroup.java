/*******************************************************************************
 * Copyright (c) 2005-2009 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Soren Mathiasen (Schantz) - implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.synchronize.cs;

import java.util.ArrayList;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.team.internal.core.subscribers.ChangeSet;

import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.model.IHgRepositoryLocation;
import com.vectrace.MercurialEclipse.model.WorkingChangeSet;

/**
 * @author Soren Mathiasen
 */
@SuppressWarnings("restriction")
public class RepositoryChangesetGroup {

	private final String name;
	private ChangesetGroup incoming;
	private ChangesetGroup outgoing;
	private final WorkingChangeSet uncommittedSet;
	private final IHgRepositoryLocation location;
	private Set<HgRoot> hgRoots;
	private ArrayList<IResource> projects;

	public RepositoryChangesetGroup(String name, IHgRepositoryLocation location,
			WorkingChangeSet uncommittedSet) {
		this.name = name;
		this.location = location;
		this.uncommittedSet = uncommittedSet;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SuperChangesetGroup [");
		if (name != null) {
			builder.append("name=");
			builder.append(name);
			builder.append(", ");
		}
		if (incoming != null && incoming.getChangesets().size() > 0) {
			for (ChangeSet set : incoming.getChangesets()) {
				builder.append(set.toString() + " ,");
			}
		}
		if (outgoing != null && outgoing.getChangesets().size() > 0) {
			for (ChangeSet set : outgoing.getChangesets()) {
				builder.append(set.toString() + " ,");
			}
		}

		builder.append("]");
		return builder.toString();
	}

	/**
	 * @param incoming
	 *            the incoming to set
	 */
	public void setIncoming(ChangesetGroup incoming) {
		this.incoming = incoming;
	}

	/**
	 * @return the incoming
	 */
	public ChangesetGroup getIncoming() {
		return incoming;
	}

	/**
	 * @param outgoing
	 *            the outgoing to set
	 */
	public void setOutgoing(ChangesetGroup outgoing) {
		this.outgoing = outgoing;
	}

	/**
	 * @return the outgoing
	 */
	public ChangesetGroup getOutgoing() {
		return outgoing;
	}

	/**
	 * @return the location
	 */
	public IHgRepositoryLocation getLocation() {
		return location;
	}

	/**
	 * @return the uncommittedSet
	 */
	public WorkingChangeSet getUncommittedSet() {
		return uncommittedSet;
	}

	/**
	 * @param hgRoots
	 *            the hgRoots to set
	 */
	public void setHgRoots(Set<HgRoot> hgRoots) {
		this.hgRoots = hgRoots;
	}

	/**
	 * @return the hgRoots
	 */
	public Set<HgRoot> getHgRoots() {
		return hgRoots;
	}

	/**
	 * @param projects
	 */
	public void setProjects(ArrayList<IResource> projects) {
		this.projects = projects;

	}

	public ArrayList<IResource> getProjects() {
		return projects;
	}
}
