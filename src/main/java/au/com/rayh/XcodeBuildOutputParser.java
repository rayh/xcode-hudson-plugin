/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.com.rayh;

import au.com.rayh.report.TestCase;
import au.com.rayh.report.TestFailure;
import au.com.rayh.report.TestSuite;
import hudson.FilePath;
import hudson.model.BuildListener;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

/**
 *
 * @author ray
 */
public class XCodeBuildOutputParser {
    private static Pattern START_SUITE = Pattern.compile("Test Suite '(\\S+)'.*started at\\s+(.*)");
    private static Pattern END_SUITE = Pattern.compile("Test Suite '(\\S+)'.*finished at\\s+(.*).");
    private static Pattern START_TESTCASE = Pattern.compile("Test Case '-\\[\\S+\\s+(\\S+)\\]' started.");
    private static Pattern END_TESTCASE = Pattern.compile("Test Case '-\\[\\S+\\s+(\\S+)\\]' passed \\((.*) seconds\\)");
    private static Pattern ERROR_TESTCASE = Pattern.compile("(.*): error: -\\[(\\S+) (\\S+)\\] : (.*)");
    private static Pattern FAILED_TESTCASE = Pattern.compile("Test Case '-\\[\\S+ (\\S+)\\]' failed \\((\\S+) seconds\\)");
    private static Pattern FAILED_WITH_EXIT_CODE = Pattern.compile("failed with exit code (\\d+)");

    FilePath testReportsDir;
    OutputStream captureOutputStream;
    BuildListener buildListener;

    int exitCode;
    TestSuite currentTestSuite;
    TestCase currentTestCase;

    public XCodeBuildOutputParser(FilePath workspace, BuildListener buildListener) throws IOException, InterruptedException {
        this.buildListener = buildListener;
        this.captureOutputStream = new LineBasedFilterOutputStream();

        testReportsDir = workspace.child("test-reports");
        testReportsDir.mkdirs();
    }

    public class LineBasedFilterOutputStream extends FilterOutputStream {
        StringBuilder buffer = new StringBuilder();

        public LineBasedFilterOutputStream() {
            super(buildListener.getLogger());
        }

        public void write(int b) throws IOException {
            if((char)b == '\n') {
                try {
                    handleLine(buffer.toString());
                    buffer = new StringBuilder();
                } catch(Exception e) {  // Very fugly
                    throw new IOException(e);
                }
            } else {
                buffer.append((char)b);
            }
            
            super.write(b);
        }
    }

    private void requireTestSuite() {
        if(currentTestSuite==null) {
            throw new RuntimeException("Log statements out of sync: current test suite was null");
        }
    }

    private void requireTestSuite(String name) {
        requireTestSuite();
        if(!currentTestSuite.getName().equals(name)) {
            throw new RuntimeException("Log statements out of sync: current test suite was '" + currentTestSuite.getName() + "' and not '" + name + "'");
        }
    }

    private void requireTestCase(String name) {
        if(currentTestCase==null) {
            throw new RuntimeException("Log statements out of sync: current test case was null");
        } else if(!currentTestCase.getName().equals(name)) {
            throw new RuntimeException("Log statements out of sync: current test case was '" + currentTestCase.getName() + "'");
        }
    }

    private void writeTestReport() throws IOException, InterruptedException, JAXBException {
        OutputStream testReportOutputStream = testReportsDir.child("TEST-" + currentTestSuite.getName() + ".xml").write();
        JAXBContext jaxbContext = JAXBContext.newInstance(TestSuite.class, TestCase.class, TestFailure.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.marshal(currentTestSuite, testReportOutputStream);

    }

    protected void handleLine(String line) throws ParseException, IOException, InterruptedException, JAXBException {
        Matcher m = START_SUITE.matcher(line);
        if(m.matches()) {
            currentTestSuite = new TestSuite("hostname", m.group(1), DateFormat.getInstance().parse(m.group(2)));
            return;
        }

        m = END_SUITE.matcher(line);
        if(m.matches()) {
            requireTestSuite(m.group(1));
            currentTestSuite.setEndTime(DateFormat.getInstance().parse(m.group(2)));

            writeTestReport();
            
            currentTestSuite = null;
            return;
        }

        m = START_TESTCASE.matcher(line);
        if(m.matches()) {
            currentTestCase = new TestCase(m.group(1));
            return;
        }

        m = END_TESTCASE.matcher(line);
        if(m.matches()) {
            requireTestSuite();
            requireTestCase(m.group(1));

            currentTestCase.setTime(Float.valueOf(m.group(2)));
            currentTestSuite.getTestCases().add(currentTestCase);
            currentTestSuite.tests+=1;
            currentTestCase = null;
            return;
        }

        m = ERROR_TESTCASE.matcher(line);
        if(m.matches()) {

            String errorLocation = m.group(1);
            String testSuite = m.group(2);
            String testCase = m.group(3);
            String errorMessage = m.group(4);

            requireTestSuite(testSuite);
            requireTestCase(testCase);

            TestFailure failure = new TestFailure(errorMessage, errorLocation);
            currentTestCase.getFailures().add(failure);
            currentTestSuite.tests+=1;
            currentTestSuite.errors+=1;
            return;
        } 
        
        m = FAILED_TESTCASE.matcher(line);
        if(m.matches()) {
            requireTestSuite();
            requireTestCase(m.group(1));
            currentTestSuite.tests+=1;
            currentTestSuite.failures+=1;
            currentTestCase.setTime(Float.valueOf(m.group(2)));
            return;
        }

        m = FAILED_WITH_EXIT_CODE.matcher(line);
        if(m.matches()) {
            exitCode = Integer.valueOf(m.group(1));
            return;
        }

        if(line.matches("BUILD FAILED")) {
            exitCode = -1;
        }
    }

    public OutputStream getOutputStream() {
        return captureOutputStream;
    }

    public int getExitCode() {
        return exitCode;
    }
}
