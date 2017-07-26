package amie.mining.assistant;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import amie.data.KB;
import amie.rules.ConfidenceMetric;
import amie.rules.Metric;
import amie.rules.Rule;
import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import javatools.datatypes.MultiMap;
import javatools.datatypes.Pair;

/**
 * Simpler miner assistant which implements all the logic required 
 * to mine conjunctive rules from a RDF datastore. Subclasses are encouraged
 * to override the following methods:
 * <ul>
 * <li>getInitialAtomsFromSeeds</li>
 * <li>getInitialAtoms</li>
 * <li>getDanglingAtoms</li>
 * <li>getClosingAtoms</li>
 * <li>getInstantiatedAtoms</li>
 * </ul>
 * @author lgalarra
 *
 */
public class MiningAssistant{
	
	/**
	 * Maximum number of times a relation can appear in 
	 * a rule.
	 */
	protected int recursivityLimit = 3;
	
	/**
	 * Factory object to instantiate query components
	 */
	protected KB kb;
	
	/**
	 * Exclusively used for schema information, such as subclass and sub-property
	 * relations or relation signatures.
	 */
	protected KB kbSchema;
	
	/**
	 * Number of different objects in the underlying dataset
	 */
	protected long totalObjectCount;

	/**
	 * Number of different subjects in the underlying dataset
	 */
	protected long totalSubjectCount;
	
	/**
	 * Type keyword
	 */
	protected ByteString typeString;
	
	/**
	 * Subproperty keyword
	 */
	protected ByteString subPropertyString;
		
	/**
	 * Minimum confidence
	 */
	protected double minStdConfidence;
	
	/**
	 * Minimum confidence
	 */
	protected double minPcaConfidence;
	
	/**
	 * Maximum number of atoms allowed in the antecedent
	 */
	
	private Rule subclassQuery;
	
	/**
	 * Maximum number of atoms in a query
	 */
	protected int maxDepth;
	
	/**
	 * Contains the number of triples per relation in the database
	 */
	protected HashMap<String, Double> headCardinalities;

	/**
	 * Allow constants for refinements
	 */
	protected boolean allowConstants;
	
	/**
	 * Enforce constants in all atoms of rules
	 */
	protected boolean enforceConstants;
	
	/**
	 * List of excluded relations for the body of rules;
	 */
	protected Collection<ByteString> bodyExcludedRelations;
	
	/**
	 * List of excluded relations for the head of rules;
	 */
	protected Collection<ByteString> headExcludedRelations;
	
	/**
	 * List of target relations for the body of rules;
	 */
	protected Collection<ByteString> bodyTargetRelations;

	/**
	 * Count directly on subject or use functional information
	 */
	protected boolean countAlwaysOnSubject;
	
	/**
	 * Use a functionality vs suggested functionality heuristic to prune low confident rule upfront.
	 */
	protected boolean enabledFunctionalityHeuristic;
	
	/**
	 * Enable confidence and PCA confidence upper bounds for pruning when given a confidence threshold
	 */
	protected boolean enabledConfidenceUpperBounds;
	
	/**
	 * If true, the assistant will output minimal debug information
	 */
	protected boolean verbose;
		
	/**
	 * If true, the assistant will never add atoms of the form type(x, y), i.e., it will always bind 
	 * the second argument to a type.
	 */
	protected boolean avoidUnboundTypeAtoms;
	
	/**
	 * If false, the assistant will not exploit the maximum length restriction to improve
	 * runtime. 
	 */
	protected boolean exploitMaxLengthOption;
	
	/**
	 * Enable query rewriting to optimize runtime.
	 */
	protected boolean enableQueryRewriting;
	
	/**
	 * Enable perfect rule pruning, i.e., do not further specialize rules with PCA confidence
	 * 1.0.
	 */
	protected boolean enablePerfectRules;
	
	/**
	 * Confidence metric used to assess the quality of rules.
	 */
	protected ConfidenceMetric confidenceMetric;

	
	/**
	 * Do not calculate standard confidence.
	 */
	protected boolean ommitStdConfidence;
	
    /**
     * If true, AMIE outputs rules using the datalog notation
     * otherwise its default [subject, relation, object] notation
     */
    protected boolean datalogNotation;
    
    /**
     * Sequence of mining operators to be applied to a rule.
     */
    private LinkedList<Method> miningOperators;
	
	
	/**
	 * @param dataSource
	 */
	public MiningAssistant(KB dataSource) {
		this.kb = dataSource;
		this.minStdConfidence = 0.0;
		this.minPcaConfidence = 0.0;
		this.maxDepth = 3;
		this.allowConstants = false;
		ByteString[] rootPattern = Rule.fullyUnboundTriplePattern1();
		List<ByteString[]> triples = new ArrayList<ByteString[]>();
		triples.add(rootPattern);
		this.totalSubjectCount = this.kb.countDistinct(rootPattern[0], triples);
		this.totalObjectCount = this.kb.countDistinct(rootPattern[2], triples);
		this.typeString = ByteString.of("rdf:type");
		this.subPropertyString = ByteString.of("rdfs:subPropertyOf");
		this.headCardinalities = new HashMap<String, Double>();
		ByteString[] subclassPattern = Rule.fullyUnboundTriplePattern1();
		subclassPattern[1] = subPropertyString;
		this.subclassQuery = new Rule(subclassPattern, 0);
		this.countAlwaysOnSubject = false;
		this.verbose = false;
		this.exploitMaxLengthOption = true;
		this.enableQueryRewriting = true;
		this.enablePerfectRules = true;
		this.confidenceMetric = ConfidenceMetric.PCAConfidence;
		this.datalogNotation = false;
		this.ommitStdConfidence = false;
		buildRelationsDictionary();
		this.miningOperators = new LinkedList<>();
		computeOperatorHierarchy();
	}	
	
	/**
	 * Hierarchy of operators. If an operator is the parent of another operator, then it 
	 * will be called before this operator and its output will be sent as argument 
	 * to the child operator. For example the standard operator "dangling" is the parent of 
	 * the "instantiated" operator because the latter depends on the output of the first one.
	 * @author galarrag
	 *
	 */
	class OperatorDependencyTree {
		HashMap<Method, OperatorDependencyTree> tree;
		
		public OperatorDependencyTree() {
			tree = new HashMap<>();
		}
		
		public void addMethod(Method m) {
			tree.put(m, new OperatorDependencyTree());
		}
		
		public void addMethod(Method m, String parentId) {
			OperatorDependencyTree parent = find(parentId);
			if (parent != null)
				parent.addMethod(m);
		}

