package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.Connections.Connection;
import model.ConnectionsModel.CONN_TYPE;
import model.SelectionalPreferenceModel.SelectionalPreference;
import utils.Measurements;
import utils.Pair;
import utils.Triple;
import utils.Utils;
import data.ActionDiagram;
import data.IngredientParser;
import data.ProjectParameters;
import data.RecipeEvent;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;
import edu.stanford.nlp.util.StringUtils;

// structure that stores information about an action graph
public class GraphInfo {
	public static boolean NEWPREFSTRUCT = true;

	private ActionDiagram ad;
	public Map<Argument, Boolean> is_arg_food;
	private Map<ActionNode, SelectionalPreference> node_to_selectional_pref;
	public Connections connections_;
	public Map<Argument, CONN_TYPE> visible_arg_to_type_;
	public Map<ActionNode, Set<String>> node_to_ing_phrases_;
	public Map<ActionNode, Set<String>> node_to_orig_ing_phrases_;
	public Map<ActionNode, Set<String>> node_to_loc_phrases_;
	public Map<ActionNode, String> if_leaf_dobj_loc_string_;
	public Set<String> all_ing_phrases_;
	private List<SearchOp> reverse = null;

	public GraphInfo(ActionDiagram ad, boolean apply_ings, boolean conn_ings, 
			ThreeWayStringClassifier str_classifier, SelectionalPreferenceModel pref_model) {
		this.ad = ad;
		is_arg_food = new HashMap<Argument, Boolean>();
		node_to_selectional_pref = new HashMap<ActionNode, SelectionalPreference>();
		visible_arg_to_type_ = new HashMap<Argument, CONN_TYPE>();
		node_to_ing_phrases_ = new HashMap<ActionNode, Set<String>>();
		node_to_orig_ing_phrases_ = new HashMap<ActionNode, Set<String>>();
		node_to_loc_phrases_ = new HashMap<ActionNode, Set<String>>();
		all_ing_phrases_ = new HashSet<String>();
		if_leaf_dobj_loc_string_ = new HashMap<ActionNode, String>();
		connections_ = new Connections();
		if (apply_ings) {
			if (conn_ings) {
				connectIngredientsPerSpan();
			} else {
				collectIngredientsPerSpan();
			}
			identifyExplicitArgTypes(str_classifier, pref_model);
		} else {
			for (int i = 0; i < ad.numNodes(); i++) {
				ActionNode node = ad.getNodeAtIndex(i);
				RecipeEvent event = node.event();
				Argument dobj = event.dobj();
				if (dobj == null) {
					dobj = event.setDirectObject("");
					dobj.addNonIngredientSpan("");
				}
			}
		}
	}


	public GraphInfo(ActionDiagram ad, boolean conn_ings) {
		this.ad = ad;
		is_arg_food = new HashMap<Argument, Boolean>();
		node_to_selectional_pref = new HashMap<ActionNode, SelectionalPreference>();
		visible_arg_to_type_ = new HashMap<Argument, CONN_TYPE>();
		node_to_ing_phrases_ = new HashMap<ActionNode, Set<String>>();
		node_to_orig_ing_phrases_ = new HashMap<ActionNode, Set<String>>();
		node_to_loc_phrases_ = new HashMap<ActionNode, Set<String>>();
		all_ing_phrases_ = new HashSet<String>();
		if_leaf_dobj_loc_string_ = new HashMap<ActionNode, String>();
		connections_ = new Connections();
		if (conn_ings) {
			connectIngredientsPerSpan();
		}
	}

	public void setArgAsFood(Argument arg, Boolean is_food) {
		is_arg_food.put(arg, is_food);
	}

	public Boolean isArgFood(Argument arg) {
		return is_arg_food.get(arg);
	}

	
	public void setSelectionalPreferencesOfNode(ActionNode node, SelectionalPreference sp) {
		SelectionalPreference old_pref = node_to_selectional_pref.get(node);
		Set<Connection> incoming = connections_.getIncomingConnectionsToNode(node);
		if (sp.loc != null) {
			if (sp.loc.string().equals("")) {
				ActionNode origin = getOrigin(node, sp.loc, "");
				if (origin == null) {
					node_to_loc_phrases_.put(node, new HashSet<String>());
				} else {
					Set<String> origin_loc = node_to_loc_phrases_.get(origin);
					if (origin_loc == null) {
						node_to_loc_phrases_.put(node, new HashSet<String>());
					} else {
						node_to_loc_phrases_.put(node, new HashSet<String>(origin_loc));
					}
				}
			} else if (sp.loc.type() == Type.OBJECT && (incoming == null || incoming.size() == 0)) {
				//			} else {
				Set<String> loc_tkns = new HashSet<String>();
				String[] split = sp.loc.string().split(" ");
				for (int i = split.length - 1; i >= 0; i--) {
					String s = split[i];
					if (s.length() < 3) {
						continue;
					}
					loc_tkns.add(Utils.stem(s));
					break;
				}
				node_to_loc_phrases_.put(node, loc_tkns);
			}
		} else {
			node_to_loc_phrases_.put(node, new HashSet<String>());
		}
		node_to_selectional_pref.put(node, sp);
	}

	public SelectionalPreference getSelectionalPreferencesOfNode(ActionNode node) {
		return node_to_selectional_pref.get(node);
	}

	public Set<String> getIngSpansForNode(ActionNode node) {
		return node_to_ing_phrases_.get(node);
	}

	public String getLeafDobjLoc(ActionNode node) {
		return if_leaf_dobj_loc_string_.get(node);
	}

	public Set<String> getLocTknsForNode(ActionNode node) {
		return node_to_loc_phrases_.get(node);
	}

	public ActionDiagram actionDiagram() {
		return ad;
	}

	public Set<Connection> dest_set(ActionNode node) {
		return connections_.getOutgoingConnections(node);
	}

	public ActionNode getOrigin(ActionNode node, Argument arg, String span) {
		return connections_.getOrigin(new Triple<ActionNode, Argument, String>(node, arg, span));
	}

	public ActionNode getOrigin(Triple<ActionNode, Argument, String> dest_triple) {
		return connections_.getOrigin(dest_triple);
	}

	public boolean doesSpanHaveOrigin(ActionNode node, Argument arg, String span) {
		return connections_.doesSpanHaveOrigin(new Triple<ActionNode, Argument, String>(node, arg, span));
	}

	public boolean doesSpanHaveOrigin(Triple<ActionNode, Argument, String> dest_triple) {
		return connections_.doesSpanHaveOrigin(dest_triple);
	}

	public CONN_TYPE getArgType(Argument arg) {
		return visible_arg_to_type_.get(arg);
	}

