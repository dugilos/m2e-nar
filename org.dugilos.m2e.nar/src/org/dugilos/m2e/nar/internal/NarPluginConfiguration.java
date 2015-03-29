package org.dugilos.m2e.nar.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import com.github.maven_nar.AOL;
import com.github.maven_nar.AbstractNarLayout;
import com.github.maven_nar.Library;
import com.github.maven_nar.Linker;
import com.github.maven_nar.NarArtifact;
import com.github.maven_nar.NarLayout;
import com.github.maven_nar.NarManager;
import com.github.maven_nar.NarProperties;
import com.github.maven_nar.NarUtil;

public class NarPluginConfiguration {

	private static final String NAR_MAVEN_PLUGIN_GROUPID = "com.github.maven-nar";
	private static final String NAR_MAVEN_PLUGIN_ARTIFACTID = "nar-maven-plugin";

	private static final String COMPILE_DEPENDENCIES_UNPACK_DIRECTORY = "nar";
	private static final String TEST_DEPENDENCIES_UNPACK_DIRECTORY = "test-nar";
	
	private static final String DEFAULT_LAYOUT = "NarLayout21"; //"com.github.maven_nar.NarLayout21";
	private static final String DEFAULT_LIBRARY_TYPE = "shared";
	private static final boolean DEFAULT_DEBUG = false;
	private static final String DEFAULT_RELATIVE_SOURCE_DIRECTORY = "src/main";
	private static final String DEFAULT_RELATIVE_TEST_SOURCE_DIRECTORY = "src/test";
	private static final String DEFAULT_RELATIVE_INCLUDE_DIRECTORY = "include";
	
	//private MavenProject mavenProject = null;
	private String pluginId = null;
	
	private File absoluteProjectBaseDir = null;
	private String projectBuildDirectory = null;
	
	private Plugin narMavenPlugin = null;
	private Plugin MgtNarMavenPlugin = null;
	
	private Linker linker = null;
	private NarManager narManager = null;
	private AOL aol = null;
	private NarLayout narLayout = null;
	
