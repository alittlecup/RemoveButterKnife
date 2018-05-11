package com.hbl.removebutterknife;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

public class FileEditorRemoveAction extends BaseGenerateAction {
  protected PsiClass mClass;
  Project project;
  PsiFile file;
  private PsiElementFactory mFactory;

  public FileEditorRemoveAction() {
    super(null);
  }

  public FileEditorRemoveAction(CodeInsightActionHandler handler) {
    super(handler);
  }

  @Override public void actionPerformed(AnActionEvent e) {
    try {

      project = e.getData(PlatformDataKeys.PROJECT);
      Editor editor = e.getData(PlatformDataKeys.EDITOR);
      file = PsiUtilBase.getPsiFileInEditor(editor, project);
      mFactory = JavaPsiFacade.getElementFactory(project);
      mClass = getTargetClass(editor, file);
      Document document = editor.getDocument();
      new DeleteAction(project, file, document, mClass).execute();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
