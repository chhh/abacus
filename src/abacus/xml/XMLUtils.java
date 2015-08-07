/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package abacus.xml;

import abacus.Abacus;
import abacus.Globals;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author Dmitry Avtonomov
 */
public class XMLUtils {

    /**
     *
     * @param srcDir directory path
     * @param xmlFile filename in that directory
     * @param dataType
     * @param prep
     * @param fileNumber
     * @param out
     * @return
     * @throws IOException
     */
    public static boolean parseXMLDocument(String srcDir, String xmlFile, String dataType, PreparedStatement prep, int fileNumber, Appendable out) throws IOException {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        InputStream input = null;
        Path path = Paths.get(srcDir, xmlFile).toAbsolutePath();
        if (Files.notExists(path)) {
            if (out != null) {
                out.append("\nThe XML document to be parsed does not exist.\n");
            }
            return true;
        }
        XMLStreamReader xmlStreamReader = null;
        try {
            xmlStreamReader = inputFactory.createXMLStreamReader(input);
            switch (dataType) {
                case "pepXML":
                    if (parsePepXML(xmlStreamReader, xmlFile, prep, fileNumber, out)) {
                        return true; // if this returns true, there is a problem in the pepXML file
                    }
                    break;
                case "protXML":
                    if (parsePepXML(xmlStreamReader, xmlFile, prep, fileNumber, out)) {
                        return true; // if this returns true, there is a problem in the protXML file
                    }
                    break;
                default:
                    throw new IllegalArgumentException("dataType can only be 'papXML' or 'protXML'");
            }
        } catch (XMLStreamException e) {
            if (out != null) {
                out.append("\nXMLStreamException: " + e.toString() + "\n");
            } else {
                e.printStackTrace();
            }
        } finally {
            if (xmlStreamReader != null) {
                try {
                    xmlStreamReader.close();
                } catch (XMLStreamException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Function written to parse pepXML files.
     *
     * @param xmlStreamReader
     * @param xmlFile
     * @param prep
     * @param fileNumber
     * @param out
     * @return
     * @throws IOException
     */
    public static boolean parsePepXML(XMLStreamReader xmlStreamReader, String xmlFile, PreparedStatement prep, int fileNumber, Appendable out) throws IOException {
        PepXML curPSM = null;
        String err;
        boolean is_iprophet_data = false; // true means the file is an i-prophet file
        err = "Parsing pepXML [ " + (fileNumber + 1) + " of " + Globals.pepXmlFiles.size() + " ]: " + xmlFile + "\n";
        if (out != null) {
            out.append(err);
        }
        try {
            while (xmlStreamReader.hasNext()) {
                int event = xmlStreamReader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String elementName = xmlStreamReader.getLocalName();
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
                    } else if (elementName.equals("spectrum_query")) {
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
                    if (elementName.equals("interprophet_result")) {
                        curPSM.record_iniProb(xmlStreamReader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String elementName = xmlStreamReader.getLocalName();
                    if (elementName.equals("spectrum_query")) {
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
     * Function parses protXML files.
     *
     * @param xmlStreamReader
     * @param xmlFile
     * @param prep
     * @param fileNumber
     * @param out
     * @return
     * @throws java.io.IOException
     */
    public static boolean parseProtXML(XMLStreamReader xmlStreamReader, String xmlFile, PreparedStatement prep, int fileNumber, Appendable out) throws IOException {
        ProtXML curGroup = null;
        String curProtid_ = null;
        String curPep_ = null;
        String err = null;
        boolean is_iprophet_data = false; // use this to identify i-prophet files
        err = "Parsing protXML [ " + (fileNumber + 1) + " of " + Globals.protXmlFiles.size() + " ]:  " + xmlFile + "\n";
        if (out != null) {
            out.append(err);
        }
        try {
            while (xmlStreamReader.hasNext()) {
                int event = xmlStreamReader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String elementName = xmlStreamReader.getLocalName();
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
                            if (Globals.parseProtXML_header(xmlStreamReader, xmlFile)) {
                                err = "\nERROR:\n" + "The pepXML files used to create '" + xmlFile + "' could not be found.\n" + "The pepXML file names must match whatever is in the protXML file header.\n" + "I have to quit now.\n\n";
                                if (out != null) {
                                    out.append(err);
                                    return true;
                                }
                            }
                            break;
                        case "protein_group":
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
                            }
                            break;
                        case "indistinguishable_protein":
                            curProtid_ = curGroup.parse_protein_line(xmlStreamReader);
                            break;
                        case "peptide":
                            curPep_ = curGroup.parse_peptide_line(xmlStreamReader);
                            break;
                        case "modification_info":
                            curGroup.record_AA_mod_protXML(xmlStreamReader, curPep_);
                            break;
                        case "mod_aminoacid_mass":
                            curGroup.record_AA_mod_protXML(xmlStreamReader, curPep_);
                            break;
                    }
                } else if (event == XMLStreamReader.END_ELEMENT) {
                    String elementName = xmlStreamReader.getLocalName();
                    switch (elementName) {
                        case "peptide":
                            curGroup.annotate_modPeptide_protXML(curPep_);
                            curPep_ = null;
                            break;
                        case "protein":
                            curGroup.classify_group();
                            try {
                                curGroup.write_to_db(prep);
                            } catch (Exception e) {
                                if (out != null) {
                                    out.append(e.toString());
                                    return true;
                                } else {
                                    e.printStackTrace();
                                    System.exit(-1);
                                }
                            }
                            curGroup.clear_variables();
                            curProtid_ = null;
                            break;
                        case "protein_group":
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
                                if (out != null) {
                                    out.append(e.toString());
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
            if (curGroup != null) {
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
            if (out != null) {
                String msg = "Error parsing " + xmlFile + ": " + e.toString();
                out.append(msg);
                return true;
            } else {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        return false;
    }

    private XMLUtils() {throw new IllegalStateException("No instances.");}

    

}
