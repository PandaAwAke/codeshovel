package com.felixgrund.codestory.ast.tasks;

import com.felixgrund.codestory.ast.entities.CommitInfo;
import com.felixgrund.codestory.ast.entities.CommitInfoCollection;
import com.felixgrund.codestory.ast.parser.JsParser;
import com.felixgrund.codestory.ast.util.Utl;
import jdk.nashorn.internal.ir.FunctionNode;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class CreateCommitInfoCollectionTask {

	private Git git;
	private Repository repository;
	private String filePath;
	private String fileName;
	private String branchName;
	private String startCommitName;
	private String functionName;
	private int functionStartLine;


	private String startFileContent;
	private FunctionNode startFunctionNode;
	private RevCommit startCommit;

	private CommitInfo headCommitInfo;
	private CommitInfoCollection result;

	public CreateCommitInfoCollectionTask(Repository repository) {
		this.repository = repository;
		this.git = new Git(repository);
	}

	public void run() throws Exception {
		long start = new Date().getTime();
		this.buildAndValidate();
		String hash = this.createUuidHash();
		this.result = Utl.loadFromCache(hash);
		if (this.result == null) {
			System.out.println("NOT FOUND IN CACHE");
			this.createCommitCollection();
			Utl.saveToCache(hash, this.result);
		} else {
			System.out.println("FOUND IN CACHE");
		}

		long timeTakenSeconds = (new Date().getTime() - start) / 1000;
		System.out.println("MEASURE CreateCommitInfoCollectionTask in seconds: " + timeTakenSeconds);
	}

	private void buildAndValidate() throws Exception {
		Utl.checkNotNull("repository", this.repository);
		Utl.checkNotNull("startCommitName", this.startCommitName);
		Utl.checkNotNull("filePath", this.filePath);
		Utl.checkNotNull("fileName", this.fileName);
		Utl.checkNotNull("functionName", this.functionName);
		Utl.checkNotNull("functionStartLine", this.functionStartLine);

		this.startCommit = Utl.findCommitByName(this.repository, this.startCommitName);
		Utl.checkNotNull("startCommit", this.startCommit);

		this.startFileContent = Utl.findFileContent(this.repository, this.startCommit, this.filePath);
		Utl.checkNotNull("startFileContent", this.startFileContent);

		JsParser startParser = new JsParser(this.fileName, this.startFileContent);
		this.startFunctionNode = startParser.findFunctionByNameAndLine(this.functionName, this.functionStartLine);
		Utl.checkNotNull("startFunctionNode", this.startFunctionNode);
	}

	public void createCommitCollection() throws IOException, GitAPIException {
		Ref masterRef = repository.findRef(branchName);
		ObjectId masterId = masterRef.getObjectId();
		RevWalk walk = new RevWalk(repository);
		RevCommit headCommit = walk.parseCommit(masterId);
		walk.markStart(headCommit);
		Iterator<RevCommit> iterator = walk.iterator();
		iterator.next(); // skip head

		this.result = new CommitInfoCollection();
		this.headCommitInfo = this.createCommitInfoHead(headCommit);
		this.result.add(this.headCommitInfo);

		CommitInfo commitInfoAfter = this.headCommitInfo;
		while (iterator.hasNext()) {
			RevCommit currentCommit = iterator.next();
			CommitInfo currentCommitInfo = createCommitInfoNonHead(currentCommit);
			currentCommitInfo.setNext(commitInfoAfter);
			commitInfoAfter.setPrev(currentCommitInfo);
			commitInfoAfter.setDiff(createDiff(commitInfoAfter.getCommit(), currentCommit));
			this.result.add(currentCommitInfo);
			commitInfoAfter = currentCommitInfo;
		}
	}



	private CommitInfo createCommitInfoNonHead(RevCommit commit) throws IOException {
		CommitInfo commitInfo = createBaseCommitInfo(commit);
		if (commitInfo.isFileFound() && this.headCommitInfo.getMatchedFunctionNode() != null) {
			JsParser parser = new JsParser(commitInfo.getFileName(), commitInfo.getFileContent());
			String functionPath = this.headCommitInfo.getMatchedFunctionNode().getName();
			FunctionNode matchedNode = parser.findFunctionByFunctionPath(functionPath);
			commitInfo.setMatchedFunctionNode(matchedNode);
		}
		return commitInfo;
	}

	private CommitInfo createCommitInfoHead(RevCommit commit) throws IOException {
		CommitInfo commitInfo = createBaseCommitInfo(commit);
		if (commitInfo.isFileFound()) {
			JsParser parser = new JsParser(commitInfo.getFileName(), commitInfo.getFileContent());
			FunctionNode matchedNode = parser.findFunctionByNameAndLine(this.functionName, this.functionStartLine);
			commitInfo.setMatchedFunctionNode(matchedNode);
		}
		return commitInfo;
	}

	private CommitInfo createBaseCommitInfo(RevCommit commit) throws IOException {
		CommitInfo ret = new CommitInfo(commit);
		ret.setFileName(this.fileName);

		RevTree tree = commit.getTree();
		TreeWalk treeWalk = new TreeWalk(this.repository);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(this.filePath));

		if (treeWalk.next()) {
			ObjectId objectId = treeWalk.getObjectId(0);
			String fileContent = Utl.getFileContentByObjectId(repository, objectId);
			ret.setFileContent(fileContent);
		}

		return ret;

	}

	private List<DiffEntry> createDiff(RevCommit commit, RevCommit prevCommit) throws IOException, GitAPIException {
		ObjectReader objectReader = repository.newObjectReader();
		CanonicalTreeParser treeParserNew = new CanonicalTreeParser();
		treeParserNew.reset(objectReader, commit.getTree());
		CanonicalTreeParser treeParserOld = new CanonicalTreeParser();
		treeParserOld.reset(objectReader, prevCommit.getTree());
		return git.diff()
				.setOldTree(treeParserOld)
				.setNewTree(treeParserNew)
				.call();

	}

	public CommitInfoCollection getResult() {
		return result;
	}

	public void setStartCommitName(String startCommitName) {
		this.startCommitName = startCommitName;
	}


	public CommitInfo getHeadCommitInfo() {
		return headCommitInfo;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public void setFunctionStartLine(int functionStartLine) {
		this.functionStartLine = functionStartLine;
	}

	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String createUuidHash() {
		StringBuilder builder = new StringBuilder();
		builder.append(branchName)
				.append(filePath)
				.append(fileName)
				.append(functionName)
				.append(functionStartLine)
				.append(startCommitName);
		return DigestUtils.md5Hex(builder.toString());
	}

}
