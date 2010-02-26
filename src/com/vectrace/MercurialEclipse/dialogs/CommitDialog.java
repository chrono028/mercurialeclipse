/*******************************************************************************
 * Copyright (c) 2006-2009 VecTrace (Zingo Andersen) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eclipse.org - see CommitWizardCommitPage
 *     Software Balm Consulting Inc (Peter Hunnisett <peter_hge at softwarebalm dot com>) - implementation
 *     Bastian Doetsch - Added spellchecking and some other stuff
 *     StefanC - many updates
 *     Zingo Andersen - some updates
 *     Andrei Loskutov (Intland) - bug fixes
 *     Adam Berkes (Intland) - bug fixes
 *******************************************************************************/
package com.vectrace.MercurialEclipse.dialogs;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.ProgressMonitorPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.DefaultMarkerAnnotationAccess;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.texteditor.spelling.SpellingAnnotation;

import com.vectrace.MercurialEclipse.MercurialEclipsePlugin;
import com.vectrace.MercurialEclipse.SafeWorkspaceJob;
import com.vectrace.MercurialEclipse.commands.HgAddClient;
import com.vectrace.MercurialEclipse.commands.HgCommitClient;
import com.vectrace.MercurialEclipse.commands.HgLogClient;
import com.vectrace.MercurialEclipse.commands.HgRemoveClient;
import com.vectrace.MercurialEclipse.commands.extensions.mq.HgQFinishClient;
import com.vectrace.MercurialEclipse.commands.extensions.mq.HgQImportClient;
import com.vectrace.MercurialEclipse.commands.extensions.mq.HgQRefreshClient;
import com.vectrace.MercurialEclipse.exception.HgException;
import com.vectrace.MercurialEclipse.model.ChangeSet;
import com.vectrace.MercurialEclipse.model.HgRoot;
import com.vectrace.MercurialEclipse.mylyn.MylynFacadeFactory;
import com.vectrace.MercurialEclipse.storage.HgCommitMessageManager;
import com.vectrace.MercurialEclipse.team.ActionRevert;
import com.vectrace.MercurialEclipse.team.MercurialTeamProvider;
import com.vectrace.MercurialEclipse.team.cache.LocalChangesetCache;
import com.vectrace.MercurialEclipse.team.cache.RefreshRootJob;
import com.vectrace.MercurialEclipse.ui.ChangesetInfoTray;
import com.vectrace.MercurialEclipse.ui.CommitFilesChooser;
import com.vectrace.MercurialEclipse.ui.SWTWidgetHelper;
import com.vectrace.MercurialEclipse.utils.ResourceUtils;
import com.vectrace.MercurialEclipse.utils.StringUtils;

/**
 * A commit dialog box allowing choosing of what files to commit and a commit message for those
 * files. Untracked files may also be chosen.
 */
public class CommitDialog extends TitleAreaDialog {
	public static final String FILE_MODIFIED = Messages.getString("CommitDialog.modified"); //$NON-NLS-1$
	public static final String FILE_ADDED = Messages.getString("CommitDialog.added"); //$NON-NLS-1$
	public static final String FILE_REMOVED = Messages.getString("CommitDialog.removed"); //$NON-NLS-1$
	public static final String FILE_UNTRACKED = Messages.getString("CommitDialog.untracked"); //$NON-NLS-1$
	public static final String FILE_DELETED = Messages.getString("CommitDialog.deletedInWorkspace"); //$NON-NLS-1$
	public static final String FILE_CLEAN = Messages.getString("CommitDialog.clean"); //$NON-NLS-1$
	private static final String DEFAULT_COMMIT_MESSAGE = Messages
			.getString("CommitDialog.defaultCommitMessage"); //$NON-NLS-1$

