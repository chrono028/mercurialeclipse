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
package com.vectrace.MercurialEclipse.synchronize;

import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.variants.IResourceVariant;

import com.vectrace.MercurialEclipse.team.IStorageMercurialRevision;

public class MercurialRemoteResourceVariant implements IResourceVariant {
	private IStorageMercurialRevision rev;
	public MercurialRemoteResourceVariant(IStorageMercurialRevision rev) {
		this.rev=rev;
	}

	public byte[] asBytes() {
		return rev.getName().getBytes();
	}

	public String getContentIdentifier() {
		return rev.getName();
	}

	public String getName() {
		return rev.getName();
	}

	public IStorage getStorage(IProgressMonitor monitor) throws TeamException {
		return rev;
	}

	public boolean isContainer() {		
		return false;
	}

}
