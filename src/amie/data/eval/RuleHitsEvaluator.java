package amie.data.eval;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import javatools.datatypes.Triple;
import javatools.filehandlers.TSVFile;
import amie.data.KB;
import amie.rules.AMIEParser;
import amie.rules.Rule;


class TripleComparator implements Comparator<ByteString[]> {

	@Override
	public int compare(ByteString[] o1, ByteString[] o2) {
		if (o1[0].equals(o2[0])) {
			if (o1[1].equals(o2[1])) {
				return o1[2].toString().compareTo(o2[2].toString()); 
			} else {
				return o1[1].toString().compareTo(o2[1].toString());
			}
		} else {
			return o1[0].toString().compareTo(o2[0].toString());
		}
	}
}
/**
 * This class implements a simple program that given a set of rules extracted from an old version of a KB, 
 * counts the number of right predictions (hits) in the newer version of the KB.
 * @author lgalarra
 *
 */
public class RuleHitsEvaluator {
	
	public static void main(String args[]) throws IOException{
		if(args.length < 3){
			System.err.println("RuleHitsEvaluator <inputfile> <trainingDb> <targetDb>");
			System.exit(1);
		}
		
		File inputFile = new File(args[0]);		
		KB trainingDataset = new KB();
		KB targetDataset = new KB();		
		TSVFile tsvFile = new TSVFile(inputFile);
		int rawHitsInTargetNotInTraining = 0;
		int hitsInTarget = 0;
		int rawHitsInTarget = 0;
		int rawHitsInTraining = 0;
		int hitsInTraining = 0;
		int hitsInTargetNotInTraining = 0;
		
		trainingDataset.load(new File(args[1]));
		targetDataset.load(new File(args[2]));
		Predictor predictor = new Predictor(trainingDataset);	
		IntHashMap<Triple<ByteString, ByteString, ByteString>> predictions = 
				new IntHashMap<>();
		// Collect all predictions made by the rules.
		for(List<String> record: tsvFile) {
			Rule q = AMIEParser.rule(record.get(0));
			if(q == null) {
				continue;
			}
			
			ByteString[] head = q.getHead();
			q.setFunctionalVariablePosition(Rule.findFunctionalVariable(q, trainingDataset));
			Object bindings = null;
			try {
				bindings = predictor.generateBodyBindings(q);
			} catch (Exception e) {
				continue;
			}
			
			if(KB.numVariables(head) == 1){
				Set<ByteString> oneVarBindings = (Set<ByteString>)bindings;
				for(ByteString binding: oneVarBindings){
					Triple<ByteString, ByteString, ByteString> t = 
							new Triple<>(ByteString.of("?a"), head[1], ByteString.of("?b"));
					if (q.getFunctionalVariablePosition() == 0) {
						t.first = binding;
					} else {
						t.third = binding;
					}
					predictions.increase(t);
				}
			}else{
				Map<ByteString, IntHashMap<ByteString>> twoVarsBindings = 
						(Map<ByteString, IntHashMap<ByteString>>)bindings;
				for(ByteString value1: twoVarsBindings.keySet()){
					for(ByteString value2: twoVarsBindings.get(value1)){
						Triple<ByteString, ByteString, ByteString> t = 
								new Triple<>(ByteString.of("?a"), head[1], ByteString.of("?b"));
						if(q.getFunctionalVariablePosition() == 0){
							t.first = value1;
							t.third = value2;
						}else{
							t.first = value2;
							t.third = value1;					
						}
						predictions.increase(t);
					}
				}
			}		
		}
		
		for (Triple<ByteString, ByteString, ByteString> t : predictions) {
			ByteString[] triple = KB.triple2Array(t);
			int eval = Evaluator.evaluate(triple, trainingDataset, targetDataset);
			if(eval == 0) { 
				++hitsInTarget;
				rawHitsInTarget += predictions.get(t);
			}
			
			if(trainingDataset.count(triple) > 0) {
				++hitsInTraining;
				rawHitsInTraining += predictions.get(t);
			} else {
				if (eval == 0) {
					++hitsInTargetNotInTraining;
					rawHitsInTargetNotInTraining += predictions.get(t);
				}
			}
		}
		
		System.out.println("Total unique predictions\tTotal Hits in target"
				+ "\tTotal unique hits in target\tTotal hits on training"
				+ "\tTotal unique hits in training\tTotal hits in target not in training"
				+ "\tTotal unique hits in target not in training");
		System.out.println(predictions.size() + 
				"\t" + rawHitsInTarget + "\t" + hitsInTarget + 
				"\t" + rawHitsInTraining + "\t" + hitsInTraining + 
				"\t" + rawHitsInTargetNotInTraining + "\t" + hitsInTargetNotInTraining);
		tsvFile.close();
	}
}
