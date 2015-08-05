package abacus;

import abacus.ui.UIAlerter;
import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


public class Abacus {

    public void main(String[] args) throws IOException {

        File dir = null;
        Connection conn = null;
        HyperSQLObject forProteins = null;
        HyperSQLObjectGene forGenes = null;
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
        if (!checkPepXmlFiles(System.err, null, null)) return;
        if (!checkProtXmlFiles(System.err, null, null)) return;
        if (!checkFastaFile(System.err, null, null)) return;

        db = checkJdbcInMemDb(db, System.err);

        conn = createDbConnection(db, System.err, null, null);
        if (conn == null) return;

        if (!Globals.byPeptide) {
            if (load_protXML(conn, System.err, null, null)) return;
        }
        if (load_pepXML(conn, System.err, null, null)) return;
        


        try {

            if (Globals.byPeptide) { // user wants peptide-level results
                forProteins = new HyperSQLObject();
                forProteins.initialize();
                forProteins.makeSrcFileTable(conn, null);

                forProteins.correctPepXMLTags(conn);
                forProteins.peptideLevelResults(conn, null);

            } else if (Globals.byGene) { // user wants gene-centric output

                forGenes = new HyperSQLObjectGene();
                forGenes.initialize();

                forGenes.makeSrcFileTable(conn, null);
                //forGenes.makeProtLenTable(conn, null); //deprecated function
                forGenes.correctPepXMLTags(conn);

                forGenes.makeGeneTable(conn, null);
                forGenes.makeCombinedTable(conn, null);
                forGenes.makeProtXMLTable(conn, null);

                System.gc(); // need more memory

                forGenes.makeGeneCombined(conn, null);
                forGenes.makeGeneXML(conn, null);
                forGenes.adjustGenePeptideWT(conn, null);

                forGenes.makeTempGene2pepTable(conn);

                forGenes.makeGeneidSummary(conn, null);
                forGenes.makeGeneResults(conn, null);

                forGenes.makeGenePepUsageTable(conn, null);
                forGenes.appendIndividualExpts_GC(conn, null);

                if (Globals.doNSAF) { //generate NSAF data
                    forGenes.getNSAF_values_gene(conn, null);
                }

                if (Globals.genesHaveDescriptions) { // append gene descriptions
                    forGenes.appendGeneDescriptions(conn);
                }

                // choose output format
                if (Globals.outputFormat == Globals.geneQspecFormat) {
                    forGenes.formatQspecOutput(conn, null);
                } else {
                    forGenes.defaultResults(conn, null);
                }

            } else { // default protein-centric output

                forProteins = new HyperSQLObject();
                forProteins.initialize();

                forProteins.makeSrcFileTable(conn, null);
                //forProteins.makeProtLenTable(conn, null); //deprecated function
                forProteins.correctPepXMLTags(conn);

                forProteins.makeCombinedTable(conn, null);
                forProteins.makeProtXMLTable(conn, null);

                System.gc(); // need more memory

                forProteins.makeTempProt2PepTable(conn, null);

                forProteins.makeProtidSummary(conn, null);

                if (Globals.gene2protFile != null) {
                    forProteins.makeGeneTable(conn, null);
                    forProteins.appendGeneIDs(conn, null);
                    System.err.print("\n");
                }

                forProteins.makeResultsTable(conn, null);
                forProteins.addProteinLengths(conn, null, 0);

                // these functions deal with adjusting spectral counts
                forProteins.makeWT9XgroupsTable(conn);
                forProteins.makePepUsageTable(conn, null);

                // add individual experiment data to results table
                forProteins.appendIndividualExpts(conn, null);

				// reduce the number of columns in the results table
                // by merging the groupid and siblingGroup fields
                forProteins.mergeIDfields(conn);

                if (Globals.doNSAF) {
                    forProteins.getNSAF_values_prot(conn, null);
                }

                if (Globals.makeVerboseOutput) {
                    forProteins.addExtraProteins(conn, null);
                    forProteins.addProteinLengths(conn, null, 1);
                }

                // choose output format
                switch (Globals.outputFormat) {
                    case Globals.protQspecFormat:
                        forProteins.formatQspecOutput(conn, null);
                        break;
                    case Globals.customOutput:
                        forProteins.customOutput(conn, null);
                        break;
                    default:
                        forProteins.defaultResults(conn, null);
                }
            }

            // user has elected to keep database, remove unnecessary tables.
            if (Globals.keepDB) {
                if (Globals.byGene) {
                    forGenes.cleanUp(conn);
                } else {
                    forProteins.cleanUp(conn);
                }
            } else { // left over files that should be removed
                String tmpFile = "" + Globals.DBname + ".properties";
                File f = new File(tmpFile);
                if (f.exists()) {
                    f.delete();
                }
                f = null;
            }

            //shut things down
            conn.createStatement().execute("SHUTDOWN");
            conn.close();
            conn = null;
            elapsed_time = System.currentTimeMillis() - start_time;
            String timeStr = Globals.formatTime(elapsed_time);
            System.err.println("\nTotal runtime (hh:mm:ss): " + timeStr + "\n");
            System.gc();

        } catch (Exception e) {
            e.printStackTrace();
        }
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
     * @param err error stream for textual output
     * @param alerter something that alerts the user visually
     * @param comp parent component of the alerter
     * @return true if check was passed
     * @throws IOException
     */
    public boolean checkFastaFile(Appendable err, UIAlerter alerter, Component comp) throws IOException {
        boolean result = true;
        if (!Globals.byPeptide) {

            if (Globals.fastaFile == null || Globals.fastaFile.isEmpty()) {
                if (err != null)
                    err.append("No fasta file was given so protein lengths will not be reported\n\n");
            } else {
                if (err != null)
                    err.append("Retrieving protein lengths from\n'" + Globals.fastaFile + "'\n");
                result = Globals.parseFasta(err, null, null);
                if (err != null)
                    err.append("\n");
            }
        }
        return result;
    }

    /**
     * Check the provided prot.xml files for existence.
     * @param err error stream for textual output
     * @param alerter something that alerts the user visually
     * @param comp parent component of the alerter
     * @return
     * @throws IOException
     */
    public boolean checkProtXmlFiles(Appendable err, UIAlerter alerter, Component comp) throws IOException {
        if (!Globals.byPeptide && (Globals.protXmlFiles == null || Globals.protXmlFiles.isEmpty())) {
            if (err != null)
                err.append("No protXML files were found in '" + Globals.srcDir + "'\n");
            if (alerter != null)
                alerter.alert(comp);
            
            return false;
        }
        return true;
    }


    /**
     * Check the provided pep.xml files for existence.
     * @param err error stream for textual output
     * @param alerter something that alerts the user visually
     * @param comp parent component of the alerter
     * @return
     * @throws IOException
     */
    public boolean checkPepXmlFiles(Appendable err, UIAlerter alerter, Component comp) throws IOException {
        if (Globals.pepXmlFiles == null || Globals.pepXmlFiles.isEmpty()) {
            if (err != null)
                err.append("No pepXML files were found in '" + Globals.srcDir + "'\n");
            if (alerter != null)
                alerter.alert(comp);
            return false;
        }
        return true;
    }

    /**
     * This code checks to see if the database we are going to create already
     * exists if it does, delete it first. HyperSQL makes several files when it
     * makes a database so we have to iterate through them.
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

    public static boolean parseXMLDocument(String xmlFile, String dataType, PreparedStatement prep, int fileNumber, Appendable console) throws IOException {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();

        InputStream input = null;

        try {
            String file_path = "";
            file_path = Globals.srcDir + Globals.fileSepChar + xmlFile;

            input = new FileInputStream(new File(file_path));

        } catch (FileNotFoundException e) {
            if (console != null) {
                console.append("\nException getting input XML file.\n");
            } else {
                e.printStackTrace();
            }
        }

        XMLStreamReader xmlStreamReader = null;
        try {
            xmlStreamReader = inputFactory.createXMLStreamReader(input);
        } catch (XMLStreamException e) {
            if (console != null) {
                console.append("\nException getting xmlStreamReader object.\n");
            } else {
                e.printStackTrace();
            }
        }

        // Based on dataType, call the appropriate function
        boolean status = true;
        if (dataType.equals("pepXML")) {
            status = parsePepXML(xmlStreamReader, xmlFile, prep, fileNumber, console);
            if (status) {
                return status; // if this returns true, there is a problem in the pepXML file
            }
        }
        if (dataType.equals("protXML")) {
            status = parseProtXML(xmlStreamReader, xmlFile, prep, fileNumber, console);
            if (status) {
                return status; // if this returns true, there is a problem in the protXML file
            }
        }

        try {
            if (xmlStreamReader != null)
                xmlStreamReader.close();
        } catch (XMLStreamException e) {
            if (console != null) {
                console.append(e.toString());
            }
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Function parses protXML files
     * @param xmlStreamReader
     * @param xmlFile
     * @param prep
     * @param fileNumber
     * @param console
     * @return
     * @throws java.io.IOException
     */
    public static boolean parseProtXML(XMLStreamReader xmlStreamReader, String xmlFile, PreparedStatement prep, int fileNumber, Appendable console) throws IOException {
        ProtXML curGroup = null;   // current protein group
        String curProtid_ = null;   // need this to get protein description
        String curPep_ = null;   // need this to annotate any AA modifications
        String err = null; // text printed to screen or console
        boolean is_iprophet_data = false; // use this to identify i-prophet files

        err = "Parsing protXML [ " + (fileNumber + 1) + " of " + Globals.protXmlFiles.size() + " ]:  " + xmlFile + "\n";
        if (console != null) {
            console.append(err);
        } else {
            System.err.print(err);
        }

        try {
            while (xmlStreamReader.hasNext()) {
                int event = xmlStreamReader.next();

                if (event == XMLStreamConstants.START_ELEMENT) { // beginning of new element
                    String elementName = xmlStreamReader.getLocalName();

                    /*
                     * This code determines if the current protXML file represents an i-prophet
                     * output file.
                     */
                    switch (elementName) {
                        case "proteinprophet_details":
                            for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
                                String n = xmlStreamReader.getAttributeLocalName(i);
                                String v = xmlStreamReader.getAttributeValue(i);
                                if (n.equals("run_options")) {
                                    if (v.contains("IPROPHET")) {
                                        is_iprophet_data = true;
                                    }
                                    break;
                                }
                            }
                            break;

                        case "protein_summary_header":
                             // This code identifies the pepXML files used for this protXML file
                            // This information is used to map between these files.
                            if (Globals.parseProtXML_header(xmlStreamReader, xmlFile)) {
                                err = "\nERROR:\n"
                                        + "The pepXML files used to create '" + xmlFile + "' could not be found.\n"
                                        + "The pepXML file names must match whatever is in the protXML file header.\n"
                                        + "I have to quit now.\n\n";

                                if (console != null) {
                                    console.append(err);
                                    return true;
                                } else {
                                    System.err.print(err);
                                    System.exit(-1);
                                }
                            }
                            break;

                        case "protein_group":  // beginning of new protein group
                            curGroup = new ProtXML(xmlFile, is_iprophet_data);
                            curGroup.parse_protGroup_line(xmlStreamReader);
                            break;

                        case "protein":
                            curProtid_ = curGroup.parse_protein_line(xmlStreamReader);
                            break;

                        case "annotation":
                            for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
                                String n = xmlStreamReader.getAttributeLocalName(i);
                                String v = xmlStreamReader.getAttributeValue(i);
                                if (n.equals("protein_description")) {
                                    curGroup.setProtId(v, curProtid_);
                                    curProtid_ = null;
                                    break;
                                }
                                v = null;
                                n = null;
                            }
                            break;

                        case "indistinguishable_protein":
                            curProtid_ = curGroup.parse_protein_line(xmlStreamReader);
                            break;

                        case "peptide":
                            // beginning of peptide record
                            curPep_ = curGroup.parse_peptide_line(xmlStreamReader);
                            break;

                        case "modification_info":
// N-terminal modifcation
                            curGroup.record_AA_mod_protXML(xmlStreamReader, curPep_);
                            break;
                        case "mod_aminoacid_mass":
                            curGroup.record_AA_mod_protXML(xmlStreamReader, curPep_);
                            break;
                    }

                } else if (event == XMLStreamReader.END_ELEMENT) { // end of a record
                    String elementName = xmlStreamReader.getLocalName();

                    switch (elementName) {
                        case "peptide":
                            curGroup.annotate_modPeptide_protXML(curPep_);
                            curPep_ = null;
                            break;

                        case "protein":  // end of current protein
                            curGroup.classify_group();
                            try {
                                curGroup.write_to_db(prep);
                            } catch (Exception e) {
                                if (console != null) {
                                    console.append(e.toString());
                                    return true;
                                } else {
                                    e.printStackTrace();
                                    System.exit(-1);
                                }

                            }
                            curGroup.clear_variables();
                            curProtid_ = null;
                            break;

                        case "protein_group":  // end of protein group
                            curGroup.classify_group();
                            try {
                                if (xmlFile.contains(Globals.combinedFile)) {
                                    if (curGroup.getPw() >= Globals.minCombinedFilePw) {
                                        curGroup.write_to_db(prep);
                                    }
                                } else {
                                    if (curGroup.getPw() >= Globals.minPw) {
                                        curGroup.write_to_db(prep);
                                    }
                                }
                            } catch (Exception e) {
                                if (console != null) {
                                    console.append(e.toString());
                                    return true;
                                } else {
                                    e.printStackTrace();
                                    System.exit(-1);
                                }
                            }

                            curGroup.clear_variables();
                            curGroup = null;
                            curProtid_ = null;
                            break;
                    }
                }
            }

            if (curGroup != null) { // record last group entry
                curGroup.classify_group();
                if (xmlFile.contains(Globals.combinedFile)) {
                    if (curGroup.getPw() >= Globals.minCombinedFilePw) {
                        curGroup.write_to_db(prep);
                    }
                } else {
                    if (curGroup.getPw() >= Globals.minPw) {
                        curGroup.write_to_db(prep);
                    }
                }
                curGroup.clear_variables();
                curGroup = null;
                curProtid_ = null;
            }

        } catch (XMLStreamException e) {
            if (console != null) {
                String msg = "Error parsing " + xmlFile + ": " + e.toString();
                console.append(msg);
                return true;
            } else {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        return false;
    }


    /**
     * Function written to parse pepXML files.
     * @param xmlStreamReader
     * @param xmlFile
     * @param prep
     * @param fileNumber
     * @param out
     * @return
     * @throws IOException
     */
    public static boolean parsePepXML(XMLStreamReader xmlStreamReader, String xmlFile, PreparedStatement prep, int fileNumber, Appendable out) throws IOException {

        PepXML curPSM = null;  // current peptide-to-spectrum match
        String err; // holds string for stderr or console
        boolean is_iprophet_data = false; // true means the file is an i-prophet file

        err = "Parsing pepXML [ " + (fileNumber + 1) + " of " + Globals.pepXmlFiles.size() + " ]: " + xmlFile + "\n";

        if (out != null) {
            out.append(err);
        } else {
            System.err.print(err);
        }

        try {
            while (xmlStreamReader.hasNext()) {
                int event = xmlStreamReader.next();

                if (event == XMLStreamConstants.START_ELEMENT) { //beginning of new element
                    String elementName = xmlStreamReader.getLocalName();

                    // determine if this file is an i-prophet file
                    if (elementName.equals("analysis_summary")) {
                        for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
                            String n = xmlStreamReader.getAttributeLocalName(i);
                            String v = xmlStreamReader.getAttributeValue(i);
                            if (n.equals("analysis")) {
                                if (v.equals("interprophet")) {
                                    is_iprophet_data = true;
                                }
                                break;
                            }
                        }
                    }

                    if (elementName.equals("peptideprophet_summary")) {
                        xmlStreamReader.next();
                    } else if (elementName.equals("spectrum_query")) { // new peptide record starts
                        curPSM = new PepXML(xmlFile, is_iprophet_data);
                        curPSM.parse_pepXML_line(xmlStreamReader);
                    }

                    if (elementName.equals("search_hit")) {
                        curPSM.parse_pepXML_line(xmlStreamReader);
                    }

                    if (elementName.equals("modification_info")) {
                        curPSM.record_AA_mod(xmlStreamReader);
                    }

                    if (elementName.equals("mod_aminoacid_mass")) {
                        curPSM.record_AA_mod(xmlStreamReader);
                    }

                    if (elementName.equals("search_score")) {
                        curPSM.parse_search_score_line(xmlStreamReader);
                    }

                    if (elementName.equals("peptideprophet_result")) {
                        curPSM.record_iniProb(xmlStreamReader);
                    }

					// If the user provided iProphet input, we wil take the iProphet probability
                    // and use that instead of the PeptideProphet probability
                    if (elementName.equals("interprophet_result")) {
                        curPSM.record_iniProb(xmlStreamReader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) { // end of element
                    String elementName = xmlStreamReader.getLocalName();

                    if (elementName.equals("spectrum_query")) { // end of peptide record
                        curPSM.annotate_modPeptide();

                        try {
                            if (curPSM.getIniProb() >= Globals.iniProbTH) {
                                curPSM.write_to_db(prep);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.exit(-1);
                        }
                        curPSM = null;
                    }
                }
            }
        } catch (XMLStreamException | NullPointerException e) {
            if (out != null) {
                out.append("\nDied parsing " + xmlFile + "\n");
                out.append("This error means there is a problem with the formatting of your pepXML file.\n");
                out.append("Exiting now... sorry\n");
                return true;
            }
            e.printStackTrace();
        }
        return false;
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
                status = parseXMLDocument(Globals.protXmlFiles.get(i), "protXML", prep, i, out);
                if (status) {
                    return status; // a return of 'true' means something went wrong
                }
                if (Globals.proceedWithQuery) { // if queryCtr = true then you got at least 1 row to insert into the DB
                    conn.setAutoCommit(false);
                    prep.executeBatch();
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
            if (stmt != null)
                stmt.close();
            if (prep != null)
                prep.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (out != null) out.append("\n");
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
                status = parseXMLDocument(Globals.pepXmlFiles.get(i), "pepXML", prep, i, out);
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
                if (stmt != null) stmt.close();
                if (prep != null) prep.close();
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
