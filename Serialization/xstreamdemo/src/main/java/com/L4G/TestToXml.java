package com.L4G;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

class TestToXml {
    public static void main(String [] args) {
        Person person = new Person();
        person.setAge(18);
        person.setName("yds");
        XStream xstream = new XStream(new DomDriver());
        String xml = xstream.toXML(person);
        System.out.println(xml);
    }
}
