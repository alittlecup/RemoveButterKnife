package com.hbl.removebutterknife;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.messages.impl.Message;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.crypto.DSA;

public class DirectoryRemoveAction extends AnAction {
  List<PsiFile> toDealFiles = new ArrayList<>();
  boolean isDirFlag = false;

  @Override public void actionPerformed(AnActionEvent e) {
    toDealFiles.clear();
    if (isDirFlag) {
      IdeView ideView = e.getRequiredData(LangDataKeys.IDE_VIEW);
      PsiDirectory directory = ideView.getOrChooseDirectory();
      if (directory != null) {
        findNeedDealFile(directory);
      }
    } else {
      VirtualFile data = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      Project project = e.getData(PlatformDataKeys.PROJECT);
      if (project != null && data != null) {
        PsiFile file = PsiManager.getInstance(project).findFile(data);
        toDealFiles.add(file);
      }
    }
    if (!toDealFiles.isEmpty()) {
      for (PsiFile file : toDealFiles) {
        dealWithFile(e, file);
      }
    }
    Messages.showMessageDialog("Remove ButterKnife Completed", "Notify", null);
  }

  private void findNeedDealFile(PsiDirectory directory) {
    PsiFile[] files = directory.getFiles();
    if (files.length > 0) {
      for (PsiFile file : files) {
        if (file.getFileType().getName().equals("JAVA")) {
          toDealFiles.add(file);
        }
      }
    }
    PsiDirectory[] subdirectories = directory.getSubdirectories();
    for (PsiDirectory directory1 : subdirectories) {
      findNeedDealFile(directory1);
    }
  }

  private void dealWithFile(AnActionEvent event, PsiFile file) {
    try {
      Project project = event.getData(PlatformDataKeys.PROJECT);
      int classIndex = file.getText().indexOf("class");
      if (classIndex == -1) return;
      PsiClass mClass = PsiTreeUtil.getParentOfType(file.findElementAt(classIndex), PsiClass.class);
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      new DeleteAction(project, file, document, mClass).execute();
    } catch (Exception e) {
    }
  }

  @Override public void update(AnActionEvent e) {
    VirtualFile data = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (data == null) return;
    if (data.isDirectory()) {
      isDirFlag = true;
      this.getTemplatePresentation().setEnabled(true);
    } else if (data.getExtension() != null && data.getExtension().equals("java")) {
      this.getTemplatePresentation().setEnabled(true);
      isDirFlag = false;
    } else {
      this.getTemplatePresentation().setEnabled(false);
    }
  }
}
