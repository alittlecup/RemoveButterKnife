package com.hbl.removebutterknife;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.EverythingGlobalScope;
import java.util.List;

public class FindViewByIdWriter extends WriteCommandAction.Simple {

  PsiClass mClass;
  private PsiElementFactory mFactory;
  List<String> code;
  Project mProject;
  String holderClassName;

  protected FindViewByIdWriter(Project project, PsiFile file, PsiClass psiClass, List<String> code,
      PsiElementFactory mFactory) {
    super(project, file);
    mClass = psiClass;
    this.code = code;
    this.mFactory = mFactory;
    mProject = project;
  }

  @Override protected void run() {
    try {
      if (code.isEmpty()) return;
      generateInjects(mProject);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void generateInjects(Project mProject) {
    PsiClass activityClass = JavaPsiFacade.getInstance(mProject)
        .findClass("android.app.Activity", new EverythingGlobalScope(mProject));
    PsiClass fragmentClass = JavaPsiFacade.getInstance(mProject)
        .findClass("android.app.Fragment", new EverythingGlobalScope(mProject));
    PsiClass supportFragmentClass = JavaPsiFacade.getInstance(mProject)
        .findClass("android.support.v4.app.Fragment", new EverythingGlobalScope(mProject));

    PsiClass adapter = JavaPsiFacade.getInstance(mProject)
        .findClass("android.widget.BaseAdapter", new EverythingGlobalScope(mProject));
    PsiClass recyclerAdapter = JavaPsiFacade.getInstance(mProject)
        .findClass("android.support.v7.widget.RecyclerView.Adapter",
            new EverythingGlobalScope(mProject));

    PsiClass flexItem = JavaPsiFacade.getInstance(mProject)
        .findClass("eu.davidea.flexibleadapter.items.AbstractFlexibleItem",
            new EverythingGlobalScope(mProject));

    // Check for Activity class
    if (activityClass != null && mClass.isInheritor(activityClass, true)) {
      writeActivity();
      // Check for Fragment class
    } else if ((fragmentClass != null && mClass.isInheritor(fragmentClass, true)) || (
        supportFragmentClass != null
            && mClass.isInheritor(supportFragmentClass, true))) {
      writeFragment();
      // check for adapter  maybe
    } else if ((adapter != null && mClass.isInheritor(adapter, true)) || (recyclerAdapter != null
        && mClass.isInheritor(recyclerAdapter, true)) || (flexItem != null && mClass.isInheritor(
        flexItem, true))) {
      writeAdapterViewHolder();
    }
  }

  private void writeAdapterViewHolder() {
    PsiClass[] innerClasses = mClass.getInnerClasses();
    if (innerClasses.length > 0) {
      for (PsiClass innerClass : innerClasses) {
        if (!innerClass.isInterface() && !innerClass.isEnum() && !innerClass.isAnnotationType()) {
          holderClassName = innerClass.getName();
          break;
        }
      }
    }
    PsiClass gymVH = mClass.findInnerClassByName(holderClassName, false);
    PsiMethod constructor = gymVH.getConstructors()[0];
    String view = constructor.getParameters()[0].getName() + ".";
    PsiStatement[] statements = constructor.getBody().getStatements();
    if (statements.length == 0) {
      for (int i = code.size() - 1; i >= 0; i--) {
        StringBuffer stringBuffer = new StringBuffer(code.get(i));
        int index = stringBuffer.indexOf(")");
        int findIndex = stringBuffer.indexOf("findViewById");
        if (findIndex < index) {
          stringBuffer.insert(0, view);
        } else {
          stringBuffer.insert(index + 1, view);
        }
        constructor.getBody()
            .addAfter(mFactory.createStatementFromText(stringBuffer.toString() + "\n", mClass),
                null);
      }
    } else {
      if (statements[0].getFirstChild() instanceof PsiMethodCallExpression) {
        PsiReferenceExpression methodExpression =
            ((PsiMethodCallExpression) statements[0].getFirstChild()).getMethodExpression();
        if (methodExpression.getText().equals("super")) {
          for (int i = code.size() - 1; i >= 0; i--) {
            StringBuffer stringBuffer = new StringBuffer(code.get(i));
            int index = stringBuffer.indexOf(")");
            int findIndex = stringBuffer.indexOf("findViewById");
            if (findIndex < index) {
              stringBuffer.insert(0, view);
            } else {
              stringBuffer.insert(index + 1, view);
            }
            constructor.getBody()
                .addAfter(mFactory.createStatementFromText(stringBuffer.toString() + "\n", mClass),
                    statements[0]);
          }
        }
      }
    }
  }

  private void writeFragment() {
    PsiMethod onCreateView = mClass.findMethodsByName("onCreateView", false)[0];
    String viewName = "";
    for (PsiStatement statement1 : onCreateView.getBody().getStatements()) {
      if (statement1 instanceof PsiReturnStatement) {
        PsiElement[] elements = statement1.getChildren();
        for (PsiElement element : elements) {
          if (element instanceof PsiReferenceExpressionImpl) {
            viewName = element.getText()+".";
          }
        }
      }
    }
    if (viewName.equals("")) return;
    for (PsiStatement statement : onCreateView.getBody().getStatements()) {
      String returnValue = statement.getText();
      if (returnValue.contains("R.layout")) {
        for (int i = code.size() - 1; i >= 0; i--) {
          StringBuffer buffer = new StringBuffer(code.get(i));
          int num = buffer.indexOf(")");
          int findViewByIdIndex = buffer.indexOf("findViewById");
          if (findViewByIdIndex < num) {
            buffer.insert(0, viewName);
          } else {
            buffer.insert(num + 1, viewName);
          }
          try {
            statement.addAfter(mFactory.createStatementFromText(buffer.toString(), mClass),
                statement);
          } catch (Exception e1) {
          }
        }
        break;
      }
    }
  }

  private void writeActivity() {
    PsiMethod onCreate = mClass.findMethodsByName("onCreate", false)[0];
    for (PsiStatement statement : onCreate.getBody().getStatements()) {
      // Search for setContentView()
      if (statement.getFirstChild() instanceof PsiMethodCallExpression) {
        PsiReferenceExpression methodExpression =
            ((PsiMethodCallExpression) statement.getFirstChild()).getMethodExpression();
        // Insert ButterKnife.inject()/ButterKnife.bind() after setContentView()
        if (methodExpression.getText().equals("setContentView")) {
          for (int i = code.size() - 1; i >= 0; i--) {
            onCreate.getBody()
                .addAfter(mFactory.createStatementFromText(code.get(i) + "\n", mClass), statement);
          }
          break;
        }
      }
    }
  }
}