	public NarPluginConfiguration(IProject project, MavenProject mavenProject, String pluginId, Log log) throws CoreException {

		//this.mavenProject = mavenProject;
		this.pluginId = pluginId;

		// extract local repository (containing nar files dependencies)
		ProjectBuildingRequest buildingRequest = mavenProject.getProjectBuildingRequest();
		ArtifactRepository artifactRepository = buildingRequest.getLocalRepository();

		// extract project directories
		absoluteProjectBaseDir = project.getLocation().toFile();
		projectBuildDirectory = mavenProject.getBuild().getDirectory();
		
		// extract com.github.maven-nar:nar-maven-plugin plugin
		narMavenPlugin = getNarMavenPlugin(mavenProject);
		MgtNarMavenPlugin = getMgtNarMavenPlugin(mavenProject);
		// If the plugin isn't used in the project, it's useless to go further
		if(narMavenPlugin == null) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, "Plugin " + NAR_MAVEN_PLUGIN_GROUPID + ":" + NAR_MAVEN_PLUGIN_ARTIFACTID + " not found");
			throw new CoreException(status);
		}
		
		String arch = NarUtil.getArchitecture(null);
		String os = NarUtil.getOS(null);
		String aolPrefix = arch + "." + os + ".";

		NarProperties narProperties = null;
		try {
			narProperties = NarProperties.getInstance(mavenProject);
		} catch (MojoFailureException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}

		String configLinkerName = getConfigurationLinkerName();
		linker = new Linker(configLinkerName, log);
		// if configLinkerName is null we call getName(NarProperties properties, String prefix) to initialise the name from AOL
		if(configLinkerName == null) {
			try {
				linker.getName(narProperties, aolPrefix);
			} catch (MojoFailureException e) {
				int severity = IStatus.ERROR;
				Status status = new Status(severity, pluginId, e.getMessage(), e);
				throw new CoreException(status);
			} catch (MojoExecutionException e) {
				int severity = IStatus.ERROR;
				Status status = new Status(severity, pluginId, e.getMessage(), e);
				throw new CoreException(status);
			}
		}
		
		try {
			narManager = new NarManager(log, artifactRepository, mavenProject, arch, os, linker);
		} catch (MojoFailureException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		} catch (MojoExecutionException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		try {
			aol = NarUtil.getAOL(mavenProject, arch, os, linker, null, log);
		} catch (MojoFailureException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		} catch (MojoExecutionException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		String layoutName = getLayoutName();
		try {
			narLayout = AbstractNarLayout.getLayout(layoutName, log);
		} catch (MojoExecutionException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
	}

	private Plugin getNarMavenPlugin(MavenProject mavenProject) {
		// extract com.github.maven-nar:nar-maven-plugin plugin
		Plugin narMavenPlugin = null;

		List<Plugin> plugins = mavenProject.getBuild().getPlugins();
		for(Iterator<Plugin> it = plugins.iterator(); it.hasNext();) {
			Plugin plugin = it.next();
			
			if(NAR_MAVEN_PLUGIN_GROUPID.equals(plugin.getGroupId()) && NAR_MAVEN_PLUGIN_ARTIFACTID.equals(plugin.getArtifactId())) {
				narMavenPlugin = plugin;
				break;
			}
		}

		return narMavenPlugin;
	}
	
	private Plugin getMgtNarMavenPlugin(MavenProject mavenProject) {
		// extract com.github.maven-nar:nar-maven-plugin management plugin
		Plugin MgtNarMavenPlugin = null;

		List<Plugin> plugins = mavenProject.getBuild().getPluginManagement().getPlugins();
		for(Iterator<Plugin> it = plugins.iterator(); it.hasNext();) {
			Plugin plugin = it.next();
			
			if(NAR_MAVEN_PLUGIN_GROUPID.equals(plugin.getGroupId()) && NAR_MAVEN_PLUGIN_ARTIFACTID.equals(plugin.getArtifactId())) {
				MgtNarMavenPlugin = plugin;
				break;
			}
		}
		
		return MgtNarMavenPlugin;
	}
	
	private String getConfigurationLinkerName() throws CoreException {
		String linkerName = null;
		
		// Get the linker name from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			if(pluginDomConfig != null) {
				linkerName = getLinkerNameFromDomConfig(pluginDomConfig);
			}
			if(linkerName == null && mgtPluginDomConfig != null) {
				linkerName = getLinkerNameFromDomConfig(mgtPluginDomConfig);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return linkerName;
	}

	private String getLinkerNameFromDomConfig(Xpp3Dom domConfig) throws CoreException {
		String linkerName = null;
		
		// Get the linker name in domConfig if defined
		try {
			Xpp3Dom linkerDomElement = domConfig.getChild("linker");
			if(linkerDomElement != null) {
				Xpp3Dom nameDomElement = linkerDomElement.getChild("name");
				if(nameDomElement != null) {
					linkerName = nameDomElement.getValue();
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return linkerName;
	}

	private String getLayoutName() throws CoreException {
		// Set the default layout
		String layout = DEFAULT_LAYOUT;
		
		// Get the layout from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			String str = null;
			if(pluginDomConfig != null) {
				str = getLayoutFromDomConfig(pluginDomConfig);
			}
			if(str == null && mgtPluginDomConfig != null) {
				str = getLayoutFromDomConfig(mgtPluginDomConfig);
			}
			if(str != null) {
				layout = str;
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return layout;
	}
	
	private String getLayoutFromDomConfig(Xpp3Dom domConfig) throws CoreException {
		// Get the layout in domConfig if defined
		try {
			Xpp3Dom layoutDomElement = domConfig.getChild("layout");
			if(layoutDomElement != null) {
				return layoutDomElement.getValue();
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}
	
	public Linker getLinker() {
		return linker;
	}

	public NarManager getNarManager() {
		return narManager;
	}

	public AOL getAol() {
		return aol;
	}

	public NarLayout getNarLayout() {
		return narLayout;
	}

	public List<NarDependency> getCompileDependenciesProperties(Log log) throws CoreException {
		return getDependenciesProperties(Artifact.SCOPE_COMPILE, COMPILE_DEPENDENCIES_UNPACK_DIRECTORY, log);
	}

	public List<NarDependency> getTestDependenciesProperties(Log log) throws CoreException {
		return getDependenciesProperties(Artifact.SCOPE_TEST, TEST_DEPENDENCIES_UNPACK_DIRECTORY, log);
	}
	
	@SuppressWarnings("unchecked")
	private List<NarDependency> getDependenciesProperties(String scope, String unpackFolder, Log log) throws CoreException {
		
		// get dependencies unpack directory
		File unpackDirectory = new File(projectBuildDirectory, unpackFolder);
		
		// get nar dependencies
		List<NarArtifact> narDependencies = null;
		try {
			narDependencies = narManager.getNarDependencies(scope);
		} catch (MojoExecutionException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		// get nar dependencies paths
		List<NarDependency> narDependenciesProperties = getNarDependenciesProperties(narDependencies, unpackDirectory, log);
		
		return narDependenciesProperties;
	}

	private List<NarDependency> getNarDependenciesProperties(List<NarArtifact> narDependencies, File unpackDirectory, Log log) throws CoreException {
		List<NarDependency> narDependenciesProperties = new ArrayList<NarDependency>();
		
		for (Iterator<NarArtifact> it = narDependencies.iterator(); it.hasNext();) {
			NarArtifact narDependency = it.next();
			
			String binding = narDependency.getNarInfo().getBinding(aol, Library.NONE);
			if (!binding.equals(Library.JNI) && !binding.equals(Library.NONE)) {
				AOL narDependencyAol = narDependency.getNarInfo().getAOL(aol);
				
				String artifactId = narDependency.getArtifactId();
				String version = narDependency.getVersion();
				
				// build library name : artifactId-version
				String libraryName = artifactId + "-" + version;
				
				// Get include and library paths for the dependency
				File includeDirectory = null;
				File libDirectory = null;
				try {
					includeDirectory = narLayout.getIncludeDirectory(unpackDirectory, artifactId, version);
					libDirectory = narLayout.getLibDirectory(unpackDirectory, artifactId, version, narDependencyAol.toString(), binding);
				} catch (MojoFailureException e) {
					int severity = IStatus.ERROR;
					Status status = new Status(severity, pluginId, e.getMessage(), e);
					throw new CoreException(status);
				} catch (MojoExecutionException e) {
					int severity = IStatus.ERROR;
					Status status = new Status(severity, pluginId, e.getMessage(), e);
					throw new CoreException(status);
				}

				// Convert the paths to absolute if thez are relative
				String absoluteIncludePath;
				if(includeDirectory.isAbsolute()) {
					absoluteIncludePath = includeDirectory.getPath();
				} else {
					File absoluteIncludeDir = new File(absoluteProjectBaseDir, includeDirectory.getPath());
					absoluteIncludePath = absoluteIncludeDir.getPath();
				}
				String absoluteLibPath;
				if(libDirectory.isAbsolute()) {
					absoluteLibPath = libDirectory.getPath();
				} else {
					File absoluteLibDir = new File(absoluteProjectBaseDir, libDirectory.getPath());
					absoluteLibPath = absoluteLibDir.getPath();
				}
				
				NarDependency dependencyProperties = new NarDependency(libraryName, absoluteIncludePath, absoluteLibPath);
				narDependenciesProperties.add(dependencyProperties);
			}
		}
		
		return narDependenciesProperties;
	}
	
	public String getFirstLibraryType() throws CoreException {
		// Set the default library type
		String libraryType = DEFAULT_LIBRARY_TYPE;
		
		// Get the library type from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			String str = null;
			if(pluginDomConfig != null) {
				str = getFirstLibraryTypeFromDomConfig(pluginDomConfig);
			}
			if(str == null && mgtPluginDomConfig != null) {
				str = getFirstLibraryTypeFromDomConfig(mgtPluginDomConfig);
			}
			if(str != null) {
				libraryType = str;
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return libraryType;
	}
	
	private String getFirstLibraryTypeFromDomConfig(Xpp3Dom domConfig) throws CoreException {
		// Get the library type in domConfig if defined
		try {
			Xpp3Dom librariesDomElement = domConfig.getChild("libraries");
			if(librariesDomElement != null && librariesDomElement.getChildCount() > 0) {
				Xpp3Dom libraryDomElement = librariesDomElement.getChild(0);
				if(libraryDomElement != null) {
					Xpp3Dom libraryTypeDomElement = libraryDomElement.getChild("type");
					if(libraryTypeDomElement != null) {
						return libraryTypeDomElement.getValue();
					}
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}
	
	public boolean isDebug() throws CoreException {
		// Set the default debug mode
		boolean debug = DEFAULT_DEBUG;
		
		// Get the debug mode from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			Boolean b = null;
			if(pluginDomConfig != null) {
				b = getDebugFromDomConfig(pluginDomConfig);
			}
			if(b == null && mgtPluginDomConfig != null) {
				b = getDebugFromDomConfig(mgtPluginDomConfig);
			}
			if(b != null) {
				debug = b.booleanValue();
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return debug;
	}
	
	private Boolean getDebugFromDomConfig(Xpp3Dom domConfig) throws CoreException {
		// Get the debug mode in domConfig if defined
		try {
			String cppDebugStr = null;
			Xpp3Dom cppDomElement = domConfig.getChild("cpp");
			if(cppDomElement != null) {
				Xpp3Dom debugDomElement = cppDomElement.getChild("debug");
				if(debugDomElement != null) {
					cppDebugStr = debugDomElement.getValue();
				}
			}
			
			String cDebugStr = null;
			Xpp3Dom cDomElement = domConfig.getChild("c");
			if(cDomElement != null) {
				Xpp3Dom debugDomElement = cDomElement.getChild("debug");
				if(debugDomElement != null) {
					cDebugStr = debugDomElement.getValue();
				}
			}
			
			if(cppDebugStr != null || cDebugStr != null) {
				if("true".equals(cppDebugStr) || "true".equals(cDebugStr)) {
					return Boolean.TRUE;
				} else {
					return Boolean.FALSE;
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}

	public String getAbsoluteCppSourceDirectory() throws CoreException {
		String sourceDirectory = null;
		
		// Get the source directory from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			String src = null;
			if(pluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("cpp", "sourceDirectory", pluginDomConfig);
			}
			if(src == null && mgtPluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("cpp", "sourceDirectory", mgtPluginDomConfig);
			}
			if(src != null) {
				sourceDirectory = src;
			} else {
				// default c++ source directory
				File absoluteSrcDir = new File(absoluteProjectBaseDir, DEFAULT_RELATIVE_SOURCE_DIRECTORY);
				sourceDirectory = absoluteSrcDir.getPath();
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return sourceDirectory;
	}

	public String getAbsoluteCSourceDirectory() throws CoreException {
		String sourceDirectory = null;
		
		// Get the source directory from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			String src = null;
			if(pluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("c", "sourceDirectory", pluginDomConfig);
			}
			if(src == null && mgtPluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("c", "sourceDirectory", mgtPluginDomConfig);
			}
			if(src != null) {
				sourceDirectory = src;
			} else {
				// default c source directory
				File absoluteSrcDir = new File(absoluteProjectBaseDir, DEFAULT_RELATIVE_SOURCE_DIRECTORY);
				sourceDirectory = absoluteSrcDir.getPath();
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return sourceDirectory;
	}

	public String getAbsoluteCppTestSourceDirectory() throws CoreException {
		String sourceDirectory = null;
		
		// Get the source directory from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			String src = null;
			if(pluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("cpp", "testSourceDirectory", pluginDomConfig);
			}
			if(src == null && mgtPluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("cpp", "testSourceDirectory", mgtPluginDomConfig);
			}
			if(src != null) {
				sourceDirectory = src;
			} else {
				// default c++ test source directory
				File absoluteSrcDir = new File(absoluteProjectBaseDir, DEFAULT_RELATIVE_TEST_SOURCE_DIRECTORY);
				sourceDirectory = absoluteSrcDir.getPath();
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return sourceDirectory;
	}

	public String getAbsoluteCTestSourceDirectory() throws CoreException {
		String sourceDirectory = null;
		
		// Get the source directory from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			String src = null;
			if(pluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("c", "testSourceDirectory", pluginDomConfig);
			}
			if(src == null && mgtPluginDomConfig != null) {
				src = getAbsoluteSourceDirectoryFromDomConfig("c", "testSourceDirectory", mgtPluginDomConfig);
			}
			if(src != null) {
				sourceDirectory = src;
			} else {
				// default c test source directory
				File absoluteSrcDir = new File(absoluteProjectBaseDir, DEFAULT_RELATIVE_TEST_SOURCE_DIRECTORY);
				sourceDirectory = absoluteSrcDir.getPath();
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return sourceDirectory;
	}
	
	private String getAbsoluteSourceDirectoryFromDomConfig(String language, String type, Xpp3Dom domConfig) throws CoreException {
		try {
			Xpp3Dom languageDomElement = domConfig.getChild(language);
			if(languageDomElement != null) {
				Xpp3Dom sourceDirectoryDomElement = languageDomElement.getChild(type);
				if(sourceDirectoryDomElement != null) {
					String sourceDirectory = sourceDirectoryDomElement.getValue();
					// sourceDirectory may be an absolute path or a relative path (relative to the project base directory)
					File srcDir = new File(sourceDirectory);
					if(srcDir.isAbsolute()) {
						return srcDir.getPath();
					} else {
						File absoluteSrcDir = new File(absoluteProjectBaseDir, sourceDirectory);
						return absoluteSrcDir.getPath();
					}
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}
	
	public List<String> getAbsoluteCppIncludePaths() throws CoreException {
		List<String> includes = new ArrayList<String>();
		
		// Get the includes from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			List<String> list = null;
			if(pluginDomConfig != null) {
				list = getAbsoluteIncludePathsFromDomConfig("cpp", pluginDomConfig);
			}
			if(list == null && mgtPluginDomConfig != null) {
				list = getAbsoluteIncludePathsFromDomConfig("cpp", mgtPluginDomConfig);
			}
			if(list != null) {
				includes.addAll(list);
			} else {
				// default c++ include path
				File file = new File(getAbsoluteCppSourceDirectory(), DEFAULT_RELATIVE_INCLUDE_DIRECTORY);
				includes.add(file.getPath());
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return includes;
	}

	public List<String> getAbsoluteCIncludePaths() throws CoreException {
		List<String> includes = new ArrayList<String>();
		
		// Get the includes from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			List<String> list = null;
			if(pluginDomConfig != null) {
				list = getAbsoluteIncludePathsFromDomConfig("c", pluginDomConfig);
			}
			if(list == null && mgtPluginDomConfig != null) {
				list = getAbsoluteIncludePathsFromDomConfig("c", mgtPluginDomConfig);
			}
			if(list != null) {
				includes.addAll(list);
			} else {
				// default c include path
				File file = new File(getAbsoluteCSourceDirectory(), DEFAULT_RELATIVE_INCLUDE_DIRECTORY);
				includes.add(file.getPath());
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return includes;
	}

	private List<String> getAbsoluteIncludePathsFromDomConfig(String language, Xpp3Dom domConfig) throws CoreException {
		try {
			Xpp3Dom languageDomElement = domConfig.getChild(language);
			if(languageDomElement != null) {
				Xpp3Dom includePathsDomElement = languageDomElement.getChild("includePaths");
				if(includePathsDomElement != null) {
					Xpp3Dom[] tabIncludePathDomElement = includePathsDomElement.getChildren();
					if(tabIncludePathDomElement != null && tabIncludePathDomElement.length > 0) {
						List<String> list = new ArrayList<String>();
						for(Xpp3Dom includePathDomElement : tabIncludePathDomElement) {
							Xpp3Dom pathDomElement = includePathDomElement.getChild("path");
							if(pathDomElement != null) {
								String path = pathDomElement.getValue();
								// path may be an absolute path or a relative path (relative to the project base directory)
								File includeDir = new File(path);
								if(includeDir.isAbsolute()) {
									list.add(includeDir.getPath());
								} else {
									File absoluteIncludeDir = new File(absoluteProjectBaseDir, path);
									list.add(absoluteIncludeDir.getPath());
								}
							}
						}
						return list;
					}
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}

	public List<String> getCppSystemIncludePaths() throws CoreException {
		List<String> includes = new ArrayList<String>();
		
		// Get the includes from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			List<String> list = null;
			if(pluginDomConfig != null) {
				list = getSystemIncludePathsFromDomConfig("cpp", pluginDomConfig);
			}
			if(list == null && mgtPluginDomConfig != null) {
				list = getSystemIncludePathsFromDomConfig("cpp", mgtPluginDomConfig);
			}
			if(list != null) {
				includes.addAll(list);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return includes;
	}

	public List<String> getCSystemIncludePaths() throws CoreException {
		List<String> includes = new ArrayList<String>();
		
		// Get the includes from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			List<String> list = null;
			if(pluginDomConfig != null) {
				list = getSystemIncludePathsFromDomConfig("c", pluginDomConfig);
			}
			if(list == null && mgtPluginDomConfig != null) {
				list = getSystemIncludePathsFromDomConfig("c", mgtPluginDomConfig);
			}
			if(list != null) {
				includes.addAll(list);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return includes;
	}

	private List<String> getSystemIncludePathsFromDomConfig(String language, Xpp3Dom domConfig) throws CoreException {
		try {
			Xpp3Dom languageDomElement = domConfig.getChild(language);
			if(languageDomElement != null) {
				Xpp3Dom systemIncludePathsDomElement = languageDomElement.getChild("systemIncludePaths");
				if(systemIncludePathsDomElement != null) {
					Xpp3Dom[] tabSystemIncludePathDomElement = systemIncludePathsDomElement.getChildren();
					if(tabSystemIncludePathDomElement != null && tabSystemIncludePathDomElement.length > 0) {
						List<String> list = new ArrayList<String>();
						for(Xpp3Dom systemIncludePathDomElement : tabSystemIncludePathDomElement) {
							list.add(systemIncludePathDomElement.getValue());
						}
						return list;
					}
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}
	
	public Map<String, String> getCppDefines() throws CoreException {
		Map<String, String> macros = new HashMap<String, String>();
		
		// Get the cpp defines from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			Map<String, String> map = null;
			if(pluginDomConfig != null) {
				map = getDefinesFromDomConfig("cpp", "defines", pluginDomConfig);
			}
			if(map == null && mgtPluginDomConfig != null) {
				map = getDefinesFromDomConfig("cpp", "defines", mgtPluginDomConfig);
			}
			if(map != null) {
				macros.putAll(map);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return macros;
	}

	public Map<String, String> getCDefines() throws CoreException {
		Map<String, String> macros = new HashMap<String, String>();
		
		// Get the c defines from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			Map<String, String> map = null;
			if(pluginDomConfig != null) {
				map = getDefinesFromDomConfig("c", "defines", pluginDomConfig);
			}
			if(map == null && mgtPluginDomConfig != null) {
				map = getDefinesFromDomConfig("c", "defines", mgtPluginDomConfig);
			}
			if(map != null) {
				macros.putAll(map);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return macros;
	}

	public Map<String, String> getCppUndefines() throws CoreException {
		Map<String, String> macros = new HashMap<String, String>();
		
		// Get the cpp undefines from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			Map<String, String> map = null;
			if(pluginDomConfig != null) {
				map = getDefinesFromDomConfig("cpp", "undefines", pluginDomConfig);
			}
			if(map == null && mgtPluginDomConfig != null) {
				map = getDefinesFromDomConfig("cpp", "undefines", mgtPluginDomConfig);
			}
			if(map != null) {
				macros.putAll(map);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return macros;
	}

	public Map<String, String> getCUndefines() throws CoreException {
		Map<String, String> macros = new HashMap<String, String>();
		
		// Get the c undefines from plugin configuration if defined
		try {
			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
			Xpp3Dom mgtPluginDomConfig = null;
			if(MgtNarMavenPlugin != null) {
				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
			}
			
			Map<String, String> map = null;
			if(pluginDomConfig != null) {
				map = getDefinesFromDomConfig("c", "undefines", pluginDomConfig);
			}
			if(map == null && mgtPluginDomConfig != null) {
				map = getDefinesFromDomConfig("c", "undefines", mgtPluginDomConfig);
			}
			if(map != null) {
				macros.putAll(map);
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return macros;
	}

	private Map<String, String> getDefinesFromDomConfig(String language, String type, Xpp3Dom domConfig) throws CoreException {
		try {
			Xpp3Dom languageDomElement = domConfig.getChild(language);
			if(languageDomElement != null) {
				Xpp3Dom definesDomElement = languageDomElement.getChild(type);
				if(definesDomElement != null) {
					Xpp3Dom[] tabDefineDomElement = definesDomElement.getChildren();
					if(tabDefineDomElement != null && tabDefineDomElement.length > 0) {
						Map<String, String> map = new HashMap<String, String>();
						for(Xpp3Dom defineDomElement : tabDefineDomElement) {
							String define = defineDomElement.getValue();
							String[] macro = define.split("=");
							String name = macro[0];
							String value = "";
							if(macro.length > 1) {
								value = macro[1];
							}
							map.put(name, value);
						}
						return map;
					}
				}
			}
		} catch(ClassCastException e) {
			int severity = IStatus.ERROR;
			Status status = new Status(severity, pluginId, e.getMessage(), e);
			throw new CoreException(status);
		}
		
		return null;
	}
	
//	public List<String> getCppIncludes() throws CoreException {
//		List<String> includes = new ArrayList<String>();
//		
//		// Get the includes from plugin configuration if defined
//		try {
//			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
//			Xpp3Dom mgtPluginDomConfig = null;
//			if(MgtNarMavenPlugin != null) {
//				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
//			}
//			
//			List<String> list = null;
//			if(pluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("cpp", "includes", pluginDomConfig);
//			}
//			if(list == null && mgtPluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("cpp", "includes", mgtPluginDomConfig);
//			}
//			if(list != null) {
//				includes.addAll(list);
//			}
//		} catch(ClassCastException e) {
//			int severity = IStatus.ERROR;
//			Status status = new Status(severity, pluginId, e.getMessage(), e);
//			throw new CoreException(status);
//		}
//		
//		return includes;
//	}
//
//	public List<String> getCIncludes() throws CoreException {
//		List<String> includes = new ArrayList<String>();
//		
//		// Get the includes from plugin configuration if defined
//		try {
//			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
//			Xpp3Dom mgtPluginDomConfig = null;
//			if(MgtNarMavenPlugin != null) {
//				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
//			}
//			
//			List<String> list = null;
//			if(pluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("c", "includes", pluginDomConfig);
//			}
//			if(list == null && mgtPluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("c", "includes", mgtPluginDomConfig);
//			}
//			if(list != null) {
//				includes.addAll(list);
//			}
//		} catch(ClassCastException e) {
//			int severity = IStatus.ERROR;
//			Status status = new Status(severity, pluginId, e.getMessage(), e);
//			throw new CoreException(status);
//		}
//		
//		return includes;
//	}
//
//	public List<String> getCppExcludes() throws CoreException {
//		List<String> excludes = new ArrayList<String>();
//		
//		// Get the excludes from plugin configuration if defined
//		try {
//			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
//			Xpp3Dom mgtPluginDomConfig = null;
//			if(MgtNarMavenPlugin != null) {
//				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
//			}
//			
//			List<String> list = null;
//			if(pluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("cpp", "excludes", pluginDomConfig);
//			}
//			if(list == null && mgtPluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("cpp", "excludes", mgtPluginDomConfig);
//			}
//			if(list != null) {
//				excludes.addAll(list);
//			}
//		} catch(ClassCastException e) {
//			int severity = IStatus.ERROR;
//			Status status = new Status(severity, pluginId, e.getMessage(), e);
//			throw new CoreException(status);
//		}
//		
//		return excludes;
//	}
//
//	public List<String> getCExcludes() throws CoreException {
//		List<String> excludes = new ArrayList<String>();
//		
//		// Get the excludes from plugin configuration if defined
//		try {
//			Xpp3Dom pluginDomConfig = Xpp3Dom.class.cast(narMavenPlugin.getConfiguration());
//			Xpp3Dom mgtPluginDomConfig = null;
//			if(MgtNarMavenPlugin != null) {
//				mgtPluginDomConfig = Xpp3Dom.class.cast(MgtNarMavenPlugin.getConfiguration());
//			}
//			
//			List<String> list = null;
//			if(pluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("c", "excludes", pluginDomConfig);
//			}
//			if(list == null && mgtPluginDomConfig != null) {
//				list = getIncludesExcludesFromDomConfig("c", "excludes", mgtPluginDomConfig);
//			}
//			if(list != null) {
//				excludes.addAll(list);
//			}
//		} catch(ClassCastException e) {
//			int severity = IStatus.ERROR;
//			Status status = new Status(severity, pluginId, e.getMessage(), e);
//			throw new CoreException(status);
//		}
//		
//		return excludes;
//	}
//
//
//	private List<String> getIncludesExcludesFromDomConfig(String language, String type, Xpp3Dom domConfig) throws CoreException {
//		try {
//			Xpp3Dom languageDomElement = domConfig.getChild(language);
//			if(languageDomElement != null) {
//				Xpp3Dom includesDomElement = languageDomElement.getChild(type);
//				if(includesDomElement != null) {
//					Xpp3Dom[] tabIncludeDomElement = includesDomElement.getChildren();
//					if(tabIncludeDomElement != null && tabIncludeDomElement.length > 0) {
//						List<String> list = new ArrayList<String>();
//						for(Xpp3Dom includeDomElement : tabIncludeDomElement) {
//							list.add(includeDomElement.getValue());
//						}
//						return list;
//					}
//				}
//			}
//		} catch(ClassCastException e) {
//			int severity = IStatus.ERROR;
//			Status status = new Status(severity, pluginId, e.getMessage(), e);
//			throw new CoreException(status);
//		}
//		
//		return null;
//	}
	
}
