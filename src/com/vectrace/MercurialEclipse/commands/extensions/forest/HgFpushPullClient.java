/*******************************************************************************
 * Copyright (c) 2008 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bastian Doetsch           - implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands.extensions.forest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.commands.AbstractShellCommand;
import com.vectrace.MercurialEclipse.commands.HgCommand;
import com.vectrace.MercurialEclipse.commands.HgPushPullClient;
import com.vectrace.MercurialEclipse.commands.RefreshWorkspaceStatusJob;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.preferences.MercurialPreferenceConstants;
import com.vectrace.MercurialEclipse.storage.HgRepositoryLocation;

public class HgFpushPullClient extends HgPushPullClient {

    public static String fpush(File forestRoot, HgRepositoryLocation repo,
            String revision, int timeout, File snapFile) throws CoreException {

        try {
            AbstractShellCommand command = new HgCommand("fpush",
                    forestRoot, true);
            command
                    .setUsePreferenceTimeout(MercurialPreferenceConstants.PUSH_TIMEOUT);

            if (snapFile != null) {
                command.addOptions("--snapfile", snapFile.getCanonicalPath());
            }

            if (revision != null && revision.length() > 0) {
                command.addOptions("-r", revision.trim());
            }

            URI uri = repo.getUri();
            if (uri != null) {
                command.addOptions(uri.toASCIIString());
            } else {
                command.addOptions(repo.getLocation());
            }

            String result = new String(command.executeToBytes(timeout));
            Set<IProject> projects = MercurialEclipsePlugin.getRepoManager().getAllRepoLocationProjects(repo);
            for (IProject project : projects) {
                updateAfterPush(result, project, repo);
            }

            return result;
        } catch (IOException e) {
            throw new HgException(e.getLocalizedMessage(), e);
        }
    }

    public static String fpull(File forestRoot, HgRepositoryLocation repo,
            boolean update, boolean timeout, ChangeSet changeset,
            boolean walkHg, File snapFile, boolean partial) throws HgException {

        URI uri = repo.getUri();
        String pullSource;
        if (uri != null) {
            pullSource = uri.toASCIIString();
        } else {
            pullSource = repo.getLocation();
        }
        try {
            AbstractShellCommand command = new HgCommand("fpull",
                    forestRoot, true);

            if (update) {
                command.addOptions("--update");
            }

            if (changeset != null) {
                command.addOptions("--rev", changeset.getChangeset());
            }

            if (snapFile != null) {
                command.addOptions("--snapfile", snapFile.getCanonicalPath());
            }

            if (walkHg) {
                command.addOptions("--walkhg", "true");
            }

            if (partial) {
                command.addOptions("--partial");
            }

            command.addOptions(pullSource);

            String result;
            if (timeout) {
                command
                        .setUsePreferenceTimeout(MercurialPreferenceConstants.PULL_TIMEOUT);
                result = new String(command.executeToBytes());
            } else {
                result = new String(command.executeToBytes(Integer.MAX_VALUE));
            }
            if(update) {
                Set<IProject> projects = MercurialEclipsePlugin.getRepoManager().getAllRepoLocationProjects(repo);
                for (IProject project : projects) {
                    new RefreshWorkspaceStatusJob(project).schedule();
                }
            }
            return result;
        } catch (IOException e) {
            throw new HgException(e.getLocalizedMessage(), e);
        }
    }
}
