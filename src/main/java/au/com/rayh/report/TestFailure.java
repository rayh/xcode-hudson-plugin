package au.com.rayh.report;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

@XmlType(name="failure")
@XmlAccessorType(XmlAccessType.NONE)
public class TestFailure {
    @XmlAttribute
    String message;
    
    @XmlAttribute
    String type = "Failure";
    
    @XmlValue
    String location;

    public TestFailure() {
    }

    public TestFailure(String message, String location) {
        this.message = message;
        this.location = location;
    }

    public String getMessage() {
        return message;
    }

    public String getLocation() {
        return location;
    }
    
}
