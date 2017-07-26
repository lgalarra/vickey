/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amie.keys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import amie.rules.Rule;
import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import telecom.util.collections.MultiMap;

/**
 *
 * @author symeonid
 */
public class CombinationsExplorationNew {

    // Dictionaries
    static Map<String, Integer> property2Id = new HashMap<>();
    static Map<Integer, String> id2Property = new HashMap<>();

    static HashSet<HashSet<Integer>> nonKeysInt = new HashSet<>();
    //static HashSet<node> nonKeysIntNodes = new HashSet<>();
    static ArrayList<Integer> propertiesList = new ArrayList<>();
    static HashSet<Integer> nonKeyInt1 = new HashSet<>();
    static HashSet<Integer> nonKeyInt2 = new HashSet<>();
    static HashSet<Integer> nonKeyInt3 = new HashSet<>();
    static IntHashMap<ByteString> propertiesSupport = null;
    /**
     * A map where the keys are conditions, e.g., residence=Paris and the values
     * are conditional keys with that condition, e.g., nationality zipCode |
     * residence=Paris; lastname nationality | residence=Paris etc.
     */
    static MultiMap<Rule, Rule> conditions2Keys = new MultiMap<>();
    static MiningAssistant miningHelper = null;
    public static final int defaultMinSupport = 2;
    static int support = defaultMinSupport;
    public static long numberOfInstances = 0;
    static int keyCounter = 0;
    static KB kb;

    /**
     * Parses the command line arguments and the returns an object that maps
     * each argument to its value.
     *
     * @param args
     * @return
     * @throws IOException
     */
    public static CommandLine parseArguments(String[] args) throws IOException {
        HelpFormatter formatter = new HelpFormatter();

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

        options.addOption(supportOpt);
        options.addOption(ratioOpt);
        options.addOption(nonKeysOpt);

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
        kb = amie.data.U.loadFiles(fileNameArgs);
        if (cli.hasOption("mins")) {
            try {
                support = Integer.parseInt(cli.getOptionValue("mins"));
            } catch (NumberFormatException e) {
                System.out.println("Unexpected exception: " + e.getMessage());
                formatter.printHelp("CombinationsExploration [OPTIONS] <TSV FILES>", options);
                System.exit(1);
            }
        }

        if (cli.hasOption("p")) {
            System.out.println("Support interpreted as a " + support + "% of the number of entities.");
            long numberOfInstances = kb.size(Column.Subject);
            System.out.println(numberOfInstances + " instances found as subjects in the KB.");
            support = (int) Math.ceil(numberOfInstances * support / 100.0);
        }

        System.out.println("Using minimum support " + support);

        return cli;
    }

