package com.github.java2uml.core.parsing;

import com.github.java2uml.core.Options;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.Node;

import japa.parser.ast.body.*;

import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.type.Type;
import japa.parser.ast.visitor.CloneVisitor;
import japa.parser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.*;

/**
 * Created by nadcukandrej on 12.02.15.
 */
public class UMLDiagramSequence {

    private static Logger log = LoggerFactory.getLogger(UMLDiagramSequence.class);

    private CompilationUnit cu;
    private CreateSequenceUmlCode csuc;
    public static Map<String, Clazz> markedClasses;
    public static final int ON_METHOD = 1;
    public static final int ON_CONSTRUCT = 2;
    public static int signOfBuilding = 2;
    private static Map<File, CompilationUnit> cacheCu;

    public UMLDiagramSequence(CreateSequenceUmlCode csuc, CompilationUnit cu) {
        this.cu = cu;
        this.csuc = csuc;
        if (markedClasses == null)
            markedClasses = new HashMap<>();
        if(cacheCu == null)
            cacheCu = new HashMap<>();
    }

    private void setCu(File file) throws Exception {
        log.debug("Read file : " + file);
        if(cacheCu.containsKey(file)) {
            cu = cacheCu.get(file);
        }
        else {
            cu = csuc.getCu(file);
            cacheCu.put(file, cu);
        }
    }

