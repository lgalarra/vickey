/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amie.keys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import amie.data.KB;
import amie.data.KB.Column;
import amie.mining.assistant.DefaultMiningAssistant;
import amie.mining.assistant.MiningAssistant;
import amie.rosa.AlignKBs;
import amie.rules.Rule;
import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import javatools.datatypes.Triple;
import telecom.util.collections.MultiMap;

/**
 *
 * @author symeonid
 */
public class CSAKey {

    // Dictionaries
    private Map<String, Integer> property2Id;
    private Map<Integer, String> id2Property;

    private HashSet<HashSet<Integer>> nonKeysInt;
    //static HashSet<node> nonKeysIntNodes = new HashSet<>();
    private List<Integer> propertiesList;
    /**
     * A map where the keys are conditions, e.g., residence=Paris
     * and the values are conditional keys with that condition, e.g.,
     * nationality zipCode | residence=Paris; lastname nationality | residence=Paris
     * etc.
     */
    private MultiMap<Rule, Rule> conditions2Keys = new MultiMap<>();    
    private MiningAssistant miningHelper = null;
    public static final float defaultMinSupport = 2;
    public float support = defaultMinSupport;
    private KB kb;
    public String nonKeysFile = null;
    private static int maxLoad = 50;
    private static int minLoad = 20;
    
    public CSAKey(MiningAssistant assistant, HashSet<HashSet<Integer>> nonKeysInt, Map<String, Integer> property2Id, 
    		Map<Integer, String> id2Property, List<Integer> propertiesList) {
    	this.nonKeysInt = nonKeysInt;
    	this.property2Id = property2Id;
    	this.id2Property = id2Property;
    	this.propertiesList = propertiesList;
    	this.miningHelper = assistant;
    	this.kb = assistant.getKb();
    }
    
    /**
     * Parses the command line arguments and the returns an object that maps each argument
     * to its value.
     * @param args
     * @return
     * @throws IOException
     */
    public static Triple<MiningAssistant, Float, String> parseArguments(String[] args) throws IOException {
    	HelpFormatter formatter = new HelpFormatter();
    	float inputSupport = defaultMinSupport;

        // create the command line parser
        CommandLineParser parser = new PosixParser();
        // create the Options
        Options options = new Options();
        CommandLine cli = null;
        
        Option supportOpt = OptionBuilder.withArgName("min-support")
                .hasArg()
                .withDescription("Minimum support. Default: 5 positive examples. If the option percentage is enabled, "
                		+ "the value is considered as the percentage of entities covered "
                		+ "by the a conditional key.")
                .create("mins");
        
        Option ratioOpt = OptionBuilder.withArgName("percentage")
                .withDescription("Interpret the support as a percentage "
                		+ "Default: false")
                .create("p");
        
        Option nonKeysOpt = OptionBuilder.withArgName("non-keys")
        		.withDescription("Path the to the non-keys file.")
        		.hasArg()
        		.isRequired()
        		.create("nk");
        
        Option minLoadOpt = OptionBuilder.withArgName("")
        		.withDescription("Mininum load")
        		.hasArg()
        		.create("minl");
        
        Option maxLoadOpt = OptionBuilder.withArgName("")
        		.withDescription("Maximum load")
        		.hasArg()
        		.create("maxl");
        
        options.addOption(supportOpt);
        options.addOption(ratioOpt);
        options.addOption(nonKeysOpt);
        options.addOption(minLoadOpt);
        options.addOption(maxLoadOpt);
        
        try {
            cli = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("Unexpected exception: " + e.getMessage());
            formatter.printHelp("CombinationsExploration [OPTIONS] <TSV FILES>", options);
            System.exit(1);
        }
                
        String[] fileNameArgs = cli.getArgs();        
        // Use kb = amie.data.U.loadFiles(args, 1) to ignore the first
        // argument. This first argument could be the list of non-keys
        //kb = amie.data.U.loadFiles(fileNameArgs);        
        KB kb = AlignKBs.loadFiles(fileNameArgs, 0);
        MiningAssistant miningHelper = new DefaultMiningAssistant(kb);
    	
        if (cli.hasOption("mins")) {
        	try {
        		inputSupport = Float.parseFloat(cli.getOptionValue("mins"));
        	} catch (NumberFormatException e) {
        		System.out.println("Unexpected exception: " + e.getMessage());
                formatter.printHelp("CombinationsExploration [OPTIONS] <TSV FILES>", options);
                System.exit(1);
        	}
        }
        
        if (cli.hasOption("p")) {
        	System.out.println("Support interpreted as a " + inputSupport + "% of the number of entities.");
        	long numberOfInstances = kb.size(Column.Subject);
        	System.out.println(numberOfInstances + " instances found as subjects in the KB.");
        	inputSupport = (int) Math.ceil(numberOfInstances * inputSupport / 100.0);
        }

        if (cli.hasOption("minl")) {
        	minLoad = Integer.parseInt(cli.getOptionValue("minl"));
        	System.out.println("minLoad=" + minLoad);
        }
        
        if (cli.hasOption("maxl")) {
        	maxLoad = Integer.parseInt(cli.getOptionValue("maxl"));
        	System.out.println("maxLoad=" + maxLoad);
        }
        
    	System.out.println("Using minimum support " + inputSupport);
        
        return new Triple<>(miningHelper, inputSupport, cli.getOptionValue("nk"));
    }
    