    public static void main(String[] args) throws IOException {
        CommandLine cli = parseArguments(args);

        // We get a map with the properties and their number of subjects
        propertiesSupport = kb.frequentBindingsOf(ByteString.of("?p"), ByteString.of("?s"),
                KB.triples(KB.triple("?s", "?p", "?o")));
        // Helper object that contains the implementation for the calculation
        // of confidence and support
        numberOfInstances = kb.size(Column.Subject);
        miningHelper = new DefaultMiningAssistant(kb);
        // The file with the non-keys, one per line
        List<List<String>> nonKeys = new ArrayList<>();
        for (List<String> nonKey : Utilities.parseNonKeysFile(cli.getOptionValue("nk"))) {
            if (nonKey.size() > 1) {
                nonKeys.add(nonKey);
            }
        }
        System.out.println("nonKeys" + nonKeys);
        // for(List<String> nonKey:nonKeys){
        //     System.out.println(nonKey);
        //}
        long timea = System.currentTimeMillis();
        // System.out.println("nonKeys:"+nonKeys);
        // We map the non-keys to an integer space
        nonKeysInt = buildDictionaries(nonKeys, propertiesList);
     //   System.out.println(nonKeysInt.size() + " => nonKeysInt:" + nonKeysInt);
        //System.out.println("prop:"+property2Id);
        System.out.println("Dictionaries built");
        // We discover the conditional keys
        discoverConditionalKeys();
        long timeb = System.currentTimeMillis();
        System.out.println("Total time in ms: " + (timeb - timea));
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
    private static Rule buildAMIERule(List<String> properties,
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

        miningHelper.computeCardinality(rule);
        rule.setSupportRatio(rule.getSupport() / (double) numberOfInstances);
        miningHelper.computeStandardConfidence(rule);

        return rule;
    }

    public static boolean getSupport(HashSet<Integer> properties,
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
        //System.out.println("computing cardinality of " + rule);
        miningHelper.computeCardinality(rule);
        if ((int) rule.getSupport() < support) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Construction of the maps from properties -> id and id -> properties
     */
    private static HashSet<HashSet<Integer>> buildDictionaries(
            List<List<String>> nonKeys, List<Integer> propertiesList) {
        int id = 0;
        HashSet<HashSet<Integer>> result = new HashSet<>();
        for (List<String> nonKey : nonKeys) {
            //    System.out.println("nonkey:" + nonKey);
            HashSet<Integer> nonKeyInt = new HashSet<>();
            for (int k = 0; k < nonKey.size(); ++k) {
                // Ignore 
                String property = nonKey.get(k);
                // boolean supportCutProperty =false;
//                if (getPropertySupport(property) < support) {
              //      System.out.println("getPropertySupport(property):"+getPropertySupport(property));
                    //   supportCutProperty = true;
                    //  System.out.println("ee");
//                    continue;
//                }
                
                //**** Debugging code ***/
            	/**List<String> testingProperties = Arrays.asList("db:campus", "db:mascot", "db:officialschoolcolour", "db:athletics", "db:country");       	
                if (getPropertySupport(property) < support || !testingProperties.contains(property)) {
                	continue;
                }**/
                //**** Debugging code ***/
                
                if (!property2Id.containsKey(property)) {
                    property2Id.put(property, id);
                    id2Property.put(id, property);
                    // if(!supportCutProperty){
                    propertiesList.add(id);//}
                    ++id;
                }
                Integer idProperty = property2Id.get(property);
                nonKeyInt.add(idProperty);
            }
            //System.out.println("propertyList:"+propertiesList);
            if (nonKeyInt.size() != 0) {
                result.add(nonKeyInt);
                //   System.out.println("nonKeyInt!:" + nonKeyInt + " " + nonKeyInt.size() + "\n");
            }
        }
        // System.out.println("id2Property:"+id2Property);
        // System.out.println("nonKeysInt:" + result.size() + "\n");

        result = simplifyHashNonKeySet(result);
        //System.out.println("nonKeysInt:" + result.size() + "\n");
        return result;
    }

    /**
     * It outputs all the conditional keys on a KB given a minimum support
     * threshold.
     *
     * @param support
     * @param kb
     */
    public static void discoverConditionalKeys() {
        HashMap<Rule, GraphNew> ruleToGraphNewFirstLevel = new HashMap<>();
        /**
         * We build a graph for each property. The graph contains nodes for each
         * of the other properties and all its possible combinations. For
         * instance given the relations 1, 2, 3, 4. The graph for property 1
         * contains as nodes: 2, 3, 4, 23, 24, 34, 234 with their corresponding
         * parent relationships, e.g., 23 has 2 and 3 as parents. While
         * relations are strings, we have mapped them to an integer space (that
         * is why the key is an integer)
         *
         */
        //System.out.println("idToPro:"+id2Property);
        HashMap<Integer, GraphNew> instantiatedProperty2GraphNew = buildPropertyGraphsOnTheFly((int) support);
        System.out.println("Key\tCondition\tSupport");
        /**
         * Here we find all conditional keys when the condition has size 1,
         * i.e., it involves only one property such as residence=Paris |
         * zipCode, field. The map contains the conditions as keys and the list
         * of potential properties that could be used to extend this condition
         * as values.
         */
        HashMap<Rule, HashSet<String>> conditionsToPotentialExtensions
                = discoverConditionalKeysFirstLevel(ruleToGraphNewFirstLevel, instantiatedProperty2GraphNew);
        /**for (Rule rule : ruleToGraphNewFirstLevel.keySet()) {
            System.out.println("rule:" + rule + " => " + ruleToGraphNewFirstLevel.get(rule));
        }**/
        if (conditionsToPotentialExtensions.size() != 0) {
            discoverConditionalKeysPerLevel(conditionsToPotentialExtensions, ruleToGraphNewFirstLevel, ruleToGraphNewFirstLevel);
        }
        System.out.println("We found " + conditions2Keys.valueSize() + " key(s)");

    }

    public static HashMap<Integer, GraphNew> buildPropertyGraphsOnTheFly(int support) {
        HashMap<Integer, GraphNew> instantiatedProperty2GraphNew = new HashMap<>();
        for (int instantiatedProperty : propertiesList) {
        	/**** Debugging code ***/
        	/**List<String> testingProperties = Arrays.asList("db:athletics", "db:country");       	
        	if (!testingProperties.contains(id2Property.get(instantiatedProperty)))
        		continue;**/
        	/**** Debugging code ***/
            GraphNew graph = new GraphNew(nonKeysInt, propertiesList, instantiatedProperty);
            instantiatedProperty2GraphNew.put(instantiatedProperty, graph);
        }

        return instantiatedProperty2GraphNew;
    }

    private static int getPropertySupport(String property) {
        int propertySupport = propertiesSupport.get(ByteString.of(property));
        if (propertySupport != -1) {
            return propertySupport;
        }
        return 0;
    }

    public static void discoverConditionalKeysForCondition(GraphNew newGraph, GraphNew graph, HashSet<Node> candidateKeys, Rule conditionRule) {
        HashSet<Node> newCandidateKeys = new HashSet<>();
        for (Node candidateKey : candidateKeys) {
            //  System.out.println("candidateKey:" + candidateKey);
            if (candidateKey.toExplore) {
                //   System.out.println("candidate:" + candidateKey);
                // if (candidateKey.toExplore) {
                List<String> properties = candidateKey.mapToString(id2Property);
                Rule amieRule = buildAMIERule(properties, conditionRule);
                boolean isConditionalKey = isConditionaKey(amieRule);

                if (amieRule.getSupport() >= support && !isConditionalKey) {
                	//System.out.println("Case 0");
                    if (!newGraph.graph.containsKey(candidateKey)) {
                    	//System.out.println("Case 1");
                        Node newCandidateKey = candidateKey.clone();
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(newCandidateKey, children);
                        newGraph.nodes.put(newCandidateKey, newCandidateKey);
                        newCandidateKeys.add(newCandidateKey);
                    } else {
                    	//System.out.println("Case 2");
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
                        System.out.println(Utilities.formatKey(amieRule));
                        conditions2Keys.put(conditionRule, amieRule);
                        newCandidateKey.toExplore = false;
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(newCandidateKey, children);
                        newGraph.nodes.put(newCandidateKey, newCandidateKey);
                        newCandidateKeys.add(newCandidateKey);
                    	//System.out.println("Case 3");
                    } else {
                        System.out.println(Utilities.formatKey(amieRule));
                        conditions2Keys.put(conditionRule, amieRule);
                        candidateKey.toExplore = false;
                        HashSet<Node> children = new HashSet<>();
                        newGraph.graph.put(candidateKey, children);
                        newGraph.nodes.put(candidateKey, candidateKey);
                        newCandidateKeys.add(candidateKey);
                    	//System.out.println("Case 4");
                    }

                    // System.out.println(Utilities.formatKey(amieRule));
                    //conditions2Keys.put(conditionRule, amieRule);
                    // candidateKey.toExplore = false;
                }
            } else {
            	//System.out.println("Case 5");
                newCandidateKeys.add(candidateKey);
            }
        }
     //   System.out.println("newGraphBefore:" + newGraph);

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
                    if ((newSet.size() == parent1.set.size() + 1) && 
                    		(getSupport(newSet, conditionRule, support)) && GraphNew.containsASuperSetOf(CombinationsExplorationNew.nonKeysInt, condProp_KeyProp) != -1) {
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
            discoverConditionalKeysForCondition(newGraph, newGraph, allChildren, conditionRule);
        }
    }

    public static void discoverConditionalKeysForComplexConditions(GraphNew graph, HashSet<Node> candidateKeys, Rule conditionRule) {
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
        }
        if (!childrenCandidateKeys.isEmpty()) {
            discoverConditionalKeysForComplexConditions(graph, childrenCandidateKeys, conditionRule);
        }
    }

    /**
     * It determines whether there exists a more general version of the given
     * conditional key, a version of the key with the exact same relations but
     * fewer instantiations. For instance if the key states lastname |
     * nationality=French, field=Databases but the map contains a key lastname
     * nationality | field=Databases (here nationality is not instantiated), the
     * method will report this a subsumption case and return true.
     *
     * @param conditionalKey
     * @param conditionRule
     * @param conditions2Keys2
     * @return
     */
    private static boolean isSubsumedByKey(Rule conditionalKey, Rule conditionRule,
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

    private static List<Rule> getAllProperSubconditions(Rule conditionRule) {
        int numberOfConditions = conditionRule.getLength();
        List<Rule> results = new ArrayList<>();
        for (int size = 1; size < numberOfConditions; ++size) {
            List<int[]> combinations
                    = telecom.util.collections.Collections.subsetsOfSize(numberOfConditions, size);
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

    private static boolean isConditionaKey(Rule amieRule) {
        return amieRule.getStdConfidence() == 1.0;
    }

    public static HashSet<HashSet<Integer>> buidPropertyGraphNew(int property) {
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

    public static HashSet<HashSet<Integer>> powerSet(HashSet<Integer> originalSet) {
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
    private static void flagChildren(GraphNew graph, Node candidateKey) {
        // System.out.println("graph1:"+graph);
        //  System.out.println("candidateKey:"+candidateKey);
        if (graph.graph.containsKey(candidateKey)) {
            HashSet<Node> childrenNodes = graph.graph.get(candidateKey);
            for (Node child : childrenNodes) {
                child.toExplore = false;
            }
        }    //    System.out.println("graph2:"+graph);

    }

    private static HashMap<Rule, HashSet<String>> discoverConditionalKeysFirstLevel(
            HashMap<Rule, GraphNew> ruleToGraphNew,
            HashMap<Integer, GraphNew> instantiatedProperty2GraphNew) {
        Rule rule = new Rule();
        for (int conditionProperty : instantiatedProperty2GraphNew.keySet()) {
            GraphNew graph = instantiatedProperty2GraphNew.get(conditionProperty);
            String prop = id2Property.get(conditionProperty);

            Iterable<Rule> conditions = Utilities.getConditions(rule, prop, (int) support, kb);
            for (Rule conditionRule : conditions) {
                GraphNew newGraph = new GraphNew();
                discoverConditionalKeysForCondition(newGraph, graph, graph.topGraphNodes(), conditionRule);
                if (newGraph != null) {
                    ruleToGraphNew.put(conditionRule, newGraph);
                }
            }
        }

        HashMap<Rule, HashSet<String>> newRuleToExtendWith = new HashMap<>();
        for (Rule conRule : ruleToGraphNew.keySet()) {
            GraphNew newGraph = ruleToGraphNew.get(conRule);
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

    /**
     *
     * @param ruleToExtendWith
     * @param ruleToGraphNewFirstLevel
     * @param ruleToGraphNewLastLevel
     * @param kb
     */
    private static void discoverConditionalKeysPerLevel(HashMap<Rule, HashSet<String>> ruleToExtendWith,
            HashMap<Rule, GraphNew> ruleToGraphNewFirstLevel, HashMap<Rule, GraphNew> ruleToGraphNewLastLevel) {
        HashMap<Rule, GraphNew> ruleToGraphNewThisLevel = new HashMap<>();
        for (Rule currentRule : ruleToExtendWith.keySet()) {
            for (String conditionProperty : ruleToExtendWith.get(currentRule)) {
                if (Utilities.getRelationIds(currentRule, property2Id).last()
                        > property2Id.get(conditionProperty)) {
                    GraphNew graph = ruleToGraphNewLastLevel.get(currentRule);
                    GraphNew currentGraphNew = (GraphNew) graph.clone();
                    Integer propertyId = property2Id.get(conditionProperty);
                    HashSet<Integer> propertiesSet = new HashSet<>();
                    propertiesSet.add(propertyId);
                    Node node = currentGraphNew.createNode(propertiesSet);
                    node.toExplore = false;
                    Iterable<Rule> conditions = Utilities.getConditions(currentRule, conditionProperty, (int) support, kb);
                    for (Rule conditionRule : conditions) {
                        Rule complementaryRule = getComplementaryRule(conditionRule);
                        if (!ruleToGraphNewFirstLevel.containsKey(complementaryRule)) {
                            // We should never fall in this case
                            for (Rule r : ruleToGraphNewFirstLevel.keySet()) {
                                System.out.println(r.getDatalogBasicRuleString());
                            }
                            System.out.println(complementaryRule.getDatalogBasicRuleString());
                            System.out.println(complementaryRule + " not found in the first level graph");
                        }
                        GraphNew complementaryGraphNew = ruleToGraphNewFirstLevel.get(complementaryRule);
                        GraphNew newGraphNew = (GraphNew) currentGraphNew.clone();
                        HashSet<Integer> conditionProperties = new HashSet<>();
                        conditionProperties.addAll(getRelations(conditionRule, property2Id));
                        conditionProperties.addAll(getRelations(currentRule, property2Id));
                        newGraphNew = mergeGraphNews(newGraphNew, complementaryGraphNew, newGraphNew.topGraphNodes(), conditionProperties);
                    
                        discoverConditionalKeysForComplexConditions(newGraphNew, newGraphNew.topGraphNodes(), conditionRule);
                        ruleToGraphNewThisLevel.put(conditionRule, newGraphNew);
                    }
                }
            }
        }
        HashMap<Rule, HashSet<String>> newRuleToExtendWith = new HashMap<>();
        for (Rule conRule : ruleToGraphNewThisLevel.keySet()) {
            GraphNew newGraphNew = ruleToGraphNewThisLevel.get(conRule);
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
            discoverConditionalKeysPerLevel(newRuleToExtendWith, ruleToGraphNewFirstLevel, ruleToGraphNewThisLevel);
        }
    }

    private static Rule getComplementaryRule(Rule conditionRule) {
        long cardinality = kb.count(conditionRule.getHead());
        Rule complementaryRule = new Rule(conditionRule.getHead(), cardinality);
        return complementaryRule;
    }

    private static GraphNew mergeGraphNews(GraphNew currentGraphNew, GraphNew graph2, HashSet<Node> currentGraphNewTopNodes, HashSet<Integer> conditionProperties) {
        HashSet<Node> childrenNodes = new HashSet<>();
        for (Node currentGraphNewtopNode : currentGraphNewTopNodes) {
            if (currentGraphNewtopNode.toExplore) {
                //   System.out.println("currentGraphNewtopNode:"+currentGraphNewtopNode);
                if (graph2.graph.containsKey(currentGraphNewtopNode)) {
                    // System.out.println("yes");
                    Node nodeInGraphNew2 = graph2.getNode(currentGraphNewtopNode);
                    if (!nodeInGraphNew2.toExplore) {
                        //   System.out.println("no2");
                        currentGraphNewtopNode.toExplore = false;
                        currentGraphNew.getAndCreateNode(currentGraphNewtopNode);
                    } else {
                        // System.out.println("yes2");
                        HashSet<Integer> allProperties = new HashSet<>();
                        allProperties.addAll(conditionProperties);
                        allProperties.addAll(currentGraphNewtopNode.set);
                        //  System.out.println("allProperties:"+allProperties);
                        if (!containsSubSet(allProperties)) {
                            //  System.out.println("no3");
                            currentGraphNewtopNode.toExplore = false;
                            currentGraphNew.getAndCreateNode(currentGraphNewtopNode);
                        }
                    }
                } else {
                    //   System.out.println("no");
                    currentGraphNewtopNode.toExplore = false;
                    currentGraphNew.getAndCreateNode(currentGraphNewtopNode);
                }
            }

            if (currentGraphNew.graph.get(currentGraphNewtopNode) != null) {
                childrenNodes.addAll(currentGraphNew.graph.get(currentGraphNewtopNode));
            }
        }
        if (!childrenNodes.isEmpty()) {
            mergeGraphNews(currentGraphNew, graph2, childrenNodes, conditionProperties);
        }
        return currentGraphNew;
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

    public static boolean containsSubSet(HashSet<Integer> allPropertiesSet) {
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

    private static HashSet<Integer> getRelations(Rule rule, Map<String, Integer> relation2Id) {
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

    public static int support(HashSet<Integer> propertyIds,
            Map<Integer, String> ids2Properties, KB kb) {
        List<ByteString[]> query = new ArrayList<>();
        ByteString var = ByteString.of("?s");
        int k = 0;
        for (Integer id : propertyIds) {
            String relation = ids2Properties.get(id);
            if (relation == null) {
                throw new IllegalArgumentException("No property with id " + id);
            }
            query.add(KB.triple(var, ByteString.of(relation),
                    ByteString.of("?o" + k)));
            ++k;
        }

        return (int) kb.countDistinct(var, query);
    }

    private static HashSet<Node> createChildren(GraphNew newGraph, HashSet<Node> parents, Rule conditionRule) {
        HashSet<Node> allChildren = new HashSet<>();
        for (Node parent1 : parents) {
            for (Node parent2 : parents) {
                if (parent1 != parent2) {
                    HashSet<Integer> newSet = new HashSet<>();
                    newSet.addAll(parent1.set);
                    newSet.addAll(parent2.set);
                    HashSet<Integer> newSet2 = new HashSet<>();
                    newSet2.addAll(newSet);
                    newSet2.addAll(newSet);
                    if ((newSet.size() == parent1.set.size() + 1) && (getSupport(newSet2, conditionRule, support))) {
                        Node child = new Node(newSet);
                        HashSet<Node> children1 = newGraph.graph.get(parent1);
                        children1.add(child);
                        newGraph.nodes.put(child, child);
                        newGraph.graph.put(parent1, children1);
                        HashSet<Node> children2 = newGraph.graph.get(parent2);
                        children2.add(child);
                        newGraph.graph.put(parent2, children2);
                        allChildren.add(child);
                        //System.out.println("child:"+child);
                    }
                }
            }
        }
        //   System.out.println("allChildren:" + allChildren);
        return allChildren;
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
}
