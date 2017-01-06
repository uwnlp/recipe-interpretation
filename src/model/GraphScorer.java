package model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import model.Connections.Connection;
import model.ConnectionsModel.CONN_TYPE;
import model.SelectionalPreferenceModel.SelectionalPreference;
import utils.Measurements;
import utils.Pair;
import utils.Utils;
import data.ActionDiagram;
import data.ActionDiagram.ActionNode;
import data.RecipeSentenceSegmenter;
import data.RecipeEvent;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;
import edu.stanford.nlp.util.StringUtils;

// Model for scoring action graphs.
public class GraphScorer {

	private GraphInfo gi;
	private RecipeSentenceSegmenter chunker;
	public ThreeWayStringClassifier str_classifier;
	public SelectionalPreferenceModel selectional_pref_model;
	public RevSelectionalPreferenceModel rev_selectional_pref_model;
	public ConnectionsModel connection_model;
	private IBM1MixturesModel mixtures_model;
	private Map<Integer, Double> node_score;
	
	public static boolean VERBOSE = false;

	private static boolean USE_PRED_PROB = false;

	public GraphScorer(GraphInfo gi, RecipeSentenceSegmenter chunker, ThreeWayStringClassifier str_classifier, 
			SelectionalPreferenceModel selectional_pref_model, 
			RevSelectionalPreferenceModel rev_selectional_pref_model, IBM1MixturesModel mix_model,
			ConnectionsModel conn_model) {
		this.gi = gi;
		this.chunker = chunker;
		this.str_classifier = str_classifier;
		this.selectional_pref_model = selectional_pref_model;
		this.rev_selectional_pref_model = rev_selectional_pref_model;
		this.mixtures_model = mix_model;
		this.connection_model = conn_model;
		node_score = new HashMap<Integer, Double>();
		initNodeScores();
	}

	public GraphScorer(RecipeSentenceSegmenter chunker, ThreeWayStringClassifier str_classifier, 
			SelectionalPreferenceModel selectional_pref_model, 
			RevSelectionalPreferenceModel rev_selectional_pref_model, IBM1MixturesModel mix_model,
			ConnectionsModel conn_model) {
		this.gi = null;
		this.chunker = chunker;
		this.str_classifier = str_classifier;
		this.selectional_pref_model = selectional_pref_model;
		this.rev_selectional_pref_model = rev_selectional_pref_model;
		this.mixtures_model = mix_model;
		this.connection_model = conn_model;
		node_score = new HashMap<Integer, Double>();
	}

	public GraphInfo graphInfo() {
		return gi;
	}

	public void changeGraph(GraphInfo new_gi) {
		gi = new_gi;
		node_score.clear();
	}

