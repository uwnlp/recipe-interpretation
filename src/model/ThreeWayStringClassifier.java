package model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import model.SelectionalPreferenceModel.SelectionalPreference;
import utils.IntPair;
import utils.Measurements;
import utils.Pair;
import utils.TokenCounter;
import utils.Utils;
import data.ActionDiagram;
import data.RecipeSentenceSegmenter;
import data.IngredientParser;
import data.ProjectParameters;
import data.RecipeEvent;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;

public class ThreeWayStringClassifier {

	public Set<String> recipe_blacklist_;

	public Set<String> predicate_tokens_;
	public Set<String> tokens_;
	public Map<String, Integer> ing_tkn_counts_;
	public int num_tkns_in_ing_phrases_ = 0;
	public int num_ing_phrases_ = 0;
	public Map<String, Integer> noning_tkn_counts_;
	public int num_tkns_in_noning_phrases_ = 0;
	public int num_noning_phrases_ = 0;
	public Map<String, Integer> loc_tkn_counts_;
	public int num_tkns_in_loc_phrases_ = 0;
	public int num_loc_phrases_ = 0;
	
	public Map<String, Integer> verb_counts_;
	public Map<String, Map<String, Integer>> verb_to_location_counts_;
	public Map<String, Map<String, Integer>> verb_to_ing_counts_;

	public Map<String, Integer> raw_ing_tkn_counts_;
	public Map<String, Integer> mix_ing_tkn_counts_;
	public double alpha = 0.00000000001;

	public ThreeWayStringClassifier() {

		predicate_tokens_ = new HashSet<String>();
		tokens_ = new HashSet<String>();
		ing_tkn_counts_ = new HashMap<String, Integer>();
		noning_tkn_counts_ = new HashMap<String, Integer>();
		loc_tkn_counts_ = new HashMap<String, Integer>();
		raw_ing_tkn_counts_ = new HashMap<String, Integer>();
		mix_ing_tkn_counts_ = new HashMap<String, Integer>();

		verb_to_location_counts_ = new HashMap<String, Map<String, Integer>>();
		verb_counts_ = new HashMap<String, Integer>();

		
//		try {
//			 chunker = Chunker.readInFromFile("/Users/chloe/Research/AllRecipes_20_Args/chunker1.model");
//		} catch (Exception ex) {
//			ex.printStackTrace();
//			System.exit(1);
//		}
	}

	private boolean initialized = false;
	
	public double lprobPhraseGivenIng(String phrase, boolean stem) {
		double lprob = 0.0;
		String[] tokens = phrase.split(" ");
		double denom = -1 * Math.log((alpha*tokens_.size()) + num_tkns_in_ing_phrases_);
		for (String token : tokens) {
			if (stem) {
				token = Utils.stem(token);
			}
//			System.out.println(tokens_.contains(token) + " " + token);
			if (!tokens_.contains(token)) {
				continue;
			}
			if (token.length() < 3) {
				continue;
			}
			Integer cnt = ing_tkn_counts_.get(token);
//			System.out.println(token + " " + cnt);
			if (cnt == null) {
				lprob += Math.log(alpha) + denom;
			} else {
				lprob += Math.log(alpha + cnt.doubleValue()) + denom;
			}
		}
		return lprob;
	}
	
	public boolean isPhraseMoreLikelyToBeIng(String phrase, boolean all_else_even, boolean stem) {
		double ing_prob = lprobPhraseGivenIng(phrase, stem);
		double noning_prob = lprobPhraseGivenNonIng(phrase, stem);
		double loc_prob = lprobPhraseGivenLoc(phrase, stem);
//		System.out.println(Utils.stem(phrase) + " " + ing_prob + " " + noning_prob + " " + loc_prob);
		if (ing_prob == 0.0) {
			return false;
		}
		if (ing_prob >= loc_prob) {
			return true;
		}
		return false;
	}
	