		private OperatorDependencyTree find(String parentId) {
			for (Method m : tree.keySet()) {
				MiningOperator annotInstance = m.getAnnotation(MiningOperator.class);
                if (annotInstance.name().equals(parentId)) {         
                    return tree.get(m);
                } else {
                	OperatorDependencyTree child = tree.get(m).find(parentId);
                	if (child != null)
                		return child;
                }
			}
			
			return null;
		}

		/**
		 * It performs a depth-first search to determine the order in which the operators
		 * will be invoked.
		 * @param output
		 */
		public void traverse(LinkedList<Method> output) {
			for (Method m : tree.keySet()) {
				output.add(m);
				tree.get(m).traverse(output);
			}
		}
	}
	
	/**
	 * This method precomputes the order in which the mining operators will be 
	 * called by AMIE.
	 * 
	 */
	private void computeOperatorHierarchy() {
	    Class<?> klass = this.getClass();
	    OperatorDependencyTree opTree = new OperatorDependencyTree();
	    while (klass != Object.class) { // need to iterated thought hierarchy in order to retrieve methods from above the current instance
	        // iterate though the list of methods declared in the class represented by klass variable, and add those annotated with the specified annotation
	        final List<Method> allMethods = new ArrayList<Method>(Arrays.asList(klass.getDeclaredMethods()));
	        for (final Method method : allMethods) {
	            if (method.isAnnotationPresent(MiningOperator.class)) {         
	            	MiningOperator annotInstance = method.getAnnotation(MiningOperator.class);
	            	if (annotInstance.dependency().equals("")) {
	            		opTree.addMethod(method);
	            	} else {
	            		opTree.addMethod(method, annotInstance.dependency());
	            	}
	            }
	        }
	        // move to the upper class in the hierarchy in search for more methods
	        klass = klass.getSuperclass();
	    }
	    
	    opTree.traverse(this.miningOperators);
	}
	
	/**
	 * Builds a dictionary with the relations and their sizes.
	 */
	private void buildRelationsDictionary() {
		Collection<ByteString> relations = kb.getRelations();
		for (ByteString relation : relations) {
			ByteString[] query = KB.triple(ByteString.of("?x"), relation, ByteString.of("?y"));
			double relationSize = kb.count(query);
			headCardinalities.put(relation.toString(), relationSize);
		}
	}

	public int getRecursivityLimit() {
		return recursivityLimit;
	}

	public void setRecursivityLimit(int recursivityLimit) {
		this.recursivityLimit = recursivityLimit;
	}

	public long getTotalCount(Rule candidate){
		if(countAlwaysOnSubject){
			return totalSubjectCount;
		}else{
			return getTotalCount(candidate.getFunctionalVariablePosition());
		}
	}
	
	/**
	 * Returns the total number of subjects in the database.
	 * @return
	 */
	public long getTotalSubjectCount(){
		return totalSubjectCount;
	}
		
	public long getTotalObjectCount() {
		return totalObjectCount;
	}

	/**
	 * @return the maxDepth
	 */
	public int getMaxDepth() {
		return maxDepth;
	}

	/**
	 * @param maxAntecedentDepth
	 */
	public void setMaxDepth(int maxAntecedentDepth) {
		this.maxDepth = maxAntecedentDepth;
	}
	
	/**
	 * @return the minStdConfidence
	 */
	public double getMinConfidence() {
		return minStdConfidence;
	}

	/**
	 * @return the minPcaConfidence
	 */
	public double getPcaConfidenceThreshold() {
		return minPcaConfidence;
	}

	/**
	 * @param minConfidence the minPcaConfidence to set
	 */
	public void setPcaConfidenceThreshold(double minConfidence) {
		this.minPcaConfidence = minConfidence;
	}

	/**
	 * @param minConfidence the minConfidence to set
	 */
	public void setStdConfidenceThreshold(double minConfidence) {
		this.minStdConfidence = minConfidence;
	}
	
	/**
	 * It returns the training dataset from which rules atoms are added
	 * @return
	 */
	public KB getKb() {
		return kb;
	}
	
	/**
	 * It returns the KB containing the schema information (subclass and subproperty relationships,
	 * domains and ranges for relation, etc.) about the training dataset.
	 * @return
	 */
	public KB getKbSchema(){
		return kbSchema;
	}
	
	/**
	 * Brief description of the MiningAssistant capabilities.
	 */
	public String getDescription() {
		 if (countAlwaysOnSubject) {
             return "Counting on the subject variable of the head relation";
         } else {
             return "Counting on the most functional variable of the head relation";
         }
	}
	
	public void setKbSchema(KB schemaSource) {
		// TODO Auto-generated method stub
		this.kbSchema = schemaSource;
	}

	public boolean registerHeadRelation(Rule query){		
		return headCardinalities.put(query.getHeadRelation(), 
				new Double(query.getSupport())) == null;		
	}
	
	public boolean registerHeadRelation(ByteString relation, double cardinality){		
		return headCardinalities.put(relation.toString(), cardinality) == null;		
	}
	
	public long getHeadCardinality(Rule query){
		return headCardinalities.get(query.getHeadRelation()).longValue();
	}
	
	public double getRelationCardinality(String relation) {
		return headCardinalities.get(relation);
	}
	
	public double getRelationCardinality(ByteString relation) {
		return headCardinalities.get(relation.toString());
	}

	protected Set<ByteString> getSubClasses(ByteString className){
		ByteString[] lastPattern = subclassQuery.getTriples().get(0);
		ByteString tmpVar = lastPattern[2];
		lastPattern[2] = className;		
		Set<ByteString> result = kb.selectDistinct(lastPattern[0], subclassQuery.getTriples());
		lastPattern[2] = tmpVar;
		return result;
	}
	/**
	 * Returns true if the assistant configuration allows the addition of instantiated atom, i.e., atoms
	 * where one of the arguments has a constant.
	 * @return
	 */
	protected boolean canAddInstantiatedAtoms() {
		return allowConstants || enforceConstants;
	}
	
	
	/**
	 * Returns a list of one-atom queries using the head relations provided in the collection relations.
	 * @param relations
	 * @param minSupportThreshold Only relations of size bigger or equal than this value will be considered.
	 */
	public Collection<Rule> getInitialAtomsFromSeeds(Collection<ByteString> relations, 
			double minSupportThreshold) {
		Collection<Rule> output = new ArrayList<>();
		Rule emptyQuery = new Rule();
		
		ByteString[] newEdge = emptyQuery.fullyUnboundTriplePattern();		
		emptyQuery.getTriples().add(newEdge);
		
		for (ByteString relation: relations) {
			newEdge[1] = relation;
			
			int countVarPos = countAlwaysOnSubject? 0 : findCountingVariable(newEdge);
			ByteString countingVariable = newEdge[countVarPos];
			long cardinality = kb.countDistinct(countingVariable, emptyQuery.getTriples());
			
			ByteString[] succedent = newEdge.clone();
			Rule candidate = new Rule(succedent, cardinality);
			candidate.setFunctionalVariablePosition(countVarPos);
			registerHeadRelation(candidate);			

			if(canAddInstantiatedAtoms() && !relation.equals(KB.EQUALSbs)) {
				getInstantiatedAtoms(candidate, null, 0, countVarPos == 0 ? 2 : 0, minSupportThreshold, output);
			}
			
			if (!enforceConstants) {
				output.add(candidate);
			}
		}
		emptyQuery.getTriples().remove(0);
		return output;
	}
	
