package eval;

import graphics.BratAnnotationReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import utils.Pair;
import utils.Triple;
import utils.Utils;
import model.ConnectionsModel;
import model.GraphInfo;
import model.GraphScorer;
import model.IBM1MixturesModel;
import model.LinearGraphInfoInitializer;
import model.LocalSearcher;
import model.RevSelectionalPreferenceModel;
import model.SelectionalPreferenceModel;
import model.ThreeWayStringClassifier;
import model.Connections.Connection;
import model.ConnectionsModel.CONN_TYPE;
import model.SelectionalPreferenceModel.SelectionalPreference;
import data.ActionDiagram;
import data.ActionDiagram.ActionNode;
import data.RecipeSentenceSegmenter;
import data.ProjectParameters;
import data.RecipeEvent;
import data.RecipeEvent.Argument;
import data.RecipeEvent.Argument.Type;
import edu.stanford.nlp.util.StringUtils;

/**
 * This class computes statistics of ActionDiagrams by comparing them to
 * gold-standard versions.
 * 
 * Usage:
 * 
 * To compare the gold standard annoations to the baseline system for the BananaMuffins 
 * recipes using the default data directory in ProjectParameters.DEFAULT_CLUSTER_DIRECTORY
 * 
 * java GoldStandardEvaluator BananaMuffins
 * 
 * To compare the gold standard annoations to the baseline system for the BananaMuffins 
 * recipes using a specified data directory:
 * 
 * java GoldStandardEvaluator BananaMuffins /path/to/data/directory
 * 
 * @author chloe
 *
 */
public class GoldStandardEvaluator {
	public static class Results {
		public int true_positive = 0;
		public int true_negative = 0;
		public int false_positive = 0;
		public int false_negative = 0;
		private int correct = 0;
		private int total = 0;
		public int tp_predicates = 0;
		public int fn_predicates = 0;
		public int fp_predicates = 0;

		public String toString() {
			return true_positive + " " + true_negative + " " + false_positive + " " + false_negative;
		}

		public double recall() {
			return (double)true_positive / (true_positive + false_negative);
		}

		public double precision() {
			return (double)true_positive / (true_positive + false_positive);
		}

		public double pred_recall() {
			return (double)tp_predicates / (tp_predicates + fn_predicates);
		}

		public double pred_precision() {
			return (double)tp_predicates / (tp_predicates + fp_predicates);
		}

		public double f1() {
			double recall = recall();
			double precision = precision();
			return 2.0*((precision * recall) / (precision + recall));
		}

		public double accuracy() {
			return (double)correct / total;
		}
	}

	private String model_directory = ProjectParameters.DEFAULT_DATA_DIRECTORY;
	private String data_directory;
	private String category;
	private boolean exact_match;
	private boolean ignore_other_args;
	private boolean account_for_inter_sentence_edges;
	private boolean account_for_intra_sentence_edges = false;
	private boolean care_only_about_origin = false;
	private boolean ignore_imp_objs = false;
	private boolean calculate_accuracy = false;

	public GoldStandardEvaluator() {
		this(null, ProjectParameters.DEFAULT_DATA_DIRECTORY);
	}

	public void setDataDirectory(String dir) {
		this.data_directory = dir;
	}

	public void setModelDirectory(String dir) {
		this.model_directory = dir;
	}

	public GoldStandardEvaluator(String category) {
		this(category, ProjectParameters.DEFAULT_DATA_DIRECTORY);
	}

	public GoldStandardEvaluator(String category, String data_directory) {
		this.category = category;
		this.data_directory = data_directory;
		this.exact_match = ProjectParameters.exact_match;
		this.ignore_other_args = ProjectParameters.ignore_other_args;
		this.account_for_inter_sentence_edges = ProjectParameters.account_for_inter_sentence_edges;
	}

	// Set flags for parameters used in evaluation
	//////////////////
	public void setExactMatch(boolean exact_match) {
		this.exact_match = exact_match;
	}

	public void setIgnoreOtherArgs(boolean ignore_other_args) {
		this.ignore_other_args = ignore_other_args;
	}

	public void setAccountForInterSentenceEdges(boolean account_for_inter_sentence_edges) {
		this.account_for_inter_sentence_edges = account_for_inter_sentence_edges;
	}
	//////////////////

	/*
	 * This methods counts the number of predicate --> arg edges for an event and adds
	 * them all as false positives in the results object. When the input event does not appear
	 * in the gold-standard version of the recipe, all of its edges are deemed incorrect.
	 */
	private void addIncorrectEventCountsToResults(ActionNode node, Results results, 
			boolean ignore_other_args, boolean account_for_inter_sentence_edges) {
		RecipeEvent event = node.event();
		if (event.dobj() != null) {
			if (account_for_intra_sentence_edges) {
				incorrecteventparse++;
				results.false_positive++;
			}
		}
		if (account_for_intra_sentence_edges) {
			results.false_positive += event.numPrepositionalArguments();
			incorrecteventparse+=event.numPrepositionalArguments();
		}
		if (!ignore_other_args) {
			if (account_for_intra_sentence_edges) {
				results.false_positive += event.numOtherArguments();
				incorrecteventparse+=event.numOtherArguments();
			}
		}

		if (account_for_inter_sentence_edges) {
			Iterator<String> output_it = node.outputEntityIterator();
			while (output_it.hasNext()) {
				TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> destinations = node.getDestinationsForEntity(output_it.next());
				for (Integer index : destinations.keySet()) {
					for (Triple<ActionNode, Argument, String> destination : destinations.get(index)) {
						//					Triple<ActionNode, Argument, String> destination = destinations.get(index);
						if (!ignore_imp_objs || destination.getSecond().type() != Argument.Type.OBJECT || !destination.getThird().equals("")) {
							if (ProjectParameters.VERBOSE) {
								System.out.println("INCORRECT");
							}
							results.false_positive++;
							incorrecteventparse++;
						}
					}
				}
			}
		}
	}

	/*
	 * This methods counts the number of predicate --> arg edges for an event and adds
	 * them all as false positives in the results object. When the input event does not appear
	 * in the gold-standard version of the recipe, all of its edges are deemed incorrect.
	 */
	private void addMissingEventCountsToResults(ActionNode node, Results results,
			boolean ignore_other_args, boolean account_for_inter_sentence_edges) {
		RecipeEvent event = node.event();
		if (event.dobj() != null) {
			if (account_for_intra_sentence_edges) {
				missingeventparse++;
				results.false_negative++;
			}
		}
		if (account_for_intra_sentence_edges) {
			results.false_negative += event.numPrepositionalArguments();
			missingeventparse+= event.numPrepositionalArguments();
		}
		if (!ignore_other_args) {
			if (account_for_intra_sentence_edges) {
				results.false_negative += event.numOtherArguments();
				missingeventparse += event.numOtherArguments();
			}
		}

		if (account_for_inter_sentence_edges) {
			Iterator<String> output_it = node.outputEntityIterator();
			while (output_it.hasNext()) {
				TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> destinations = node.getDestinationsForEntity(output_it.next());
				for (Integer index : destinations.keySet()) {
					for (Triple<ActionNode, Argument, String> destination : destinations.get(index)) {
						if (!ignore_imp_objs || destination.getSecond().type() != Argument.Type.OBJECT || !destination.getThird().equals("")) {
							if (ProjectParameters.VERBOSE) {
								System.out.println("MISSING");
							}
							results.false_negative++;
							missingeventparse++;
						}
					}
				}
			}
		}
	}

	int impl_obj = 0;

	int seqbutlong = 0;
	int longbutseq = 0;
	int longbutlong = 0;
	int missinglong = 0;
	int missingseq = 0;
	int incorrectlong = 0;
	int incorrectseq = 0;
	int extra = 0;
	int incorrecteventparse = 0;
	int missingeventparse = 0;
	int other = 0;