	public boolean isPhraseMoreLikelyToBeLoc(String phrase, boolean all_else_even, boolean stem) {
		double ing_prob = lprobPhraseGivenIng(phrase, stem);
		double noning_prob = lprobPhraseGivenNonIng(phrase, stem);
		double loc_prob = lprobPhraseGivenLoc(phrase, stem);
		if (ing_prob == 0.0) {
			return false;
		}
		if (loc_prob > ing_prob) {
			return true;
		}
		return false;
	}
	
	public double LocOverlap(String predicate, Set<String> phrase, boolean stem) {
		double lprob = 0.0;
		Map<String, Integer> loc_cnts = verb_to_location_counts_.get(predicate);
		if (loc_cnts == null) {
			return Math.log(0.00000001);
		}
		Integer ttl_cnt = verb_counts_.get(predicate);
		int max_cnt = 0;
		for (String tkn : phrase) {
			if (stem) {
				tkn = Utils.stem(tkn);
			}
			Integer cnt = loc_cnts.get(tkn);
			if (cnt != null && cnt > max_cnt) {
				max_cnt = cnt;
			}
		}
		if (max_cnt == 0) {
			return Math.log(0.00000001);
		}
		return Math.log(max_cnt) - Math.log(ttl_cnt);
	}
	
	public double lprobTknsGivenLoc(String predicate, Set<String> phrase, boolean stem) {
		double lprob = 0.0;
		if (verb_to_location_counts_.size() == 0) {
			return Math.log(0.00000001);
		}
		Map<String, Integer> loc_cnts = verb_to_location_counts_.get(predicate);
		if (loc_cnts == null) {
			return Math.log(0.00000001);
		}
		Integer ttl_cnt = verb_counts_.get(predicate);
		double denom = -1 * Math.log(ttl_cnt + alpha*(loc_cnts.size() + 1));
		for (String tkn : phrase) {
			if (stem) {
				tkn = Utils.stem(tkn);
			}
			Integer cnt = loc_cnts.get(tkn);
			if (cnt == null) {
				lprob += Math.log(alpha) + denom;
			} else {
				lprob += Math.log(alpha + cnt.intValue()) + denom;
			}
		}
		return lprob;
	}
	
	public double lprobPhraseGivenNonIng(String phrase, boolean stem) {
		double lprob = 0.0;
		String[] tokens = phrase.split(" ");
		double denom = -1 * Math.log((alpha*tokens_.size()) + num_tkns_in_noning_phrases_);
		for (String token : tokens) {
			if (stem) {
				token = Utils.stem(token);
			}
			if (!tokens_.contains(token)) {
				continue;
			}
			if (token.length() < 3) {
				continue;
			}
			Integer cnt = noning_tkn_counts_.get(token);
//			System.out.println(token + " " + cnt);
			if (cnt == null) {
				lprob += Math.log(alpha) + denom;
			} else {
				lprob += Math.log(alpha + cnt.doubleValue()) + denom;
			}
		}
		return lprob;
	}
	
	public double lprobPhraseGivenRaw(String phrase, boolean stem) {
		double lprob = 0.0;
		String[] tokens = phrase.split(" ");
		double denom = -1 * Math.log((alpha*tokens_.size()) + total_raw_cnt);
		for (String token : tokens) {
			if (!Utils.isMostlyNounInWordNet(token)) {
				continue;
			}
			if (stem) {
				token = Utils.stem(token);
			}
			if (!tokens_.contains(token)) {
				continue;
			}
			if (token.length() < 3) {
				continue;
			}
			Integer cnt = raw_ing_tkn_counts_.get(token);
//			System.out.println(token + " " + cnt);
			if (cnt == null) {
				lprob += Math.log(alpha) + denom;
//				return Double.NEGATIVE_INFINITY;
			} else {
				lprob += Math.log(alpha + cnt.doubleValue()) + denom;
//				lprob += Math.log(cnt.doubleValue()) + denom;
			}
		}
		return lprob;
	}
	
