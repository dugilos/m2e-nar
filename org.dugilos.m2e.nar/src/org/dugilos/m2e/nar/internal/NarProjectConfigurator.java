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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.eclipse.cdt.core.CCProjectNature;
import org.eclipse.cdt.core.CProjectNature;
import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsProvider;
import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsProvidersKeeper;
import org.eclipse.cdt.core.language.settings.providers.LanguageSettingsManager;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CIncludePathEntry;
import org.eclipse.cdt.core.settings.model.CLibraryPathEntry;
import org.eclipse.cdt.core.settings.model.CMacroEntry;
import org.eclipse.cdt.core.settings.model.CSourceEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICFolderDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.core.settings.model.ICSourceEntry;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationData;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.IProjectType;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.Configuration;
import org.eclipse.cdt.newmake.core.IMakeBuilderInfo;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectChangedEvent;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;

import com.github.maven_nar.NarUtil;
import com.github.maven_nar.OS;

/**
 * 
 * http://cdt-devel-faq.wikidot.com/
 * 
 * @author jfblanc
 *
 */
public class NarProjectConfigurator extends AbstractProjectConfigurator {

	private static final String PLUGIN_ID = "org.dugilos.m2e.nar";
	
	private static final String CDT_GPP_LANGUAGE_ID = "org.eclipse.cdt.core.g++";
	private static final String CDT_GCC_LANGUAGE_ID = "org.eclipse.cdt.core.gcc";
	
	private static final String MAVEN_CONFIGURATION_NAME = "maven";
	
