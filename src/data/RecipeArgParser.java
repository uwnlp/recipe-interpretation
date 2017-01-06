package data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import preprocessing.Lemmatizer;
import utils.Pair;
import utils.Triple;
import utils.Utils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.StringUtils;

/**
 * This class reads in plain text recipe steps and identifies the main
 * action verbs of the steps and their arguments.
 * 
 * Usage:
 * 
 * Create files of the predicate argument structure of the recipes in the BananaMuffins
 * directory of the default data directory given by ProjectParameters.DEFAULT_DATA_DIRECTORY:
 * 
 * java RecipeArgParser BananaMuffins
 * 
 * Create files of the predicate argument structure of the recipes in the BananaMuffins
 * directory of a specified data directory:
 * 
 * java RecipeArgParser BananaMuffins /path/to/data/directory/
 * 
 * In both cases, the outputted arg files are written to BananaMuffins-args/ in the data
 * directory used.
 * 
 * This code assumes the BananaMuffins directory has two parallel subdirectories of files:
 * BananaMuffins-steps/ that contains plaintext recipe instructions
 * BananaMuffins-fulltext/ that contains the full text of the recipes along with ingredients
 * 
 * Note(chloe): All the information could be gleaned from the fulltext folder so in the future,
 * we should just use that.
 * 
 * @author chloe
 *
 */
public class RecipeArgParser {
	// If true, compare and output lemmas instead of the original string tokens
	private static boolean OUTPUT_LEMMAS_ = false;

	// Objects required to parse using Stanford Parser
	private static LexicalizedParser lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
	private static TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	private static GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
	private static Lemmatizer lemmatizer = new Lemmatizer();

	// Punctuation that denotes the end of a sentence. Note that we add
	// a semicolon and colon and remove periods. We will handle periods on
	// our own. This array is used by the DocumentPreprocessor.
	private static String[] FINAL_SENTENCE_PUNC = new String[]{"!", "?", ";", ":", "*NL*", "\n"};

	private String data_directory;
	private String category;
	
	private static boolean nofuss = true;

	// Map from recipes to their events in the following map structure:
	// TreeMap<RecipeName, TreeMap<Sentence Index, TreeMap<Predicate Index, RecipeEvent>>>
	// Assumes each predicate has only one event attached to it. (TODO(chloe): I have no reason to suspect
	// this would ever not be the case)
	private TreeMap<String, TreeMap<Integer, TreeMap<Integer, RecipeEvent>>> recipes_to_events_by_sent_and_pred_idx_;
	private TreeMap<String, List<String>> recipe_to_sentences_ = new TreeMap<String, List<String>>();
	private TreeMap<String, List<List<Label>>> recipe_to_sentence_tags_ = new TreeMap<String, List<List<Label>>>();


	public RecipeArgParser(String category) {
		this(category, ProjectParameters.DEFAULT_DATA_DIRECTORY);
	}

	public RecipeArgParser(String category, String data_directory) {
		this.category = category;
		this.data_directory = data_directory;
		this.recipes_to_events_by_sent_and_pred_idx_ = new TreeMap<String, TreeMap<Integer, TreeMap<Integer, RecipeEvent>>>();
	}




	/**
	 * Recursive helper for GetTreeGraphStringRec.
	 * 
	 * This function follows dependency nodes to their dependents
	 * adding to a map of these nodes' indices to their strings.
	 * getTreeGraphString() will then use that sorted map to print
	 * out the full text span for a given head node.
	 * 
	 * NOTE: we used the typed dependencies that are guaranteed to
	 * give a tree structure.
	 * 
	 * If this function finds a dependent that is a particular
	 * type of verb (VB or VBP) and we are not in a prepositional
	 * phrases, the function does not recurse on the verb and
	 * instead adds it to a set as the @return value. This set
	 * will then be used to identify other action verbs in the
	 * sentence.
	 *
	 * @param tgn 	Current TreeGraphNode we are recursing on
	 * @param index_to_string	output TreeMap with node indicies to strings
	 * @param node_to_deps	Maps a node to its dependents
	 * @param edge_rels	Map that stores a pair of nodes (e.g., governor and
	 * 			dependent) to the edge relation that connects them
	 * @param lemmas	Ordered list of lemmas for the words in the sentence
	 * @param tags		Ordered list of tags of the words in the sentence
	 * @param find_new_actions	boolean that denotes when we should be looking for
	 * 						new action verbs
	 * @return	a set of TreeGraphNodes containing nodes of new action verbs
	 */
	public static Set<TreeGraphNode> getTreeGraphStringRec(TreeGraphNode tgn, 
			TreeMap<Integer, String> index_to_string, 
			Map<TreeGraphNode, Set<TreeGraphNode>> node_to_deps,
			Map<Pair<Integer, Integer>, String> edge_rels,
			List<String> lemmas, List<Label> tags,
			boolean find_new_actions) {
		Set<TreeGraphNode> found_verbs = new HashSet<TreeGraphNode>();
		String node_string = tgn.nodeString();
		int index = tgn.index();
		index_to_string.put(index, node_string);
		Set<TreeGraphNode> chds = node_to_deps.get(tgn);

		// if children, recurse
		if (chds != null) {
			for (TreeGraphNode chd : chds) {
				int child_index = chd.index();
				Label tag = tags.get(child_index - 1);
				if (!find_new_actions && (tag.value().equals("VB") || tag.value().equals("VBP"))) {
					found_verbs.add(chd);
					continue;
				}
				Set<TreeGraphNode> verbs = getTreeGraphStringRec(chd, index_to_string, node_to_deps, edge_rels, lemmas, tags, find_new_actions);
				found_verbs.addAll(verbs);
			}
		}
		return found_verbs;
	}

