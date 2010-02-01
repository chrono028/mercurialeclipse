/*******************************************************************************
 * Copyright (c) 2005-2010 Andrei Loskutov (Intland) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		Andrei Loskutov (Intland)	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.synchronize.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.SynchronizeModelAction;
import org.eclipse.team.ui.synchronize.SynchronizeModelOperation;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.WorkingChangeSet;
import com.vectrace.MercurialEclipse.team.cache.MercurialStatusCache;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;

/**
 * @author Andrei
 */
public class AddAction extends SynchronizeModelAction {

	private final ISynchronizePageConfiguration configuration;

	public AddAction(String text,
			ISynchronizePageConfiguration configuration,
			ISelectionProvider selectionProvider) {
		super(text, configuration, selectionProvider);
		setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(
				ISharedImages.IMG_OBJ_ADD));
		this.configuration = configuration;
	}

	@Override
	protected SynchronizeModelOperation getSubscriberOperation(ISynchronizePageConfiguration config,
			IDiffElement[] elements) {
		List<IResource> selectedResources = new ArrayList<IResource>();
		IStructuredSelection sel = getStructuredSelection();
		Object[] objects = sel.toArray();
		for (Object object : objects) {
			if (object instanceof WorkingChangeSet){
				selectedResources.addAll(((WorkingChangeSet)object).getFiles());
			} else if(!(object instanceof ChangeSet)){
				IResource resource = ResourceUtils.getResource(object);
				if(resource != null) {
					selectedResources.add(resource);
				}
			}
		}
		IResource[] resources = new IResource[selectedResources.size()];
		selectedResources.toArray(resources);
		return new AddOperation(configuration, elements, resources);
	}

	@Override
	protected final boolean updateSelection(IStructuredSelection selection) {
		boolean updateSelection = super.updateSelection(selection);
		if(!updateSelection){
			Object[] array = selection.toArray();
			for (Object object : array) {
				if(!isSupported(object)){
					return false;
				}
			}
			return true;
		}
		return updateSelection;
	}

	private boolean isSupported(Object object) {
		IResource adapter = MercurialEclipsePlugin.getAdapter(object,	IResource.class);
		if (adapter != null) {
			return MercurialStatusCache.getInstance().isUnknown(adapter);
		}
		return false;
	}
}