	@Override
	public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
			IPluginExecutionMetadata executionMetadata) {
		return new NarBuildParticipant(execution);
	}

	@Override
	public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
		
		Log log = new SystemStreamLog();
		
		IProject project = request.getProject();
		MavenProject mavenProject = request.getMavenProject();
		NarPluginConfiguration narPluginConfiguration = new NarPluginConfiguration(project, mavenProject, PLUGIN_ID, log);
		
		// Check if the CDT configuration for maven exists and create it if it doesn't exist
		checkAndCreateCdtProject(project, narPluginConfiguration, monitor);
		// Update the CDT configuration from the maven configuration
		setProjectSpecificConfiguration(project, mavenProject, narPluginConfiguration, monitor, log);
	}

	@Override
	public void mavenProjectChanged(MavenProjectChangedEvent event, IProgressMonitor monitor) throws CoreException {
		super.mavenProjectChanged(event, monitor);
		
		Log log = new SystemStreamLog();
		
		IProject project = event.getMavenProject().getProject();
		MavenProject mavenProject = event.getMavenProject().getMavenProject();
		NarPluginConfiguration narPluginConfiguration = new NarPluginConfiguration(project, mavenProject, PLUGIN_ID, log);
		
		// Get the CDT project description if it exists
		CoreModel cdtCoreModel = CoreModel.getDefault();
		ICConfigurationDescription mavenConfDesc = null;
		ICProjectDescription prjDesc = cdtCoreModel.getProjectDescription(project);
		if(prjDesc != null) {
			ICConfigurationDescription configDecriptions[] = prjDesc.getConfigurations();
			for (ICConfigurationDescription configDescription : configDecriptions) {
				if(MAVEN_CONFIGURATION_NAME.equals(configDescription.getName())) {
					mavenConfDesc = configDescription;
					break;
				}
			}
		}
		if(mavenConfDesc != null) {
			// Set the configuration as the active one in case it was not
			prjDesc.setActiveConfiguration(mavenConfDesc);
			// Update the CDT configuration from the maven configuration
			setProjectSpecificConfiguration(project, mavenProject, narPluginConfiguration, monitor, log);
		}
	}

	/**
	 * Add C and CC nature and create the cdt configuration if necessary.
	 * 
	 * @param project
	 * @param monitor
	 * @throws CoreException
	 */
	private void checkAndCreateCdtProject(IProject project, NarPluginConfiguration narPluginConfiguration, IProgressMonitor monitor) throws CoreException {
		
		// Add C and CC nature (if necessary)
		IProjectDescription desc = project.getDescription();
		String[] currentNatures = desc.getNatureIds();
		boolean cNature = false;
		boolean ccNature = false;
		if(Arrays.asList(currentNatures).contains(CProjectNature.C_NATURE_ID)) {
			cNature = true;
		}
		if(Arrays.asList(currentNatures).contains(CCProjectNature.CC_NATURE_ID)) {
			ccNature = true;
		}
		if(!cNature) {
			CProjectNature.addCNature(project, monitor);
		}
		if(!ccNature) {
			CCProjectNature.addCCNature(project, monitor);
		}
		
		CoreModel cdtCoreModel = CoreModel.getDefault();
		
		// Create (or get if it exists) the CDT project description
		ICProjectDescription prjDesc = cdtCoreModel.createProjectDescription(project, true, true);
		
		// we must create or check the maven configuration, configurations are derived from model configurations from CDT.
		// The model configurations are defined by :
		// * The toolchain (depending on the system) :
		//  - gnu (linux)
		//  - gnu.cross (??)
		//  - gnu.cygwin (Windows)
		//  - gnu.mingw (Windows)
		//  - gnu.solaris (Solaris)
		//  - macosx (Mac OS X)
		//  - msvc (Windows)
		// * The delivery type
		//  - executable
		//  - shared library
		//  - static library
		// * A "release" or a "debug" mode
		
		// * OS (get from NarUtil.getOS(String))
		//  - Windows
		//  - Linux
		//  - MacOSX
		//  - SunOS
		//  - FreeBSD
		String os = NarUtil.getOS(null);
		
		// * Linker name from the maven nar plugin configuration
		//  - msvc (for Windows)
		//  - icl (for Windows)
		//  - g++ (for Windows)
		//  - gcc (for Windows)
		// ------------------------
		//  - g++ (for Linux)
		//  - gcc (for linux)
		//  - icpc (for Linux)
		//  - icc (for Linux)
		// ------------------------
		//  - g++ (for Mac OS X)
		//  - gcc (for Mac OS X)
		//  - icpc (for Mac OS X)
		//  - icc (for Mac OS X)
		// ------------------------
		//  - CC (for Solaris / SunOS)
		//  - g++ (for Solaris / SunOS)
		//  - gcc (for Solaris / SunOS)
		// ------------------------
		//  - g++ (for FreeBSD)
		//  - gcc (for FreeBSD)
		// ------------------------
		//  - g++ (for AIX)
		//  - gcc (for AIX)
		//  - xlC (for AIX)
		String linkerName = narPluginConfiguration.getLinker().getName();
		
		// * Library type from the maven nar plugin configuration
		//  - executable
		//  - shared
		//  - static
		//  - jni
		//  - plugin
		String libraryType = narPluginConfiguration.getFirstLibraryType();
		
		// * Debug mode from the maven nar plugin configuration
		//  - true
		//  - false
		boolean debug = narPluginConfiguration.isDebug();

		// Choose the project type from maven nar configuration
		IProjectType cdtProjectType = chooseProjectType(os, linkerName, libraryType);
		
		// Choose the configuration to use :
		IConfiguration cdtConfiguration = chooseConfiguration(cdtProjectType, os, linkerName, debug);
		
		// Create teh id for the maven configuration
		String mavenConfId = ManagedBuildManager.calculateChildId(cdtConfiguration.getId(), MAVEN_CONFIGURATION_NAME);
		
		// get the "maven" configuration description if it exists, or create it
		ICConfigurationDescription mavenConfDesc = null;
		ICConfigurationDescription configDecriptions[] = prjDesc.getConfigurations();
		for (ICConfigurationDescription configDescription : configDecriptions) {
			if(mavenConfId.equals(configDescription.getId())) {
				mavenConfDesc = configDescription;
				break;
			}
		}
		if(mavenConfDesc == null) {
			// Create the build info (mandatory to createManagedProject)
			ManagedBuildManager.createBuildInfo(project);
			
			// create the ManagedProject associated to the project
			IManagedProject managedProject = null;
			try {
				managedProject = ManagedBuildManager.createManagedProject(project, cdtProjectType);
			} catch (BuildException e) {
				int severity = IStatus.ERROR;
				String pluginId = PLUGIN_ID;
				Status status = new Status(severity, pluginId, e.getMessage(), e);
				throw new CoreException(status);
			}
			
			// create the real configuration
			IConfiguration configuration = managedProject.createConfiguration(cdtConfiguration, mavenConfId);
			CConfigurationData configurationData = configuration.getConfigurationData();
			ICConfigurationDescription configurationDesc = prjDesc.createConfiguration(ManagedBuildManager.CFG_DATA_PROVIDER_ID, configurationData);
			mavenConfDesc = configurationDesc;

			// Set the configuration name
			mavenConfDesc.setName(MAVEN_CONFIGURATION_NAME);
		}
		// set the configuration as the active one
		prjDesc.setActiveConfiguration(mavenConfDesc);
		
		// tweak the configuration for maven
		tweakConfigurationForMaven(os, libraryType, debug, mavenConfDesc, narPluginConfiguration);
		
		// Save the CDT project description
		cdtCoreModel.setProjectDescription(project, prjDesc);
	}
	
	private IProjectType chooseProjectType(String os, String linkerName, String libraryType) throws CoreException {
		// ------------------------
		// cdt.managedbuild.target.gnu.exe,
		// cdt.managedbuild.target.gnu.lib,
		// cdt.managedbuild.target.gnu.so,
		// ------------------------
		// cdt.managedbuild.target.gnu.cross.exe,
		// cdt.managedbuild.target.gnu.cross.lib,
		// cdt.managedbuild.target.gnu.cross.so,
		// ------------------------
		// cdt.managedbuild.target.gnu.cygwin.exe,
		// cdt.managedbuild.target.gnu.cygwin.lib,
		// cdt.managedbuild.target.gnu.cygwin.so,
		// ------------------------
		// cdt.managedbuild.target.gnu.mingw.exe,
		// cdt.managedbuild.target.gnu.mingw.lib,
		// cdt.managedbuild.target.gnu.mingw.so,
		// ------------------------
		// cdt.managedbuild.target.gnu.solaris.exe,
		// cdt.managedbuild.target.gnu.solaris.lib,
		// cdt.managedbuild.target.gnu.solaris.so,
		// ------------------------
		// cdt.managedbuild.target.macosx.exe,
		// cdt.managedbuild.target.macosx.lib,
		// cdt.managedbuild.target.macosx.so,
		// ------------------------
		// org.eclipse.cdt.msvc.projectType.dll,
		// org.eclipse.cdt.msvc.projectType.exe,
		// org.eclipse.cdt.msvc.projectType.lib,
		
		// We throw an exception if linker definition from maven is not among the CDT configurations
		if("icl".equals(linkerName) || 
				"icpc".equals(linkerName) || "icc".equals(linkerName) || 
				"CC".equals(linkerName) || "xlC".equals(linkerName)) {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Linker " + linkerName + " incompatible with CDT");
			throw new CoreException(status);
		}
		
		String prefix = null;
		if(OS.WINDOWS.equals(os)) {
			//  - msvc (for Windows)
			//  - icl (for Windows)
			//  - g++ (for Windows)
			//  - gcc (for Windows)
			if("msvc".equals(linkerName)) {
				prefix = "org.eclipse.cdt.msvc.projectType";
			} else {
				//TODO choose between mingw and cygwin
				prefix = "cdt.managedbuild.target.gnu.mingw";
			}
		} else if(OS.LINUX.equals(os)) {
			//  - g++ (for Linux)
			//  - gcc (for linux)
			//  - icpc (for Linux)
			//  - icc (for Linux)
			prefix = "cdt.managedbuild.target.gnu";
		} else if(OS.MACOSX.equals(os)) {
			//  - g++ (for Mac OS X)
			//  - gcc (for Mac OS X)
			//  - icpc (for Mac OS X)
			//  - icc (for Mac OS X)
			prefix = "cdt.managedbuild.target.macosx";
		} else if(OS.SUNOS.equals(os)) {
			//  - CC (for Solaris / SunOS)
			//  - g++ (for Solaris / SunOS)
			//  - gcc (for Solaris / SunOS)
			prefix = "cdt.managedbuild.target.gnu.solaris";
		} else if(OS.FREEBSD.equals(os)) {
			//  - g++ (for FreeBSD)
			//  - gcc (for FreeBSD)
			prefix = "cdt.managedbuild.target.gnu";
		} else if("AIX".equals(os)) {
			//  - g++ (for AIX)
			//  - gcc (for AIX)
			//  - xlC (for AIX)
			prefix = "cdt.managedbuild.target.gnu";
		} else {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknown OS " + os + ", unable to choose base configuration");
			throw new CoreException(status);
		}
		
		String suffix = null;
		if("executable".equals(libraryType)) {
			suffix = ".exe";
		} else if("shared".equals(libraryType) || "jni".equals(libraryType)) {
			if(OS.WINDOWS.equals(os) && "msvc".equals(linkerName)) {
				suffix = ".dll";
			} else {
				suffix = ".so";
			}
		} else if("static".equals(libraryType)) {
			suffix = ".lib";
		} else if("plugin".equals(libraryType)) {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Library type " + libraryType + " incompatible with CDT");
			throw new CoreException(status);
		} else {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknwon library type " + libraryType);
			throw new CoreException(status);
		}
		
		String projectTypeId = prefix + suffix;
		IProjectType cdtProjectType = ManagedBuildManager.getExtensionProjectType(projectTypeId);
		if(cdtProjectType == null) {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknwon project type " + projectTypeId);
			throw new CoreException(status);
		}
		
		return cdtProjectType;
	}

	private IConfiguration chooseConfiguration(IProjectType projectType, String os, String linkerName, boolean debug) throws CoreException {
		
		String configurationId = null;
		
		if(OS.WINDOWS.equals(os) && "msvc".equals(linkerName)) {
			// org.eclipse.cdt.msvc.projectType.exe -> org.eclipse.cdt.msvc.exe.[release|debug]
			configurationId = projectType.getId() .replace(".projectType", "");
		} else {
			// cdt.managedbuild.target.gnu.mingw.exe -> cdt.managedbuild.config.gnu.mingw.exe.[release|debug]
			configurationId = projectType.getId().replace("target", "config");
		}
		
		if(debug) {
			configurationId = configurationId + ".debug";
		} else {
			configurationId = configurationId + ".release";
		}
		
		IConfiguration cdtConfiguration = projectType.getConfiguration(configurationId);
		if(cdtConfiguration == null) {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknwon configuraion " + configurationId + " in project type " + projectType.getId());
			throw new CoreException(status);
		}
		
		return cdtConfiguration;
	}

	@SuppressWarnings("restriction")
	private void tweakConfigurationForMaven(String os, String libraryType, boolean debug, ICConfigurationDescription configDecription, NarPluginConfiguration narPluginConfiguration) throws CoreException {
		
		IConfiguration configuration = ManagedBuildManager.getConfigurationForDescription(configDecription);

		// ====== "C/C++ Build / Tool Chain Editor" screen ======
		// Set Current toolchain (default from configuration selection should be fine)
		
		// Set Current builder "Gnu Make Builder"
		// choose the builder
		IBuilder gnuMakeBuilder = chooseGnuMakeBuilder(os, libraryType, debug);
		String gnuMakeBuilderId = gnuMakeBuilder.getId();
		String mavenBuilderId = ManagedBuildManager.calculateChildId(gnuMakeBuilderId, MAVEN_CONFIGURATION_NAME);
		
		IBuilder mavenBuilder = configuration.getEditableBuilder();
		if(mavenBuilder != null && !mavenBuilderId.equals(mavenBuilder.getId())) {
			mavenBuilder = null;
		}
		if(mavenBuilder == null) {
			((Configuration)configuration).changeBuilder(gnuMakeBuilder, mavenBuilderId, gnuMakeBuilder.getName(), true);
		}

		// ====== "C/C++ Build" screen ======
		// === "Builder Settings" tab ===
		// --- "Builder" panel ---
		// Disable the Internal builder (set External builder), already done by Set "Gnu Make Builder"
		((Configuration)configuration).enableInternalBuilder(false);
		
		// Disable the use of Default build command
		configuration.getEditableBuilder().setUseDefaultBuildCmd(false);
		
		// Set the Build command
		configuration.getEditableBuilder().setBuildAttribute(IMakeBuilderInfo.BUILD_COMMAND, SystemConfigurator.getMvnCommand(os));
		configuration.getEditableBuilder().setBuildAttribute(IMakeBuilderInfo.BUILD_ARGUMENTS, "");
		
		// --- "Makefile generation" panel ---
		// Disable the Makefile generation
		configuration.getEditableBuilder().setManagedBuildOn(false);
		
		// --- "Build location" panel ---
		// Set the Build directory
		//String buildPath = configuration.getEditableBuilder().getBuildPath(); // ${workspace_loc:/<project-name>}/
		//configuration.getEditableBuilder().setBuildPath(buildPath);
		
		// === "Behavior" tab ===
		// --- "Build settings" panel ---
		// Stop on first build error
		
		// Enable parallel build
		
		// --- "Workbench Build Behavior" panel ---
		// Build on resource save (Auto build)
		configuration.getEditableBuilder().setAutoBuildEnable(false);
		configuration.getEditableBuilder().setBuildAttribute(IMakeBuilderInfo.BUILD_TARGET_AUTO, "compile");
		
		// Build (Incremental build)
		configuration.getEditableBuilder().setIncrementalBuildEnable(true);
		configuration.getEditableBuilder().setBuildAttribute(IMakeBuilderInfo.BUILD_TARGET_INCREMENTAL, "install");
		
		// Clean
		// we add generate-sources after the clean command in order to unpack the dependencies in the target folder,
		// this way eclipse won't complain about missing include files
		configuration.getEditableBuilder().setCleanBuildEnable(true);
		configuration.getEditableBuilder().setBuildAttribute(IMakeBuilderInfo.BUILD_TARGET_CLEAN, "clean generate-sources");
		
		// ====== "C/C++ Build / Build Variables" screen ======
		// Place here new build variables

		// ====== "C/C++ Build / Discovery Options" screen ======
		// --- "Discovery profiles scope" panel ---
		// Set "Per Langage" / "Configuration-wide"
		
		// --- "Automated discovery of paths and symbols" panel ---
		// Set "Automate discovery of paths and symbols" and depending configuration. Deprecated !
		
		// ====== "C/C++ Build / Environment" screen ======
		// Set Environment variables
		
		// ====== "C/C++ Build / Logging" screen ======
		// Set Log file location

		// ====== "C/C++ Build / Settings" screen ======
		// === "Binary Parsers" tab ===
		// Set Binary parsers (default from configuration selection should be fine)
		
		// === "Error Parsers" tab ===
		// Set Error Parsers (default from configuration selection should be fine)
		
		// ====== "C/C++ General" screen ======
		// Set Enable project specific settings
		
		// --- "Documentation tool comments" panel ---
		// Set Documentation tool

		// ====== "C/C++ General / Code Analysis" screen ======
		// Set Use workspace settings or Use project settings
		
		// Enable / disable code analysis items

		// ====== "C/C++ General / Documentation" screen ======
		// Set Help books

		// ====== "C/C++ General / Export Settings" screen ======
		// === "Includes" tab ===
		// Set include paths
		
		// === "Include Files" tab ===
		// Set include files
		
		// === "Symbols" tab ===
		// Set symbols
		
		// === "Libraries" tab ===
		// Set libraries
		
		// === "Library Paths" tab ===
		// Set library paths
		
		// ====== "C/C++ General / Files Types" screen ======
		// Set Use workspace settings or Use project settings

		// Set files associations

		// ====== "C/C++ General / Formatter" screen ======
		// Set Enable project specific settings

		// Set formatter to use

		// ====== "C/C++ General / Indexer" screen ======
		// Set Enable project specific settings
		
		// Set Store settings with project
		
		// Set Enable indexer
		
		// --- "Indexer options" panel ---
		// Set Index source files not included in the build
		
		// Set Index unused headers as C++ files
		
		// Set Index unused headers as C files
		
		// Set Index all header variants
		
		// Set Index all variants od specific headers
		
		// Set Index source and header files opened in editor
		
		// Set Allow heuristic resolution of includes
		
		// Set Skip files larger than
		
		// Set Skip included files larger than
		
		// Set Skip all references
		
		// Set Skip implicit references
		
		// Set Skip type and macro references
		
		// --- "Build configuration for the indexer" panel ---
		// Set Use active build configuration or Use fixed build configuration
		
		// Set Configuration if fixed build configuration
		
		// Set Reindex project on change of active build configuration

		// ====== "C/C++ General / Language Mappings" screen ======
		// Set language mappings

		// ====== "C/C++ General / Paths and Symbols" screen ======

		// === "Source Location" tab ===
		// The source directories management will be done in project specific configuration
		
		// === "Includes" tab ===
		// The include management will be done in project specific configuration
		
		// === "Include Files" tab ===
		
		// === "Symbols" tab ===
		
		// === "Libraries" tab ===
		
		// === "Library Paths" tab ===

		// === "Output Location" tab ===
		
		// === "References" tab ===

		// ====== "C/C++ General / Preprocessor Include Paths, Macros etc." screen ======
		// === "Entries" tab ===
		// (nothing to do there as it is just to see the entries form the providers in the ui)
		
		// === "Providers" tab ===
		setLanguageProviders(configDecription, configuration);
		
		// ====== "C/C++ General / Profiling Categories" screen ======
		// === "Timing" tab ===
		// Set Enable project-specific settings
		
		// --- "Choose default launch provider" panel ---
		// Set timing profiling provider
		
		// === "Memory" tab ===
		// Set Enable project-specific settings
		
		// --- "Choose default launch provider" panel ---
		// Set memory profiling provider
		
		// === "Coverage" tab ===
		// Set Enable project-specific settings
		
		// --- "Choose default launch provider" panel ---
		// Set coverage profiling provider
		
	}

	private IBuilder chooseGnuMakeBuilder(String os, String libraryType, boolean debug) throws CoreException {
		// ------------------------
		// cdt.managedbuild.target.gnu.builder.exe.debug
		// cdt.managedbuild.target.gnu.builder.exe.release
		// cdt.managedbuild.target.gnu.builder.lib.debug
		// cdt.managedbuild.target.gnu.builder.lib.release
		// cdt.managedbuild.target.gnu.builder.so.debug
		// cdt.managedbuild.target.gnu.builder.so.release
		// ------------------------
		// cdt.managedbuild.target.gnu.builder.cygwin.exe.debug
		// cdt.managedbuild.target.gnu.builder.cygwin.exe.release
		// cdt.managedbuild.target.gnu.builder.cygwin.lib.debug
		// cdt.managedbuild.target.gnu.builder.cygwin.lib.release
		// cdt.managedbuild.target.gnu.builder.cygwin.so.debug
		// cdt.managedbuild.target.gnu.builder.cygwin.so.release
		// ------------------------
		// cdt.managedbuild.target.gnu.builder.macosx.exe.debug
		// cdt.managedbuild.target.gnu.builder.macosx.exe.release
		// cdt.managedbuild.target.gnu.builder.macosx.lib.debug
		// cdt.managedbuild.target.gnu.builder.macosx.lib.release
		// cdt.managedbuild.target.gnu.builder.macosx.so.debug
		// cdt.managedbuild.target.gnu.builder.macosx.so.release
		// ------------------------
		
		String id = null;
		if(OS.WINDOWS.equals(os)) {
			//TODO choose between mingw and cygwin
			id = "cdt.managedbuild.target.gnu.builder";
		} else if(OS.LINUX.equals(os)) {
			id = "cdt.managedbuild.target.gnu.builder";
		} else if(OS.MACOSX.equals(os)) {
			id = "cdt.managedbuild.target.gnu.builder.macosx";
		} else if(OS.SUNOS.equals(os)) {
			id = "cdt.managedbuild.target.gnu.builder";
		} else if(OS.FREEBSD.equals(os)) {
			id = "cdt.managedbuild.target.gnu.builder";
		} else if("AIX".equals(os)) {
			id = "cdt.managedbuild.target.gnu.builder";
		} else {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknown OS " + os + ", unable to choose base configuration");
			throw new CoreException(status);
		}
		
		if("executable".equals(libraryType)) {
			id += ".exe";
		} else if("shared".equals(libraryType) || "jni".equals(libraryType)) {
			id += ".so";
		} else if("static".equals(libraryType)) {
			id += ".lib";
		} else if("plugin".equals(libraryType)) {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Library type " + libraryType + " incompatible with CDT");
			throw new CoreException(status);
		} else {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknwon library type " + libraryType);
			throw new CoreException(status);
		}
		
		if(debug) {
			id += ".debug";
		} else {
			id += ".release";
		}
		
		IBuilder builder = ManagedBuildManager.getExtensionBuilder(id);
		if(builder == null) {
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "Unknwon builder " + id);
			throw new CoreException(status);
		}
		
		return builder;
	}
	
	private void setLanguageProviders(ICConfigurationDescription configDecription, IConfiguration configuration) throws CoreException {
		
		boolean gcc = false;
		boolean msvc = false;
		
		if(configuration.getParent() != null) {
			if(configuration.getParent().getId().contains(".gnu.") || configuration.getParent().getId().contains(".target.")) {
				gcc = true;
			} else if(configuration.getParent().getId().contains(".msvc.")) {
				msvc = true;
			}
		}
		
		// Enable the CDT GCC Built-in Compiler Settings if the configuration is based on a "gnu" or "macosx" configuration
		if(gcc) {
			ILanguageSettingsProvider cdtGccProvider = LanguageSettingsManager.getWorkspaceProvider("org.eclipse.cdt.managedbuilder.core.GCCBuiltinSpecsDetector");
			
			if (configDecription instanceof ILanguageSettingsProvidersKeeper) {
				List<ILanguageSettingsProvider> providers = new ArrayList<ILanguageSettingsProvider>(((ILanguageSettingsProvidersKeeper)configDecription).getLanguageSettingProviders());
				boolean addProvider = true;
				for(ILanguageSettingsProvider provider : providers) {
					if(provider.getId().equals(cdtGccProvider.getId())) {
						addProvider = false;
						break;
					}
				}
				if(addProvider) {
					providers.add(cdtGccProvider);
					((ILanguageSettingsProvidersKeeper)configDecription).setLanguageSettingProviders(providers);
				}
			} else {
				// Other way to do the job ?
			}
		} else if(msvc) {
			// TODO language setting providers for msvc
		}
		
		// --- "Language Settings Provider Options" panel ---
		// Set Use global provider shared between projects
		
		// Set Store entries in project settings folder
		
		// Set Command to get compiler specs
		
		// Set allocate console in the Console View
		
	}

	private void setProjectSpecificConfiguration(IProject project, MavenProject mavenProject, NarPluginConfiguration narPluginConfiguration, IProgressMonitor monitor, Log log) throws CoreException {
		
		// Get the CDT project description
		CoreModel cdtCoreModel = CoreModel.getDefault();
		ICConfigurationDescription mavenConfDesc = null;
		ICProjectDescription prjDesc = cdtCoreModel.getProjectDescription(project);
		if(prjDesc != null) {
			ICConfigurationDescription configDecriptions[] = prjDesc.getConfigurations();
			for (ICConfigurationDescription configDescription : configDecriptions) {
				if(MAVEN_CONFIGURATION_NAME.equals(configDescription.getName())) {
					mavenConfDesc = configDescription;
					break;
				}
			}
		}
		if(mavenConfDesc == null) {
			// we "can't" fall here
			int severity = IStatus.ERROR;
			String pluginId = PLUGIN_ID;
			Status status = new Status(severity, pluginId, "can't find the maven configuration description");
			throw new CoreException(status);
		}
		
		// Set the source directories from project configuration
		setProjectSourceDirectories(mavenConfDesc, narPluginConfiguration);
		
		// Set the includes paths, libraries paths and macro from project configuration
		setProjectLanguagesConfiguration(mavenConfDesc, narPluginConfiguration, log);
		
		// Save the CDT project description
		cdtCoreModel.setProjectDescription(project, prjDesc);
	}

	/**
	 * Set the maven source folders.
	 * 
	 * @param configDecription
	 * @throws CoreException
	 */
	private void setProjectSourceDirectories(ICConfigurationDescription mavenConfDesc, NarPluginConfiguration narPluginConfiguration) throws CoreException {
		IProject project = mavenConfDesc.getProjectDescription().getProject();
		String absoluteProjectBaseDir = project.getLocation().toFile().getPath();
		String workspaceRelativeProjectBaseDir = project.getFullPath().toString();
		
		// Default exclusion pattern (nothing)
		IPath[] exclusionPatterns = new IPath[0];
		
		List<ICSourceEntry> listSourceEntry = new ArrayList<ICSourceEntry>();
		CSourceEntry srcEntry = null;
		
//		// Save existing source entries into the list and exclude the souce entry corresponding to the project base dir
//		srcEntry = new CSourceEntry(workspaceRelativeProjectBaseDir, exclusionPatterns, CSourceEntry.VALUE_WORKSPACE_PATH);
//		ICSourceEntry[] sourceEntries = mavenConfDesc.getSourceEntries();
//		for(ICSourceEntry entry : sourceEntries) {
//			if(!srcEntry.equalsByContents(entry)) {
//				listSourceEntry.add(entry);
//			}
//		}
		
		String sourceDirectory;
		List<String> includePaths;
		
		// Add c++ source directories
		sourceDirectory = narPluginConfiguration.getAbsoluteCppSourceDirectory();
		srcEntry = buildCSourceEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory, exclusionPatterns);
		addCSourceEntry(listSourceEntry, srcEntry);
		
		sourceDirectory = narPluginConfiguration.getAbsoluteCppTestSourceDirectory();
		srcEntry = buildCSourceEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory, exclusionPatterns);
		addCSourceEntry(listSourceEntry, srcEntry);

		// Add c++ include paths as source directories
		includePaths = narPluginConfiguration.getAbsoluteCppIncludePaths();
		for(String includePath : includePaths) {
			srcEntry = buildCSourceEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, includePath, exclusionPatterns);
			addCSourceEntry(listSourceEntry, srcEntry);
		}

		// Add c source directories
		sourceDirectory = narPluginConfiguration.getAbsoluteCSourceDirectory();
		srcEntry = buildCSourceEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory, exclusionPatterns);
		addCSourceEntry(listSourceEntry, srcEntry);
		
		sourceDirectory = narPluginConfiguration.getAbsoluteCTestSourceDirectory();
		srcEntry = buildCSourceEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory, exclusionPatterns);
		addCSourceEntry(listSourceEntry, srcEntry);

		// Add c include paths as source directories
		includePaths = narPluginConfiguration.getAbsoluteCIncludePaths();
		for(String includePath : includePaths) {
			srcEntry = buildCSourceEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, includePath, exclusionPatterns);
			addCSourceEntry(listSourceEntry, srcEntry);
		}
		
		mavenConfDesc.setSourceEntries(listSourceEntry.toArray(new CSourceEntry[listSourceEntry.size()]));
	}

	private CSourceEntry buildCSourceEntry(String absoluteProjectBaseDir, String workspaceRelativeProjectBaseDir, String sourceDirectory, IPath[] exclusionPatterns) {
		String directory = sourceDirectory;
		int flag = CSourceEntry.NONE;
		
		if(directory.startsWith(absoluteProjectBaseDir)) {
			directory = workspaceRelativeProjectBaseDir.concat(directory.substring(absoluteProjectBaseDir.length()).replace("\\", "/"));
			flag = CSourceEntry.VALUE_WORKSPACE_PATH;
		}
		CSourceEntry srcEntry = new CSourceEntry(directory, exclusionPatterns, flag);
		return srcEntry;
	}

	private void addCSourceEntry(List<ICSourceEntry> listSourceEntry, ICSourceEntry entry) {
		boolean add = true;
		ICSourceEntry toRemove = null;
		for(ICSourceEntry sourceEntry : listSourceEntry) {
			if(sourceEntry.equalsByContents(entry)) {
				add = false;
				break;
			}
			// if the new entry is a subfolder of an existing entry, there is no need to keep it
			if(sourceEntry.getFullPath().isPrefixOf(entry.getFullPath())) {
				add = false;
				break;
			}
			// if an existing entry is a subfolder of the new entry, we replace the existing entry by the new one
			if(entry.getFullPath().isPrefixOf(sourceEntry.getFullPath())) {
				toRemove = sourceEntry;
			}
		}
		if(add) {
			listSourceEntry.add(entry);
			if(toRemove != null) {
				listSourceEntry.remove(toRemove);
			}
		}
	}

	private void setProjectLanguagesConfiguration(ICConfigurationDescription mavenConfDesc, NarPluginConfiguration narPluginConfiguration, Log log) throws CoreException {
		IProject project = mavenConfDesc.getProjectDescription().getProject();
		String absoluteProjectBaseDir = project.getLocation().toFile().getPath();
		String workspaceRelativeProjectBaseDir = project.getFullPath().toString();
		
		List<ICLanguageSettingEntry> cppIncludePathEntries = new ArrayList<ICLanguageSettingEntry>();
		List<ICLanguageSettingEntry> cIncludePathEntries = new ArrayList<ICLanguageSettingEntry>();
		CIncludePathEntry includePathEntry;
		
		// Note : libraries and libraries path are not useful as they are not used by the CDT editor (contrary to
		// includes and macro which are used to check the code) and the compile and linking job will not be done by CDT
		/*
		List<ICLanguageSettingEntry> libraryPathEntries = new ArrayList<ICLanguageSettingEntry>();
		CLibraryPathEntry libraryPathEntry;

		List<ICLanguageSettingEntry> libraryFileEntries = new ArrayList<ICLanguageSettingEntry>();
		CLibraryFileEntry libraryFileEntry;
		*/
		
		List<ICLanguageSettingEntry> cppDefineEntries = new ArrayList<ICLanguageSettingEntry>();
		List<ICLanguageSettingEntry> cDefineEntries = new ArrayList<ICLanguageSettingEntry>();
		//List<ICLanguageSettingEntry> cppUndefineEntries = new ArrayList<ICLanguageSettingEntry>();
		Set<String> cppUndefines = new HashSet<String>();
		//List<ICLanguageSettingEntry> cUndefineEntries = new ArrayList<ICLanguageSettingEntry>();
		Set<String> cUndefines = new HashSet<String>();
		
		CMacroEntry macroEntry;
		
//		String sourceDirectory;
		List<String> includePaths;
		List<NarDependencyProperties> dependenciesProperties;
		Map<String, String> macros;
		Set<Entry<String, String>> entries;

//		// Add c++ source directories as include paths
//		sourceDirectory = narPluginConfiguration.getAbsoluteCppSourceDirectory();
//		includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory);
//		addLanguageSettingEntry(cppIncludePathEntries, includePathEntry);
//		
//		sourceDirectory = narPluginConfiguration.getAbsoluteCppTestSourceDirectory();
//		includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory);
//		addLanguageSettingEntry(cppIncludePathEntries, includePathEntry);
		
		// Add c++ project include paths
		includePaths = narPluginConfiguration.getAbsoluteCppIncludePaths();
		for(String includePath : includePaths) {
			includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, includePath);
			addLanguageSettingEntry(cppIncludePathEntries, includePathEntry);
		}
		
