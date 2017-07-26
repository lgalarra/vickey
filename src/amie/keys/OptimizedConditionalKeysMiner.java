package amie.keys;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import amie.data.KB;
import amie.mining.assistant.DefaultMiningAssistant;
import amie.mining.assistant.MiningAssistant;
import amie.rules.Rule;
import javatools.datatypes.ByteString;
import telecom.util.collections.Collections;
import telecom.util.collections.MultiMap;

public class OptimizedConditionalKeysMiner {

	private KB kb;
	
	private MiningAssistant assistant;
	
	private Map<String, Integer> property2Id;
	
	private Map<Integer, String> id2Property;
	
	private List<TreeSet<Integer>> nonKeysInt;
	
	private TreeMap<Integer, TreeSet<Integer>> size2Combinations;
	
	int propertiesSize;
	
	public OptimizedConditionalKeysMiner(KB kb, List<List<String>> nonKeys) {
		this.kb = kb;
		this.assistant = new DefaultMiningAssistant(this.kb);
		this.populatePropertyMaps(nonKeys);
	}
	
	/**
	 * Construction of the maps from properties -> id and id -> properties
	 */
	private void populatePropertyMaps(List<List<String>> nonKeys) {
		this.property2Id = new HashMap<>();
		this.id2Property = new HashMap<>();
		this.nonKeysInt = new ArrayList<>();
		
		int id = 0;
		for (List<String> nonKey : nonKeys) {
			TreeSet<Integer> nonKeyInt = new TreeSet<>();
			for (int k = 0; k < nonKey.size(); ++k) {
				String property = nonKey.get(k);
				if (!property2Id.containsKey(property)) {
					property2Id.put(property, id);
					id2Property.put(id, property);
					++id;
				}
				nonKeyInt.add(property2Id.get(property));
				TreeSet<Integer> remainingNonKey = null;
				Integer key = nonKeyInt.first();
				if (!size2Combinations.containsKey(key)){
					remainingNonKey = new TreeSet<>();
					size2Combinations.put(key, remainingNonKey);
				}
				remainingNonKey.addAll(nonKeyInt);
				remainingNonKey.remove(key);
			}
			nonKeysInt.add(nonKeyInt);
		}
	}

	/**
	 * It returns all the minimal conditional keys derived from a given set of properties
	 * known to be a non-key. Conditional keys are returned 
	 * 
	 * @param nk
	 * @param minSupport
	 * @return
	 */
	public List<Rule> mineFromSingleNonKey(List<String> nk, int minSupport) {
		List<Rule> result = new ArrayList<>();
		MultiMap<String, Rule> keys2ConditionsMap = new MultiMap<String, Rule>();
		if (nk.size() == 1)
			return result;
		
		mineFromSingleNonKey(nk, minSupport, 1, keys2ConditionsMap, result);
		return result;
	}
	
	/**
	 * It computes all the minimal conditional keys derived from the given non-key
	 * @param nk A set of properties known to be a non-key
	 * @param minSupport The minimal number of cases that support the key. 
	 * @param conditionSize The number of conditions that define the key
	 * @param keys2Conditions 
	 * @param output
	 */
	private void mineFromSingleNonKey(List<String> nk, int minSupport, int conditionSize, 
			MultiMap<String, Rule> keys2Conditions, List<Rule> output) {
		// At least one of the properties must be non-instantiated
		if (conditionSize == nk.size())
			return;
		// Return all the possible combinations of 'conditionSize' properties that can be 
		// used to define conditions
		List<int[]> conditionsSubsets = Collections.subsetsOfSize(nk.size(), conditionSize);
		for (int[] conditionsIndex : conditionsSubsets) {
			// Materialized list of properties used to define the conditions
			List<String> conditionsProperties = buildList(nk, conditionsIndex);
			// The remaining properties are non-instantiated and define the non-key
			List<String> nkRemaining = new ArrayList<String>(nk);
			nkRemaining.removeAll(conditionsProperties);
			
			// Now get all possible conditions with this combination of properties.
			// They are stored as AMIE rules, e.g, isCitizenOf(a, Ecuador), worksAt(a, Telecom), i.e.,
			// we do not care about the implication
			Iterable<Rule> conditions = 
					Utilities.getConditions(conditionsProperties, minSupport, this.kb);
			// Iterate over the conditions
			for (Rule conditionsRule : conditions) {
				// This list keeps track of the combinations of properties that are 
				// keys that were identified as keys to guarantee minimality
				MultiMap<Integer, int[]> multiMapKeysLastIteration = new MultiMap<>();
				for (int i = 1; i <= nkRemaining.size(); ++i) {
					List<int[]> propertiesSubsets = Collections.subsetsOfSize(nkRemaining.size(), i);
					for (int[] propertiesIndex : propertiesSubsets) {
						// Check minimality within a condition in the list of already found keys
						if (thereExistsSubset(propertiesIndex, multiMapKeysLastIteration)) {
							continue;
						}
						
						List<String> properties = buildList(nkRemaining, propertiesIndex);
						// Check minimality with respect to simpler conditions, e.g., imagine 
						// we are testing the key [p1, p2] with conditions p(C) and p'(C') and we know
						// already that [p1, p2] is a key with solely p(C), then we will not output this rule
						if (thereExistsKeyWithFewerConditions(properties, conditionsRule, keys2Conditions)) {
							continue;
						}
						// Now build an AMIE rule of the form p1(a, l), p1(b, l), .... p(a, C) p(b, C) => equals(a, b)
						Rule amieRule = buildAMIERule(properties, conditionsRule);
						// We test whether this rule has confidence 100%
						if (amieRule.getSupport() >= minSupport && isConditionalKey(amieRule)) {
							// We add it to the output
							output.add(amieRule);
							// We hash it so that we can test minimality in next stages.
							String concat = hash(properties);
							keys2Conditions.add(concat, conditionsRule);
							int propertiesHash = hash(propertiesIndex);
							multiMapKeysLastIteration.put(propertiesHash, propertiesIndex);
						}
					}
				}
			}
		}
		// We repeat the process for a bigger condition size
		mineFromSingleNonKey(nk, minSupport, conditionSize + 1, keys2Conditions, output);
	}
	
