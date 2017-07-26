package amie.data.eval;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import javatools.datatypes.Triple;
import amie.data.KB;
import amie.rules.AMIEParser;
import amie.rules.Rule;

/**
 * This class defines objects that take rules extracted from a training 
 * KB and produce a sample of the predictions made by the rules, i.e., triples 
 * that are not in the training source.
 * 
 * @author lgalarra
 *
 */
public class Predictor {
	/**
	 * Sample size
	 */
	private int sampleSize;
	
	/**
	 * Input dataset
	 */
	private KB source;
	
	public Predictor(KB dataset) {
		super();
		this.source = dataset;
		sampleSize = 30;
	}
	
	public Predictor(KB dataset, int sampleSize) {
		super();
		this.source = dataset;
		this.sampleSize = sampleSize;
	}
	
	/**
	 * @return the sampleSize
	 */
	public int getSampleSize() {
		return sampleSize;
	}

	/**
	 * @param sampleSize the sampleSize to set
	 */
	public void setNumberOfPredictions(int numberOfPredictions) {
		this.sampleSize = numberOfPredictions;
	}
		
	
	public Set<Triple<ByteString, ByteString, ByteString>> generateBodyTriples(Rule rule, boolean PCAMode) {
		Object bindings = null;
		if (PCAMode) {
			bindings = generateBodyPCABindings(rule);
		} else {
			bindings = generateBodyBindings(rule);
		}
		
		Set<Triple<ByteString, ByteString, ByteString>> triples = new LinkedHashSet<>();
		ByteString[] head = rule.getHead();
		ByteString relation = rule.getHead()[1];
		
		if (KB.numVariables(rule.getHead()) == 1) {
			Set<ByteString> constants = (Set<ByteString>) bindings;
			int variablePosition = rule.getFunctionalVariablePosition();
			for (ByteString constant : constants) {
				if (variablePosition == 0) {
					triples.add(new Triple<>(constant, relation, head[2]));
				} else {
					triples.add(new Triple<>(head[0], relation, constant));					
				}
			}
		} else {
			Map<ByteString, IntHashMap<ByteString>> pairs = (Map<ByteString, IntHashMap<ByteString>>) bindings; 
			int functionalPosition = rule.getFunctionalVariablePosition();
			for (ByteString subject : pairs.keySet()) {
				for (ByteString object : pairs.get(subject)) {
					if (functionalPosition == 0) {
						triples.add(new Triple<>(subject, relation, object));
					} else {
						triples.add(new Triple<>(object, relation, subject));						
					}
				}
			}
		}
		
		return triples;
	}
	
	/**
	 * Given a rule, it produces a sample from the body bindings of the rule.
	 * 
	 * @param rule
	 * @return
	 */
	public Object generateBodyBindings(Rule rule){
		if(KB.numVariables(rule.getHead()) == 1)
			return generateBindingsForSingleVariable(rule);
		else if (KB.numVariables(rule.getHead()) == 2)
			return generateBindingsForTwoVariables(rule);
		else 
			return generateBindingsForThreeVariables(rule);
	}
	
	private Object generateBindingsForThreeVariables(Rule rule) {
		ByteString[] head = rule.getHead();
		return source.selectDistinct(rule.getFunctionalVariable(), 
				head[1], rule.getNonFunctionalVariable(), rule.getAntecedent());
	}

	private Object generateBindingsForTwoVariables(Rule rule) {
		return source.selectDistinct(rule.getFunctionalVariable(), 
				rule.getNonFunctionalVariable(), rule.getAntecedent());
	}

	private Object generateBindingsForSingleVariable(Rule rule) {
		return source.selectDistinct(rule.getFunctionalVariable(), rule.getAntecedent());
	}
	
