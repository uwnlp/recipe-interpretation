package model;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import utils.Dot;
import utils.Pair;
import utils.Triple;
import utils.Utils;
import data.ActionDiagram;
import data.ProjectParameters;
import data.RecipeArgParser;
import data.RecipeEvent;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;

/**
 * This class parses an recipe using a naive heuristic baseline.
 * 
 * First pass: if a verb is missing a direct object, connect to previous sentence's output.
 * Second pass: If a verb has an argument's output isn't
 * 				connected to any other verb, search following verbs for overlap arg matches.
 * 				If one exists, connect to the argument. 
 * Third pass: If a verb has an argument's output still isn't
 * 				connected, connect to next sentence that has a direct object with no link. 
 * 				If all direct objects have links, try the closest prepositional argument 
 * 				with no links, followed by other arguments with no links.
 * Fourth pass: (clean up) Connect to an empty argument in the next verb.
 * 
 * Usage:
 * 
 * To create .gv files using this baseline for BananaMuffins assuming the default data
 * directory given by ProjectParameters.DEFAULT_DATA_DIRECTORY:
 * 
 * java NaiveTextOrderConnector BananaMuffins
 * 
 * To create .gv files using this baseline for BananaMuffins within a specified data
 * directory:
 * 
 * java NaiveTextOrderConnector BananaMuffins /path/to/data/directory
 * 
 * In both cases, .gv files are written out to BananaMuffins-graph/ in the data directory.
 * 
 * @author chloe
 *
 */
public class NaiveTextOrderConnector {

	private String data_directory;
	private String category;

	public NaiveTextOrderConnector(String category) {
		this(category, ProjectParameters.DEFAULT_DATA_DIRECTORY);
	}

	public NaiveTextOrderConnector(String category, String data_directory) {
		this.category = category;
		this.data_directory = data_directory;
	}

	public static int impl_obj = 0;
	
