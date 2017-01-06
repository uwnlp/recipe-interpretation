package model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import data.ProjectParameters;

public class ConnectionsModel {

	public static boolean relearn = false;
	
	public enum CONN_TYPE {
		FOOD("FOOD"),
		LOC("LOC"),
		OTHER("OTHER");

		private final String name;       

		private CONN_TYPE(String s) {
			name = s;
		}

		public boolean equalsName(String otherName) {
			return (otherName == null) ? false:name.equals(otherName);
		}

		public String toString() {
			return name;
		}

	};
	
//	public double num_outputs_geo = 0.999999;
	// submit version is 0.999
	public double good_conn = 0.99;
	public double num_outputs_geo = 0.99;
	public double imp_closeness_geo = 0.99999999999999;
	public double evo_closeness_geo = 0.1;
	public double imp_location_closeness_geo = 0.5;
	public double evo_location_closeness_geo = 0.5;
	
	public double prob_lin_food = 0.5;
	public double nonlin_food_geo = 0.2;
	public double prob_lin_loc = 0.5;
	public double nonlin_loc_geo = 0.2;
	
	public double food_closeness_geo = 0.20;
	public double location_closeness_geo = 0.20;
	public double new_closeness_geo = 0.001;
	public double new_location_closeness_geo = 0.001;
	
	public double prob_blank_no_conn = 0.01;
	public double prob_blank_ing_conn = 0.01;
	
	public double food_conn_prob = 0.95;
	public double loc_conn_prob = 0.05;
	public double other_conn_prob = 0.0;
	
	public double imp_food_conn_prob = 0.5;
	public double evo_food_conn_prob = 0.5;
	
	// NEW AUG 11 -- switch
//	public double imp_loc_conn_prob = 0.5;
//	public double evo_loc_conn_prob = 0.5;
	public double imp_loc_conn_prob = 0.0001;
	public double evo_loc_conn_prob = 0.9999;
	// NEW AUG 11 END
	
	public double evo_food_obj_prob = 0.5;
	public double evo_loc_obj_prob = 0.5;
	public double imp_food_obj_prob = 0.5;
	public double imp_loc_obj_prob = 0.5;
	public double new_loc_obj_prob = 0.5;
	public double new_food_obj_prob = 0.001;
	
	public double foodConnProb() {
		return food_conn_prob;
	}
	
	public double locConnProb() {
		return loc_conn_prob;
	}
	
	public double otherConnProb() {
		return other_conn_prob;
	}
	
	public double evoFoodObjectProb() {
		return evo_food_obj_prob;
	}
	
	public double evoFoodPrepProb() {
		return 1.0 - evo_food_obj_prob;
	}
	
	public double newFoodObjectProb() {
		return new_food_obj_prob;
	}
	
	public double newFoodPrepProb() {
		return 1.0 - new_food_obj_prob;
	}
	
	public double evoLocObjectProb() {
		return evo_loc_obj_prob;
	}
	
	public double evoLocPrepProb() {
		return 1.0 - evo_loc_obj_prob;
	}
	
	public double newLocObjectProb() {
		return new_loc_obj_prob;
	}
	
	public double newLocPrepProb() {
		return 1.0 - new_loc_obj_prob;
	}
	
	public double impFoodObjectProb() {
		return imp_food_obj_prob;
	}
	
	public double impFoodPrepProb() {
		return 1.0 - imp_food_obj_prob;
	}
	
	public double impLocObjectProb() {
		return imp_loc_obj_prob;
	}
	
	public double impLocPrepProb() {
		return 1.0 - imp_loc_obj_prob;
	}
	
	public double imp_ing_closeness_prob(int closeness) {
		if (closeness == 1) {
			return 0.0;
		} else {
			return Double.NEGATIVE_INFINITY;
		}
	}