    private static int computeLoad(List<List<String>> nks) {
    	int sum = 0;
    	for (List<String> nk : nks) {
    		sum += nk.size();
    	}
    	
    	return sum;
    }
    
    
    private static int[] nextIndex(List<HashSet<Integer>> nonKeys, int lastIndex, int batchSize) {
    	int result[] = new int[]{0, 0};
    	
    	int idx = lastIndex;
    	while (result[1] < batchSize) {
			if (idx >= nonKeys.size())
				break;

			result[1] += nonKeys.get(idx).size();
    		++idx;
    	}
    	result[0] = idx;
    	return result;
	}

    public static void main(String[] args) throws IOException, InterruptedException {
    	final Triple<MiningAssistant, Float, String> parsedArgs = parseArguments(args);    	
    	final Set<Rule> output = new LinkedHashSet<>();
        
        // Helper object that contains the implementation for the calculation
        // of confidence and support
        // The file with the non-keys, one per line
    	long timea = System.currentTimeMillis();
    	List<List<String>> inputNonKeys = Utilities.parseNonKeysFile(parsedArgs.third);
    	System.out.println(inputNonKeys.size() + " input non-keys");
        final List<List<String>> nonKeys = pruneBySupport(inputNonKeys, parsedArgs.second, parsedArgs.first.getKb());
        Collections.sort(nonKeys, new Comparator<List<String>>() {

			@Override
			public int compare(List<String> o1, List<String> o2) {
				int r = Integer.compare(o2.size(), o1.size());
				if (r == 0) {
					return Integer.compare(o2.hashCode(), o1.hashCode());
				}
				
				return r;
			}
        	
        });
    	System.out.println(nonKeys.size() + " non-keys after pruning");
    	int totalLoad = computeLoad(nonKeys);
    	System.out.println(totalLoad + " is the total load");
        int nThreads = Runtime.getRuntime().availableProcessors();
        //int batchSize = Math.max(Math.min(maxBatchSize, totalLoad / nThreads), minBatchSize);
    	int batchSize = Math.max(Math.min(maxLoad, totalLoad / nThreads), minLoad);
    	
    	final Queue<int[]> chunks = new PriorityQueue(50, new Comparator<int[]>(){
			@Override
			public int compare(int[] o1, int[] o2) {
				return Integer.compare(o2[2], o1[2]);
			}
    		
    	});
    	
    	
		final HashSet<HashSet<Integer>> nonKeysInt = new HashSet<>();
		final HashMap<String, Integer> property2Id = new HashMap<>();
		final HashMap<Integer, String> id2Property = new HashMap<>();		
		final List<Integer> propertiesList = new ArrayList<>();
		int support = (int) parsedArgs.second.floatValue();
		KB kb = parsedArgs.first.getKb();
		buildDictionaries(nonKeys, nonKeysInt, property2Id, id2Property, propertiesList, support, kb);
    	final List<HashSet<Integer>> nonKeysIntList = new ArrayList<>(nonKeysInt);
    	int start = 0;
    	int[] nextIdx = nextIndex(nonKeysIntList, 0, batchSize);
    	int end = nextIdx[0];
    	int load = nextIdx[1];
    	while (start < nonKeysIntList.size()) {
    		int[] chunk = new int[]{start, end, load}; 
    		chunks.add(chunk);
    		start = end;    		
    		nextIdx = nextIndex(nonKeysIntList, end, batchSize); 
    		end = nextIdx[0];
    		load = nextIdx[1];
    	}
    	
    	
    	Thread[] threads = new Thread[Math.min(Runtime.getRuntime().availableProcessors(), chunks.size())];
    	for (int i = 0; i < threads.length; ++i) {
        	threads[i] = new Thread(new Runnable() {

				@Override
				public void run() {
					while (true) {
						int[] chunk = null;
    					synchronized (chunks) {
    						if (!chunks.isEmpty()) {
    							chunk = chunks.poll();
    						} else {
    							break;
    						}
    					}
    					System.out.println("Processing chunk " + Arrays.toString(chunk));
    					mine(parsedArgs, nonKeysIntList, property2Id, id2Property, propertiesList, chunk[0], chunk[1], output);
					}
					
				}
        	});	
        	threads[i].start();
    	}
    	
    	for (int i = 0; i < threads.length; ++i) {
    		threads[i].join();
    	}
        long timeb = System.currentTimeMillis();
        System.out.println("==== Unique C-keys =====");
        for (Rule r : output) {
        	System.out.println(Utilities.formatKey(r));
        }
        System.out.println("VICKEY found " + output.size() + " unique conditional keys in " + (timeb - timea) +  " ms");
    }
    
    /**
     * 
     * @param propertyIds
     * @param ids2Properties
     * @return
     */
    static int support(HashSet<Integer> propertyIds, Map<Integer, String> ids2Properties, KB kb) {
    	List<ByteString[]> query = new ArrayList<>();
    	ByteString var = ByteString.of("?s");
    	int k = 0;
    	for (Integer id : propertyIds) {
    		String relation = ids2Properties.get(id);
    		if (relation == null) {
    			throw new IllegalArgumentException("No property with id " + id);
    		}
    		query.add(KB.triple(var, ByteString.of(relation), ByteString.of("?o" + k)));
    		++k;
    	}
    	
    	return (int) kb.countDistinct(var, query);
    }