	/**
	 * Returns a list of one-atom queries using the relations from the KB
	 * @param minSupportThreshold Only relations of size bigger or equal than this value will 
	 * be considered.
	 * @param output
	 */
	public Collection<Rule> getInitialAtoms(double minSupportThreshold) {
		List<ByteString[]> newEdgeList = new ArrayList<ByteString[]>(1);
		ByteString[] newEdge = new ByteString[]{ByteString.of("?x"), ByteString.of("?y"), ByteString.of("?z")};
		newEdgeList.add(newEdge);
		IntHashMap<ByteString> relations = kb.frequentBindingsOf(newEdge[1], newEdge[0], newEdgeList);
		return buildInitialQueries(relations, minSupportThreshold);
	}
	
	/**
	 * Given a list of relations with their corresponding support (one assistant could count based on the number of pairs,
	 * another could use the number of subjects), it returns a queue with rules of the form => r(x, y) for each relation
	 * r.
	 * 
	 * @param relations
	 * @param minSupportThreshold Only relations with support equal or above this value are considered.
	 * @param output
	 */
	protected Collection<Rule> buildInitialQueries(IntHashMap<ByteString> relations, double minSupportThreshold) {
		Collection<Rule> output = new ArrayList<>();
		Rule query = new Rule();
		ByteString[] newEdge = query.fullyUnboundTriplePattern();
		for(ByteString relation: relations){
			if (this.headExcludedRelations != null 
					&& this.headExcludedRelations.contains(relation)) {
				continue;
			}
			
			double cardinality = relations.get(relation);
			if(cardinality >= minSupportThreshold){
				ByteString[] succedent = newEdge.clone();
				succedent[1] = relation;
				int countVarPos = this.countAlwaysOnSubject ? 0 : findCountingVariable(succedent);
				Rule candidate = new Rule(succedent, cardinality);
				candidate.setFunctionalVariablePosition(countVarPos);
				registerHeadRelation(candidate);
				if (canAddInstantiatedAtoms() && 
						!relation.equals(KB.EQUALSbs)){
					getInstantiatedAtoms(candidate, null, 0, countVarPos == 0 ? 2 : 0, minSupportThreshold, output);
				}
				
				if (!this.enforceConstants) {
					output.add(candidate);
				}
			}
		}			
		return output;
	}

	/**
	 * Returns all candidates obtained by adding a new dangling atom to the query. A dangling atom joins with the
	 * rule on one variable and introduces a fresh variable not seen in the rule.
	 * @param rule
	 * @param minSupportThreshold
	 * @param output
	 */
	@MiningOperator(name="dangling")
	public void getDanglingAtoms(Rule rule, double minSupportThreshold, Collection<Rule> output){		
		ByteString[] newEdge = rule.fullyUnboundTriplePattern();
		if (rule.isEmpty()) {
			throw new IllegalArgumentException("This method expects a non-empty query");
		}
		//General case
		if(!isNotTooLong(rule))
			return;
		
		if (exploitMaxLengthOption) {
			if(rule.getRealLength() == maxDepth - 1){
				if(!rule.getOpenVariables().isEmpty() && !allowConstants){
					return;
				}
			}
		}
		
		List<ByteString> joinVariables = null;
		
		//Then do it for all values
		if(rule.isClosed(true)){
			joinVariables = rule.getVariables();
		}else{
			joinVariables = rule.getOpenVariables();
		}

		int nPatterns = rule.getTriples().size();
		ByteString originalRelationVariable = newEdge[1];		
		
		for(int joinPosition = 0; joinPosition <= 2; joinPosition += 2){
			ByteString originalFreshVariable = newEdge[joinPosition];
			
			for(ByteString joinVariable: joinVariables){					
				newEdge[joinPosition] = joinVariable;
				rule.getTriples().add(newEdge);
				IntHashMap<ByteString> promisingRelations = kb.frequentBindingsOf(newEdge[1], 
						rule.getFunctionalVariable(), rule.getTriples());
				rule.getTriples().remove(nPatterns);
				
				int danglingPosition = (joinPosition == 0 ? 2 : 0);
				boolean boundHead = !KB.isVariable(rule.getTriples().get(0)[danglingPosition]);
				for(ByteString relation: promisingRelations){
					if (this.bodyExcludedRelations != null && 
							this.bodyExcludedRelations.contains(relation))
						continue;
					//Here we still have to make a redundancy check						
					int cardinality = promisingRelations.get(relation);
					if(cardinality >= minSupportThreshold){
						newEdge[1] = relation;
						Rule candidate = rule.addAtom(newEdge, cardinality);
						if(candidate.containsUnifiablePatterns()){
							//Verify whether dangling variable unifies to a single value (I do not like this hack)
							if(boundHead && 
									kb.countDistinct(newEdge[danglingPosition], candidate.getTriples()) < 2)
								continue;
						}
						
						candidate.setHeadCoverage((double)candidate.getSupport() 
								/ headCardinalities.get(candidate.getHeadRelation()));
						candidate.setSupportRatio((double)candidate.getSupport() 
								/ (double)getTotalCount(candidate));
						candidate.addParent(rule);
						if (!enforceConstants) {
							output.add(candidate);
						}
					}
				}
				
				newEdge[1] = originalRelationVariable;
			}
			newEdge[joinPosition] = originalFreshVariable;
		}
	}

