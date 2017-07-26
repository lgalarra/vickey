/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amie.keys;

import static amie.keys.CombinationsExplorationNew.propertiesList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author Danai
 */
public class GraphNew implements Cloneable {

    public HashMap<Node, Node> nodes;

    public HashMap<Node, HashSet<Node>> graph;

    protected GraphNew() {
        this.graph = new HashMap<>();
        this.nodes = new HashMap<>();
    }

    public GraphNew(HashSet<HashSet<Integer>> nonKeysInt, ArrayList<Integer> propertiesList, int instatiatedProperty) {
        this.nodes = new HashMap<>();
        this.graph = new HashMap<>();
        HashSet<Node> parents = buildParents(instatiatedProperty, propertiesList);
        //this.graph = new HashMap<>();
        //this.graph = buildGraphOnTheFly(nonKeysInt, parents, instatiatedProperty);
    }

    private HashSet<Node> buildParents(int instantiatedProperty, ArrayList<Integer> propertiesList) {
        HashSet<Node> parents = new HashSet<>();
        for (int currentProperty : propertiesList) {
            if (instantiatedProperty != currentProperty) {
                HashSet<Integer> candidateParent = new HashSet<>();
                candidateParent.add(currentProperty);
                candidateParent.add(instantiatedProperty);
                if (containsASuperSetOf(CombinationsExplorationNew.nonKeysInt, candidateParent) == 1/*exact equal*/
                        || containsASuperSetOf(CombinationsExplorationNew.nonKeysInt, candidateParent) == 0) /*included*/ {
                    if (CombinationsExplorationNew.support(candidateParent, CombinationsExplorationNew.id2Property, CombinationsExplorationNew.kb) > CombinationsExplorationNew.support) {
                        HashSet<Integer> parentProperty = new HashSet<>();
                        parentProperty.add(currentProperty);
                        Node parent = createNode(parentProperty);
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

    public Node getNode(Node nd) {
        return nodes.get(nd);
    }

    public Node createNode(HashSet<Integer> properties) {
        Node newNode = new Node(properties);
        if (!nodes.containsKey(newNode)) {
            nodes.put(newNode, newNode);
        }
        return nodes.get(newNode);
    }

    public Node getAndCreateNode(Node aNode) {
        Node nwNode = createNode(aNode.set);
        nwNode.toExplore = aNode.toExplore;
        return nwNode;
    }

    private HashMap<Node, HashSet<Node>> buildGraphOnTheFly(
            HashSet<HashSet<Integer>> nonKeysInt,
            HashSet<Node> parents, int instatiatedProperty) {
        boolean stop = false;
        HashSet<Node> allChildren = new HashSet<>();
        //   System.out.println("graphhh:"+graph);
        for (Node parent : parents) {
            //  System.out.println("parent:"+parent);
            HashSet<Node> children = new HashSet<>();
            int counter = 0;
            for (int property : propertiesList) {
                if (property != instatiatedProperty) {
                    if (!parent.set.contains(property)) {
                        counter++;
                        HashSet<Integer> c = new HashSet<>();
                        c.add(property);
                        c.addAll(parent.set);
                        Node child = createNode(c);
                        c = new HashSet<>();
                        c.addAll(child.set);
                        c.add(instatiatedProperty);
                        Node set = createNode(c);
                        if (containsASuperSetOf(nonKeysInt, set.set) == 1/*exact equal*/
                                || containsASuperSetOf(nonKeysInt, set.set) == 0) /*included*/ {
                            //        System.out.println("prop:"+property);

                            int contains = containsASuperSetOf(nonKeysInt, child.set);
                            if (contains == 1) {
                                children.add(child);
                            } else if (contains == 0) {
                                children.add(child);
                                allChildren.add(child);

                            }
                        }
                        if (child.set.size() == propertiesList.size()) {
                            stop = true;
                        }
                    }
                }
            }
            // if (!children.isEmpty()) {
            graph.put(parent, children);
            // System.out.println("children:"+children);
            //  System.out.println("graph:"+graph);
            // }
            /**
             * This if loop allows us to construct graphs containing only one
             * level. Without this loop given for example a non key [0,1] we
             * wouldn't be able to construct any graph.
             */
            if (counter == 0) {
                //   System.out.println("test");
                //	graph.put(parent, children);
                //            System.out.println("graph:"+graph);
                return graph;
            }
            //    System.out.println("graph:"+graph);

        }
        if (stop == true || allChildren.isEmpty()) {
            return graph;
        }

        buildGraphOnTheFly(nonKeysInt, allChildren, instatiatedProperty);
        //  System.out.println("graph:"+graph);
        //   System.out.println("end");
        return graph;
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
        
       // System.out.println("graphhh"+graph.keySet());
        GraphNew clonedGraph = new GraphNew();
        for (Node nd : nodes.keySet()) {
            Node newNd = clonedGraph.getAndCreateNode(nd);
            if (graph.containsKey(nd)) {
                HashSet<Node> children = graph.get(nd);
                //System.out.println("children:"+children);
                if (children.isEmpty()) {
                    clonedGraph.graph.put(newNd, children);
                } else {
                    HashSet<Node> clonedChildren = new HashSet<>();
                    for (Node c : children) {
                        Node clonedC = clonedGraph.getAndCreateNode(c);
                        clonedChildren.add(clonedC);
                    }
                    clonedGraph.graph.put(newNd, clonedChildren);
                }
            }
        }
        return clonedGraph;
    }

    public String toString() {
        return graph.toString();
    }

}
