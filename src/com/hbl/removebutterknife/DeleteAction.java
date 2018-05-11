package com.hbl.removebutterknife;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.EverythingGlobalScope;
import gherkin.lexer.Pa;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeleteAction extends WriteCommandAction.Simple {
  Document document;
  String[] s1;
  Project project;
  PsiFile psiFile;
  PsiClass psiClass;
  PsiElementFactory psiElementFactory;
  List<Integer> tod;

  Map<String, String> nameidmap = new LinkedHashMap<>();
  Map<Integer, String> tcmap = new LinkedHashMap<>();

  public DeleteAction(Project project, PsiFile file, Document document, PsiClass psiClass) {
    super(project, file);
    this.document = document;
    s1 = document.getText().split("\n");
    this.project = project;
    this.psiFile = file;
    this.psiClass = psiClass;
    psiElementFactory = JavaPsiFacade.getElementFactory(project);
    tod = new ArrayList<>();
  }

  @Override protected void run() {
    try {
      if (psiClass == null) return;
      if (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType()) {
        return;
      }
      deleteImport();
      deleteAnnotationAndGetIdNames();
      deleteButterKnife();
      replaceOnClickAnnotationAndGetId();

      for (Map.Entry<Integer, String> entry : tcmap.entrySet()) {
        int deleteStart = document.getLineStartOffset(entry.getKey());
        int deleteEnd = document.getLineEndOffset(entry.getKey());
        document.replaceString(deleteStart, deleteEnd, "\t" + entry.getValue());
      }
      for (int i = 0; i < tod.size(); i++) {
        int deleteStart = document.getLineStartOffset(tod.get(i));
        int deleteEnd = document.getLineEndOffset(tod.get(i));
        document.deleteString(deleteStart, deleteEnd);
      }
      for (Map.Entry<Integer, Integer> entry : onClickOffSet.entrySet()) {
        int deleteStart = document.getLineStartOffset(entry.getKey());
        int deleteEnd = deleteStart + entry.getValue();
        document.deleteString(deleteStart, deleteEnd);
      }
      PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
      manager.commitDocument(document);
      createFindViewByIdCode();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  List<String> codes;

  private void createFindViewByIdCode() {
    codes = new ArrayList<>();
    for (Map.Entry<String, String> entry : nameidmap.entrySet()) {
      String value = entry.getValue();
      if (value.contains("R2")) {
        value = value.replace("R2", "R");
      }
      String code = entry.getKey() + "findViewById(" + value + ");";
      codes.add(code);
    }
    for (Map.Entry<String, String> entry : onClickIdsAndMethodName.entrySet()) {

      String value = entry.getValue();
      String key = entry.getKey();
      boolean hasParameters = false;
      if (value.endsWith("+")) {
        hasParameters = true;
        value = value.substring(0, value.length() - 1);
      }
      if (value.equals("onClick")) {
        value = psiClass.getName() + ".this." + value;
      }
      if (key.contains("R2")) {
        key = key.replace("R2", "R");
      }
      String code = "findViewById("
          + key
          + ").setOnClickListener(new View.OnClickListener() {\n"
          + "      @Override public void onClick(View v) {\n"
          + "        "
          + value
          + "("
          + (hasParameters ? "v" : "")
          + ");\n"
          + "      }\n"
          + "    });";
      codes.add(code);
    }
    try {
      new FindViewByIdWriter(project, psiFile, psiClass, codes, psiElementFactory).execute();
    } catch (Exception e) {

    }
  }

  private void deleteButterKnife() {
    for (int i = 0; i < s1.length; i++) {
      if (s1[i].trim().indexOf("ButterKnife") == 0 || s1[i].trim().indexOf("unbinder") == 0) {
        tod.add(i);
      }
      if (s1[i].contains("Unbinder unbinder")) {
        tod.add(i);
      }
      if (s1[i].trim().indexOf("Unbinder") == 0) {
        tod.add(i);
      }
    }
  }

  private void replaceAnnotationAndGetIdName() {
    String pattern = "@(BindView|InjectView|Bind)\\(R2?.id.*\\)*;";
    Pattern pattern1 = Pattern.compile(pattern);
    int j;
    for (j = 0; j < s1.length; j++) {
      if (s1[j].trim().startsWith("//")) continue;
      Matcher m = pattern1.matcher(s1[j]);
      if (m.find()) {
        String id = s1[j].substring(s1[j].indexOf("(") + 1, s1[j].indexOf(")"));
        String s2 = s1[j].substring(s1[j].indexOf(")") + 1).trim();
        String[] split = s2.split(" ");
        String name;
        String type;
        if (split.length == 3) {
          name = split[2];
          type = split[1];
        } else {
          name = split[1];
          type = split[0];
        }
        name = name.substring(0, name.length() - 1);
        tcmap.put(j, s2);
        name = name + " = " + "(" + type + ")";
        nameidmap.put(name, id);
        System.out.println("replace: " + type + "--" + name);
      }
    }
  }

  private void deleteAnnotationAndGetIdName() {
    String pattern = "^@(BindView|InjectView|Bind)\\(R2?.id.*\\)$";
    Pattern r = Pattern.compile(pattern);
    for (int i = 0; i < s1.length; i++) {
      Matcher m = r.matcher(s1[i].trim());
      String trim = s1[i].trim();
      if (trim.startsWith("//")) continue;
      if (m.find()) {
        String id = trim.substring(trim.indexOf("(") + 1, trim.length() - 1);
        String[] s2 = s1[i + 1].trim().split(" ");
        String name;
        if (s2.length == 3) {
          name = s2[2].substring(0, s2[2].length() - 1) + " = " + "(" + s2[1] + ")";
        } else {
          name = s2[1].substring(0, s2[1].length() - 1) + " = " + "(" + s2[0] + ")";
        }
        System.out.println("delete: " + name + "--" + id);
        nameidmap.put(name, id);
        tod.add(i);
      }
    }
  }

  private void deleteAnnotationAndGetIdNames() {
    String oneLinePattern = "^@(BindView|InjectView|Bind)\\(R2?.id.*\\)$";
    String twoLinePattern = "@(BindView|InjectView|Bind)\\(R2?.id.*\\)*;";
    Pattern patternOneLine = Pattern.compile(oneLinePattern);
    Pattern patternTwoLine = Pattern.compile(twoLinePattern);
    for (int i = 0; i < s1.length; i++) {
      String line = s1[i].trim();
      if (line.startsWith("//")) continue;
      Matcher matcher = patternTwoLine.matcher(line);
      String id = "";
      String s2 = "";
      boolean finded = false;
      if (matcher.find()) {
        id = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
        s2 = line.substring(line.indexOf(")") + 1).trim();
        tcmap.put(i, s2);
        finded = true;
      } else {
        Matcher matcherOneLine = patternOneLine.matcher(line);
        if (matcherOneLine.find()) {
          id = line.substring(line.indexOf("(") + 1, line.length() - 1);
          s2 = s1[i + 1].trim();
          tod.add(i);
          finded = true;
        }
      }
      if (finded) {
        String[] split = s2.substring(0, s2.length() - 1).split(" ");
        int length = split.length;
        if (length >= 2) {
          String name = split[length - 1] + " = (" + split[length - 2] + ")";
          nameidmap.put(name, id);
        }
      }
    }
  }

  Map<String, String> onClickIdsAndMethodName = new LinkedHashMap<>();
  Map<Integer, Integer> onClickOffSet = new LinkedHashMap<>();

  private void replaceOnClickAnnotationAndGetId() {
    for (int i = 0; i < s1.length; i++) {
      if (s1[i].trim().indexOf("@OnClick(") == 0) {
        String substring = s1[i].substring(s1[i].indexOf("(") + 1, s1[i].indexOf(")"));
        if (substring.contains("{")) {
          String substring1 =
              substring.substring(substring.indexOf("{") + 1, substring.indexOf("}"));
          String[] split = substring1.split(",");
          for (String id : split) {
            if (isAdapter()) {
              PsiClass[] innerClasses = psiClass.getInnerClasses();
              if (innerClasses.length > 0) {
                onClickIdsAndMethodName.put(id, getMethodNameByLineNumber(innerClasses[0], i));
              } else {
                showErrorMessage(psiClass.toString() + "not match adapter");
              }
            } else {
              onClickIdsAndMethodName.put(id, getMethodNameByLineNumber(psiClass, i));
            }
          }
        } else {
          if (isAdapter()) {
            PsiClass[] innerClasses = psiClass.getInnerClasses();
            if (innerClasses.length > 0) {
              onClickIdsAndMethodName.put(substring, getMethodNameByLineNumber(innerClasses[0], i));
            } else {
              showErrorMessage(psiClass.toString() + "not match adapter");
            }
          } else {
            onClickIdsAndMethodName.put(substring, getMethodNameByLineNumber(psiClass, i));
          }
        }
        onClickOffSet.put(i, s1[i].indexOf(")") + 1);
      }
    }
  }

  private void showErrorMessage(String msg) {
    Messages.showErrorDialog(msg, "Error");
  }

  private String getMethodNameByLineNumber(PsiClass psiClass, int i) {
    PsiMethod[] methods = psiClass.getMethods();
    for (int j = i; j < s1.length; j++) {
      for (PsiMethod method : methods) {
        if (s1[j].contains(method.getName())) {
          JvmParameter[] parameters = method.getParameters();
          if (parameters.length > 0) {
            return method.getName() + "+";
          }
          return method.getName();
        }
      }
    }
    return "";
  }

  private boolean isAdapter() {
    PsiClass adapter = JavaPsiFacade.getInstance(project)
        .findClass("android.widget.BaseAdapter", new EverythingGlobalScope(project));
    PsiClass recyclerAdapter = JavaPsiFacade.getInstance(project)
        .findClass("android.support.v7.widget.RecyclerView.Adapter",
            new EverythingGlobalScope(project));

    PsiClass flexItem = JavaPsiFacade.getInstance(project)
        .findClass("eu.davidea.flexibleadapter.items.AbstractFlexibleItem",
            new EverythingGlobalScope(project));
    if ((adapter != null && psiClass.isInheritor(adapter, true)) || (recyclerAdapter != null
        && psiClass.isInheritor(recyclerAdapter, true)) || (flexItem != null
        && psiClass.isInheritor(flexItem, true))) {
      return true;
    }
    return false;
  }

  private void deleteImport() {

    for (int i = 0; i < s1.length; i++) {
      if (Definitions.imports.contains(s1[i])) {
        tod.add(i);
      }
    }
  }
}

