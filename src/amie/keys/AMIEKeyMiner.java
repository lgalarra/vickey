package amie.keys;

import java.util.Arrays;
import java.util.List;

import amie.data.KB;
import amie.mining.AMIE;
import amie.rosa.AlignKBs;
import amie.rules.ConfidenceMetric;
import amie.rules.Metric;
import amie.rules.Rule;

public class AMIEKeyMiner {

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("AMIEKeyMiner <support> <non-keys> <file1> [file2] ...");
		}
		long startTime = System.currentTimeMillis();
		KB dataSource = AlignKBs.loadFiles(args, 2);
		double numberOfSubjects = dataSource.size(KB.Column.Subject);
		double minInitialSupport = Double.parseDouble(args[0]);
		double threshold = minInitialSupport < 1.0 ? numberOfSubjects * minInitialSupport : minInitialSupport;
		ConditionalKeyMiningAssistant assistant = new ConditionalKeyMiningAssistant(dataSource, args[1]);
		assistant.setConfidenceMetric(ConfidenceMetric.PCAConfidence);
		assistant.setMaxDepth(100);
		assistant.setPcaConfidenceThreshold(1.0);
		AMIE amie = new AMIE(assistant, (int)threshold, threshold, Metric.Support, Runtime.getRuntime().availableProcessors());
		amie.setSeeds(Arrays.asList(KB.EQUALSbs));
		amie.setRealTime(true);
		List<Rule> rules = amie.mine();
		for (Rule r : rules) {
			System.out.println(Utilities.formatKey(r));
		}
		
		long endTime = System.currentTimeMillis();
		System.out.println("The mining has taken " + (endTime - startTime) + " milliseconds");
		System.out.println("AMIE has mined " + rules.size() + " conditional keys");
	}

}