    public boolean getEntryPoint() {
        final boolean[] entryPoint = {false};
        new VoidVisitorAdapter() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                // Определяем точку входа
                if (n.getMembers().toString().contains("public static void main(String[] args)") && UMLDiagramClasses.isMain(n)) {
                    entryPoint[0] = true;
                }
            }
        }.visit(cu, null);

        return entryPoint[0];
    }

    public void startBuildSequence() {
        final Clazz[] nextClass = {null};
        final Clazz[] parentClass = {null};
        new VoidVisitorAdapter() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                int i = getIndexClassesArray(n.getName());
                log.debug("{} startBuildSequence i {}", n.getName(), i);
                if(i > -1) {
                    parentClass[0] = new Clazz(n.getName(),
                            nameWithPath(n.getName()),
                            CreateUmlCode.classesWithAbsolutePath.get(i),
                            Clazz.ACTOR);

                    CreateUmlCode.source.append(parentClass[0].getObjectType() + " " + parentClass[0].getName() + "\n");
                    readFields(parentClass[0]);
                    readMethods(parentClass[0]);
                    markedClasses.put(nameWithPath(n.getName()), parentClass[0]);
                }
            }
        }.visit(cu, null);

        new VoidVisitorAdapter() {
            @Override
            public void visit(MethodDeclaration n, Object arg) {
                if (parentClass[0] != null && n.getName().equals("main") && n.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC) && n.getType().toString().equals("void")) {
                    bodyMethod(n, parentClass[0], "startBuildSequence");
                }
            }
        }.visit(cu, null);
    }

    public void buildSequenceOnConstruct() {
        final Clazz[] nextClass = {null};
        final Clazz[] parentClass = {null};
        new VoidVisitorAdapter() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                parentClass[0] = markedClasses.get(nameWithPath(n.getName()));
                log.debug(parentClass[0].getName() + " buildSequenceOnConstruct ");
                if(parentClass[0] != null) {
                    if (n.getExtends() != null
                            && getIndexClassesArray(n.getExtends().get(0).getName()) > -1
                            && !parentClass[0].isExtends()
                            && !parentClass[0].isSingletonCreated()) {

                        if (!markedClasses.containsKey(nameWithPath(n.getExtends().get(0).getName()))) {
                            String objectType = Clazz.ENTITY;
                            int i = getIndexClassesArray(n.getExtends().get(0).getName());
                            nextClass[0] = new Clazz(CreateUmlCode.classes.get(i),
                                    nameWithPath(CreateUmlCode.classes.get(i)),
                                    CreateUmlCode.classesWithAbsolutePath.get(i),
                                    objectType);
                            markedClasses.put(nameWithPath(n.getExtends().get(0).getName()), nextClass[0]);
                        } else {
                            nextClass[0] = markedClasses.get(nameWithPath(n.getExtends().get(0).getName()));
                        }
                        CreateUmlCode.source.append(nextClass[0].getObjectType() + " " + nextClass[0].getName() + "\n");
                        nextClass[0].setChildren(parentClass[0]);
                        nextClass[0].setChildrenStaticInitialisation(rememberStaticInitialisation());
                        nextClass[0].setClassWhereCalled(parentClass[0].getName());
                        parentClass[0].setExtends(true);

                        changeCu(nextClass[0]);
                        try {
                            setCu(parentClass[0].getAbsolutePath());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        buildSequenceOnConstruct();
                    } else {
                        Clazz cl;
                        if (!parentClass[0].isExtends()) {
                            readStatic(parentClass[0]);
                        } else if ((parentClass[0].getChildrenStaticInitialisation()) != null) {
                            MethodDeclaration md = new MethodDeclaration(0, null, null, null, null, null, 0, null, parentClass[0].getChildrenStaticInitialisation());
                            bodyMethod(md, parentClass[0], "buildSequenceOnConstruct ChildrenStaticInitialisation");
                        }
                        readFields(parentClass[0]);
                        readMethods(parentClass[0]);
                        readConstructors(parentClass[0]);
                        if ((cl = parentClass[0].getChildren()) != null) {
                            try {
                                setCu(cl.getAbsolutePath());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            buildSequenceOnConstruct();
                        }

                    }
                }
            }
        }.visit(cu, null);

        new VoidVisitorAdapter() {
            @Override
            public void visit(MethodDeclaration n, Object arg) {
                if (n.getAnnotations() != null
                        && n.getAnnotations().size() > 0
                        && isOverride(n.getAnnotations())
                        && !parentClass[0].alreadyCalledOverrideMethod(n.getName())
                        && n.getParameters() != null
                        && n.getParameters().get(0).getType().toString().toLowerCase().contains("event")) {
                    log.debug("Class {} annotations {} methodName {} params {}",parentClass[0].getName(), n.getAnnotations(), n.getName(), n.getParameters());
                    bodyMethod(n, parentClass[0], "buildSequenceOnConstruct override method");
                    parentClass[0].setOverrideMethod(n.getName());
                }
            }
        }.visit(cu, null);

    }

    public void buildSequenceOnMethod() {
        final Clazz[] nextClass = {null};
        final Clazz[] parentClass = {null};
        new VoidVisitorAdapter() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Object arg) {

                parentClass[0] = markedClasses.get(nameWithPath(n.getName()));
            }
        }.visit(cu, null);

        new VoidVisitorAdapter() {
            @Override
            public void visit(MethodDeclaration n, Object arg) {
                if(parentClass[0] != null && parentClass[0].getCalledMethods() != null)
                for (int i = 0; i < parentClass[0].getCalledMethods().size(); i++) {
                    String nameMethod = parentClass[0].getCalledMethods().get(i);
                    if (nameMethod.equals(n.getName())) {
                        parentClass[0].getCalledMethods().remove(nameMethod);
                        bodyMethod(n, parentClass[0], ("buildSequenceOnMethod " + i));
                    }
                }

            }
        }.visit(cu, null);

    }

    private void changeCu(Clazz cl){
        try {
            setCu(cl.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (signOfBuilding == ON_METHOD){
            log.debug("Read on method from method : {}", cl.getName());
            buildSequenceOnMethod();
        }else{
            log.debug("Read on construct from method : {}", cl.getName());
            buildSequenceOnConstruct();
        }
    }

    private void bodyMethod(MethodDeclaration n, Clazz parentClass, String called){
        Clazz cl = null;
        log.debug("Called : {} method {}", called, n.getName());
        Type type = (n.getType() == null || n.getType().toString().equals("void")) ? null : n.getType();
        if(n.getBody() != null && n.getBody().getStmts() != null)
        for (Statement s : n.getBody().getStmts()) {

            String statement =  s.toStringWithoutComments();

            String[] statements = statement.split(";");
            for (int i = 0; i < statements.length; i++) {

                String st = statements[i].replaceAll("}", "").trim();
                st = st.length() > 0 ? st + ";" : null;
                if (st != null && (cl = getLink(st, parentClass, n.getBody().toString(), n.getName())) != null) {
                    changeCu(cl);
                    log.debug("Read method body reading class {}", parentClass.getName());
                    try {
                        setCu(parentClass.getAbsolutePath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (type != null && !parentClass.isSingletonCreated()) {
                        CreateUmlCode.source.append(parentClass.getName() + " --> " + parentClass.getClassWhereCalled());
                        CreateUmlCode.source.append(": " + type + "\n");
                    }
                }
            }

        }
    }

    private Clazz getLink(String statement, Clazz parentClass, String method, String methodName){

        Clazz cl = null;
        String expression = null;
        log.debug("{} statement : {}", parentClass.getName(), statement);

        if ((expression = calledConstructorOrMethod(statement, parentClass, method)) != null) {
            String objectType = Clazz.PARTICIPANT;

            log.debug("{} expression : {}", parentClass.getName(), expression);
            String[] strArray = expression.split("\\.");
            if (strArray.length > 0) {
                int i = getIndexClassesArray(replaceStr(strArray[1]));
                List<String> list = new ArrayList<String>();

                if(!markedClasses.containsKey(nameWithPath(CreateUmlCode.classes.get(i)))) {


                    if (CreateUmlCode.classesWithAbsolutePath.get(i).toString().toLowerCase().contains("control")) {
                        objectType = Clazz.CONTROLLER;
                    }else if(CreateUmlCode.classesWithAbsolutePath.get(i).toString().toLowerCase().contains("database")){
                        objectType = Clazz.DATA_BASE;
                    }
                    CreateUmlCode.source.append(objectType + " " + CreateUmlCode.classes.get(i) + "\n");

                    if(strArray.length > 2) {
                        for (int j = 2; j < strArray.length; j++) {

                            list.add(replaceStr(strArray[j]));
                        }
                    }

                    cl = new Clazz(CreateUmlCode.classes.get(i),
                            nameWithPath(CreateUmlCode.classes.get(i)),
                            CreateUmlCode.classesWithAbsolutePath.get(i),
                            objectType);
                    markedClasses.put(nameWithPath(CreateUmlCode.classes.get(i)), cl);

                    cl.setClassWhereCalled(parentClass.getName());
                    CreateUmlCode.source.append(parentClass.getName() + " -> " + cl.getName());
                    cl.setLink(null, parentClass.getName() + " -> " + cl.getName(), parentClass.getName() + " -> " + cl.getName());
                    log.debug(parentClass.getName() + " -> " + cl.getName());
                    if(strArray.length > 2) {
                        CreateUmlCode.source.append(": " + replaceStr(strArray[strArray.length - 1]) + "\n");
                    }
                    else {
                        if(methodName != null)
                            CreateUmlCode.source.append(": from " + methodName + "\n");
                        else
                            CreateUmlCode.source.append("\n");
                    }

                }else{
                    cl = markedClasses.get(nameWithPath(CreateUmlCode.classes.get(i)));
                    if(strArray.length > 2) {
                        for (int j = 2; j < strArray.length; j++) {
                            if(cl.getAllMethods() != null && cl.methodInAllMethods(replaceStr(strArray[j])))
                                list.add(replaceStr(strArray[j]));
                        }
                        if(list.size() == 0)
                            return null;
                    }
                    if(cl.getLink(methodName, parentClass.getName() + " -> " + cl.getName()) == null) {
                        if(methodName != null) {
                            log.debug("{} -> {} methodName : {}",parentClass.getName(), cl.getName(), methodName);
                            CreateUmlCode.source.append(parentClass.getName() + " -> " + cl.getName());
                            if(list.size() > 0)
                                CreateUmlCode.source.append(": " + list.get(0) + "\n");
                            else
                                CreateUmlCode.source.append(": from " + methodName + "\n");
                            cl.setLink(methodName, parentClass.getName() + " -> " + cl.getName(), parentClass.getName() + " -> " + cl.getName());
                        }
                    }else {
                        return null;
                    }
                }



                if(list != null && list.size() > 0)
                    cl.setCalledMethods(list);

                    if (strArray[0].equals("new") && !cl.isSingleton()) {
                        if (cl.getClassActivity().equals(Clazz.DEACTIVATE)) {
                            cl.setClassActivity(Clazz.ACTIVATE);
                            CreateUmlCode.source.append(Clazz.ACTIVATE + " " + CreateUmlCode.classes.get(i) + "\n");
                        }
                    }

            }
        }
        log.debug("Return class : " + (cl == null ? null : cl.getName()));
        return cl;
    }

    private void readFields(final Clazz clazz) {
        final Clazz[] cl = {null};
        new CloneVisitor() {
            @Override
            public Node visit(FieldDeclaration _n, Object _arg) {
                List<VariableDeclarator> variables = visit(_n.getVariables(), _arg);
                // Запоминаем пару
                // key = название поля
                // value = тип поля
                for (VariableDeclarator v : variables) {
                    for (String cl : CreateUmlCode.classes) {
                        if (_n.getType().toString().equals(cl)) {
                            clazz.setVariables(v.getId().toString(), _n.getType().toString());
                            if(_n.getType().toString().equals(clazz.getName()))
                                clazz.setSingleton(true);
                        }
                    }
                }

                if(_n.getVariables() != null){
                    for (VariableDeclarator variable : _n.getVariables()){
                        if(variable.getInit() == null)
                            continue;
                        if(variable != null && !variable.getInit().toString().equals("null")){
                            if((cl[0] = getLink(variable.getInit().toString() + ";", clazz, null, null)) != null) {
                                if(!cl[0].getName().equals(_n.getType().toString()) || variable.getInit().toString().contains("new ")) {
                                    try {
                                        setCu(cl[0].getAbsolutePath());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    if (signOfBuilding == ON_METHOD) {
                                        log.debug("Read on method from field : {}", cl[0].getName());
                                        buildSequenceOnMethod();
                                    } else if (signOfBuilding == ON_CONSTRUCT) {
                                        log.debug("Read on construct from field : {}", cl[0].getName());
                                        buildSequenceOnConstruct();
                                    }
                                    log.debug("Read fields reading class {}", clazz.getName());
                                    try {
                                        setCu(clazz.getAbsolutePath());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                    }
                }
                return super.visit(_n, _arg);
            }
        }.visit(cu, null);
    }

    private void readMethods(final Clazz clazz){
        new CloneVisitor(){
            @Override
            public Node visit(MethodDeclaration _n, Object _arg) {
                clazz.setMethodToAllMethods(_n.getName());
                return super.visit(_n, _arg);
            }
        }.visit(cu, null);
    }

    private BlockStmt rememberStaticInitialisation(){
        final BlockStmt[] bs = {null};
        new CloneVisitor(){
            @Override
            public Node visit(InitializerDeclaration _n, Object _arg) {
                if(_n.isStatic()) {
                    bs[0] = _n.getBlock();
                }
                return super.visit(_n, _arg);
            }
        }.visit(cu, null);
        return bs[0];
    }

    private void readStatic(final Clazz clazz){
        final Clazz[] cl = {null};
        new CloneVisitor(){
            @Override
            public Node visit(InitializerDeclaration _n, Object _arg) {
                if(_n.isStatic()) {
                    MethodDeclaration md = new MethodDeclaration(0, null, null, null, null, null, 0, null, _n.getBlock());
                    bodyMethod(md, clazz, "readStatic");
                }
                return super.visit(_n, _arg);
            }
        }.visit(cu, null);

    }

    private void readConstructors(final Clazz clazz){
        final Clazz[] cl = {null};
        new CloneVisitor(){
            @Override
            public Node visit(ConstructorDeclaration _n, Object _arg) {
                if(_n.getBlock() != null){
                    log.debug("{} singleton : {} created : {}", clazz.getName(), clazz.isSingleton(), clazz.isSingletonCreated());

                    if(!clazz.isSingletonCreated()) {
                        if(clazz.isSingleton())
                            clazz.setSingletonCreated(true);
                        MethodDeclaration md = new MethodDeclaration(0, null, null, null, null, null, 0, null, _n.getBlock());
                        bodyMethod(md, clazz, "readConstructors");
                    }
                }
                return super.visit(_n, _arg);
            }
        }.visit(cu, null);
    }

    private String nameWithPath(String nameClass) {
        if (cu.getImports() != null && cu.getImports().size() > 0)
            for (ImportDeclaration imp : cu.getImports()) {

                if (imp.getName().toString().toLowerCase().endsWith("." + nameClass.toLowerCase()))
                    return imp.getName().toString();

                StringBuilder builder = new StringBuilder();
                builder.append(Options.getPath());
                builder.append(System.getProperty("file.separator"));
                builder.append("src");
                builder.append(System.getProperty("file.separator"));
                builder.append(imp.getName().toString().replace(".", System.getProperty("file.separator")));
                builder.append(System.getProperty("file.separator"));
                builder.append(nameClass);
                builder.append(".java");
                File file = new File(builder.toString());
                if (file.exists())
                    return imp.getName().toString() + "." + nameClass;


            }
        return getPackage() + nameClass;
    }

    private String getPackage() {

        return (cu.getPackage() == null ? "" : cu.getPackage().getName().toString() + ".");

    }

    private static String replaceStr(String string) {
        string = string.replaceAll("\\(.*\\)|;", "");
        return string;
    }

    private String calledConstructorOrMethod(String string, Clazz clazz, String method) {
        if(string == null)
            return null;

        string = string.replaceAll("=this", clazz.getName()).replaceAll("= this", clazz.getName());

        if(method != null)
            getLocalVariable(string, clazz, method);
        String str = getCalledMethod(string, clazz);
        if(str != null && str.length() > 0)
            return str;

        for (int i = 0; i < CreateUmlCode.classes.size(); i++) {
            if(containsObject(string, CreateUmlCode.classes.get(i)) || checkVariable(string, clazz, CreateUmlCode.classes.get(i), method)
                    && checkImports(CreateUmlCode.classes.get(i))){
                if(string.contains("new " + CreateUmlCode.classes.get(i) + "(")){
                    signOfBuilding = ON_CONSTRUCT;
                    return "new." + CreateUmlCode.classes.get(i) + getExpression(string, "new " + CreateUmlCode.classes.get(i));
                }else{
                    signOfBuilding = ON_METHOD;
                    return "." + CreateUmlCode.classes.get(i) + getExpression(string, CreateUmlCode.classes.get(i));
                }
            }
        }
        return null;
    }

    private boolean checkVariable(String string, Clazz clazz, String obj, String method){
        if(clazz.getVariables() != null && clazz.getVariables().size() > 0) {
            for (Map.Entry<String, String> e : clazz.getVariables().entrySet()) {
                if(e.getValue().equals(obj) && string.contains(e.getKey()) && !string.contains(e.getKey() + ";"))
                    return true;
            }
        }
        if(clazz.getMethodVariables(method) != null){
            for (Map.Entry<String, String> e : clazz.getMethodVariables(method).entrySet()) {
                if(e.getValue().equals(obj) && string.contains(e.getKey()) && (string.contains(e.getKey() + " =") || string.contains(e.getKey() + "=") || string.contains(e.getKey() + ".")))
                    return true;
            }
        }
        return false;
    }

    private static void getLocalVariable(String string, Clazz clazz, String method){
        String[] lines = string.split(System.getProperty("line.separator"));
        if(lines.length > 0) {
            for (int j = 0; j < lines.length; j++) {
                localVariable(lines[j], clazz, method);

            }
        }else {
            localVariable(string, clazz, method);
        }
    }

    private static void localVariable(String string, Clazz clazz, String method){
        String[] strings = string.split("=");
        if (strings.length > 1) {
            log.debug("getLocalVariable \"\" str {} str[0] {}", string, strings[0]);
            String[] firstString = strings[0].trim().split(" ");
            if (firstString.length == 2) {
                log.debug("getLocalVariable \"\" firstString[0] {}", firstString[0]);
                int i;
                if ((i = getIndexClassesArray(firstString[0].trim())) > -1) {
                    log.debug("check method variables method {} key {} value {}", method, firstString[1].trim(), CreateUmlCode.classes.get(i));
                    clazz.setMethodVariables(method, firstString[1].trim(), CreateUmlCode.classes.get(i));
                }
            }else if(firstString.length == 1){

            }
        }

    }

    public String getCalledMethod(String string, Clazz clazz){
        String str = calledMethod(string, clazz);
        if(str.length() > 0)
            return str;
        String[] lines = string.split(System.getProperty("line.separator"));
        if(lines.length > 0) {
            for (int j = 0; j < lines.length; j++) {

                if((str = calledMethod(lines[j], clazz)).length() > 0)
                    return str;

            }
        }
        return null;
    }

    public String calledMethod(String string, Clazz clazz){
        if(string.contains("new "))
            return "";
        StringBuilder builder = new StringBuilder();
        Pattern pattern = Pattern.compile("(^[a-z][\\w\\S]*?\\(.*\\);$)");
        Matcher matcher = pattern.matcher(string.replaceAll(" ", ""));
        if(matcher.find() && !matcher.group().toString().contains("=")) {
            String[] strings = matcher.group().split("\\.");
            log.debug("Get Called Methods{} in class {}", strings, clazz.getName());
            if(strings.length > 1 && strings[0].endsWith(")")){
                builder.append("." + clazz.getName());
                for (int i = 0; i < strings.length; i++) {
                    builder.append("." + strings[i].replaceAll(" ", ""));
                }
            }else if(strings.length == 1){
                builder.append("." + clazz.getName());
                builder.append("." + strings[0].replaceAll(" ", ""));
            }

            signOfBuilding = ON_METHOD;
        }
        return builder.toString();
    }

    private static String getVariable(String obj, Clazz clazz){
        String var = "";
        if(clazz.getVariables() != null && clazz.getVariables().size() > 0) {
            for (Map.Entry<String, String> e : clazz.getVariables().entrySet()) {
                if(e.getValue().equals(obj)) {
                    var = e.getKey();
                    break;
                }
            }
        }
        return var;
    }

    private boolean checkImports(String nameClass){
        StringBuilder builder = new StringBuilder();
        builder.append(Options.getPath());
        builder.append(System.getProperty("file.separator"));
        builder.append("src");
        builder.append(System.getProperty("file.separator"));

        if (cu.getImports() != null && cu.getImports().size() > 0) {
            for (ImportDeclaration imp : cu.getImports()) {

                if (imp.getName().toString().endsWith("." + nameClass))
                    return true;StringBuilder im = new StringBuilder();

                im.append(builder);

                im.append(imp.getName().toString().replace(".", System.getProperty("file.separator")));

                im.append(System.getProperty("file.separator"));
                im.append(nameClass);
                im.append(".java");
                File file = new File(im.toString());

                if (file.exists())
                    return true;

            }
        }else{
            builder.append(getPackage().replace(".", System.getProperty("file.separator")));
        }

        return false;
    }

    private static int getIndexClassesArray(String cl){
        for (int i = 0; i < CreateUmlCode.classes.size(); i++) {
            if(CreateUmlCode.classes.get(i).equals(cl))
                return i;
        }
        return -1;
    }

    private static String getExpression(String str, String obj){

        String expression = "";
        String part = "(;|\\)|,)$";
        Pattern pattern = Pattern.compile("(" + obj + "[\\.\\w]*?(.*);)|(" + obj + "[\\.\\w]*?(.*)(?=\\)))");
        Matcher matcher = pattern.matcher(str);
        if (matcher.find())
        {
            String[] strArray = matcher.group().split("\\.");
            if(strArray.length > 1 && strArray[1].contains("(") && strArray[1].contains(")")) {
                if(strArray.length > 2){
                    StringBuilder b = new StringBuilder();
                    for (int i = 1; i < strArray.length; i++) {
                        if(strArray[i].contains("(") && strArray[i].contains(")"))
                            b.append("." + strArray[i]);

                    }
                    return b.toString();
                }
                return "." + strArray[1];
            }
        }
        return expression;
    }

    private String getObjectMethod(String string, String obj){
        StringBuilder builder = new StringBuilder();
        builder.append(obj);
        return builder.toString();
    }

    private static boolean containsObject(String string, String obj){

        if(string.contains(obj + ".") && !string.contains(obj + ".this")){
            String[] strArray = string.split(obj + "\\.");
            return (strArray[1].contains("(") && (strArray[1].contains(").") || strArray[1].contains(");")));
        }
        return (string.contains(obj + "("));
    }

    public static void deactivateClasses(){

        for (Map.Entry<String, Clazz> entry: markedClasses.entrySet()){
            Clazz cl = entry.getValue();
            if(cl.getClassActivity().equals(Clazz.ACTIVATE)) {
                if(cl.getLinks() == null)
                    CreateUmlCode.source.append(cl.getName() + " -> " + cl.getName() + "\n");
                CreateUmlCode.source.append(Clazz.DEACTIVATE + " " + cl.getName() + "\n");
                cl.setClassActivity(Clazz.DEACTIVATE);
            }

        }
    }

    private static boolean isOverride(List list){
        for (int i = 0; i < list.size(); i++) {
            if(list.get(i).toString().equals("@Override"))
                return true;
        }
        return false;
    }
}