	/**
	 * It determines the counting variable of an atom with constant relation based on 
	 * the functionality of the relation
	 * @param headAtom
	 */
	protected int findCountingVariable(ByteString[] headAtom) {
		int nVars = KB.numVariables(headAtom);
		if(nVars == 1){
			return KB.firstVariablePos(headAtom);
		}else{
			return kb.isFunctional(headAtom[1]) ? 0 : 2;
		}
	}
	
	/**
	 * It computes the standard and the PCA confidence of a given rule. It assumes
	 * the rule's cardinality (absolute support) is known.
	 * @param candidate
	 */
	public void calculateConfidenceMetrics(Rule candidate) {		
		if (this.ommitStdConfidence) {
			candidate.setBodySize((long)candidate.getSupport() * 2);
		} else {
			computeStandardConfidence(candidate);
		}
		computePCAConfidence(candidate);
	}

	/**
	 * Returns all rule candidates obtained by adding a new atom that does not contain
	 * fresh variables.
	 * @param rule
	 * @param minSupportThreshold Only candidates with support above or equal this value are returned.
	 * @param output 
	 */
	@MiningOperator(name="closing")
	public void getClosingAtoms(Rule rule, double minSupportThreshold, Collection<Rule> output){
		if (enforceConstants) {
			return;
		}
		
		int nPatterns = rule.getTriples().size();

		if(rule.isEmpty())
			return;
		
		if(!isNotTooLong(rule))
			return;
		
		List<ByteString> sourceVariables = null;
		List<ByteString> allVariables = rule.getVariables();
		List<ByteString> openVariables = rule.getOpenVariables();
		
		if(rule.isClosed(true)){
			sourceVariables = rule.getVariables();
		}else{
			sourceVariables = openVariables; 
		}
		
		Pair<Integer, Integer>[] varSetups = new Pair[2];
		varSetups[0] = new Pair<Integer, Integer>(0, 2);
		varSetups[1] = new Pair<Integer, Integer>(2, 0);
		ByteString[] newEdge = rule.fullyUnboundTriplePattern();
		ByteString relationVariable = newEdge[1];
		
		for(Pair<Integer, Integer> varSetup: varSetups){			
			int joinPosition = varSetup.first.intValue();
			int closeCirclePosition = varSetup.second.intValue();
			ByteString joinVariable = newEdge[joinPosition];
			ByteString closeCircleVariable = newEdge[closeCirclePosition];
						
			for(ByteString sourceVariable: sourceVariables){					
				newEdge[joinPosition] = sourceVariable;
				
				for(ByteString variable: allVariables){
					if(!variable.equals(sourceVariable)){
						newEdge[closeCirclePosition] = variable;
						
						rule.getTriples().add(newEdge);
						IntHashMap<ByteString> promisingRelations = 
								kb.frequentBindingsOf(newEdge[1], rule.getFunctionalVariable(), rule.getTriples());
						rule.getTriples().remove(nPatterns);
						
						for(ByteString relation: promisingRelations){
							if(bodyExcludedRelations != null && bodyExcludedRelations.contains(relation))
								continue;
							
							//Here we still have to make a redundancy check
							int cardinality = promisingRelations.get(relation);
							newEdge[1] = relation;
							if(cardinality >= minSupportThreshold){										
								Rule candidate = rule.addAtom(newEdge, cardinality);
								if(!candidate.isRedundantRecursive()){
									candidate.setHeadCoverage((double)cardinality 
											/ (double)headCardinalities.get(candidate.getHeadRelation()));
									candidate.setSupportRatio((double)cardinality 
											/ (double)getTotalCount(candidate));
									candidate.addParent(rule);
									output.add(candidate);
								}
							}
						}
					}
					newEdge[1] = relationVariable;
				}
				newEdge[closeCirclePosition] = closeCircleVariable;
				newEdge[joinPosition] = joinVariable;
			}
		}
	}
	
	/**
	 * Returns all candidates obtained by instantiating the dangling variable of the last added
	 * triple pattern in the rule
	 * @param rule
	 * @param minSupportThreshold
	 * @param danglingEdges 
	 * @param output
	 */
	@MiningOperator(name="instantiated", dependency="dangling")
	public void getInstantiatedAtoms(Rule rule, double minSupportThreshold, 
			Collection<Rule> danglingEdges, Collection<Rule> output) {
		if (!canAddInstantiatedAtoms()) {
			return;
		}
		
		List<ByteString> queryFreshVariables = rule.getOpenVariables();
		if (this.exploitMaxLengthOption 
				|| rule.getRealLength() < this.maxDepth - 1 
				|| queryFreshVariables.size() < 2) {	
			for (Rule candidate : danglingEdges) {
				// Find the dangling position of the query
				int lastTriplePatternIndex = candidate.getLastRealTriplePatternIndex();
				ByteString[] lastTriplePattern = candidate.getTriples().get(lastTriplePatternIndex);
				
				List<ByteString> candidateFreshVariables = candidate.getOpenVariables();
				int danglingPosition = 0;
				if (candidateFreshVariables.contains(lastTriplePattern[0])) {
					danglingPosition = 0;
				} else if (candidateFreshVariables.contains(lastTriplePattern[2])) {
					danglingPosition = 2;
				} else {
					throw new IllegalArgumentException("The query " + rule.getRuleString() + 
								" does not contain fresh variables in the last triple pattern.");
				}
				getInstantiatedAtoms(candidate, candidate, 
						lastTriplePatternIndex, danglingPosition, 
						minSupportThreshold, output);
			}
		}
	}
	
	/**
	 * Returns all candidates obtained by replacing the type of the last atom with a subclass. If the last
	 * atom added to the rule is not a type constraint, i.e, [?x rdf:type C] the method does nothing.
	 * @param rule
	 * @param minSupportThreshold
	 * @param output
	 */
	public void getTypeSpecializedAtoms(Rule rule, double minSupportThreshold, Collection<Rule> output) {
		if (this.kbSchema == null)
			return;
		
		ByteString[] lastAtom = rule.getLastRealTriplePattern();
		if (!lastAtom[1].equals(amie.data.U.typeRelationBS))
			return;
		
		if (KB.isVariable(lastAtom[2]))
			return;
		
		ByteString typeToSpecialize = lastAtom[2];
		Set<ByteString> subtypes = amie.data.U.getSubtypes(this.kbSchema, typeToSpecialize);
		for (ByteString subtype : subtypes) {
			lastAtom[2] = subtype;
			long support = kb.countDistinct(rule.getFunctionalVariable(), rule.getTriples());
			lastAtom[2] = typeToSpecialize;				
			if (support >= minSupportThreshold) {
				Rule newRule = rule.specializeTypeAtom(subtype, support);				
				newRule.addParent(rule);
				output.add(newRule);
			}
		}
	}
	
