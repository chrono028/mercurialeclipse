/*******************************************************************************
 * Copyright (c) 2005-2010 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * lordofthepigs	implementation
 *******************************************************************************/
package com.vectrace.MercurialEclipse.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.utils.IniFile;

/**
 * @author lordofthepigs
 *
 */
public class HgSubreposClient extends AbstractClient {

	private static final String HGSUB = ".hgsub";
	private static final String HGDIR = ".hg";

	/**
	 * Returns the list of the subrepositories of the specified Hg repository that are cloned inside the working copy.
	 */
	public static Set<HgRoot> findSubrepositories(HgRoot parent){
		File parentDir = parent.getAbsoluteFile();
		File hgsub = new File(parentDir, HGSUB);

		if(!hgsub.exists() || !hgsub.isFile()){
			return new HashSet<HgRoot>();
		}

		Map<String, String> subrepos;
		try {
			IniFile iniFile = new IniFile(hgsub.getAbsolutePath());
			subrepos = iniFile.getSection(null);

		} catch (FileNotFoundException e) {
			// this shouldn't happen because we checked for existence of the file before, but who knows,
			// bad timing happens...
			MercurialEclipsePlugin.logError(e);
			return new HashSet<HgRoot>();
		}

		Set<HgRoot> result = new HashSet<HgRoot>();
		for(String subReposRootPath : subrepos.keySet()){
			File subReposRootDir = new File(parent.getAbsoluteFile(), subReposRootPath);
			if(!subReposRootDir.exists()){
				// for some reason the subrepos was not cloned or disappeared, just ignore it
				continue;
			}
			File subRepoHg = new File(subReposRootDir, HGDIR);
			if(subRepoHg.exists() && subRepoHg.isDirectory()){
				// we are reasonably sure that an HgRoot really exists in subReposRootDir
				try{
					result.add(new HgRoot(subReposRootDir));
				}catch(IOException ioe){
					MercurialEclipsePlugin.logError(ioe);
				}
			}
		}

		return result;
	}

	public static Set<HgRoot> findSubrepositoriesRecursively(HgRoot root){
		Set<HgRoot> found = new HashSet<HgRoot>();
		doFindSubrepositoriesRecursively(root, found);
		return found;
	}

	private static void doFindSubrepositoriesRecursively(HgRoot root, Set<HgRoot> found){
		Set<HgRoot> subs = findSubrepositories(root);
		found.addAll(subs);
		for(HgRoot sub : subs){
			doFindSubrepositoriesRecursively(sub, found);
		}
	}
}
