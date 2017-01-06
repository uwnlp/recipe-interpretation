package model;

import java.util.Set;

import model.Connections.Connection;
import model.SelectionalPreferenceModel.SelectionalPreference;
import data.ActionDiagram;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;

public class ConnectionsModelLearner {
	public int num_outputs_num = 0;
	public int num_outputs_sum = 0;

	public int imp_closeness_num = 0;
	public int imp_closeness_sum = 0;

	public int evo_closeness_num = 0;
	public int evo_closeness_sum = 0;

	public int imp_location_closeness_num = 0;
	public int imp_location_closeness_sum = 0;

	public int evo_location_closeness_num = 0;
	public int evo_location_closeness_sum = 0;

	public int new_closeness_num = 0;
	public int new_closeness_sum = 0;

	public int new_location_closeness_num = 0;
	public int new_location_closeness_sum = 0;

	public int total_conn_cnt = 0;
	public int food_conn_cnt = 0;
	public int loc_conn_cnt = 0;
	public int other_conn_cnt = 0;

	public int evo_food_cnt = 0;
	public int evo_food_obj_cnt = 0;

	public int evo_loc_cnt = 0;
	public int evo_loc_obj_cnt = 0;

	public int imp_food_cnt = 0;
	public int imp_food_obj_cnt = 0;

	public int imp_loc_cnt = 0;
	public int imp_loc_obj_cnt = 0;

	public int new_loc_cnt = 0;
	public int new_loc_obj_cnt = 0;

	public int new_food_cnt = 0;
	public int new_food_obj_cnt = 0;

	public int food_closeness_num = 0;
	public int food_closeness_sum = 0;

	public int location_closeness_num = 0;
	public int location_closeness_sum = 0;

	public int new_food_closeness_num = 0;
	public int new_food_closeness_sum = 0;

	public int blank_ing_cnt = 0;

	public int lin_food_cnt = 0;
	public int nonlin_food_num = 0;
	public int nonlin_food_sum = 0;
	public int lin_loc_cnt = 0;
	public int nonlin_loc_num = 0;
	public int nonlin_loc_sum = 0;

	public int good_conn = 0;
	public int all_conn = 0;

	public void addNewCounts(ConnectionsModelLearner other) {
		num_outputs_num += other.num_outputs_num;
		num_outputs_sum += other.num_outputs_sum;

		imp_closeness_num += other.imp_closeness_num;
		imp_closeness_sum += other.imp_closeness_sum;

		evo_closeness_num += other.evo_closeness_num;
		evo_closeness_sum += other.evo_closeness_sum;

		imp_location_closeness_num += other.imp_location_closeness_num;
		imp_location_closeness_sum += other.imp_location_closeness_sum;

		evo_location_closeness_num += other.evo_location_closeness_num;
		evo_location_closeness_sum += other.evo_location_closeness_sum;

		new_closeness_num += other.new_closeness_num;
		new_closeness_sum += other.new_closeness_sum;

		new_location_closeness_num += other.new_location_closeness_num;
		new_location_closeness_sum += other.new_location_closeness_sum;

		total_conn_cnt += other.total_conn_cnt;
		food_conn_cnt += other.food_conn_cnt;
		loc_conn_cnt += other.loc_conn_cnt;
		other_conn_cnt += other.other_conn_cnt;

		evo_food_cnt += other.evo_food_cnt;
		evo_food_obj_cnt += other.evo_food_obj_cnt;

		evo_loc_cnt += other.evo_loc_cnt;
		evo_loc_obj_cnt += other.evo_loc_obj_cnt;

		imp_food_cnt += other.imp_food_cnt;
		imp_food_obj_cnt += other.imp_food_obj_cnt;

		imp_loc_cnt += other.imp_loc_cnt;
		imp_loc_obj_cnt += other.imp_loc_obj_cnt;

		new_loc_cnt += other.new_loc_cnt;
		new_loc_obj_cnt += other.new_loc_obj_cnt;

		new_food_cnt += other.new_food_cnt;
		new_food_obj_cnt += other.new_food_obj_cnt;

		food_closeness_num += other.food_closeness_num;
		food_closeness_sum += other.food_closeness_sum;

		location_closeness_num += other.location_closeness_num;
		location_closeness_sum += other.location_closeness_sum;

		new_food_closeness_num += other.new_food_closeness_num;
		new_food_closeness_sum += other.new_food_closeness_sum;

		blank_ing_cnt += other.blank_ing_cnt;

		lin_food_cnt += other.lin_food_cnt;
		nonlin_food_num += other.nonlin_food_num;
		nonlin_food_sum += other.nonlin_food_sum;
		lin_loc_cnt += other.lin_loc_cnt;
		nonlin_loc_num += other.nonlin_loc_num;
		nonlin_loc_sum += other.nonlin_loc_sum;

		good_conn += other.good_conn;
		all_conn += other.all_conn;
	}

