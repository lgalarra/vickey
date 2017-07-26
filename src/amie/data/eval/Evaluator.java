package amie.data.eval;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

import javatools.datatypes.ByteString;
import javatools.datatypes.Pair;
import javatools.filehandlers.TSVFile;
import amie.data.KB;
import amie.rules.Rule;

public class Evaluator {
	
	/**
	 * Given a triple that was deduced from rules extracted from the training dataset,
	 * it tests its correctness with respect to the target dataset. 
	 * @param triple
	 * @param training
	 * @param target
	 * @return
	 */
	public static int evaluate(ByteString[] triple, 
			KB training, KB target) {
		//If we know something else about the triple, PCA says it is false
		if (triple == null) {
			System.out.println("Triple is null");			
		}
		// TODO Auto-generated method stub
		int returnVal = 3;
		ByteString[] head = Rule.fullyUnboundTriplePattern1();
		head[1] = triple[1];
		boolean relationIsFunctional = 
				training.functionality(triple[1]) >= 0.9 
				|| training.inverseFunctionality(triple[1]) >= 0.84;

		int boundVarPos = 
				training.functionality(triple[1]) 
				> training.inverseFunctionality(triple[1]) ? 0 : 2;
		
		if (target == null) {
			System.out.println("Target is null");						
		}
		if(target.count(triple) > 0){
			//Bingo!
			returnVal = 0;
		}else{
			head[boundVarPos] = triple[boundVarPos];
			
			//Here apply PCA on the most functional variable
			if(training.count(head) > 0 && relationIsFunctional)
				returnVal = 1;
			else if(target.count(head) > 0 && relationIsFunctional)
				returnVal = 2;
			else
				returnVal = 3;
		}
		
		return returnVal;
	}
	
	/**
	 * Given a triple predicted by a rule, it evaluates its correctness in the training and target datasets
	 * @param rule Rule that generated the prediction
	 * @param triple Prediction
	 * @param training The dataset from which the rule was mined
	 * @param target A newer version of the training dataset (superset)
	 * @return 0 if the prediction is in the target dataset. 1 if it contradicts a functional constraint in the training dataset. 2 if it contradicts a functional constraint in the target dataset.
	 * 3 otherwise.
	 */
	public static int evaluate(Rule rule, 
			ByteString[] triple, KB training, KB target){
		// TODO Auto-generated method stub
		ByteString[] head = rule.getHead();
		ByteString boundVariable = null;
		int returnVal = 3;
		boolean relationIsFunctional = 
				(rule.getFunctionalVariablePosition() == 0 && training.functionality(rule.getHead()[1]) >= 0.9) ||
				(rule.getFunctionalVariablePosition() == 2 && training.inverseFunctionality(rule.getHead()[1]) >= 0.84);

		int boundVarPos = rule.getFunctionalVariablePosition();

		//If we know something else about the triple, PCA says it is false
		if(target.count(triple) > 0){
			//Bingo!
			returnVal = 0;
		}else{
			boundVariable = head[boundVarPos];
			head[boundVarPos] = triple[boundVarPos];
			
			//Here apply PCA on the most functional variable
			if(training.count(head) > 0 && relationIsFunctional)
				returnVal = 1;
			else if(target.count(head) > 0 && relationIsFunctional)
				returnVal = 2;
			else
				returnVal = 3;
			
			//Restore the head
			head[boundVarPos] = boundVariable;
		}
		
		return returnVal;		
	}
	
	public static void main(String args[]) throws Exception{
		if(args.length < 4){
			System.err.println("Evaluator <inputfile> <trainingDb> <targetDb> [outputManual] [outputAutomatic]");
			System.exit(1);
		}
		
		File inputFile = new File(args[0]);
		String lastRuleStr = "";
		Rule lastRule = null, currentRule = null;
		
		KB trainingDataset = new KB();
		KB targetDataset = new KB();		
		TSVFile tsvFile = new TSVFile(inputFile);
		
		trainingDataset.load(new File(args[1]));
		targetDataset.load(new File(args[2]));
		FileWriter manualEvalOut = new FileWriter(new File(args[3]));
		FileWriter automaticEvalOut = new FileWriter(new File(args[4]));
		PrintWriter manualEvalPw = new PrintWriter(manualEvalOut);
		PrintWriter automaticEvalPw = new PrintWriter(automaticEvalOut);
		int lineNumber = 1;
		for(List<String> record: tsvFile){
			if(record.size() < 4){
				System.err.println("Warning: record at line " + lineNumber + " has wrong format, ignoring it");
				continue;
			}
			
			ByteString[] triple = new ByteString[3];
			
			String ruleStr = record.get(0);
			if(ruleStr.equals(lastRuleStr)){
				currentRule = lastRule;
			}else{
				Pair<List<ByteString[]>, ByteString[]> rulePair = KB.rule(ruleStr);
				currentRule = new Rule();
				currentRule.getTriples().add(rulePair.second);
				currentRule.getTriples().addAll(rulePair.first);
			}
			
			for(int i = 0; i < 3; ++i){
				triple[i] = ByteString.of(record.get(i + 1));
			}
			
			int evalCode = evaluate(currentRule, triple, trainingDataset, targetDataset);
			if(evalCode == 3){
				//Output it in the manual evaluation file
				manualEvalPw.println(currentRule.getRuleString() + "\t" + triple[0] + "\t" + triple[1] + "\t" + triple[2] + "\tManualEvaluation");
			}else{
				EvalResult evalResult = EvalResult.Unknown;
				EvalSource evalSource = EvalSource.Undefined;
				switch(evalCode){
				case 0:
					evalResult = EvalResult.True;
					evalSource = EvalSource.TargetSource;
					break;
				case 1:
					evalResult = EvalResult.False;
					evalSource = EvalSource.TrainingSource;
					break;
				case 2:
					evalResult = EvalResult.False;
					evalSource = EvalSource.TargetSource;
					break;
				}
				automaticEvalPw.println(currentRule.getRuleString() + "\t" + triple[0] + "\t" + triple[1] + "\t" + triple[2] + "\t" + evalSource + "\t" + evalResult);	
			}
			
			lastRule = currentRule;
			lastRuleStr = ruleStr;
		}
		
		tsvFile.close();
		manualEvalOut.close();
		automaticEvalOut.close();
		manualEvalPw.close();
		automaticEvalPw.close();
	}
}