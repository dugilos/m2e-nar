package org.dugilos.m2e.nar.internal;

import java.io.File;

import com.github.maven_nar.OS;

public class SystemConfigurator {
	
	public static final String DEFAULT_MVN_COMMAND = "mvn";
	
	public static String getMvnCommand(String os) {
		String command;
		
		if(OS.WINDOWS.equals(os)) {
			command = getWindowsMvnCommand();
		} else {
			command = DEFAULT_MVN_COMMAND;
		}
		
		return command;
	}

	private static String getWindowsMvnCommand() {
		String[] commands = new String[]{DEFAULT_MVN_COMMAND + ".bat", DEFAULT_MVN_COMMAND + ".cmd", DEFAULT_MVN_COMMAND + ".exe"};
		String command = null;
		
		String path = System.getenv("path");
		String[] pathElements = path.split(File.pathSeparator);
		for(String directory : pathElements) {
			
			for(String testCommand : commands) {
				File commandFile = new File(directory, testCommand);
				if(commandFile.exists()) {
					command = testCommand;
					break;
				}
			}
			if(command != null) {
				break;
			}
		}
		
		if(command == null) {
			// la commande exécutable maven pour Windows n'a pas été trouvée dans le PATH, on met la commande par défaut
			command = DEFAULT_MVN_COMMAND;
		}
		return command;
	}

}
