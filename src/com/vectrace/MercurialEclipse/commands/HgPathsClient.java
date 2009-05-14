package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.utils.IniFile;

public class HgPathsClient {
    public static final String DEFAULT = "default"; //$NON-NLS-1$
    public static final String DEFAULT_PULL = "default-pull"; //$NON-NLS-1$
    public static final String DEFAULT_PUSH = "default-push"; //$NON-NLS-1$
    public static final String PATHS_LOCATION = "/.hg/hgrc"; //$NON-NLS-1$
    public static final String PATHS_SECTION = "paths"; //$NON-NLS-1$

    public static Map<String, String> getPaths(IProject project)
            throws HgException {
        
        
        
        
        File hgrc = new File (project.getLocation()+PATHS_LOCATION);
        
        if (!hgrc.exists())
            return new HashMap<String, String>();

        Map<String,String> paths = new HashMap<String,String>();
        
        try {
            FileInputStream input = new FileInputStream(hgrc);
            IniFile ini = new IniFile(hgrc.toURL());
            Map<String,String> section = ini.getSection(PATHS_SECTION);
            if (section != null) {
                paths.putAll(section);
            }
            input.close();
        } catch (IOException e) {
            MercurialEclipsePlugin.logError(e);
            // TODO: Fix log message
            throw new HgException("Unable to read paths", e);
        }
        
        return paths;
    }

}
