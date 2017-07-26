package amie.keys;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import javatools.datatypes.ByteString;
import javatools.datatypes.IntHashMap;
import amie.data.KB;
import amie.data.KB.Column;
import amie.rules.Rule;

/**
 * An iterable object that traverses conjunctions of conditions from a KB
 * in a depth-first fashion. Conditions are stored as AMIE rules of the 
 * form r(?a, C) r'(?a, C') .... (in datalog notation), while they are stored
 * as triple patterns: [?a r C], [?a r' C']
 * 
 * @author galarrag
 *
 */
public class ConditionsResult implements Iterable<Rule>{
	private Rule constantConditions;
	
	private List<String> properties;
	
	private KB kb;
	
	private int minSupport;
	
	private long numberOfInstances;
	
	/**
	 * 
	 * @param properties
	 * @param minSupport For a pattern of the form [?a r C], [?a r' C'], this is the minimum number of
	 * instantiations of the variable ?a that should satisfy the pattern.
	 * @param kb
	 */
	public ConditionsResult(List<String> properties, int minSupport, KB kb) {
		this.constantConditions = null;
		this.properties = properties;
		this.kb = kb;
		this.minSupport = minSupport;
		this.numberOfInstances = kb.size(Column.Subject);
	}
	
	public ConditionsResult(Rule conditions, List<String> properties, int minSupport, KB kb) {
		this(properties, minSupport, kb);
		this.constantConditions = conditions;
	}
	

	/**
	 * It iterates a set of condition results in a depth-first manner. This iterator
	 * materializes all possible conditions on demand. Conditions are stored as AMIE
	 * rules.
	 * 
	 * @author galarrag
	 *
	 */
	class ConditionsIterator implements Iterator<Rule>{
		/**
		 * LIFO structure that allow us to search depth-first.
		 */
		private Stack<Rule> atomsStack;
		
		private boolean done;
		
		/**
		 * It initializes the stack with all the conditions of size 1. For example given the relations
		 *  [r, r'] (non-key of size 2) and if the possible instantiations for these relations are 
		 *  r(?a, C1), r(?a, C2), r'(?a, C1'), r'(?a, C2') the stack is initialized with all the instantiations
		 *  above the support threshold.
		 */
		public ConditionsIterator() {
			this.atomsStack = new Stack<>();
			this.done = false;
			// Initialize the stack
			ByteString[] firstAtom = buildAtom(properties.get(0));
			List<ByteString[]> query = new ArrayList<>();
			if (constantConditions != null) {
				query.addAll(constantConditions.getTriples());
			}
			query.add(firstAtom);
			IntHashMap<ByteString> firstInstantiations = 
					kb.frequentBindingsOf(firstAtom[2], firstAtom[0], query);
			for (ByteString inst : firstInstantiations) {
				int support = firstInstantiations.get(inst);
				if (support >= minSupport) {
					ByteString[] instantiatedAtom = firstAtom.clone();
					instantiatedAtom[2] = inst;
					Rule rule = null;
					if (constantConditions == null || constantConditions.isEmpty()) {
						rule = new Rule(instantiatedAtom, support);	
					} else {
						rule = new Rule(instantiatedAtom, constantConditions.getTriples(), support);
					}
					rule.setSupportRatio(support / (double)numberOfInstances);
					atomsStack.push(rule);
				}
			}
		}
		
		/**
		 * It pops out the top element of the stack and refines it with all possible instantiations
		 * of the next property in the set of properties. Given the relations [r, r', r''], if the 
		 * top of the stack contains the atoms r(?a, C') r'(?a, C'), then populate stack with add all
		 * refinements of the form r(?a, C') r'(?a, C') r''(?a, ?X) where ?X will be replaced by all 
		 * constants that keep the whole pattern above the support threshold (recall that support is defined
		 * in terms of the number of instantiations of ?a)
		 */
		private void populateStack() {
			while (!atomsStack.isEmpty() &&
					atomsStack.peek().getLength() < properties.size()) {
				Rule currentRule = atomsStack.pop();
				String nextProperty = properties.get(currentRule.getLength());
				ByteString nextAtom[] = buildAtom(nextProperty);
				List<ByteString[]> query = new ArrayList<ByteString[]>();
				query.addAll(currentRule.getTriples());
				query.add(nextAtom);
				IntHashMap<ByteString> instantiations = 
						kb.frequentBindingsOf(nextAtom[2], nextAtom[0], query);
				for (ByteString inst : instantiations) {
					int support = instantiations.get(inst);
					if (support >= minSupport) {
						List<ByteString[]> deepClone = cloneDeeply(query);
						ByteString[] lastAtom = deepClone.get(deepClone.size() - 1);
						lastAtom[2] = inst;
						Rule newRule = new Rule();
						newRule.setSupport(support);
						newRule.setSupportRatio(support / (double)numberOfInstances);
						newRule.getTriples().addAll(deepClone);
						atomsStack.push(newRule);
					}
				}
			}
			
			if (atomsStack.isEmpty())
				this.done = true;	
		}
		
		/**
		 * Returns a clone of a list of triples patterns where each element of 
		 * the list has been also cloned.
		 * @param query
		 * @return
		 */
		private List<ByteString[]> cloneDeeply(List<ByteString[]> query) {
			List<ByteString[]> result = new ArrayList<ByteString[]>();
			for (ByteString[] atom : query) {
				result.add(atom.clone());
			}
			
			return result;
		}

		private ByteString[] buildAtom(String relation) {
			return KB.triple(ByteString.of("?a"), ByteString.of(relation), ByteString.of("?b"));
		}

		@Override
		public boolean hasNext() {
			populateStack();
			return !this.done;
		}
	
		@Override
		public Rule next() {
			if (!hasNext())
				throw new NoSuchElementException();

			return atomsStack.pop();
		}
	
		@Override
		public void remove() {
			
		}
	}


	@Override
	public java.util.Iterator<Rule> iterator() {
		return new ConditionsIterator();
	}

}
