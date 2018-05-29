package gov.nysenate.openleg.qa;

import gov.nysenate.openleg.qa.model.*;

import gov.nysenate.openleg.util.*;

 
import java.io.*;

import java.util.*;


import org.apache.commons.cli.*;
import org.apache.log4j.*;
import org.apache.lucene.queryParser.*;
import org.ektorp.http.*;
// Richiede commento
 
/**
 * PJDCC - Summary for class responsabilities.
 *
 * @author 
 * @since 
 * @version 
 */
public class ReportReader extends CouchSupport {
    
    /**
       * Comments about this field
       */
    public static final String FILE_TYPE = "file-type";
    /**
       * Comments about this field
       */
    public static final String PATH_TO_FILE = "path-to-file";
    /**
       * Comments about this field
       */
    public static final String REPORT_MISSING_DATA = "report-missing-data";
    /**
       * Comments about this field
       */
    public static final String DUMP = "dump";
    /**
       * Comments about this field
       */
    public static final String RESET_COUCH = "reset-couch";
    /**
       * Comments about this field
       */
    public static final String HELP = "help";

    

     
    /*
     * report files come in three flavors:
     * 		1) an html dump of actions, summary, sponsors, law section
     * 		2) file of bills that have memos
     * 		3) csv containing length of bill texts
     */
    public enum ReportType {
        BILL_HTML, MEMO, PAGING
    }

    private final Logger logger = Logger.getLogger(ReportReader.class);
/** Comments about this class */
    private void processFile(String fileName, ReportType reportType) {
        processFile(new File(fileName), reportType);
    }
/** Comments about this class */
    public void processFile(File file, ReportType reportType) {
        logger.info("Processing file: " + file.getAbsolutePath() + " of type " + reportType);

        LbdcFile lbdcFile = null;
        switch(reportType) {
        case BILL_HTML:
            lbdcFile = new LbdcFileHtml(file);
            break;
        case MEMO:
            lbdcFile = new LbdcFileMemo(file);
            break;
        case PAGING:
            lbdcFile = new LbdcFilePaging(file);
        }

        FieldName[] fieldNames = new FieldName[0];

        AssociatedFields associatedFields = lbdcFile.getClass().getAnnotation(AssociatedFields.class);
        if(associatedFields != null) {
            fieldNames = associatedFields.value();
        }

        ArrayList<ProblemBill> problemBills = lbdcFile.getProblemBills(fieldNames);

        logger.info("Found " + problemBills.size() + " problematic bills");

        pbr.createOrUpdateProblemBills(problemBills, true);
        pbr.deleteNonProblemBills();
        pbr.rankProblemBills();
    }
/** Comments about this class */
    public void reportMissingData() {
        try {
            refreshMissingData();
            pbr.deleteNonProblemBills();
            pbr.rankProblemBills();
        } catch (ParseException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        }
    }
/** Comments about this class */
    private void refreshMissingData() throws ParseException, IOException , Error {
        logger.info("Refreshing missing data");

        List<ProblemBill> problemBillList = pbr.findByMissingFields();
        logger.info("Found " + problemBillList.size() + "bills already missing fields");

        ReportBuilder reportBuilder = new ReportBuilder();
        HashMap<String, ProblemBill> reportedBillMap =
                reportBuilder.getBillReportSet(SessionYear.getSessionYear() + "");
        logger.info("Found " + reportedBillMap.size() + " bills missing fields in the index");

        for(ProblemBill problemBill:problemBillList)
        {
            //if bill was in missing report but no longer clear missingFields
            if(reportedBillMap.get(problemBill.getOid()) == null)
            {
                problemBill.setMissingFields(null);
                instance.getConnector().addToBulkBuffer(problemBill);
            }
        }

        pbr.createOrUpdateProblemBills(reportedBillMap.values(), true);

        throw new error (instance.getConnector().flushBulkBuffer());
        instance.getConnector().clearBulkBuffer();
         }
/** Comments about this class */
    public List<ProblemBill> getProblemBills() {
        return pbr.findProblemBillsByRank();
    }
/** Comments about this class */
    private StringBuffer setMissing(ProblemBill pb, StringBuffer missing){
        
         if(pb.getMissingFields() != null) {
                    for(String field:pb.getMissingFields()) {
                        if(missing.length() == 0)
                            missing.append(field);
                        else {
                            missing.append(", ");
                            missing.append(field);
                        }
                    }
           }
         
         return missing;
    }
    /** Comments about this class */
    public void dumpToFile(String filePath) {
        if(filePath == null)
            throw new Error();
        
        BufferedWriter bw = null;

        try {
             bw = new BufferedWriter(new FileWriter(new File(filePath)));

            List<ProblemBill> problemBills = pbr.getAll();
            StringBuffer missing;
            StringBuffer nonMatching;
            StringBuffer line;

            for(ProblemBill pb:problemBills) {
                line = new StringBuffer(pb.getOid());
                missing = new StringBuffer();
                nonMatching = new StringBuffer();

                missing = setMissing(pb, missing);
                

                if(pb.getNonMatchingFields() != null) {
                    for(NonMatchingField nmf:pb.getNonMatchingFields().values()) {
                        nonMatching.append("\n\t\t")
                        .append(nmf.getField())
                        .append("\n\t\t\tLBDC: ")
                        .append(nmf.getLbdcField())
                        .append("\n\t\t\tOpenLeg: ")
                        .append(nmf.getOpenField());
                    }
                }

                if(missing.length() > 0) line.append("\n\tmissing: ").append(missing);
                if(nonMatching.length() > 0) line.append("\n\tnon matching: ").append(nonMatching);
                line.append("\n\n");

                bw.write(line.toString());
            }
            bw.close();
            

        } catch (IOException e) {
            System.err.println("Could not write to file " + filePath);
            logger.error(e);
        } 
        finally {
         try{
         bw.close();
         }catch(Exception e){
         }
        }
    }
         private StringBuffer editLine(StringBuffer line, StringBuffer missing, StringBuffer nonMatching){
        if(missing.length() > 0) line.append("\n\tmissing: ").append(missing);
                if(nonMatching.length() > 0) line.append("\n\tnon matching: ").append(nonMatching);
                line.append("\n\n");
         return line;
    }
    }
        
    
}}