	private SelectionalPreference getPreference(List<Argument> conn_args_ing, 
			List<Argument> conn_args_loc, List<Argument> non_conn_args, ActionNode node,
			boolean ing_obj, GraphScorer gs) {
		RecipeEvent event = node.event();
//		int num_conn_args = conn_args_ing.size() + conn_args_loc.size();
		////new
		int num_conn_args = conn_args_ing.size();
		if (num_conn_args > 2) {
			int ings = 0;
			for (Argument arg : conn_args_ing) {
				if (arg.type() != Type.OBJECT && arg.hasIngredients()) {
					ings++;
				}
			}
		}
		Argument[] arg_arr = new Argument[num_conn_args];
		int a = 0;
		Iterator<Argument> arg_it = conn_args_ing.iterator();
		while (arg_it.hasNext()) {
			arg_arr[a] = arg_it.next();
			a++;
		}
		SelectionalPreference pref = new SelectionalPreference();
		if (ing_obj) {
			if (num_conn_args >= 2) {
				pref.pref_type = "INGOBJ:PREP";
				pref.arg_arr = arg_arr;
				if (conn_args_loc.size() == 1) {
					pref.loc = conn_args_loc.iterator().next();
					Argument imp_arg = null;
					pref.other_args = non_conn_args;
					if (imp_arg != null) {
						pref.other_args.remove(imp_arg);
					}
				} else {
					double best_prob = Double.NEGATIVE_INFINITY;
					Argument best_loc = null;
					Argument imp_arg = null;

					pref.loc = best_loc;
					if (conn_args_loc.size() == 1) {
						pref.loc = conn_args_loc.iterator().next();
					}
					pref.other_args = new ArrayList<Argument>(non_conn_args);
					pref.other_args.remove(pref.loc);

				}
			} else {
				pref.pref_type = "INGOBJ:NOPREP";
				pref.arg_arr = arg_arr;
				double best_prob = Double.NEGATIVE_INFINITY;
				Argument best_loc = null;
				Argument imp_arg = null;
//				for (Argument arg : non_conn_args) {
//					if (arg.string().equals("") && arg.type() != Type.OBJECT) {
//						imp_arg = arg;
//						continue;
//					}
//					if (arg.nonIngredientSpans().size() != 1) {
//						continue;
//					}
//					if (!gs.str_classifier.isPhraseMoreLikelyToBeLoc(arg.string(), true, true)) {
//						continue;
//					}
//					double loc_prob = gs.str_classifier.lprobPhraseGivenLoc(arg.string(), true);
//					if (loc_prob > best_prob) {
//						best_prob = loc_prob;
//						best_loc = arg;
//					}
//				}
				//				for (Argument arg : conn_args_ing) {
				//					if (arg.string().equals("")) {
				//						continue;
				//					}
				//					if (arg.nonIngredientSpans().size() != 1) {
				//						continue;
				//					}
				//					if (gs.str_classifier.isPhraseMoreLikelyToBeIng(arg.string(), true)) {
				//						continue;
				//					}
				//					System.out.println(arg.string());
				//					System.exit(1);
				//					double loc_prob = gs.str_classifier.lprobPhraseGivenLoc(arg.string());
				//					if (loc_prob > best_prob) {
				//						best_prob = loc_prob;
				//						best_loc = arg;
				//					}
				//				}
				pref.loc = best_loc;
				if (conn_args_loc.size() == 1) {
					pref.loc = conn_args_loc.iterator().next();
				}
//				if (pref.loc != null) {
//					pref.pref_type = "INGOBJ:PREP";
//				}
				pref.other_args = new ArrayList<Argument>(non_conn_args);
				pref.other_args.remove(pref.loc);
				if (imp_arg != null) {
					pref.other_args.remove(imp_arg);
				}
			}
		} else {
			if (num_conn_args >= 2) {
				pref.pref_type = "NONINGOBJ:PREP";
				pref.arg_arr = arg_arr;
				if (conn_args_loc.size() == 0) {
					Argument imp_arg = null;
					double best_prob = Double.NEGATIVE_INFINITY;
					Argument best_loc = null;
//					for (Argument arg : non_conn_args) {
//						if (arg.string().equals("") && arg.type() != Type.OBJECT) {
//							imp_arg = arg;
//							continue;
//						}
//						if (arg.nonIngredientSpans().size() != 1) {
//							continue;
//						}
//						if (!gs.str_classifier.isPhraseMoreLikelyToBeLoc(arg.string(), true, true)) {
//							continue;
//						}
//						double loc_prob = gs.str_classifier.lprobPhraseGivenLoc(arg.string(), true);
//						if (loc_prob > best_prob) {
//							best_prob = loc_prob;
//							best_loc = arg;
//						}
//					}
					//					for (Argument arg : conn_args_ing) {
					//						if (arg.string().equals("")) {
					//							continue;
					//						}
					//						if (arg.nonIngredientSpans().size() != 1) {
					//							continue;
					//						}
					//						if (gs.str_classifier.isPhraseMoreLikelyToBeIng(arg.string(), true)) {
					//							continue;
					//						}
					//						System.out.println(arg.string());
					//						System.exit(1);
					//						double loc_prob = gs.str_classifier.lprobPhraseGivenLoc(arg.string());
					//						if (loc_prob > best_prob) {
					//							best_prob = loc_prob;
					//							best_loc = arg;
					//						}
					//					}
					pref.loc = best_loc;
					pref.other_args = non_conn_args;
					if (imp_arg != null) {
						pref.other_args.remove(imp_arg);
					}
				} else {
					pref.loc = conn_args_loc.iterator().next();
					Argument imp_arg = null;
//					for (Argument arg : non_conn_args) {
//						if (arg.string().equals("") && arg.type() != Type.OBJECT) {
//							imp_arg = arg;
//							break;
//						}
//					}
					pref.other_args = non_conn_args;
					if (imp_arg != null) {
						pref.other_args.remove(imp_arg);
					}
				}
			} else if (num_conn_args == 1) {
				pref.pref_type = "NONINGOBJ:PREP";
				pref.arg_arr = arg_arr;
				if (conn_args_loc.size() != 0) {
					pref.loc = conn_args_loc.iterator().next();
					Argument imp_arg = null;
//					for (Argument arg : non_conn_args) {
//						if (arg.string().equals("") && arg.type() != Type.OBJECT) {
//							imp_arg = arg;
//							break;
//						}
//					}
					pref.other_args = non_conn_args;
					if (imp_arg != null) {
						pref.other_args.remove(imp_arg);
					}
				} else {
					Argument imp_arg = null;
					double best_prob = Double.NEGATIVE_INFINITY;
					Argument best_loc = null;
//					for (Argument arg : non_conn_args) {
//						if (arg.string().equals("") && arg.type() != Type.OBJECT) {
//							imp_arg = arg;
//							continue;
//						}
//						if (arg.nonIngredientSpans().size() != 1) {
//							continue;
//						}
//						if (!gs.str_classifier.isPhraseMoreLikelyToBeLoc(arg.string(), true, true)) {
//							continue;
//						}
//						double loc_prob = gs.str_classifier.lprobPhraseGivenLoc(arg.string(), true);
//						if (loc_prob > best_prob) {
//							best_prob = loc_prob;
//							best_loc = arg;
//						}
//					}
					//					for (Argument arg : conn_args_ing) {
					//						if (arg.string().equals("")) {
					//							continue;
					//						}
					//						if (arg.nonIngredientSpans().size() != 1) {
					//							continue;
					//						}
					//						if (gs.str_classifier.isPhraseMoreLikelyToBeIng(arg.string(), true)) {
					//							continue;
					//						}
					//						System.out.println(arg.string());
					//						System.exit(1);
					//						double loc_prob = gs.str_classifier.lprobPhraseGivenLoc(arg.string());
					//						if (loc_prob > best_prob) {
					//							best_prob = loc_prob;
					//							best_loc = arg;
					//						}
					//					}
					pref.loc = best_loc;
					pref.other_args = non_conn_args;
					if (imp_arg != null) {
						pref.other_args.remove(imp_arg);
					}
				}
			} else {
				pref.pref_type = "NONINGOBJ:NOPREP";
				pref.arg_arr = new Argument[0];
				pref.loc = event.dobj();
				Argument imp_arg = null;
//				for (Argument arg : non_conn_args) {
//					if (arg.string().equals("") && arg.type() != Type.OBJECT) {
//						imp_arg = arg;
//						continue;
//					}
//				}
				pref.other_args = new ArrayList<Argument>(non_conn_args);
				pref.other_args.remove(pref.loc);
				if (imp_arg != null) {
					pref.other_args.remove(imp_arg);
				}
			}
		}
		//		System.out.println(event);
		//		System.out.println(pref.pref_type);
		if (connections_.getIncomingConnectionsToNode(node) == null || 
				connections_.getIncomingConnectionsToNode(node).size() == 0) {
			pref.pref_type += ":LEAF";
		}
		return pref;
	}

	private static boolean set_prefs = true;

