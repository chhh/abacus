/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package abacus.ui;

import abacus.Abacus;
import abacus.Globals;
import abacus.HyperSQLObject;
import abacus.HyperSQLObjectGene;
import abacus.console.AbacusTextArea;
import abacus.console.ProgressBarHandler;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;

/**
 * This class extends the Thread class so all the magic can happen here.
 */
public class UIWorkerThread extends Thread {
    /** The parent component of the GUI, e.g. the main JFrame. */
    private final Component parentComp;
    AbacusTextArea console;
    Abacus pmasc;
    AbacusUIAlerter alerter = new AbacusUIAlerter();
    File dir = null;
    Connection conn = null;
    HyperSQLObject forProteins = null;
    HyperSQLObjectGene forGenes = null;
    String db = "jdbc:hsqldb";
    String err; // used for error messages

    /**
     *
     * @param parentComp can be null, the parent GUI component, e.g. the main JFrame
     */
    public UIWorkerThread(Component parentComp) {
        this.parentComp = parentComp;
    }

    // This method is called when the thread runs
    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        pmasc = new Abacus();
        console = new AbacusTextArea();
        console.setVisible(true);
        console.append(pmasc.printHeader());
        console.append(Globals.printParameters());
        dir = new File(Globals.srcDir);
        // checking inputs and cleaning
        try {
            pmasc.cleanup(console);
            pmasc.record_XML_files(dir); // record only the protXML and pepXML files
            if (!pmasc.checkPepXmlFiles(console, alerter, console)) {
                return;
            }
            if (!pmasc.checkProtXmlFiles(console, alerter, console)) {
                return;
            }
            if (!pmasc.checkFastaFile(console, alerter, console)) {
                return;
            }
            db = pmasc.checkJdbcInMemDb(db, console);
            conn = pmasc.createDbConnection(db, console, alerter, parentComp);
            if (conn == null) {
                return;
            }
            if (!Globals.byPeptide) {
                if (pmasc.load_protXML(conn, console, alerter, parentComp)) {
                    console.changeCloseStatus(ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);
                    return;
                }
            }
            if (pmasc.load_pepXML(conn, console, alerter, parentComp)) {
                console.changeCloseStatus(ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);
                return;
            }
        } catch (IOException ex) {
            //Logger.getLogger(WorkerThread.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        }
        System.gc(); // System clean up
        console.updateProgress(1);
        // the main processing function
        if (pmasc.process(startTime, conn, console, console, alerter, parentComp)) {
            return;
        }
        // clean up
        pmasc = null;
        System.gc();
    }

}