	protected String defaultCommitMessage;
	private Combo oldCommitComboBox;
	private ISourceViewer commitTextBox;
	protected CommitFilesChooser commitFilesList;
	private List<IResource> resourcesToAdd;
	private List<IResource> resourcesToCommit;
	private List<IResource> resourcesToRemove;
	private String commitMessage;
	private final IDocument commitTextDocument;
	private SourceViewerDecorationSupport decorationSupport;
	private final List<IResource> inResources;
	private Text userTextField;
	private String user;
	private Button revertCheckBox;
	private boolean filesSelectable;
	private final HgRoot root;
	private String commitResult;
	private Button closeBranchCheckBox;
	private Button amendCheckbox;
	private ChangeSet currentChangeset;
	private ProgressMonitorPart monitor;
	private Sash sash;
	private ChangesetInfoTray tray;
	private Label leftSeparator;
	private Label rightSeparator;
	private Control trayControl;

	/**
	 *
	 * @param shell
	 * @param hgRoot
	 *            non null
	 * @param resources
	 *            might be null
	 */
	public CommitDialog(Shell shell, HgRoot hgRoot, List<IResource> resources) {
		super(shell);
		this.root = hgRoot;
		setShellStyle(getShellStyle() | SWT.RESIZE | SWT.TITLE);
		defaultCommitMessage = DEFAULT_COMMIT_MESSAGE;
		setBlockOnOpen(false);
		inResources = resources;
		commitTextDocument = new Document();
		filesSelectable = true;
	}

	public String getCommitMessage() {
		return commitMessage;
	}

	public List<IResource> getResourcesToCommit() {
		return resourcesToCommit;
	}

	public List<IResource> getResourcesToAdd() {
		return resourcesToAdd;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = SWTWidgetHelper.createComposite(parent, 1);
		container.addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
				if (e.character == SWT.CR && e.stateMask == SWT.MOD1) {
					okPressed();
					e.doit = false;
				}
			}

