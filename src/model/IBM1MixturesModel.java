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

import data.ProjectParameters;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;
import edu.stanford.nlp.util.StringUtils;
import utils.Measurements;
import utils.Pair;
import utils.TokenCounter;
import utils.Utils;

public class IBM1MixturesModel {
	private static boolean scale = true;
	private static double default_alpha = 0.01;
	private double alpha = 0.01; 
	private Map<String, Map<String, Double>> mix_probs_given_ing;
	private Set<String> all_ings;

	// 100,000
	
	public IBM1MixturesModel() {
		mix_probs_given_ing = new HashMap<String, Map<String, Double>>();
		all_ings = new HashSet<String>();
	}

	public static String NULL_WORD = "NULL";
	
	public static IBM1MixturesModel learnModelFromData(Set<String> mix_tkns, List<Pair<String, Set<String>>> data) {
		double initial_prob = 1.0 / mix_tkns.size();
		
		IBM1MixturesModel model = new IBM1MixturesModel();
		
		Map<String, Map<String, Double>> mix_given_ing_probs = new HashMap<String, Map<String, Double>>();
		Map<String, Double> ing_count = new HashMap<String, Double>();
		
		for (Pair<String, Set<String>> instance : data) {
			String mixture = instance.getFirst();
			String[] mix_words = mixture.split(" ");
			Set<String> ingredients = instance.getSecond();
			
			Set<String> matched_ings = new HashSet<String>();
			Map<String, Double> sentence_probs = new HashMap<String, Double>();
//			for (String mw : mix_words) {
//				if (!mix_tkns.contains(mw)) {
//					continue;
//				}
//				if (mw.trim().equals("")) {
//					continue;
//				}
//				if (ingredients.contains(mw)) {
//					matched_ings.add(mw);
//					continue;
//				}
//			}
			for (String mw : mix_words) {
				if (!mix_tkns.contains(mw)) {
					continue;
				}
//				if (ingredients.contains(mw)) {
//					continue;
//				}
				Double total_s = sentence_probs.get(mw);
				double total = 0.0;
				if (total_s != null) {
					total = total_s.doubleValue();
				}
				for (String ing : ingredients) {
//					if (!Utils.isMostlyNounInWordNet(ing)) {
//						continue;
//					}
					total += initial_prob;
				}
				total += initial_prob;
//				total += (initial_prob * (ingredients.size() + 1));
				sentence_probs.put(mw, total);
			}

			for (String mw : mix_words) {
				if (!mix_tkns.contains(mw)) {
					continue;
				}
//				if (ingredients.contains(mw)) {
//					continue;
//				}
				for (String ing : ingredients) {
//					if (matched_ings.contains(ing)) {
//						continue;
//					}
//					if (!Utils.isMostlyNounInWordNet(ing)) {
//						continue;
//					}
					double num = initial_prob / sentence_probs.get(mw);
					Utils.incrementStringDoubleMapValueCount(mix_given_ing_probs,
							mw, ing, num);
					Utils.incrementStringDoubleMapCount(ing_count, ing, num);
				}
				double num = initial_prob / sentence_probs.get(mw);
				Utils.incrementStringDoubleMapValueCount(mix_given_ing_probs,
						mw, NULL_WORD, num);
				Utils.incrementStringDoubleMapCount(ing_count, NULL_WORD, num);
			}
 		}
		System.out.println(mix_given_ing_probs.keySet());
		System.out.println(mix_tkns);
		for (String mw : mix_given_ing_probs.keySet()) {
			if (!mix_tkns.contains(mw)) {
				continue;
			}
//			if (ingredients.contains(mw)) {
//				continue;
//			}
			double num = 10000;
			Utils.incrementStringDoubleMapValueCount(mix_given_ing_probs,
					mw, mw, num);
			Utils.incrementStringDoubleMapCount(ing_count, mw, num);
			
		}

		for (String mw : mix_given_ing_probs.keySet()) {
			Map<String, Double> unnorm = mix_given_ing_probs.get(mw);
			Map<String, Double> ing_probs = new HashMap<String, Double>();
			for (String ing : ing_count.keySet()) {
				Double unnorm_prob = unnorm.get(ing);
				if (unnorm_prob != null) {
					double mix_given_ing = unnorm.get(ing) / ing_count.get(ing);
					ing_probs.put(ing, mix_given_ing);
				}
			}
			Double unnorm_prob = unnorm.get(NULL_WORD);
			if (unnorm_prob != null) {
				double mix_given_ing = unnorm.get(NULL_WORD) / ing_count.get(NULL_WORD);
				ing_probs.put(NULL_WORD, mix_given_ing);
			}
			model.mix_probs_given_ing.put(mw, ing_probs);
		}
		
		IBM1MixturesModel new_model = model;
		int iter = 0;
		do {
			System.out.println(iter);
			Pair<IBM1MixturesModel, Boolean> new_model_and_converge = emRound(data, new_model);
			try {
				new_model = new_model_and_converge.getFirst();
//				new_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "ibm_mix_"+ iter + ".txt");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			if (new_model_and_converge.getSecond()) {
				break;
			}
			iter++;
		} while (true);
		return new_model;
	}
	