	/**
	 * Given a rule, it produces a sample from the body* bindings
	 * of the rule (the bindings that match the denominator of the
	 * PCA confidence expression)
	 * @param rule
	 * @return
	 */
	public Object generateBodyPCABindings(Rule rule) {
		if(KB.numVariables(rule.getHead()) == 1)
			return generatePCABindingsForSingleVariable(rule);
		else
			return generatePCABindingsForTwoVariables(rule);		
		
	}
	
	private Object generatePCABindingsForSingleVariable(Rule rule) {
		return source.selectDistinct(rule.getFunctionalVariable(), rule.getPCAQuery());
	}

	private Object generatePCABindingsForTwoVariables(Rule rule) {
		// TODO Auto-generated method stub
		return source.selectDistinct(rule.getFunctionalVariable(),
				rule.getNonFunctionalVariable(), rule.getPCAQuery());
	}

	/**
	 * Given a rule, it produces sample of predictions (triples that are beyond the database)
	 * 
	 * @param rule
	 * @return
	 */
	public Object generatePredictions(Rule rule){		
		if(KB.numVariables(rule.getHead()) == 1)
			return predictBindingsForSingleVariable(rule);
		else
			return predictBindingsForTwoVariables(rule);
	}
	
	private Set<ByteString> predictBindingsForSingleVariable(Rule rule) {
		//First get the bindings for the projection variable in the antecedent
		return source.difference(rule.getFunctionalVariable(), rule.getAntecedent(), rule.getTriples());
	}
	
	private Map<ByteString, IntHashMap<ByteString>> predictBindingsForTwoVariables(Rule rule) {
		return source.difference(rule.getFunctionalVariable(), 
				rule.getNonFunctionalVariable(), rule.getAntecedent(), rule.getTriples());
	}
	
	/**
	 * It takes a sample of unique predictions for the given rules by taking care of duplicated predictions.
	 * If a prediction was generated before by a rule, then it is not considered in the sampling.
	 * 
	 * @param rules
	 */
	public void runMode1(Collection<Rule> rules) {
		Map<ByteString, Map<ByteString, Set<ByteString>>> allPredictions = 
				new HashMap<ByteString, Map<ByteString, Set<ByteString>>>();
		
		for(Rule rule: rules){
			Object predictions = generatePredictions(rule);
			Collection<Triple<ByteString, ByteString, ByteString>> newPredictions = 
					samplePredictions(predictions, rule, allPredictions);
			printPredictions(rule, newPredictions);
		}
	}		
		
	/**
	 * It takes a sample of the predictions of every rule in the argument. Unlike the method runMode1, the result can contain duplicates
	 * if two rules predict the same fact and this fact is considered in both samples.
	 * 
	 * @param rules
	 */
	public void runMode2(Collection<Rule> rules){
		for(Rule rule: rules){
			Object predictions = generatePredictions(rule);
			Collection<Triple<ByteString, ByteString, ByteString>> newPredictions = 
					samplePredictions(predictions, rule);
			printPredictions(rule, newPredictions);
		}		
	}

	private Collection<Triple<ByteString, ByteString, ByteString>> samplePredictions(Object predictions, Rule rule) {
		// TODO Auto-generated method stub
		int nVars = KB.numVariables(rule.getHead());
		if(nVars == 2){
			return samplePredictionsTwoVariables((Map<ByteString, IntHashMap<ByteString>>)predictions, rule);
		}else if(nVars == 1){
			return samplePredictionsOneVariable((Set<ByteString>)predictions, rule);			
		}
		
		return null;
	}

	private Collection<Triple<ByteString, ByteString, ByteString>> samplePredictionsOneVariable(Set<ByteString> predictions, Rule rule) {
		// TODO Auto-generated method stub
		return null;
	}

