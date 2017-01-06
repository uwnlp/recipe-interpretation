package graphics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import utils.Dot;
import utils.IntPair;
import utils.Pair;
import utils.TokenCounter;
import utils.Utils;
import data.ActionDiagram;
import data.IngredientParser;
import data.ProjectParameters;
import data.RecipeArgParser;
import data.RecipeEvent;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;

/**
 * Class for reading in BRAT annotation files and outputting ActionDiagrams.
 * 
 * Usage:
 * 
 * To create and store .gv files based on the BRAT gold-standard annotations in the BananaMuffins directory
 * using the default data directory in ProjectParameters.DEFAULT_DATA_DIRECTORY:
 * 
 * java BratAnnotationReader BananaMuffins
 * 
 * To create and store .gv files based on the BRAT gold-standard annotations in the BananaMuffins directory
 * using a specified data directory:
 * 
 * java BratAnnotationReader BananaMuffins /path/to/data/directory
 * 
 * The output directory will be /path/to/data/directory/BananaMuffins/BananaMuffins-gold/
 * 
 * TODO(chloe): allow the user to specify a different output directory -- low priority
 * 
 * @author chloe
 *
 */
public class BratAnnotationReader {

	// intermediate data structures for storing information from the BRAT file
	///////////////////
	private static TreeMap<Integer, String> start_index_to_predicate_id = new TreeMap<Integer, String>();
	private static Map<String, String> id_to_string = new HashMap<String, String>();
	private static Map<String, String> id_to_type = new HashMap<String, String>();
	private static Map<String, IntPair> id_to_span_indices = new HashMap<String, IntPair>();
	private static Map<String, Map<String, String>> gov_to_dep_and_rel = new HashMap<String, Map<String, String>>();
	private static Map<String, IntPair> evolution_id_to_span_indices = new HashMap<String, IntPair>();
	private static Map<String, String> evolution_link = new HashMap<String, String>();
	private static List<Integer> sentence_character_end = new ArrayList<Integer>();
	private static List<Integer> true_sentence_character_end = new ArrayList<Integer>();
	private static List<String> sentence_strings = new ArrayList<String>();
	private static Set<String> predicate_ids = new HashSet<String>();
	private static TreeMap<Integer, String> ingredient_span_start_to_id = new TreeMap<Integer, String>();

	private static boolean connect_ = true;
	///////////////////


	private String data_directory_;
	private String category_;

	public BratAnnotationReader(String category) {
		category_ = category;
		data_directory_ = ProjectParameters.DEFAULT_DATA_DIRECTORY;
	}

	public BratAnnotationReader(String category, String data_directory) {
		category_ = category;
		data_directory_ = data_directory;
	}

