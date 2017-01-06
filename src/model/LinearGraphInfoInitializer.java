package model;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import model.Connections.Connection;
import model.ConnectionsModel.CONN_TYPE;
import model.SelectionalPreferenceModel.SelectionalPreference;
import utils.Triple;
import data.ActionDiagram;
import data.RecipeEvent;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;

public class LinearGraphInfoInitializer {

	public static boolean USE_STR_TYPES = true;

	public static void initialize(GraphInfo gi, GraphScorer gs) {
		initialize(gi, gs, USE_STR_TYPES, true);
	}

	public static void initialize(GraphInfo gi, GraphScorer gs, boolean use_str_types, boolean use_raw_ings) {
		ActionDiagram ad = gi.actionDiagram();
		if (ad.numNodes() < 2) {
			return;
		}

		Iterator<ActionNode> node_iterator = ad.node_iterator();
		while (node_iterator.hasNext()) {
			ActionNode node = node_iterator.next();
			int index = node.index();
			if (index == ad.numNodes() - 1) {
				continue;
			}

			Set<String> origin_ing_spans = gi.getIngSpansForNode(node);
			CONN_TYPE conn_type = CONN_TYPE.FOOD;
			if (use_raw_ings && (origin_ing_spans == null || origin_ing_spans.size() == 0)) {
				conn_type = CONN_TYPE.LOC;
			}
			Set<Connection> orig_origs = gi.connections_.getIncomingConnectionsToNode(node);
			if (gi.visible_arg_to_type_.get(node.event().dobj()) == CONN_TYPE.LOC) {
				if (orig_origs == null || orig_origs.size() == 0) {
					conn_type = CONN_TYPE.LOC;
				} else {
					boolean found = false;
					for (Connection o : orig_origs) {
						if (o.type == CONN_TYPE.FOOD) {
							found = true;
							break;
						}
					}
					if (!found) {
						conn_type = CONN_TYPE.LOC;
					}
				}
			}

			ActionNode next = ad.getNodeAtIndex(index + 1);

			RecipeEvent event = next.event();
			Argument dobj = event.dobj();
			if (dobj == null) {
				dobj = event.setDirectObject("");
				dobj.addNonIngredientSpan("");
			}
			CONN_TYPE dobj_type = null;
			if (use_str_types && !dobj.string().equals("")) {
				dobj_type = gi.getArgType(dobj);
			}
			if (dobj_type == null || (dobj_type == conn_type)) {
				List<String> non_ingredient_spans = dobj.nonIngredientSpans();
				if (non_ingredient_spans.size() != 0) {
					boolean found = false;
					for (String span : non_ingredient_spans) {
						ActionNode orig = gi.getOrigin(node, dobj, span);
						if (orig == null) {
							if (gs.VERBOSE)
								System.out.println("create conn " + node + " -> " + new Triple<ActionNode, Argument, String>
								(next, dobj, span) + " " + (dobj_type == null ? conn_type : dobj_type));
							gi.createConnection(node, new Triple<ActionNode, Argument, String>
							(next, dobj, span), (dobj_type == null ? conn_type : dobj_type), gs);

							found = true;
							break;
						}
					}
					if (found) {
						continue;
					}
				}
			}

			Iterator<Argument> prep_it = event.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument arg = prep_it.next();
				if (arg.type() != Type.LOCOROBJ && arg.type() != Type.LOCATION && arg.type() != Type.COOBJECT) {
					continue;
				}
				CONN_TYPE arg_type = null;
				if (use_str_types && !arg.string().equals("")) {
					arg_type = gi.getArgType(arg);
				}
				if (arg_type == null || (arg_type == conn_type) ) {
					List<String> non_ingredient_spans = arg.nonIngredientSpans();
					if (non_ingredient_spans.size() != 0) {
						boolean found = false;
						for (String span : non_ingredient_spans) {
							ActionNode orig = gi.getOrigin(node, arg, span);
							if (orig == null) {
								gi.createConnection(node, new Triple<ActionNode, Argument, String>
								(next, arg, span), (arg_type == null ? conn_type : arg_type), gs);
								if (gs.VERBOSE)
									System.out.println("create conn " + node + " -> " + new Triple<ActionNode, Argument, String>
									(next, arg, span) + " " + (arg_type == null ? conn_type : arg_type));
								found = true;
								break;
							}
						}
						if (found) {
							break;
						}
					}
				}

			}

			Set<Connection> outgoing = gi.connections_.getOutgoingConnections(node);
			if (outgoing != null && outgoing.size() != 0) {
				continue;
			}

			Argument ghost_arg = event.addPrepositionalArgument("", "in");
			if (ghost_arg != null) {
				ghost_arg.addNonIngredientSpan("");
				gi.createConnection(node, new Triple<ActionNode, Argument, String>
				(next, ghost_arg, ""), conn_type, gs);
				if (gs.VERBOSE)
					System.out.println("create conn " + node + " -> " + new Triple<ActionNode, Argument, String>
					(next, ghost_arg, "") + " " + conn_type);
				continue;
			} else {
				Iterator<Argument> it = event.prepositionalArgIterator();
				while (it.hasNext()) {
					Argument arg = it.next();
					if (arg.string().equals("")) {
						arg.addNonIngredientSpan("");
						gi.createConnection(node, new Triple<ActionNode, Argument, String>
						(next, ghost_arg, ""), conn_type, gs);
						if (gs.VERBOSE)
							System.out.println("create conn " + node + " -> " + new Triple<ActionNode, Argument, String>
							(next, ghost_arg, "") + " " + conn_type);
						break;
					}
				}
			}
			outgoing = gi.connections_.getOutgoingConnections(node);
			if (outgoing == null || outgoing.size() == 0) {
				System.out.println(node.event());
				System.out.println("no connection");
				System.out.println(ad.getNodeAtIndex(node.index() + 1).event());
				System.exit(1);
			}
		}

		node_iterator = ad.node_iterator();
		while (node_iterator.hasNext()) {
			ActionNode node = node_iterator.next();
			gi.updateNode(gs, node);

			SelectionalPreference p = gi.getSelectionalPreferencesOfNode(node);
			System.out.println(node);
			System.out.println(node.event());
			System.out.println(p.pref_type);
			System.out.println("loc: " + p.loc);
			for (Argument a : p.arg_arr) {
				System.out.print(a + "   ");
			}
			System.out.println();
			for (Argument a : p.other_args) {
				System.out.print("other: " + a + "   ");
			}
			System.out.println();
			System.out.println(gi.connections_.getIncomingConnectionsToNode(node));
		}

	}
}
