package com.felixgrund.codestory.ast.interpreters;

import com.felixgrund.codestory.ast.changes.*;
import com.felixgrund.codestory.ast.entities.*;
import com.felixgrund.codestory.ast.parser.Yfunction;
import com.felixgrund.codestory.ast.parser.Yparser;
import com.felixgrund.codestory.ast.util.Utl;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;

import java.util.ArrayList;
import java.util.List;

public class Interpreter {

	private Ycommit ycommit;

	public Interpreter(Ycommit ycommit) {
		this.ycommit = ycommit;
	}

	public Ychange interpret() throws Exception {
		Ychange interpretation;

		Yfunction matchedFunction = this.ycommit.getMatchedFunction();
		Yparser parser = this.ycommit.getParser();
		List<Ychange> changes = new ArrayList<>();

		if (isFirstFunctionOccurrence()) {
			Yfunction compareFunction = null;
			if (matchedFunction != null) {
				compareFunction = this.getCompareFunction(this.ycommit);
			}
			if (compareFunction != null) {
				List<Ysignaturechange> majorChanges = parser.getMajorChanges(this.ycommit, compareFunction);
				changes.addAll(majorChanges);
			}

			if (changes.isEmpty()) {
//				List<Ychange> crossFileChanges = parser.getCrossFileChanges(this.ycommit);
//				if (!crossFileChanges.isEmpty()) {
//					changes.addAll(crossFileChanges);
//				} else {
					changes.add(new Yintroduced(ycommit));
//				}

			} else {
				Ysignaturechange firstMajorChange = (Ysignaturechange) changes.get(0);
				List<Ychange> minorChanges = parser.getMinorChanges(ycommit, firstMajorChange.getCompareFunction());
				changes.addAll(minorChanges);
			}
		} else {
			Yfunction parentMatchedFunction = ycommit.getParent().getMatchedFunction();
			if (ycommit.getParent() != null && parentMatchedFunction != null) {
				List<Ychange> minorChanges = parser.getMinorChanges(ycommit, parentMatchedFunction);
				changes.addAll(minorChanges);
			}
		}

		int numChanges = changes.size();
		if (numChanges > 1) {
			interpretation = new Ymultichange(ycommit, changes);
		} else if (numChanges == 1) {
			interpretation = changes.get(0);
		} else {
			interpretation = new Ynochange(ycommit);
		}

		return interpretation;
	}

	private Yfunction getCompareFunction(Ycommit ycommit) {
		Yfunction ret = null;
		Ycommit parentCommit = ycommit.getParent();
		if (parentCommit != null) {
			Ydiff ydiff = ycommit.getYdiff();
			if (ydiff != null) {
				Yfunction functionB = ycommit.getMatchedFunction();
				int lineNumberB = functionB.getNameLineNumber();
				EditList editList = ydiff.getEditList();
				for (Edit edit : editList) {
					int beginA = edit.getBeginA();
					int endA = edit.getEndA();
					int beginB = edit.getBeginB();
					int endB = edit.getEndB();
					if (beginB <= lineNumberB && endB >= lineNumberB) {
						Yparser parser = parentCommit.getParser();
						List<Yfunction> functionsInRange = parser.findFunctionsByLineRange(beginA, endA);
						if (functionsInRange.size() == 1) {
							ret = functionsInRange.get(0);
						} else if (functionsInRange.size() > 1) {
							ret = parser.getMostSimilarFunction(functionsInRange, functionB, true);
						}
					}
				}
			}
		}
		return ret;
	}


	private boolean isFirstFunctionOccurrence() {
		return this.ycommit.getParent() == null || this.ycommit.getParent().getMatchedFunction() == null;
	}


}