	/**
	 * This function finds the string corresponding to the span of tokens
	 * generated by a particular node (e.g., word) in the dependency graph.
	 * 
	 * NOTE: currently this function only spits out contiguous spans, or rather,
	 * if the dependents are not contiguous, it will add everything that is missing.
	 * This is useful for adding back in punctuation, like the commas in an 
	 * enumeration, for example. The only time we would want (TODO(chloe): check this)
	 * a non-contiguous span is for verbs (e.g., Set the chicken aside) and verbs
	 * are handled by the findActionVerbsAndArguments() function.
	 * 
	 * @param tgn	Head node (TreeGraphNode) of the phrase to find the span for 
	 *  @param node_to_deps	Maps a node to its dependents
	 * @param edge_rels	Map that stores a pair of nodes (e.g., governor and
	 * 			dependent) to the edge relation that connects them
	 * @param lemmas	Ordered list of lemmas for the words in the sentence
	 * @param tags		Ordered list of tags of the words in the sentence
	 * @param find_new_actions	boolean that denotes when we should be looking for
	 * 						new action verbs
	 * @param words		Ordered list of words in the sentence
	 * @return	a pair of the string corresponding to the span and a
	 * 			set of TreeGraphNodes containing nodes of new action verbs
	 */
	private static Pair<String, Set<TreeGraphNode>> getTreeGraphString(TreeGraphNode tgn, 
			Map<TreeGraphNode, Set<TreeGraphNode>> node_to_deps, 
			Map<Pair<Integer, Integer>, String> edge_rels,
			List<String> lemmas, List<Label> tags, List<HasWord> words,
			boolean find_new_actions) {
		Set<TreeGraphNode> new_verbs = new HashSet<TreeGraphNode>();
		TreeMap<Integer, String> index_to_string = new TreeMap<Integer, String>();
		String node_string = tgn.nodeString();
		int index = tgn.index();
		index_to_string.put(index, node_string);

		Set<TreeGraphNode> chds = node_to_deps.get(tgn);
		// if children, use recursive helper
		if (chds != null) {
			for (TreeGraphNode chd : chds) {
				int child_index = chd.index();
				Label tag = tags.get(child_index - 1);
				if (!find_new_actions && (tag.value().equals("VB") || tag.value().equals("VBP"))) {
					continue;
				}
				Set<TreeGraphNode> verbs = getTreeGraphStringRec(chd, index_to_string, node_to_deps, edge_rels, lemmas, tags, find_new_actions);
				new_verbs.addAll(verbs);
			}
		}

		// find the beginning and end index of the span.
		Iterator<Integer> iterator = index_to_string.keySet().iterator();
		int begin = iterator.next();
		int last = begin;

		while (iterator.hasNext()) {
			last = iterator.next();
		}

		// create the string from that span either with lemmas or words
		String output = "";
		for (int i = begin; i <= last; i++) {
			if (i != begin) {
				output += " ";
			}
			if (OUTPUT_LEMMAS_) {
				output += lemmas.get(i - 1);
			} else {
				output += words.get(i - 1).word();
			}
		}
		return new Pair<String, Set<TreeGraphNode>>(output, new_verbs);
	}

	private int charOffset(Tree tree) {
		return ((CoreLabel)(tree.label())).beginPosition();
	}

