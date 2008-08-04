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
package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;

import com.vectrace.MercurialEclipse.exception.HgException;

/**
 * Base client class
 * @author bastian
 * 
 */
public abstract class AbstractClient {
    /**
     * @param resource
     * @return
     */
    protected static File getWorkingDirectory(IResource resource) {
        Assert.isNotNull(resource);
        IResource myWorkDir = resource;
        if (resource.getType()==IResource.FILE) {
            myWorkDir = resource.getParent();
        }
        return myWorkDir.getLocation().toFile();
    }
    
    /**
     * @param path
     * @return
     */
    protected static File getWorkingDirectory(IPath path) {
        Assert.isNotNull(path);
        if (path.toFile().isFile()) {
            return path.toFile().getParentFile();
        }
        return path.toFile();
    }
    
    protected static File getWorkingDirectory(File file) {        
        if (file.isFile()) {
            return file.getParentFile();
        }
        return file;
    }

    public AbstractClient() {
    }
    
    /**
     * @return
     * @throws HgException
     */
    public static File getHgRoot(IResource res) throws HgException {
        Assert.isNotNull(res);
        return HgRootClient.getHgRootAsFile(res);                
    }
    
    public static File getHgRoot(IPath path) throws HgException {
        Assert.isNotNull(path);
        File file = HgRootClient.getHgRoot(path.toFile());
        return file;
    }
    
    public static File getHgRoot(File file) throws HgException {
        File root = HgRootClient.getHgRoot(file);
        return root;
    }
    
    static List<File> toFiles(List<IResource> files) {
        List<File> toFiles = new ArrayList<File>();
        for (IResource r : files) {
            toFiles.add(r.getLocation().toFile());
        }
        return toFiles;
    }
    
    static List<String> toPaths(List<File> files) {
        List<String> paths = new ArrayList<String>();
        for (File f : files) {
            paths.add(f.getAbsolutePath());
        }
        return paths;
    }
    
}
