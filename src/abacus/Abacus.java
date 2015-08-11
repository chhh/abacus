package abacus;

import abacus.console.ProgressBarHandler;
import abacus.ui.UIAlerter;
import abacus.xml.XMLUtils;
import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class Abacus {

    public void main(String[] args) throws IOException {

        long startTime = System.currentTimeMillis();

        File dir = null;
        Connection conn = null;
        String db = "jdbc:hsqldb";

        long start_time = System.currentTimeMillis();
        long elapsed_time = 0;

        System.err.print(printHeader());

        Globals.parseCommandLineArgs(args);

        System.err.print(Globals.printParameters());

        //verify that the output file's directory is valid
        dir = new File(Globals.outputFilePath);
        String parentPath = dir.getParent();
        dir = new File(parentPath);
        if (!dir.exists()) {
            Globals.printError(Globals.ERR_OUTPUT_PATH_NOT_FOUND, System.err);
        }

        //verify that user input is a valid directory
        dir = new File(Globals.srcDir);
        if (!dir.isDirectory()) {
            Globals.printError(Globals.ERR_DIR, System.err);
        }

        // checking inputs and cleaning
        cleanup(System.err);
        record_XML_files(dir); // record only the protXML and pepXML files
        if (!checkPepXmlFiles(System.err, null, null)) {
            return;
        }
        if (!checkProtXmlFiles(System.err, null, null)) {
            return;
        }
        if (!checkFastaFile(System.err, null, null)) {
            return;
        }

        db = checkJdbcInMemDb(db, System.err);

        conn = createDbConnection(db, System.err, null, null);
        if (conn == null) {
            return;
        }

        if (!Globals.byPeptide) {
            if (load_protXML(conn, System.err, null, null)) {
                return;
            }
        }
        if (load_pepXML(conn, System.err, null, null)) {
            return;
        }

        HyperSQLObject forProteins = null;
        HyperSQLObjectGene forGenes = null;


        // the main processing function
        Appendable out = System.err;
        if (process(startTime, conn, out, null, null, null)) {
            return;
        }

    }

    private void updateProgress(ProgressBarHandler pbh, int N) {
        if (pbh != null) {
            pbh.updateProgress(N);
        }
    }

    private void updateProgressType(ProgressBarHandler pbh, ProgressBarHandler.PROGRESS_TYPE type) {
        if (pbh != null) {
            pbh.changeBarType(type);
        }
    }

    private void updateProgressCloseStatus(ProgressBarHandler pbh, ProgressBarHandler.WND_CLOSE_STATUS status) {
        if (pbh != null) {
            pbh.changeCloseStatus(status);
        }
    }

    private void updateOutput(Appendable out, CharSequence seq) {
        if (out != null) {
            try {
                out.append(seq);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void updateAlerter(UIAlerter alerter, Component comp) {
        if (alerter != null) {
            alerter.alert(comp);
        }
    }

    /**
     * The main part of the program doing the calculations.
     *
     * @param startTime use {@code System.nanoTime()} if you don't have a better
     * idea
     * @param conn
     * @param out a printable stream, can be null
     * @param pbh if you have a progress bar, use this for updates, can be null
     * @param alerter will pop up a message for the user, can be null
     * @param comp can be null, the parent to use for alerter
     * @return
     */
    public boolean process(long startTime, Connection conn, Appendable out, ProgressBarHandler pbh, UIAlerter alerter, Component comp) {
        
        HyperSQLObject forProteins = null;
        HyperSQLObjectGene forGenes = null;
        // now the work begins
        try {
            if (Globals.byPeptide) {
                // user wants peptide-level results
                forProteins = new HyperSQLObject();
                forProteins.initialize();
                forProteins.makeSrcFileTable(conn, pbh, out);

                forProteins.correctPepXMLTags(conn);
                updateProgress(pbh, 1);

                forProteins.peptideLevelResults(conn, out);
                updateProgress(pbh, 20);
            } else if (Globals.byGene) {
                // user wants gene-centric output

                forGenes = new HyperSQLObjectGene();
                forGenes.initialize();
                //forGenes.makeProtLenTable(conn, console); //deprecated function
                forGenes.makeSrcFileTable(conn, pbh, out);
                updateProgress(pbh, 1);
                forGenes.correctPepXMLTags(conn);
                if (forGenes.makeGeneTable(conn, out)) {
                    updateAlerter(alerter, comp);
                    updateProgressCloseStatus(pbh, ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);
                    return true;
                }
                updateProgress(pbh, 1);
                forGenes.makeCombinedTable(conn, out, pbh);
                updateProgress(pbh, 1);
                forGenes.makeProtXMLTable(conn, out, pbh);
                updateProgress(pbh, 1);
                System.gc(); // need more RAM
                forGenes.makeGeneCombined(conn, out);
                updateProgress(pbh, 1);
                forGenes.makeGeneXML(conn, out);
                updateProgress(pbh, 1);
                forGenes.adjustGenePeptideWT(conn, out, pbh);
                updateProgress(pbh, 1);
                forGenes.makeTempGene2pepTable(conn);
                System.gc(); // System clean up
                updateProgressType(pbh, ProgressBarHandler.PROGRESS_TYPE.SHAKER);
                forGenes.makeGeneidSummary(conn, out, pbh);
                updateProgressType(pbh, ProgressBarHandler.PROGRESS_TYPE.PROGRESS);
                updateProgress(pbh, 1);
                forGenes.makeGeneResults(conn, out);
                updateProgress(pbh, 1);
                updateProgressType(pbh, ProgressBarHandler.PROGRESS_TYPE.SHAKER);
                forGenes.makeGenePepUsageTable(conn, out, pbh);
                updateProgressType(pbh, ProgressBarHandler.PROGRESS_TYPE.PROGRESS);
                updateProgress(pbh, 1);
                System.gc(); // System clean up
                updateProgressType(pbh, ProgressBarHandler.PROGRESS_TYPE.SHAKER);
                forGenes.appendIndividualExpts_GC(conn, out, pbh);
                updateProgressType(pbh, ProgressBarHandler.PROGRESS_TYPE.PROGRESS);
                updateOutput(out, "\n");
                if (Globals.doNSAF) {
                    forGenes.getNSAF_values_gene(conn, out);
                    updateOutput(out, "\n");
                }
                updateProgress(pbh, 1);
                if (Globals.genesHaveDescriptions) { // append gene descriptions
                    forGenes.appendGeneDescriptions(conn);
                    updateProgress(pbh, 1);
                } else {
                    updateProgress(pbh, 2);
                }
                // choose output format
                if (Globals.outputFormat == Globals.geneQspecFormat) {
                    forGenes.formatQspecOutput(conn, out);
                } else {
                    forGenes.defaultResults(conn, out);
                }
                updateProgress(pbh, 1);
            } else {
                // default protein-centric output
                forProteins = new HyperSQLObject();
                forProteins.initialize();
                //forProteins.makeProtLenTable(conn, console); // deprecated function
                forProteins.makeSrcFileTable(conn, pbh, out);
                updateProgress(pbh, 1);
                forProteins.correctPepXMLTags(conn);
                forProteins.makeCombinedTable(conn, out, pbh);
                updateProgress(pbh, 1);
                forProteins.makeProtXMLTable(conn, out, pbh);
                updateProgress(pbh, 1);
                System.gc(); // need more RAM
                forProteins.makeTempProt2PepTable(conn, out, pbh);
                System.gc(); // System clean up
                //console.changeBarType("shaker");
                forProteins.makeProtidSummary(conn, out, pbh);
                //console.changeBarType("progress");
                updateProgress(pbh, 1);
                if (Globals.gene2protFile != null) {
                    forProteins.makeGeneTable(conn, out);
                    forProteins.appendGeneIDs(conn, out);
                    updateOutput(out, "\n");

                }
                if (forProteins.makeResultsTable(conn, out)) {
                    updateAlerter(alerter, comp);
                    updateOutput(out, "\nError creating results table.\n");
                    updateProgressCloseStatus(pbh, ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);
                    return true;
                }
                updateProgress(pbh, 1);
                forProteins.addProteinLengths(conn, 0, out, pbh);
                updateProgress(pbh, 1);
                // these functions deal with adjusting spectral counts
                forProteins.makeWT9XgroupsTable(conn);
                forProteins.makePepUsageTable(conn, out, pbh);
                updateProgress(pbh, 1);
                // add individual experiment data to results table
                forProteins.appendIndividualExpts(conn, out);
                updateProgress(pbh, 1);
                    // reduce the number of columns in the results table
                // by merging the groupid and siblingGroup fields
                forProteins.mergeIDfields(conn);
                if (Globals.doNSAF) {
                    forProteins.getNSAF_values_prot(conn, out);
                    updateProgressCloseStatus(pbh, ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);
                    updateOutput(out, "\n");
                }
                if (Globals.makeVerboseOutput) {
                    forProteins.addExtraProteins(conn, out);
                    forProteins.addProteinLengths(conn, 1, out, pbh);
                }
                // choose output format
                switch (Globals.outputFormat) {
                    case Globals.protQspecFormat:
                        forProteins.formatQspecOutput(conn, out);
                        break;
                    case Globals.customOutput:
                        forProteins.customOutput(conn, out);
                        break;
                    default:
                        forProteins.defaultResults(conn, out);
                }
                updateProgress(pbh, 1);
            }
            // user has elected to keep database, remove unnecessary tables.
            if (Globals.keepDB) {
                if (Globals.byGene && forGenes != null) {
                    forGenes.cleanUp(conn);
                } else if (forProteins != null) {
                    forProteins.cleanUp(conn);
                }
            } else { // left over files that should be removed
                String tmpFile = "" + Globals.DBname + ".properties";
                File f = new File(tmpFile);
                if (f.exists()) {
                    f.delete();
                }
            }
            updateProgressCloseStatus(pbh, ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);
            long elapsedTime = System.currentTimeMillis() - startTime;
            String timeStr = Globals.formatTime(elapsedTime);
            updateOutput(out, "\n\nTotal runtime (hh:mm:ss): " + timeStr + "\n");
            updateOutput(out, "\nYou may now close this window\n\n");
        } catch (Exception ex) {
            throw new RuntimeException("Somethign awful happened during main processing step", ex);
        } finally {
            try {
                // Whatever happens, shutdown the HSQLDB connection nicely
                if (conn != null) {
                    conn.createStatement().execute("SHUTDOWN");
                    conn.close();
                }
            } catch (Exception e) {
                updateOutput(out, e.toString());
                updateProgressCloseStatus(pbh, ProgressBarHandler.WND_CLOSE_STATUS.ALLOW_CLOSE);

            }
        }
        return false;
    }

    /**
     *
     * @param db
     * @param err
     * @param alerter
     * @param comp
     * @return null if connection could not be established
     * @throws IOException
     */
    public Connection createDbConnection(String db, Appendable err, UIAlerter alerter, Component comp) throws IOException {
        Connection conn;
        //Connect to hyperSQL database object
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
            conn = DriverManager.getConnection(db, "SA", "");

        } catch (ClassNotFoundException | SQLException e) {
            if (err != null) {
                err.append("There was an error connecting to the HyperSQL database\n");
                err.append(e.toString());
                err.append("\n");
            }
            if (alerter != null) {
                alerter.alert(comp);
            }
            return null;
        }
        return conn;
    }

    /**
     * By default, the program stores the database in memory.<br/>
     * If the user wants to keep the database, this code allows them to.<br/>
     * NOTE: writing to disk is much slower!!!
     *
     * @param db
     * @param err
     * @return
     */
    public String checkJdbcInMemDb(String db, Appendable err) throws IOException {
        if (Globals.keepDB) {
            db += ":file:" + Globals.DBname;
            if (err != null) {
                err.append("\nDatabase will be written to disk within the following files and folders:\n");
                err.append("\t" + Globals.DBname + ".script\n");
                err.append("\t" + Globals.DBname + ".properties\n");
                err.append("\t" + Globals.DBname + ".tmp\n\n");
                err.append("NOTE: Writing to disk slows things down so please be patient...\n\n\n");
            }
        } else {
            db += ":mem:memoryDB"; //default method, do everything in memory
        }
        return db;
    }

    /**
     * Check the provided FASTA file for proper contents.
     *
     * @param out stream for textual output
     * @param alerter something that alerts the user visually
     * @param comp parent component of the alerter
     * @return true if check was passed
     * @throws IOException
     */
    public boolean checkFastaFile(Appendable out, UIAlerter alerter, Component comp) throws IOException {
        boolean result = true;
        if (!Globals.byPeptide) {

            if (Globals.fastaFile == null || Globals.fastaFile.isEmpty()) {
                if (out != null) {
                    out.append("No fasta file was given so protein lengths will not be reported\n\n");
                }
            } else {
                if (out != null) {
                    out.append("Retrieving protein lengths from\n'" + Globals.fastaFile + "'\n");
                }
                if (Globals.parseFasta(out, null, null)) {
                    out.append("Error parsing fasta file.\n");
                    result = false;
                }
                if (out != null) {
                    out.append("\nDone\n");
                }
            }
        }
        return result;
    }

    /**
     * Check the provided prot.xml files for existence.
     *
     * @param err error stream for textual output
     * @param alerter something that alerts the user visually
     * @param comp parent component of the alerter
     * @return
     * @throws IOException
     */
    public boolean checkProtXmlFiles(Appendable err, UIAlerter alerter, Component comp) throws IOException {
        if (!Globals.byPeptide && (Globals.protXmlFiles == null || Globals.protXmlFiles.isEmpty())) {
            if (err != null) {
                err.append("No protXML files were found in '" + Globals.srcDir + "'\n");
            }
            if (alerter != null) {
                alerter.alert(comp);
            }

            return false;
        }
        return true;
    }

    /**
     * Check the provided pep.xml files for existence.
     *
     * @param err error stream for textual output
     * @param alerter something that alerts the user visually
     * @param comp parent component of the alerter
     * @return
     * @throws IOException
     */
    public boolean checkPepXmlFiles(Appendable err, UIAlerter alerter, Component comp) throws IOException {
        if (Globals.pepXmlFiles == null || Globals.pepXmlFiles.isEmpty()) {
            if (err != null) {
                err.append("No pepXML files were found in '" + Globals.srcDir + "'\n");
            }
            if (alerter != null) {
                alerter.alert(comp);
            }
            return false;
        }
        return true;
    }

    /**
     * This code checks to see if the database we are going to create already
     * exists if it does, delete it first. HyperSQL makes several files when it
     * makes a database so we have to iterate through them.
     *
     * @param err
     * @throws java.io.IOException
     */
    public void cleanup(Appendable err) throws IOException {

        String[] toRemove = {".data", ".properties", ".script", ".tmp", ".log"};

        for (String aToRemove : toRemove) {
            String tmpFile = "" + Globals.DBname + aToRemove;
            File f = new File(tmpFile);
            if (f.exists()) {

                f.delete();
                err.append("Abacus disk clean up: removing " + tmpFile + "\n");

            }
        }
        err.append("\n");
    }

    /**
     * Converts the contents of a file into a CharSequence suitable for use by
     * the regex package.
     *
     * @param filename
     * @return
     * @throws java.io.IOException
     */
    public static CharSequence fromFile(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        FileChannel fc = fis.getChannel();

        // Create a read-only CharBuffer on the file
        ByteBuffer bbuf = fc.map(FileChannel.MapMode.READ_ONLY, 0, (int) fc.size());
        CharBuffer cbuf = Charset.forName("8859_1").newDecoder().decode(bbuf);
        return cbuf;
    }

    /**
     * Function opens up files in the given directory and determines if they are
     * protXML files based upon their content.
     *
     * @param dir
     */
    public void record_XML_files(File dir) {

        FilenameFilter filter = null;

        // filter to only select pepXML files
        filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(Globals.pepXMLsuffix);
            }
        };
        String[] pep = dir.list(filter);
        for (String aPep : pep) {
            if (!Globals.pepXmlFiles.contains(aPep)) {
                Globals.pepXmlFiles.add(aPep);
            }
        }

        if (!Globals.byPeptide) {
            // filter to only select protXML files
            filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(Globals.protXMLsuffix);
                }
            };
            String[] prot = dir.list(filter);

            for (String aProt : prot) {
                if (!Globals.protXmlFiles.contains(aProt)) {
                    Globals.protXmlFiles.add(aProt);
                }
            }
        }

    }




    /**
     * Function iterates through the protXML files and loads them into SQLite
     * Loading is always handled by the prot version of the SQLite objects since
     * the basic starting material is always protein-centric.
     *
     * @param conn
     * @param out
     * @param alerter
     * @param comp
     * @return
     * @throws java.io.IOException
     */
    public boolean load_protXML(Connection conn, Appendable out, UIAlerter alerter, Component comp) throws IOException {
        boolean status = false;
        Statement stmt = null;
        PreparedStatement prep = null;
        try {
            String err = "Loading protXML files\n";

            if (out != null) {
                out.append(err);
            }

            stmt = conn.createStatement();
            String query;
            stmt.executeUpdate("DROP TABLE IF EXISTS RAWprotXML");

            query = "CREATE CACHED TABLE RAWprotXML ("
                    + "  srcFile VARCHAR(250),"
                    + "  groupid INT,"
                    + "  siblingGroup VARCHAR(5),"
                    + "  Pw DECIMAL(8,6),"
                    + "  localPw DECIMAL(8,6),"
                    + "  protId VARCHAR(100),"
                    + "  isFwd INT,"
                    + "  peptide VARCHAR(250),"
                    + "  modPeptide VARCHAR(250),"
                    + "  charge INT,"
                    + "  iniProb DECIMAL(8,6),"
                    + "  wt DECIMAL(8,6),"
                    + "  defline VARCHAR(1000)"
                    + ")";
            stmt.executeUpdate(query);

            query = "INSERT INTO RAWprotXML VALUES ("
                    + "?, " //srcFile
                    + "?, " //groupid
                    + "?, " //siblingGroup
                    + "?, " //Pw
                    + "?, " //localPw
                    + "?, " //protId
                    + "?, " //isFwd
                    + "?, " //peptide
                    + "?, " //modPeptide
                    + "?, " //charge
                    + "?, " //iniProb
                    + "?, " //wt
                    + "? " //defline
                    + ");";
            prep = conn.prepareStatement(query);

            // At this juncture, the database should have been created.
            // We will now iterate through the protXML files loading the relevant content
            for (int i = 0; i < Globals.protXmlFiles.size(); i++) {
                Globals.proceedWithQuery = false;
                status = XMLUtils.parseXMLDocument(Globals.srcDir, Globals.protXmlFiles.get(i), "protXML", prep, i, out);
                if (status) {
                    return status; // a return of 'true' means something went wrong
                }
                if (Globals.proceedWithQuery) { // if queryCtr = true then you got at least 1 row to insert into the DB
                    conn.setAutoCommit(false);
                    prep.executeBatch();
                    conn.commit();
                    conn.setAutoCommit(true);
                    prep.clearBatch();
                }
            }

            prep.clearBatch();
        } catch (SQLException e) {
            if (out != null) {
                out.append("\nThere was an error parsing your protXML files.\n");
                out.append("This usually happens due to a syntax or formatting error in the protXML file.\n");
            }
            if (alerter != null) {
                alerter.alert(comp);
            }
            e.printStackTrace();
            status = true;
        } finally {
            // clean up
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (prep != null) {
                    prep.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (out != null) {
            out.append("\n");
        }
        return status;
    }

    /**
     * Function iterates through the pepXML files and loads them into HyperSQL
     * Loading is always handled by the prot version of the SQLite objects since
     * the basic starting material is always protein-centric.
     *
     * @param conn
     * @param out
     * @param alerter
     * @param comp
     * @return
     * @throws java.io.IOException
     */
    public boolean load_pepXML(Connection conn, Appendable out, UIAlerter alerter, Component comp) throws IOException {
        boolean status = false;
        Statement stmt = null;
        PreparedStatement prep = null;
        try {
            String err = "Loading pepXML files\n";
            if (out != null) {
                out.append(err);
            }

            stmt = conn.createStatement();
            String query;

            stmt.executeUpdate("DROP TABLE IF EXISTS pepXML");

            query = "CREATE CACHED TABLE pepXML ("
                    + "  srcFile VARCHAR(250),"
                    + "  specId VARCHAR(250),"
                    + "  charge TINYINT,"
                    + "  peptide VARCHAR(250),"
                    + "  modPeptide VARCHAR(250),"
                    + "  iniProb DECIMAL(8,6)"
                    + ")";

            stmt.executeUpdate(query);

            query = "INSERT INTO pepXML VALUES ("
                    + "?, " //srcFile
                    + "?, " //specId
                    + "?, " //charge
                    + "?, " //peptide
                    + "?, " //modPeptide
                    + "? " //iniProb
                    + ")";
            prep = conn.prepareStatement(query);

            // At this juncture, the database should have been created.
            // We will now iterate through the pepXML files loading the relevant content
            for (int i = 0; i < Globals.pepXmlFiles.size(); i++) {
                Globals.proceedWithQuery = false;
                status = XMLUtils.parseXMLDocument(Globals.srcDir, Globals.pepXmlFiles.get(i), "pepXML", prep, i, out);
                if (status) {
                    return status; // a return of 'true' means something went wrong
                }
                if (Globals.proceedWithQuery) { // if queryCtr > 0 then you got at least 1 row to insert into the DB
                    conn.setAutoCommit(false);
                    prep.executeBatch();
                    conn.setAutoCommit(true);
                    prep.clearBatch();
                }
            }
            prep.clearBatch();
        } catch (SQLException | IOException e) {
            if (out != null) {
                out.append("There was an error parsing your pepXML files\n\n");
                status = true;
            }
            if (alerter != null) {
                alerter.alert(comp);
            }
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (prep != null) {
                    prep.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (out != null) {
            out.append("\n");
        }
        return status;
    }

    /**
     * Function just reports version and built-date for program.
     *
     * @return
     */
    public String printHeader() {
        String ret;
        StringBuilder sb = new StringBuilder(0);
        sb.append("\n***********************************\n");
        sb.append("\tAbacus\n");
        try {
            sb.append("\tVersion: ");
            //sb.append(abacus.class.getPackage().getImplementationVersion());
            sb.append("2.5");
        } catch (Exception e) {
            // Don't print anything
        }
        sb.append("\n***********************************\n");
        sb.append(
                "Developed and written by: Damian Fermin and Alexey Nesvizhskii\n"
                + "Copyright 2010 Damian Fermin\n\n"
                + "Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                + "you may not use this file except in compliance with the License.\n"
                + "You may obtain a copy of the License at \n\n"
                + "http://www.apache.org/licenses/LICENSE-2.0\n\n"
                + "Unless required by applicable law or agreed to in writing, software\n"
                + "distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                + "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                + "See the License for the specific language governing permissions and\n"
                + "limitations under the License.\n\n"
        );
        ret = sb.toString();
        return ret;
    }

}