	/**
	 * Finds the main action predicates in the sentence and their respective arguments.
	 * For each, creates a RecipeEvent object to store the information. This information
	 * is stored in the predicate_idx_to_event map.
	 * 
	 * @param node verb node to be examining
	 * @param edge_rels map of governor/dependent pairs and the edge relation that connects
	 * 		them, if any.
	 * @param node_to_deps map that connects a governor node to its dependents.
	 * @param leaves list of leaf nodes of the constituent parse tree
	 * @param lemmas list of lemmas of tokens in the sentence
	 * @param tags list of POS tags of the tokens in the sentence 
	 * @param words list of words in the sentence
	 * @param recipe_name name of the recipe
	 * @param sentence_idx index of the sentence within the current recipe
	 * @param curr_sentence_char_offset starting no-space character offset of the 
	 * 			current sentence
	 * @param sentence_string full text string of the current sentence
	 * @param predicate_idx_to_event map of character offsets to the recipe events that start
	 * 			at that offset. Note that this is not the no-space character offset since
	 * 			the tree map just cares about the relative order
	 */
	private void findActionVerbsAndArguments(TreeGraphNode node, 
			Map<Pair<Integer, Integer>, String> edge_rels,
			Map<TreeGraphNode, Set<TreeGraphNode>> node_to_deps,
			Map<TreeGraphNode, TreeGraphNode> dep_to_gov,
			String root_type,
			List<Tree> leaves,
			List<String> lemmas, List<Label> tags, List<HasWord> words,
			String recipe_name, int sentence_idx, int curr_sentence_char_offset, 
			String sentence_string, TreeMap<Integer, RecipeEvent> predicate_idx_to_event) {
		String node_string = node.nodeString();

		int index = node.index();
		Label tag = tags.get(index - 1);

		int hw_character_offset = charOffset(leaves.get(index - 1));

		// Find the no-space character offset of the predicate
		int no_space_character_offset = hw_character_offset;
		String sentence_prefix = sentence_string.substring(0, hw_character_offset);
		while (sentence_prefix.contains(" I would ")) {
			no_space_character_offset -= 9;
			sentence_prefix = sentence_prefix.replaceFirst(" I would ", "");
		}
		while (sentence_prefix.contains("I would ")) {
			no_space_character_offset -= 8;
			sentence_prefix = sentence_prefix.replaceFirst("I would ", "");
		}
		while (sentence_prefix.contains(" ")) {
			no_space_character_offset--;
			sentence_prefix = sentence_prefix.replaceFirst(" ", "");
		}

		if (tag.value().startsWith("V")) {
			Set<TreeGraphNode> deps = node_to_deps.get(node);
			if (deps == null) {
				System.out.println(node + " " + deps);
				// Create RecipeEvent
				RecipeEvent event = new RecipeEvent(node_string.toLowerCase(), recipe_name, sentence_idx, curr_sentence_char_offset + no_space_character_offset);
				predicate_idx_to_event.put(hw_character_offset, event);
				return;
			}
			
			// check for pcomp
			boolean has_pcomp = false;
			TreeGraphNode pcomp = null;
			for (TreeGraphNode dep3 : deps) {
				String rel_string2 = edge_rels.get(new Pair<Integer, Integer>(hw_character_offset, charOffset(dep3)));
				if (rel_string2.equals("pcomp")) {
					has_pcomp = true;
					pcomp = dep3;
					break;
				}
			}
			if (has_pcomp) {
				System.out.println("has pcomp");
				TreeGraphNode grandgov = dep_to_gov.get(node);
				if (grandgov != null) {
					int grandgov_index = grandgov.index();
					RecipeEvent grandevent = predicate_idx_to_event.get(grandgov_index);
					if (grandevent != null) {
						Pair<String, Set<TreeGraphNode>> args = getTreeGraphString(node, node_to_deps, edge_rels, lemmas, tags, words, true);
						grandevent.addPrepositionalArgument(hw_character_offset, args.getFirst(), pcomp.nodeString());
					}
				}
				return;
			}
			
			
			// Create RecipeEvent
			RecipeEvent event = new RecipeEvent(node_string.toLowerCase(), recipe_name, sentence_idx, curr_sentence_char_offset + no_space_character_offset);
			

			boolean has_subj = false;
			TreeGraphNode subj = null;
			for (TreeGraphNode dep : deps) {
				String rel_string2 = edge_rels.get(new Pair<Integer, Integer>(hw_character_offset, charOffset(dep)));
				if (rel_string2.equals("nsubj") && !root_type.equals("SINV")) {
					has_subj = true;
					subj = dep;
					break;
				}
			}
			// If the verb has a subject other than "I", assume parse error and add the subject as an argument.
			if (has_subj && !root_type.equals("SINV")) {
				System.out.println(has_subj + " " + root_type);
				if (!subj.nodeString().equals("I")) {

					Pair<String, Set<TreeGraphNode>> args = getTreeGraphString(node, node_to_deps, edge_rels, lemmas, tags, words, false);
					String arg_string = args.getFirst();
					Set<TreeGraphNode> verbs = args.getSecond();
					if (verbs.size() == 1) {
						TreeGraphNode verb = verbs.iterator().next();
						dep_to_gov.put(verb, node);
						findActionVerbsAndArguments(verb, edge_rels, node_to_deps, dep_to_gov, "", leaves, lemmas, tags, words, recipe_name, sentence_idx,
								curr_sentence_char_offset, sentence_string, predicate_idx_to_event);
						RecipeEvent dep_event = predicate_idx_to_event.get(charOffset(leaves.get(verb.index() - 1)));
						if (dep_event != null) {
							String[] split = arg_string.split(" ");
							int found = -1;
							for (int i = 0; i < split.length; i++) {
								if (split[i].equals(node.nodeString())) {
									found = i;
									break;
								}
							}
							if (tags.get(index - found - 1).value().equals("IN")) {
								dep_event.addPrepositionalArgument(hw_character_offset, arg_string);
							} else {
								dep_event.addOtherArgument(hw_character_offset, arg_string);
							}
						}
					} else {
						// TODO(chloe): do something else in this case?
						for (TreeGraphNode verb : verbs) {
							dep_to_gov.put(verb, node);
							findActionVerbsAndArguments(verb, edge_rels, node_to_deps, dep_to_gov, "", leaves, lemmas, tags, words, recipe_name, sentence_idx,
									curr_sentence_char_offset, sentence_string, predicate_idx_to_event);
						}
					}
					return;
				}
			}

			// Search through the dependents of the verb and add each expanded dependent as an argument.
			Set<TreeGraphNode> new_deps = new HashSet<TreeGraphNode>(deps);
			TreeGraphNode pobj = null;
			while (new_deps.size() != 0) {
				List<TreeGraphNode> deps_list = new ArrayList<TreeGraphNode>(new_deps);
				new_deps.clear();
				for (TreeGraphNode dep : deps_list) {
					TreeGraphNode gov = dep_to_gov.get(dep);
					if (gov == null) {
						gov = node;
					}
					int gov_index = index;
					int gov_hw_offset = hw_character_offset;
					String gov_string = node_string;
					if (gov != null) {
						gov_index = gov.index();
						gov_hw_offset = charOffset(leaves.get(gov_index - 1));
						gov_string = gov.nodeString();
					}
					int dep_index = dep.index();
					int dep_hw_offset = charOffset(leaves.get(dep_index - 1));
					String dep_string = dep.nodeString();

					if (dep.nodeString().equals("would")) {
						continue;
					}
					String rel_string = edge_rels.get(new Pair<Integer, Integer>(gov_hw_offset, dep_hw_offset));
					if (rel_string.startsWith("nsubj") && !root_type.equals("SINV") && (gov == node || dep.nodeString().equals("I"))) {
						// skip subjects
						continue;
					}
					
					if (rel_string.startsWith("pobj")) {
						// Store the prepositional object, but ignore for now. We will deal with it when the
						// prepositional phrase is found.
						pobj = dep;
						continue;
					}

					// For vmod and xcomp edges, create an argument for the current verb
					if (rel_string.equals("vmod") || rel_string.equals("xcomp")) {
						Pair<String, Set<TreeGraphNode>> args = getTreeGraphString(dep, node_to_deps, edge_rels, lemmas, tags, words, true);
						String arg_string = args.getFirst();
						String[] split = arg_string.split(" ");
						int found = -1;
						for (int i = 0; i < split.length; i++) {
							if (split[i].equals(dep.nodeString())) {
								found = i;
								break;
							}
						}
						if (found != -1) {
							if (tags.get(dep_index - found - 1).value().equals("IN")) {
								event.addPrepositionalArgument(dep_hw_offset, arg_string);
							} else {
								event.addOtherArgument(dep_hw_offset, arg_string);
							}
						}
						continue;
					}

					// Deal with prepositional phrases and determining whether or not a dependent verb is a new verb or
					// part of the current verb. Uses heuristics to find out.
					boolean is_prep = rel_string.startsWith("prep") || tags.get(dep_index - 1).value().equals("IN");
					Label dep_tag = tags.get(dep_index - 1);
					if (dep_tag.value().startsWith("V") && !is_prep && !rel_string.equals("advcl")
							) {
						if (dep_index == index + 2 && rel_string.startsWith("conj")) {
							int underscore = rel_string.indexOf('_');
							String conj_string = rel_string.substring(underscore + 1);
							node_string += " " + conj_string + " " + dep_string;
							Set<TreeGraphNode> depdeps = node_to_deps.get(dep);
							if (depdeps != null) {
								new_deps.addAll(depdeps);
								for (TreeGraphNode depdep : depdeps) {
									dep_to_gov.put(depdep, dep);
								}
							}
							event.addVerbToPredicate(dep.nodeString(), conj_string, (dep.index() > gov_index));
						} else if (rel_string.startsWith("conj") || rel_string.equals("parataxis") || rel_string.equals("dep")) {
							Set<TreeGraphNode> depdeps = node_to_deps.get(dep);
							has_subj = false;
							if (depdeps != null) {
								for (TreeGraphNode depdep : depdeps) {
									String rel_string2 = edge_rels.get(new Pair<Integer, Integer>(dep_hw_offset, charOffset(leaves.get(depdep.index() - 1))));
									if (rel_string2.equals("nsubj")) {
										has_subj = true;
										break;
									}
								}
								if (has_subj) {
									Pair<String, Set<TreeGraphNode>> args = getTreeGraphString(dep, node_to_deps, edge_rels, lemmas, tags, words, true);
									String arg_string = args.getFirst();
									String[] split = arg_string.split(" ");
									int found = -1;
									for (int i = 0; i < split.length; i++) {
										if (split[i].equals(dep.nodeString())) {
											found = i;
											break;
										}
									}
									if (found != -1) {
										if (tags.get(dep_index - found - 1).value().equals("IN")) {
											event.addPrepositionalArgument(dep_hw_offset, arg_string);
										} else {
											event.addOtherArgument(dep_hw_offset, arg_string);
										}
									}
								} else {
									findActionVerbsAndArguments(dep, edge_rels, node_to_deps, dep_to_gov, "", leaves, lemmas, tags, words, recipe_name, sentence_idx,
											curr_sentence_char_offset, sentence_string, predicate_idx_to_event);
								}
							}
						} else {
							Set<TreeGraphNode> depdeps = node_to_deps.get(dep);
							if (depdeps != null) {
								new_deps.addAll(depdeps);
								for (TreeGraphNode depdep : depdeps) {
									dep_to_gov.put(depdep, dep);
								}
							}
							event.addVerbToPredicate(dep.nodeString(), "", (dep.index() > gov_index));
						}
					} else {  // prepositional phrase
						Pair<String, Set<TreeGraphNode>> args = getTreeGraphString(dep, node_to_deps, edge_rels, lemmas, tags, words, is_prep || rel_string.equals("dobj"));
						String arg_string = args.getFirst();

						for (TreeGraphNode verb : args.getSecond()) {
							findActionVerbsAndArguments(verb, edge_rels, node_to_deps, dep_to_gov, "", leaves, lemmas, tags, words, recipe_name, sentence_idx,
									curr_sentence_char_offset, sentence_string, predicate_idx_to_event);
						}

						if (is_prep) {
							String pobj_arg_string = null;
							if (pobj != null) {
								args = getTreeGraphString(pobj, node_to_deps, edge_rels, lemmas, tags, words, is_prep);
								pobj_arg_string = args.getFirst();

								for (TreeGraphNode verb : args.getSecond()) {
									findActionVerbsAndArguments(verb, edge_rels, node_to_deps, dep_to_gov, "" , leaves, lemmas, tags, words, recipe_name, sentence_idx,
											curr_sentence_char_offset, sentence_string, predicate_idx_to_event);
								}
							} else {
								for (TreeGraphNode dep2 : deps_list) {
									TreeGraphNode gov2 = dep_to_gov.get(dep2);
									if (gov2 == null) {
										gov2 = gov;
									}
									String rel_string2 = edge_rels.get(new Pair<Integer, Integer>(charOffset(leaves.get(gov2.index() - 1)), charOffset(leaves.get(dep2.index() - 1))));

									if (rel_string2.equals("pobj")) {
										args = getTreeGraphString(dep2, node_to_deps, edge_rels, lemmas, tags, words, is_prep);
										pobj_arg_string = args.getFirst();

										for (TreeGraphNode verb : args.getSecond()) {
											findActionVerbsAndArguments(verb, edge_rels, node_to_deps, dep_to_gov, "", leaves, lemmas, tags, words, recipe_name, sentence_idx,
													curr_sentence_char_offset, sentence_string, predicate_idx_to_event);
										}
										break;
									}
								}
							}

							// Find the true preposition from the collapsed dependency relation
							int underscore = rel_string.indexOf('_');
							if (underscore != -1) {
								String prep_string = rel_string.substring(underscore + 1);
								prep_string = prep_string.replace('_', ' ');
								if (pobj_arg_string != null) {
									if (prep_string.contains(arg_string)) {
										arg_string = prep_string + " " + pobj_arg_string;
									} else {
										arg_string = prep_string + " " + arg_string + " " + pobj_arg_string;
									}
								} else {
									arg_string = prep_string + " " + arg_string;
								}
							}
							event.addPrepositionalArgument(dep_hw_offset, arg_string);
						} else if (rel_string.equals("dobj") || rel_string.equals("nsubj")) {
							//							event.setDirectObject(arg_string);
							String[] split = arg_string.split(" ");
							int found = -1;
							for (int i = 0; i < split.length; i++) {
								if (split[i].equals(dep.nodeString())) {
									found = i;
									break;
								}
							}
							if (found != -1) {
								System.out.println("DIRECTOBJECT " + arg_string);
								if (tags.get(dep_index - found - 1).value().equals("IN")) {
									event.addPrepositionalArgument(dep_hw_offset, arg_string);
								} else {
									if (arg_string.contains("minutes") || arg_string.contains("hours")) {
										event.addPrepositionalArgument(dep_hw_offset, arg_string, "for");
									} else {
										event.setDirectObject(arg_string);
									}
								}
							}
						} else {
							String[] split = arg_string.split(" ");
							int found = -1;
							for (int i = 0; i < split.length; i++) {
								if (split[i].equals(dep.nodeString())) {
									found = i;
									break;
								}
							}
							if (found != -1) {
								if (tags.get(dep_index - found - 1).value().equals("IN")) {
									System.out.println("prep");
									event.addPrepositionalArgument(dep_hw_offset, arg_string);
								} else {
									event.addOtherArgument(dep_hw_offset, arg_string);
								}
							}
						}
					}
				}
			}
			System.out.println(event);
			predicate_idx_to_event.put(index, event);
		} else {
			// This method should not be called if the node is not a verb.
			//
			// Not sure if this happens, so I will currently have it crash so I can investigate it.
			Pair<String, Set<TreeGraphNode>> args = getTreeGraphString(node, node_to_deps, edge_rels, lemmas, tags, words, false);
			String arg_string = args.getFirst();
			Set<TreeGraphNode> verbs = args.getSecond();
			System.out.println(arg_string);
			System.out.println("getting args error");
			System.exit(1);
		}
	}