	/**
	 * Check whether the rule meets the length criteria configured in the object.
	 * @param candidate
	 * @return
	 */
	protected boolean isNotTooLong(Rule candidate) {
		return candidate.getRealLength() < maxDepth;
	}
	
	/**
	 * Check whether the rule meets the syntactic language bias of the assistant object.
	 * By default AMIE searches for closed rules, i.e., each variable occurs in at least 
	 * two non-special atoms (relations such as DIFFERENTFROM are considered special and therefore
	 * not counted).
	 * @param candidate
	 * @return
	 */
	public boolean shouldBeOutput(Rule candidate) {
		return candidate.isClosed(true);
	}
	
	/**
	 * It computes the confidence upper bounds and approximations for the rule sent as argument.
	 * @param candidate
	 * @return True If neither the confidence bounds nor the approximations are aplicable or if they
	 * did not find enough evidence to discard the rule.
	 */
	public boolean calculateConfidenceBoundsAndApproximations(Rule candidate) {		
		if(enabledConfidenceUpperBounds){
			if (!calculateConfidenceBounds(candidate)) {
				return false;
			}
		}

		if (enabledFunctionalityHeuristic) {
			int realLength = candidate.getRealLength();
			if(realLength == 3) {
				return calculateConfidenceApproximationFor3Atoms(candidate);
			} else if (realLength > 3) {
				return calculateConfidenceApproximationForGeneralCase(candidate);
			}		
		}
		
		return true;
	}
	
	/**
	 * It computes the confidence bounds for rules
	 * @param candidate
	 * @return boolean True if the confidence bounds are not applicable or they cannot
	 * find enough evidence to discard the rule.
	 */
	private boolean calculateConfidenceBounds(Rule candidate) {
		if (candidate.getRealLength() != 3) {
			return true;
		}
		
		int[] hardQueryInfo = null;
		hardQueryInfo = kb.identifyHardQueryTypeI(candidate.getAntecedent());
		if(hardQueryInfo != null){
			double pcaConfUpperBound = getPcaConfidenceUpperBound(candidate);			
			if(pcaConfUpperBound < this.minPcaConfidence){
				if (this.verbose) {
					System.err.println("Query " + candidate + 
							" discarded by PCA confidence upper bound " + pcaConfUpperBound);			
				}
				return false;
			}
			
			double stdConfUpperBound = getStdConfidenceUpperBound(candidate);			
			
			if(stdConfUpperBound < this.minStdConfidence){
				if (this.verbose) {
					System.err.println("Query " + candidate + 
							" discarded by standard confidence upper bound " + stdConfUpperBound);
				}
				return false;
			}

			candidate.setConfidenceUpperBound(stdConfUpperBound);
			candidate.setPcaConfidenceUpperBound(pcaConfUpperBound);
		}
		
		return true;
	}

	/**
	 * Given a rule with more than 3 atoms and a single path connecting the head variables, 
	 * it computes a confidence approximation. It corresponds to the last formula of 
	 * page 15 in http://luisgalarraga.de/docs/amie-plus.pdf
	 * @param candidate
	 * @return boolean True if the approximation is not applicable or produces a value 
	 * above the confidence thresholds, i.e., there is not enough evidence to drop the rule.
	 */
	protected boolean calculateConfidenceApproximationForGeneralCase(
			Rule candidate) {
		// First identify whether the rule is a single path rule
		if (!candidate.containsSinglePath()) {
			// The approximation is not applicable.
			return true;
		}
		double denominator = 1.0;
		// If the approximation is applicable, let's reorder the atoms in the canonical way
		List<ByteString[]> path = candidate.getCanonicalPath();
		// Let's calculate the first term.
		ByteString r1 = path.get(0)[1];
		ByteString rh = candidate.getHead()[1];
		int[] joinInformation = Rule.joinPositions(path.get(0), candidate.getHead());
		// If r1 is not functional or it is not joining from the subject, we replace it with the corresponding inverse relation.
		boolean relationRewritten = joinInformation[0] != 0;		
		double funr1 = this.kb.functionality(r1, relationRewritten);
		double overlap = 0.0;
		overlap = computeOverlap(joinInformation, r1, rh);
		// The first part of the formula
		denominator = denominator * (overlap / funr1);
		
		// Now iterate
		for (int i = 1; i < path.size(); ++i) {
			ByteString ri = path.get(i)[1];
			ByteString ri_1 = path.get(i - 1)[1];
			joinInformation = Rule.joinPositions(path.get(i - 1), path.get(i));
			// Inverse r_{i-1} if it is not functional or it joins from the subject.
			boolean rewriteRi = joinInformation[1] != 0;
			double rng = 0.0;
			double funri = this.kb.functionality(ri, rewriteRi);
			double ifunri = this.kb.inverseFunctionality(ri, rewriteRi);
			
			rng = this.kb.relationColumnSize(ri_1, 
					joinInformation[0] == 0 ? KB.Column.Subject : KB.Column.Object);
			
			overlap = computeOverlap(joinInformation, ri_1, ri);
			double term = (overlap * ifunri) / (rng * funri); 
			denominator = denominator * term;
		}
		
		double estimatedPCA = (double)candidate.getSupport() / denominator;
		candidate.setPcaEstimation(estimatedPCA);
		if (estimatedPCA < this.minPcaConfidence) {
			if (!this.verbose) {
				System.err.println("Query " + candidate + " discarded by functionality heuristic with ratio " + estimatedPCA);
			}							
			return false;
		}
		
		return true;
	}

	/**
	 * Given two relations and the positions at which they join, it returns the number 
	 * of entities in the overlap of such positions.
	 * @param joinInformation
	 * @param r1
	 * @param rh
	 * @return
	 */
	private double computeOverlap(int[] jinfo, ByteString r1, ByteString r2) {
		if (jinfo[0] == 0 && jinfo[1] == 0) {
			return this.kb.overlap(r1, r2, KB.SUBJECT2SUBJECT);
		} else if (jinfo[0] == 2 && jinfo[1] == 2) {
			return this.kb.overlap(r1, r2, KB.OBJECT2OBJECT);
		} else if (jinfo[0] == 0 && jinfo[1] == 2) {
			return this.kb.overlap(r1, r2, KB.SUBJECT2OBJECT);
		} else if (jinfo[0] == 2 && jinfo[1] == 0) {
			return this.kb.overlap(r2, r1, KB.SUBJECT2OBJECT);
		} else {
			return 0.0;
		}
	}