	private static void readInBratAnnotations(File arg_file) throws IOException {

		String line;

		// Read .ann file and extract necessary information
		BufferedReader br = new BufferedReader(new FileReader(arg_file));
		System.out.println(arg_file);

		while ((line = br.readLine()) != null) {
			line = line.replaceAll("\\s+", " ").toLowerCase();
			if (line.contains("arg1:")) { // is an argument edge
				int rel_begin = line.indexOf(' ') + 1;
				int rel_end = line.indexOf(' ', rel_begin);
				String rel_name = line.substring(rel_begin, rel_end);
				int arg1_colon = line.indexOf(':');
				int arg1_end = line.indexOf(' ', arg1_colon);
				String arg1 = line.substring(arg1_colon + 1, arg1_end);
				int arg2_colon = line.indexOf(':', arg1_colon + 1);
				String arg2 = line.substring(arg2_colon + 1).trim();
				Map<String, String> dep_and_rels = gov_to_dep_and_rel.get(arg1);
				if (dep_and_rels == null) {
					dep_and_rels = new HashMap<String, String>();
					gov_to_dep_and_rel.put(arg1, dep_and_rels);
				}

				// If there is already a relation between the two args, pick 
				// in order object, co-object, location, other to keep
				if (dep_and_rels.containsKey(arg2)) {
					String other_rel = dep_and_rels.get(arg2);
					int rel_index = ProjectParameters.rel_order.indexOf(rel_name);
					int other_rel_index = ProjectParameters.rel_order.indexOf(other_rel);
					if (rel_index < other_rel_index) {
						dep_and_rels.put(arg2, rel_name);		
					}
				} else {
					dep_and_rels.put(arg2, rel_name);
				}
			} else if (line.contains("evolution:")) { // is an evolution edge
				int arg1_colon = line.indexOf(':');
				int arg1_end = line.indexOf(' ', arg1_colon);
				String arg1 = line.substring(arg1_colon + 1, arg1_end);
				int arg2_colon = line.indexOf(':', arg1_end);
				String arg2 = line.substring(arg2_colon + 1).trim();

				evolution_link.put(arg1, arg2);
			} else {
				int end_id = line.indexOf(' ');
				String id = line.substring(0, end_id);
				int end_type = line.indexOf(' ', (end_id+1));
				String type = line.substring(end_id + 1, end_type);
				int end_start_index = line.indexOf(' ', end_type + 1);
				int start_index = Integer.parseInt(line.substring(end_type + 1, end_start_index));
				if (type.equals("predicate")) {
					start_index_to_predicate_id.put(start_index, id);
					predicate_ids.add(id);
				} else if (type.equals("ingredient")) {
					ingredient_span_start_to_id.put(start_index, id);
				}
				int end_end_index = line.indexOf(' ', end_start_index + 1);
				int semicolon = line.indexOf(';', end_start_index + 1);
				id_to_type.put(id, type);

				if (semicolon != -1 && semicolon < end_end_index) { // dealing with non-contiguous text
					// lines look like: T10 predicate 465 471;481 485 Reduce heat
					//
					// NOTE(chloe): for now, just ignore the second span indices and pretend it is all
					//    in the first
					int end_index = Integer.parseInt(line.substring(end_start_index + 1, semicolon));
					int second_end_end_index = line.indexOf(' ', (end_end_index + 1));
					String span = line.substring(second_end_end_index + 1).trim();
					id_to_string.put(id, span);
					if (type.equals("evolution")) {
						evolution_id_to_span_indices.put(id, new IntPair(start_index, end_index));
					} else {
						id_to_span_indices.put(id, new IntPair(start_index, end_index));
					}
				} else {
					int end_index = Integer.parseInt(line.substring(end_start_index + 1, end_end_index));
					String span = line.substring(end_end_index + 1).trim();
					id_to_string.put(id, span);
					if (type.equals("evolution")) {
						evolution_id_to_span_indices.put(id, new IntPair(start_index, end_index));
					} else {
						id_to_span_indices.put(id, new IntPair(start_index, end_index));
					}
				}
			}
		}
		br.close();
	}

	private static void readSentenceEndIndices(File step_file) throws IOException {
		String line;

		// Read in character line ends from -step file
		BufferedReader step_br = new BufferedReader(new FileReader(step_file));
		int num_characters = 0;
		int no_space_num_characters = 0;
		while ((line = step_br.readLine()) != null) {
			num_characters +=  line.length() + 1;
			sentence_character_end.add(num_characters);
			sentence_strings.add(line);
			no_space_num_characters += line.replaceAll(" ", "").length();
			true_sentence_character_end.add(no_space_num_characters);
		}
		step_br.close();
	}

	public static int getSentenceFromCharacterOffset(int start_index) {
		int sentence = 0;

		for (int i = 0; i < sentence_character_end.size(); i++) {
			Integer sentence_end = sentence_character_end.get(i);
			if (start_index >= sentence_end) {
				sentence++;
				continue;
			} else {
				return sentence;
			}
		}
		return sentence_character_end.size() - 1;
	}

	public static int getSentenceCharacterOffset(int start_index) {
		int sentence = getSentenceFromCharacterOffset(start_index);
		if (sentence == 0) {
			return start_index;
		}
		int previous_no_space_offset = true_sentence_character_end.get(sentence - 1);
		int in_sentence_offset = start_index - sentence_character_end.get(sentence - 1);

		if (in_sentence_offset == 0) {
			return previous_no_space_offset;
		}
		String sentence_prefix = sentence_strings.get(sentence).substring(0, in_sentence_offset);
		sentence_prefix = sentence_prefix.replaceAll(" ", ""); 
		return previous_no_space_offset + sentence_prefix.length();
	}