	/**
	 * Splits a file into a list of sentences. Identifies sentence breaks through the
	 * CoreNLP DocumentPreprocessor as well as if a period appears at the end of a word
	 * and the next word is upper case.
	 *   Ex. Preheat to 350 F. Bake for 40 min.  (Split after "F.")
	 *   
	 *   Periods themselves are NOT included in the initial set of final punctuation
	 *   marks, as there are a few issues with abbreviations. This function looks for
	 *   periods that are followed by capital words and splits in those cases.
	 *   
	 * @param input_file input recipe file. It should be plaintext instructions.
	 * @return List of sentences represented as List<HasWord>
	 */
	private static List<List<HasWord>> splitSentences(File input_file) {
		DocumentPreprocessor dp = new DocumentPreprocessor(input_file.getAbsolutePath());
		dp.setSentenceFinalPuncWords(FINAL_SENTENCE_PUNC);
		// Set the tokenizer to assume that carriage returns are automatic sentence ends.
		dp.setTokenizerFactory(PTBTokenizer.factory(new CoreLabelTokenFactory(), "tokenizeNLs=true"));
		List<List<HasWord>> sentences = new ArrayList<List<HasWord>>();
		for (List<HasWord> sentence : dp) {
			int num_words = sentence.size();
			int curr_begin = 0;
			for (int w = 0; w < num_words; w++) {
				HasWord word = sentence.get(w);
				if (word.word().endsWith(".")) {
					if ((w != num_words - 1 && Character.isUpperCase(sentence.get(w + 1).word().charAt(0)))
							|| (word.word().equals("."))) {
						List<HasWord> new_sentence = sentence.subList(curr_begin, w + 1);
						if (new_sentence.size() != 0) {
							sentences.add(new_sentence);
						}
						curr_begin = w + 1;
					}
				}
				else if (word.word().equals("then")) {
					List<HasWord> new_sentence = sentence.subList(curr_begin, w);
					if (new_sentence.size() != 0) {
						sentences.add(new_sentence);
					}
					curr_begin = w + 1;
				}
			}
			List<HasWord> new_sentence = sentence.subList(curr_begin, num_words);
			if (new_sentence.size() != 0) {
				sentences.add(new_sentence);
			}

		}
		return sentences;
	}

