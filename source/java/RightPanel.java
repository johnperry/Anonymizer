/*---------------------------------------------------------------
*  Copyright 2020 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense.pdf)
*----------------------------------------------------------------*/

package org.rsna.anonymizer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import org.dcm4che.dict.Tags;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMCorrector;
import org.rsna.ctp.stdstages.anonymizer.xml.XMLAnonymizer;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.ColorPane;
import org.rsna.ui.FileEvent;
import org.rsna.ui.FileListener;
import org.rsna.ui.GeneralFileFilter;
import org.rsna.ui.SourcePanel;
import org.w3c.dom.*;
import org.rsna.util.FileUtil;

/**
 * A JPanel that provides a user interface for the active part of
 * the ELICAnonymizer program, including starting the anonymization
 * process and logging the results.
 */
public class RightPanel extends JPanel
						implements FileListener, ActionListener, MouseListener  {

	HeaderPanel headerPanel;
	JPanel centerPanel;
	FooterPanel footerPanel;
	ApplicationProperties properties;
	SourcePanel sourcePanel;
	ResultsScrollPane resultsPane;
	JFileChooser chooser = null;

	File currentSelection = null;
	String[] currentPath = null;
	boolean subdirectories = false;
	boolean forceIVRLE = false;
	boolean renameToSOPIUID = false;
	String dicomScriptFile = null;
	String lookupTableFile = null;
	IntegerTable integerTable = null;
	GeneralFileFilter filter = null;
	Color background = Color.getHSBColor(0.58f, 0.17f, 0.95f);

	/**
	 * Class constructor; creates an instance of the RightPanel and
	 * initializes the user interface for it.
	 * @param sourcePanel the panel that contains file or directory
	 * selected for anonymization.
	 */
	public RightPanel(SourcePanel sourcePanel) {
		super();
		Configuration config = Configuration.getInstance();
		this.properties = config.getProps();
		this.sourcePanel = sourcePanel;
		this.dicomScriptFile = config.dicomScriptFile;
		this.lookupTableFile = config.lookupTableFile;
 		this.integerTable = config.getIntegerTable();
		this.background = config.background;
		this.setLayout(new BorderLayout());
		headerPanel = new HeaderPanel();
		this.add(headerPanel,BorderLayout.NORTH);
		resultsPane = new ResultsScrollPane();
		this.add(resultsPane,BorderLayout.CENTER);
		footerPanel = new FooterPanel();
		this.add(footerPanel,BorderLayout.SOUTH);

		sourcePanel.getDirectoryPane().addFileListener(this);
		headerPanel.dirLabel.addMouseListener(this);
		footerPanel.anonymize.addActionListener(this);
		footerPanel.setStorageDir.addActionListener(this);
	}

	/**
	 * The FileListener implementation.
	 * This method captures the current file selection when
	 * it changes in the sourcePanel.
	 * @param event the event containing the File currently selected.
	 */
	public void fileEventOccurred(FileEvent event) {
		currentSelection = event.getFile();
		currentPath = currentSelection.getAbsolutePath().split("[\\\\/]");
	}

	/**
	 * The ActionListener for the footer's action buttons.
	 * This method starts the anonymization/VR correction process.
	 * @param event the event
	 */
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(footerPanel.setStorageDir)) setStorageDir();
		else if (source.equals(footerPanel.anonymize) && (currentSelection != null)) {
			subdirectories = sourcePanel.getSubdirectories();
			filter = sourcePanel.getFileFilter();
			if (source.equals(footerPanel.anonymize)) {
				new AnonymizerThread().start();
			}
		}
		else Toolkit.getDefaultToolkit().beep();
	}
	
	//Implement the MouseListener
	public void mouseClicked(MouseEvent e) {
		String path = headerPanel.dirLabel.getText();
		sourcePanel.getDirectoryPane().setCurrentPath(path);
	}
	public void mouseEntered(MouseEvent e) { }
	public void mouseExited(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }
	
	class AnonymizerThread extends Thread {
		public AnonymizerThread() {
			super();
		}
		public void run() {
			resultsPane.clear();
			anonymize(currentSelection);
			resultsPane.text.print(Color.black, "\nDone.\n");
		}
	}			

	// Anonymize the selected file(s).
	private void anonymize(File file) {
		String filterScript = FilterPanel.getInstance().getText().trim();
		boolean filterSRs = FilterPanel.getInstance().getFilterSRs();
		boolean filterResult = true;
		if (file.isFile()) {
			resultsPane.newItem(file.getAbsolutePath());
			DicomObject dob;
			if ( ((dob=getDicomObject(file)) != null)
					&& ( !filterSRs || !dob.isSR() )
					&& ( filterResult=((filterScript.length() == 0) || dob.matches(filterScript)) ) ) {
				File temp;
				File storageDir;
				try { 
					storageDir = Configuration.getInstance().getStorageDir();
					storageDir.mkdirs();
					temp = File.createTempFile("TEMP-", ".dcm", storageDir);
				}
				catch (Exception ex) {
					resultsPane.print(Color.red, "Unable to copy file.\n");
					return;
				}
				String origPtName = dob.getPatientName();
				String origPtID = dob.getPatientID();
				String origStudyDate = dob.getStudyDate();
				String origAccessionNumber = dob.getAccessionNumber();

				String result = "";
				DAScript dicomScript = DAScript.getInstance( new File(dicomScriptFile) );
				LookupTable lookupTable = LookupTable.getInstance(new File(lookupTableFile) );
				dob.copyTo(temp);
				result =
					DICOMAnonymizer.anonymize(
						temp, temp,
						dicomScript.toProperties(), lookupTable.getProperties(), integerTable,
						forceIVRLE, renameToSOPIUID).isOK() ? "" : "failed";;

				//Report the results
				if (!result.equals("")) {
					resultsPane.print(Color.red,"Failed\n");
				}
				else {
					resultsPane.print(Color.black,"OK\n");
					// Get the spoke name
					Properties daprops = dicomScript.toProperties();
					String spokeName = daprops.getProperty("param.SITEID");

					//Figure out where to put the temp file.
					//It is already in the root of the storageDir.
					//It needs to go in the appropriate series subdirectory
					dob = getDicomObject(temp);
					String anonPtName = dob.getPatientName();
					String anonPtID = dob.getPatientID();
					String anonSOPInstanceUID = dob.getSOPInstanceUID();
					String anonStudyInstanceUID = dob.getStudyInstanceUID();
					String anonSeriesInstanceUID = dob.getSeriesInstanceUID();
					String anonStudyDate = dob.getStudyDate();
					String anonStudyTime = dob.getStudyTime();
					int k = anonStudyTime.indexOf(".");
					k = (k >= 0) ? k : anonStudyTime.length();
					anonStudyTime = anonStudyTime.substring(0,k);
					if (anonStudyTime.length() > 0) anonStudyTime = "T" + anonStudyTime;
					String anonStudyDateTime = anonStudyDate + anonStudyTime;
					String anonSeriesNumber = dob.getSeriesNumber();
					String anonInstanceNumber = dob.getInstanceNumber();
					String anonAccessionNumber = dob.getAccessionNumber();
					File imgdir = new File(storageDir, anonPtID + "/" 
									+ "Study-"+anonStudyDateTime + "/" 
									+ "Series-"+anonSeriesNumber);
					imgdir.mkdirs();
					File dest = new File(imgdir, "Image-"+anonInstanceNumber+".dcm");

					//Move the file to the correct directory.
					if (dest.exists()) dest.delete();
					temp.renameTo(dest);

					//Update the index
					Index index = Index.getInstance();
					index.addPatient(origPtName, origPtID, anonPtName, anonPtID);
					index.addStudy(origPtID, origStudyDate, origAccessionNumber, anonStudyDate, anonAccessionNumber);
				}
			}
			else {
				if (dob == null) resultsPane.print(Color.red,"File rejected (not a DICOM file)\n");
				else if (!filterResult) resultsPane.print(Color.red,"File rejected (filter)\n");
				else resultsPane.print(Color.red,"File rejected (unknown reason)\n");
			}
			return;
		}
		else {
			File[] files = file.listFiles(filter);
			for (File f : files) {
				if (f.isFile() || subdirectories) anonymize(f);
			}
		}
	}
	
	private DicomObject getDicomObject(File file) {
		try { return new DicomObject(file); }
		catch (Exception ex) { return null; }
	}
	
	private void setStorageDir() {
		Configuration config = Configuration.getInstance();
		if (chooser == null) {
			chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setSelectedFile(config.getStorageDir());
		}
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File dir = chooser.getSelectedFile();
			config.setStorageDir(dir);
		}
	}
	
	//Class to display the results of the processing
	class ResultsScrollPane extends JScrollPane {
		public ColorPane text;
		int count;
		String margin = "       ";
		public ResultsScrollPane() {
			super();
			text = new ColorPane();
			setViewportView(text);
			count = 0;
		}
		public void clear() {
			count = 0;
			text.setText("");
		}
		public void newItem(String s) {
			count++;
			text.print(Color.black, String.format("%5d: %s\n", count, s));
		}
		public void print(Color c, String s) {
			text.print(c, margin + s);
		}
	}

	//Class to display the heading in the proper place
	class HeaderPanel extends JPanel {
		public JLabel dirLabel;
		public HeaderPanel() {
			super();
			this.setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
			JLabel panelLabel = new JLabel(" Results",SwingConstants.LEFT);
			setStorageDir(Configuration.getInstance().getStorageDir());
			this.setBackground(background);
			Font labelFont = new Font("Dialog", Font.BOLD, 18);
			panelLabel.setFont(labelFont);
			this.add(panelLabel);
			this.add(Box.createHorizontalGlue());
			this.add(dirLabel);
			this.add(Box.createHorizontalStrut(17));
		}
		public void setStorageDir(File dir) {
			dirLabel = new JLabel(dir.getAbsolutePath());
		}
	}

	//Class to display the footer with the action buttons and
	//the checkbox for changing the names of processed files.
	class FooterPanel extends JPanel {
		public JButton anonymize;
		public JButton setStorageDir;
		public FooterPanel() {
			super();
			this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			this.setBackground(background);

			anonymize = new JButton("Import");
			setStorageDir = new JButton("Set Storage Directory");

			Box rowB = new Box(BoxLayout.X_AXIS);
			rowB.add(Box.createHorizontalStrut(17));
			rowB.add(setStorageDir);
			rowB.add(Box.createHorizontalGlue());
			rowB.add(anonymize);
			rowB.add(Box.createHorizontalStrut(17));

			Dimension anSize = anonymize.getPreferredSize();
			Dimension sdSize = setStorageDir.getPreferredSize();
			int maxWidth = Math.max(anSize.width, sdSize.width);
			anSize.width = maxWidth;
			anonymize.setPreferredSize(anSize);
			setStorageDir.setPreferredSize(anSize);

			this.add(rowB);
		}
	}

}