	/**
     * Returns only the non-keys with (1) more than one property, (2) 
     * whose support is above the minimum support threshold.
     * @param parseNonKeysFile
     * @param second
     * @return
     */
    private static List<List<String>> pruneBySupport(List<List<String>> nonKeys, Float supportThreshold, KB kb) {
    	List<List<String>> result = new ArrayList<>();
    	// We get a map with the properties and their number of subjects
        IntHashMap<ByteString> propertiesSupport = kb.frequentBindingsOf(ByteString.of("?p"), ByteString.of("?s"),
                KB.triples(KB.triple("?s", "?p", "?o")));

        for (List<String> nonKey : nonKeys) {
            if (nonKey.size() == 1)
            	continue;
            
            List<String> newNonKey = new ArrayList<>();
            for (String property : nonKey) {
            	int support = propertiesSupport.get(ByteString.of(property));
            	if (support >= supportThreshold) {
            		newNonKey.add(property);
            	}
            }
            
            if (newNonKey.size() > 1) {
            	result.add(newNonKey);
            }
        }
        
        return simplifyNonKeysSet(result);
	}

	private static void mine(Triple<MiningAssistant, Float, String> parsedArgs, 
    		List<HashSet<Integer>> nonKeysInt, HashMap<String, Integer> property2Id, HashMap<Integer, String> id2Property,
    		List<Integer> propertiesList,
    		int start, int end, Set<Rule> output) {
    	// First prune non-promising non-keys    	
		HashSet<HashSet<Integer>> hs = new HashSet<>(nonKeysInt.subList(start, end));
    	CSAKey ckminer = new CSAKey(parsedArgs.first, hs, property2Id, id2Property, propertiesList);
    	//System.out.println(hs);
    	ckminer.support = parsedArgs.second.floatValue();
        ckminer.discoverConditionalKeys(output);
    }

	private static HashSet<HashSet<Integer>> subSet(HashSet<HashSet<Integer>> nonKeysInt2, int start, int end) {
		int counter = 0;
		Iterator<HashSet<Integer>> it = nonKeysInt2.iterator();
		HashSet<HashSet<Integer>> result = new HashSet<>();
		while (counter < start) {
			it.next();
			++counter;
		}
		
		while (counter < end) {
			result.add(it.next());
			++counter;
		}
		
		return result;
	}

	/**
     * It builds a rule of the form r(?a, ?l) r(?b, ?l') ... c(?a, C), c(?b, C')
     * ... => equals(a, b) from a set of non-instantiated properties (r) and a
     * set of instantiated atoms of the form c(?a, C).
     *
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
            rule.getTriples().add(conditionAtom.clone());
            rule.getTriples().add(missingAtom);
        }

        miningHelper.computeCardinality(rule);
        miningHelper.computeStandardConfidence(rule);

        return rule;
    }
    
    private static int getPropertySupport(String property, KB kb) {
    	IntHashMap<ByteString> propertiesSupport = kb.frequentBindingsOf(ByteString.of("?p"), ByteString.of("?s"),
                KB.triples(KB.triple("?s", "?p", "?o")));
        int propertySupport = propertiesSupport.get(ByteString.of(property));
        if (propertySupport != -1) {
            return propertySupport;
        }
        return 0;
    }

    /**
     * Construction of the maps from properties -> id and id -> properties
     */
    private static void buildDictionaries(List<List<String>> nonKeys, HashSet<HashSet<Integer>> nonKeysInt, HashMap<String, Integer> property2Id,
    		HashMap<Integer, String> id2Property, List<Integer> propertiesList, int support, KB kb) {
    	int id = 0;
    	HashSet<HashSet<Integer>> nonKeysIntTmp = new HashSet<>();
        for (List<String> nonKey : nonKeys) {
            HashSet<Integer> nonKeyInt = new HashSet<>();
            for (int k = 0; k < nonKey.size(); ++k) {
                // Ignore 
                String property = nonKey.get(k);
                //**** Debugging code ***/
/*//            	List<String> testingProperties = Arrays.asList("db:campus", "db:mascot", "db:officialschoolcolour", "db:athletics", "db:country");       	
//                if (getPropertySupport(property, kb) < support || !testingProperties.contains(property)) {
//                	continue;
//                }
*/                //**** Debugging code ***/

                if (!property2Id.containsKey(property)) {
                    property2Id.put(property, id);
                    id2Property.put(id, property);
                    propertiesList.add(id);
                    ++id;
                }
                Integer idProperty = property2Id.get(property);
                nonKeyInt.add(idProperty);
            }
            nonKeysIntTmp.add(nonKeyInt);
        }
        nonKeysInt.addAll(simplifyHashNonKeySet(nonKeysIntTmp));
        //System.out.println("Simplified " + nonKeysInt + "\tThread " + Thread.currentThread().getId() + "\t" + id2Property);
    }

