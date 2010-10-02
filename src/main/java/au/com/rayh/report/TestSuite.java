/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.com.rayh.report;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement
public class TestSuite {
    @XmlAttribute public int errors;
    @XmlAttribute public int failures;
    @XmlAttribute String hostname;
    @XmlAttribute String name;
    @XmlAttribute public int tests;
    @XmlAttribute float time;
    @XmlAttribute(name="timestamp") Date endTime;
    @XmlElement(name="testcase") List<TestCase> testcases = new ArrayList<TestCase>();
    @XmlTransient Date startTime;
    
    public TestSuite(String hostname, String name, Date startTime) {
        this.hostname = hostname;
        this.name = name;
        this.startTime = startTime;
    }

    public String getName() {
        return name;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
        this.time = (endTime.getTime() - startTime.getTime())/1000;
    }

    public List<TestCase> getTestCases() {
        return testcases;
    }

}
