package preprocessing;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import utils.Pair;
import utils.Triple;
import utils.Utils;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.BeginIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EndIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.TTags;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.ModCollinsHeadFinder;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreeGraphNode;
//import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

public class Lemmatizer {

	protected StanfordCoreNLP parser;
	protected LexicalizedParser lp = null; //LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishRNN.ser.gz");
	protected LexicalizedParser lp_pcfg = null;

	public Lemmatizer() {

		Properties parserprop;
		parserprop = new Properties();
		parserprop.put("annotators", "tokenize, ssplit, pos, parse");
		parserprop.put("pos.model", "edu/stanford/nlp/models/pos-tagger/english-bidirectional/english-bidirectional-distsim.tagger");
		parserprop.put("parser.model", "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
	
		lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
		lp_pcfg = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishRNN.ser.gz");
		

		// StanfordCoreNLP loads a lot of models, so you probably
		// only want to do this once per execution
		this.parser = new StanfordCoreNLP(parserprop);
		

	}
	
	public List<String> tokenize(String text) {
		List<String> tokens = new ArrayList<String>();
		// create an empty Annotation just with the given text
		Annotation document = new Annotation(text);

		// run all Annotators on this text
		parser.annotate(document);
			// Iterate over all tokens in a sentence
			for (CoreLabel token: document.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				tokens.add(token.word());
			}
		return tokens;
	}
	
	public List<String> stem(String documentText) {
		List<String> stems = new ArrayList<String>();
		
		return stems;
	}

	public List<List<String>> lemmatize(String documentText)
	{
		List<List<String>> lemmas = new ArrayList<List<String>>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);

		// run all Annotators on this text
		this.parser.annotate(document);

		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for(CoreMap sentence: sentences) {
			// Iterate over all tokens in a sentence
			List<String> sentence_lemmas = new ArrayList<String>();
			for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				sentence_lemmas.add(token.get(LemmaAnnotation.class));
			}
			lemmas.add(sentence_lemmas);
		}

		return lemmas;
	}

	
	public Pair<List<String>, List<String>> tagAndTokenize(String documentText)
	{
		List<String> tags = new ArrayList<String>();
		List<String> tokens = new ArrayList<String>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);

		// run all Annotators on this text
		this.parser.annotate(document);

		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for(CoreMap sentence: sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				tags.add(token.get(PartOfSpeechAnnotation.class));
				tokens.add(token.word());
			}
		}

		return new Pair<List<String>, List<String>>(tags, tokens);
	}
	
	public List<String> tag(String documentText)
	{
		List<String> tags = new ArrayList<String>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);

		// run all Annotators on this text
		this.parser.annotate(document);

		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for(CoreMap sentence: sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				tags.add(token.get(PartOfSpeechAnnotation.class));
			}
		}

		return tags;
	}
	
	
	public List<Pair<List<String>, Tree> > parse(String documentText)
	{
		List<Pair<List<String>, Tree> > trees = new ArrayList<Pair<List<String>, Tree>>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);

		// run all Annotators on this text
		Tree parse = this.lp.parse(documentText);
		
		List<Tree> leaves = parse.getLeaves();
		List<String> tokens = new ArrayList<String>();
		List<Label> pos = parse.preTerminalYield();
		for (int i = 0; i < leaves.size(); i++) {
			tokens.add(leaves.get(i).value());
		}
		trees.add(new Pair<List<String>, Tree>(tokens, parse));

		return trees;
	}
	
	public static void main(String args[]) {
		Lemmatizer lemmatizer = new Lemmatizer();
		System.out.println(lemmatizer.tag("cover rice"));
		System.out.println(Utils.canWordBeVerbInWordNet("cook"));
		System.out.println(Utils.canWordBeVerbInWordNet("rice"));
		System.out.println(lemmatizer.tagAndTokenize("cook rice in salted water until tender but still undercooked -lrb- 15 minutes for white rice , 30 minutes for brown -rrb- ."));
		
	}
}