/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * abacus_textArea.java
 *
 * Created on Jul 22, 2010, 4:16:28 PM
 */

package abacus_textArea;

import abacus.Globals;

import javax.swing.ProgressMonitor;

/**
 *
 * @author dfermin
 */
public class abacus_textArea extends javax.swing.JFrame {
    int curStatusValue = 0; // holds the progressBar value when switching to indeterminant mode
	ProgressMonitor pMonitor;

    /** Creates new form abacus_textArea */
    public abacus_textArea() {
        initComponents();
        //this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mainTextArea.setEditable(false);


		pBar.setStringPainted(true);
		pBar.setMinimum(0);
		pBar.setValue(0);

		if(Globals.byGene) pBar.setMaximum(14);
		else pBar.setMaximum(10);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        consolePanel = new javax.swing.JPanel();
        scroller = new javax.swing.JScrollPane();
        mainTextArea = new javax.swing.JTextArea();
        progressPanel = new javax.swing.JPanel();
        pBar = new javax.swing.JProgressBar();

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        consolePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("ABACUS Console"));

        mainTextArea.setColumns(20);
        mainTextArea.setRows(5);
        scroller.setViewportView(mainTextArea);

        javax.swing.GroupLayout consolePanelLayout = new javax.swing.GroupLayout(consolePanel);
        consolePanel.setLayout(consolePanelLayout);
        consolePanelLayout.setHorizontalGroup(
            consolePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(consolePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroller, javax.swing.GroupLayout.DEFAULT_SIZE, 604, Short.MAX_VALUE)
                .addContainerGap())
        );
        consolePanelLayout.setVerticalGroup(
            consolePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, consolePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroller, javax.swing.GroupLayout.DEFAULT_SIZE, 485, Short.MAX_VALUE)
                .addContainerGap())
        );

        progressPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Progress Bar"));

        javax.swing.GroupLayout progressPanelLayout = new javax.swing.GroupLayout(progressPanel);
        progressPanel.setLayout(progressPanelLayout);
        progressPanelLayout.setHorizontalGroup(
            progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(progressPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pBar, javax.swing.GroupLayout.DEFAULT_SIZE, 592, Short.MAX_VALUE)
                .addContainerGap())
        );
        progressPanelLayout.setVerticalGroup(
            progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(progressPanelLayout.createSequentialGroup()
                .addComponent(pBar, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(consolePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(progressPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(progressPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(consolePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
    * @param args the command line arguments
    */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new abacus_textArea().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel consolePanel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextArea mainTextArea;
    private javax.swing.JProgressBar pBar;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JScrollPane scroller;
    // End of variables declaration//GEN-END:variables


    public void append(String txt) {
        this.mainTextArea.append(txt);

		// this forces scrolling to the bottom of the text area
		int pos = this.mainTextArea.getDocument().getLength();
		this.mainTextArea.setCaretPosition(pos);
    }
    
	public void updateProgress(int newValue) {
		curStatusValue = pBar.getValue() + newValue;
		pBar.setValue(curStatusValue);
	}

	// function to initialize a progress monitor object
	public void monitorBoxInit(int maxValue, String message) {
		pMonitor = new ProgressMonitor(abacus_textArea.this,
					message,
					"", 0, maxValue
				);
		pMonitor.setNote("");

		pMonitor.setMillisToDecideToPopup(500);
	}

	// function to update the progress monitor's counter
	public void monitorBoxUpdate(int newValue) {
		double x = ((double)newValue / (double)pMonitor.getMaximum()) * 100.00;
		String num = Double.toString( Globals.roundDbl(x, 0) );
		String txt = "Completed " + num + "%\n";
		pMonitor.setNote(txt);
		pMonitor.setProgress(newValue);
	}

	// function to close the progressMontior window
	public void closeMonitorBox() {
		pMonitor.close();
		pMonitor = null;
	}


	// switches from a progress bar to a "shaking bar"
	public void changeBarType(String displayType) {
		curStatusValue = pBar.getValue();

		if(displayType.equals("shaker")) {
			pBar.setStringPainted(false);
			pBar.setIndeterminate(true);
		}
		else {
			pBar.setStringPainted(true);
			pBar.setIndeterminate(false);
			pBar.setValue(curStatusValue);
		}
	}

	// Function sets the text of the progress bar
	public void setProgressBarString(int iter) {
		pBar.setString(Integer.toString(iter));
	}

	// Function to change the console window clickable close status
	public void changeCloseStatus(String act) {
		if(act.equals("allowClose")) this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}
}
