package model;

import java.util.HashMap;
import java.util.Map;

public class LinearDistribution {

	private static Map<Integer, Double> sum_map = new HashMap<Integer, Double>();
	
	static {
		for (int i = 2; i < 20; i++) {
			double sum = 0;
			for (int n = 1; n < i; n++) {
				sum += (1.0 / n);
			}
			sum_map.put(i, sum);
		}
	}
	
	public static double getSum(int i) {
		Double sum = sum_map.get(i);
		if (sum != null) {
			return sum;
		}
		double s = 0;
		for (int n = 1; n < i; n++) {
			s += (1.0 / n);
		}
		sum_map.put(i, s);
		return s;
	}
}
