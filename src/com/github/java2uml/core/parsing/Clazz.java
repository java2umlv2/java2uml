package com.github.java2uml.core.parsing;

import japa.parser.ast.stmt.BlockStmt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by nadcukandrej on 12.02.15.
 */
public class Clazz{
    private String name;
    private String nameWithPackage;
    private File absolutePath;
    private List<String> calledMethods;
    private List<String> allMethods;
    private Map<String, String> variables;
    private Map<String, Map<String, String>> methodVariables;
    public static final String ACTOR = "actor";
    public static final String CONTROLLER = "control";
    public static final String PARTICIPANT = "participant";
    public static final String ENTITY = "entity";
    public static final String DATA_BASE = "database";
    public static final String ACTIVATE = "activate";
    public static final String DEACTIVATE = "deactivate";
    public static final String DESTROY = "destroy";
    private String objectType;
    private String classActivity = DEACTIVATE;
    private Clazz children;
    private BlockStmt childrenStaticInitialisation;
    private String classNameWhereCalled;
    private boolean isExtends = false;
    private boolean singleton = false;
    private boolean singletonCreated = false;
    private Map<String, Map<String, String>> links;
    private List<String> overrideMethods;

    public Clazz(String name, String nameWithPackage, File absolutePath, String objectType) {
        this.name = name;
        this.nameWithPackage = nameWithPackage;
        this.absolutePath = absolutePath;
        this.objectType = objectType == null ? PARTICIPANT : objectType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameWithPackage() {
        return nameWithPackage;
    }

    public void setNameWithPackage(String nameWithPackage) {
        this.nameWithPackage = nameWithPackage;
    }

    public File getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(File absolutePath) {
        this.absolutePath = absolutePath;
    }


    public List<String> getCalledMethods() {
        return calledMethods;
    }

    public void setCalledMethods(List<String> calledMethods) {
        this.calledMethods = calledMethods;
    }


    public String getObjectType() {
        return objectType;
    }

    public List<String> getAllMethods() {

        return allMethods;
    }

    public void setMethodToAllMethods(String method) {
        if(allMethods == null)
            allMethods = new ArrayList<>();
        this.allMethods.add(method);
    }

    public boolean methodInAllMethods(String method){
        if(allMethods != null)
        for (int i = 0; i < allMethods.size(); i++) {
            if(allMethods.get(i).equals(method))
                return true;
        }
        return false;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(String key, String value) {
        if(this.variables == null)
            variables = new HashMap<>();
        this.variables.put(key, value);
    }

    public Map<String, String> getMethodVariables(String method) {
        if(methodVariables != null && methodVariables.containsKey(method))
            return methodVariables.get(method);
        return null;
    }

    public void setMethodVariables(String method, String key, String value) {
        if(this.methodVariables == null)
            this.methodVariables = new HashMap<>();
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        methodVariables.put(method, map);
    }

    public Clazz getChildren() {
        return children;
    }

    public void setChildren(Clazz children) {
        this.children = children;
    }

    public BlockStmt getChildrenStaticInitialisation() {
        return childrenStaticInitialisation;
    }

    public String getClassWhereCalled() {

        return classNameWhereCalled == null ? this.getName() : classNameWhereCalled;
    }

    public void setClassWhereCalled(String classWhereCalled) {
        this.classNameWhereCalled = classWhereCalled;
    }

    public void setChildrenStaticInitialisation(BlockStmt childrenStaticInitialisation) {
        this.childrenStaticInitialisation = childrenStaticInitialisation;
    }

    public boolean isExtends() {
        return isExtends;
    }

    public void setExtends(boolean isExtends) {
        this.isExtends = isExtends;
    }

    public String getClassActivity() {
        return classActivity;
    }

    public void setClassActivity(String classActivity) {
        this.classActivity = classActivity;
    }

    public boolean isSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public boolean isSingletonCreated() {
        return singletonCreated;
    }

    public void setSingletonCreated(boolean singletonCreated) {
        this.singletonCreated = singletonCreated;
    }

    public String getLink(String key, String subKey) {
        if(links != null && links.containsKey(key)){
            Map<String, String> map = links.get(key);
            if(map.containsKey(subKey))
                return map.get(subKey);
        }

        return null;
    }

    public void setLink(String key, String subKey, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(subKey, value);
        if(links == null) {
            links = new HashMap<>();
        }else {

            if(!links.containsKey(key)){
                links.put(key, map);
            }else {
                Map<String, String> map1 = links.get(key);
                map1.put(subKey, value);
                links.put(key, map1);
            }
        }
    }

    public Map getLinks(){
        return links;
    }

    public List<String> getOverrideMethods() {
        return overrideMethods;
    }

    public boolean alreadyCalledOverrideMethod(String method) {
        if(overrideMethods != null && overrideMethods.size() > 0){
            for (int i = 0; i < overrideMethods.size(); i++) {
                if(overrideMethods.get(i).equals(method))
                    return true;
            }
        }
        return false;
    }

    public void setOverrideMethod(String overrideMethod) {
        if(overrideMethods == null)
            overrideMethods = new ArrayList<>();
        this.overrideMethods.add(overrideMethod);
    }

    @Override
    public String toString() {
        return "Clazz{" +
                "name='" + name + '\'' + "\n" +
                ", nameWithPackage='" + nameWithPackage + '\'' + "\n" +
                ", absolutePath=" + absolutePath + "\n" +
                ", calledMethods=" + calledMethods + "\n" +
                ", variables=" + variables + "\n" +
                ", objectType='" + objectType + '\'' + "\n" +
                ", children=" + children + "\n" +
                ", childrenStaticInitialisation=" + childrenStaticInitialisation + "\n" +
                ", classWhereCalled=" + classNameWhereCalled + "\n" +
                ", isExtends=" + isExtends + "\n" +
                ", classActivity=" + classActivity + "\n" +
                ", singleton=" + singleton +
                '}';
    }
}
