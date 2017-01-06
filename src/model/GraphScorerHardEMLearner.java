package model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.Connections.Connection;
import model.SelectionalPreferenceModel.SelectionalPreference;
import utils.Pair;
import utils.Utils;
import data.ActionDiagram;
import data.RecipeSentenceSegmenter;
import data.ProjectParameters;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;

/***
 * Trains the recipe parsing model.
 * 
 * To run:
 * java GraphScorerHardEMLearner num_iterations_to_run chunker_model_file any_string_version_information_for_model_names
 * @author chloe
 *
 */
public class GraphScorerHardEMLearner {

	private static double alpha = 0.01;

	public static void main(String args[]) {
		int num_iters = Integer.parseInt(args[0]);
		String chunker_model_file = args[1];
		String version_info = "";
		if (args.length == 3) {
			version_info = args[2];
		}
		try {
			RecipeSentenceSegmenter chunker = RecipeSentenceSegmenter.readInFromFile(chunker_model_file);
			for (int iter = 0; iter < num_iters; iter++) {
				ThreeWayStringClassifier str_classifier = null;
				SelectionalPreferenceModel selectional_preference_model = null;
				RevSelectionalPreferenceModel rev_selectional_preference_model = null;
				IBM1MixturesModel mixtures_model = null;
				ConnectionsModel conn_model = null;
				if (iter == 0) {
						str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_string_classifier_stem_lastloc_nouns.model");
						selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_selectional_pref.model");
						rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "init_rev_selectional_pref_leaf2.model");
						conn_model = new ConnectionsModel();	
				} else {
					str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "str_classifier_" + iter + ".model");
					rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "rev_pref_model_" + iter + ".model");
					selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "pref_model_" + iter + ".model");
					mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "mix_model_" + iter + ".model");
					conn_model = ConnectionsModel.readFromFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "conn_model_" + iter + ".model");
				}
				GraphScorer scorer = new GraphScorer(chunker, str_classifier, selectional_preference_model, 
						rev_selectional_preference_model, mixtures_model, conn_model);

				List<Pair<File, File>> dev_files = Utils.getFileList();
				/*
				 *  files[0] = ann_file;
				files[1] = arg_file;
				files[2] = step_file;
				files[3] = fulltext_file;
				 */
				ThreeWayStringClassifier new_str_classifier = new ThreeWayStringClassifier();
				List<Pair<String, Set<String>>> mix_part_pairs = new ArrayList<Pair<String, Set<String>>>();
				List<Pair<String, Set<String>>> loc_part_pairs = new ArrayList<Pair<String, Set<String>>>();
				Set<String> mix_tkns = new HashSet<String>();
				Set<String> loc_tkns = new HashSet<String>();
				Map<String, Integer> verb_cnt = new HashMap<String, Integer>();
				Map<String, Map<String, Integer>> pref_cnt = new HashMap<String, Map<String, Integer>>();
				Map<String, Map<String, Integer>> imp_prep_cnt = new HashMap<String, Map<String, Integer>>();
				Map<String, Integer> loc_cnt = new HashMap<String, Integer>();
				Map<String, Integer> verb_origin_cnt = new HashMap<String, Integer>();
				int num_imp_loc_edges = 0;
				int imp_loc_edge_close_sum = 0;
				int num_evo_loc_edges = 0;
				int evo_loc_edge_close_sum = 0;
				int num_imp_ing_edges = 0;
				int imp_ing_edge_close_sum = 0;
				int num_evo_ing_edges = 0;
				int evo_ing_edge_close_sum = 0;
				int num_outputs = 0;
				int num_output_edges = 0;
				ConnectionsModelLearner conn_model_learner = new ConnectionsModelLearner();
				RevSelectionalPreferenceModelLearner rev_model_learner = new RevSelectionalPreferenceModelLearner();
				int num_sentences = 0;
				Set<String> seen_dev_files = new HashSet<String>();

				for (int i = 0; i < dev_files.size(); i++) {
					Pair<File, File> instance = dev_files.get(i);
					ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromFile(instance.getFirst(), instance.getSecond(), false, false);
					System.out.println(ad.recipeName());

					if (seen_dev_files.contains(ad.recipeName())) {
						continue;
					}
					seen_dev_files.add(ad.recipeName());

					num_sentences += ad.num_sentences;
					if (ad.numNodes() < 2) {
						continue;
					}

					GraphInfo gi = new GraphInfo(ad, true, true, scorer.str_classifier, scorer.selectional_pref_model);
					System.out.println(gi.visible_arg_to_type_);
					scorer.changeGraph(gi);
					gi.update(scorer);
					LinearGraphInfoInitializer.initialize(gi, scorer, true, true);
					scorer.initNodeScores();
					LocalSearcher searcher = new LocalSearcher(gi, scorer);
					searcher.initialize();
					searcher.run();
					gi.commitConnections();

					new_str_classifier.addData(gi);
					conn_model_learner.addData(gi);
					rev_model_learner.addData(gi);

					Pair<List<Pair<String, Set<String>>>, Set<String>> pairs = gi.collectMixPartPairs();
					mix_part_pairs.addAll(pairs.getFirst());
					mix_tkns.addAll(pairs.getSecond());
					pairs = gi.collectMixLocPartPairs();
					loc_part_pairs.addAll(pairs.getFirst());
					loc_tkns.addAll(pairs.getSecond());

					for (int n = 0; n < ad.numNodes(); n++) {
						ActionNode node = ad.getNodeAtIndex(n);
						SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
						String pred = node.event().predicate(); 
						String pref_type = pref.pref_type;

						if (pref_type.endsWith(":LEAF")) {
							pref_type = pref_type.substring(0, pref_type.length() - 5);
						}
						Utils.incrementStringMapCount(verb_cnt, pred);
						Utils.incrementStringMapValueCount(pref_cnt, pred, pref_type);

						for (Argument arg : pref.arg_arr) {
							if (arg.type() != Type.OBJECT) { 
								if (arg.string().equals("")) {
									Utils.incrementStringMapValueCount(imp_prep_cnt, pred, "IMP");
								} else {
									Utils.incrementStringMapValueCount(imp_prep_cnt, pred, "EVO");
								}
							}
							for (String span : arg.nonIngredientSpans()) {
								ActionNode origin = gi.getOrigin(node, arg, span);
								if (origin != null) {
									int close = node.index() - origin.index();
									if (span.equals("")) {
										num_imp_ing_edges++;
										imp_ing_edge_close_sum += close;
									} else {
										num_evo_ing_edges++;
										evo_ing_edge_close_sum += close;
									}
									Utils.incrementStringMapCount(verb_origin_cnt, origin.event().predicate());
								}
							}
						}

						Argument loc = pref.loc;
						if (loc != null) {
							ActionNode origin = gi.getOrigin(node, loc, loc.string());
							if (origin != null) {
								int close = node.index() - origin.index();
								if (loc.string().equals("")) {
									num_imp_loc_edges++;
									imp_loc_edge_close_sum += close;
								} else {
									num_evo_loc_edges++;
									evo_loc_edge_close_sum += close;
								}
								Utils.incrementStringMapCount(verb_origin_cnt, origin.event().predicate());
								Utils.incrementStringMapCount(loc_cnt, origin.event().predicate());
							}
						}

						Set<Connection> dests = gi.dest_set(node);
						if (n != ad.numNodes() - 1) {
							num_output_edges += dests.size();
							num_outputs++;
						}
					}
				}
				System.out.println(pref_cnt.get("add"));
				System.out.println(num_sentences);
				System.out.println("recipes: " + seen_dev_files.size());
				if (true) {
					ConnectionsModel new_conn_model = conn_model_learner.compute();
					new_conn_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "conn_model_" + (iter+1) + ".model");
					RevSelectionalPreferenceModel rev_pref_model = rev_model_learner.computeModel();
					rev_pref_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "rev_pref_model_" + (iter+1) + ".model");
					IBM1MixturesModel.writeDataToFile(mix_part_pairs, ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "mix_data_" + (iter+1) + ".model");
					IBM1MixturesModel.writeDataToFile(loc_part_pairs, ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "loc_data_" + (iter+1) + ".model");
					IBM1MixturesModel new_mix_model = IBM1MixturesModel.learnModelFromData(mix_tkns, mix_part_pairs);
					new_mix_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "mix_model_" + (iter+1) + ".model");
					IBM1MixturesModel new_loc_model = IBM1MixturesModel.learnModelFromData(loc_tkns, loc_part_pairs);
					new_loc_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "loc_model_" + (iter+1) + ".model");
					mix_part_pairs.addAll(loc_part_pairs);
					mix_tkns.addAll(loc_tkns);
					IBM1MixturesModel new_mix_and_loc_model = IBM1MixturesModel.learnModelFromData(mix_tkns, mix_part_pairs);
					new_mix_and_loc_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "mix_and_loc_model_" + (iter+1) + ".model");

					new_str_classifier.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "str_classifier_" + (iter+1) + ".model");

					SelectionalPreferenceModel new_pref_model = new SelectionalPreferenceModel();
					Map<String, Integer> global_cnts = new HashMap<String, Integer>();
					Map<String, Map<String, Double>> probs = new HashMap<String, Map<String, Double>>();
					Map<String, Map<String, Double>> imp_probs = new HashMap<String, Map<String, Double>>();
					Map<String, Double> global_probs = new HashMap<String, Double>();
					Map<String, Double> verb_loc_probs = new HashMap<String, Double>();
					int global_total_cnt = 0;
					int ttl_loc = 0;
					int ttl = 0;
					for (String verb : verb_cnt.keySet()) {
						Integer ttl_cnt = verb_cnt.get(verb);
						Map<String, Integer> pref_cnts = pref_cnt.get(verb);
						Map<String, Double> pref_probs = new HashMap<String, Double>();
						
						for (String pref : SelectionalPreferenceModel.possible_prefs) {
							Integer cnt = pref_cnts.get(pref);
							if (cnt == null) {
								pref_probs.put(pref, Math.log(alpha) - Math.log(ttl_cnt + SelectionalPreferenceModel.possible_prefs.length));
							} else {
								Utils.incrementStringMapCount(global_cnts, pref, cnt);
								pref_probs.put(pref, Math.log(cnt + alpha) - Math.log(ttl_cnt + SelectionalPreferenceModel.possible_prefs.length));
							}
						}
						Map<String, Integer> pred_imp_cnt = imp_prep_cnt.get(verb);
						Map<String, Double> verb_imp_probs = new HashMap<String, Double>();
						if (pred_imp_cnt == null) {
							verb_imp_probs.put("IMP", Math.log(0.5));
							verb_imp_probs.put("EVO", Math.log(0.5));
						} else {

							Integer imp_cnt = pred_imp_cnt.get("IMP");
							if (imp_cnt == null) {
								imp_cnt = 0;
							}
							Integer evo_cnt = pred_imp_cnt.get("EVO");
							if (evo_cnt == null) {
								evo_cnt = 0;
							}
							double denom = Math.log(imp_cnt + evo_cnt + (2*alpha));
							verb_imp_probs.put("IMP", Math.log(imp_cnt + alpha) - denom);
							verb_imp_probs.put("EVO", Math.log(evo_cnt + alpha) - denom);
						}

						probs.put(verb, pref_probs);
						imp_probs.put(verb, verb_imp_probs);

						Integer o_cnt = verb_origin_cnt.get(verb);
						if (o_cnt != null) {
							ttl += o_cnt;
							Integer l_cnt = loc_cnt.get(verb);
							if (l_cnt == null) {
								verb_loc_probs.put(verb, Math.log(alpha) - Math.log(o_cnt + (2.0*alpha)));
							} else {
								ttl_loc += l_cnt;
								verb_loc_probs.put(verb, Math.log(l_cnt + alpha) - Math.log(o_cnt + (2.0*alpha)));
							}
						}
					}
					double denom = -1 * Math.log(global_total_cnt + alpha*SelectionalPreferenceModel.possible_prefs.length);
					for (String possible_pref : SelectionalPreferenceModel.possible_prefs) {
						Integer cnt = global_cnts.get(possible_pref);
						if (cnt != null) {
							global_probs.put(possible_pref, Math.log(cnt.intValue() + alpha) + denom);
						} else {
							global_probs.put(possible_pref, Math.log(alpha) + denom);
						}
					}
					new_pref_model.setGlobalPrefProbs(global_probs);
					new_pref_model.setVerbPrefProbs(probs);
					new_pref_model.setVerbImpPrepProbs(imp_probs);
					new_pref_model.setVerbLocProbs(verb_loc_probs);
					new_pref_model.setGlobalLocProb(Math.log(ttl_loc + alpha) - Math.log(ttl + (2*alpha)));
					new_pref_model.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + version_info + "pref_model_" + (iter+1) + ".model");
					System.out.println("imp loc: " + ((double)num_imp_loc_edges / (double)imp_loc_edge_close_sum));
					System.out.println("evo loc: " + ((double)num_evo_loc_edges / (double)evo_loc_edge_close_sum));
					System.out.println("imp ing: " + ((double)num_imp_ing_edges / (double)imp_ing_edge_close_sum));
					System.out.println("evo ing: " + ((double)num_evo_ing_edges / (double)evo_ing_edge_close_sum));
					System.out.println("num outputs: " + ((double)num_outputs / (double)num_output_edges));
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
