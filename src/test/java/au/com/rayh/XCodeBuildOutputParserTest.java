/*
 * The MIT License
 *
 * Copyright (c) 2011 Ray Yamamoto Hilton
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.com.rayh;

import au.com.rayh.report.TestCase;
import au.com.rayh.report.TestSuite;
import hudson.FilePath;
import hudson.console.ConsoleNote;
import hudson.model.TaskListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.Date;

import static org.junit.Assert.*;

/**
 *
 * @author ray
 */
public class XCodeBuildOutputParserTest {
    XCodeBuildOutputParser parser;

    @Before
    public void setUp() throws IOException, InterruptedException {
        parser = new XCodeBuildOutputParser(new FilePath(new File(".")), new TaskListener() {

            public PrintStream getLogger() {
                try {
                    return new PrintStream("test-output.txt");
                } catch(FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            public void annotate(ConsoleNote consoleNote) throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public void hyperlink(String s, String s1) throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PrintWriter error(String string) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PrintWriter error(String string, Object... os) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PrintWriter fatalError(String string) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            public PrintWriter fatalError(String string, Object... os) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        });
    }

    @After
    public void tearDown() {
    }

    @Test
    public void shouldIgnoreStartSuiteLineThatContainsFullPath() throws Exception {
        String line = "Test Suite '/Users/ray/Development/Projects/Java/xcodebuild-hudson-plugin/work/jobs/PBS Streamer/workspace/build/Debug-iphonesimulator/TestSuite.octest(Tests)' started at 2010-10-02 13:39:22 GMT 0000";
        parser.handleLine(line);
        assertNull(parser.currentTestSuite);
    }

    @Test
    public void shouldParseStartTestSuite() throws Exception {
        String line = "Test Suite 'PisClientTestCase' started at 2010-10-02 13:39:23 GMT 0000";
        parser.handleLine(line);
        assertNotNull(parser.currentTestSuite);
        assertEquals("PisClientTestCase", parser.currentTestSuite.getName());
        assertEquals(new Date(Date.UTC(110, 9, 2, 13, 39, 23)), parser.currentTestSuite.getStartTime());
    }

    @Test
    public void shouldParseEndTestSuite() throws Exception {
        parser.currentTestSuite = new TestSuite("host", "PisClientTestCase", new Date());
        String line = "Test Suite 'PisClientTestCase' finished at 2010-10-02 13:41:23 GMT 0000.";
        parser.handleLine(line);
        assertNull(parser.currentTestSuite);
        assertEquals(0, parser.exitCode);
    }

    @Test
    public void shouldParseStartTestCase() throws Exception {
        parser.currentTestSuite = new TestSuite("host", "PisClientTestCase", new Date());
        String line = "Test Case '-[PisClientTestCase testThatFails]' started.";
        parser.handleLine(line);
        assertNotNull(parser.currentTestCase);
        assertEquals("testThatFails", parser.currentTestCase.getName());
    }

    @Test
    public void shouldAddErrorToTestCase() throws Exception {
        parser.currentTestSuite = new TestSuite("host", "PisClientTestCase", new Date());
        parser.currentTestCase = new TestCase("PisClientTestCase", "testThatFails");
        String line = "/Users/ray/Development/Projects/Java/xcodebuild-hudson-plugin/work/jobs/PBS Streamer/workspace/PisClientTestCase.m:21: error: -[PisClientTestCase testThatFails] : \"((nil) != nil)\" should be true. This always fails";
        parser.handleLine(line);
        assertEquals(1, parser.currentTestCase.getFailures().size());
        assertEquals("/Users/ray/Development/Projects/Java/xcodebuild-hudson-plugin/work/jobs/PBS Streamer/workspace/PisClientTestCase.m:21", parser.currentTestCase.getFailures().get(0).getLocation());
        assertEquals("\"((nil) != nil)\" should be true. This always fails", parser.currentTestCase.getFailures().get(0).getMessage());
    }

    @Test
    public void shouldParsePassedTestCase() throws Exception {
        parser.currentTestSuite = new TestSuite("host", "PisClientTestCase", new Date());
        parser.currentTestCase = new TestCase("PisClientTestCase","testThatPasses");
        String line = "Test Case '-[PisClientTestCase testThatPasses]' passed (1.234 seconds).";
        parser.handleLine(line);
        assertNull(parser.currentTestCase);
        assertEquals(1, parser.currentTestSuite.getTestCases().size());
        assertEquals("testThatPasses", parser.currentTestSuite.getTestCases().get(0).getName());
        assertEquals(1.234f, parser.currentTestSuite.getTestCases().get(0).getTime(),0);
        assertEquals(1,parser.currentTestSuite.getTests());
        assertEquals(0,parser.currentTestSuite.getFailures());
    }

    @Test
    public void shouldParseFailedTestCase() throws Exception {
        parser.currentTestSuite = new TestSuite("host", "PisClientTestCase", new Date());
        parser.currentTestCase = new TestCase("PisClientTestCase","testThatFails");
        String line = "Test Case '-[PisClientTestCase testThatFails]' failed (1.234 seconds).";
        parser.handleLine(line);
        assertNull(parser.currentTestCase);
        assertEquals(1, parser.currentTestSuite.getTestCases().size());
        assertEquals("testThatFails", parser.currentTestSuite.getTestCases().get(0).getName());
        assertEquals(1.234f, parser.currentTestSuite.getTestCases().get(0).getTime(),0);
        assertEquals(1,parser.currentTestSuite.getTests());
        assertEquals(1,parser.currentTestSuite.getFailures());
    }
}