	private Collection<Triple<ByteString, ByteString, ByteString>> 
	samplePredictionsTwoVariables(Map<ByteString, IntHashMap<ByteString>> predictions, Rule rule) {
		Set<ByteString> keySet = predictions.keySet();
		ByteString relation = rule.getHead()[1];
		//Depending on the counting variable the order is different
		int countingVarPos = rule.getFunctionalVariablePosition();
		Set<Triple<ByteString, ByteString, ByteString>> samplingCandidates = 
				new LinkedHashSet<Triple<ByteString, ByteString, ByteString>>();
		
		for(ByteString value1: keySet){
			for(ByteString value2: predictions.get(value1)){
				Triple<ByteString, ByteString, ByteString> triple = 
						new Triple<ByteString, ByteString, ByteString>(null, null, null);
				
				if(value1.equals(value2)) continue;
				
				if(countingVarPos == 0){
					triple.first = value1;
					triple.third = value2;
				}else{
					triple.first = value2;
					triple.third = value1;					
				}
				
				triple.second = relation;
				samplingCandidates.add(triple);
			}			
		}
		
		return Predictor.sample(samplingCandidates, this.sampleSize);
	}
	

	private void printPredictions(Rule rule, Collection<Triple<ByteString, ByteString, ByteString>> newPredictions) {
		for(Triple<ByteString, ByteString, ByteString> triple: newPredictions){
			System.out.println(rule.getRuleString() + "\t" + triple.first + "\t" + triple.second + "\t" + triple.third);
		}
	}

	/**
	 * 
	 * @param predictions
	 * @param rule
	 */
	private Collection<Triple<ByteString, ByteString, ByteString>> samplePredictions(
			Object predictions, Rule rule, Map<ByteString, Map<ByteString, Set<ByteString>>> allPredictions) {
		// TODO Auto-generated method stub
		int nVars = KB.numVariables(rule.getHead());
		if(nVars == 2){
			return samplePredictionsTwoVariables((Map<ByteString, IntHashMap<ByteString>>)predictions, rule, allPredictions);
		}else if(nVars == 1){
			return samplePredictionsOneVariable((Set<ByteString>)predictions, rule, allPredictions);			
		}
		
		return null;
	}

	private Collection<Triple<ByteString, ByteString, ByteString>> samplePredictionsOneVariable(Set<ByteString> predictions,
			Rule rule,
			Map<ByteString, Map<ByteString, Set<ByteString>>> allPredictions) {
		// TODO Auto-generated method stub
		return null;
		
	}

	private Collection<Triple<ByteString, ByteString, ByteString>> samplePredictionsTwoVariables(
			Map<ByteString, IntHashMap<ByteString>> predictions, 
			Rule rule, Map<ByteString, Map<ByteString, Set<ByteString>>> allPredictions){
		Set<ByteString> keySet = predictions.keySet();
		ByteString relation = rule.getHead()[1];
		//Depending on the counting variable the order is different
		int countingVarPos = rule.getFunctionalVariablePosition();
		Set<Triple<ByteString, ByteString, ByteString>> samplingCandidates = 
				new LinkedHashSet<Triple<ByteString, ByteString, ByteString>>();
		
		for(ByteString value1: keySet){
			for(ByteString value2: predictions.get(value1)){
				Triple<ByteString, ByteString, ByteString> triple = 
						new Triple<ByteString, ByteString, ByteString>(null, null, null);
				
				if(value1.equals(value2)) continue;
				
				if(countingVarPos == 0){
					triple.first = value1;
					triple.third = value2;
				}else{
					triple.first = value2;
					triple.third = value1;					
				}
				
				triple.second = relation;
				
				if(!containsPrediction(allPredictions, triple)){
					samplingCandidates.add(triple);
				}
				
				addPrediction(allPredictions, triple);
			}			
		}
		
		return Predictor.sample(samplingCandidates, sampleSize);
	}
	
