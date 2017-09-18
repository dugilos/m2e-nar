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

import java.io.File;

import com.github.maven_nar.OS;

/**
 * Classe utilitaire fournissant des méthodes spécifique à l'OS.
 * 
 * @author Jean-François Blanc
 */
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
