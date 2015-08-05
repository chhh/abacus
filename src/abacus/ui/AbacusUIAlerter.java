/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package abacus.ui;

import java.awt.Component;
import javax.swing.JOptionPane;

/**
 *
 * @author Dmitry Avtonomov
 */
public class AbacusUIAlerter implements UIAlerter {

    /**
     * Alert the user visually.
     * @param parent parent component or null
     */
    @Override
    public void alert(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "ERROR!\nCheck Console for\nmore useful information",
                "Douh!",
                JOptionPane.ERROR_MESSAGE
        );
    }

}
