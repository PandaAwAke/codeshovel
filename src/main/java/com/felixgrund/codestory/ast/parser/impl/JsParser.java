package com.felixgrund.codestory.ast.parser.impl;import com.felixgrund.codestory.ast.parser.Yfunction;import com.felixgrund.codestory.ast.entities.Yparameter;import com.felixgrund.codestory.ast.exceptions.ParseException;import com.felixgrund.codestory.ast.parser.AbstractParser;import com.felixgrund.codestory.ast.parser.Yparser;import com.felixgrund.codestory.ast.util.FunctionSimilarity;import com.felixgrund.codestory.ast.util.Utl;import jdk.nashorn.internal.ir.FunctionNode;import jdk.nashorn.internal.ir.visitor.SimpleNodeVisitor;import jdk.nashorn.internal.parser.Parser;import jdk.nashorn.internal.runtime.Context;import jdk.nashorn.internal.runtime.ErrorManager;import jdk.nashorn.internal.runtime.Source;import jdk.nashorn.internal.runtime.options.Options;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import java.util.*;import java.util.function.Function;import java.util.regex.Pattern;public class JsParser extends AbstractParser implements Yparser {	private Logger log = LoggerFactory.getLogger(JsParser.class);	private Options parserOptions;	private FunctionNode rootFunctionNode;	public JsParser(String fileName, String fileContent) {		super(fileName, fileContent);		this.parserOptions = new Options("nashorn");		this.parserOptions.set("anon.functions", true);		this.parserOptions.set("parse.only", true);		this.parserOptions.set("scripting", true);		this.parserOptions.set("language", "es6");	}	@Override	public Object parse() throws ParseException {		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();		ErrorManager errorManager = new ErrorManager();		Context context = new Context(this.parserOptions, errorManager, classLoader);		Source source = Source.sourceFor(this.fileName, this.fileContent);		Parser originalParser = new Parser(context.getEnv(), source, errorManager);		this.rootFunctionNode = originalParser.parse();		if (this.rootFunctionNode == null) {			throw new ParseException("Could not parse root function node", this.fileName, this.fileContent);		}		return this.rootFunctionNode;	}	@Override	public boolean functionNamesConsideredEqual(String aName, String bName) {		return aName != null && bName != null &&				(aName.equals(bName) || (aName.startsWith("L:") && bName.startsWith("L:")));	}	@Override	public Yfunction findFunctionByNameAndLine(String name, int line) {		Yfunction ret = null;		FunctionNode node = findFunction(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				return functionNode.getLineNumber() == line && functionNode.getIdent().getName().equals(name);			}		});		if (node != null) {			ret = new JsFunction(node);		}		return ret;	}	@Override	public List<Yfunction> findFunctionsByLineRange(int beginLine, int endLine) {		List<FunctionNode> matchedFunctions = findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				int lineNumber = functionNode.getLineNumber();				return lineNumber >= beginLine && lineNumber <= endLine;			}		});		return transformNodes(matchedFunctions);	}	@Override	public List<Yfunction> getAllFunctions() {		List<FunctionNode> nodes = findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				return !functionNode.isProgram();			}		});		return transformNodes(nodes);	}	@Override	public Yfunction findFunctionByOtherFunction(Yfunction otherFunction) {		Yfunction function = null;		FunctionNode otherFunctionNode = (FunctionNode) otherFunction.getRawFunction();		List<Yfunction> candidatesByFunctionPath = findFunctionsByFunctionPath(otherFunctionNode.getName());		if (candidatesByFunctionPath.size() == 1) {			function = candidatesByFunctionPath.get(0);		} else {			List<Yfunction> candidatesByNameAndParams = findFunctionsByNameAndParams(otherFunction);			if (candidatesByNameAndParams.size() == 1) {				function = candidatesByNameAndParams.get(0);			} else if (candidatesByNameAndParams.size() > 1) {				log.warn("Found more than one matches for name and parameters. Finding candidate with highest body similarity");				function = getMostSimilarFunction(candidatesByNameAndParams, otherFunction);			}		}		return function;	}	private List<String> getNamedScopeParts(Yfunction compareFunction) {		List<String> scopeParts = new ArrayList<>();		FunctionNode functionNode = (FunctionNode) compareFunction.getRawFunction();		String functionPath = functionNode.getName();		if (functionPath.contains("#")) {			String[] scopeSplit = functionPath.split("#");			for (String scopeName : scopeSplit) {				if (!scopeName.startsWith("L:")) {					scopeParts.add(scopeName);				}			}		}		return scopeParts;	}	private int getLineNumberDistance(Yfunction aFunction, Yfunction bFunction) {		return Math.abs(aFunction.getNameLineNumber() - bFunction.getNameLineNumber());	}	private double getLineNumberSimilarity(Yfunction aFunction, Yfunction bFunction) {		String aFileSource = ((FunctionNode) aFunction.getRawFunction()).getSource().getString();		int aNumLines = Utl.countLineNumbers(aFileSource);		String bFileSource = ((FunctionNode) bFunction.getRawFunction()).getSource().getString();		int bNumLines = Utl.countLineNumbers(bFileSource);		double maxLines = Math.max(aNumLines, bNumLines);		double lineNumberDistance = getLineNumberDistance(aFunction, bFunction);		double similarity = (maxLines - lineNumberDistance) / maxLines;		return similarity;	}	private double getScopeSimilarity(List<String> functionScopeParts, List<String> compareFunctionScopeParts) {		double scopeSimilarity;		boolean hasCompareFunctionScope = !compareFunctionScopeParts.isEmpty();		boolean hasThisFunctionScope = !functionScopeParts.isEmpty();		if (!hasCompareFunctionScope && !hasThisFunctionScope) {			scopeSimilarity = 1;		} else if (hasCompareFunctionScope && hasThisFunctionScope) {			int matchedScopeParts = 0;			for (String part : compareFunctionScopeParts) {				if (functionScopeParts.contains(part)) {					matchedScopeParts += 1;				}			}			scopeSimilarity = matchedScopeParts / (double) compareFunctionScopeParts.size();		} else {			scopeSimilarity = 0;		}		return scopeSimilarity;	}	private Yfunction getMostSimilarFunction(List<Yfunction> candidates, Yfunction compareFunction) {		log.info("Trying to find most similar function");		Map<Yfunction, FunctionSimilarity> similarities = new HashMap<>();		List<String> compareFunctionScopeParts = getNamedScopeParts(compareFunction);		for (Yfunction candidate : candidates) {			double bodySimilarity = Utl.getBodySimilarity(compareFunction, candidate);			if (bodySimilarity == 1) {				log.info("Found function with body similarity of 1. Done.");				return candidate;			}			int lineNumberDistance = getLineNumberDistance(candidate, compareFunction);			List<String> thisFunctionScopeParts = getNamedScopeParts(candidate);			double scopeSimilarity = getScopeSimilarity(thisFunctionScopeParts, compareFunctionScopeParts);			if (bodySimilarity > 0.9 && lineNumberDistance < 10 && scopeSimilarity == 1) {				log.info("Found function with body similarity > 0.9 and line distance < 10 and scope similarity of 1s. Done.");				return candidate;			}			FunctionSimilarity similarity = new FunctionSimilarity();			similarity.setBodySimilarity(bodySimilarity);			similarity.setScopeSimilarity(scopeSimilarity);			similarities.put(candidate, similarity);		}		Yfunction highestOverallSimilarityFunction = null;		double highestOverallSimilarity = 0;		for (Yfunction candidate : similarities.keySet()) {			FunctionSimilarity similarity = similarities.get(candidate);			similarity.setLineNumberSimilarity(getLineNumberSimilarity(candidate, compareFunction));			double overallSimilarity = similarity.getOverallSimilarity();			if (highestOverallSimilarityFunction == null || overallSimilarity > highestOverallSimilarity) {				highestOverallSimilarity = overallSimilarity;				highestOverallSimilarityFunction = candidate;			}		}		FunctionSimilarity similarity = similarities.get(highestOverallSimilarityFunction);		log.info("Highest similarity with overall similarity of {}: {}", similarity);		if (highestOverallSimilarity > 0.9) {			log.info("Highest similarity is > 0.9. Accepting function.");			return highestOverallSimilarityFunction;		} else {			log.info("Highest similarity is < 0.9. Rejecting function.");			return null;		}	}	private List<Yfunction> findFunctionsByNameAndParams(Yfunction otherFunction) {		return transformNodes(findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				Yfunction currentFunction = new JsFunction(functionNode);				List<Yparameter> parametersCurrent = currentFunction.getParameters();				String functionNameCurrent = currentFunction.getName();				boolean nameMatches = functionNamesConsideredEqual(functionNameCurrent, otherFunction.getName());				boolean paramsMatch = parametersCurrent.equals(otherFunction.getParameters());				return nameMatches && paramsMatch;			}		}));	}	private List<Yfunction> findFunctionsByFunctionPath(String functionPath) {		return transformNodes(findAllFunctions(new FunctionNodeVisitor() {			@Override			public boolean nodeMatches(FunctionNode functionNode) {				return functionNode.getName().equals(functionPath);			}		}));	}	private List<Yfunction> transformNodes(List<FunctionNode> nodes) {		List<Yfunction> functions = new ArrayList<>();		for (FunctionNode node : nodes) {			functions.add(new JsFunction(node));		}		return functions;	}	private FunctionNode findFunction(FunctionNodeVisitor visitor) {		FunctionNode ret = null;		visitor.setOnlyFirstMatch(true);		this.rootFunctionNode.accept(visitor);		List<FunctionNode> matchedNodes = visitor.getMatchedNodes();		if (matchedNodes.size() > 0) {			ret = matchedNodes.get(0);		}		return ret;	}	private List<FunctionNode> findAllFunctions(FunctionNodeVisitor visitor) {		this.rootFunctionNode.accept(visitor);		return visitor.getMatchedNodes();	}	public static abstract class FunctionNodeVisitor extends SimpleNodeVisitor {		private List<FunctionNode> matchedNodes = new ArrayList<>();		private boolean onlyFirstMatch;		public abstract boolean nodeMatches(FunctionNode functionNode);		public FunctionNodeVisitor() {		}		@Override		public boolean enterFunctionNode(FunctionNode functionNode) {			if (nodeMatches(functionNode)) {				matchedNodes.add(functionNode);				if (this.onlyFirstMatch) {					return false;				}			}			return true;		}		public void setOnlyFirstMatch(boolean onlyFirstMatch) {			this.onlyFirstMatch = onlyFirstMatch;		}		public List<FunctionNode> getMatchedNodes() {			return matchedNodes;		}	}}