	public static Pair<IBM1MixturesModel, Boolean> emRound(List<Pair<String, Set<String>>> data,
			IBM1MixturesModel curr_model) {
		Map<String, Map<String, Double>> curr_probs = curr_model.mix_probs_given_ing;
		return emRound(data, curr_probs);
	}
	
	public static Pair<IBM1MixturesModel, Boolean> emRound(List<Pair<String, Set<String>>> data,
			Map<String, Map<String, Double>> curr_probs) {
		
		IBM1MixturesModel model = new IBM1MixturesModel();
		
		Map<String, Map<String, Double>> mix_given_ing_probs = new HashMap<String, Map<String, Double>>();
		Map<String, Double> ing_count = new HashMap<String, Double>();
		
		for (Pair<String, Set<String>> instance : data) {
//			System.out.println(instance);
			String mixture = instance.getFirst();
			String[] mix_words = mixture.split(" ");
			Set<String> ingredients = instance.getSecond();
			Set<String> matched_ingredients = new HashSet<String>();
			
			Map<String, Double> sentence_probs = new HashMap<String, Double>();
			for (String mw : mix_words) {
				if (mw.trim().equals("")) {
					continue;
				}
			}
			for (String mw : mix_words) {
				Map<String, Double> mix_probs = curr_probs.get(mw);
				if (mix_probs == null) {
					continue;
				}
				if (mw.trim().equals("")) {
					continue;
				}
				Double total_s = sentence_probs.get(mw);
				double total = 0.0;
				if (total_s != null) {
					total = total_s.doubleValue();
				}
				for (String ing : ingredients) {
					Double ing_prob = Math.exp(mix_probs.get(ing));
//					System.out.println(ing);
					total += ing_prob;
				}
				Double ing_prob = Math.exp(mix_probs.get(NULL_WORD));
				total += ing_prob;
				sentence_probs.put(mw, total);
			}
//			
			for (String mw : mix_words) {
				Map<String, Double> mix_probs = curr_probs.get(mw);
				if (mix_probs == null) {
					continue;
				}
				if (mw.trim().equals("")) {
					continue;
				}
				
				for (String ing : ingredients) {
					double num = Math.exp(mix_probs.get(ing).doubleValue()) / sentence_probs.get(mw);
					Utils.incrementStringDoubleMapValueCount(mix_given_ing_probs,
							mw, ing, num);
					Utils.incrementStringDoubleMapCount(ing_count, ing, num);
				}
				double num = Math.exp(mix_probs.get(NULL_WORD).doubleValue()) / sentence_probs.get(mw);
				Utils.incrementStringDoubleMapValueCount(mix_given_ing_probs,
						mw, NULL_WORD, num);
				Utils.incrementStringDoubleMapCount(ing_count, NULL_WORD, num);
			}
 		}
		for (String mw : mix_given_ing_probs.keySet()) {
			double num = 10000;
			Utils.incrementStringDoubleMapValueCount(mix_given_ing_probs,
					mw, mw, num);
			Utils.incrementStringDoubleMapCount(ing_count, mw, num);
		}
		
		double converge = 0.001;
		boolean converged = true;
		for (String mw : mix_given_ing_probs.keySet()) {
			Map<String, Double> unnorm = mix_given_ing_probs.get(mw);
			Map<String, Double> curr = curr_probs.get(mw);
			Map<String, Double> ing_probs = new HashMap<String, Double>();
			for (String ing : ing_count.keySet()) {
				Double unnorm_prob = unnorm.get(ing);
				if (unnorm_prob != null) {
					double mix_given_ing = unnorm.get(ing) / ing_count.get(ing);
					ing_probs.put(ing, mix_given_ing);

					Double curr_prob = curr.get(ing);
					if (Math.abs(curr_prob - mix_given_ing) > converge) {
						converged = false;
					}
				}
			}
			model.mix_probs_given_ing.put(mw, ing_probs);
		}
		return new Pair<IBM1MixturesModel, Boolean>(model, converged);
	}
	
