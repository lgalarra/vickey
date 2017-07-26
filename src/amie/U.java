package amie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import javatools.datatypes.Triple;

public class U {

	/**
	 * Outputs a list of objects separated by tabs in one line.
	 * @param list
	 */
	public static <T> void tsvOutput(List<T> line) {
		for (int i = 0; i < line.size() - 1; ++i) {
			System.out.print(line.get(i) + "\t");
		}	
		System.out.println(line.get(line.size() - 1));
	}
	
	/**
	 * Prints a IntHashMap representing a histogram.
	 * @param histogram
	 */
	public static void printHistogram(IntHashMap<Integer> histogram) {
		for (Integer key : histogram.keys()) {			
			System.out.println(key + "\t" + histogram.get(key));
		}
	}
	
	/**
	 * Prints a histogram as well as the probability that X > Xi
	 * for each Xi in the histogram.
	 * @param histogram
	 */
	public static void printHistogramAndCumulativeDistribution(IntHashMap<Integer> histogram) {
		double total = 1.0;
		double accum = 0.0;
		double sum = histogram.computeSum();
		for (Integer key : histogram.keys()) {
			double prob = histogram.get(key) / sum;
			accum += prob;
			System.out.println(key + "\t" + histogram.get(key) + "\t" + prob + "\t" + (total - accum));
		}
	}
	
	/**
	 * It constructs a histogram based on a multimap.
	 * @param map
	 * @return
	 */
	public static <E, T> IntHashMap<Integer> buildHistogram(Map<T, List<E>> map) {
		IntHashMap<Integer> histogram = new IntHashMap<>();
		for (T key : map.keySet()) {
			histogram.increase(map.get(key).size());
		}
		return histogram;
	}
	
	/**
	 * Converts an array into a triple
	 * @param array
	 * @return
	 */
	public static <T> Triple<T, T, T> toTriple(T[] array) {
		if (array.length < 3) {
			return null;
		} else {
			return new Triple<T, T, T>(array[0], array[1], array[2]);
		}
	}
	
	/**
	 * Converts an array into a triple
	 * @param array
	 * @return
	 */
	public static ByteString[] toArray(Triple<ByteString, ByteString, ByteString> triple) {
		return new ByteString[] { triple.first, triple.second, triple.third};
	}
	
	/**
	 * Performs a deep clone of the given list, i.e., it returns a new list where 
	 * each element has been cloned.
	 * @param collection
	 */
	public static <T> List<T[]> deepClone(List<T[]> collection) {
		List<T[]> newList = new ArrayList<>(collection.size());
		for (T[] t : collection) {
			newList.add(t.clone());
		}
		return newList;
	}
}