	public double getConnectionsLogProb() {
		double lprob = Math.log(0.5);
		ActionDiagram ad = gi.actionDiagram();
		
//	
		double pref_prob = 0.0;
		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			RecipeEvent event = node.event();
			SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
			pref_prob += rev_selectional_pref_model.lprobPrefPrior(pref.pref_type);
			for (Argument arg : pref.arg_arr) {
				if (arg == pref.loc) {
					continue;
				}
				for (String span : arg.nonIngredientSpans()) {
					ActionNode origin = gi.connections_.getOrigin(node, arg, span);
					if (origin == null) {
						pref_prob += Math.log(connection_model.good_conn);
					}
				}
			}
			Argument loc = pref.loc;
			if (loc != null && gi.connections_.getOrigin(node, loc, loc.string()) == null) {
				pref_prob += Math.log(connection_model.good_conn);
			}

			pref_prob += pref.other_args.size() * Math.log(connection_model.good_conn);
			
			if (n != ad.numNodes() - 1) {
				Set<Connection> dests = gi.dest_set(node);
				if (dests == null || dests.size() == 0) {
					lprob += -100000000000.0;
				} else {
					int num_outputs = dests.size();
					pref_prob += Math.log(connection_model.good_conn);
					pref_prob += (num_outputs - 1) * Math.log(1.0 - connection_model.good_conn);
					for (Connection conn : dests) {
						int closeness = conn.destination.getFirst().index() - conn.origin.index();
						double sum = LinearDistribution.getSum(conn.destination.getFirst().index() + 1);
						if (conn.type != CONN_TYPE.FOOD && conn.type != CONN_TYPE.LOC) {
							lprob += Math.log(connection_model.otherConnProb());
						}
					}
				}
			}
		}
		return pref_prob;
	}

	public double getTextGivenConnectionsLogProb(ActionNode node) {
		double lprob = 0.0;
		RecipeEvent event = node.event();
		String predicate = event.predicate();
		SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
		
		lprob += rev_selectional_pref_model.lprobVerbGivenPref(predicate, pref.pref_type);

		boolean imp_prep = false;
		for (int i = 0; i < pref.arg_arr.length; i++) {
			Argument arg = pref.arg_arr[i];
			if (arg == pref.loc) {
				continue;
			} else {
				if (arg.type() != Type.OBJECT && arg.string().equals("")) {
					imp_prep = true;
				}
				lprob += getArgTypeScore(node, arg, CONN_TYPE.FOOD);
				for (String span : arg.nonIngredientSpans()) {
					Connection connection = gi.connections_.getConnection(node, arg, span);
//					System.out.println("span: " + span + "  " + connection);
					if (connection == null) {
//						lprob += getClosenessLogProb(null, node, span, CONN_TYPE.FOOD);
						
						// NEW LINE
//						lprob += connection_model.new_closeness_prob(node.index());
						
						if (span.trim().equals("")) {
							// nothing
//							lprob += Math.log(0.5);
							lprob += Double.NEGATIVE_INFINITY;
						} else {
							Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
							String[] split = amt_and_ing.getSecond().split(" ");
							if (split.length == 0) {
								split = new String[]{span};
							}
							boolean found = false;
							boolean other = false;
							for (String s : split) {
								if (gi.all_ing_phrases_.contains(Utils.stem(s))) {
									if (VERBOSE)
									System.out.println(found + " " + amt_and_ing.getSecond() + " " + s);
									found = true;
								} else {
									other = true;
								}
							}
							HashSet<String> stems = new HashSet<String>();
							for (String s : split) {
								if (s.length() < 3) {
									continue;
								}
								if (!Utils.isMostlyNounInWordNet(s)) {
									continue;
								}
								String stem = Utils.stem(s);
								stems.add(stem);
							}
//							if (found && !other) {
////								lprob += Math.log(0.0000000000001);
//								lprob += -100;
//							} else {
//								lprob += str_classifier.lprobPhraseIsRawGivenIng(span, true);
							// NEW AUG
							lprob += connection_model.impFoodProb();
							//
								lprob += str_classifier.lprobPhraseGivenRaw(Utils.stem(split[split.length - 1]), false);
								if (VERBOSE)
								System.out.println(span + " " + (Math.log(0.9999999999999) + str_classifier.lprobPhraseIsRawGivenIng(span, true)));
//							}
//							if (mixtures_model != null) {
//								lprob += str_classifier.lprobPhraseIsRawGivenIng(span);
//							} else {
//								lprob += Math.log(0.0001);
//							}
							//							System.out.println(span + " " + str_classifier.lprobPhraseIsRawGivenIng(span));
						}
					} else {
//						lprob += getArgTypeScore(node, arg, span, connection.origin, CONN_TYPE.FOOD);
//						lprob += getClosenessLogProb(connection.origin, node, span, CONN_TYPE.FOOD);
						// NEW IF STATEMENT
						if (span.equals("") && (node.index() - connection.origin.index() != 1)) {
							lprob += Double.NEGATIVE_INFINITY;
						}

						CONN_TYPE origin_type = CONN_TYPE.FOOD;
						Set<String> ings = gi.getIngSpansForNode(connection.origin);
						if (ings == null || ings.size() == 0) {
							origin_type = CONN_TYPE.LOC;
						}
						Set<Connection> orig_origs = gi.connections_.getIncomingConnectionsToNode(connection.origin);
						if (gi.visible_arg_to_type_.get(node.event().dobj()) == CONN_TYPE.LOC) {
							if (orig_origs == null || orig_origs.size() == 0) {
									origin_type = CONN_TYPE.LOC;
							} else {
								boolean found = false;
								for (Connection o : orig_origs) {
									if (o.type == CONN_TYPE.FOOD) {
										found = true;
										break;
									}
								}
								if (!found) {
									origin_type = CONN_TYPE.LOC;
								}
							}
						}
//						System.out.println("origin type: " + origin_type);
						if (origin_type == CONN_TYPE.LOC) {
							lprob += -100;
						} else {
							lprob += Math.log(1.0 - Math.exp(-100));
						}
						if (!span.equals("")) {
							Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
							String[] split = amt_and_ing.getSecond().split(" ");
							boolean match = false;
							int match_count = 0;
							if (VERBOSE)
							System.out.println("span: " + span + " " + gi.visible_arg_to_type_.get(arg)
									+ " " + connection.type);
							HashSet<String> stems = new HashSet<String>();
							for (String s: split) {
								if (s.length() < 3) {
									continue;
								}
								if (!Utils.isMostlyNounInWordNet(s)) {
									continue;
								}
								String stem = Utils.stem(s);
								stems.add(stem);
								if (ings.contains(stem)) {
									match = true;
									match_count++;
								}
							}
							if (mixtures_model == null) {
//								if (match) {
//									lprob += Math.log(0.9999);
//								} else {
//									lprob += Math.log(0.0001);
//								}
								if (match_count > 0) {
									lprob += Math.log(connection_model.evoFoodProb()) + Math.log(0.99);
//									lprob += Math.log(connection_model.evoFoodProb()) + (match_count * Math.log(0.99));
//									if (ings.size() != match_count) {
//										lprob += (stems.size() - match_count) * Math.log(0.01);
//									}
									if (VERBOSE)
										System.out.println(span + " " + ings + " " + match);
								} else {
									double prob = Math.log(connection_model.evoFoodProb());
									
									prob +=  str_classifier.lprobPhraseGivenMix(Utils.stem(split[split.length - 1]), false);
//									for (String stem : stems) {
//										prob +=  str_classifier.lprobPhraseGivenMix(stem, false);
//									}
									if (VERBOSE)
										System.out.println(span + " " + ings + " " + match + " " + prob);
									lprob += prob;
//									lprob += stems.size() * Math.log(0.01);
								}
							} else {
								double prob =  Math.log(connection_model.evoFoodProb()) + mixtures_model.logProbOfMixGivenIngs(predicate, StringUtils.join(stems), ings);
								if (VERBOSE)
								System.out.println(stems + " " + ings + " "  + prob);
								lprob += prob;
							}
						} else {
							lprob += Math.log(connection_model.impFoodProb());
//							System.out.println("imp food prob");
						}
					}
				}
			}
//			System.out.println("lprob: " + lprob);
		}
//		System.out.println(lprob);

		Argument loc = pref.loc;
		if (loc != null) {
			if (loc.type() != Type.OBJECT && loc.string().equals("")) {
				imp_prep = true;
			}
			String loc_string = loc.string();
			Connection connection = gi.connections_.getConnection(node, loc, loc_string);
			lprob += getArgTypeScore(node, loc, CONN_TYPE.LOC);
			if (connection == null) {
//				lprob += getClosenessLogProb(null, node, loc_string, CONN_TYPE.LOC);
				// no penalty for new locations
				// NEW LINE
//				lprob += connection_model.new_closeness_prob(node.index());
			} else {
//				lprob += getArgTypeScore(node, loc, loc_string, connection.origin, CONN_TYPE.LOC);
//				lprob += getClosenessLogProb(connection.origin, node, loc_string, CONN_TYPE.LOC);

				Set<String> locs = gi.getLocTknsForNode(connection.origin);
				if (locs == null || locs.size() == 0) {
					lprob += -100;
//					if (loc_string.equals("")) {
//						lprob += -100;
//					} else {
////						Pair<String, String> amt_and_ing = Measurements.splitAmountString(loc_string);
////						String[] split = amt_and_ing.getSecond().split(" ");
//						lprob += Math.log(0.00000001);
//					}
				} else {
					if (loc_string.equals("")) {
						if (str_classifier.verb_to_location_counts_ == null || str_classifier.verb_to_location_counts_.size() == 0) {
//							lprob += locs.size() * Math.log(0.0001);
							// NEW AUG 11 - switch
//							lprob += Math.log(0.0001);
							lprob += Math.log(connection_model.imp_loc_conn_prob);
							lprob += Math.log(1.0 - connection_model.imp_loc_conn_prob);
							// NEW AUG 11 END
						} else {
							double prob = str_classifier.lprobTknsGivenLoc(node.event().predicate(), locs, false);
							if (VERBOSE)
								System.out.println(node.event().predicate() + " " + locs + prob);
							lprob += prob;
							// NEW AUG 11
							lprob += Math.log(connection_model.imp_loc_conn_prob);
							// NEW AUG 11 END
						}
					} else {
						Pair<String, String> amt_and_ing = Measurements.splitAmountString(loc_string);
						String[] split = amt_and_ing.getSecond().split(" ");
						boolean match = false;
						int match_count = 0;
						HashSet<String> stems = new HashSet<String>();
						for (int a = split.length - 1; a >= 0; a--) {
							String s = split[a];
							//							if (s.length() < 3) {
							//								continue;
							//							}
							//							if (!Utils.isMostlyNounInWordNet(s)) {
							//								continue;
							//							}
							String stem = Utils.stem(s);
							stems.add(stem);
							if (locs.contains(stem)) {
								match_count++;
								match = true;
							}
							break;
						}
						boolean pmatch = false;
						for (int a = connection.origin.index() + 1; a < node.index(); a++) {
							ActionNode prev = gi.actionDiagram().getNodeAtIndex(a);
							SelectionalPreference ppref = gi.getSelectionalPreferencesOfNode(prev);
							Argument ploc = ppref.loc;
							if (ploc != null) {
								String ploc_string = ploc.string();
								if (ploc_string.equals("")) {
									continue;
								}
								Pair<String, String> pamt_and_ing = Measurements.splitAmountString(ploc_string);
								String[] psplit = pamt_and_ing.getSecond().split(" ");
								if (psplit.length == 0) {
									continue;
								}
								String ps = psplit[psplit.length - 1];
								String stem = Utils.stem(ps);
								if (locs.contains(stem)) {
									pmatch = true;
									break;
								}
							}
						}
						if (pmatch) {
							lprob += -100;
						}
						if (true || mixtures_model == null) {
							if (match) {
//								lprob += Math.log(0.999999);
								// NEW AUG 11
								if (str_classifier.verb_to_location_counts_ == null || str_classifier.verb_to_location_counts_.size() == 0) {
									lprob += Math.log(1.0 - connection_model.imp_loc_conn_prob);
								} else {
									double p1 = Math.log(1.0 - connection_model.imp_loc_conn_prob);
									double impprob = str_classifier.lprobTknsGivenLoc(node.event().predicate(), locs, false);
									double p2 = Math.log(1.0 - Math.exp(impprob)) + Math.log(connection_model.imp_loc_conn_prob);
//									double ibmprob = mixtures_model.logProbOfMixGivenIngs(predicate, StringUtils.join(stems), locs);
									lprob += Utils.logsumexp(p1, p2);
//									System.out.println(Math.exp(Utils.logsumexp(p1, p2)));
//									System.out.println(Math.exp(p1));
//									System.out.println(Math.exp(impprob + Math.log(connection_model.imp_loc_conn_prob)));
//									System.out.println(locs + " " + Math.exp(impprob));
//									System.exit(1);
//									lprob += p1;
								}
								// NEW AUG 11 END
							} else {
//								lprob += Math.log(0.000001);
								
								// NEW AUG 11 - switch
								lprob += -100;
//								if (str_classifier.verb_to_location_counts_ == null || str_classifier.verb_to_location_counts_.size() == 0) {
////									lprob += -100;
//									lprob += 2*Math.log(connection_model.imp_loc_conn_prob);
//								} else {
//									lprob += Math.log(connection_model.imp_loc_conn_prob);
//									double impprob = str_classifier.lprobTknsGivenLoc(node.event().predicate(), locs, false);
//									lprob += Math.log(1.0 - Math.exp(impprob));
//								}
								//
								
							}
							//							if (match_count > 0) {
							//								lprob += match_count * Math.log(0.9999);
							//								lprob += (locs.size() - match_count) * Math.log(0.0001);
							//							} else {
							//								lprob += locs.size() * Math.log(0.0001);
							//							}
							if (VERBOSE)
							System.out.println(loc_string + " " + match_count + " " + locs.size());
						} else {
							double prob = mixtures_model.logProbOfMixGivenIngs(predicate, StringUtils.join(stems), locs);
							if (VERBOSE)
							System.out.println(predicate + " " + stems + " " + locs + " " + prob);
							lprob += prob;
							
						}
					}
				}
			}
		}
//		System.out.println(lprob);
//		if (!imp_prep) {
//			lprob += Math.log(0.5);
//		}
		
		for (int i = 0; i < pref.other_args.size(); i++) {
			Argument arg = pref.other_args.get(i);
			for (String span : arg.nonIngredientSpans()) {
				ActionNode origin = gi.getOrigin(node, arg, span);
				if (origin != null) {
					lprob += -100;
					if (VERBOSE) {
						System.out.println("other " + arg + "  " + origin);
					}
				} else if (arg.string().equals("") && arg.type() == Type.OBJECT) {
					lprob += Double.NEGATIVE_INFINITY;
				}
			}
		}

		return lprob;
	}

	public double getClosenessLogProb(ActionNode origin, ActionNode dest, String span, CONN_TYPE type) {
		if (origin == null) {
//			if (type == CONN_TYPE.FOOD) {
//				return connection_model.new_closeness_prob(dest.index());
//			} else {
//				return connection_model.new_loc_closeness_prob(dest.index());
//			}
			return 0.0;
		} else {
			int closeness = dest.index() - origin.index();
			if (type == CONN_TYPE.FOOD) {
				if (span.equals("")) {
					return connection_model.imp_ing_closeness_prob(closeness);
				} else {
					return connection_model.evo_ing_closeness_prob(closeness);
				}
			} else {
				if (span.equals("")) {
					return connection_model.imp_loc_closeness_prob(closeness);
				} else {
//					System.out.println(origin + " " + dest + " " + span + " " + closeness + " " + connection_model.evo_loc_closeness_prob(closeness));
					return connection_model.evo_loc_closeness_prob(closeness);
				}
			}
		}
	}

	public double getArgTypeScore(ActionNode node, Argument arg, ConnectionsModel.CONN_TYPE type) {
		double lprob = 0.0;
		if (arg.string().equals("")) {
			ActionNode origin = gi.getOrigin(node, arg, "");
			if (origin == null) {
				if (arg.type() == Type.OBJECT) {
					return Double.NEGATIVE_INFINITY;
				} else {
					return 0.0;
				}
			} else {
				return 0.0;
			}
		} else {
			return 0.0;
		}
	}



	public double getNodeScore(ActionNode node) {
		return getTextGivenConnectionsLogProb(node);
	}

	public void initNodeScores() {
		ActionDiagram ad = gi.actionDiagram();
		for (int i = 0; i < ad.numNodes(); i++) {
			ActionNode node = ad.getNodeAtIndex(i);


			double lprob = getNodeScore(node);
			if (VERBOSE)
				System.out.println("INIT NODE SCORE: " + lprob);
			node_score.put(i, lprob);
		}

	}

	public double getGraphScore() {
		return getGraphScore(0);
	}

	public double getGraphScore(int start_index) {
		if (gi == null) {
			try {
				throw new Exception("Graph for scorer uninitialized.");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		ActionDiagram ad = gi.actionDiagram();
		double lprob = getConnectionsLogProb();
		if (VERBOSE) {
			System.out.println("CONNS SCORE: " + lprob);
		}

		for (int i = 0; i < ad.numNodes(); i++) {
			double nodescore;
			if (i < start_index) {
				System.exit(1);
				nodescore = node_score.get(i);
			} else {
				nodescore = getNodeScore(ad.getNodeAtIndex(i));
			}
			if (VERBOSE) {
				System.out.println("NODE SCORE " + ad.getNodeAtIndex(i) + " " + nodescore + " " + gi.getSelectionalPreferencesOfNode(ad.getNodeAtIndex(i)).pref_type + " " + gi.getSelectionalPreferencesOfNode(ad.getNodeAtIndex(i)).loc);
			}
			lprob += nodescore;
		}

		return lprob;
	}

	public double getGraphScore(SearchOp op) {
		if (gi == null) {
			try {
				throw new Exception("Graph for scorer uninitialized.");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		gi.applySearchOp(op, this);
		double lprob = getGraphScore(0);
		gi.popSearchOp(this);
		return lprob;
	}

	public SelectionalPreferenceModel selectionalPreferenceModel() {
		return selectional_pref_model;
	}

	public RecipeSentenceSegmenter chunker() {
		return chunker;
	}
}
