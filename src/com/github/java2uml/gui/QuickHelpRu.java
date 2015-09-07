package com.github.java2uml.gui;

import javax.swing.*;

/**
 * Created by mac on 07.09.15.
 */
public class QuickHelpRu extends JFrame {
    private static QuickHelpRu quickHelpRu;

    public static boolean quickHelpRuIsNull(){
        return quickHelpRu == null;
    }

    public static QuickHelpRu getInstance(){
        return QuickHelpRuHolder.quickHelpRuInstance;
    }

    private static class QuickHelpRuHolder {
        static final QuickHelpRu quickHelpRuInstance = new QuickHelpRu();
    }

    private QuickHelpRu(){
        super("Java2UML Quick Help");

        JLabel imageLabel = new JLabel(new ImageIcon(getClass().getClassLoader().getResource("quickhelp_ru.png")));
        JScrollPane scrollPane = new JScrollPane(imageLabel);

        this.add(scrollPane);
        this.setSize(670, 480);
        this.setResizable(false);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }
}
