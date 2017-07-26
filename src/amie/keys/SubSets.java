package amie.keys;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javatools.filehandlers.FileLines;

/** Prints all subsets of items in comma separated lines*/
public class SubSets {

  /** Registers those that we already printed*/
  protected static Set<String> alreadyPrinted = new HashSet<>();

  /** where to write*/
  protected static Writer out = new OutputStreamWriter(System.out);

  /** Prints a subset only if it has not already been printed (for a different line)*/
  public static void printSubSet(String subset) throws IOException {
    if (alreadyPrinted.add(subset)) out.write(subset + "\n");
  }

  /** Prints all subsets of the elements in "properties" to the right of position "start", prefixed by "collectedSoFar"
   * @throws IOException */
  public static void printSubSets(List<String> properties, int start, String collectedSoFar) throws IOException {
    printSubSet(collectedSoFar);
    for (int i = start; i < properties.size(); i++) {
      printSubSets(properties, i + 1, collectedSoFar + ", " + properties.get(i));
    }
  }

  /** Prints all subsets of items in a file of comma separated lines. Takes as args[0] the name of the file.*/
  public static void main(String[] args) throws Exception {
    //args = new String[] { "C:/fabian/data/keys/nonkeysFile.txt" };
    // TODO: Where to write the output. Possibly do not write it at all, but handle it immediately 
    out = new FileWriter("C:/fabian/data/keys/nonkeyscombs.txt");
    // Run through the file
    for (String line : new FileLines(args[0])) {
      // Get the list of properties
      List<String> properties = new ArrayList<>();
      for (String prop : line.split(", ")) {
        properties.add(prop.substring(prop.lastIndexOf("/")));
      }
      // Sort the properties
      Collections.sort(properties);
      // Adding a new line after all the subsets of one input line -- for readibility only 
      out.write("\n");
      // Gooooooo
      printSubSets(properties, 0, "");
    }
  }
}