	/**
	 * Checks for suspected imperative parse tree errors. In recipes, most
	 * sentences are supposed to be imperatives, so if the first tag is a 
	 * noun or an adjective, we will mark it as incorrect.
	 * 
	 * @param tree Parse tree to investigate
	 * @return True if there is a suspected error, False otherwise
	 */
	private static boolean hasSuspectedImperativeParseTreeError(Tree tree) {
		List<Label> preleaves = tree.preTerminalYield();
		if (preleaves.get(0).value().startsWith("N") || preleaves.get(0).value().startsWith("J")
				|| preleaves.get(0).value().equals("CD") || preleaves.get(0).value().startsWith("RB")) {
			return true;
		}
		return false;
	}

	/**
	 * Checks for suspected declarative sentences. In recipes, these will
	 * be few, but we can safely assume sentences where the first word is
	 * a determiner or a pronoun are declarative, as those are hard
	 * tags to mislabel.
	 * 
	 * @param tree Parse tree to investigate
	 * @return True if the sentence is suspected to be declarative, False otherwise
	 */
	private static boolean isSuspectedDeclarativeSentence(Tree tree) {
		List<Label> preleaves = tree.preTerminalYield();
		if (preleaves.get(0).value().equals("DT") || preleaves.get(0).value().startsWith("PR")) {
			return true;
		}
		return false;
	}


