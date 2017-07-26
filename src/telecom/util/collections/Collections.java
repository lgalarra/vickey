package telecom.util.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javatools.datatypes.IntHashMap;

public class Collections {

	/**
	 * Dequeues an element from the given collection. It returns null
	 * if the collection is empty.
	 */
	public static <T> T poll(Collection<T> collection) {
		if (collection.isEmpty()) 
			return null;
		Iterator<T> it = collection.iterator();
		T obj = it.next();
		it.remove();
		return obj;
	}
	
	/**
	 * It enumerates all the subsets of indexes of size 'n' for a collection of items of size 
	 * 'collectionSize'.
	 * @param collectionSize
	 * @param n
	 */
	private static void subsetsOfSize(int collectionSize, int size, List<int[]> output) {
		if (size == 1) {
			for (int i = 0; i < collectionSize; ++i) {
				output.add(new int[]{i});
			}
		} else if (size > 1) {
			List<int[]> setsOfSizeNMinus1 = new ArrayList<int[]>();
			subsetsOfSize(collectionSize, size - 1, setsOfSizeNMinus1);
			for (int[] s : setsOfSizeNMinus1) {
				for (int i = s[s.length - 1] + 1; i < collectionSize; ++i) {
					int[] newSet = new int[s.length + 1];
					for (int k = 0; k < s.length; ++k) {
						newSet[k] = s[k];
					}
					newSet[s.length] = i; 
					output.add(newSet);
				}
			}
		}
	}
	
	
	/**
	 * Recursively enumerates all the subsets of indexes of size 'n' for a collection of items of size 
	 * 'collectionSize'.
	 * @param collectionSize
	 * @param n
	 */
	public static List<int[]> subsetsOfSize(int collectionSize, int size) {
		List<int[]> results = new ArrayList<int[]>();
		subsetsOfSize(collectionSize, size, results);
		return results;
	}

	/**
	 * Enumerates all the subsets of indexes up to size 'n' for a collection of items of size 
	 * 'collectionSize'.
	 * @param collectionSize
	 * @param n
	 * @return
	 */
/**	public static List<int[]> subsetsUpToSize(int collectionSize, int n) {
		List<int[]> subsets = new ArrayList<int[]>();
		subsetsOfSize(collectionSize, n, subsets);
		return subsets;
	} **/
	
	public static void main(String[] args) {		
		for (int[] x : subsetsOfSize(10, 4)) {
			System.out.println(Arrays.toString(x));
		}
	}
	
	/**
	 * Applies reservoir sampling to a collection of items: http://www.geeksforgeeks.org/reservoir-sampling/
	 * @param someCollection
	 * @param sampleSize
	 */
	@SuppressWarnings("unchecked")
	public static <T> Collection<T> reservoirSampling(Collection<T> someCollection, int sampleSize) {
		//Now sample them
		Collection<T> result = new ArrayList<>(sampleSize);	
		ArrayList<T> resultArrayList = (ArrayList<T>)result;
		if(someCollection.size() <= sampleSize){
			return someCollection;
		}else{
			Object[] candidates = someCollection.toArray();
			int i;
			Random r = new Random();
			for(i = 0; i < sampleSize; ++i){				
				result.add((T)candidates[i]);
			}
			
			while(i < candidates.length){
			    int rand = r.nextInt(i);
			    if(rand < sampleSize){
			    	//Pick a random number in the reservoir.
			    	resultArrayList.set(r.nextInt(sampleSize), (T)candidates[i]);
			    }
			    ++i;
			}
		}
		
		return result;
	}
	
	/**
	 * Return the full string representation of a IntHashMap
	 * @param histogram
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <K> String toString(IntHashMap<K> histogram) {
		if (histogram.isEmpty())
			return ("{}");
		StringBuilder b = new StringBuilder("{");
		for (K key : histogram.keys()) {
			b.append(key).append('=').append(histogram.get(key)).append(", ");
		}
		b.setLength(b.length() - 2);
		return (b.append("}").toString());
	}
	
	/**
	 * Adds a value to a multimap, represented as a map where the values
	 * are lists of objects.
	 * @param map
	 * @param key
	 * @param value
	 * @return true if the key already existed in the map.
	 */
	public static <K, V> boolean addToMap(Map<K, List<V>> map, K key, V value) {
		List<V> objects = map.get(key);
		boolean keyExists = true;
		if (objects == null) {
			objects = new ArrayList<V>();
			map.put(key, objects);
			keyExists = false;
		}
		objects.add(value);
		return keyExists;
	}
	
	/**
	 * Given a collection of entities, it returns a string with the concatenation
	 * of the parts separated by the argument 'separator'
	 * @param separator
	 * @param data
	 * @return
	 */
	public static <T> String implode(Collection<T> collection, String separator) {
		if (collection.isEmpty())
			return "";
		
		StringBuilder strBuilder = new StringBuilder();
		for (T obj : collection) {
			strBuilder.append(obj.toString());
			strBuilder.append(separator);
		}
		return strBuilder.toString().substring(0, strBuilder.length() - separator.length());
	}
}