	public static int impl_obj = 0;
	

	private static ActionDiagram createActionDiagram(String recipe_name, Set<String> ingredients, boolean use_ingredients) {
		ActionDiagram ad = new ActionDiagram(recipe_name);
		ad.addIngredients(ingredients);

		int curr_sentence = 0;
		Map<String, ActionNode> predicate_id_to_node = new HashMap<String, ActionNode>();
		TreeMap<Integer, Pair<Argument, ActionNode>> start_index_to_argument = new TreeMap<Integer, Pair<Argument, ActionNode>>();
		Map<Argument, String> argument_to_id = new HashMap<Argument, String>();

		Iterator<Integer> ingredient_start_it = ingredient_span_start_to_id.keySet().iterator();
		Integer ingredient_start = Integer.MAX_VALUE;
		if (ingredient_start_it.hasNext() && use_ingredients) {
			ingredient_start = ingredient_start_it.next();
		}

		List<RecipeEvent> events = new ArrayList<RecipeEvent>();
		for (Integer start_index : start_index_to_predicate_id.keySet()) {

			String predicate_id = start_index_to_predicate_id.get(start_index);
			String string = id_to_string.get(predicate_id);
			int sentence_idx = getSentenceFromCharacterOffset(start_index);
			if (sentence_idx != curr_sentence) {
				curr_sentence = sentence_idx;
			}

			//			RecipeEvent event = new RecipeEvent(string, recipe_name, sentence_idx, getSentenceCharacterOffset(start_index));
			RecipeEvent event = new RecipeEvent(string, recipe_name, sentence_idx, 0);
			ActionNode node = ad.addEvent(event);
			predicate_id_to_node.put(predicate_id, node);
			events.add(event);
			
			boolean swap = false;
			int first = -1;
			String obj_swap_str = "";
			Argument swap_arg = null;
			if (string.contains(" in")) {
				swap = true;
			}

			Map<String, String> dep_to_rel = gov_to_dep_and_rel.get(predicate_id);
			if (dep_to_rel != null) {
				TreeMap<Integer, String> char_offset_to_dep = new TreeMap<Integer, String>();
				for (String dep : dep_to_rel.keySet()) {
					char_offset_to_dep.put(id_to_span_indices.get(dep).getFirst(), dep);
				}
				for (String dep : char_offset_to_dep.values()) {
					String rel = dep_to_rel.get(dep);
					
					if (swap) {
						if (rel.equalsIgnoreCase("object")) {
							rel = "co-object";
						} else if (rel.equalsIgnoreCase("co-object")) {
							rel = "object";
						}
					}
					if (predicate_ids.contains(dep)) { // backwards link to another predicate
						if (connect_) {
							Argument arg = null;
							//							System.out.println(event);
							//							System.out.println(rel);
							if (rel.equals("object")) {  // direct object
								arg = event.setDirectObject("");
								arg.addNonIngredientSpan("");
								impl_obj++;
							} else if (rel.equals("co-object") || rel.equals("location")) {  // prep argument
								arg = event.addPrepositionalArgument("", "in");
								if (arg != null) {
									arg.addNonIngredientSpan("");
									if (rel.equals("co-object")) {
										arg.setType(Argument.Type.COOBJECT);
									} else {
										arg.setType(Argument.Type.LOCATION);
									}
								}
							} else { // other argument
								arg = event.addOtherArgument("");
								arg.addNonIngredientSpan("");
							}
							if (arg != null) {
								ad.connectNodes(predicate_id_to_node.get(dep), node, arg, "", "");
							}
						}
					} else {
						IntPair span_indices = id_to_span_indices.get(dep);
						int dep_hw_offset = span_indices.getFirst();
						Argument arg = null;
						if (rel.equals("object")) {  // direct object
							arg = event.setDirectObject(id_to_string.get(dep));
						} else if (rel.equals("co-object") || rel.equals("location")) {  // prep argument
							if (swap) {
								obj_swap_str += id_to_string.get(dep) + " ";
								if (first == -1) {
									first = dep_hw_offset;
								}
								if (swap_arg == null) {
									arg = event.addPrepositionalArgument(dep_hw_offset, id_to_string.get(dep), "in");
									swap_arg = arg;
								} else {
									arg = swap_arg;
									arg.addString(id_to_string.get(dep));
								}
							} else {
								arg = event.addPrepositionalArgument(dep_hw_offset, id_to_string.get(dep));
							}
							if (rel.equals("co-object")) {
								arg.setType(Argument.Type.COOBJECT);
							} else {
								arg.setType(Argument.Type.LOCATION);
							}
						} else { // other argument
							arg = event.addOtherArgument(dep_hw_offset, id_to_string.get(dep));
						}
						start_index_to_argument.put(id_to_span_indices.get(dep).getFirst(), new Pair<Argument, ActionNode>(arg, node));
						argument_to_id.put(arg, dep);
						while (ingredient_start >= dep_hw_offset && ingredient_start < span_indices.getSecond()) {
							String ingredient_id = ingredient_span_start_to_id.get(ingredient_start);
							String ingredient_span = id_to_string.get(ingredient_id);
							if (use_ingredients) {
								arg.addIngredient(ingredient_span, ingredient_span);
							} else {
								arg.addNonIngredientSpan(ingredient_span);
							}

							if (ingredient_start_it.hasNext()) {
								ingredient_start = ingredient_start_it.next();
							} else {
								ingredient_start = Integer.MAX_VALUE;
								break;
							}
						}
						if (!arg.hasIngredients()) {
							if (arg.preposition() == null || (string.contains(" in") && rel.equals("co-object"))) {
								arg.addNonIngredientSpan(id_to_string.get(dep));
							} else {
								String span = id_to_string.get(dep);
								int first_space = span.indexOf(" ");
								if (first_space == -1) {
									arg.addNonIngredientSpan(id_to_string.get(dep));
								} else {
									arg.addNonIngredientSpan(span.substring(first_space + 1));
								}
							}
						}
					}
				}
			}

		}

		for (String gov : evolution_link.keySet()) {
			String dep = evolution_link.get(gov);
			String dep_type = id_to_type.get(dep);
			IntPair span = evolution_id_to_span_indices.get(gov);
			Pair<Argument, ActionNode> possible_gov_arg = start_index_to_argument.floorEntry(span.getFirst()).getValue();
			IntPair arg_span = id_to_span_indices.get(argument_to_id.get(possible_gov_arg.getFirst()));
			if (span.getFirst() > arg_span.getSecond()) {
				continue; // not in a argument, ignore
			}
			if (dep_type.equals("predicate") && connect_) {
				String span_str = id_to_string.get(gov);
				Argument arg = possible_gov_arg.getFirst();
				if (!arg.nonIngredientSpans().contains(span_str)) {
					List<String> new_noningredient_spans = new ArrayList<String>();
					for (String noningredient_span : arg.nonIngredientSpans()) {
						if (noningredient_span.contains(span_str)) {
							int index = noningredient_span.indexOf(span_str);
							if (index == 0) {
								String new_span = noningredient_span.substring(0, span_str.length());
								String new_span2 = noningredient_span.substring(span_str.length());
								new_noningredient_spans.add(new_span);
								if (!new_span2.trim().matches("|and|or|the|a|an")) {
									new_noningredient_spans.add(new_span2);
								}
							} else {
								String new_span = noningredient_span.substring(0, index);
								String new_span2 = noningredient_span.substring(index, index + span_str.length());
								String new_span3 = noningredient_span.substring(index + span_str.length());
								if (!new_span.trim().matches("|and|or|the|a|an")) {
									new_noningredient_spans.add(new_span);
								}
								new_noningredient_spans.add(new_span2);
								if (!new_span3.trim().matches("|and|or|the|a|an")) {
									new_noningredient_spans.add(new_span3);
								}
							}
						} else {
							new_noningredient_spans.add(noningredient_span);
						}
					}
					arg.setNonIngredientSpans(new_noningredient_spans);
				}
				ad.connectNodes(predicate_id_to_node.get(dep), possible_gov_arg.getSecond(), possible_gov_arg.getFirst(), id_to_string.get(gov), "");
			} else {
				// ignore for now, incorrect annotation
				// TODO(chloe): find the event the dep is an arg of, and connect to that
				continue;
			}
		}
		Iterator<ActionNode> node_it = ad.node_iterator();
		while (node_it.hasNext()) {
			ActionNode node = node_it.next();
			node.updateEventEntities();
		}

		return ad;
	}
	