	public double lprobPhraseGivenMix(String phrase, boolean stem) {
		double lprob = 0.0;
		String[] tokens = phrase.split(" ");
		double denom = -1 * Math.log((alpha*tokens_.size()) + total_raw_cnt);
		for (String token : tokens) {
			if (!Utils.isMostlyNounInWordNet(token)) {
				continue;
			}
			if (stem) {
				token = Utils.stem(token);
			}
			if (!tokens_.contains(token)) {
				continue;
			}
			if (token.length() < 3) {
				continue;
			}
			Integer cnt = mix_ing_tkn_counts_.get(token);
//			System.out.println(token + " " + cnt);
			if (cnt == null) {
				lprob += Math.log(alpha) + denom;
//				return Double.NEGATIVE_INFINITY;
			} else {
				lprob += Math.log(alpha + cnt.doubleValue()) + denom;
//				lprob += Math.log(cnt.doubleValue()) + denom;
			}
		}
		return lprob;
	}
	
	public double lprobPhraseGivenLoc(String phrase, boolean stem) {
		double lprob = 0.0;
		String[] tokens = phrase.split(" ");
		double denom = -1 * Math.log((alpha*tokens_.size()) + num_tkns_in_loc_phrases_);
		for (String token : tokens) {
			if (stem) {
				token = Utils.stem(token);
			}
			if (!tokens_.contains(token)) {
				continue;
			}
			if (token.length() < 3) {
				continue;
			}
			Integer cnt = loc_tkn_counts_.get(token);
//			System.out.println(token + " " + cnt);
			if (cnt == null) {
				lprob += Math.log(alpha) + denom;
			} else {
				lprob += Math.log(alpha + cnt.doubleValue()) + denom;
			}
		}
		return lprob;
	}
	
	public double lprobLocGivenPhrase(String phrase, boolean stem) {
		double ing_prob = lprobPhraseGivenIng(phrase, stem);
		double noning_prob = lprobPhraseGivenNonIng(phrase, stem);
		double loc_prob = lprobPhraseGivenLoc(phrase, stem);
		double sum = Utils.logsumexp(ing_prob, Utils.logsumexp(noning_prob, loc_prob));
		return loc_prob - sum;
	}
	
	public RecipeSentenceSegmenter chunker;
	
