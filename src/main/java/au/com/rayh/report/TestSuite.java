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

@XmlRootElement(name="testsuite")
@XmlAccessorType(XmlAccessType.NONE)
public class TestSuite {
    @XmlAttribute
    int failures;
    
    @XmlAttribute
    String hostname;
    
    @XmlAttribute
    String name;
    
    @XmlAttribute
    int tests;
    
    @XmlAttribute
    float time;
    
    @XmlAttribute(name="timestamp")
    Date endTime;
    
    @XmlElement(name="testcase")
    List<TestCase> testcases = new ArrayList<TestCase>();

    @XmlTransient
    Date startTime;

    public TestSuite() {
    }
    
    public TestSuite(String hostname, String name, Date startTime) {
        this.hostname = hostname;
        this.name = name;
        this.startTime = startTime;
    }

    public int getFailures() {
        return failures;
    }

    public int getTests() {
        return tests;
    }

    public void addFailure() {
        failures+=1;
    }

    public void addTest() {
        tests+=1;
    }
    
    public String getName() {
        return name;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
        this.time = (endTime.getTime() - startTime.getTime())/1000;
    }

    public List<TestCase> getTestCases() {
        return testcases;
    }

}