	/**
	 * Parse one file and store the actions and their arguments in the appropriate
	 * value of the recipes_to_events_by_sent_and_pred_idx_ map.
	 * 
	 * @param input_file Recipe file to parse
	 * @throws IOException 
	 * @throws IllegalArgumentException 
	 */
	private void parseFile(File input_file, File fulltext_file) throws IllegalArgumentException, IOException {
		System.out.println("Processing Recipe " + input_file);

		Set<String> ingredients = IngredientParser.parseIngredientsFromFullTextFile(fulltext_file);

		int sentence_index = 0;

		// Use splitSentences() to find sentence boundaries.
		List<List<HasWord>> sentences = splitSentences(input_file);
		List<String> recipe_sentences = new ArrayList<String>();
		List<List<Label>> recipe_sentence_tags = new ArrayList<List<Label>>();

		int curr_sentence_char_offset = 0;
		// Iterate over the sentences
		for (List<HasWord> sentence : sentences) {
			List<HasWord> sentence_copy = new ArrayList<HasWord>(sentence);
			if (ProjectParameters.VERBOSE) {
				System.out.println(sentence);
			}
			String original_sentence_string = StringUtils.join(sentence_copy);

			// Need to remove expanded characters for correct offset calculations.
			original_sentence_string = original_sentence_string.replaceAll("-LRB-", "(").replaceAll("-RRB-", ")");
			if (original_sentence_string.contains("(") && !original_sentence_string.contains(")")) {
				original_sentence_string = original_sentence_string.replace("(", "");
			}
			if (original_sentence_string.contains(")") && !original_sentence_string.contains("(")) {
				original_sentence_string = original_sentence_string.replace(")", "");
			}
			
			String sentence_string = original_sentence_string;

			
			// Parse sentence.
//			Tree tree = lp.apply(sentence_copy);
			Tree tree = null;
			List<Pair<List<String>, Tree>> trees = lemmatizer.parse(original_sentence_string);
			if (trees.size() != 1) {
				System.out.println(trees);
				System.out.println("more than one tree error");
				System.exit(1);
			}
			tree = trees.get(0).getSecond();

			//Get the tags of the words as specified by the parse.
			// NOTE: these may be different from the ones returned by the Lemmatizer
			// so we have to use these to match the tree.
			List<Label> tags = tree.preTerminalYield();
			List<Label> tokens = tree.yield();
			List<String> lemmas = new ArrayList<String>();
			for (Label token : tokens) {
				lemmas.add(((CoreLabel)(token)).lemma());
			}
			
			if (!sentence_string.matches(".*[a-zA-Z].*")) {
				sentence_index++;
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				continue;
			}
			
			System.out.println(tags);

			// If a sentence has "not" as the second word, ignore for now.
			// E.g., Do not forget to check the temperature.
			if (sentence_copy.size() > 1 && sentence_copy.get(1).word().equals("not")) {
				sentence_index++;
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				continue;
			}
			if (sentence_string.contains(" not ")) {
				sentence_index++;
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				continue;
			}
			if (sentence_string.contains(" will be ")) {
				sentence_index++;
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				continue;
			}

//			Triple<List<String>, List<String>, List<String>> lemmasStemsTags = lemmatizer.lemmatizeStemAndTag(sentence_string);

			try {

			if (!nofuss) {
			// If the first word is labeled a noun/adj, there is probably a POS error.
			// (Since these are mostly imperative sentences.)
			// Add a "They" to the beginning and proceed.
			// If the first word is a det/pronoun, assume declarative sentence and ignore.
			if (hasSuspectedImperativeParseTreeError(tree)
					|| tags.get(0).value().equals("VB") || tags.get(0).value().equals("VBN")
					|| (tags.get(0).value().equals("VBG") && !sentence_copy.get(0).word().endsWith("ing"))
					|| tags.get(0).value().equals("UH")
					) {
				sentence_string = "I would " + Character.toLowerCase(sentence_string.charAt(0)) + sentence_string.substring(1);
//				lemmasStemsTags = lemmatizer.lemmatizeStemAndTag(sentence_string);
				TokenizerFactory<CoreLabel> tokenizerFactory =
						PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
				Tokenizer<CoreLabel> tok =
						tokenizerFactory.getTokenizer(new StringReader(sentence_string));
				List<CoreLabel> rawWords2 = tok.tokenize();

				// Need to re-add the words to the list since the character offsets will have changed.
				sentence_copy.clear();
				for (CoreLabel word : rawWords2) {
					sentence_copy.add(word);
				}
//				tree = lp.parse(sentence_copy);
				trees = lemmatizer.parse(sentence_string);
				if (trees.size() != 1) {
					System.out.println(trees);
					System.out.println("more than one tree error");
					System.exit(1);
				}
				tree = trees.get(0).getSecond();
				tags = tree.preTerminalYield();
				lemmas.clear();
				for (Label token : tokens) {
					lemmas.add(((CoreLabel)(token)).lemma());
				}
			} else if (isSuspectedDeclarativeSentence(tree)) {
				sentence_index++;
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				continue;
			} else {
				TokenizerFactory<CoreLabel> tokenizerFactory =
						PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
				Tokenizer<CoreLabel> tok =
						tokenizerFactory.getTokenizer(new StringReader(sentence_string));
				List<CoreLabel> rawWords2 = tok.tokenize();

				// Need to re-add the words to the list since the character offsets will have changed.
				sentence_copy.clear();
				for (CoreLabel word : rawWords2) {
					sentence_copy.add(word);
				}
//				tree = lp.parse(sentence_copy);
				trees = lemmatizer.parse(sentence_string);
				if (trees.size() != 1) {
					System.out.println(trees);
					System.out.println("more than one tree error");
					System.exit(1);
				}
				tree = trees.get(0).getSecond();
				tags = tree.preTerminalYield();
				lemmas.clear();
				for (Label token : tokens) {
					lemmas.add(((CoreLabel)(token)).lemma());
				}
			}
//			if (ProjectParameters.VERBOSE) {
				System.out.println(sentence_string);
				System.out.println(StringUtils.join(tags));
//			}
			}
				
			// Get dependency structure for sentence.
			GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
			Collection<TypedDependency> tdl = gs.typedDependenciesCollapsedTree();
			List<Tree> leaves = tree.getLeaves();

			
			String tag_string = StringUtils.join(tags);
			if (!nofuss) {
			boolean found = true;
			while (found) {
				found = false;
				for (int t = 0; t < tags.size(); t++) {
					if (t != 0 && t != tags.size() - 1 
							&& tags.get(t - 1).value().equals(",") && tags.get(t).value().startsWith("NN")
							&& (tags.get(t + 1).value().equals(",") || tags.get(t + 1).value().equals("CC"))) {
						found = true;
						String new_sentence_string = StringUtils.join(leaves.subList(0, t)) + " the " + StringUtils.join(leaves.subList(t, tags.size()));
						System.out.println("@@@ " + new_sentence_string);
//						lemmasStemsTags = lemmatizer.lemmatizeStemAndTag(new_sentence_string);
						TokenizerFactory<CoreLabel> tokenizerFactory =
								PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
						Tokenizer<CoreLabel> tok =
								tokenizerFactory.getTokenizer(new StringReader(new_sentence_string));
						List<CoreLabel> rawWords2 = tok.tokenize();

						// Need to re-add the words to the list since the character offsets will have changed.
						sentence_copy.clear();
						for (CoreLabel word : rawWords2) {
							sentence_copy.add(word);
						}
						sentence_string = new_sentence_string;
//						tree = lp.parse(sentence_copy);
						trees = lemmatizer.parse(sentence_string);
						if (trees.size() != 1) {
							System.out.println(trees);
							System.out.println("more than one tree error");
							System.exit(1);
						}
						tree = trees.get(0).getSecond();
						gs = gsf.newGrammaticalStructure(tree);
						tdl = gs.typedDependenciesCollapsedTree();
						leaves = tree.getLeaves();
						tags = tree.preTerminalYield();
						lemmas.clear();
						for (Label token : tokens) {
							lemmas.add(((CoreLabel)(token)).lemma());
						}
						break;
					}
				}
			}
			if (tag_string.contains("VB VBG") || tag_string.contains("VB VBZ") || tag_string.contains("VB VB")) {
				for (int t = 0; t < tags.size(); t++) {
					if (t != tags.size() - 1) {
						if (tags.get(t).value().equals("VB") && (tags.get(t+1).value().equals("VBG") || tags.get(t+1).value().equals("VBZ")
								 || tags.get(t+1).value().equals("VB"))) {
							String new_sentence_string = StringUtils.join(leaves.subList(0, t + 1)) + " the " + StringUtils.join(leaves.subList(t+1, tags.size()));
							System.out.println("!!!! " + new_sentence_string);
//							lemmasStemsTags = lemmatizer.lemmatizeStemAndTag(new_sentence_string);
							TokenizerFactory<CoreLabel> tokenizerFactory =
									PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
							Tokenizer<CoreLabel> tok =
									tokenizerFactory.getTokenizer(new StringReader(new_sentence_string));
							List<CoreLabel> rawWords2 = tok.tokenize();

							// Need to re-add the words to the list since the character offsets will have changed.
							sentence_copy.clear();
							for (CoreLabel word : rawWords2) {
								sentence_copy.add(word);
							}
							sentence_string = new_sentence_string;
//							tree = lp.parse(sentence_copy);
							trees = lemmatizer.parse(sentence_string);
							if (trees.size() != 1) {
								System.out.println(trees);
								System.out.println("more than one tree error");
								System.exit(1);
							}
							tree = trees.get(0).getSecond();
							gs = gsf.newGrammaticalStructure(tree);
							tdl = gs.typedDependenciesCollapsedTree();
							leaves = tree.getLeaves();
							tags = tree.preTerminalYield();
							lemmas.clear();
							for (Label token : tokens) {
								lemmas.add(((CoreLabel)(token)).lemma());
							}
							break;
						}
					}
				}
			}
			}

			// Create map from gov to dependents since GrammaticalStructure doesn't give that
			// information publicly.
			TreeGraphNode root = null;
			
			int root_index = -1;
			String root_type = tree.getChild(0).label().value();
			Map<TreeGraphNode, Set<TreeGraphNode>> node_to_deps = new HashMap<TreeGraphNode, Set<TreeGraphNode>>();
			Map<Pair<Integer, Integer>, String> edge_rels = new HashMap<Pair<Integer, Integer>, String>();
			for (TypedDependency td : tdl) {
				TreeGraphNode tgn = td.gov();
				int index = tgn.index();
				if (index == 0) {
					root = td.dep();
					root_index = td.dep().index();
				} else {
					edge_rels.put(new Pair<Integer, Integer>(charOffset(leaves.get(td.gov().index() - 1)), charOffset(leaves.get(td.dep().index() - 1))), td.reln().toString());
				}
				Set<TreeGraphNode> deps = node_to_deps.get(tgn);
				if (deps == null) {
					deps = new HashSet<TreeGraphNode>();
					node_to_deps.put(tgn, deps);
				}
				deps.add(td.dep());
			}


			if (!nofuss) {
			// If the root of the dependency graph is not a verb, something has been parsed wrong.
			// However, many times with imperative sentences, even if the root is a verb, there will
			// be a parse error if we don't transform the sentence into a declarative version.
			// We won't get every case, but if the sentence starts with a prepositional phrase
			// or a participle phrase, we can heuristically find the end of the phrase and 
			// then add "I would" there.
			// Heuristics are:
			// 		-- a word labeled as an adjective after the nouns of the phrase
			//			have been presented
			//		-- a comma appears
			// After adding "I would" to the assigned place, the sentence is reparsed.
			// Also the node_to_deps map is re-populated and the tags array
			// is re-populated.
			// If we can't fix the sentence in this way, we punt and ignore the sentence completely.
			if (!tags.get(root_index - 1).value().matches("VB|VBP|VBD") && (tags.get(0).value().equals("IN") || tags.get(0).value().equals("VBG"))) {
				//				System.out.println(tags.get(0));
				boolean hit_noun = false;
				boolean changed = false;
				for (int t = 1; t < tags.size(); t++) {
					String curr_tag = tags.get(t).value();
					System.out.println(curr_tag + " " + hit_noun);
					if (curr_tag.equals("IN")) {
						hit_noun = false;
					} else if (curr_tag.equals("CC")) {
						hit_noun = false;
					} else if (curr_tag.startsWith(",")) {  // comma after the PP
						changed = true;
						String new_sentence_string = StringUtils.join(leaves.subList(0, t + 1)) + " I would " + StringUtils.join(leaves.subList(t+1, tags.size()));
//						lemmasStemsTags = lemmatizer.lemmatizeStemAndTag(new_sentence_string);
						TokenizerFactory<CoreLabel> tokenizerFactory =
								PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
						Tokenizer<CoreLabel> tok =
								tokenizerFactory.getTokenizer(new StringReader(new_sentence_string));
						List<CoreLabel> rawWords2 = tok.tokenize();

						// Need to re-add the words to the list since the character offsets will have changed.
						sentence_copy.clear();
						for (CoreLabel word : rawWords2) {
							sentence_copy.add(word);
						}
						sentence_string = new_sentence_string;
//						tree = lp.parse(sentence_copy);
						trees = lemmatizer.parse(new_sentence_string);
						if (trees.size() != 1) {
							System.out.println(trees);
							System.out.println("more than one tree error");
							System.exit(1);
						}
						tree = trees.get(0).getSecond();
						gs = gsf.newGrammaticalStructure(tree);
						tdl = gs.typedDependenciesCollapsedTree();
						leaves = tree.getLeaves();
						node_to_deps.clear();
						edge_rels.clear();
						root_type = tree.getChild(0).label().value();
						for (TypedDependency td : tdl) {
							TreeGraphNode tgn = td.gov();
							int index = tgn.index();
							if (index == 0) {
								root = td.dep();
								root_index = td.dep().index();
							} else {
								edge_rels.put(new Pair<Integer, Integer>(charOffset(leaves.get(td.gov().index() - 1)), charOffset(leaves.get(td.dep().index() - 1))), td.reln().toString());
							}
							Set<TreeGraphNode> deps = node_to_deps.get(tgn);
							if (deps == null) {
								deps = new HashSet<TreeGraphNode>();
								node_to_deps.put(tgn, deps);
							}
							deps.add(td.dep());
						}
						tags = tree.preTerminalYield();
						lemmas.clear();
						for (Label token : tokens) {
							lemmas.add(((CoreLabel)(token)).lemma());
						}
						break;
					} else if (hit_noun) {  // After the nouns in the PP
						changed = true;
						String new_sentence_string = StringUtils.join(leaves.subList(0, t)) + ", I would " + StringUtils.join(leaves.subList(t, tags.size()));
//						lemmasStemsTags = lemmatizer.lemmatizeStemAndTag(new_sentence_string);
						TokenizerFactory<CoreLabel> tokenizerFactory =
								PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
						Tokenizer<CoreLabel> tok =
								tokenizerFactory.getTokenizer(new StringReader(new_sentence_string));
						List<CoreLabel> rawWords2 = tok.tokenize();

						// Need to re-add the words to the list since the character offsets will have changed.
						sentence_copy.clear();
						for (CoreLabel word : rawWords2) {
							sentence_copy.add(word);
						}

						sentence_string = new_sentence_string;
						System.out.println(new_sentence_string);
//						tree = lp.parse(sentence_copy);
						trees = lemmatizer.parse(new_sentence_string);
						if (trees.size() != 1) {
							System.out.println(trees);
							System.out.println("more than one tree error");
							System.exit(1);
						}
						tree = trees.get(0).getSecond();
						// Get dependency structure for sentence.
						gs = gsf.newGrammaticalStructure(tree);
						tdl = gs.typedDependenciesCollapsedTree();
						leaves = tree.getLeaves();

						node_to_deps.clear();
						edge_rels.clear();
						root_type = tree.getChild(0).label().value();
						for (TypedDependency td : tdl) {
							TreeGraphNode tgn = td.gov();
							int index = tgn.index();
							if (index == 0) {
								root = td.dep();
								root_index = td.dep().index();
							} else {
								edge_rels.put(new Pair<Integer, Integer>(charOffset(leaves.get(td.gov().index() - 1)), charOffset(leaves.get(td.dep().index() - 1))), td.reln().toString());
							}
							Set<TreeGraphNode> deps = node_to_deps.get(tgn);
							if (deps == null) {
								deps = new HashSet<TreeGraphNode>();
								node_to_deps.put(tgn, deps);
							}
							deps.add(td.dep());
						}
						tags = tree.preTerminalYield();
						lemmas.clear();
						for (Label token : tokens) {
							lemmas.add(((CoreLabel)(token)).lemma());
						}
						break;
					} else if (curr_tag.startsWith("N")) {
						hit_noun = true;
					}	
				}
				if (root_index < 0 || (!changed && !tags.get(root_index - 1).value().startsWith("V"))) {
					System.out.println("Cannot parse sentence: " + sentence_string);
					recipe_sentences.add(sentence_string);
					recipe_sentence_tags.add(tags);
					sentence_index++;
					continue;
				}
			}
			if (root_index < 0 || !tags.get(root_index - 1).value().startsWith("V")) {
				System.out.println("Cannot parse sentence: " + sentence_string);
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				sentence_index++;
				continue;
			}
			//			if (ProjectParameters.VERBOSE) {
			//				System.out.println(sentence_string);
			//				System.out.println(StringUtils.join(tags));
			//			}
//			if (ProjectParameters.VERBOSE) {
				//				if (tdl.toString().contains("ccomp") || tdl.toString().contains("parataxis")) {
				System.out.println(sentence_string);
				System.out.println(StringUtils.join(tags));
				System.out.println(tdl);
				//				}
//			}
			}
			if (root_index < 0 || !tags.get(root_index - 1).value().startsWith("V")) {
				System.out.println("Cannot parse sentence: " + sentence_string);
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				sentence_index++;
				continue;
			}

			recipe_sentences.add(sentence_string);
			recipe_sentence_tags.add(tags);

			// Find appropriate value in the map for this current recipe and sentence.
			// Create values in the map, if necessary. TODO(chloe): This will always be necessary, right?
			TreeMap<Integer, TreeMap<Integer, RecipeEvent>> event_map_for_recipe = recipes_to_events_by_sent_and_pred_idx_.get(input_file.getName());
			if (event_map_for_recipe == null) {
				event_map_for_recipe = new TreeMap<Integer, TreeMap<Integer, RecipeEvent>>();
				recipes_to_events_by_sent_and_pred_idx_.put(input_file.getName(), event_map_for_recipe);
			}
			TreeMap<Integer, RecipeEvent> event_map_for_sentence = event_map_for_recipe.get(sentence_index);
			if (event_map_for_sentence == null) {
				event_map_for_sentence = new TreeMap<Integer, RecipeEvent>();
				event_map_for_recipe.put(sentence_index, event_map_for_sentence);
			}

			System.out.println(root);
			// Find the action verbs and their arguments.
			findActionVerbsAndArguments(root, edge_rels, node_to_deps, new HashMap<TreeGraphNode, TreeGraphNode>(),
					root_type,
					leaves, lemmas, tags, sentence_copy, input_file.getName(), sentence_index, 
					curr_sentence_char_offset, sentence_string, event_map_for_sentence);

//			System.out.println(event_map_for_sentence);
			// Use the IngredientParser to identify the first appearances of ingredients in the recipe.
			// addIngredientsToEventList removes found ingredients from the ingredients set so that when the next
			// sentence is read in, it won't search for ingredients that were already found.
//			ingredients = IngredientParser.addIngredientsToEventList(ingredients, event_map_for_sentence.values().iterator());
			sentence_index++;
			original_sentence_string = original_sentence_string.replaceAll(" " , "");
			curr_sentence_char_offset += original_sentence_string.length();
			} catch (Exception ex) {
				System.out.println("Cannot parse sentence: " + sentence_string);
				recipe_sentences.add(sentence_string);
				recipe_sentence_tags.add(tags);
				sentence_index++;
				continue;
			}
		}
		recipe_to_sentences_.put(input_file.getName(), recipe_sentences);
		recipe_to_sentence_tags_.put(input_file.getName(), recipe_sentence_tags);
	}

