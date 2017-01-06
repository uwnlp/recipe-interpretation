package model;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import model.Connections.Connection;
import model.ConnectionsModel.CONN_TYPE;
import model.SelectionalPreferenceModel.SelectionalPreference;
import data.ActionDiagram;
import data.RecipeSentenceSegmenter;
import data.ProjectParameters;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;
import utils.Pair;
import utils.TokenCounter;
import utils.Triple;

public class LocalSearcher {
	private GraphInfo gi_;
	private GraphScorer scorer_;

	private Map<SearchOp, Set<Integer>> opToOriginIndex_;
	private Map<Integer, Set<Pair<Double, SearchOp>>> originIndexToScoredOps_;
	private Map<SearchOp, Integer> opToMinDestIndex_;
	private Map<Integer, Set<Pair<Double, SearchOp>>> minDestIndexToScoredOps_;
	private TreeSet<Pair<Double,SearchOp>> scoreActiveAgenda_;

	private boolean is_initialized_;

	private static Set<Character> SEARCH_OPERATORS = new HashSet<Character>();
	static {
		SEARCH_OPERATORS.add(SearchOp.OP_ADD_NEW_OUTPUT_);
		SEARCH_OPERATORS.add(SearchOp.OP_NEW_DEST_);
		SEARCH_OPERATORS.add(SearchOp.OP_SWAP_DESTS_);
		SEARCH_OPERATORS.add(SearchOp.OP_THREE_WAY_);
		SEARCH_OPERATORS.add(SearchOp.OP_REMOVE_OUTPUT_);
	}

	public LocalSearcher(GraphInfo gi, GraphScorer scorer) {
		gi_ = gi;
		scorer_ = scorer;
		opToOriginIndex_ = new HashMap<SearchOp, Set<Integer>>();
		originIndexToScoredOps_ = new HashMap<Integer, Set<Pair<Double, SearchOp>>>();
		opToMinDestIndex_ = new HashMap<SearchOp, Integer>();
		minDestIndexToScoredOps_ = new HashMap<Integer, Set<Pair<Double, SearchOp>>>();
		scoreActiveAgenda_ = new TreeSet<Pair<Double,SearchOp>>();
		is_initialized_ = false;
	}

	public GraphInfo graphInfo() {
		return gi_;
	}

	private Set<Triple<ActionNode, Argument, String>> getAllPossibleDests(ActionNode dest_node, 
			boolean allow_already_connected, boolean allow_raw_ing_args, SelectionalPreference pref) {
		Set<Triple<ActionNode, Argument, String>> dests = new HashSet<Triple<ActionNode, Argument, String>>();
		RecipeEvent event = dest_node.event();
		Argument dobj = event.dobj();
		if (allow_already_connected) {
			if (allow_raw_ing_args || !dobj.hasIngredients()) {
				for (String span : dobj.nonIngredientSpans()) {
					dests.add(new Triple<ActionNode, Argument, String>(dest_node, dobj, span));
				}
			}
		} else {
			if (allow_raw_ing_args || !dobj.hasIngredients()) {
				for (String span : dobj.nonIngredientSpans()) {
					if (gi_.doesSpanHaveOrigin(dest_node, dobj, span)) {
						dests.add(new Triple<ActionNode, Argument, String>(dest_node, dobj, span));
					}
				}
			}
		}

		boolean imp_prep = false;
		Iterator<Argument> prep_it = event.prepositionalArgIterator();
		while (prep_it.hasNext()) {
			Argument prep = prep_it.next();
			if (prep.type() != Type.LOCOROBJ && prep.type() != Type.LOCATION && prep.type() != Type.COOBJECT) {
				continue;
			}
			if (prep.string().equals("")) {
				imp_prep = true;
			}
			if (allow_already_connected) {
				if (allow_raw_ing_args || !dobj.hasIngredients()) {
					for (String span : prep.nonIngredientSpans()) {
						dests.add(new Triple<ActionNode, Argument, String>(dest_node, prep, span));
					}
				}
			} else {
				if (allow_raw_ing_args || !dobj.hasIngredients()) {
					for (String span : prep.nonIngredientSpans()) {
						if (gi_.doesSpanHaveOrigin(dest_node, prep, span)) {
							dests.add(new Triple<ActionNode, Argument, String>(dest_node, prep, span));
						}
					}
				}
			}
		}
		if (!imp_prep && allow_already_connected) {
			Argument dummy_prep = event.addPrepositionalArgument("", "in");
			dummy_prep.addNonIngredientSpan("");
			dests.add(new Triple<ActionNode, Argument, String>(dest_node, dummy_prep, ""));
		}
		return dests;
	}

