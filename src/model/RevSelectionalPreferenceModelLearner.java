package model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import utils.Utils;
import model.SelectionalPreferenceModel.SelectionalPreference;
import data.ActionDiagram;
import data.ActionDiagram.ActionNode;

public class RevSelectionalPreferenceModelLearner {

	public int ttl = 0;
	public Set<String> verb_set = new HashSet<String>(); 
	public Map<String, Integer> pref_cnt = new HashMap<String, Integer>();
	public Map<String, Map<String, Integer>> pref_to_verb_cnt = new HashMap<String, Map<String, Integer>>();
	
	public void addData(GraphInfo gi) {
		ActionDiagram ad = gi.actionDiagram();

		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
			String predicate = node.event().predicate();
			verb_set.add(predicate);
			
			ttl++;
			Utils.incrementStringMapCount(pref_cnt, pref.pref_type);
			Utils.incrementStringMapValueCount(pref_to_verb_cnt, pref.pref_type, predicate);
		}
	}
	
	public RevSelectionalPreferenceModel computeModel() {
		RevSelectionalPreferenceModel model = new RevSelectionalPreferenceModel();
		Map<String, Map<String, Double>> selectional_pref_to_verb_log_probs = new HashMap<String, Map<String, Double>>();
		Map<String, Double> pref_probs = new HashMap<String, Double>();
		for (String pref : pref_cnt.keySet()) {
			Integer ttl_cnt = pref_cnt.get(pref);
			pref_probs.put(pref, Math.log(ttl_cnt) - Math.log(ttl));
			Map<String, Integer> verb_cnt = pref_to_verb_cnt.get(pref);
			double denom = -1*Math.log(ttl_cnt + (RevSelectionalPreferenceModel.alpha*(verb_cnt.size() + 1)));
			Map<String, Double> verb_prob = new HashMap<String, Double>();
			verb_prob.put(RevSelectionalPreferenceModel.UNK, Math.log(RevSelectionalPreferenceModel.alpha) + denom);
			for (String verb : verb_cnt.keySet()) {
				Integer cnt = verb_cnt.get(verb);
				verb_prob.put(verb, Math.log(RevSelectionalPreferenceModel.alpha + cnt) + denom);
			}
			selectional_pref_to_verb_log_probs.put(pref, verb_prob);
		}
		model.setSelectionalPrefModel(selectional_pref_to_verb_log_probs);
		model.setPriors(pref_probs);
		System.out.println(pref_probs);
		return model;
	}
}
