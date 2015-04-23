/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: coop
 * 21 Apr 2015
 */
package pt.lsts.neptus.plugins.preflight.utils;


import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import pt.lsts.neptus.plugins.preflight.PreflightCheck;
import pt.lsts.neptus.plugins.preflight.PreflightPanel;
import pt.lsts.neptus.plugins.preflight.PreflightSection;
import pt.lsts.neptus.plugins.preflight.check.automated.CheckHomeRef;


/**
 * @author tsmarques
 *
 */
public class PreflightPanelBuilder {
    private static final String XML_PATH = "plugins-dev/preflight/pt/lsts/neptus/plugins/preflight/etc/";
    
    public PreflightPanelBuilder() {}
    
    public PreflightPanel buildPanel(String vehicle) {
        try {
            File fXmlFile = new File(getPanelXmlFile(vehicle));
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            
            doc.getDocumentElement().normalize();
            
            NodeList nList = doc.getElementsByTagName("section");
            
            PreflightPanel panel = new PreflightPanel();
            for (int i= 0; i < nList.getLength(); i++) {               
                PreflightSection section = buildSection(nList.item(i));
                if(section != null)
                    panel.addNewSection(section);
            }
            
            return panel;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PreflightPanel();
    }
    
    
    
    private PreflightSection buildSection(Node section) {
        
        Element sectionElement = (Element) section;
        String clazz = sectionElement.getAttribute("id");
        
//        if(!clazz.equals("Planning"))
//            return null;
        
        PreflightSection prefSection = new PreflightSection(clazz);
        NodeList checks = sectionElement.getElementsByTagName("check");
        for(int i = 0; i < checks.getLength(); i++) {
            PreflightCheck check = buildCheck(checks.item(i));
            if(check != null)
                prefSection.addNewCheckItem(check);
          
        }
        return prefSection;
    }
    
    
    
    public PreflightCheck buildCheck(Node checkXml) {
        Element elemCheck = (Element) checkXml;
        String clazz = elemCheck.getAttribute("class");
        
//        if(!clazz.equals("pt.lsts.neptus.plugins.preflight.check.automated.CheckPlan"))
//            return null;
        
          try {             
              if(!classXmlWellDefined())
                  return null;
              
              System.out.println("Preflight loaded: " + clazz);
              Class<?> c = Class.forName(clazz);
              Constructor<?> cons = c.getConstructors()[0];
              Object[] args = getConstructorArguments(elemCheck);

              return (PreflightCheck)cons.newInstance(args);
          }
          catch (Exception e) {
              e.printStackTrace();
          }            
        return null; /**/
    }
    
    
    
    /* If no arguments, returns an empty array */
    private Object[] getConstructorArguments(Element clazz) {
        NodeList xmlArgs = clazz.getElementsByTagName("arg");
        int nArgs = xmlArgs.getLength();
        ArrayList<Object> args;
        
        if(nArgs != 0) {
            args = new ArrayList<>();
            for(int i = 0; i < nArgs; i++) {
                Element arg = (Element) xmlArgs.item(i);
                String argValueStr = arg.getTextContent();
                String argType = arg.getAttribute("type");
                
                if(argType.equals("integer"))
                    args.add(Integer.parseInt(argValueStr));
                else if(argType.equals("double"))
                    args.add(Double.parseDouble(argValueStr));
                else if(argType.equals("float"))
                    args.add(Float.parseFloat(argValueStr));
                else if(argType.equals("bool"))
                    args.add(Boolean.parseBoolean(argValueStr));
                else
                    args.add(argValueStr);             
            }
            return args.toArray();
        }
        else
            return new Object[0];
    }
    
    
    
    private String getPanelXmlFile(String vehicle) {
        return XML_PATH + "x8.xml"; /* for now */
    }
          
    /* Returns if the number of "arguments", of the class in xml format, 
     * is the same as the one on the class constructor
    /*TODO implement */
    private boolean classXmlWellDefined() {
        return true;
    }
    
    public static void main(String [] args) {
        PreflightPanelBuilder builder = new PreflightPanelBuilder();
        builder.buildPanel("x8-01");
        
//        try {
//        Class<?> c = Class.forName("pt.lsts.neptus.plugins.preflight.check.PreflightInfo");
//        Class<?>[] params = {String.class, String.class, int.class};
//        Constructor<?> cons = c.getConstructor(params);
//        Constructor<?> cons = c.getConstructors()[0];
//        
//        
//        cons.newInstance("BLA", "COISO");
//            
//            Class<?> c = Class.forName("java.lang.Integer");
//            
//            System.out.println(c.getClass().toString());
//        } catch(Exception e) {
//            e.printStackTrace();
//        }
        
    }
}

