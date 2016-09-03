/*
 * Copyright (c) 2015 Jean-François Blanc
 * 
 * This file is part of m2e nar plugin.
 * 
 * m2e nar plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * m2e nar plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with m2e nar plugin. If not, see <http://www.gnu.org/licenses/>.
 */
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