	/**
	 * Adds a prediction to the index of already generated predictions. It does not check whether the triple
	 * already exists in the set.
	 * @param allPredictions
	 * @param triple
	 */
	private void addPrediction(Map<ByteString, Map<ByteString, Set<ByteString>>> allPredictions, 
			Triple<ByteString, ByteString, ByteString> triple) {
		if(allPredictions.containsKey(triple.second)){
			Map<ByteString, Set<ByteString>> subjects = allPredictions.get(triple.second);
			if(subjects.containsKey(triple.first)){
				subjects.get(triple.first).add(triple.third);
			}else{
				Set<ByteString> objects = new HashSet<ByteString>();
				objects.add(triple.third);
				subjects.put(triple.first, objects);
			}
		}else{
			Set<ByteString> objects = new HashSet<ByteString>();
			objects.add(triple.third);
			Map<ByteString, Set<ByteString>> subjects = new HashMap<ByteString, Set<ByteString>>();
			subjects.put(triple.first, objects);
			allPredictions.put(triple.second, subjects);
		}
	}

	private boolean containsPrediction(Map<ByteString, Map<ByteString, Set<ByteString>>> allPredictions, 
			Triple<ByteString, ByteString, ByteString> triple) {
		// TODO Auto-generated method stub
		Map<ByteString, Set<ByteString>> subjects2objects = allPredictions.get(triple.second);
		if(subjects2objects != null){
			Set<ByteString> objects = subjects2objects.get(triple.first);
			if(objects != null){
				return objects.contains(triple.third);
			}
			return false;
		}
		
		return false;
	}

	/**
	 * Given a collection of predictions, it extracts a random sample from it.
	 * 
	 * @param samplingCandidates
	 * @return
	 */
	public static Collection<Triple<ByteString, ByteString, ByteString>> sample(Collection<Triple<ByteString, ByteString, ByteString>> samplingCandidates,
			int sampleSize){
		//Now sample them
		List<Triple<ByteString, ByteString, ByteString>> result = new ArrayList<>(sampleSize);		
		if(samplingCandidates.size() <= sampleSize){
			return samplingCandidates;
		}else{
			Object[] candidates = samplingCandidates.toArray();
			int i;
			Random r = new Random();
			for(i = 0; i < sampleSize; ++i){				
				result.add((Triple<ByteString, ByteString, ByteString>)candidates[i]);
			}
			
			while(i < candidates.length){
			    int rand = r.nextInt(i);
			    if(rand < sampleSize){
			    	//Pick a random number in the reserviour
			    	result.set(r.nextInt(sampleSize), (Triple<ByteString, ByteString, ByteString>)candidates[i]);
			    }
			    ++i;
			}
		}
		
		return result;
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		KB trainingSource = new KB();
		
		if(args.length < 4){
			System.err.println("PredictionsSampler <db> <samplesPerRule> <unique> <rules>");
			System.err.println("db\tAn RDF knowledge base");
			System.err.println("samplesPerRule\tSample size per rule. It defines the number of facts that will be randomly taken from the entire set of predictions made a each rule");
			System.err.println("unique (0|1)\tIf 1, predictions that were generated by other rules before, are not output");
			System.err.println("rules\tFile containing each rule per line, as they are output by AMIE.");
			System.exit(1);
		}
		
		trainingSource.load(new File(args[0]));
		
		int sampleSize = Integer.parseInt(args[1]);
		int mode = Integer.parseInt(args[2]);
	
		List<Rule> rules = new ArrayList<Rule>();
		for(int i = 3; i < args.length; ++i){		
			rules.addAll(AMIEParser.rules(new File(args[i])));
		}
		
		for(Rule rule: rules){
			if(trainingSource.functionality(rule.getHead()[1]) >= trainingSource.inverseFunctionality(rule.getHead()[1]))
				rule.setFunctionalVariablePosition(0);
			else
				rule.setFunctionalVariablePosition(2);
		}
		
		Predictor pp = new Predictor(trainingSource, sampleSize);
		if(mode == 1)
			pp.runMode1(rules); //Considered previous samples
		else
			pp.runMode2(rules); //Independent for each rule
	}



}