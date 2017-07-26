package amie.wikilinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import amie.data.KB;
import amie.mining.assistant.DefaultMiningAssistant;
import amie.rules.Rule;
import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;

public class WikilinksHeadVariablesMiningAssistant extends DefaultMiningAssistant {
	
	public static String wikiLinkProperty = "<linksTo>";
	
	public WikilinksHeadVariablesMiningAssistant(KB dataSource) {
		super(dataSource);
        headExcludedRelations = Arrays.asList(ByteString.of(WikilinksHeadVariablesMiningAssistant.wikiLinkProperty), 
        		ByteString.of("rdf:type"));
        bodyExcludedRelations = headExcludedRelations;
	}
	
	public String getDescription() {
		return "Rules of the form .... linksTo(x, y) "
				+ "type(x, C) type(y, C') => r(x, y)";
	}
	
	@Override
	public void setHeadExcludedRelations(java.util.Collection<ByteString> headExcludedRelations) {};
	
	@Override
	public void setBodyExcludedRelations(java.util.Collection<ByteString> excludedRelations) {};
	
	@Override
	public void getDanglingAtoms(Rule query, double minCardinality, Collection<Rule> output) {		
		if (query.isEmpty()) {
			//Initial case
			ByteString[] newEdge = query.fullyUnboundTriplePattern();
			query.getTriples().add(newEdge);
			List<ByteString[]> emptyList = Collections.emptyList();
			IntHashMap<ByteString> relations = kb.countProjectionBindings(query.getHead(), emptyList, newEdge[1]);
			for(ByteString relation: relations){
				// Language bias test
				if (query.cardinalityForRelation(relation) >= recursivityLimit) {
					continue;
				}
				
				if(headExcludedRelations != null && 
						headExcludedRelations.contains(relation)) {
					continue;
				}
				
				int cardinality = relations.get(relation);
				if(cardinality >= minCardinality){
					ByteString[] succedent = newEdge.clone();
					succedent[1] = relation;
					int countVarPos = countAlwaysOnSubject? 0 : findCountingVariable(succedent);
					Rule candidate = new Rule(succedent, cardinality);
					candidate.setFunctionalVariablePosition(countVarPos);
					registerHeadRelation(candidate);
					output.add(candidate);
				}
			}			
			query.getTriples().remove(0);
		} else {
			super.getDanglingAtoms(query, minCardinality, output);
		}
	}
	
	@Override
	public void getTypeSpecializedAtoms(Rule query, double minSupportThreshold, Collection<Rule> output) {
		if (query.containsRelation(typeString))
			return;
		
		List<Rule> tmpCandidates = new ArrayList<Rule>();
		ByteString[] head = query.getHead();
		
		//Specialization by type
		if(KB.isVariable(head[0])){
			ByteString[] newEdge = query.fullyUnboundTriplePattern();
			newEdge[0] = head[0];
			newEdge[1] = typeString;				
			query.getTriples().add(newEdge);
			IntHashMap<ByteString> subjectTypes = kb.countProjectionBindings(query.getHead(), 
					query.getAntecedent(), newEdge[2]);
			if(!subjectTypes.isEmpty()){
				for(ByteString type: subjectTypes){
					int cardinality = subjectTypes.get(type);
					if(cardinality >= minSupportThreshold){
						Rule newCandidate = new Rule(query, cardinality);
						newCandidate.getLastTriplePattern()[2] = type;
						tmpCandidates.add(newCandidate);
					}
				}
			}
			
			query.getTriples().remove(query.getTriples().size() - 1);
			//tmpCandidates.add(query);
		}
		
		if(KB.isVariable(head[2])){
			for(Rule candidate: tmpCandidates){
				ByteString[] newEdge = query.fullyUnboundTriplePattern();
				newEdge[0] = head[2];
				newEdge[1] = typeString;
				candidate.getTriples().add(newEdge);
				IntHashMap<ByteString> objectTypes = kb.countProjectionBindings(candidate.getHead(), candidate.getAntecedent(), newEdge[2]);
				for(ByteString type: objectTypes){
					int cardinality = objectTypes.get(type);
					if(cardinality >= minSupportThreshold){
						Rule newCandidate = new Rule(candidate, cardinality);
						newCandidate.setHeadCoverage((double)cardinality 
								/ (double)headCardinalities.get(newCandidate.getHeadRelation()));
						newCandidate.setSupportRatio((double)cardinality / (double)kb.size());
						newCandidate.addParent(query);
						newCandidate.getLastTriplePattern()[2] = type;
						newCandidate.addParent(query);
						output.add(newCandidate);
					}
				}
				
				/**if (candidate != query) {
					output.add(candidate);
					candidate.addParent(query);
				}**/
				candidate.getTriples().remove(candidate.getTriples().size() - 1);
			}
		}
	}
	
	@Override
	public void getClosingAtoms(Rule query, double minSupportThreshold, Collection<Rule> output) {
		int length = query.getLengthWithoutTypesAndLinksTo(typeString, ByteString.of(wikiLinkProperty));
		ByteString[] head = query.getHead();
		if (length == maxDepth - 1) {
			List<ByteString> openVariables = query.getOpenVariables();
			for (ByteString openVar : openVariables) {
				if (KB.isVariable(head[0]) && !openVar.equals(head[0])) {
					return;
				}
				
				if (KB.isVariable(head[2]) && !openVar.equals(head[2])) {
					return;
				}
			}
		}
		
		if (!query.containsRelation(ByteString.of(wikiLinkProperty))) {
			ByteString[] newEdge = head.clone();
			newEdge[1] = ByteString.of(wikiLinkProperty);
			List<ByteString[]> queryAtoms = new ArrayList<>();
			queryAtoms.addAll(query.getTriples());
			queryAtoms.add(newEdge);
			long cardinality = kb.countDistinctPairs(head[0], head[2], queryAtoms);
			if (cardinality >= minSupportThreshold) {
				Rule candidate1 = query.addAtom(newEdge, (int)cardinality);
				candidate1.setHeadCoverage((double)cardinality / (double)headCardinalities.get(candidate1.getHeadRelation()));
				candidate1.setSupportRatio((double)cardinality / (double)kb.size());
				candidate1.addParent(query);			
				output.add(candidate1);	
			}
			
			ByteString tmp = newEdge[0];
			newEdge[0] = newEdge[2];
			newEdge[2] = tmp;
			cardinality = kb.countDistinctPairs(head[0], head[2], queryAtoms);
			if (cardinality >= minSupportThreshold) {
				Rule candidate2 = query.addAtom(newEdge, (int)cardinality);
				candidate2.setHeadCoverage((double)cardinality / (double)headCardinalities.get(candidate2.getHeadRelation()));
				candidate2.setSupportRatio((double)cardinality / (double)kb.size());
				candidate2.addParent(query);			
				output.add(candidate2);	
			}
		} else {
			super.getClosingAtoms(query, minSupportThreshold, output);
		}
	}

	protected boolean isNotTooLong(Rule candidate){
		return candidate.getLengthWithoutTypesAndLinksTo(typeString, ByteString.of(wikiLinkProperty)) < maxDepth;
	}
	
	@Override
	public boolean shouldBeOutput(Rule candidate) {
		return candidate.isClosed(true) 
				&& candidate.containsRelation(typeString)
				&& candidate.containsRelation(ByteString.of(wikiLinkProperty));
	}	
}
