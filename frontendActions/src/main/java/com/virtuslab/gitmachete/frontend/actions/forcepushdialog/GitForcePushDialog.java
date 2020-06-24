package com.virtuslab.gitmachete.frontend.actions.forcepushdialog;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.border.Border;

import com.intellij.dvcs.push.PrePushHandler;
import com.intellij.dvcs.push.PushController;
import com.intellij.dvcs.push.PushInfo;
import com.intellij.dvcs.push.PushSource;
import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.ui.PushActionBase;
import com.intellij.dvcs.push.ui.PushLog;
import com.intellij.dvcs.push.ui.VcsPushUi;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import net.miginfocom.swing.MigLayout;
import org.checkerframework.checker.guieffect.qual.UI;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

public class GitForcePushDialog extends DialogWrapper implements VcsPushUi {
  private static final String DIMENSION_KEY = "Vcs.Push.Dialog.v2";

  private static final int CENTER_PANEL_HEIGHT = 450;
  private static final int CENTER_PANEL_WIDTH = 800;

  private final Project project;
  private final PushLog listPanel;
  private final PushController pushController;
  private final Action pushAction;

  @UIEffect
  public GitForcePushDialog(
      Project project,
      java.util.Collection<? extends Repository> allRepos,
      java.util.List<? extends Repository> selectedRepositories,
      @Nullable Repository currentRepo,
      @Nullable PushSource pushSource) {
    super(project, /* canBeParent */ true,
        (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    this.project = project;
    var realAction = new PushActionImpl();
    var textWithMnemonic = realAction.getTemplatePresentation().getTextWithMnemonic();
    // setText(...) from PushActionImpl constructor guarantees that the text with mnemonic is not null
    assert textWithMnemonic != null : "Text with mnemonic is null";
    this.pushAction = new PushAction(new PushActionWrapper(realAction, textWithMnemonic));
    this.pushAction.putValue(DEFAULT_ACTION, Boolean.TRUE);

    var dialog = new VcsPushDialogAdapterHolder(project, selectedRepositories, currentRepo, /* gitForcePushDialog */ this)
        .getVcsPushDialog();

    this.pushController = new PushController(project, dialog, allRepos, selectedRepositories, currentRepo, pushSource);
    this.listPanel = pushController.getPushPanelLog();

    updateOkActions();
    setOKButtonText("Force Push");
    setOKButtonMnemonic('P');
    setTitle("Force Push Commits");
    init();
  }

  @Nullable
  @Override
  protected Border createContentPaneBorder() {
    return null;
  }

  @Nullable
  @Override
  @UIEffect
  protected JPanel createSouthAdditionalPanel() {
    return createSouthOptionsPanel();
  }

  @Override
  @UIEffect
  protected JComponent createSouthPanel() {
    JComponent southPanel = super.createSouthPanel();
    southPanel.setBorder(JBUI.Borders.empty(/* topAndBottom */ 8, /* leftAndRight */ 12));
    return southPanel;
  }

  @Override
  @UIEffect
  protected JComponent createCenterPanel() {
    JPanel panel = JBUI.Panels.simplePanel(/* hgap */ 0, /* vgap */ 2)
        .addToCenter(listPanel)
        .addToBottom(createOptionsPanel());
    listPanel.setPreferredSize(new JBDimension(CENTER_PANEL_WIDTH, CENTER_PANEL_HEIGHT));
    return panel;
  }

  protected JPanel createOptionsPanel() {
    return new JPanel(new MigLayout(/* layoutConstraints */ "ins 0 0, flowy")) {
      @Override
      public Component add(Component comp) {
        JPanel wrapperPanel = new BorderLayoutPanel().addToCenter(comp);
        wrapperPanel.setBorder(JBUI.Borders.empty(5, 15, 0, 0));
        return super.add(wrapperPanel);
      }
    };
  }

  @UIEffect
  private JPanel createSouthOptionsPanel() {
    return new JPanel(new MigLayout(/* layoutConstraints */ "ins 0 ${JBUI.scale(20)}px 0 0, flowx, gapx ${JBUI.scale(16)}px"));
  }

  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_KEY;
  }

  @Override
  @UIEffect
  public JRootPane getRootPane(@UnknownInitialization GitForcePushDialog this) {
    return super.getRootPane();
  }

  @Nullable
  @Override
  @UIEffect
  protected ValidationInfo doValidate() {
    updateOkActions();
    return null;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  @UIEffect
  protected void doOKAction() {
    push(/* forcePush */ true);
  }

  @Override
  @UIEffect
  protected Action[] createActions() {
    return new Action[]{pushAction, getCancelAction()};
  }

  @Override
  @UIEffect
  public boolean canPush(@UnknownInitialization GitForcePushDialog this) {
    return pushController != null && pushController.isPushAllowed();
  }

  @Override
  @UIEffect
  public java.util.Map<PushSupport<Repository, PushSource, PushTarget>, java.util.Collection<PushInfo>> getSelectedPushSpecs() {
    return pushController.getSelectedPushSpecs();
  }

  @Nullable
  @Override
  @UIEffect
  public JComponent getPreferredFocusedComponent() {
    return listPanel.getPreferredFocusedComponent();
  }

  @Override
  protected Action getOKAction() {
    return pushAction;
  }

  @Override
  @UIEffect
  public void push(boolean forcePush) {
    executeAfterRunningPrePushHandlers(
        new Task.Backgroundable(project, /* title */ "Force Pushing...", /* canBeCancelled */ true) {
          @Override
          public void run(ProgressIndicator indicator) {
            pushController.push(forcePush);
          }
        });
  }

  @Override
  @UIEffect
  public void executeAfterRunningPrePushHandlers(Task.Backgroundable activity) {
    PrePushHandler.Result result = runPrePushHandlersInModalTask();
    if (result == PrePushHandler.Result.OK) {
      activity.queue();
      close(OK_EXIT_CODE);
    } else if (result == PrePushHandler.Result.ABORT_AND_CLOSE) {
      doCancelAction();
    } else if (result == PrePushHandler.Result.ABORT) {
      // cancel push and leave the push dialog open
    }
  }

  @UIEffect
  public PrePushHandler.Result runPrePushHandlersInModalTask() {
    FileDocumentManager.getInstance().saveAllDocuments();
    AtomicReference<PrePushHandler.Result> result = new AtomicReference<>(PrePushHandler.Result.OK);
    new Task.Modal(project, /* title */ "Checking Commits...", /* canBeCancelled */ true) {
      @Override
      public void run(ProgressIndicator indicator) {
        result.set(pushController.executeHandlers(indicator));
      }

      @Override
      @UIEffect
      public void onThrowable(Throwable error) {
        if (error instanceof PushController.HandlerException) {
          PushController.HandlerException handlerException = (PushController.HandlerException) error;
          Throwable cause = handlerException.getCause();

          String failedHandler = handlerException.getFailedHandlerName();
          java.util.List<String> skippedHandlers = handlerException.getSkippedHandlers();

          String suggestionMessageProblem;
          if (cause instanceof ProcessCanceledException) {
            suggestionMessageProblem = "${failedHandler} has been cancelled.";
          } else {
            // PushController.HandlerException constructor guarantees that cause is not null
            assert cause != null : "Exception cause is null";
            super.onThrowable(cause);
            suggestionMessageProblem = "${failedHandler} has failed. See log for more details.";
          }

          String suggestionMessageQuestion = skippedHandlers.isEmpty()
              ? "Would you like to push anyway or cancel the push completely?"
              : "Would you like to skip all remaining pre-push steps and push, or cancel the push completely?";

          suggestToSkipOrPush(suggestionMessageProblem + System.lineSeparator() + suggestionMessageQuestion);
        } else {
          super.onThrowable(error);
        }
      }

      @Override
      @UIEffect
      public void onCancel() {
        super.onCancel();
        suggestToSkipOrPush("Would you like to skip all pre-push steps and push, or cancel the push completely?");
      }

      @UIEffect
      private void suggestToSkipOrPush(String message) {
        if (Messages.showOkCancelDialog(project,
            message,
            /* title */ "Force Push",
            /* okText */ "Force Push Anyway",
            /* cancelText */ "Cancel",
            UIUtil.getWarningIcon()) == Messages.OK) {
          result.set(PrePushHandler.Result.OK);
        } else {
          result.set(PrePushHandler.Result.ABORT);
        }
      }
    }.queue();

    PrePushHandler.Result resultValue = result.get();
    assert resultValue != null : "Result value is null";
    return resultValue;
  }

  @UIEffect
  public void updateOkActions(@UnknownInitialization GitForcePushDialog this) {
    if (pushAction != null) {
      pushAction.setEnabled(canPush());
    }
  }

  @UIEffect
  public void enableOkActions(@UnknownInitialization GitForcePushDialog this, boolean value) {
    if (pushAction != null) {
      pushAction.setEnabled(value);
    }
  }

  @Override
  @Nullable
  public VcsPushOptionValue getAdditionalOptionValue(@UnknownInitialization GitForcePushDialog this, PushSupport support) {
    return null;
  }

  private static final class PushAction extends AbstractAction {
    private final PushActionWrapper pushActionWrapper;

    @UIEffect
    private PushAction(PushActionWrapper pushActionWrapper) {
      super(pushActionWrapper.getName());
      this.pushActionWrapper = pushActionWrapper;
    }

    @Override
    @UIEffect
    public void actionPerformed(ActionEvent e) {
      pushActionWrapper.actionPerformed(e);
    }

    @Override
    @UIEffect
    public void setEnabled(boolean isEnabled) {
      super.setEnabled(isEnabled);
    }
  }

  private class PushActionImpl extends PushActionBase {
    @UIEffect
    PushActionImpl() {
      getTemplatePresentation().setText("Force Push");
    }

    @Override
    @UIEffect
    protected boolean isEnabled(VcsPushUi dialog) {
      return dialog.canPush();
    }

    @Override
    protected @Nullable String getDescription(VcsPushUi dialog, boolean enabled) {
      return null;
    }

    @Override
    @UIEffect
    public void actionPerformed(Project projekt, VcsPushUi dialog) {
      push(/* forcePush */ true);
    }
  }

  @UI
  private class PushActionWrapper extends AbstractAction {
    private final PushActionImpl realAction;

    private final String name;

    PushActionWrapper(PushActionImpl realAction, String name) {
      super(name);
      this.realAction = realAction;
      this.name = name;
      putValue(OptionAction.AN_ACTION, realAction);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      realAction.actionPerformed(project, GitForcePushDialog.this);
    }

    String getName() {
      return name;
    }
  }
}