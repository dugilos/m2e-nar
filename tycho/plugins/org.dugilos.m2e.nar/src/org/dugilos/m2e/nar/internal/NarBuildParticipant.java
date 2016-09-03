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

import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;

public class NarBuildParticipant extends MojoExecutionBuildParticipant {

	public NarBuildParticipant(MojoExecution execution) {
		super(execution, true);
	}

	@Override
	public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {

		// some treatments before goal execution
		
		Set<IProject> result = super.build( kind, monitor );
		
		// some treatments after goal execution
		
		return result;
	}
}