	public void updateNode(GraphScorer gs, ActionNode next_node) {
		Set<String> ings = node_to_ing_phrases_.get(next_node);
		if (ings == null) {
			ings = new HashSet<String>();
			node_to_ing_phrases_.put(next_node, ings);
		}

		RecipeEvent event = next_node.event();
		SelectionalPreference old_pref = getSelectionalPreferencesOfNode(next_node);
		List<Argument> conn_args_ing = new ArrayList<Argument>();
		List<Argument> conn_args_loc = new ArrayList<Argument>();
		List<Argument> non_conn_args = new ArrayList<Argument>();
		List<Argument> other_args = new ArrayList<Argument>();
		boolean ing_obj = false;
		boolean obj_conn = false;
		Argument dobj = event.dobj();
		Argument loc = null;

		CONN_TYPE type = null;
		if (!dobj.string().equals("")) {
			type = visible_arg_to_type_.get(dobj);
		}
		if (type == null) {
			for (String span : dobj.nonIngredientSpans()) {
				Connection conn = connections_.getConnection(next_node, dobj, span);
				if (conn != null) {
					ActionNode origin_node = conn.origin;

					if (conn.type == CONN_TYPE.FOOD) {
						ing_obj = true;
					}
					obj_conn = true;

					Set<String> other_ings = node_to_ing_phrases_.get(origin_node);
					if (other_ings != null) {
						ings.addAll(other_ings);
					}
				}
			}
		} else {
			for (String span : dobj.nonIngredientSpans()) {
				Connection conn = connections_.getConnection(next_node, dobj, span);
				if (conn != null) {
					ActionNode origin_node = conn.origin;

					Set<String> other_ings = node_to_ing_phrases_.get(origin_node);
					if (other_ings != null) {
						ings.addAll(other_ings);
					}
				}
			}
		}
		if (type == CONN_TYPE.FOOD) {
			conn_args_ing.add(dobj);
			ing_obj = true;
		} else if (type == CONN_TYPE.LOC) {
			conn_args_loc.add(dobj);
		} else if (obj_conn) {
			if (ing_obj) {
				conn_args_ing.add(dobj);
			} else {
				conn_args_loc.add(dobj);
			}
		} else if (dobj.string().equals("")) {
			non_conn_args.add(dobj);  // shouldn't happen
		} else {
			non_conn_args.add(dobj);
		}

		Argument ing_prep = null;
		Argument null_arg = null;
		Iterator<Argument> prep_it = event.prepositionalArgIterator();
		while (prep_it.hasNext()) {
			Argument prep = prep_it.next();
			if (prep.type() != Type.LOCOROBJ && prep.type() != Type.LOCATION && prep.type() != Type.COOBJECT) {
				continue;
			}
			boolean has_ings = false;
			boolean has_origin = false;
			type = null;
			if (!prep.string().equals("")) {
				type = visible_arg_to_type_.get(prep);
			}
			if (type == null) {
				for (String span : prep.nonIngredientSpans()) {
					Connection conn = connections_.getConnection(next_node, prep, span);
					if (conn != null) {
						ActionNode origin_node = conn.origin;

						if (conn.type == CONN_TYPE.FOOD) {
							has_ings = true;
						}
						has_origin = true;

						Set<String> other_ings = node_to_ing_phrases_.get(origin_node);
						if (other_ings != null) {
							ings.addAll(other_ings);
						}
					}
				}
			} else {
				for (String span : prep.nonIngredientSpans()) {
					Connection conn = connections_.getConnection(next_node, prep, span);
					if (conn != null) {
						ActionNode origin_node = conn.origin;

						Set<String> other_ings = node_to_ing_phrases_.get(origin_node);
						if (other_ings != null) {
							ings.addAll(other_ings);
						}
					}
				}
			}
			if (!has_origin && prep.string().equals("")) {
				null_arg = prep;
				continue;
			}
			if (type == CONN_TYPE.FOOD) {
				conn_args_ing.add(prep);
			} else if (type == CONN_TYPE.LOC) {
				conn_args_loc.add(prep);
			} else if (has_origin) {
				if (old_pref != null && old_pref.loc != null && !old_pref.loc.string().equals("") && 
						prep.string().equals("") && !has_ings) {
					non_conn_args.add(prep);
				} else if (has_ings) {
					conn_args_ing.add(prep);
				} else {
					conn_args_loc.add(prep);
				}
			} else {
				non_conn_args.add(prep);
			}
		}

		if (NEWPREFSTRUCT) {
			SelectionalPreference pref = getPreference(conn_args_ing, conn_args_loc, non_conn_args, next_node, ing_obj, gs);

			setSelectionalPreferencesOfNode(next_node, pref);
//									System.out.println(event);
//									System.out.println(pref.pref_type);
			//						Set<Connection> conns = connections_.getIncomingConnectionsToNode(next_node);
			//			if (conns != null && pref.loc != null && pref.loc.type() == Type.OBJECT && pref.loc.string().equals("")) {
//							System.out.println(conn_args_ing);
//							System.out.println(conn_args_loc);
//							System.out.println(non_conn_args);
			//				System.out.println(connections_.getIncomingConnectionsToNode(next_node));
			//				System.out.println(connections_.origin_to_connections);
			//				System.out.println(connections_.dest_to_connection);
			//				System.exit(1);
			//			}
		} else {
			//			Set<SelectionalPreference> prefs = new HashSet<SelectionalPreference>();
			//			SelectionalPreference pref = new SelectionalPreference();
			//			if (ing_obj && ing_prep != null) {
			//				if (!ing_prep.hasIngredients() && !ing_prep.string().equals("") && ing_prep.nonIngredientSpans().size() == 1) {
			//					SelectionalPreference other_pref = new SelectionalPreference();
			//					other_pref.pref_type = "INGOBJ:NOPREP";
			//					other_pref.arg_arr = new Argument[]{dobj};
			//					other_pref.other_args = new ArrayList<Argument>();
			//					if (loc != null) {
			//						other_pref.other_args.add(loc);
			//					}
			//					other_pref.other_args.addAll(other_args);
			//					if (null_arg != null) {
			//						other_pref.other_args.add(null_arg);
			//					}
			//					other_pref.loc = ing_prep;
			//					prefs.add(other_pref);
			//				}
			//				pref.pref_type = "INGOBJ:PREP";
			//				pref.arg_arr = new Argument[]{dobj, ing_prep};
			//			} else if (ing_obj) {
			//				pref.pref_type = "INGOBJ:NOPREP";
			//				pref.arg_arr = new Argument[]{dobj};
			//			} else if (ing_prep != null) {
			//				pref.pref_type = "NONINGOBJ:PREP";
			//				pref.arg_arr = new Argument[]{ing_prep};
			//			} else {
			//				pref.pref_type = "NONINGOBJ:NOPREP";
			//				pref.arg_arr = new Argument[]{};
			//			}
			//			if (loc != null) {
			//				pref.loc = loc;
			//				pref.other_args = other_args;
			//				if (null_arg != null) {
			//					pref.other_args.add(null_arg);
			//				}
			//				prefs.add(pref);
			//			} else {
			//				if (other_args.size() == 0) {
			//					pref.loc = null;
			//					pref.other_args = other_args;
			//					if (null_arg != null) {
			//						pref.other_args.add(null_arg);
			//					}
			//					prefs.add(pref);
			//				} else if (other_args.size() == 1) {
			//					pref.loc = other_args.get(0);
			//					if (pref.loc.nonIngredientSpans().size() != 1) {
			//						pref.loc = null;
			//						pref.other_args = other_args;
			//						if (null_arg != null) {
			//							pref.other_args.add(null_arg);
			//						}
			//					} else {
			//						pref.other_args = new ArrayList<Argument>();
			//						if (null_arg != null) {
			//							pref.other_args.add(null_arg);
			//						}
			//					}
			//					prefs.add(pref);
			//				} else {
			//					for (int j = 0; j < other_args.size(); j++) {
			//						SelectionalPreference copy = pref.copy();
			//						Argument loc_pick = other_args.get(j);
			//						copy.loc = loc_pick;
			//						if (copy.loc.nonIngredientSpans().size() != 1) {
			//							continue;
			//						}
			//						List<Argument> other_copy = new ArrayList<Argument>(other_args);
			//						other_copy.remove(j);
			//						copy.other_args = other_copy;
			//						if (null_arg != null) {
			//							copy.other_args.add(null_arg);
			//						}
			//						prefs.add(copy);
			//					}
			//					SelectionalPreference copy = pref.copy();
			//					copy.loc = null;
			//					List<Argument> other_copy = new ArrayList<Argument>(other_args);
			//					copy.other_args = other_copy;
			//					if (null_arg != null) {
			//						copy.other_args.add(null_arg);
			//					}
			//					prefs.add(copy);
			//				}
			//			}
			//
			//			if (prefs.size() > 1) {
			//				boolean tmp = gs.VERBOSE;
			//				gs.VERBOSE = false;
			//				double max_prob = Double.NEGATIVE_INFINITY;
			//				SelectionalPreference max_pref = null;
			//				for (SelectionalPreference p : prefs) {
			//					double score = gs.getNodeScore(next_node, p);
			//					if (score > max_prob) {
			//						max_prob = score;
			//						max_pref = p;
			//					}
			//				}
			//				setSelectionalPreferencesOfNode(next_node, max_pref);
			//				gs.VERBOSE = tmp;
			//			} else {
			//				setSelectionalPreferencesOfNode(next_node, prefs.iterator().next());
			//			}
			System.out.println("TODO: fix old pref system in updateNode()");
			System.exit(1);
		}
	}

