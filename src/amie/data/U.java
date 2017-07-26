package amie.data;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;

/**
 * Set of commonly used functions.
 * 
 * @author lgalarra
 *
 */
public class U {
	
	/** X rdf:type Class **/
	public static String typeRelation = "rdf:type";
	
	public static ByteString typeRelationBS = ByteString.of(typeRelation);
	
	/** Class1 rdfs:subClassOf Class2 **/
	public static String subClassRelation = "rdfs:subClassOf";
	
	public static ByteString subClassRelationBS = ByteString.of(subClassRelation);
	
	/** relation1 rdfs:subPropertyOf relation2 **/
	public static String subPropertyRelation = "rdfs:subPropertyOf";
	
	public static ByteString subPropertyRelationBS = ByteString.of(subPropertyRelation);
	
	/** Class rdfs:domain relation **/
	public static String domainRelation = "rdfs:domain";
	
	public static ByteString domainRelationBS = ByteString.of(domainRelation);
	
	/** Class rdfs:domain range **/
	public static String rangeRelation = "rdfs:range";
	
	public static ByteString rangeRelationBS = ByteString.of(rangeRelation);
	
	public static List<ByteString> schemaRelationsBS = Arrays.asList(typeRelationBS, subClassRelationBS, 
			subPropertyRelationBS, domainRelationBS, rangeRelationBS);
	
	public static List<String> schemaRelations = Arrays.asList(typeRelation, subClassRelation, 
			subPropertyRelation, domainRelation, rangeRelation);
	
