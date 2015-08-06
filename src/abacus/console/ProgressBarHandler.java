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

    public static enum PROGRESS_TYPE {
        SHAKER, PROGRESS;
    }

    public static enum WND_CLOSE_STATUS {
        NOT_ALLOWED, ALLOW_CLOSE;
    }

    /**
     * switches from a progress bar to a "shaking bar".
     * @param displayType
     */
    void changeBarType(PROGRESS_TYPE displayType);

    /**
     * Function to change the console window clickable close status.
     * @param status
     */
    void changeCloseStatus(ProgressBarHandler.WND_CLOSE_STATUS status);

    /**
     * Function to close the progressMontior window.
     */
    void closeMonitorBox();

    /**
     * Function to initialize a progress monitor object.
     * @param maxValue
     * @param message
     */
    void monitorBoxInit(int maxValue, String message);

    /**
     * Function to update the progress monitor's counter.
     * @param newValue
     */
    void monitorBoxUpdate(int newValue);

    /**
     * Function sets the text of the progress bar
     * @param iter
     */
    void setProgressBarString(int iter);

    /**
     * Update the progress bar with some done work.
     * @param newValue 
     */
    void updateProgress(int newValue);

}