	public void update(GraphScorer gs) {
		for (int i = 0; i < ad.numNodes(); i++) {
			ActionNode node = ad.getNodeAtIndex(i);
			updateNode(gs, node);
		}
	}

	public void createConnection(ActionNode origin, Triple<ActionNode, Argument, String> dest_triple,
			CONN_TYPE type, GraphScorer gs) {
		if (gs.VERBOSE)
			System.out.println("create conn " + origin + " -> " + dest_triple + " " + type);
		Connection conn = connections_.addConnection(origin, dest_triple, type);

		// update the rest of the recipe for ing flow.
		for (int i = conn.destination.getFirst().index(); i < ad.numNodes(); i++) {
			ActionNode next_node = ad.getNodeAtIndex(i);
			updateNode(gs, next_node);
		}
	}

	private List<SearchOp> createReverseSearchOp(SearchOp op) {
//		System.out.println("op: " + op + " " + op.new_type_ + " " + op.type_);
		List<SearchOp> reverse_ops = new ArrayList<SearchOp>();
		if (op.isDestSwap()) {
			Connection conn1 = connections_.getConnection(op.node1, op.arg1, op.span1);
			Connection conn2 = connections_.getConnection(op.node2, op.arg2, op.span2);
			if (conn1.type != conn2.type) {
				System.out.println("bad swap types");
				System.exit(1);
			}
			SearchOp reverse = new SearchOp(getOrigin(op.node2, op.arg2, op.span2), op.node2, op.arg2, op.span2,
					getOrigin(op.node1, op.arg1, op.span1), op.node1, op.arg1, op.span1, 
					SearchOp.OP_SWAP_DESTS_, op.type_);
			reverse_ops.add(reverse);
			return reverse_ops;
		} else if (op.isNewDest()) {
			Connection conn = connections_.getConnection(op.node1, op.arg1, op.span1);
			//			System.out.println(op + " " + conn);
			if (op.new_type_ != null) {
				SearchOp reverse = new SearchOp(getOrigin(op.node1, op.arg1, op.span1), op.node2, op.arg2, op.span2,
					null, op.node1, op.arg1, op.span1, SearchOp.OP_NEW_DEST_, op.new_type_, op.type_);
				reverse_ops.add(reverse);
//				System.out.println("revop: " + reverse + " " + op.new_type_ + " " + op.type_);
			} else {
				SearchOp reverse = new SearchOp(getOrigin(op.node1, op.arg1, op.span1), op.node2, op.arg2, op.span2,
					null, op.node1, op.arg1, op.span1, SearchOp.OP_NEW_DEST_, conn.type);
				reverse_ops.add(reverse);
			}
			return reverse_ops;
		} else if (op.isAddDest()) {
			SearchOp reverse = new SearchOp(op.origin, op.node1, op.arg1, op.span1, 
					SearchOp.OP_REMOVE_OUTPUT_, op.type_);
			reverse_ops.add(reverse);
			return reverse_ops;
		} else if (op.isRemoveDest()) {
			SearchOp reverse = new SearchOp(op.origin, op.node1, op.arg1, op.span1, 
					SearchOp.OP_ADD_NEW_OUTPUT_, op.type_);
			reverse_ops.add(reverse);
			return reverse_ops;
		} else if (op.isThreeWay()) {
			if (op.new_type_ != null) {
				if (op.new_type_ == CONN_TYPE.LOC) {
					SearchOp reverse = new SearchOp(getOrigin(op.node3, op.arg3, op.span3), op.node3, op.arg3, op.span3,
						getOrigin(op.node2, op.arg2, op.span2), op.node2, op.arg2, op.span2, 
						getOrigin(op.node1, op.arg1, op.span1), op.node1, op.arg1, op.span1, SearchOp.OP_THREE_WAY_, op.type_,
						CONN_TYPE.FOOD);
					reverse.is_rev = true;
					reverse_ops.add(reverse);
				} else {
					SearchOp reverse = new SearchOp(getOrigin(op.node3, op.arg3, op.span3), op.node3, op.arg3, op.span3,
							getOrigin(op.node2, op.arg2, op.span2), op.node2, op.arg2, op.span2, 
							getOrigin(op.node1, op.arg1, op.span1), op.node1, op.arg1, op.span1, SearchOp.OP_THREE_WAY_, op.type_,
							CONN_TYPE.LOC);
					reverse.is_rev = true;
						reverse_ops.add(reverse);
				}
			} else {
				SearchOp reverse = new SearchOp(getOrigin(op.node3, op.arg3, op.span3), op.node3, op.arg3, op.span3,
						getOrigin(op.node2, op.arg2, op.span2), op.node2, op.arg2, op.span2, 
						getOrigin(op.node1, op.arg1, op.span1), op.node1, op.arg1, op.span1, SearchOp.OP_THREE_WAY_, op.type_);
					reverse_ops.add(reverse);
			}
			return reverse_ops;
		}
		System.out.println("TODO");
		System.exit(1);
		return null;
	}

