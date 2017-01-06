package model;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import utils.Measurements;
import utils.Pair;
import utils.Utils;
import data.ActionDiagram;
import data.IngredientParser;
import data.ProjectParameters;
import data.RecipeEvent;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;

public class RevSelectionalPreferenceModel {

	public static String[] possible_prefs = new String[]{"INGOBJ:PREP:LEAF", "INGOBJ:NOPREP:LEAF",
		"NONINGOBJ:PREP:LEAF", "NONINGOBJ:NOPREP:LEAF", "INGOBJ:PREP", "INGOBJ:NOPREP",
		"NONINGOBJ:PREP", "NONINGOBJ:NOPREP"};

	public static class SelectionalPreference implements Comparable<SelectionalPreference> {
		public String pref_type;
		public Argument[] arg_arr;
		public Argument loc = null;
		public List<Argument> other_args = null;

		public SelectionalPreference copy() {
			SelectionalPreference copy = new SelectionalPreference();
			copy.pref_type = pref_type;
			if (arg_arr != null) {
				copy.arg_arr = new Argument[arg_arr.length];
				for (int i = 0; i < arg_arr.length; i++) {
					copy.arg_arr[i] = arg_arr[i];
				}
			} else {
				copy.arg_arr = null;
			}
			copy.loc = loc;
			if (other_args != null) {
				copy.other_args = new ArrayList<Argument>(other_args);
			}
			
			return copy;
		}
		