	/**
	 * Prints all the recipe parse information to files in the output directory
	 * split by recipe.
	 * 
	 * @param output_directory_name Output directory name
	 * @throws IOException if there is some error writing the files.
	 */
	public void printParsedArgumentsToFile(String output_directory_name) throws IOException {
		for (String recipe : recipes_to_events_by_sent_and_pred_idx_.keySet()) {
			TreeMap<Integer, TreeMap<Integer, RecipeEvent>> sentence_event_map = recipes_to_events_by_sent_and_pred_idx_.get(recipe);
			List<String> sentence_list = recipe_to_sentences_.get(recipe);
			BufferedWriter bw = new BufferedWriter(new FileWriter(output_directory_name + recipe));
			if (sentence_event_map != null) {
				for (int s = 0; s < sentence_list.size(); s++) {
					bw.write("SENTID: " + s + "\n");
					bw.write("SENT: " + sentence_list.get(s) + "\n");
					TreeMap<Integer, RecipeEvent> predicate_event_map = sentence_event_map.get(s);
					if (predicate_event_map == null) {
						bw.write("\n");
						continue;
					}
					// Need additional predicate id since the predicate index refers
					// to the headword in the sentence.
					// This was done so that the predicates would be ordered by location in the sentence
					// even if they were parsed in a different order.
					for (Integer hw_char_offset : predicate_event_map.keySet()) {
						RecipeEvent event = predicate_event_map.get(hw_char_offset);
						bw.write("PREDID: " + event.predicateIndexInSentence() + "\n");
						bw.write(event.toString() + "\n");
					}
					bw.write("\n");
				}
			}
			bw.close();
		}
	}