	public double evo_ing_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - evo_closeness_geo, closeness - 1) * evo_closeness_geo);
	}

	public double imp_loc_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - imp_location_closeness_geo, closeness - 1) * imp_location_closeness_geo);
	}

	public double evo_loc_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - evo_location_closeness_geo, closeness - 1) * evo_location_closeness_geo);
	}

	public double new_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - new_closeness_geo, closeness - 1) * new_closeness_geo);
	}

	public double new_loc_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - new_location_closeness_geo, closeness - 1) * new_location_closeness_geo);
	}
	
	public double ing_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - food_closeness_geo, closeness - 1) * food_closeness_geo);
	}
	
	public double loc_closeness_prob(int closeness) {
		return Math.log(Math.pow(1.0 - location_closeness_geo, closeness - 1) * location_closeness_geo);
	}

	public double nonlin_food_closeness_prob(int closeness) {
		return Math.log(1.0 - prob_lin_food) + Math.log(Math.pow(1.0 - nonlin_food_geo, closeness - 1) * nonlin_food_geo);
	}
	
	public double nonlin_loc_closeness_prob(int closeness) {
		return Math.log(1.0 - prob_lin_loc) + Math.log(Math.pow(1.0 - nonlin_loc_geo, closeness - 1) * nonlin_loc_geo);
	}
	
	public double num_outputs_prob(int num_outputs) {
		return Math.log(Math.pow(1.0 - num_outputs_geo, num_outputs - 1) * num_outputs_geo);
	}
	
	public ConnectionsModel() {
		// defaults
	}
	
	public static ConnectionsModel readFromFile(String filename) throws IOException {
		ConnectionsModel model = new ConnectionsModel();
		BufferedReader br = new BufferedReader(new FileReader(filename));
		
		model.good_conn = Double.parseDouble(br.readLine());
		if (true || relearn) {
			model.num_outputs_geo = Double.parseDouble(br.readLine());
		} else {
			Double.parseDouble(br.readLine());
		}
//		model.imp_closeness_geo = Double.parseDouble(br.readLine());
		Double.parseDouble(br.readLine());
//		model.evo_closeness_geo = Double.parseDouble(br.readLine());
		Double.parseDouble(br.readLine());
		model.imp_location_closeness_geo = Double.parseDouble(br.readLine());
//		Double.parseDouble(br.readLine());
		model.evo_location_closeness_geo = Double.parseDouble(br.readLine());
//		Double.parseDouble(br.readLine());
		
		if (relearn) {
			model.new_closeness_geo = Double.parseDouble(br.readLine());
			model.new_location_closeness_geo = Double.parseDouble(br.readLine());
			model.food_conn_prob = Double.parseDouble(br.readLine());
			model.loc_conn_prob = Double.parseDouble(br.readLine());
			model.other_conn_prob = Double.parseDouble(br.readLine());
		} else {
			Double.parseDouble(br.readLine());
			Double.parseDouble(br.readLine());
			Double.parseDouble(br.readLine());
			Double.parseDouble(br.readLine());
			Double.parseDouble(br.readLine());
		}
		
		
		model.imp_food_conn_prob = Double.parseDouble(br.readLine());
//		Double.parseDouble(br.readLine());
		model.evo_food_conn_prob = Double.parseDouble(br.readLine());
//		model.imp_loc_conn_prob = Double.parseDouble(br.readLine());
		Double.parseDouble(br.readLine());
//		model.evo_loc_conn_prob = Double.parseDouble(br.readLine());
		Double.parseDouble(br.readLine());
		
		model.evo_food_obj_prob = Double.parseDouble(br.readLine());
		model.evo_loc_obj_prob = Double.parseDouble(br.readLine());
		model.imp_food_obj_prob = Double.parseDouble(br.readLine());
		model.imp_loc_obj_prob = Double.parseDouble(br.readLine());
		model.new_loc_obj_prob = Double.parseDouble(br.readLine());
		model.new_food_obj_prob = Double.parseDouble(br.readLine());
//		
		String line = br.readLine();
		if (line != null && relearn) {
			model.food_closeness_geo = Double.parseDouble(line);
			model.location_closeness_geo = Double.parseDouble(br.readLine());
			
			model.prob_lin_food = Double.parseDouble(line);
			model.nonlin_food_geo = Double.parseDouble(line);
			model.prob_lin_loc = Double.parseDouble(line);
			model.nonlin_loc_geo = Double.parseDouble(line);
		}
		br.close();
		return model;
	}
	
	public double impFoodProb() {
		return imp_food_conn_prob;
	}
	
	public double evoFoodProb() {
		return evo_food_conn_prob;
	}
	
	public void writeToFile(String filename)  throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		
		bw.write(good_conn + "\n");
		bw.write(num_outputs_geo + "\n");
		bw.write(imp_closeness_geo + "\n");
		bw.write(evo_closeness_geo + "\n");
		bw.write(imp_location_closeness_geo + "\n");
		bw.write(evo_location_closeness_geo + "\n");
		bw.write(new_closeness_geo + "\n");
		bw.write(new_location_closeness_geo + "\n");
		
		bw.write(food_conn_prob + "\n");
		bw.write(loc_conn_prob + "\n");
		bw.write(other_conn_prob + "\n");
		
		bw.write(imp_food_conn_prob + "\n");
		bw.write(evo_food_conn_prob + "\n");
		bw.write(imp_loc_conn_prob + "\n");
		bw.write(evo_loc_conn_prob + "\n");
		
		bw.write(evo_food_obj_prob + "\n");
		bw.write(evo_loc_obj_prob + "\n");
		bw.write(imp_food_obj_prob + "\n");
		bw.write(imp_loc_obj_prob + "\n");
		bw.write(new_loc_obj_prob + "\n");
		bw.write(new_food_obj_prob + "\n");
		
		bw.write(food_closeness_geo + "\n");
		bw.write(location_closeness_geo + "\n");
		
		bw.write(prob_lin_food + "\n");
		bw.write(nonlin_food_geo + "\n");
		bw.write(prob_lin_loc + "\n");
		bw.write(nonlin_loc_geo + "\n");
		
		bw.close();
	}
}