	public static IBM1MixturesModel learnModel2() {
		try {
		IBM1MixturesModel start = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "ibm_mix_init.txt");
		BufferedReader br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_mixtures_data.txt"));
		String line = null;
		List<Pair<String, Set<String>>> data = new ArrayList<Pair<String, Set<String>>>();
		while ((line = br.readLine()) != null) {
			int t = line.indexOf('\t');
			String mix = line.substring(0, t);
			String ing_str = line.substring(t + 1);
			String[] split = ing_str.split(" ");
			Set<String> ings = new HashSet<String>();
			for (String s : split) {
				if (s.trim().equals("")) {
					continue;
				}
				ings.add(s);
			}
			data.add(new Pair<String, Set<String>>(mix, ings));
		}
		br.close();
		IBM1MixturesModel new_model = start;
		int iter = 0;
		do {
			System.out.println(iter);
			Pair<IBM1MixturesModel, Boolean> new_model_and_converge = emRound(data, new_model);
			try {
				new_model = new_model_and_converge.getFirst();
				new_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "ibm_mix_"+ iter + ".txt");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			if (new_model_and_converge.getSecond()) {
				break;
			}
			iter++;
		} while (true);
		return new_model;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
	
	public static void writeDataToFile(List<Pair<String, Set<String>>> data, String filename) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
			for (Pair<String, Set<String>> pair : data) {
				bw.write(pair.getFirst() + "\t");
				for (String ing : pair.getSecond()) {
					bw.write(ing + " ");
				}
				bw.write("\n");
			}
			bw.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void writeToFile(String filename) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		bw.write(alpha + "\n");
		bw.write(mix_probs_given_ing.size() + "\n");
		for (String mix_word : mix_probs_given_ing.keySet()) {
			bw.write(mix_word + "\n");
			Map<String, Double> ing_probs = mix_probs_given_ing.get(mix_word);
			bw.write(ing_probs.size() + "\n");
			for (String ing : ing_probs.keySet()) {
				bw.write(ing + "\t" + ing_probs.get(ing) + "\n");
			}
		}
		bw.close();
	}

