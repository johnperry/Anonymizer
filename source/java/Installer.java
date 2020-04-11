package org.rsna.installer;

import org.rsna.installer.SimpleInstaller;

/**
 * The Anonymizer program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "Anonymizer Installer";
	static String programName = "Anonymizer";
	static String introString = "<p><b>Anonymizer</b> is a stand-alone tool for viewing, examining, "
								+ "and anonymizing DICOM objects.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}
