/*******************************************************************************
 * Copyright (c) 2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bastian Doetsch      - implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands.extensions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.GpgCommand;
import com.vectrace.MercurialEclipse.commands.HgCommand;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.team.MercurialUtilities;

/**
 * Client for hg sign
 * 
 * @author Bastian Doetsch
 * 
 */
public class HgSignClient {

    
    
    /**
     * Calls hg sign. add a signature for the current or given revision If no
     * revision is given, the parent of the working directory is used, or tip if
     * no revision is checked out.
     * 
     * @param directory
     *            the current project (working directory)
     * @param cs
     *            ChangeSet, may be null
     * @param key
     *            the keyId to use
     * @param message
     *            commit message
     * @param user
     *            user name for commit
     * @param local
     *            flag, if signing is only local
     * @param force
     *            flag to even sign if sigfile is changed
     * @param noCommit
     *            flag, if commit shall happen (invalidates params message and
     *            user)
     * @param passphrase
     *            the passphrase or null
     * @author Bastian Doetsch
     * @return
     * 
     */
    public static String sign(File directory, ChangeSet cs, String key,
            String message, String user, boolean local, boolean force,
            boolean noCommit, String passphrase) throws HgException {
        HgCommand command = new HgCommand("sign", directory, true); //$NON-NLS-1$
        File file = new File("me.gpg.tmp"); //$NON-NLS-1$
        String cmd = "gpg.cmd=".concat( //$NON-NLS-1$
                MercurialUtilities.getGpgExecutable(true)).concat(
                " --batch --no-tty --armor"); //$NON-NLS-1$
        if (passphrase != null && passphrase.length() > 0) {
            FileWriter fw = null;
            try {
                fw = new FileWriter(file);
                fw.write(passphrase.concat("\n")); //$NON-NLS-1$
                fw.flush();
                cmd = cmd.concat(" --passphrase-file ").concat( //$NON-NLS-1$
                        file.getCanonicalFile().getCanonicalPath());
            } catch (IOException e) {
                throw new HgException(e.getMessage());
            } finally {
                if (fw != null) {
                    try {
                        fw.close();
                    } catch (Exception e) {
                        MercurialEclipsePlugin.logError(e);
                    }
                }
            }
        }
        command.addOptions("-k", key, "--config", cmd); //$NON-NLS-1$ //$NON-NLS-2$
        if (local) {
            command.addOptions("-l"); //$NON-NLS-1$
        }
        if (force) {
            command.addOptions("-f"); //$NON-NLS-1$
        }
        if (noCommit) {
            command.addOptions("--no-commit"); //$NON-NLS-1$
        } else {
            command.addOptions("-m", message, "-u", user); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        command.addOptions(cs.getChangeset());
        String result;
        try {
            result = command.executeToString();
            return result;
        } finally {
            if (file.delete() == false) {
                throw new HgException(file.getName()+" could not be deleted.");
            }
        }
    }

    public static String getPrivateKeyList() throws HgException {
        List<String> getKeysCmd = new ArrayList<String>();
        getKeysCmd.add(MercurialUtilities.getGpgExecutable(true));
        getKeysCmd.add("-k"); //$NON-NLS-1$
        getKeysCmd.add("-v"); //$NON-NLS-1$
        getKeysCmd.add("0"); //$NON-NLS-1$
        GpgCommand command = new GpgCommand(getKeysCmd, ResourcesPlugin
                .getWorkspace().getRoot().getLocation().toFile(), false);
        return new String(command.executeToBytes());
    }
}
