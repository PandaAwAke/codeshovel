package com.felixgrund.codestory.ast.json;

import com.felixgrund.codestory.ast.wrappers.FunctionSimilarity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonSimilarity {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private FunctionEntry function;
	private FunctionEntry mostSimilarFunction;
	private FunctionSimilarity similarity;

	public JsonSimilarity(FunctionEntry function, FunctionEntry mostSimilarFunction, FunctionSimilarity similarity) {
		this.function = function;
		this.mostSimilarFunction = mostSimilarFunction;
		this.similarity = similarity;
	}

	public FunctionSimilarity getSimilarity() {
		return similarity;
	}

	public FunctionEntry getFunction() {
		return function;
	}

	public FunctionEntry getMostSimilarFunction() {
		return mostSimilarFunction;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public String getCommitName() {
		return this.function.getCommitName();
	}

	public static class FunctionEntry {
		private String commitName;
		private String name;
		private String path;
		private String body;

		public FunctionEntry(String commitName, String name, String path, String body) {
			this.commitName = commitName;
			this.name = name;
			this.path = path;
			this.body = body;
		}

		public String getCommitName() {
			return commitName;
		}

		public String getName() {
			return name;
		}

		public String getPath() {
			return path;
		}

		public String getBody() {
			return body;
		}

		@Override
		public String toString() {
			String template = "COMMIT: %s\nNAME: %s\nPATH: %s\nBODY:\n%s\n";
			return String.format(template, this.commitName, this.name, this.path, this.body);
		}
	}

}