	/**
	 * Parses all the recipes in a directory and outputs the parsed versions
	 * to an associated directory.
	 * @throws IOException 
	 */
	public void parseAndOutputToFile() throws IOException {
		System.out.println("Parsing recipes in the category: " + category);

		// create category directory strings
		String input_directory_name = data_directory + category + "/" + category + ProjectParameters.STEP_SUFFIX + "/";
		List<File> input_files = Utils.getInputFiles(input_directory_name, "txt");
		// get fulltext files for the recipe lists
		String fulltext_directory_name = data_directory + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
		List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name,"txt");

		// get output directory, creating one if it does not already exists
		String output_directory_name = data_directory + category + "/" + category + ProjectParameters.CHUNKED_SUFFIX + "/";
		Utils.createOutputDirectoryIfNotExist(output_directory_name);

		for (int f = 0; f < input_files.size(); f++) {
			File input_file = null;
			File fulltext_file = null;
			try {
				input_file = input_files.get(f);
				fulltext_file = fulltext_files.get(f);
				if (!input_file.getName().equals(fulltext_file.getName())) {
					if (input_file.getName().compareTo(fulltext_file.getName()) < 0) {

					}
					System.out.println(input_file.getName() + " " + fulltext_file.getName());
					throw new IOException("Expecting parallel directories of step and fulltext files.");
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				throw new IOException("Expecting parallel directories of step and fulltext files.");
			}
//						if (input_file.getName().equals("southern-sauce.txt")) {
			parseFile(input_file, fulltext_file);
//						}	
		}

		try {
			printParsedArgumentsToFile(output_directory_name);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Getters and setters for the data directory
	//////////////////////////////////////////////////////
	public String getCurrentDataDirectory() {
		return data_directory;
	}

	public void setDataDirectory(String new_data_directory) {
		data_directory = new_data_directory;
	}

	// end getters and setters for data directory
	//////////////////////////////////////////////////////

	public static void main(String args[]) {
		RecipeArgParser rap = null;
		if (args.length == 0) {
			for (String dir : ProjectParameters.ALL_RECIPE_TYPES) {
//				if (!dir.equals("CheeseBurger")) {
//					continue;
//				}
				rap = new RecipeArgParser(dir);
				try {
					rap.parseAndOutputToFile();
				} catch (Exception ex) {
					ex.printStackTrace();
					System.exit(1);
				}
			}
			return;
		} else if (args.length > 1) {
			rap = new RecipeArgParser(args[0], args[1]);
		} else {
			rap = new RecipeArgParser(args[0]);
		}
		try {
			rap.parseAndOutputToFile();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}
}
