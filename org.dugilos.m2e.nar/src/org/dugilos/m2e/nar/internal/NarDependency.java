package org.dugilos.m2e.nar.internal;

public class NarDependency {

	private String libraryName;
	private String absoluteIncludePath;
	private String absoluteLibraryPath;

	public NarDependency(String libraryName, String absoluteIncludePath, String absoluteLibraryPath) {
		this.libraryName = libraryName;
		this.absoluteIncludePath = absoluteIncludePath;
		this.absoluteLibraryPath = absoluteLibraryPath;
	}

	public String getAbsoluteIncludePath() {
		return absoluteIncludePath;
	}

	public void setAbsoluteIncludePath(String absoluteIncludePath) {
		this.absoluteIncludePath = absoluteIncludePath;
	}

	public String getAbsoluteLibraryPath() {
		return absoluteLibraryPath;
	}

	public void setAbsoluteLibraryPath(String absoluteLibraryPath) {
		this.absoluteLibraryPath = absoluteLibraryPath;
	}

	public String getLibraryName() {
		return libraryName;
	}

	public void setLibraryName(String libraryName) {
		this.libraryName = libraryName;
	}

}