	public static IBM1MixturesModel readModelFromFile(String filename) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(filename));
		IBM1MixturesModel model = new IBM1MixturesModel();
		model.alpha = Double.parseDouble(br.readLine());
		int size = Integer.parseInt(br.readLine());
		for (int i = 0; i < size; i++) {
			String mix_word = br.readLine();
			Map<String, Double> ing_probs = new HashMap<String, Double>();
			int num_ings = Integer.parseInt(br.readLine());
			for (int j = 0; j < num_ings; j++) {
				String line = br.readLine();
				String[] split = line.split("\t");
				double prob = Double.parseDouble(split[1]);
				ing_probs.put(split[0], prob);
				model.all_ings.add(split[0]);
			}
			model.mix_probs_given_ing.put(mix_word, ing_probs);
		}
		br.close();
		return model;
	}
	
	public Pair<String, Double> getBestMix(Set<String> ings) {
		double max_lprob = Double.NEGATIVE_INFINITY;
		String best_str = "";
		for (String mix : mix_probs_given_ing.keySet()) {
			HashSet<String> mix_set = new HashSet<String>();
			mix_set.add(mix);
			double lprob = logProbOfMixGivenIngs("", StringUtils.join(ings), mix_set);
			if (lprob > max_lprob) {
				max_lprob = lprob;
				best_str = mix;
			}
		}

		return new Pair<String, Double>(best_str, max_lprob);
	}
	
	
	public double logProbOfMixGivenIngs(String predicate, String mixes, Set<String> ings) {
		double lprob = 0.0;
		String[] mix_split = mixes.split(" ");
		int num_real_mixes = 0;
		
		int num_ings = ings.size();
		for (String mix : mix_split) {
			Map<String, Double> ing_probs = mix_probs_given_ing.get(mix);
			if (ing_probs != null) {
				num_real_mixes++;
			}
		}
		if (num_real_mixes == 0) {
			return 0.0;
		}
		int num_real_ings = 0;
		double factor = -1 * Math.log(Math.pow(1.0 + num_ings, num_real_mixes));
		for (String mix : mix_split) {
			double full_ing_prob = 0.0;
			for (String ing : ings) {
				if (!all_ings.contains(ing)) {
					continue;
				}
				Map<String, Double> ing_probs = mix_probs_given_ing.get(mix);
				if (ing_probs == null) {
					continue;
				}
				Double ing_prob = ing_probs.get(ing);
				if (ing_prob != null) {
					full_ing_prob += ing_prob;
				}
			}
			Map<String, Double> ing_probs = mix_probs_given_ing.get(mix);
			if (ing_probs == null) {
				continue;
			}
			Double ing_prob = ing_probs.get(NULL_WORD);
			if (ing_prob != null) {
				full_ing_prob += ing_prob;
			}
			if (full_ing_prob == 0.0) {
				factor += Math.log(0.0000000000001);
			} else {
				factor += Math.log(full_ing_prob);
			}
		}

		if (scale) {
			return Math.log(Math.pow(Math.exp(factor), 1.0/(num_ings + 1)));
		}
		return factor;
	}
	
	public double logProbOfMixGivenIngs2(String predicate, String mixes, Set<String> ings) {
		double lprob = 0.0;
		String[] mix_split = mixes.split(" ");
		int num_real_mixes = 0;
		
		int num_ings = ings.size();
		for (String mix : mix_split) {
			Map<String, Double> ing_probs = mix_probs_given_ing.get(mix);
			if (ing_probs != null) {
				num_real_mixes++;
			}
		}
		if (num_real_mixes == 0) {
			return 0.0;
		}
		int num_real_ings = 0;
		double max = 0.0;
		double factor = -1 * Math.log(Math.pow(1.0 + num_ings, num_real_mixes));
//		System.out.println("factor: " + factor);
		for (String mix : mix_split) {
			double full_ing_prob = 0.0;
			for (String ing : ings) {
				if (!all_ings.contains(ing)) {
					continue;
				}
				Map<String, Double> ing_probs = mix_probs_given_ing.get(mix);
				if (ing_probs == null) {
					continue;
				}
				Double ing_prob = ing_probs.get(ing);
				if (ing_prob != null) {
					if (max < ing_prob) {
						max = ing_prob;
					}
					full_ing_prob += ing_prob;
				}
//				System.out.println(ing + " " + mix + " " + ing_prob);
			}
			Map<String, Double> ing_probs = mix_probs_given_ing.get(mix);
			if (ing_probs == null) {
				continue;
			}
			Double ing_prob = ing_probs.get(NULL_WORD);
			if (ing_prob != null) {
				full_ing_prob += ing_prob;
			}
//			System.out.println(NULL_WORD + " " + mix + " " + ing_prob);
			if (full_ing_prob == 0.0) {
				factor += Math.log(0.0000000000001);
			} else {
				factor += Math.log(full_ing_prob);
			}
		}

		if (max == 0.0) {
			return Math.log(0.0000000000001);
		}
		return Math.log(max);
	}
	
	

	public static void main(String[] args) {
		try {
			BufferedReader token_br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + ProjectParameters.TOKEN_FILE));
			String line = null;
			while ((line = token_br.readLine()) != null) {
				TokenCounter.token_counts_.put(line, 1);
			}
			token_br.close();

			BufferedReader noun_br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + ProjectParameters.NOUN_FILE));
			line = null;
			while ((line = noun_br.readLine()) != null) {
				TokenCounter.nouns_.add(line);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		boolean train = false;
		if (train) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "loc_data_1hardem_cam.txt"));
				String line = null;
				List<Pair<String, Set<String>>> data = new ArrayList<Pair<String, Set<String>>>();
				Set<String> mix_tkns = new HashSet<String>();
				Set<String> ing_tkns = new HashSet<String>();
				while ((line = br.readLine()) != null) {
					int t = line.indexOf('\t');
					String mix = line.substring(0, t);
					String[] mixes = mix.split(" ");
					String new_mix = "";
					Set<String> mix_set = new HashSet<String>();
					for (String m : mixes) {
						if (m.trim().equals("")) {
							continue;
						}
						if (m.length() < 3) {
							continue;
						}
						if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(m)) {
							continue;
						}
						mix_tkns.add(Utils.stem(m));
						new_mix += Utils.stem(m) + " ";
						mix_set.add(Utils.stem(m));
					}
					String ing_str = line.substring(t + 1);
					String[] split = ing_str.split(" ");
					Set<String> ings = new HashSet<String>();
					for (String s : split) {
						if (s.trim().equals("")) {
							continue;
						}
						ings.add(Utils.stem(s));
					}
					ing_tkns.addAll(ings);
					if (!new_mix.trim().equals("")) {
//						data.add(new Pair<String, Set<String>>(new_mix.trim(), ings));
						data.add(new Pair<String, Set<String>>(StringUtils.join(ings), mix_set));
					}
				}
				br.close();
				br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "p10loc_data_1hardem.txt"));
				while ((line = br.readLine()) != null) {
					int t = line.indexOf('\t');
					String mix = line.substring(0, t);
					String[] mixes = mix.split(" ");
					String new_mix = "";
					Set<String> mix_set = new HashSet<String>();
					for (String m : mixes) {
						if (m.trim().equals("")) {
							continue;
						}
						if (m.length() < 3) {
							continue;
						}
						if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(m)) {
							continue;
						}
						mix_tkns.add(Utils.stem(m));
						new_mix += Utils.stem(m) + " ";
						mix_set.add(Utils.stem(m));
					}
					String ing_str = line.substring(t + 1);
					String[] split = ing_str.split(" ");
					Set<String> ings = new HashSet<String>();
					for (String s : split) {
						if (s.trim().equals("")) {
							continue;
						}
						ings.add(Utils.stem(s));
					}
					ing_tkns.addAll(ings);
					if (!new_mix.trim().equals("")) {
//						data.add(new Pair<String, Set<String>>(new_mix.trim(), ings));
						data.add(new Pair<String, Set<String>>(StringUtils.join(ings), mix_set));
					}
				}
				br.close();
				IBM1MixturesModel model = IBM1MixturesModel.learnModelFromData(ing_tkns, data);
				model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "p10rev_mix_and_loc_model_1hardem.model");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} else {
			try {
				IBM1MixturesModel model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "mix_model_4hardem99_cam_10000.model");
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
				while (true) {
					System.out.print("Ing: ");
					String ing = br.readLine();
					if (ing.equals("quit")) {
						return;
					}
					ing = Utils.stem(ing);
					SortedSet<Pair<String, Double>> sorted = new TreeSet<Pair<String, Double>>(
							new Comparator<Pair<String, Double> >() {
								public int compare(Pair<String, Double> e1, Pair<String, Double> e2) {
									int cmp = e2.getSecond().compareTo(e1.getSecond());
									if (cmp != 0) {
										return cmp;
									}
									return e1.getFirst().compareTo(e2.getFirst());
								}
							});
					for (String mixword : model.mix_probs_given_ing.keySet()) {
						Map<String, Double> probs = model.mix_probs_given_ing.get(mixword);
						if (probs.get(ing) == null) {
							continue;
						}
						
						sorted.add(new Pair<String, Double>(mixword, probs.get(ing)));
					}
					int i = 0;
					for (Pair<String, Double> pair : sorted) {
						System.out.println(pair);
						i++;
						if (i > 20) {
							break;
						}
					}
				}

			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
