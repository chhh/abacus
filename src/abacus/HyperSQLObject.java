package abacus;

import abacus.console.ProgressBarHandler;
import abacus.debug.DbgUtils;
import org.hsqldb.util.DatabaseManagerSwing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class HyperSQLObject {

    // variables specific to the database queries
    protected String combinedFile = null;
    protected String decoyTag = null;

    protected double maxIniProbTH = -1;
    protected double iniProbTH = -1;
    protected double minCombinedFilePw = -1;
    protected double minPw = -1;

    // this variable is fixed and not adjusted by the user
    protected double wtTH = 0.9;

    public HyperSQLObject() {
    }

    public void initialize() {
        if (!Globals.byPeptide) {
            combinedFile = Globals.combinedFile;
            decoyTag = Globals.decoyTag;
            maxIniProbTH = Globals.maxIniProbTH;
            minCombinedFilePw = Globals.minCombinedFilePw;
            minPw = Globals.minPw;
        }
        iniProbTH = Globals.iniProbTH;
    }

    public void makeSrcFileTable(Connection conn, ProgressBarHandler pbh, Appendable out) throws Exception {

        if (out != null) {
            out.append("Creating srcFileTags table\n");
        }

        Statement stmt = conn.createStatement();
        String query = null;
        Iterator< Entry<String, String>> iter = null;
        int N = 0;
        int ctr = 0;

        if (Globals.pepTagHash.isEmpty()) {
            Globals.recordPepXMLtags();
        }

        if (out != null) {
            out.append("  Creating schema structure\n");
        }
        stmt.executeUpdate("DROP TABLE IF EXISTS srcFileTags");

        query = "CREATE TABLE srcFileTags ("
                + "  srcFile VARCHAR(250),"
                + "  tag VARCHAR(250),"
                + "  fileType VARCHAR(20)"
                + ")";
        stmt.executeUpdate(query);

        N = 3; // 3 indexes need to be built so we preset N to 3
        N += Globals.protTagHash.size();
        N += Globals.pepTagHash.size();
        if (pbh != null) {
            pbh.monitorBoxInit(N, "srcFileTags Table");
        }

        if (!Globals.byPeptide) {
            // first load protXML files
            iter = Globals.protTagHash.entrySet().iterator();
            ctr = 1;

            if (out != null) {
                out.append("  Inserting protein tags\n");
            }

            final String type = "prot";
            StringBuilder sb = new StringBuilder(1024);
            while (iter.hasNext()) {
                Map.Entry<String, String> pairs = iter.next();
                String srcFile = pairs.getKey();
                String t = pairs.getValue();

                //String tag = Globals.replaceAll(Globals.replaceAll(pairs.getValue(), '.', '_'), '-', '_');
                String tag = pairs.getValue().replace('.', '_').replace('-', '_');


                if (Character.isDigit(tag.charAt(0))) {
                    String tmp = "x" + tag;
                    tag = tmp;
                    tmp = null;
                }

                sb.setLength(0);
                sb.append("INSERT INTO srcFileTags VALUES( ")
                        .append(" '").append(srcFile.toUpperCase()).append("', ")
                        .append(" '").append(tag.toUpperCase()).append("', ")
                        .append(" '").append(type).append("' ")
                        .append("); ");

//                query = "INSERT INTO srcFileTags VALUES( "
//                        + " '" + srcFile.toUpperCase() + "', "
//                        + " '" + tag.toUpperCase() + "', "
//                        + " '" + type + "' "
//                        + "); ";
                query = sb.toString();
                stmt.executeUpdate(query);
                ctr++;
                if (pbh != null) {
                    pbh.monitorBoxUpdate(ctr);
                }
            }

            if (out != null) {
                out.append("\n");
            }
        }

        // now load pepXML files
        if (out != null) {
            out.append("  Inserting peptide tags\n");
        }
        iter = Globals.pepTagHash.entrySet().iterator();
        StringBuilder sb = new StringBuilder(1024);
        final String type = "pep";
        while (iter.hasNext()) {
            Map.Entry<String, String> pairs = iter.next();
            String srcFile = pairs.getKey();
            //String tag = Globals.replaceAll(Globals.replaceAll(pairs.getValue(), '.', '_'), '-', '_');
            String tag = pairs.getValue().replace('.', '_').replace('-', '_');

            if (Character.isDigit(tag.charAt(0))) {
                String tmp = "x" + tag;
                tag = tmp;
            }

            sb.setLength(0);
            sb.append("INSERT INTO srcFileTags VALUES( ")
                    .append(" '").append(srcFile.toUpperCase()).append("', ")
                    .append(" '").append(tag.toUpperCase()).append("', ")
                    .append(" '").append(type).append("' ")
                    .append("); ");
//            query = "INSERT INTO srcFileTags VALUES( "
//                    + " '" + srcFile.toUpperCase() + "', "
//                    + " '" + tag.toUpperCase() + "', "
//                    + " '" + type + "' "
//                    + "); ";
            query = sb.toString();
            stmt.executeUpdate(query);
            ctr++;
            if (pbh != null) {
                pbh.monitorBoxUpdate(ctr);
            }
        }

        if (out != null) {
            out.append("  Creating index 1\n");
        }
        stmt.executeUpdate("CREATE INDEX sf_idx1 ON srcFileTags(srcFile)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(ctr++);
        }

        if (out != null) {
            out.append("  Creating index 2\n");
        }
        stmt.executeUpdate("CREATE INDEX sf_idx2 ON srcFileTags(tag)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(ctr++);
        }

        if (out != null) {
            out.append("  Creating index 3\n");
        }
        stmt.executeUpdate("CREATE INDEX sf_idx3 ON srcFileTags(fileType)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(ctr++);
            pbh.closeMonitorBox();
        }

        // save on memory
        Globals.protTagHash.clear();
        Globals.protTagHash = null;
        Globals.pepTagHash.clear();
        Globals.pepTagHash = null;

        stmt.close();
        if (out != null) {
            out.append("\n"); // for pretty output
        }
    }

    /**
     * **********
     *
     * Function creates the combined table
     *
     * @param conn
     * @throws SQLException
     *
     */
    public void makeCombinedTable(Connection conn, Appendable out, ProgressBarHandler pbh) throws SQLException, IOException {

        if (out != null) {
            out.append("Creating combined table from '" + Globals.combinedFile + "'\n");
        }

        try (Statement stmt = conn.createStatement()) {
            String query = null;
            ResultSet rs = null;
            PreparedStatement prep = null;

            stmt.executeUpdate("DROP TABLE IF EXISTS combined");

            query = "CREATE CACHED TABLE combined ("
                    + "  groupid INT, "
                    + "  siblingGroup VARCHAR(5), "
                    + "  Pw DECIMAL(8,6), "
                    + "  localPw DECIMAL(8,6), "
                    + "  protId VARCHAR(250), "
                    + "  protLen INT DEFAULT 0, "
                    + "  isFwd INT, "
                    + "  modPeptide VARCHAR(250), "
                    + "  charge INT, "
                    + "  iniProb DECIMAL(8,6), "
                    + "  wt DECIMAL(8,6), "
                    + "  defline VARCHAR(1000) "
                    + ")";
            stmt.executeUpdate(query);

            query = "INSERT INTO combined VALUES ("
                    + "?, " // groupid
                    + "?, " // siblingGroup
                    + "?, " // Pw
                    + "?, " // localPw
                    + "?, " // protId
                    + "?, " // protLen
                    + "?, " // isFwd
                    + "?, " // modPeptide
                    + "?, " // charge
                    + "?, " // iniProb
                    + "?, " // wt
                    + "? " //defline
                    + ");";
            prep = conn.prepareStatement(query);


//            try {
//                DbgUtils.dbGuiInMem();
//                Thread.sleep(3000000000L);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            // this code is used for the progress monitor
            query = "SELECT COUNT(*) FROM RAWprotXML "
                    + "WHERE srcFile = '" + this.combinedFile.toUpperCase() + "' "
                    + "AND Pw >= " + this.minPw + " "
                    + "AND iniProb >= " + this.iniProbTH + " ";
            rs = stmt.executeQuery(query);
            rs.next();
            int N = rs.getInt(1);

            if (N == 0) { // this means there was no data in the RAWprotXML table for the COMBINED file
                String err = "\nERROR:\n"
                        + "Nothing in your COMBINED file met your input parameters.\n"
                        + "Please adjust your Abacus parameters and try again.\n"
                        + "Now quiting....\n";
                if (pbh != null) {
                    JFrame frame = new JFrame();
                    JOptionPane.showMessageDialog(frame, err);
                }
                if (out != null) {
                    out.append(err);
                }
                System.exit(-1);
            }

            if (pbh != null) {
                pbh.monitorBoxInit(N, "COMBINED table");
            }

            query = "SELECT groupid, siblingGroup, Pw, localPw, protId, isFwd,"
                    + "  modPeptide, charge, iniProb, wt, defline "
                    + "FROM RAWprotXML "
                    + "WHERE srcFile = '" + this.combinedFile.toUpperCase() + "' "
                    + "AND Pw >= " + this.minCombinedFilePw + " "
                    + "AND iniProb >= " + this.iniProbTH + " "
                    + "GROUP BY groupid, siblingGroup, Pw, localPw, protId, isFwd, "
                    + " modPeptide, charge, iniProb, wt, defline "
                    + "ORDER BY groupid, siblingGroup ";
            rs = stmt.executeQuery(query);
            int iter = 1;
            while (rs.next()) {
                prep.setInt(1, rs.getInt(1)); // groupid
                prep.setString(2, rs.getString(2)); // siblingGroup
                prep.setDouble(3, rs.getDouble(3)); // Pw
                prep.setDouble(4, rs.getDouble(4)); // localPw
                prep.setString(5, rs.getString(5));  //protid

                String protid = rs.getString(5);
                int len = 0;
                if (Globals.fastaFile == null || Globals.fastaFile.isEmpty()) {
                    len = 0;
                } else {
                    if (Globals.protLen.containsKey(protid)) {
                        len = Globals.protLen.get(protid);
                    }
                }

                prep.setInt(6, len);

                prep.setInt(7, rs.getInt(6)); // isFwd
                prep.setString(8, rs.getString(7)); // modPeptide
                prep.setInt(9, rs.getInt(8)); // charge
                prep.setDouble(10, rs.getDouble(9)); // iniProb
                prep.setDouble(11, rs.getDouble(10)); // wt
                prep.setString(12, rs.getString(11)); // defline

                prep.addBatch();
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                }
                iter++;
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            if (pbh != null) {
                pbh.closeMonitorBox();
            }

            stmt.executeUpdate("CREATE INDEX com_idx1 ON combined(groupid, siblingGroup)");
            stmt.executeUpdate("CREATE INDEX com_idx2 ON combined(protid)");
            stmt.executeUpdate("CREATE INDEX com_idx3 ON combined(modPeptide, charge)");
            stmt.executeUpdate("CREATE INDEX com_idx4 ON combined(modPeptide)");

            query = "DELETE FROM RAWprotXML WHERE srcFile = '" + this.combinedFile + "'";
            stmt.executeUpdate(query);

            // clean up combined file cases where maxLocalPw
            this.curate_on_maxLocalPw(Globals.combinedFile, conn, out);
            this.recalculatePeptideWts(conn, "combined", out);

            stmt.close();
        }
        if (out != null) {
            out.append("\n\n");
        }
    }

    /**
     * ******************
     * From the combined table, only keep siblingGroups that meet the combined
     * file localPw threshold set by the user
     */
    private void curate_on_maxLocalPw(String xmlId, Connection conn, Appendable out) throws SQLException, IOException {

        if (out != null) {
            out.append("  Curating " + xmlId);
        }

        String query = null;
        Statement stmt = conn.createStatement();
        ResultSet rs = null;

        if (xmlId.equals(this.combinedFile)) {
            query = "SELECT groupid, MAX(localPw) as maxLocalPw "
                    + "FROM combined "
                    + "GROUP BY groupid ";

            rs = stmt.executeQuery(query);

            while (rs.next()) {
                int gid = rs.getInt(1);
                double maxLocalPw = rs.getDouble(2);

                if (maxLocalPw == 0) { // all sibling groups have probability = 0,
                    // just take the 'a' sibling group
                    query = "DELETE "
                            + "FROM combined "
                            + "WHERE groupid = " + gid + " "
                            + "AND siblingGroup != 'a'; ";
                    conn.createStatement().executeUpdate(query);
                } else { // the group has > 1 sibling group with a localPw above 0
                    query = "DELETE "
                            + "FROM combined "
                            + "WHERE groupid = " + gid + " "
                            + "AND localPw < " + Globals.minCombinedFilePw + " ";
                    conn.createStatement().executeUpdate(query);
                }
            }
        }
    }

//	/********************
//	 *
//	 * From the combined or protXML table only group entries that have localPw
//	 * equal to the max(localPw) for the group. If the max(localPw) for
//	 * the group is 0. Then just keep siblingGroup 'a'.
//	 *
//	 * @param combinedTag2
//	 * @param conn
//	 * @throws SQLException
//	 */
//	private void curate_on_maxLocalPw(String xmlId, Connection conn, abacus_textArea console) throws SQLException {
//
//		if(console != null) console.append("  Curating " + xmlId );
//		else System.err.print("  Curating " + xmlId);
//
//		String query = null;
//		HashMap<Integer, Double> hm = new HashMap<Integer, Double>();
//		Statement stmt = conn.createStatement();
//		ResultSet rs = null;
//		PreparedStatement prep1 = null;
//		PreparedStatement prep2 = null;
//		Iterator it = null;
//
//		if( xmlId.equals(this.combinedFile) ) {
//			query = "SELECT groupid, MAX(localPw) as maxLocalPw "
//				  + "FROM combined "
//				  + "GROUP BY groupid";
//		}
//		else {
//			query = "SELECT groupid, MAX(localPw) as maxLocalPw "
//				  + "FROM protXML "
//				  + "WHERE tag = '" + xmlId + "' "
//				  + "GROUP BY groupid ";
//		}
//
//		rs = stmt.executeQuery(query);
//
//		while(rs.next()) {
//			hm.put( rs.getInt(1), rs.getDouble(2) );
//		}
//
//		//you have two queries to consider, here are their prepared statements
//		if(xmlId.equals(this.combinedFile)) {
//			query = "DELETE FROM combined "
//				  + "WHERE groupid = ? "
//				  + "AND localPw < ? ";
//			prep1 = conn.prepareStatement(query);
//
//			// query for cases where maxLocalPw = 0
//			query = "DELETE "
//				  + "FROM combined "
//				  + "WHERE groupid = ? "
//				  + "AND siblingGroup != 'a'; ";
//			prep2 = conn.prepareStatement(query);
//		}
//		else {
//			// default query
//			query = "DELETE "
//				  + "FROM protXML "
//				  + "WHERE srcFile = '" + xmlId + "' "
//				  + "AND groupid = ? "
//				  + "AND localPw < ?; ";
//			prep1 = conn.prepareStatement(query);
//
//			// query for cases where maxLocalPw = 0
//			query = "DELETE "
//				  + "FROM protXML "
//				  + "WHERE srcFile = '" + xmlId + "' "
//				  + "AND groupid = ? "
//				  + "AND siblingGroup != 'a';";
//			prep2 = conn.prepareStatement(query);
//		}
//
//
//		// set to true if you have to 'commit' one or the other of the prepared
//		// statements above
//		boolean runPrep1 = false;
//		boolean runPrep2 = false;
//
//		//iterate over hashmap
//		Set<Integer> groupIds = hm.keySet();
//		Iterator<Integer> groupIter = groupIds.iterator();
//		while(groupIter.hasNext()) {
//			int groupId = (int) groupIter.next();
//			double maxLocalPw = hm.get( groupId );
//
//			if(maxLocalPw == 0) { // keep just siblingGroup 'a'
//				prep2.setInt(1, groupId);
//				prep2.addBatch();
//				runPrep2 = true;
//			}
//			else {
//				prep1.setInt(1, groupId);
//				prep1.setDouble(2, maxLocalPw);
//				prep1.addBatch();
//				runPrep1 = true;
//			}
//		}
//
//		if(runPrep1) {
//			conn.setAutoCommit(false);
//			prep1.executeBatch();
//			conn.setAutoCommit(true);
//		}
//
//		if(runPrep2) {
//			conn.setAutoCommit(false);
//			prep2.executeBatch();
//			conn.setAutoCommit(true);
//		}
//
//		rs.close();
//		stmt.close();
//		prep1.close();
//		prep2.close();
//	}
    /**
     * Function creates protXML table
     *
     * @param conn
     * @param out
     * @param pbh
     * @throws SQLException
     * @throws java.io.IOException
     *
     */
    public void makeProtXMLTable(Connection conn, Appendable out, ProgressBarHandler pbh) throws SQLException, IOException {
        if (out != null) {
            out.append("Creating protXML table\n");
        }

        Statement stmt = conn.createStatement();
        Statement stmt2 = conn.createStatement();
        String query = null;
        String msg = null;
        ResultSet rs = null;

        stmt.executeUpdate("DROP TABLE IF EXISTS protXML");

        query = "CREATE CACHED TABLE protXML ("
                + "  tag VARCHAR(250), "
                + "  srcFile VARCHAR(250), "
                + "  groupid INT, "
                + "  siblingGroup VARCHAR(5), "
                + "  Pw DECIMAL(8,6), "
                + "  localPw DECIMAL(8,6), "
                + "  protId VARCHAR(250), "
                + "  isFwd INT, "
                + "  modPeptide VARCHAR(250), "
                + "  charge INT, "
                + "  iniProb DECIMAL(8,6), "
                + "  wt DECIMAL(8,6), "
                + "  defline VARCHAR(1000) "
                + ")";
        stmt.executeUpdate(query);

        query = "INSERT INTO protXML VALUES ("
                + "?, " // tag
                + "?, " // srcFile
                + "?, " // groupid
                + "?, " // siblingGroup
                + "?, " // Pw
                + "?, " // localPw
                + "?, " // protId
                + "?, " // isFwd
                + "?, " // modPeptide
                + "?, " // charge
                + "?, " // iniProb
                + "?, " // wt
                + "? " //defline
                + ");";
        PreparedStatement prep = conn.prepareStatement(query);

        // this code is used for the progress monitor
        query = "SELECT COUNT(*) FROM RAWprotXML "
                + "WHERE Pw >= " + this.minPw + " "
                + "AND iniProb >= " + this.iniProbTH + " "
                + "AND srcFile != '" + this.combinedFile + "' ";
        rs = stmt.executeQuery(query);
        rs.next();
        int N = rs.getInt(1);

        int statusUpdateFreq = 100;
        int NbyFreq = N / statusUpdateFreq;

        if (pbh != null) {
            pbh.monitorBoxInit(NbyFreq, "protXML table");
        }

        query = "SELECT srcFile, srcFile, "
                + "  groupid, siblingGroup, Pw, localPw, protId, isFwd, "
                + "  modPeptide, charge, iniProb, wt, defline "
                + "FROM RAWprotXML "
                + "WHERE Pw >= " + this.minPw + " "
                + "AND iniProb >=" + this.iniProbTH + " "
                + "AND srcFile != '" + this.combinedFile.toUpperCase() + "' "
                + "GROUP BY srcFile, groupid, siblingGroup, Pw, localPw, protId, "
                + "  isFwd, modPeptide, charge, iniProb, wt, defline "
                + "ORDER BY srcFile, groupid, siblingGroup, protId, modPeptide ";

        rs = stmt.executeQuery(query);
        msg = "  Populating protXML table...";
        int iter = 0;
        while (rs.next()) {
            prep.setString(1, rs.getString(1)); // tag
            prep.setString(2, rs.getString(2)); // srcFile
            prep.setInt(3, rs.getInt(3)); // groupid
            prep.setString(4, rs.getString(4)); // siblingGroup
            prep.setDouble(5, rs.getDouble(5)); // Pw
            prep.setDouble(6, rs.getDouble(6)); // localPw
            prep.setString(7, rs.getString(7));  //protid
            prep.setInt(8, rs.getInt(8)); // isFwd
            prep.setString(9, rs.getString(9)); // modPeptide
            prep.setInt(10, rs.getInt(10)); // charge
            prep.setDouble(11, rs.getDouble(11)); // iniProb
            prep.setDouble(12, rs.getDouble(12)); // wt
            prep.setString(13, rs.getString(13)); // defline
            prep.addBatch();

            if (iter % statusUpdateFreq == 0) {
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                } else {
                    Globals.cursorStatus(iter, 100, msg);
                }
            }
            iter++;
        }
        if (pbh != null) {
            pbh.monitorBoxUpdate(NbyFreq);
        } else {
            Globals.cursorStatusDone(iter, msg);
        }
        conn.setAutoCommit(false);
        try {
            prep.executeBatch();
        } catch (SQLException e) {
            if (e.getMessage().contains("statement is not in batch mode")) {
                int a = 1;
            }
        }
        conn.setAutoCommit(true);
        prep.clearBatch();
        prep.close();

        if (pbh != null) {
            pbh.closeMonitorBox();
        }
        if (out != null) {
            out.append("\n");
            out.append("  Indexing protXML table (This can take a while...)\n");
        }

        N = 9; //number of indexes to create
        if (pbh != null) {
            pbh.monitorBoxInit(N, "Indexing protXML...");
        }
        iter = 0;

        iter++;
        if (out != null) {
            out.append(String.format("  Indexing and updating tables (%d operations)\n", N));
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("CREATE INDEX protXML_idx1 ON protXML(tag)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("CREATE INDEX protXML_idx2 ON protXML(groupid, siblingGroup)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("CREATE INDEX protXML_idx3 ON protXML(modPeptide, charge)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("CREATE INDEX protXML_idx4 ON protXML(protid)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("CREATE INDEX protXML_idx5 ON protXML(srcFile)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        query = "SELECT srcFile, tag "
                + "FROM srcFileTags "
                + "WHERE fileType = 'prot' "
                + "GROUP BY srcFile, tag ";
        rs = stmt.executeQuery(query);

        while (rs.next()) {
            query = "UPDATE protXML "
                    + " SET tag = '" + rs.getString(2) + "' "
                    + "WHERE srcFile = '" + rs.getString(1) + "' ";
            stmt2.executeUpdate(query);
        }

        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("DROP INDEX IF EXISTS protXML_idx9");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("ALTER TABLE protXML DROP COLUMN srcFile");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        iter++;
        if (out != null) {
            out.append(String.format("  Operation %d\n", iter));
        }
        stmt.executeUpdate("CREATE INDEX protXML_idx6 ON protXML(tag)");
        if (pbh != null) {
            pbh.monitorBoxUpdate(iter);
        }

        stmt.executeUpdate("DROP TABLE IF EXISTS RAWprotXML");

        if (pbh != null) {
            pbh.closeMonitorBox();
        }
        if (out != null) {
            out.append("  Indexing of protXML completed\n");
        }

        // if the epiThreshold != iniProbTH then we have to remove
        // all entries where the protein does not have at least 1 peptide with a
        // probability >= epiThreshold
        if (Globals.epiThreshold > Globals.iniProbTH) {

            msg = "  Applying Experimental Peptide Inclusion threshold (EPI >= "
                    + Globals.epiThreshold + ")\n";
            if (out != null) {
                out.append(msg);
            }

            query = "CREATE MEMORY TABLE x_ ("
                    + " tag, groupid, siblingGroup, maxIniProb "
                    + ") AS ( "
                    + "SELECT tag, groupid, siblingGroup, MAX(iniProb) "
                    + "FROM protXML "
                    + "GROUP BY tag, groupid, siblingGroup "
                    + ") WITH DATA ";

            stmt.executeUpdate(query);
            stmt.executeUpdate("CREATE INDEX x_1 ON x_(tag)");
            stmt.executeUpdate("CREATE INDEX x_2 ON x_(maxIniProb)");
            stmt.executeUpdate("CREATE INDEX x_3 ON x_(tag, groupid, siblingGroup)");
            stmt.executeUpdate("CREATE INDEX x_4 ON x_(groupid, siblingGroup)");

            query = "SELECT * FROM x_ WHERE maxIniProb < " + Globals.epiThreshold;
            rs = stmt.executeQuery(query);

            while (rs.next()) {
                String tag = rs.getString(1);
                int gid = rs.getInt(2);
                String sib = rs.getString(3);

                query = "DELETE FROM protXML "
                        + "WHERE tag = '" + tag + "' "
                        + "AND groupid = " + gid + " "
                        + "AND siblingGroup = '" + sib + "' ";
                stmt2.executeUpdate(query);
            }
            stmt.executeUpdate("DROP INDEX IF EXISTS x_4");
            stmt.executeUpdate("DROP INDEX IF EXISTS x_3");
            stmt.executeUpdate("DROP INDEX IF EXISTS x_2");
            stmt.executeUpdate("DROP INDEX IF EXISTS x_1");
            stmt.executeUpdate("DROP TABLE IF EXISTS x_");
        }

        if (Globals.recalcPepWts) {
            /**
             * ***********************************
             ** Recalculate peptide weights for peptides that are only shared
             * among isoforms * within the same protein group within each
             * protXML file
             */
            query = "SELECT DISTINCT tag "
                    + "FROM srcFileTags "
                    + "WHERE fileType = 'prot' ";
            rs = stmt.executeQuery(query);

            while (rs.next()) {
                String tag = rs.getString(1);
                recalculatePeptideWts(conn, tag, out);
                recalculateLocalPw(conn, tag);
            }
            /**
             * ******** End wt recalculation ***********
             */
        }

        stmt.close();
        stmt2.close();
        rs.close();
        rs = null;
        stmt2 = null;

        if (out != null) {
            out.append("\n");
        }
    }

    /**
     * Function recomputes the localPw of a sibling group in a given protXML.
     * This needs to be done for highly degenerate protein groups where all of
     * the sibling groups are interrelated isoforms all sharing the same common
     * set of peptides.
     *
     * @param conn
     * @param tag
     * @throws java.sql.SQLException
     */
    public void recalculateLocalPw(Connection conn, String tag) throws SQLException {

        String query;
        try (Statement stmt = conn.createStatement()) {

            query = "CREATE MEMORY TABLE x_ ("
                    + "  groupid, Pw, maxLocalPw "
                    + ") AS ( "
                    + "SELECT groupid, Pw, MAX(localPw) "
                    + "FROM protXML "
                    + "WHERE tag = '" + tag + "' "
                    + "GROUP BY groupid, Pw "
                    + ") WITH DATA ";

            stmt.executeUpdate("DROP TABLE IF EXISTS x_");
            stmt.executeUpdate(query);

            // create index
            stmt.executeUpdate("CREATE INDEX x_idx1 ON x_ (groupid)");

            query = "UPDATE protXML "
                    + "  SET localPw = Pw "
                    + "WHERE tag = '" + tag + "' "
                    + "AND groupid = ? ";
            PreparedStatement prep = conn.prepareStatement(query);

            query = "SELECT COUNT(DISTINCT groupid) "
                    + "FROM x_ "
                    + "WHERE Pw > 0 "
                    + "AND maxLocalPw = 0 ";
            ResultSet rs1 = stmt.executeQuery(query);

            rs1.next();
            int N = rs1.getInt(1);

            if (N > 0) {
                // fix protein groups that have a Pw > 0 and a localPw = 0
                query = "SELECT DISTINCT groupid "
                        + "FROM x_ "
                        + "WHERE Pw > 0 "
                        + "AND maxLocalPw = 0 ";
                rs1 = stmt.executeQuery(query);

                while (rs1.next()) {
                    int gid = rs1.getInt(1);
                    prep.setInt(1, gid);
                    prep.addBatch();
                }

                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.setAutoCommit(true);
                prep.clearBatch();
                prep.close();
            }

            stmt.executeUpdate("DROP INDEX x_idx1");
            stmt.executeUpdate("DROP TABLE x_");
        }
    }

    /**
     * Function adjusts peptide weights in protXML or combined file. This is
     * done because peptides which are shared among isoforms have different
     * weights. When in fact they should have the same weight in all cases.
     *
     * @param conn
     * @param tag
     * @param out
     * @throws java.io.IOException
     *
     */
    public void recalculatePeptideWts(Connection conn, String tag, Appendable out) throws IOException {
        if (out != null) {
            out.append("\n  Recalculating peptide weights for " + tag);
        }

        String query;

        try {
            try (Statement stmt = conn.createStatement()) {

                if (tag.equals("combined")) {
                    query = "CREATE CACHED TABLE wt_ ("
                            + "  groupid, modPeptide "
                            + ") AS ( "
                            + "  SELECT groupid, modPeptide "
                            + "  FROM combined "
                            + "  GROUP BY groupid, modPeptide "
                            + "  ORDER BY groupid "
                            + ") WITH DATA ";
                } else {
                    query = "CREATE CACHED TABLE wt_ ("
                            + "  groupid, modPeptide "
                            + ") AS ( "
                            + "  SELECT groupid, modPeptide "
                            + "  FROM protXML "
                            + "  WHERE tag = '" + tag + "' "
                            + "  GROUP BY groupid, modPeptide "
                            + "  ORDER BY groupid "
                            + ") WITH DATA ";
                }

                stmt.executeUpdate("DROP TABLE IF EXISTS wt_");
                stmt.executeUpdate(query);

                // create index
                stmt.executeUpdate("CREATE INDEX wt_idx1 ON wt_ (groupid)");
                stmt.executeUpdate("CREATE INDEX wt_idx2 ON wt_ (modPeptide)");

                // generate the prepared statements
                if (tag.equals("combined")) {
                    query = "UPDATE combined SET wt = ? WHERE modPeptide = ?;";
                } else {
                    query = "UPDATE protXML "
                            + "  SET wt = ? "
                            + "WHERE tag = '" + tag + "' "
                            + "AND modPeptide = ?;";
                }

                try (PreparedStatement prep = conn.prepareStatement(query);
                        ResultSet rs1 = stmt.executeQuery("SELECT DISTINCT modPeptide FROM wt_")) {
                    String modPep, tmp_str;
                    double newWT = 0;
                    double count = 0;
                    double tmp = 0;
                    try (Statement stmt2 = conn.createStatement()) {
                        while (rs1.next()) {
                            modPep = rs1.getString(1);
                            query = "SELECT COUNT(DISTINCT groupid) "
                                    + "FROM wt_ "
                                    + "WHERE modPeptide = '" + modPep + "' ";
                            try (ResultSet rs2 = stmt2.executeQuery(query)) {
                                rs2.next();

                                count = (double) rs2.getInt(1);
                                tmp = 1 / count;

                                // code to round newWT value to 3 decimal places
                                tmp_str = String.format("%.3g%n", tmp);
                                newWT = Double.parseDouble(tmp_str);

                                prep.setDouble(1, newWT);
                                prep.setString(2, modPep);
                                prep.addBatch();
                            }
                        }
                    }
                    conn.setAutoCommit(false);
                    prep.executeBatch();
                    conn.setAutoCommit(true);
                    stmt.executeUpdate("DROP TABLE IF EXISTS wt_;");
                    stmt.executeUpdate("DROP INDEX IF EXISTS wt_idx1;");
                    stmt.executeUpdate("DROP INDEX IF EXISTS wt_idx2;");
                }
            }

        } catch (SQLException e) {
            if (out != null) {
                out.append("\n" + e.toString());
            }
        }
    }

    /**
     * Function creates a temporary table that summarizes the peptide data used
     * for all the proteins in the combined and the protXML tables.
     *
     * @param conn
     * @param out
     * @param pbh
     * @throws java.sql.SQLException
     */
    public void makeTempProt2PepTable(Connection conn, Appendable out, ProgressBarHandler pbh) throws SQLException, IOException {
        try (Statement stmt = conn.createStatement()) {
            PreparedStatement prep;
            String query;
            String msg;
            int N = 0;
            int iter = 0;

            stmt.executeUpdate("DROP TABLE IF EXISTS prot2peps_combined");

            // combined file table
            query = "CREATE TABLE prot2peps_combined ("
                    + " protid VARCHAR(100), "
                    + " modpeptide VARCHAR(250), "
                    + " charge INT, "
                    + " wt DECIMAL(8,6), "
                    + " iniProb DECIMAL(8,6), "
                    + " nspecs INT "
                    + ")";
            stmt.executeUpdate(query);

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM combined");
            rs.next();
            N = rs.getInt(1);

            query = "INSERT INTO prot2peps_combined VALUES ("
                    + "?, " // protid
                    + "?, " // modPeptide
                    + "?, " // charge
                    + "?, " // wt
                    + "?, " // iniProb
                    + "? " // count
                    + ")";
            prep = conn.prepareStatement(query);

            msg = "  Mapping peptides to proteins (combined) ";
            if (out != null) {
                out.append(msg);
            }
            if (pbh != null) {
                pbh.monitorBoxInit((N + 2), "Combined file peptides...");
            }

            // combined file peptides
            query = "SELECT c.protid, c.modPeptide, c.charge, c.wt, c.iniProb, "
                    + "    COUNT(DISTINCT px.specId) "
                    + "FROM combined c, pepXML px "
                    + "WHERE c.modPeptide = px.modPeptide "
                    + "AND c.charge = px.charge "
                    + "GROUP BY c.protid, c.modPeptide, c.charge, c.wt, c.iniProb "
                    + "ORDER BY c.protid, c.modPeptide, c.charge ";
            rs = stmt.executeQuery(query);

            iter = 0;

            while (rs.next()) {
                prep.setString(1, rs.getString(1));
                prep.setString(2, rs.getString(2));
                prep.setInt(3, rs.getInt(3));
                prep.setDouble(4, rs.getDouble(4));
                prep.setDouble(5, rs.getDouble(5));
                prep.setInt(6, rs.getInt(6));
                prep.addBatch();

                iter++;
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                } else {
                    Globals.cursorStatus(iter, 100, msg);
                }
            }
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter);
            } else {
                Globals.cursorStatusDone(iter, msg);
            }

            // there were no rows in the resultset
            if (iter == 0) {
                if (DbgUtils.DEBUG) {
                    // TODO: org.hsqldb.HsqlException: statement is not in batch mode
                    // TODO: can be caused by if there were no batches added to prepared statement at all or after last execution
                    out.append("There were no results for combined file peptides\n");
//                    DbgUtils.dbGuiInMem();
//                    DbgUtils.sleep();
                } else {
                    String errorMsg = "Could not map peptide ions from protXML files to the ones from pepXML files.";
                    if (out != null) {
                        out.append(errorMsg);
                        System.exit(-1);
                    }
                    if (pbh != null) {
                        JFrame frame = new JFrame();
                        JOptionPane.showMessageDialog(frame, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }

            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();

            stmt.executeUpdate("CREATE INDEX pt2pep_combined_idx1 ON prot2peps_combined(protid)");
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter++);
            }
            stmt.executeUpdate("CREATE INDEX pt2pep_combined_idx2 ON prot2peps_combined(modPeptide, charge)");
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter++);
            }

            if (pbh != null) {
                pbh.closeMonitorBox();
            }
            if (out != null) {
                out.append("\n");
            }

            // Now process the individual experimental files.
            // To make things faster, we will make one table per record in srcFileTags
            rs = stmt.executeQuery("SELECT COUNT(DISTINCT tag) FROM srcFileTags WHERE fileType = 'prot'");
            rs.next();
            int numTags = rs.getInt(1);

            rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot'");

            String tag;
            while (rs.next()) {
                // create the table for this particular experimental file
                tag = rs.getString(1);

                if (out != null) {
                    out.append("  Mapping peptides to proteins (" + tag + ")\n");
                }

                stmt.executeUpdate("DROP TABLE IF EXISTS prot2peps_" + tag);

                query = "CREATE TABLE prot2peps_" + tag + " ( "
                        + " protid, modPeptide, charge, wt, iniProb, nspecs "
                        + ") AS ( "
                        + "SELECT pr.protid, pr.modPeptide, pr.charge, pr.wt, pr.iniProb, "
                        + "  COUNT(DISTINCT px.specId) "
                        + "FROM protXML pr, pepXML px "
                        + "WHERE pr.tag = '" + tag + "' "
                        + "AND pr.tag = px.tag "
                        + "AND px.modPeptide = pr.modPeptide "
                        + "AND px.charge = pr.charge "
                        + "GROUP BY pr.protid, pr.modPeptide, pr.charge, pr.wt, pr.iniProb "
                        + "ORDER BY pr.protid, pr.modPeptide, pr.charge "
                        + ") WITH DATA ";
                stmt.executeUpdate(query);

                stmt.executeUpdate("CREATE INDEX pt2pep_" + tag + "_idx1 ON prot2peps_" + tag + "(protid)");
                stmt.executeUpdate("CREATE INDEX pt2pep_" + tag + "_idx2 ON prot2peps_" + tag + "(modPeptide, charge)");
            }
            rs.close();

            if (out != null) {
                out.append("\n");
            }
        }
    }

    /**
     * Function creates the protidSummary table.
     *
     * @param conn
     * @param out
     * @param pbh
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void makeProtidSummary(Connection conn, Appendable out, ProgressBarHandler pbh) throws SQLException, IOException {
        if (out != null) {
            out.append("\nCreating protidSummary table\n");
        }

        Statement stmt2;
        Statement stmt3;
        PreparedStatement prep, prep2, p1;
        try (Statement stmt = conn.createStatement()) {
            stmt2 = conn.createStatement();
            stmt3 = conn.createStatement();
            String query;
            ResultSet rs, rs2;
            String msg;
            int N = 0;
            int iter = 0;
            int gid, numXML;
            String sib, protid;
            double maxPw;
            int numPepsTot, numPepsUniq, numSpecsTot, numSpecsUniq;
            double maxIniProb, wt_maxIniProb, maxIniProbUniq;
            msg = "  Collecting list of all proteins identified in " + this.combinedFile + "\n";
            if (out != null) {
                out.append(msg);
            }
            // This function can take a really long time to run if there if a lot of data
            // has been submitted by the user. For this reason we will use the progress monitor throughout
            // this function call.
            query = "CREATE MEMORY TABLE t1_ ("
                    + "  groupid INT, "
                    + "  siblingGroup VARCHAR(10), "
                    + "  protid VARCHAR(100), "
                    + "  numXML INT DEFAULT 0, "
                    + "  maxPw DECIMAL(8,6) "
                    + ")";
            stmt.executeUpdate(query);
            query = "INSERT INTO t1_ (groupid, siblingGroup, protid) VALUES ("
                    + "?, " // groupid
                    + "?, " // siblingGroup
                    + "? " // protid
                    + ")";
            prep = conn.prepareStatement(query);
            query = "SELECT groupid, siblingGroup, protid "
                    + "FROM combined "
                    + "WHERE wt > " + this.wtTH + " "
                    + "AND Pw > " + this.minCombinedFilePw + " "
                    + "GROUP BY groupid, siblingGroup, protid "
                    + "ORDER BY groupid, siblingGroup ";

            if (DbgUtils.DEBUG && out != null) {
                String tableName = "t1_";
                out.append(String.format("\n\n    Populating temp table %s using query:", tableName));
                out.append(String.format("\n    %s", query));
                out.append(String.format("\n\n"));
            }

            rs = stmt.executeQuery(query);
            while (rs.next()) {
                prep.setInt(1, rs.getInt(1));
                prep.setString(2, rs.getString(2));
                prep.setString(3, rs.getString(3));
                prep.addBatch();
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            stmt.executeUpdate("CREATE INDEX t1_idx1 ON t1_(protid)");
            stmt.executeUpdate("CREATE INDEX t1_idx2 ON t1_(groupid, siblingGroup)");
            // code to get initial starting values for progress monitor
            query = "SELECT COUNT(*) FROM ( "
                    + "  SELECT groupid, siblingGroup, protid "
                    + "  FROM combined "
                    + "  WHERE wt > " + this.wtTH + " "
                    + "  AND Pw > " + this.minCombinedFilePw + " "
                    + "  GROUP BY groupid, siblingGroup, protid "
                    + ") ";
            rs = stmt.executeQuery(query);
            rs.next();
            N = rs.getInt(1);
            iter = 0;
            if (pbh != null) {
                pbh.monitorBoxInit(N, "Getting protein frequencies...");
            }
            msg = "  Counting protein frequencies across independent files\n";
            if (out != null) {
                out.append(msg);
            }
            query = "SELECT protid, COUNT(DISTINCT tag) AS f "
                    + "FROM protXML "
                    + "GROUP BY protid ";
            rs = stmt.executeQuery(query);

            if (DbgUtils.DEBUG && out != null) {
                String tableName = "t1_";
                out.append(String.format("\n\n    Getting ProtIDs from protXML table:"));
                out.append(String.format("\n    %s", query));
                out.append(String.format("\n\n"));
            }

            iter = 0;
            while (rs.next()) {
                query = "UPDATE t1_ "
                        + "  SET numXML = " + rs.getInt(2) + " "
                        + "WHERE protid = '" + rs.getString(1) + "' ";
                stmt.executeUpdate(query);
                iter++;
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                } else {
                    Globals.cursorStatus(iter, 100, "  Getting protein frequencies ");
                }
            }
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter);
            } else {
                Globals.cursorStatusDone(iter, "  Getting protein frequencies ");
            }
            rs.close();
            if (pbh != null) {
                pbh.closeMonitorBox();
            }
            if (out != null) {
                out.append("\n");
            }
            // this removes proteins only identified in the COMBINED file
            query = "DELETE FROM t1_ WHERE numXML = 0";
            stmt.executeUpdate(query);
            msg = "  Recording best ProteinProphet scores for each protein\n";
            if (out != null) {
                out.append(msg);
            }
            query = "SELECT protid, MAX(Pw) as mPw "
                    + "FROM protXML "
                    + "GROUP BY protid ";
            rs = stmt.executeQuery(query);
            if (pbh != null) {
                pbh.monitorBoxInit(N, "Collecting protein probabilities...");
            }
            iter = 0;
            while (rs.next()) {
                query = "UPDATE t1_ "
                        + "  SET maxPw = " + rs.getDouble(2) + " "
                        + "WHERE protid = '" + rs.getString(1) + "' ";
                stmt.executeUpdate(query);
                iter++;
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                } else {
                    Globals.cursorStatus(iter, 100, "  Collecting protein probabilities ");
                }
            }
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter);
            } else {
                Globals.cursorStatusDone(iter, "  Collecting protein probabilities ");
            }
            rs.close();
            if (pbh != null) {
                pbh.closeMonitorBox();
            } else {
                System.err.print("\n");
            }
            query = "CREATE MEMORY TABLE t2_ ("
                    + "  groupid INT, "
                    + "  siblingGroup VARCHAR(5), "
                    + "  numXML INT, "
                    + "  maxPw DECIMAL(8,6) "
                    + ")";
            stmt.executeUpdate(query);
            query = "INSERT INTO t2_ VALUES ("
                    + "?, " // groupid
                    + "?, " // siblingGroup
                    + "?, " // numXML
                    + "? " // maxPw
                    + ") ";
            prep = conn.prepareStatement(query);
            query = "SELECT groupid, siblingGroup, MAX(numXML), MAX(maxPw) "
                    + "FROM t1_ "
                    + "GROUP BY groupid, siblingGroup "
                    + "ORDER BY groupid, siblingGroup ";

            if (DbgUtils.DEBUG && out != null) {
                String tableName = "t1_";
                out.append(String.format("\n\n    Getting ProtIDs, SiblinbgGroup from %s table:", tableName));
                out.append(String.format("\n    %s", query));
                out.append(String.format("\n\n"));
            }

            rs = stmt.executeQuery(query);
            if (pbh != null) {
                pbh.monitorBoxInit(N, "Selecting candidate proteins...");
            }
            iter = 0;
            while (rs.next()) {
                prep.setInt(1, rs.getInt(1));
                prep.setString(2, rs.getString(2));
                prep.setInt(3, rs.getInt(3));
                prep.setDouble(4, rs.getDouble(4));
                prep.addBatch();
                iter++;

                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                }
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            if (pbh != null) {
                pbh.closeMonitorBox();
            }
            stmt.executeUpdate("CREATE INDEX t2_idx1 ON t2_(groupid, siblingGroup)");
            query = "CREATE MEMORY TABLE t3_ ("
                    + "  groupid INT, "
                    + "  siblingGroup VARCHAR(10), "
                    + "  protid VARCHAR(100), "
                    + "  numXML INT, "
                    + "  maxPw DECIMAL(8,6), "
                    + "  numPepsTot INT DEFAULT 0, "
                    + "  numPepsUniq INT DEFAULT 0, "
                    + "  numSpecsTot INT DEFAULT 0, "
                    + "  numSpecsUniq INT DEFAULT 0, "
                    + "  maxIniProb DECIMAL(8,6), "
                    + "  wt_maxIniProb DECIMAL(8,6), "
                    + "  maxIniProbUniq DECIMAL(8,6) "
                    + ")";
            stmt.executeUpdate(query);
            msg = "  Creating selection heuristics table (This could take a while)...";
            if (out != null) {
                out.append(msg + "\n");
            }
            // We break up the process of making t3_ into several loops to speed things up
            query = "INSERT INTO t3_ (groupid, siblingGroup, protid) VALUES ("
                    + "?, " // groupid
                    + "?, " // siblingGroup
                    + "? " // protid
                    + ") ";
            prep = conn.prepareStatement(query);
            query = "SELECT groupid, siblingGroup, protid "
                    + "FROM t1_ "
                    + "GROUP BY groupid, siblingGroup, protid "
                    + "ORDER BY groupid, siblingGroup, protid ";
            rs = stmt.executeQuery(query);
            iter = 0;
            if (pbh != null) {
                pbh.monitorBoxInit((N + 2), "Building Heuristics...");
            }
            while (rs.next()) {
                prep.setInt(1, rs.getInt(1));
                prep.setString(2, rs.getString(2));
                prep.setString(3, rs.getString(3));
                prep.addBatch();
                iter++;
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                }
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            stmt.executeUpdate("CREATE INDEX t3_idx1 ON t3_(groupid, siblingGroup)");
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter++);
            }
            stmt.executeUpdate("CREATE INDEX t3_idx2 ON t3_(protid)");
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter++);
            }
            if (pbh != null) {
                pbh.closeMonitorBox();
            }   // update counter 'N' value
            rs = stmt.executeQuery("SELECT COUNT(*) FROM t2_");
            rs.next();
            N = rs.getInt(1);
            // prep statement 1
            query = "UPDATE t3_ "
                    + " SET numXML = ? "
                    + "WHERE groupid = ? "
                    + "AND siblingGroup = ?";
            prep = conn.prepareStatement(query);
            // prep statement 2
            query = "UPDATE t3_ "
                    + " SET maxPw = ? "
                    + "WHERE groupid = ? "
                    + "AND siblingGroup = ? ";
            prep2 = conn.prepareStatement(query);
            rs = stmt.executeQuery("SELECT * FROM t2_ ORDER BY groupid, siblingGroup ");
            iter = 0;
            if (pbh != null) {
                pbh.monitorBoxInit(N, "Building Heuristics (1/2)...");
            }
            while (rs.next()) {
                gid = rs.getInt(1);
                sib = rs.getString(2);
                numXML = rs.getInt(3);
                maxPw = rs.getDouble(4);

                prep.setInt(1, numXML);
                prep.setInt(2, gid);
                prep.setString(3, sib);
                prep.addBatch();

                prep2.setDouble(1, maxPw);
                prep2.setInt(2, gid);
                prep2.setString(3, sib);
                prep2.addBatch();

                iter++;
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                }
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            conn.setAutoCommit(false);
            prep2.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            prep2.clearBatch();
            prep2.close();
            if (pbh != null) {
                pbh.closeMonitorBox();
            }
            // update counter 'N' value
            rs = stmt.executeQuery("SELECT COUNT(*) FROM t1_");
            rs.next();
            N = rs.getInt(1);
            query = "UPDATE t3_ "
                    + "  SET numPepsTot = ?, "
                    + "      numPepsUniq = ?, "
                    + "      numSpecsTot = ?, "
                    + "      numSpecsUniq = ?, "
                    + "      maxIniProb = ?, "
                    + "      wt_maxIniProb = ?, "
                    + "      maxIniProbUniq = ? "
                    + "WHERE protid = ? ";
            prep = conn.prepareStatement(query);
            rs = stmt.executeQuery("SELECT protid, numXML, maxPw FROM t3_ WHERE numXML > 0 GROUP BY protid, numXML, maxPw ORDER BY protid");
            if (pbh != null) {
                pbh.monitorBoxInit(N, "Building Heuristics (2/2)...");
            }
            iter = 0; // used only for STDERR output
            while (rs.next()) {
                protid = rs.getString(1);
                //numXML = rs.getInt(2);
                //maxPw = rs.getDouble(3);

                // number of peptides total
                numPepsTot = this.retNumPeps(conn, this.combinedFile, protid, 0, this.iniProbTH);

                // number of peptides unique
                numPepsUniq = this.retNumPeps(conn, this.combinedFile, protid, this.wtTH, this.iniProbTH);

                // number of spectra total
                numSpecsTot = this.retNumSpectra(conn, this.combinedFile, protid, 0, this.iniProbTH);

                // number of spectra unique
                numSpecsUniq = this.retNumSpectra(conn, this.combinedFile, protid, this.wtTH, this.iniProbTH);

                // get maxIniProb for this group
                maxIniProb = this.retMaxIniProb(conn, this.combinedFile, protid, 0);

                // get wt of maxIniProb for this group
                wt_maxIniProb = this.retWTmaxIniProb(conn, this.combinedFile, protid, maxIniProb);

                // get maxIniProb for this group
                maxIniProbUniq = this.retMaxIniProb(conn, this.combinedFile, protid, this.wtTH);

                prep.setInt(1, numPepsTot);
                prep.setInt(2, numPepsUniq);
                prep.setInt(3, numSpecsTot);
                prep.setInt(4, numSpecsUniq);
                prep.setDouble(5, maxIniProb);
                prep.setDouble(6, wt_maxIniProb);
                prep.setDouble(7, maxIniProbUniq);
                prep.setString(8, protid);
                prep.addBatch();

                iter++;
                if (pbh == null) {
                    Globals.cursorStatus(iter, 100, msg);
                } else {
                    pbh.monitorBoxUpdate(iter);
                }
            }
            if (pbh == null) {
                Globals.cursorStatusDone(iter, msg);
                if (out != null) {
                    out.append("\n");
                }
            } else {
                pbh.monitorBoxUpdate(iter);
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            rs.close();
            rs = null;
            if (pbh != null) {
                pbh.closeMonitorBox();
            }   // clean up

            if (!DbgUtils.DEBUG) {
                stmt.executeUpdate("DROP INDEX t1_idx1");
                stmt.executeUpdate("DROP INDEX t1_idx2");
                stmt.executeUpdate("DROP TABLE t1_");
                stmt.executeUpdate("DROP INDEX t2_idx1");
                stmt.executeUpdate("DROP TABLE t2_");
            }

            stmt.executeUpdate("DROP TABLE IF EXISTS protidSummary");

            query = "CREATE CACHED TABLE protidSummary ( "
                    + "  groupid INT, "
                    + "  siblingGroup VARCHAR(10), "
                    + "  repID VARCHAR(100), "
                    + "  numXML INT, "
                    + "  maxPw DECIMAL(8,6), "
                    + "  numPepsTot INT DEFAULT 0, "
                    + "  numPepsUniq INT DEFAULT 0, "
                    + "  numSpecsTot INT DEFAULT 0, "
                    + "  numSpecsUniq INT DEFAULT 0, "
                    + "  maxIniProb DECIMAL(8,6), "
                    + "  wt_maxIniProb DECIMAL(8,6), "
                    + "  maxIniProbUniq DECIMAL(8,6) "
                    + ")";


            stmt.executeUpdate(query);
            if (out != null) {
                out.append("  Picking representative protids\n");
            }
            query = "SELECT COUNT(*) "
                    + "FROM ( SELECT DISTINCT groupid, siblingGroup FROM t3_ ) ";
            rs = stmt.executeQuery(query);
            rs.next();
            N = rs.getInt(1);
            N += 2; // there are to indexes to be built further down for t3_
            rs.close();
            query = "SELECT groupid, siblingGroup "
                    + "FROM t3_ "
                    + "GROUP BY groupid, siblingGroup "
                    + "ORDER BY groupid, siblingGroup ";
            if (DbgUtils.DEBUG && out != null) {
                String tableName = "t3_";
                out.append(String.format("\n\n    Getting groupid, siblingGroup from %s to put into final protidSummary table:", tableName));
                out.append(String.format("\n    %s", query));
                out.append(String.format("\n\n"));

//                DbgUtils.dbGuiInMem();
//                DbgUtils.sleep();
            }
            rs = stmt.executeQuery(query);
            msg = "  Loading protidSummary table...";
            if (pbh != null) {
                pbh.monitorBoxInit(N, msg);
            }
            iter = 0;
            while (rs.next()) {
                gid = rs.getInt(1);
                sib = rs.getString(2);

                query = "SELECT protid, numXML, maxPw, numPepsTot, numPepsUniq, "
                        + "  numSpecsTot, numSpecsUniq, maxIniProb, wt_maxIniProb, "
                        + "  maxIniProbUniq "
                        + "FROM t3_ "
                        + "WHERE groupid = " + gid + " "
                        + "AND siblingGroup = '" + sib + "' "
                        + "AND maxIniProb >= " + this.maxIniProbTH + " "
                        + "GROUP BY protid, numXML, maxPw, numPepsTot, numPepsUniq, "
                        + "  numSpecsTot, numSpecsUniq, maxIniProb, wt_maxIniProb, "
                        + "  maxIniProbUniq "
                        + "ORDER BY numXML DESC, maxPw DESC, maxIniProb DESC, "
                        + "  maxIniProbUniq DESC, numPepsUniq DESC, numSpecsUniq DESC, protid ASC "
                        + "LIMIT 1 ";

                rs2 = stmt2.executeQuery(query);

                while (rs2.next()) {
                    query = "INSERT INTO protidSummary VALUES ( "
                            + gid + ", "
                            + "'" + sib + "', "
                            + "'" + rs2.getString(1) + "', " //repId
                            + rs2.getInt(2) + ", " //numXML
                            + rs2.getDouble(3) + ", " //maxPw
                            + rs2.getInt(4) + ", " //numPepsTot
                            + rs2.getInt(5) + ", " //numPepsUniq
                            + rs2.getInt(6) + ", " //numSpecsTot
                            + rs2.getInt(7) + ", " //numSpecsUniq
                            + rs2.getDouble(8) + ", " //maxIniProb
                            + rs2.getDouble(9) + ", " //wt_maxIniProb
                            + rs2.getDouble(10) + " " //maxIniProbUniq
                            + ")";
                    stmt3.executeUpdate(query);
                }
                rs2.close();
                iter++;

                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                } else {
                    Globals.cursorStatus(iter, 100, msg);
                }
            }
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter);
            } else {
                Globals.cursorStatusDone(iter, msg);
            }
            stmt.executeUpdate("CREATE INDEX ps_idx1 ON protidSummary(groupid, siblingGroup)");
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter++);
            }
            stmt.executeUpdate("CREATE INDEX ps_idx2 ON protidSummary(repID)");
            if (pbh != null) {
                pbh.monitorBoxUpdate(iter++);
            }
            if (Globals.gene2protFile != null) {
                stmt.executeUpdate("ALTER TABLE protidSummary ADD COLUMN geneID VARCHAR(100) BEFORE numXML;");
            }   //clean up
            stmt.executeUpdate("DROP INDEX t3_idx1");
            stmt.executeUpdate("DROP INDEX t3_idx2");
            stmt.executeUpdate("DROP TABLE t3_");
            rs.close();
        }
        stmt2.close();
        stmt3.close();
        prep.close();
        prep2.close();
        if (pbh != null) {
            pbh.closeMonitorBox();
        }
        if (out != null) {
            out.append("\n"); // makes for prettier stderr
        }
    }

    public int retNumPeps(Connection conn, String tag, String pid, double wt, double iniProb) throws SQLException {
        return retNumPeps(conn, tag, pid, wt, iniProb, null);
    }

    public int retNumPeps(Connection conn, String tag, String pid, double wt, double iniProb, Appendable out) throws SQLException {
        String query;
        Statement stmt;
        ResultSet rs;
        int ret = 0;

        if (tag.equals(Globals.combinedFile)) {
            tag = "combined";
        }

        query = "CREATE MEMORY TABLE nptmp_ ( "
                + "  modPeptide, charge "
                + ") AS ( "
                + "SELECT modPeptide, charge "
                + "FROM prot2peps_" + tag + " "
                + "WHERE protid = '" + pid + "' "
                + "AND wt >= " + wt + " "
                + "AND iniProb >= " + iniProb + " "
                + "GROUP BY modPeptide, charge "
                + ") WITH DATA";

        stmt = conn.createStatement();
        stmt.executeUpdate(query);

        if (DbgUtils.DEBUG && pid.equals("ENSP00000222574")) {


            if (out != null) {
                try {
                    out.append("\n\n");

                    out.append("    Executing query: \n");
                    out.append(String.format("    %s\n", query));

                    out.append("\n\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            DbgUtils.dbGuiInMem();
            DbgUtils.sleep();
        }

        rs = stmt.executeQuery("SELECT COUNT(*) FROM nptmp_");
        rs.next();
        ret = rs.getInt(1);

        stmt.executeUpdate("DROP TABLE nptmp_");

        stmt.close();
        rs.close();

        return ret;
    }

    public double retMaxIniProb(Connection conn, String tag, String protid, double wt) throws SQLException {
        String query;
        Statement stmt;
        ResultSet rs;
        double ret = 0;

        if (tag.equals(Globals.combinedFile)) {
            tag = "combined";
        }

        query = "SELECT MAX(iniProb) "
                + "FROM prot2peps_" + tag + " "
                + "WHERE protid = '" + protid + "' "
                + "AND wt >= " + wt + " ";

        stmt = conn.createStatement();
        rs = stmt.executeQuery(query);

        rs.next();
        ret = rs.getDouble(1);

        stmt.close();
        rs.close();

        return ret;
    }

    /**
     * Function returns the wt of the maxIniProb for a given
     * groupid-siblingGroup.
     */
    private double retWTmaxIniProb(Connection conn, String tag, String protid, double maxIniProb) throws SQLException {

        if (tag.equals(Globals.combinedFile)) {
            tag = "combined";
        }

        String query = "SELECT MAX(wt) "
                + "FROM prot2peps_" + tag + " "
                + "WHERE protid = '" + protid + "' "
                + "AND iniProb = " + maxIniProb + " ";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query);) {
            rs.next();
            double result = rs.getDouble(1);
            return result;
        }
    }

    /*
     *  Function returns the number of spectra assigned to a given groupid-siblingGroup
     */
    public int retNumSpectra(Connection conn, String tag, String protid, double wt, double iniProb) throws SQLException {
        if (tag.equals(Globals.combinedFile)) {
            tag = "combined";
        }

        try (Statement stmt = conn.createStatement()) {

            String query = "SELECT SUM(nspecs) "
                    + "FROM prot2peps_" + tag + " "
                    + "WHERE protid = '" + protid + "' "
                    + "AND wt >= " + wt + " ";
            try (ResultSet rs = stmt.executeQuery(query)) {
                rs.next();
                int result = rs.getInt(1);
                return result;
            }
        }
    }

    /**
     * Function loads the user's protid-to-geneSymbol table into SQLite.
     *
     * @param conn
     * @param out
     * @return true on error
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public boolean makeGeneTable(Connection conn, Appendable out) throws SQLException, IOException {

        if (out != null) {
            if (!Globals.byGene) {
                out.append("  ");
            }
            out.append("Mapping protein IDs to their Gene IDs\n\n");
        }

        try (Statement stmt1 = conn.createStatement(); Statement stmt2 = conn.createStatement()) {
            PreparedStatement prep;
            stmt1.executeUpdate("DROP TABLE IF EXISTS gene2prot");
            String query = "CREATE CACHED TABLE gene2prot("
                    + "  geneid VARCHAR(250),"
                    + "  protid VARCHAR(250),"
                    + "  geneDefline VARCHAR(1000) DEFAULT 'No Gene Description' "
                    + ")";
            stmt1.executeUpdate(query);
            stmt1.executeUpdate("CREATE INDEX p2g_idx1 ON gene2prot(protid);");
            stmt1.executeUpdate("CREATE INDEX p2g_idx2 ON gene2prot(geneid);");
            query = "INSERT INTO gene2prot VALUES("
                    + "?, " //geneid
                    + "?," //protid
                    + "?)"; //gene description
            prep = conn.prepareStatement(query);
            //open the user's file for reading
            File mapFile = new File(Globals.gene2protFile);
            if (!mapFile.exists()) {
                if (out != null) {
                    out.append("\n\nERROR loading gene2prot map file\n"
                            + "The file '" + Globals.gene2protFile + "' doesn't exist!\n\n");
                }
                return true;
            }
            try (BufferedReader input = new BufferedReader(new FileReader(mapFile))) {
                String line = null;
                String ary[] = null;
                while ((line = input.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    if (line.matches("^[^\\w]*")) {
                        continue; // blank line
                    }
                    ary = line.split("\\t"); //ary[0] == geneid, ary[1] == protid, ary[2] == defline

                    if (ary.length > 1) {
                        if (!ary[0].isEmpty()) {
                            prep.setString(1, ary[0]);
                        }
                        if (!ary[1].isEmpty()) {
                            prep.setString(2, ary[1]);
                        }
                    }

                    if (ary.length == 3) {
                        Globals.genesHaveDescriptions = true;

                        String defline;

                        // TODO: WARNING: ACHTUNG: what does that condition mean?!
                        if (ary[2].length() > 1000) {
                            //defline = Globals.replaceAll(ary[2].substring(0, 990), '#', '_');
                            defline = ary[2].substring(0, 990).replace('#', '_');
                        } else {
                            defline = ary[2];
                        }

                        prep.setString(3, defline);
                    } else {
                        prep.setString(3, "No Gene Description");
                    }

                    prep.addBatch();
                }

                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.setAutoCommit(true);

            } catch (IOException | SQLException e) {
                String err = "Error parsing '" + Globals.gene2protFile + "'\n"
                        + e.toString() + "\n\n";

                if (out != null) {
                    out.append(err);
                }
                return true;
            }
            prep.close();
        }


        return false;
    }

    /**
     * Function adds gene IDs to the protidSummary table. This function is only
     * called if the gene2prot table exists in the SQLite database.
     *
     * @param conn
     * @param out
     * @throws IOException
     * @throws SQLException
     */
    public void appendGeneIDs(Connection conn, Appendable out) throws IOException, SQLException {
        if (out != null) {
            out.append("  Appending gene IDs to protidSummary table\n");
        }
        try (Statement stmt = conn.createStatement()) {
            String query = "UPDATE protidSummary ps "
                    + "  SET geneID = ( "
                    + "    SELECT gn.geneid "
                    + "    FROM gene2prot gn "
                    + "    WHERE gn.protid = ps.repID "
                    + ")";
            stmt.executeUpdate(query);
        }
    }

    /**
     * Function creates the final results table.
     *
     * @param conn
     * @param out
     * @return
     * @throws java.io.IOException
     * @throws java.sql.SQLException
     */
    public boolean makeResultsTable(Connection conn, Appendable out) throws IOException, SQLException {
        if (out != null) {
            out.append("\nCreating results table\n");
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS results");
            String query = "CREATE CACHED TABLE results ( protid, ";

            if (Globals.gene2protFile != null) {
                query += "geneid, ";
            }

            query += "  isFwd, defline, numXML, ALL_groupid, ALL_siblingGroup, "
                    + "  maxPw, ALL_Pw, ALL_localPw, maxIniProb, wt_maxIniProb, "
                    + "  maxIniProbUniq, ALL_numPepsTot, ALL_numPepsUniq, "
                    + "  ALL_numSpecsTot, ALL_numSpecsUniq "
                    + ") AS ("
                    + "SELECT b.repID, ";

            if (Globals.gene2protFile != null) {
                query += "b.geneid, ";
            }

            query += "c.isFwd, "
                    + "c.defline, "
                    + "b.numXML, "
                    + "b.groupid, "
                    + "b.siblingGroup, "
                    + "b.maxPw, "
                    + "c.Pw, "
                    + "c.localPw, "
                    + "b.maxIniProb, "
                    + "b.wt_maxIniProb, "
                    + "b.maxIniProbUniq, "
                    + "b.numPepsTot, "
                    + "b.numPepsUniq, "
                    + "b.numSpecsTot, "
                    + "b.numSpecsUniq "
                    + "FROM protidSummary AS b, combined AS c "
                    + "WHERE b.groupid = c.groupid "
                    + "AND b.siblingGroup = c.siblingGroup "
                    + "AND b.repID = c.protid "
                    + "GROUP BY "
                    + "b.repID, "
                    + "c.isFwd, "
                    + "c.defline, "
                    + "b.numXML, "
                    + "b.groupid, "
                    + "b.siblingGroup, "
                    + "b.maxPw, "
                    + "c.Pw, "
                    + "c.localPw, "
                    + "b.maxIniProb, "
                    + "b.wt_maxIniProb, "
                    + "b.maxIniProbUniq, "
                    + "b.numPepsTot, "
                    + "b.numPepsUniq, "
                    + "b.numSpecsTot, "
                    + "b.numSpecsUniq";

            if (Globals.gene2protFile != null) {
                query += ", b.geneid";
            }

            query += " ORDER BY b.groupid ASC, b.siblingGroup ASC"
                    + ") WITH DATA";

            if (DbgUtils.DEBUG && out != null) {
                out.append("\n    Populating results table using query:");
                out.append("\n    " + query);
                out.append("\n\n");
            }

            stmt.executeUpdate(query);

            // create indexes
            query = "CREATE INDEX res_gid_idx ON results(ALL_groupid, ALL_siblingGroup)";
            stmt.executeUpdate(query);

            query = "CREATE INDEX res_pid_idx ON results(protid)";
            stmt.executeUpdate(query);

            if (Globals.gene2protFile != null) {
                stmt.executeUpdate("UPDATE results SET geneid = 'DECOY' WHERE isFwd = 0");
            }

            stmt.executeUpdate("UPDATE results SET defline = 'DECOY PROTEIN' WHERE isFwd = 0");
        }

        return false;
    }

    /**
     * Function parses the contents of the GLOBALS.protLen hash map and uses it
     * to populate the protLen field of the results table.
     *
     * @param conn
     * @param dataType
     * @param out
     * @param pbh
     * @throws java.io.IOException
     */
    public void addProteinLengths(Connection conn, int dataType, Appendable out, ProgressBarHandler pbh) throws IOException, SQLException {
        if (out != null) {
            out.append("  Appending protLen column\n");
        }
        //else System.err.print("  Appending protLen column");

        PreparedStatement prep = null;
        ResultSet rs, rs2 = null;
        int ctr = 0;
        int N = 0;

        //Statement stmt;
        try (Statement stmt = conn.createStatement()) {
            if (dataType == 0) {

                if (out != null) {
                    out.append("  Appending protein lengths\n");
                }

                stmt.executeUpdate("ALTER TABLE results ADD COLUMN protLen INT BEFORE isFwd");
                prep = conn.prepareStatement("UPDATE results SET protLen = ? WHERE protid = ?");

                rs = stmt.executeQuery("SELECT DISTINCT protid, protLen FROM COMBINED");
                while (rs.next()) {
                    String pid = rs.getString(1);
                    int protLen = rs.getInt(2);

                    prep.setInt(1, protLen);
                    prep.setString(2, pid);
                    prep.addBatch();
                }
                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.setAutoCommit(true);

                rs.close();
            } else if (dataType == 1) {
                // The v_results table is a duplicate of results table so the protLen field is already present
                prep = conn.prepareStatement("UPDATE v_results SET protLen = ? WHERE protid = ?");

                if (pbh != null) {
                    rs = stmt.executeQuery("SELECT COUNT(DISTINCT protid) FROM results");
                    rs.next();
                    N = rs.getInt(1);
                    pbh.monitorBoxInit(N, "Appending additional protein lengths...");
                }

                rs = stmt.executeQuery("SELECT DISTINCT protid FROM v_results WHERE protid LIKE '%:::%'");
                while (rs.next()) {
                    String x = rs.getString(1);
                    String pid = x.substring((x.lastIndexOf(':') + 1));

                    rs2 = stmt.executeQuery("SELECT protLen FROM COMBINED WHERE protid = '" + pid + "' ");
                    rs2.next();
                    int protLen = rs2.getInt(1);

                    prep.setInt(1, protLen);
                    prep.setString(2, x);
                    prep.addBatch();

                    ctr++;
                    if (pbh != null) {
                        pbh.monitorBoxUpdate(ctr);
                    } else {
                        Globals.cursorStatus(ctr, "  Appending additional protein lengths...");
                    }

                }
                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.setAutoCommit(true);
                if (rs2 != null) {
                    rs2.close();
                }

                // looks better on STDERR
                if (out != null) {
                    out.append("\n");
                }
            }
        }
        if (prep != null) {
            prep.close();
        }

    }

    /**
     * **************
     *
     * Function creates a table listing the number of unique peptides each
     * protein in the protidSummary table has.
     *
     * @param conn
     * @throws Exception
     *
     */
    public void makeWT9XgroupsTable(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        ResultSet rs1, rs2;
        String query = null;
        String tag = null;

        rs1 = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot'");
        while (rs1.next()) {
            tag = rs1.getString(1);

            query = "CREATE CACHED TABLE wt9X_" + tag + " ("
                    + "  protid, nspecsUniq )  AS ( "
                    + "SELECT protid, SUM(nspecs) "
                    + "FROM prot2peps_" + tag + " "
                    + "WHERE wt >= " + this.wtTH + " "
                    + "AND iniProb >= " + this.iniProbTH + " "
                    + "GROUP BY protid ) WITH DATA ";
            stmt.executeUpdate(query);

            query = "CREATE INDEX wt_idx1_" + tag + " ON wt9X_" + tag + "(protid)";
            stmt.executeUpdate(query);
        }

        stmt.close();
    }

    /**
     * Function called within adjSpectralCounts function. Creates the
     * pepUsage_table.
     *
     * @param conn
     * @param out
     * @param pbh
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void makePepUsageTable(Connection conn, Appendable out, ProgressBarHandler pbh) throws SQLException, IOException {
        if (out != null) {
            out.append("\nCreating peptide usage table\n");
        }

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs1, rs2;
            PreparedStatement prep;
            String query;
            String tag;
            int N, iter;
            query = "CREATE CACHED TABLE pepUsage_ ("
                    + "  tag VARCHAR(100), "
                    + "  protid VARCHAR(100), "
                    + "  modPeptide VARCHAR(250), "
                    + "  charge INT, "
                    + "  nspecs INT DEFAULT 0, "
                    + "  numer INT DEFAULT 0, "
                    + "  denom INT DEFAULT 0, "
                    + "  alpha DECIMAL(8,6), "
                    + "  adjSpecs INT DEFAULT 0"
                    + ")";
            stmt.executeUpdate(query);
            query = "INSERT INTO pepUsage_ (tag, protid, modPeptide, charge, nspecs, numer) VALUES ("
                    + "?, ?, ?, ?, ?, ?) ";
            prep = conn.prepareStatement(query);
            rs1 = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot'");
            while (rs1.next()) {
                tag = rs1.getString(1);

                // get counter for progress monitor
                rs2 = stmt.executeQuery("SELECT COUNT(*) FROM prot2peps_" + tag + " ");
                rs2.next();
                N = rs2.getInt(1);
                rs2.close();

                if (out != null) {
                    out.append("  Indexing Peptide Usage for: " + tag + "\n");
                }
                if (pbh != null) {
                    pbh.monitorBoxInit(N, "Indexing Peptide Usage (" + tag + ")...");
                } else {
                    Globals.cursorStatus(N, "  Peptide usage index (" + tag + ")... ");
                }

                query = "SELECT s.repid, a.modPeptide, a.charge, a.nspecs, b.nspecsUniq "
                        + "FROM prot2peps_" + tag + " AS a, wt9X_" + tag + " AS b, protidSummary AS s "
                        + "WHERE a.protid = b.protid "
                        + "AND a.protid = s.repid "
                        + "GROUP BY s.repid, a.modPeptide, a.charge, a.nspecs, b.nspecsUniq ";
                rs2 = stmt.executeQuery(query);

                iter = 0;
                while (rs2.next()) {
                    prep.setString(1, tag);
                    prep.setString(2, rs2.getString(1));
                    prep.setString(3, rs2.getString(2));
                    prep.setInt(4, rs2.getInt(3));
                    prep.setInt(5, rs2.getInt(4));
                    prep.setInt(6, rs2.getInt(5));
                    prep.addBatch();

                    iter++;
                    if (pbh != null) {
                        pbh.monitorBoxUpdate(iter);
                    } else {
                        Globals.cursorStatus(iter, 100, "  Peptide usage index (" + tag + ")... ");
                    }
                }
                if (pbh != null) {
                    pbh.monitorBoxUpdate(iter);
                } else {
                    Globals.cursorStatusDone(iter, "  Peptide usage index (" + tag + ")... ");
                }
                rs2.close();

                stmt.executeUpdate("DROP INDEX IF EXISTS wt_idx1_" + tag + " ");
                stmt.executeUpdate("DROP TABLE IF EXISTS wt9X_" + tag + " ");

                if (pbh != null) {
                    pbh.closeMonitorBox();
                }
            }
            conn.setAutoCommit(false);
            prep.executeBatch();
            conn.setAutoCommit(true);
            prep.clearBatch();
            prep.close();
            rs1.close();
            if (out != null) {
                out.append("  Indexing pepUsage_ table\n");
            }
            stmt.executeUpdate("CREATE INDEX pu_idx1 ON pepUsage_(tag, protid)");
            stmt.executeUpdate("CREATE INDEX pu_idx2 ON pepUsage_(tag, modPeptide, charge)");

            // Define function to count 'numer' field in pepUsage_
            query = "CREATE FUNCTION sumNumer( "
                    + "  tag_ VARCHAR(100), "
                    + "  modPep_ VARCHAR(250), "
                    + "  charge_ INT "
                    + ") RETURNS INT "
                    + "READS SQL DATA " // required by HSQLDB 2.1.0
                    + "BEGIN ATOMIC "
                    + "  DECLARE rv INT; "
                    + "  SET rv = ( "
                    + "    SELECT SUM(numer) "
                    + "    FROM pepUsage_ "
                    + "    WHERE tag = tag_ "
                    + "    AND modPeptide = modPep_ "
                    + "    AND charge = charge_ "
                    + "  ); "
                    + "  RETURN rv; "
                    + "END ";
            stmt.executeUpdate(query);
            stmt.executeUpdate("UPDATE pepUsage_ SET denom = sumNumer(tag, modPeptide, charge)");
            query = "UPDATE pepUsage_ "
                    + "  SET alpha = ROUND((CAST(numer AS DECIMAL(16,6)) / CAST(denom AS DECIMAL(16,6))), 6)";
            stmt.executeUpdate(query);
            if (out != null) {
                out.append("  Updating adjusted spectral counts\n");
            }
            query = "UPDATE pepUsage_ "
                    + "  SET adjSpecs = ( CAST("
                    + "   ROUND( (CAST(nspecs AS DECIMAL(16,6)) * alpha), 0) "
                    + "   AS INT) "
                    + ") ";
            stmt.executeUpdate(query);
            stmt.executeUpdate("UPDATE pepUsage_ SET adjSpecs = 0 WHERE adjSpecs IS NULL");
        }

    }

    /**
     * Function appends the statistics for the repID reported in the results
     * from among all of the independent experiment files (ie: files in protXML
     * table).
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void appendIndividualExpts(Connection conn, Appendable out) throws SQLException, IOException {
        if (out != null) {
            out.append("\nRetrieving data from individual experiments\n");
        }

        try (Statement stmt = conn.createStatement()) {
            // Get all the individual files
            ResultSet rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot' ORDER BY tag ASC;");
            while (rs.next()) {
                String tag = rs.getString(1).trim();
                if (out != null) {
                    out.append("  Adding data from " + tag + "\n");
                }

                this.appendColumns(conn, tag);
                this.fillColumns(conn, tag);
                this.updateSpectralCounts(conn, tag);
            }
            rs.close();
        }
    }

    /**
     * Function adds empty columns to results table based upon 'tag'
     * information.
     */
    private void appendColumns(Connection conn, String tag) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_groupid INT ");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_sibGroup VARCHAR(5) ");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_Pw DECIMAL(8,6) ");

            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_numPepsTot INT DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_numPepsUniq INT DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_numSpecsTot INT DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_numSpecsUniq INT DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_numSpecsAdj INT DEFAULT 0");
        }
    }

    /**
     * Function fills in the values for the given experiment (ie: tag).
     */
    private void fillColumns(Connection conn, String tag) throws SQLException {

        try (Statement stmt = conn.createStatement()) {
            ResultSet rs;
            String query = "UPDATE results SET "
                    + tag + "_groupid = ?, "
                    + tag + "_sibGroup = ?, "
                    + tag + "_Pw = ?, "
                    + tag + "_numPepsTot = ?, "
                    + tag + "_numPepsUniq = ?, "
                    + tag + "_numSpecsTot = ?, "
                    + tag + "_numSpecsUniq = ? "
                    + "WHERE protid = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            // check to see if there are any rows in protXML for the given 'tag'
            // If the user provided a list of required AA mods you may not get any
            // results if the given AA's are not found in a certain file.
            query = "SELECT COUNT(*) FROM protXML where tag = '" + tag + "'";
            rs = stmt.executeQuery(query);
            rs.next();
            int N = rs.getInt(1);
            if (N > 0) {
                query = "SELECT p.groupid, p.siblingGroup, p.localPw, p.protid "
                        + "FROM protXML AS p, results AS r "
                        + "WHERE p.tag = '" + tag + "' "
                        + "AND p.protid = r.protid "
                        + "GROUP BY p.groupid, p.siblingGroup, p.localPw, p.protid; ";
                rs = stmt.executeQuery(query);

                while (rs.next()) {
                    int gid = rs.getInt(1);
                    String sib = rs.getString(2);
                    double localPw = rs.getDouble(3);
                    String protid = rs.getString(4);

                    int numPepsTot = this.retNumPeps(conn, tag, protid, 0, this.iniProbTH);
                    int numPepsUniq = this.retNumPeps(conn, tag, protid, this.wtTH, this.iniProbTH);
                    int nspecsTot = this.retNumSpectra(conn, tag, protid, 0, this.iniProbTH);
                    int nspecsUniq = this.retNumSpectra(conn, tag, protid, this.wtTH, this.iniProbTH);

                    prep.setInt(1, gid);
                    prep.setString(2, sib);
                    prep.setDouble(3, localPw);
                    prep.setInt(4, numPepsTot);
                    prep.setInt(5, numPepsUniq);
                    prep.setInt(6, nspecsTot);
                    prep.setInt(7, nspecsUniq);
                    prep.setString(8, protid);
                    prep.addBatch();
                }
                conn.setAutoCommit(false);
                prep.executeBatch();
                conn.setAutoCommit(true);
            }
            if (rs != null) {
                rs.close();
            }
            if (prep != null) {
                prep.close();
            }
        }
    }

    /**
     * Function updates result table with new adjusted spectral counts.
     *
     * @param conn
     * @param tag
     * @throws SQLException
     */
    public void updateSpectralCounts(Connection conn, String tag) throws SQLException {

        try (Statement stmt = conn.createStatement()) {
            String query = "CREATE CACHED TABLE adjSpecs_ ( "
                    + "  tag, protid, X "
                    + ") AS ( "
                    + "SELECT tag, protid, SUM(adjSpecs) "
                    + "FROM pepUsage_ "
                    + "WHERE tag = '" + tag + "' "
                    + "GROUP BY tag, protid "
                    + "ORDER BY tag "
                    + ") WITH DATA ";
            stmt.executeUpdate(query);

            stmt.executeUpdate("CREATE INDEX asp_idx2 ON adjSpecs_(protid)");

            query = "UPDATE results "
                    + "  SET " + tag + "_numSpecsAdj = ( "
                    + "    SELECT X "
                    + "    FROM adjSpecs_ "
                    + "    WHERE adjSpecs_.tag = '" + tag + "' "
                    + "    AND adjSpecs_.protid  = results.protid "
                    + "  ) ";
            stmt.executeUpdate(query);

            stmt.executeUpdate("DROP TABLE adjSpecs_");
        }
    }

    /**
     * Function writes the final results to a file.
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void defaultResults(Connection conn, Appendable out) throws SQLException, IOException {

        String outputFileName = Globals.outputFilePath;

        if (out != null) {
            out.append("\nWriting results to: '" + outputFileName + "'\n");
        }

        Statement stmt;
        ResultSet rs;
        ResultSetMetaData rsmd;
        String query;

        try (BufferedWriter outBuf = new BufferedWriter(new FileWriter(outputFileName))) {
            stmt = conn.createStatement();

            query = "SELECT * FROM results ORDER BY ALL_id;";
            if (Globals.makeVerboseOutput) {
                query = "SELECT * FROM v_results ORDER BY ALL_id;";
            }

            if (Globals.byGene) {
                query = "SELECT * FROM geneResults ORDER BY geneid;";
            }

            rs = stmt.executeQuery(query);
            rsmd = rs.getMetaData();
            int numColumns = rsmd.getColumnCount();

            // print header line
            for (int i = 1; i < numColumns; i++) {
                String colName = rsmd.getColumnName(i);
                outBuf.append(colName).append("\t");
            }
            outBuf.append(rsmd.getColumnName(numColumns)).append("\n");

            // print values
            while (rs.next()) {
                for (int i = 1; i <= numColumns; i++) {
                    int colType = rsmd.getColumnType(i);
                    switch (colType) {
                        case Types.INTEGER:
                            outBuf.append(Integer.toString(rs.getInt(i)));
                            break;
                        case Types.VARCHAR:
                            outBuf.append(rs.getString(i));
                            break;
                        default:
                            double d1 = rs.getDouble(i);
                            outBuf.append(Double.toString(Globals.roundDbl(d1, 4)));
                            break;
                    }
                    if (i != numColumns) {
                        outBuf.append("\t");
                    } else {
                        outBuf.append("\n");
                    }
                }
            }
        }
    }

    /**
     * Function generates output file formatted for submission to QSpec.
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void formatQspecOutput(Connection conn, Appendable out) throws SQLException, IOException {
        Statement stmt = conn.createStatement();
        ResultSet rs;
        ResultSetMetaData rsmd;
        String query = null;

        String outputFileName = Globals.outputFilePath;
        if (out != null) {
            out.append("\nWriting spectral counts for QSpec to file:\n\t'" + outputFileName + "'\n");
        }

        try (BufferedWriter outBuf = new BufferedWriter(new FileWriter(outputFileName))) {
            if (Globals.outputFormat == Globals.protQspecFormat) {
                query = "SELECT * FROM results ORDER BY ALL_id";
                if (Globals.makeVerboseOutput) {
                    query = "SELECT * FROM v_results ORDER BY ALL_id";
                }
            } else if (Globals.outputFormat == Globals.geneQspecFormat) {
                // assume user wants gene-centric output
                query = "SELECT * FROM geneResults ORDER BY geneid ";
            }

            rs = stmt.executeQuery(query);
            rsmd = rs.getMetaData();
            int numColumns = rsmd.getColumnCount();

            // get column names and write them to file.
            // we only want the nspecsAdj columns
            Map<Integer, String> colHdrs = new LinkedHashMap<>();
            String c = null;
            for (int i = 1; i <= numColumns; i++) {
                c = rsmd.getColumnName(i);

                if (Globals.outputFormat == Globals.geneQspecFormat) { // gene-centric output
                    if (c.equalsIgnoreCase("geneid")) {
                        colHdrs.put(i, "geneid");
                    } else if (c.equalsIgnoreCase("avgProtLen")) {
                        colHdrs.put(i, "avgProtLen");
                    } else if (c.endsWith("_NUMSPECSADJ")) {
                        int j = c.indexOf("_NUMSPECSADJ");
                        String s = c.substring(0, j);
                        colHdrs.put(i, s);
                    }
                } else { // for protein-centric output
                    if (c.equalsIgnoreCase("protid")) {
                        colHdrs.put(i, "protid");
                    } else if (c.equalsIgnoreCase("protLen")) {
                        colHdrs.put(i, "protLen");
                    } else if (c.endsWith("_NUMSPECSADJ")) {
                        int j = c.indexOf("_NUMSPECSADJ");
                        String s = c.substring(0, j);
                        colHdrs.put(i, s);
                    }
                }
            }

            int maxColNum = 0; // holds the maximum column index for the relevant columns
            for (Iterator<Integer> it = colHdrs.keySet().iterator(); it.hasNext();) {
                int k = it.next();
                String v = colHdrs.get(k);
                outBuf.append(v);
                if (it.hasNext()) {
                    outBuf.append("\t");
                }
                if (k > maxColNum) {
                    maxColNum = k;
                }
            }
            outBuf.append("\n");

            // now write the data
            while (rs.next()) {
                for (int i = 1; i <= numColumns; i++) {
                    if (colHdrs.containsKey(i)) {
                        int colType = rsmd.getColumnType(i);
                        switch (colType) {
                            case Types.INTEGER:
                                outBuf.append(Integer.toString(rs.getInt(i)));
                                break;
                            case Types.VARCHAR:
                                outBuf.append(rs.getString(i));
                                break;
                        }
                        if (i != maxColNum) {
                            outBuf.append("\t");
                        } else {
                            outBuf.append("\n");
                        }
                    }
                }
            }
        }

    }

    /**
     * Function writes a custom results file based upon user's chosen columns.
     * Because the USER is making the column choices, the resulting table may
     * make absolutely no sense.
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void customOutput(Connection conn, Appendable out) throws SQLException, IOException {

        // Need to construct a new HashMap that contains all the fields in the
        // results table that will be used for the custom output.
        Map<Integer, String> selectCols = new HashMap<>();
        Statement stmt1 = conn.createStatement();
        ResultSet rs1;
        ResultSetMetaData rsmd;
        Set<String> exptSet = null;
        String query;
        int numCols = 0;

        // first construct the field names for the individual experiments
        // using the data in printE Set
        if (Globals.printE.size() > 0) {
            exptSet = new HashSet<>();
            rs1 = stmt1.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot';");
            while (rs1.next()) {
                String tag = rs1.getString(1);

                for (String suffix : Globals.printE) {
                    String colName = tag.toUpperCase() + suffix.toUpperCase();
                    exptSet.add(colName);
                }
            }
            rs1.close();
        }

        query = "SELECT * FROM results LIMIT 1";
        if (Globals.makeVerboseOutput) {
            query = "SELECT * FROM v_results LIMIT 1";
        } else if (Globals.byGene) {
            query = "SELECT * FROM geneResults LIMIT 1";
        }

        rs1 = stmt1.executeQuery(query);
        rsmd = rs1.getMetaData();

        // Now we will populate the HashMap with column names that we want to extract
        numCols = rsmd.getColumnCount();
        for (int i = 1; i <= numCols; i++) {
            String colName = rsmd.getColumnName(i);

            if (!Globals.printC.isEmpty()) {
                if (Globals.printC.contains(colName)) {
                    selectCols.put(i, colName);
                    //if(console != null) console.append("Adding " + colName + "\n");
                }

            }

            if (!Globals.printE.isEmpty()) {
                if (exptSet != null && exptSet.contains(colName)) {
                    selectCols.put(i, colName);
                    //if(console != null) console.append("Adding " + colName + "\n");
                }
            }
        }
        rs1.close();

        if (selectCols.isEmpty()) {
            if (out != null) {
                out.append("No columns have been selected for output\n");
            }
            return;
        }

        // Now construct final query
        // need to order the data in 'selectCols' from high to low column numbers
        String queryBody = "";
        SortedSet<Integer> mapKeys = new TreeSet<>();
        for (Integer k : selectCols.keySet()) {
            mapKeys.add(k);
        }

        if (out != null) {
            out.append("\nCustom output columns:\n");
        }

        for (Integer k : mapKeys) {
            String v = selectCols.get(k);

            if (out != null) {
                out.append(v + "\n");
            }

            queryBody += v + ", ";
        }
        String tmp = queryBody.substring(0, queryBody.length() - 2);
        queryBody = tmp;

        // write data to file
        NumberFormat formatter = new DecimalFormat("#0.0000");
        String outputFileName = Globals.outputFilePath;

        if (out != null) {
            out.append("\nWriting results to: '" + outputFileName + "'\n");
        }

        try (BufferedWriter outBuf = new BufferedWriter(new FileWriter(outputFileName))) {
            query = "SELECT DISTINCT " + queryBody + " FROM results";
            if (Globals.makeVerboseOutput) {
                query = "SELECT DISTINCT " + queryBody + " FROM v_results";
            } else if (Globals.byGene) {
                query = "SELECT DISTINCT " + queryBody + " FROM geneResults";
            }

            rs1 = stmt1.executeQuery(query);
            rsmd = rs1.getMetaData();

            numCols = rsmd.getColumnCount();
            // print header line
            for (int i = 1; i < numCols; i++) {
                String colName = rsmd.getColumnName(i);
                outBuf.append(colName).append("\t");
            }
            outBuf.append(rsmd.getColumnName(numCols)).append("\n");

            while (rs1.next()) {
                for (int i = 1; i <= numCols; i++) {
                    int colType = rsmd.getColumnType(i);
                    switch (colType) {
                        case Types.INTEGER:
                            outBuf.append(Integer.toString(rs1.getInt(i)));
                            break;
                        case Types.VARCHAR:
                            outBuf.append(rs1.getString(i));
                            break;
                        default:
                            double d1 = rs1.getDouble(i);
                            double d2 = Double.parseDouble(formatter.format(d1));
                            outBuf.append(String.valueOf(d2));
                            break;
                    }
                    if (i != numCols) {
                        outBuf.append("\t");
                    } else {
                        outBuf.append("\n");
                    }
                }
            }
        }

    }

    /**
     * ****************
     *
     * Function to remove unnecessary tables from database. Add entries as
     * needed
     *
     * @param conn
     * @throws SQLException
     *
     */
    public void cleanUp(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = null;
        String tag = null;

        // drop expt specific tables
        rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags ");
        while (rs.next()) {
            tag = rs.getString(1);
            stmt.executeUpdate("DROP INDEX IF EXISTS wt_idx1_" + tag);
            stmt.executeUpdate("DROP TABLE IF EXISTS wt9X_" + tag);

            stmt.executeUpdate("DROP INDEX IF EXISTS pt2peps_" + tag + "_idx1");
            stmt.executeUpdate("DROP INDEX IF EXISTS pt2peps_" + tag + "_idx2");
            stmt.executeUpdate("DROP TABLE IF EXISTS prot2peps_" + tag);
        }
        rs.close();

        stmt.executeUpdate("DROP INDEX IF EXISTS pt2peps_combined_idx1");
        stmt.executeUpdate("DROP INDEX IF EXISTS pt2peps_combined_idx2");
        stmt.executeUpdate("DROP TABLE IF EXISTS prot2peps_combined");

        if (Globals.byGene) {
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx1");
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx2");
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx3");
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx4");
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx5");
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx6");
            stmt.executeUpdate("DROP INDEX IF EXISTS g2pep_idx7");
            stmt.executeUpdate("DROP TABLE IF EXISTS g2pep_");
            stmt.executeUpdate("DROP TABLE IF EXISTS t1_");
        } else {
            stmt.executeUpdate("DROP INDEX IF EXISTS pt2pep_idx1");
            stmt.executeUpdate("DROP INDEX IF EXISTS pt2pep_idx2");
            stmt.executeUpdate("DROP INDEX IF EXISTS pt2pep_idx3");
            stmt.executeUpdate("DROP INDEX IF EXISTS pt2pep_idx4");
            stmt.executeUpdate("DROP INDEX IF EXISTS pt2pep_idx5");
            stmt.executeUpdate("DROP TABLE IF EXISTS t1_");
        }

        stmt.close();
    }

    /**
     * Function concatenates the groupid and siblingGroup fields together to
     * make reading the output easier.
     *
     * @param conn
     * @throws SQLException
     */
    public void mergeIDfields(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            try (Statement stmt2 = conn.createStatement()) {
                ResultSet rs;
                String query;

//                DbgUtils.dbGuiInMem();
//                DbgUtils.sleep();

                if (!Globals.byGene) {
                    query = "ALTER TABLE results ADD COLUMN ALL_id VARCHAR(20) BEFORE maxPw";
                    stmt.executeUpdate(query);

                    query = "UPDATE results rs "
                            + "  SET ALL_id = (ALL_groupid || '-'|| ALL_siblingGroup)";
                    stmt.executeUpdate(query);
                    stmt.executeUpdate("ALTER TABLE results DROP COLUMN ALL_groupid");
                    stmt.executeUpdate("ALTER TABLE results DROP COLUMN ALL_siblingGroup");

                    //append individual experiment fields
                    rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot'");
                    while (rs.next()) {
                        String tag = rs.getString(1);
                        query = "ALTER TABLE results "
                                + "ADD COLUMN " + tag + "_id VARCHAR(20) BEFORE "
                                + tag + "_groupid ";
                        stmt2.executeUpdate(query);

                        query = "UPDATE results "
                                + "  SET " + tag + "_id = (" + tag + "_groupid || '-' || " + tag + "_sibGroup)";
                        stmt2.executeUpdate(query);

                        query = "ALTER TABLE results DROP COLUMN " + tag + "_groupid ";
                        stmt2.executeUpdate(query);
                        query = "ALTER TABLE results DROP COLUMN " + tag + "_sibGroup ";
                        stmt2.executeUpdate(query);
                    }
                    rs.close();
                }
            }
        }
    }

    /**
     * The pepXML file names may not be identical to the protXML file names eg:
     * protXML name = interact.bas.prot.xml pepXML names =
     * interact.bas_1.pep.xml interact.bas_2.pep.xml etc...
     *
     * This function uses the srcFileTags table to append matching tags to the
     * pepXML table.
     *
     * @param conn
     * @throws SQLException
     *
     */
    public void correctPepXMLTags(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("ALTER TABLE pepXML ADD COLUMN tag VARCHAR(250)");

            String query = "UPDATE pepXML px "
                    + "  SET tag = ( "
                    + "    SELECT sf.tag "
                    + "    FROM srcFileTags sf "
                    + "    WHERE sf.fileType = 'pep' "
                    + "    AND sf.srcFile = px.srcFile "
                    + ")";
            stmt.executeUpdate(query);

            stmt.executeUpdate("CREATE INDEX pepxml_idx1 ON pepXML(specId)");
            stmt.executeUpdate("CREATE INDEX pepxml_idx2 ON pepXML(modPeptide)");
            stmt.executeUpdate("CREATE INDEX pepxml_idx3 ON pepXML(tag, modPeptide, charge)");
            stmt.executeUpdate("CREATE INDEX pepxml_idx4 ON pepXML(tag, specId)");
            stmt.executeUpdate("CREATE INDEX pepxml_idx5 ON pepXML(tag, modPeptide)");
            stmt.executeUpdate("CREATE INDEX pepxml_idx6 ON pepXML(modPeptide, charge)");
        }
    }

    /**
     * Function generates spectral count data in ln(NSAF) format.
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void getNSAF_values_prot(Connection conn, Appendable out) throws SQLException, IOException {

        try (Statement stmt = conn.createStatement()) {
            String query, msg;
            ResultSet rs, rs2;

            stmt.executeUpdate("DROP TABLE IF EXISTS nsaf_p1");
            stmt.executeUpdate("DROP TABLE IF EXISTS nsaf");
            msg = "\nCreating NSAF values table (protein-centric)\n";
            if (out != null) {
                out.append(msg);
            }

            // initial table
            query = "CREATE CACHED TABLE nsaf_p1 ("
                    + "  protid "
                    + ") AS ( "
                    + "SELECT protid "
                    + "FROM results "
                    + "GROUP BY protid "
                    + "ORDER BY protid ASC "
                    + ")WITH DATA";
            stmt.executeUpdate(query);
            stmt.executeUpdate("CREATE INDEX nsaf_p1_idx1 ON nsaf_p1(protid)");
            // final NSAF table
            query = "CREATE CACHED TABLE nsaf ("
                    + "  protid "
                    + ") AS ( "
                    + "SELECT protid "
                    + "FROM results "
                    + "GROUP BY protid "
                    + "ORDER BY protid ASC "
                    + ")WITH DATA";
            stmt.executeUpdate(query);
            stmt.executeUpdate("CREATE INDEX nsaf_idx1 ON nsaf(protid)");
            /*
             * All NSAF values are multiplied by a factor to raise their values.
             * This is done because of a rounding error problem inherent to HSQLDB.
             * I can't find the cause of the bug, but by multiplying the values by this
             * factor I can eliminate it.
             * Our factor is computed as 10^N where N = # of digits in representing
             * number of proteins reported + 1
             * example: if 377 proteins are reported then N = 3+1, 377 consists of 3 digits
             */
            rs = stmt.executeQuery("SELECT COUNT(protid) FROM results WHERE isFwd = 1");
            rs.next();
            String numProts = Integer.toString(rs.getInt(1));
            int factor = numProts.length() + 1;
            double NSAF_FACTOR = Math.pow(10, factor); // we multiply all NSAF values by this
            Globals.NSAF_FACTOR = NSAF_FACTOR;
            rs.close();
            msg = "  NSAF_FACTOR = 10^" + factor + " = " + NSAF_FACTOR + "\n";
            if (out != null) {
                out.append(msg);
            }

            // now you need the add columns for spectral counts
            rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot' ORDER BY tag ASC");

            try (Statement stmt2 = conn.createStatement()) {
                try (Statement stmt3 = conn.createStatement()) {
                    while (rs.next()) {
                        String tag = rs.getString(1);

                        stmt2.executeUpdate("ALTER TABLE nsaf_p1 ADD COLUMN " + tag + "_specsTot DOUBLE DEFAULT 0");
                        stmt2.executeUpdate("ALTER TABLE nsaf_p1 ADD COLUMN " + tag + "_specsUniq DOUBLE DEFAULT 0");
                        stmt2.executeUpdate("ALTER TABLE nsaf_p1 ADD COLUMN " + tag + "_specsAdj DOUBLE DEFAULT 0");

                        // also add columns to results table
                        stmt2.executeUpdate("ALTER TABLE nsaf ADD COLUMN " + tag + "_totNSAF DOUBLE DEFAULT 0");
                        stmt2.executeUpdate("ALTER TABLE nsaf ADD COLUMN " + tag + "_uniqNSAF DOUBLE DEFAULT 0");
                        stmt2.executeUpdate("ALTER TABLE nsaf ADD COLUMN " + tag + "_adjNSAF DOUBLE DEFAULT 0");

                        // get spectral counts and protein lengths
                        query = "SELECT protid, protLen, "
                                + "  " + tag + "_numSpecsTot,  "
                                + "  " + tag + "_numSpecsUniq, "
                                + "  " + tag + "_numSpecsAdj "
                                + "FROM results "
                                + "ORDER BY protid ";

                        rs2 = stmt2.executeQuery(query);

                        // assign spectral-count/length to nsaf_p1 table
                        while (rs2.next()) {
                            String protid = rs2.getString(1);
                            double protLen = rs2.getDouble(2);

                            double tot = rs2.getDouble(3) / protLen;
                            double uniq = rs2.getDouble(4) / protLen;
                            double adj = rs2.getDouble(5) / protLen;

                            query = "UPDATE nsaf_p1 "
                                    + "  SET " + tag + "_specsTot = " + tot + ", "
                                    + "      " + tag + "_specsUniq = " + uniq + ", "
                                    + "      " + tag + "_specsAdj = " + adj + " "
                                    + "WHERE protid = '" + protid + "' ";

                            stmt3.executeUpdate(query);
                        }
                        rs2.close();

                        // now add up columns of current tag
                        query = "SELECT SUM(" + tag + "_specsTot), "
                                + "       SUM(" + tag + "_specsUniq), "
                                + "       SUM(" + tag + "_specsAdj) "
                                + "FROM nsaf_p1 ";
                        rs2 = stmt2.executeQuery(query);
                        rs2.next();

                        double totSum = rs2.getDouble(1);
                        double uniqSum = rs2.getDouble(2);
                        double adjSum = rs2.getDouble(3);

                        rs2.close();

                        query = "SELECT protid, "
                                + "  " + tag + "_specsTot, "
                                + "  " + tag + "_specsUniq, "
                                + "  " + tag + "_specsAdj "
                                + "FROM nsaf_p1 "
                                + "GROUP BY protid, "
                                + "  " + tag + "_specsTot, "
                                + "  " + tag + "_specsUniq, "
                                + "  " + tag + "_specsAdj "
                                + "ORDER BY protid ASC ";

                        rs2 = stmt2.executeQuery(query);

                        while (rs2.next()) {
                            String protid = rs2.getString(1);

                            double x_t = rs2.getDouble(2);
                            double x_u = rs2.getDouble(3);
                            double x_a = rs2.getDouble(4);

                            double nsaf_t = (x_t / totSum) * NSAF_FACTOR;
                            double nsaf_u = (x_u / uniqSum) * NSAF_FACTOR;
                            double nsaf_a = (x_a / adjSum) * NSAF_FACTOR;

                            query = "UPDATE nsaf "
                                    + "  SET " + tag + "_totNSAF = " + nsaf_t + ", "
                                    + " " + tag + "_uniqNSAF = " + nsaf_u + ","
                                    + " " + tag + "_adjNSAF = " + nsaf_a + " "
                                    + " WHERE protid = '" + protid + "' ";

                            stmt3.executeUpdate(query);
                        }
                        rs2.close();
                    }
                }
            }
            rs.close();
            stmt.executeUpdate("DROP INDEX nsaf_p1_idx1");
            stmt.executeUpdate("DROP TABLE nsaf_p1");
        }

        // Now we need to add NSAF values to results table
        reformat_results(conn, out);
    }

    /**
     * Function to reformats the results table to include NSAF values
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void reformat_results(Connection conn, Appendable out) throws SQLException, IOException {

        String msg = "\nAdding NSAF values to results table.\n";
        if (out != null) {
            out.append(msg);
        }

        ResultSet rs1;
        String query;
        Statement stmt = conn.createStatement();

        // Capture all of the srcFileTags in order
        String[] tags;
        int N = 0;
        rs1 = stmt.executeQuery("SELECT COUNT(DISTINCT tag) FROM srcFileTags WHERE fileType = 'prot'");
        rs1.next();
        N = rs1.getInt(1);
        tags = new String[N];

        rs1.close();
        rs1 = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags WHERE fileType = 'prot' ORDER BY tag ASC");

        int i = 0;
        while (rs1.next()) {
            tags[i] = rs1.getString(1);
            i++;
        }

        // iterate over tags[] inserting NSAF columns where appropriate
        for (int j = 1; j < tags.length; j++) {
            i = j - 1;

            if (Globals.byGene) {
                query = "ALTER TABLE geneResults "
                        + "  ADD COLUMN " + tags[i] + "_totNSAF DOUBLE "
                        + "BEFORE " + tags[i] + "_numSpecsUniq ";
                stmt.executeUpdate(query);

                query = "ALTER TABLE geneResults "
                        + "  ADD COLUMN " + tags[i] + "_uniqNSAF DOUBLE "
                        + "BEFORE " + tags[i] + "_numSpecsAdj ";
                stmt.executeUpdate(query);

                query = "UPDATE geneResults "
                        + "  SET      " + tags[i] + "_totNSAF = ( "
                        + "    SELECT " + tags[i] + "_totNSAF "
                        + "    FROM nsaf "
                        + "    WHERE nsaf.geneid = geneResults.geneid "
                        + ")";
                stmt.executeUpdate(query);

                query = "UPDATE geneResults "
                        + "  SET      " + tags[i] + "_uniqNSAF = ( "
                        + "    SELECT " + tags[i] + "_uniqNSAF "
                        + "    FROM nsaf "
                        + "    WHERE nsaf.geneid = geneResults.geneid "
                        + ")";
                stmt.executeUpdate(query);

                query = "ALTER TABLE geneResults "
                        + "  ADD COLUMN " + tags[i] + "_adjNSAF DOUBLE "
                        + "BEFORE " + tags[i] + "_numPepsTot ";
                stmt.executeUpdate(query);

                query = "UPDATE geneResults "
                        + "  SET      " + tags[i] + "_adjNSAF = ( "
                        + "    SELECT " + tags[i] + "_adjNSAF "
                        + "    FROM nsaf "
                        + "    WHERE nsaf.geneid = geneResults.geneid "
                        + ")";
                stmt.executeUpdate(query);
            } else {  //protein results
                query = "ALTER TABLE results "
                        + "  ADD COLUMN " + tags[i] + "_totNSAF DOUBLE "
                        + "BEFORE " + tags[i] + "_numSpecsUniq ";
                stmt.executeUpdate(query);

                query = "ALTER TABLE results "
                        + "  ADD COLUMN " + tags[i] + "_uniqNSAF DOUBLE "
                        + "BEFORE " + tags[i] + "_numSpecsAdj ";
                stmt.executeUpdate(query);

                query = "UPDATE results "
                        + "  SET      " + tags[i] + "_totNSAF = ( "
                        + "    SELECT " + tags[i] + "_totNSAF "
                        + "    FROM nsaf "
                        + "    WHERE nsaf.protid = results.protid "
                        + ")";
                stmt.executeUpdate(query);

                query = "UPDATE results "
                        + "  SET      " + tags[i] + "_uniqNSAF = ( "
                        + "    SELECT " + tags[i] + "_uniqNSAF "
                        + "    FROM nsaf "
                        + "    WHERE nsaf.protid = results.protid "
                        + ")";
                stmt.executeUpdate(query);

                query = "ALTER TABLE results "
                        + "  ADD COLUMN " + tags[i] + "_adjNSAF DOUBLE "
                        + "BEFORE " + tags[j] + "_id ";
                stmt.executeUpdate(query);

                query = "UPDATE results "
                        + "  SET      " + tags[i] + "_adjNSAF = ( "
                        + "    SELECT " + tags[i] + "_adjNSAF "
                        + "    FROM nsaf "
                        + "    WHERE nsaf.protid = results.protid "
                        + ")";
                stmt.executeUpdate(query);

            } // end protein results

        } //end for loop


        /*
         * the last experimental tag never gets added to the results table
         * by the above for loop, so we add it here manually
         */
        String tag = tags[(N - 1)]; // the last tag

        if (Globals.byGene) {

            stmt.executeUpdate("ALTER TABLE geneResults ADD COLUMN " + tag + "_totNSAF DOUBLE BEFORE " + tag + "_numSpecsUniq ");
            stmt.executeUpdate("ALTER TABLE geneResults ADD COLUMN " + tag + "_uniqNSAF DOUBLE BEFORE " + tag + "_numSpecsAdj ");
            stmt.executeUpdate("ALTER TABLE geneResults ADD COLUMN " + tag + "_adjNSAF DOUBLE BEFORE " + tag + "_numPepsTot ");

            query = "UPDATE geneResults "
                    + "  SET      " + tag + "_totNSAF = ( "
                    + "    SELECT " + tag + "_totNSAF "
                    + "    FROM nsaf "
                    + "    WHERE nsaf.geneid = geneResults.geneid "
                    + ")";
            stmt.executeUpdate(query);

            query = "UPDATE geneResults "
                    + "  SET      " + tag + "_uniqNSAF = ( "
                    + "    SELECT " + tag + "_uniqNSAF "
                    + "    FROM nsaf "
                    + "    WHERE nsaf.geneid = geneResults.geneid "
                    + ")";
            stmt.executeUpdate(query);

            query = "UPDATE geneResults "
                    + "  SET      " + tag + "_adjNSAF = ( "
                    + "    SELECT " + tag + "_adjNSAF "
                    + "    FROM nsaf "
                    + "    WHERE nsaf.geneid = geneResults.geneid "
                    + ")";
            stmt.executeUpdate(query);

        } else {

            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_totNSAF DOUBLE BEFORE " + tag + "_numSpecsUniq ");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_uniqNSAF DOUBLE BEFORE " + tag + "_numSpecsAdj ");
            stmt.executeUpdate("ALTER TABLE results ADD COLUMN " + tag + "_adjNSAF DOUBLE ");

            query = "UPDATE results "
                    + "  SET      " + tag + "_totNSAF = ( "
                    + "    SELECT " + tag + "_totNSAF "
                    + "    FROM nsaf "
                    + "    WHERE nsaf.protid = results.protid "
                    + ")";
            stmt.executeUpdate(query);

            query = "UPDATE results "
                    + "  SET      " + tag + "_uniqNSAF = ( "
                    + "    SELECT " + tag + "_uniqNSAF "
                    + "    FROM nsaf "
                    + "    WHERE nsaf.protid = results.protid "
                    + ")";
            stmt.executeUpdate(query);

            query = "UPDATE results "
                    + "  SET      " + tag + "_adjNSAF = ( "
                    + "    SELECT " + tag + "_adjNSAF "
                    + "    FROM nsaf "
                    + "    WHERE nsaf.protid = results.protid "
                    + ")";
            stmt.executeUpdate(query);
        }

    }

    /**
     * Function returns the gene id for the given protein id from the gene2prot
     * table.
     *
     * @param conn
     * @param protid
     * @return
     * @throws java.sql.SQLException
     */
    public String getGeneId(Connection conn, String protid) throws SQLException {
        String ret = "";

        try (Statement stmt = conn.createStatement()) {
            if (protid.startsWith(decoyTag)) {
                return "DECOY";
            }
            String query = "SELECT geneid FROM gene2prot WHERE protid = '" + protid + "' ";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                ret = rs.getString(1);
            } else {
                ret = "";
            }
        }

        return ret;

    }

    /**
     * Function returns the length of the given protein ID.
     *
     * @param protid
     * @return
     */
    public int getProtLen(String protid) {
        int ret = 0;

        if (Globals.fastaFile == null || Globals.fastaFile.isEmpty()) {
            ret = 0;
        } else {
            if (Globals.protLen.containsKey(protid)) {
                ret = Globals.protLen.get(protid);
            }
        }

        return ret;
    }

    /**
     * Function appends all protein IDs associated with a COMBINED file group-id
     * to the final results table. The lines repeat and the only difference
     * between them is the repProtId field.
     *
     * @param conn
     * @param out
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    public void addExtraProteins(Connection conn, Appendable out) throws SQLException, IOException {
        Statement stmt = conn.createStatement();
        Statement stmt2 = conn.createStatement();
        Statement stmt3 = conn.createStatement();
        Statement stmt4 = conn.createStatement();
        String query;
        ResultSet rs, rs2, rs3;
        ResultSetMetaData rsmd;

        if (out != null) {
            out.append("\nAppending additional protein identifiers to final output\n");
        }

        // create verbose results table
        query = "CREATE CACHED TABLE v_results AS ( "
                + "  SELECT * FROM results "
                + ") WITH DATA ";
        stmt.executeUpdate(query);

        stmt.executeUpdate("CREATE INDEX vr_idx1 ON v_results(protid)");
        stmt.executeUpdate("CREATE INDEX vr_idx2 ON v_results(ALL_id)");

        query = "SELECT DISTINCT ALL_id, protId "
                + "FROM results "
                + "ORDER BY ALL_Id ";
        rs = stmt.executeQuery(query);

        String all_groupid, all_sib;
        String geneId = null;

        while (rs.next()) {
            String all_id = rs.getString(1);
            String repProtId = rs.getString(2);

            String x[] = all_id.split("-");
            all_groupid = x[0];
            all_sib = x[1];

            query = "SELECT DISTINCT protid, defline "
                    + "FROM combined "
                    + "WHERE groupid = " + all_groupid + " "
                    + "AND siblingGroup = '" + all_sib + "' "
                    + "AND protId != '" + repProtId + "' ";
            rs2 = stmt2.executeQuery(query);

            while (rs2.next()) {
                String curId = rs2.getString(1);
                String curDefline = rs2.getString(2);

                if (Globals.gene2protFile != null) {
                    geneId = getGeneId(conn, curId);
                }

                int protLen = getProtLen(curId);

                query = "SELECT * FROM v_results WHERE protId = '" + repProtId + "'";
                rs3 = stmt3.executeQuery(query);
                rsmd = rs3.getMetaData();
                rs3.next();

                int Ncols = rsmd.getColumnCount();
                query = "INSERT INTO v_results VALUES ( '" + repProtId + ":::" + curId + "', ";
                for (int i = 2; i <= Ncols; i++) {

                    if (rsmd.getColumnName(i).equals("PROTLEN")) {
                        query += Integer.toString(protLen);
                    } else if (rsmd.getColumnName(i).equals("DEFLINE")) {
                        query += "'" + curDefline + "'";
                    } else if (rsmd.getColumnName(i).equals("GENEID")) {
                        query += "'" + geneId + "'";
                    } else {

                        int colType = rsmd.getColumnType(i);

                        switch (colType) {
                            case Types.INTEGER:
                                query += Integer.toString(rs3.getInt(i));
                                break;
                            case Types.VARCHAR:
                                query += "'" + rs3.getString(i) + "'";
                                break;
                            default:
                                query += Double.toString(rs3.getDouble(i));
                                break;
                        }
                    }

                    if (i != Ncols) {
                        query += ", ";
                    } else {
                        query += " )";
                    }
                }

                try {
                    stmt4.executeUpdate(query);
                } catch (SQLException e) {
                    System.err.print("\nError caused by:\n" + query + "\n\n");
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Function generates a peptide-level output file for users who need it.
     *
     * @param conn
     * @param out
     * @throws SQLException
     * @throws java.io.IOException
     */
    public void peptideLevelResults(Connection conn, Appendable out) throws SQLException, IOException {
        Statement stmt = conn.createStatement();
        Statement stmt2 = conn.createStatement();
        Statement stmt3 = conn.createStatement();
        PreparedStatement prep = null;
        ResultSet rs = null;
        ResultSet rs2 = null;
        ResultSetMetaData rsmd = null;
        int Ncols = 0;
        int i = 0;

        String query = null;

        if (out != null) {
            out.append("\nWriting peptide-level summary file to disk.\n");
        }

        // create initial peptide table
        query = "CREATE CACHED TABLE pepResults ("
                + "  modPep VARCHAR(250), "
                + "  charge INT, ";

        rs = stmt.executeQuery("SELECT COUNT(DISTINCT tag) FROM srcFileTags WHERE fileType = 'pep'");
        rs.next();
        Ncols = rs.getInt(1) - 1;
        i = 0;
        rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags");
        while (rs.next()) {
            String tag = rs.getString(1);

            query += " " + tag + "_maxProb DOUBLE DEFAULT 0, "
                    + " " + tag + "_nspecs INT DEFAULT 0 ";
            if (i < Ncols) {
                query += ", ";
            }
            i++;
        }
        query += ") ";
        stmt.executeUpdate(query);

        // populate the table
        query = "INSERT INTO pepResults (modPep, charge) VALUES ( ?, ? )";
        prep = conn.prepareStatement(query);

        query = "SELECT DISTINCT modPeptide, charge FROM pepXML ";
        rs = stmt.executeQuery(query);

        while (rs.next()) {
            String modPep = rs.getString(1);
            int z = rs.getInt(2);
            prep.setString(1, modPep);
            prep.setInt(2, z);
            prep.addBatch();
        }
        conn.setAutoCommit(false);
        prep.executeBatch();
        conn.setAutoCommit(true);

        rs = stmt.executeQuery("SELECT DISTINCT tag FROM srcFileTags");
        while (rs.next()) {
            String tag = rs.getString(1);

            if (out != null) {
                out.append("Adding " + tag + " peptides\n");
            }

            query = "SELECT modPeptide, charge, MAX(iniProb) as maxProb, COUNT(DISTINCT specId) as nspecs "
                    + "FROM pepXML WHERE tag = '" + tag + "'"
                    + "GROUP BY modPeptide, charge ";

            rs2 = stmt2.executeQuery(query);
            while (rs2.next()) {
                String modPep = rs2.getString(1);
                int z = rs2.getInt(2);
                int nspecs = rs2.getInt(4);
                double prob = rs2.getDouble(3);

                query = "UPDATE pepResults SET "
                        + tag + "_nspecs = " + nspecs + " "
                        + "WHERE modPep = '" + modPep + "' "
                        + "AND charge = " + z + " ";

                stmt3.executeUpdate(query);

                query = "UPDATE pepResults SET "
                        + tag + "_maxProb = " + prob + " "
                        + "WHERE modPep = '" + modPep + "' "
                        + "AND charge = " + z + " ";

                stmt3.executeUpdate(query);

            }
        }

        // now write this table to disk
        BufferedWriter outBuf = null;
        try {
            outBuf = new BufferedWriter(new FileWriter(Globals.outputFilePath));

            rs = stmt.executeQuery("SELECT * FROM pepResults");
            rsmd = rs.getMetaData();
            Ncols = rsmd.getColumnCount();

            for (i = 1; i < Ncols; i++) {
                String c = rsmd.getColumnName(i);
                outBuf.append(c).append("\t");
            }
            outBuf.append(rsmd.getColumnName(Ncols)).append("\n");

            // write the data
            while (rs.next()) {
                for (i = 1; i <= Ncols; i++) {
                    int colType = rsmd.getColumnType(i);
                    switch (colType) {
                        case Types.INTEGER:
                            outBuf.append(Integer.toString(rs.getInt(i)));
                            break;
                        case Types.DOUBLE:
                            outBuf.append(Double.toString(rs.getDouble(i)));
                            break;
                        case Types.VARCHAR:
                            outBuf.append(rs.getString(i));
                            break;
                    }
                    if (i != Ncols) {
                        outBuf.append("\t");
                    } else {
                        outBuf.append("\n");
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (outBuf != null) {
                outBuf.flush();
                outBuf.close();
            }
        }

        stmt3.close();
        rs.close();
        prep.close();
        if (rs2 != null) {
            rs2.close();
        }
    }

}
