package com.vectrace.MercurialEclipse.commands;

import org.eclipse.core.resources.IFile;

import com.vectrace.MercurialEclipse.exception.HgException;

public class HgAnnotateClient {

    public static String getAnnotation(IFile file) throws HgException {
        HgCommand command = new HgCommand("annotate", file.getProject(), true); //$NON-NLS-1$
        command.addOptions("--user", "--number", "--changeset", "--date"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        command.addFiles(file);
        return command.executeToString();
    }
}