	public void applySearchOp(SearchOp op, GraphScorer gs) {
		//				System.out.println("APPLY: " + op);
		reverse = createReverseSearchOp(op);
		if (op.isDestSwap()) {
			Connection conn1 = connections_.getConnection(op.node1, op.arg1, op.span1);
			Connection conn2 = connections_.getConnection(op.node2, op.arg2, op.span2);
			Triple<ActionNode, Argument, String> triple1 = new Triple<ActionNode, Argument, String>(op.node1, op.arg1, op.span1);
			Triple<ActionNode, Argument, String> triple2 = new Triple<ActionNode, Argument, String>(op.node2, op.arg2, op.span2);
			removeConnection(conn1.origin, triple1, conn1.type, gs);
			removeConnection(conn2.origin, triple2, conn2.type, gs);
			createConnection(conn1.origin, triple2, op.type_, gs);
			createConnection(conn2.origin, triple1, op.type_, gs);
		} else if (op.isNewDest()) {
			ActionNode origin1 = getOrigin(op.node1, op.arg1, op.span1);
			Triple<ActionNode, Argument, String> triple1 = new Triple<ActionNode, Argument, String>(op.node1, op.arg1, op.span1);
			Connection conn = connections_.getConnection(triple1);
			Triple<ActionNode, Argument, String> triple2 = new Triple<ActionNode, Argument, String>(op.node2, op.arg2, op.span2);
			removeConnection(origin1, triple1, conn.type, gs);
			createConnection(origin1, triple2, op.type_, gs);
		} else if (op.isAddDest()) {
			Triple<ActionNode, Argument, String> triple = new Triple<ActionNode, Argument, String>(op.node1, op.arg1, op.span1);
			createConnection(op.origin, triple, op.type_, gs);
		} else if (op.isRemoveDest()) {
			Triple<ActionNode, Argument, String> triple = new Triple<ActionNode, Argument, String>(op.node1, op.arg1, op.span1);
			removeConnection(op.origin, triple, op.type_, gs);
		} else if (op.isThreeWay()) {
			ActionNode origin1 = getOrigin(op.node1, op.arg1, op.span1);
			ActionNode origin2 = getOrigin(op.node2, op.arg2, op.span2);
			ActionNode origin3 = getOrigin(op.node3, op.arg3, op.span3);
			Triple<ActionNode, Argument, String> triple1 = new Triple<ActionNode, Argument, String>(op.node1, op.arg1, op.span1);
			Triple<ActionNode, Argument, String> triple2 = new Triple<ActionNode, Argument, String>(op.node2, op.arg2, op.span2);
			Triple<ActionNode, Argument, String> triple3 = new Triple<ActionNode, Argument, String>(op.node3, op.arg3, op.span3);
			
			if (op.new_type_ != null && op.is_rev) {
				if (op.new_type_ == CONN_TYPE.LOC) {
					removeConnection(origin1, triple1, CONN_TYPE.FOOD, gs);
				} else {
					removeConnection(origin1, triple1, CONN_TYPE.LOC, gs);
				}
			} else {
				removeConnection(origin1, triple1, op.type_, gs);
			}
			
			if (op.new_type_ != null && !op.is_rev) {
				if (op.new_type_ == CONN_TYPE.LOC) {
					removeConnection(origin2, triple2, CONN_TYPE.FOOD, gs);
				} else {
					removeConnection(origin2, triple2, CONN_TYPE.LOC, gs);
				}
			} else {
				removeConnection(origin2, triple2, op.type_, gs);
			}
			if (origin3 != null) {
				removeConnection(origin3, triple3, op.type_, gs);
			}
			createConnection(origin1, triple2, op.type_, gs);
			if (op.new_type_ != null) {
				createConnection(origin2, triple3, op.new_type_, gs);
			} else {
				createConnection(origin2, triple3, op.type_, gs);
			}
			if (origin3 != null) {
				createConnection(origin3, triple1, op.type_, gs);
			}
		} else {
			System.out.println("TODO: applySearchOp");
			System.exit(1);
		}
	}

	public void popSearchOp(GraphScorer gs) {
		if (reverse == null) {
			System.out.println("Nothing to pop.");
			System.exit(1);
		}
		for (SearchOp rev : reverse) {
			applySearchOp(rev, gs);
		}
	}

	public void commitConnections() {
		Iterator<Triple<ActionNode, Argument, String>> dest_it = connections_.dest_iterator();
		while (dest_it.hasNext()) {
			Triple<ActionNode, Argument, String> dest = dest_it.next();
			Connection conn = connections_.getConnection(dest);
			if (ProjectParameters.VERBOSE)
				System.out.println(conn);
			ActionNode dest_node = dest.getFirst();
			SelectionalPreference pref = getSelectionalPreferencesOfNode(dest_node);
			
			Argument arg = dest.getSecond();
			ActionNode origin = conn.origin;
//			if (pref.loc == arg) {
//				arg.setType(Type.LOCATION);
//			}
//			if (arg.type() != Type.OBJECT) {
//				if (conn.type == CONN_TYPE.FOOD) {
//					arg.setType(Type.COOBJECT);
//				} else {
//					arg.setType(Type.LOCATION);
//				}
//			}
			ad.connectNodes(origin, dest_node, arg, dest.getThird(), "");
			if (arg.hasIngredients() || (getIngSpansForNode(origin) != null && getIngSpansForNode(origin).size() != 0)) {
				is_arg_food.put(arg, true);
			} else {
				is_arg_food.put(arg, false);
			}
		}
	}

