package amie.data.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import amie.data.KB;
import amie.mining.assistant.DefaultMiningAssistant;
import amie.rules.AMIEParser;
import amie.rules.Rule;
import javatools.datatypes.ByteString;

public class EnhanceRulesWithTypeConstraints {

	public static void main(String[] args) throws IOException {
		List<String> lines = Files.readAllLines(Paths.get(args[0]), Charset.forName("UTF-8"));
		KB kb = amie.data.U.loadFiles(args, 1);
		DefaultMiningAssistant assistant = new DefaultMiningAssistant(kb);
		assistant.setOmmitStdConfidence(true);
		
		for (String line : lines) {
			Rule r = AMIEParser.rule(line);
			ByteString headRelation = r.getHead()[1];
			ByteString domain = amie.data.U.getRelationDomain(kb, headRelation);
			ByteString range = amie.data.U.getRelationRange(kb, headRelation);
			
			if(domain != null){
				ByteString[] domainTriple = new ByteString[3];
				domainTriple[0] = r.getHead()[0];
				domainTriple[1] = amie.data.U.typeRelationBS;
				domainTriple[2] = domain;
				r.getTriples().add(domainTriple);
			}
			
			if(range != null){
				ByteString[] rangeTriple = new ByteString[3];
				rangeTriple[0] = r.getHead()[2];
				rangeTriple[1] = amie.data.U.typeRelationBS;
				rangeTriple[2] = range;
				r.getTriples().add(rangeTriple);
			}
			
			assistant.computeCardinality(r);
			assistant.calculateConfidenceMetrics(r);
			System.out.println(assistant.formatRule(r));
		}
		
	}

}