	private void evaluatePredicateArgumentsAccuracy(ActionNode node, ActionNode gold_node, RecipeEvent event, RecipeEvent gold_event, 
			boolean exact_match, boolean ignore_other_args, Map<ActionNode, ActionNode> action_to_gold_map, Results results,
			ActionDiagram ad, ActionDiagram gold_ad) {
		Iterator<String> output_it = node.outputEntityIterator();
		Iterator<String> gold_output_it = gold_node.outputEntityIterator();

		if (output_it.hasNext() && !gold_output_it.hasNext()) {
			System.out.println("Error: adding extra destination " + node);
			other+=node.numDestinations();
			results.false_positive += node.numDestinations();
		}
		if (!gold_output_it.hasNext()) {
			return;
		}
		results.total += gold_node.numDestinations();

		if (!output_it.hasNext() && gold_output_it.hasNext()) {
			System.out.println("Error: missing destination " + node);
			missingeventparse += gold_node.numDestinations();
			results.false_negative += gold_node.numDestinations();
			while (gold_output_it.hasNext()) {
				TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> gold_destinations = gold_node.getDestinationsForEntity(gold_output_it.next());
				for (Integer gold_index : gold_destinations.keySet()) {
					if (gold_node.index() == gold_index - 1) {
						num_true_lin_conns+=gold_destinations.get(gold_index).size();
					} else {
						num_true_long_conns+=gold_destinations.get(gold_index).size();
					}
				}
			}
			return;
		}
		TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> destinations = node.getDestinationsForEntity(output_it.next());
		TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> mapped_destinations = new TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>>();
		for (Integer index : destinations.keySet()) {
			for (Triple<ActionNode, Argument, String> destination_triple : destinations.get(index)) {
				ActionNode destination_node = destination_triple.getFirst();
				ActionNode mapped_destination_node = action_to_gold_map.get(destination_node);
				if (mapped_destination_node == null) {
					System.out.println("Error: incorrect destination " + node + " " + destination_node);
					incorrecteventparse++;
					results.false_positive++;
				} else {
					Set<Triple<ActionNode, Argument, String>> map_dest_set = mapped_destinations.get(mapped_destination_node.index());
					if (map_dest_set == null) {
						map_dest_set = new HashSet<Triple<ActionNode, Argument, String>>();
						mapped_destinations.put(mapped_destination_node.index(), map_dest_set);
					}
					map_dest_set.add(new Triple<ActionNode, Argument, String>(mapped_destination_node, destination_triple.getSecond(), destination_triple.getThird()));
				}
			}
		}
		System.out.println(mapped_destinations);
		if (mapped_destinations.size() == 0) {
			System.out.println("Error: no destination " + node);
			results.false_negative += gold_node.numDestinations();
			incorrecteventparse += gold_node.numDestinations();
			TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> gold_destinations = gold_node.getDestinationsForEntity(gold_output_it.next());
			for (Integer gold_index : gold_destinations.keySet()) {
				if (gold_node.index() == gold_index - 1) {
					num_true_lin_conns+=gold_destinations.get(gold_index).size();
				} else {
					num_true_long_conns+=gold_destinations.get(gold_index).size();
				}
			}
			return;
		}

		TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> gold_destinations = gold_node.getDestinationsForEntity(gold_output_it.next());
		Iterator<Integer> dest_it = mapped_destinations.keySet().iterator();
		Iterator<Integer> gold_dest_it = gold_destinations.keySet().iterator();
		Integer dest_index = dest_it.next();
		Integer gold_index = gold_dest_it.next();
		while (true) {
			if (dest_index < gold_index) {
				results.false_positive+=mapped_destinations.get(dest_index).size();
				System.out.println("Error");
				if (dest_index - gold_node.index() == 1) {
					incorrectseq+=mapped_destinations.get(dest_index).size();
				} else {
					incorrectlong+=mapped_destinations.get(dest_index).size();
				}
				System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index));
				System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index));
				if (dest_it.hasNext()) {
					dest_index = dest_it.next();
					continue;
				} else {
					if (gold_node.index() == gold_index - 1) {
						num_true_lin_conns+=gold_destinations.get(gold_index).size();
					} else {
						num_true_long_conns+=gold_destinations.get(gold_index).size();
					}
					results.false_negative+=gold_destinations.get(gold_index).size();
					while (gold_dest_it.hasNext()) {
						gold_index = gold_dest_it.next();
						if (gold_node.index() == gold_index - 1) {
							num_true_lin_conns+=gold_destinations.get(gold_index).size();
						} else {
							num_true_long_conns+=gold_destinations.get(gold_index).size();
						}
						if (gold_index - gold_node.index() == 1) {
							missingseq+=gold_destinations.get(gold_index).size();
						} else {
							missinglong+=gold_destinations.get(gold_index).size();
						}
						results.false_negative+=gold_destinations.get(gold_index).size();
					}
					break;
				}
			} else if (dest_index > gold_index) {
				if (gold_node.index() == gold_index - 1) {
					num_true_lin_conns+=gold_destinations.get(gold_index).size();
				} else {
					num_true_long_conns+=gold_destinations.get(gold_index).size();
				}
				results.false_negative+=gold_destinations.get(gold_index).size();
				System.out.println("Error");
				System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index));
				System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index));
				if (gold_index - gold_node.index() == 1) {
					missingseq+=gold_destinations.get(gold_index).size();
				} else {
					missinglong+=gold_destinations.get(gold_index).size();
				}
				if (gold_dest_it.hasNext()) {
					gold_index = gold_dest_it.next();
					continue;
				} else {
					//					System.out.println("Error: false positive" + node + " " + ad.getNodeAtIndex(dest_index));
					results.false_positive+=mapped_destinations.get(dest_index).size();
					while (dest_it.hasNext()) {
						int d = dest_it.next();
						//						System.out.println("Error: false positive" + node + " " + ad.getNodeAtIndex(d));
						results.false_positive+=mapped_destinations.get(d).size();
						if (dest_index - gold_node.index() == 1) {
							incorrectseq+=mapped_destinations.get(d).size();
						} else {
							incorrectlong+=mapped_destinations.get(d).size();
						}
					}
					break;
				}
			}
			if (dest_index == gold_index) {
				if (care_only_about_origin) {
					results.correct++;
					results.true_positive+=mapped_destinations.get(dest_index).size();
					System.out.println("GOOD");
					System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index));
					System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index));
				} else {
					Set<Triple<ActionNode, Argument, String>> mapped_dests = mapped_destinations.get(dest_index);
					Set<String> mapped_dest_strings = new HashSet<String>();
					for (Triple<ActionNode, Argument, String> dest : mapped_dests) {
						mapped_dest_strings.add(dest.getThird());
					}
					Set<Triple<ActionNode, Argument, String>> gold_dests = gold_destinations.get(gold_index);
					Set<String> gold_dest_strings = new HashSet<String>();
					for (Triple<ActionNode, Argument, String> dest : gold_dests) {
						gold_dest_strings.add(dest.getThird());
					}
					//					System.out.println("mapped dest strings: " + mapped_dest_strings);
					//					System.out.println("gold strings: " + gold_dest_strings);
					Set<String> used_gold_strings = new HashSet<String>();
					for (String dest_string : mapped_dest_strings) {
						//						String dest_string = mapped_destinations.get(dest_index).getThird();
						//						String gold_dest_string = gold_destinations.get(gold_index).getThird();
						boolean gold_dest_found = gold_dest_strings.contains(dest_string);
						if (gold_dest_found) {
							if (gold_node.index() == gold_index - 1) {
								num_true_lin_conns++;
								num_correct_lin_conns++;
							} else {
								num_true_long_conns++;
								num_correct_long_conns++;
							}
							results.correct++;
							results.true_positive++;
							used_gold_strings.add(dest_string);
						} else if (dest_string.equals("")) {
							System.out.println("Error1");
							System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index) + " " + dest_string);
							System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index) + " " + gold_dest_strings);
							results.false_positive++;
							if (dest_index - gold_node.index() == 1) {
								incorrectseq++;
							} else {
								incorrectlong++;
							}
						} else {
							boolean found = false;
							for (String gold_string : gold_dest_strings) {
								if (used_gold_strings.contains(gold_string)) {
									continue;
								}
								if (exact_match || gold_string.trim().equals("")) {
									if (dest_string.trim().equals(gold_string.trim())) {
										results.correct++;
										results.true_positive++;
										if (gold_node.index() == gold_index - 1) {
											num_true_lin_conns++;
											num_correct_lin_conns++;
										} else {
											num_true_long_conns++;
											num_correct_long_conns++;
										}
										used_gold_strings.add(gold_string);
										found = true;
										break;	
									} else if (dest_string.contains(gold_string) || gold_string.contains(dest_string)) {
										System.out.println(event);
										System.out.println(gold_event);
										System.out.println("gold: |" + gold_string + "|");
										System.out.println("dest: |" + dest_string + "|");
										System.exit(1);
									}
								} else if (dest_string.contains(gold_string) || gold_string.contains(dest_string)) {
									results.correct++;
									results.true_positive++;
									if (gold_node.index() == gold_index - 1) {
										num_true_lin_conns++;
										num_correct_lin_conns++;
									} else {
										num_true_long_conns++;
										num_correct_long_conns++;
									}
									used_gold_strings.add(gold_string);
									found = true;
									break;
								}
							}
							if (!found) {
								System.out.println("Error2");
								System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index) + " " + dest_string);
								System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index) + " " + gold_dest_strings);
								results.false_positive++;
								if (gold_index - gold_node.index() == 1) {
									incorrectseq++;
								} else {
									incorrectlong++;
								}
							}
						}
					}
					for (String gold_string : gold_dest_strings) {
						if (!used_gold_strings.contains(gold_string)) {
							System.out.println("Error3");
							System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index));
							System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index) + " " + gold_string);
							if (gold_node.index() == gold_index - 1) {
								num_true_lin_conns++;
							} else {
								num_true_long_conns++;
							}
							results.false_negative++;
							if (gold_index - gold_node.index() == 1) {
								missingseq++;
							} else {
								missinglong++;
							}
						}
					}
				}
				if (!dest_it.hasNext()) {
					while (gold_dest_it.hasNext()) {
						gold_index = gold_dest_it.next();
						if (gold_node.index() == gold_index - 1) {
							num_true_lin_conns+=gold_destinations.get(gold_index).size();
						} else {
							num_true_long_conns+=gold_destinations.get(gold_index).size();
						}
						System.out.println("Error4");
						System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index));
						System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index));
						results.false_negative+=gold_destinations.get(gold_index).size();
						if (gold_index - gold_node.index() == 1) {
							missingseq+=gold_destinations.get(gold_index).size();
						} else {
							missinglong+=gold_destinations.get(gold_index).size();
						}
					}
					break;
				} else if (!gold_dest_it.hasNext()) {
					while (dest_it.hasNext()) {
						dest_index = dest_it.next();
						System.out.println("Error5");
						System.out.println(node.event().predicate() + " " + node.index() + "  " + gold_ad.getNodeAtIndex(dest_index));
						System.out.println(gold_node.event().predicate() + " " + gold_node.index() + "  " + gold_ad.getNodeAtIndex(gold_index));
						results.false_positive+=mapped_destinations.get(dest_index).size();
						if (dest_index - gold_node.index() == 1) {
							incorrectseq+=mapped_destinations.get(dest_index).size();
						} else {
							incorrectlong+=mapped_destinations.get(dest_index).size();
						}
					}
					break;
				}
				dest_index = dest_it.next();
				gold_index = gold_dest_it.next();
			}
		}
	}


	private static class Cell {
		private Cell prevCell;
		private int score;
		private int row;
		private int col;

		public Cell(int r, int c) {
			row = r;
			col = c;
			prevCell = null;
		}

		public void setScore(int s) {
			score = s;
		}

		public void setPrevCell(Cell pc) {
			prevCell = pc;
		}

		public int getScore() {
			return score;
		}

		public int getCol() {
			return col;
		}

		public int getRow() {
			return row;
		}

		public Cell getPrevCell() {
			return prevCell;
		}
	}

	private static Cell getInitialPointer(int row, int col, Cell[][] scoreTable) {
		if (row == 0 && col != 0) {
			return scoreTable[row][col - 1];
		} else if (col == 0 && row != 0) {
			return scoreTable[row - 1][col];
		} else {
			return null;
		}
	}

	protected static int getInitialScore(int row, int col) {
		if (row == 0 && col != 0) {
			return col * 1;
		} else if (col == 0 && row != 0) {
			return row * 1;
		} else {
			return 0;
		}
	}

	protected static Cell[][] initialize(int n, int m) {
		Cell[][] scoreTable = new Cell[n][m];
		for (int i = 0; i < scoreTable.length; i++) {
			for (int j = 0; j < scoreTable[i].length; j++) {
				scoreTable[i][j] = new Cell(i, j);
			}
		}
		initializeScores(scoreTable);
		initializePointers(scoreTable);

		return scoreTable;
	}

	protected static void initializeScores(Cell[][] scoreTable) {
		for (int i = 0; i < scoreTable.length; i++) {
			for (int j = 0; j < scoreTable[i].length; j++) {
				scoreTable[i][j].setScore(getInitialScore(i, j));
			}
		}
	}

	protected static void initializePointers(Cell[][] scoreTable) {
		for (int i = 0; i < scoreTable.length; i++) {
			for (int j = 0; j < scoreTable[i].length; j++) {
				scoreTable[i][j].setPrevCell(getInitialPointer(i, j, scoreTable));
			}
		}
	}

	protected static void fillInCell(Cell currentCell, Cell cellAbove, Cell cellToLeft,
			Cell cellAboveLeft, ActionDiagram ad, ActionDiagram gold_ad) {
		int rowSpaceScore = cellAbove.getScore() - 1;
		int colSpaceScore = cellToLeft.getScore() - 1;
		int matchOrMismatchScore = cellAboveLeft.getScore();
		ActionNode node = ad.getNodeAtIndex(currentCell.getCol() - 1);
		ActionNode gold = gold_ad.getNodeAtIndex(currentCell.getRow() - 1);
		String ad_pred = node.event().predicate();
		String gold_pred = gold.event().predicate();
		if (ad_pred.contains(gold_pred) || gold_pred.contains(ad_pred)) {
			matchOrMismatchScore+=5;
			//			System.out.println(ad_pred + " " + gold_pred + " " + matchOrMismatchScore);
		} else {
			matchOrMismatchScore--;
		}

		if (rowSpaceScore >= colSpaceScore) {
			if (matchOrMismatchScore >= rowSpaceScore) {
				currentCell.setScore(matchOrMismatchScore);
				currentCell.setPrevCell(cellAboveLeft);
			} else {
				currentCell.setScore(rowSpaceScore);
				currentCell.setPrevCell(cellAbove);
			}
		} else {
			if (matchOrMismatchScore >= colSpaceScore) {
				currentCell.setScore(matchOrMismatchScore);
				currentCell.setPrevCell(cellAboveLeft);
			} else {
				currentCell.setScore(colSpaceScore);
				currentCell.setPrevCell(cellToLeft);
			}
		}
	}

	protected static boolean traceBackIsNotDone(Cell currentCell) {
		return currentCell.getPrevCell() != null;
	}

	protected static Cell getTracebackStartingCell(Cell[][] scoreTable) {
		return scoreTable[scoreTable.length - 1][scoreTable[0].length - 1];
	}

	protected static Map<ActionNode, ActionNode> getTraceback(Cell[][] scoreTable, ActionDiagram ad, ActionDiagram gold_ad) {
		Map<ActionNode, ActionNode> action_to_gold_map = new HashMap<ActionNode, ActionNode>();
		Cell currentCell = getTracebackStartingCell(scoreTable);
		while (traceBackIsNotDone(currentCell)) {
			if (ProjectParameters.VERBOSE) {
				System.out.println(currentCell.getRow() + " " + currentCell.getCol());
			}
			if (currentCell.getRow() - currentCell.getPrevCell().getRow() == 1
					&& currentCell.getCol() - currentCell.getPrevCell().getCol() == 1) {

				ActionNode node = ad.getNodeAtIndex(currentCell.getCol() - 1);
				ActionNode gold = gold_ad.getNodeAtIndex(currentCell.getRow() - 1);
				if (ProjectParameters.VERBOSE) {
					System.out.println(node + " " + gold);
				}
				action_to_gold_map.put(node, gold);
			} else {
				if (ProjectParameters.VERBOSE) {
					System.out.println(currentCell.getRow() + " " + currentCell.getCol());
				}
			}
			currentCell = currentCell.getPrevCell();
		}
		System.out.println(scoreTable[1][1].score);
		return action_to_gold_map;
	}

	protected static void fillIn(Cell[][] scoreTable, ActionDiagram ad, ActionDiagram ad_gold) {
		for (int row = 1; row < scoreTable.length; row++) {
			for (int col = 1; col < scoreTable[row].length; col++) {
				Cell currentCell = scoreTable[row][col];
				Cell cellAbove = scoreTable[row - 1][col];
				Cell cellToLeft = scoreTable[row][col - 1];
				Cell cellAboveLeft = scoreTable[row - 1][col - 1];
				fillInCell(currentCell, cellAbove, cellToLeft, cellAboveLeft, ad, ad_gold);
			}
		}
	}

	protected static Map<ActionNode, ActionNode> actionToGoldSearch(ActionDiagram ad, ActionDiagram ad_gold) {
		Iterator<ActionNode> ad_it = ad.node_iterator();
		while (ad_it.hasNext()) {
			System.out.print(ad_it.next().event().predicate() +" ");
		}
		System.out.println();
		ad_it = ad_gold.node_iterator();
		while (ad_it.hasNext()) {
			System.out.print(ad_it.next().event().predicate() +" ");
		}
		System.out.println();

		Map<ActionNode, ActionNode> action_to_gold_map = new HashMap<ActionNode, ActionNode>();
		if (ad.numNodes() != 0) {
			Cell[][] scoreTable = initialize(ad_gold.numNodes() + 1, ad.numNodes() + 1);
			fillIn(scoreTable, ad, ad_gold);
			action_to_gold_map = getTraceback(scoreTable, ad, ad_gold);
		}

		for (ActionNode ad_node : action_to_gold_map.keySet()) {
			ActionNode gold_node = action_to_gold_map.get(ad_node);
			if (ProjectParameters.VERBOSE) {
				System.out.println(ad_node.index() + ": " + ad_node.event().predicate() + " -> " + gold_node.index() + ": " + gold_node.event().predicate());
			}
		}

		return action_to_gold_map;
	}

	int missing_nodes = 0;
	int extra_nodes = 0;
	int right_nodes = 0;

	public void evaluatePossibleEdges(ActionDiagram ad, ActionDiagram gold_ad, Results results,
			boolean exact_match, boolean ignore_other_args, boolean account_for_inter_sentence_edges) {

		// TODO(chloe): check edge cases where one or more of the diagrams have no nodes.
		Map<ActionNode, ActionNode> action_to_gold_map = actionToGoldSearch(ad, gold_ad);
		Map<ActionNode, ActionNode> gold_to_action_map = new HashMap<ActionNode, ActionNode>();
		for (Map.Entry<ActionNode, ActionNode> entry : action_to_gold_map.entrySet()) {
			gold_to_action_map.put(entry.getValue(), entry.getKey());
		}
		Set<ActionNode> used_gold = new HashSet<ActionNode>();


		for (ActionNode curr_ad_node : action_to_gold_map.keySet()) {
			ActionNode curr_gold_node = action_to_gold_map.get(curr_ad_node);
			used_gold.add(curr_gold_node);

			RecipeEvent curr_event = curr_ad_node.event();
			RecipeEvent curr_gold_event = curr_gold_node.event();
			results.tp_predicates++;
			right_nodes++;

			TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> dests = curr_gold_node.getDestinationsForEntity("");
			if (dests != null) {
				for (Integer dest_id : dests.keySet()) {
					ActionNode gold_dest_node = gold_ad.getNodeAtIndex(dest_id);

					ActionNode dest_node = gold_to_action_map.get(gold_dest_node);
					if (dest_node == null) {
						results.false_positive+=dests.get(dest_id).size();
						results.false_negative+=dests.get(dest_id).size();
					} else {
						//						results.true_positive+=dests.get(dest_id).size();

						Set<String> dest_strings = new HashSet<String>();
						RecipeEvent dest_event = dest_node.event();
						Argument dest_dobj = dest_event.dobj();
						if (dest_dobj != null) {
							for (String span : dest_dobj.nonIngredientSpans()) {
								dest_strings.add(span.trim());
							}
							for (String span : dest_dobj.ingredientSpans()) {
								dest_strings.add(span.trim());
							}
							//							dest_strings.add(dest_dobj.string());
						}
						Iterator<Argument> dest_prep_it = dest_event.prepositionalArgIterator();
						while (dest_prep_it.hasNext()) {
							Argument dest_prep = dest_prep_it.next();
							for (String span : dest_prep.nonIngredientSpans()) {
								dest_strings.add(span.trim());
							}
							for (String span : dest_prep.ingredientSpans()) {
								dest_strings.add(span.trim());
							}
							//							dest_strings.add(dest_prep.string());
						}

						Set<Triple<ActionNode, Argument, String>> dest_args = dests.get(dest_id);
						for (Triple<ActionNode, Argument, String> dest_arg : dest_args) {
							String str = dest_arg.getThird().trim();
							String[] split = str.split(",| and ");
							str = StringUtils.join(split, ", ");
							str = str.replaceAll("  ", " ");
							boolean found = false;
							for (String dest_string : dest_strings) {
								if (dest_string.equals(str)) {
									found = true;
									break;
								} else if (!exact_match && (dest_string.contains(str) || str.contains(dest_string))) {
									found = true;
									break;
								}
							}
							if (found) {
								results.true_positive++;
							} else {
								System.out.println(dest_event);
								System.out.print(str + " ");
								for (String d : dest_strings){
									System.out.print("|"+ d + "| ");
								}
								System.out.println();
								results.false_positive++;
								results.false_negative++;
							}
						}
					}
				}
			}
		}

		Iterator<ActionNode> ad_iter = ad.node_iterator();
		while (ad_iter.hasNext()) {
			ActionNode curr_ad_node = ad_iter.next();
			if (!action_to_gold_map.containsKey(curr_ad_node)) {
				extra_nodes++;
				TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> dests = curr_ad_node.getDestinationsForEntity("");
				if (dests != null) {
					for (Integer dest_id : dests.keySet()) {
						results.false_positive++;
						//						results.false_negative++;
					}
				}
			}
		}

		Iterator<ActionNode> gold_iter = gold_ad.node_iterator();
		while (gold_iter.hasNext()) {
			ActionNode curr_gold_node = gold_iter.next();
			if (!used_gold.contains(curr_gold_node)) {
				missing_nodes++;
				TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> dests = curr_gold_node.getDestinationsForEntity("");
				if (dests != null) {
					for (Integer dest_id : dests.keySet()) {
						ActionNode gold_dest_node = gold_ad.getNodeAtIndex(dest_id);
						ActionNode dest_node = gold_to_action_map.get(gold_dest_node);
						//						results.false_positive+=dests.get(dest_id).size();
						results.false_negative+=dests.get(dest_id).size();
					}
				}
			}
		}
	}

	private String getLocationStr(ActionNode node, GraphInfo gi) {
		if (gi != null) {
			SelectionalPreference pref = gi.getSelectionalPreferencesOfNode(node);
			if (pref.loc != null) {
				return pref.loc.string();
			}
			return null;
		}
		Iterator<Argument> prep_it = node.event().prepositionalArgIterator();
		while (prep_it.hasNext()) {
			Argument prep = prep_it.next();
			if (prep.type() == Type.LOCATION) {
				return prep.string();
			}
		}
		TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> dests = node.getDestinationsForEntity("");
		if (dests != null) {
			for (Set<Triple<ActionNode, Argument, String>> dest_set : dests.values()) {
				for (Triple<ActionNode, Argument, String> dest : dest_set) {
					if (dest.getSecond().type() == Type.LOCATION) {
						return node.event().dobj().string();
					}
				}
			}
		}
		return null;
	}

	private boolean hasIngs(ActionNode node, Argument arg, GraphInfo gi) {
		if (arg.hasIngredients()) {
			return true;
		}
		if (gi != null) {
			if (gi.visible_arg_to_type_.get(arg) == CONN_TYPE.FOOD) {
				return true;
			}
			for (String span : arg.nonIngredientSpans()) {
				Connection conn = gi.connections_.getConnection(node, arg, span);
				if (conn != null) {
					if (conn.type == CONN_TYPE.FOOD) {
						return true;
					}
				}
			}
		}
		for (String span : arg.nonIngredientSpans()) {
			Pair<ActionNode, String> origin = node.getOrigin(arg, span);
			if (origin == null || origin.getFirst() == null) {
				continue;
			}
			ActionNode origin_node = origin.getFirst();
			Argument origin_dobj = origin_node.event().dobj(); 
			if (origin_dobj != null && hasIngs(origin_node, origin_dobj, gi)) {
				return true;
			}
			Iterator<Argument> prep_it = origin_node.event().prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep = prep_it.next();
				if (hasIngs(origin_node, prep, gi)) {
					return true;
				}
			}

			Iterator<Argument> other_it = origin_node.event().otherArgIterator();
			while (other_it.hasNext()) {
				Argument other = other_it.next();
				if (hasIngs(origin_node, other, gi)) {
					return true;
				}
			}

		}
		return false;
	}

	private TreeSet<String> getFoodStrings(ActionNode node, GraphInfo gi) {
		TreeSet<String> foods = new TreeSet<String>();
		Iterator<Argument> prep_it = node.event().prepositionalArgIterator();
		while (prep_it.hasNext()) {
			Argument prep = prep_it.next();
			if (prep.type() == Type.COOBJECT || prep.hasIngredients() || hasIngs(node, prep, gi)) {
				foods.add(prep.string().trim().replaceAll(" ", ""));
			}
		}
		Argument dobj = node.event().dobj();
		if (dobj != null && dobj.hasIngredients()) {
			foods.add(dobj.string().trim().replaceAll(" ", ""));
		} else if (dobj != null && hasIngs(node, dobj, gi)) {
			foods.add(dobj.string().trim().replaceAll(" ", ""));
		}
		return foods;
	}

	public void evaluateFoods(ActionDiagram ad, ActionDiagram gold_ad, Results results,
			boolean exact_match, GraphInfo gi) {

		// TODO(chloe): check edge cases where one or more of the diagrams have no nodes.
		Map<ActionNode, ActionNode> action_to_gold_map = actionToGoldSearch(ad, gold_ad);
		Map<ActionNode, ActionNode> gold_to_action_map = new HashMap<ActionNode, ActionNode>();
		for (Map.Entry<ActionNode, ActionNode> entry : action_to_gold_map.entrySet()) {
			gold_to_action_map.put(entry.getValue(), entry.getKey());
		}
		Set<ActionNode> used_gold = new HashSet<ActionNode>();


		for (ActionNode curr_ad_node : action_to_gold_map.keySet()) {
			ActionNode curr_gold_node = action_to_gold_map.get(curr_ad_node);
			used_gold.add(curr_gold_node);

			RecipeEvent curr_event = curr_ad_node.event();
			RecipeEvent curr_gold_event = curr_gold_node.event();
			results.tp_predicates++;
			right_nodes++;

			TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> dests = curr_gold_node.getDestinationsForEntity("");
			if (dests != null) {
				for (Integer dest_id : dests.keySet()) {
					ActionNode gold_dest_node = gold_ad.getNodeAtIndex(dest_id);
					TreeSet<String> gold_foods = getFoodStrings(gold_dest_node, null);
					System.out.println("GOLD FOODS: " + gold_foods);

					ActionNode dest_node = gold_to_action_map.get(gold_dest_node);
					if (dest_node == null) {
						String loc = getLocationStr(gold_dest_node, null);
						if (loc != null) {
							results.false_negative+=gold_foods.size();
						}
					} else {
						//						results.true_positive+=dests.get(dest_id).size();
						TreeSet<String> foods = getFoodStrings(dest_node, gi);
						System.out.println("FOODS: " + foods);
						if (foods.size() == 0) {
							results.false_negative+=gold_foods.size();
						} else if (gold_foods.size() == 0) {
							results.false_positive += foods.size();
						} else {
							Iterator<String> food_it = foods.iterator();
							Iterator<String> gold_it = gold_foods.iterator();
							String f = food_it.next();
							String g = gold_it.next();
							while (true) {
								if (f.equals(g)) {
									results.true_positive++;
									if (!food_it.hasNext()) {
										while (gold_it.hasNext()) {
											gold_it.next();
											results.false_negative++;
										}
										break;
									} else if (!gold_it.hasNext()) {
										while (food_it.hasNext()) {
											food_it.next();
											results.false_positive++;
										}
										break;
									} else {
										f = food_it.next();
										g = gold_it.next();
									}
								} else if (f.compareTo(g) < 0) {
									results.false_positive++;
									if (!food_it.hasNext()) {
										results.false_negative++;
										while (gold_it.hasNext()) {
											gold_it.next();
											results.false_negative++;
										}
										break;
									}
									f = food_it.next();
								} else {
									results.false_negative++;
									if (!gold_it.hasNext()) {
										results.false_positive++;
										while (food_it.hasNext()) {
											food_it.next();
											results.false_positive++;
										}
										break;
									}
									g = gold_it.next();
								}
							}
						}
					}
				}
			}
		}

		Iterator<ActionNode> ad_iter = ad.node_iterator();
		while (ad_iter.hasNext()) {
			ActionNode curr_ad_node = ad_iter.next();
			if (!action_to_gold_map.containsKey(curr_ad_node)) {
				TreeSet<String> foods = getFoodStrings(curr_ad_node, gi);
				results.false_positive += foods.size();
			}
		}

		Iterator<ActionNode> gold_iter = gold_ad.node_iterator();
		while (gold_iter.hasNext()) {
			ActionNode curr_gold_node = gold_iter.next();
			if (!used_gold.contains(curr_gold_node)) {
				TreeSet<String> foods = getFoodStrings(curr_gold_node, null);
				results.false_negative += foods.size();
			}
		}
	}

	public void evaluateLocations(ActionDiagram ad, ActionDiagram gold_ad, Results results,
			boolean exact_match, GraphInfo gi) {

		// TODO(chloe): check edge cases where one or more of the diagrams have no nodes.
		Map<ActionNode, ActionNode> action_to_gold_map = actionToGoldSearch(ad, gold_ad);
		Map<ActionNode, ActionNode> gold_to_action_map = new HashMap<ActionNode, ActionNode>();
		for (Map.Entry<ActionNode, ActionNode> entry : action_to_gold_map.entrySet()) {
			gold_to_action_map.put(entry.getValue(), entry.getKey());
		}
		Set<ActionNode> used_gold = new HashSet<ActionNode>();


		for (ActionNode curr_ad_node : action_to_gold_map.keySet()) {
			ActionNode curr_gold_node = action_to_gold_map.get(curr_ad_node);
			used_gold.add(curr_gold_node);

			RecipeEvent curr_event = curr_ad_node.event();
			RecipeEvent curr_gold_event = curr_gold_node.event();
			results.tp_predicates++;
			right_nodes++;

			TreeMap<Integer, Set<Triple<ActionNode, Argument, String>>> dests = curr_gold_node.getDestinationsForEntity("");
			if (dests != null) {
				for (Integer dest_id : dests.keySet()) {
					ActionNode gold_dest_node = gold_ad.getNodeAtIndex(dest_id);


					ActionNode dest_node = gold_to_action_map.get(gold_dest_node);
					if (dest_node == null) {
						String loc = getLocationStr(gold_dest_node, null);
						if (loc != null) {
							results.false_negative++;
						}
					} else {
						//						results.true_positive+=dests.get(dest_id).size();

						RecipeEvent dest_event = dest_node.event();
						String dest_loc = getLocationStr(dest_node, gi);

						String gold_loc = getLocationStr(gold_dest_node, null);
						System.out.println("LOCATIONS: " + dest_loc + "  " + gold_loc);
						if (dest_loc == null) {
							if (gold_loc != null) {
								results.false_negative++;
							}
						} else {
							if (gold_loc == null) {
								results.false_positive++;
							} else {
								results.true_positive++;
							}
						}
					}
				}
			}
		}

		Iterator<ActionNode> ad_iter = ad.node_iterator();
		while (ad_iter.hasNext()) {
			ActionNode curr_ad_node = ad_iter.next();
			if (!action_to_gold_map.containsKey(curr_ad_node)) {
				String loc = getLocationStr(curr_ad_node, gi);
				if (loc != null) {
					results.false_positive++;
				}
			}
		}

		Iterator<ActionNode> gold_iter = gold_ad.node_iterator();
		while (gold_iter.hasNext()) {
			ActionNode curr_gold_node = gold_iter.next();
			if (!used_gold.contains(curr_gold_node)) {
				String loc = getLocationStr(curr_gold_node, null);
				if (loc != null) {
					results.false_negative++;
				}
			}
		}
	}

	public int num_true_long_conns = 0;
	public int num_correct_long_conns = 0;
	public int num_true_lin_conns = 0;
	public int num_correct_lin_conns = 0;

	public void evaluateIntraSentenceEdges(ActionDiagram ad, ActionDiagram gold_ad, Results results,
			boolean exact_match, boolean ignore_other_args, boolean account_for_inter_sentence_edges) {

		// TODO(chloe): check edge cases where one or more of the diagrams have no nodes.
		Map<ActionNode, ActionNode> action_to_gold_map = actionToGoldSearch(ad, gold_ad);
		Set<ActionNode> used_gold = new HashSet<ActionNode>();

		for (ActionNode curr_ad_node : action_to_gold_map.keySet()) {
			ActionNode curr_gold_node = action_to_gold_map.get(curr_ad_node);
			used_gold.add(curr_gold_node);

			RecipeEvent curr_event = curr_ad_node.event();
			RecipeEvent curr_gold_event = curr_gold_node.event();
			results.tp_predicates++;

			if (calculate_accuracy) {
				evaluatePredicateArgumentsAccuracy(curr_ad_node, curr_gold_node, curr_event, curr_gold_event, exact_match, ignore_other_args, 
						action_to_gold_map, results, ad, gold_ad);
			} else {
				evaluatePredicateArgumentsAccuracy(curr_ad_node, curr_gold_node, curr_event, curr_gold_event, exact_match, ignore_other_args, 
						action_to_gold_map, results, ad, gold_ad);
			}
		}

		Iterator<ActionNode> ad_iter = ad.node_iterator();
		while (ad_iter.hasNext()) {
			ActionNode curr_ad_node = ad_iter.next();
			if (!action_to_gold_map.containsKey(curr_ad_node)) {
				System.out.println("incorrect  " + curr_ad_node.index() + "  " + curr_ad_node.event().predicate());
				addIncorrectEventCountsToResults(curr_ad_node, results, ignore_other_args, account_for_inter_sentence_edges);
				results.fp_predicates++;
			}
		}

		Iterator<ActionNode> gold_iter = gold_ad.node_iterator();
		while (gold_iter.hasNext()) {
			ActionNode curr_gold_node = gold_iter.next();
			if (!used_gold.contains(curr_gold_node)) {
				System.out.println("missing  " + curr_gold_node.index() + "  " + curr_gold_node.event().predicate());
				addMissingEventCountsToResults(curr_gold_node, results, ignore_other_args, account_for_inter_sentence_edges);
				results.fn_predicates++;
			}
		}
	}



	int num_implicit_args = 0;
	int num_total_args = 0;

	int num_verbs = 0;
	int num_correct_pref_types = 0;

	public Results oracleEvaluate(File arg_file, File ann_file, File step_file, File fulltext_file, 
			boolean exact_match, boolean ignore_other_args, boolean account_for_inter_sentence_edges, int iter, GraphScorer gs) {

		Results results = new Results();
		ActionDiagram oracle = BratAnnotationReader.generativeActionDiagramFromBratAnnotations(ann_file, step_file, fulltext_file, false, false);

		ActionDiagram gold = BratAnnotationReader.generativeActionDiagramFromBratAnnotations(ann_file, step_file, fulltext_file, true, true);

		GraphInfo gi = new GraphInfo(oracle, true, true, gs.str_classifier, gs.selectional_pref_model);
		System.out.println(gi.visible_arg_to_type_);
		gs.changeGraph(gi);
		gi.update(gs);
		LinearGraphInfoInitializer.initialize(gi, gs, true, true);
		gs.initNodeScores();

		LocalSearcher searcher = new LocalSearcher(gi, gs);
		searcher.initialize();
		searcher.run();
		gi.commitConnections();
		oracle = gi.actionDiagram();
		//oracle.printDotFile("AllRecipes_20_Args/AnnotationSession/AnnotationSession-searchgraph/" + arg_file.getName() + ".4hardem99_cam_10000_filter.oracle.gv");

		evaluateIntraSentenceEdges(oracle, gold, results, exact_match, ignore_other_args, account_for_inter_sentence_edges);

		if (results.false_negative != results.false_positive) {
			System.out.println("error2");
		}
		return results;
	}

	public Results evaluate(File arg_file, File ann_file, File step_file, File fulltext_file,
			boolean exact_match, boolean ignore_other_args, boolean account_for_inter_sentence_edges, 
			int iter, RecipeSentenceSegmenter chunker, GraphScorer gs) {

		Results results = new Results();
		ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromFile(arg_file, fulltext_file, false, false);

		GraphInfo gi = new GraphInfo(ad, true, true, gs.str_classifier, gs.selectional_pref_model);
		System.out.println(gi.visible_arg_to_type_);

		gs.changeGraph(gi);
		gi.update(gs);
		LinearGraphInfoInitializer.initialize(gi, gs, true, true);
		gs.initNodeScores();

		LocalSearcher searcher = new LocalSearcher(gi, gs);
		searcher.initialize();
		searcher.run();
		gi.commitConnections();
		ad = gi.actionDiagram();

		// ad.printDotFile("AllRecipes_20_Args/TestSet/TestSet-searchgraph/" + arg_file.getName() + ".lin.gv");

		ActionDiagram gold = BratAnnotationReader.generativeActionDiagramFromBratAnnotations(ann_file, step_file, fulltext_file, true, true);
		//	gold.printDotFile("AllRecipes_20_Args/AnnotationSession/AnnotationSession-searchgraph/" + arg_file.getName() + ".gold.gv");
		ad.printDotFile("AllRecipes_20_Args/AnnotationSession/AnnotationSession-searchgraph/" + arg_file.getName() + ".4hardem99_cam_10000_filter.auto.gv");

		evaluateIntraSentenceEdges(ad, gold, results, exact_match, ignore_other_args, account_for_inter_sentence_edges);
		return results;
	}

	public void evaluateMixedSet(String eval_directory, int num, boolean use_oracle_parse) {
		String[] dirs = new String[]{
				"BananaMuffins",
				"BeefChilli", 
				"BeefMeatLoaf", 
				"BeefStroganoff",
				"CarrotCake",
				"CheeseBurger",
				"ChickenSalad",
				"ChickenStirFry", "Coleslaw", 
				"CornChowder", 
				"DeviledEggs", 
				"EggNoodles", 
				"FrenchToast",
				"MacAndCheese", 
				"MeatLasagna", 
				"PecanPie",
				"PotatoSalad", 
				"PulledPork", 
				"PumpkinPie", 
				"VeggiePizza"
		};

		List<Double> precision = new ArrayList<Double>();
		List<Double> recall = new ArrayList<Double>();
		List<Double> f1 = new ArrayList<Double>();

		int total_iters = 0;
		for (int a = 0; a <= total_iters; a++) {
			Map<String, Results> dir_to_results = new TreeMap<String, Results>();
			Results overall_results = new Results();
			int total_num_files = 0;
			double avg_precision = 0.0;
			double avg_recall = 0.0;

			RecipeSentenceSegmenter chunker = null;
			SelectionalPreferenceModel selectional_preference_model = null;
			RevSelectionalPreferenceModel rev_selectional_preference_model = null;
			IBM1MixturesModel mixtures_model = null;
			GraphScorer scorer = null;
			ThreeWayStringClassifier str_classifier = null;
			ConnectionsModel conn_model = null;
			try {
				if (a == 0) {
					conn_model = new ConnectionsModel();
					str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(model_directory + "init_string_classifier_stem_lastloc_nouns.model");
					selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(model_directory + "init_selectional_pref.model");
					rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(model_directory + "init_rev_selectional_pref_leaf2.model");

				} else {

					conn_model = ConnectionsModel.readFromFile(model_directory + "conn_model_" + num + "hardem99_cam_10000_filter.model");
					str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(model_directory + "str_classifier_" + num + "hardem99_cam_10000_filter.model");
					//				str_classifier.verb_to_location_counts_ = other_str_classifier.verb_to_location_counts_;
					selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(model_directory + "pref_model_" + num + "hardem99_cam_10000_filter.model");
					rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(model_directory + "rev_pref_model_" + num + "hardem99_cam_10000_filter.model");
					mixtures_model = IBM1MixturesModel.readModelFromFile(model_directory + "mix_model_" + num + "hardem99_cam_10000_filter.model");
				}
				scorer = new GraphScorer(chunker, str_classifier, selectional_preference_model, 
						rev_selectional_preference_model, mixtures_model, conn_model);
				//				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
			}


			int num_files = 0;
			for (String dir : dirs) {
				category = dir;

				Results dir_results = new Results();

				String arg_directory_name = data_directory + dir + "/" + dir + ProjectParameters.CHUNKED_SUFFIX + "/";
				System.out.println(arg_directory_name);
				List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");

				System.out.println(dir);
				ProjectParameters.TOKEN_NUM = 0;

				String ann_directory_name = data_directory + eval_directory + "/" + eval_directory + "-splitann/" + dir + "/";
				System.out.println(ann_directory_name);
				File input_directory = new File(ann_directory_name);
				if (!input_directory.exists()) {
					continue;
				}
				List<File> ann_files = Utils.getInputFiles(ann_directory_name, "ann");


				String step_directory_name = data_directory + dir + "/" + dir + ProjectParameters.STEP_SUFFIX + "/";
				List<File> step_files = Utils.getInputFiles(step_directory_name, "txt");

				String fulltext_directory_name = data_directory + dir + "/" + dir + ProjectParameters.FULLTEXT_SUFFIX + "/";
				List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

				int j = 0;
				//			for (int s = 0; s < 2; s++) {
				for (int i = 0; i < ann_files.size(); i++) {
					File arg_file = null;
					File ann_file = null;
					File step_file = null;
					File fulltext_file = null;
					String recipe_name = null;
					while (true) {
						arg_file = arg_files.get(j);
						ann_file = ann_files.get(i);
						step_file = step_files.get(j);
						fulltext_file = fulltext_files.get(j);
						int suffix = arg_file.getName().lastIndexOf('.');
						recipe_name = arg_file.getName().substring(0, suffix);
						suffix = ann_file.getName().lastIndexOf('.');
						if (!recipe_name.equals(ann_file.getName().substring(0, suffix))) {
							j++;
						} else {
							break;
						}
					}
					System.out.println(recipe_name);

					System.out.println(arg_file.getAbsolutePath() + " " + arg_file.getName());
					Results file_results = null;
					if (use_oracle_parse) {
						file_results = oracleEvaluate(arg_file, ann_file, step_file, fulltext_file, exact_match, ignore_other_args, account_for_inter_sentence_edges, a, scorer);
					} else {
						file_results = evaluate(arg_file, ann_file, step_file, fulltext_file, exact_match, ignore_other_args, account_for_inter_sentence_edges, a, chunker, scorer);
					}
					System.out.println(dir + " RESULTS = " + file_results.precision() + " " + file_results.recall());
					System.out.println(file_results);
					overall_results.false_negative += file_results.false_negative;
					overall_results.false_positive += file_results.false_positive;
					overall_results.true_negative += file_results.true_negative;
					overall_results.true_positive += file_results.true_positive;
					overall_results.correct += file_results.correct;
					overall_results.total += file_results.total;
					overall_results.tp_predicates += file_results.tp_predicates;
					overall_results.fp_predicates += file_results.fp_predicates;
					overall_results.fn_predicates += file_results.fn_predicates;
					dir_results.false_negative += file_results.false_negative;
					dir_results.false_positive += file_results.false_positive;
					dir_results.true_negative += file_results.true_negative;
					dir_results.true_positive += file_results.true_positive;
					dir_results.correct += file_results.correct;
					dir_results.total += file_results.total;
					avg_precision += file_results.precision();
					avg_recall += file_results.recall();
				}
				//			}
				dir_to_results.put(dir, dir_results);
				//							break;
			}
			avg_precision /= num_files;
			avg_recall /= num_files;

			System.out.println(num_files);
			System.out.println(overall_results);
			System.out.println("Overall precision: " + overall_results.precision());
			precision.add(overall_results.precision());
			System.out.println("Overall recall: " + overall_results.recall());
			recall.add(overall_results.recall());
			System.out.println("Overall F1: " + 2*((overall_results.precision() * overall_results.recall())/(overall_results.precision() + overall_results.recall())));
			f1.add((2*((overall_results.precision() * overall_results.recall())/(overall_results.precision() + overall_results.recall()))));
			System.out.println("Accuracy: " + overall_results.accuracy());
			System.out.println("Average precision: " + avg_precision);
			System.out.println("Average recall: " + avg_recall);
			System.out.println("Preds: " + overall_results.tp_predicates + " " + overall_results.fp_predicates + " " + overall_results.fn_predicates);
			System.out.println("Pred precision: " + overall_results.pred_precision());
			System.out.println("Pred recall: " + overall_results.pred_recall());
			for (String dir : dir_to_results.keySet()) {
				System.out.println(dir);
				Results results = dir_to_results.get(dir);
				System.out.println("	Precision: " + results.precision());
				System.out.println("	Recall: " + results.recall());
			}
			System.out.println("missing nodes: " + missing_nodes);
			System.out.println("extra nodes: " + extra_nodes);
			System.out.println("right nodes: " + right_nodes);
			System.out.println("lin: " + num_correct_lin_conns + " " + num_true_lin_conns);
			System.out.println("long: " + num_correct_long_conns + " " + num_true_long_conns);

		}
		System.out.println(precision);
		System.out.println(recall);
		System.out.println(f1);
		System.out.println(num_correct_pref_types + " " + num_verbs);

		System.out.println("missingseq " + missingseq);
		System.out.println("missinglong " + missinglong);
		System.out.println("incorrectseq " + incorrectseq);
		System.out.println("incorrectlong " + incorrectlong);
		System.out.println("incorrect " + incorrecteventparse);
		System.out.println("missing " + missingeventparse);
		System.out.println("other " + other);

	}

	public void evaluateTestSet(String eval_directory, int num) {
		String[] dirs = new String[]{
				"BananaMuffins",
				"BeefChilli", 
				"BeefMeatLoaf", 
				"BeefStroganoff",
				"CarrotCake",
				"CheeseBurger",
				"ChickenSalad",
				"ChickenStirFry", "Coleslaw", "CornChowder", "DeviledEggs", 
				"EggNoodles", 
				"FrenchToast",
				"MacAndCheese", "MeatLasagna", 
				"PecanPie",
				"PotatoSalad", 
				"PulledPork", 
				"PumpkinPie", 
				"VeggiePizza",
		};


		List<Double> precision = new ArrayList<Double>();
		List<Double> recall = new ArrayList<Double>();
		List<Double> f1 = new ArrayList<Double>();

		String ann_directory_name = data_directory + eval_directory + "/" + eval_directory + "-ann/";
		File input_directory = new File(ann_directory_name);
		List<File> ann_files = Utils.getInputFiles(ann_directory_name, "ann");

		Map<String, File> recipe_name_to_ann_file = new HashMap<String, File>();
		for (File ann_file : ann_files) {
			int suffix = ann_file.getName().lastIndexOf('.');
			String recipe_name = ann_file.getName().substring(0, suffix);
			recipe_name_to_ann_file.put(recipe_name, ann_file);
		}
		System.out.println(recipe_name_to_ann_file.size());

		int total_iters = 1;
		for (int a = 1; a <= total_iters; a++) {
			Map<String, Results> dir_to_results = new TreeMap<String, Results>();
			Results overall_results = new Results();
			int total_num_files = 0;
			double avg_precision = 0.0;
			double avg_recall = 0.0;


			RecipeSentenceSegmenter chunker = null;
			SelectionalPreferenceModel selectional_preference_model = null;
			RevSelectionalPreferenceModel rev_selectional_preference_model = null;
			IBM1MixturesModel mixtures_model = null;
			GraphScorer scorer = null;
			ThreeWayStringClassifier str_classifier = null;
			ConnectionsModel conn_model = null;
			try {
				
				conn_model = ConnectionsModel.readFromFile(model_directory + "conn_model_" + num + "hardem99_cam_10000_filter.model");
				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(model_directory + "str_classifier_" + num + "hardem99_cam_10000_filter.model");
				//				str_classifier.verb_to_location_counts_ = other_str_classifier.verb_to_location_counts_;
				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(model_directory + "pref_model_" + num + "hardem99_cam_10000_filter.model");
				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(model_directory + "rev_pref_model_" + num + "hardem99_cam_10000_filter.model");
				mixtures_model = IBM1MixturesModel.readModelFromFile(model_directory + "mix_model_" + num + "hardem99_cam_10000_filter.model");

				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_27.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_27.model");
				////				str_classifier.verb_to_location_counts_ = other_str_classifier.verb_to_location_counts_;
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_27.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_27.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_27.model");
				//////				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_cam_1000.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_cam_1000.model");
				////				str_classifier.verb_to_location_counts_ = other_str_classifier.verb_to_location_counts_;
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_cam_1000.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_cam_1000.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_cam_1000.model");

				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_27.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_27.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_27.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_27.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_27.model");
				////				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_3hardem_27.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_3hardem_27.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_3hardem_27.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_3hardem_27.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_3hardem_27.model");
				//				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_36.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_36.model");
				////				str_classifier.verb_to_location_counts_ = other_str_classifier.verb_to_location_counts_;
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_36.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_36.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_36.model");
				////////////////				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_36.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_36.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_36.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_36.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_36.model");
				//////////////				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_3hardem_36.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_3hardem_36.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_3hardem_36.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_3hardem_36.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_3hardem_36.model");
				////				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_36.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_36.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_36.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_36.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_36.model");
				//////				
				// submitted was 5 36
				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem_cam.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem_cam.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem_cam.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem_cam.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem_cam.model");
				//				
				//				conn_model = ConnectionsModel.readFromFile("conn_model_5hardem_cam.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_5hardem_cam.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_5hardem_cam.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_5hardem_cam.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_5hardem_cam.model");

				//				conn_model = ConnectionsModel.readFromFile("conn_model_4hardem99_cam_10000_filter.model");
				//				str_classifier = ThreeWayStringClassifier.readPhraseIdentifierFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "str_classifier_4hardem99_cam_10000_filter.model");
				//				selectional_preference_model = SelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "pref_model_4hardem99_cam_10000_filter.model");
				//				rev_selectional_preference_model = RevSelectionalPreferenceModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "rev_pref_model_4hardem99_cam_10000_filter.model");
				//				mixtures_model = IBM1MixturesModel.readModelFromFile(ProjectParameters.DEFAULT_CLUSTER_DIRECTORY + "mix_model_4hardem99_cam_10000_filter.model");

				scorer = new GraphScorer(chunker, str_classifier, selectional_preference_model, 
						rev_selectional_preference_model, mixtures_model, conn_model);
				//				scorer = new GraphScorer(chunker, str_classifier, selectional_preference_model, 
				//						rev_selectional_preference_model, null, conn_model);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
			}

			int num_sentences = 0;


			int num_files = 0;
			Set<String> seen = new HashSet<String>();
			for (String dir : dirs) {
				category = dir;

				Results dir_results = new Results();

				String arg_directory_name = data_directory + dir + "/" + dir + ProjectParameters.CHUNKED_SUFFIX + "/";
				System.out.println(arg_directory_name);
				List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");

				//				String model_name = data_directory + category + "/" + category + ".flow.ingaware.noem.model";
				//				System.out.println(model_name);
				//				File f = new File(model_name);
				//				if(!f.exists()) {
				//					continue;
				//				}
				System.out.println(dir);
				ProjectParameters.TOKEN_NUM = 0;

				//			IngredientAwareLinearFeatureFunction.addArgIngFeatureExtractor(IngredientAwareArgIngFeatureExtractors.ARGTYPE);
				//			IngredientAwareLinearFeatureFunction.addArgIngFeatureExtractor(IngredientAwareArgIngFeatureExtractors.TOKEN);
				//			IngredientAwareLinearFeatureFunction.addArgIngFeatureExtractor(IngredientAwareArgIngFeatureExtractors.VERBANDARGTYPE);
				//			IngredientAwareLinearFeatureFunction.addArgIngFeatureExtractor(IngredientAwareArgIngFeatureExtractors.VERB_HAS_INGREDIENT_ARGS);

				//			IngredientAwareLinearFeatureFunction.addIngConnectionFeatureExtractor(IngredientAwareIngConnectionFeatureExtractors.ARGTYPECONNTYPE);
				//
				//			IngredientAwareLinearFeatureFunction.addOriginFeatureExtractor(IngredientAwareOriginFeatureExtractors.TOKEN);
				//			IngredientAwareLinearFeatureFunction.addOriginFeatureExtractor(IngredientAwareOriginFeatureExtractors.INGREDIENT_MATCH);
				//			IngredientAwareLinearFeatureFunction.addOriginFeatureExtractor(IngredientAwareOriginFeatureExtractors.NONINGREDIENT_MATCH);
				//			IngredientAwareLinearFeatureFunction.addOriginFeatureExtractor(IngredientAwareOriginFeatureExtractors.ORIGIN_OUTPUT);
				//			IngredientAwareLinearFeatureFunction.addOriginFeatureExtractor(IngredientAwareOriginFeatureExtractors.PREV_PRED);
				//			IngredientAwareLinearFeatureFunction.addOriginFeatureExtractor(IngredientAwareOriginFeatureExtractors.VERB_PAIR);



				//			IngredientAwarePerceptronLearner learner = new IngredientAwarePerceptronLearner(model_name);



				String step_directory_name = data_directory + dir + "/" + dir + ProjectParameters.STEP_SUFFIX + "/";
				List<File> step_files = Utils.getInputFiles(step_directory_name, "txt");

				String fulltext_directory_name = data_directory + dir + "/" + dir + ProjectParameters.FULLTEXT_SUFFIX + "/";
				List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");


				for (int i = 0; i < fulltext_files.size(); i++) {
					File fulltext_file = fulltext_files.get(i);
					int suffix = fulltext_file.getName().lastIndexOf('.');
					String recipe_name = fulltext_file.getName().substring(0, suffix);
					File ann_file = recipe_name_to_ann_file.get(recipe_name);
					if (ann_file != null) {
						File step_file = step_files.get(i);
						File arg_file = arg_files.get(i);
						System.out.println(recipe_name);
						if (seen.contains(recipe_name)) {
							continue;
						}
						seen.add(recipe_name);
						System.out.println(arg_file.getAbsolutePath() + " " + arg_file.getName());
						//						if (!recipe_name.startsWith("salmon-and-swiss")) {
						//							continue;
						//						}

						Results file_results = oracleEvaluate(arg_file, ann_file, step_file, fulltext_file, exact_match, ignore_other_args, account_for_inter_sentence_edges, a, scorer);

						num_files++;
						//						}
						System.out.println(dir + " RESULTS = " + file_results.precision() + " " + file_results.recall());
						System.out.println(file_results);
						//								System.exit(1);
						overall_results.false_negative += file_results.false_negative;
						overall_results.false_positive += file_results.false_positive;
						overall_results.true_negative += file_results.true_negative;
						overall_results.true_positive += file_results.true_positive;
						overall_results.correct += file_results.correct;
						overall_results.total += file_results.total;
						overall_results.tp_predicates += file_results.tp_predicates;
						overall_results.fp_predicates += file_results.fp_predicates;
						overall_results.fn_predicates += file_results.fn_predicates;
						dir_results.false_negative += file_results.false_negative;
						dir_results.false_positive += file_results.false_positive;
						dir_results.true_negative += file_results.true_negative;
						dir_results.true_positive += file_results.true_positive;
						dir_results.correct += file_results.correct;
						dir_results.total += file_results.total;
						avg_precision += file_results.precision();
						avg_recall += file_results.recall();
					}

				}
				dir_to_results.put(dir, dir_results);
			}
			avg_precision /= num_files;
			avg_recall /= num_files;

			System.out.println(num_files);
			System.out.println(overall_results);
			System.out.println("Overall precision: " + overall_results.precision());
			precision.add(overall_results.precision());
			System.out.println("Overall recall: " + overall_results.recall());
			recall.add(overall_results.recall());
			System.out.println("Overall F1: " + 2*((overall_results.precision() * overall_results.recall())/(overall_results.precision() + overall_results.recall())));
			f1.add((2*((overall_results.precision() * overall_results.recall())/(overall_results.precision() + overall_results.recall()))));
			System.out.println("Accuracy: " + overall_results.accuracy());
			System.out.println("Average precision: " + avg_precision);
			System.out.println("Average recall: " + avg_recall);
			System.out.println("Preds: " + overall_results.tp_predicates + " " + overall_results.fp_predicates + " " + overall_results.fn_predicates);
			System.out.println("Pred precision: " + overall_results.pred_precision());
			System.out.println("Pred recall: " + overall_results.pred_recall());
			for (String dir : dir_to_results.keySet()) {
				System.out.println(dir);
				Results results = dir_to_results.get(dir);
				System.out.println("	Precision: " + results.precision());
				System.out.println("	Recall: " + results.recall());
			}
			System.out.println("missing nodes: " + missing_nodes);
			System.out.println("extra nodes: " + extra_nodes);
			System.out.println("right nodes: " + right_nodes);
			System.out.println("lin: " + num_correct_lin_conns + " " + num_true_lin_conns);
			System.out.println("long: " + num_correct_long_conns + " " + num_true_long_conns);
			num_correct_lin_conns = 0;
			num_correct_long_conns = 0;
			num_true_lin_conns = 0;
			num_true_long_conns = 0;
			System.out.println("num imp args: " + num_implicit_args);
			System.out.println("num total args: " + num_total_args);

			System.out.println("num files: " + num_files);
			System.out.println("num sentences: " + num_sentences);
		}
		System.out.println(precision);
		System.out.println(recall);
		System.out.println(f1);
	}

	public static void main(String args[]) {
		GoldStandardEvaluator evaluator = null;

		evaluator = new GoldStandardEvaluator();

		if (args[0] == "dev") {
			evaluator.evaluateMixedSet("AnnotationSession", 4, false);
		} else {
			evaluator.evaluateTestSet("TestSet", Integer.parseInt(args[1]));
		}
	}
}