//		// Add c source directories as include paths
//		sourceDirectory = narPluginConfiguration.getAbsoluteCSourceDirectory();
//		includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory);
//		addLanguageSettingEntry(cIncludePathEntries, includePathEntry);
//		
//		sourceDirectory = narPluginConfiguration.getAbsoluteCTestSourceDirectory();
//		includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, sourceDirectory);
//		addLanguageSettingEntry(cIncludePathEntries, includePathEntry);
		
		// Add c project include paths
		includePaths = narPluginConfiguration.getAbsoluteCIncludePaths();
		for(String includePath : includePaths) {
			includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, includePath);
			addLanguageSettingEntry(cIncludePathEntries, includePathEntry);
		}
		
		// Add dependencies include paths for c and c++ and dependencies as library path and library
		dependenciesProperties = narPluginConfiguration.getCompileDependenciesProperties(log);
		for(NarDependencyProperties dependencyProperties : dependenciesProperties) {
			List<String> absoluteIncludePaths = dependencyProperties.getAbsoluteIncludePaths();
			for(String absoluteIncludePath : absoluteIncludePaths) {
				includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, absoluteIncludePath);
				addLanguageSettingEntry(cppIncludePathEntries, includePathEntry);
				addLanguageSettingEntry(cIncludePathEntries, includePathEntry);
			}
			
			/*
			List<String> absoluteLibraryPaths = dependencyProperties.getAbsoluteLibraryPaths();
			for(String absoluteLibraryPath : absoluteLibraryPaths) {
				libraryPathEntry = buildCLibraryPathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, absoluteLibraryPath);
				addLanguageSettingEntry(libraryPathEntries, libraryPathEntry);
				
			}
			libraryFileEntry = new CLibraryFileEntry(dependencyProperties.getLibraryName(), CLibraryFileEntry.NONE);
			addLanguageSettingEntry(libraryFileEntries, libraryFileEntry);
			*/
		}

		// Add test dependencies include paths for c and c++ and test dependencies as library path and library
		dependenciesProperties = narPluginConfiguration.getTestDependenciesProperties(log);
		for(NarDependencyProperties dependencyProperties : dependenciesProperties) {
			List<String> absoluteIncludePaths = dependencyProperties.getAbsoluteIncludePaths();
			for(String absoluteIncludePath : absoluteIncludePaths) {
				includePathEntry = buildCIncludePathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, absoluteIncludePath);
				addLanguageSettingEntry(cppIncludePathEntries, includePathEntry);
				addLanguageSettingEntry(cIncludePathEntries, includePathEntry);
			}
			
			/*
			List<String> absoluteLibraryPaths = dependencyProperties.getAbsoluteLibraryPaths();
			for(String absoluteLibraryPath : absoluteLibraryPaths) {
				libraryPathEntry = buildCLibraryPathEntry(absoluteProjectBaseDir, workspaceRelativeProjectBaseDir, absoluteLibraryPath);
				addLanguageSettingEntry(libraryPathEntries, libraryPathEntry);
				
			}
			libraryFileEntry = new CLibraryFileEntry(dependencyProperties.getLibraryName(), CLibraryFileEntry.NONE);
			addLanguageSettingEntry(libraryFileEntries, libraryFileEntry);
			*/
		}
		
		// Add c++ system include paths
		includePaths = narPluginConfiguration.getCppSystemIncludePaths();
		for(String includePath : includePaths) {
			includePathEntry = new CIncludePathEntry(includePath, CIncludePathEntry.NONE);
			addLanguageSettingEntry(cppIncludePathEntries, includePathEntry);
		}
		
		// Add c system include paths
		includePaths = narPluginConfiguration.getCSystemIncludePaths();
		for(String includePath : includePaths) {
			includePathEntry = new CIncludePathEntry(includePath, CIncludePathEntry.NONE);
			addLanguageSettingEntry(cIncludePathEntries, includePathEntry);
		}
		
		//TODO clearDefaultDefines
		
		// Extract c++ macros to add
		macros = narPluginConfiguration.getCppDefines();
		entries = macros.entrySet();
		for(Entry<String, String> entry : entries) {
			macroEntry = new CMacroEntry(entry.getKey(), entry.getValue(), CMacroEntry.NONE);
			addLanguageSettingEntry(cppDefineEntries, macroEntry);
		}

		// Extract c++ macros to remove
		macros = narPluginConfiguration.getCppUndefines();
		entries = macros.entrySet();
		for(Entry<String, String> entry : entries) {
			//macroEntry = new CMacroEntry(entry.getKey(), entry.getValue(), CMacroEntry.NONE);
			//addLanguageSettingEntry(cppUndefineEntries, macroEntry);
			cppUndefines.add(entry.getKey());
		}
		
		// Extract c macros to add
		macros = narPluginConfiguration.getCDefines();
		entries = macros.entrySet();
		for(Entry<String, String> entry : entries) {
			macroEntry = new CMacroEntry(entry.getKey(), entry.getValue(), CMacroEntry.NONE);
			addLanguageSettingEntry(cDefineEntries, macroEntry);
		}

		// Extract c macros to remove
		macros = narPluginConfiguration.getCUndefines();
		entries = macros.entrySet();
		for(Entry<String, String> entry : entries) {
			//macroEntry = new CMacroEntry(entry.getKey(), entry.getValue(), CMacroEntry.NONE);
			//addLanguageSettingEntry(cUndefineEntries, macroEntry);
			cUndefines.add(entry.getKey());
		}
		
		/*
		//TODO libs
		
		//TODO sysLibs
		*/
		
		ICFolderDescription projectRoot = mavenConfDesc.getRootFolderDescription();
		ICLanguageSetting[] settings = projectRoot.getLanguageSettings();
		for (ICLanguageSetting setting : settings) {
			if (CDT_GPP_LANGUAGE_ID.equals(setting.getLanguageId())) {
				
				// === Include paths ===
				List<ICLanguageSettingEntry> includePathEntries = new ArrayList<ICLanguageSettingEntry>();
				//List<ICLanguageSettingEntry> currentIncludePathEntries = setting.getSettingEntriesList(ICSettingEntry.INCLUDE_PATH);
				//includePathEntries.addAll(currentIncludePathEntries);
				
				for(ICLanguageSettingEntry cppIncludePathEntry : cppIncludePathEntries) {
					addLanguageSettingEntry(includePathEntries, cppIncludePathEntry);
				}

				setting.setSettingEntries(ICSettingEntry.INCLUDE_PATH, includePathEntries);
				
//				// === Include files ===
//				List<ICLanguageSettingEntry> includeFileEntries = new ArrayList<ICLanguageSettingEntry>();
//				List<ICLanguageSettingEntry> currentIncludeFileEntries = setting.getSettingEntriesList(ICSettingEntry.INCLUDE_FILE);
//				includeFileEntries.addAll(currentIncludeFileEntries);
//				
//				setting.setSettingEntries(ICSettingEntry.INCLUDE_FILE, includeFileEntries);
				
				// === macro ===
				List<ICLanguageSettingEntry> macroEntries = new ArrayList<ICLanguageSettingEntry>();
				List<ICLanguageSettingEntry> currentMacroEntries = setting.getSettingEntriesList(ICSettingEntry.MACRO);
				
				// Add the current macros which are not undefined
				for(ICLanguageSettingEntry currentMacroEntry : currentMacroEntries) {
					if(!cppUndefines.contains(currentMacroEntry.getName())) {
						macroEntries.add(currentMacroEntry);
					}
				}
				
				// Add the defined macro (if they are not undefined the same time)
				for(ICLanguageSettingEntry cppMacroEntry : cppDefineEntries) {
					if(!cppUndefines.contains(cppMacroEntry.getName())) {
						macroEntries.add(cppMacroEntry);
					}
				}
				
				setting.setSettingEntries(ICSettingEntry.MACRO, macroEntries);
				
				// === macro files ??? ICSettingEntry.MACRO_FILE ===
				
			} else if(CDT_GCC_LANGUAGE_ID.equals(setting.getLanguageId())) {
				
				// === Include paths ===
				List<ICLanguageSettingEntry> includePathEntries = new ArrayList<ICLanguageSettingEntry>();
				//List<ICLanguageSettingEntry> currentIncludePathEntries = setting.getSettingEntriesList(ICSettingEntry.INCLUDE_PATH);
				//includePathEntries.addAll(currentIncludePathEntries);

				for(ICLanguageSettingEntry cIncludePathEntry : cIncludePathEntries) {
					addLanguageSettingEntry(includePathEntries, cIncludePathEntry);
				}

				setting.setSettingEntries(ICSettingEntry.INCLUDE_PATH, includePathEntries);

//				// === Include files ===
//				List<ICLanguageSettingEntry> includeFileEntries = new ArrayList<ICLanguageSettingEntry>();
//				List<ICLanguageSettingEntry> currentIncludeFileEntries = setting.getSettingEntriesList(ICSettingEntry.INCLUDE_FILE);
//				includeFileEntries.addAll(currentIncludeFileEntries);
//				
//				setting.setSettingEntries(ICSettingEntry.INCLUDE_FILE, includeFileEntries);

				// === macro ===
				List<ICLanguageSettingEntry> macroEntries = new ArrayList<ICLanguageSettingEntry>();
				List<ICLanguageSettingEntry> currentMacroEntries = setting.getSettingEntriesList(ICSettingEntry.MACRO);
				
				// Add the current macros which are not undefined
				for(ICLanguageSettingEntry currentMacroEntry : currentMacroEntries) {
					if(!cUndefines.contains(currentMacroEntry.getName())) {
						macroEntries.add(currentMacroEntry);
					}
				}
				
				// Add the defined macro (if they are not undefined the same time)
				for(ICLanguageSettingEntry cMacroEntry : cDefineEntries) {
					if(!cUndefines.contains(cMacroEntry.getName())) {
						macroEntries.add(cMacroEntry);
					}
				}
				
				setting.setSettingEntries(ICSettingEntry.MACRO, macroEntries);

				// === macro files ??? ICSettingEntry.MACRO_FILE ===
				
			} else if(setting.getLanguageId() == null && setting.getId().contains("org.eclipse.cdt.build.core.settings.holder.libs")) {

				/*
				// === Library paths ===
				List<ICLanguageSettingEntry> libPathEntries = new ArrayList<ICLanguageSettingEntry>();
//				List<ICLanguageSettingEntry> currentLibPathEntries = setting.getSettingEntriesList(ICSettingEntry.LIBRARY_PATH);
//				libPathEntries.addAll(currentLibPathEntries);

				for(ICLanguageSettingEntry libPathEntry : libraryPathEntries) {
					addLanguageSettingEntry(libPathEntries, libPathEntry);
				}

				setting.setSettingEntries(ICSettingEntry.LIBRARY_PATH, libPathEntries);
				
				// === Library files ===
				List<ICLanguageSettingEntry> libFileEntries = new ArrayList<ICLanguageSettingEntry>();
//				List<ICLanguageSettingEntry> currentLibFileEntries = setting.getSettingEntriesList(ICSettingEntry.LIBRARY_FILE);
//				libFileEntries.addAll(currentLibFileEntries);

				for(ICLanguageSettingEntry libFileEntry : libraryFileEntries) {
					addLanguageSettingEntry(libFileEntries, libFileEntry);
				}

				setting.setSettingEntries(ICSettingEntry.LIBRARY_FILE, libFileEntries);
				*/
			}
		}
	}

	private CIncludePathEntry buildCIncludePathEntry(String absoluteProjectBaseDir, String workspaceRelativeProjectBaseDir, String includePath) {
		String directory = includePath;
		int flag = CIncludePathEntry.NONE;
		if(directory.startsWith(absoluteProjectBaseDir)) {
			directory = workspaceRelativeProjectBaseDir.concat(directory.substring(absoluteProjectBaseDir.length()).replace("\\", "/"));
			flag = CIncludePathEntry.VALUE_WORKSPACE_PATH;
		}
		CIncludePathEntry includePathEntry = new CIncludePathEntry(directory, flag);
		return includePathEntry;
	}

	private CLibraryPathEntry buildCLibraryPathEntry(String absoluteProjectBaseDir, String workspaceRelativeProjectBaseDir, String libraryPath) {
		String directory = libraryPath;
		int flag = CLibraryPathEntry.NONE;
		if(directory.startsWith(absoluteProjectBaseDir)) {
			directory = workspaceRelativeProjectBaseDir.concat(directory.substring(absoluteProjectBaseDir.length()).replace("\\", "/"));
			flag = CLibraryPathEntry.VALUE_WORKSPACE_PATH;
		}
		CLibraryPathEntry libraryPathEntry = new CLibraryPathEntry(directory, flag);
		return libraryPathEntry;
	}

	private void addLanguageSettingEntry(List<ICLanguageSettingEntry> listSourceEntry, ICLanguageSettingEntry entry) {
		boolean add = true;
		for(ICLanguageSettingEntry sourceEntry : listSourceEntry) {
			if(sourceEntry.equalsByContents(entry)) {
				add = false;
				break;
			}
		}
		if(add) {
			listSourceEntry.add(entry);
		}
	}

}