	public static void loadSchemaConf() {
		try {
			String schemaPath = System.getProperty("schema");
			if (schemaPath == null) {
				schemaPath = "conf/schema_properties";
			}
			List<String> lines = Files.readAllLines(Paths.get(schemaPath),
			        Charset.defaultCharset());
			for (String line : lines) {
				String[] lineParts = line.split("=");
				if (lineParts.length < 2)
					continue;
				try {
					amie.data.U.class.getField(lineParts[0]).set(null, lineParts[1]);
					amie.data.U.class.getField(lineParts[0] + "BS").set(null, ByteString.of(lineParts[1]));
				} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
						| SecurityException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			System.err.println("Using the default schema relations");
		}
		
	}
	
	/**
	 * True if the relation is a special RDF/RDFS relation such as
	 * rdf:type
	 * @param relation
	 * @return
	 */
	public static boolean isSchemaRelation(ByteString relation) {
		return schemaRelationsBS.contains(relation);
	}
	
	/**
	 * True if the relation is a special RDF/RDFS relation such as
	 * rdf:type
	 * @param relation
	 * @return
	 */
	public static boolean isSchemaRelation(String relation) {
		return schemaRelations.contains(relation);
	}

	/**
	 * Returns the domain of a given relation in a knowledge base
	 * @param source
	 * @param relation
	 * @return
	 */
	public static ByteString getRelationDomain(KB source, ByteString relation){
		List<ByteString[]> query = KB.triples(KB.triple(relation, domainRelation, "?x"));
		Set<ByteString> domains = source.selectDistinct(ByteString.of("?x"), query);
		if(!domains.isEmpty()){
			return domains.iterator().next();
		}
		
		//Try looking for the superproperty
		List<ByteString[]> query2 = KB.triples(KB.triple(relation, subPropertyRelation, "?y"), 
				KB.triple("?y", "rdfs:domain", "?x"));
		
		domains = source.selectDistinct(ByteString.of("?x"), query2);
		if(!domains.isEmpty()){
			return domains.iterator().next();
		}
		
		return null;
	}
	
	/**
	 * Returns the range of a given relation in a knowledge base.
	 * @param source
	 * @param relation
	 * @return
	 */
	public static ByteString getRelationRange(KB source, ByteString relation){
		List<ByteString[]> query = KB.triples(KB.triple(relation, rangeRelation, "?x"));
		Set<ByteString> ranges = source.selectDistinct(ByteString.of("?x"), query);
		if(!ranges.isEmpty()){
			return ranges.iterator().next();
		}
		
		//Try looking for the superproperty
		List<ByteString[]> query2 = KB.triples(KB.triple(relation, subPropertyRelation, "?y"), 
				KB.triple("?y", "rdfs:range", "?x"));
		
		ranges = source.selectDistinct(ByteString.of("?x"), query2);
		if(!ranges.isEmpty()){
			return ranges.iterator().next();
		}
		
		return null;		
	}
	
	/**
	 * It returns all the materialized types of an entity in a knowledge base.
	 * @param source
	 * @param entity
	 * @return
	 */
	public static Set<ByteString> getMaterializedTypesForEntity(KB source, ByteString entity){
		List<ByteString[]> query = KB.triples(KB.triple(entity, typeRelationBS, ByteString.of("?x")));
		return source.selectDistinct(ByteString.of("?x"), query);
	}
	
	/**
	 * Determines whether a given type is specific, that is, it does not have subclasses.
	 * @param source
	 * @param type
	 * @return
	 */
	public static boolean isLeafDatatype(KB source, ByteString type){
		List<ByteString[]> query = KB.triples(KB.triple("?x", subClassRelation, type));		
		return source.countDistinct(ByteString.of("?x"), query) == 0;
	}
	
	/**
	 * It returns the most specific types of an entity according to the type hierarchy
	 * of the knowledge base.
	 * @param source
	 * @param entity
	 * @return
	 */
	public static Set<ByteString> getLeafTypesForEntity(KB source, ByteString entity){
		Set<ByteString> tmpTypes = getMaterializedTypesForEntity(source, entity);
		Set<ByteString> resultTypes = new HashSet<ByteString>();
		
		for(ByteString type: tmpTypes){
			if(isLeafDatatype(source, type)){
				resultTypes.add(type);
			}
		}
		
		return resultTypes;
	}
	
	/**
	 * It returns all the types of a given entity.
	 * @param source
	 * @param entity
	 * @return
	 */
	public static Set<ByteString> getAllTypesForEntity(KB source, ByteString entity){
		Set<ByteString> leafTypes = getMaterializedTypesForEntity(source, entity);
		Set<ByteString> resultTypes = new HashSet<ByteString>(leafTypes);
		for(ByteString leafType: leafTypes){
			resultTypes.addAll(getAllSuperTypes(source, leafType));
		}
		return resultTypes;
	}
	
	/**
	 * It returns all the immediate super-types of a given type.
	 * @param source
	 * @param type
	 * @return
	 */
	public static Set<ByteString> getSuperTypes(KB source, ByteString type){
		List<ByteString[]> query = KB.triples(KB.triple(type, subClassRelation, "?x"));		
		return new LinkedHashSet<ByteString>(source.selectDistinct(ByteString.of("?x"), query));
	}
	
	/**
	 * It returns all the supertypes of a given type.
	 * @param source
	 * @param type
	 * @return
	 */
	public static Set<ByteString> getAllSuperTypes(KB source, ByteString type) {
		Set<ByteString> resultSet = new LinkedHashSet<ByteString>();
		Queue<ByteString> queue = new LinkedList<>();
		Set<ByteString> seenTypes = new LinkedHashSet<>();
		Set<ByteString> superTypes = getSuperTypes(source, type);
		queue.addAll(superTypes);
		seenTypes.addAll(superTypes);
		
		while (!queue.isEmpty()) {
			ByteString currentType = queue.poll();
			resultSet.add(currentType);
			superTypes = getSuperTypes(source, currentType);
			for (ByteString st : superTypes) {
		        if (!seenTypes.contains(st)) {
	                seenTypes.add(st);
	                queue.add(st);
		        }
			}
		}
		
		return resultSet;
	}
	
	/**
	 * It returns all the instances of a given type.
	 * @param source
	 * @param type
	 * @return
	 */
	public static Set<ByteString> getAllEntitiesForType(KB source, ByteString type) {
		List<ByteString[]> query = KB.triples(KB.triple("?x", typeRelation, type));		
		return new LinkedHashSet<ByteString>(source.selectDistinct(ByteString.of("?x"), query));	
	}
	
	/**
	 * Returns the number of instances of the given class in a KB
	 * @param kb
	 * @param type
	 * @return
	 */
	public static long getNumberOfEntitiesForType(KB kb, ByteString type) {
		return kb.count(ByteString.of("?s"), typeRelationBS, type);
	}
	
	/**
	 * Returns all present data types in the given KB.
	 * @param kb
	 */
	public static Set<ByteString> getAllTypes(KB kb) {
		List<ByteString[]> query = KB.triples(KB.triple("?x", typeRelation, "?type"));		
		return new LinkedHashSet<ByteString>(kb.selectDistinct(ByteString.of("?type"), query));	
	}
	
	/**
	 * Gets all the entities of the type of the given relation's domain.
	 * @param source
	 * @param relation
	 * @return
	 */
	public static Set<ByteString> getDomainSet(KB source, ByteString relation) {
		ByteString domainType = getRelationDomain(source, relation);
		Set<ByteString> result = new LinkedHashSet<ByteString>();
		if (domainType != null) 
			result.addAll(getAllEntitiesForType(source, domainType));
		result.addAll(source.relation2subject2object.get(relation).keySet());
		return result;
	}
	
	/**
	 * Gets all the entities of the given type that occur as subjects in the relation.
	 * @param source
	 * @param relation
	 * @return
	 */
	public static Set<ByteString> getDomainSet(KB source, ByteString relation, 
			ByteString domainType) {
		List<ByteString[]> query = null;
		String queryVar = "?s";
		query = KB.triples(KB.triple("?s", relation, "?o"), 
						   KB.triple(ByteString.of(queryVar), 
								   amie.data.U.typeRelationBS, domainType));
		
		return source.selectDistinct(ByteString.of(queryVar), query);		
	}

	
	/**
	 * Get all the immediate subtypes of a given type.
	 * @param source
	 * @param type
	 * @return
	 */
	public static Set<ByteString> getSubtypes(KB source, ByteString type) {
		List<ByteString[]> query = KB.triples(KB.triple(ByteString.of("?x"), subClassRelation, type));		
		return new LinkedHashSet<ByteString>(source.selectDistinct(ByteString.of("?x"), query));	
	}
	
	/**
	 * Get all subtypes of a given type.
	 * @param source
	 * @param type
	 * @return
	 */
	public static Set<ByteString> getAllSubtypes(KB source, ByteString type) {
		Set<ByteString> resultSet = new LinkedHashSet<ByteString>();
		Queue<ByteString> queue = new LinkedList<>();
		Set<ByteString> seenTypes = new LinkedHashSet<>();
		Set<ByteString> subTypes = getSubtypes(source, type);
		queue.addAll(subTypes);
		seenTypes.addAll(subTypes);
		
		while (!queue.isEmpty()) {
			ByteString currentType = queue.poll();
			resultSet.add(currentType);
			subTypes = getSubtypes(source, currentType);
			for (ByteString st : subTypes) {
		        if (!seenTypes.contains(st)) {
	                seenTypes.add(st);
	                queue.add(st);
		        }
			}
		}
		
		return resultSet;
	}
	
	/**
	 * Gets all the entities of the type of the given relation's range.
	 * @param source
	 * @param relation
	 * @return
	 */
	public static Set<ByteString> getRangeSet(KB source, ByteString relation) {
		ByteString rangeType = getRelationRange(source, relation);
		Set<ByteString> result = new LinkedHashSet<ByteString>();
		if (rangeType != null) 
			result.addAll(getAllEntitiesForType(source, rangeType));
		result.addAll(source.relation2object2subject.get(relation).keySet());
		return result;
	}
	

	/**
	 * Gets all the entities of the given type that occur as objects in the relation.
	 * @param source
	 * @param relation
	 * @return
	 */
	public static Set<ByteString> getRangeSet(KB source, ByteString relation, 
			ByteString rangeType) {
		List<ByteString[]> query = null;
		String queryVar = "?o";
		query = KB.triples(KB.triple("?s", relation, "?o"), 
						   KB.triple(ByteString.of(queryVar), 
								   amie.data.U.typeRelationBS, rangeType));
		
		return source.selectDistinct(ByteString.of(queryVar), query);		
	}
	
	/**
	 * It performs a KB coalesce between 2 KBs consisting of all the facts in both ontologies
	 * for the intersection of all entities in the first KB with the subjects of the second KB.
	 * @param source1
	 * @param source2
	 * @param withObjs If true, the coalesce is done between all the entities in the first KB
	 * and all the entities in the second KB.
	 */
	public static void coalesce(KB source1, 
			KB source2, boolean withObjs) {
		Set<ByteString> sourceEntities = new LinkedHashSet<>();
		sourceEntities.addAll(source1.subjectSize);
		sourceEntities.addAll(source1.objectSize);
		for(ByteString entity: sourceEntities){
			//Print all facts of the source ontology
			Map<ByteString, IntHashMap<ByteString>> tail1 = source1.subject2relation2object.get(entity);
			Map<ByteString, IntHashMap<ByteString>> tail2 = source2.subject2relation2object.get(entity);
			if(tail2 == null)
				continue;
						
			for(ByteString predicate: tail1.keySet()){
				for(ByteString object: tail1.get(predicate)){
					System.out.println(entity + "\t" + predicate + "\t" + object);
				}
			}
			//Print all facts in the target ontology
			for(ByteString predicate: tail2.keySet()){
				for(ByteString object: tail2.get(predicate)){
					System.out.println(entity + "\t" + predicate + "\t" + object);
				}
			}
		}
		
		if(withObjs){
			for(ByteString entity: source2.objectSize){
				if(sourceEntities.contains(entity)) continue;
				
				Map<ByteString, IntHashMap<ByteString>> tail2 = source2.subject2relation2object.get(entity);
				if(tail2 == null) continue;
				
				//Print all facts in the target ontology
				for(ByteString predicate: tail2.keySet()){
					for(ByteString object: tail2.get(predicate)){
						System.out.println(entity + "\t" + predicate + "\t" + object);
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * @param source
	 */
	public static void printOverlapTable(KB source) {
		//for each pair of relations, print the overlap table
		System.out.println("Relation1\tRelation2\tRelation1-subjects"
				+ "\tRelation1-objects\tRelation2-subjects\tRelation2-objects"
				+ "\tSubject-Subject\tSubject-Object\tObject-Subject\tObject-Object");
		for(ByteString r1: source.relationSize){
			Set<ByteString> subjects1 = source.relation2subject2object.get(r1).keySet();
			Set<ByteString> objects1 = source.relation2object2subject.get(r1).keySet();
			int nSubjectsr1 = subjects1.size();
			int nObjectsr1 = objects1.size();
			for(ByteString r2: source.relationSize){
				if(r1.equals(r2))
					continue;				
				System.out.print(r1 + "\t");
				System.out.print(r2 + "\t");
				Set<ByteString> subjects2 = source.relation2subject2object.get(r2).keySet();
				Set<ByteString> objects2 = source.relation2object2subject.get(r2).keySet();
				int nSubjectr2 = subjects2.size();
				int nObjectsr2 = objects2.size();
				System.out.print(nSubjectsr1 + "\t" + nObjectsr1 + "\t" + nSubjectr2 + "\t" + nObjectsr2 + "\t");
				System.out.print(computeOverlap(subjects1, subjects2) + "\t");
				System.out.print(computeOverlap(subjects1, objects2) + "\t");
				System.out.print(computeOverlap(subjects2, objects1) + "\t");
				System.out.println(computeOverlap(objects1, objects2));
			}
		}		
	}
		
	
	/**
	 * Returns a KB with the content of all the files referenced in the string array.
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static KB loadFiles(String args[]) throws IOException {
		// Load the data
		KB kb = new KB();
		List<File> files = new ArrayList<File>();
		for (int i = 0; i < args.length; ++i) {
			files.add(new File(args[i]));
		}
		kb.load(files);
		return kb;
	}
	
	/**
	 * Returns a KB with the content of all the files referenced in the object array.
	 * Each element of the array is converted to a string object
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static KB loadFiles(Object args[]) throws IOException {
		// Load the data
		KB kb = new KB();
		List<File> files = new ArrayList<File>();
		for (int i = 0; i < args.length; ++i) {
			files.add(new File((String)args[i]));
		}
		kb.load(files);
		return kb;
	}
	
	
	/**
	 * Returns a KB with the content of all the files referenced in the string array
	 * starting from a given position.
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static KB loadFiles(String args[], int fromIndex) throws IOException {
		if (fromIndex >= args.length)
			throw new IllegalArgumentException("Index " + fromIndex + 
					" equal or bigger than size of the array.");
		// Load the data
		KB kb = new KB();
		List<File> files = new ArrayList<File>();
		for (int i = fromIndex; i < args.length; ++i) {
			files.add(new File(args[i]));
		}
		kb.load(files);
		return kb;
	}
	
	/**
	 * Returns a KB with the content of all the files referenced in the string array.
	 * @param args
	 * @return
	 * @throws IOException
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public static KB loadFiles(String args[], Class kbSubclass) 
			throws IOException, InstantiationException, IllegalAccessException, 
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		// Load the data
		KB kb = (KB) kbSubclass.getConstructor().newInstance();
		List<File> files = new ArrayList<File>();
		for (int i = 0; i < args.length; ++i) {
			files.add(new File(args[i]));
		}
		kb.load(files);
		return kb;
	}
	
	/**
	 * Returns a KB with the content of all the files referenced in the subarray starting
	 * at the given index of the input array 'args'.
	 * @param args
	 * @return
	 * @throws IOException
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public static KB loadFiles(String args[], int fromIndex, Class kbSubclass) 
			throws IOException, InstantiationException, IllegalAccessException, 
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		// Load the data
		KB kb = (KB) kbSubclass.getConstructor().newInstance();
		List<File> files = new ArrayList<File>();
		for (int i = fromIndex; i < args.length; ++i) {
			files.add(new File(args[i]));
		}
		kb.load(files);
		return kb;
	}

	/**
	 * 
	 * @param subjects1
	 * @param subjects2
	 * @return
	 */
	private static int computeOverlap(Set<ByteString> subjects1,
			Set<ByteString> subjects2) {
		int overlap = 0; 
		for(ByteString entity1 : subjects1){
			if(subjects2.contains(entity1))
				++overlap;
		}
		
		return overlap;
	}
	
	/**
	 * Compute a histogram on the theorethical domain of the relation (all the instances
	 * of the type defined as domain of the relation). This function looks at the most functional
	 * side, that is, if the relation is more inverse functional than functional it will calculate
	 * the histogram on the inverse relation (meaning it will provide a histogram of the range).
	 * @param kb
	 * @param relation
	 * @return
	 */
	public static IntHashMap<Integer> getHistogramOnDomain(KB kb, ByteString relation) {
		IntHashMap<Integer> hist = new IntHashMap<>();
		List<ByteString[]> query = null;
		String queryVar = null;
		String existVar = null;
		ByteString targetType = null;
	
		if (kb.isFunctional(relation)) {
			queryVar = "?s";
			existVar = "?o";
			query = KB.triples(KB.triple("?s", relation, "?o"));
			targetType = getRelationDomain(kb, relation);
		} else {
			queryVar = "?o";
			existVar = "?s";
			query = KB.triples(KB.triple("?o", relation, "?s"));
			targetType = getRelationRange(kb, relation);
		}
		
		if (targetType == null) {
			return hist;
		}
		
		Set<ByteString> effectiveDomain = kb.selectDistinct(ByteString.of(queryVar), query);
		Set<ByteString> theorethicalDomain = getAllEntitiesForType(kb, targetType);
		effectiveDomain.retainAll(theorethicalDomain);
		for (ByteString entity : effectiveDomain) {
			long val;
			if (kb.isFunctional(relation)) {
				val = kb.count(KB.triple(entity, relation, ByteString.of(existVar)));
			} else {
				val = kb.count(KB.triple(ByteString.of(queryVar), relation, entity));
			}
			hist.increase((int)val);
		}
		kb.selectDistinct(ByteString.of(existVar), query);
		
		return hist;		
	}
	
	/**
	 * Computes a histogram for relation on a given type (all the instances
	 * of the provided type). The type must be a subclass of the domain of the relation.
	 * This function looks at the most functional side of the relation, that is, if the relation 
	 * is more inverse functional than functional it will calculate the histogram on the 
	 * inverse relation (meaning it will provide a histogram of the range).
	 * @param kb
	 * @param relation
	 * @return
	 */
	public static IntHashMap<Integer> getHistogramOnDomain(KB kb,
			ByteString relation, ByteString domainType) {
		IntHashMap<Integer> hist = new IntHashMap<>();
		List<ByteString[]> query = null;
		String queryVar = null;
		String existVar = null;
	
		if (kb.isFunctional(relation)) {
			queryVar = "?s";
			existVar = "?o";
			query = KB.triples(KB.triple("?s", relation, "?o"), 
							   KB.triple(ByteString.of("?s"), 
									   amie.data.U.typeRelationBS, domainType));
		} else {
			queryVar = "?o";
			existVar = "?s";
			query = KB.triples(KB.triple("?o", relation, "?s"),
					 		   KB.triple(ByteString.of("?o"), 
					 				   amie.data.U.typeRelationBS, domainType));
		}
				
		Set<ByteString> effectiveDomain = kb.selectDistinct(ByteString.of(queryVar), query);
		for (ByteString entity : effectiveDomain) {
			long val;
			if (kb.isFunctional(relation)) {
				val = kb.count(KB.triple(entity, relation, ByteString.of(existVar)));
			} else {
				val = kb.count(KB.triple(ByteString.of(queryVar), relation, entity));
			}
			hist.increase((int)val);
		}
		kb.selectDistinct(ByteString.of(existVar), query);
		
		return hist;	
	}
	
	/**
	 * It returns a map containing the number of instances of each class in the KB.
	 * @param kb
	 * @return
	 */
	public static IntHashMap<ByteString> getTypesCount(KB kb) {
		List<ByteString[]> query = KB.triples(KB.triple("?s", typeRelation, "?o"));
		Map<ByteString, IntHashMap<ByteString>> types2Instances = 
				kb.selectDistinct(ByteString.of("?o"), ByteString.of("?s"), query);
		IntHashMap<ByteString> result = new IntHashMap<>();
		for (ByteString type : types2Instances.keySet()) {
			result.put(type, types2Instances.get(type).size());
		}
		return result;
	}

	
	public static void main(String args[]) throws IOException {
		KB d = new KB();
	    ArrayList<File> files = new ArrayList<File>();
	    for(String file: args)
	    	files.add(new File(file));
	    
	    d.load(files);
	    
	    for(ByteString relation: d.relationSize){
	    	System.out.println(relation + "\t" + getRelationDomain(d, relation) 
	    			+ "\t" + getRelationRange(d, relation));
	    }
	}

	
	/**
	 * It returns the number of facts where the given entity participates as
	 * a subject or object.
	 * @param kb
	 * @param entity
	 * @return
	 */
	public static int numberOfFacts(KB kb, ByteString entity) {
		ByteString[] querySubject = KB.triple(entity, ByteString.of("?r"), ByteString.of("?o")); 
		ByteString[] queryObject = KB.triple(ByteString.of("?s"), ByteString.of("?r"), entity); 
		return (int)kb.count(querySubject) + (int)kb.count(queryObject);
	}
	
	/**
	 * It returns the number of facts where the given entity participates as
	 * a subject or object.
	 * @param kb
	 * @param entity
	 * @param omittedRelations These relations are not counted as facts.
	 * @return
	 */
	public static int numberOfFacts(KB kb, ByteString entity, Collection<ByteString> omittedRelations) {
		ByteString[] querySubject = KB.triple(entity, ByteString.of("?r"), ByteString.of("?o")); 
		ByteString[] queryObject = KB.triple(ByteString.of("?s"), ByteString.of("?r"), entity); 
		Map<ByteString, IntHashMap<ByteString>> relationsSubject = 
				kb.resultsTwoVariables(ByteString.of("?r"), ByteString.of("?o"), querySubject);
		Map<ByteString, IntHashMap<ByteString>> relationsObject = 
				kb.resultsTwoVariables(ByteString.of("?r"), ByteString.of("?s"), queryObject);
		int count1 = 0;
		int count2 = 0;
		for (ByteString relation : relationsSubject.keySet()) {
			if (!omittedRelations.contains(relation))
				count1 += relationsSubject.get(relation).size();
		}
		
		for (ByteString relation : relationsObject.keySet()) {
			if (!omittedRelations.contains(relation))
				count1 += relationsObject.get(relation).size();
		}

		return count1 + count2;
	}
	
	/**
	 * Returns true if the relation is defined as a function.
	 * @return
	 */
	public static boolean isFunction(KB kb, ByteString relation) {
		return kb.contains(relation, ByteString.of("<isFunction>"), ByteString.of("TRUE"));
	}
	
	/**
	 * Returns true if the relation is defined as compulsory for all members 
	 * of its domain (this function assumes relations are always analyzed from 
	 * their most functional side.
	 * @return
	 */
	public static boolean isMandatory(KB kb, ByteString relation) {
		return kb.contains(relation, ByteString.of("<isMandatory>"), ByteString.of("TRUE"));
	}

	/**
	 * It returns all the entities that have 'cardinality' different number of values
	 * for the given relation.
	 * @param kb
	 * @param relation
	 * @param cardinality
	 * @return
	 */
	public static Set<ByteString> getEntitiesWithCardinality(KB kb, ByteString relation, int cardinality) {
		Map<ByteString, IntHashMap<ByteString>> results = null;
		List<ByteString[]> query = KB.triples(KB.triple(ByteString.of("?s"), 
				relation, ByteString.of("?o")));
		if (kb.isFunctional(relation)) {
			results = kb.selectDistinct(ByteString.of("?s"), ByteString.of("?o"), query);
		} else {
			results = kb.selectDistinct(ByteString.of("?o"), ByteString.of("?s"), query);			
		}
		Set<ByteString> entities = new LinkedHashSet<>();
		for (ByteString e : results.keySet()) {
			if (results.get(e).size() == cardinality) {
				entities.add(e);
			}
		}
		return entities;
	}

	/**
	 * Determines if the first class is a superclass of the second.
	 * @param parentType
	 * @param childType
	 * @return
	 */
	public static boolean isSuperType(KB kb, ByteString parentType, ByteString childType) {
		Set<ByteString> st1 = getSuperTypes(kb, childType);
		return st1.contains(parentType);
	}
}