	public Pair<List<Pair<String, Set<String>>>, Set<String>> collectMixPartPairs() {
		List<Pair<String, Set<String>>> list = new ArrayList<Pair<String, Set<String>>>();
		Set<String> mix_tkns = new HashSet<String>();
		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			SelectionalPreference pref = node_to_selectional_pref.get(node);
			for (Argument ing_arg : pref.arg_arr) {
				for (String span : ing_arg.nonIngredientSpans()) {
					if (span.equals("")) {
						continue;
					}
					Pair<ActionNode, String> origin = node.getOrigin(ing_arg, span);
					if (origin != null) {
						Set<String> ings = getIngSpansForNode(origin.getFirst());
						if (ings != null && ings.size() != 0) {
							String[] split = span.split(" ");
							Set<String> new_span = new HashSet<String>();
							for (String s : split) {
								if (s.trim().equals("")) {
									continue;
								}
								if (s.length() < 3) {
									continue;
								}
								if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(s)) {
									continue;
								}
								mix_tkns.add(Utils.stem(s));
								new_span.add(Utils.stem(s));
							}
							list.add(new Pair<String, Set<String>>(StringUtils.join(new_span), new HashSet<String>(ings)));
						}
					}
				}
			}
		}
		return new Pair<List<Pair<String, Set<String>>>, Set<String>>(list, mix_tkns);
	}
	
	public Pair<List<Pair<String, Set<String>>>, Set<String>> collectMixLocPartPairs() {
		List<Pair<String, Set<String>>> list = new ArrayList<Pair<String, Set<String>>>();
		Set<String> mix_tkns = new HashSet<String>();
		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			SelectionalPreference pref = node_to_selectional_pref.get(node);
			Argument loc = pref.loc;
			if (loc == null) {
				continue;
			}
			for (String span : loc.nonIngredientSpans()) {
				if (span.equals("")) {
					continue;
				}
				Pair<ActionNode, String> origin = node.getOrigin(loc, span);
				if (origin != null) {
					Set<String> ings = getLocTknsForNode(origin.getFirst());
					if (ings != null && ings.size() != 0) {
						String[] split = span.split(" ");
						Set<String> new_span = new HashSet<String>();
						for (String s : split) {
							if (s.trim().equals("")) {
								continue;
							}
							if (s.length() < 3) {
								continue;
							}
							if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(s)) {
								continue;
							}
							mix_tkns.add(Utils.stem(s));
							new_span.add(Utils.stem(s));
						}
						list.add(new Pair<String, Set<String>>(StringUtils.join(new_span), new HashSet<String>(ings)));
					}
				}
			}
		}
		return new Pair<List<Pair<String, Set<String>>>, Set<String>>(list, mix_tkns);
	}

	public Pair<List<Pair<String, Set<String>>>, Set<String>> collectLocPartPairs() {
		List<Pair<String, Set<String>>> list = new ArrayList<Pair<String, Set<String>>>();
		Set<String> mix_tkns = new HashSet<String>();
		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			SelectionalPreference pref = node_to_selectional_pref.get(node);
			Argument loc = pref.loc;
			if (loc == null) {
				continue;
			}
			ActionNode origin = getOrigin(node, loc, loc.string());
			if (origin == null) {
				continue;
			}
			Set<String> loc_tkns = getLocTknsForNode(origin);
			if (loc_tkns == null) {
				continue;
			}
			if (loc.string().equals("")) {
				continue;
			}
			list.add(new Pair<String, Set<String>>(loc.string(), new HashSet<String>(loc_tkns)));
			String[] split = loc.string().split(" ");
			for (String s : split) {
				if (s.trim().equals("")) {
					continue;
				}
				if (s.length() < 3) {
					continue;
				}
				mix_tkns.add(Utils.stem(s));
			}
		}
		return new Pair<List<Pair<String, Set<String>>>, Set<String>>(list, mix_tkns);
	}


	public void removeConnection(ActionNode origin, Triple<ActionNode, Argument, String> dest_triple, 
			CONN_TYPE type, GraphScorer gs) {
		if (gs.VERBOSE)
			System.out.println("remove conn " + origin + " -> " + dest_triple + " " + type);
		Connection conn = connections_.removeConnection(origin, dest_triple, type);

		// update the rest of the recipe for ing flow.
		for (int i = conn.destination.getFirst().index(); i < ad.numNodes(); i++) {
			ActionNode next_node = ad.getNodeAtIndex(i);
			Set<Connection> incoming_conns = connections_.getIncomingConnectionsToNode(next_node);
			Set<String> ings = node_to_ing_phrases_.get(next_node);
			if (ings == null) {
				ings = new HashSet<String>();
				node_to_ing_phrases_.put(next_node, ings);
			}
			ings.clear();

			Set<String> orig_ings = node_to_orig_ing_phrases_.get(next_node); 
			if (orig_ings != null) {
				ings.addAll(orig_ings);
			}

			if (incoming_conns != null && incoming_conns.size() != 0) {
				for (Connection incoming_conn : incoming_conns) {
					ActionNode other_origin = incoming_conn.origin;
					Set<String> other_ings = node_to_ing_phrases_.get(other_origin);
					if (other_ings != null) {
						ings.addAll(other_ings);
					}
					//				Set<String> other_all = node_to_ing_phrases_.get(other_origin);
					//				if (other_all != null) {
					//					all.addAll(other_all);
					//				}
				}
			}

			updateNode(gs, next_node);
		}
	}

	public void decommitConnections() {
		ad.clear();
	}
	
	public void collectIngredientsPerSpan() {
		for (int i = 0; i < ad.numNodes(); i++) {
			ActionNode node = ad.getNodeAtIndex(i);
			RecipeEvent event = node.event();

			double loc_prob = Double.NEGATIVE_INFINITY;
			Argument loc = null;
			Set<String> ings = node_to_ing_phrases_.get(node);
			if (ings == null) {
				ings = new HashSet<String>();
				node_to_ing_phrases_.put(node, ings);
			}
			Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
			if (orig_ings == null) {
				orig_ings = new HashSet<String>();
				node_to_orig_ing_phrases_.put(node, orig_ings);
			}

			Argument dobj = event.dobj();
			if (dobj == null) {
				dobj = event.setDirectObject("");
				dobj.addNonIngredientSpan("");
			} else if (dobj.hasIngredients()) {
				visible_arg_to_type_.put(dobj, CONN_TYPE.FOOD);
				for (String span : dobj.ingredientSpans()) {
					Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
					String[] split = amt_and_ing.getSecond().split(" ");
					for (String s : split) {
						if (s.trim().equals("")) {
							continue;
						}
						if (Utils.canWordBeNounOrAdjInWordNet(s)) {
							ings.add(Utils.stem(s));
							orig_ings.add(Utils.stem(s));
							all_ing_phrases_.add(Utils.stem(s));
						}
					}
				}
			}

			Iterator<Argument> prep_it = event.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep = prep_it.next();
				if (prep.type() != Type.LOCOROBJ && prep.type() != Type.LOCATION && prep.type() != Type.COOBJECT) {
					continue;
				}
				if (prep.string().equals("")) {
					continue;
				}
				if (prep.hasIngredients()) {
					visible_arg_to_type_.put(prep, CONN_TYPE.FOOD);
					for (String span : prep.ingredientSpans()) {
						Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
						String[] split = amt_and_ing.getSecond().split(" ");
						for (String s : split) {
							if (s.trim().equals("")) {
								continue;
							}
							if (Utils.canWordBeNounOrAdjInWordNet(s)) {
								ings.add(Utils.stem(s));
								orig_ings.add(Utils.stem(s));
								all_ing_phrases_.add(Utils.stem(s));
							}
						}
					}
				}
			}
		}
	}

	public void connectIngredientsPerSpan() {
		Set<String> ingredients = null;
		try {
			ingredients = ad.ingredientSetCopy();
		} catch (NullPointerException ex) {
			return;
		}
//		System.out.println(ingredients);
		Set<String> used_ingredients = new HashSet<String>();

		Iterator<ActionNode> node_iterator = ad.node_iterator();
		while (node_iterator.hasNext()) {
			ActionNode node = node_iterator.next();
			RecipeEvent event = node.event();

			Argument dobj = event.dobj();
			if (dobj == null) {
				dobj = event.setDirectObject("");
				dobj.addNonIngredientSpan("");
			} else if (!dobj.string().equals("")) {
				List<String> spans = new ArrayList<String>(dobj.nonIngredientSpans());
				for (String span : spans) {
					Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
					Pair<String, Double> best_ing_pair = IngredientParser.checkStringForIngredientUseTokenizer(
							amt_and_ing.getFirst(), amt_and_ing.getSecond(), ingredients, used_ingredients, false);
					String best_ing = best_ing_pair.getFirst();
					if (best_ing != null) {
						dobj.setSpanAsIngredient(best_ing, span);
						node.incorporateIngredient(span);
//												System.out.println("spaning0: " + span + " = " + best_ing);
						used_ingredients.add(best_ing);
						Set<String> ings = node_to_ing_phrases_.get(node);
						if (ings == null) {
							ings = new HashSet<String>();
							node_to_ing_phrases_.put(node, ings);
						}
						Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
						if (orig_ings == null) {
							orig_ings = new HashSet<String>();
							node_to_orig_ing_phrases_.put(node, orig_ings);
						}
						String[] split = amt_and_ing.getSecond().split(" ");
						for (String s : split) {
							if (s.trim().equals("")) {
								continue;
							}
							if (Utils.canWordBeNounOrAdjInWordNet(s)) {
								ings.add(Utils.stem(s));
								orig_ings.add(Utils.stem(s));
								all_ing_phrases_.add(Utils.stem(s));
								//								if (Utils.isMostlyNounInWordNet(s)) {
								//									String[] base = Utils.getBaseNoun(s);
								//									if (base.length == 0) {
								//										ings.add(s);
								//										orig_ings.add(s);
								//										all_ing_phrases_.add(s);
								//									} else {
								//										ings.add(base[0]);
								//										orig_ings.add(base[0]);
								//										all_ing_phrases_.add(base[0]);
								//									}
								//								} else {
								//									ings.add(s);
								//									orig_ings.add(s);
								//									all_ing_phrases_.add(s);
								//								}
							}
						}
						//						ings.add(amt_and_ing.getSecond());
						//						all_ing_phrases_.add(amt_and_ing.getSecond());
					}
				}
				if (dobj.hasIngredients()) {
					is_arg_food.put(dobj, true);
				}
			}
			//			if (!dobj.hasIngredients()) {
			//				if_leaf_dobj_loc_string_.put(node, dobj.string());
			//				Set<String> ings = node_to_ing_phrases_.get(node);
			//				if (ings == null) {
			//					ings = new HashSet<String>();
			//					node_to_ing_phrases_.put(node, ings);
			//				}
			//				Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
			//				if (orig_ings == null) {
			//					orig_ings = new HashSet<String>();
			//					node_to_orig_ing_phrases_.put(node, orig_ings);
			//				}
			//				String[] leaf_loc = dobj.string().split(" ");
			//				for (String s : leaf_loc) {
			//					ings.add(s);
			//					orig_ings.add(s);
			//				}
			//			} else {
			if_leaf_dobj_loc_string_.put(node, null);
			//			}


			Iterator<Argument> prep_it = event.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep = prep_it.next();
				if (prep.type() == Type.COOBJECT || prep.type() == Type.LOCOROBJ || prep.type() == Type.LOCATION) {
					List<String> spans = new ArrayList<String>(prep.nonIngredientSpans());
					for (String span : spans) {
						Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
						Pair<String, Double> best_ing_pair = IngredientParser.checkStringForIngredientUseTokenizer(
								amt_and_ing.getFirst(), amt_and_ing.getSecond(), ingredients, used_ingredients, false);
						String best_ing = best_ing_pair.getFirst();
						if (best_ing != null) {
							prep.setSpanAsIngredient(best_ing, span);
							node.incorporateIngredient(span);
//							System.out.println("spaning1: " + span + " = " + best_ing);
							used_ingredients.add(best_ing);
							Set<String> ings = node_to_ing_phrases_.get(node);
							if (ings == null) {
								ings = new HashSet<String>();
								node_to_ing_phrases_.put(node, ings);
							}
							Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
							if (orig_ings == null) {
								orig_ings = new HashSet<String>();
								node_to_orig_ing_phrases_.put(node, orig_ings);
							}
							String[] split = amt_and_ing.getSecond().split(" ");
							for (String s : split) {
								if (s.trim().equals("")) {
									continue;
								}
								if (Utils.isMostlyNounInWordNet(s)) {
									String[] base = Utils.getBaseNoun(s);
									if (base.length == 0) {
										ings.add(s);
										orig_ings.add(s);
										all_ing_phrases_.add(s);
									} else {
										ings.add(base[0]);
										orig_ings.add(base[0]);
										all_ing_phrases_.add(base[0]);
									}
								} else {
									ings.add(s);
									orig_ings.add(s);
									all_ing_phrases_.add(s);
								}
							}
							//							ings.add(amt_and_ing.getSecond());
							//							all_ing_phrases_.add(amt_and_ing.getSecond());
						}
					}
					if (prep.hasIngredients()) {
						is_arg_food.put(prep, true);
					}
				}
			}		
		}

		if (ingredients.size() != used_ingredients.size()) {
			node_iterator = ad.node_iterator();
			while (node_iterator.hasNext()) {
				ActionNode node = node_iterator.next();
				RecipeEvent event = node.event();

				Argument dobj = event.dobj();
				if (dobj == null) {
					dobj = event.setDirectObject("");
					dobj.addNonIngredientSpan("");
				} else if (!dobj.string().equals("")) {
					List<String> spans = new ArrayList<String>(dobj.nonIngredientSpans());
					for (String span : spans) {
						Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
						Pair<String, Double> best_ing_pair = IngredientParser.checkStringForIngredientUseTokenizer(
								amt_and_ing.getFirst(), amt_and_ing.getSecond(), ingredients, used_ingredients, false);
						String best_ing = best_ing_pair.getFirst();
						if (best_ing != null) {
							dobj.setSpanAsIngredient(best_ing, span);
							node.incorporateIngredient(span);
//							System.out.println("spaning2: " + span + " = " + best_ing);
							used_ingredients.add(best_ing);
							Set<String> ings = node_to_ing_phrases_.get(node);
							if (ings == null) {
								ings = new HashSet<String>();
								node_to_ing_phrases_.put(node, ings);
							}
							Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
							if (orig_ings == null) {
								orig_ings = new HashSet<String>();
								node_to_orig_ing_phrases_.put(node, orig_ings);
							}
							String[] split = amt_and_ing.getSecond().split(" ");
							for (String s : split) {
								if (s.trim().equals("")) {
									continue;
								}
								if (Utils.canWordBeNounOrAdjInWordNet(s)) {
									if (Utils.isMostlyNounInWordNet(s)) {
										String[] base = Utils.getBaseNoun(s);
										if (base.length == 0) {
											ings.add(s);
											orig_ings.add(s);
											all_ing_phrases_.add(s);
										} else {
											ings.add(base[0]);
											orig_ings.add(base[0]);
											all_ing_phrases_.add(base[0]);
										}
									} else {
										ings.add(s);
										orig_ings.add(s);
										all_ing_phrases_.add(s);
									}
								}
							}
							//							ings.add(amt_and_ing.getSecond());
							//							all_ing_phrases_.add(amt_and_ing.getSecond());
						}
					}
					if (dobj.hasIngredients()) {
						is_arg_food.put(dobj, true);
					}
				}

				Iterator<Argument> prep_it = event.prepositionalArgIterator();
				while (prep_it.hasNext()) {
					Argument prep = prep_it.next();
					if (prep.type() == Type.COOBJECT || prep.type() == Type.LOCOROBJ || prep.type() == Type.LOCATION) {
						List<String> spans = new ArrayList<String>(prep.nonIngredientSpans());
						for (String span : spans) {
							Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
							Pair<String, Double> best_ing_pair = IngredientParser.checkStringForIngredientUseTokenizer(
									amt_and_ing.getFirst(), amt_and_ing.getSecond(), ingredients, used_ingredients, false);
							String best_ing = best_ing_pair.getFirst();
							if (best_ing != null) {
								prep.setSpanAsIngredient(best_ing, span);
								node.incorporateIngredient(span);
//								System.out.println("spaning3: " + span + " = " + best_ing);
								used_ingredients.add(best_ing);
								Set<String> ings = node_to_ing_phrases_.get(node);
								if (ings == null) {
									ings = new HashSet<String>();
									node_to_ing_phrases_.put(node, ings);
								}
								Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
								if (orig_ings == null) {
									orig_ings = new HashSet<String>();
									node_to_orig_ing_phrases_.put(node, orig_ings);
								}
								String[] split = amt_and_ing.getSecond().split(" ");
								for (String s : split) {
									if (s.trim().equals("")) {
										continue;
									}
									if (Utils.canWordBeNounOrAdjInWordNet(s)) {
										if (Utils.isMostlyNounInWordNet(s)) {
											String[] base = Utils.getBaseNoun(s);
											if (base.length == 0) {
												ings.add(s);
												orig_ings.add(s);
												all_ing_phrases_.add(s);
											} else {
												ings.add(base[0]);
												orig_ings.add(base[0]);
												all_ing_phrases_.add(base[0]);
											}
										} else {
											ings.add(s);
											orig_ings.add(s);
											all_ing_phrases_.add(s);
										}
									}
								}
								//								ings.add(amt_and_ing.getSecond());
								//								all_ing_phrases_.add(amt_and_ing.getSecond());
							}
						}
						if (prep.hasIngredients()) {
							is_arg_food.put(prep, true);
						}
					}
				}		
			}
		}

		if (ingredients.size() != used_ingredients.size()) {
			node_iterator = ad.node_iterator();
			while (node_iterator.hasNext()) {
				ActionNode node = node_iterator.next();
				RecipeEvent event = node.event();

				Argument dobj = event.dobj();
				if (dobj == null) {
					dobj = event.setDirectObject("");
					dobj.addNonIngredientSpan("");
				} else if (!dobj.string().equals("")) {
					List<String> spans = new ArrayList<String>(dobj.nonIngredientSpans());
					for (String span : spans) {
						Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
						Pair<String, Double> best_ing_pair = IngredientParser.checkStringForIngredientUseTokenizer(
								amt_and_ing.getFirst(), amt_and_ing.getSecond(), ingredients, used_ingredients, true);
						String best_ing = best_ing_pair.getFirst();
						if (best_ing != null) {
							dobj.setSpanAsIngredient(best_ing, span);
							node.incorporateIngredient(span);
//							System.out.println("spaning4: " + span + " = " + best_ing);
							used_ingredients.add(best_ing);
							Set<String> ings = node_to_ing_phrases_.get(node);
							if (ings == null) {
								ings = new HashSet<String>();
								node_to_ing_phrases_.put(node, ings);
							}
							Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
							if (orig_ings == null) {
								orig_ings = new HashSet<String>();
								node_to_orig_ing_phrases_.put(node, orig_ings);
							}
							String[] split = amt_and_ing.getSecond().split(" ");
							for (String s : split) {
								if (s.trim().equals("")) {
									continue;
								}
								if (Utils.canWordBeNounOrAdjInWordNet(s)) {
									if (Utils.isMostlyNounInWordNet(s)) {
										String[] base = Utils.getBaseNoun(s);
										if (base.length == 0) {
											ings.add(s);
											orig_ings.add(s);
											all_ing_phrases_.add(s);
										} else {
											ings.add(base[0]);
											orig_ings.add(base[0]);
											all_ing_phrases_.add(base[0]);
										}
									} else {
										ings.add(s);
										orig_ings.add(s);
										all_ing_phrases_.add(s);
									}
								}
							}
							//							ings.add(amt_and_ing.getSecond());
							//							all_ing_phrases_.add(amt_and_ing.getSecond());
						}
					}
					if (dobj.hasIngredients()) {
						is_arg_food.put(dobj, true);
					}
				}

				Iterator<Argument> prep_it = event.prepositionalArgIterator();
				while (prep_it.hasNext()) {
					Argument prep = prep_it.next();
					if (prep.type() == Type.COOBJECT || prep.type() == Type.LOCOROBJ || prep.type() == Type.LOCATION) {
						List<String> spans = new ArrayList<String>(prep.nonIngredientSpans());
						for (String span : spans) {
							Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
							Pair<String, Double> best_ing_pair = IngredientParser.checkStringForIngredientUseTokenizer(
									amt_and_ing.getFirst(), amt_and_ing.getSecond(), ingredients, used_ingredients, true);
							String best_ing = best_ing_pair.getFirst();
							if (best_ing != null) {
								prep.setSpanAsIngredient(best_ing, span);
								node.incorporateIngredient(span);
//								System.out.println("spaning5: " + span + " = " + best_ing);
								used_ingredients.add(best_ing);
								Set<String> ings = node_to_ing_phrases_.get(node);
								if (ings == null) {
									ings = new HashSet<String>();
									node_to_ing_phrases_.put(node, ings);
								}
								Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
								if (orig_ings == null) {
									orig_ings = new HashSet<String>();
									node_to_orig_ing_phrases_.put(node, orig_ings);
								}
								String[] split = amt_and_ing.getSecond().split(" ");
								for (String s : split) {
									if (s.trim().equals("")) {
										continue;
									}
									if (Utils.canWordBeNounOrAdjInWordNet(s)) {
										if (Utils.isMostlyNounInWordNet(s)) {
											String[] base = Utils.getBaseNoun(s);
											if (base.length == 0) {
												ings.add(s);
												orig_ings.add(s);
												all_ing_phrases_.add(s);
											} else {
												ings.add(base[0]);
												orig_ings.add(base[0]);
												all_ing_phrases_.add(base[0]);
											}
										} else {
											ings.add(s);
											orig_ings.add(s);
											all_ing_phrases_.add(s);
										}
									}
								}
								//								ings.add(amt_and_ing.getSecond());
								//								all_ing_phrases_.add(amt_and_ing.getSecond());
							}
						}
						if (prep.hasIngredients()) {
							is_arg_food.put(prep, true);
						}
					}
				}		
			}
		}
	}

	public void identifyExplicitArgTypes(ThreeWayStringClassifier str_classifier, SelectionalPreferenceModel pref_model) {
		boolean seen_ings = false;
		for (int i = 0; i < ad.numNodes(); i++) {
			ActionNode node = ad.getNodeAtIndex(i);
			RecipeEvent event = node.event();

			double loc_prob = Double.NEGATIVE_INFINITY;
			Argument loc = null;
			Set<String> ings = node_to_ing_phrases_.get(node);
			if (ings == null) {
				ings = new HashSet<String>();
				node_to_ing_phrases_.put(node, ings);
			}
			Set<String> orig_ings = node_to_orig_ing_phrases_.get(node);
			if (orig_ings == null) {
				orig_ings = new HashSet<String>();
				node_to_orig_ing_phrases_.put(node, orig_ings);
			}

			Argument dobj = event.dobj();
			if (dobj.hasIngredients()) {
				visible_arg_to_type_.put(dobj, CONN_TYPE.FOOD);
				for (String span : dobj.nonIngredientSpans()) {
					Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
					String[] split = amt_and_ing.getSecond().split(" ");
					for (String s : split) {
						if (s.trim().equals("")) {
							continue;
						}
						if (Utils.canWordBeNounOrAdjInWordNet(s)) {
							ings.add(Utils.stem(s));
							orig_ings.add(Utils.stem(s));
						}
					}
				}
				seen_ings = true;
			} else if (!dobj.string().equals("")) {
				int space = dobj.string().lastIndexOf(' ');
				String str = dobj.string();
				if (space != -1) {
					str = dobj.string().substring(space + 1);
				}
//				System.out.println(str + " " + Utils.canWordBeNounInWordNet(str));
				if (!Utils.isMostlyNounInWordNet(str) || str.matches("-lrb-|-rrb-")) {
					visible_arg_to_type_.put(dobj, CONN_TYPE.OTHER);
				} else {
					boolean is_food = str_classifier.isPhraseMoreLikelyToBeIng(str, true, true);
					boolean food_obj = pref_model.doesPredMostLikelyHaveIngObj(event.predicate());
					if (is_food && (seen_ings || food_obj)) {
						visible_arg_to_type_.put(dobj, CONN_TYPE.FOOD);
						for (String span : dobj.nonIngredientSpans()) {
							Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
							String[] split = amt_and_ing.getSecond().split(" ");
							for (String s : split) {
								if (s.trim().equals("")) {
									continue;
								}
								if (Utils.canWordBeNounOrAdjInWordNet(s)) {
									ings.add(Utils.stem(s));
									orig_ings.add(Utils.stem(s));
								}
							}
						}
					} else {
						boolean is_loc = str_classifier.isPhraseMoreLikelyToBeLoc(str, true, true);
						if ((is_loc || !seen_ings) && dobj.nonIngredientSpans().size() == 1) {
							visible_arg_to_type_.put(dobj, CONN_TYPE.LOC);
							loc = dobj;
							loc_prob = str_classifier.lprobPhraseGivenLoc(str, true);
						} else {
							visible_arg_to_type_.put(dobj, CONN_TYPE.OTHER);
						}
					}
				}
			}

			boolean has_ing_prep = false;
			Iterator<Argument> prep_it = event.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep = prep_it.next();
				if (prep.type() != Type.LOCOROBJ && prep.type() != Type.LOCATION && prep.type() != Type.COOBJECT) {
					continue;
				}
				if (prep.string().equals("")) {
					continue;
				}
				if (prep.hasIngredients()) {
					visible_arg_to_type_.put(prep, CONN_TYPE.FOOD);
					for (String span : prep.nonIngredientSpans()) {
						Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
						String[] split = amt_and_ing.getSecond().split(" ");
						for (String s : split) {
							if (s.trim().equals("")) {
								continue;
							}
							if (Utils.canWordBeNounOrAdjInWordNet(s)) {
								ings.add(Utils.stem(s));
								orig_ings.add(Utils.stem(s));
							}
						}
					}
					seen_ings = true;
					has_ing_prep = true;
				} else {
					int space = prep.string().lastIndexOf(' ');
					String str = prep.string();
					if (space != -1) {
						str = prep.string().substring(space + 1);
					}
//					System.out.println(str + " " + Utils.isMostlyNounInWordNet(str));
					if (!Utils.isMostlyNounInWordNet(str) || str.matches("-lrb-|-rrb-")) {
						visible_arg_to_type_.put(prep, CONN_TYPE.OTHER);
					} else {
						boolean is_food = str_classifier.isPhraseMoreLikelyToBeIng(str, true, true);
						if (is_food && !has_ing_prep && seen_ings) {
							visible_arg_to_type_.put(prep, CONN_TYPE.FOOD);
							for (String span : prep.nonIngredientSpans()) {
								Pair<String, String> amt_and_ing = Measurements.splitAmountString(span);
								String[] split = amt_and_ing.getSecond().split(" ");
								for (String s : split) {
									if (s.trim().equals("")) {
										continue;
									}
									if (Utils.canWordBeNounOrAdjInWordNet(s)) {
										ings.add(Utils.stem(s));
										orig_ings.add(Utils.stem(s));
									}
								}
							}
							has_ing_prep = true;
						} else {
							boolean is_loc = str_classifier.isPhraseMoreLikelyToBeLoc(str, true, true);
							if (loc == null && is_loc && prep.nonIngredientSpans().size() == 1) {
								visible_arg_to_type_.put(prep, CONN_TYPE.LOC);
								loc = prep;
							} else if (prep.nonIngredientSpans().size() == 1) {
								double new_loc_prob = str_classifier.lprobPhraseGivenLoc(str, true);
								if (new_loc_prob > loc_prob) {
									visible_arg_to_type_.put(loc, CONN_TYPE.OTHER);
									loc = prep;
									loc_prob = new_loc_prob;
									visible_arg_to_type_.put(loc, CONN_TYPE.LOC);
								} else {
									visible_arg_to_type_.put(prep, CONN_TYPE.OTHER);
								}
							} else {
								visible_arg_to_type_.put(prep, CONN_TYPE.OTHER);
							}
						}
					}
				}
			}
		}
		System.out.println(visible_arg_to_type_);
//		System.exit(1);
	}

}
