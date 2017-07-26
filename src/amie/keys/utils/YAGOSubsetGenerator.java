package amie.keys.utils;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import amie.data.KB;
import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;

public class YAGOSubsetGenerator {

	public static void main(String[] args) throws IOException {
		amie.data.U.loadSchemaConf();
		ByteString targetClass = ByteString.of(args[0]);
		KB kb = amie.data.U.loadFiles(args, 1);
		
		// We'll get all the facts about relations 
		Set<ByteString> entities = amie.data.U.getAllEntitiesForType(kb, targetClass);
		ByteString[] triple = KB.triple("?s", "?r", "?o");
		for (ByteString e : entities) {
			triple[0] = e;
			Map<ByteString, IntHashMap<ByteString>> result = 
					kb.resultsTwoVariables(triple[1], triple[2], triple);
			for (ByteString relation : result.keySet()) {
				for (ByteString object : result.get(relation)) {
					System.out.println(e + " " + relation + " " + object);
				}
			}
		}
	}
}
