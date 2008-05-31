/*******************************************************************************
 * Copyright (c) 2005-2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * bastian	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.wizards.mq;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.actions.HgOperation;
import com.vectrace.MercurialEclipse.commands.mq.HgQInitClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.wizards.HgWizard;

/**
 * @author bastian
 *
 */
public class QInitWizard extends HgWizard {
    private QInitWizardPage page = null;
    
    private class InitOperation extends HgOperation {

        /**
         * @param context
         */
        public InitOperation(IRunnableContext context) {
            super(context);
        }

        /* (non-Javadoc)
         * @see com.vectrace.MercurialEclipse.actions.HgOperation#getActionDescription()
         */
        @Override
        protected String getActionDescription() {
            return "Initializing queue repository...";
        }
        
        /* (non-Javadoc)
         * @see com.vectrace.MercurialEclipse.actions.HgOperation#run(org.eclipse.core.runtime.IProgressMonitor)
         */
        @Override
        public void run(IProgressMonitor monitor)
                throws InvocationTargetException, InterruptedException {
            monitor.beginTask("Initializing queue repository", 2);
            monitor.worked(1);
            monitor.subTask("Calling Mercurial...");
            
            try {
                HgQInitClient.init(resource, page.getCheckBox().getSelection());
                monitor.worked(1);
                monitor.done();                
            } catch (HgException e) {                
                throw new InvocationTargetException(e,e.getLocalizedMessage());
            }            
        }
        
    }
    
    private IResource resource;

    /**
     * @param windowTitle
     */
    public QInitWizard(IResource resource) {
        super("Mercurial Queue Initialization Wizard");
        this.resource = resource;
        setNeedsProgressMonitor(true);        
        page = new QInitWizardPage("QInitPage","Initialize queue repository",null,null,resource); 
        initPage("Initialize a new mercurial queue repository", page);
        addPage(page);
    }
    
    /* (non-Javadoc)
     * @see com.vectrace.MercurialEclipse.wizards.HgWizard#performFinish()
     */
    @Override
    public boolean performFinish() {
        InitOperation initOperation = new InitOperation(getContainer());
        try {
            getContainer().run(false, false, initOperation);
        } catch (Exception e) {
            MercurialEclipsePlugin.logError(e);
            page.setErrorMessage(e.getLocalizedMessage());
            return false;
        }
        return true;
    }

}
