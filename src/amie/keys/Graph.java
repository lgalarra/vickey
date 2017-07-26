/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amie.keys;


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import amie.data.KB;

/**
 *
 * @author Danai
 */
public class Graph implements Cloneable {


    public HashMap<Node, Node> nodes;

    public HashMap<Node, HashSet<Node>> graph;

    protected Graph() {
        this.graph = new HashMap<>();
        this.nodes = new HashMap<>();
    }

    public Graph(HashSet<HashSet<Integer>> nonKeysInt, List<Integer> propertiesList, int instatiatedProperty, Map<Integer, String> id2Property, KB kb, int support) {
        this.nodes = new HashMap<>();
        this.graph = new HashMap<>();
        buildParents(nonKeysInt, instatiatedProperty, propertiesList, id2Property, kb, support);
    }

    private HashSet<Node> buildParents(HashSet<HashSet<Integer>> nonKeysInt, int instantiatedProperty, List<Integer> propertiesList,
    		Map<Integer, String> id2Property, KB kb, int support) {
        HashSet<Node> parents = new HashSet<>();
        for (int currentProperty : propertiesList) {
            if (instantiatedProperty != currentProperty) {
                HashSet<Integer> candidateParent = new HashSet<>();
                candidateParent.add(currentProperty);
                candidateParent.add(instantiatedProperty);
                if (containsASuperSetOf(nonKeysInt, candidateParent) == 1/*exact equal*/
                        || containsASuperSetOf(nonKeysInt, candidateParent) == 0) /*included*/ {
                    if (CSAKey.support(candidateParent, id2Property, kb) > support) {
                        HashSet<Integer> parentProperty = new HashSet<>();
                        parentProperty.add(currentProperty);
                        Node parent = createOrGetNode(parentProperty);
                        parents.add(parent);
                        HashSet<Node> children =  new HashSet<>();
                        graph.put(parent, children);
                     //   System.out.println("graph:"+graph);
                       // System.out.println("nodes:"+nodes);
                    }
                }
            }
        }
      //  System.out.println("parents:" + parents.size());
        return parents;
    }

    public static int containsASuperSetOf(HashSet<HashSet<Integer>> hh, HashSet<Integer> h) {
        if (hh.contains(h)) {
            //1 => exactly equal
            return 1;
        }

        for (HashSet<Integer> s : hh) {
            if (s.containsAll(h)) {
                //0 => contained
                return 0;
            }
        }
        //-1 => not contained
        return -1;
    }
    
    public Node getNode(HashSet<Integer> properties) {
    	for (Node n : nodes.keySet()) {
    		if (n.set.equals(properties))
    			return n;
    	}
    	return null;
    }

    public Node getNode(Node nd) {
        return nodes.get(nd);
    }

    public Node createOrGetNode(HashSet<Integer> properties) {
        Node newNode = new Node(properties);
        if (!nodes.containsKey(newNode)) {
            nodes.put(newNode, newNode);
        }
        return nodes.get(newNode);
    }

    public Node createOrGetNode(Node aNode) {
        Node nwNode = createOrGetNode(aNode.set);
        nwNode.toExplore = aNode.toExplore;
        return nwNode;
    }
    
    public Node duplicateOrGetNode(Node aNode) {
    	Node nd = nodes.get(aNode);
    	Node newNd = null;
    	if (nd == null) {
	    	newNd = new Node(aNode.set);
	    	newNd.toExplore = aNode.toExplore;
	    	nodes.put(newNd, newNd);	    	
    	} else {
    		newNd = nodes.get(aNode);
    	}
    	
    	return newNd;
    }


    public HashSet<Node> topGraphNodes() {
        HashSet<Node> topNodes = new HashSet<>();
        for (Node node : graph.keySet()) {
            if (node.set.size() == 1) {
                topNodes.add(node);
            }
        }
        return topNodes;
    }

    @Override
    public Object clone() {
        Graph clonedGraph = new Graph();        
        for (Node nd : nodes.keySet()) {
            Node newNd = clonedGraph.duplicateOrGetNode(nd);
            if (graph.containsKey(nd)) {
                HashSet<Node> children = graph.get(nd);
                HashSet<Node> clonedChildren = new HashSet<>();
                for (Node c : children) {
                    Node clonedC = clonedGraph.duplicateOrGetNode(c);
                    clonedChildren.add(clonedC);
                }
                clonedGraph.graph.put(newNd, clonedChildren);
            }
        }
        return clonedGraph;
    }

    public String toString() {
        return graph.toString();
    }

}