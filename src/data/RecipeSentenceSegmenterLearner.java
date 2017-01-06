package data;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import utils.Measurements;
import utils.Pair;
import utils.Utils;

/**
 * This class trains RecipeSentenceSegmenter models using hard EM for a chosen # of iterations.
 * 
 * @author chloe
 *
 */

public class RecipeSentenceSegmenterLearner {

	private int NUM_ITERS = 5;
	private List<File> files_;

	public RecipeSentenceSegmenterLearner(String training_dir) {
		try {
			files_ = Utils.getInputFiles(training_dir, "txt");
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	public RecipeSentenceSegmenter run(String verbs_filename) {
		RecipeSentenceSegmenter segmenter = new RecipeSentenceSegmenter(true, verbs_filename);
		for (int i = 1; i <= NUM_ITERS; i++) {
			System.out.println("Iteration: " + i);
			segmenter = runOneIterHardEM(segmenter, i);
		}
		return segmenter;
	}

	private RecipeSentenceSegmenter runOneIterHardEM(RecipeSentenceSegmenter old_segmenter, int iter) {
		RecipeSentenceSegmenter new_segmenter = new RecipeSentenceSegmenter();
		Map<String, Map<String, Integer>> arg_bigram_cnt = new HashMap<String, Map<String, Integer>>();
		Map<String, Integer> arg_tkn_ttl_cnt_ = new HashMap<String, Integer>();

		Map<String, Integer> verb_cnt = new HashMap<String, Integer>();
		Map<String, Integer> verb_arg_num_sum = new HashMap<String, Integer>();
		Map<String, Map<Integer, Integer>> verb_arg_num_cnts = new HashMap<String, Map<Integer, Integer>>();
		

		Set<String> tokens = new HashSet<String>();
		tokens.add(RecipeSentenceSegmenter.END);

		int num_sentences = 0;
		int ttl_num_verbs = 0;
		int file_no = 1;
		
		for (File sentence_file: files_) {
			System.out.println("Segmenting file " + file_no + " of " + files_.size());
			file_no++;
			List<String> sentences = Utils.splitStepSentences(sentence_file);
			for (String sentence : sentences) {
				sentence = sentence.replaceAll("\\*", "");
				sentence = sentence.replaceAll("  ", " ");
				num_sentences++;
				String[] words = sentence.split(" ");
				List<String> best_tags = old_segmenter.getMostProbableSequence(words);
				if (best_tags == null) {
					continue;
				}

				List<Pair<String, List<String>>> pred_args = old_segmenter.extractPredArgStructures(words, best_tags);
				for (Pair<String, List<String>> pair : pred_args) {
					String pred = pair.getFirst();
					List<String> args = pair.getSecond();
					ttl_num_verbs++;
					Utils.incrementStringMapCount(verb_cnt, pred);
					Integer cnt = verb_arg_num_sum.get(pred);
					if (cnt == null) {
						verb_arg_num_sum.put(pred, args.size());
					} else {
						verb_arg_num_sum.put(pred, cnt.intValue() + args.size());
					}
					Integer arg_cnt = args.size();
					if (arg_cnt.intValue() > 3) {
						arg_cnt = 3;
					}
					Utils.incrementIntMapValueCount(verb_arg_num_cnts, pred, arg_cnt);
					for (String arg : args) {
						tokens.add(arg);
						String[] split = arg.split(" ");
						Utils.incrementStringMapCount(arg_tkn_ttl_cnt_, RecipeSentenceSegmenter.PRED + pred);
						Utils.incrementStringMapCount(arg_tkn_ttl_cnt_, RecipeSentenceSegmenter.END);
						for (int i = 0; i < split.length; i++) {
							String tkn = split[i];
							String prev = null;
							if (i != 0) {
								prev = split[i-1];
								if (Measurements.isNumericString(prev)) {
									prev = "NUM#";
								}
							}
							if (Measurements.isNumericString(tkn)) {
								tkn = "NUM#";
							}
							Utils.incrementStringMapCount(arg_tkn_ttl_cnt_, tkn);
							if (i == 0) {
								Utils.incrementStringMapValueCount(arg_bigram_cnt, RecipeSentenceSegmenter.PRED + pred, tkn);
								if (i == split.length - 1) {
									Utils.incrementStringMapValueCount(arg_bigram_cnt, tkn, RecipeSentenceSegmenter.END);
								}
							} else if (i == split.length - 1) {
								Utils.incrementStringMapValueCount(arg_bigram_cnt, tkn, RecipeSentenceSegmenter.END);
								Utils.incrementStringMapValueCount(arg_bigram_cnt, prev, tkn);
							} else {
								Utils.incrementStringMapValueCount(arg_bigram_cnt, prev, tkn);
							}
						}
					}
				}
			}
		}

		new_segmenter.setSentGeo((double)num_sentences / (ttl_num_verbs + num_sentences));
		new_segmenter.setTotalVerbsCount(ttl_num_verbs);
		for (String verb : verb_cnt.keySet()) {
			Integer ttl_cnt = verb_cnt.get(verb);
			Integer arg_sum = verb_arg_num_sum.get(verb);
			new_segmenter.setVerbGeo(verb, ttl_cnt.doubleValue() / (arg_sum.doubleValue() + ttl_cnt.doubleValue()));
			new_segmenter.setVerbCount(verb, ttl_cnt);
			
			Map<Integer, Integer> arg_num_cnt = verb_arg_num_cnts.get(verb);
			double denom = -1 * Math.log(ttl_cnt + 4);
			for (int i = 0; i <= 3; i++) {
				Integer cnt = arg_num_cnt.get(i);
				if (cnt == null) {
					new_segmenter.setVerbArgNumProb(verb, i, denom);
				} else {
					new_segmenter.setVerbArgNumProb(verb, i, Math.log(cnt.doubleValue() + 1) + denom);
				}
			}
		}

		new_segmenter.setArgBigramProb(RecipeSentenceSegmenter.OTHER, RecipeSentenceSegmenter.OTHER, -1 * Math.log(tokens.size() + 1));
		for (String token : arg_bigram_cnt.keySet()) {
			if (token.equals("END")) {
				continue;
			}
			Integer token_cnt = arg_tkn_ttl_cnt_.get(token);
			if (token_cnt == null) {
				System.out.println(token);
				System.exit(1);
			}
			Map<String, Integer> next_cnt = arg_bigram_cnt.get(token);
			double denom = -1 * Math.log(token_cnt + tokens.size() + 1);
			new_segmenter.setArgBigramProb(token, RecipeSentenceSegmenter.OTHER, denom);
			for (String next : next_cnt.keySet()) {
				Integer cnt = next_cnt.get(next);
				new_segmenter.setArgBigramProb(token, next, Math.log(cnt.doubleValue() + 1) + denom);
			}
		}
		try {
			new_segmenter.writeToFile(ProjectParameters.DEFAULT_DATA_DIRECTORY + "segmenter_iter" + iter + ".model");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return new_segmenter;
	}

	public static void main(String args[]) {
		if (args.length < 2 || args.length > 3) {
			System.out.println("Incorrect command line arguments.");
			System.out.println("Use: java RecipeSentenceSegmenterLearner training_file_dir verbs_file number_of_iterations(optional,default=5)");
		}
		String training_file_dir = args[0];
		String verbs_file = args[1];
		RecipeSentenceSegmenterLearner learner = new RecipeSentenceSegmenterLearner(training_file_dir);
		RecipeSentenceSegmenter segmenter = learner.run(verbs_file);
		if (args.length == 3) {
			learner.NUM_ITERS = Integer.parseInt(args[2]); 
		}
		segmenter.tryOut();
	}
}
