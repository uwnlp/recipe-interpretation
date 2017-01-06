package data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import preprocessing.Lemmatizer;
import model.SimpleValue;
import model.StringValue;
import model.Value;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;
import utils.Measurements;
import utils.Pair;
import utils.TokenCounter;
import utils.Triple;
import utils.Utils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Data structure for storing the flow of actions of a recipe.
 * 
 * The ActionDiagram is composed of a list of ActionNodes. Each
 * ActionNode contains a particular RecipeEvent and stores the destination
 * ActionNodes of the entities created by the action (as well as the origins of
 * different arguments).
 * 
 * The class contains methods to create, add to, and refine an ActionDiagram.
 * The class also has a method that creates an unconnected ActionDiagram (i.e., no
 * ActionNodes are connected) from the output file from RecipeArgParser.
 * 
 * @author chloe
 *
 */
public class ActionDiagram implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	/**
	 * Data structure for a node in the ActionDiagram.
	 * 
	 * @author chloe
	 *
	 */
	public static class ActionNode implements Comparable<ActionNode>, Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private RecipeEvent event_ = null;
		private TreeMap<String, TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>>> output_components_to_destinations_;
		public Map<Argument, Map<String, Pair<ActionNode, String>>> input_arg_spans_to_origins_;
		private Set<String> all_incorporated_entities_;
		private Set<String> incorporated_ingredients_;
		private Set<String> incorporated_noningredients_;
		private boolean has_ingredients_ = false;
		private int index_ = -1;
		private ActionDiagram par_;

		public static class Arity {

		}


		public ActionNode(RecipeEvent event, int index, ActionDiagram ad) {
			event_ = event;
			index_ = index;
			par_ = ad;
			output_components_to_destinations_ = new TreeMap<String, TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>>>();
			input_arg_spans_to_origins_ = new HashMap<Argument, Map<String, Pair<ActionNode, String>>>();
			all_incorporated_entities_ = new HashSet<String>();
			incorporated_ingredients_ = new HashSet<String>();
			incorporated_noningredients_ = new HashSet<String>();

			// Fill incorporated entities with ingredients and non-ingredients
			Argument dobj = event.dobj();
			if (dobj != null) {
				List<String> ingredient_spans = dobj.ingredientSpans();
				List<String> non_ingredient_spans = dobj.nonIngredientSpans();
				for (String span : non_ingredient_spans) {
					all_incorporated_entities_.add(span);
					incorporated_noningredients_.add(span);
				}
				for (String span : ingredient_spans) {
					Set<String> ingredients = dobj.ingredientsForSpan(span);
					for (String ingredient : ingredients) {
						List<String> overlap = Utils.getWordOverlap(ingredient, span);
						String overlap_string = StringUtils.join(overlap);
						all_incorporated_entities_.add(overlap_string);
						incorporated_ingredients_.add(overlap_string);
					}
					has_ingredients_ = true;
				}
			}
			Iterator<Argument> prep_it = event.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep_arg = prep_it.next();
				List<String> ingredient_spans = prep_arg.ingredientSpans();
				for (String span : ingredient_spans) {
					Set<String> ingredients = prep_arg.ingredientsForSpan(span);
					for (String ingredient : ingredients) {
						List<String> overlap = Utils.getWordOverlap(ingredient, span);
						String overlap_string = StringUtils.join(overlap);
						all_incorporated_entities_.add(overlap_string);
						incorporated_ingredients_.add(overlap_string);
					}
					has_ingredients_ = true;
				}
			}
		}

		public void updateEventEntities() {
			// Fill incorporated entities with ingredients and non-ingredients
			Argument dobj = event_.dobj();
			if (dobj != null) {
				List<String> ingredient_spans = dobj.ingredientSpans();
				List<String> non_ingredient_spans = dobj.nonIngredientSpans();
				for (String span : non_ingredient_spans) {
					all_incorporated_entities_.add(span);
					incorporated_noningredients_.add(span);
				}
				for (String span : ingredient_spans) {
					Set<String> ingredients = dobj.ingredientsForSpan(span);
					for (String ingredient : ingredients) {
						List<String> overlap = Utils.getWordOverlap(ingredient, span);
						String overlap_string = StringUtils.join(overlap);
						all_incorporated_entities_.add(overlap_string);
						incorporated_ingredients_.add(overlap_string);
					}
					has_ingredients_ = true;
				}
			}
			Iterator<Argument> prep_it = event_.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep_arg = prep_it.next();
				List<String> ingredient_spans = prep_arg.ingredientSpans();
				List<String> non_ingredient_spans = prep_arg.nonIngredientSpans();
				for (String span : ingredient_spans) {
					Set<String> ingredients = prep_arg.ingredientsForSpan(span);
					for (String ingredient : ingredients) {
						List<String> overlap = Utils.getWordOverlap(ingredient, span);
						String overlap_string = StringUtils.join(overlap);
						all_incorporated_entities_.add(overlap_string);
						incorporated_ingredients_.add(overlap_string);
					}
					has_ingredients_ = true;
				}
			}
		}

		public void clear() {
			output_components_to_destinations_.clear();
			input_arg_spans_to_origins_.clear();
			// TODO: figure out fixing the incorporated elements
		}

		public int index() {
			return index_;
		}

		public ActionDiagram actionDiagram() {
			return par_;
		}

		public Triple<Argument, String, Integer> bestDobjOrPrepArgumentOverlap(ActionNode other) {
			int best_count = 0;
			Argument best_argument = null;
			String best_span = null;

			// Check the direct object
			if (event_.dobj() != null) {
				List<String> non_ingredient_spans = event_.dobj().nonIngredientSpans();
				for (String span : non_ingredient_spans) {
					if (span.equals("")) {
						continue;
					}
					if (input_arg_spans_to_origins_.get(event_.dobj()) != null &&
							input_arg_spans_to_origins_.get(event_.dobj()).get(span) != null) {
						continue;
					}
					if (other.hasIngredients()) {
						for (String entity : other.incorporated_ingredients_) {
							int overlap = Utils.wordOverlapCountUseTokenizer(span, entity);
							if (overlap > best_count) {
								best_count = overlap;
								best_argument = event_.dobj();
								best_span = span;
							}
						}
						RecipeEvent other_event = other.event_;
						if (other_event.dobj() != null) {
							Argument dobj = other_event.dobj();
							List<String> dobj_noningredients = dobj.nonIngredientSpans();
							for (String dobj_ni : dobj_noningredients) {
								int overlap = Utils.wordOverlapCountUseTokenizer(span, dobj_ni);
								if (overlap > best_count) {
									best_count = overlap;
									best_argument = event_.dobj();
									best_span = span;
								}
							}
						}
					} else {
						for (String entity : other.all_incorporated_entities_) {
							int overlap = Utils.wordOverlapCountUseTokenizer(span, entity);
							if (overlap > best_count) {
								best_count = overlap;
								best_argument = event_.dobj();
								best_span = span;
							}
						}
					}
				}
			}

			// Check the prepositional arguments
			Iterator<Argument> prep_it = event_.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep_arg = prep_it.next();
				if (prep_arg.type() == Argument.Type.DURATION) {
					continue;
				}
				if (prep_arg.type() == Argument.Type.OTHER) {
					continue;
				}
				//				System.out.println(prep_arg + " " + prep_arg.nonIngredientSpans());
				List<String> non_ingredient_spans = prep_arg.nonIngredientSpans();
				for (String span : non_ingredient_spans) {
					if (span.equals("")) {
						continue;
					}

					if (input_arg_spans_to_origins_.get(prep_arg) != null &&
							input_arg_spans_to_origins_.get(prep_arg).get(span) != null) {
						System.out.println("found");
						continue;
					}
					//					System.out.println(other.has_ingredients_);
					if (other.hasIngredients()) {
						for (String entity : other.incorporated_ingredients_) {
							int overlap = Utils.wordOverlapCountUseTokenizer(span, entity);
							if (overlap > best_count) {
								best_count = overlap;
								best_argument = prep_arg;
								best_span = span;
							}
						}
						RecipeEvent other_event = other.event_;
						if (other_event.dobj() != null) {
							Argument dobj = other_event.dobj();
							List<String> dobj_noningredients = dobj.nonIngredientSpans();
							for (String dobj_ni : dobj_noningredients) {
								int overlap = Utils.wordOverlapCountUseTokenizer(span, dobj_ni);
								if (overlap > best_count) {
									best_count = overlap;
									best_argument = prep_arg;
									best_span = span;
								}
							}
						}
					} else {
						for (String entity : other.all_incorporated_entities_) {
							int overlap = Utils.wordOverlapCountUseTokenizer(span, entity);
							if (overlap > best_count) {
								best_count = overlap;
								best_argument = prep_arg;
								best_span = span;
							}
						}
					}
				}
			}

			return new Triple<Argument, String, Integer>(best_argument, best_span, best_count);
		}

		/**
		 * Updates the set of incorporated entities (i.e., argument string spans) based on
		 * the connection of an argument span to some origin ActionNode through evolution
		 * or implicit-ness.
		 * 
		 * @param origin  Node whose entities should be incorporated
		 */
		public void incorporate(ActionNode origin) {
			all_incorporated_entities_.addAll(origin.all_incorporated_entities_);
			has_ingredients_ = has_ingredients_ || origin.has_ingredients_;
			incorporated_ingredients_.addAll(origin.incorporated_ingredients_);
			incorporated_noningredients_.addAll(origin.incorporated_noningredients_);
			//			System.out.println(toString());
			//			System.out.println("ings: " + incorporated_ingredients_);
			//			System.out.println("nonings: " + incorporated_noningredients_);
		}

		//// Incorporated ingredients / non-ingredients iterators and mutators
		////////////////
		public Iterator<String> incorporated_entity_iterator() {
			return all_incorporated_entities_.iterator();
		}

		public void incorporateEntity(String entity) {
			all_incorporated_entities_.add(entity);
		}

		public void removeIncorporatedEntity(String entity) {
			all_incorporated_entities_.remove(entity);
		}

		public Iterator<String> incorporated_ingredients_iterator() {
			return incorporated_ingredients_.iterator();
		}

		public Set<String> incorporated_ingredients() {
			return new HashSet<String>(incorporated_ingredients_);
		}

		public Set<String> incorporated_noningredients() {
			return new HashSet<String>(incorporated_noningredients_);
		}

		public Set<String> incorporated_entities() {
			return all_incorporated_entities_;
		}

		public void incorporateIngredient(String ingredient) {
			has_ingredients_ = true;
			incorporated_ingredients_.add(ingredient);
			if (incorporated_noningredients_.contains(ingredient)) {
				incorporated_noningredients_.remove(ingredient);
			}
		}

		public void removeIncorporatedIngredient(String ingredient) {
			incorporated_ingredients_.remove(ingredient);
			if (incorporated_ingredients_.size() == 0) {
				has_ingredients_ = false;
			}
		}

		public Iterator<String> incorporated_noningredients_iterator() {
			return incorporated_noningredients_.iterator();
		}

		public void incorporateNonIngredient(String noningredient) {
			incorporated_noningredients_.add(noningredient);
		}

		public void removeIncorporatedNonIngredient(String noningredient) {
			incorporated_noningredients_.remove(noningredient);
		}
		////////////////


		public RecipeEvent event() {
			return event_;
		}

		public boolean hasIngredients() {
			return has_ingredients_;
		}

		public boolean hasConnectedOutput() {
			return output_components_to_destinations_.size() != 0;
		}

		public Pair<ActionNode, String> getOrigin(Argument arg, String span) {
			Map<String, Pair<ActionNode, String>> origins = input_arg_spans_to_origins_.get(arg);
			if (origins == null) {
				return null;
			}
			return origins.get(span);
		}

		public Iterator<String> outputEntityIterator() {
			return output_components_to_destinations_.keySet().iterator();
		}

		public int numDestinations() {
			int cnt = 0;
			for (String output : output_components_to_destinations_.keySet()) {
				Map<Integer, Set<Triple<ActionNode, Argument, String>>> dests = output_components_to_destinations_.get(output);
				for (Integer dest_id : dests.keySet()) {
					cnt += dests.get(dest_id).size();
				}
			}
			return cnt;
		}

		public TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> getDestinationsForEntity(String entity) {
			return output_components_to_destinations_.get(entity);
		}

		public void printDetss() {
			System.out.println(output_components_to_destinations_);
		}

		public Iterator<Argument> getImplicitAndEvolvedArgumentIterator() {
			return input_arg_spans_to_origins_.keySet().iterator();
		}

		public Iterator<String> getImplicitAndEvolvedStringSpanIteratorForArgument(Argument argument) {
			Map<String, Pair<ActionNode, String>> span_to_origin_map = input_arg_spans_to_origins_.get(argument);
			if (span_to_origin_map == null) {
				return null;
			}
			return span_to_origin_map.keySet().iterator();
		}

		@Override
		public int compareTo(ActionNode o) {
			return index_ - o.index_;
		}

		public String toString() {
			if (event() == null) {
				return "null" + index();
			}
			return event().predicate() + index();
		}

		public int hashCode() {
			return toString().hashCode();
		}
	}

	private Set<ActionNode> starting_nodes_;
	private List<ActionNode> node_list_;
	private Map<ActionNode, Integer> node_to_index_;
	private ActionNode end_result_;
	private String recipe_name_;
	private Set<String> ingredients_;
	private Set<ActionNode> output_ingredients_;
	private Set<String> categories_;

	public ActionDiagram(String recipe_name) {
		recipe_name_ = recipe_name;
		RecipeEvent end_event = new RecipeEvent("Bon appetit!", recipe_name, -1, -1);
		end_result_ = new ActionNode(end_event, -1, this);
		starting_nodes_ = new HashSet<ActionNode>();
		node_list_ = new ArrayList<ActionNode>();
		node_to_index_ = new HashMap<ActionNode, Integer>();
		ingredients_ = new HashSet<String>();
		output_ingredients_ = new HashSet<ActionNode>();
		categories_ = new HashSet<String>();
	}

	public Iterator<ActionNode> starting_nodes_iterator() {
		return starting_nodes_.iterator();
	}

	public String recipeName() {
		return recipe_name_;
	}

	public Iterator<String> category_iterator() {
		return categories_.iterator();
	}

	public void addCategory(String cat) {
		categories_.add(cat);
	}

	public void addIngredients(Set<String> ingredients) {
		ingredients_.addAll(ingredients);
	}

	public void addIngredient(String ingredient) {
		ingredients_.add(ingredient);
	}

	public Iterator<String> ingredient_iterator() {
		return ingredients_.iterator();
	}

	public Set<String> ingredientSetCopy() {
		return new HashSet<String>(ingredients_);
	}

	public Iterator<ActionNode> node_iterator() {
		return node_list_.iterator();
	}

	public Integer getIndex(ActionNode node) {
		return node_to_index_.get(node);
	}

	public ActionNode getNodeAtIndex(int index) {
		return node_list_.get(index);
	}

	public int numNodes() {
		return node_list_.size();
	}

	public void clear() {
		for (ActionNode node : node_list_) {
			node.clear();
			//			node.event_.clear();
		}
		starting_nodes_.addAll(node_list_);
		output_ingredients_.clear();
	}

	public void setIngredientFlow() {
		for (ActionNode node : node_list_) {
			System.out.println(node + " " + node.has_ingredients_);
			if (node.hasIngredients()) {
				output_ingredients_.add(node);
			} else {
				for (Argument arg : node.input_arg_spans_to_origins_.keySet()) {
					//					System.out.println(arg);
					Map<String, Pair<ActionNode, String>> origins = node.input_arg_spans_to_origins_.get(arg);
					for (String span : origins.keySet()) {
						Pair<ActionNode, String> origin = origins.get(span);
						if (output_ingredients_.contains(origin.getFirst())) {
							output_ingredients_.add(node);
							System.out.println(node + " contains ings");
							break;
						}
					}
					if (output_ingredients_.contains(node)) {
						break;
					}
				}
			}
		}
	}

	public boolean nodeOutputsIngredient(ActionNode node) {
		return output_ingredients_.contains(node);
	}

	/**
	 * Connects nodes in the ActionDiagram.
	 * 
	 * @param orig Origin ActionNode (i.e., governor node of the edge)
	 * @param dest Destination ActionNode (i.e., dependent node)
	 * @param arg Argument of dest that is the evolved form (or implicit form) of the origin ActionNode
	 * @param span String span of arg that is the evolved string
	 * @param output Name of the output entity of the origin node. (In most cases this will be an empty string.
	 * 			In the future we may extend this to split the entities that are ouputted by an action.)
	 */
	public void connectNodes(ActionNode orig, ActionNode dest, Argument arg, String span, String output) {
//				if (ProjectParameters.VERBOSE) {
		System.out.println("CONNECT: " + orig + " -> " + dest + " \"" + span + "\" " + arg.type() + " : " + arg);
//				}
		//			if (arg.type() == Type.OBJECT) {
		//				System.out.println(dest.event().dobj() + " " + arg);
		//			}

		//		if (ProjectParameters.INCORPORATE_WHEN_CONNECTING) {
		//			System.out.println("incorporating");
		//			dest.incorporate(orig);
		//			if (orig.has_ingredients_) {
		//				if (arg.type() == Argument.Type.LOCATION || arg.type() == Argument.Type.LOCOROBJ) {
		//					arg.setType(Argument.Type.COOBJECT);
		//				}
		//			}
		//		}

		// Destination node must be removed from starting nodes list (if still in list)
		starting_nodes_.remove(dest);

		Map<String, Pair<ActionNode, String>> span_to_origins = dest.input_arg_spans_to_origins_.get(arg);
		if (span_to_origins == null) {
			span_to_origins = new HashMap<String, Pair<ActionNode, String>>();
			dest.input_arg_spans_to_origins_.put(arg, span_to_origins);
		}
		if (span_to_origins.containsKey(span)) {
			System.out.println("ERROR: " + span + " is already spoken for." + arg + "  " + arg.type());
			try {
				throw new Exception();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
			}
			System.exit(1);
		} else {
			span_to_origins.put(span, new Pair<ActionNode, String>(orig, output));
			//			System.out.println(dest.input_arg_spans_to_origins_);
			//			System.out.println(arg + " ? " + arg.nonIngredientSpans());
			//			System.out.println(dest.input_arg_spans_to_origins_.get(arg));
			TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> destinations = orig.output_components_to_destinations_.get(output);
			if (destinations == null) {
				destinations = new TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>>();
				orig.output_components_to_destinations_.put(output, destinations);
			}
			Set<Triple<ActionNode, Argument, String>> dest_set = destinations.get(dest.index());
			if (dest_set == null) {
				dest_set = new HashSet<Triple<ActionNode, Argument, String>>();
				destinations.put(dest.index(), dest_set);
			}
			dest_set.add(new Triple<ActionNode, Argument, String>(dest, arg, span));
		}

		if (ProjectParameters.INCORPORATE_WHEN_CONNECTING) {
			//			System.out.println("incorporating");
			dest.incorporate(orig);
			//			if (orig.has_ingredients_) {
			//				if (arg.type() == Argument.Type.LOCATION || arg.type() == Argument.Type.LOCOROBJ) {
			//					arg.setType(Argument.Type.COOBJECT);
			//				}
			//			}
		}
	}

	/**
	 * Create an ActionNode in the ActionDiagram that represents the inputted event.
	 * 
	 * @param event input RecipeEvent to be represented in the diagram
	 * @return the new ActionNode that was created
	 */
	public ActionNode addEvent(RecipeEvent event) {
		int index = node_list_.size();
		ActionNode new_action = new ActionNode(event, index, this);
		node_to_index_.put(new_action, index);
		node_list_.add(new_action);

		// Must add to starting events since it is not connected to anything.
		starting_nodes_.add(new_action);
		return new_action;
	}

	private static String cleanSplitSentence(String sent) {
		sent = sent.trim();
		if (sent.endsWith(",") || sent.endsWith(";")) {
			return sent.substring(0, sent.length() - 1) + ".";
		} else if (sent.endsWith(", and")) {
			return sent.substring(0, sent.length() - 5) + ".";
		} else if (sent.endsWith(" and")) {
			return sent.substring(0, sent.length() - 4) + ".";
		}
		return sent;
	}

	
	public static ActionDiagram generateNaiveActionDiagramFromPredArgMap(RecipeSentenceSegmenter chunker, File step_file,
			File fulltext_file) {
		String recipe_name = step_file.getName();
		ActionDiagram ad = new ActionDiagram(recipe_name);


		try {
			Set<String> recipe_ingredients = IngredientParser.parseIngredientsFromFullTextFile(fulltext_file);
			ad.addIngredients(recipe_ingredients);

			List<Pair<String, List<Pair<String, List<String>>>>> pred_args = chunker.getPredArgStructure(step_file);
			int sent_idx = 0;
			for (Pair<String, List<Pair<String, List<String>>>> sent_pair : pred_args) {
				String sentence_str = sent_pair.getFirst();
				List<Pair<String, List<String>>> sent_pred_args = sent_pair.getSecond();
				int pred_idx = 0;
				for (Pair<String, List<String>> pair : sent_pred_args) {
					String pred = pair.getFirst();
					List<String> args = pair.getSecond();
					RecipeEvent event = new RecipeEvent(pred, recipe_name, sent_idx, pred_idx, sentence_str);
					String poss_dobj = null;
					for (String arg : args) {
						int space = arg.indexOf(' ');
						if (space == -1) {
							List<String> tags = Utils.lemmatizer_.tag(arg);
							if (tags.size() == 0 || tags.get(0).equals("RB")) {
								Argument a = event.addOtherArgument(arg);
								String[] split = arg.split(",| and ");
								for (String span : split) {
									if (!span.trim().equals("")) {
										a.addNonIngredientSpan(span);
									}
								}
								// do nothing
							} else {
								if (poss_dobj == null) {
									poss_dobj = arg;
								} else {
									if (poss_dobj.endsWith("ly") || poss_dobj.contains("ly ")) {
										if (arg.endsWith("ly") || arg.contains("ly ")) {
											if (poss_dobj.length() < arg.length()) {
												Argument a = event.addOtherArgument(poss_dobj);
												String[] split = poss_dobj.split(",| and ");
												for (String span : split) {
													if (!span.trim().equals("")) {
														a.addNonIngredientSpan(span);
													}
												}
												poss_dobj = arg;
											}
										} else {
											Argument a = event.addOtherArgument(poss_dobj);
											String[] split = poss_dobj.split(",| and ");
											for (String span : split) {
												if (!span.trim().equals("")) {
													a.addNonIngredientSpan(span);
												}
											}
											poss_dobj = arg;
										}
									} else {
										if (arg.endsWith("ly") || arg.contains("ly ")) {
											// do nothing
											Argument a = event.addOtherArgument(arg);
											String[] split = arg.split(",| and ");
											for (String span : split) {
												if (!span.trim().equals("")) {
													a.addNonIngredientSpan(span);
												}
											}
										} else {
											if (poss_dobj.length() < arg.length()) {
												Argument a = event.addOtherArgument(poss_dobj);
												String[] split = poss_dobj.split(",| and ");
												for (String span : split) {
													if (!span.trim().equals("")) {
														a.addNonIngredientSpan(span);
													}
												}
												poss_dobj = arg;
											}
										}
									}
								}
							}
						} else {
							String first = arg.substring(0, space);
							List<String> tags = Utils.lemmatizer_.tag(first);
							if (tags.get(0).matches("IN|TO")) {
								if (tags.get(0).matches("TO")) {
									String prep_str = arg.substring(space + 1);
									int second_space = prep_str.indexOf(' ');
									String next_word = null;
									if (second_space == -1) {
										next_word = prep_str;
									} else {
										next_word = prep_str.substring(0, second_space);
									}
									List<String> ts = Utils.lemmatizer_.tag(next_word);
									//								System.out.println(next_word + " " +  ts);
									if (ts.get(0).matches("RB|VB") && !Utils.isMostlyAdjectiveInWordNet(next_word)
											&& !Utils.isMostlyNounInWordNet(next_word)) {
										Argument a = event.addOtherArgument(arg);
										String[] split = arg.split(",| and ");
										for (String span : split) {
											if (!span.trim().equals("")) {
												a.addNonIngredientSpan(span);
											}
										}
										System.out.println(next_word + " " +  ts);
										System.out.println(arg);
									} else {
										Argument a = event.addPrepositionalArgument(prep_str, first);
										String[] split = prep_str.split(",| and ");
										for (String span : split) {
											if (!span.trim().equals("")) {
												a.addNonIngredientSpan(span);
											}
										}
									}
								} else {
									String prep_str = arg.substring(space + 1);
									Argument a = event.addPrepositionalArgument(prep_str, first);
									String[] split = prep_str.split(",| and ");
									for (String span : split) {
										if (!span.trim().equals("")) {
											a.addNonIngredientSpan(span);
										}
									}
								}
							} else if (tags.get(0).startsWith("W")) {
								Argument a = event.addOtherArgument(arg);
								String[] split = arg.split(",| and ");
								for (String span : split) {
									if (!span.trim().equals("")) {
										a.addNonIngredientSpan(span);
									}
								}
							} else {
								if (arg.contains(" minutes ") || arg.contains(" hours ")  ||
										arg.contains(" hour ") || arg.contains(" minute ") ||
										arg.endsWith(" minutes") || arg.endsWith(" minute") ||
										arg.endsWith(" hours") || arg.endsWith(" hour") ||
										arg.endsWith(" seconds")) {
									Argument a = event.addOtherArgument(arg);
									String[] split = arg.split(",| and ");
									for (String span : split) {
										if (!span.trim().equals("")) {
											a.addNonIngredientSpan(span);
										}
									}
								} else {
									if (poss_dobj == null) {
										poss_dobj = arg;
									} else {
										if (poss_dobj.endsWith("ly") || poss_dobj.contains("ly ")) {
											if (arg.endsWith("ly") || arg.contains("ly ")) {
												if (poss_dobj.length() < arg.length()) {
													Argument a = event.addOtherArgument(poss_dobj);
													String[] split = poss_dobj.split(",| and ");
													for (String span : split) {
														if (!span.trim().equals("")) {
															a.addNonIngredientSpan(span);
														}
													}
													poss_dobj = arg;
												}
											} else {
												Argument a = event.addOtherArgument(poss_dobj);
												String[] split = poss_dobj.split(",| and ");
												for (String span : split) {
													if (!span.trim().equals("")) {
														a.addNonIngredientSpan(span);
													}
												}
												poss_dobj = arg;
											}
										} else {
											if (arg.endsWith("ly") || arg.contains("ly ")) {
												Argument a = event.addOtherArgument(arg);
												String[] split = arg.split(",| and ");
												for (String span : split) {
													if (!span.trim().equals("")) {
														a.addNonIngredientSpan(span);
													}
												}
												// do nothing
											} else {
												if (poss_dobj.length() < arg.length()) {
													Argument a = event.addOtherArgument(poss_dobj);
													String[] split = poss_dobj.split(",| and ");
													for (String span : split) {
														if (!span.trim().equals("")) {
															a.addNonIngredientSpan(span);
														}
													}
													poss_dobj = arg;
												}
											}
										}
									}
								}
							}
						}
					}
					if (poss_dobj != null) {
						Argument a = event.setDirectObject(poss_dobj);
						String[] split = poss_dobj.split(",| and ");
						for (String span : split) {
							if (!span.trim().equals("")) {
								a.addNonIngredientSpan(span);
							}
						}
					}
					event.dobj();
					ad.addEvent(event);
					pred_idx++;
				}
				sent_idx++;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		return ad;
	}

	public int num_sentences = 0;


	/**
	 * Generates an ActionDiagram from a file created by the RecipeArgParser.
	 * 
	 * @param arg_file file created by RecipeArgParser
	 * @return the ActionDiagram created by parsing arg_file
	 */
	public static ActionDiagram generateNaiveActionDiagramFromFile(File arg_file, File fulltext_file, boolean use_ingredient_annotations, boolean use_tokenizer) {
		String recipe_name = arg_file.getName();
		ActionDiagram ad = new ActionDiagram(recipe_name);


		try {
			Set<String> recipe_ingredients = IngredientParser.parseIngredientsFromFullTextFile(fulltext_file);
			ad.addIngredients(recipe_ingredients);

			BufferedReader br = new BufferedReader(new FileReader(arg_file));
			String line;
			String sent_str = "";
			int sent_id = 0;
			int pred_id = 0;
			RecipeEvent event = null;
			Argument arg = null;
			boolean location = false;
			boolean coobject = false;

			boolean new_sent = false;
			String prev_sent = "";

			int num_lines = 0;
			while ((line = br.readLine()) != null) {
				if (line.length() == 0) {
					continue;
				}
				int colon_idx = line.indexOf(':');
				if (colon_idx == -1) {
					throw new ParseException("Improper line: " + line, num_lines);
				}
				String type = line.substring(0, colon_idx).trim();
				String data = line.substring(colon_idx + 2);
				if (type.equals("SENTID")) {
					ad.num_sentences++;
					//					if (new_sent) {
					//						System.out.println(prev_sent);
					//					}
					// if pred is not null, add previous predicate to diagram
					if (event != null) {
						ad.addEvent(event);
						event = null;
					}
					sent_id = Integer.parseInt(data);
				} else if (type.equals("SENT")) {
					new_sent = true;
					sent_str = data;
					prev_sent = data;
					continue;
				} else if (type.equals("PREDID")) {
					if (event != null) {
						ad.addEvent(event);
						event = null;
						location = false;
						coobject = false;
					}
					pred_id = Integer.parseInt(data);
				} else if (type.equals("PRED")) {
					if (!new_sent) {
						int pred_index = sent_str.toLowerCase().indexOf(data + " ");
						if (pred_index == 0) {
							pred_index = sent_str.toLowerCase().indexOf(data + " ", 1);
						}
						if (pred_index == -1) {
							pred_index = sent_str.toLowerCase().indexOf(data + ".");
						}
						if (pred_index == -1) {
							pred_index = sent_str.toLowerCase().indexOf(data + ",");
						}
						if (pred_index == -1) {
							pred_index = sent_str.toLowerCase().indexOf(data);
						}
						//						System.out.println(sent_str);
						//						System.out.println(data);
						String old_sent_str;
						if (pred_index == -1) {
							old_sent_str = new String(sent_str);
						} else {
							old_sent_str = sent_str.substring(0, pred_index - 1);
						}
						ad.getNodeAtIndex(ad.numNodes() - 1).event().setSentenceString(cleanSplitSentence(old_sent_str));
						if (pred_index == -1) {
						} else {
							sent_str = sent_str.substring(pred_index);
						}
					}
					new_sent = false;
					event = new RecipeEvent(data, recipe_name, sent_id, pred_id, sent_str);
				} else if (type.equals("DOBJ")) {
					if (!data.equals("NULL")) {
						int space = data.indexOf(' ');
						String word = data;
						if (space != -1) {
							word = data.substring(0, space);
						}
						if (data.contains(" minutes ") || data.contains(" hours ")  ||
								data.contains(" hour ") || data.contains(" minute ") ||
								data.endsWith(" minutes") || data.endsWith(" minute") ||
								data.endsWith(" hours") || data.endsWith(" hour") ||
								data.endsWith(" seconds")) {
							arg = event.addOtherArgument(data);
							String[] split = data.split(",| and ");
							for (String span : split) {
								if (!span.trim().equals("")) {
									arg.addNonIngredientSpan(span);
								}
							}
							//						} else if (Utils.isAdverb(word)) {
							//							arg = event.addOtherArgument(data);
							//							String[] split = data.split(",| and ");
							//							for (String span : split) {
							//								if (!span.trim().equals("")) {
							//									arg.addNonIngredientSpan(span);
							//								}
							//							}
						} else {
							arg = event.setDirectObject(data);
							if (!use_ingredient_annotations) {
								//							System.out.println("DOBJ: " + data);
								String[] split = data.split(",| and ");
								for (String span : split) {
									if (!span.trim().equals("")) {
										arg.addNonIngredientSpan(span);
									}
								}
								if (use_tokenizer) {
									TokenCounter.addTokens(data);
								}
							}
						}
					}
				} else if (type.equals("PARG")) {
					line = br.readLine();
					int colon_idx2 = line.indexOf(':');
					if (colon_idx2 == -1) {
						throw new ParseException("Improper line: " + line, num_lines);
					}
					String data2 = line.substring(colon_idx2 + 2);
					arg = event.addPrepositionalArgument(data, data2);
					if (arg.type() == Argument.Type.LOCATION) {
						location = true;
					} else if (arg.type() == Argument.Type.COOBJECT) {
						coobject = true;
					}
					//					if (arg.type() == Type.DURATION) {
					//						arg.setType(Type.LOCOROBJ);
					//					}
					if (!use_ingredient_annotations) {
						String[] split = data.split(",| and ");
						for (String span : split) {
							if (!span.trim().equals("")) {
								arg.addNonIngredientSpan(span);
							}
						}
						if (use_tokenizer) {
							TokenCounter.addTokens(data);
						}
					}
				} else if (type.equals("OARG")) {
					//					List<String> tags = TokenCounter.lemmatizer_.tag(data);
					////										System.out.println(data + " " + tags);
					//					if (tags.size() > 2 && tags.get(0).equals("IN")) {
					//						arg = event.addPrepositionalArgument(data);
					//					} else if (tags.size() > 2 && event.dobj() == null && (tags.get(0).startsWith("N") || tags.get(0).startsWith("J") || tags.get(0).equals("DT"))) {
					//						arg = event.setDirectObject(data);
					//					} else {
					arg = event.addOtherArgument(data);	
					//					}
					if (!use_ingredient_annotations) {
						String[] split = data.split(",| and ");
						for (String span : split) {
							if (!span.trim().equals("")) {
								arg.addNonIngredientSpan(span);
							}
						}
						if (use_tokenizer) {
							TokenCounter.addTokens(data);
						}
					}
				} else if (type.equals("INGREDIENT SPAN")) {
					line = br.readLine();
					if (arg == null || !use_ingredient_annotations) {
						continue;
					}
					int colon_idx2 = line.indexOf(':');
					if (colon_idx2 == -1) {
						throw new ParseException("Improper line: " + line, num_lines);
					}
					String data2 = line.substring(colon_idx2 + 2);
					String[] ingredients = data2.split(", ");
					for (String ingredient : ingredients) {
						arg.addIngredient(ingredient, data);
						if (use_tokenizer) {
							TokenCounter.addTokens(data);
						}
					}
				} else if (type.equals("NON-INGREDIENT SPAN")) {
					if (arg != null && use_ingredient_annotations) {
						arg.addNonIngredientSpan(data);
						if (use_tokenizer) {
							TokenCounter.addTokens(data);
						}
					}
				}
				num_lines++;
			}
			if (event != null) {
				ad.addEvent(event);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (ParseException e) {
			e.printStackTrace();
			System.exit(1);
		}

		return ad;
	}

	// FUNCTIONS FOR PRINTING A .dot FILE
	/////////////////////////////////////////
	
	private static String getNodeString(String node_type, String node_idx, String label, String dot_specification) {
		return node_type + node_idx + "[label=\"" + label + "\", " + dot_specification + "]\n";
	}

	private void printStartingNode(ActionNode start_node, BufferedWriter bw) throws IOException {
		Integer idx = node_to_index_.get(start_node);
		RecipeEvent event = start_node.event_;
		Argument dobj = event.dobj();
		if (dobj != null) {
			if (dobj.string().length() != 0) {
				List<String> ingredient_spans = dobj.ingredientSpans();
				if (ingredient_spans.size() == 0) {
					bw.write(getNodeString("D", Integer.toString(idx), "DOBJ: " + dobj.string(), ProjectParameters.OTHER_INPUT_DOT_SPECIFICATION));
				} else {
					bw.write(getNodeString("D", Integer.toString(idx), "DOBJ: " + StringUtils.join(dobj.ingredients(), ", "), ProjectParameters.INGREDIENT_DOT_SPECIFICATION));
				}
				bw.write("D" + idx + " -> " + "E" + idx + "\n");
			}
		}
		Iterator<Argument> prep_it = event.prepositionalArgIterator();
		int prep_idx = 0;
		while (prep_it.hasNext()) {
			Argument prep_arg = prep_it.next();
			if (prep_arg.string().length() == 0) {
				continue;
			}
			List<String> ingredient_spans = prep_arg.ingredientSpans();
			String prep_id = Integer.toString(idx) + "_" +  prep_idx;
			if (ingredient_spans.size() == 0) {
				bw.write(getNodeString("P", prep_id, prep_arg.type() + ":" + prep_arg.preposition() + " " + prep_arg.string(), ProjectParameters.OTHER_INPUT_DOT_SPECIFICATION));
			} else {
				bw.write(getNodeString("P", prep_id, prep_arg.type() + ":" + prep_arg.preposition() + " " + StringUtils.join(prep_arg.ingredients(), ", "),
						ProjectParameters.INGREDIENT_DOT_SPECIFICATION));
			}
			bw.write("P" + prep_id + " -> " + "E" + idx + "\n");
			prep_idx++;
		}

		Iterator<Argument> other_it = event.otherArgIterator();
		int other_idx = 0;
		while (other_it.hasNext()) {
			Argument other_arg = other_it.next();
			if (other_arg.string().length() == 0) {
				continue;
			}
			List<String> ingredient_spans = other_arg.ingredientSpans();
			String other_id = Integer.toString(idx) + "_" +  other_idx;
			if (ingredient_spans.size() == 0) {
				bw.write(getNodeString("O", other_id, "OTHER: " + other_arg.string(), ProjectParameters.OTHER_INPUT_DOT_SPECIFICATION));
			} else {
				bw.write(getNodeString("O", other_id, "OTHER: " + StringUtils.join(other_arg.ingredients(), ", "), ProjectParameters.INGREDIENT_DOT_SPECIFICATION));
			}
			bw.write("O" + other_id + " -> " + "E" + idx + "\n");
			other_idx++;
		}
	}

	private void printMiddleNode(ActionNode node, int i, BufferedWriter bw) throws IOException {
		RecipeEvent event = node.event();
		Argument dobj = event.dobj();
		if (dobj != null) {
			List<String> ingredient_spans = dobj.ingredientSpans();
			if (ingredient_spans.size() != 0) {
				bw.write(getNodeString("D", Integer.toString(i) + "_ing", "DOBJ: " + StringUtils.join(dobj.ingredients(), ", "), ProjectParameters.INGREDIENT_DOT_SPECIFICATION));
				bw.write("D" + i + "_ing" + " -> " + "E" + i + "\n");
			}
			Map<String, Pair<ActionNode, String>> orig_map = node.input_arg_spans_to_origins_.get(dobj);
			int span_id = 0;
			
			for (String span : dobj.nonIngredientSpans()) {
				Pair<ActionNode, String> orig = null;
				if (orig_map != null) {
					orig = orig_map.get(span);
				}
				if (orig == null) {
					if (span.length() == 0) {
						continue;
					}
					bw.write(getNodeString("D", Integer.toString(i) + "_" + span_id, "DOBJ: " + span, ProjectParameters.OTHER_INPUT_DOT_SPECIFICATION));
					bw.write("D" + i + "_" + span_id + " -> " + "E" + i + "\n");
				} else {

					ActionNode orig_node = orig.getFirst();
					Integer orig_idx = node_to_index_.get(orig_node);
					if (span.trim().equals("")) {
						bw.write("E" + orig_idx.intValue() + " -> " + "E" + i + 
								" [label=\"IMPLICIT DOBJ\"]\n");
					} else {
						bw.write("E" + orig_idx.intValue() + " -> " + "E" + i + 
								" [label=\"" + span + "\"]\n");
					}
				} 
				span_id++;
			}
			//			}
		}
		int prep_idx = 0;
		Iterator<Argument> prep_it = event.prepositionalArgIterator();
		while (prep_it.hasNext()) {
			Argument prep_arg = prep_it.next();
			String prep_id = Integer.toString(i) + "_" +  prep_idx;
			List<String> ingredient_spans = prep_arg.ingredientSpans();
			if (ingredient_spans.size() != 0) {
				bw.write(getNodeString("P", prep_id + "_ing", prep_arg.type() + ": " + prep_arg.preposition() + " "
						+ StringUtils.join(prep_arg.ingredients(), ", "), ProjectParameters.INGREDIENT_DOT_SPECIFICATION));
				bw.write("P" + prep_id + "_ing" + " -> " + "E" + i + "\n");
			}
			Map<String, Pair<ActionNode, String>> orig_map = node.input_arg_spans_to_origins_.get(prep_arg);
			int span_id = 0;
			for (String span : prep_arg.nonIngredientSpans()) {
				Pair<ActionNode, String> orig = null;
				if (orig_map != null) {
					orig = orig_map.get(span);
				}
				if (orig == null) {
					if (span.length() == 0) {
						continue;
					}
					bw.write(getNodeString("P", prep_id + "_" + span_id, prep_arg.type() + ":" + prep_arg.preposition() + 
							" " + span, ProjectParameters.OTHER_INPUT_DOT_SPECIFICATION));
					bw.write("P" + prep_id + "_" + span_id + " -> " + "E" + i + "\n");
				} else {
					ActionNode orig_node = orig.getFirst();
					Integer orig_idx = node_to_index_.get(orig_node);
					if (span.trim().equals("")) {
						bw.write("E" + orig_idx.intValue() + " -> " + "E" + i + 
								" [label=\"IMPLICIT PREP\"]\n");
					} else {
						bw.write("E" + orig_idx.intValue() + " -> " + "E" + i + 
								" [label=\"" + prep_arg.type() + ":" + prep_arg.preposition() + " " + span + "\"]\n");
					}
				} 
				span_id++;
			}
			prep_idx++;
		}
		int other_idx = 0;
		Iterator<Argument> other_it = event.otherArgIterator();
		while (other_it.hasNext()) {
			Argument other_arg = other_it.next();
			String other_id = Integer.toString(i) + "_" +  other_idx;
			List<String> ingredient_spans = other_arg.ingredientSpans();
			if (ingredient_spans.size() != 0) {
				bw.write(getNodeString("O", other_id + "_ing", "OTHER: "
						+ StringUtils.join(other_arg.ingredients(), ", "),
						ProjectParameters.INGREDIENT_DOT_SPECIFICATION));
				bw.write("O" + other_id + "_ing" + " -> " + "E" + i + "\n");
			}
			Map<String, Pair<ActionNode, String>> orig_map = node.input_arg_spans_to_origins_.get(other_arg);
			int span_id = 0;
			for (String span : other_arg.nonIngredientSpans()) {
				Pair<ActionNode, String> orig = null;
				if (orig_map != null) {
					orig = orig_map.get(span);
				}
				if (orig == null) {
					if (span.length() == 0) {
						continue;
					}
					bw.write(getNodeString("O", other_id + "_" + span_id, "OTHER: " 
							+ span, ProjectParameters.OTHER_INPUT_DOT_SPECIFICATION));
					bw.write("O" + other_id + "_" + span_id + " -> " + "E" + i + "\n");
				} else {
					ActionNode orig_node = orig.getFirst();
					Integer orig_idx = node_to_index_.get(orig_node);
					if (span.trim().equals("")) {
						bw.write("E" + orig_idx.intValue() + " -> " + "E" + i + 
								" [label=\"IMPLICIT OTHER\"]\n");
					} else {
						bw.write("E" + orig_idx.intValue() + " -> " + "E" + i + 
								" [label=\"" + other_arg.type() + ":" + span + "\"]\n");
					}
				} 
				span_id++;
			}
			other_idx++;
		}
	}

	/**
	 * Prints the ActionDiagram to a file that can be processed externally by dot.
	 * 
	 * @param output_filename Name of the output file
	 */
	public void printDotFile(String output_filename) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(output_filename));
			bw.write("digraph recipe {\n");
			for (int e = 0; e < node_list_.size(); e++) {
				ActionNode node = node_list_.get(e);
				RecipeEvent event = node.event_;
				bw.write(getNodeString("E", Integer.toString(e), event.getFullPredicateString() + "\n" + e, ProjectParameters.EVENT_NODE_DOT_SPECIFICATION));
			}
			for (ActionNode start_node : starting_nodes_) {
				printStartingNode(start_node, bw);
			}
			for (int i = 0; i < node_list_.size(); i++) {
				ActionNode node = node_list_.get(i);
				if (i != node_list_.size() - 1) {
					if (!node.outputEntityIterator().hasNext()) {
						System.out.println("UNCONNECTED ERROR " + node);
						//						System.exit(1);
					}
				}
				if (starting_nodes_.contains(node)) {
					continue;
				}
				printMiddleNode(node, i, bw);
			}
			bw.write(getNodeString("EOR", "", end_result_.event_.predicate(), ProjectParameters.END_NODE_DOT_SPECIFICATION));
			bw.write("E" + (node_list_.size() - 1) + " -> EOR\n");
			bw.write("}\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