    /**
     * It outputs all the conditional keys on a KB given a minimum support 
     * threshold.
     * @param support
     * @param kb
     */
    public void discoverConditionalKeys(Set<Rule> output) {
        HashMap<Rule, Graph> ruleToGraphFirstLevel = new HashMap<>();
        /** 
         * We build a graph (subset lattice) for each property. The graph contains nodes
         * for each of the other properties and all its possible combinations.
         * For instance given the relations 1, 2, 3, 4. The graph for property 1
         * contains as nodes: 2, 3, 4, 23, 24, 34, 234 with their corresponding
         * parent relationships, e.g., 23 has 2 and 3 as parents. While relations
         * are strings, we have mapped them to an integer space (that is why the key
         * is an integer)
         **/
        HashMap<Integer, Graph> instantiatedProperty2Graph = buildPropertyGraphs((int) support);
        //System.out.println(instantiatedProperty2Graph);
        /**
         * Here we find all conditional keys when the condition has size 1, i.e., it involves
         * only one property such as residence=Paris | zipCode, field.
         * The map contains the conditions as keys and the list of potential properties that could
         * be used to extend this condition as values.
         */
        HashMap<Rule, HashSet<String>> conditionsToPotentialExtensions
                = discoverConditionalKeysFirstLevel(ruleToGraphFirstLevel, instantiatedProperty2Graph, output);
        
        //for (Rule rule : ruleToGraphFirstLevel.keySet()) {
        //    System.out.println("rule:" + rule + " => " + ruleToGraphFirstLevel.get(rule) + "\tThread" + Thread.currentThread().getId() );
        //}
        
        if (conditionsToPotentialExtensions.size() != 0) {
        	// If there is room for extension, we look for conditional keys of longer size.
            discoverConditionalKeysPerLevel(conditionsToPotentialExtensions, ruleToGraphFirstLevel, ruleToGraphFirstLevel, output);
        }
        System.out.println("We found " + conditions2Keys.valueSize() + " key(s)");

    }

    public HashMap<Integer, Graph> buildPropertyGraphs(int support) {
        HashMap<Integer, Graph> instantiatedProperty2Graph = new HashMap<>();
        for (int instantiatedProperty : propertiesList) {
        	/**** Debugging code ***/
        	/*List<String> testingProperties = Arrays.asList("db:athletics", "db:country");       	
        	if (!testingProperties.contains(id2Property.get(instantiatedProperty)))
        		continue;*/
        	/**** Debugging code ***/
            //build a graph for the instantiatedProperty
            Graph graph = new Graph(nonKeysInt, propertiesList, instantiatedProperty, id2Property, miningHelper.getKb(), support);
            instantiatedProperty2Graph.put(instantiatedProperty, graph);
        }

        return instantiatedProperty2Graph;
    }

    public void discoverConditionalKeysForCondition(Graph newGraph, Graph graph, 
    		HashSet<Node> candidateKeys,
    		Rule conditionRule, Set<Rule> output) {
    	HashSet<Node> newCandidateKeys = new HashSet<>();
        for (Node candidateKey : candidateKeys) {
            //  System.out.println("candidateKey:" + candidateKey);
            if (candidateKey.toExplore) {
                //   System.out.println("candidate:" + candidateKey);
                // if (candidateKey.toExplore) {
                List<String> properties = candidateKey.mapToString(id2Property);
                Rule amieRule = buildAMIERule(properties, conditionRule);
                //    System.out.println("rule:" + amieRule);
                boolean isConditionalKey = isConditionaKey(amieRule);
                //System.out.println("isConditionalKey:"+isConditionalKey +  " Thread " + Thread.currentThread().getId() + "\t" + Utilities.formatKey(amieRule));

                if (amieRule.getSupport() >= support && !isConditionalKey) {
                	//System.out.println("Case 0" +  " Thread " + Thread.currentThread().getId());
                    if (!newGraph.graph.containsKey(candidateKey)) {
                    	//System.out.println("Case 1" +  " Thread " + Thread.currentThread().getId());
                        Node newCandidateKey = candidateKey.clone();
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(newCandidateKey, children);
                        newGraph.nodes.put(newCandidateKey, newCandidateKey);
                        newCandidateKeys.add(newCandidateKey);
                    } else {
                    	//System.out.println("Case 2" +  " Thread " + Thread.currentThread().getId());                    	
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(candidateKey, children);
                        newCandidateKeys.add(candidateKey);
                    }
                }

                // If the rule is a conditional above the support
                // and there is no a simpler key already discovered
                // then output it
                if (isConditionalKey
                        && amieRule.getSupport() >= support
                        && !isSubsumedByKey(amieRule, conditionRule, conditions2Keys)) {
                    //        System.out.println("KEY");
                    if (!newGraph.graph.containsKey(candidateKey)) {
                        //       System.out.println("clone");
                        Node newCandidateKey = candidateKey.clone();
                        synchronized (output) {
							output.add(amieRule);
						}
                        //System.out.println(Utilities.formatKey(amieRule) + "\tThread " + Thread.currentThread().getId() + " Case 3");
                        System.out.println(Utilities.formatKey(amieRule));
                        conditions2Keys.put(conditionRule, amieRule);
                        newCandidateKey.toExplore = false;
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(newCandidateKey, children);
                        newGraph.nodes.put(newCandidateKey, newCandidateKey);
                        newCandidateKeys.add(newCandidateKey);
                    } else {
                        synchronized (output) {
							output.add(amieRule);
						}
                        System.out.println(Utilities.formatKey(amieRule));
                        //System.out.println(Utilities.formatKey(amieRule) + "\tThread " + Thread.currentThread().getId() + " Case 4");
                        conditions2Keys.put(conditionRule, amieRule);
                        candidateKey.toExplore = false;
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(candidateKey, children);
                        newGraph.nodes.put(candidateKey, candidateKey);
                        newCandidateKeys.add(candidateKey);
                    }
                }
            } else {
            	//System.out.println("Case 5");
                newCandidateKeys.add(candidateKey);
            }
        }

        // createChildren
        HashSet<Node> allChildren = new HashSet<>();
        // System.out.println("newCandidateKeys:"+newCandidateKeys);
        for (Node parent1 : newCandidateKeys) {
            //    System.out.println("parent1:"+parent1);
            for (Node parent2 : newCandidateKeys) {
                if (parent1 != parent2 && parent1.toExplore != false && parent2.toExplore != false) {
                    HashSet<Integer> newSet = new HashSet<>();
                    newSet.addAll(parent1.set);
                    newSet.addAll(parent2.set);
                    HashSet<Integer> condProp_KeyProp = new HashSet<>();
                    condProp_KeyProp.addAll(newSet);
                    condProp_KeyProp.addAll(getRelations(conditionRule, property2Id));
                    //     System.out.println("newSet:" + newSet);
                    if ((newSet.size() == parent1.set.size() + 1) && (getSupport(newSet, conditionRule, (int)support)) 
                    		&& Graph.containsASuperSetOf(nonKeysInt, condProp_KeyProp) != -1) {
                        //      System.out.println("enters");
                        Node child = new Node(newSet);
                        if (hasFalseParent(newSet, newCandidateKeys)) {
                            //         System.out.println("falseParent");
                            child.toExplore = false;
                        }
                        HashSet<Node> children1 = newGraph.graph.get(parent1);
                        children1.add(child);
                        newGraph.graph.put(parent1, children1);
                        newGraph.nodes.put(child, child);
                        HashSet<Node> grandChildren = new HashSet<>();
                        newGraph.graph.put(child, grandChildren);
                        HashSet<Node> children2 = newGraph.graph.get(parent2);
                        children2.add(child);
                        newGraph.graph.put(parent2, children2);
                        allChildren.add(child);
                    }
                }
            }
        }

        if (!allChildren.isEmpty()) {
            discoverConditionalKeysForCondition(newGraph, newGraph, allChildren, conditionRule, output);
        }
    }

