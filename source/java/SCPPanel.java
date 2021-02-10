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
import org.apache.log4j.*;
import org.dcm4che.dict.Tags;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMCorrector;
import org.rsna.ctp.stdstages.dicom.SimpleDicomStorageSCP;
import org.rsna.ui.ColorPane;
import org.rsna.util.FileUtil;
import org.rsna.util.IPUtil;


public class SCPPanel extends BasePanel implements ActionListener, KeyListener {

	static final Logger logger = Logger.getLogger(SCPPanel.class);
	public static ColorPane cp;

	Configuration config;
	File scpDirectory;
	boolean scpRunning = false;
	JScrollPane jsp;
	JButton clear;
	JButton start;
	JCheckBox autoStart;
	PanelField port;
	PanelField aet;
	String aetString;
	boolean forceIVRLE = false;
	boolean renameToSOPIUID = false;

	Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
	IntegerTable integerTable = null;
	SimpleDicomStorageSCP scp = null;

	static SCPPanel scpPanel = null;

	public static synchronized SCPPanel getInstance() {
		if (scpPanel == null) scpPanel = new SCPPanel();
		return scpPanel;
	}

	protected SCPPanel() {
		super();
		config = Configuration.getInstance();
		scpDirectory = new File("SCP");
		scpDirectory.mkdirs();
		integerTable = config.getIntegerTable();
	
		//UI Components
		cp = new ColorPane();
		cp.setScrollableTracksViewportWidth(false);
		autoStart = new JCheckBox("Autostart");
		boolean auto = config.getProps().getProperty("autostart", "").equals("true");
		autoStart.setSelected(auto);
		autoStart.setBackground(bgColor);
		autoStart.addActionListener(this);
		clear = new JButton("Clear");
		clear.addActionListener(this);
		start = new JButton("Start SCP");
		start.addActionListener(this);
		//Allow the configuration to specify the MAC address of the adapter to use.
		JLabel ip;
		String mac = config.getProps().getProperty("mac");
		if (mac != null) ip = new JLabel(IPUtil.getIPAddressForMAC(mac)+": ");
		else ip = new IPLabel(IPUtil.getIPAddress()+": ");
		String portString = config.getProps().getProperty("storagescpPort","104");
		port = new PanelField(portString);
		port.addKeyListener(this);
		aetString = config.getProps().getProperty("storagescpAET","ANONSTORE");
		aet = new PanelField(aetString, 70);
		aet.addKeyListener(this);
		
		//Header
		Box header = Box.createHorizontalBox();
		header.setBackground(Configuration.getInstance().background);
		header.add(new JLabel(" DICOM Storage SCP:  "));
		header.add(ip);
		header.add(port);
		header.add(new JLabel("   AET:  "));
		header.add(aet);
		header.add(Box.createHorizontalGlue());
		header.add(start);
		add(header, BorderLayout.NORTH);

		//Main panel
		BasePanel bp = new BasePanel();
		bp.add(cp, BorderLayout.CENTER);
		jsp = new JScrollPane();
		jsp.getVerticalScrollBar().setUnitIncrement(10);
		jsp.setViewportView(bp);
		jsp.getViewport().setBackground(Color.white);
		add(jsp, BorderLayout.CENTER);
		
		//Footer
		Box footer = Box.createHorizontalBox();
		footer.setBackground(Configuration.getInstance().background);
		footer.add(autoStart);
		footer.add(Box.createHorizontalGlue());
		footer.add(clear);
		add(footer, BorderLayout.SOUTH);
		
		if (auto) startSCP();
		new AnonymizerThread().start();
	}
	
	public void shutdown() {
		stopSCP();
	}
	
