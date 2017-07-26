package amie.keys.assistant;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import amie.data.KB;
import amie.mining.assistant.DefaultMiningAssistant;
import amie.mining.assistant.MiningOperator;
import amie.rules.Rule;

/**
 * Implements a subclass of the mining assistant that is optimized to mine keys. It extends
 * the standard head variables mining assistant to mine rules of the form
 * r(x, z1) r(y, z1) ....  r'(x, zk) r'(y, zk) => equals(x, y)
 * @author galarrag
 *
 */
public class KeyMinerMiningAssistant extends DefaultMiningAssistant {

	public KeyMinerMiningAssistant(KB dataSource) {
		super(dataSource);
		recursivityLimit = 2; // The maximum number of atoms of a relation in the query
	}
	
	public String getDescription() {
		 return "Overriding recursivity limit. Using a value of " + getRecursivityLimit();
	}
	
	/**
	 * We enforce always an equals relation on the head, no matter if the user provides
	 * seeds relations.
	 */
	public Collection<Rule> getInitialAtomsFromSeeds(Collection<ByteString> seedsRelations, 
			double minSupportThreshold) {
		Collection<ByteString> equalsRelation = Arrays.asList(KB.EQUALSbs);
		return super.getInitialAtomsFromSeeds(equalsRelation, minSupportThreshold);
	}
	
	@Override
	@MiningOperator(name="dangling")
	public void getDanglingAtoms(Rule query, double minCardinality, Collection<Rule> output) {		
		if (query.isEmpty()) {
			output.addAll(getInitialAtomsFromSeeds(Collections.EMPTY_LIST, minCardinality));
			return;
		}
		// Do nothing as getCloseCircleEdges takes care of building rules for keys.
	}
	
	/**
	 * Returns all candidates obtained by adding a composite closing edge of the form
	 * r(x, z), r(y, z) where x, y are the head variables of the rule sent as argument.
	 * @param currentNode
	 * @param minSupportThreshold
	 * @param omittedVariables
	 * @return
	 */
	@Override
	@MiningOperator(name="closing")
	public void getClosingAtoms(Rule rule, double minSupportThreshold, Collection<Rule> output) {
		if (this.enforceConstants) {
			return;
		}
		
		int nPatterns = rule.getTriples().size();

		if (rule.isEmpty())
			return;
		
		if (!isNotTooLong(rule))
			return;
		// We first add a dangling atom of the form r(x, z) where x is one of the head variables		
		List<ByteString> joinVariables = rule.getHeadVariables();
		if (joinVariables.size() < 2) {
			return;
		}
		
		ByteString[] newEdge = rule.fullyUnboundTriplePattern();			
		int joinPosition = 0;
		ByteString[] newEdge1 = newEdge.clone();
		ByteString[] newEdge2 = newEdge.clone();
		
		newEdge1[joinPosition] = joinVariables.get(0);
		newEdge2[joinPosition] = joinVariables.get(1);
		rule.getTriples().add(newEdge1);
		IntHashMap<ByteString> promisingRelations = this.kb.countProjectionBindings(rule.getHead(), rule.getAntecedent(), newEdge1[1]);
		rule.getTriples().remove(nPatterns);
		
		for(ByteString relation: promisingRelations){
			int cardinality = promisingRelations.get(relation);
			
			if (cardinality < minSupportThreshold) {
				continue;
			}			
			
			// Language bias test
			if (rule.cardinalityForRelation(relation) >= recursivityLimit) {
				continue;
			}
			
			if (bodyExcludedRelations != null 
					&& bodyExcludedRelations.contains(relation)) {
				continue;
			}
			
			if (bodyTargetRelations != null 
					&& !bodyTargetRelations.contains(relation)) {
				continue;
			}
			
			newEdge1[1] = relation;
			newEdge2[1] = relation;
			
			Rule candidate = rule.addEdges(newEdge1, newEdge2);
			computeCardinality(candidate);
			if (candidate.getSupport() < minSupportThreshold) {
				continue;
			}
			candidate.addParent(rule);	
			output.add(candidate);
		}
	}
}
