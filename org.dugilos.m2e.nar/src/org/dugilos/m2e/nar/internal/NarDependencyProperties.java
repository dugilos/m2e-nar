package org.dugilos.m2e.nar.internal;

import java.util.ArrayList;
import java.util.List;

public class NarDependencyProperties {

	private String libraryName;
	private List<String> absoluteIncludePaths;
	private List<String> absoluteLibraryPaths;

	public NarDependencyProperties(String libraryName) {
		this.libraryName = libraryName;
		this.absoluteIncludePaths = new ArrayList<String>();
		this.absoluteLibraryPaths = new ArrayList<String>();
	}

	public String getLibraryName() {
		return libraryName;
	}

	public void setLibraryName(String libraryName) {
		this.libraryName = libraryName;
	}

	public List<String> getAbsoluteIncludePaths() {
		return absoluteIncludePaths;
	}

	public List<String> getAbsoluteLibraryPaths() {
		return absoluteLibraryPaths;
	}

}
