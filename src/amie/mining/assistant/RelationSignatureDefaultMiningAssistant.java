package amie.mining.assistant;

import java.util.Arrays;
import java.util.List;

import javatools.datatypes.ByteString;
import amie.data.KB;
import amie.data.U;
import amie.rules.Rule;

/**
 * This class overrides the default mining assistant enforcing type constraints on the 
 * head variables of rules. The type constraints correspond to the domain and ranges of 
 * the head relation, that is, it mines rules of the form B ^ is(x, D) ^ is(y, R) =&gt; rh(x, y)
 * where D and R are the domain and ranges of relation rh.
 * @author luis
 *
 */
public class RelationSignatureDefaultMiningAssistant extends DefaultMiningAssistant {
	/**
	 * @param dataSource
	 */
	public RelationSignatureDefaultMiningAssistant(KB dataSource) {
		super(dataSource);
        List<ByteString> excludedRelationsSignatured = Arrays.asList(ByteString.of("rdf:type"),
                ByteString.of("rdfs:domain"), ByteString.of("rdfs:range"));
        bodyExcludedRelations = excludedRelationsSignatured;
        headExcludedRelations = excludedRelationsSignatured;
	}
	
	@Override
	public String getDescription() {
        return "Counting on both head variables and "
        		+ "using relation signatures (domain and range types) "
        		+ "[EXPERIMENTAL]";
	}
	
	@Override
	public void setHeadExcludedRelations(java.util.Collection<ByteString> headExcludedRelations) {};
	
	@Override
	public void setBodyExcludedRelations(java.util.Collection<ByteString> excludedRelations) {};
	
	@Override
	public boolean testConfidenceThresholds(Rule candidate) {
		boolean queryChanged = false;
		
		if (!candidate.isClosed(true)){
			return false;
		}
		
		//Add the schema information to the rule
		ByteString domain, range, relation;
		relation = candidate.getHead()[1];
		domain = U.getRelationDomain(kb, relation);
		if(domain != null){
			ByteString[] domainTriple = new ByteString[3];
			domainTriple[0] = candidate.getHead()[0];
			domainTriple[1] = ByteString.of("rdf:type");
			domainTriple[2] = domain;
			candidate.getTriples().add(domainTriple);
			queryChanged = true;
		}
		
		range = U.getRelationRange(kb, relation);
		if(range != null){
			ByteString[] rangeTriple = new ByteString[3];
			rangeTriple[0] = candidate.getHead()[2];
			rangeTriple[1] = ByteString.of("rdf:type");
			rangeTriple[2] = range;
			candidate.getTriples().add(rangeTriple);
			queryChanged = true;
		}
		
		if (queryChanged) {
			recalculateSupport(candidate);		
			calculateConfidenceMetrics(candidate);
		}
		
		return super.testConfidenceThresholds(candidate);
	}

	/**
	 * It recalculates the support of a rule after it has been enhanced with type constraints.
	 * @param candidate
	 */
	private void recalculateSupport(Rule candidate) {
		long cardinality = kb.countProjection(candidate.getHead(), candidate.getAntecedent());
		candidate.setSupport(cardinality);
		candidate.setHeadCoverage((double)candidate.getSupport() / headCardinalities.get(candidate.getHeadRelation()));
		candidate.setSupportRatio((double)candidate.getSupport() / (double)kb.size());
	}
}
