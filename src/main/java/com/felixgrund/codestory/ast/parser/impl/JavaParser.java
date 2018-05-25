package com.felixgrund.codestory.ast.parser.impl;import com.felixgrund.codestory.ast.changes.*;import com.felixgrund.codestory.ast.entities.Ycommit;import com.felixgrund.codestory.ast.parser.Yfunction;import com.felixgrund.codestory.ast.entities.Yparameter;import com.felixgrund.codestory.ast.exceptions.ParseException;import com.felixgrund.codestory.ast.parser.AbstractParser;import com.felixgrund.codestory.ast.parser.Yparser;import com.github.javaparser.ast.CompilationUnit;import com.github.javaparser.ast.body.MethodDeclaration;import com.github.javaparser.ast.body.TypeDeclaration;import com.github.javaparser.ast.visitor.VoidVisitorAdapter;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import java.util.ArrayList;import java.util.List;public class JavaParser extends AbstractParser implements Yparser {	private Logger log = LoggerFactory.getLogger(JavaParser.class);	private CompilationUnit rootCompilationUnit;	public JavaParser(String repoName, String fileName, String fileContent, String commitName) {		super(repoName, fileName, fileContent, commitName);	}	@Override	public Yfunction findFunctionByNameAndLine(String name, int line) {		Yfunction ret = null;		MethodDeclaration method = findMethod(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				String methodName = method.getNameAsString();				int methodLineNumber = getMethodStartLine(method); // TODO get() ?				return name.equals(methodName) && line == methodLineNumber;			}		});		if (method != null) {			ret = new JavaFunction(method, this.commitName, this.fileContent);		}		return ret;	}	@Override	public List<Yfunction> findFunctionsByLineRange(int beginLine, int endLine) {		List<Yfunction> functions = new ArrayList<>();		List<MethodDeclaration> matchedMethods = findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				int lineNumber = getMethodStartLine(method);				return lineNumber >= beginLine && lineNumber <= endLine;			}		});		for (MethodDeclaration method : matchedMethods) {			functions.add(new JavaFunction(method, this.commitName, this.fileContent));		}		return transformMethods(matchedMethods);	}	@Override	public List<Yfunction> getAllFunctions() {		List<MethodDeclaration> matchedMethods = findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				return !method.isAbstract();			}		});		return transformMethods(matchedMethods);	}	@Override	public Yfunction findFunctionByOtherFunction(Yfunction otherMethod) {		Yfunction function = null;		String methodNameOther = otherMethod.getName();		List<Yparameter> parametersOther = otherMethod.getParameters();		List<MethodDeclaration> matchedMethods = findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				Yfunction yfunction = new JavaFunction(method, commitName, fileContent);				String methodNameThis = yfunction.getName();				List<Yparameter> parametersThis = yfunction.getParameters();				boolean methodNameMatches = methodNameOther.equals(methodNameThis);				boolean parametersMatch = parametersOther.equals(parametersThis);				return methodNameMatches && parametersMatch;			}		});		int numMatches = matchedMethods.size();		if (numMatches == 1) {			function = new JavaFunction(matchedMethods.get(0), this.commitName, this.fileContent);		} else if (numMatches > 1) {			log.info("Found more than one matching function. Trying to find correct candidate.");			function = getCandidateWithSameParent(matchedMethods, otherMethod);		}		return function;	}	private Yfunction getCandidateWithSameParent(List<MethodDeclaration> candidates, Yfunction compareMethod) {		for (MethodDeclaration candidateMethod : candidates) {			if (parentNameEquals(candidateMethod, compareMethod)) {				String parentName = ((TypeDeclaration) candidateMethod.getParentNode().get()).getNameAsString();				log.info("Found correct candidate. Parent name: {}", parentName);				return new JavaFunction(candidateMethod, this.commitName, this.fileContent);			}		}		return null;	}	private boolean parentNameEquals(MethodDeclaration method, Yfunction compareMethod) {		MethodDeclaration compareMethodRaw = (MethodDeclaration) compareMethod.getRawFunction();		TypeDeclaration compareMethodParent = (TypeDeclaration) compareMethodRaw.getParentNode().get();		String parentName = compareMethodParent.getNameAsString();		if (method.getParentNode().isPresent()) {			Object candidateMethodParent = method.getParentNode().get();			if (candidateMethodParent instanceof TypeDeclaration) {				String name = ((TypeDeclaration) candidateMethodParent).getNameAsString();				if (name.equals(parentName)) {					return true;				}			}		}		return false;	}	@Override	public Object parse() throws ParseException {		this.rootCompilationUnit = com.github.javaparser.JavaParser.parse(this.fileContent);		if (this.rootCompilationUnit == null) {			throw new ParseException("Could not parse root compilation unit", this.filePath, this.fileContent);		}		return this.rootCompilationUnit;	}	@Override	public boolean functionNamesConsideredEqual(String aName, String bName) {		return aName != null && aName.equals(bName);	}	@Override	public double getScopeSimilarity(Yfunction function, Yfunction compareFunction) {		MethodDeclaration methodRaw = (MethodDeclaration) function.getRawFunction();		return parentNameEquals(methodRaw, compareFunction) ? 1 : 0;	}	@Override	public List<Ysignaturechange> getMajorChanges(Ycommit commit, Yfunction compareFunction) {		List<Ysignaturechange> changes = new ArrayList<>();		Yparameterchange yparameterchange = getParametersChange(commit, compareFunction);		Yinfilerename yinfilerename = getFunctionRename(commit, compareFunction);		if (yinfilerename != null) {			changes.add(yinfilerename);		}		if (yparameterchange != null) {			changes.add(yparameterchange);		}		return changes;	}	@Override	public List<Ychange> getMinorChanges(Ycommit commit, Yfunction compareFunction) {		List<Ychange> changes = new ArrayList<>();		Yreturntypechange yreturntypechange = getReturnTypeChange(commit, compareFunction);		Ymodifierchange ymodifierchange = getModifiersChange(commit, compareFunction);		Yexceptionschange yexceptionschange = getExceptionsChange(commit, compareFunction);		Ybodychange ybodychange = getBodyChange(commit, compareFunction);		if (yreturntypechange != null) {			changes.add(yreturntypechange);		}		if (ymodifierchange != null) {			changes.add(ymodifierchange);		}		if (yexceptionschange != null) {			changes.add(yexceptionschange);		}		if (ybodychange != null) {			changes.add(ybodychange);		}		return changes;	}	@Override	public List<Ychange> getCrossFileChanges(Ycommit ycommit) {		return null;	}	private List<Yfunction> transformMethods(List<MethodDeclaration> methods) {		List<Yfunction> functions = new ArrayList<>();		for (MethodDeclaration method : methods) {			functions.add(new JavaFunction(method, this.commitName, this.fileContent));		}		return functions;	}	private MethodDeclaration findMethod(MethodVisitor visitor) {		MethodDeclaration ret = null;		List<MethodDeclaration> matchedNodes = findAllMethods(visitor);		if (matchedNodes.size() > 0) {			ret = matchedNodes.get(0);		}		return ret;	}	private List<MethodDeclaration> findAllMethods(MethodVisitor visitor) {		this.rootCompilationUnit.accept(visitor, null);		return visitor.getMatchedNodes();	}	public static int getMethodStartLine(MethodDeclaration method) {		return method.getName().getBegin().get().line;	}	public static abstract class MethodVisitor extends VoidVisitorAdapter<Void> {		private List<MethodDeclaration> matchedNodes = new ArrayList<>();		public abstract boolean methodMatches(MethodDeclaration method);		@Override		public void visit(MethodDeclaration method, Void arg) {			super.visit(method, arg);			if (methodMatches(method)) {				matchedNodes.add(method);			}		}		public List<MethodDeclaration> getMatchedNodes() {			return matchedNodes;		}	}}