		@Override
		public int compareTo(SelectionalPreference o) {
			int cmp = pref_type.compareTo(o.pref_type);
			if (cmp != 0) {
				return cmp;
			}
			cmp = arg_arr.length - o.arg_arr.length;
			if (cmp != 0) {
				return cmp;
			}
			for (int i = 0; i < arg_arr.length; i++) {
				cmp = arg_arr[i].compareTo(o.arg_arr[i]);
				if (cmp != 0) {
					return cmp;
				}
			}
			if (loc == null) {
				if (o.loc == null) {
					return 0;
				}
				return -1;
			}
			if (o.loc == null) {
				return 1;
			}
			cmp = loc.compareTo(o.loc);
			return cmp;
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof SelectionalPreference)) {
				return false;
			}
			SelectionalPreference other = (SelectionalPreference)obj;
			return compareTo(other) == 0;
		}
		
		public String toString() {
			String str = pref_type + "\n";
			for (Argument arg : arg_arr) {
				str += arg + "  ";
			}
			str += "\n";
			str += loc;
			return str;
		}
	}

	private static boolean ADD_IMPLICITS = true;

	private Map<String, Double> pref_probs;
	private Map<String, Map<String, Double>> selectional_pref_to_verb_log_probs;
	public static double alpha = 0.01;
	public static String UNK = "UNK";


	public RevSelectionalPreferenceModel() {
		selectional_pref_to_verb_log_probs = new HashMap<String, Map<String, Double>>();
		pref_probs = new HashMap<String, Double>();
	}
	
	public void setSelectionalPrefModel(Map<String, Map<String, Double>> probs) {
		selectional_pref_to_verb_log_probs = probs;
	}
	
	public void initialize(List<Pair<File, File>> arg_and_fulltext_files) throws IllegalArgumentException, IOException {
		int verb_count = 0;
		int ttl = 0;
		Set<String> verb_set = new HashSet<String>();
		Map<String, Double> pref_cnt = new HashMap<String, Double>();
		Map<String, Map<String, Double>> pref_to_verb_cnt = new HashMap<String, Map<String, Double>>();

		// Find first sentences for non ingredient phrases
		// e.g., any phrase in a first sentence where no tokens seen in ingredients appear
		System.out.println("Counting non-ingredient tokens...");
		for (Pair<File, File> arg_and_fulltext_file_pair : arg_and_fulltext_files) {
			// Don't use any ingredient information. Set all spans to non-ingredient
			File arg_file = arg_and_fulltext_file_pair.getFirst();
			File fulltext_file = arg_and_fulltext_file_pair.getSecond();
//			System.out.println(fulltext_file.getName());
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
						recipe_ingredient_tokens.add(s.toLowerCase());
					}
				}
			}

			Iterator<ActionNode> node_it = ad.node_iterator();
			boolean first = true;
			while (node_it.hasNext()) {
				ActionNode node = node_it.next();
				RecipeEvent event = node.event();
				String predicate = event.predicate();
				verb_set.add(predicate);

				Argument dobj = event.dobj();
				int dobj_ing = 0;
				if (dobj != null && !dobj.string().equals("")) {
					dobj_ing = -1;
					for (String span : dobj.nonIngredientSpans()) {
						boolean ingredient_token_found = false;
						boolean non_ingredient_token_found = false;
						Pair<String, String> amount_and_ing = Measurements.splitAmountString(span);
						String[] tokens = amount_and_ing.getSecond().split(" ");
						for (String token : tokens) {
							if (!Utils.canWordBeNounInWordNet(token)) {
								continue;
							}
							if (recipe_ingredient_tokens.contains(token)) {
								ingredient_token_found = true;
							} else {
								non_ingredient_token_found = true;
								break;
							}
						}
						if (!non_ingredient_token_found && ingredient_token_found) {
							dobj_ing = 1;
							break;
						}
					}
				}
				
				boolean has_ing_prep = false;
				Iterator<Argument> prep_it = event.prepositionalArgIterator();
				while (prep_it.hasNext()) {
					Argument prep = prep_it.next();
					if (prep.type() != Type.LOCATION && prep.type() != Type.LOCOROBJ && prep.type() != Type.COOBJECT) {
						continue;
					}
					for (String span : prep.nonIngredientSpans()) {
						boolean ingredient_token_found = false;
						boolean non_ingredient_token_found = false;
						Pair<String, String> amount_and_ing = Measurements.splitAmountString(span);
						String[] tokens = amount_and_ing.getSecond().split(" ");
						for (String token : tokens) {
							if (!Utils.canWordBeNounInWordNet(token)) {
								continue;
							}
							if (recipe_ingredient_tokens.contains(token)) {
								ingredient_token_found = true;
							} else {
								non_ingredient_token_found = true;
								break;
							}
						}
						if (!non_ingredient_token_found && ingredient_token_found) {
							has_ing_prep = true;
							break;
						}
					}
				}
				if (predicate.equals("add") && first) {
					System.out.println(ad.recipeName() + " " + dobj_ing + " " + has_ing_prep);
				}
				ttl++;
				if (dobj_ing == -1 && first) {
					if (has_ing_prep) {
						Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:PREP:LEAF", 1.0);
						Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:PREP:LEAF", 
								predicate, 1.0);
					} else {
						Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:NOPREP:LEAF", 1.0);	
						Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:NOPREP:LEAF", 
								predicate, 1.0);
					}
				} else if (dobj_ing == 1) {
					if (first) {
						if (has_ing_prep) {
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP:LEAF", 1.0);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP:LEAF", 
									predicate, 1.0);
						} else {
//							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP:LEAF", 0.5);
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:NOPREP:LEAF", 1.0);
//							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP:LEAF", 
//									predicate, 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:NOPREP:LEAF", 
									predicate, 1.0);
						}
					} else {
						if (has_ing_prep) {
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP", 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP", 
									predicate, 0.5);
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP:LEAF", 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP:LEAF", 
									predicate, 0.5);
						} else {
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP", 0.5);
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:NOPREP", 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP", 
									predicate, 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:NOPREP", 
									predicate, 0.5);
						}
					}
				} else {
					if (first) {
						if (has_ing_prep) {
//							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP:LEAF", 0.5);
							Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:PREP:LEAF", 1.0);
//							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP:LEAF", 
//									predicate, 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:PREP:LEAF", 
									predicate, 1.0);
						} else {
//							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP:LEAF", 0.25);
//							Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:PREP:LEAF", 0.25);
//							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP:LEAF", 
//									predicate, 0.25);
//							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:PREP:LEAF", 
//									predicate, 0.25);
//							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:NOPREP:LEAF", 0.25);
							Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:NOPREP:LEAF", 1.0);
//							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:NOPREP:LEAF", 
//									predicate, 0.25);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:NOPREP:LEAF", 
									predicate, 1.0);
						}
					} else {
						if (has_ing_prep) {
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP", 0.5);
							Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:PREP", 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP", 
									predicate, 0.5);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:PREP", 
									predicate, 0.5);
						} else {
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:PREP", 0.25);
							Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:PREP", 0.25);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:PREP", 
									predicate, 0.25);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:PREP", 
									predicate, 0.25);
							Utils.incrementStringDoubleMapCount(pref_cnt, "INGOBJ:NOPREP", 0.25);
							Utils.incrementStringDoubleMapCount(pref_cnt, "NONINGOBJ:NOPREP", 0.25);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "INGOBJ:NOPREP", 
									predicate, 0.25);
							Utils.incrementStringDoubleMapValueCount(pref_to_verb_cnt, "NONINGOBJ:NOPREP", 
									predicate, 0.25);
						}
					}
				}
				first = false;
			}
		}
		int ttl_ing = 0;
		int ttl_noning = 0;
		
		for (String pref : pref_cnt.keySet()) {
			Double ttl_cnt = pref_cnt.get(pref);
			pref_probs.put(pref, Math.log(ttl_cnt) - Math.log(ttl));
			Map<String, Double> verb_cnt = pref_to_verb_cnt.get(pref);
			double denom = -1*Math.log(ttl_cnt + (alpha*(verb_cnt.size() + 1)));
			Map<String, Double> verb_prob = new HashMap<String, Double>();
			verb_prob.put(UNK, Math.log(alpha) + denom);
			for (String verb : verb_cnt.keySet()) {
				Double cnt = verb_cnt.get(verb);
				verb_prob.put(verb, Math.log(alpha + cnt) + denom);
			}
			selectional_pref_to_verb_log_probs.put(pref, verb_prob);
		}
