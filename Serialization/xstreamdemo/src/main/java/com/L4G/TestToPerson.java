package com.L4G;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class TestToPerson {
    public static void main(String[] args) throws FileNotFoundException {
        FileInputStream xml = new FileInputStream("person.xml");
        XStream xStream = new XStream(new DomDriver());
        Person person = (Person)xStream.fromXML(xml);
        System.out.println(person);
    }
}