	/**
	 * Calculate the confidence approximation of the query for the case when the rule has exactly 3 atoms.
	 * It is the implementation of section 6.2.2 in http://luisgalarraga.de/docs/amie-plus.pdf
	 * @param candidate
	 * @return boolean True if the approximation is not applicable or produces a value above the confidence thresholds, i.e.,
	 * there is not enough evidence to drop the rule.
	 */
	protected boolean calculateConfidenceApproximationFor3Atoms(Rule candidate) {
		int[] hardQueryInfo = null;
		hardQueryInfo = kb.identifyHardQueryTypeIII(candidate.getAntecedent());
		if(hardQueryInfo != null){
			ByteString[] targetPatternOutput = null;
			ByteString[] targetPatternInput = null; //Atom with the projection variable
			ByteString[] p1, p2;
			p1 = candidate.getAntecedent().get(hardQueryInfo[2]);
			p2 = candidate.getAntecedent().get(hardQueryInfo[3]);
			int posCommonInput = hardQueryInfo[0];
			int posCommonOutput = hardQueryInfo[1];		
			
			if (KB.varpos(candidate.getFunctionalVariable(), p1) == -1) {
				targetPatternOutput = p1;
				targetPatternInput = p2;
				posCommonInput = hardQueryInfo[0];
				posCommonOutput = hardQueryInfo[1];
			} else if (KB.varpos(candidate.getFunctionalVariable(), p2) == -1) {
				targetPatternOutput = p2;
				targetPatternInput = p1;
				posCommonInput = hardQueryInfo[1];
				posCommonOutput = hardQueryInfo[0];							
			}
			
			//Many to many case
			if (targetPatternOutput != null) {
				double funcInputRelation = kb.colFunctionality(targetPatternInput[1], 
						posCommonInput == 0 ? KB.Column.Object : KB.Column.Subject);
				double funcOutputRelation = kb.colFunctionality(targetPatternOutput[1], 
						posCommonOutput == 0 ? KB.Column.Subject : KB.Column.Object);
				double ifuncOutputRelation = kb.colFunctionality(targetPatternOutput[1], 
						posCommonOutput == 0 ? KB.Column.Object : KB.Column.Subject); //Duplicate elimination term
				double nentities = kb.relationColumnSize(targetPatternInput[1], 
						posCommonInput == 0 ? KB.Column.Subject : KB.Column.Object);
				
				double overlap;
				if(posCommonInput == posCommonOutput)
					overlap = kb.overlap(targetPatternInput[1], targetPatternOutput[1],
							posCommonInput + posCommonOutput);
				else if(posCommonInput < posCommonOutput)
					overlap = kb.overlap(targetPatternInput[1], targetPatternOutput[1], 
							posCommonOutput);
				else
					overlap = kb.overlap(targetPatternOutput[1], targetPatternInput[1], 
							posCommonInput);
				
				double overlapHead;
				int posInput = posCommonInput == 0 ? 2 : 0;
				if(posInput == candidate.getFunctionalVariablePosition()){
					overlapHead = kb.overlap(targetPatternInput[1], candidate.getHead()[1], 
							posInput + candidate.getFunctionalVariablePosition());
				}else if(posInput < candidate.getFunctionalVariablePosition()){
					overlapHead = kb.overlap(targetPatternInput[1], candidate.getHead()[1], 
							candidate.getFunctionalVariablePosition());							
				}else{
					overlapHead = kb.overlap(candidate.getHead()[1], targetPatternInput[1], posInput);							
				}
				
				double f4 = (1 / funcInputRelation) * (overlap / nentities);
				// Overlap between the body and the head * estimation of body size * duplicate elimination factor
				double ratio = overlapHead * f4 * (ifuncOutputRelation / funcOutputRelation);
				ratio = (double)candidate.getSupport() / ratio;
				candidate.setPcaEstimation(ratio);
				if(ratio < minPcaConfidence) { 
					if (this.verbose) {
						System.err.println("Rule " + candidate + 
								" discarded by functionality heuristic with ratio " + ratio);
					}							
					return false;
				}
			}
		}
		
		return true;
	}

	/**
	 * It checks whether a rule satisfies the confidence thresholds and the
	 * sky-line heuristic: the strategy that avoids outputting rules that do not
	 * improve the confidence w.r.t their parents.
	 * @param candidate
	 * @return
	 */
	public boolean testConfidenceThresholds(Rule candidate) {
		boolean addIt = true;
		
		if(candidate.containsLevel2RedundantSubgraphs()) {
			return false;
		}	
		
		if(candidate.getStdConfidence() >= minStdConfidence 
				&& candidate.getPcaConfidence() >= minPcaConfidence){
			//Now check the confidence with respect to its ancestors
			List<Rule> ancestors = candidate.getAncestors();			
			for(int i = 0; i < ancestors.size(); ++i){
				double ancestorConfidence = 0.0;
				double ruleConfidence = 0.0;
				if (this.confidenceMetric == ConfidenceMetric.PCAConfidence) {
					ancestorConfidence = ancestors.get(i).getPcaConfidence();
					ruleConfidence = candidate.getPcaConfidence();
				} else {
					ancestorConfidence = ancestors.get(i).getStdConfidence();
					ruleConfidence = candidate.getStdConfidence();
				}
				// Skyline technique on PCA confidence					
				if (shouldBeOutput(ancestors.get(i)) && 
						ruleConfidence <= ancestorConfidence){
					addIt = false;
					break;
				}		
			}
		}else{
			return false;
		}
		
		return addIt;
	}

	/**
	 * Given a rule of the form r(x, z) r(y, z) => rh(x, y), it calculates
	 * a loose upper bound on its PCA confidence.
	 * @param rule
	 * @return
	 */
	private double getPcaConfidenceUpperBound(Rule rule) {
		int[] hardCaseInfo = kb.identifyHardQueryTypeI(rule.getAntecedent());
		ByteString projVariable = rule.getFunctionalVariable();
		//ByteString commonVariable = query.getAntecedent().get(hardCaseInfo[2])[hardCaseInfo[0]];
		int freeVarPosition = rule.getFunctionalVariablePosition() == 0 ? 2 : 0;
		List<ByteString[]> easyQuery = new ArrayList<ByteString[]>(rule.getAntecedent());
		
		//Remove the pattern that does not have the projection variable
		ByteString[] pattern1 = easyQuery.get(hardCaseInfo[2]);
		ByteString[] pattern2 = easyQuery.get(hardCaseInfo[3]);
		ByteString[] remained = null;
		
		if(!pattern1[0].equals(projVariable) && !pattern1[2].equals(projVariable)){
			easyQuery.remove(hardCaseInfo[2]);
			remained = pattern2;
		}else if(!pattern2[0].equals(projVariable) && !pattern2[2].equals(projVariable)){
			easyQuery.remove(hardCaseInfo[3]);
			remained = pattern1;
		}
		
		//Add the existential triple only if it is not redundant
		if(remained != null){
			if(!remained[1].equals(rule.getHead()[1]) || hardCaseInfo[1] != rule.getFunctionalVariablePosition()){
				ByteString[] existentialTriple = rule.getHead().clone();
				existentialTriple[freeVarPosition] = ByteString.of("?z");
				easyQuery.add(existentialTriple);
			}
		}
		
		double denominator = kb.countDistinct(projVariable, easyQuery);
		return rule.getSupport() / denominator;
	}

