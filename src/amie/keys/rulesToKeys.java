/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amie.keys;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author Danai
 */
public class rulesToKeys {

    public static void main(String[] args) throws IOException {

        /*SET THE FILE AND THE NUMBER OF EXCEPTIONS */
        String triplesFile = "/Users/Danai/Dropbox/postdoc/test2.rdf";
       String rulesFile = "/Users/Danai/NetBeansProjects/AMIE/scripts/sakey/test2.rdf/amieOutputFile";
      //  String triplesFile = args[0];
       // String rulesFile = args[1];    	
        rulesToKeys(triplesFile, rulesFile);
        //exceptionsValidator(triplesFile, "wasbornininv");
    }

    public static void rulesToKeys(String triplesFile, String rulesFile) throws IOException {
        BufferedReader br1 = new BufferedReader(new FileReader(triplesFile));
        String line1 = null;
        HashSet<String> properties = new HashSet<>();
        while ((line1 = br1.readLine()) != null) {
            String tripleTable[] = line1.split("	");
            String property = tripleTable[1].toLowerCase();
            properties.add(property);
        }
        br1.close();
        BufferedReader br2 = new BufferedReader(new FileReader(rulesFile));
        String line2 = null;
        int counter = 0;
        System.out.println("CONDITION	KEY	CONFIDENCE	SUPPORT");

        while ((line2 = br2.readLine()) != null) {
            HashSet<String> key = new HashSet<>();
            HashMap<String, String> conditions = new HashMap<>();
            //System.out.println("rule:" + line2);
            if (line2.contains("> <")) {
                String instanceTable[] = line2.split("> <");
                // subject = instanceTable[0].split("<")[1];

            }
         //   System.out.println("counter:"+counter);

            // System.out.println("line2:"+line2);
            String[] elements = line2.split("=>")[0].split("  ");
            // System.out.println("elements:"+line2.split("=>")[1]);
            String[] measures = line2.split("=>")[1].split("	");

            String confidence = measures[3];
            //System.out.println("confidence:"+confidence);
            String support = measures[4];
            // System.out.println("support:"+support);
            int numberOfTriples = elements.length / 3;
            for (int tripleNumber = 0; tripleNumber < numberOfTriples; tripleNumber++) {
                String object = elements[tripleNumber * 3 + 2];
                String property = elements[tripleNumber * 3 + 1];
                if (object.contains("?")) {
                    key.add(property);
                } else {
                    conditions.put(property, object);
                }

            }
            counter++;
            //System.out.println("rule:" + line2);
            System.out.println(conditions + "	" + key + "	" + confidence + "	" + support);
        }
        br2.close();
        // System.out.println("counter:"+counter);
    }

    public static void exceptionsValidator(String triplesFile, String givenProperty) throws IOException {
        BufferedReader br1 = new BufferedReader(new FileReader(triplesFile));
        String line1 = null;
        System.out.println("given:" + givenProperty);
        HashMap<String, String> subjectObject = new HashMap<>();
        HashSet<String> properties = new HashSet<>();
        while ((line1 = br1.readLine()) != null) {
            if (line1.contains("> <")) {
                String tripleTable[] = line1.split("> <");
                String property = tripleTable[1].toLowerCase();
                //System.out.println("property:"+property);
                if (givenProperty.equals(property)) {
                    System.out.println("line:" + line1);
                    if (!subjectObject.containsKey(tripleTable[2].toLowerCase())) {
                        subjectObject.put(tripleTable[2].split(">")[1].toLowerCase(), tripleTable[0].split("<")[1].toLowerCase());
                    } else {
                        System.out.println("edo => " + tripleTable[2].toLowerCase());
                    }
                }
            } else {
                String tripleTable[] = line1.split("	");
                //System.out.println("line:"+line1);
                String property = tripleTable[1].toLowerCase();
                //System.out.println("property:"+property);
                if (givenProperty.equals(property)) {
                   // System.out.println("line:" + line1);
                    if (!subjectObject.containsKey(tripleTable[2].toLowerCase())) {
                        subjectObject.put(tripleTable[2].toLowerCase(), tripleTable[0].toLowerCase());
                    } else {
                        System.out.println("edo => " + tripleTable[2].toLowerCase());
                    }
                }

            }
        }
        br1.close();
    }

}