	public void initialize() {
		initialize(0);
	}

	private void scoreOp(SearchOp op, int min_index, Set<Integer> origin_indices) {
		if (GraphScorer.VERBOSE)
			System.out.println("SCORING " + op + " " + op.type_ + " " + op.new_type_);
		double score = -1 * scorer_.getGraphScore(op);
		if (GraphScorer.VERBOSE)
			System.out.println("END SCORING "+ " " + op + " " + score);

		opToOriginIndex_.put(op, origin_indices);
		opToMinDestIndex_.put(op, min_index);
		Pair<Double, SearchOp> scored_op = new Pair<Double, SearchOp>(score, op);
		scoreActiveAgenda_.add(scored_op);
		Set<Pair<Double, SearchOp>> op_set = minDestIndexToScoredOps_.get(min_index);
		if (op_set == null) {
			op_set = new HashSet<Pair<Double, SearchOp>>();
			minDestIndexToScoredOps_.put(min_index, op_set);
		}
		op_set.add(scored_op);
		for (Integer origin : origin_indices) {
			op_set = originIndexToScoredOps_.get(origin);
			if (op_set == null) {
				op_set = new HashSet<Pair<Double, SearchOp>>();
				originIndexToScoredOps_.put(origin, op_set);
			}
			op_set.add(scored_op);
		}
	}

