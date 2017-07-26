package amie.keys;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javatools.datatypes.ByteString;

public class NonKeysGraph {
	
	public static ByteString StartNode = ByteString.of("ST");
	
	private Map<ByteString, Set<ByteString>> graph;

	public NonKeysGraph(String nonKeysFile) throws IOException {
		this.graph = new HashMap<ByteString, Set<ByteString>>();
		Set<ByteString> startingEdges = new HashSet<ByteString>();
		this.graph.put(StartNode, startingEdges);
		List<String> lines = Files.readAllLines(Paths.get(nonKeysFile), Charset.forName("UTF-8"));
		// Bootstrapping phase
		for (String line : lines) {
			String[] relations = line.split(",");
			for (int i = 0; i < relations.length; ++i) {
				ByteString relationBS = ByteString.of(relations[i].trim());
				startingEdges.add(relationBS);
				Set<ByteString> edgesForRelation = this.graph.get(relationBS);
				if (edgesForRelation == null) {
					edgesForRelation = new HashSet<ByteString>();
					this.graph.put(relationBS, edgesForRelation);
				}
				
				for (int j = i + 1; j < relations.length; ++j) {
					edgesForRelation.add(ByteString.of(relations[j].trim()));
				}
			}			
		}
	}
	
	public String toString() {
		return this.graph.toString();
	}
	
	public Set<ByteString> getEdges(ByteString relation) {
		return this.graph.get(relation);
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println(new NonKeysGraph("/home/galarrag/Dropbox/postdoc/nonkeystest-luis.txt"));

	}

}
