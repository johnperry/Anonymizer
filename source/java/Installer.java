package org.rsna.installer;

import org.rsna.installer.SimpleInstaller;

/**
 * The Anonymizer program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "Anonymizer Installer";
	static String programName = "Anonymizer";
	static String introString = "<p><b>Anonymizer</b> is a stand-alone tool for importing, de-identifying, "
								+ "and organizing DICOM objects for submission to the RSNA Covid-19 dataset.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}
