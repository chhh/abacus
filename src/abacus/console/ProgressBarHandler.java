/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package abacus.console;

/**
 *
 * @author Dmitry Avtonomov
 */
public interface ProgressBarHandler {

    // switches from a progress bar to a "shaking bar"
    void changeBarType(String displayType);

    // Function to change the console window clickable close status
    void changeCloseStatus(String act);

    // function to close the progressMontior window
    void closeMonitorBox();

    // function to initialize a progress monitor object
    void monitorBoxInit(int maxValue, String message);

    // function to update the progress monitor's counter
    void monitorBoxUpdate(int newValue);

    // Function sets the text of the progress bar
    void setProgressBarString(int iter);

    void updateProgress(int newValue);

}