	public String getAET() {
		return aet.getText().trim();
	}

	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(clear)) {
			cp.clear();
		}
		else if (source.equals(start)) {
			if (scpRunning) stopSCP();
			else startSCP();
		}
		else if (source.equals(autoStart)) {
			boolean auto = autoStart.isSelected();
			config.getProps().setProperty("autostart", (auto?"true":"false"));
		}
	}
	
	public void keyTyped(KeyEvent event) { }
	public void keyPressed(KeyEvent event) { }
	public void keyReleased(KeyEvent event) {
		config.getProps().setProperty("storagescpPort", port.getText().trim());
		config.getProps().setProperty("storagescpAET", aet.getText().trim());
	}
	
	class IPLabel extends JLabel {
		public IPLabel(String text) {
			super(text);
			setFont(mono);
		}
	}
	
	class PanelField extends JTextField {
		public PanelField(String text) {
			this(text, 40);
		}
		public PanelField(String text, int width) {
			super(text);
			setFont(mono);
			Dimension d = getPreferredSize();
			d.width = width;
			setMaximumSize(d);
			setMinimumSize(d);
			setPreferredSize(d);
		}
	}
	
	private void startSCP() {
		try {
			int scpPort = Integer.parseInt(port.getText().trim());
			scp = new SimpleDicomStorageSCP(scpDirectory, scpPort);
			scp.setCalledAET(aetString);
			scp.start();
			String adrs = IPUtil.getIPAddress() + ":" + scpPort + " [AET:"+getAET()+"]";
			cp.println("DICOM Storage SCP open on "+adrs);
			logger.info("DICOM Storage SCP open on "+adrs);
			start.setText("Stop SCP");
			scpRunning = true;
		}
		catch (Exception ex) {
			String adrs = IPUtil.getIPAddress() + ":" + port.getText().trim();
			logger.info("Unable to start the DICOM Storage SCP on "+adrs);
			JOptionPane.showMessageDialog(
				this,
				"Unable to start the\nDICOM Storage SCP on\n"+adrs);
		}
	}
	
	private void stopSCP() {
		if (scp != null) scp.stop();
		cp.println("DICOM Storage SCP stopped");
		logger.info("DICOM Storage SCP stopped");
		start.setText("Start SCP");
		scpRunning = false;
	}
	
	class AnonymizerThread extends Thread {
		PartialFilter filter;
		long minage = 1000;
		public AnonymizerThread() {
			super();
			filter = new PartialFilter();
		}
		public void run() {
			while (true) {
				File[] files = scpDirectory.listFiles(filter);
				for (File file : files) {
					if ((System.currentTimeMillis() - file.lastModified()) > minage) {
						anonymize(file);
						file.delete();
					}
				}
				try { sleep(2000); }
				catch (Exception ignore) { }
			}
		}
		class PartialFilter implements FileFilter {
			public boolean accept(File file) {
				return !file.getName().endsWith("partial");
			}
		}
	}
	
	// Anonymize the selected file(s).
	private boolean anonymize(File file) {
		FilterPanel fp = FilterPanel.getInstance();
		String filterScript = fp.getText().trim();
		boolean filterSRs = fp.getFilterSRs();
		boolean filterSCs = fp.getFilterSCs();
		boolean acceptRFs = fp.getAcceptRFs();
		boolean filterResult = true;
		cp.print(Color.black, file.getName());
		DicomObject dob;
		if ( ((dob=getDicomObject(file)) != null)
				&& ( dob.isImage() )
				&& ( !filterSCs || !dob.isSecondaryCapture() || (acceptRFs && dob.isReformatted()) )
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
				cp.println(Color.red, "    Unable to copy file.");
				return false;
			}
			String origPtName = dob.getPatientName();
			String origPtID = dob.getPatientID();
			String origStudyDate = dob.getStudyDate();
			String origAccessionNumber = dob.getAccessionNumber();

			String result = "";
			DAScript dicomScript = DAScript.getInstance( new File(config.dicomScriptFile) );
			LookupTable lookupTable = LookupTable.getInstance(new File(config.lookupTableFile) );
			dob.copyTo(temp);
			result =
				DICOMAnonymizer.anonymize(
					temp, temp,
					dicomScript.toProperties(), lookupTable.getProperties(), integerTable,
					forceIVRLE, renameToSOPIUID).isOK() ? "" : "failed";;

			//Report the results
			if (!result.equals("")) {
				cp.println(Color.red,"    Failed");
				return true;
			}
			else {
				cp.println(Color.black,"    OK");
				// Get the spoke name
				Properties daprops = dicomScript.toProperties();
				String spokeName = daprops.getProperty("param.SITEID");

				//Figure out where to put the temp file.
				//It is already in the root of the storageDir.
				//It needs to go in the appropriate series subdirectory
				dob = getDicomObject(temp);
				String modality = dob.getModality();
				String anonPtName = dob.getPatientName();
				String anonPtID = dob.getPatientID();
				String anonSOPInstanceUID = dob.getSOPInstanceUID();
				String anonStudyInstanceUID = dob.getStudyInstanceUID();
				String anonSeriesInstanceUID = dob.getSeriesInstanceUID();
				String anonStudyDate = dob.getStudyDate();
				String anonStudyTime = dob.getStudyTime().trim();
				int k = anonStudyTime.indexOf(".");
				k = (k >= 0) ? k : anonStudyTime.length();
				anonStudyTime = anonStudyTime.substring(0,k);
				if (anonStudyTime.length() > 0) anonStudyTime = "T" + anonStudyTime;
				String anonStudyDateTime = anonStudyDate + anonStudyTime;
				String anonSeriesNumber = dob.getSeriesNumber();
				String anonInstanceNumber = dob.getInstanceNumber();
				String anonAccessionNumber = dob.getAccessionNumber();
				String hash = "";
				try { hash = "-" + AnonymizerFunctions.hash(anonStudyInstanceUID, 4); }
				catch (Exception unable) { }
				File imgdir = new File(storageDir, anonPtID + "/" 
								+ "Study-"+modality+"-"+anonStudyDateTime+hash + "/" 
								+ "Series-"+anonSeriesNumber);
				imgdir.mkdirs();
				File dest = new File(imgdir, "Image-"+anonInstanceNumber+".dcm");

				//Move the file to the correct directory.
				if (dest.exists()) dest.delete();
				if (!temp.renameTo(dest)) {
					temp.delete();
					return false;
				}

				//Update the index
				Index index = Index.getInstance();
				index.addPatient(origPtName, origPtID, anonPtName, anonPtID);
				index.addStudy(origPtID, origStudyDate, origAccessionNumber, anonStudyDate, anonAccessionNumber);
				return true;
			}
		}
		else {
			if (dob == null) cp.println(Color.red,"    File rejected (not a DICOM file)");
			else if (!dob.isImage()) cp.println(Color.red,"    File rejected (not an image)");
			else if (filterSRs && dob.isSR()) cp.println(Color.red,"    File rejected (Structured Report)");
			else if (filterSCs && dob.isSecondaryCapture()) cp.println(Color.red,"    File rejected (Secondary Capture)");
			else if (!filterResult) cp.println(Color.red,"    File rejected (filter)");
			else cp.println(Color.red,"    File rejected (unknown reason)");
			return false;
		}
	}

	private DicomObject getDicomObject(File file) {
		try { return new DicomObject(file); }
		catch (Exception ex) { return null; }
	}
}
