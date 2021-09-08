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
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerFunctions;
import org.rsna.ctp.stdstages.anonymizer.AnonymizerStatus;
import org.rsna.ctp.stdstages.anonymizer.IntegerTable;
import org.rsna.ctp.stdstages.anonymizer.LookupTable;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMAnonymizer;
import org.rsna.ctp.stdstages.anonymizer.dicom.DICOMCorrector;
import org.rsna.ctp.stdstages.dicom.SimpleDicomStorageSCP;
import org.rsna.ui.ColorPane;
import org.rsna.ui.FileEvent;
import org.rsna.ui.FileListener;
import org.rsna.util.FileUtil;
import org.rsna.util.IPUtil;


public class SCPPanel extends BasePanel implements ActionListener, KeyListener, FileListener {

	static final Logger logger = Logger.getLogger(SCPPanel.class);

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
	
	ResultsScrollPane resultsPane;
	StatusPanel statusPanel;
	int count = 0; //count of processed files
	int received = 0; //count of files received from the SCP

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
		JPanel p = new JPanel();
		p.setLayout(new BorderLayout());
		resultsPane = new ResultsScrollPane();
		p.add(resultsPane,BorderLayout.CENTER);
		statusPanel = new StatusPanel(bgColor);
		p.add(statusPanel,BorderLayout.SOUTH);
		this.add(p, BorderLayout.CENTER);		

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
	
	public synchronized int setCount(int c) {
		count = c;
		return count;
	}
	public synchronized int getCount() {
		return count;
	}
	public synchronized int incrementCount() {
		return ++count;
	}
	
	public String getAET() {
		return aet.getText().trim();
	}

	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(clear)) {
			resultsPane.clear();
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
			scp.addFileListener(this);
			scp.start();
			String adrs = IPUtil.getIPAddress() + ":" + scpPort + " [AET:"+getAET()+"]";
			resultsPane.clear();
			statusPanel.clear();
			setCount(0);
			resultsPane.println("DICOM Storage SCP open on "+adrs);
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
		resultsPane.println("DICOM Storage SCP stopped");
		logger.info("DICOM Storage SCP stopped");
		start.setText("Start SCP");
		scpRunning = false;
	}
	
	public synchronized void fileEventOccurred(FileEvent event) {
		if (event.isSTORE()) {
			received++;
		}
	}
	
	public synchronized int getReceivedFileCount() {
		return received;
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
		boolean saveRejected = fp.getSaveRejected();
		boolean filterResult = true;
		long startTime = System.currentTimeMillis();
		DicomObject dob = null;
		if ( ((dob=getDicomObject(file)) != null)
				&& ( dob.isImage() )
				&& ( !filterSCs || !dob.isSecondaryCapture() || (acceptRFs && dob.isReformatted()) )
				&& ( !filterSRs || !dob.isSR() )
				&& ( filterResult=((filterScript.length() == 0) || dob.matches(filterScript)) ) ) {
			File temp;
			File tempDir;
			File storageDir;
			try {
				Configuration config = Configuration.getInstance();
				storageDir = config.getStorageDir();
				storageDir.mkdirs();
				tempDir = config.getTempDir();
				temp = File.createTempFile("TEMP-", ".dcm", tempDir);
			}
			catch (Exception ex) {
				resultsPane.println(Color.red, "Unable to copy file"+file.getName());
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
				resultsPane.println(Color.red,"Anonymization failed: "+temp.getName());
				return true;
			}
			else {
				// Get the spoke name
				Properties daprops = dicomScript.toProperties();
				String spokeName = daprops.getProperty("param.SITEID");

				//Figure out where to put the temp file.
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
				if (FileUtil.copy(temp, dest)) {
					//Get rid of the temp file and update the index
					temp.delete();
					Index index = Index.getInstance();
					index.addPatient(origPtName, origPtID, anonPtName, anonPtID);
					index.addStudy(origPtID, origStudyDate, origAccessionNumber, anonStudyDate, anonAccessionNumber);
					long endTime = System.currentTimeMillis();
					statusPanel.setStatus(incrementCount(), dest.getAbsolutePath(), endTime-startTime);
					return true;
				}
				else {
					logger.warn("Unable to copy\n     "+temp+"\n to: "+dest);
					return false;
				}
			}
		}
		else {
			if (dob == null) {
				resultsPane.println(Color.black, file.getName());
				resultsPane.println(Color.red, "\n    File rejected (not a DICOM file)");
			}
			else {
				resultsPane.println(Color.black, dob.getFile().getName());
				if (filterSRs && dob.isSR()) resultsPane.println(Color.red,"    File rejected (Structured Report)");
				else if (filterSCs && dob.isSecondaryCapture()) resultsPane.println(Color.red,"    File rejected (Secondary Capture)");
				else if (!filterResult) resultsPane.println(Color.red,"    File rejected (filter)");
				else if (!dob.isImage()) resultsPane.println(Color.red,"    File rejected (not an image)");
				else resultsPane.println(Color.red,"    File rejected (unknown reason)");
			}
			if (saveRejected && (dob != null)) {
				dob.copyTo(new File(Configuration.getInstance().getQuarantineDir(), dob.getFile().getName()));
			}
			return false;
		}
	}

	private DicomObject getDicomObject(File file) {
		try { return new DicomObject(file); }
		catch (Exception ex) { return null; }
	}
}
