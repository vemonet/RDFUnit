package org.aksw.rdfunit.io.writer;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.rdfunit.model.impl.results.DatasetOverviewResults;
import org.aksw.rdfunit.model.interfaces.results.TestExecution;
import org.apache.jena.datatypes.xsd.XSDDateTime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * <p>Abstract JunitXMLResultsWriter class.</p>
 *
 * @author Martin Bruemmer
 *         Writes results in JUnit XML format
 * @since 11/14/13 1:04 PM
 * @version $Id: $Id
 */
public abstract class JunitXmlResultsWriter implements RdfWriter {
	protected final TestExecution testExecution;
	private final OutputStream outputStream;

    /**
     * <p>Constructor for JunitXMLResultsWriter.</p>
     *
     * @param outputStream a {@link java.io.OutputStream} object.
     */
    public JunitXmlResultsWriter(TestExecution testExecution, OutputStream outputStream) {
        super();
        this.testExecution = testExecution;
        this.outputStream = outputStream;
    }

    /**
     * <p>Constructor for JunitXMLResultsWriter.</p>
     *
     * @param filename a {@link java.lang.String} object.
     */
    public JunitXmlResultsWriter(TestExecution testExecution, String filename) {
        this(testExecution, RdfStreamWriter.getOutputStreamFromFilename(filename));
    }

    /** {@inheritDoc} */
    @Override
    public void write(QueryExecutionFactory qef) throws RdfWriterException {
  
        try {
            // TODO not efficient StringBuilder.toString().getBytes()
            outputStream.write(getHeader().toString().getBytes("UTF8"));
            outputStream.write(getTestExecutionStats().toString().getBytes("UTF8"));
            outputStream.write(getTestExecutionResults().toString().getBytes("UTF8"));
            outputStream.write(getFooter().toString().getBytes("UTF8"));
            outputStream.close();
        } catch (IOException e) {
            throw new RdfWriterException("Cannot write XML", e);
        }
    }


    protected abstract StringBuffer getResultsList() ;

    private StringBuffer getHeader() {
        StringBuffer header = new StringBuffer();
        header.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        return header;
    }

    private StringBuffer getFooter() {
        return new StringBuffer("</testsuite>");
    }
    
    private StringBuffer getTestExecutionStats() {
        StringBuffer stats = new StringBuffer();
        stats.append("<testsuite name=\"").append(testExecution.getTestExecutionUri()).append("\" ");
  
        DatasetOverviewResults dor = testExecution.getDatasetOverviewResults();
        stats.append("timestamp=\"").append(dor.getEndTime()).append("\" ");
        String length = testLength(dor.getStartTime(), dor.getEndTime());
        if(length!=null) {
        	stats.append("time=\"").append(length).append("\" ");
        }
        stats.append("tests=\"").append(dor.getTotalTests()).append("\" ");
        stats.append("failures=\"").append(dor.getFailedTests()).append("\" ");
        stats.append("errors=\"").append(dor.getTimeoutTests()+dor.getErrorTests()).append("\" ");
        stats.append("package=\"").append(testExecution.getTestedDatasetUri()).append("\"");
        stats.append(">\n");
        return stats;
    }
    
    private String testLength(XSDDateTime datetimeStart, XSDDateTime datetimeEnd) {
    	long diff = datetimeEnd.asCalendar().getTimeInMillis() - datetimeStart.asCalendar().getTimeInMillis();
    	return String.format("%02d:%02d:%02d", 
    		    TimeUnit.MILLISECONDS.toHours(diff),
    		    TimeUnit.MILLISECONDS.toMinutes(diff) - 
    		    TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diff)),
    		    TimeUnit.MILLISECONDS.toSeconds(diff) - 
    		    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff)));
    }

    private StringBuffer getTestExecutionResults() {
        StringBuffer results = new StringBuffer();
        results.append(getResultsList());
        return results;
    }
}