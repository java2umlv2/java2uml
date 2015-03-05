package com.github.java2uml.core.parsing;

import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

/**
 * Created by nadcukandrej on 12.02.15.
 */
public class CreateSequenceUmlCode {
    private static Logger log = LoggerFactory.getLogger(CreateSequenceUmlCode.class);
    private CreateUmlCode cuc;
    private ArrayList<File> entryPoints;

    public CreateSequenceUmlCode(CreateUmlCode cuc) throws Exception {
        this.cuc = cuc;
        init();
    }

    private void init() throws Exception {
        entryPoints = new ArrayList<>();
        getEntryPoints();
        readSequence();
    }

    private void getEntryPoints() throws Exception {
        for (int i = 0; i < CreateUmlCode.classesWithAbsolutePath.size(); i++) {
            CompilationUnit cu = getCu(CreateUmlCode.classesWithAbsolutePath.get(i));
            if(new UMLDiagramSequence(this, cu).getEntryPoint())
                entryPoints.add(CreateUmlCode.classesWithAbsolutePath.get(i));
        }
        if(entryPoints.size() == 0)
            throw new CreateUmlCodeException("No entry point");
    }


    public CompilationUnit getCu(File path) throws Exception {
        // creates an input stream for the file to be parsed
        FileInputStream in = new FileInputStream(path);
        CompilationUnit cu;
        try {
            // parse the file
            cu = JavaParser.parse(in);
        } finally {
            in.close();
        }
        return cu;
    }

    private void readSequence() throws Exception {
        for (File entryPoint : entryPoints){
            log.debug("Start with : " + entryPoint);
            new UMLDiagramSequence(this, getCu(entryPoint)).startBuildSequence();
            UMLDiagramSequence.deactivateClasses();
        }
    }
}
