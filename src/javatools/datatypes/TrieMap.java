package javatools.datatypes;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javatools.administrative.D;

/**
 * This class is part of the Java Tools (see
 * http://mpii.de/yago-naga/javatools). It is licensed under the Creative
 * Commons Attribution License (see http://creativecommons.org/licenses/by/3.0)
 * by the YAGO-NAGA team (see http://mpii.de/yago-naga).
 *
 * The class adds map functionality to a trie. Inspired from javatools.datatypes.Trie.
 */
public class TrieMap<V> extends AbstractMap<CharSequence, V>implements Iterable<Map.Entry<CharSequence, V>> {

  /** Holds the children */
  protected TreeMap<Character, TrieMap<V>> children = new TreeMap<Character, TrieMap<V>>();

  /** true if this is a word */
  protected V value;

  /** number of elements */
  protected int size = 0;

  /** maps to parent*/
  protected TrieMap<V> parent;

  /** Constructs a Trie*/
  public TrieMap() {

  }

  /** Constructs a Trie*/
  protected TrieMap(TrieMap<V> p) {
    parent = p;
  }

  @Override
  public V put(CharSequence key, V value) {
    TrieMap<V> trie = get(key, 0, true);
    V old = trie.value;
    trie.value = value;
    size += 1;
    return old;
  }

  @Override
  public void clear() {
    children.clear();
    value = null;
    size = 0;
  }

  @Override
  public boolean isEmpty() {
    return (size == 0);
  }

  /** Get the subtrie corresponding to the substring of sequence s starting from 'start'.
   *  Missing subtries will be generated, if 'create' is set*/
  protected TrieMap<V> get(CharSequence s, int start, boolean create) {
    if (s.length() == start) {
      return create ? this : null;
    }
    Character c = s.charAt(start);
    if (children.get(c) == null) children.put(c, new TrieMap<V>(this));
    return (children.get(c).get(s, start + 1, create));
  }

  @Override
  public boolean containsKey(Object s) {
    return (s instanceof CharSequence && get((CharSequence) s, 0, false) != null);
  }

  @Override
  public Iterator<Entry<CharSequence, V>> iterator() {
    return iterator("");
  }

  /**
   * Return an iterator for the entries contained in this trie recursively.
   * Append 'prefix' to every key of the returned entries.
   * @param prefix
   * @return an entry iterator
   */
  private Iterator<Entry<CharSequence, V>> iterator(final String prefix) {
    return new PeekIterator<Entry<CharSequence, V>>() {

      // iterator of the treeMap of this node of the TrieMap
      Iterator<Entry<Character, TrieMap<V>>> treeMapIt = null;

      // iterator of the chosen subtrie
      Iterator<Entry<CharSequence, V>> subtrieIt = null;

      String localPrefix;

      boolean isInitialized = false;

      @Override
      protected Entry<CharSequence, V> internalNext() throws Exception {
        if (treeMapIt == null) {
          // initialize treeMapIt
          if (!isInitialized) {
            isInitialized = true;
            if (children != null) {
              treeMapIt = children.entrySet().iterator();
            }
          } else {
            return null;
          }
          // return word representing this subtrie
          if (value != null) {
            return new AbstractMap.SimpleEntry<CharSequence, V>(prefix, value);
          }
        }

        // search next subtrie iterator
        while (subtrieIt == null || subtrieIt.hasNext() == false) {
          if (!treeMapIt.hasNext()) return null;
          Entry<Character, TrieMap<V>> e = treeMapIt.next();
          localPrefix = prefix + e.getKey();
          TrieMap<V> subtrie = e.getValue();
          if (subtrie != null) {
            subtrieIt = subtrie.iterator(localPrefix);
          }
        }

        // iterate over entries of subtrie
        return subtrieIt.next();
      }
    };
  }

  @Override
  public String toString() {
    return "Trie with " + size() + " elements and " + children.size() + " children";
  }

  @Override
  public int size() {
    return (size);
  }

  /**
   * Returns the length of the longest contained subsequence, starting from
   * start position
   */
  public int containedLength(CharSequence s, int startPos) {
    // if recursion stops at this node, return 0 for a valid end,
    // else -1 to back off to parent node
    int terminationValue = (value != null ? 0 : -1);
    if (s.length() <= startPos) return terminationValue;
    Character c = s.charAt(startPos);
    if (children.get(c) == null) return terminationValue;
    int subtreelength = children.get(c).containedLength(s, startPos + 1);
    if (subtreelength == -1) return terminationValue;
    return (subtreelength + 1);
  }

  /** Returns all words found in 'text' */
  public PeekIterator<CharSequence> wordsIn(final CharSequence text) {
    return (new PeekIterator<CharSequence>() {

      int pos = -1;

      @Override
      public CharSequence internalNext() {
        while (++pos < text.length()) {
          int subtreeLength = containedLength(text, pos);
          if (subtreeLength != -1) return (text.subSequence(pos, subtreeLength + pos));
        }
        return (null);
      }
    });
  }

  /** Returns all entries found in 'text' */
  public PeekIterator<Entry<CharSequence, V>> entriesIn(final CharSequence text) {
    final PeekIterator<CharSequence> cs = wordsIn(text);
    return (new PeekIterator<Entry<CharSequence, V>>() {

      @Override
      public Entry<CharSequence, V> internalNext() {
        try {
          CharSequence word = cs.internalNext();
          V val = TrieMap.this.get(word);
          return new AbstractMap.SimpleEntry<CharSequence, V>(word, val);
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      }
    });
  }

  /** Test method */
  public static void main(String[] args) {
    TrieMap<String> t = new TrieMap<String>();
    t.put("hallo", "<hallo>");
    t.put("key", "<key>");
    t.put("du", "<du>");
    t.put("dublin", "<dublin>");
    //for(String s : t.strings()) D.p(s);
    for (Map.Entry<CharSequence, String> e : t)
      D.p(e.getKey() + ": " + e.getValue());
    D.p(t.wordsIn("Blah hallo blub hallo fasel du aus dublin").asList());
  }

  @Override
  public Set<java.util.Map.Entry<CharSequence, V>> entrySet() {
    return new AbstractSet<Entry<CharSequence, V>>() {

      @Override
      public Iterator<Entry<CharSequence, V>> iterator() {
        return TrieMap.this.iterator("");
      }

      @Override
      public int size() {
        return TrieMap.this.size();
      }
    };
  }

  /** Iterable for contained words */
  public Iterable<String> strings() {
    return (new MappedIterator<Map.Entry<CharSequence, V>, String>(iterator(), new MappedIterator.Map<Map.Entry<CharSequence, V>, String>() {

      @Override
      public String map(java.util.Map.Entry<CharSequence, V> e) {
        return e.getKey().toString();
      }
    }));
  }
}