	public void initialize(List<Pair<File, File>> arg_and_fulltext_files,
			SelectionalPreferenceModel pref_model) throws IllegalArgumentException, IOException {
		int verb_count = 0;
		Set<String> verbs = new HashSet<String>();

		verbs.add("LEAF");
		
//		Set<String> ingredient_tokens = new HashSet<String>();
//		for (Pair<File, File> arg_and_fulltext_file_pair : arg_and_fulltext_files) {
//			// Don't use any ingredient information. Set all spans to non-ingredient
//			File arg_file = arg_and_fulltext_file_pair.getFirst();
//			File fulltext_file = arg_and_fulltext_file_pair.getSecond();
//			System.out.println(fulltext_file.getName());
////			ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromFile(arg_file, fulltext_file, false, false);
////			if (ad.numNodes() == 0) {
////				continue;
////			}
//
//			Set<String> recipe_ingredients = IngredientParser.parseIngredientsFromFullTextFileNoTagger(fulltext_file);
//			for (String ingredient : recipe_ingredients) {
//				String[] split = ingredient.split(" ");
//				for (String s : split) {
//					if (Utils.canWordBeNounInWordNet(s)) {
//						ingredient_tokens.add(s.toLowerCase());
//					}
//				}
//			}
//		}
//		System.out.println(ingredient_tokens);
//		System.exit(1);

		// Find first sentences for non ingredient phrases
		// e.g., any phrase in a first sentence where no tokens seen in ingredients appear
		System.out.println("Counting non-ingredient tokens...");
		for (Pair<File, File> arg_and_fulltext_file_pair : arg_and_fulltext_files) {
			// Don't use any ingredient information. Set all spans to non-ingredient
			File arg_file = arg_and_fulltext_file_pair.getFirst();
			File fulltext_file = arg_and_fulltext_file_pair.getSecond();
			System.out.println(fulltext_file.getName());
			ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromFile(arg_file, fulltext_file, false, false);
			if (ad.numNodes() == 0) {
				continue;
			}

			Set<String> recipe_ingredients = IngredientParser.parseIngredientsFromFullTextFileNoTagger(fulltext_file);
			Set<String> recipe_ingredient_tokens = new HashSet<String>();
			for (String ingredient : recipe_ingredients) {
				String[] split = ingredient.split(" ");
				for (String s : split) {
					if (Utils.canWordBeNounInWordNet(s)) {
						recipe_ingredient_tokens.add(Utils.stem(s.toLowerCase()));
					}
				}
			}
			if (recipe_ingredient_tokens.contains("ingredi")) {
				System.out.println(ad.recipeName());
				System.out.println(recipe_ingredient_tokens);
				System.exit(1);
			}

			Iterator<ActionNode> node_it = ad.node_iterator();
			boolean first = true;
			while (node_it.hasNext()) {
				ActionNode node = node_it.next();
				RecipeEvent event = node.event();
				String predicate = event.predicate();
				verbs.add(predicate);
				Utils.incrementStringMapCount(verb_counts_, predicate);
				boolean ing_dobj = pref_model.doesPredMostLikelyHaveIngObj(predicate);
				
				Argument dobj = event.dobj();
				if (dobj != null) {
					boolean contains_ingredients = false;
					for (String span : dobj.nonIngredientSpans()) {
						boolean ingredient_token_found = false;
						boolean non_ingredient_token_found = false;
						Pair<String, String> amount_and_ing = Measurements.splitAmountString(span);
						String[] tokens = amount_and_ing.getSecond().split(" ");
						if (!ing_dobj) {
							for (String token : tokens) {
								token = Utils.stem(token);
								if (recipe_ingredient_tokens.contains(token)) {
									ingredient_token_found = true;
								} else {
									non_ingredient_token_found = true;
									break;
								}
							}
						}
						if (ing_dobj || (!non_ingredient_token_found && ingredient_token_found)) {
							num_ing_phrases_++;
							for (String token : tokens) {
								if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(token)) {
									continue;
								}
								if (!Utils.isMostlyNounInWordNet(token)) {
									continue;
								}
								token = Utils.stem(token);
								num_tkns_in_ing_phrases_++;
								Utils.incrementStringMapCount(ing_tkn_counts_, token);
								if (Utils.isMostlyNounInWordNet(token)) {
									if (recipe_ingredient_tokens.contains(token)) {
										Utils.incrementStringMapCount(raw_ing_tkn_counts_, token);
									} else {
										Utils.incrementStringMapCount(mix_ing_tkn_counts_, token);
									}
								}
							}
							contains_ingredients = true;
						}
					}

					if (!contains_ingredients && first) {
						num_noning_phrases_++;
						num_loc_phrases_++;
						for (String span : dobj.nonIngredientSpans()) {
							String[] tokens = span.split(" ");
							String token = tokens[tokens.length - 1];
//							for (String token : tokens) {
//								if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(token)) {
//									continue;
//								}
								token = Utils.stem(token);
								num_tkns_in_noning_phrases_++;
								num_tkns_in_loc_phrases_++;
								Utils.incrementStringMapCount(noning_tkn_counts_, token);
								Utils.incrementStringMapCount(loc_tkn_counts_, token);
//							}
						}
					}
				}
				Iterator<Argument> prep_it = event.prepositionalArgIterator();
				while (prep_it.hasNext()) {
					Argument prep = prep_it.next();
					if (prep.type() != Type.COOBJECT && prep.type() != Type.LOCATION && prep.type() != Type.LOCOROBJ) {
						continue;
					}
					boolean contains_ingredients = false;
					for (String span : prep.nonIngredientSpans()) {
						boolean ingredient_token_found = false;
						boolean non_ingredient_token_found = false;
						Pair<String, String> amount_and_ing = Measurements.splitAmountString(span);
						String[] tokens = amount_and_ing.getSecond().split(" ");
						for (String token : tokens) {
							token = Utils.stem(token);
//							tokens_.add(token);
							if (recipe_ingredient_tokens.contains(token)) {
								ingredient_token_found = true;
							} else {
								non_ingredient_token_found = true;
								break;
							}
						}
						if (!non_ingredient_token_found && ingredient_token_found) {
//							num_ing_phrases_++;
//							for (String token : tokens) {
//								if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(token)) {
//									continue;
//								}
//								num_tkns_in_ing_phrases_++;
//								Utils.incrementStringMapCount(ing_tkn_counts_, token);
//							}
							contains_ingredients = true;
						}
					}
					if (!contains_ingredients && first) {
						num_noning_phrases_++;
						num_loc_phrases_++;
						for (String span : prep.nonIngredientSpans()) {
							String[] tokens = span.split(" ");
							String token = tokens[tokens.length - 1];
//							for (String token : tokens) {
//								if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(token)) {
//									continue;
//								}
								token = Utils.stem(token);
								tokens_.add(token);
								num_tkns_in_noning_phrases_++;
								num_tkns_in_loc_phrases_++;
								Utils.incrementStringMapCount(loc_tkn_counts_, token);
								Utils.incrementStringMapCount(noning_tkn_counts_, token);
//							}
						}
					} else if (contains_ingredients) {
						num_ing_phrases_++;
						for (String span : prep.nonIngredientSpans()) {
							Pair<String, String> amount_and_ing = Measurements.splitAmountString(span);
							String[] tokens = amount_and_ing.getSecond().split(" ");
							for (String token : tokens) {
								if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(token)) {
									continue;
								}
								if (!Utils.isMostlyNounInWordNet(token)) {
									continue;
								}
								token = Utils.stem(token);
								tokens_.add(token);
//								if (token.equals("cup")) {
//									System.out.println(event);
//									System.exit(1);
//								}
//								if (token.matches("turn|turning")) {
//									System.out.println(event);
//									System.out.println(amount_and_ing);
//									System.exit(1);
//								}
								num_tkns_in_ing_phrases_++;
								Utils.incrementStringMapCount(ing_tkn_counts_, token);
								if (Utils.isMostlyNounInWordNet(token)) {
									if (recipe_ingredient_tokens.contains(token)) {
										Utils.incrementStringMapCount(raw_ing_tkn_counts_, token);
									} else {
										Utils.incrementStringMapCount(mix_ing_tkn_counts_, token);
									}
								}
							}
						}
					}
				}

				first = false;

			}
		}

		System.out.println("priors: " + num_ing_phrases_ + " " + num_noning_phrases_);
	}


	public void addData(GraphInfo gi) {
		ActionDiagram ad = gi.actionDiagram();

		for (int i = 0; i < ad.numNodes(); i++) {
			ActionNode node = ad.getNodeAtIndex(i);
			RecipeEvent event = node.event();
			String predicate = event.predicate();
			Utils.incrementStringMapCount(verb_counts_, predicate);

			SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
			
			for (Argument ing_arg : pref.arg_arr) {
				if (ing_arg.string().equals("")) {
					continue;
				}
				String arg_str = ing_arg.string();
				String[] arg_tkns = arg_str.split(" ");
				for (String arg_tkn : arg_tkns) {
					if (arg_tkn.trim().equals("")) {
						continue;
					}
					if (!Utils.isMostlyNounInWordNet(arg_tkn)) {
						 continue;
					}
					Utils.incrementStringMapCount(ing_tkn_counts_, Utils.stem(arg_tkn));
					num_tkns_in_ing_phrases_++;
				}
				
				for (String span : ing_arg.nonIngredientSpans()) {
					if (span.trim().equals("")) {
						continue;
					}
					ActionNode origin = gi.getOrigin(node, ing_arg, span);
					if (origin == null) {
						String[] span_tkns = span.split(" ");
						for (String span_tkn : span_tkns) {
							if (!Utils.isMostlyNounInWordNet(span_tkn)) {
								 continue;
							}
//							if (Utils.stem(span_tkn).equals("ingredi")) {
//								System.out.println(ad.recipeName());
//								System.exit(1);
//							}
							Utils.incrementStringMapCount(raw_ing_tkn_counts_, Utils.stem(span_tkn));
						}
					} else {
						String[] span_tkns = span.split(" ");
						for (String span_tkn : span_tkns) {
							if (!Utils.isMostlyNounInWordNet(span_tkn)) {
								 continue;
							}
							Utils.incrementStringMapCount(mix_ing_tkn_counts_, Utils.stem(span_tkn));
						}
					}
				}
				for (String span : ing_arg.ingredientSpans()) {
					String[] span_tkns = span.split(" ");
					for (String span_tkn : span_tkns) {
						if (!Utils.isMostlyNounInWordNet(span_tkn)) {
							 continue;
						}
						Utils.incrementStringMapCount(raw_ing_tkn_counts_, Utils.stem(span_tkn));
					}
				}
			}
			if (pref.loc != null) {
				String loc_str = pref.loc.string();
				if (!pref.loc.string().equals("")) {
					String[] arg_tkns = loc_str.split(" ");
					String arg_tkn = arg_tkns[arg_tkns.length - 1];
//						if (arg_tkn.trim().equals("")) {
//							continue;
//						}
//						if (!Utils.isMostlyNounInWordNet(arg_tkn)) {
//							 continue;
//						}
						Utils.incrementStringMapCount(loc_tkn_counts_, Utils.stem(arg_tkn));
						num_tkns_in_loc_phrases_++;
						Utils.incrementStringMapValueCount(verb_to_location_counts_, predicate, Utils.stem(arg_tkn));
//					}
				}
			}
			for (Argument other_arg : pref.other_args) {
				String arg_str = other_arg.string();
				if (other_arg.string().equals("")) {
					continue;
				}
				String[] arg_tkns = arg_str.split(" ");
				for (String arg_tkn : arg_tkns) {
					if (arg_tkn.trim().equals("")) {
						continue;
					}
					if (!Utils.isMostlyNounInWordNet(arg_tkn)) {
						 continue;
					}
					Utils.incrementStringMapCount(noning_tkn_counts_, Utils.stem(arg_tkn));
					num_tkns_in_noning_phrases_++;
				}
			}
		}
	}

	private static void populateHashMap(BufferedReader br, int num_entries, Map<String, Integer> map) throws IOException {
		for (int i = 0; i < num_entries; i++) {
			String line = br.readLine();
			String[] split = line.split("\t");
			try {
				map.put(split[0], Integer.parseInt(split[1]));
			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println(line);
				System.exit(1);
			}
		}
	}

	public static ThreeWayStringClassifier readPhraseIdentifierFromFile(String filename) throws IOException {
		ThreeWayStringClassifier identifier = new ThreeWayStringClassifier();
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line = br.readLine();
		identifier.alpha = Double.parseDouble(line);

		line = br.readLine();
		int num_entries = Integer.parseInt(line);
		populateHashMap(br, num_entries, identifier.ing_tkn_counts_);
		identifier.tokens_.addAll(identifier.ing_tkn_counts_.keySet());

		identifier.num_tkns_in_ing_phrases_ = Integer.parseInt(br.readLine());
		identifier.num_ing_phrases_ = Integer.parseInt(br.readLine());

		num_entries = Integer.parseInt(br.readLine());
		populateHashMap(br, num_entries, identifier.noning_tkn_counts_);
		identifier.tokens_.addAll(identifier.noning_tkn_counts_.keySet());
		
		identifier.num_tkns_in_noning_phrases_ = Integer.parseInt(br.readLine());
		identifier.num_noning_phrases_ = Integer.parseInt(br.readLine());

		num_entries = Integer.parseInt(br.readLine());
		populateHashMap(br, num_entries, identifier.loc_tkn_counts_);
		identifier.tokens_.addAll(identifier.loc_tkn_counts_.keySet());

		
		identifier.num_tkns_in_loc_phrases_ = Integer.parseInt(br.readLine());
		identifier.num_loc_phrases_ = Integer.parseInt(br.readLine());

		num_entries = Integer.parseInt(br.readLine());
		populateHashMap(br, num_entries, identifier.verb_counts_);
		identifier.predicate_tokens_.addAll(identifier.verb_counts_.keySet());

		num_entries = Integer.parseInt(br.readLine());
		for (int j = 0; j < num_entries; j++) {
			String verb = br.readLine();
			Map<String, Integer> location_counts = new HashMap<String, Integer>();
			int num_locations = Integer.parseInt(br.readLine());
			populateHashMap(br, num_locations, location_counts);	
			identifier.verb_to_location_counts_.put(verb, location_counts);
		}

		identifier.raw_ing_tkn_counts_ = new HashMap<String, Integer>();
		num_entries = Integer.parseInt(br.readLine());
		populateHashMap(br, num_entries, identifier.raw_ing_tkn_counts_);
		for (Integer cnt : identifier.raw_ing_tkn_counts_.values()) {
			identifier.total_raw_cnt += cnt;
		}

		identifier.mix_ing_tkn_counts_ = new HashMap<String, Integer>();
		num_entries = Integer.parseInt(br.readLine());
		populateHashMap(br, num_entries, identifier.mix_ing_tkn_counts_);
		for (Integer cnt : identifier.mix_ing_tkn_counts_.values()) {
			identifier.total_mix_cnt += cnt;
		}

		br.close();

		identifier.initialized = true;
		return identifier;
	}

	public int total_raw_cnt = 0;
	public int total_mix_cnt = 0;

	public void writeToFile(String filename) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		bw.write(alpha + "\n");
		bw.write(ing_tkn_counts_.size() + "\n");
		for (String tkn : ing_tkn_counts_.keySet()) {
			bw.write(tkn + "\t" + ing_tkn_counts_.get(tkn) + "\n");
		}
		bw.write(num_tkns_in_ing_phrases_ + "\n");
		bw.write(num_ing_phrases_ + "\n");
		bw.write(noning_tkn_counts_.size() + "\n");
		for (String tkn : noning_tkn_counts_.keySet()) {
			bw.write(tkn + "\t" + noning_tkn_counts_.get(tkn) + "\n");
		}
		bw.write(num_tkns_in_noning_phrases_ + "\n");
		bw.write(num_noning_phrases_ + "\n");
		
		
		bw.write(loc_tkn_counts_.size() + "\n");
		for (String tkn : loc_tkn_counts_.keySet()) {
			bw.write(tkn + "\t" + loc_tkn_counts_.get(tkn) + "\n");
		}
		bw.write(num_tkns_in_loc_phrases_ + "\n");
		bw.write(num_loc_phrases_ + "\n");

		bw.write(verb_counts_.size() + "\n");
		for (String tkn : verb_counts_.keySet()) {
			bw.write(tkn + "\t" + verb_counts_.get(tkn) + "\n");
		}

		bw.write(verb_to_location_counts_.size() + "\n");
		for (String verb : verb_to_location_counts_.keySet()) {
			bw.write(verb + "\n");
			Map<String, Integer> location_counts = verb_to_location_counts_.get(verb);
			bw.write(location_counts.size() + "\n");
			for (String tkn : location_counts.keySet()) {
				bw.write(tkn + "\t" + location_counts.get(tkn) + "\n");
			}
		}

		if (raw_ing_tkn_counts_ == null) {
			bw.write("0\n");
		} else {
			bw.write(raw_ing_tkn_counts_.size() + "\n");
			for (String tkn : raw_ing_tkn_counts_.keySet()) {
				bw.write(tkn + "\t" + raw_ing_tkn_counts_.get(tkn) + "\n");
			}
		}
		if (mix_ing_tkn_counts_ == null) {
			bw.write("0\n");
		} else {
			bw.write(mix_ing_tkn_counts_.size() + "\n");
			for (String tkn : mix_ing_tkn_counts_.keySet()) {
				bw.write(tkn + "\t" + mix_ing_tkn_counts_.get(tkn) + "\n");
			}
		}
		bw.close();
	}
	
	public double lprobPhraseIsRawGivenIng(String phrase, boolean stem) {
		double total = Math.log(total_mix_cnt + total_raw_cnt);
		double raw = Math.log(total_raw_cnt) - total;
		double mix = Math.log(total_mix_cnt) - total;
		
		String[] split = phrase.split(" ");
		for (String s : split) {
			if (!Utils.isMostlyNounInWordNet(s)) {
				continue;
			}
			if (stem) {
				s = Utils.stem(s);
			}
			Integer raw_cnt = raw_ing_tkn_counts_.get(s);
			Integer mix_cnt = mix_ing_tkn_counts_.get(s);
//			System.out.println(raw_cnt + " " + mix_cnt);
			if (raw_cnt == null) {
				if (mix_cnt == null) {
					raw += Math.log(0.5);
					mix += Math.log(0.5);
				} else {
					double denom = Math.log((2.0*alpha) + mix_cnt);
					raw += Math.log(alpha) - denom;
					mix += Math.log(alpha + mix_cnt) - denom;
//					double denom = Math.log(mix_cnt);
//					raw += Double.NEGATIVE_INFINITY;
//					mix += 0.0;
				}
			} else {
				if (mix_cnt == null) {
					double denom = Math.log((2.0*alpha) + raw_cnt);
					raw += Math.log(alpha + raw_cnt) - denom;
					mix += Math.log(alpha) - denom;
//					double denom = Math.log(raw_cnt);
//					mix += Double.NEGATIVE_INFINITY;
//					raw += 0.0;
				} else {
					double denom = Math.log((2.0*alpha) + raw_cnt + mix_cnt);
					raw += Math.log(alpha + raw_cnt) - denom;
					mix += Math.log(alpha + mix_cnt) - denom;
//					double denom = Math.log(raw_cnt + mix_cnt);
//					raw += Math.log(raw_cnt) - denom;
//					mix += Math.log(mix_cnt) - denom;
				}
			}
		}
		double sum = Utils.logsumexp(raw, mix);
		return raw - sum;
	}

	public static void main(String[] args) {
		try {
			if (args.length != 1) {
				System.out.println("Requires one argument 'train' or 'test'");
			} else if (args[0] == "train") {
				List<Pair<File, File>> training_files = Utils.getFileList();
				ThreeWayStringClassifier classifier = new ThreeWayStringClassifier();
				SelectionalPreferenceModel pref_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_selectional_pref.model");
				classifier.initialize(training_files, pref_model);
				classifier.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_string_classifier_stem_lastloc_nouns.model");
			} else if (args[0] == "test") {
				ThreeWayStringClassifier classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_string_classifier_stem_lastloc_nouns.model");
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				while (true) {
					System.out.print("Phrase: ");
					String phrase = br.readLine();
					if (phrase.equals("quit")) {
						break;
					}
					HashSet<String> s = new HashSet<String>();
					s.add(phrase);
					SortedSet<Pair<String, Integer>> sorted = new TreeSet<Pair<String, Integer>>(
							new Comparator<Pair<String, Integer> >() {
								public int compare(Pair<String, Integer> e1, Pair<String, Integer> e2) {
									int cmp = e2.getSecond().compareTo(e1.getSecond());
									if (cmp != 0) {
										return cmp;
									}
									return e1.getFirst().compareTo(e2.getFirst());
								}
							});
					Map<String, Integer> loc_counts = classifier.verb_to_location_counts_.get(phrase);
					System.out.println(classifier.verb_to_location_counts_);
					if (loc_counts != null) {
						for (String loc : loc_counts.keySet()) {
							Integer count = loc_counts.get(loc);
							sorted.add(new Pair<String, Integer>(loc, count));
						}
						int i = 0;
						for (Pair<String, Integer> pair : sorted) {
							s = new HashSet<String>();
							s.add(pair.getFirst());
							System.out.println(pair + " " + Math.exp(classifier.lprobTknsGivenLoc(phrase, s, false)));
							i++;
							if (i > 10) {
								break;
							}
						}
					}
				}
			} else {
				System.out.println("Requires one argument 'train' or 'test'");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
