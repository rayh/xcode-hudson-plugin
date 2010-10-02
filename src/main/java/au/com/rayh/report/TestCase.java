/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.com.rayh.report;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlType
@XmlAccessorType(XmlAccessType.NONE)
public class TestCase {
    @XmlAttribute String classname;
    @XmlAttribute String name;
    @XmlAttribute float time;
    @XmlElement(name="failure") List<TestFailure> failures = new ArrayList<TestFailure>();

    public TestCase(String name) {
        this.classname = name;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public List<TestFailure> getFailures() {
        return failures;
    }
}