    /**
     * It determines whether there exists a more general version of the given conditional key, a version of the
     * key with the exact same relations but fewer instantiations. For instance
     * if the key states lastname | nationality=French, field=Databases but the map contains a key
     * lastname nationality | field=Databases (here nationality is not instantiated), the method will report 
     * this as a subsumption case and return true. 
     * @param conditionalKey
     * @param conditionRule
     * @param conditions2Keys2
     * @return
     */
    private boolean isSubsumedByKey(Rule conditionalKey, Rule conditionRule, 
    		MultiMap<Rule, Rule> conditions2Keys2) {
    	if (conditionRule.getLength() < 2) {
            return false;
        }

        Set<ByteString> instantiations = new LinkedHashSet<>();
        Set<ByteString> instantiatedRelations = new LinkedHashSet<>();
        Set<ByteString> nonInstantiatedRelations = new LinkedHashSet<>();
        Utilities.parseConditionalKey(conditionalKey, nonInstantiatedRelations, instantiations, instantiatedRelations);

        /**
         * Now get all possible simpler versions of the condition If the
         * condition is field=Databases, residence=Paris, gender=female the
         * method returns: field=Databases, residence=Paris field=Database,
         * gender=female residence=Paris, gender=female residence=Paris
         * gender=female field=Databases *
         */
        List<Rule> properSubconditions = getAllProperSubconditions(conditionRule);
        for (Rule subCondition : properSubconditions) {
            List<Rule> potentialParents = conditions2Keys2.get(subCondition);
            if (potentialParents != null) {
                for (Rule potentialParent : potentialParents) {
                    if (potentialParent.getLength() != conditionalKey.getLength()) {
                       // System.out.println("potentialParent:" + potentialParent);
                        continue;
                    }
                    Set<ByteString> instantiatedRelationsParent = new LinkedHashSet<>();
                    Set<ByteString> nonInstantiatedRelationsParent = new LinkedHashSet<>();
                    Set<ByteString> instantiationsParent = new LinkedHashSet<>();
                    Utilities.parseConditionalKey(potentialParent, nonInstantiatedRelationsParent,
                            instantiationsParent, instantiatedRelationsParent);
                    Set<ByteString> instansiatedNonInstantiatedRelations = new LinkedHashSet<>();
					instansiatedNonInstantiatedRelations.addAll(instantiatedRelations);
					instansiatedNonInstantiatedRelations.addAll(nonInstantiatedRelations);
                    Set<ByteString> instansiatedNonInstantiatedRelationsParent = new LinkedHashSet<>();
					instansiatedNonInstantiatedRelationsParent.addAll(instantiatedRelationsParent);
					instansiatedNonInstantiatedRelationsParent.addAll(nonInstantiatedRelationsParent);
                    if (instantiatedRelations.containsAll(instantiatedRelationsParent)
                            && nonInstantiatedRelationsParent.containsAll(nonInstantiatedRelations) && 
                            instansiatedNonInstantiatedRelationsParent.containsAll(instansiatedNonInstantiatedRelations)) {
                        return true;
                    }
                }
            }
        }

        return false;
	}

	private List<Rule> getAllProperSubconditions(Rule conditionRule) {
		int numberOfConditions = conditionRule.getLength();
		List<Rule> results = new ArrayList<>();
		for (int size = 1; size < numberOfConditions; ++size) {
			List<int[]> combinations = 
					telecom.util.collections.Collections.subsetsOfSize(numberOfConditions, size);
			for (int[] combination : combinations) {
				List<ByteString[]> atoms = new ArrayList<>();
				for (int idx : combination) {
					atoms.add(conditionRule.getTriples().get(idx));
				}
				Rule newRule = null;
				long card = kb.countDistinct(atoms.get(0)[0], atoms);
				if (atoms.size() > 1) {
					newRule = new Rule(atoms.get(0), atoms.subList(1, atoms.size()), card);
				} else {
					newRule = new Rule(atoms.get(0), card);
				}
				results.add(newRule);
			}
		}
		return results;
	}

