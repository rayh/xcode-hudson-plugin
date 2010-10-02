package au.com.rayh.report;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType(name="testcase")
@XmlAccessorType(XmlAccessType.NONE)
public class TestCase {
    @XmlAttribute
    String classname;
    
    @XmlAttribute
    String name;
    
    @XmlAttribute
    float time;
    
    @XmlElement(name="failure")
    List<TestFailure> failures = new ArrayList<TestFailure>();

    public TestCase() {
    }

    public TestCase(String classname, String name) {
        this.classname = classname;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public float getTime() {
        return time;
    }

    public List<TestFailure> getFailures() {
        return failures;
    }
}
