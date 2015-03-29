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