	private boolean isConditionaKey(Rule amieRule) {
        return amieRule.getStdConfidence() == 1.0;
    }

    public HashSet<HashSet<Integer>> buidPropertyGraph(int property) {
        HashSet<HashSet<Integer>> propertyPowerSets = new HashSet<>();
        for (HashSet<Integer> nonKeyInt : nonKeysInt) {
            if (nonKeyInt.contains(property)) {
                HashSet<Integer> remainingSet = new HashSet<>(nonKeyInt);
                remainingSet.addAll(nonKeyInt);
                remainingSet.remove(property);
                propertyPowerSets.addAll(powerSet(remainingSet));
            }
        }
        return propertyPowerSets;
    }

    public HashSet<HashSet<Integer>> powerSet(HashSet<Integer> originalSet) {
        HashSet<HashSet<Integer>> sets = new HashSet<HashSet<Integer>>();
        if (originalSet.isEmpty()) {
            sets.add(new HashSet<Integer>());
            return sets;
        }
        List<Integer> list = new ArrayList<Integer>(originalSet);
        int head = list.get(0);
        HashSet<Integer> rest = new HashSet<Integer>(list.subList(1, list.size()));
        for (HashSet<Integer> set : powerSet(rest)) {
            HashSet<Integer> newSet = new HashSet<Integer>();
            newSet.add(head);
            newSet.addAll(set);
            sets.add(newSet);
            sets.add(set);
        }
        return sets;
    }

    /**
     * public static void nonKeysIntToNodes() {
     *
     * for (HashSet<Integer> nonKeyInt : nonKeysInt) { node nonKeyNode = new
     * node(); nonKeyNode.set = nonKeyInt; nonKeyNode.toExplore = true;
     * nonKeysIntNodes.add(nonKeyNode); } } *
     */
    private void flagChildren(Graph graph, Node candidateKey) {
        // System.out.println("graph1:"+graph);
        //  System.out.println("candidateKey:"+candidateKey);
        if (graph.graph.containsKey(candidateKey)) {
            HashSet<Node> childrenNodes = graph.graph.get(candidateKey);
            for (Node child : childrenNodes) {
                child.toExplore = false;
            }
        }    //    System.out.println("graph2:"+graph);

    }

    private HashMap<Rule, HashSet<String>> discoverConditionalKeysFirstLevel(
    		HashMap<Rule, Graph> ruleToGraph, 
    		HashMap<Integer, Graph> instantiatedProperty2Graph,
    		Set<Rule> output) {
        Rule rule = new Rule();
        for (int conditionProperty : instantiatedProperty2Graph.keySet()) {
            Graph graph = instantiatedProperty2Graph.get(conditionProperty);
            String prop = id2Property.get(conditionProperty);

            Iterable<Rule> conditions = Utilities.getConditions(rule, prop, (int) support, kb);
            for (Rule conditionRule : conditions) {
                Graph newGraph = new Graph();
                discoverConditionalKeysForCondition(newGraph, graph, graph.topGraphNodes(), conditionRule, output);
                ruleToGraph.put(conditionRule, newGraph);
            }
        }

        HashMap<Rule, HashSet<String>> newRuleToExtendWith = new HashMap<>();
        for (Rule conRule : ruleToGraph.keySet()) {
            Graph newGraph = ruleToGraph.get(conRule);
            HashSet<String> properties = new HashSet<>();
            for (Node node : newGraph.topGraphNodes()) {
                if (node.toExplore) {
                    Iterator<Integer> it = node.set.iterator();
                    int prop = it.next();
                    String propertyStr = id2Property.get(prop);
                    properties.add(propertyStr);
                }

            }
            if (properties.size() != 0) {
                newRuleToExtendWith.put(conRule, properties);
            }
        }
        return newRuleToExtendWith;
    }
    
    private HashSet<Integer> getRelations(Rule rule, Map<String, Integer> relation2Id) {
    	List<ByteString> relationsInRule = rule.getAllRelationsBS();
    	HashSet<Integer> result = new HashSet<>();
    	for (ByteString relation : relationsInRule) {
        	Integer id = relation2Id.get(relation.toString());
        	if (id != null) {
        		result.add(id);
        	}
        }
        return result;
	}
    