	/**
	 * It computes a standard confidence upper bound for the rule.
	 * @param rule
	 * @return
	 */
	private double getStdConfidenceUpperBound(Rule rule) {
		int[] hardCaseInfo = kb.identifyHardQueryTypeI(rule.getAntecedent());
		double denominator = 0.0;
		ByteString[] triple = new ByteString[3];
		triple[0] = ByteString.of("?xw");
		triple[1] = rule.getAntecedent().get(0)[1];
		triple[2] = ByteString.of("?yw");
		
		if(hardCaseInfo[0] == 2){
			// Case r(y, z) r(x, z)
			denominator = kb.countDistinct(ByteString.of("?xw"), KB.triples(triple));
		}else{
			// Case r(z, y) r(z, x)
			denominator = kb.countDistinct(ByteString.of("?yw"), KB.triples(triple));
		}
		
		return rule.getSupport() / denominator;
	}

	/**
	 * It returns all the refinements of queryWithDanglingEdge where the fresh variable in the dangling
	 * atom has been bound to all the constants that keep the query above the support threshold.
	 * @param queryWithDanglingEdge
	 * @param parentQuery
	 * @param danglingAtomPosition
	 * @param danglingPositionInEdge
	 * @param minSupportThreshold
	 * @param output
	 */
	protected void getInstantiatedAtoms(Rule queryWithDanglingEdge, Rule parentQuery, 
			int danglingAtomPosition, int danglingPositionInEdge, double minSupportThreshold, Collection<Rule> output) {
		ByteString[] danglingEdge = queryWithDanglingEdge.getTriples().get(danglingAtomPosition);
		IntHashMap<ByteString> constants = kb.frequentBindingsOf(danglingEdge[danglingPositionInEdge], 
				queryWithDanglingEdge.getFunctionalVariable(), queryWithDanglingEdge.getTriples());
		for (ByteString constant: constants){
			int cardinality = constants.get(constant);
			if(cardinality >= minSupportThreshold){
				ByteString[] lastPatternCopy = queryWithDanglingEdge.getLastTriplePattern().clone();
				lastPatternCopy[danglingPositionInEdge] = constant;
				Rule candidate = queryWithDanglingEdge.instantiateConstant(danglingPositionInEdge, 
						constant, cardinality);

				if(candidate.getRedundantAtoms().isEmpty()){
					candidate.setHeadCoverage((double)cardinality / headCardinalities.get(candidate.getHeadRelation()));
					candidate.setSupportRatio((double)cardinality / (double)getTotalCount(candidate));
					candidate.addParent(parentQuery);					
					output.add(candidate);
				}
			}
		}
	}
	
	/**
	 * It computes the number of positive examples (cardinality) of the given rule 
	 * based on the evidence in the database.
	 * @param rule
	 * @return
	 */
	public double computeCardinality(Rule rule) {
		ByteString[] head = rule.getHead();
		ByteString countVariable = null;
		if (countAlwaysOnSubject) {
			countVariable = head[0];
		} else {
			countVariable = rule.getFunctionalVariable();
		}
		rule.setSupport(kb.countDistinct(countVariable, rule.getTriples()));
		rule.setSupportRatio((double) rule.getSupport() / kb.size());
		return rule.getSupport();
	}
	
	/**
	 * It computes the PCA confidence of the given rule based on the evidence in database.
	 * The value is both returned and set to the rule
	 * @param rule
	 * @return
	 */
	public double computePCAConfidence(Rule rule) {
		// TODO Auto-generated method stub
		List<ByteString[]> antecedent = new ArrayList<ByteString[]>();
		antecedent.addAll(rule.getTriples().subList(1, rule.getTriples().size()));
		ByteString[] succedent = rule.getTriples().get(0);
		ByteString[] existentialTriple = succedent.clone();
		int freeVarPos = 0;
		long pcaDenominator = 0;
		
		if(KB.numVariables(existentialTriple) == 1){
			freeVarPos = KB.firstVariablePos(existentialTriple);
		}else{
			if(existentialTriple[0].equals(rule.getFunctionalVariable()))
				freeVarPos = 2;
			else
				freeVarPos = 0;
		}

		existentialTriple[freeVarPos] = ByteString.of("?xw");
		if (!antecedent.isEmpty()) {
			//Improved confidence: Add an existential version of the head
			antecedent.add(existentialTriple);
			try{
				pcaDenominator = kb.countDistinct(rule.getFunctionalVariable(), antecedent);
				rule.setPcaBodySize(pcaDenominator);
			}catch(UnsupportedOperationException e){
				
			}
		}
		
		return rule.getPcaConfidence();
	}
	
	/**
	 * It computes the standard confidence of the given rule based on the evidence in database.
	 * The value is both returned and set to the rule
	 * @param candidate
	 * @return
	 */
	public double computeStandardConfidence(Rule candidate) {
		// Calculate confidence
		long denominator = 0;
		List<ByteString[]> antecedent = new ArrayList<ByteString[]>();
		antecedent.addAll(candidate.getTriples().subList(1, candidate.getTriples().size()));
				
		if(!antecedent.isEmpty()) {
			//Confidence
			try{
				denominator = kb.countDistinct(candidate.getFunctionalVariable(), antecedent);
				candidate.setBodySize(denominator);
			} catch(UnsupportedOperationException e) {
				
			}
		}		
		
		return candidate.getStdConfidence();
	}
	