			public void keyPressed(KeyEvent e) {
			}
		});

		createCommitTextBox(container);
		createOldCommitCombo(container);
		createUserCommitCombo(container);
		createCloseBranchCheckBox(container);
		createAmendCheckBox(container);
		createRevertCheckBox(container);
		createFilesList(container);

		getShell().setText(Messages.getString("CommitDialog.window.title")); //$NON-NLS-1$
		setTitle(Messages.getString("CommitDialog.title")); //$NON-NLS-1$";
		monitor = new ProgressMonitorPart(container, null);
		monitor.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		monitor.setVisible(false);
		return container;
	}

	@Override
	protected Control createContents(Composite parent) {
		Control control = super.createContents(parent);
		final String initialCommitMessage = MylynFacadeFactory.getMylynFacade()
				.getCurrentTaskComment(
						inResources == null ? null : inResources.toArray(new IResource[0]));
		setCommitMessage(initialCommitMessage);

		commitTextBox.getTextWidget().setFocus();
		commitTextBox.getTextWidget().selectAll();
		return control;
	}

	private void validateCommitMessage(final String message) {
		if (StringUtils.isEmpty(message) || DEFAULT_COMMIT_MESSAGE.equals(message)) {
			setErrorMessage(Messages.getString("CommitDialog.message")); // ";
			getButton(IDialogConstants.OK_ID).setEnabled(false);
		} else {
			setErrorMessage(null); // ";
			setMessage(Messages.getString("CommitDialog.message")); // ";
			getButton(IDialogConstants.OK_ID).setEnabled(true);
		}
	}

	protected void createRevertCheckBox(Composite container) {
		revertCheckBox = SWTWidgetHelper.createCheckBox(container, Messages
				.getString("CommitDialog.revertCheckBoxLabel.revertUncheckedResources")); //$NON-NLS-1$
	}

	protected void createCloseBranchCheckBox(Composite container) {
		closeBranchCheckBox = SWTWidgetHelper.createCheckBox(container, Messages
				.getString("CommitDialog.closeBranch"));
	}

	protected void createAmendCheckBox(Composite container) {
		try {
			currentChangeset = LocalChangesetCache.getInstance().getChangesetForRoot(root);
			if (currentChangeset != null) {
				String branch = MercurialTeamProvider.getCurrentBranch(root);
				String label = Messages.getString("CommitDialog.amendCurrentChangeset1") + currentChangeset.getChangesetIndex() //$NON-NLS-1$
						+ ":" + currentChangeset.getNodeShort() + "@" + branch + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				amendCheckbox = SWTWidgetHelper.createCheckBox(container, label);
				amendCheckbox.addSelectionListener(new SelectionListener() {

					public void widgetSelected(SelectionEvent e) {
						if (amendCheckbox.getSelection() && currentChangeset != null) {
							try {
								openSash();
							} catch (HgException e1) {
								setErrorMessage("Cannot amend.");
								closeSash();
								amendCheckbox.setSelection(false);
								amendCheckbox.setEnabled(false);
							}
						} else {
							closeSash();
						}
					}

					public void widgetDefaultSelected(SelectionEvent e) {
						widgetSelected(e);
					}
				});
			}
		} catch (HgException e) {
			MercurialEclipsePlugin.logError(e);
			setErrorMessage(e.getLocalizedMessage());
		}
	}

	protected void createFilesList(Composite container) {
		SWTWidgetHelper.createLabel(container, Messages.getString("CommitDialog.selectFiles"));
		commitFilesList = new CommitFilesChooser(container, areFilesSelectable(), inResources,
				true, true, false);

		IResource[] mylynTaskResources = MylynFacadeFactory.getMylynFacade()
				.getCurrentTaskResources();
		if (mylynTaskResources != null) {
			commitFilesList.setSelectedResources(Arrays.asList(mylynTaskResources));
		}
	}

	private boolean areFilesSelectable() {
		return filesSelectable;
	}

	public void setFilesSelectable(boolean on) {
		filesSelectable = on;
	}

	private void createUserCommitCombo(Composite container) {
		Composite comp = SWTWidgetHelper.createComposite(container, 2);
		SWTWidgetHelper.createLabel(comp, Messages.getString("CommitDialog.userLabel.text"));
		userTextField = SWTWidgetHelper.createTextField(comp);
		user = getInitialCommitUserName();
		userTextField.setText(user);
	}

	protected String getInitialCommitUserName() {
		return HgCommitMessageManager.getDefaultCommitName(root);
	}

	private void createCommitTextBox(Composite container) {
		setMessage(Messages.getString("CommitDialog.commitTextLabel.text"));

		commitTextBox = new SourceViewer(container, null, SWT.V_SCROLL | SWT.MULTI | SWT.BORDER
				| SWT.WRAP);
		commitTextBox.setEditable(true);
		commitTextBox.getTextWidget().setLayoutData(SWTWidgetHelper.getFillGD(100));

		// set up spell-check annotations
		decorationSupport = new SourceViewerDecorationSupport(commitTextBox, null,
				new DefaultMarkerAnnotationAccess(), EditorsUI.getSharedTextColors());

		AnnotationPreference pref = EditorsUI.getAnnotationPreferenceLookup()
				.getAnnotationPreference(SpellingAnnotation.TYPE);

		decorationSupport.setAnnotationPreference(pref);
		decorationSupport.install(EditorsUI.getPreferenceStore());

		commitTextBox.configure(new TextSourceViewerConfiguration(EditorsUI.getPreferenceStore()));
		AnnotationModel annotationModel = new AnnotationModel();
		commitTextBox.setDocument(commitTextDocument, annotationModel);
		commitTextBox.getTextWidget().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				decorationSupport.uninstall();
			}
		});

		commitTextBox.addTextListener(new ITextListener() {
			public void textChanged(TextEvent event) {
				validateCommitMessage(commitTextBox.getDocument().get());
			}
		});
	}

	private void createOldCommitCombo(Composite container) {
		final String oldCommits[] = MercurialEclipsePlugin.getCommitMessageManager()
				.getCommitMessages();
		if (oldCommits.length > 0) {
			oldCommitComboBox = SWTWidgetHelper.createCombo(container);
			oldCommitComboBox.add(Messages.getString("CommitDialog.oldCommitMessages")); //$NON-NLS-1$
			oldCommitComboBox.setText(Messages.getString("CommitDialog.oldCommitMessages"));
			for (int i = 0; i < oldCommits.length; i++) {
				/*
				 * Add text to the combo but replace \n with <br> to get a one-liner
				 */
				oldCommitComboBox.add(oldCommits[i].replaceAll("\\n", "<br>"));
			}
			oldCommitComboBox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (oldCommitComboBox.getSelectionIndex() != 0) {
						commitTextDocument
								.set(oldCommits[oldCommitComboBox.getSelectionIndex() - 1]);
						commitTextBox.setSelectedRange(0, oldCommits[oldCommitComboBox
								.getSelectionIndex() - 1].length());
					}

				}
			});

		}
	}

	/**
	 * Override the OK button pressed to capture the info we want first and then call super.
	 */
	@Override
	protected void okPressed() {
		try {
			IProgressMonitor pm = monitor;
			monitor.setVisible(true);
			monitor.attachToCancelComponent(getButton(IDialogConstants.CANCEL_ID));
			pm.beginTask("Committing...", 20);
			pm.subTask("Determining resources to add, remove and to commit.");
			resourcesToAdd = commitFilesList.getCheckedResources(FILE_UNTRACKED);
			resourcesToCommit = commitFilesList.getCheckedResources();
			resourcesToRemove = commitFilesList.getCheckedResources(FILE_DELETED);
			pm.worked(3);
			commitMessage = commitTextDocument.get();
			user = userTextField.getText();
			if (user == null || user.length() == 0) {
				user = getInitialCommitUserName();
			}

			// amend changeset
			ChangeSet cs = null;
			if (amendCheckbox != null && amendCheckbox.getSelection() && currentChangeset != null) {
				// only one root allowed when amending
				Map<HgRoot, List<IResource>> map = ResourceUtils.groupByRoot(resourcesToCommit);
				if (map.size() > 1) {
					throw new HgException(Messages.getString("CommitDialog.amendingOnlyForOneRoot"));
				}

				// determine current changeset
				int startRev = currentChangeset.getChangesetIndex();

				// update to get file status information
				pm.subTask("Amending changeset " + currentChangeset.getChangesetIndex()
						+ ". Fetching changeset information.");
				Map<IPath, Set<ChangeSet>> changesets = HgLogClient.getRootLog(root, 1, startRev,
						true);
				pm.worked(1);
				if (changesets != null && changesets.size() > 0) {
					cs = changesets.get(root.getIPath()).iterator().next();
					pm.worked(1);
					boolean ok = confirmHistoryRewrite();
					if (ok) {
						// import old current changeset into MQ
						pm.subTask("Importing changeset into MQ.");
						HgQImportClient.qimport(root, true, true, false, new ChangeSet[] { cs },
								null);
						pm.worked(1);
					} else {
						throw new HgException(Messages.getString("CommitDialog.abortedAmending"));
					}
				} else {
					setErrorMessage(Messages.getString("CommitDialog.noChangesetToAmend"));
				}
			}

			// add new resources
			pm.subTask("Adding selected unknown resources to repository.");
			HgAddClient.addResources(resourcesToAdd, null);
			pm.worked(1);

			// remove deleted resources
			pm.subTask("Removing selected deleted resources from repository.");
			HgRemoveClient.removeResources(resourcesToRemove);
			pm.worked(1);

			// commit all
			String messageToCommit = getCommitMessage();

			boolean closeBranch = closeBranchCheckBox != null ? closeBranchCheckBox.getSelection()
					: false;

			commitResult = performCommit(messageToCommit, closeBranch, cs);
			pm.worked(1);

			/* Store commit message in the database if not the default message */
			if (!commitMessage.equals(defaultCommitMessage)) {
				pm.subTask("Storing the commit message for later use.");
				MercurialEclipsePlugin.getCommitMessageManager().saveCommitMessage(commitMessage);
			}
			pm.worked(1);

			// revertCheckBox can be null if this is a merge dialog
			if (revertCheckBox != null && revertCheckBox.getSelection()) {
				pm.subTask("Reverting resources not selected.");
				revertResources();
			}

			super.okPressed();
			pm.done();
		} catch (CoreException e) {
			setErrorMessage(e.getLocalizedMessage());
			MercurialEclipsePlugin.logError(e);
		}
		monitor.removeFromCancelComponent(getButton(IDialogConstants.CANCEL_ID));
		monitor.setVisible(false);
	}

	/**
	 * @return
	 */
	private boolean confirmHistoryRewrite() {
		return MessageDialog.openQuestion(getShell(), Messages
				.getString("CommitDialog.reallyAmendAndRewriteHistory"), //$NON-NLS-1$
				Messages.getString("CommitDialog.amendWarning1")
						+ Messages.getString("CommitDialog.amendWarning2")
						+ Messages.getString("CommitDialog.amendWarning3"));
	}

	/**
	 * @return the result of the commit operation (hg output), if any. If there was no commit or
	 *         commit ouput was null, return empty string
	 */
	public String getCommitResult() {
		return commitResult != null ? commitResult : "";
	}

	protected String performCommit(String messageToCommit, boolean closeBranch)
			throws CoreException {
		return performCommit(messageToCommit, closeBranch, null);
	}

	protected String performCommit(String messageToCommit, boolean closeBranch, ChangeSet cs)
			throws CoreException {
		IProgressMonitor pm = monitor;
		if (amendCheckbox != null && amendCheckbox.getSelection() && cs != null) {
			// refresh patch with added/removed/changed files
			pm.subTask("Refreshing MQ amend patch with newly added/removed/changed files.");
			String result = HgQRefreshClient
					.refresh(root, true, resourcesToCommit, messageToCommit);
			pm.worked(1);
			// remove patch and promote it to a new changeset
			pm.subTask("Removing amend patch from MQ and promoting it to repository.");
			result = HgQFinishClient.finish(root, cs.getChangesetIndex() + ".diff");
			pm.worked(1);
			new RefreshRootJob(
					Messages.getString("CommitDialog.refreshingAfterAmend1") + root.getName() //$NON-NLS-1$
							+ Messages.getString("CommitDialog.refreshingAfterAmend2"), root, RefreshRootJob.LOCAL_AND_OUTGOING) //$NON-NLS-1$
					.schedule();
			return result;
		}

		if (!filesSelectable && resourcesToCommit.isEmpty()) {
			// enforce commit anyway
			return HgCommitClient.commitResources(root, closeBranch, user, messageToCommit, pm);
		}
		return HgCommitClient.commitResources(resourcesToCommit, user, messageToCommit, pm,
				closeBranch);
	}

	private void revertResources() {
		final List<IResource> revertResources = commitFilesList.getUncheckedResources(FILE_ADDED,
				FILE_DELETED, FILE_MODIFIED, FILE_REMOVED);
		final List<IResource> untrackedResources = commitFilesList
				.getUncheckedResources(FILE_UNTRACKED);
		new SafeWorkspaceJob(Messages.getString("CommitDialog.revertJob.RevertingFiles")) {
			@Override
			protected IStatus runSafe(IProgressMonitor m) {
				ActionRevert action = new ActionRevert();
				try {
					action.doRevert(m, revertResources, untrackedResources, false, null);
				} catch (HgException e) {
					MercurialEclipsePlugin.logError(e);
					return e.getStatus();
				}
				return super.runSafe(m);
			}
		}.schedule();
	}

	public List<IResource> getResourcesToRemove() {
		return resourcesToRemove;
	}

	private void setCommitMessage(String msg) {
		if (msg == null) {
			msg = defaultCommitMessage;
		}
		commitTextDocument.set(msg);
		commitTextBox.setSelectedRange(0, msg.length());
	}

	public String getUser() {
		return user;
	}

	public void setDefaultCommitMessage(String defaultCommitMessage) {
		this.defaultCommitMessage = defaultCommitMessage;
	}

	public Button getCloseBranchCheckBox() {
		return closeBranchCheckBox;
	}

	private void openSash() throws HgException {
		IProgressMonitor pm = this.monitor;
		monitor.setVisible(true);
		pm.beginTask("Loading changeset information for amend", 2);
		// only one root allowed when amending
		Map<HgRoot, List<IResource>> map = ResourceUtils.groupByRoot(resourcesToCommit);
		if (map.size() > 1) {
			setMessage(Messages.getString("CommitDialog.amendingOnlyForOneRoot"));
			amendCheckbox.setEnabled(false);
			amendCheckbox.setSelection(false);
		}

		// determine current changeset
		int startRev = currentChangeset.getChangesetIndex();

		// update to get file status information
		Map<IPath, Set<ChangeSet>> changesets = HgLogClient.getRootLog(root, 1, startRev, true);
		pm.worked(1);
		if (changesets != null && changesets.size() > 0) {
			currentChangeset = changesets.get(root.getIPath()).iterator().next();
			pm.worked(1);
		}
		pm.done();
		monitor.setVisible(false);
		ChangesetInfoTray t = new ChangesetInfoTray(currentChangeset);
		final Shell shell = getShell();
		leftSeparator = new Label(shell, SWT.SEPARATOR | SWT.VERTICAL);
		leftSeparator.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		sash = new Sash(shell, SWT.VERTICAL);
		sash.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		rightSeparator = new Label(shell, SWT.SEPARATOR | SWT.VERTICAL);
		rightSeparator.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		trayControl = t.createContents(shell);
		Rectangle clientArea = shell.getClientArea();
		final GridData data = new GridData(GridData.FILL_VERTICAL);
		data.widthHint = trayControl.computeSize(SWT.DEFAULT, clientArea.height).x;
		trayControl.setLayoutData(data);
		int trayWidth = leftSeparator.computeSize(SWT.DEFAULT, clientArea.height).x
				+ sash.computeSize(SWT.DEFAULT, clientArea.height).x
				+ rightSeparator.computeSize(SWT.DEFAULT, clientArea.height).x + data.widthHint;
		Rectangle bounds = shell.getBounds();
		shell.setBounds(bounds.x
				- ((Window.getDefaultOrientation() == SWT.RIGHT_TO_LEFT) ? trayWidth : 0),
				bounds.y, bounds.width + trayWidth, bounds.height);
		sash.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				if (event.detail != SWT.DRAG) {
					Rectangle rect = shell.getClientArea();
					int newWidth = rect.width - event.x
							- (sash.getSize().x + rightSeparator.getSize().x);
					if (newWidth != data.widthHint) {
						data.widthHint = newWidth;
						shell.layout();
					}
				}
			}
		});
		this.tray = t;
	}

	private void closeSash() {
		if (tray == null) {
			throw new IllegalStateException("Tray was not open"); //$NON-NLS-1$
		}
		int trayWidth = trayControl.getSize().x + leftSeparator.getSize().x + sash.getSize().x
				+ rightSeparator.getSize().x;
		trayControl.dispose();
		trayControl = null;
		tray = null;
		leftSeparator.dispose();
		leftSeparator = null;
		rightSeparator.dispose();
		rightSeparator = null;
		sash.dispose();
		sash = null;
		Shell shell = getShell();
		Rectangle bounds = shell.getBounds();
		shell.setBounds(bounds.x
				+ ((Window.getDefaultOrientation() == SWT.RIGHT_TO_LEFT) ? trayWidth : 0),
				bounds.y, bounds.width - trayWidth, bounds.height);
	}

}