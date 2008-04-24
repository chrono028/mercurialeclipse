package com.vectrace.MercurialEclipse.team;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.HgTagClient;
import com.vectrace.MercurialEclipse.dialogs.TagDialog;

/**
 * 
 * @author Jerome Negre <jerome+hg@jnegre.org>
 *
 */public class TagAction extends SingleResourceAction {

	@Override
	protected void run(IResource resource) throws Exception {
		IProject project = resource.getProject();
		TagDialog dialog = new TagDialog(getShell(), project);
		
		if(dialog.open() == IDialogConstants.OK_ID) {
			HgTagClient.addTag(
					resource,
					dialog.getName(),
					dialog.getTargetRevision(),
					null, //user
					dialog.isLocal(),
					dialog.isForced());
			MercurialEclipsePlugin.refreshProjectFlags(resource.getProject());
		}
	}

}