	/**
     * It finds all potential parents of a rule in the output set of indexed
     * rules. A rule X => Y is a parent of rule X'=> Y' if Y = Y' and
     * X is a subset of X' (in other words X' => Y' is a more specific version
     * of X => Y)
     *
     * @param currentRule
     */
    public void setAdditionalParents(Rule currentRule, MultiMap<Integer, Rule> indexedOutputSet) {
        int currentGeneration = currentRule.getGeneration();
        int offset = 1;
        // Go up until you find a parent that was output
        while (currentGeneration - offset > 1) {
        	int generation = currentGeneration - offset;
            int parentHashCode = Rule.headAndGenerationHashCode(currentRule.getHeadKey(), generation);
            Set<Rule> candidateParentsOfGeneration = indexedOutputSet.get(parentHashCode);
            
            if (candidateParentsOfGeneration != null) {
	            for (Rule parent : candidateParentsOfGeneration) {
	                if (parent.subsumes(currentRule)) {
	                	currentRule.addParent(parent);
	                }
	            }
        	}
            ++offset;
        }
    }
    
    /**
     * It returns a string representation of the rule depending on the assistant configurations
     * @param rule
     * @return
     */
	public String formatRule(Rule rule) {
		StringBuilder result = new StringBuilder();
		Metric[] metrics2Ommit = new Metric[0];
		if (this.ommitStdConfidence) {
			metrics2Ommit = new Metric[]{Metric.StandardConfidence, Metric.BodySize};
		}
		
		if (this.datalogNotation) {
    		if (isVerbose()) {
    			result.append(rule.getDatalogFullRuleString(metrics2Ommit));
    		} else {
    			result.append(rule.getDatalogBasicRuleString(metrics2Ommit));
    		}
    	} else {     
    		if (isVerbose()) {
    			result.append(rule.getFullRuleString(metrics2Ommit));
    		} else {
    			result.append(rule.getBasicRuleString(metrics2Ommit));                        			
    		}
    	}
		
		return result.toString();
	}
	
	/**
	 * It call all the declared mining operators.
	 * @param currentRule
	 * @param threshold
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	public Map<String, Collection<Rule>> applyMiningOperators(Rule currentRule, double threshold) 
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Map<String, Collection<Rule>> temporalResultsMap = new LinkedHashMap<>();
		for (Method m: this.miningOperators) {
			MiningOperator mo = m.getAnnotation(MiningOperator.class);
			Collection<Rule> tmpResult = new ArrayList<>();
			if (mo.dependency().equals("")) {
				m.invoke(this, currentRule, threshold, tmpResult);
			} else {
				m.invoke(this, currentRule, threshold, temporalResultsMap.get(mo.dependency()), tmpResult);
			}
			
			temporalResultsMap.put(mo.name(), tmpResult);
		}
		
		return temporalResultsMap;
	}
    
	public void setAllowConstants(boolean allowConstants) {
		// TODO Auto-generated method stub
		this.allowConstants = allowConstants;
	}

	public boolean isEnforceConstants() {
		return enforceConstants;
	}

	public void setEnforceConstants(boolean enforceConstants) {
		this.enforceConstants = enforceConstants;
	}

	public Collection<ByteString> getBodyExcludedRelations() {
		return bodyExcludedRelations;
	}
	
	public void setBodyExcludedRelations(Collection<ByteString> excludedRelations) {
		this.bodyExcludedRelations = excludedRelations;
	}
	
	public Collection<ByteString> getHeadExcludedRelations() {
		return headExcludedRelations;
	}

	public void setHeadExcludedRelations(
			Collection<ByteString> headExcludedRelations) {
		this.headExcludedRelations = headExcludedRelations;
	}

	public Collection<ByteString> getBodyTargetRelations() {
		return bodyTargetRelations;
	}
	
	public boolean isAvoidUnboundTypeAtoms() {
		return avoidUnboundTypeAtoms;
	}

	public void setAvoidUnboundTypeAtoms(boolean avoidUnboundTypeAtoms) {
		this.avoidUnboundTypeAtoms = avoidUnboundTypeAtoms;
	}

	public void setTargetBodyRelations(
			Collection<ByteString> bodyTargetRelations) {
		this.bodyTargetRelations = bodyTargetRelations;
	}	

	public long getTotalCount(int projVarPosition) {
		if(projVarPosition == 0)
			return totalSubjectCount;
		else if(projVarPosition == 2)
			return totalObjectCount;
		else
			throw new IllegalArgumentException("Only 0 and 2 are valid variable positions");
	}

	public void setCountAlwaysOnSubject(boolean countAlwaysOnSubject) {
		// TODO Auto-generated method stub
		this.countAlwaysOnSubject = countAlwaysOnSubject;
	}

	public long getFactsCount() {
		// TODO Auto-generated method stub
		return kb.size();
	}

	public boolean isEnabledFunctionalityHeuristic() {
		return enabledFunctionalityHeuristic;
	}

	public void setEnabledFunctionalityHeuristic(boolean enableOptimizations) {
		this.enabledFunctionalityHeuristic = enableOptimizations;
	}

	public boolean isEnabledConfidenceUpperBounds() {
		return enabledConfidenceUpperBounds;
	}

	public void setEnabledConfidenceUpperBounds(boolean enabledConfidenceUpperBounds) {
		this.enabledConfidenceUpperBounds = enabledConfidenceUpperBounds;
	}


	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean isExploitMaxLengthOption() {
		return exploitMaxLengthOption;
	}

	public void setExploitMaxLengthOption(boolean exploitMaxLengthOption) {
		this.exploitMaxLengthOption = exploitMaxLengthOption;
	}

	public boolean isEnableQueryRewriting() {
		return enableQueryRewriting;
	}

	public void setEnableQueryRewriting(boolean enableQueryRewriting) {
		this.enableQueryRewriting = enableQueryRewriting;
	}

	public boolean isEnablePerfectRules() {
		return enablePerfectRules;
	}

	public void setEnablePerfectRules(boolean enablePerfectRules) {
		this.enablePerfectRules = enablePerfectRules;
	}

	public ConfidenceMetric getConfidenceMetric() {
		return confidenceMetric;
	}

	public void setConfidenceMetric(ConfidenceMetric confidenceMetric) {
		this.confidenceMetric = confidenceMetric;
	}

	public void setOmmitStdConfidence(boolean ommitStdConfidence) {
		this.ommitStdConfidence = ommitStdConfidence;
	}

	public boolean isOmmitStdConfidence() {
		return this.ommitStdConfidence;
	}

	public boolean isDatalogNotation() {
		return datalogNotation;
	}

	public void setDatalogNotation(boolean datalogNotation) {
		this.datalogNotation = datalogNotation;
	}
}