	/**
	 * Connects the nodes in an ActionDiagram based on the baseline heuristics.
	 * 
	 * This method changes the input ActionDiagram instead of creating an entirely
	 * new ActionDiagram.
	 * 
	 * @param ad the input ActionDiagram to be connected
	 */
	public static void connectDiagram(ActionDiagram ad) {

		// First pass: If a predicate is missing a direct object, connect 
		// to previous sentence.
		Iterator<ActionNode> node_iterator = ad.node_iterator();
		// Skip first predicate node since it cannot be linked back to a previous node. 
		node_iterator.next();
		while (node_iterator.hasNext()) {
			ActionNode node = node_iterator.next();
//			System.out.println(node);
			RecipeEvent event = node.event();
			int index = ad.getIndex(node);

			Argument dobj = event.dobj();
//			System.out.println(dobj);
			if (dobj == null) {
				// Create empty direct object.
				dobj = event.setDirectObject("");
				
				ActionNode prev_node = ad.getNodeAtIndex(index - 1);
				dobj.addNonIngredientSpan("");

				// Connect nodes through direct object
				ad.connectNodes(prev_node, node, dobj, "", "");
				impl_obj++;
			}
		}
		
		//Second pass: If a verb has an argument that contains ingredients but its output isn't
		// 				connected to any other verb, search following verbs for overlap arg matches.
		// 				If one exists, connect to the argument.
		node_iterator = ad.node_iterator();
		while (node_iterator.hasNext()) {
			ActionNode node = node_iterator.next();
//			System.out.println(node.event().predicate() + " " + node.event().dobj());
			if (!node.hasConnectedOutput()) {
				Integer node_idx = ad.getIndex(node);
				for (int i = node_idx + 1; i < ad.numNodes(); i++) {
					ActionNode other = ad.getNodeAtIndex(i);
//					System.out.println("  " + other.event().predicate());
					Triple<Argument, String, Integer> overlap = other.bestDobjOrPrepArgumentOverlap(node);
//					System.out.println("    " + overlap);
					if (overlap.getFirst() != null) {
						ad.connectNodes(node, other, overlap.getFirst(), overlap.getSecond(), "");
						break;
					}
				}
			}
		}
//
		//Third pass -- dobjs
		node_iterator = ad.node_iterator();
		while (node_iterator.hasNext()) {
			ActionNode node = node_iterator.next();
			if (!node.hasConnectedOutput()) {
				Integer node_idx = ad.getIndex(node);
				for (int i = node_idx + 1; i < ad.numNodes(); i++) {
					ActionNode other = ad.getNodeAtIndex(i);
					RecipeEvent event = other.event();
					Argument dobj = event.dobj();
					if (dobj == null || dobj.string().equals("")) {
						continue;
					}
					boolean found = false;
					List<String> non_ingredient_spans = dobj.nonIngredientSpans();
					for (String span : non_ingredient_spans) {
						Pair<ActionNode, String> orig = other.getOrigin(dobj, span);
						if (orig == null) {
							ad.connectNodes(node, other, dobj, span, "");
							found = true;
							break;
						}
					}
					if (found) {
						break;
					}
				}
			}
		}

//		//Third pass -- preps
//		node_iterator = ad.node_iterator();
//		while (node_iterator.hasNext()) {
//			ActionNode node = node_iterator.next();
//			if (!node.hasConnectedOutput()) {
//				Integer node_idx = ad.getIndex(node);
//				for (int i = node_idx + 1; i < ad.numNodes(); i++) {
//					ActionNode other = ad.getNodeAtIndex(i);
//					RecipeEvent event = other.event();
//					Iterator<Argument> prep_it = event.prepositionalArgIterator();
//					boolean found = false;
//					while (prep_it.hasNext()) {
//						Argument prep_arg = prep_it.next();
//						if (prep_arg.string().equals("")) {
//							continue;
//						}
//						List<String> non_ingredient_spans = prep_arg.nonIngredientSpans();
//						for (String span : non_ingredient_spans) {
//							Pair<ActionNode, String> orig = other.getOrigin(prep_arg, span);
//							if (orig == null) {
//								ad.connectNodes(node, other, prep_arg, span, "");
//								found = true;
//								break;
//							}
//						}
//						if (found) {
//							break;
//						}
//					}
//					if (found) {
//						break;
//					}
//				}
//			}
//		}

//		//Third pass -- other arguments
//		node_iterator = ad.node_iterator();
//		while (node_iterator.hasNext()) {
//			ActionNode node = node_iterator.next();
//			if (!node.hasConnectedOutput()) {
//				Integer node_idx = ad.getIndex(node);
//				for (int i = node_idx + 1; i < ad.numNodes(); i++) {
//					ActionNode other = ad.getNodeAtIndex(i);
//					RecipeEvent event = other.event();
//					Iterator<Argument> other_it = event.otherArgIterator();
//					boolean found = false;
//					while (other_it.hasNext()) {
//						Argument other_arg = other_it.next();
//						if (other_arg.string().equals("")) {
//							continue;
//						}
//						List<String> non_ingredient_spans = other_arg.nonIngredientSpans();
//						for (String span : non_ingredient_spans) {
//							Pair<ActionNode, String> orig = other.getOrigin(other_arg, span);
//							if (orig == null) {
//								ad.connectNodes(node, other, other_arg, span, "");
//								found = true;
//								break;
//							}
//						}
//						if (found) {
//							break;
//						}
//					}
//					if (found) {
//						break;
//					}
//				}
//			}
//		}
//
//		// Final pass: connect to new empty argument in the textually subsequent predicate
//		node_iterator = ad.node_iterator();
//		while (node_iterator.hasNext()) {
//			ActionNode node = node_iterator.next();
//			if (!node.hasConnectedOutput()) {
//				Integer node_idx = ad.getIndex(node);
//				if (node_idx == ad.numNodes() - 1) {
//					// TODO(chloe): figure this out. Weird situation. Until this is resolved,
//					// in this case there will be a forest.
//				} else {
//					for (int i = node_idx + 1; i < ad.numNodes(); i++) {
//						ActionNode other = ad.getNodeAtIndex(i);
//						RecipeEvent event = other.event();
//						Argument ghost_arg = event.addPrepositionalArgument("", "");
//						if (ghost_arg != null) {
//							ghost_arg.addNonIngredientSpan("");
//							ad.connectNodes(node, other, ghost_arg, "", "");
//							break;
//						}
//					}
//				}
//			}
//		}
	}

	/**
	 * Parse all the files based on the specified category.
	 */
	public void parseFiles() {
		// create category directory strings
		String input_directory_name = data_directory + category + "/" + category + ProjectParameters.ARG_SUFFIX + "/";
		List<File> input_files = Utils.getInputFiles(input_directory_name, "txt");
		
		String fulltext_directory_name = data_directory + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
		List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

		String output_directory_name = data_directory + category + "/" + category + ProjectParameters.DOT_SUFFIX + "/";
		Utils.createOutputDirectoryIfNotExist(output_directory_name);

		for (int i = 0; i < input_files.size(); i++) {
			File input_file = input_files.get(i);
			File fulltext_file = fulltext_files.get(i);
			System.out.println(input_file);
			ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromFile(input_file, fulltext_file, true, true);
			connectDiagram(ad);

			int suffix_idx = input_file.getName().lastIndexOf('.');
			String dotFile = output_directory_name + input_file.getName().substring(0, suffix_idx) + ".gv";
			String svgFile = output_directory_name + input_file.getName().substring(0, suffix_idx) + ".svg";
			ad.printDotFile(dotFile);
			
			try {
			  Dot.getSVG(dotFile, svgFile);
			} catch(Exception e) {
			  System.out.println("Exception while creating svg file: " + e);
			}
		}
	}

	public static void main(String[] args) {
		if (args.length == 0 || args.length > 2) {
			System.out.println("Usage: java NaiveTextOrderConnector category [data_directory]");
			return;
		}
		String category = args[0];
		NaiveTextOrderConnector parser = null;
		if (args.length == 1) {
			parser = new NaiveTextOrderConnector(category);
		} else {
			parser = new NaiveTextOrderConnector(category, args[1]);
		}
		parser.parseFiles();
	}
}
