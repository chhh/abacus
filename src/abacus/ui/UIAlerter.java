/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package abacus.ui;

import java.awt.Component;

/**
 * Creates a visual alert for the user.
 * @author Dmitry Avtonomov
 */
public interface UIAlerter {

    /**
     * Display an alert dialog, using {@code parent} as the parent frame.
     * @param parent
     */
    void alert(Component parent);
}
