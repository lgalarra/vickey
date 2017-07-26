/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amie.keys;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author symeonid
 */
public class Node implements Cloneable {

    public HashSet<Integer> set;
    public boolean toExplore;
    
    public Node(Collection<Integer> properties) {
        this.set = new HashSet<Integer>(properties);
        this.toExplore = true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(set.toString() + " ");
        builder.append(toExplore);
        return builder.toString();

    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.set);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Node other = (Node) obj;
        if (!Objects.equals(this.set, other.set)) {
            return false;
        }
        
        return true;
    }

    /**
     * Given a dictionary from ids to property strings, it maps every id in 
     * the key set of the node to its string equivalent
     * @param id2Property
     * @return
     */
	public List<String> mapToString(Map<Integer, String> id2Property) {
		List<String> strings = new ArrayList<>();
		for (Integer id : set) {
			strings.add(id2Property.get(id));
		}
		return strings;
	}
	
	public Node clone() {
		Node cloned = new Node(set);
		cloned.toExplore = this.toExplore;
		return (Node)cloned;
	}

}
