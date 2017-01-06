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

public class SelectionalPreferenceModel {

	public static String[] possible_prefs = new String[]{"INGOBJ:PREP", "INGOBJ:NOPREP",
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

	private String ing_classifier_filename_;
	private Map<String, Map<String, Double>> verb_to_selectional_pref_log_probs;
	private Map<String, Map<String, Double>> verb_to_imp_prep_log_probs;
	private Map<String, Double> global_selectional_pref_log_probs;
	private Map<String, Double> verb_creates_loc_log_probs;
	private double global_creates_loc_log_prob = Math.log(0.01);
	private static double alpha = 0.01;


	public SelectionalPreferenceModel() {
		verb_to_selectional_pref_log_probs = new HashMap<String, Map<String, Double>>();
		global_selectional_pref_log_probs = new HashMap<String, Double>();
		verb_to_imp_prep_log_probs = new HashMap<String, Map<String, Double>>();
		verb_creates_loc_log_probs = new HashMap<String, Double>();
	}

	public void initialize(List<Pair<File, File>> arg_and_fulltext_files) throws IllegalArgumentException, IOException {
		int verb_count = 0;
		Map<String, Integer> verb_cnt = new HashMap<String, Integer>();
		Map<String, Integer> verb_ing_obj_cnt = new HashMap<String, Integer>();
		Map<String, Integer> verb_noning_obj_cnt = new HashMap<String, Integer>();
		Map<String, Integer> verb_first_cnt = new HashMap<String, Integer>();
		Map<String, Integer> verb_first_loc_cnt = new HashMap<String, Integer>();

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
				Utils.incrementStringMapCount(verb_cnt, predicate);

				Argument dobj = event.dobj();
				if (dobj != null) {
					boolean contains_ingredients = false;
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
							contains_ingredients = true;
							break;
						}
					}
					if (first) {
						Utils.incrementStringMapCount(verb_first_cnt, predicate);
					}

					if (!contains_ingredients && first) {
						Utils.incrementStringMapCount(verb_noning_obj_cnt, predicate);
						Utils.incrementStringMapCount(verb_first_loc_cnt, predicate);
					} else if (contains_ingredients) {
						Utils.incrementStringMapCount(verb_ing_obj_cnt, predicate);
					}
				}
				first = false;
			}
		}
		int ttl_ing = 0;
		int ttl_noning = 0;
		int ttl = 0;
		for (String verb : verb_cnt.keySet()) {
			Map<String, Double> verb_pref_probs = new HashMap<String, Double>();
			Integer ttl_cnt = verb_cnt.get(verb);
			ttl += ttl_cnt.intValue();
			double denom = -1 * Math.log(ttl_cnt + (2*alpha));
			Integer ing = verb_ing_obj_cnt.get(verb);
			Integer noning = verb_noning_obj_cnt.get(verb);
			if (ing == null) {
				double prob = Math.log(alpha) + denom;
				double half_prob = prob - Math.log(2.0);
				verb_pref_probs.put("INGOBJ:PREP", half_prob);
				verb_pref_probs.put("INGOBJ:NOPREP", half_prob);
			} else {
				ttl_ing += ing.intValue();
				double prob = Math.log(alpha + ing.doubleValue()) + denom;
				double half_prob = prob - Math.log(2.0);
				verb_pref_probs.put("INGOBJ:PREP", half_prob);
				verb_pref_probs.put("INGOBJ:NOPREP", half_prob);
			}
			if (noning == null) {
				double prob = Math.log(alpha) + denom;
				double half_prob = prob - Math.log(2.0);
				verb_pref_probs.put("NONINGOBJ:PREP", half_prob);
				verb_pref_probs.put("NONINGOBJ:NOPREP", half_prob);
			} else {
				ttl_noning += noning.intValue();
				double prob = Math.log(alpha + noning.doubleValue()) + denom;
				double half_prob = prob - Math.log(2.0);
				verb_pref_probs.put("NONINGOBJ:PREP", half_prob);
				verb_pref_probs.put("NONINGOBJ:NOPREP", half_prob);
			}
			verb_to_selectional_pref_log_probs.put(verb, verb_pref_probs);
		}
		double denom = -1 * Math.log(ttl + (2*alpha));
		double ing_prob = Math.log(alpha + ttl_ing) + denom;
		double noning_prob = Math.log(alpha + ttl_noning) + denom;
		double half_ing_prob = ing_prob - Math.log(2.0);
		double half_noning_prob = noning_prob - Math.log(2.0);
		global_selectional_pref_log_probs.put("INGOBJ:PREP", half_ing_prob);
		global_selectional_pref_log_probs.put("INGOBJ:NOPREP", half_ing_prob);
		global_selectional_pref_log_probs.put("NONINGOBJ:PREP", half_noning_prob);
		global_selectional_pref_log_probs.put("NONINGOBJ:NOPREP", half_noning_prob);
		
		ttl = 0;
		int loc = 0;
		for (String verb : verb_first_cnt.keySet()) {
			Integer ttl_cnt = verb_first_cnt.get(verb);
			ttl += ttl_cnt;
			Integer loc_cnt = verb_first_loc_cnt.get(verb);
			if (loc_cnt == null) {
				verb_creates_loc_log_probs.put(verb, Math.log(alpha) - Math.log(ttl_cnt + (2.0*alpha)));
			} else {
				loc += loc_cnt;
				verb_creates_loc_log_probs.put(verb, Math.log(loc_cnt + alpha) - Math.log(ttl_cnt + (2.0*alpha)));
			}
		}
		global_creates_loc_log_prob = Math.log(loc + alpha) - Math.log(ttl + (2.0*alpha));
	}

	public void setVerbPrefProbs(Map<String, Map<String, Double>> verb_pref_probs) {
		verb_to_selectional_pref_log_probs = verb_pref_probs;
	}
	
	public void setVerbImpPrepProbs(Map<String, Map<String, Double>> imp_prep_probs) {
		verb_to_imp_prep_log_probs = imp_prep_probs;
	}

	public void setGlobalPrefProbs(Map<String, Double> global_pref_probs) {
		global_selectional_pref_log_probs = global_pref_probs;
	}
	
	public double logProbVerbHasImpPrepGivenPrep(String verb) {
		Map<String, Double> probs = verb_to_imp_prep_log_probs.get(verb);
		if (probs == null) {
			return Math.log(0.5);
		}
		return probs.get("IMP");
	}
	
	public double logProbVerbHasEvoPrepGivenPrep(String verb) {
		Map<String, Double> probs = verb_to_imp_prep_log_probs.get(verb);
		if (probs == null) {
			return Math.log(0.5);
		}
		return probs.get("EVO");
	}

	public double logProbVerbHasIngObj(String verb) {
		Map<String, Double> selectional_prefs =  verb_to_selectional_pref_log_probs.get(verb);
		if (selectional_prefs == null) {
			selectional_prefs = global_selectional_pref_log_probs;
		}
		return Utils.logsumexp(selectional_prefs.get("INGOBJ:PREP"), selectional_prefs.get("INGOBJ:NOPREP"));
	}

	public double logProbVerbHasPrep(String verb) {
		Map<String, Double> selectional_prefs =  verb_to_selectional_pref_log_probs.get(verb);
		if (selectional_prefs == null) {
			selectional_prefs = global_selectional_pref_log_probs;
		}

		return Utils.logsumexp(selectional_prefs.get("INGOBJ:PREP"), selectional_prefs.get("NONINGOBJ:PREP"));
	}

	public double logProbVerbHasNonIngObj(String verb) {
		Map<String, Double> selectional_prefs =  verb_to_selectional_pref_log_probs.get(verb);
		if (selectional_prefs == null) {
			selectional_prefs = global_selectional_pref_log_probs;
		}

		return Utils.logsumexp(selectional_prefs.get("NONINGOBJ:PREP"), selectional_prefs.get("NONINGOBJ:NOPREP"));
	}

	public double logProbVerbHasNoPrep(String verb) {
		Map<String, Double> selectional_prefs =  verb_to_selectional_pref_log_probs.get(verb);
		if (selectional_prefs == null) {
			selectional_prefs = global_selectional_pref_log_probs;
		}

		return Utils.logsumexp(selectional_prefs.get("INGOBJ:NOPREP"), selectional_prefs.get("NONINGOBJ:NOPREP"));
	}
	
	public double logProbVerbCreateLoc(String verb) {
		Double prob = verb_creates_loc_log_probs.get(verb);
		if (prob == null) {
			return global_creates_loc_log_prob;
		}
		return prob;
	}
	
	public String bestPrefType(String verb) {
		Map<String, Double> selectional_prefs =  verb_to_selectional_pref_log_probs.get(verb);
		if (selectional_prefs == null) {
			selectional_prefs = global_selectional_pref_log_probs;
		}
		double max_prob = Double.NEGATIVE_INFINITY;
		String best_pref = "";
		for (String pref : selectional_prefs.keySet()) {
			Double prob = selectional_prefs.get(pref);
			if (prob > max_prob) {
				max_prob = prob;
				best_pref = pref;
			}
		}
		return best_pref;
	}

	public double getLogProbOfNodePrefs(ActionNode node, SelectionalPreference sp, GraphInfo gi) {
		RecipeEvent event = node.event();
		Map<String, Double> verb_probs = verb_to_selectional_pref_log_probs.get(event.predicate());
		if (verb_probs == null) {
			verb_probs = global_selectional_pref_log_probs;
		}
		return verb_probs.get(sp.pref_type);
	}

	public double getLogProbOfPrefType(String predicate, String pref_type) {
		Map<String, Double> selectional_prefs =  verb_to_selectional_pref_log_probs.get(predicate);
		if (selectional_prefs == null) {
			selectional_prefs = global_selectional_pref_log_probs;
		}
		return selectional_prefs.get(pref_type);
	}
	
	public boolean doesPredMostLikelyHaveIngObj(String predicate) {
		double prob = logProbVerbHasIngObj(predicate);
		return prob >= Math.log(0.5);
	}

	public static SelectionalPreferenceModel readModelFromFile(String filename) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String ing_classifier_filename = br.readLine();
		SelectionalPreferenceModel model = new SelectionalPreferenceModel();
		int num_verbs = Integer.parseInt(br.readLine());
		for (int v = 0; v < num_verbs; v++) {
			String verb = br.readLine();
			Map<String, Double> prep_cnt_probs = new HashMap<String, Double>();
			int num_prefs = Integer.parseInt(br.readLine());
			for (int p = 0; p < num_prefs; p++)	{
				String[] split = br.readLine().split("\t");
				prep_cnt_probs.put(split[0], Double.parseDouble(split[1]));
			}
			model.verb_to_selectional_pref_log_probs.put(verb, prep_cnt_probs);
		}
		int num_prefs = Integer.parseInt(br.readLine());
		for (int p = 0; p < num_prefs; p++)	{
			String[] split = br.readLine().split("\t");
			model.global_selectional_pref_log_probs.put(split[0], Double.parseDouble(split[1]));
		}
		
		String line = br.readLine();
		if (line != null) {
			num_verbs = Integer.parseInt(line);
			for (int v = 0; v < num_verbs; v++) {
				String verb = br.readLine();
				Map<String, Double> prep_cnt_probs = new HashMap<String, Double>();
				num_prefs = Integer.parseInt(br.readLine());
				for (int p = 0; p < num_prefs; p++)	{
					String[] split = br.readLine().split("\t");
					prep_cnt_probs.put(split[0], Double.parseDouble(split[1]));
				}
				model.verb_to_imp_prep_log_probs.put(verb, prep_cnt_probs);
			}
			num_prefs = Integer.parseInt(br.readLine());
			for (int p = 0; p < num_prefs; p++)	{
				String[] split = br.readLine().split("\t");
				model.verb_creates_loc_log_probs.put(split[0], Double.parseDouble(split[1]));
			}
			model.global_creates_loc_log_prob = Double.parseDouble(br.readLine());
		}
		br.close();
		return model;
	}

	public void removePrepDistinction() {
		for (String verb : verb_to_selectional_pref_log_probs.keySet()) {
			Map<String, Double> pref_probs = verb_to_selectional_pref_log_probs.get(verb);
			double inging = pref_probs.get("INGOBJ:PREP");
			double ingnoing = pref_probs.get("INGOBJ:NOPREP");
			double noinging = pref_probs.get("NONINGOBJ:PREP");
			double noingnoing = pref_probs.get("NONINGOBJ:NOPREP");
			double ing = Utils.logsumexp(inging, ingnoing) - Math.log(2.0);
			double noing = Utils.logsumexp(noinging, noingnoing) - Math.log(2.0);
			pref_probs.put("INGOBJ:PREP", ing);
			pref_probs.put("INGOBJ:NOPREP", ing);
			pref_probs.put("NONINGOBJ:PREP", noing);
			pref_probs.put("NONINGOBJ:NOPREP", noing);
		}
		Map<String, Double> pref_probs = global_selectional_pref_log_probs;
		double inging = pref_probs.get("INGOBJ:PREP");
		double ingnoing = pref_probs.get("INGOBJ:NOPREP");
		double noinging = pref_probs.get("NONINGOBJ:PREP");
		double noingnoing = pref_probs.get("NONINGOBJ:NOPREP");
		double ing = Utils.logsumexp(inging, ingnoing) - Math.log(2.0);
		double noing = Utils.logsumexp(noinging, noingnoing) - Math.log(2.0);
		pref_probs.put("INGOBJ:PREP", ing);
		pref_probs.put("INGOBJ:NOPREP", ing);
		pref_probs.put("NONINGOBJ:PREP", noing);
		pref_probs.put("NONINGOBJ:NOPREP", noing);
	}

	public void writeToFile(String filename) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		bw.write(ing_classifier_filename_ + "\n");
		bw.write(verb_to_selectional_pref_log_probs.size() + "\n");
		for (String verb : verb_to_selectional_pref_log_probs.keySet()) {
			bw.write(verb + "\n");
			Map<String, Double> prep_cnt_probs = verb_to_selectional_pref_log_probs.get(verb);
			bw.write(prep_cnt_probs.size() + "\n");
			for (Map.Entry<String, Double> entry : prep_cnt_probs.entrySet()) {
				bw.write(entry.getKey() + "\t" + entry.getValue() + "\n");
			}
		}
		bw.write(global_selectional_pref_log_probs.size() + "\n");
		for (String pref : global_selectional_pref_log_probs.keySet()) {
			bw.write(pref + "\t" + global_selectional_pref_log_probs.get(pref) + "\n");
		}
		bw.write(verb_to_imp_prep_log_probs.size() + "\n");
		for (String verb : verb_to_imp_prep_log_probs.keySet()) {
			bw.write(verb + "\n");
			Map<String, Double> prep_cnt_probs = verb_to_imp_prep_log_probs.get(verb);
			bw.write(prep_cnt_probs.size() + "\n");
			for (Map.Entry<String, Double> entry : prep_cnt_probs.entrySet()) {
				bw.write(entry.getKey() + "\t" + entry.getValue() + "\n");
			}
		}
		bw.write(verb_creates_loc_log_probs.size() + "\n");
		for (String verb : verb_creates_loc_log_probs.keySet()) {
			bw.write(verb + "\t" + verb_creates_loc_log_probs.get(verb) + "\n");
		}
		bw.write(global_creates_loc_log_prob + "\n");
		bw.close();
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("Requires one argument 'train' or 'test'");
		} else if (args[0] == "train") {
			try {
				SelectionalPreferenceModel model = new SelectionalPreferenceModel();
				List<Pair<File, File>> training_files = Utils.getFileList();
				model.initialize(training_files);
				model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_selectional_pref.model");
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(1);
			}
		} else if (args[0] == "test") {
			try {
				SelectionalPreferenceModel model = SelectionalPreferenceModel.readModelFromFile("init_selectional_pref.model");
				
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				while (true) {
					System.out.print("Word ('quit' to quit): ");
					String word = br.readLine();
					if (word.equals("quit")) {
						break;
					}
					System.out.println(model.bestPrefType(word));
				}

				System.out.println(model.logProbVerbCreateLoc("preheat"));
				System.out.println(model.logProbVerbCreateLoc("combine"));
				System.out.println(model.logProbVerbCreateLoc("flour"));
				System.out.println(model.logProbVerbCreateLoc("grease"));
				System.out.println(model.logProbVerbCreateLoc("whisk"));
				System.out.println(model.logProbVerbCreateLoc("spray"));
				System.out.println(Math.log(0.5));
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(1);
			}
		} else {
			System.out.println("Requires one argument 'train' or 'test'");
		}
	}

	public void setVerbLocProbs(Map<String, Double> verb_loc_probs) {
		verb_creates_loc_log_probs = verb_loc_probs;
	}
	
	public void setGlobalLocProb(double prob) {
		global_creates_loc_log_prob = prob;
	}
}