//		for (String pref : pref_cnt.keySet()) {
//			Double ttl_cnt = pref_cnt.get(pref);
//			pref_probs.put(pref, Math.log(ttl_cnt) - Math.log(ttl));
//			Map<String, Double> verb_cnt = pref_to_verb_cnt.get(pref);
//			double denom = -1*Math.log(ttl_cnt + (alpha*(verb_set.size() + 1)));
//			Map<String, Double> verb_prob = new HashMap<String, Double>();
//			verb_prob.put(UNK, Math.log(alpha) + denom);
//			for (String verb : verb_set) {
//				Double cnt = verb_cnt.get(verb);
//				if (cnt != null) {
//					verb_prob.put(verb, Math.log(alpha + cnt) + denom);
//				} else {
//					verb_prob.put(verb, Math.log(alpha) + denom);
//				}
//			}
//			selectional_pref_to_verb_log_probs.put(pref, verb_prob);
//		}
	}

	public static RevSelectionalPreferenceModel readModelFromFile(String filename) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(filename));
		RevSelectionalPreferenceModel model = new RevSelectionalPreferenceModel();
		int num_prefs = Integer.parseInt(br.readLine());
		for (int v = 0; v < num_prefs; v++) {
			String[] split = br.readLine().split("\t");
			model.pref_probs.put(split[0], Double.parseDouble(split[1]));
		}
		num_prefs = Integer.parseInt(br.readLine());
		for (int v = 0; v < num_prefs; v++) {
			String pref = br.readLine();
			Map<String, Double> verb_probs = new HashMap<String, Double>();
			int num_verbs = Integer.parseInt(br.readLine());
			for (int p = 0; p < num_verbs; p++)	{
				String[] split = br.readLine().split("\t");
				verb_probs.put(split[0], Double.parseDouble(split[1]));
			}
			model.selectional_pref_to_verb_log_probs.put(pref, verb_probs);
		}
		br.close();
		return model;
	}

	public void writeToFile(String filename) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		bw.write(pref_probs.size() + "\n");
		for (String pref : pref_probs.keySet()) {
			bw.write(pref + "\t" + pref_probs.get(pref) + "\n");
		}
		bw.write(selectional_pref_to_verb_log_probs.size() + "\n");
		for (String pref : selectional_pref_to_verb_log_probs.keySet()) {
			bw.write(pref + "\n");
			Map<String, Double> verb_prob = selectional_pref_to_verb_log_probs.get(pref);
			bw.write(verb_prob.size() + "\n");
			for (Map.Entry<String, Double> entry : verb_prob.entrySet()) {
				bw.write(entry.getKey() + "\t" + entry.getValue() + "\n");
			}
		}
		bw.close();
	}
	
	public double lprobVerbGivenPref(String verb, String pref) {
		Map<String, Double> verb_probs = selectional_pref_to_verb_log_probs.get(pref);
		Double prob = verb_probs.get(verb);
		if (prob == null) {
			return verb_probs.get(UNK);
		}
		return prob;
	}
	
	public double lprobPrefPrior(String pref) {
		return pref_probs.get(pref);
	}


	public static void main(String[] args) {
		boolean learn = false;
		if (learn) {
			try {
				RevSelectionalPreferenceModel model = new RevSelectionalPreferenceModel();
				List<Pair<File, File>> training_files = Utils.getFileList();
				model.initialize(training_files);
//				model.writeToFile("init_rev_selectional_pref_leaf2.model");
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(1);
			}
		} else {
			try {
				RevSelectionalPreferenceModel model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "rev_pref_model_4hardem99_cam_10000.model");
//				RevSelectionalPreferenceModel model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_rev_selectional_pref_leaf2.model");
//				RevSelectionalPreferenceModel model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "split-09-14-16/rev_selectional_pref_init.model");
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				while (true) {
					System.out.print("Verb: ");
					String verb = br.readLine();
					if (verb.equals("quit")) {
						break;
					}
					double sum = 0.0;
					for (String pref : model.selectional_pref_to_verb_log_probs.keySet()) {
						Map<String, Double> verb_probs = model.selectional_pref_to_verb_log_probs.get(pref);
						Double prob = verb_probs.get(verb);
						if (prob == null) {
							prob = verb_probs.get(UNK);
						}
						sum += Math.exp(prob + model.pref_probs.get(pref));
					}
					for (String pref : model.selectional_pref_to_verb_log_probs.keySet()) {
						Map<String, Double> verb_probs = model.selectional_pref_to_verb_log_probs.get(pref);
						Double prob = verb_probs.get(verb);
						if (prob == null) {
							prob = verb_probs.get(UNK);
						}
						System.out.println(pref + "  " + Math.exp(prob + model.pref_probs.get(pref)) / sum);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(1);
			}
		}
	}

	public void setPriors(Map<String, Double> pref_probs2) {
		pref_probs = pref_probs2;
	}
}