	public void initializeNode(int n, int start) {
		ActionDiagram ad = gi_.actionDiagram();
		ActionNode node = ad.getNodeAtIndex(n);
		CONN_TYPE origin_type = CONN_TYPE.FOOD;
		Set<String> ings = gi_.getIngSpansForNode(node);
		if (ings == null || ings.size() == 0) {
			origin_type = CONN_TYPE.LOC;
		}
		Set<Connection> orig_origs = gi_.connections_.getIncomingConnectionsToNode(node);
		if (gi_.visible_arg_to_type_.get(node.event().dobj()) == CONN_TYPE.LOC) {
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
		Set<Connection> orig_dests = gi_.dest_set(node);
		if (orig_dests == null || orig_dests.size() == 0) {
			System.out.println("TODO: add edges");
			System.exit(1);
		} else {
			Set<Connection> dests = new HashSet<Connection>(orig_dests);
			for (Connection dest : dests) {
				if (dest.type == CONN_TYPE.LOC) {
					origin_type = CONN_TYPE.LOC;
				}
			}
			for (Connection conn : dests) {
				Triple<ActionNode, Argument, String> dest = conn.destination;
				ActionNode dest_node = dest.getFirst();
				if (orig_dests.size() > 1) {
					SearchOp other_op = new SearchOp(node, dest, SearchOp.OP_REMOVE_OUTPUT_, conn.type);
					int min_index = Math.min(dest_node.index(), dest.getFirst().index());
					HashSet<Integer> origin_indices = new HashSet<Integer>();
					origin_indices.add(n);
					scoreOp(other_op, min_index, origin_indices);
				}
				CONN_TYPE curr_dest_type = null;
				if (!dest.getThird().equals("")) {
					curr_dest_type = gi_.getArgType(dest.getSecond());
				}
				for (int m = Math.max(n + 1, start); m < ad.numNodes(); m++) {
					ActionNode next_node = ad.getNodeAtIndex(m);
					SelectionalPreference pref = gi_.getSelectionalPreferencesOfNode(next_node);
					Set<Triple<ActionNode, Argument, String>> possible_dests;
					possible_dests = getAllPossibleDests(next_node, true, true, pref);
					for (Triple<ActionNode, Argument, String> possible_dest : possible_dests) {
						if (dest.equals(possible_dest)) {
							continue;
						}
						if (dest.getSecond().equals(possible_dest.getSecond())) {
							continue;
						}
						if (next_node.event().dobj().string().equals("") && 
								possible_dest.getSecond().type() != Type.OBJECT &&
								possible_dest.getThird().equals("") &&
								origin_type == CONN_TYPE.FOOD) {
							if (gi_.getSelectionalPreferencesOfNode(possible_dest.getFirst()).loc == null) {
								Set<Integer> origin_indexes = new HashSet<Integer>();
								origin_indexes.add(n);
								ActionNode other_origin = null;
								Connection other_connection = gi_.connections_.getConnection(possible_dest);
								if (other_connection != null) {
									other_origin = other_connection.origin;
								}
								if (other_origin == null) {
									SearchOp op = new SearchOp(dest, possible_dest, SearchOp.OP_NEW_DEST_, CONN_TYPE.LOC, CONN_TYPE.FOOD, gi_);
									int min_index = Math.min(dest_node.index(), possible_dest.getFirst().index());
									scoreOp(op, min_index, origin_indexes);

								}
							}
							continue;
						}
						// AUG commented
						//						if (possible_dest.getSecond().type() != Type.OBJECT
						//								&& possible_dest.getThird().equals("")) {
						//							boolean has_visible_coobj = false;
						//							for (Argument arg : pref.arg_arr) {
						//								if (arg.type() != Type.OBJECT && gi_.visible_arg_to_type_.get(arg) == CONN_TYPE.FOOD) {
						//									has_visible_coobj = true;
						//									break;
						//								}
						//							}
						//							if (has_visible_coobj) {
						//								continue;
						//							}
						//						}

						SearchOp op = null;
						ActionNode other_origin = null;
						Connection other_connection = gi_.connections_.getConnection(possible_dest);
						if (other_connection != null) {
							other_origin = other_connection.origin;
						}
						if (other_origin != null && (other_origin.index() >= dest_node.index() || other_origin.index() > node.index())) {
							continue;
						}
						if (other_origin != null && other_origin == node) {
							continue;
						}

						CONN_TYPE dest_type = null;
						if (!possible_dest.getThird().equals("")) {
							dest_type = gi_.getArgType(possible_dest.getSecond());
						}
						if (dest_type != null && dest_type == CONN_TYPE.OTHER) {
							continue;
						}
						if (dest_type != null && dest_type == CONN_TYPE.FOOD && origin_type == CONN_TYPE.LOC) {
							continue;
						}
						// AUG 11 commented
						// prev
						//						if (dest_type != null && dest_type == CONN_TYPE.LOC && origin_type == CONN_TYPE.FOOD) {
						//							continue;
						//						}
						// end prev
						if (dest_type != null && dest_type == CONN_TYPE.LOC && origin_type == CONN_TYPE.FOOD) {
							Set<Integer> origin_indexes = new HashSet<Integer>();
							origin_indexes.add(n);
							if (other_origin == null) {
								op = new SearchOp(dest, possible_dest, SearchOp.OP_NEW_DEST_, CONN_TYPE.LOC, CONN_TYPE.FOOD, gi_);
								int min_index = Math.min(dest_node.index(), possible_dest.getFirst().index());
								scoreOp(op, min_index, origin_indexes);

								for (int k = dest_node.index() - 1; k >= 0; k--) {
									if (k == node.index()) {
										continue;
									}
									ActionNode node2 = ad.getNodeAtIndex(k);
									Set<Connection> dest_set2 = new HashSet<Connection>(gi_.dest_set(node2));
									for (Connection conn2 : dest_set2) {
										Triple<ActionNode, Argument, String> dest2 = conn2.destination;
										if (dest2.getFirst() != node || 
												(Math.abs(dest.getFirst().index() - possible_dest.getFirst().index()) > 4
														&& Math.abs(dest.getFirst().index() - dest2.getFirst().index()) > 4)
														&& Math.abs(dest2.getFirst().index() - possible_dest.getFirst().index()) > 4) {
											continue;  // stick to special 3-case for now
										}
										if (conn2.type == dest_type) {
											continue;
										}

										ActionNode or1 = gi_.getOrigin(dest2);
										ActionNode or2 = gi_.getOrigin(dest);
										ActionNode or3 = gi_.getOrigin(possible_dest);
										if (or3 != null && or3.index() == dest2.getFirst().index()) {
											continue;
										}
										if (or2 != null && or2.index() == possible_dest.getFirst().index()) {
											continue;
										}
										if (or1 != null && or1.index() == dest.getFirst().index()) {
											continue;
										}

										op = new SearchOp(dest2, dest, possible_dest, SearchOp.OP_THREE_WAY_, conn2.type, dest_type, gi_);
										Set<Integer> origin_indexes2 = new HashSet<Integer>(origin_indexes);
										origin_indexes2.add(k);
										min_index = Math.min(dest_node.index(), possible_dest.getFirst().index());
										min_index = Math.min(min_index, dest2.getFirst().index());
										scoreOp(op, min_index, origin_indexes2);
									}
								}
							}
							continue;
						}
						// AUG 11 end

						CONN_TYPE new_type = origin_type;
						//						if (dest_type != null && dest_type == CONN_TYPE.LOC) {
						//							new_type = CONN_TYPE.LOC;  // even if it has food, it must be a location based on string
						//						}

						if (other_origin != null && other_connection.type != new_type) {
							continue; // can only swap the same type
						}

						Set<Integer> origin_indexes = new HashSet<Integer>();
						origin_indexes.add(n);

						if (other_origin == null) {
							op = new SearchOp(dest, possible_dest, SearchOp.OP_NEW_DEST_, new_type, gi_);

							if (curr_dest_type == null || origin_type == curr_dest_type) {
								boolean implicit = false;
								for (Connection c : orig_dests) {
									if (c.destination.getThird().equals("")) {
										implicit = true;
										break;
									}
								}
								if (!implicit) {
									SearchOp other_op = new SearchOp(node, possible_dest, SearchOp.OP_ADD_NEW_OUTPUT_, new_type);
									int min_index = Math.min(dest_node.index(), possible_dest.getFirst().index());
									scoreOp(other_op, min_index, new HashSet<Integer>(origin_indexes));
								}
							}
						} else {
							if (other_connection.type != conn.type) {
								continue;
							}
							op = new SearchOp(dest, possible_dest, SearchOp.OP_SWAP_DESTS_, new_type, gi_);
							origin_indexes.add(other_origin.index());
						}
						int min_index = Math.min(dest_node.index(), possible_dest.getFirst().index());
						scoreOp(op, min_index, origin_indexes);

						if (true || other_origin == null) {
							for (int k = dest_node.index() - 1; k >= 0; k--) {
								if (k == node.index()) {
									continue;
								}
								ActionNode node2 = ad.getNodeAtIndex(k);
								Set<Connection> dest_set2 = new HashSet<Connection>(gi_.dest_set(node2));
								for (Connection conn2 : dest_set2) {
									Triple<ActionNode, Argument, String> dest2 = conn2.destination;
									if (dest2.getFirst() != node || 
											(Math.abs(dest.getFirst().index() - possible_dest.getFirst().index()) > 4
													&& Math.abs(dest.getFirst().index() - dest2.getFirst().index()) > 4)
													&& Math.abs(dest2.getFirst().index() - possible_dest.getFirst().index()) > 4) {
										continue;  // stick to special 3-case for now
									}
									if (conn2.type != conn.type || conn.type != origin_type) {
										continue; // must be same type
									}
									ActionNode or1 = gi_.getOrigin(dest2);
									ActionNode or2 = gi_.getOrigin(dest);
									ActionNode or3 = gi_.getOrigin(possible_dest);
									if (or3 != null && or3.index() == dest2.getFirst().index()) {
										continue;
									}
									if (or2 != null && or2.index() == possible_dest.getFirst().index()) {
										continue;
									}
									if (or1 != null && or1.index() == dest.getFirst().index()) {
										continue;
									}
									op = new SearchOp(dest2, dest, possible_dest, SearchOp.OP_THREE_WAY_, origin_type, gi_);
									Set<Integer> origin_indexes2 = new HashSet<Integer>(origin_indexes);
									origin_indexes2.add(k);
									min_index = Math.min(dest_node.index(), possible_dest.getFirst().index());
									min_index = Math.min(min_index, dest2.getFirst().index());
									scoreOp(op, min_index, origin_indexes2);
								}
							}
						}
					}
				}
			}
		}
	}


	public void initialize(int start) {
		ActionDiagram ad = gi_.actionDiagram();

		for (int n = 0; n < ad.numNodes() - 1; n++) {
			initializeNode(n, start);
		}

		is_initialized_ = true;
	}

	public void run() {
		if (!is_initialized_) {
			initialize();
		}
		scorer_.changeGraph(gi_);
		double curr_lprob = -1 * scorer_.getGraphScore();
		if (scorer_.VERBOSE)
			System.out.println("initial score: " + curr_lprob);

		int c = 0;
		double new_lprob = curr_lprob;
		do {
			curr_lprob = new_lprob;
			Pair<Double, SearchOp> op_pair = scoreActiveAgenda_.pollFirst();
			if (op_pair == null) {
				break; // no more operations
			}
			SearchOp op = op_pair.getSecond();
			if (op_pair.getFirst() >= curr_lprob) {
				break;
			}
			if (Math.abs(op_pair.getFirst() - curr_lprob) < 0.001) {
				break;
			}
			if (Double.isInfinite(op_pair.getFirst())) {
				break;
			}
			if (scorer_.VERBOSE)
				System.out.println("OP CHOICE " + op_pair);

			gi_.applySearchOp(op, scorer_);

			scoreActiveAgenda_.clear();

			initialize();
			new_lprob = op_pair.getFirst();
			c++;

		} while (Math.abs(new_lprob - curr_lprob) > 0.001);
	}

}
