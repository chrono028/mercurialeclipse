/*******************************************************************************
 * Copyright (c) 2003, 2006 Subclipse project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Subclipse project committers - initial API and implementation
 ******************************************************************************/
package com.vectrace.MercurialEclipse.wizards;

import java.util.Properties;

import org.eclipse.jface.wizard.IWizard;
import org.eclipse.team.core.TeamException;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocation;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocationManager;

/**
 * Wizard to add a new location. Uses ConfigurationWizardMainPage for entering
 * informations about SVN repository location
 */
public class NewLocationWizard extends HgWizard {
    
    public NewLocationWizard() {
        super("Create new repository location");
    }

    public NewLocationWizard(Properties initialProperties) {
        this();
        this.properties = initialProperties;
    }
    
    @Override
    public void addPages() {
        mainPage = createPage("RepoCreationPage", "Create new repository",
                "wizards/share_wizban.png",
                "Here you can create a new repository location.");
        addPage(mainPage);
    }

    /**
     * @see IWizard#performFinish
     */
    @Override
    public boolean performFinish() {
        super.performFinish();
        Properties props = mainPage.getProperties();
        final HgRepositoryLocation[] root = new HgRepositoryLocation[1];
        HgRepositoryLocationManager provider = MercurialEclipsePlugin
                .getRepoManager();
        try {
            root[0] = provider.createRepository(props);
            provider.addRepoLocation(root[0]);
            return true;
        } catch (TeamException e) {
            MercurialEclipsePlugin.logError(e);
        }
        return false;
    }
}