    /**
     *
     * @param ruleToExtendWith
     * @param ruleToGraphFirstLevel
     * @param ruleToGraphLastLevel
     * @param kb
     */
    private void discoverConditionalKeysPerLevel(HashMap<Rule, HashSet<String>> ruleToExtendWith,
            HashMap<Rule, Graph> ruleToGraphFirstLevel, HashMap<Rule, Graph> ruleToGraphLastLevel,
            Set<Rule> output) {
    	//System.out.println("discoverConditionalKeysPerLevel()");
    	HashMap<Rule, Graph> ruleToGraphThisLevel = new HashMap<>();
        for (Rule currentRule : ruleToExtendWith.keySet()) {
            Graph graph = ruleToGraphLastLevel.get(currentRule);
        	//System.out.println("Current rule: " + currentRule+ " Graph:"+graph);
            for (String conditionProperty : ruleToExtendWith.get(currentRule)) {            	
                if (Utilities.getRelationIds(currentRule, property2Id).last()
                        > property2Id.get(conditionProperty)) {
                    Graph currentGraphNew = (Graph) graph.clone();
                    Integer propertyId = property2Id.get(conditionProperty);
                    HashSet<Integer> propertiesSet = new HashSet<>();
                    propertiesSet.add(propertyId);
                    Node node = currentGraphNew.createOrGetNode(propertiesSet); //Before it was createNode
                    node.toExplore = false;
                    Iterable<Rule> conditions = Utilities.getConditions(currentRule, conditionProperty, (int) support, kb);
                    for (Rule conditionRule : conditions) {
                        Rule complementaryRule = getComplementaryRule(conditionRule);
                        if (!ruleToGraphFirstLevel.containsKey(complementaryRule)) {
                            // We should never fall in this case
                            for (Rule r : ruleToGraphFirstLevel.keySet()) {
                                System.out.println(r.getDatalogBasicRuleString());
                            }
                            System.out.println(complementaryRule.getDatalogBasicRuleString());
                            System.out.println(complementaryRule + " not found in the first level graph");
                        }
                                      	
                        Graph complementaryGraphNew = ruleToGraphFirstLevel.get(complementaryRule);
                        //System.out.println("Complementary rule: " + complementaryRule + "\tThread " + Thread.currentThread().getId() + "\t" + complementaryGraphNew);
                        Graph newGraphNew = (Graph) currentGraphNew.clone();
                        HashSet<Integer> conditionProperties = new HashSet<>();
                        conditionProperties.addAll(getRelations(conditionRule, property2Id));
                        conditionProperties.addAll(getRelations(currentRule, property2Id));
                        //System.out.println("currentGraph:"+currentGraphNew);
                        //System.out.println("clone of currentGraph:"+newGraphNew);
                        newGraphNew = mergeGraphs(newGraphNew, complementaryGraphNew, newGraphNew.topGraphNodes(), conditionProperties);
                        //System.out.println("newMergeGraph:"+newGraphNew);
                        discoverConditionalKeysForComplexConditions(newGraphNew, newGraphNew.topGraphNodes(), conditionRule, output);
                        ruleToGraphThisLevel.put(conditionRule, newGraphNew);
                    }
                }
            }
        }
        HashMap<Rule, HashSet<String>> newRuleToExtendWith = new HashMap<>();
        for (Rule conRule : ruleToGraphThisLevel.keySet()) {
            Graph newGraphNew = ruleToGraphThisLevel.get(conRule);
            for (Node node : newGraphNew.topGraphNodes()) {
                HashSet<String> properties = new HashSet<>();
                if (node.toExplore) {
                    Iterator<Integer> it = node.set.iterator();
                    int prop = it.next();
                    String propertyStr = id2Property.get(prop);
                    properties.add(propertyStr);
                }
                if (properties.size() != 0) {
                    newRuleToExtendWith.put(conRule, properties);
                }
            }
        }

        if (newRuleToExtendWith.size() != 0) {
            discoverConditionalKeysPerLevel(newRuleToExtendWith, ruleToGraphFirstLevel, ruleToGraphThisLevel, output);
        }
        
    	//System.out.println("discoverConditionalKeysPerLevel()");

    }
    
    public void discoverConditionalKeysForComplexConditions(Graph graph, HashSet<Node> candidateKeys, Rule conditionRule, Set<Rule> output) {
        HashSet<Node> childrenCandidateKeys = new HashSet<>();
        //   System.out.println("candidates:" + candidateKeys);

        for (Node candidateKey : candidateKeys) {
            //   System.out.println("candidate:" + candidateKey);
            if (candidateKey.toExplore) {
                //       System.out.println("candidate:" + candidateKey);
                if (candidateKey.toExplore) {
                    List<String> properties = candidateKey.mapToString(id2Property);
                    //      System.out.println("properties:"+properties);
                    Rule amieRule = buildAMIERule(properties, conditionRule);

                    //       System.out.println("amieRule:" + amieRule.getDatalogFullRuleString());
                    boolean isConditionalKey = isConditionaKey(amieRule);
                    if (amieRule.getSupport() < support || isConditionalKey) {
                        candidateKey.toExplore = false;
                        //      System.out.println("key");
                        flagChildren(graph, candidateKey);
                    }

                    // If the rule is a conditional above the support
                    // and there is no a simpler key already discovered
                    // then output it
                    if (isConditionalKey
                            && amieRule.getSupport() >= support
                            && !isSubsumedByKey(amieRule, conditionRule, conditions2Keys)) {
                        synchronized (output) {
							output.add(amieRule);
						}
                        //System.out.println(Utilities.formatKey(amieRule) + "\tThread " + Thread.currentThread().getId());
                        System.out.println(Utilities.formatKey(amieRule));
                        conditions2Keys.put(conditionRule, amieRule);
                    }

                    if (candidateKey.toExplore) {
                        if (graph.graph.containsKey(candidateKey)) {
                            childrenCandidateKeys.addAll(graph.graph.get(candidateKey));
                        }
                    }
                }
            } else {
                flagChildren(graph, candidateKey);
            }
        }//System.out.println("OUT");
        if (!childrenCandidateKeys.isEmpty()) {
            discoverConditionalKeysForComplexConditions(graph, childrenCandidateKeys, conditionRule, output);
        }
    }