	public ConnectionsModelLearner() {

	}

	public void addData(GraphInfo gi) {
		ActionDiagram ad = gi.actionDiagram();

		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
			Set<Connection> outgoing = gi.connections_.getOutgoingConnections(node);
			if (outgoing != null && outgoing.size() != 0) {
				num_outputs_num++;
				num_outputs_sum+=outgoing.size();
				good_conn++;
				all_conn+=outgoing.size();
			}
			for (int i = 0; i < pref.arg_arr.length; i++) {
				Argument arg = pref.arg_arr[i];
				if (arg == pref.loc) {
					continue;
				} else {
					for (String span : arg.nonIngredientSpans()) {
						Connection conn = gi.connections_.getConnection(node, arg, span);
						if (conn == null) {
							new_food_cnt++;
							//							food_conn_cnt++;
							if (arg.type() == Type.OBJECT) {
								new_food_obj_cnt++;
							}
							new_closeness_num++;
							new_closeness_sum+=(n+1);
							good_conn++;
							all_conn++;
						} else {
							food_conn_cnt++;
							total_conn_cnt++;
							int closeness = node.index() - conn.origin.index();
							if (closeness == 1) {
								lin_food_cnt++;
							} else {
								nonlin_food_num++;
								nonlin_food_sum+=closeness;
							}
							food_closeness_num++;
							food_closeness_sum+=closeness;
							if (span.equals("")) {
								imp_food_cnt++;
								if (arg.type() == Type.OBJECT) {
									imp_food_obj_cnt++;
								}
								if (closeness != 1) {
									System.out.println(ad.recipeName());
									System.out.println(node);
									System.out.println(node.event());
									System.out.println(arg + "   " + span);
									System.out.println(conn);
									System.exit(1);
								}
								imp_closeness_num++;
								imp_closeness_sum+=closeness;
							} else {
								evo_food_cnt++;
								if (arg.type() == Type.OBJECT) {
									evo_food_obj_cnt++;
								}
								evo_closeness_num++;
								evo_closeness_sum+=closeness;
							}
						}
					}
				}
			}

			Argument loc = pref.loc;
			if (loc != null) {
				String loc_string = loc.string();
				Connection conn = gi.connections_.getConnection(node, loc, loc_string);
				if (conn == null) {
					new_loc_cnt++;
					//					loc_conn_cnt++;
					if (loc.type() == Type.OBJECT) {
						new_loc_obj_cnt++;
					}
					new_location_closeness_num++;
					new_location_closeness_sum+=(n+1);
					good_conn++;
					all_conn++;
				} else {
					loc_conn_cnt++;
					total_conn_cnt++;
					int closeness = node.index() - conn.origin.index();
					if (closeness == 1) {
						lin_loc_cnt++;
					} else {
						nonlin_loc_num++;
						nonlin_loc_sum+=closeness;
					}
					location_closeness_num++;
					location_closeness_sum+=closeness;
					if (loc_string.equals("")) {
						imp_loc_cnt++;
						if (loc.type() == Type.OBJECT) {
							imp_loc_obj_cnt++;
						}
						imp_location_closeness_num++;
						imp_location_closeness_sum+=closeness;
					} else {
						evo_loc_cnt++;
						if (loc.type() == Type.OBJECT) {
							evo_loc_obj_cnt++;
						}
						evo_location_closeness_num++;
						evo_location_closeness_sum+=closeness;
					}
				}
			}

			for (int i = 0; i < pref.other_args.size(); i++) {
				Argument arg = pref.other_args.get(i);
				for (String span : arg.nonIngredientSpans()) {
					Connection conn = gi.connections_.getConnection(node, arg, span);
					if (conn == null) {
						continue;
					} else {
						other_conn_cnt++;
						total_conn_cnt++;
					}
				}
			}
		}
	}

	private double generateGeoDistribution(int num, int sum) {
		return (double)num / (double)sum;
	}

	public ConnectionsModel compute() {
		ConnectionsModel model = new ConnectionsModel();

		model.good_conn = (double)good_conn / all_conn;
		model.evo_closeness_geo = generateGeoDistribution(evo_closeness_num, evo_closeness_sum);
		model.imp_closeness_geo = generateGeoDistribution(imp_closeness_num, imp_closeness_sum);
		model.new_closeness_geo = generateGeoDistribution(new_closeness_num, new_closeness_sum);
		model.evo_location_closeness_geo = generateGeoDistribution(evo_location_closeness_num, evo_location_closeness_sum);
		model.imp_location_closeness_geo = generateGeoDistribution(imp_location_closeness_num, imp_location_closeness_sum);
		model.new_location_closeness_geo = generateGeoDistribution(new_location_closeness_num, new_location_closeness_sum);

		model.num_outputs_geo = generateGeoDistribution(num_outputs_num, num_outputs_sum);

		model.food_conn_prob = (double)food_conn_cnt / total_conn_cnt;
		model.loc_conn_prob = (double)loc_conn_cnt / total_conn_cnt;
		model.other_conn_prob = (double)other_conn_cnt / total_conn_cnt;

		model.evo_food_conn_prob = (double)evo_food_cnt / food_conn_cnt;
		model.imp_food_conn_prob = (double)imp_food_cnt / food_conn_cnt;
		//		model.new_food_prob = (double)new_food_cnt / food_conn_cnt;

		model.evo_loc_conn_prob = (double)evo_loc_cnt / loc_conn_cnt;
		model.imp_loc_conn_prob = (double)imp_loc_cnt / loc_conn_cnt;
		//		model.new_loc_prob = (double)new_loc_cnt / loc_conn_cnt;

		model.evo_food_obj_prob = (double)evo_food_obj_cnt / evo_food_cnt;
		model.imp_food_obj_prob = (double)imp_food_obj_cnt / imp_food_cnt;
		model.evo_loc_obj_prob = (double)evo_loc_obj_cnt / evo_loc_cnt;
		model.imp_loc_obj_prob = (double)imp_loc_obj_cnt / imp_loc_cnt;
		model.new_loc_obj_prob = (double)new_loc_obj_cnt / new_loc_cnt;
		model.new_food_obj_prob = (double)new_food_obj_cnt / new_food_cnt;

		model.food_closeness_geo = generateGeoDistribution(food_closeness_num, food_closeness_sum);
		model.location_closeness_geo = generateGeoDistribution(location_closeness_num, location_closeness_sum);

		model.prob_lin_food = (double)lin_food_cnt / food_conn_cnt;
		model.prob_lin_loc = (double)lin_loc_cnt / loc_conn_cnt;
		model.nonlin_food_geo = generateGeoDistribution(nonlin_food_num, nonlin_food_sum);
		model.nonlin_loc_geo = generateGeoDistribution(nonlin_loc_num, nonlin_loc_sum);

		return model;
	}
}