	/**
	 * Given sorted array of indexes as first argument, it checks in the second argument whether there 
	 * exists another array of indexes that is a subset of the first argument.
	 * @param propertiesIndex
	 * @param indexKeysFromLastIteration
	 * @return
	 */
	private boolean thereExistsSubset(int[] propertiesIndex, MultiMap<Integer, int[]> multiMapKeysLastIteration) {
		List<int[]> indexSubsets = Collections.subsetsOfSize(propertiesIndex.length, propertiesIndex.length - 1);
		for (int[] indexSubset : indexSubsets) {
			int[] actualArray = indexSubset.clone();
			for (int i = 0; i < actualArray.length; ++i) {
				actualArray[i] = propertiesIndex[actualArray[i]];
			}
			List<int[]> values = multiMapKeysLastIteration.get(hash(actualArray));
			if (values != null) {
				for (int[] v : values) {
					if (Arrays.equals(v, actualArray)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * It checks whether the given set of properties is a conditional key for a set of conditions
	 * that subsumes the provided conditionsRule. For instance if the properties are [r, r', r''], 
	 * and the conditions are {p = C, p'= C'}, this method checks whether [r, r', r''] has been found
	 * as a key in the simpler sets {p = C, p' = C'}. 
	 * 
	 * @param properties
	 * @param keys2Conditions A map containing all the keys found so far. 
	 * @return
	 */
	private boolean thereExistsKeyWithFewerConditions(
			List<String> properties, Rule conditionsRule, MultiMap<String, Rule> keys2Conditions) {
		String key = hash(properties);
		List<Rule> conditions = keys2Conditions.get(key);
		if (conditions == null) {
			return false;
		}
		
		// Then check if there is a set of conditions that subsumes the given conditions
		for (Rule condition : conditions) {
			if (isSubset(condition, conditionsRule))
				return true;
		}
		return false;
	}

	/**
	 * It checks whether the set of atoms in the test rule is a subset of the atoms
	 * of the superset
	 * @param condition
	 * @param conditionsRule
	 * @return
	 */
	private boolean isSubset(Rule test, Rule superset) {
		for (ByteString[] testAtom : test.getTriples()) {
			boolean found = false;
			for (ByteString[] supersetAtom : superset.getTriples()) {
				if (Arrays.equals(testAtom, supersetAtom)) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * It checks whether the test array is contained in the superset array. It
	 * assumes both arrays are sorted.
	 * @param test
	 * @param superset
	 * @return
	 */
	private boolean isSubset(int[] test, int[] superset) {
		for (int i = 0; i < test.length; ++i) {
			boolean contained = false;
			for (int j = 0; j < superset.length; ++j) {
				if (test[i] == superset[j]) {
					contained = true;
					break;
				}
			}
			if (!contained) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Returns a string which is the concatenation of all the properties in the list.
	 * This string is used for hashing purposes.
	 * @param properties
	 * @return
	 */
	private String hash(List<String> properties) {
		List<String> strList = new ArrayList<>();
		StringBuilder strBuilder = new StringBuilder();
		for (String property : properties) {
			strList.add(property.toString());
		}
		java.util.Collections.sort(strList);
		for (String str : strList) {
			strBuilder.append(str);	
		}
		return strBuilder.toString();
	}
	
	/**
	 * Returns a hash which is the sum of the hashes of the integers in the input
	 * array.
	 * @param propertyIndexes
	 * @return
	 */
	private int hash(int[] propertyIndexes) {
		int sum = 0;
		for (int i = 0; i < propertyIndexes.length; ++i) {
			sum += propertyIndexes[i];
		}
		return sum;
	}

	/**
	 * Given sorted array of indexes as first argument, it checks in the second argument whether there 
	 * exists another array of indexes that is a subset of the first argument.
	 * @param propertiesIndex
	 * @param indexKeysFromLastIteration
	 * @return
	 */
	private boolean thereExistsSubset(int[] propertiesIndex,
			List<int[]> indexKeysFromLastIteration) {
		for (int[] indexes : indexKeysFromLastIteration) {		
			if (isSubset(indexes, propertiesIndex)) {
				return true;
			}
		}
		
		return false;
	}
	

	/**
	 * It build a rule of the form r(?a, ?l) r(?b, ?l') ... c(?a, C), c(?b, C') ... => equals(a, b)
	 * from a set of non-instantiated properties (r) and a set of instantiated atoms of the form c(?a, C).
	 * @param properties
	 * @param conditions
	 * @return
	 */
	private Rule buildAMIERule(List<String> properties,
			Rule conditionsRule) {
		Rule rule = new Rule(KB.triple("?a", KB.EQUALSstr, "?b"), 0.0);
		
		int k = 1;
		for (String property : properties) {
			rule.getTriples().add(KB.triple(ByteString.of("?a"), ByteString.of(property), ByteString.of("?ob" + k)));
			rule.getTriples().add(KB.triple(ByteString.of("?b"), ByteString.of(property), ByteString.of("?ob" + k)));
			++k;
		}
		
		for (ByteString[] conditionAtom : conditionsRule.getTriples()) {
			ByteString[] missingAtom = conditionAtom.clone();
			missingAtom[0] = ByteString.of("?b");
			rule.getTriples().add(conditionAtom);
			rule.getTriples().add(missingAtom);
		}
		
		assistant.computeCardinality(rule);
		assistant.computeStandardConfidence(rule);
		
		return rule;
	}

	/**
	 * Determines if the given rule is a conditional key, i.e., it has 100% confidence
	 * @param amieRule A rule of the form r(?a, ?l) r(?b, ?l') ... c(?a, C), c(?b, C') ... => equals(a, b)
	 * @return
	 */
	private boolean isConditionalKey(Rule amieRule) {
		return amieRule.getStdConfidence() == 1.0;
	}

	/**
	 * It returns a list with the elements of the indexes stored in the indexes array
	 * @param nk
	 * @param conditionsIndex
	 * @return
	 */
	private <T> List<T> buildList(List<T> nk, int[] indexes) {
		List<T> result = new ArrayList<>();
		for (int i : indexes) {
			result.add(nk.get(i));
		}
		return result;
	} 

	
	/**
	 * Takes an AMIE rule representing a conditional key and converts into a human 
	 * readable string.
	 * 
	 * @param rule
	 * @return
	 */
	public static String formatKey(Rule rule) {
		StringBuilder strBuilder = new StringBuilder();
		Set<String> instantiated = new LinkedHashSet<>();
		Set<String> nonInstantiated = new LinkedHashSet<>();		
		for (ByteString[] atom : rule.getTriples()) {
			if (atom[1].equals(KB.EQUALSbs))
				continue;
			
			if (KB.isVariable(atom[2])) {
				nonInstantiated.add(atom[1].toString());
			} else {
				instantiated.add(atom[1] + "=" + atom[2]);
			}
		}
		
		
		strBuilder.append("Key ");
		for (String noninst : nonInstantiated) {
			strBuilder.append(noninst);
			strBuilder.append(" ");
		}
		strBuilder.append(" with condition");
		if (instantiated.size() > 1)
			strBuilder.append("s");
		strBuilder.append(" ");
		for (String inst : instantiated) {
			strBuilder.append(inst);
			strBuilder.append(" ");
		}
		
		strBuilder.append(" and support " + rule.getSupport());
		strBuilder.append(" and confidence " + rule.getStdConfidence());
		
		return strBuilder.toString();
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("amie.keys.ConditionalKeysMiner <KB> <NonKeys> <min-support>");
			System.exit(1);
		}
		
		KB kb = new KB();
		kb.load(new File(args[0]));
		
		ConditionalKeysMiner miner = new ConditionalKeysMiner(kb);
		int support = Integer.parseInt(args[2]);
		for (List<String> nonKey : Utilities.parseNonKeysFile(args[1])) {
			for (Rule r : miner.mineFromSingleNonKey(nonKey, support)) {
				System.out.println(formatKey(r));
			}
		}
	}
}