	private static List<Pair<String, String>> createTextAlignment(String recipe_name, Set<String> ingredients, 
			boolean use_ingredients) {
		List<Pair<String, String>> alignments = new ArrayList<Pair<String, String>>();

		int curr_sentence = 0;
		Map<String, ActionNode> predicate_id_to_node = new HashMap<String, ActionNode>();
		TreeMap<Integer, Pair<Argument, ActionNode>> start_index_to_argument = new TreeMap<Integer, Pair<Argument, ActionNode>>();
		Map<Argument, String> argument_to_id = new HashMap<Argument, String>();
		Map<Integer, TreeMap<Integer, Pair<String, String>>> sent_to_parts = 
				new HashMap<Integer, TreeMap<Integer, Pair<String, String>>>();

		Iterator<Integer> ingredient_start_it = ingredient_span_start_to_id.keySet().iterator();
		Integer ingredient_start = Integer.MAX_VALUE;
		if (ingredient_start_it.hasNext() && use_ingredients) {
			ingredient_start = ingredient_start_it.next();
		}

		List<RecipeEvent> events = new ArrayList<RecipeEvent>();
		for (Integer start_index : start_index_to_predicate_id.keySet()) {

			String predicate_id = start_index_to_predicate_id.get(start_index);
			String string = id_to_string.get(predicate_id);
			int sentence_idx = getSentenceFromCharacterOffset(start_index);
			if (sentence_idx != curr_sentence) {
				curr_sentence = sentence_idx;
			}
			
			TreeMap<Integer, Pair<String, String>> part_map = sent_to_parts.get(sentence_idx);
			if (part_map == null) {
				part_map = new TreeMap<Integer, Pair<String, String>>();
				sent_to_parts.put(sentence_idx, part_map);
			}
			part_map.put(start_index, new Pair<String, String>(string, "PRED"));

			Map<String, String> dep_to_rel = gov_to_dep_and_rel.get(predicate_id);
			if (dep_to_rel != null) {
				TreeMap<Integer, String> char_offset_to_dep = new TreeMap<Integer, String>();
				for (String dep : dep_to_rel.keySet()) {
					char_offset_to_dep.put(id_to_span_indices.get(dep).getFirst(), dep);
				}
				for (Integer char_offset : char_offset_to_dep.keySet()) {
					String dep = char_offset_to_dep.get(char_offset);
					String rel = dep_to_rel.get(dep);
					if (string.contains(" in")) {
						if (rel.equalsIgnoreCase("object")) {
							rel = "co-object";
						} else if (rel.equalsIgnoreCase("co-object")) {
							rel = "object";
						}
					}
					IntPair span_indices = id_to_span_indices.get(dep);
					int dep_hw_offset = span_indices.getFirst();
					String argument_string = id_to_string.get(dep);
					if (rel.equals("object")) {
						part_map.put(dep_hw_offset, new Pair<String, String>(argument_string, "OBJ"));
					} else if (rel.equals("co-object")) {
						part_map.put(dep_hw_offset, new Pair<String, String>(argument_string, "PREP"));
					} else if (rel.equals("location")) {
						part_map.put(dep_hw_offset, new Pair<String, String>(argument_string, "PREP"));
					} else if (rel.equals("duration")) {
						part_map.put(dep_hw_offset, new Pair<String, String>(argument_string, "DUR"));
					}
					
				}
			}
		}
		for (int i = 0; i < sentence_strings.size(); i++) {
			String sentence_string = sentence_strings.get(i);
			
		}
		return alignments;
	}

