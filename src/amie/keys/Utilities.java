package amie.keys;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import amie.data.KB;
import amie.rules.Rule;
import javatools.datatypes.ByteString;

/**
 * It contains a set of utility methods for the discovery of conditional keys
 * in KBs
 * 
 * @author galarrag
 */
public class Utilities {
	
	/**
	 * Returns an iterable object for conjunctions of instantiated atoms. The conjunctions
	 * are returned as AMIE rules of the form [?a r C1], [?a r' C'] ... .
	 * 
	 * @param conditions Predefined conditions
	 * @param relations The relations that must appear in the returned expressions.
	 * @param minSupport The minimum number of instantations for ?a 
	 * @param kb
	 * @return
	 */
	public static Iterable<Rule> getConditions(Rule conditions, List<String> relations, 
			int minSupport, KB kb) {
		return new ConditionsResult(conditions, relations, minSupport, kb);
	}
	
	public static Iterable<Rule> getConditions(Rule conditions, String relation, 
			int minSupport, KB kb) {
		return new ConditionsResult(conditions, Arrays.asList(relation), minSupport, kb);
	}
	
	/**
	 * Returns an iterable object for conjunctions of instantiated atoms. The conjunctions
	 * are returned as AMIE rules of the form [?a r C1], [?a r' C'] ... .
	 * 
	 * @param relations The relations that must appear in the returned expressions.
	 * @param minSupport The minimum number of instantations for ?a 
	 * @param kb
	 * @return
	 */
	public static Iterable<Rule> getConditions(List<String> relations, int minSupport, KB kb) {
		return new ConditionsResult(relations, minSupport, kb);
	}
	
	/**
	 * It parses a non-keys files.
	 * 
	 * @param nonKeysFile
	 * @return A list of non-keys, where each non-key is represented as a list of properties (byte strings)
	 * @throws IOException 
	 */
	public static List<List<String>> parseNonKeysFile(String nonKeysFile) throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(nonKeysFile), Charset.forName("UTF-8"));
		List<List<String>> result = new ArrayList<List<String>>();
		// Bootstrapping phase
		for (String line : lines) {
			String[] relations = line.split(",");
                   //     System.out.println("relations:"+relations.toString());
			List<String> lineList = new ArrayList<String>();
			for (int i = 0; i < relations.length; ++i) {
				String relationBS = relations[i].trim();
                if (relationBS.length()!=0){
            		lineList.add(relationBS);
                }
			}
			result.add(lineList);
		}
		
		return result;
	}
	
	/**
	 * Find the instantiated and non-instantiated relations in the key.
	 * @param keyRule
	 * @param instantiations
	 * @param nonInstantiatedRelations
	 */
	public static void parseConditionalKey(
			Rule keyRule, Set<ByteString> nonInstantiatedRelations, 
			Set<ByteString> instantiations, Set<ByteString> instantiatedRelations) {
		for (ByteString[] atom : keyRule.sortBody()) {			
			if (KB.isVariable(atom[2])) {
				nonInstantiatedRelations.add(atom[1]);
			} else {
				instantiations.add(ByteString.of(atom[1] + "=" + atom[2]));
				instantiatedRelations.add(atom[1]);
			}
		}
	}
	
	/**
	 * Takes an AMIE rule representing a conditional key and converts into a human 
	 * readable string.
	 * 
	 * @param keyRule
	 * @return
	 */
	public static String formatKey(Rule keyRule) {
		StringBuilder strBuilder = new StringBuilder();
		Set<ByteString> instantiated = new LinkedHashSet<>();
		Set<ByteString> nonInstantiated = new LinkedHashSet<>();
		Set<ByteString> instantiations = new LinkedHashSet<>();
		parseConditionalKey(keyRule, nonInstantiated, instantiated, instantiations);
		
		strBuilder.append("");
		for (ByteString noninst : nonInstantiated) {
			strBuilder.append(noninst);
			strBuilder.append(" ");
		}
		strBuilder.append("\t");
		for (ByteString inst : instantiated) {
			strBuilder.append(inst);
			strBuilder.append(" ");
		}
		
		strBuilder.append("\t" + keyRule.getSupport());
		strBuilder.append("\t" + keyRule.getSupportRatio());
		
		
		return strBuilder.toString();
	}
	
	public static TreeSet<Integer> getRelationIds(Rule rule, Map<String, Integer> dict) {
		String headRelation = rule.getHeadRelation();
		TreeSet<Integer> result = new TreeSet<>();
		Integer val = dict.get(headRelation);
		if (val != null) {
			result.add(val);
		}
		
		for (ByteString bRelation : rule.getBodyRelationsBS()) {
			val = dict.get(bRelation.toString());
			if (val != null) {
				result.add(val);
			}
		}
		
		return result;
	}
	
	public static void main(String args[]) throws IOException {
		KB kb = amie.data.U.loadFiles(args);
		Iterable<Rule> conditions = getConditions(Arrays.asList("residence", "workPlace"), 1, kb);
		System.out.println("Example 1");
		for (Rule c : conditions) {
			System.out.println(c + " " + c.getSupport());
		}
		
		
		Rule rule = new Rule(KB.triple("?a", "residence", "Paris"), 3);
		conditions = getConditions(rule, "workPlace", 1, kb);
		System.out.println("Example 2");
		for (Rule c : conditions) {
			System.out.println(c + " " + c.getSupport());
		}
		
		rule = new Rule(KB.triple("?a", "residence", "Paris"), KB.triples(KB.triple("?a", "workPlace", "Télécom")), 3);
		conditions = getConditions(rule, "favouriteColour", 1, kb);
		System.out.println("Example 3");
		for (Rule c : conditions) {
			System.out.println(c + " " + c.getSupport());
		}
	}
}