    private Rule getComplementaryRule(Rule conditionRule) {
        long cardinality = kb.count(conditionRule.getHead());
        Rule complementaryRule = new Rule(conditionRule.getHead(), cardinality);
        return complementaryRule;
    }
    
    public boolean containsSubSet(HashSet<Integer> allPropertiesSet) {
        boolean contains = false;
        if (nonKeysInt.contains(allPropertiesSet)) {
            return true;
        }
        for (HashSet<Integer> nonKeyInt : nonKeysInt) {
            //   System.out.println("nonKeyInt:"+nonKeyInt);
            if (nonKeyInt.containsAll(allPropertiesSet)) {
                //|| nonKeyInt.equals(allPropertiesSet)) {

                return true;
            }
        }
        return contains;
    }
    
    private Graph mergeGraphs(Graph currentGraph, Graph graph2, HashSet<Node> currentGraphTopNodes, 
    		HashSet<Integer> conditionProperties) {
    	HashSet<Node> childrenNodes = new HashSet<>();
        for (Node currentGraphNewtopNode : currentGraphTopNodes) {
            if (currentGraphNewtopNode.toExplore) {
                //   System.out.println("currentGraphNewtopNode:"+currentGraphNewtopNode);
                if (graph2.graph.containsKey(currentGraphNewtopNode)) {
                    // System.out.println("yes");
                    Node nodeInGraphNew2 = graph2.getNode(currentGraphNewtopNode);
                    if (!nodeInGraphNew2.toExplore) {
                        //   System.out.println("no2");
                        currentGraphNewtopNode.toExplore = false;
                        currentGraph.createOrGetNode(currentGraphNewtopNode);
                    } else {
                        // System.out.println("yes2");
                        HashSet<Integer> allProperties = new HashSet<>();
                        allProperties.addAll(conditionProperties);
                        allProperties.addAll(currentGraphNewtopNode.set);
                        //  System.out.println("allProperties:"+allProperties);
                        if (!containsSubSet(allProperties)) {
                            //  System.out.println("no3");
                            currentGraphNewtopNode.toExplore = false;
                            currentGraph.createOrGetNode(currentGraphNewtopNode);
                        }
                    }
                } else {
                    //   System.out.println("no");
                    currentGraphNewtopNode.toExplore = false;
                    currentGraph.createOrGetNode(currentGraphNewtopNode);
                }
            }

            if (currentGraph.graph.get(currentGraphNewtopNode) != null) {
                childrenNodes.addAll(currentGraph.graph.get(currentGraphNewtopNode));
            }
        }
        if (!childrenNodes.isEmpty()) {
            mergeGraphs(currentGraph, graph2, childrenNodes, conditionProperties);
        }
        return currentGraph;
    }

    
    public static HashSet<HashSet<Integer>> simplifyHashNonKeySet(HashSet<HashSet<Integer>> nonKeySet) {
        HashSet<HashSet<Integer>> newnonKeySet = new HashSet<HashSet<Integer>>();
        newnonKeySet.addAll(nonKeySet);
        for (HashSet set : nonKeySet) {
            for (HashSet set2 : nonKeySet) {
                if (set2 != set && set2.containsAll(set)) {
                    newnonKeySet.remove(set);
                    break;
                }
            }
        }
        return newnonKeySet;
    }
    
    private static List<List<String>> simplifyNonKeysSet(List<List<String>> nonKeySet) {
    	List<List<String>> newnonKeySet = new ArrayList<List<String>>();
        newnonKeySet.addAll(nonKeySet);
        for (List<String> set : nonKeySet) {
            for (List<String> set2 : nonKeySet) {
                if (set2 != set && set2.containsAll(set)) {
                    newnonKeySet.remove(set);
                    break;
                }
            }
        }

        return newnonKeySet;
    }
    
    private static boolean hasFalseParent(HashSet<Integer> newSet2, HashSet<Node> newCandidateKeys) {
        for (Node parent : newCandidateKeys) {
            //          System.out.println("parent:" + parent);
            //   System.out.println("parent.toExplore:" + parent.toExplore);
            //   System.out.println("=>"+newSet2.containsAll(parent.set));
            if (newSet2.containsAll(parent.set) && parent.toExplore == false) {
                //             System.out.println("doesnt enter");
                return true;
            }
        }
        return false;
    }
    
    public boolean getSupport(HashSet<Integer> properties,
            Rule conditionsRule, int support) {
        Rule rule = new Rule(KB.triple("?a", KB.EQUALSstr, "?b"), 0.0);

        int k = 1;
        for (int property : properties) {
            rule.getTriples().add(KB.triple(ByteString.of("?a"), ByteString.of(id2Property.get(property)), ByteString.of("?ob" + k)));
            rule.getTriples().add(KB.triple(ByteString.of("?b"), ByteString.of(id2Property.get(property)), ByteString.of("?ob" + k)));
            ++k;
        }

        for (ByteString[] conditionAtom : conditionsRule.getTriples()) {
            ByteString[] missingAtom = conditionAtom.clone();
            missingAtom[0] = ByteString.of("?b");
            rule.getTriples().add(conditionAtom.clone());
            rule.getTriples().add(missingAtom);
        }

        miningHelper.computeCardinality(rule);
        if ((int) rule.getSupport() < support) {
            return false;
        } else {
            return true;
        }
    }
    
}