	public static List<Pair<String, String>> generativeSequencesFromBratAnnotations(File arg_file, File step_file, 
			File fulltext_file, 
			boolean connect, boolean use_ingredients) {
		String recipe_name = arg_file.getName();

		try {
			start_index_to_predicate_id.clear();
			id_to_string.clear();
			id_to_type.clear();
			id_to_span_indices.clear();
			gov_to_dep_and_rel.clear();
			evolution_id_to_span_indices.clear();
			evolution_link.clear();
			predicate_ids.clear();
			sentence_character_end.clear();
			sentence_strings.clear();
			true_sentence_character_end.clear();
			connect_ = connect;
			ingredient_span_start_to_id.clear();


			readSentenceEndIndices(step_file);
			readInBratAnnotations(arg_file);

			Set<String> ingredients = IngredientParser.parseIngredientsFromFullTextFile(fulltext_file);
			List<Pair<String, String>> sequences = createTextAlignment(recipe_name, ingredients, use_ingredients);

			return null;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	public static ActionDiagram generativeActionDiagramFromBratAnnotations(File arg_file, File step_file, File fulltext_file, 
			boolean connect, boolean use_ingredients) {
		String recipe_name = arg_file.getName();

		try {
			start_index_to_predicate_id.clear();
			id_to_string.clear();
			id_to_type.clear();
			id_to_span_indices.clear();
			gov_to_dep_and_rel.clear();
			evolution_id_to_span_indices.clear();
			evolution_link.clear();
			predicate_ids.clear();
			sentence_character_end.clear();
			sentence_strings.clear();
			true_sentence_character_end.clear();
			connect_ = connect;
			ingredient_span_start_to_id.clear();


			readSentenceEndIndices(step_file);
			readInBratAnnotations(arg_file);

			Set<String> ingredients = IngredientParser.parseIngredientsFromFullTextFile(fulltext_file);
			ActionDiagram ad = createActionDiagram(recipe_name, ingredients, use_ingredients);

			return ad;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}


	public void parseFilesAndPrintToGvFiles() {
		// create category directory strings
		String input_directory_name = data_directory_ + category_ + "/" + category_ + ProjectParameters.ANNOTATION_SUFFIX + "/";
		List<File> input_annotation_files = Utils.getInputFiles(input_directory_name, "ann");

		String steps_directory_name = data_directory_ + category_ + "/" + category_ + ProjectParameters.STEP_SUFFIX + "/";
		List<File> input_step_files = Utils.getInputFiles(steps_directory_name, "txt");

		String fulltext_directory_name = data_directory_ + category_ + "/" + category_ + ProjectParameters.FULLTEXT_SUFFIX + "/";
		List<File> input_fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

		String output_directory_name = data_directory_ + category_ + "/" + category_ + ProjectParameters.GOLDDOT_SUFFIX + "/";
		Utils.createOutputDirectoryIfNotExist(output_directory_name);

		try {
			for (int i = 0; i < input_annotation_files.size(); i++) {
				File ann_file = input_annotation_files.get(i);
				File step_file = input_step_files.get(i);
				File fulltext_file = input_fulltext_files.get(i);
				int suffix = ann_file.getName().lastIndexOf('.');
				String recipe_name = ann_file.getName().substring(0, suffix);
				suffix = step_file.getName().lastIndexOf('.');
				if (!step_file.getName().substring(0, suffix).equals(recipe_name)) {
					throw new IOException("Expecting parallel directories of step and fulltext files.");
				}
				suffix = fulltext_file.getName().lastIndexOf('.');
				if (!fulltext_file.getName().substring(0, suffix).equals(recipe_name)) {
					throw new IOException("Expecting parallel directories of step and fulltext files.");
				}

				ActionDiagram ad = generativeActionDiagramFromBratAnnotations(ann_file, step_file, fulltext_file, true, true);
				ad.printDotFile(output_directory_name + recipe_name + ".gv");

				try {
					//          Dot.getSVG(output_directory_name + recipe_name + ".gv", output_directory_name + recipe_name + ".svg");
				} catch(Exception e) {
					System.out.println("Exception while creating svg file: " + e);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String[] args) {
		BratAnnotationReader bar = null;
		if (args.length > 1) {
			bar = new BratAnnotationReader(args[0], args[1]);
		} else {
			bar = new BratAnnotationReader(args[0]);
		}
		bar.parseFilesAndPrintToGvFiles();
	}
}
